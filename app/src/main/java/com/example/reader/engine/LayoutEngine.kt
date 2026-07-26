package com.example.reader.engine

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.example.reader.util.ParagraphSplitter

/**
 * Layout engine that paginates text using Compose's [TextMeasurer].
 *
 * Enhancements over Phase 1 (D06/D08):
 * - Paragraph-level measurement: the chapter is first split into paragraphs by
 *   [ParagraphSplitter]; each paragraph is measured once and packed greedily.
 * - Exact pixel paragraph spacing ([ReaderStyleConfig.paragraphSpacingPx]) is injected
 *   between paragraphs — never approximated via line-height multipliers.
 * - First-line indent ([ReaderStyleConfig.firstLineIndentPx]) is applied at render time.
 * - The **same** [TextStyle] instance is used here (measurement) and in
 *   [com.example.reader.ui.reader.ReaderPage] (rendering) so line breaks never diverge.
 * - Oversized paragraphs (taller than a full page, e.g. an EPUB chapter that was collapsed
 *   to a single line) are recursively split into character-range pieces so they can be
 *   broken across pages instead of producing one unbreakable, overflowing "page".
 *
 * Must be created inside composition (via `rememberTextMeasurer()`) so that the measurer
 * can resolve [androidx.compose.ui.unit.Density] and font resolution from composition locals.
 */
class LayoutEngine(private val textMeasurer: TextMeasurer) {

    /**
     * Paginates a single chapter's text into pages that fit within the given size.
     *
     * @param text            Full chapter text.
     * @param style           The unique [TextStyle] shared with rendering.
     * @param maxWidthPx      Maximum page width in pixels.
     * @param maxHeightPx     Maximum page height in pixels.
     * @param cfg             Reader style configuration (spacing / indent).
     * @param chapterIndex    Index of the chapter (recorded on each [PageInfo]).
     * @param titleReservePx  Height reserved for the chapter title on the first page of the chapter.
     * @return Pages; empty if [text] is blank.
     */
    fun paginate(
        text: String,
        style: TextStyle,
        maxWidthPx: Int,
        maxHeightPx: Int,
        cfg: ReaderStyleConfig,
        chapterIndex: Int = 0,
        titleReservePx: Int = 0
    ): List<PageInfo> {
        if (text.isEmpty()) return emptyList()
        val maxWidth = maxWidthPx.coerceAtLeast(1)
        val constraints = Constraints(maxWidth = maxWidth)
        // Height a single paragraph may occupy before we force-split it across pages.
        val usableHeight = (maxHeightPx - cfg.paragraphSpacingPx).coerceAtLeast(1)

        // 1. Split into paragraphs, then split any paragraph that is taller than a page so it
        //    can actually be broken across pages. Without this, an unbreakable single block
        //    (e.g. an EPUB chapter collapsed to one line) would become a single oversized
        //    "page" that overflows the screen and can never be turned — the classic
        //    "opens once, then hangs on layout" symptom.
        val baseParagraphs = ParagraphSplitter.split(text)
        if (baseParagraphs.isEmpty()) return emptyList()
        val paragraphs = expandOversized(baseParagraphs, style, constraints, usableHeight)

        // 2. Measure each (possibly expanded) paragraph once and record original char offsets.
        val paraHeights = IntArray(paragraphs.size)
        val paraStart = IntArray(paragraphs.size)
        for (i in paragraphs.indices) {
            paraStart[i] = paragraphs[i].start
            paraHeights[i] = measureHeight(paragraphs[i].text, style, constraints)
        }

        // 3. Greedy page packing with exact pixel spacing.
        val pages = mutableListOf<PageInfo>()
        var pageStart = 0
        var y = 0
        for (i in paragraphs.indices) {
            val spacing = if (i == pageStart) 0 else cfg.paragraphSpacingPx
            val available = if (pageStart == 0) (maxHeightPx - titleReservePx).coerceAtLeast(1) else maxHeightPx
            val needed = y + spacing + paraHeights[i]
            if (needed > available && i > pageStart) {
                pages.add(buildPage(paragraphs, paraStart, pageStart, i, chapterIndex))
                pageStart = i
                y = paraHeights[i]
            } else {
                y = needed
            }
        }
        if (pageStart < paragraphs.size) {
            pages.add(buildPage(paragraphs, paraStart, pageStart, paragraphs.size, chapterIndex))
        }
        return pages
    }

    /**
     * Returns the measured pixel height of [text] under [style]/[constraints], clamped to >= 1.
     */
    private fun measureHeight(text: String, style: TextStyle, constraints: Constraints): Int {
        val h = textMeasurer.measure(text = text, style = style, constraints = constraints).size.height
        return if (h <= 0) 1 else h
    }

    /**
     * Flattens [paragraphs] so that no single paragraph is taller than [usableHeight]. Any
     * oversized paragraph is recursively cut into smaller character ranges (preserving the
     * original character offsets) until every piece fits — guaranteeing the greedy packer can
     * place it on one or more pages instead of producing one unbreakable "page".
     */
    private fun expandOversized(
        paragraphs: List<ParagraphSplitter.Paragraph>,
        style: TextStyle,
        constraints: Constraints,
        usableHeight: Int
    ): List<ParagraphSplitter.Paragraph> {
        val minStep = 64
        val out = mutableListOf<ParagraphSplitter.Paragraph>()
        for (p in paragraphs) {
            splitToFit(p, style, constraints, usableHeight, minStep, out)
        }
        return out
    }

    private fun splitToFit(
        p: ParagraphSplitter.Paragraph,
        style: TextStyle,
        constraints: Constraints,
        usableHeight: Int,
        minStep: Int,
        out: MutableList<ParagraphSplitter.Paragraph>
    ) {
        val h = measureHeight(p.text, style, constraints)
        if (h <= usableHeight || p.text.length <= minStep) {
            out.add(p)
            return
        }
        // Roughly how many equal pieces are needed for this paragraph to fit one-per-page.
        val pieces = max(2, (h / usableHeight) + 1)
        val step = (p.text.length / pieces).coerceAtLeast(minStep)
        var start = 0
        while (start < p.text.length) {
            val end = (start + step).coerceAtMost(p.text.length)
            val sub = ParagraphSplitter.Paragraph(
                text = p.text.substring(start, end),
                start = p.start + start,
                end = p.start + end
            )
            splitToFit(sub, style, constraints, usableHeight, minStep, out)
            start = end
        }
    }

    private fun buildPage(
        paragraphs: List<ParagraphSplitter.Paragraph>,
        paraStart: IntArray,
        start: Int,
        endExclusive: Int,
        chapterIndex: Int
    ): PageInfo {
        val startChar = paraStart[start]
        val lastIdx = endExclusive - 1
        val endChar = paragraphs[lastIdx].end
        val sb = StringBuilder()
        for (k in start until endExclusive) {
            if (k > start) sb.append('\n')
            sb.append(paragraphs[k].text)
        }
        return PageInfo(
            startCharIndex = startChar,
            endCharIndex = endChar,
            text = sb.toString(),
            chapterIndex = chapterIndex,
            paragraphIndex = start
        )
    }
}
