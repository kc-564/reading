package com.example.reader.parser

/**
 * Cleans download-artifact fragments (both leading and trailing) from a book title.
 *
 * **Suffix cleaning** (unchanged behaviour): trailing version/year codes, hex hashes and
 * bracketed tokens are removed.
 *
 * **Prefix cleaning** (new): leading download artifacts such as an ISBN / ASIN / numeric hash
 * followed by a separator (`9787123456789_三体` → `三体`, `12345_mybook` → `mybook`) or a
 * leading bracketed metadata token (`(ISBN...)书名` → `书名`) are stripped.
 *
 * Cleaning is applied repeatedly until the string stops changing. Meaningful Chinese prefixes
 * such as `第1卷` / `卷一` are NEVER removed because they contain CJK characters and do not
 * start with a bare digit/hash token. If cleaning would empty the whole title, the original
 * (trimmed) title is returned so we never lose it.
 */
fun sanitizeBookTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw

    var current = trimmed
    var previous: String
    do {
        previous = current
        current = current
            // ── Leading bracketed ASCII metadata, e.g. (ISBN...), [abc123], (2024) ──
            .let { stripLeadingBracketMeta(it) }
            // ── Leading digit/hash token before a separator (ISBN, ASIN, 8f3a…) ──
            .replace(Regex("^[0-9][0-9a-zA-Z]{0,}[_\\-\\s.~/]+"), "")
            // ── Leading bare hex hash (letter-led) before a separator ──
            .replace(Regex("^[0-9a-fA-F]{8,}[_\\-\\s.~/]+"), "")
            // ── Trailing short version / year / single token (e.g. "_2024", "-v2", " 9") ──
            .replace(Regex("[\\s_\\-]+[0-9a-zA-Z]{1,4}$"), "")
            // ── Trailing hex hash (e.g. "_8f3a2c1b") ──
            .replace(Regex("[\\s_\\-]+[0-9a-fA-F]{6,}$"), "")
            // ── Trailing (...) or [...] tokens ──
            .replace(Regex("\\([0-9a-zA-Z_\\-]+\\)\\s*$"), "")
            .replace(Regex("\\[[0-9a-zA-Z_\\-]+\\]\\s*$"), "")
            .trim()
    } while (current != previous && current.isNotBlank())

    // Collapse internal multiple spaces into a single space.
    current = current.replace(Regex("\\s+"), " ").trim()

    return if (current.isBlank()) trimmed else current
}

/**
 * Strips a single leading bracketed token only when it looks like download metadata:
 * it must contain no CJK characters (so `第1卷` / `卷一` are kept) and must contain at least
 * one digit or an edition keyword (ISBN/ASIN/EAN/EPUB/PDF). `(ISBN...)` and `[abc123]` are
 * removed; `(卷一)` and `(上)` are preserved.
 */
private fun stripLeadingBracketMeta(s: String): String {
    val match = Regex("^[(\\[][^\\)\\]]*[\\)\\]]").find(s) ?: return s
    val inner = match.value.substring(1, match.value.length - 1)
    val hasCjk = inner.any { it >= '\u4e00' && it <= '\u9fff' }
    if (hasCjk) return s
    val looksLikeMeta = inner.any { it.isDigit() } ||
        inner.contains(Regex("(?i)isbn|asin|ean|epub|pdf"))
    if (!looksLikeMeta) return s
    return s.removePrefix(match.value).trim()
}
