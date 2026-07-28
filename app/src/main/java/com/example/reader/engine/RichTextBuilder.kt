package com.example.reader.engine

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spanned
import android.text.SpannableString
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.example.reader.parser.RichText
import com.example.reader.parser.SpanSpec
import com.example.reader.parser.SpanType

/**
 * Turns a plain chapter [text] + a [LayoutSignature] + optional rich [spans] into a
 * [SpannableString] ready for [com.example.reader.engine.PageRenderer].
 *
 * Design guarantees (see incremental-optimization-design §7.5):
 * 1. **The backing string is the original [text].** Indent / spacing / style are applied as
 *    *spans*, never by inserting or removing characters — so every character offset in the
 *    produced [SpannableString] equals the offset in [text]. Pagination therefore yields
 *    ranges in the original coordinate space, keeping bookmarks / search / resume offsets valid.
 * 2. **First-line indent** is a [LeadingMarginSpan.Standard] on each paragraph's first character
 *    only, so continuation pages (a paragraph split across a page boundary) are *not* indented.
 * 3. **Paragraph spacing** is a [ParagraphSpacingSpan] on the paragraph's last character, adding
 *    trailing space below the paragraph without altering the string.
 *
 * Both pagination ([PageRenderer.paginateChapter]) and rendering ([PageRenderer.renderPageBitmap])
 * consume the *same* instance, so a baked page is pixel-identical to its paginated range.
 */
object RichTextBuilder {

    /**
     * Builds the display [SpannableString].
     *
     * @param text             Original chapter text (the [SpannableString] will wrap this exact string).
     * @param signature        Layout signature driving indent / spacing / rich-style version.
     * @param spans            Rich-text style spans (headings / bold / italic) from the parser.
     * @param paragraphStarts  Character offsets of each paragraph's first character (from
     *                         [com.example.reader.util.ParagraphSplitter]).
     */
    fun build(
        text: String,
        signature: LayoutSignature,
        spans: List<SpanSpec> = emptyList(),
        paragraphStarts: Set<Int> = emptySet()
    ): SpannableString {
        val spannable = SpannableString(text)
        applyIndent(spannable, signature, paragraphStarts)
        applySpacing(spannable, signature, paragraphStarts)
        applyStyle(spannable, spans)
        return spannable
    }

    /** Convenience overload accepting a [RichText] payload (text + spans). */
    fun build(
        richText: RichText,
        signature: LayoutSignature,
        paragraphStarts: Set<Int> = emptySet()
    ): SpannableString = build(richText.text, signature, richText.spans, paragraphStarts)

    private fun applyIndent(sp: SpannableString, sig: LayoutSignature, starts: Set<Int>) {
        if (sig.firstLineIndentPx <= 0) return
        for (start in starts) {
            if (start < 0 || start >= sp.length) continue
            val end = (start + 1).coerceAtMost(sp.length)
            sp.setSpan(
                LeadingMarginSpan.Standard(sig.firstLineIndentPx, 0),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun applySpacing(sp: SpannableString, sig: LayoutSignature, starts: Set<Int>) {
        if (sig.paragraphSpacingPx <= 0) return
        val ends = paragraphEnds(sp.toString(), starts)
        for (end in ends) {
            if (end <= 0) continue
            val from = (end - 1).coerceAtLeast(0)
            sp.setSpan(
                ParagraphSpacingSpan(sig.paragraphSpacingPx),
                from,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun applyStyle(sp: SpannableString, spans: List<SpanSpec>) {
        for (spec in spans) {
            val start = spec.start.coerceAtLeast(0)
            val end = spec.end.coerceAtMost(sp.length)
            if (end <= start) continue
            when (spec.type) {
                SpanType.H1 -> applyHeading(sp, start, end, 1.8f)
                SpanType.H2 -> applyHeading(sp, start, end, 1.5f)
                SpanType.H3 -> applyHeading(sp, start, end, 1.3f)
                SpanType.H4 -> applyHeading(sp, start, end, 1.15f)
                SpanType.H5 -> applyHeading(sp, start, end, 1.08f)
                SpanType.H6 -> applyHeading(sp, start, end, 1.0f)
                SpanType.BOLD -> sp.setSpan(
                    StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                SpanType.ITALIC -> sp.setSpan(
                    StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun applyHeading(sp: SpannableString, start: Int, end: Int, factor: Float) {
        sp.setSpan(RelativeSizeSpan(factor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * Exclusive end offsets of each paragraph (position just after its last content character),
     * derived from the sorted paragraph starts and the text length. Used to attach the spacing
     * span to each paragraph's final line.
     */
    private fun paragraphEnds(text: String, starts: Set<Int>): List<Int> {
        val sorted = starts.filter { it in 0..text.length }.sorted()
        if (sorted.isEmpty()) return emptyList()
        val ends = mutableListOf<Int>()
        for (i in sorted.indices) {
            val s = sorted[i]
            val nextStart = if (i + 1 < sorted.size) sorted[i + 1] else text.length
            // Walk back over the inter-paragraph separators to the last content character.
            var e = nextStart
            while (e > s && e > 0 && text[e - 1].isWhitespace()) e--
            ends.add(e)
        }
        return ends
    }

    /**
     * Adds [extraPx] of trailing space below the line it covers, implementing paragraph spacing
     * without altering the character string. Attached to a paragraph's last character so only
     * that paragraph's final line is affected.
     */
    private class ParagraphSpacingSpan(private val extraPx: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanstartv: Int,
            lineHeight: Int,
            fm: Paint.FontMetricsInt
        ) {
            fm.descent += extraPx
            fm.bottom += extraPx
        }
    }
}
