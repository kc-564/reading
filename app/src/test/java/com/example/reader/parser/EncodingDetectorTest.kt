package com.example.reader.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * JVM tests for [EncodingDetector] (C05). Verifies UTF-8 / GBK / BIG5 detection and BOM handling.
 *
 * Assertions are based on *round-trip decoding*: the detected charset must decode the bytes back
 * to the original text (BOM stripped where applicable), which is the property that actually
 * matters for correct rendering.
 */
class EncodingDetectorTest {

    private fun tempFile(bytes: ByteArray): File {
        val f = File.createTempFile("enc_", ".txt")
        f.writeBytes(bytes)
        f.deleteOnExit()
        return f
    }

    private fun decode(cs: Charset, f: File): String =
        cs.decode(ByteBuffer.wrap(f.readBytes())).toString().replace("\uFEFF", "")

    @Test
    fun detectsUtf8() {
        val text = "第一章 风起青萍之末"
        val f = tempFile(text.toByteArray(StandardCharsets.UTF_8))
        val cs = EncodingDetector.detect(f.absolutePath, "UTF-8")
        assertNotNull(cs)
        assertEquals("UTF-8", cs!!.name())
        assertEquals(text, decode(cs, f))
    }

    @Test
    fun detectsGbk() {
        val text = "第一章 风起青萍之末，江湖路远。"
        val f = tempFile(text.toByteArray(Charset.forName("GBK")))
        val cs = EncodingDetector.detect(f.absolutePath, "UTF-8")
        assertNotNull(cs)
        assertEquals(text, decode(cs!!, f))
    }

    @Test
    fun detectsBig5() {
        val text = "第一章 風起青萍之末，江湖路遠。"
        val f = tempFile(text.toByteArray(Charset.forName("BIG5")))
        val cs = EncodingDetector.detect(f.absolutePath, "UTF-8")
        assertNotNull(cs)
        assertEquals(text, decode(cs!!, f))
    }

    @Test
    fun detectsUtf8Bom() {
        val text = "第一章 风起"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val f = tempFile(bom + text.toByteArray(StandardCharsets.UTF_8))
        val cs = EncodingDetector.detect(f.absolutePath, "UTF-8")
        assertNotNull(cs)
        assertEquals("UTF-8", cs!!.name())
        assertTrue("decoded text should contain the original", decode(cs, f).endsWith(text))
    }
}
