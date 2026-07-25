package com.example.reader.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Profile screen (v1.1).
 *
 * Loads deduplicated recent reading history and aggregated reading statistics.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(AppDatabase.getInstance(application))

    /** Deduplicated recent books (consecutive same bookId merged, max 10). */
    val recentDistinctBooks: StateFlow<List<BookEntity>> =
        repository.getRecentDistinctBookFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Aggregated total reading time in minutes (approximate). */
    val totalReadingMinutes: StateFlow<Long> =
        repository.getAllBooksFlow()
            .map { books -> books.sumOf { it.totalChars } / 500 / 60 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
}
