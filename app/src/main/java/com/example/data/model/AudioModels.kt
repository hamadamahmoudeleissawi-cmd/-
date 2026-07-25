package com.example.data.model

import android.net.Uri

enum class AudioOutputFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String
) {
    MP3("mp3", "audio/mpeg", "MP3 (شائع ومعياري)"),
    AAC("m4a", "audio/mp4a-latm", "AAC / M4A (جودة عالية)"),
    WAV("wav", "audio/wav", "WAV (بدون ضغط - أقصى جودة)"),
    FLAC("flac", "audio/flac", "FLAC (ضغط بدون فقدان)"),
    OGG("ogg", "audio/ogg", "OGG (صوت فورتس)")
}

enum class AudioEditMode(val titleAr: String, val descriptionAr: String) {
    TRIM("قص وتحديد", "الإبقاء على الجزء المحدد فقط وحذف ما قبله وما بعده"),
    CUT_OUT("حذف جزء", "إزالة الجزء المحدد ودمج الجزأين المتبقيين معاً")
}

data class VideoMetadata(
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val fileSize: Long,
    val mimeType: String,
    val resolution: String = "",
    val hasAudioTrack: Boolean = true,
    val audioFormatName: String = "AAC/MP3"
)

data class AudioPcmData(
    val sampleRate: Int,
    val channelCount: Int,
    val pcmShorts: ShortArray,
    val originalDurationMs: Long,
    val waveformAmplitudes: FloatArray // 100-200 normalized values (0.0 .. 1.0) for visualizer
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioPcmData
        return pcmShorts.contentEquals(other.pcmShorts)
    }

    override fun hashCode(): Int {
        return pcmShorts.contentHashCode()
    }
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class LoadingVideo(val progress: Float = 0f) : ProcessingState()
    data class ExtractingAudio(val progress: Float = 0f, val message: String = "") : ProcessingState()
    data class Exporting(val progress: Float = 0f, val message: String = "") : ProcessingState()
    data class Success(val savedFilePath: String, val savedTitle: String) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
