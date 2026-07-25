package com.example.reader.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for books table.
 */
@Dao
interface BookDao {

    @Query("SELECT * FROM books")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE bookId = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

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

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("SELECT * FROM books WHERE bookId = :bookId")
    fun getBookFlow(bookId: String): Flow<BookEntity?>
}
