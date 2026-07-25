package com.example.reader.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for the reader app.
 *
 * Migration 2 -> 3 (Phase 2):
 * - Adds `author` / `cover_uri` / `is_read` columns to the existing `books` table.
 * - Creates `bookmarks`, `reading_sessions`, `toc_rule_prefs`, `highlights`, `layout_cache`.
 *
 * All new columns are nullable or have a NOT NULL DEFAULT so existing rows migrate
 * without data loss. The destructive fallback in [AppDatabase] remains as a safety net.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Extend books ──
        db.execSQL("ALTER TABLE books ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN cover_uri TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN is_read INTEGER NOT NULL DEFAULT 0")

        // ── bookmarks ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                bookmark_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                book_id TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                page_index INTEGER NOT NULL,
                char_offset INTEGER NOT NULL,
                preview_text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_book_id ON bookmarks(book_id)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bookmarks_book_id_chapter_index " +
                "ON bookmarks(book_id, chapter_index)"
        )

        // ── reading_sessions ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                book_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                duration_sec INTEGER NOT NULL,
                date_key TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_sessions_book_id ON reading_sessions(book_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_sessions_date_key ON reading_sessions(date_key)")

        // ── toc_rule_prefs ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS toc_rule_prefs (
                book_id TEXT NOT NULL,
                rule_id TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                PRIMARY KEY(book_id, rule_id)
            )
            """.trimIndent()
        )

        // ── highlights ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS highlights (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                book_id TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                start_char INTEGER NOT NULL,
                end_char INTEGER NOT NULL,
                color_argb INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_book_id ON highlights(book_id)")

        // ── layout_cache ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS layout_cache (
                cache_key TEXT NOT NULL PRIMARY KEY,
                book_id TEXT NOT NULL,
                pages_json TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
