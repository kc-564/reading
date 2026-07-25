package com.example.reader.data

import com.example.reader.db.AppDatabase
import com.example.reader.db.DayStat
import com.example.reader.db.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository for reading statistics (F07).
 */
class StatsRepository(private val db: AppDatabase) {

    private val dao = db.statsDao()

    suspend fun recordSession(session: ReadingSessionEntity) = dao.insert(session)

    fun getSessionsFlow(bookId: String): Flow<List<ReadingSessionEntity>> = dao.getByBookId(bookId)

    fun getTotalDurationFlow(bookId: String): Flow<Int> = dao.getTotalDurationByBook(bookId)

    fun getDailyFlow(start: String, end: String): Flow<List<DayStat>> = dao.getDailyDurations(start, end)

    fun getTotalDurationAllFlow(): Flow<Int> = dao.getTotalDurationAll()

    suspend fun getTotalDuration(bookId: String): Int = dao.getTotalDurationByBook(bookId).first()

    suspend fun deleteByBook(bookId: String) = dao.deleteByBookId(bookId)

    /** Sessions from the last 7 days. */
    fun getWeeklyStats(): Flow<List<ReadingSessionEntity>> {
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        return dao.getSessionsSince(weekAgo)
    }

    /** Sessions from the last 30 days. */
    fun getMonthlyStats(): Flow<List<ReadingSessionEntity>> {
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
        return dao.getSessionsSince(monthAgo)
    }
}
