package com.example.reader.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Layout (pagination) cache entry (C07).
 *
 * Only stores the pagination *result* (per-chapter character ranges) as compact JSON —
 * never the full text. A bad cache entry is self-healed by the caller (try/catch and
 * re-paginate on parse failure).
 *
 * @property cacheKey  Primary key = sha1(fingerprint + layoutHash + screenSize).
 * @property bookId    Foreign key to [BookEntity.bookId].
 * @property pagesJson Compact JSON array of per-chapter `[[start,end], ...]` ranges.
 * @property createdAt Epoch millis when the cache entry was written.
 */
@Entity(tableName = "layout_cache")
data class LayoutCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "pages_json") val pagesJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
