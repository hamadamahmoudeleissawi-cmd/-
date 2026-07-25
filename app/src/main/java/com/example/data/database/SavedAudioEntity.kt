package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_audios")
data class SavedAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalVideoName: String,
    val format: String, // MP3, AAC, WAV, FLAC, OGG
    val bitrate: Int, // e.g. 192, 320 kbps
    val durationMs: Long,
    val fileSizeByte: Long,
    val filePath: String,
    val fileUriString: String,
    val createdAt: Long = System.currentTimeMillis()
)
