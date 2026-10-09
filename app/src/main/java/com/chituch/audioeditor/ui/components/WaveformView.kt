package com.chituch.audioeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    zoomLevel: Float,
    scrollOffsetMs: Long,
    onSeek: (Long) -> Unit,
    onSegmentStartChanged: (Int, Long, Boolean) -> Unit,
    onSegmentEndChanged: (Int, Long, Boolean) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onScrollChanged: (Long) -> Unit,
    onZoomToSegment: ((startMs: Long, endMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0L) return

    val visibleMs = (durationMs / zoomLevel).toLong().coerceAtLeast(1L)
    val startMs = scrollOffsetMs
    val endMs = (scrollOffsetMs + visibleMs).coerceAtMost(durationMs)

    var canvasWidth by remember { mutableStateOf(0f) }
    val HANDLE_TOUCH_PX = 44f

    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    fun xToMs(x: Float): Long = (startMs + (x / canvasWidth.coerceAtLeast(1f)) * visibleMs).toLong().coerceIn(0L, durationMs)
    fun msToX(ms: Long): Float {
        if (canvasWidth <= 0f) return 0f
        return ((ms - startMs).toFloat() / visibleMs.toFloat()) * canvasWidth
    }

    data class DragTarget(val segId: Int, val isStart: Boolean)
    var dragging by remember { mutableStateOf<DragTarget?>(null) }

    Box(modifier = modifier) {
        // Pinch to zoom + pan
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
                .pointerInput(durationMs, zoomLevel, scrollOffsetMs) {
                    detectTransformGestures { _, pan, zoomDelta, _ ->
                        val newZoom = (zoomLevel * zoomDelta).coerceIn(1f, 30f)
                        onZoomChanged(newZoom)
                        val panMs = -(pan.x / canvasWidth.coerceAtLeast(1f)) * visibleMs
                        onScrollChanged((scrollOffsetMs + panMs.toLong()))
                    }
                }
                .pointerInput(durationMs, segmentPairs, scrollOffsetMs, zoomLevel) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            canvasWidth = size.width.toFloat()
                            val touchX = offset.x
                            var bestDist = HANDLE_TOUCH_PX * 2
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
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val d = dragging
                            if (d == null) {
                                val panMs = -(dragAmount.x / canvasWidth.coerceAtLeast(1f)) * visibleMs
                                onScrollChanged((scrollOffsetMs + panMs.toLong()))
                            } else {
                                val ms = xToMs(change.position.x)
                                if (d.isStart) onSegmentStartChanged(d.segId, ms, true)
                                else onSegmentEndChanged(d.segId, ms, true)
                            }
                        },
                        onDragEnd = {
                            dragging?.let { d ->
                                val pair = segmentPairs.find { it.id == d.segId }
                                if (pair != null) {
                                    if (d.isStart) onSegmentStartChanged(d.segId, pair.startMs, false)
                                    else onSegmentEndChanged(d.segId, pair.endMs, false)
                                }
                            }
                            dragging = null
                        },
                        onDragCancel = { dragging = null }
                    )
                }
                .pointerInput(durationMs, scrollOffsetMs, zoomLevel, segmentPairs, activeSegmentId) {
                    detectTapGestures(
                        onTap = { offset ->
                            canvasWidth = size.width.toFloat()
                            onSeek(xToMs(offset.x))
                        },
                        onDoubleTap = { _ ->
                            val activePair = segmentPairs.find { it.id == activeSegmentId }
                            if (activePair != null) {
                                onZoomToSegment?.invoke(activePair.startMs, activePair.endMs)
                            }
                        }
                    )
                }
        ) {
            canvasWidth = size.width

            val centerY = size.height / 2f
            val maxBarH = size.height * 0.45f

            // Visible range of waveform samples
            val totalSamples = waveformData.size
            val startFrac = startMs.toFloat() / durationMs
            val endFrac = endMs.toFloat() / durationMs
            val sampleStart = (startFrac * totalSamples).toInt().coerceIn(0, totalSamples)
            val sampleEnd = (endFrac * totalSamples).toInt().coerceIn(0, totalSamples)
            val visibleSamples = waveformData.copyOfRange(sampleStart, sampleEnd)

            val barW = if (visibleSamples.isNotEmpty()) size.width / visibleSamples.size else 1f

            visibleSamples.forEachIndexed { i, amplitude ->
                val x = i * barW + barW / 2f
                val barH = amplitude * maxBarH
                val sampleMs = startMs + (i.toLong() * visibleMs / visibleSamples.size.coerceAtLeast(1))
                val color = if (currentPositionMs > startMs && sampleMs <= currentPositionMs) Color(0xFF7C4DFF) else Color(0xFF546E7A)
                drawLine(color = color, start = Offset(x, centerY - barH), end = Offset(x, centerY + barH),
                    strokeWidth = (barW * 0.7f).coerceAtLeast(1f), cap = StrokeCap.Round)
            }

            // Segment regions
            segmentPairs.forEachIndexed { idx, pair ->
                val color = segmentColors[idx % segmentColors.size]
                val sx = msToX(pair.startMs).coerceIn(0f, size.width)
                val ex = msToX(pair.endMs).coerceIn(0f, size.width)
                val w = (ex - sx).coerceAtLeast(0f)

                drawRect(color = color.copy(alpha = 0.18f), topLeft = Offset(sx, 0f), size = Size(w, size.height))

                val strokeW = if (pair.id == activeSegmentId) 4.dp.toPx() else 2.5.dp.toPx()
                val triSize = if (pair.id == activeSegmentId) 14.dp.toPx() else 10.dp.toPx()

                if (pair.startMs in startMs..endMs) {
                    drawLine(color = color, start = Offset(sx, 0f), end = Offset(sx, size.height), strokeWidth = strokeW)
                    val topPath = Path().apply {
                        moveTo(sx - triSize / 2, 0f)
                        lineTo(sx + triSize / 2, 0f)
                        lineTo(sx, triSize)
                        close()
                    }
                    drawPath(topPath, color = color)
                    val botPath = Path().apply {
                        moveTo(sx - triSize / 2, size.height)
                        lineTo(sx + triSize / 2, size.height)
                        lineTo(sx, size.height - triSize)
                        close()
                    }
                    drawPath(botPath, color = color)
                    // Timestamp label above start handle
                    drawIntoCanvas { canvas ->
                        val label = formatTime(pair.startMs)
                        labelPaint.color = android.graphics.Color.argb(230, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                        val textW = labelPaint.measureText(label)
                        val lx = (sx - textW / 2f).coerceIn(2f, size.width - textW - 2f)
                        val bgPaint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.argb(180, 20, 20, 40)
                            isAntiAlias = true
                        }
                        val pad = 4f
                        canvas.nativeCanvas.drawRoundRect(
                            lx - pad, triSize + 4f, lx + textW + pad, triSize + labelPaint.textSize + 8f,
                            6f, 6f, bgPaint
                        )
                        canvas.nativeCanvas.drawText(label, lx, triSize + labelPaint.textSize + 2f, labelPaint)
                    }
                }
                if (pair.endMs in startMs..endMs) {
                    drawLine(color = color, start = Offset(ex, 0f), end = Offset(ex, size.height), strokeWidth = strokeW)
                    val topPath = Path().apply {
                        moveTo(ex - triSize / 2, 0f)
                        lineTo(ex + triSize / 2, 0f)
                        lineTo(ex, triSize)
                        close()
                    }
                    drawPath(topPath, color = color)
                    val botPath = Path().apply {
                        moveTo(ex - triSize / 2, size.height)
                        lineTo(ex + triSize / 2, size.height)
                        lineTo(ex, size.height - triSize)
                        close()
                    }
                    drawPath(botPath, color = color)
                    // Timestamp label above end handle
                    drawIntoCanvas { canvas ->
                        val label = formatTime(pair.endMs)
                        labelPaint.color = android.graphics.Color.argb(230, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                        val textW = labelPaint.measureText(label)
                        val lx = (ex - textW / 2f).coerceIn(2f, size.width - textW - 2f)
                        val bgPaint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.argb(180, 20, 20, 40)
                            isAntiAlias = true
                        }
                        val pad = 4f
                        canvas.nativeCanvas.drawRoundRect(
                            lx - pad, triSize + 4f, lx + textW + pad, triSize + labelPaint.textSize + 8f,
                            6f, 6f, bgPaint
                        )
                        canvas.nativeCanvas.drawText(label, lx, triSize + labelPaint.textSize + 2f, labelPaint)
                    }
                }
            }

            // Playhead
            val px = msToX(currentPositionMs)
            if (px in 0f..size.width) {
                drawLine(color = Color.White, start = Offset(px, 0f), end = Offset(px, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(px, 8.dp.toPx()))
            }

            // Time labels at edges
            val timeColor = Color.White.copy(alpha = 0.5f)
        }
    }
}
