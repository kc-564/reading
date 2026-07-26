package com.example.reader.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the books table.
 */
@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE bookId = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    @Query("SELECT bookId FROM books WHERE content_fingerprint = :fp LIMIT 1")
    suspend fun getBookIdByFingerprint(fp: String): String?

    @Query("SELECT * FROM books WHERE bookId = :bookId")
    fun getBookFlow(bookId: String): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query(
        """
        UPDATE books SET
            lastOpenedAt = :openedAt,
            progressPercent = :progressPercent,
            lastChapterIndex = :chapterIndex,
            lastCharOffset = :charOffset
        WHERE bookId = :bookId
        """
    )
    suspend fun updateProgress(
        bookId: String,
        openedAt: Long,
        progressPercent: Float,
        chapterIndex: Int,
        charOffset: Int
    )

    @Query(
        """
        UPDATE books SET
            title = :title,
            author = :author,
            cover_uri = :coverUri
        WHERE bookId = :bookId
        """
    )
    suspend fun updateBookMeta(
        bookId: String,
        title: String,
        author: String?,
        coverUri: String?
    )

    @Query("UPDATE books SET is_read = :isRead WHERE bookId = :bookId")
    suspend fun setRead(bookId: String, isRead: Boolean)

    @Query("UPDATE books SET encoding = :encoding WHERE bookId = :bookId")
    suspend fun updateEncoding(bookId: String, encoding: String)

    @Query("DELETE FROM books WHERE bookId = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("SELECT * FROM books WHERE bookId IN (:ids)")
    suspend fun getBooksByIds(ids: List<String>): List<BookEntity>
}
