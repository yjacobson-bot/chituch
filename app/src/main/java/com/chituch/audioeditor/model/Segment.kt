package com.chituch.audioeditor.model

data class Segment(
    val id: Int,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = endMs - startMs

    fun isValid(): Boolean = startMs >= 0 && endMs > startMs
}

enum class EditMode {
    KEEP,   // Keep selected segments
    REMOVE  // Remove selected segments
}

enum class ExportMode {
    MERGE,    // Merge all segments into one file
    SEPARATE  // Export each segment as separate file
}
