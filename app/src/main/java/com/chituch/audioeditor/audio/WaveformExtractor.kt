package com.chituch.audioeditor.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

object WaveformExtractor {

    suspend fun extract(context: Context, uri: Uri, samplesCount: Int): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val audioTrackIndex = findAudioTrack(extractor)
                if (audioTrackIndex < 0) return@withContext FloatArray(samplesCount)

                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(samplesCount)

                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val rawSamples = mutableListOf<Short>()
                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val buf = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outputIndex >= 0) {
                        val buf = codec.getOutputBuffer(outputIndex)!!
                        val shorts = ShortArray(bufferInfo.size / 2)
                        buf.asShortBuffer().get(shorts)
                        rawSamples.addAll(shorts.toList())
                        codec.releaseOutputBuffer(outputIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }

                        if (rawSamples.size > 2_000_000) sawOutputEOS = true
                    }
                }

                codec.stop()
                codec.release()

                downsample(rawSamples.toShortArray(), samplesCount)
            } catch (e: Exception) {
                FloatArray(samplesCount)
            } finally {
                extractor.release()
            }
        }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun downsample(samples: ShortArray, targetCount: Int): FloatArray {
        if (samples.isEmpty()) return FloatArray(targetCount)
        val result = FloatArray(targetCount)
        val chunkSize = max(1, samples.size / targetCount)
        for (i in 0 until targetCount) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, samples.size)
            var maxVal = 0
            for (j in start until end) {
                maxVal = max(maxVal, abs(samples[j].toInt()))
            }
            result[i] = maxVal / 32768f
        }
        return result
    }
}
