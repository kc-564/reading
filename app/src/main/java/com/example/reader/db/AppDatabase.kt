package com.example.reader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the reader app.
 *
 * Version 2 (Phase 1): books + reading_history.
 * Version 3 (Phase 2): + bookmarks, reading_sessions, toc_rule_prefs, highlights, layout_cache,
 * and the `author`/`cover_uri`/`is_read` columns on books.
 */
@Database(
    entities = [
        BookEntity::class,
        ReadingHistoryEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class,
        TocRulePrefEntity::class,
        HighlightEntity::class,
        LayoutCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun statsDao(): StatsDao
    abstract fun tocRulePrefDao(): TocRulePrefDao
    abstract fun highlightDao(): HighlightDao
    abstract fun layoutCacheDao(): LayoutCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reader.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
