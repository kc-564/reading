package com.example.reader.engine

/**
 * Represents a single page of text produced by the [LayoutEngine].
 *
 * @property startCharIndex The character index in the source text where this page starts (inclusive).
 * @property endCharIndex   The character index in the source text where this page ends (exclusive).
 * @property text           The visible text content of this page.
 */
data class PageInfo(
    val startCharIndex: Int,
    val endCharIndex: Int,
    val text: String
)
