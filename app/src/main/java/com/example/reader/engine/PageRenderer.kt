package com.example.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.comparisons.maxOf

/**
 * Inclusive-exclusive character range of a single paginated page, in chapter-text coordinates.
 */
data class PageRange(val start: Int, val end: Int)

/**
 * Native, GPU-friendly pagination + pre-rendering for the reader surface.
 *
 * Why this module exists (performance): the previous renderer laid out every page with
 * Compose's [androidx.compose.ui.text.TextMeasurer] and a `BasicText` per page. That
 * re-measured and re-composed the whole page on every frame of a scroll/fling, which is
 * exactly what made page turns feel janky. Here we use Android's own text engine —
 * [StaticLayout] (the same C++ line-breaker Compose's TextMeasurer wraps) — to compute the
 * exact character range that fits on each page, and then pre-render each page to an
 * [Bitmap] (acquired from [BitmapPool] so allocation stays flat). The pager then flips by
 * blitting those baked bitmaps (GPU-composited), so turning a page is a pure image swap with
 * zero text re-measurement.
 *
 * [paginateChapter] and [renderPageBitmap] deliberately share the SAME paint + width +
 * line-spacing + alignment + break strategy + hyphenation, and both call `setIncludePad` with
 * the same value, so a page's baked bitmap is pixel-identical to the character range that
 * [paginateChapter] cut out of the chapter — no clipped or overflowing lines.
 *
 * Both functions accept a [CharSequence] (typically a [android.text.SpannableString] built by
 * [RichTextBuilder] carrying first-line indent / paragraph spacing / rich-style spans). Because
 * the spannable's backing string is the original chapter text, character offsets are unchanged
 * and the same instance can drive both pagination and rendering.
 */
object PageRenderer {

