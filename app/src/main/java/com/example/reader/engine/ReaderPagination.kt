package com.example.reader.engine

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.example.reader.parser.Chapter

/**
 * Book-level pagination service (C01/C07).
 *
 * Responsibilities:
 * - Paginate every chapter into pages ([paginateBook]), producing a [BookPagination].
 * - Map a `(chapterIndex, charOffset)` position to a global (cross-chapter) page index
 *   ([globalPageOf]) and to a book-wide percentage ([globalPercentOf]).
 * - Flatten per-chapter pages into a single list of [GlobalPage] for the pager.
 * - Honour the layout cache: when a cache entry exists it skips re-measurement and rebuilds
 *   pages from the stored character ranges.
 *
 * Invariant: re-paginating after a style change keeps the *character offset*; callers must
 * use [globalPageOf] with the retained offset so reading never jumps back to page 0.
 */
class ReaderPagination {

    /**
     * Paginates the whole book.
     *
     * @param cache        Optional layout cache; a hit skips measurement entirely.
     * @param bookId       Book id used when writing cache entries.
     * @param fingerprint  File fingerprint used to build the cache key.
     */
    suspend fun paginateBook(
        chapters: List<Chapter>,
        style: TextStyle,
        maxWidthPx: Int,
        maxHeightPx: Int,
        cfg: ReaderStyleConfig,
        measurer: TextMeasurer,
        cache: LayoutCache? = null,
        bookId: String = "",
        fingerprint: String = ""
    ): BookPagination {
        val key = if (cache != null) cache.buildKey(fingerprint, cfg, maxWidthPx, maxHeightPx) else null
        val cached = if (cache != null && key != null) {
            runCatching { cache.get(key) }.getOrNull()
        } else null

        val useCache = cached != null && cached.size == chapters.size
        val chapterPages: List<ChapterPages> = if (useCache && cached != null) {
            chapters.mapIndexed { idx, ch ->
                val content = ch.getContent()
                val ranges = cached[idx]
                val pages = ranges.map { (s, e) ->
                    val end = e.coerceAtMost(content.length)
                    val start = s.coerceAtMost(end)
                    PageInfo(start, end, content.substring(start, end), idx)
                }
                ChapterPages(idx, pages)
            }
        } else {
            val engine = LayoutEngine(measurer)
            chapters.mapIndexed { idx, ch ->
                ChapterPages(idx, engine.paginate(ch.getContent(), style, maxWidthPx, maxHeightPx, cfg, idx))
            }
        }

        val perChapterPageCounts = chapterPages.map { it.pages.size }
        val totalPages = perChapterPageCounts.sum()

        if (cache != null && key != null && !useCache) {
            val ranges = chapterPages.map { cp -> cp.pages.map { it.startCharIndex to it.endCharIndex } }
            runCatching { cache.put(key, bookId, ranges) }
        }

        return BookPagination(chapterPages, perChapterPageCounts, totalPages)
    }

    /**
     * Resolves the global (cross-chapter) page index for the given chapter + char offset.
     */
    fun globalPageOf(book: BookPagination, chapterIndex: Int, charOffset: Int): Int {
        var acc = 0
        for (cp in book.chapters) {
            if (cp.chapterIndex == chapterIndex) {
                if (cp.pages.isEmpty()) return acc
                val local = cp.pages.indexOfFirst { charOffset in it.startCharIndex until it.endCharIndex }
                return acc + if (local >= 0) local else cp.pages.lastIndex
            }
            acc += cp.pages.size
        }
        return 0
    }

    /**
     * Book-wide reading percentage (0..1) for a chapter + char offset.
     */
    fun globalPercentOf(
        book: BookPagination,
        chapterIndex: Int,
        charOffset: Int,
        totalChars: Long
    ): Float {
        if (totalChars <= 0) return 0f
        val globalPageIndex = globalPageOf(book, chapterIndex, charOffset)
        val prevChars = book.chapters.take(chapterIndex).sumOf { ch ->
            ch.pages.lastOrNull()?.endCharIndex?.toLong() ?: 0L
        }
        val offsetInBook = prevChars + charOffset.toLong()
        return (offsetInBook.toFloat() / totalChars.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Flattens per-chapter pages into a single ordered list of [GlobalPage].
     */
    fun flatten(book: BookPagination): List<GlobalPage> {
        val out = mutableListOf<GlobalPage>()
        var g = 0
        for (cp in book.chapters) {
            cp.pages.forEachIndexed { local, page ->
                out.add(GlobalPage(g, cp.chapterIndex, local, page.startCharIndex, page.endCharIndex, page.text))
                g++
            }
        }
        return out
    }
}
