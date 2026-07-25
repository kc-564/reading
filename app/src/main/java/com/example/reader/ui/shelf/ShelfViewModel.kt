package com.example.reader.ui.shelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookRepository
import com.example.reader.data.ReadFilter
import com.example.reader.data.SortMode
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.db.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the shelf / home screen (C04 / F05 / F07).
 *
 * Exposes:
 * - [recentHistory] — recently opened books (max 10).
 * - [books] — the library sorted + filtered by the current [sortMode] / [readFilter].
 * - [sortMode] / [readFilter] — current controls, mutated by [setSortMode] / [setReadFilter].
 * - [deleteBook] / [saveBookMeta] — per-book mutations.
 */
class ShelfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(AppDatabase.getInstance(application))

    /** Recently opened books, ordered by last opened time (newest first, max 10). */
    val recentHistory: StateFlow<List<ReadingHistoryEntity>> =
        repository.getRecentHistoryFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sortMode = MutableStateFlow(SortMode.LAST_OPENED)
    private val _readFilter = MutableStateFlow(ReadFilter.ALL)

    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    val readFilter: StateFlow<ReadFilter> = _readFilter.asStateFlow()

    /** All books after applying the current sort + read/unread filter. */
    val books: StateFlow<List<BookEntity>> = combine(
        repository.getAllBooksFlow(),
        _sortMode,
        _readFilter
    ) { list, sort, filter ->
        val filtered = when (filter) {
            ReadFilter.ALL -> list
            ReadFilter.READ -> list.filter { it.isRead }
            ReadFilter.UNREAD -> list.filter { !it.isRead }
        }
        when (sort) {
            SortMode.LAST_OPENED -> filtered.sortedByDescending { it.lastOpenedAt }
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortMode.PROGRESS -> filtered.sortedByDescending { it.progressPercent }
            SortMode.ADDED -> filtered.sortedByDescending { it.lastOpenedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setReadFilter(filter: ReadFilter) {
        _readFilter.value = filter
    }

    /** Loads a single book (suspend) for the meta editor. */
    suspend fun getBook(bookId: String): BookEntity? = repository.getBook(bookId)

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    /** Persists edited title / author / cover (F05). */
    fun saveBookMeta(bookId: String, title: String, author: String?, coverUri: String?) {
        viewModelScope.launch {
            repository.updateBookMeta(
                bookId = bookId,
                title = title.ifBlank { repository.getBook(bookId)?.title ?: title },
                author = author?.takeIf { it.isNotBlank() },
                coverUri = coverUri?.takeIf { it.isNotBlank() }
            )
        }
    }
}
