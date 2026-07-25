package com.example.reader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for reading_history table.
 */
@Dao
interface ReadingHistoryDao {

    @Query("SELECT * FROM reading_history ORDER BY openedAt DESC LIMIT 10")
    fun getRecentHistoryFlow(): Flow<List<ReadingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
