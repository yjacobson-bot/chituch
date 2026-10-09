package com.chituch.audioeditor.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chituch.audioeditor.audio.AudioPlayer
import com.chituch.audioeditor.audio.AudioProcessor
import com.chituch.audioeditor.audio.WaveformExtractor
import com.chituch.audioeditor.model.EditMode
import com.chituch.audioeditor.model.ExportMode
import com.chituch.audioeditor.model.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SegmentPair(
    val id: Int,
    val startMs: Long,
    val endMs: Long
)

data class AudioEditorState(
    val audioUri: Uri? = null,
    val audioFileName: String = "",
    val audioPath: String = "",
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val waveformData: FloatArray = FloatArray(0),
    val isLoadingWaveform: Boolean = false,
    val editMode: EditMode = EditMode.KEEP,
    val segmentPairs: List<SegmentPair> = listOf(SegmentPair(0, 0L, 0L)),
    val activeSegmentId: Int = 0,
    val exportMode: ExportMode = ExportMode.MERGE,
    val isProcessing: Boolean = false,
    val exportedFiles: List<File> = emptyList(),
    val errorMessage: String? = null,
    val showExportDialog: Boolean = false
)

class AudioEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AudioEditorState())
    val state: StateFlow<AudioEditorState> = _state.asStateFlow()

    private val audioPlayer = AudioPlayer(application)

    init {
        audioPlayer.setOnProgressChanged { pos ->
            _state.value = _state.value.copy(currentPositionMs = pos)
        }
        audioPlayer.setOnPlaybackComplete {
            _state.value = _state.value.copy(isPlaying = false)
        }
        audioPlayer.setOnDurationReady { dur ->
            _state.value = _state.value.copy(
                durationMs = dur,
                segmentPairs = listOf(SegmentPair(0, 0L, dur))
            )
        }
    }

    fun loadAudio(uri: Uri, fileName: String, filePath: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                audioUri = uri,
                audioFileName = fileName,
                audioPath = filePath,
                isLoadingWaveform = true,
                segmentPairs = listOf(SegmentPair(0, 0L, 0L)),
                exportedFiles = emptyList(),
                errorMessage = null
            )
            try {
                audioPlayer.loadAudio(uri)
                val waveform = WaveformExtractor.extract(getApplication(), uri, 500)
                _state.value = _state.value.copy(
                    waveformData = waveform,
                    isLoadingWaveform = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingWaveform = false,
                    errorMessage = "שגיאה בטעינת הקובץ: ${e.message}"
                )
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

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    fun setEditMode(mode: EditMode) {
        _state.value = _state.value.copy(editMode = mode)
    }

    fun updateSegmentStart(id: Int, startMs: Long) {
        val pairs = _state.value.segmentPairs.map { p ->
            if (p.id == id) p.copy(startMs = startMs.coerceIn(0L, p.endMs - 1000L)) else p
        }
        _state.value = _state.value.copy(segmentPairs = pairs)
    }

    fun updateSegmentEnd(id: Int, endMs: Long) {
        val pairs = _state.value.segmentPairs.map { p ->
            if (p.id == id) p.copy(endMs = endMs.coerceIn(p.startMs + 1000L, _state.value.durationMs)) else p
        }
        _state.value = _state.value.copy(segmentPairs = pairs)
    }

    fun addSegmentPair() {
        val current = _state.value
        if (current.durationMs == 0L) return
        val newId = (current.segmentPairs.maxOfOrNull { it.id } ?: 0) + 1
        val newPair = SegmentPair(newId, 0L, current.durationMs)
        _state.value = current.copy(
            segmentPairs = current.segmentPairs + newPair,
            activeSegmentId = newId
        )
    }

    fun removeSegmentPair(id: Int) {
        val current = _state.value
        if (current.segmentPairs.size <= 1) return
        val newPairs = current.segmentPairs.filter { it.id != id }
        _state.value = current.copy(
            segmentPairs = newPairs,
            activeSegmentId = newPairs.firstOrNull()?.id ?: 0
        )
    }

    fun setActiveSegment(id: Int) {
        _state.value = _state.value.copy(activeSegmentId = id)
    }

    fun setExportMode(mode: ExportMode) {
        _state.value = _state.value.copy(exportMode = mode)
    }

    fun showExportDialog() {
        _state.value = _state.value.copy(showExportDialog = true)
    }

    fun hideExportDialog() {
        _state.value = _state.value.copy(showExportDialog = false)
    }

    fun processAndExport() {
        val current = _state.value
        if (current.audioPath.isEmpty() || current.durationMs == 0L) return

        val context = getApplication<Application>()
        val outputDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }

        val segments = current.segmentPairs.mapIndexed { idx, p ->
            Segment(idx, p.startMs, p.endMs)
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, showExportDialog = false, errorMessage = null)
            try {
                val files = AudioProcessor.processAudio(
                    context = context,
                    inputPath = current.audioPath,
                    segments = segments,
                    editMode = current.editMode,
                    durationMs = current.durationMs,
                    exportSeparate = current.exportMode == ExportMode.SEPARATE,
                    outputDir = outputDir
                )
                _state.value = _state.value.copy(
                    isProcessing = false,
                    exportedFiles = files,
                    errorMessage = if (files.isEmpty()) "שגיאה בעיבוד הקובץ" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = "שגיאה: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearExportedFiles() {
        _state.value = _state.value.copy(exportedFiles = emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
