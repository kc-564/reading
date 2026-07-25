package com.example.reader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the bookmarks table.
 */
@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookmark_id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY chapter_index ASC, char_offset ASC")
    fun getByBookId(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY chapter_index ASC, char_offset ASC")
    suspend fun getByBookIdList(bookId: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId AND chapter_index = :chapterIndex AND char_offset = :charOffset LIMIT 1")
    suspend fun findByPosition(bookId: String, chapterIndex: Int, charOffset: Int): BookmarkEntity?
}
