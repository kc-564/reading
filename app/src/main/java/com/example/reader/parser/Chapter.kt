package com.example.reader.parser

/**
 * A parsed chapter.
 *
 * @property title          Chapter title.
 * @property startLineIndex Index of the first source line (kept for backward compatibility).
 * @property lineCount      Number of source lines.
 * @property contentLines   Chapter text split into lines (joined by `\n` for the plain text).
 * @property richText       Optional rich-text payload (EPUB only). Its [RichText.text] is the
 *                          plain text and is preferred by [getContent]; TXT chapters leave it null.
 */
data class Chapter(
    val title: String,
    val startLineIndex: Int,
    val lineCount: Int,
    val contentLines: List<String>,
    val richText: RichText? = null
) {
    /**
     * Total number of characters in this chapter, including newlines between lines.
     * Computed lazily for efficiency.
     *
     * Formula: sum(line lengths) + max(0, lines.size - 1) newline characters
     */
    val totalCharCount: Int by lazy {
        contentLines.sumOf { it.length } + maxOf(0, contentLines.size - 1)
    }

    /**
     * Returns the full content of the chapter. Prefers [RichText.text] (when present) so EPUB
     * rich chapters and TXT chapters share one canonical plain-text source.
     */
    fun getContent(): String {
        return richText?.text ?: contentLines.joinToString("\n")
    }

    /**
     * Returns the chapter content with line breaks for display.
     */
    fun getDisplayText(): String {
        return getContent()
    }
}
