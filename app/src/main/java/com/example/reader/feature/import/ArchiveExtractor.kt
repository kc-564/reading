package com.example.reader.feature.import

import android.util.Log
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Extracts text files from zip / rar archives (D03).
 * EPUB files are passed through as-is (parsed later by [EpubParser]).
 *
 * - zip: JDK `java.util.zip` (no extra dependency).
 * - rar: `com.github.anjoze:junrar` (best-effort; rar5 is unsupported and will be skipped on
 *   failure — the caller still imports any zip/plain files).
 * - epub: passed through directly (parsed by [EpubParser]).
 *
 * Extracted files land in a temp directory; the returned list feeds [ImportManager].
 */
class ArchiveExtractor {

    fun extract(paths: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (path in paths) {
            val file = File(path)
            when (file.extension.lowercase()) {
                "zip" -> extractZip(file, out)
                "rar" -> extractRar(file, out)
                "epub" -> if (file.isFile) out.add(path) // pass-through; EpubParser handles internally
                else -> if (file.isFile) out.add(path)
            }
        }
        return out
    }

    private fun tempDir(): File {
        val base = System.getProperty("java.io.tmpdir")?.let { File(it) } ?: File("/tmp")
        val dir = File(base, "reader_extract_${System.currentTimeMillis()}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun extractZip(file: File, out: MutableList<String>) {
        runCatching {
            val dir = tempDir()
            ZipInputStream(file.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".txt", ignoreCase = true)) {
                        val target = File(dir, "${System.nanoTime()}_${File(entry.name).name}")
                        FileOutputStream(target).use { os -> zis.copyTo(os) }
                        out.add(target.absolutePath)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }.onFailure { Log.w("ArchiveExtractor", "zip extract failed: ${it.message}") }
    }

    private fun extractRar(file: File, out: MutableList<String>) {
        runCatching {
            val dir = tempDir()
            val archive = Archive(file)
            try {
                for (header in archive.fileHeaders) {
                    val name = header.fileName
                    if (name != null && name.endsWith(".txt", ignoreCase = true)) {
                        val target = File(dir, "${System.nanoTime()}_${File(name).name}")
                        FileOutputStream(target).use { os -> archive.extractFile(header, os) }
                        out.add(target.absolutePath)
                    }
                }
            } finally {
                runCatching { archive.close() }
            }
        }.onFailure { Log.w("ArchiveExtractor", "rar extract failed: ${it.message}") }
    }
}
