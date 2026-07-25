package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioProcessor
import com.example.audio.SampleMediaGenerator
import com.example.data.database.AppDatabase
import com.example.data.database.SavedAudioEntity
import com.example.data.model.AudioEditMode
import com.example.data.model.AudioOutputFormat
import com.example.data.model.AudioPcmData
import com.example.data.model.ProcessingState
import com.example.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.savedAudioDao()

    val savedAudios: StateFlow<List<SavedAudioEntity>> = dao.getAllSavedAudios()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI States
    private val _videoMetadata = MutableStateFlow<VideoMetadata?>(null)
    val videoMetadata: StateFlow<VideoMetadata?> = _videoMetadata.asStateFlow()

    private val _pcmData = MutableStateFlow<AudioPcmData?>(null)
    val pcmData: StateFlow<AudioPcmData?> = _pcmData.asStateFlow()

    private val _editedPcmData = MutableStateFlow<AudioPcmData?>(null)
    val editedPcmData: StateFlow<AudioPcmData?> = _editedPcmData.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    // Editing Parameters
    val editMode = MutableStateFlow(AudioEditMode.TRIM)
    val startMs = MutableStateFlow(0L)
    val endMs = MutableStateFlow(0L)
    val fadeInMs = MutableStateFlow(0)
    val fadeOutMs = MutableStateFlow(0)
    val volumeMultiplier = MutableStateFlow(1.0f)

    // Export Options
    val customOutputName = MutableStateFlow("")
    val selectedFormat = MutableStateFlow(AudioOutputFormat.MP3)
    val selectedBitrateKbps = MutableStateFlow(192)
    val customFolderUri = MutableStateFlow<Uri?>(null)
    val customFolderName = MutableStateFlow<String?>("مجلد الموسيقى الافتراضي")

    // Playback State
    val isPlaying = MutableStateFlow(false)
    val currentPlaybackMs = MutableStateFlow(0L)
    val isLooping = MutableStateFlow(false)

    private var audioTrackPlayer: AudioTrack? = null
    private var playbackJob: Job? = null

    fun selectVideo(uri: Uri) {
        viewModelScope.launch {
            stopPlayback()
            _processingState.value = ProcessingState.LoadingVideo(0.1f)
            try {
                val context = getApplication<Application>()
                val meta = AudioProcessor.getVideoMetadata(context, uri)
                _videoMetadata.value = meta
                customOutputName.value = meta.title.substringBeforeLast(".") + "_صوت"

                _processingState.value = ProcessingState.ExtractingAudio(0.2f, "جاري استخراج الصوت من الفيديو...")

                val extractedPcm = AudioProcessor.extractPcmFromVideo(context, uri) { prog ->
                    _processingState.value = ProcessingState.ExtractingAudio(0.2f + 0.7f * prog, "جاري فك ضغط الصوت...")
                }

                _pcmData.value = extractedPcm
                startMs.value = 0L
                endMs.value = extractedPcm.originalDurationMs
                
                applyCurrentEdits()

                _processingState.value = ProcessingState.Idle
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error(e.message ?: "حدث خطأ أثناء قراءة الفيديو")
            }
        }
    }

    fun loadDemoVideo() {
        viewModelScope.launch {
            stopPlayback()
            _processingState.value = ProcessingState.LoadingVideo(0.2f)
            try {
                val context = getApplication<Application>()
                val meta = SampleMediaGenerator.createDemoVideoMetadata(context)
                val pcm = SampleMediaGenerator.createDemoAudioPcm()

                _videoMetadata.value = meta
                customOutputName.value = "صوت_توضيحي_موسيقائي"
                _pcmData.value = pcm

                startMs.value = 0L
                endMs.value = pcm.originalDurationMs

                applyCurrentEdits()

                _processingState.value = ProcessingState.Idle
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("حدث خطأ أثناء تحميل التجربة")
            }
        }
    }

    fun updateTrimRange(newStart: Long, newEnd: Long) {
        val maxDuration = _pcmData.value?.originalDurationMs ?: 0L
        startMs.value = newStart.coerceIn(0L, maxDuration)
        endMs.value = newEnd.coerceIn(startMs.value + 500L, maxDuration)
        applyCurrentEdits()
    }

    fun updateEditMode(mode: AudioEditMode) {
        editMode.value = mode
        applyCurrentEdits()
    }

    fun updateFadeIn(ms: Int) {
        fadeInMs.value = ms
        applyCurrentEdits()
    }

    fun updateFadeOut(ms: Int) {
        fadeOutMs.value = ms
        applyCurrentEdits()
    }

    fun updateVolume(vol: Float) {
        volumeMultiplier.value = vol
        applyCurrentEdits()
    }

    fun setCustomFolder(uri: Uri?, name: String?) {
        customFolderUri.value = uri
        customFolderName.value = name ?: "المجلد المحدد"
    }

    private fun applyCurrentEdits() {
        val pcm = _pcmData.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val edited = AudioProcessor.editPcm(
                pcm = pcm,
                startMs = startMs.value,
                endMs = endMs.value,
                editMode = editMode.value,
                fadeInMs = fadeInMs.value,
                fadeOutMs = fadeOutMs.value,
                volumeMultiplier = volumeMultiplier.value
            )
            _editedPcmData.value = edited
            if (isPlaying.value) {
                stopPlayback()
            }
        }
    }

    fun togglePlayback() {
        if (isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val pcm = _editedPcmData.value ?: return
        if (pcm.pcmShorts.isEmpty()) return

        stopPlayback()
        isPlaying.value = true

        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val channelConfig = if (pcm.channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val minBufferSize = AudioTrack.getMinBufferSize(
                    pcm.sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(pcm.sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrackPlayer = track
                track.play()

                val shorts = pcm.pcmShorts
                val chunkSize = 2048
                var offset = 0

                val totalDurationMs = pcm.originalDurationMs
                val samplesPerMs = (pcm.sampleRate.toDouble() * pcm.channelCount.toDouble() / 1000.0)

                while (isPlaying.value && offset < shorts.size) {
                    val count = minOf(chunkSize, shorts.size - offset)
                    track.write(shorts, offset, count)
                    offset += count

                    val currentMs = if (samplesPerMs > 0) (offset.toDouble() / samplesPerMs).toLong() else 0L
                    currentPlaybackMs.value = currentMs

                    if (offset >= shorts.size && isLooping.value) {
                        offset = 0
                    }
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    stopPlayback()
                }
            }
        }
    }

    fun seekPlaybackTo(targetMs: Long) {
        currentPlaybackMs.value = targetMs
        if (isPlaying.value) {
            stopPlayback()
            startPlayback()
        }
    }

    fun stopPlayback() {
        isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrackPlayer?.stop()
            audioTrackPlayer?.release()
        } catch (_: Exception) {}
        audioTrackPlayer = null
    }

    fun exportAudio() {
        val pcm = _editedPcmData.value ?: return
        viewModelScope.launch {
            stopPlayback()
            _processingState.value = ProcessingState.Exporting(0.1f, "جاري تحويل ومعالجة الملف الصوتي...")

            try {
                val context = getApplication<Application>()
                val title = customOutputName.value.ifBlank { "صوت_مفصول" }
                val format = selectedFormat.value
                val bitrate = selectedBitrateKbps.value
                val folderUri = customFolderUri.value

                val result = AudioProcessor.exportAudio(
                    context = context,
                    pcm = pcm,
                    format = format,
                    bitrateKbps = bitrate,
                    customTitle = title,
                    outputTreeUri = folderUri
                ) { prog ->
                    _processingState.value = ProcessingState.Exporting(0.2f + 0.7f * prog, "جاري الحفظ والترميز...")
                }

                val savedEntity = SavedAudioEntity(
                    title = result.title,
                    originalVideoName = _videoMetadata.value?.title ?: "فيديو",
                    format = format.extension.uppercase(),
                    bitrate = bitrate,
                    durationMs = pcm.originalDurationMs,
                    fileSizeByte = result.fileSizeBytes,
                    filePath = result.filePath,
                    fileUriString = result.fileUriString
                )

                dao.insertSavedAudio(savedEntity)

                _processingState.value = ProcessingState.Success(
                    savedFilePath = result.filePath,
                    savedTitle = result.title
                )
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error(e.message ?: "حدث خطأ أثناء تصدير الصوت")
            }
        }
    }

    fun deleteAudioItem(item: SavedAudioEntity) {
        viewModelScope.launch {
            dao.deleteSavedAudio(item)
        }
    }

    fun renameAudioItem(id: Long, newTitle: String) {
        viewModelScope.launch {
            dao.updateTitle(id, newTitle)
        }
    }

    fun resetState() {
        _processingState.value = ProcessingState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
