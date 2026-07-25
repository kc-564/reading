package com.example.reader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Reading-area specific colors that change with [ThemeMode] (C03).
 *
 * Unlike the Material color scheme (used for chrome like the shelf and toolbars), these
 * colors paint the actual reading surface — the background behind the text and the text
 * color itself.
 */
data class ReaderThemeColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val titleColor: Color,
    val secondary: Color,
    val isDark: Boolean
)

/**
 * Returns the [ReaderThemeColors] for the given [ThemeMode].
 */
fun readerColors(mode: ThemeMode): ReaderThemeColors = when (mode) {
    ThemeMode.LIGHT -> ReaderThemeColors(
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        primary = PrimaryLight,
        titleColor = OnBackgroundLight,
        secondary = OnSurfaceVariantLight,
        isDark = false
    )

    ThemeMode.DARK -> ReaderThemeColors(
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        primary = PrimaryDark,
        titleColor = OnBackgroundDark,
        secondary = OnSurfaceVariantDark,
        isDark = true
    )

    ThemeMode.OLED_BLACK -> ReaderThemeColors(
        background = BackgroundOled,
        onBackground = OnBackgroundOled,
        surface = SurfaceOled,
        onSurface = OnSurfaceOled,
        primary = PrimaryDark,
        titleColor = OnBackgroundOled,
        secondary = OnSurfaceVariantOled,
        isDark = true
    )

    ThemeMode.PARCHMENT -> ReaderThemeColors(
        background = BackgroundParchment,
        onBackground = OnBackgroundParchment,
        surface = SurfaceParchment,
        onSurface = OnSurfaceParchment,
        primary = PrimaryLight,
        titleColor = OnBackgroundParchment,
        secondary = OnSurfaceVariantParchment,
        isDark = false
    )
}
