package com.example.reader.data

import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.db.ReadingHistoryEntity
import com.example.reader.parser.Chapter
import kotlinx.coroutines.flow.Flow

/**
 * Repository that mediates between the data layer (Room DB) and the rest of the app.
 */
class BookRepository(private val db: AppDatabase) {

    private val bookDao = db.bookDao()
    private val historyDao = db.readingHistoryDao()

    // ── Books ──

    fun getAllBooksFlow(): Flow<List<BookEntity>> = bookDao.getAllBooksFlow()

    suspend fun getBook(bookId: String): BookEntity? = bookDao.getBook(bookId)

    suspend fun upsertBook(book: BookEntity) = bookDao.upsertBook(book)

    /**
     * Updates progress for a book.
     *
     * @param percent Already-calculated progress percent (0.0 ~ 1.0).
     */
    suspend fun updateProgress(
        bookId: String,
        openedAt: Long,
        percent: Float,
        chapterIndex: Int,
        charOffset: Int
    ) {
        bookDao.updateProgress(bookId, openedAt, percent, chapterIndex, charOffset)
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
        historyDao.deleteByBookId(book.bookId)
    }

    // ── Reading History ──

    fun getRecentHistoryFlow(): Flow<List<ReadingHistoryEntity>> = historyDao.getRecentHistoryFlow()

    suspend fun recordOpen(bookId: String, progressPercent: Float) {
        historyDao.upsertHistory(
            ReadingHistoryEntity(
                bookId = bookId,
                openedAt = System.currentTimeMillis(),
                progressPercent = progressPercent
            )
        )
    }

    // ── Helpers ──

    /**
     * Calculates the cumulative reading percent across chapters.
     *
     * Formula: (sum of totalCharCount of chapters 0..chapterIndex-1 + charOffset) / totalChars
     */
    fun calculatePercent(
        chapterIndex: Int,
        charOffset: Int,
        chapters: List<Chapter>,
        totalChars: Long
    ): Float {
        if (totalChars <= 0 || chapters.isEmpty()) return 0f
        val prevChars = (0 until chapterIndex.coerceIn(0, chapters.size))
            .sumOf { chapters[it].totalCharCount.toLong() }
        return ((prevChars + charOffset).toFloat() / totalChars.toFloat())
            .coerceIn(0f, 1f)
    }
}
