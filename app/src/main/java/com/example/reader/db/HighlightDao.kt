package com.example.reader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the highlights table (F06).
 */
@Dao
interface HighlightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM highlights WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)

    @Query("SELECT * FROM highlights WHERE book_id = :bookId ORDER BY chapter_index ASC, start_char ASC")
    fun getByBookId(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: Long): HighlightEntity?
}
