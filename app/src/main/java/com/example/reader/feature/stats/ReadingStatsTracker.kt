package com.example.reader.feature.stats

import com.example.reader.data.StatsRepository
import com.example.reader.db.ReadingSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks foreground reading time and writes [ReadingSessionEntity] rows (F07).
 *
 * No foreground service is used: a heartbeat coroutine accumulates elapsed minutes while the
 * reader is active, and [flush] writes one session covering the whole visit. [tick] is called
 * on every progress change to mark the session as alive.
 */
class ReadingStatsTracker(
    private val statsRepository: StatsRepository,
    private val scope: CoroutineScope
) {
    private var bookId: String? = null
    private var sessionStart: Long = 0
    private var accumulatedSec: Int = 0
    private var active = false
    private var heartbeatJob: Job? = null

    fun start(bookId: String) {
        if (active && this.bookId == bookId) return
        flush()
        this.bookId = bookId
        sessionStart = System.currentTimeMillis()
        accumulatedSec = 0
        active = true
        startHeartbeat()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && active) {
                delay(60_000L)
                if (active) accumulatedSec += 60
            }
        }
    }

    /** Marks active reading (called on page flips / progress changes). */
    fun tick() {
        if (!active) return
        // Heartbeat already accumulates per minute; tick keeps the session alive.
    }

    /** Ends the current session and persists a [ReadingSessionEntity]. */
    fun flush() {
        if (!active || bookId == null) return
        active = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        val now = System.currentTimeMillis()
        val durationSec = (accumulatedSec + ((now - sessionStart) / 1000).toInt()).coerceAtLeast(1)
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val id = bookId!!
        scope.launch(Dispatchers.IO) {
            statsRepository.recordSession(
                ReadingSessionEntity(
                    bookId = id,
                    startedAt = sessionStart,
                    endedAt = now,
                    durationSec = durationSec,
                    dateKey = dateKey
                )
            )
        }
    }
}
