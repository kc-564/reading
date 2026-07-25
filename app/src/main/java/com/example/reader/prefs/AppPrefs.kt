package com.example.reader.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.reader.engine.ReaderStyleConfig
import com.example.reader.parser.TocRules
import com.example.reader.ui.theme.ClickZoneConfig
import com.example.reader.ui.theme.FontFamilyKey
import com.example.reader.ui.theme.PageAnimationMode
import com.example.reader.ui.theme.ReadingMode
import com.example.reader.ui.theme.ThemeMode
import com.example.reader.ui.theme.alignmentFromKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_prefs")

/**
 * Centralised reader preferences backed by DataStore. Every preference is exposed as a
 * [Flow] so the [com.example.reader.ui.reader.ReaderViewModel] can re-paginate in real time
 * when a layout parameter changes.
 */
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

        // ── Phase 2 keys ──
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_ALIGNMENT = stringPreferencesKey("alignment")
        private val KEY_PARAGRAPH_SPACING = intPreferencesKey("paragraph_spacing")
        private val KEY_LETTER_SPACING = floatPreferencesKey("letter_spacing")
        private val KEY_FIRST_LINE_INDENT = intPreferencesKey("first_line_indent")
        private val KEY_READING_MODE = stringPreferencesKey("reading_mode")
        private val KEY_PAGE_ANIMATION = stringPreferencesKey("page_animation")
        private val KEY_RTL = booleanPreferencesKey("rtl")
        private val KEY_CLICK_ZONES = stringPreferencesKey("click_zones")
        private val KEY_TEXTURE_KEY = stringPreferencesKey("texture_key")
        private val KEY_IMPORTED_FONTS = stringPreferencesKey("imported_fonts")

        // ── v1.1 keys ──
        private val KEY_GLOBAL_TOC_RULES = stringPreferencesKey("global_toc_rules")

        const val DEFAULT_ENCODING = "UTF-8"
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_THEME_MODE = "light"
        const val DEFAULT_BRIGHTNESS = -1f // -1 means follow system
        const val DEFAULT_LINE_SPACING = 1.6f
        const val DEFAULT_PAGE_MARGIN = 16
        const val DEFAULT_AUTO_SCROLL_SPEED = 0 // 0 = disabled
        const val DEFAULT_PARAGRAPH_SPACING = 8
        const val DEFAULT_LETTER_SPACING = 0.5f
        const val DEFAULT_FIRST_LINE_INDENT = 0
    }

    // --- Encoding ---
    val defaultEncoding: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_ENCODING] ?: DEFAULT_ENCODING
    }

    suspend fun setDefaultEncoding(encoding: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_ENCODING] = encoding }
    }

    // --- Font Scale ---
    val fontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SCALE] ?: DEFAULT_FONT_SCALE
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_FONT_SCALE] = scale.coerceIn(0.5f, 3.0f) }
    }

    // --- Theme Mode ---
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode }
    }

    // --- Brightness ---
    val brightness: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_BRIGHTNESS] ?: DEFAULT_BRIGHTNESS
    }

    suspend fun setBrightness(brightness: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_BRIGHTNESS] = brightness.coerceIn(-1f, 1f) }
    }

    // --- Line Spacing ---
    val lineSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LINE_SPACING] ?: DEFAULT_LINE_SPACING
    }

    suspend fun setLineSpacing(spacing: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LINE_SPACING] = spacing.coerceIn(1.0f, 3.0f) }
    }

    // --- Page Margin ---
    val pageMargin: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_MARGIN] ?: DEFAULT_PAGE_MARGIN
    }

    suspend fun setPageMargin(margin: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_PAGE_MARGIN] = margin.coerceIn(0, 64) }
    }

    // --- Auto Scroll Speed ---
    val autoScrollSpeed: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SCROLL_SPEED] ?: DEFAULT_AUTO_SCROLL_SPEED
    }

    suspend fun setAutoScrollSpeed(speed: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_SCROLL_SPEED] = speed.coerceIn(0, 100) }
    }

    // --- Last Opened Book ---
    val lastOpenedBook: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_OPENED_BOOK]
    }

    suspend fun setLastOpenedBook(path: String?) {
        context.dataStore.edit { prefs ->
            if (path != null) prefs[KEY_LAST_OPENED_BOOK] = path
            else prefs.remove(KEY_LAST_OPENED_BOOK)
        }
    }

    // ── Phase 2 preferences ──

    val fontFamily: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_FAMILY] ?: FontFamilyKey.DEFAULT.storageKey
    }

    suspend fun setFontFamily(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_FONT_FAMILY] = key }
    }

    val alignment: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALIGNMENT] ?: "start"
    }

    suspend fun setAlignment(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ALIGNMENT] = key }
    }

    val paragraphSpacing: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PARAGRAPH_SPACING] ?: DEFAULT_PARAGRAPH_SPACING
    }

    suspend fun setParagraphSpacing(px: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_PARAGRAPH_SPACING] = px.coerceIn(0, 48) }
    }

    val letterSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LETTER_SPACING] ?: DEFAULT_LETTER_SPACING
    }

    suspend fun setLetterSpacing(sp: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_LETTER_SPACING] = sp.coerceIn(0f, 8f) }
    }

    val firstLineIndent: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LINE_INDENT] ?: DEFAULT_FIRST_LINE_INDENT
    }

    suspend fun setFirstLineIndent(px: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_FIRST_LINE_INDENT] = px.coerceIn(0, 64) }
    }

    val readingMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_READING_MODE] ?: ReadingMode.PAGED.storageKey
    }

    suspend fun setReadingMode(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_READING_MODE] = key }
    }

    val pageAnimation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_ANIMATION] ?: PageAnimationMode.SMOOTH.storageKey
    }

    suspend fun setPageAnimation(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_PAGE_ANIMATION] = key }
    }

    val rtl: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_RTL] ?: false
    }

    suspend fun setRtl(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RTL] = enabled }
    }

    val clickZones: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLICK_ZONES] ?: ClickZoneConfig().let { ClickZoneConfig.toKey(it) }
    }

    suspend fun setClickZones(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_CLICK_ZONES] = key }
    }

    val textureKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEXTURE_KEY] ?: "none"
    }

    suspend fun setTextureKey(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_TEXTURE_KEY] = key }
    }

    val importedFonts: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMPORTED_FONTS] ?: ""
    }

    suspend fun setImportedFonts(json: String) {
        context.dataStore.edit { prefs -> prefs[KEY_IMPORTED_FONTS] = json }
    }

    // ── v1.1 preferences ──

    /**
     * Global TOC rules stored as a JSON object string mapping ruleId -> enabled.
     * Default: all built-in rules enabled.
     */
    val globalTocRules: Flow<Map<String, Boolean>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_GLOBAL_TOC_RULES] ?: ""
        if (raw.isBlank()) {
            TocRules.ALL.associate { it.id to true }
        } else {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val map = org.json.JSONObject(raw)
                val result = mutableMapOf<String, Boolean>()
                val keys = map.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = map.getBoolean(key)
                }
                result
            }.getOrDefault(TocRules.ALL.associate { it.id to true })
        }
    }

    /**
     * Toggles a single TOC rule on/off. Reads the current map, updates the entry,
     * and writes back as JSON.
     */
    suspend fun updateTocRule(ruleId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_GLOBAL_TOC_RULES] ?: ""
            val map = if (raw.isBlank()) {
                TocRules.ALL.associate { it.id to true }.toMutableMap()
            } else {
                runCatching {
                    val json = org.json.JSONObject(raw)
                    val result = mutableMapOf<String, Boolean>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        result[key] = json.getBoolean(key)
                    }
                    result
                }.getOrDefault(TocRules.ALL.associate { it.id to true }.toMutableMap())
            }
            map[ruleId] = enabled
            val json = org.json.JSONObject()
            for ((k, v) in map) {
                json.put(k, v)
            }
            prefs[KEY_GLOBAL_TOC_RULES] = json.toString()
        }
    }

    /**
     * Combined reader style configuration. Emits a new [ReaderStyleConfig] whenever any
     * layout-affecting preference changes — the ViewModel collects this and re-paginates.
     */
    val styleConfigFlow: Flow<ReaderStyleConfig> = combine(
        fontScale, themeMode, brightness, lineSpacing, pageMargin,
        fontFamily, alignment, paragraphSpacing, letterSpacing, firstLineIndent,
        readingMode, pageAnimation, rtl, clickZones, textureKey
    ) { values ->
        val fontScaleV = values[0] as Float
        val themeModeV = values[1] as String
        val brightnessV = values[2] as Float
        val lineSpacingV = values[3] as Float
        val pageMarginV = values[4] as Int
        val fontFamilyV = values[5] as String
        val alignmentV = values[6] as String
        val paragraphSpacingV = values[7] as Int
        val letterSpacingV = values[8] as Float
        val firstLineIndentV = values[9] as Int
        val readingModeV = values[10] as String
        val pageAnimationV = values[11] as String
        val rtlV = values[12] as Boolean
        val clickZonesV = values[13] as String
        val textureKeyV = values[14] as String

        ReaderStyleConfig(
            fontScale = fontScaleV,
            lineSpacing = lineSpacingV,
            paragraphSpacingPx = paragraphSpacingV,
            letterSpacing = letterSpacingV,
            pageMarginPx = pageMarginV,
            alignment = alignmentFromKey(alignmentV),
            firstLineIndentPx = firstLineIndentV,
            fontFamily = FontFamilyKey.fromKey(fontFamilyV),
            themeMode = ThemeMode.fromKey(themeModeV),
            brightness = brightnessV,
            pageAnimation = PageAnimationMode.fromKey(pageAnimationV),
            readingMode = ReadingMode.fromKey(readingModeV),
            rtl = rtlV,
            clickZones = ClickZoneConfig.fromKey(clickZonesV),
            textureKey = textureKeyV
        )
    }
}
