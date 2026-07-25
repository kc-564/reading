package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a highlighted text range (F06).
 *
 * @property id        Auto-generated primary key.
 * @property bookId    Foreign key to [BookEntity.bookId].
 * @property chapterIndex Index of the chapter containing the highlight.
 * @property startChar Start character offset inside the chapter text (inclusive).
 * @property endChar   End character offset inside the chapter text (exclusive).
 * @property colorArgb ARGB color of the highlight.
 * @property createdAt Epoch millis when the highlight was created.
 */
@Entity(
    tableName = "highlights",
    indices = [Index(value = ["book_id"])]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "start_char") val startChar: Int,
    @ColumnInfo(name = "end_char") val endChar: Int,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
