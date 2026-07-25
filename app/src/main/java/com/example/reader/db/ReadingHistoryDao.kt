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

    /**
     * Returns the most-recently-opened books deduplicated by bookId.
     * Uses a subquery with GROUP BY bookId + MAX(openedAt) to pick the
     * latest open event per book, then joins back to books for full entity.
     */
    @Query("""
        SELECT b.* FROM books b
        INNER JOIN (
            SELECT bookId, MAX(openedAt) as maxOpenedAt
            FROM reading_history
            GROUP BY bookId
        ) h ON b.bookId = h.bookId
        ORDER BY h.maxOpenedAt DESC
        LIMIT 10
    """)
    fun getRecentDistinctBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
