package com.example.reader.parser

/**
 * Lightweight rich-text model produced by [com.example.reader.parser.EpubParser].
 *
 * [text] is the **plain** chapter text and MUST stay byte-identical to the previous
 * `htmlToPlainText` output — downstream pagination keys, the layout cache, bookmarks and search
 * all depend on it. Styling is carried *out of band* as [spans] so the underlying string is
 * never mutated by rich formatting.
 *
 * @property text  Plain chapter text (≡ old `htmlToPlainText` output).
 * @property spans Style spans (headings / bold / italic) with offsets into [text].
 */
data class RichText(val text: String, val spans: List<SpanSpec>)

/**
 * A single style span with absolute character offsets into [RichText.text].
 */
data class SpanSpec(val start: Int, val end: Int, val type: SpanType)

/**
 * Rich-text style categories mapped from HTML tags (see incremental-optimization-design §7.3).
 */
enum class SpanType {
    H1, H2, H3, H4, H5, H6, BOLD, ITALIC
}
