package com.example.reader.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a reading history entry (one open event).
 */
@Entity(
    tableName = "reading_history",
    indices = [Index(value = ["openedAt"]), Index(value = ["bookId"])]
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val openedAt: Long,
    val progressPercent: Float
)
