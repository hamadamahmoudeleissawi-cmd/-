package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AudioEditMode
import com.example.data.model.AudioOutputFormat
import com.example.data.model.ProcessingState
import com.example.ui.MainViewModel
import com.example.ui.components.AudioPlayerControls
import com.example.ui.components.WaveformView

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtractorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val videoMetadata by viewModel.videoMetadata.collectAsStateWithLifecycle()
    val pcmData by viewModel.pcmData.collectAsStateWithLifecycle()
    val editedPcmData by viewModel.editedPcmData.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()

    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val startMs by viewModel.startMs.collectAsStateWithLifecycle()
    val endMs by viewModel.endMs.collectAsStateWithLifecycle()
    val fadeInMs by viewModel.fadeInMs.collectAsStateWithLifecycle()
    val fadeOutMs by viewModel.fadeOutMs.collectAsStateWithLifecycle()
    val volumeMultiplier by viewModel.volumeMultiplier.collectAsStateWithLifecycle()

    val customOutputName by viewModel.customOutputName.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val selectedBitrateKbps by viewModel.selectedBitrateKbps.collectAsStateWithLifecycle()
    val customFolderName by viewModel.customFolderName.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPlaybackMs by viewModel.currentPlaybackMs.collectAsStateWithLifecycle()
    val isLooping by viewModel.isLooping.collectAsStateWithLifecycle()

    // Activity Result Launchers
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (_: Exception) {}
            viewModel.selectVideo(it)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (_: Exception) {}
            viewModel.selectVideo(it)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (_: Exception) {}
            viewModel.setCustomFolder(it, it.lastPathSegment ?: "مجلد مخصص")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Pick Video Header Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_video_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "فصل وتعديل الصوت من الفيديو",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "قم باختيار ملف فيديو من هاتفك لاستخراج وتعديل الصوت بسهولة",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                openDocumentLauncher.launch(arrayOf("video/*"))
                            } catch (_: Exception) {
                                videoPickerLauncher.launch("video/*")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("select_video_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VideoFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "اختر فيديو")
                    }

                    OutlinedButton(
                        onClick = { viewModel.loadDemoVideo() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("demo_video_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "تجربة توضيحية", fontSize = 13.sp)
                    }
                }
            }
        }

        // 2. Video Info & Format Metadata Card
        videoMetadata?.let { meta ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_info_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meta.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "المدة: ${formatDuration(meta.durationMs)}  |  الدقة: ${meta.resolution}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 3. Audio Waveform Editor & Trimming Controls
        pcmData?.let { pcm ->
            Text(
                text = "محرر ومقص الصوت",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Mode Selector: Trim vs Cut Out
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = editMode == AudioEditMode.TRIM,
                    onClick = { viewModel.updateEditMode(AudioEditMode.TRIM) },
                    label = { Text("قص والتحديد (الإبقاء)") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trim_mode_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                FilterChip(
                    selected = editMode == AudioEditMode.CUT_OUT,
                    onClick = { viewModel.updateEditMode(AudioEditMode.CUT_OUT) },
                    label = { Text("حذف الجزء المحدد") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cut_out_mode_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.error,
                        selectedLabelColor = MaterialTheme.colorScheme.onError
                    )
                )
            }

            // Waveform Canvas
            WaveformView(
                waveformAmplitudes = editedPcmData?.waveformAmplitudes ?: pcm.waveformAmplitudes,
                totalDurationMs = pcm.originalDurationMs,
                startMs = startMs,
                endMs = endMs,
                currentPlaybackMs = currentPlaybackMs,
                editMode = editMode,
                onRangeChanged = { nStart, nEnd -> viewModel.updateTrimRange(nStart, nEnd) },
                onSeekTo = { targetMs -> viewModel.seekPlaybackTo(targetMs) }
            )

            // Player Preview Controls
            AudioPlayerControls(
                isPlaying = isPlaying,
                onPlayPauseToggle = { viewModel.togglePlayback() },
                onRewind5s = { viewModel.seekPlaybackTo((currentPlaybackMs - 5000L).coerceAtLeast(0L)) },
                onForward5s = { viewModel.seekPlaybackTo((currentPlaybackMs + 5000L).coerceAtMost(pcm.originalDurationMs)) },
                isLooping = isLooping,
                onLoopToggle = { viewModel.isLooping.value = it },
                volumeMultiplier = volumeMultiplier,
                onVolumeChange = { viewModel.updateVolume(it) },
                fadeInMs = fadeInMs,
                onFadeInChange = { viewModel.updateFadeIn(it) },
                fadeOutMs = fadeOutMs,
                onFadeOutChange = { viewModel.updateFadeOut(it) }
            )

            // 4. Output Settings & Options Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_options_card"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "إعدادات حفظ الملف والصيغ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Output Name Input
                    OutlinedTextField(
                        value = customOutputName,
                        onValueChange = { viewModel.customOutputName.value = it },
                        label = { Text("اسم الصوت المحفوظ") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("output_name_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Format Selection Chips
                    Column {
                        Text(
                            text = "اختيار صيغة الصوت الممتازة:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioOutputFormat.values().forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { viewModel.selectedFormat.value = fmt },
                                    label = { Text(fmt.extension.uppercase()) },
                                    leadingIcon = if (selectedFormat == fmt) {
                                        { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("format_chip_${fmt.extension}")
                                )
                            }
                        }
                    }

                    // Bitrate / Quality Selection
                    if (selectedFormat == AudioOutputFormat.MP3 || selectedFormat == AudioOutputFormat.AAC) {
                        Column {
                            Text(
                                text = "معدل البت / الجودة الصوتية (Bitrate):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(128, 192, 256, 320).forEach { bitrate ->
                                    FilterChip(
                                        selected = selectedBitrateKbps == bitrate,
                                        onClick = { viewModel.selectedBitrateKbps.value = bitrate },
                                        label = { Text("${bitrate}k") },
                                        modifier = Modifier.weight(1f).testTag("bitrate_chip_$bitrate")
                                    )
                                }
                            }
                        }
                    }

                    // Destination Save Location Folder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "موقع حفظ الملف:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = customFolderName ?: "مجلد الموسيقى",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.testTag("select_folder_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = "تغيير المجلد", fontSize = 12.sp)
                        }
                    }

                    // Main Action Button: Extract & Save Audio
                    Button(
                        onClick = { viewModel.exportAudio() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("extract_and_save_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "فصل الصوت وحفظه الآن",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Loading or Success Processing Dialogs
        when (val state = processingState) {
            is ProcessingState.LoadingVideo, is ProcessingState.ExtractingAudio, is ProcessingState.Exporting -> {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = {
                        Text(
                            text = when (state) {
                                is ProcessingState.LoadingVideo -> "جاري التحميل..."
                                is ProcessingState.ExtractingAudio -> "جاري فصل الصوت..."
                                else -> "جاري الترميز والحفظ..."
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val prog = when (state) {
                                is ProcessingState.LoadingVideo -> state.progress
                                is ProcessingState.ExtractingAudio -> state.progress
                                is ProcessingState.Exporting -> state.progress
                                else -> 0.5f
                            }
                            LinearProgressIndicator(
                                progress = { prog },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (state) {
                                    is ProcessingState.ExtractingAudio -> state.message
                                    is ProcessingState.Exporting -> state.message
                                    else -> "يرجى الانتظار لحين اكتمال العملية..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            is ProcessingState.Success -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = { Text(text = "تم حفظ الصوت بنجاح! 🎉", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "تم حفظ الملف باسم:\n${state.savedTitle}\n\nمسار الملف:\n${state.savedFilePath}",
                            textAlign = TextAlign.Center
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.testTag("success_dialog_ok_button")
                        ) {
                            Text(text = "رائع، شكراً")
                        }
                    }
                )
            }
            is ProcessingState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    title = { Text(text = "تنبيه خطأ") },
                    text = { Text(text = state.message) },
                    confirmButton = {
                        Button(onClick = { viewModel.resetState() }) {
                            Text(text = "موافق")
                        }
                    }
                )
            }
            ProcessingState.Idle -> {}
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
