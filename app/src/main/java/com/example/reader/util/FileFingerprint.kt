package com.example.reader.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileFingerprint {

    private const val ALGORITHM = "SHA-256"

    /**
     * Computes a fingerprint for a file based on its path, size, and last modified time.
     * The fingerprint is a SHA-256 hex string derived from the concatenation of
     * filePath + ":" + fileSize + ":" + lastModified.
     *
     * This is NOT a content hash — it is a fast identity fingerprint suitable for
     * cache invalidation where file metadata changes indicate content changes.
     */
    fun compute(filePath: String): String {
        val file = File(filePath)
        return compute(file)
    }

    /**
     * Computes a fingerprint for a file based on its path, size, and last modified time.
     */
    fun compute(file: File): String {
        val input = buildString {
            append(file.absolutePath)
            append(":")
            append(file.length())
            append(":")
            append(file.lastModified())
        }

        val digest = MessageDigest.getInstance(ALGORITHM)
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    /**
     * Computes a full content hash of the file (SHA-256 of all bytes).
     * Useful when you need to verify file integrity rather than just identity.
     */
    fun contentHash(file: File): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return bytesToHex(digest.digest())
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = HEX_DIGITS[v ushr 4]
            hexChars[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
        }
        return String(hexChars)
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
