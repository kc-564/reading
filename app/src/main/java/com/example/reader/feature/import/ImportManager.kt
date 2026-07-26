package com.example.reader.feature.import

import android.content.Context
import com.example.reader.data.BookRepository
import com.example.reader.db.AppDatabase
import com.example.reader.db.BookEntity
import com.example.reader.parser.EncodingDetector
import com.example.reader.parser.EpubParser
import com.example.reader.parser.LruEncodingCache
import com.example.reader.parser.TxtParser
import com.example.reader.parser.sanitizeBookTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Batch book importer (D03 / C05).
 *
 * Resolves encoding per file (cached), parses chapters, and upserts a [BookEntity] for each
 * successfully imported text file. Archives are extracted first via [ArchiveExtractor].
 * EPUB files are routed to [EpubParser] for lightweight parsing.
 */
class ImportManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val repository = BookRepository(db)
    private val parser = TxtParser()
    private val encodingCache = LruEncodingCache()

    /** Imports a list of already-resolved file paths. Returns the number imported. */
    suspend fun importFiles(paths: List<String>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (path in paths.distinct()) {
            val file = File(path)
            if (!file.exists() || file.isDirectory) continue
            when {
                // EPUB branch — use EpubParser
                file.extension.equals("epub", ignoreCase = true) -> {
                    val result = runCatching {
                        EpubParser.parse(context, file, path)
                    }.getOrNull()
                    if (result == null) {
                        android.util.Log.w("ImportManager", "EPUB parse failed for $path")
                        continue
                    }

                    val totalChars = result.chapters.sumOf { it.charCount.toLong() }
                    val coverPath = result.coverPath
                    val book = BookEntity(
                        bookId = path,
                        filePath = path,
                        fileName = file.name,
                        title = sanitizeBookTitle(result.metadata.title.ifBlank { file.nameWithoutExtension }),
                        author = result.metadata.author,
                        coverUri = coverPath,
                        format = "epub",
                        sizeBytes = file.length(),
                        encoding = StandardCharsets.UTF_8.name(),
                        lastOpenedAt = System.currentTimeMillis(),
                        totalChapters = result.chapters.size,
                        totalChars = totalChars
                    )
                    repository.upsertBook(book)
                    count++
                }
                // TXT branch — existing logic
                else -> {
                    val defaultPref = "UTF-8"
                    val encoding = encodingCache.getOrPut(path, defaultPref) {
                        EncodingDetector.detect(path, defaultPref) ?: StandardCharsets.UTF_8
                    }
                    val chapters = runCatching {
                        parser.parse(path, encoding)
                    }.getOrElse { emptyList() }
                    val totalChars = chapters.sumOf { it.totalCharCount.toLong() }
                    val book = BookEntity(
                        bookId = path,
                        filePath = path,
                        fileName = file.name,
                        title = sanitizeBookTitle(file.nameWithoutExtension),
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
            }
        }
        count
    }

    /** Extracts archives (zip/rar) then imports contained text/epub files. */
    suspend fun importArchives(paths: List<String>): Int {
        val extractor = ArchiveExtractor()
        val extracted = extractor.extract(paths)
        return importFiles(extracted)
    }
}
