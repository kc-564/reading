package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a user bookmark inside a book.
 *
 * @property bookmarkId Auto-generated primary key.
 * @property bookId     Foreign key to [BookEntity.bookId].
 * @property chapterIndex Index of the chapter the bookmark points to.
 * @property pageIndex  Index of the page within the chapter (for ordering/display).
 * @property charOffset Character offset inside the chapter text.
 * @property previewText A short snippet of text around the bookmark for display.
 * @property createdAt  Epoch millis when the bookmark was created.
 */
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["book_id", "chapter_index"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "bookmark_id")
    val bookmarkId: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    @ColumnInfo(name = "preview_text") val previewText: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
