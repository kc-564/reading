package com.example.reader.parser

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * TXT file parser that reads a text file with the given encoding
 * and splits it into chapters.
 *
 * Phase 0: Basic implementation — reads all lines and creates a single chapter.
 * Phase 1+: Will use TOC rules to split into multiple chapters.
 */
class TxtParser {

    /**
     * Default chapter heading patterns used for splitting books.
     */
    private val defaultChapterPatterns: List<Regex> = listOf(
        // Chinese: 第x章, 第x节, 第x回, 第x折 (both Arabic and Chinese numerals)
        Regex("""^第[一二三四五六七八九十百千零〇0-9]+[章节回折]""", RegexOption.MULTILINE),
        // Chinese: 第\d+章/节/回/折
        Regex("""^第\d+[章节回折]""", RegexOption.MULTILINE),
        // English: Chapter N (case-insensitive)
        Regex("""^[Cc]hapter\s+\d+""", RegexOption.MULTILINE),
        // Numbered: N. Title
        Regex("""^\d+\.\s""", RegexOption.MULTILINE)
    )

    /**
     * Parses a TXT file and returns a list of chapters.
     *
     * Phase 1: Attempts to split the book into chapters using common heading patterns.
     * If no chapter headings are found, falls back to a single-chapter representation.
     *
     * @param path Absolute path to the TXT file
     * @param encoding The charset encoding to use for reading
     * @return List of chapters parsed from the file
     */
    fun parse(path: String, encoding: Charset): List<Chapter> {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }

        val allLines = readAllLines(file, encoding)
        if (allLines.isEmpty()) {
            return emptyList()
        }

        // Try to split into chapters using pattern matching
        val chapterBoundaries = findChapterBoundaries(allLines)
        if (chapterBoundaries.isNotEmpty()) {
            return buildChapters(allLines, chapterBoundaries, file.nameWithoutExtension)
        }

        // Fallback: no chapter heading found — treat entire file as one chapter
        return listOf(
            Chapter(
                title = file.nameWithoutExtension,
                startLineIndex = 0,
                lineCount = allLines.size,
                contentLines = allLines
            )
        )
    }

    /**
     * Scans all lines and returns the indices of lines that match a chapter heading pattern.
     *
     * @return List of line indices where a new chapter starts.
     */
    private fun findChapterBoundaries(lines: List<String>): List<Int> {
        val boundaries = mutableListOf<Int>()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val isHeading = defaultChapterPatterns.any { pattern ->
                pattern.containsMatchIn(trimmed)
            }
            if (isHeading) {
                boundaries.add(index)
            }
        }
        return boundaries
    }

    /**
     * Builds a list of [Chapter] objects from line content and boundary indices.
     *
     * Lines before the first heading are treated as an introduction chapter.
     */
    private fun buildChapters(
        lines: List<String>,
        boundaries: List<Int>,
        fallbackTitle: String
    ): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        var prevBoundary = 0

        for (boundary in boundaries) {
            // Lines from prevBoundary to boundary-1 belong to the previous chapter
            if (prevBoundary < boundary) {
                val introLines = lines.subList(prevBoundary, boundary)
                chapters.add(
                    Chapter(
                        title = if (chapters.isEmpty()) fallbackTitle else lines[prevBoundary].trim(),
                        startLineIndex = prevBoundary,
                        lineCount = introLines.size,
                        contentLines = introLines.toList()
                    )
                )
            }
            prevBoundary = boundary
        }

        // Add the last chapter (from the last boundary to end)
        if (prevBoundary < lines.size) {
            val lastChapterLines = lines.subList(prevBoundary, lines.size)
            val title = lines[prevBoundary].trim()
            chapters.add(
                Chapter(
                    title = title,
                    startLineIndex = prevBoundary,
                    lineCount = lastChapterLines.size,
                    contentLines = lastChapterLines.toList()
                )
            )
        }

        return chapters
    }

    /**
     * Parses a TXT file with chapter splitting based on TOC patterns.
     * Stub for future Phase 1 implementation.
     *
     * @param path Absolute path to the TXT file
     * @param encoding The charset encoding to use for reading
     * @param tocPatterns List of regex patterns that identify chapter titles
     * @return List of chapters parsed from the file
     */
    fun parseWithToc(path: String, encoding: Charset, tocPatterns: List<Regex>): List<Chapter> {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }

        val allLines = readAllLines(file, encoding)

        if (allLines.isEmpty()) {
            return emptyList()
        }

        val chapters = mutableListOf<Chapter>()
        var currentChapterStart = 0
        var currentChapterLines = mutableListOf<String>()
        var currentTitle = file.nameWithoutExtension

        for ((index, line) in allLines.withIndex()) {
            val matchedPattern = tocPatterns.firstOrNull { pattern ->
                pattern.containsMatchIn(line.trim())
            }

            if (matchedPattern != null && currentChapterLines.isNotEmpty()) {
                // Save current chapter
                chapters.add(
                    Chapter(
                        title = currentTitle,
                        startLineIndex = currentChapterStart,
                        lineCount = currentChapterLines.size,
                        contentLines = currentChapterLines.toList()
                    )
                )
                // Start new chapter
                currentChapterStart = index
                currentChapterLines = mutableListOf()
                currentTitle = line.trim()
            }

            currentChapterLines.add(line)
        }

        // Add the last chapter
        if (currentChapterLines.isNotEmpty()) {
            chapters.add(
                Chapter(
                    title = currentTitle,
                    startLineIndex = currentChapterStart,
                    lineCount = currentChapterLines.size,
                    contentLines = currentChapterLines.toList()
                )
            )
        }

        return chapters
    }

    /**
     * Reads all lines from a text file using the specified encoding.
     */
    fun readAllLines(file: File, encoding: Charset): List<String> {
        return try {
            FileInputStream(file).use { input ->
                InputStreamReader(input, encoding).use { reader ->
                    BufferedReader(reader).use { buffered ->
                        buffered.readLines()
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
