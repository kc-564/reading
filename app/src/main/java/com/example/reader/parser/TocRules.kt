package com.example.reader.parser

/**
 * A single table-of-contents detection rule (D07 / E05).
 *
 * @property id      Stable identifier (persisted per-book toggle state).
 * @property label   Human-readable label for the settings UI.
 * @property pattern The positive heading pattern (anchored to the whole line via `^...$`
 *                   semantics applied by [matches]).
 * @property negative Optional negative filters — if a line matches any of these, it is
 *                    NOT treated as a heading even when [pattern] matches.
 */
data class TocRule(
    val id: String,
    val label: String,
    val pattern: Regex,
    val negative: List<Regex> = emptyList()
) {
    /** Returns true if [line] is a chapter heading according to this rule. */
    fun matches(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (negative.any { it.containsMatchIn(trimmed) }) return false
        return pattern.containsMatchIn(trimmed)
    }
}

/**
 * Built-in TOC rule set. Rules are anchored to the start of a line and use negative filters
 * to suppress common false positives. The set is kept in sync with `tools/toc_validate.py`
 * so CI can validate samples.
 */
object TocRules {

    val ALL: List<TocRule> = listOf(
        TocRule(
            id = "cn_num",
            label = "中文数字章节",
            pattern = Regex("""^第[一二三四五六七八九十百千零〇0-9]+[章节回折卷部]"""),
            negative = listOf(Regex("""图\s*$"""), Regex("""表\s*$"""))
        ),
        TocRule(
            id = "cn_num_digit",
            label = "第N章（阿拉伯数字）",
            pattern = Regex("""^第\d+[章节回折卷部]""")
        ),
        TocRule(
            id = "en_chapter",
            label = "Chapter N",
            pattern = Regex("""^[Cc]hapter\s+\d+""")
        ),
        TocRule(
            id = "en_volume",
            label = "Volume N",
            pattern = Regex("""^[Vv]olume\s+\d+""")
        ),
        TocRule(
            id = "ascii_num",
            label = "数字编号 (1. 2、)",
            pattern = Regex("""^\d{1,3}[\.、]\s*\S"""),
            negative = listOf(Regex("""^\d{4}""")) // avoid years like 2023.
        ),
        TocRule(
            id = "sep_title",
            label = "分隔线标题",
            pattern = Regex("""^[—\-=]{4,}\s*[章节卷]""")
        )
    )

    private val BY_ID: Map<String, TocRule> = ALL.associateBy { it.id }

    /** Returns the rules enabled for a book given a `ruleId -> enabled` map. */
    fun enabledFor(prefs: Map<String, Boolean>): List<TocRule> =
        ALL.filter { prefs[it.id] ?: true }

    fun getById(id: String): TocRule? = BY_ID[id]

    /** Default: all rules enabled. */
    fun defaultEnabledIds(): Set<String> = ALL.map { it.id }.toSet()
}
