package com.example.reader.data

import com.example.reader.db.AppDatabase
import com.example.reader.db.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for bookmarks (D01).
 */
class BookmarkRepository(private val db: AppDatabase) {

    private val dao = db.bookmarkDao()

    suspend fun addBookmark(bookmark: BookmarkEntity) = dao.insert(bookmark)

    suspend fun removeBookmark(id: Long) = dao.deleteById(id)

    suspend fun removeByPosition(bookId: String, chapterIndex: Int, charOffset: Int) {
        val existing = dao.findByPosition(bookId, chapterIndex, charOffset)
        if (existing != null) dao.deleteById(existing.bookmarkId)
    }

    suspend fun getBookmarks(bookId: String): List<BookmarkEntity> = dao.getByBookIdList(bookId)

    fun getBookmarksFlow(bookId: String): Flow<List<BookmarkEntity>> = dao.getByBookId(bookId)

    suspend fun deleteByBook(bookId: String) = dao.deleteByBookId(bookId)
}
