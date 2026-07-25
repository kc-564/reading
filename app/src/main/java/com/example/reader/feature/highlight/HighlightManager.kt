package com.example.reader.feature.highlight

import com.example.reader.db.AppDatabase
import com.example.reader.db.HighlightEntity
import kotlinx.coroutines.flow.Flow

/**
 * Manages highlight (text markup) persistence (F06).
 */
class HighlightManager(private val db: AppDatabase) {

    private val dao = db.highlightDao()

    fun getFlow(bookId: String): Flow<List<HighlightEntity>> = dao.getByBookId(bookId)

    suspend fun add(
        bookId: String,
        chapterIndex: Int,
        startChar: Int,
        endChar: Int,
        colorArgb: Int
    ) {
        dao.insert(
            HighlightEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                startChar = startChar,
                endChar = endChar,
                colorArgb = colorArgb,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(id: Long) = dao.deleteById(id)

    companion object {
        /** Available highlight colors (ARGB ints). */
        val COLORS: List<Int> = listOf(
            0xFFFFEB3B.toInt(), // yellow
            0xFF4CAF50.toInt(), // green
            0xFF2196F3.toInt(), // blue
            0xFFF44336.toInt(), // red
            0xFF9C27B0.toInt()  // purple
        )
    }
}
