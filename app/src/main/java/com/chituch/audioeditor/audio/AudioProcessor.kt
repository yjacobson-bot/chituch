package com.chituch.audioeditor.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.chituch.audioeditor.model.EditMode
import com.chituch.audioeditor.model.ExportSettings
import com.chituch.audioeditor.model.OutputFormat
import com.chituch.audioeditor.model.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min

object AudioProcessor {

    data class SegmentWithFade(
        val startMs: Long,
        val endMs: Long,
        val fadeInMs: Long = 0L,
        val fadeOutMs: Long = 0L
    )

    suspend fun processAudio(
        inputPath: String,
        segments: List<Segment>,
        editMode: EditMode,
        durationMs: Long,
        settings: ExportSettings,
        outputDir: File,
        inputMimeType: String = ""
    ): List<File> = withContext(Dispatchers.IO) {

        val sortedSegments = segments.filter { it.isValid() }.sortedBy { it.startMs }
        val keepSegments: List<Segment> = when (editMode) {
            EditMode.KEEP -> sortedSegments
            EditMode.REMOVE -> invertSegments(sortedSegments, durationMs)
        }
        if (keepSegments.isEmpty()) return@withContext emptyList()

        val (effectiveFormat, outputExt) = if (settings.outputFormat == OutputFormat.ORIGINAL) {
            resolveEffectiveFormat(inputPath, inputMimeType)
        } else {
            Pair(settings.outputFormat, settings.outputFormat.extension)
        }
        val effectiveSettings = settings.copy(outputFormat = effectiveFormat)
        val exportSeparate = settings.exportMode == com.chituch.audioeditor.model.ExportMode.SEPARATE

        if (exportSeparate) {
            keepSegments.mapIndexed { i, seg ->
                val out = File(outputDir, "segment_${i + 1}.$outputExt")
                val ok = trimSegment(inputPath, SegmentWithFade(seg.startMs, seg.endMs, seg.fadeInMs, seg.fadeOutMs), effectiveSettings, out)
                if (ok) out else null
            }.filterNotNull()
        } else {
            if (keepSegments.size == 1) {
                val seg = keepSegments[0]
                val out = File(outputDir, "output.$outputExt")
                val ok = trimSegment(inputPath, SegmentWithFade(seg.startMs, seg.endMs, seg.fadeInMs, seg.fadeOutMs), effectiveSettings, out)
                if (ok) listOf(out) else emptyList()
            } else {
                val temps = keepSegments.mapIndexed { i, seg ->
                    val tmp = File(outputDir, "tmp_$i.$outputExt")
                    trimSegment(inputPath, SegmentWithFade(seg.startMs, seg.endMs, seg.fadeInMs, seg.fadeOutMs), effectiveSettings, tmp)
                    tmp
                }
                val out = File(outputDir, "output.$outputExt")
                val ok = when (effectiveFormat) {
                    OutputFormat.WAV -> concatWav(temps, out)
                    OutputFormat.AAC_M4A, OutputFormat.ORIGINAL -> concatM4a(temps, out)
                }
                temps.forEach { it.delete() }
                if (ok) listOf(out) else emptyList()
            }
        }
    }

    private fun resolveEffectiveFormat(inputPath: String, inputMimeType: String): Pair<OutputFormat, String> {
        val mime = inputMimeType.ifEmpty {
            try {
                val ex = MediaExtractor()
                ex.setDataSource(inputPath)
                val m = (0 until ex.trackCount)
                    .mapNotNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                    .firstOrNull { it.startsWith("audio/") } ?: ""
                ex.release()
                m
            } catch (_: Exception) { "" }
        }
        return if (mime == "audio/raw" || mime == "audio/wav" || inputPath.endsWith(".wav", ignoreCase = true))
            Pair(OutputFormat.WAV, "wav")
        else
            Pair(OutputFormat.AAC_M4A, "m4a")
    }

    private fun trimSegment(inputPath: String, seg: SegmentWithFade, settings: ExportSettings, out: File): Boolean {
        val needsFade = seg.fadeInMs > 0 || seg.fadeOutMs > 0
        return when (settings.outputFormat) {
            OutputFormat.WAV -> trimToWav(inputPath, seg, out)
            OutputFormat.AAC_M4A, OutputFormat.ORIGINAL -> {
                if (!needsFade && tryDirectCopy(inputPath, seg.startMs, seg.endMs, out)) true
                else reencodeToAac(inputPath, seg, settings.bitrateKbps * 1000, out)
            }
        }
    }

