package com.chituch.audioeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chituch.audioeditor.model.EditMode
import com.chituch.audioeditor.model.ExportMode
import com.chituch.audioeditor.model.ExportSettings
import com.chituch.audioeditor.model.OutputFormat
import com.chituch.audioeditor.model.BITRATE_OPTIONS
import com.chituch.audioeditor.ui.components.SegmentTimeInputRow
import com.chituch.audioeditor.ui.components.WaveformView
import com.chituch.audioeditor.ui.components.formatTime
import com.chituch.audioeditor.ui.theme.segmentColors
import com.chituch.audioeditor.viewmodel.AudioEditorViewModel
import java.io.File

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorScreen(vm: AudioEditorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbarHostState.showSnackbar(it); vm.clearMessages() } }
    LaunchedEffect(state.successMessage) { state.successMessage?.let { snackbarHostState.showSnackbar(it); vm.clearMessages() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

            // ── Header ────────────────────────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("חיתוכצ'יק", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (state.audioUri != null) {
                        Row {
                            IconButton(onClick = vm::undo, enabled = state.canUndo) {
                                Icon(Icons.Default.Undo, "בטל", tint = if (state.canUndo) Color.White else Color.White.copy(alpha = 0.3f))
                            }
                            IconButton(onClick = vm::redo, enabled = state.canRedo) {
                                Icon(Icons.Default.Redo, "חזור", tint = if (state.canRedo) Color.White else Color.White.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {

                // ── File picker ───────────────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    if (state.audioUri == null) {
                        // Empty state — big tap target to open in-app browser
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { vm.openAudioBrowser() }.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("בחר שיר", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("לחץ לפתיחת ספריית השירים", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            Text("MP3 · AAC · FLAC · WAV ועוד", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(state.audioFileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("משך: ${formatTime(state.durationMs)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            TextButton(onClick = { vm.openAudioBrowser() }) {
                                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("החלף")
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = state.audioUri != null) {
                    Column {
                        Spacer(Modifier.height(16.dp))

                        // ── Waveform + controls ───────────────────────────────────
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column {
                                // Taller waveform (200dp)
                                if (state.isLoadingWaveform) {
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF7C4DFF))
                                    }
                                } else {
                                    WaveformView(
                                        waveformData = state.waveformData,
                                        durationMs = state.durationMs,
                                        currentPositionMs = state.currentPositionMs,
                                        segmentPairs = state.segmentPairs,
                                        activeSegmentId = state.activeSegmentId,
                                        zoomLevel = state.waveformZoom,
                                        scrollOffsetMs = state.waveformScrollMs,
                                        onSeek = vm::seekTo,
                                        onSegmentStartChanged = { id, ms, dragging -> vm.updateSegmentStart(id, ms, !dragging) },
                                        onSegmentEndChanged = { id, ms, dragging -> vm.updateSegmentEnd(id, ms, !dragging) },
                                        onZoomChanged = vm::setWaveformZoom,
                                        onScrollChanged = vm::setWaveformScroll,
                                        onZoomToSegment = { startMs, endMs -> vm.zoomToSegment(startMs, endMs) },
                                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    )
                                }

                                // ── Quick time inputs directly below waveform ─────
                                val activePair = state.segmentPairs.find { it.id == state.activeSegmentId }
                                if (activePair != null && !state.isLoadingWaveform) {
                                    val activeColor = segmentColors[state.segmentPairs.indexOfFirst { it.id == state.activeSegmentId }.coerceAtLeast(0) % segmentColors.size]
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E32)).padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Colored dot
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(activeColor))
                                        Spacer(Modifier.width(6.dp))
                                        SegmentTimeInputRow(
                                            label = "",
                                            segmentColor = activeColor,
                                            startMs = activePair.startMs,
                                            endMs = activePair.endMs,
                                            durationMs = state.durationMs,
                                            onStartChanged = { ms -> vm.updateSegmentStart(activePair.id, ms, true) },
                                            onEndChanged = { ms -> vm.updateSegmentEnd(activePair.id, ms, true) },
                                            compact = true
                                        )
                                    }
                                }

                                // Zoom controls
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(formatTime(state.waveformScrollMs), color = Color(0xFF90A4AE), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { vm.setWaveformZoom(state.waveformZoom / 1.5f) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.ZoomOut, null, tint = Color(0xFF90A4AE), modifier = Modifier.size(16.dp))
                                        }
                                        Text("${state.waveformZoom.toInt()}×", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                                        IconButton(onClick = { vm.setWaveformZoom(state.waveformZoom * 1.5f) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.ZoomIn, null, tint = Color(0xFF90A4AE), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        formatTime((state.waveformScrollMs + state.durationMs / state.waveformZoom).toLong().coerceAtMost(state.durationMs)),
                                        color = Color(0xFF90A4AE), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp
                                    )
                                }

                                // Player controls
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(formatTime(state.currentPositionMs), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF7C4DFF)).clickable { vm.togglePlayPause() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                    Text(formatTime(state.durationMs), color = Color(0xFF90A4AE), style = MaterialTheme.typography.bodySmall)
                                }

                                // Speed selector
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF12121E)).padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("מהירות:", color = Color(0xFF90A4AE), fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                                    SPEED_OPTIONS.forEach { speed ->
                                        val selected = state.playbackSpeed == speed
                                        Surface(
                                            modifier = Modifier.padding(horizontal = 3.dp).clickable { vm.setPlaybackSpeed(speed) },
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (selected) Color(0xFF7C4DFF) else Color(0xFF2A2A3E),
                                            border = if (selected) null else BorderStroke(1.dp, Color(0xFF3A3A4E))
                                        ) {
                                            Text(
                                                "${if (speed == speed.toLong().toFloat()) speed.toLong() else speed}×",
                                                color = if (selected) Color.White else Color(0xFF90A4AE),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Edit Mode ─────────────────────────────────────────────
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("מצב עריכה", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModeChip("שמור קטעים", "בחר קטעים לשמירה", state.editMode == EditMode.KEEP, { vm.setEditMode(EditMode.KEEP) }, Modifier.weight(1f))
                                    ModeChip("הסר קטעים", "בחר קטעים למחיקה", state.editMode == EditMode.REMOVE, { vm.setEditMode(EditMode.REMOVE) }, Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Segments ──────────────────────────────────────────────
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (state.editMode == EditMode.KEEP) "קטעים לשמירה" else "קטעים להסרה",
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (state.segmentPairs.size < 4) {
                                        FilledTonalButton(onClick = vm::addSegmentPair, modifier = Modifier.height(32.dp)) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp)); Text("הוסף קטע", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))

                                state.segmentPairs.forEachIndexed { idx, pair ->
                                    val color = segmentColors[idx % segmentColors.size]
                                    val isActive = pair.id == state.activeSegmentId
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { vm.setActiveSegment(pair.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isActive) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (isActive) BorderStroke(1.5.dp, color.copy(alpha = 0.5f)) else null
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("קטע ${idx + 1}", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(formatTime(pair.endMs - pair.startMs), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { vm.previewSegment(pair.id) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.PlayCircle, "נגן קטע", tint = color, modifier = Modifier.size(20.dp))
                                                    }
                                                    val isLooping = state.loopingSegmentId == pair.id
                                                    IconButton(onClick = { vm.toggleLoopSegment(pair.id) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(
                                                            if (isLooping) Icons.Default.Repeat else Icons.Default.RepeatOne,
                                                            "לולאה",
                                                            tint = if (isLooping) color else Color.Gray,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    if (state.segmentPairs.size > 1) {
                                                        IconButton(onClick = { vm.removeSegmentPair(pair.id) }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.Default.Delete, "הסר", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(10.dp))
                                            SegmentTimeInputRow(
                                                label = "קטע ${idx + 1}",
                                                segmentColor = color,
                                                startMs = pair.startMs, endMs = pair.endMs,
                                                durationMs = state.durationMs,
                                                onStartChanged = { ms -> vm.updateSegmentStart(pair.id, ms, true) },
                                                onEndChanged = { ms -> vm.updateSegmentEnd(pair.id, ms, true) }
                                            )

                                            Spacer(Modifier.height(10.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                FadeSlider("Fade In", pair.fadeInMs, color, Modifier.weight(1f)) { vm.updateSegmentFadeIn(pair.id, it) }
                                                FadeSlider("Fade Out", pair.fadeOutMs, color, Modifier.weight(1f)) { vm.updateSegmentFadeOut(pair.id, it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Export button ─────────────────────────────────────────
                        Button(
                            onClick = vm::showExportDialog,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.isProcessing && state.durationMs > 0L
                        ) {
                            if (state.isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp)); Text("מעבד...")
                            } else {
                                Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(8.dp))
                                Text("ייצא", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        // ── Exported files ────────────────────────────────────────
                        AnimatedVisibility(visible = state.exportedFiles.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("קבצים מוכנים (${state.exportedFiles.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        IconButton(onClick = vm::clearExportedFiles, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    state.exportedFiles.forEach { file ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.name, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                                                Text(formatFileSize(file.length()), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                                            }
                                            IconButton(onClick = { shareFile(context, file) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Share, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        // ── Export dialog ─────────────────────────────────────────────────────────
        if (state.showExportDialog) {
            ExportDialog(
                settings = state.exportSettings,
                onSettingsChanged = vm::updateExportSettings,
                onConfirm = vm::processAndExport,
                onDismiss = vm::hideExportDialog
            )
        }

        // ── In-app audio browser ──────────────────────────────────────────────────
        if (state.showAudioBrowser) {
            AudioBrowserSheet(
                onDismiss = vm::closeAudioBrowser,
                onFileSelected = { uri, displayName, filePath ->
                    try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    val resolvedPath = filePath.ifEmpty { getRealPath(context, uri) ?: copyToCache(context, uri, displayName)?.absolutePath ?: "" }
                    vm.loadAudio(uri, displayName, resolvedPath)
                    vm.closeAudioBrowser()
                }
            )
        }
    }
}

// ── Export dialog ─────────────────────────────────────────────────────────────────

@Composable
private fun ExportDialog(
    settings: ExportSettings,
    onSettingsChanged: (ExportSettings) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הגדרות ייצוא") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("אופן ייצוא", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportModeChip("קובץ אחד", settings.exportMode == ExportMode.MERGE, Modifier.weight(1f)) {
                        onSettingsChanged(settings.copy(exportMode = ExportMode.MERGE))
                    }
                    ExportModeChip("קבצים נפרדים", settings.exportMode == ExportMode.SEPARATE, Modifier.weight(1f)) {
                        onSettingsChanged(settings.copy(exportMode = ExportMode.SEPARATE))
                    }
                }

                HorizontalDivider()

                Text("פורמט", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutputFormat.entries.forEach { fmt ->
                        ExportModeChip(fmt.label, settings.outputFormat == fmt, Modifier.weight(1f)) {
                            onSettingsChanged(settings.copy(outputFormat = fmt))
                        }
                    }
                }

                if (settings.outputFormat == OutputFormat.AAC_M4A) {
                    Text("איכות (kbps)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BITRATE_OPTIONS.forEach { br ->
                            ExportModeChip("$br", settings.bitrateKbps == br, Modifier.weight(1f)) {
                                onSettingsChanged(settings.copy(bitrateKbps = br))
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSettingsChanged(settings.copy(saveToMusicLibrary = !settings.saveToMusicLibrary)) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("שמור בספריית מוזיקה", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Music/חיתוך/", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = settings.saveToMusicLibrary, onCheckedChange = { onSettingsChanged(settings.copy(saveToMusicLibrary = it)) })
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("ייצא") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

// ── Small reusable composables ────────────────────────────────────────────────────

@Composable
private fun ModeChip(text: String, desc: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ExportModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Text(
            text, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FadeSlider(label: String, valueMs: Long, color: Color, modifier: Modifier = Modifier, onChange: (Long) -> Unit) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
            Text("${valueMs / 1000}.${(valueMs % 1000) / 100}s", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
        }
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { onChange(it.toLong()) },
            valueRange = 0f..5000f,
            steps = 49,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────────

private fun getRealPath(context: android.content.Context, uri: Uri): String? = try {
    if (uri.scheme == "file") uri.path
    else {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val resolved = java.io.File("/proc/self/fd/${fd.fd}").canonicalPath
        fd.close()
        if (resolved.startsWith("/")) resolved else null
    }
} catch (_: Exception) { null }

private fun copyToCache(context: android.content.Context, uri: Uri, name: String): File? = try {
    val dest = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
    dest
} catch (_: Exception) { null }

private fun shareFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "שתף קובץ שמע"))
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
