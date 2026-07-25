package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a book in the library.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val bookId: String,
    val filePath: String,
    val fileName: String,
    val title: String,
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
