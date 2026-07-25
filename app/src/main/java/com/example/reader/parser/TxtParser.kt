package com.example.reader.parser

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * TXT file parser that reads a text file with the given encoding and splits it into chapters.
 *
 * Chapters are detected with [TocRules] (configurable per book via [parseWithRules]).
 * Lines before the first detected heading form an "introduction" chapter.
 */
class TxtParser {

    /**
     * Parses a TXT file into chapters using the default (all-enabled) TOC rules.
     */
    fun parse(path: String, encoding: Charset): List<Chapter> =
        parseWithRules(path, encoding, TocRules.ALL)

    /**
     * Parses a TXT file into chapters using the supplied enabled rules.
     *
     * @param enabledRules Rules whose [TocRule.matches] identifies a chapter heading.
     */
    fun parseWithRules(path: String, encoding: Charset, enabledRules: List<TocRule>): List<Chapter> {
        val file = File(path)
        if (!file.exists() || !file.isFile) return emptyList()

        val allLines = readAllLines(file, encoding)
        if (allLines.isEmpty()) return emptyList()

        val boundaries = findChapterBoundaries(allLines, enabledRules)
        if (boundaries.isNotEmpty()) {
            return buildChapters(allLines, boundaries, file.nameWithoutExtension)
        }

        // Fallback: no chapter heading found — treat entire file as one chapter.
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
     * Scans all lines and returns the indices of lines that match any enabled heading rule.
     */
    private fun findChapterBoundaries(lines: List<String>, rules: List<TocRule>): List<Int> {
        if (rules.isEmpty()) return emptyList()
        val boundaries = mutableListOf<Int>()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (rules.any { it.matches(line) }) {
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
