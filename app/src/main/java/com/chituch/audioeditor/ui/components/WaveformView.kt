package com.chituch.audioeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.chituch.audioeditor.ui.theme.segmentColors
import com.chituch.audioeditor.viewmodel.SegmentPair
import kotlin.math.abs

@Composable
fun WaveformView(
    waveformData: FloatArray,
    durationMs: Long,
    currentPositionMs: Long,
    segmentPairs: List<SegmentPair>,
    activeSegmentId: Int,
    onSeek: (Long) -> Unit,
    onSegmentStartChanged: (Int, Long) -> Unit,
    onSegmentEndChanged: (Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0L) return

    var canvasWidth by remember { mutableStateOf(0f) }

    val HANDLE_TOUCH_RADIUS = 40f

    fun xToMs(x: Float): Long = ((x / canvasWidth) * durationMs).toLong().coerceIn(0L, durationMs)
    fun msToX(ms: Long): Float = if (durationMs > 0) (ms.toFloat() / durationMs.toFloat()) * canvasWidth else 0f

    data class DragTarget(val segId: Int, val isStart: Boolean)
    var dragging by remember { mutableStateOf<DragTarget?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
                .pointerInput(durationMs, segmentPairs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            canvasWidth = size.width.toFloat()
                            val touchX = offset.x
                            var bestDist = HANDLE_TOUCH_RADIUS * 2
                            var best: DragTarget? = null
                            segmentPairs.forEach { pair ->
                                val sx = msToX(pair.startMs)
                                val ex = msToX(pair.endMs)
                                val ds = abs(touchX - sx)
                                val de = abs(touchX - ex)
                                if (ds < bestDist) { bestDist = ds; best = DragTarget(pair.id, true) }
                                if (de < bestDist) { bestDist = de; best = DragTarget(pair.id, false) }
                            }
                            dragging = best
                        },
                        onDrag = { change, _ ->
                            val d = dragging ?: return@detectDragGestures
                            val ms = xToMs(change.position.x)
                            if (d.isStart) onSegmentStartChanged(d.segId, ms)
                            else onSegmentEndChanged(d.segId, ms)
                        },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null }
                    )
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        canvasWidth = size.width.toFloat()
                        val ms = xToMs(offset.x)
                        onSeek(ms)
                    }
                }
        ) {
            canvasWidth = size.width

            val centerY = size.height / 2f
            val maxBarHeight = size.height * 0.45f
            val barWidth = size.width / (waveformData.size.coerceAtLeast(1).toFloat())

            // Draw waveform bars
            waveformData.forEachIndexed { i, amplitude ->
                val x = i * barWidth + barWidth / 2f
                val barH = amplitude * maxBarHeight
                val color = if (currentPositionMs > 0L && x <= msToX(currentPositionMs)) {
                    Color(0xFF7C4DFF)
                } else {
                    Color(0xFF546E7A)
                }
                drawLine(
                    color = color,
                    start = Offset(x, centerY - barH),
                    end = Offset(x, centerY + barH),
                    strokeWidth = (barWidth * 0.7f).coerceAtLeast(1f),
                    cap = StrokeCap.Round
                )
            }

            // Draw segment regions
            segmentPairs.forEachIndexed { idx, pair ->
                val color = segmentColors[idx % segmentColors.size]
                val startX = msToX(pair.startMs)
                val endX = msToX(pair.endMs)

                drawRect(
                    color = color.copy(alpha = 0.18f),
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, size.height)
                )

                // Start handle
                drawLine(
                    color = color,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = if (pair.id == activeSegmentId) 4.dp.toPx() else 2.5.dp.toPx()
                )
                drawCircle(
                    color = color,
                    radius = if (pair.id == activeSegmentId) 10.dp.toPx() else 7.dp.toPx(),
                    center = Offset(startX, centerY)
                )

                // End handle
                drawLine(
                    color = color,
                    start = Offset(endX, 0f),
                    end = Offset(endX, size.height),
                    strokeWidth = if (pair.id == activeSegmentId) 4.dp.toPx() else 2.5.dp.toPx()
                )
                drawCircle(
                    color = color,
                    radius = if (pair.id == activeSegmentId) 10.dp.toPx() else 7.dp.toPx(),
                    center = Offset(endX, centerY)
                )
            }

            // Draw playhead
            if (durationMs > 0) {
                val playX = msToX(currentPositionMs)
                drawLine(
                    color = Color.White,
                    start = Offset(playX, 0f),
                    end = Offset(playX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(playX, 8.dp.toPx())
                )
            }
        }
    }
}
