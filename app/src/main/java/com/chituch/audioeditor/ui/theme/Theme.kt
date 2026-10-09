package com.chituch.audioeditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF6750A4)
val PrimaryContainer = Color(0xFFEADDFF)
val Secondary = Color(0xFF625B71)
val Background = Color(0xFFFFFBFE)
val Surface = Color(0xFFFFFBFE)
val OnPrimary = Color(0xFFFFFFFF)

val SegmentColor1 = Color(0xFF4CAF50)
val SegmentColor2 = Color(0xFF2196F3)
val SegmentColor3 = Color(0xFFFF9800)
val SegmentColor4 = Color(0xFFE91E63)

val segmentColors = listOf(SegmentColor1, SegmentColor2, SegmentColor3, SegmentColor4)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer
)

@Composable
fun ChiTuchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
