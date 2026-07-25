package com.example.reader.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookRepository
import com.example.reader.data.StatsRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Simple stats data class for the profile screen.
 */
data class ReadingStats(
    val totalMinutes: Long = 0L,
    val bookCount: Int = 0,
    val totalChars: Long = 0L
)

/**
 * ViewModel for the Profile screen (v1.1).
 *
 * Loads deduplicated recent reading history and aggregated reading statistics
 * (cumulative, weekly, monthly).
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = BookRepository(db)
    private val statsRepository = StatsRepository(db)

    /** Deduplicated recent books (consecutive same bookId merged, max 10). */
    val recentDistinctBooks: StateFlow<List<BookEntity>> =
        repository.getRecentDistinctBookFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cumulative reading stats across all books / sessions. */
    val totalStats: StateFlow<ReadingStats> =
        repository.getAllBooksFlow()
            .map { books ->
                ReadingStats(
                    totalMinutes = books.sumOf { it.totalChars } / 500 / 60,
                    bookCount = books.size,
                    totalChars = books.sumOf { it.totalChars }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())

    /** Weekly reading stats (last 7 days of sessions). */
    val weeklyStats: StateFlow<ReadingStats> =
        statsRepository.getWeeklyStats()
            .map { sessions ->
                val totalSecs = sessions.sumOf { it.durationSec }
                ReadingStats(
                    totalMinutes = (totalSecs / 60).toLong(),
                    bookCount = sessions.map { it.bookId }.distinct().size,
                    totalChars = 0L
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())

    /** Monthly reading stats (last 30 days of sessions). */
    val monthlyStats: StateFlow<ReadingStats> =
        statsRepository.getMonthlyStats()
            .map { sessions ->
                val totalSecs = sessions.sumOf { it.durationSec }
                ReadingStats(
                    totalMinutes = (totalSecs / 60).toLong(),
                    bookCount = sessions.map { it.bookId }.distinct().size,
                    totalChars = 0L
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())
}
