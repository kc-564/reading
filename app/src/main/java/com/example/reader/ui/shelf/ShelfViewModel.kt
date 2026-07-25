package com.example.reader.ui.shelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.db.ReadingHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the shelf / home screen.
 *
 * Exposes the list of all books and the recent reading history as [StateFlow]s.
 */
class ShelfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(AppDatabase.getInstance(application))

    /** Recently opened books, ordered by last opened time (newest first, max 10). */
    val recentHistory: StateFlow<List<ReadingHistoryEntity>> =
        repository.getRecentHistoryFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All books in the library. */
    val allBooks: StateFlow<List<BookEntity>> =
        repository.getAllBooksFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
