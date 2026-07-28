package com.example.reader.engine

import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.example.reader.parser.SpanSpec
import com.example.reader.util.ParagraphSplitter

/**
 * Layout engine that paginates text using Android's native [android.text.StaticLayout]
 * (the same C++ line-breaker Compose's TextMeasurer wraps). It produces the exact same
 * [PageInfo] contract as before, so every downstream consumer
 * ([ReaderPagination.flatten], [ReaderPagination.globalPageOf], bookmarks, search, the pager)
 * is unaffected.
 *
 * The actual line-breaking + page-cutting lives in [PageRenderer]; this class only adapts
 * the original `paginate(...)` signature to the renderer and maps the result back to
 * [PageInfo]s. The [style] parameter is retained for signature compatibility (and to
 * keep the shared [TextStyle]-based call surface stable); the native layout is driven by
 * [cfg] + the pre-resolved [typeface].
 *
 * To keep pagination and rendering pixel-identical, the chapter is first turned into a single
 * display [android.text.SpannableString] (via [RichTextBuilder]) carrying first-line indent,
 * paragraph spacing and rich-style spans. Its backing string is the original chapter text, so
 * the produced [PageRange]s stay in the original coordinate space (bookmarks / search / resume
 * offsets remain valid). That same `display` instance is returned in [ChapterRender] and later
 * handed to [PageRenderer.renderPageBitmap] for baking.
 */
class LayoutEngine(private val density: Density, private val typeface: Typeface) {

    /**
     * Paginates a single chapter's text into pages that fit within the given size.
     *
     * @param text         Full chapter text (original, unmutated).
     * @param style        The [TextStyle] shared with the renderer (kept for signature compatibility).
     * @param maxWidthPx   Maximum inner page width in pixels.
     * @param maxHeightPx  Maximum inner page height in pixels.
     * @param cfg          Reader style configuration (spacing / alignment / font scale).
     * @param chapterIndex Index of the chapter (recorded on each [PageInfo]).
     * @param spans        Rich-text style spans (EPUB headings / bold / italic), empty for TXT.
     * @param paragraphStarts Offsets of each paragraph's first character (for first-line indent).
     * @return [ChapterRender] with the pages and the shared display [CharSequence].
     */
    fun paginate(
        text: String,
        style: TextStyle,
        maxWidthPx: Int,
        maxHeightPx: Int,
        cfg: ReaderStyleConfig,
        chapterIndex: Int = 0,
        spans: List<SpanSpec> = emptyList(),
        paragraphStarts: List<Int> = emptyList()
    ): ChapterRender {
        if (text.isEmpty()) return ChapterRender(chapterIndex, emptyList(), text)
        val paint = PageRenderer.buildTextPaint(cfg, Color.BLACK, density, typeface)
        val display = RichTextBuilder.build(
            text = text,
            signature = cfg.toSignature(),
            spans = spans,
            paragraphStarts = paragraphStarts.toSet()
        )
        val ranges = PageRenderer.paginateChapter(display, paint, maxWidthPx, maxHeightPx, cfg)
        val pages = ranges.map {
            PageInfo(it.start, it.end, text.substring(it.start, it.end), chapterIndex)
        }
        return ChapterRender(chapterIndex, pages, display)
    }
}
