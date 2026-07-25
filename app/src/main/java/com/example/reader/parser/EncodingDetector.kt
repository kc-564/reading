package com.example.reader.parser

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Encoding cascade detector for TXT files.
 *
 * Detection pipeline:
 * 1. BOM detection (Byte Order Mark) — identifies UTF-8/16 with BOM and strips it
 * 2. juniversalchardet statistical detection — uses Mozilla's charset detection
 * 3. User default encoding preference — try decoding with user's configured encoding
 * 4. UTF-8 validity check — verify if content is valid UTF-8
 * 5. GBK fallback — last resort for Chinese text
 */
object EncodingDetector {

    private const val MAX_READ_SIZE = 64 * 1024 // 64KB

    /**
     * Detects the encoding of a file using the cascade detection pipeline.
     *
     * @param filePath Path to the file
     * @param defaultPref User's default encoding preference (e.g., "UTF-8", "GBK")
     * @return Detected Charset, or null if detection fails
     */
    fun detect(filePath: String, defaultPref: String): Charset? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null

        val bytes = readHeadBytes(file) ?: return null
        if (bytes.isEmpty()) return StandardCharsets.UTF_8

        // Step 1: BOM detection
        val bomResult = detectBom(bytes)
        val cleanBytes = bomResult.second

        if (bomResult.first != null) {
            return bomResult.first
        }

        // Step 2: juniversalchardet statistical detection
        val detectedByLib = detectByUniversalChardet(cleanBytes)
        if (detectedByLib != null) {
            return detectedByLib
        }

        // Step 3: Try user's default encoding preference
        val defaultCharset = try {
            Charset.forName(defaultPref)
        } catch (e: Exception) {
            null
        }
        if (defaultCharset != null && isValidEncoding(cleanBytes, defaultCharset)) {
            return defaultCharset
        }

        // Step 4: UTF-8 validity check
        if (isValidUtf8(cleanBytes)) {
            return StandardCharsets.UTF_8
        }

        // Step 5: GBK fallback
        return try {
            Charset.forName("GBK")
        } catch (e: Exception) {
            StandardCharsets.UTF_8
        }
    }

    /**
     * Strips the BOM (Byte Order Mark) from a byte array if present.
     *
     * @param bytes Input byte array potentially starting with BOM
     * @return Byte array with BOM stripped (or unchanged if no BOM)
     */
    fun stripBom(bytes: ByteArray): ByteArray {
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> {
                bytes.copyOfRange(3, bytes.size)
            }
            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> {
                bytes.copyOfRange(2, bytes.size)
            }
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> {
                bytes.copyOfRange(2, bytes.size)
            }
            else -> bytes
        }
    }

    /**
     * BOM detection with stripping.
     * Returns a pair of (detected Charset or null, bytes with BOM stripped).
     */
    private fun detectBom(bytes: ByteArray): Pair<Charset?, ByteArray> {
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> {
                Pair(StandardCharsets.UTF_8, bytes.copyOfRange(3, bytes.size))
            }
            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> {
                Pair(StandardCharsets.UTF_16BE, bytes.copyOfRange(2, bytes.size))
            }
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> {
                Pair(StandardCharsets.UTF_16LE, bytes.copyOfRange(2, bytes.size))
            }
            else -> Pair(null, bytes)
        }
    }

    /**
     * Uses juniversalchardet library for statistical encoding detection.
     */
    private fun detectByUniversalChardet(bytes: ByteArray): Charset? {
        return try {
            val detector = org.mozilla.universalchardet.UniversalDetector(null)
            detector.handleData(bytes, 0, bytes.size)
            detector.dataEnd()
            val detectedName = detector.detectedCharset
            detector.reset()
            if (detectedName != null) {
                try {
                    Charset.forName(detectedName)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads the first 64KB of the file.
     */
    private fun readHeadBytes(file: File): ByteArray? {
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(MAX_READ_SIZE)
                val bytesRead = input.read(buffer, 0, MAX_READ_SIZE)
                if (bytesRead <= 0) {
                    ByteArray(0)
                } else if (bytesRead < MAX_READ_SIZE) {
                    buffer.copyOf(bytesRead)
                } else {
                    buffer
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if bytes can be decoded using the given charset without errors.
     */
    private fun isValidEncoding(bytes: ByteArray, charset: Charset): Boolean {
        return try {
            val decoder = charset.newDecoder()
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates if the byte array is valid UTF-8 encoded text.
     */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b and 0x80 == 0 -> {
                    // Single byte (0xxxxxxx) — valid
                    i++
                }
                b and 0xE0 == 0xC0 -> {
                    // Two bytes (110xxxxx 10xxxxxx)
                    if (i + 1 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    i += 2
                }
                b and 0xF0 == 0xE0 -> {
                    // Three bytes (1110xxxx 10xxxxxx 10xxxxxx)
                    if (i + 2 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 2].toInt() and 0xC0 != 0x80) return false
                    i += 3
                }
                b and 0xF8 == 0xF0 -> {
                    // Four bytes (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
                    if (i + 3 >= bytes.size) return false
                    if (bytes[i + 1].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 2].toInt() and 0xC0 != 0x80) return false
                    if (bytes[i + 3].toInt() and 0xC0 != 0x80) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }
}
