package com.chituch.audioeditor.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.chituch.audioeditor.model.EditMode
import com.chituch.audioeditor.model.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioProcessor {

    suspend fun processAudio(
        inputPath: String,
        segments: List<Segment>,
        editMode: EditMode,
        durationMs: Long,
        exportSeparate: Boolean,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {

        val sortedSegments = segments.filter { it.isValid() }.sortedBy { it.startMs }

        val keepSegments: List<Segment> = when (editMode) {
            EditMode.KEEP -> sortedSegments
            EditMode.REMOVE -> invertSegments(sortedSegments, durationMs)
        }

        if (keepSegments.isEmpty()) return@withContext emptyList()

        if (exportSeparate) {
            keepSegments.mapIndexed { index, seg ->
                val outFile = File(outputDir, "segment_${index + 1}.m4a")
                val ok = trimSegment(inputPath, seg.startMs, seg.endMs, outFile)
                if (ok) outFile else null
            }.filterNotNull()
        } else {
            if (keepSegments.size == 1) {
                val seg = keepSegments[0]
                val outFile = File(outputDir, "output.m4a")
                val ok = trimSegment(inputPath, seg.startMs, seg.endMs, outFile)
                if (ok) listOf(outFile) else emptyList()
            } else {
                val tempFiles = keepSegments.mapIndexed { idx, seg ->
                    val tmp = File(outputDir, "tmp_${idx}.m4a")
                    trimSegment(inputPath, seg.startMs, seg.endMs, tmp)
                    tmp
                }
                val outFile = File(outputDir, "output.m4a")
                val ok = concatSegments(tempFiles, outFile)
                tempFiles.forEach { it.delete() }
                if (ok) listOf(outFile) else emptyList()
            }
        }
    }

    private fun trimSegment(inputPath: String, startMs: Long, endMs: Long, outputFile: File): Boolean {
        return try {
            if (tryDirectCopy(inputPath, startMs, endMs, outputFile)) return true
            reencodeSegment(inputPath, startMs, endMs, outputFile)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Direct copy without re-encoding (works for AAC/M4A). Fastest path.
     */
    private fun tryDirectCopy(inputPath: String, startMs: Long, endMs: Long, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(inputPath)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) return false
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            // Direct copy only works reliably for AAC in MPEG-4 container
            if (!mime.contains("mp4a") && !mime.contains("aac")) return false

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(format)
            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstPts = Long.MIN_VALUE

            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break

                if (firstPts == Long.MIN_VALUE) firstPts = pts

                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts - firstPts
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            true
        } catch (e: Exception) {
            try { muxer?.release() } catch (_: Exception) {}
            outputFile.delete()
            false
        } finally {
            extractor.release()
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Decode → re-encode to AAC → mux. Handles MP3, OGG, FLAC, etc.
     */
    private fun reencodeSegment(inputPath: String, startMs: Long, endMs: Long, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        return try {
            extractor.setDataSource(inputPath)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex < 0) return false
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val bitRate = if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE))
                inputFormat.getInteger(MediaFormat.KEY_BIT_RATE).coerceIn(64_000, 320_000)
            else 128_000

            // Setup decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Setup encoder (AAC)
            val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1
            var muxerStarted = false

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bufInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var firstOutputPts = Long.MIN_VALUE

            while (!encoderDone) {
                // Feed extractor → decoder
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(0)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        val pts = extractor.sampleTime
                        if (size < 0 || pts > endUs) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, pts - startUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain decoder → encoder
                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(bufInfo, 0)
                    if (outIdx >= 0) {
                        val pcmBuf = decoder.getOutputBuffer(outIdx)!!
                        val encInIdx = encoder.dequeueInputBuffer(10_000)
                        if (encInIdx >= 0) {
                            val encBuf = encoder.getInputBuffer(encInIdx)!!
                            encBuf.clear()
                            val toCopy = minOf(pcmBuf.remaining(), encBuf.capacity())
                            val slice = pcmBuf.slice().limit(toCopy) as ByteBuffer
                            encBuf.put(slice)
                            val flags = if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decoderDone = true
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            } else 0
                            encoder.queueInputBuffer(encInIdx, 0, toCopy, bufInfo.presentationTimeUs, flags)
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                    }
                }

                // Drain encoder → muxer
                val encOutIdx = encoder.dequeueOutputBuffer(bufInfo, 0)
                if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    muxerTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encOutIdx >= 0) {
                    val encData = encoder.getOutputBuffer(encOutIdx)!!
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufInfo.size > 0 && muxerStarted) {
                        if (firstOutputPts == Long.MIN_VALUE) firstOutputPts = bufInfo.presentationTimeUs
                        bufInfo.presentationTimeUs -= firstOutputPts
                        muxer.writeSampleData(muxerTrack, encData, bufInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encoderDone = true
                    }
                }
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun concatSegments(files: List<File>, outputFile: File): Boolean {
        if (files.isEmpty()) return false
        if (files.size == 1) { files[0].copyTo(outputFile, overwrite = true); return true }

        var muxer: MediaMuxer? = null
        val extractors = mutableListOf<MediaExtractor>()

        return try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1
            var muxerStarted = false
            var timeOffsetUs = 0L

            files.forEach { file ->
                val extractor = MediaExtractor()
                extractors.add(extractor)
                extractor.setDataSource(file.absolutePath)
                val track = findAudioTrack(extractor)
                if (track < 0) return@forEach
                extractor.selectTrack(track)

                if (!muxerStarted) {
                    muxerTrack = muxer.addTrack(extractor.getTrackFormat(track))
                    muxer.start()
                    muxerStarted = true
                }

                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                var lastPts = 0L

                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime + timeOffsetUs
                    info.flags = extractor.sampleFlags
                    lastPts = info.presentationTimeUs
                    muxer.writeSampleData(muxerTrack, buffer, info)
                    extractor.advance()
                }
                timeOffsetUs = lastPts + 23220 // ~1 AAC frame at 44100Hz
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        } finally {
            extractors.forEach { try { it.release() } catch (_: Exception) {} }
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun invertSegments(removed: List<Segment>, durationMs: Long): List<Segment> {
        val result = mutableListOf<Segment>()
        var cursor = 0L
        var id = 0
        for (seg in removed) {
            if (cursor < seg.startMs) result.add(Segment(id++, cursor, seg.startMs))
            cursor = seg.endMs
        }
        if (cursor < durationMs) result.add(Segment(id++, cursor, durationMs))
        return result
    }
}
