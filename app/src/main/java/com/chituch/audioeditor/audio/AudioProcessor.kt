package com.chituch.audioeditor.audio

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.chituch.audioeditor.model.EditMode
import com.chituch.audioeditor.model.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AudioProcessor {

    suspend fun processAudio(
        context: Context,
        inputPath: String,
        segments: List<Segment>,
        editMode: EditMode,
        durationMs: Long,
        exportSeparate: Boolean,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {

        val extension = inputPath.substringAfterLast('.', "mp3")
        val sortedSegments = segments.filter { it.isValid() }.sortedBy { it.startMs }

        val keepSegments: List<Segment> = when (editMode) {
            EditMode.KEEP -> sortedSegments
            EditMode.REMOVE -> invertSegments(sortedSegments, durationMs)
        }

        if (keepSegments.isEmpty()) return@withContext emptyList()

        if (exportSeparate) {
            keepSegments.mapIndexed { index, seg ->
                val outFile = File(outputDir, "segment_${index + 1}.$extension")
                val cmd = buildTrimCommand(inputPath, seg.startMs, seg.endMs, outFile.absolutePath)
                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode)) outFile else null
            }.filterNotNull()
        } else {
            if (keepSegments.size == 1) {
                val seg = keepSegments[0]
                val outFile = File(outputDir, "output.$extension")
                val cmd = buildTrimCommand(inputPath, seg.startMs, seg.endMs, outFile.absolutePath)
                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode)) listOf(outFile) else emptyList()
            } else {
                val tempFiles = keepSegments.mapIndexed { idx, seg ->
                    val tmp = File(outputDir, "tmp_${idx}.$extension")
                    val cmd = buildTrimCommand(inputPath, seg.startMs, seg.endMs, tmp.absolutePath)
                    FFmpegKit.execute(cmd)
                    tmp
                }
                val concatList = File(outputDir, "concat_list.txt")
                concatList.writeText(tempFiles.joinToString("\n") { "file '${it.absolutePath}'" })
                val outFile = File(outputDir, "output.$extension")
                val concatCmd = "-f concat -safe 0 -i \"${concatList.absolutePath}\" -c copy \"${outFile.absolutePath}\""
                val session = FFmpegKit.execute(concatCmd)
                tempFiles.forEach { it.delete() }
                concatList.delete()
                if (ReturnCode.isSuccess(session.returnCode)) listOf(outFile) else emptyList()
            }
        }
    }

    private fun buildTrimCommand(input: String, startMs: Long, endMs: Long, output: String): String {
        val startSec = startMs / 1000.0
        val durationSec = (endMs - startMs) / 1000.0
        return "-i \"$input\" -ss $startSec -t $durationSec -c copy \"$output\""
    }

    private fun invertSegments(removed: List<Segment>, durationMs: Long): List<Segment> {
        val result = mutableListOf<Segment>()
        var cursor = 0L
        var id = 0
        for (seg in removed) {
            if (cursor < seg.startMs) {
                result.add(Segment(id++, cursor, seg.startMs))
            }
            cursor = seg.endMs
        }
        if (cursor < durationMs) {
            result.add(Segment(id++, cursor, durationMs))
        }
        return result
    }
}
