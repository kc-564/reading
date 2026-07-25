package com.example.reader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the toc_rule_prefs table (E05).
 */
@Dao
interface TocRulePrefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: TocRulePrefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prefs: List<TocRulePrefEntity>)

    @Query("SELECT * FROM toc_rule_prefs WHERE book_id = :bookId")
    fun getByBookId(bookId: String): Flow<List<TocRulePrefEntity>>

    @Query("SELECT * FROM toc_rule_prefs WHERE book_id = :bookId")
    suspend fun getByBookIdList(bookId: String): List<TocRulePrefEntity>

    @Query("DELETE FROM toc_rule_prefs WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
