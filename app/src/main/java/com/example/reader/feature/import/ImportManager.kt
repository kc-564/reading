package com.example.reader.feature.import

import android.content.Context
import com.example.reader.data.BookRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.parser.EncodingDetector
import com.example.reader.parser.LruEncodingCache
import com.example.reader.parser.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charsets

/**
 * Batch book importer (D03 / C05).
 *
 * Resolves encoding per file (cached), parses chapters, and upserts a [BookEntity] for each
 * successfully imported text file. Archives are extracted first via [ArchiveExtractor].
 */
class ImportManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val repository = BookRepository(db)
    private val parser = TxtParser()
    private val encodingCache = LruEncodingCache()

    /** Imports a list of already-resolved text-file paths. Returns the number imported. */
    suspend fun importFiles(paths: List<String>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (path in paths.distinct()) {
            val file = File(path)
            if (!file.exists() || file.isDirectory) continue
            val defaultPref = "UTF-8"
            val encoding = encodingCache.getOrPut(path, defaultPref) {
                EncodingDetector.detect(path, defaultPref) ?: Charsets.UTF_8
            }
            val chapters = runCatching { parser.parse(path, encoding) }.getOrElse { emptyList() }
            val totalChars = chapters.sumOf { it.totalCharCount.toLong() }
            val book = BookEntity(
                bookId = path,
                filePath = path,
                fileName = file.name,
                title = file.nameWithoutExtension,
                format = file.extension.lowercase().ifBlank { "txt" },
                sizeBytes = file.length(),
                encoding = encoding.name(),
                lastOpenedAt = System.currentTimeMillis(),
                totalChapters = chapters.size,
                totalChars = totalChars
            )
            repository.upsertBook(book)
            count++
        }
        count
    }

    /** Extracts archives (zip/rar) then imports contained text files. */
    suspend fun importArchives(paths: List<String>): Int {
        val extractor = ArchiveExtractor()
        val extracted = extractor.extract(paths)
        return importFiles(extracted)
    }
}
