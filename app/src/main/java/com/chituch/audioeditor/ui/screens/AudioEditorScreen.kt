package com.chituch.audioeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.chituch.audioeditor.ui.components.SegmentTimeInputRow
import com.chituch.audioeditor.ui.components.WaveformView
import com.chituch.audioeditor.ui.components.formatTime
import com.chituch.audioeditor.ui.theme.segmentColors
import com.chituch.audioeditor.viewmodel.AudioEditorViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorScreen(vm: AudioEditorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else "audio"
            } ?: "audio"

            val filePath = getRealPathFromUri(context, it) ?: run {
                val cacheFile = copyUriToCache(context, it, fileName)
                cacheFile?.absolutePath ?: ""
            }

            vm.loadAudio(it, fileName, filePath)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "חיתוך שמע",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {

                // File picker section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.audioUri == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { filePicker.launch(arrayOf("audio/*")) }
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "לחץ לבחירת קובץ שמע",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "MP3, WAV, AAC, FLAC ועוד",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AudioFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            state.audioFileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            "משך: ${formatTime(state.durationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                TextButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("החלף")
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = state.audioUri != null) {
                    Column {
                        Spacer(Modifier.height(16.dp))

                        // Waveform
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(0.dp)) {
                                if (state.isLoadingWaveform) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(Color(0xFF1A1A2E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF7C4DFF))
                                    }
                                } else {
                                    WaveformView(
                                        waveformData = state.waveformData,
                                        durationMs = state.durationMs,
                                        currentPositionMs = state.currentPositionMs,
                                        segmentPairs = state.segmentPairs,
                                        activeSegmentId = state.activeSegmentId,
                                        onSeek = vm::seekTo,
                                        onSegmentStartChanged = vm::updateSegmentStart,
                                        onSegmentEndChanged = vm::updateSegmentEnd,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    )
                                }

                                // Player controls
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1A1A2E))
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        formatTime(state.currentPositionMs),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF7C4DFF))
                                            .clickable { vm.togglePlayPause() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (state.isPlaying) "עצור" else "נגן",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Text(
                                        formatTime(state.durationMs),
                                        color = Color(0xFF90A4AE),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Edit Mode selector
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "מצב עריכה",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModeButton(
                                        text = "שמור קטעים",
                                        description = "בחר קטעים לשמירה",
                                        selected = state.editMode == EditMode.KEEP,
                                        onClick = { vm.setEditMode(EditMode.KEEP) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ModeButton(
                                        text = "הסר קטעים",
                                        description = "בחר קטעים למחיקה",
                                        selected = state.editMode == EditMode.REMOVE,
                                        onClick = { vm.setEditMode(EditMode.REMOVE) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Segments
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (state.editMode == EditMode.KEEP) "קטעים לשמירה" else "קטעים להסרה",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (state.segmentPairs.size < 4) {
                                        FilledTonalButton(
                                            onClick = vm::addSegmentPair,
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("הוסף קטע", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))

                                state.segmentPairs.forEachIndexed { idx, pair ->
                                    val color = segmentColors[idx % segmentColors.size]
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .clickable { vm.setActiveSegment(pair.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (pair.id == state.activeSegmentId)
                                            color.copy(alpha = 0.08f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        tonalElevation = if (pair.id == state.activeSegmentId) 2.dp else 0.dp
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "קטע ${idx + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = color,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (state.segmentPairs.size > 1) {
                                                    IconButton(
                                                        onClick = { vm.removeSegmentPair(pair.id) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "הסר",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            SegmentTimeInputRow(
                                                label = "קטע ${idx + 1}",
                                                segmentColor = color,
                                                startMs = pair.startMs,
                                                endMs = pair.endMs,
                                                durationMs = state.durationMs,
                                                onStartChanged = { ms -> vm.updateSegmentStart(pair.id, ms) },
                                                onEndChanged = { ms -> vm.updateSegmentEnd(pair.id, ms) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Export button
                        Button(
                            onClick = vm::showExportDialog,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.isProcessing && state.durationMs > 0L
                        ) {
                            if (state.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("מעבד...")
                            } else {
                                Icon(Icons.Default.ContentCut, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("ייצא", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        // Exported files
                        AnimatedVisibility(visible = state.exportedFiles.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "קבצים מיוצאים (${state.exportedFiles.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        IconButton(
                                            onClick = vm::clearExportedFiles,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "סגור", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    state.exportedFiles.forEach { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF1B5E20),
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { shareFile(context, file) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = "שתף",
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Export dialog
        if (state.showExportDialog) {
            AlertDialog(
                onDismissRequest = vm::hideExportDialog,
                title = { Text("אפשרויות ייצוא") },
                text = {
                    Column {
                        Text("כיצד לייצא את הקטעים?", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setExportMode(ExportMode.MERGE) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.exportMode == ExportMode.MERGE,
                                onClick = { vm.setExportMode(ExportMode.MERGE) }
                            )
                            Column {
                                Text("קובץ אחד מחובר", fontWeight = FontWeight.SemiBold)
                                Text("כל הקטעים יחוברו לקובץ אחד", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setExportMode(ExportMode.SEPARATE) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.exportMode == ExportMode.SEPARATE,
                                onClick = { vm.setExportMode(ExportMode.SEPARATE) }
                            )
                            Column {
                                Text("קבצים נפרדים", fontWeight = FontWeight.SemiBold)
                                Text("כל קטע יישמר בקובץ נפרד", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = vm::processAndExport) {
                        Text("ייצא")
                    }
                },
                dismissButton = {
                    TextButton(onClick = vm::hideExportDialog) {
                        Text("ביטול")
                    }
                }
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun getRealPathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        if (uri.scheme == "file") return uri.path
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        val path = "/proc/self/fd/${fd.fd}"
        val resolved = java.io.File(path).canonicalPath
        fd.close()
        if (resolved != path) resolved else null
    } catch (e: Exception) {
        null
    }
}

private fun copyUriToCache(context: android.content.Context, uri: Uri, fileName: String): File? {
    return try {
        val cacheDir = context.cacheDir
        val dest = File(cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest
    } catch (e: Exception) {
        null
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "שתף קובץ שמע"))
}
