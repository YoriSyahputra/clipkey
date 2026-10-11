package com.example.clipkey.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY isPinned DESC, createdAt DESC")
    fun getAllClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE content LIKE '%' || :query || '%' ORDER BY isPinned DESC, createdAt DESC")
    fun searchClips(query: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE isPinned = 1 ORDER BY createdAt DESC")
    fun getPinnedClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE content = :text LIMIT 1")
    suspend fun findByContent(text: String): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: ClipEntity): Long

    @Update
    suspend fun update(clip: ClipEntity)

    @Delete
    suspend fun delete(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE clips SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean)
}
