package com.example.reader.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_prefs")

class AppPrefs(private val context: Context) {

    // Preference keys
    companion object {
        private val KEY_DEFAULT_ENCODING = stringPreferencesKey("default_encoding")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_PAGE_MARGIN = intPreferencesKey("page_margin")
        private val KEY_AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed")
        private val KEY_LAST_OPENED_BOOK = stringPreferencesKey("last_opened_book")

        const val DEFAULT_ENCODING = "UTF-8"
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_THEME_MODE = "light"
        const val DEFAULT_BRIGHTNESS = -1f // -1 means follow system
        const val DEFAULT_LINE_SPACING = 1.6f
        const val DEFAULT_PAGE_MARGIN = 16
        const val DEFAULT_AUTO_SCROLL_SPEED = 0 // 0 = disabled
    }

    // --- Encoding ---
    val defaultEncoding: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_ENCODING] ?: DEFAULT_ENCODING
    }

    suspend fun setDefaultEncoding(encoding: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_ENCODING] = encoding
        }
    }

    // --- Font Scale ---
    val fontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SCALE] ?: DEFAULT_FONT_SCALE
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE] = scale.coerceIn(0.5f, 3.0f)
        }
    }

    // --- Theme Mode ---
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    // --- Brightness ---
    val brightness: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_BRIGHTNESS] ?: DEFAULT_BRIGHTNESS
    }

    suspend fun setBrightness(brightness: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BRIGHTNESS] = brightness.coerceIn(-1f, 1f)
        }
    }

    // --- Line Spacing ---
    val lineSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LINE_SPACING] ?: DEFAULT_LINE_SPACING
    }

    suspend fun setLineSpacing(spacing: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LINE_SPACING] = spacing.coerceIn(1.0f, 3.0f)
        }
    }

    // --- Page Margin ---
    val pageMargin: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_MARGIN] ?: DEFAULT_PAGE_MARGIN
    }

    suspend fun setPageMargin(margin: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PAGE_MARGIN] = margin.coerceIn(0, 64)
        }
    }

    // --- Auto Scroll Speed ---
    val autoScrollSpeed: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SCROLL_SPEED] ?: DEFAULT_AUTO_SCROLL_SPEED
    }

    suspend fun setAutoScrollSpeed(speed: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_SCROLL_SPEED] = speed.coerceIn(0, 100)
        }
    }

    // --- Last Opened Book ---
    val lastOpenedBook: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_OPENED_BOOK]
    }

    suspend fun setLastOpenedBook(path: String?) {
        context.dataStore.edit { prefs ->
            if (path != null) {
                prefs[KEY_LAST_OPENED_BOOK] = path
            } else {
                prefs.remove(KEY_LAST_OPENED_BOOK)
            }
        }
    }
}
