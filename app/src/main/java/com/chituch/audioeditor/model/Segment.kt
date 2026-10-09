package com.chituch.audioeditor.model

data class Segment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    val durationMs: Long get() = endMs - startMs
    fun isValid(): Boolean = startMs >= 0 && endMs > startMs
}

enum class EditMode {
    KEEP,
    REMOVE
}

enum class ExportMode {
    MERGE,
    SEPARATE
}
