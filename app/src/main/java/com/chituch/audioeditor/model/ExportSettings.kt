package com.chituch.audioeditor.model

enum class OutputFormat(val label: String, val extension: String, val mimeType: String) {
    ORIGINAL("פורמט מקורי", "", ""),   // extension/mime resolved at runtime from input
    AAC_M4A("AAC (M4A)", "m4a", "audio/mp4"),
    WAV("WAV (איכות מלאה)", "wav", "audio/wav")
}

data class ExportSettings(
    val outputFormat: OutputFormat = OutputFormat.ORIGINAL,
    val bitrateKbps: Int = 192,
    val exportMode: ExportMode = ExportMode.MERGE
)

val BITRATE_OPTIONS = listOf(64, 128, 192, 320)
