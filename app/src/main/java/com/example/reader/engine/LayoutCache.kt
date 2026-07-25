package com.example.reader.engine

import com.example.reader.db.LayoutCacheDao
import com.example.reader.db.LayoutCacheEntity
import java.security.MessageDigest

/**
 * Reads/writes pagination results to the [LayoutCacheEntity] table (C07).
 *
 * The cached payload is *only* the per-chapter character ranges (compact JSON), never the
 * full text. The cache key is `sha1(fingerprint + layoutHash + screenSize)`; [themeMode] is
 * intentionally excluded from [ReaderStyleConfig.layoutHash] so a theme switch does not
 * invalidate a perfectly good layout.
 *
 * All read/write paths are wrapped in `runCatching` by callers; a corrupt entry is treated
 * as a miss and the caller re-paginates (self-healing).
 */
class LayoutCache(private val dao: LayoutCacheDao) {

    /**
     * Builds the cache key.
     */
    fun buildKey(fingerprint: String, cfg: ReaderStyleConfig, maxWidthPx: Int, maxHeightPx: Int): String {
        val raw = "$fingerprint|${cfg.layoutHash()}|${maxWidthPx}x${maxHeightPx}"
        return sha1(raw)
    }

    /**
     * Returns the cached per-chapter ranges, or null on miss / parse error.
     */
    suspend fun get(key: String): List<List<Pair<Int, Int>>>? {
        val entity = dao.getByKey(key) ?: return null
        return runCatching { deserialize(entity.pagesJson) }.getOrNull()
    }

    /**
     * Persists per-chapter ranges. Swallows errors (cache is best-effort).
     */
    suspend fun put(key: String, bookId: String, ranges: List<List<Pair<Int, Int>>>) {
        runCatching {
            dao.insert(LayoutCacheEntity(key, bookId, serialize(ranges), System.currentTimeMillis()))
        }
    }

    suspend fun clearForBook(bookId: String) = runCatching { dao.deleteByBookId(bookId) }

    // ── Serialization ──
    // Format: JSON array of arrays of [start,end] pairs, e.g. [[0,120],[120,240]]
    // No external JSON library is used.

    private fun serialize(ranges: List<List<Pair<Int, Int>>>): String {
        val sb = StringBuilder()
        sb.append('[')
        ranges.forEachIndexed { ci, chapter ->
            if (ci > 0) sb.append(',')
            sb.append('[')
            chapter.forEachIndexed { pi, pair ->
                if (pi > 0) sb.append(',')
                sb.append('[').append(pair.first).append(',').append(pair.second).append(']')
            }
            sb.append(']')
        }
        sb.append(']')
        return sb.toString()
    }

    private fun deserialize(json: String): List<List<Pair<Int, Int>>> {
        // Strip outer brackets.
        val trimmed = json.trim()
        if (trimmed.length < 2 || trimmed.first() != '[' || trimmed.last() != ']') return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1)
        if (inner.isBlank()) return emptyList()

        val chapters = mutableListOf<List<Pair<Int, Int>>>()
        var i = 0
        val n = inner.length
        while (i < n) {
            // Find next '[' (start of a chapter).
            while (i < n && inner[i] != '[') i++
            if (i >= n) break
            // Find matching ']'.
            val end = inner.indexOf(']', i)
            if (end < 0) break
            val chapterBody = inner.substring(i + 1, end)
            chapters.add(parsePairs(chapterBody))
            i = end + 1
        }
        return chapters
    }

    private fun parsePairs(body: String): List<Pair<Int, Int>> {
        if (body.isBlank()) return emptyList()
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        val n = body.length
        while (i < n) {
            while (i < n && body[i] != '[') i++
            if (i >= n) break
            val end = body.indexOf(']', i)
            if (end < 0) break
            val pairBody = body.substring(i + 1, end)
            val comma = pairBody.indexOf(',')
            if (comma > 0) {
                val a = pairBody.substring(0, comma).toIntOrNull()
                val b = pairBody.substring(comma + 1).toIntOrNull()
                if (a != null && b != null) pairs.add(a to b)
            }
            i = end + 1
        }
        return pairs
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1))
        }
        return sb.toString()
    }
}
