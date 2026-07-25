package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording a single foreground reading session (F07).
 *
 * No foreground service is used — the [com.example.reader.feature.stats.ReadingStatsTracker]
 * samples foreground time and flushes one [ReadingSessionEntity] per continuous read.
 *
 * @property id        Auto-generated primary key.
 * @property bookId    Foreign key to [BookEntity.bookId].
 * @property startedAt Epoch millis when the session started.
 * @property endedAt   Epoch millis when the session ended.
 * @property durationSec Total foreground seconds.
 * @property dateKey   Local date key `yyyy-MM-dd` for daily aggregation.
 */
@Entity(
    tableName = "reading_sessions",
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["date_key"])
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "date_key") val dateKey: String
)
