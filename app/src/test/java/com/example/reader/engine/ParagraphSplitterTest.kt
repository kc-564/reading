package com.example.reader.engine

import com.example.reader.util.ParagraphSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [ParagraphSplitter] (D06 / D08): blank-line splitting and greedy page packing.
 */
class ParagraphSplitterTest {

    @Test
    fun emptyInputYieldsNoParagraphs() {
        assertTrue(ParagraphSplitter.split("").isEmpty())
    }

    @Test
    fun singleLineIsOneParagraph() {
        val paras = ParagraphSplitter.split("hello")
        assertEquals(1, paras.size)
        assertEquals("hello", paras[0].text)
        assertEquals(0, paras[0].start)
        assertEquals(5, paras[0].end)
    }

    @Test
    fun splitsOnBlankLinesWithDedup() {
        // "第一段\n第二行" | "第三段" | "第四段"
        val paras = ParagraphSplitter.split("第一段\n第二行\n\n第三段\n\n\n第四段")
        assertEquals(3, paras.size)
        assertEquals("第一段\n第二行", paras[0].text)
        assertEquals("第三段", paras[1].text)
        assertEquals("第四段", paras[2].text)
        // char spans must map back into the source
        assertEquals(0, paras[0].start)
        assertEquals(7, paras[0].end)
        assertEquals(16, paras[2].start)
        assertEquals(19, paras[2].end)
    }

    @Test
    fun groupIntoPagesRespectsSpacing() {
        // three 100px paragraphs, page height 250, 10px between paragraphs
        val pages = ParagraphSplitter.groupIntoPages(listOf(100, 100, 100), 250, 10)
        assertEquals(2, pages.size)
        assertEquals(0, pages[0].first)
        assertEquals(1, pages[0].last)
        assertEquals(2, pages[1].first)
        assertEquals(2, pages[1].last)
    }

    @Test
    fun groupIntoPagesSingleParagraphAboveHeight() {
        val pages = ParagraphSplitter.groupIntoPages(listOf(300), 250, 10)
        assertEquals(1, pages.size)
        assertEquals(0, pages[0].first)
        assertEquals(0, pages[0].last)
    }
}
