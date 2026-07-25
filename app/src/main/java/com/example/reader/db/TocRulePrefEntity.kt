package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Per-book TOC rule toggle (E05).
 *
 * Each book can enable/disable individual chapter-detection rules. The composite
 * primary key is [bookId] + [ruleId].
 */
@Entity(tableName = "toc_rule_prefs", primaryKeys = ["book_id", "rule_id"])
data class TocRulePrefEntity(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "rule_id") val ruleId: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean
)
