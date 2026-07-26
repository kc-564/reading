package com.example.reader.parser

/**
 * Cleans download-artifact suffixes from a book title.
 *
 * Only trailing fragments that look like download artifacts are removed:
 *  - a short version / year code after a separator (`_2024`, `-v2`, ` 9`)
 *  - a hex hash after a separator (`_8f3a2c1b`)
 *  - a parenthesised or bracketed token at the end (`(12345)`, `[abc123]`)
 *
 * The cleaning is applied repeatedly until the string stops changing. If cleaning would
 * empty the whole title, the original (trimmed) title is returned so we never lose it.
 */
fun sanitizeBookTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw

    var current = trimmed
    var previous: String
    do {
        previous = current
        current = current
            // trailing short version / year / single token (e.g. "_2024", "-v2", " 9")
            .replace(Regex("[\\s_\\-]+[0-9a-zA-Z]{1,4}$"), "")
            // trailing hex hash (e.g. "_8f3a2c1b")
            .replace(Regex("[\\s_\\-]+[0-9a-fA-F]{6,}$"), "")
            // trailing (...) or [...] tokens
            .replace(Regex("\\([0-9a-zA-Z_\\-]+\\)\\s*$"), "")
            .replace(Regex("\\[[0-9a-zA-Z_\\-]+\\]\\s*$"), "")
    } while (current != previous && current.isNotBlank())

    // Collapse internal multiple spaces into a single space.
    current = current.replace(Regex("\\s+"), " ").trim()

    return if (current.isBlank()) trimmed else current
}
