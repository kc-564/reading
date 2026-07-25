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
     * Parses a TXT file and returns a list of chapters.
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

        // Phase 0: Simple implementation — everything is one chapter
        // Phase 1+ will replace this with TOC-based chapter splitting
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