    // ── Direct copy (AAC input, no fade) ────────────────────────────────────────

    private fun tryDirectCopy(inputPath: String, startMs: Long, endMs: Long, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(inputPath)
            val track = findAudioTrack(extractor)
            if (track < 0) return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            if (!mime.contains("mp4a") && !mime.contains("aac")) return false

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(format)
            muxer.start()

            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buf = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstPts = Long.MIN_VALUE

            while (true) {
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                val pts = extractor.sampleTime
                if (pts > endMs * 1000L) break
                if (firstPts == Long.MIN_VALUE) firstPts = pts
                info.offset = 0; info.size = size
                info.presentationTimeUs = pts - firstPts
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxTrack, buf, info)
                extractor.advance()
            }
            muxer.stop(); true
        } catch (_: Exception) {
            outputFile.delete(); false
        } finally {
            extractor.release()
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    // ── Decode → apply fade → encode AAC ────────────────────────────────────────

    private fun reencodeToAac(inputPath: String, seg: SegmentWithFade, bitrate: Int, outputFile: File): Boolean {
        val (pcm, sampleRate, channels) = decodeToPcm(inputPath, seg.startMs, seg.endMs) ?: return false
        applyFade(pcm, sampleRate, channels, seg.fadeInMs, seg.fadeOutMs)
        return encodePcmToAac(pcm, sampleRate, channels, bitrate, outputFile)
    }

    // ── Decode → apply fade → write WAV ─────────────────────────────────────────

    private fun trimToWav(inputPath: String, seg: SegmentWithFade, outputFile: File): Boolean {
        val (pcm, sampleRate, channels) = decodeToPcm(inputPath, seg.startMs, seg.endMs) ?: return false
        applyFade(pcm, sampleRate, channels, seg.fadeInMs, seg.fadeOutMs)
        return try {
            WavWriter.write(shortArrayToBytes(pcm), sampleRate, channels, 16, outputFile)
            true
        } catch (_: Exception) { false }
    }

    // ── Decode to raw PCM shorts ─────────────────────────────────────────────────

    private data class PcmData(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    private fun decodeToPcm(inputPath: String, startMs: Long, endMs: Long): PcmData? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            extractor.setDataSource(inputPath)
            val track = findAudioTrack(extractor)
            if (track < 0) return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val endUs = endMs * 1000L
            val startUs = startMs * 1000L

            val allSamples = mutableListOf<Short>()
            val bufInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val idx = decoder.dequeueInputBuffer(5000)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        val pts = extractor.sampleTime
                        if (size < 0 || pts > endUs) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, size, pts - startUs, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(bufInfo, 5000)
                if (outIdx >= 0) {
                    val buf = decoder.getOutputBuffer(outIdx)!!
                    val shorts = ShortArray(bufInfo.size / 2)
                    buf.asShortBuffer().get(shorts)
                    allSamples.addAll(shorts.toList())
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            decoder.stop()
            PcmData(allSamples.toShortArray(), sampleRate, channels)
        } catch (_: Exception) { null }
        finally {
            extractor.release()
            try { decoder?.release() } catch (_: Exception) {}
        }
    }

    // ── Encode PCM to AAC/M4A ────────────────────────────────────────────────────

    private fun encodePcmToAac(pcm: ShortArray, sampleRate: Int, channels: Int, bitrate: Int, outputFile: File): Boolean {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate.coerceIn(64_000, 320_000))
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192 * channels)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxTrack = -1
            var muxStarted = false
            var firstPts = Long.MIN_VALUE

            val bytesPerFrame = 1024 * channels * 2
            var pcmOffset = 0
            val pcmBytes = shortArrayToBytes(pcm)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val idx = encoder.dequeueInputBuffer(0)
                    if (idx >= 0) {
                        val buf = encoder.getInputBuffer(idx)!!
                        if (pcmOffset >= pcmBytes.size) {
                            encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunk = min(bytesPerFrame, pcmBytes.size - pcmOffset)
                            buf.clear(); buf.put(pcmBytes, pcmOffset, chunk)
                            val pts = (pcmOffset / (channels * 2)).toLong() * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(idx, 0, chunk, pts, 0)
                            pcmOffset += chunk
                        }
                    }
                }
                val outIdx = encoder.dequeueOutputBuffer(info, 0)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start(); muxStarted = true
                    }
                    outIdx >= 0 -> {
                        val buf = encoder.getOutputBuffer(outIdx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && muxStarted) {
                            if (firstPts == Long.MIN_VALUE) firstPts = info.presentationTimeUs
                            info.presentationTimeUs -= firstPts
                            muxer.writeSampleData(muxTrack, buf, info)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            muxer.stop(); true
        } catch (_: Exception) { outputFile.delete(); false }
        finally {
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    // ── Fade envelope ────────────────────────────────────────────────────────────

    private fun applyFade(pcm: ShortArray, sampleRate: Int, channels: Int, fadeInMs: Long, fadeOutMs: Long) {
        val totalFrames = pcm.size / channels
        val fadeInFrames = (fadeInMs * sampleRate / 1000L).toInt().coerceAtMost(totalFrames)
        val fadeOutFrames = (fadeOutMs * sampleRate / 1000L).toInt().coerceAtMost(totalFrames)

        for (i in 0 until fadeInFrames) {
            val gain = i.toFloat() / fadeInFrames
            for (c in 0 until channels) pcm[i * channels + c] = (pcm[i * channels + c] * gain).toInt().toShort()
        }
        for (i in 0 until fadeOutFrames) {
            val frame = totalFrames - 1 - i
            val gain = i.toFloat() / fadeOutFrames
            for (c in 0 until channels) pcm[frame * channels + c] = (pcm[frame * channels + c] * gain).toInt().toShort()
        }
    }

    // ── Concat helpers ───────────────────────────────────────────────────────────

    private fun concatM4a(files: List<File>, output: File): Boolean {
        if (files.size == 1) { files[0].copyTo(output, overwrite = true); return true }
        var muxer: MediaMuxer? = null
        val extractors = mutableListOf<MediaExtractor>()
        return try {
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxTrack = -1
            var muxStarted = false
            var offsetUs = 0L

            files.forEach { file ->
                val ext = MediaExtractor().also { extractors.add(it) }
                ext.setDataSource(file.absolutePath)
                val track = findAudioTrack(ext)
                if (track < 0) return@forEach
                ext.selectTrack(track)
                if (!muxStarted) {
                    muxTrack = muxer.addTrack(ext.getTrackFormat(track))
                    muxer.start(); muxStarted = true
                }
                val buf = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                var lastPts = 0L
                while (true) {
                    val size = ext.readSampleData(buf, 0)
                    if (size < 0) break
                    info.offset = 0; info.size = size
                    info.presentationTimeUs = ext.sampleTime + offsetUs
                    info.flags = ext.sampleFlags
                    lastPts = info.presentationTimeUs
                    muxer.writeSampleData(muxTrack, buf, info)
                    ext.advance()
                }
                offsetUs = lastPts + 23220L
            }
            muxer.stop(); true
        } catch (_: Exception) { output.delete(); false }
        finally {
            extractors.forEach { try { it.release() } catch (_: Exception) {} }
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun concatWav(files: List<File>, output: File): Boolean {
        return try {
            val allBytes = files.flatMap { f ->
                if (!f.exists()) return@flatMap emptyList()
                val bytes = f.readBytes()
                if (bytes.size <= 44) return@flatMap emptyList()
                bytes.drop(44).toList()
            }.toByteArray()
            val first = files.firstOrNull { it.exists() } ?: return false
            val header = first.readBytes().take(44).toByteArray()
            val sampleRate = header.getIntLE(24)
            val channels = header.getShortLE(22).toInt()
            WavWriter.write(allBytes, sampleRate, channels, 16, output)
            true
        } catch (_: Exception) { false }
    }

    // ── Utilities ────────────────────────────────────────────────────────────────

    private fun findAudioTrack(e: MediaExtractor): Int {
        for (i in 0 until e.trackCount) {
            val mime = e.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun invertSegments(removed: List<Segment>, durationMs: Long): List<Segment> {
        val res = mutableListOf<Segment>(); var cur = 0L; var id = 0
        for (s in removed) { if (cur < s.startMs) res.add(Segment(id++, cur, s.startMs)); cur = s.endMs }
        if (cur < durationMs) res.add(Segment(id++, cur, durationMs))
        return res
    }

    private fun shortArrayToBytes(sa: ShortArray): ByteArray {
        val ba = ByteArray(sa.size * 2)
        for (i in sa.indices) { ba[i * 2] = (sa[i].toInt() and 0xFF).toByte(); ba[i * 2 + 1] = (sa[i].toInt() shr 8 and 0xFF).toByte() }
        return ba
    }

    private fun ByteArray.getIntLE(offset: Int) = (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.getShortLE(offset: Int) = ((this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)).toShort()
}
