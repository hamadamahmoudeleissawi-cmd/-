package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAudioDao {
    @Query("SELECT * FROM saved_audios ORDER BY createdAt DESC")
    fun getAllSavedAudios(): Flow<List<SavedAudioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAudio(audio: SavedAudioEntity): Long

    @Update
    suspend fun updateSavedAudio(audio: SavedAudioEntity)

    @Query("UPDATE saved_audios SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)

    @Delete
    suspend fun deleteSavedAudio(audio: SavedAudioEntity)

    @Query("DELETE FROM saved_audios WHERE id = :id")
    suspend fun deleteById(id: Long)
}
