package com.example.reader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the reading_sessions table (F07 reading statistics).
 */
@Dao
interface StatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY started_at DESC")
    fun getByBookId(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY started_at DESC")
    suspend fun getByBookIdList(bookId: String): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions ORDER BY started_at DESC")
    suspend fun getAll(): List<ReadingSessionEntity>

    @Query("SELECT COALESCE(SUM(duration_sec), 0) FROM reading_sessions WHERE book_id = :bookId")
    fun getTotalDurationByBook(bookId: String): Flow<Int>

    @Query(
        """
        SELECT date_key AS dateKey, SUM(duration_sec) AS totalSec
        FROM reading_sessions
        WHERE date_key BETWEEN :start AND :end
        GROUP BY date_key
        ORDER BY date_key ASC
        """
    )
    fun getDailyDurations(start: String, end: String): Flow<List<DayStat>>

    @Query("DELETE FROM reading_sessions WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)

    @Query("SELECT COALESCE(SUM(duration_sec), 0) FROM reading_sessions")
    fun getTotalDurationAll(): Flow<Int>
}

/**
 * Aggregated reading duration for a single day.
 */
data class DayStat(
    @ColumnInfo(name = "dateKey") val dateKey: String,
    @ColumnInfo(name = "totalSec") val totalSec: Int
)
