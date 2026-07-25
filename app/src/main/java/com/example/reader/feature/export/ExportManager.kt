package com.example.reader.feature.export

import android.content.Context
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports reader data (bookmarks, etc.) to shareable text files (F03).
 */
class ExportManager(private val context: Context) {

    /** Exports bookmarks to a text file under `cacheDir/exports`. Returns the file path or null. */
    suspend fun exportBookmarks(
        bookId: String,
        bookmarks: List<BookmarkEntity>
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "exports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "bookmarks_${bookId.hashCode().toString(36)}.txt")
            file.bufferedWriter().use { w ->
                w.write("书签导出\n")
                w.write("====================\n")
                if (bookmarks.isEmpty()) {
                    w.write("(暂无书签)\n")
                } else {
                    bookmarks.forEachIndexed { i, bm ->
                        w.write("${i + 1}. [第${bm.chapterIndex + 1}章] ${bm.previewText}\n")
                    }
                }
            }
            file.absolutePath
        }.getOrNull()
    }

    /** Exports arbitrary text content (e.g. a search result set) to a file. */
    suspend fun exportText(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "exports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content)
            file.absolutePath
        }.getOrNull()
    }
}
