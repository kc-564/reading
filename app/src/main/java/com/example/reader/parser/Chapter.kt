package com.example.reader.parser

data class Chapter(
    val title: String,
    val startLineIndex: Int,
    val lineCount: Int,
    val contentLines: List<String>
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
     * Returns the full content of the chapter by joining all lines.
     */
    fun getContent(): String {
        return contentLines.joinToString("\n")
    }

    /**
     * Returns the chapter content with line breaks for display.
     */
    fun getDisplayText(): String {
        return contentLines.joinToString("\n")
    }
}
