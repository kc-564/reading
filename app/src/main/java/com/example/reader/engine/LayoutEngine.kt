package com.example.reader.engine

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * Layout engine that paginates text using Compose's [TextMeasurer].
 *
 * The engine splits a continuous block of text into pages that fit within
 * the given width/height constraints. Lines are never broken mid-text —
 * only whole lines are included in each page.
 */
class LayoutEngine(private val textMeasurer: TextMeasurer) {

    /**
     * Paginates the given [text] into a list of [PageInfo] entries.
     *
     * @param text        The full text content to paginate.
     * @param style       The [TextStyle] to use for layout measurement.
     * @param maxWidthPx  Maximum page width in pixels.
     * @param maxHeightPx Maximum page height in pixels.
     * @return A list of [PageInfo] where each entry represents one page.
     */
    fun paginate(
        text: String,
        style: TextStyle,
        maxWidthPx: Int,
        maxHeightPx: Int
    ): List<PageInfo> {
        if (text.isEmpty()) return emptyList()

        val pages = mutableListOf<PageInfo>()
        val constraints = Constraints(
            maxWidth = maxWidthPx.coerceAtLeast(1),
            maxHeight = maxHeightPx.coerceAtLeast(1)
        )
        var offset = 0
        val textLength = text.length

        while (offset < textLength) {
            val remaining = text.substring(offset)
            val layoutResult = textMeasurer.measure(
                text = remaining,
                style = style,
                constraints = constraints
            )

            if (layoutResult.didOverflowHeight) {
                // Some lines overflowed the max height.
                // Lines 0 .. lineCount-2 are fully visible; lineCount-1 is the overflow line.
                val lastVisibleLine = (layoutResult.lineCount - 2).coerceAtLeast(0)
                val lineEnd = layoutResult.getLineEnd(
                    lineIndex = lastVisibleLine,
                    visibleEnd = true
                ).coerceAtMost(remaining.length)

                if (lineEnd <= 0) {
                    // Safety: if we can't make progress, take at least one character
                    pages.add(
                        PageInfo(
                            startCharIndex = offset,
                            endCharIndex = offset + 1,
                            text = remaining.substring(0, 1)
                        )
                    )
                    offset += 1
                } else {
                    val pageText = remaining.substring(0, lineEnd)
                    pages.add(
                        PageInfo(
                            startCharIndex = offset,
                            endCharIndex = offset + lineEnd,
                            text = pageText
                        )
                    )
                    offset += lineEnd
                }
            } else {
                // Everything fits — consume the rest as a single page.
                pages.add(
                    PageInfo(
                        startCharIndex = offset,
                        endCharIndex = textLength,
                        text = remaining
                    )
                )
                break
            }
        }

        return pages
    }
}
