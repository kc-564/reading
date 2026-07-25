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
        val paragraphs = ParagraphSplitter.split(text)
        if (paragraphs.isEmpty()) return emptyList()

        val maxWidth = maxWidthPx.coerceAtLeast(1)
        val constraints = Constraints(maxWidth = maxWidth)

        // Measure each paragraph once (O(n) total) and record original char offsets.
        val paraHeights = IntArray(paragraphs.size)
        val paraStart = IntArray(paragraphs.size)
        for (i in paragraphs.indices) {
            paraStart[i] = paragraphs[i].start
            val h = textMeasurer.measure(text = paragraphs[i].text, style = style, constraints = constraints)
                .size.height
            paraHeights[i] = if (h <= 0) 1 else h
        }

        // Greedy page packing with exact pixel spacing.
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
