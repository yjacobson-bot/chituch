package com.chituch.audioeditor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    val millis = (ms % 1000) / 10
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, millis)
    }
}

fun parseTimeToMs(input: String): Long? {
    val trimmed = input.trim()
    return try {
        val parts = trimmed.split(":")
        when (parts.size) {
            1 -> {
                val secParts = parts[0].split(".")
                val sec = secParts[0].toLong()
                val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                sec * 1000 + ms
            }
            2 -> {
                val min = parts[0].toLong()
                val secParts = parts[1].split(".")
                val sec = secParts[0].toLong()
                val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                min * 60_000 + sec * 1000 + ms
            }
            3 -> {
                val hours = parts[0].toLong()
                val min = parts[1].toLong()
                val secParts = parts[2].split(".")
                val sec = secParts[0].toLong()
                val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                hours * 3_600_000 + min * 60_000 + sec * 1000 + ms
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SegmentTimeInputRow(
    label: String,
    segmentColor: Color,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onStartChanged: (Long) -> Unit,
    onEndChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    var startText by remember { mutableStateOf(formatTime(startMs)) }
    var endText by remember { mutableStateOf(formatTime(endMs)) }

    LaunchedEffect(startMs) { startText = formatTime(startMs) }
    LaunchedEffect(endMs) { endText = formatTime(endMs) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.width(12.dp).padding(end = 4.dp)) {
                drawCircle(color = segmentColor, radius = 6.dp.toPx())
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = segmentColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "זמן התחלה",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        parseTimeToMs(startText)?.let { ms ->
                            onStartChanged(ms.coerceIn(0L, durationMs))
                        } ?: run { startText = formatTime(startMs) }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = segmentColor,
                        unfocusedBorderColor = segmentColor.copy(alpha = 0.5f)
                    ),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "זמן סיום",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        parseTimeToMs(endText)?.let { ms ->
                            onEndChanged(ms.coerceIn(0L, durationMs))
                        } ?: run { endText = formatTime(endMs) }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = segmentColor,
                        unfocusedBorderColor = segmentColor.copy(alpha = 0.5f)
                    ),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