    /**
     * Builds the [TextPaint] that drives [StaticLayout]. All sizes are resolved to **pixels**
     * (via [density]) so the native layout engine measures identically to what we later draw.
     *
     * @param style      Reader typography config (font scale, line spacing, letter spacing, alignment).
     * @param textColor  ARGB color the glyphs are painted with (ignored for pagination, used when baking).
     * @param density    Density used to convert `sp` units to pixels.
     * @param typeface   Resolved native [Typeface] (built-in family or an imported TTF/OTF).
     */
    fun buildTextPaint(
        style: ReaderStyleConfig,
        textColor: Int,
        density: Density,
        typeface: Typeface
    ): TextPaint {
        val textSizePx = with(density) { (ReaderStyleConfig.BASE_FONT_SP.sp * style.fontScale).toPx() }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG)
        paint.typeface = typeface
        paint.textSize = textSizePx
        paint.color = textColor
        paint.isAntiAlias = true
        // TextStyle stores letterSpacing in `sp` (~px); TextPaint expects em units, so convert.
        paint.letterSpacing = if (style.letterSpacing > 0f) {
            style.letterSpacing / (ReaderStyleConfig.BASE_FONT_SP * style.fontScale)
        } else 0f
        paint.textAlign = android.graphics.Paint.Align.LEFT
        return paint
    }

    /**
     * Paginates a whole chapter into per-page character ranges using a single [StaticLayout].
     *
     * The entire chapter is laid out once at the page width; we then walk its lines
     * top-to-bottom and cut a new page whenever the next line would cross the page's
     * remaining height. Because the cut is always on a line boundary, a page that spills
     * across a paragraph simply continues on the next page (matching real-book flow).
     *
     * @param text          Full chapter text (a [CharSequence], usually a spannable carrying indent
     *                      / spacing / style spans). Its backing string is the original chapter text.
     * @param paint         Paint produced by [buildTextPaint] (same instance reused for rendering).
     * @param pageWidthPx   Inner text width in px (already excludes horizontal page margins).
     * @param pageHeightPx  Inner text height in px (already excludes vertical page padding).
     * @param cfg           Reader config supplying [ReaderStyleConfig.lineSpacing] / alignment.
     * @param titleReservePx Height reserved for a chapter title on the first page (0 when unused).
     * @return Ordered page ranges; empty if [text] is blank.
     */
    fun paginateChapter(
        text: CharSequence,
        paint: TextPaint,
        pageWidthPx: Int,
        pageHeightPx: Int,
        cfg: ReaderStyleConfig,
        titleReservePx: Int = 0
    ): List<PageRange> {
        if (text.isEmpty()) return emptyList()
        val width = maxOf(1, pageWidthPx)
        val height = maxOf(1, pageHeightPx)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setLineSpacing(0f, cfg.lineSpacing)
            .setAlignment(mapAlignment(cfg.alignment))
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setIncludePad(false)
            .apply {
                if (cfg.alignment == TextAlign.Justify) {
                    setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
                }
            }
            .build()

        val ranges = mutableListOf<PageRange>()
        val lineCount = layout.lineCount
        if (lineCount == 0) return emptyList()

        var pageStartLine = 0
        var usedHeight = 0
        var limit = if (titleReservePx > 0) height - titleReservePx else height

        for (i in 0 until lineCount) {
            val lineHeight = layout.getLineBottom(i) - layout.getLineTop(i)
            if (usedHeight + lineHeight > limit && i > pageStartLine) {
                val start = layout.getLineStart(pageStartLine)
                val end = layout.getLineEnd(i - 1)
                if (start < end) ranges.add(PageRange(start, end))
                pageStartLine = i
                usedHeight = lineHeight
                limit = height
            } else {
                usedHeight += lineHeight
            }
        }
        if (pageStartLine < lineCount) {
            val start = layout.getLineStart(pageStartLine)
            val end = layout.getLineEnd(lineCount - 1)
            if (start < end) ranges.add(PageRange(start, end))
        }
        return ranges
    }

    /**
     * Renders one page's character range to an [Bitmap] using the exact same layout
     * parameters as [paginateChapter], so the bitmap reproduces that range's lines
     * pixel-for-pixel. The bitmap is acquired from [BitmapPool] (reused across pages) and
     * fully erased to [bgColor] first, so a recycled/pooled bitmap is never left with ghost
     * content (important for transparent texture backgrounds).
     *
     * Safe to call off the main thread (off-screen [Canvas]); the returned bitmap is then
     * handed to Compose's image pipeline on the UI thread.
     *
     * @param text        Full chapter text (the range is a substring of this).
     * @param range       Character range to render (from [paginateChapter]).
     * @param paint       Same paint instance used during pagination.
     * @param pageWidthPx Inner text width in px.
     * @param pageHeightPx Inner text height in px.
     * @param bgColor     ARGB background fill. Pass a transparent color to let a paper/wood/linen
     *                    texture (drawn behind the bitmap) show through.
     * @param cfg         Reader config (line spacing / alignment) — must match pagination.
     */
    fun renderPageBitmap(
        text: CharSequence,
        range: PageRange,
        paint: TextPaint,
        pageWidthPx: Int,
        pageHeightPx: Int,
        bgColor: Int,
        cfg: ReaderStyleConfig
    ): Bitmap {
        val width = maxOf(1, pageWidthPx)
        val height = maxOf(1, pageHeightPx)
        val bitmap = BitmapPool.acquire(width, height, Bitmap.Config.ARGB_8888)
        // Fully reset (incl. transparent) so a reused pooled bitmap shows no ghosts.
        bitmap.eraseColor(bgColor)

        val start = range.start.coerceAtLeast(0)
        val end = range.end.coerceAtMost(text.length).coerceAtLeast(start)
        if (end > start) {
            val sub = text.subSequence(start, end)
            val layout = StaticLayout.Builder.obtain(sub, 0, sub.length, paint, width)
                .setLineSpacing(0f, cfg.lineSpacing)
                .setAlignment(mapAlignment(cfg.alignment))
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .setIncludePad(false)
                .apply {
                    if (cfg.alignment == TextAlign.Justify) {
                        setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
                    }
                }
                .build()
            layout.draw(Canvas(bitmap))
        }
        return bitmap
    }

    /** Maps Compose [TextAlign] to a native [Layout.Alignment]. */
    private fun mapAlignment(align: TextAlign): Layout.Alignment = when (align) {
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
        TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }
}
