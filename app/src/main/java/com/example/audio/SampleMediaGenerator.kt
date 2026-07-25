package com.example.audio

import android.content.Context
import android.net.Uri
import com.example.data.model.AudioPcmData
import com.example.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

object SampleMediaGenerator {

    suspend fun createDemoAudioPcm(): AudioPcmData = withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val channelCount = 2
        val durationSeconds = 10
        val totalSamples = sampleRate * channelCount * durationSeconds

        val pcmShorts = ShortArray(totalSamples)

        // Musical notes frequencies (C4, E4, G4, B4, C5, E5, G5, A4)
        val noteFreqs = doubleArrayOf(261.63, 329.63, 392.00, 493.88, 523.25, 659.25, 783.99, 440.00)

        var sampleIndex = 0
        val samplesPerNote = sampleRate / 2 // half second per note

        for (i in 0 until totalSamples step 2) {
            val secondOffset = (i / 2)
            val noteIndex = (secondOffset / samplesPerNote) % noteFreqs.size
            val freq = noteFreqs[noteIndex]

            val t = (secondOffset % samplesPerNote).toDouble() / sampleRate.toDouble()
            
            // Envelope: Attack and Decay
            val envelope = sin(PI * t * 2.0).coerceIn(0.0, 1.0)
            
            // Melodic wave + harmonics
            val wave1 = sin(2.0 * PI * freq * t)
            val wave2 = 0.5 * sin(2.0 * PI * freq * 2.0 * t)
            val wave3 = 0.25 * sin(2.0 * PI * freq * 1.5 * t)
            val combined = (wave1 + wave2 + wave3) / 1.75

            val amplitude = (combined * envelope * 22000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            // Stereo channels (Left & Right)
            pcmShorts[i] = amplitude
            pcmShorts[i + 1] = (amplitude * 0.9).toInt().toShort()
        }

        val waveformBars = FloatArray(120) { idx ->
            val factor = sin(idx.toDouble() * 0.15) * 0.4 + 0.6
            factor.toFloat().coerceIn(0.15f, 0.95f)
        }

        AudioPcmData(
            sampleRate = sampleRate,
            channelCount = channelCount,
            pcmShorts = pcmShorts,
            originalDurationMs = durationSeconds * 1000L,
            waveformAmplitudes = waveformBars
        )
    }

    suspend fun createDemoVideoMetadata(context: Context): VideoMetadata = withContext(Dispatchers.IO) {
        val demoFile = File(context.cacheDir, "demo_sample_video.mp4")
        if (!demoFile.exists()) {
            demoFile.createNewFile()
        }
        VideoMetadata(
            uri = Uri.fromFile(demoFile),
            title = "فيديو_توضيحي_موسيقائي.mp4",
            durationMs = 10000L,
            fileSize = 1024 * 512,
            mimeType = "video/mp4",
            resolution = "1080x1920",
            hasAudioTrack = true,
            audioFormatName = "AAC Stereo 44.1kHz"
        )
    }
}
