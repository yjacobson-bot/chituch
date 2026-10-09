package com.chituch.audioeditor.model

enum class OutputFormat(val label: String, val extension: String, val mimeType: String) {
    AAC_M4A("AAC (M4A)", "m4a", "audio/mp4"),
    WAV("WAV (איכות מלאה)", "wav", "audio/wav")
}

data class ExportSettings(
    val outputFormat: OutputFormat = OutputFormat.AAC_M4A,
    val bitrateKbps: Int = 128,
    val exportMode: ExportMode = ExportMode.MERGE,
    val saveToMusicLibrary: Boolean = false
)

val BITRATE_OPTIONS = listOf(64, 128, 192, 320)
