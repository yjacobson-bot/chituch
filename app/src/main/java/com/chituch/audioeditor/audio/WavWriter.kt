package com.chituch.audioeditor.audio

import java.io.DataOutputStream
import java.io.File

object WavWriter {

    fun write(pcmBytes: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int = 16, outputFile: File) {
        val dataSize = pcmBytes.size
        DataOutputStream(outputFile.outputStream().buffered()).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLE(dataSize + 36)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)                                       // PCM
            out.writeShortLE(channels)
            out.writeIntLE(sampleRate)
            out.writeIntLE(sampleRate * channels * bitsPerSample / 8) // byte rate
            out.writeShortLE(channels * bitsPerSample / 8)            // block align
            out.writeShortLE(bitsPerSample)
            out.writeBytes("data")
            out.writeIntLE(dataSize)
            out.write(pcmBytes)
        }
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
}
