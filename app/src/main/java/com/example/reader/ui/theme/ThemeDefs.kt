package com.example.reader.ui.theme

import androidx.compose.ui.text.style.TextAlign

/**
 * Reader theme modes. The storage key is what gets persisted in [com.example.reader.prefs.AppPrefs].
 */
enum class ThemeMode(val storageKey: String) {
    LIGHT("light"),
    DARK("dark"),
    OLED_BLACK("oled"),
    PARCHMENT("parchment");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: LIGHT
    }
}

/**
 * Built-in font families plus the user-imported custom family placeholder.
 *
 * SANS/SERIF/MONOSPACE map to Android system font families; the actual typeface for a
 * custom import is resolved at runtime by [com.example.reader.feature.fonts.FontManager].
 */
enum class FontFamilyKey(val storageKey: String) {
    DEFAULT("default"),
    SANS("sans"),
    SERIF("serif"),
    MONOSPACE("monospace"),
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String?): FontFamilyKey =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

/**
 * Page transition animation style (E03). No third-party libraries are used.
 */
enum class PageAnimationMode(val storageKey: String) {
    SMOOTH("smooth"),
    NONE("none"),
    FLIP3D("flip3d");

    companion object {
        fun fromKey(key: String?): PageAnimationMode =
            entries.firstOrNull { it.storageKey == key } ?: SMOOTH
    }
}

/**
 * Reading layout mode.
 */
enum class ReadingMode(val storageKey: String) {
    PAGED("paged"),
    SCROLLED("scrolled");

    companion object {
        fun fromKey(key: String?): ReadingMode =
            entries.firstOrNull { it.storageKey == key } ?: PAGED
    }
}

/**
 * Action triggered when a tap/click zone is activated (E01).
 */
enum class ClickZoneAction(val label: String) {
    PREVIOUS_PAGE("上一页"),
    NEXT_PAGE("下一页"),
    OPEN_MENU("打开菜单"),
    OPEN_TOC("目录"),
    NONE("无操作");

    companion object {
        fun fromKey(key: String?): ClickZoneAction =
            entries.firstOrNull { it.name == key } ?: NONE
    }
}

/**
 * Mapping of the five reader tap zones to actions (E01).
 *
 * Serialized as a single `|`-separated string of [ClickZoneAction.name] values:
 * `top|bottom|left|right|center`.
 */
data class ClickZoneConfig(
    val top: ClickZoneAction = ClickZoneAction.OPEN_MENU,
    val bottom: ClickZoneAction = ClickZoneAction.OPEN_MENU,
    val left: ClickZoneAction = ClickZoneAction.PREVIOUS_PAGE,
    val right: ClickZoneAction = ClickZoneAction.NEXT_PAGE,
    val center: ClickZoneAction = ClickZoneAction.NONE
) {
    companion object {
        private const val SEP = "|"

        fun fromKey(key: String?): ClickZoneConfig {
            if (key.isNullOrBlank()) return ClickZoneConfig()
            val parts = key.split(SEP)
            val get = { i: Int -> ClickZoneAction.fromKey(parts.getOrNull(i)?.takeIf { it.isNotBlank() }) }
            return ClickZoneConfig(
                top = get(0),
                bottom = get(1),
                left = get(2),
                right = get(3),
                center = get(4)
            )
        }

        fun toKey(c: ClickZoneConfig): String =
            listOf(c.top, c.bottom, c.left, c.right, c.center)
                .joinToString(SEP) { it.name }
    }
}

/** Convert a persisted alignment string to [TextAlign]. */
fun alignmentFromKey(key: String?): TextAlign = when (key) {
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    "justify" -> TextAlign.Justify
    else -> TextAlign.Start
}

/** Convert a [TextAlign] to its persisted string form. */
fun TextAlign.toKey(): String = when (this) {
    TextAlign.Center -> "center"
    TextAlign.End -> "end"
    TextAlign.Justify -> "justify"
    else -> "start"
}
