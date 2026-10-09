package com.chituch.audioeditor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chituch.audioeditor.audio.AudioPlayer
import com.chituch.audioeditor.audio.AudioProcessor
import com.chituch.audioeditor.audio.MediaStoreSaver
import com.chituch.audioeditor.audio.WaveformExtractor
import com.chituch.audioeditor.model.EditMode
import com.chituch.audioeditor.model.ExportSettings
import com.chituch.audioeditor.model.ExportMode
import com.chituch.audioeditor.model.OutputFormat
import com.chituch.audioeditor.model.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SegmentPair(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

data class AudioEditorState(
    val audioUri: Uri? = null,
    val audioFileName: String = "",
    val audioPath: String = "",
    val inputMimeType: String = "",
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val waveformData: FloatArray = FloatArray(0),
    val isLoadingWaveform: Boolean = false,
    val editMode: EditMode = EditMode.KEEP,
    val segmentPairs: List<SegmentPair> = listOf(SegmentPair(0, 0L, 0L)),
    val activeSegmentId: Int = 0,
    val exportSettings: ExportSettings = ExportSettings(),
    val isProcessing: Boolean = false,
    val exportedFiles: List<File> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showExportDialog: Boolean = false,
    val showAudioBrowser: Boolean = false,
    val waveformZoom: Float = 1f,
    val waveformScrollMs: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class AudioEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AudioEditorState())
    val state: StateFlow<AudioEditorState> = _state.asStateFlow()

    private val audioPlayer = AudioPlayer(application)

    // Undo/redo history — stores snapshots of segmentPairs
    private val history = ArrayDeque<List<SegmentPair>>()
    private var historyIndex = -1
    private var lastHistoryPushMs = 0L

    init {
        audioPlayer.setOnProgressChanged { pos ->
            _state.value = _state.value.copy(currentPositionMs = pos)
        }
        audioPlayer.setOnPlaybackComplete {
            _state.value = _state.value.copy(isPlaying = false)
        }
        audioPlayer.setOnDurationReady { dur ->
            val defaultStart = dur / 10L
            val defaultEnd = dur * 9L / 10L
            val defaultPairs = listOf(SegmentPair(0, defaultStart, defaultEnd))
            _state.value = _state.value.copy(
                durationMs = dur,
                segmentPairs = defaultPairs
            )
            pushHistory(defaultPairs)
        }
    }

    fun loadAudio(uri: Uri, fileName: String, filePath: String) {
        viewModelScope.launch {
            history.clear(); historyIndex = -1
            val detectedMime = when {
                filePath.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                filePath.endsWith(".m4a", ignoreCase = true) || filePath.endsWith(".aac", ignoreCase = true) -> "audio/mp4"
                filePath.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                filePath.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
                filePath.endsWith(".flac", ignoreCase = true) -> "audio/flac"
                filePath.endsWith(".opus", ignoreCase = true) -> "audio/opus"
                else -> ""
            }
            _state.value = _state.value.copy(
                audioUri = uri, audioFileName = fileName, audioPath = filePath,
                inputMimeType = detectedMime,
                isLoadingWaveform = true, segmentPairs = listOf(SegmentPair(0, 0L, 0L)),
                exportedFiles = emptyList(), errorMessage = null,
                waveformZoom = 1f, waveformScrollMs = 0L,
                canUndo = false, canRedo = false
            )
            try {
                audioPlayer.loadAudio(uri)
                val waveform = WaveformExtractor.extract(getApplication(), uri, 500)
                _state.value = _state.value.copy(waveformData = waveform, isLoadingWaveform = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingWaveform = false, errorMessage = "שגיאה בטעינת הקובץ: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        if (audioPlayer.isPlaying) {
            audioPlayer.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            audioPlayer.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun previewSegment(id: Int) {
        val pair = _state.value.segmentPairs.find { it.id == id } ?: return
        audioPlayer.playSegment(pair.startMs, pair.endMs)
        _state.value = _state.value.copy(isPlaying = true)
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setSpeed(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun setEditMode(mode: EditMode) {
        _state.value = _state.value.copy(editMode = mode)
    }

    fun updateSegmentStart(id: Int, startMs: Long, commit: Boolean = true) {
        val pairs = _state.value.segmentPairs.map { p ->
            if (p.id == id) p.copy(startMs = startMs.coerceIn(0L, p.endMs - 500L)) else p
        }
        _state.value = _state.value.copy(segmentPairs = pairs)
        if (commit) pushHistory(pairs)
    }

    fun updateSegmentEnd(id: Int, endMs: Long, commit: Boolean = true) {
        val pairs = _state.value.segmentPairs.map { p ->
            if (p.id == id) p.copy(endMs = endMs.coerceIn(p.startMs + 500L, _state.value.durationMs)) else p
        }
        _state.value = _state.value.copy(segmentPairs = pairs)
        if (commit) pushHistory(pairs)
    }

    fun updateSegmentFadeIn(id: Int, fadeMs: Long) {
        val pairs = _state.value.segmentPairs.map { p ->
            if (p.id == id) p.copy(fadeInMs = fadeMs.coerceIn(0L, 10_000L)) else p
        }
        _state.value = _state.value.copy(segmentPairs = pairs)
        pushHistory(pairs)
    }

    fun updateSegmentFadeOut(id: Int, fadeMs: Long) {
        val pairs = _state.value.segmentPairs.map { p ->
            if (p.id == id) p.copy(fadeOutMs = fadeMs.coerceIn(0L, 10_000L)) else p
        }
        _state.value = _state.value.copy(segmentPairs = pairs)
        pushHistory(pairs)
    }

    fun addSegmentPair() {
        val cur = _state.value
        if (cur.durationMs == 0L) return
        val newId = (cur.segmentPairs.maxOfOrNull { it.id } ?: 0) + 1
        val newPairs = cur.segmentPairs + SegmentPair(newId, 0L, cur.durationMs)
        _state.value = cur.copy(segmentPairs = newPairs, activeSegmentId = newId)
        pushHistory(newPairs)
    }

    fun removeSegmentPair(id: Int) {
        val cur = _state.value
        if (cur.segmentPairs.size <= 1) return
        val newPairs = cur.segmentPairs.filter { it.id != id }
        _state.value = cur.copy(segmentPairs = newPairs, activeSegmentId = newPairs.firstOrNull()?.id ?: 0)
        pushHistory(newPairs)
    }

    fun setActiveSegment(id: Int) {
        _state.value = _state.value.copy(activeSegmentId = id)
    }

    // ── Waveform zoom ────────────────────────────────────────────────────────────

    fun setWaveformZoom(zoom: Float) {
        val cur = _state.value
        val newZoom = zoom.coerceIn(1f, 30f)
        val maxScroll = (cur.durationMs - (cur.durationMs / newZoom).toLong()).coerceAtLeast(0L)
        val newScroll = cur.waveformScrollMs.coerceIn(0L, maxScroll)
        _state.value = cur.copy(waveformZoom = newZoom, waveformScrollMs = newScroll)
    }

    fun setWaveformScroll(scrollMs: Long) {
        val cur = _state.value
        val maxScroll = (cur.durationMs - (cur.durationMs / cur.waveformZoom).toLong()).coerceAtLeast(0L)
        _state.value = cur.copy(waveformScrollMs = scrollMs.coerceIn(0L, maxScroll))
    }

    // ── Undo / Redo ──────────────────────────────────────────────────────────────

    private fun pushHistory(pairs: List<SegmentPair>) {
        val now = System.currentTimeMillis()
        // Debounce: if same call within 400ms, replace last entry
        if (now - lastHistoryPushMs < 400 && historyIndex >= 0) {
            history[historyIndex] = pairs
            lastHistoryPushMs = now
            return
        }
        while (history.size > historyIndex + 1 && historyIndex >= 0) history.removeLast()
        history.addLast(pairs)
        if (history.size > 30) history.removeFirst()
        historyIndex = history.size - 1
        lastHistoryPushMs = now
        _state.value = _state.value.copy(canUndo = historyIndex > 0, canRedo = false)
    }

    fun undo() {
        if (historyIndex <= 0) return
        historyIndex--
        _state.value = _state.value.copy(
            segmentPairs = history[historyIndex],
            canUndo = historyIndex > 0,
            canRedo = true
        )
    }

    fun redo() {
        if (historyIndex >= history.size - 1) return
        historyIndex++
        _state.value = _state.value.copy(
            segmentPairs = history[historyIndex],
            canUndo = historyIndex > 0,
            canRedo = historyIndex < history.size - 1
        )
    }

    // ── Export settings ──────────────────────────────────────────────────────────

    fun updateExportSettings(settings: ExportSettings) {
        _state.value = _state.value.copy(exportSettings = settings)
    }

    fun showExportDialog() { _state.value = _state.value.copy(showExportDialog = true) }
    fun hideExportDialog() { _state.value = _state.value.copy(showExportDialog = false) }

    fun openAudioBrowser() { _state.value = _state.value.copy(showAudioBrowser = true) }
    fun closeAudioBrowser() { _state.value = _state.value.copy(showAudioBrowser = false) }

    fun processAndExport() {
        val cur = _state.value
        if (cur.audioPath.isEmpty() || cur.durationMs == 0L) return
        val context = getApplication<Application>()
        val outputDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val segments = cur.segmentPairs.mapIndexed { i, p -> Segment(i, p.startMs, p.endMs, p.fadeInMs, p.fadeOutMs) }

        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, showExportDialog = false, errorMessage = null, successMessage = null)
            try {
                val files = AudioProcessor.processAudio(
                    inputPath = cur.audioPath,
                    segments = segments,
                    editMode = cur.editMode,
                    durationMs = cur.durationMs,
                    settings = cur.exportSettings,
                    outputDir = outputDir,
                    inputMimeType = cur.inputMimeType
                )
                if (files.isEmpty()) {
                    _state.value = _state.value.copy(isProcessing = false, errorMessage = "שגיאה בעיבוד הקובץ")
                    return@launch
                }

                val savedUris = if (cur.exportSettings.saveToMusicLibrary) {
                    val saveMime = when {
                        cur.exportSettings.outputFormat != OutputFormat.ORIGINAL -> cur.exportSettings.outputFormat.mimeType
                        cur.inputMimeType.contains("wav") -> "audio/wav"
                        else -> "audio/mp4"
                    }
                    files.mapNotNull { f ->
                        MediaStoreSaver.saveToMusicLibrary(context, f, saveMime)
                    }
                } else emptyList()

                _state.value = _state.value.copy(
                    isProcessing = false,
                    exportedFiles = files,
                    successMessage = if (cur.exportSettings.saveToMusicLibrary && savedUris.isNotEmpty())
                        "נשמר בספריית המוזיקה (${files.size} קבצים)" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isProcessing = false, errorMessage = "שגיאה: ${e.message}")
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, successMessage = null)
    }

    fun clearExportedFiles() {
        _state.value = _state.value.copy(exportedFiles = emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
