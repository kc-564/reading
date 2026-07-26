package com.example.reader.data

import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.db.ReadingHistoryEntity
import com.example.reader.parser.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Repository that mediates between the data layer (Room DB) and the rest of the app.
 */
class BookRepository(private val db: AppDatabase) {

    private val bookDao = db.bookDao()
    private val historyDao = db.readingHistoryDao()

    // ── Books ──

    /** All books ordered by last opened time (newest first). */
    fun getAllBooksFlow(): Flow<List<BookEntity>> = bookDao.getAllBooksFlow()

    /** Books after applying a sort mode and a read/unread filter. */
    fun getSortedBooksFlow(
        sortMode: SortMode = SortMode.LAST_OPENED,
        readFilter: ReadFilter = ReadFilter.ALL
    ): Flow<List<BookEntity>> =
        bookDao.getAllBooksFlow().map { list ->
            val filtered = when (readFilter) {
                ReadFilter.ALL -> list
                ReadFilter.READ -> list.filter { it.isRead }
                ReadFilter.UNREAD -> list.filter { !it.isRead }
            }
            when (sortMode) {
                SortMode.LAST_OPENED -> filtered.sortedByDescending { it.lastOpenedAt }
                SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
                SortMode.PROGRESS -> filtered.sortedByDescending { it.progressPercent }
                SortMode.ADDED -> filtered.sortedByDescending { it.lastOpenedAt }
            }
        }

    suspend fun getBook(bookId: String): BookEntity? = bookDao.getBook(bookId)

    /**
     * Returns true if a book with the given content fingerprint already exists in the library.
     * Used to skip re-importing a file whose content is already present.
     */
    suspend fun existsByFingerprint(fp: String): Boolean = bookDao.getBookIdByFingerprint(fp) != null

    suspend fun upsertBook(book: BookEntity) = bookDao.upsertBook(book)

    suspend fun upsertBooks(books: List<BookEntity>) = bookDao.upsertBooks(books)

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

    suspend fun updateBookMeta(bookId: String, title: String, author: String?, coverUri: String?) {
        bookDao.updateBookMeta(bookId, title, author, coverUri)
    }

    suspend fun setRead(bookId: String, isRead: Boolean) = bookDao.setRead(bookId, isRead)

    suspend fun updateEncoding(bookId: String, encoding: String) =
        bookDao.updateEncoding(bookId, encoding)

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
        historyDao.deleteByBookId(book.bookId)
    }

    suspend fun deleteBookById(bookId: String) {
        val book = bookDao.getBook(bookId) ?: return
        deleteBook(book)
    }

    // ── Reading History ──

    fun getRecentHistoryFlow(): Flow<List<ReadingHistoryEntity>> = historyDao.getRecentHistoryFlow()

    /** Deduplicated recent books (GROUP BY bookId + MAX openedAt). Returns full [BookEntity] list. */
    fun getRecentDistinctBookFlow(): Flow<List<BookEntity>> = historyDao.getRecentDistinctBooks()

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
     * Saves a cover image to the app's internal storage.
     *
     * @param context   Android context (for filesDir resolution).
     * @param bookId    Book identifier used as the filename stem.
     * @param bytes     Raw image bytes (PNG, JPEG, etc.).
     * @return The absolute file path where the cover was written.
     */
    fun saveCoverImage(context: android.content.Context, bookId: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "cover")
        if (!dir.exists()) dir.mkdirs()
        val safeId = java.net.URLEncoder.encode(bookId, "UTF-8")
        val file = File(dir, "$safeId.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * Calculates the cumulative reading percent across chapters.
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

    /** Sum of chapter char counts up to (but not including) [chapterIndex]. */
    fun charsBeforeChapter(chapters: List<Chapter>, chapterIndex: Int): Long {
        return (0 until chapterIndex.coerceIn(0, chapters.size))
            .sumOf { chapters[it].totalCharCount.toLong() }
    }
}

/**
 * How the shelf sorts books.
 */
enum class SortMode {
    LAST_OPENED, TITLE, PROGRESS, ADDED
}

/**
 * Read/unread filter for the shelf.
 */
enum class ReadFilter {
    ALL, READ, UNREAD
}
