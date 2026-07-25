package com.example.reader.feature.search

import com.example.reader.parser.Chapter

/**
 * A single full-text search hit.
 *
 * @property chapterIndex Index of the chapter containing the hit.
 * @property charOffset   Character offset of the match inside the chapter text.
 * @property previewText  A short snippet around the match (newlines replaced by spaces).
 */
data class SearchHit(
    val chapterIndex: Int,
    val charOffset: Int,
    val previewText: String
)

/**
 * Linear, chapter-level full-text search with surrounding context (D02).
 *
 * Case-insensitive. Returns every occurrence with a snippet window of [context] characters
 * on each side. No FTS index is used — the book is already fully in memory.
 */
class SearchEngine {

    fun search(chapters: List<Chapter>, query: String, context: Int = 24): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        val hits = mutableListOf<SearchHit>()
        for ((ci, chapter) in chapters.withIndex()) {
            val content = chapter.getContent()
            if (content.isEmpty()) continue
            val lower = content.lowercase()
            var from = 0
            while (from <= lower.length - q.length) {
                val idx = lower.indexOf(q, from)
                if (idx < 0) break
                val start = (idx - context).coerceAtLeast(0)
                val end = (idx + q.length + context).coerceAtMost(content.length)
                val preview = buildString {
                    if (start > 0) append("…")
                    append(content.substring(start, end).replace('\n', ' ').replace('\r', ' '))
                    if (end < content.length) append("…")
                }
                hits.add(SearchHit(chapterIndex = ci, charOffset = idx, previewText = preview))
                from = idx + q.length
            }
        }
        return hits
    }
}
