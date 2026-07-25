package com.example.reader.parser

data class Chapter(
    val title: String,
    val startLineIndex: Int,
    val lineCount: Int,
    val contentLines: List<String>
) {
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
