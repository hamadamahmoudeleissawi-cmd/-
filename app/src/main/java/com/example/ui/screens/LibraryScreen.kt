package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.SavedAudioEntity
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedAudios by viewModel.savedAudios.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFormatFilter by remember { mutableStateOf("الكل") }

    var renamingAudioItem by remember { mutableStateOf<SavedAudioEntity?>(null) }
    var renameTextFieldValue by remember { mutableStateOf("") }

    var deletingAudioItem by remember { mutableStateOf<SavedAudioEntity?>(null) }

    val filteredAudios = savedAudios.filter { item ->
        val matchesSearch = item.title.contains(searchQuery, ignoreCase = true) || item.originalVideoName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = selectedFormatFilter == "الكل" || item.format.equals(selectedFormatFilter, ignoreCase = true)
        matchesSearch && matchesFilter
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("البحث في التسجيلات الصوتية...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_field"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Format Filter Chips
        val formats = listOf("الكل", "MP3", "AAC", "WAV", "FLAC", "M4A")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(formats) { fmt ->
                FilterChip(
                    selected = selectedFormatFilter == fmt,
                    onClick = { selectedFormatFilter = fmt },
                    label = { Text(fmt) },
                    modifier = Modifier.testTag("filter_chip_$fmt")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredAudios.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("empty_library_view"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "لم تقم بفصل أي مقطع صوتي بعد",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "اختر فيديو من الشاشة الرئيسية لاستخراج المقاطع الصوتية وحفظها هنا",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAudios, key = { it.id }) { audio ->
                    AudioItemCard(
                        audio = audio,
                        onPlay = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(audio.fileUriString), "audio/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(audio.fileUriString))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملف الصوتي"))
                        },
                        onRename = {
                            renamingAudioItem = audio
                            renameTextFieldValue = audio.title
                        },
                        onDelete = {
                            deletingAudioItem = audio
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    renamingAudioItem?.let { item ->
        AlertDialog(
            onDismissRequest = { renamingAudioItem = null },
            title = { Text("تعديل اسم الصوت") },
            text = {
                OutlinedTextField(
                    value = renameTextFieldValue,
                    onValueChange = { renameTextFieldValue = it },
                    label = { Text("الاسم الجديد") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_text_field")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameTextFieldValue.isNotBlank()) {
                            viewModel.renameAudioItem(item.id, renameTextFieldValue)
                        }
                        renamingAudioItem = null
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingAudioItem = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Dialog
    deletingAudioItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingAudioItem = null },
            title = { Text("حذف الملف الصوتي") },
            text = { Text("هل أنت تأكد من رغبتك في حذف \"${item.title}\" من المكتبة؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAudioItem(item)
                        deletingAudioItem = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAudioItem = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun AudioItemCard(
    audio: SavedAudioEntity,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_item_card_${audio.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onPlay() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "تشغيل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = audio.format,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val sizeMb = audio.fileSizeByte.toFloat() / (1024 * 1024)
                    val formattedDate = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(audio.createdAt))
                    Text(
                        text = "${formatDuration(audio.durationMs)}  •  ${String.format("%.1f MB", sizeMb)}  •  $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Row {
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل الاسم", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
