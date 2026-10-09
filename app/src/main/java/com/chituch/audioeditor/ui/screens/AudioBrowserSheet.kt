package com.chituch.audioeditor.ui.screens

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chituch.audioeditor.ui.components.formatTime

data class AudioFile(
    val uri: Uri,
    val displayName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val filePath: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBrowserSheet(
    onDismiss: () -> Unit,
    onFileSelected: (uri: Uri, displayName: String, filePath: String) -> Unit
) {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf<List<AudioFile>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) audioFiles = queryAudioFiles(context)
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasPermission = true
            audioFiles = queryAudioFiles(context)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    val filtered = remember(audioFiles, searchQuery) {
        val files = audioFiles ?: return@remember emptyList()
        if (searchQuery.isBlank()) files
        else files.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.9f)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("בחר קובץ שמע", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("חיפוש לפי שם / אמן...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            when {
                !hasPermission -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                            Spacer(Modifier.height(16.dp))
                            Text("נדרשת הרשאה לגישה לקבצי שמע", textAlign = TextAlign.Center, color = Color.Gray)
                        }
                    }
                }
                audioFiles == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF7C4DFF))
                    }
                }
                filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MusicOff, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            Text(if (searchQuery.isBlank()) "לא נמצאו קבצי שמע" else "אין תוצאות לחיפוש", color = Color.Gray)
                        }
                    }
                }
                else -> {
                    Text(
                        "${filtered.size} קבצים",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn {
                        items(filtered, key = { it.uri.toString() }) { file ->
                            AudioFileRow(file) {
                                onFileSelected(file.uri, file.displayName, file.filePath)
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp), thickness = 0.5.dp)
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioFileRow(file: AudioFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.title.ifBlank { file.displayName },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file.artist.isNotBlank() && file.artist != "<unknown>") {
                Text(file.artist, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (file.durationMs > 0L) {
            Text(formatTime(file.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 11.sp)
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
    }
}

private fun queryAudioFiles(context: android.content.Context): List<AudioFile> {
    val files = mutableListOf<AudioFile>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )
    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.DURATION} > 0",
        null,
        "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
    ) ?: return files
    cursor.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            files.add(
                AudioFile(
                    uri = contentUri,
                    displayName = c.getString(nameCol) ?: "",
                    title = c.getString(titleCol) ?: "",
                    artist = c.getString(artistCol) ?: "",
                    durationMs = c.getLong(durCol),
                    filePath = c.getString(dataCol) ?: ""
                )
            )
        }
    }
    return files
}
