package com.example.reader.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the TOC detection rules (D07 / E05). Kept in sync with the patterns in
 * [TocRules] and `app/src/main/assets/toc_rules.json` (validated by `tools/toc_validate.py`).
 */
class TocRulesTest {

    @Test
    fun cnNumMatchesChineseNumberHeading() {
        val rule = TocRules.getById("cn_num")!!
        assertTrue(rule.matches("第一章 风起"))
        assertTrue(rule.matches("第一百章 终章"))
        assertTrue(rule.matches("第〇章 序"))
    }

    @Test
    fun cnNumRejectsNegativeFilters() {
        val rule = TocRules.getById("cn_num")!!
        assertFalse("ends with 图 should be suppressed", rule.matches("第三章图"))
        assertFalse("ends with 表 should be suppressed", rule.matches("第五章表"))
    }

    @Test
    fun cnNumDigitRequiresChapterChar() {
        val rule = TocRules.getById("cn_num_digit")!!
        assertTrue(rule.matches("第12章 觉醒"))
        assertFalse("'条' is not a chapter marker", rule.matches("第3条规定如下"))
    }

    @Test
    fun enChapterMatches() {
        val rule = TocRules.getById("en_chapter")!!
        assertTrue(rule.matches("Chapter 1 The Beginning"))
        assertTrue(rule.matches("chapter 5 Reunion"))
        assertFalse(rule.matches("Section 1"))
    }

    @Test
    fun enVolumeMatches() {
        val rule = TocRules.getById("en_volume")!!
        assertTrue(rule.matches("Volume 2 The Empire"))
        assertTrue(rule.matches("volume 7 The End"))
    }

    @Test
    fun asciiNumMatchesNumberedTitles() {
        val rule = TocRules.getById("ascii_num")!!
        assertTrue(rule.matches("1. 引言"))
        assertTrue(rule.matches("3、 小标题"))
        assertFalse("a 4-digit year should be excluded", rule.matches("2023 年出版"))
    }

    @Test
    fun sepTitleMatchesDashHeading() {
        val rule = TocRules.getById("sep_title")!!
        assertTrue(rule.matches("———— 卷一 风云"))
    }

    @Test
    fun enabledForReturnsAllByDefault() {
        val rules = TocRules.enabledFor(emptyMap())
        assertEquals(TocRules.ALL.size, rules.size)
    }
}
