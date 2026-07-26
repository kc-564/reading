package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a book in the library.
 *
 * Phase 2 extends the schema with [author]/[coverUri]/[isRead] columns (added in
 * migration 2 -> 3). They are nullable/optional so existing rows migrate cleanly.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val bookId: String,
    val filePath: String,
    val fileName: String,
    val title: String,
    @ColumnInfo(name = "author") val author: String? = null,
    @ColumnInfo(name = "cover_uri") val coverUri: String? = null,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String? = null,
    @ColumnInfo(name = "format") val format: String,
    val sizeBytes: Long,
    val encoding: String,
    val lastOpenedAt: Long,
    val progressPercent: Float = 0f,
    val lastChapterIndex: Int = -1,
    val lastCharOffset: Int = 0,
    val totalChapters: Int = 0,
    val totalChars: Long = 0L
)
