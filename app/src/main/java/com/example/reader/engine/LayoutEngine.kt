package com.example.reader.engine

import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density

/**
 * Layout engine that paginates text using Android's native [android.text.StaticLayout]
 * (the same C++ line-breaker Compose's TextMeasurer wraps). It produces the exact same
 * [PageInfo] contract as before, so every downstream consumer
 * ([ReaderPagination.flatten], [ReaderPagination.globalPageOf], bookmarks, search, the pager)
 * is unaffected.
 *
 * The actual line-breaking + page cutting lives in [PageRenderer]; this class only adapts
 * the original `paginate(...)` signature to the renderer and maps the result back to
 * [PageInfo]s. The [style] parameter is retained for signature compatibility (and to
 * keep the shared [TextStyle]-based call surface stable); the native layout is driven by
 * [cfg] + the pre-resolved [typeface].
 */
class LayoutEngine(private val density: Density, private val typeface: Typeface) {

    /**
     * Paginates a single chapter's text into pages that fit within the given size.
     *
     * @param text         Full chapter text.
     * @param style        The [TextStyle] shared with the renderer (kept for signature compatibility).
     * @param maxWidthPx   Maximum inner page width in pixels.
     * @param maxHeightPx  Maximum inner page height in pixels.
     * @param cfg          Reader style configuration (spacing / alignment / font scale).
     * @param chapterIndex Index of the chapter (recorded on each [PageInfo]).
     * @return Pages; empty if [text] is blank.
     */
    fun paginate(
        text: String,
        style: TextStyle,
        maxWidthPx: Int,
        maxHeightPx: Int,
        cfg: ReaderStyleConfig,
        chapterIndex: Int = 0
    ): List<PageInfo> {
        if (text.isEmpty()) return emptyList()
        val paint = PageRenderer.buildTextPaint(cfg, Color.BLACK, density, typeface)
        val ranges = PageRenderer.paginateChapter(text, paint, maxWidthPx, maxHeightPx, cfg)
        return ranges.map { PageInfo(it.start, it.end, text.substring(it.start, it.end), chapterIndex) }
    }
}
