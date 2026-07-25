package com.example.reader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the layout_cache table (C07).
 */
@Dao
interface LayoutCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LayoutCacheEntity)

    @Query("SELECT * FROM layout_cache WHERE cache_key = :key")
    suspend fun getByKey(key: String): LayoutCacheEntity?

    @Query("DELETE FROM layout_cache WHERE cache_key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM layout_cache WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)

    @Query("SELECT COUNT(*) FROM layout_cache WHERE book_id = :bookId")
    suspend fun countByBookId(bookId: String): Int
}
