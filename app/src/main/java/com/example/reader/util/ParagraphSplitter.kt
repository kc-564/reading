package com.example.reader.util

/**
 * Splits running text into paragraphs using one-or-more blank lines as separators.
 *
 * Consecutive blank lines are collapsed (deduped) so they never produce empty paragraphs.
 * Each produced [Paragraph] keeps its **original** character span (`start`/`end`) so that the
 * layout engine can map page breaks back to offsets in the source text.
 *
 * The pure helper [groupIntoPages] performs page packing from estimated paragraph heights
 * without any Android dependency and is exercised by [com.example.reader.engine] unit tests.
 */
object ParagraphSplitter {

    /**
     * A single paragraph of text.
     *
     * @property text  Paragraph content (non-blank lines joined by `\n`).
     * @property start Inclusive character index in the source text where the paragraph begins.
     * @property end   Exclusive character index in the source text where the paragraph ends.
     * @property isBlank Always `false` — blank paragraphs are dropped by [split].
     */
    data class Paragraph(
        val text: String,
        val start: Int,
        val end: Int,
        val isBlank: Boolean = false
    )

    /**
     * Splits [text] into paragraphs. Returns an empty list for empty input.
     */
    fun split(text: String): List<Paragraph> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<Paragraph>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (isBlankLine(text, i)) {
                i = nextLineStart(text, i)
                continue
            }
            val paraStart = i
            val sb = StringBuilder()
            var paraEnd = i
            var first = true
            while (i < n && !isBlankLine(text, i)) {
                val end = lineEnd(text, i)
                if (!first) sb.append('\n')
                sb.append(text.substring(i, end))
                first = false
                paraEnd = end
                i = nextLineStart(text, i)
            }
            result.add(Paragraph(text = sb.toString(), start = paraStart, end = paraEnd, isBlank = false))
        }
        return result
    }

    /**
     * Returns the character offset of every paragraph's first character, in ascending order.
     * Memoizable by callers that need to know which pages start a paragraph (for first-line
     * indent).
     */
    fun paragraphStartOffsets(text: String): List<Int> = split(text).map { it.start }

    /**
     * Greedy page packing from estimated paragraph heights. Pure and JVM-testable.
     *
     * A new page is started when adding the next paragraph (plus its inter-paragraph spacing)
     * would exceed [pageHeightPx]. Paragraphs already placed on a page carry no leading spacing;
     * every subsequent paragraph adds [paragraphSpacingPx].
     *
     * @return A list of index ranges into [paragraphHeightsPx], one per page.
     */
    fun groupIntoPages(
        paragraphHeightsPx: List<Int>,
        pageHeightPx: Int,
        paragraphSpacingPx: Int
    ): List<IntRange> {
        if (paragraphHeightsPx.isEmpty()) return emptyList()
        val pages = mutableListOf<IntRange>()
        var pageStart = 0
        var y = 0
        for (idx in paragraphHeightsPx.indices) {
            val spacing = if (idx == pageStart) 0 else paragraphSpacingPx
            val needed = y + spacing + paragraphHeightsPx[idx]
            if (needed > pageHeightPx && idx > pageStart) {
                pages.add(pageStart until idx)
                pageStart = idx
                y = paragraphHeightsPx[idx]
            } else {
                y = needed
            }
        }
        if (pageStart <= paragraphHeightsPx.lastIndex) {
            pages.add(pageStart..paragraphHeightsPx.lastIndex)
        }
        return pages
    }

    // ── Line helpers (operate on the source text) ──

    private fun lineEnd(text: String, start: Int): Int {
        val nl = text.indexOf('\n', start)
        return if (nl < 0) text.length else nl
    }

    private fun nextLineStart(text: String, from: Int): Int {
        val nl = text.indexOf('\n', from)
        return if (nl < 0) text.length else nl + 1
    }

    private fun isBlankLine(text: String, start: Int): Boolean {
        val end = lineEnd(text, start)
        for (k in start until end) {
            if (!text[k].isWhitespace()) return false
        }
        return true
    }
}
