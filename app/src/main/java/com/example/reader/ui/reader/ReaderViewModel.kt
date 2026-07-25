package com.example.reader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.parser.EncodingDetector
import com.example.reader.parser.LruEncodingCache
import com.example.reader.parser.TxtParser
import com.example.reader.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * ViewModel for the reader screen.
 *
 * Loads a book from disk, parses it into chapters, and manages reading progress.
 */
class ReaderViewModel(
    application: Application,
    private val bookPath: String
) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = BookRepository(db)
    private val parser = TxtParser()
    private val encodingCache = LruEncodingCache()
    private val prefs = AppPrefs(application)

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadBook()
    }

    /**
     * Loads the book file: detects encoding, parses chapters, restores progress.
     */
    private fun loadBook() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Get user's default encoding preference
                val defaultPref = prefs.defaultEncoding.first()

                // 2. Detect encoding with LRU cache
                val encoding: Charset = encodingCache.getOrPut(bookPath, defaultPref) {
                    EncodingDetector.detect(bookPath, defaultPref)
                        ?: StandardCharsets.UTF_8
                }

                // 3. Parse the book into chapters
                val chapters = parser.parse(bookPath, encoding)
                if (chapters.isEmpty()) {
                    _uiState.value = ReaderUiState.Error("无法读取文件或文件为空")
                    return@launch
                }

                // 4. Calculate total chars
                val totalChars = chapters.sumOf { it.totalCharCount.toLong() }

                // 5. Check for existing progress in database
                val existingBook = repository.getBook(bookPath)
                val currentChapterIndex: Int
                val currentCharOffset: Int

                if (existingBook != null) {
                    currentChapterIndex = existingBook.lastChapterIndex.coerceIn(0, chapters.size - 1)
                    currentCharOffset = existingBook.lastCharOffset
                } else {
                    // New book — insert a fresh BookEntity
                    val file = File(bookPath)
                    val newBook = BookEntity(
                        bookId = bookPath,
                        filePath = bookPath,
                        fileName = file.name,
                        title = file.nameWithoutExtension,
                        format = "txt",
                        sizeBytes = file.length(),
                        encoding = encoding.name(),
                        lastOpenedAt = System.currentTimeMillis(),
                        totalChapters = chapters.size,
                        totalChars = totalChars
                    )
                    repository.upsertBook(newBook)
                    currentChapterIndex = 0
                    currentCharOffset = 0
                }

                // 6. Record this open event
                val initialPercent = if (currentChapterIndex >= 0) {
                    repository.calculatePercent(
                        currentChapterIndex, currentCharOffset, chapters, totalChars
                    )
                } else 0f
                repository.recordOpen(bookPath, initialPercent)

                // 7. Emit Ready state
                _uiState.value = ReaderUiState.Ready(
                    chapters = chapters,
                    currentChapterIndex = currentChapterIndex,
                    currentCharOffset = currentCharOffset,
                    totalChars = totalChars,
                    encoding = encoding.name()
                )
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error("加载失败: ${e.message}")
            }
        }
    }

    /**
     * Saves the current reading progress to the database.
     */
    fun saveProgress(chapterIndex: Int, charOffset: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            if (state is ReaderUiState.Ready) {
                val percent = repository.calculatePercent(
                    chapterIndex, charOffset, state.chapters, state.totalChars
                )
                val now = System.currentTimeMillis()
                repository.updateProgress(bookPath, now, percent, chapterIndex, charOffset)
                repository.recordOpen(bookPath, percent)
            }
        }
    }

    /**
     * Navigates to a different chapter.
     */
    fun navigateToChapter(index: Int) {
        val state = _uiState.value
        if (state is ReaderUiState.Ready && index in state.chapters.indices) {
            _uiState.value = state.copy(
                currentChapterIndex = index,
                currentCharOffset = 0
            )
        }
    }

    companion object {
        /**
         * Returns a [ViewModelProvider.Factory] that creates [ReaderViewModel]
         * with the given [bookPath].
         */
        fun provideFactory(
            application: Application,
            bookPath: String
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ReaderViewModel(application, bookPath) as T
                }
            }
        }
    }
}
