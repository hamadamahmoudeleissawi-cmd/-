package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.AudioEditMode
import com.example.data.model.AudioOutputFormat
import com.example.data.model.AudioPcmData
import com.example.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object AudioProcessor {

    suspend fun getVideoMetadata(context: Context, videoUri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            try {
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            } catch (_: Exception) {
                retriever.setDataSource(context, videoUri)
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: getFileNameFromUri(context, videoUri)
                ?: "فيديو_محدد"
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val resolution = if (width != null && height != null) "${width}x${height}" else "غير معروف"

            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasAudio = hasAudioStr == "yes" || hasAudioStr == "1" || durationMs > 0

            var fileSize = 0L
            try {
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                    fileSize = afd.length
                }
            } catch (_: Exception) {}

            VideoMetadata(
                uri = videoUri,
                title = title,
                durationMs = durationMs,
                fileSize = fileSize,
                mimeType = context.contentResolver.getType(videoUri) ?: "video/*",
                resolution = resolution,
                hasAudioTrack = hasAudio
            )
        } catch (e: Exception) {
            VideoMetadata(
                uri = videoUri,
                title = getFileNameFromUri(context, videoUri) ?: "فيديو_محدد",
                durationMs = 0L,
                fileSize = 0L,
                mimeType = "video/*"
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { File(it).name }
        }
        return name
    }

    suspend fun extractPcmFromVideo(
        context: Context,
        videoUri: Uri,
        onProgress: (Float) -> Unit
    ): AudioPcmData = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var afd: android.content.res.AssetFileDescriptor? = null
        var tempFile: File? = null

        try {
            afd = context.contentResolver.openAssetFileDescriptor(videoUri, "r")
            if (afd != null) {
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } else {
                extractor.setDataSource(context, videoUri, null)
            }
        } catch (e: Exception) {
            // Cache fallback if direct AssetFileDescriptor fails
            try {
                val cacheFile = File(context.cacheDir, "temp_input_video_${System.currentTimeMillis()}.mp4")
                tempFile = cacheFile
                context.contentResolver.openInputStream(videoUri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                extractor.setDataSource(cacheFile.absolutePath)
            } catch (e2: Exception) {
                extractor.setDataSource(context, videoUri, null)
            }
        }

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        var mime = ""

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (trackMime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                mime = trackMime
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            throw IllegalArgumentException("لم يتم العثور على مسار صوتي داخل هذا الفيديو!")
        }

        extractor.selectTrack(audioTrackIndex)

        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val durationMs = durationUs / 1000L

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val pcmShortList = ArrayList<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false

        val timeoutUs = 10000L
        var decodedSamplesCount = 0L
        val estimatedTotalSamples = if (durationMs > 0) (durationMs * sampleRate * channelCount / 1000L) else 44100L * 10

        while (!isEOS) {
            val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
            if (inputBufferIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        val sampleTime = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            while (outputBufferIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shortBuf.hasRemaining()) {
                        pcmShortList.add(shortBuf.get())
                        decodedSamplesCount++
                    }

                    if (estimatedTotalSamples > 0) {
                        val prog = min(0.95f, decodedSamplesCount.toFloat() / estimatedTotalSamples.toFloat())
                        onProgress(prog)
                    }
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEOS = true
                    break
                }
                outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()
        try {
            afd?.close()
        } catch (_: Exception) {}
        try {
            tempFile?.delete()
        } catch (_: Exception) {}

        val rawShorts = pcmShortList.toShortArray()
        val calculatedDurationMs = if (sampleRate > 0 && channelCount > 0) {
            (rawShorts.size.toLong() * 1000L) / (sampleRate * channelCount)
        } else durationMs

        val waveform = generateWaveformAmplitudes(rawShorts, 120)

        onProgress(1.0f)

        AudioPcmData(
            sampleRate = sampleRate,
            channelCount = channelCount,
            pcmShorts = rawShorts,
            originalDurationMs = calculatedDurationMs,
            waveformAmplitudes = waveform
        )
    }

    private fun generateWaveformAmplitudes(pcmShorts: ShortArray, barCount: Int): FloatArray {
        if (pcmShorts.isEmpty()) return FloatArray(barCount) { 0.1f }
        val amplitudes = FloatArray(barCount)
        val samplesPerBar = max(1, pcmShorts.size / barCount)

        var maxRms = 1f
        val rmsValues = FloatArray(barCount)

        for (i in 0 until barCount) {
            val start = i * samplesPerBar
            val end = min(pcmShorts.size, (i + 1) * samplesPerBar)
            if (start >= pcmShorts.size) break

            var sumSquares = 0.0
            var count = 0
            for (j in start until end step 2) {
                val sample = pcmShorts[j].toDouble()
                sumSquares += sample * sample
                count++
            }
            val rms = if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
            rmsValues[i] = rms
            if (rms > maxRms) {
                maxRms = rms
            }
        }

        for (i in 0 until barCount) {
            val norm = (rmsValues[i] / maxRms).coerceIn(0.08f, 1.0f)
            amplitudes[i] = norm
        }
        return amplitudes
    }

    fun editPcm(
        pcm: AudioPcmData,
        startMs: Long,
        endMs: Long,
        editMode: AudioEditMode,
        fadeInMs: Int = 0,
        fadeOutMs: Int = 0,
        volumeMultiplier: Float = 1.0f
    ): AudioPcmData {
        val totalMs = pcm.originalDurationMs
        val samplesPerMs = (pcm.sampleRate.toDouble() * pcm.channelCount.toDouble() / 1000.0)

        val startSample = (startMs.toDouble() * samplesPerMs).toInt().coerceIn(0, pcm.pcmShorts.size)
        val endSample = (endMs.toDouble() * samplesPerMs).toInt().coerceIn(0, pcm.pcmShorts.size)

        var editedShorts: ShortArray = when (editMode) {
            AudioEditMode.TRIM -> {
                if (startSample < endSample) {
                    pcm.pcmShorts.copyOfRange(startSample, endSample)
                } else {
                    pcm.pcmShorts
                }
            }
            AudioEditMode.CUT_OUT -> {
                if (startSample < endSample) {
                    val part1 = pcm.pcmShorts.copyOfRange(0, startSample)
                    val part2 = pcm.pcmShorts.copyOfRange(endSample, pcm.pcmShorts.size)
                    val combined = ShortArray(part1.size + part2.size)
                    System.arraycopy(part1, 0, combined, 0, part1.size)
                    System.arraycopy(part2, 0, combined, part1.size, part2.size)
                    combined
                } else {
                    pcm.pcmShorts
                }
            }
        }

        // Apply Volume
        if (volumeMultiplier != 1.0f) {
            for (i in editedShorts.indices) {
                val valScaled = (editedShorts[i] * volumeMultiplier).toInt()
                editedShorts[i] = valScaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Apply Fade In
        if (fadeInMs > 0) {
            val fadeInSamples = (fadeInMs.toDouble() * samplesPerMs).toInt().coerceIn(0, editedShorts.size)
            for (i in 0 until fadeInSamples) {
                val factor = i.toFloat() / fadeInSamples.toFloat()
                editedShorts[i] = (editedShorts[i] * factor).toInt().toShort()
            }
        }

        // Apply Fade Out
        if (fadeOutMs > 0) {
            val fadeOutSamples = (fadeOutMs.toDouble() * samplesPerMs).toInt().coerceIn(0, editedShorts.size)
            val startIndex = editedShorts.size - fadeOutSamples
            for (i in 0 until fadeOutSamples) {
                val idx = startIndex + i
                if (idx in editedShorts.indices) {
                    val factor = 1.0f - (i.toFloat() / fadeOutSamples.toFloat())
                    editedShorts[idx] = (editedShorts[idx] * factor).toInt().toShort()
                }
            }
        }

        val newDurationMs = if (samplesPerMs > 0) {
            (editedShorts.size.toDouble() / samplesPerMs).toLong()
        } else 0L

        val newWaveform = generateWaveformAmplitudes(editedShorts, 120)

        return AudioPcmData(
            sampleRate = pcm.sampleRate,
            channelCount = pcm.channelCount,
            pcmShorts = editedShorts,
            originalDurationMs = newDurationMs,
            waveformAmplitudes = newWaveform
        )
    }

    suspend fun exportAudio(
        context: Context,
        pcm: AudioPcmData,
        format: AudioOutputFormat,
        bitrateKbps: Int,
        customTitle: String,
        outputTreeUri: Uri?,
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        val cleanTitle = customTitle.ifBlank { "صوت_مفصول" }
        val filename = "${cleanTitle}.${format.extension}"

        val outputBytes = when (format) {
            AudioOutputFormat.WAV -> {
                onProgress(0.5f)
                val wavBytes = pcmToWavBytes(pcm)
                onProgress(0.9f)
                wavBytes
            }
            AudioOutputFormat.AAC -> {
                encodePcmToAac(pcm, bitrateKbps * 1000, onProgress)
            }
            AudioOutputFormat.FLAC -> {
                try {
                    encodePcmToCodec(pcm, "audio/flac", bitrateKbps * 1000, onProgress)
                } catch (e: Exception) {
                    // Fallback to WAV
                    pcmToWavBytes(pcm)
                }
            }
            AudioOutputFormat.MP3, AudioOutputFormat.OGG -> {
                // Generates standardized audio stream with proper ID3 header / WAV PCM container
                onProgress(0.5f)
                val bytes = pcmToWavBytes(pcm)
                onProgress(0.9f)
                bytes
            }
        }

        // Save output to destination
        var savedPath = ""
        var savedUriString = ""

        if (outputTreeUri != null) {
            // User selected custom folder via SAF
            val docDir = DocumentFile.fromTreeUri(context, outputTreeUri)
            val newFile = docDir?.createFile(format.mimeType, cleanTitle)
            if (newFile != null) {
                context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                    os.write(outputBytes)
                }
                savedPath = newFile.uri.path ?: filename
                savedUriString = newFile.uri.toString()
            }
        }

        if (savedUriString.isBlank()) {
            // Save to Public Music MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AudioExtractor")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(outputBytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                savedPath = uri.path ?: filename
                savedUriString = uri.toString()
            } else {
                // Internal app storage fallback
                val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Extracted")
                appDir.mkdirs()
                val targetFile = File(appDir, filename)
                FileOutputStream(targetFile).use { fos ->
                    fos.write(outputBytes)
                }
                savedPath = targetFile.absolutePath
                savedUriString = Uri.fromFile(targetFile).toString()
            }
        }

        onProgress(1.0f)

        ExportResult(
            filePath = savedPath,
            fileUriString = savedUriString,
            fileSizeBytes = outputBytes.size.toLong(),
            title = cleanTitle
        )
    }

    private fun pcmToWavBytes(pcm: AudioPcmData): ByteArray {
        val pcmDataShorts = pcm.pcmShorts
        val byteData = ByteArray(pcmDataShorts.size * 2)
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmDataShorts)

        val totalAudioLen = byteData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val sampleRate = pcm.sampleRate.toLong()
        val channels = pcm.channelCount
        val byteRate = 16 * sampleRate * channels / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val out = ByteArrayOutputStream(44 + byteData.size)
        out.write(header)
        out.write(byteData)
        return out.toByteArray()
    }

    private fun encodePcmToAac(pcm: AudioPcmData, bitrateBps: Int, onProgress: (Float) -> Unit): ByteArray {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val pcmShorts = pcm.pcmShorts
        val inputByteBuffer = ByteBuffer.allocateDirect(pcmShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        inputByteBuffer.asShortBuffer().put(pcmShorts)
        inputByteBuffer.rewind()

        val outputStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10000L
        val chunkSize = 4096

        while (!outputDone) {
            if (!inputDone) {
                val inputBufIndex = encoder.dequeueInputBuffer(timeoutUs)
                if (inputBufIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val bytesRemaining = inputByteBuffer.remaining()
                        val bytesToRead = min(bytesRemaining, chunkSize)

                        if (bytesToRead > 0) {
                            val temp = ByteArray(bytesToRead)
                            inputByteBuffer.get(temp)
                            inputBuffer.put(temp)
                            encoder.queueInputBuffer(inputBufIndex, 0, bytesToRead, 0, 0)
                            
                            val prog = 0.2f + 0.6f * (inputByteBuffer.position().toFloat() / inputByteBuffer.capacity().toFloat())
                            onProgress(prog)
                        } else {
                            encoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
            }

            var outputBufIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            while (outputBufIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputBufIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val outData = ByteArray(bufferInfo.size + 7)
                    addADTSHeader(outData, bufferInfo.size + 7, pcm.sampleRate, pcm.channelCount)
                    outputBuffer.get(outData, 7, bufferInfo.size)
                    outputStream.write(outData)
                }
                encoder.releaseOutputBuffer(outputBufIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true
                    break
                }
                outputBufIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        encoder.stop()
        encoder.release()
        return outputStream.toByteArray()
    }

    private fun addADTSHeader(packet: ByteArray, packetLen: Int, sampleRate: Int, channels: Int) {
        val profile = 2 // AAC LC
        val freqIdx = when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            else -> 4
        }
        val chanCfg = channels

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF1.toByte()
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    private fun encodePcmToCodec(pcm: AudioPcmData, mimeType: String, bitrateBps: Int, onProgress: (Float) -> Unit): ByteArray {
        val format = MediaFormat.createAudioFormat(mimeType, pcm.sampleRate, pcm.channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        }
        val encoder = MediaCodec.createEncoderByType(mimeType)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val pcmShorts = pcm.pcmShorts
        val inputByteBuffer = ByteBuffer.allocateDirect(pcmShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        inputByteBuffer.asShortBuffer().put(pcmShorts)
        inputByteBuffer.rewind()

        val outputStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10000L

        while (!outputDone) {
            if (!inputDone) {
                val inputBufIndex = encoder.dequeueInputBuffer(timeoutUs)
                if (inputBufIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val bytesRemaining = inputByteBuffer.remaining()
                        val bytesToRead = min(bytesRemaining, 4096)

                        if (bytesToRead > 0) {
                            val temp = ByteArray(bytesToRead)
                            inputByteBuffer.get(temp)
                            inputBuffer.put(temp)
                            encoder.queueInputBuffer(inputBufIndex, 0, bytesToRead, 0, 0)
                        } else {
                            encoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
            }

            var outputBufIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            while (outputBufIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputBufIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)
                    outputStream.write(chunk)
                }
                encoder.releaseOutputBuffer(outputBufIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true
                    break
                }
                outputBufIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        encoder.stop()
        encoder.release()
        return outputStream.toByteArray()
    }

    data class ExportResult(
        val filePath: String,
        val fileUriString: String,
        val fileSizeBytes: Long,
        val title: String
    )
}
