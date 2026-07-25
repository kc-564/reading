package com.example.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.reader.prefs.AppPrefs

/** Whether a [ThemeMode] uses a dark reading surface (dark status-bar icons = false). */
fun isDarkMode(mode: ThemeMode): Boolean = mode == ThemeMode.DARK || mode == ThemeMode.OLED_BLACK

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight, onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight, onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight, onBackground = OnBackgroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorLight, onError = OnErrorLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark, onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark, onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark, onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark, onBackground = OnBackgroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark, onError = OnErrorDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark
)

private val OledColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark, onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark, onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark, onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundOled, onBackground = OnBackgroundOled,
    surface = SurfaceOled, onSurface = OnSurfaceOled,
    surfaceVariant = SurfaceVariantOled, onSurfaceVariant = OnSurfaceVariantOled,
    error = ErrorDark, onError = OnErrorDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark
)

private val ParchmentColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight, onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight, onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundParchment, onBackground = OnBackgroundParchment,
    surface = SurfaceParchment, onSurface = OnSurfaceParchment,
    surfaceVariant = SurfaceVariantParchment, onSurfaceVariant = OnSurfaceVariantParchment,
    error = ErrorLight, onError = OnErrorLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight
)

/**
 * Builds the Material color scheme for the given [ThemeMode].
 */
fun schemeFor(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> LightColorScheme
    ThemeMode.DARK -> DarkColorScheme
    ThemeMode.OLED_BLACK -> OledColorScheme
    ThemeMode.PARCHMENT -> ParchmentColorScheme
}

/**
 * Application theme. Reads the user's [ThemeMode] from [AppPrefs] and applies the matching
 * Material color scheme. The status-bar color tracks the scheme background so it blends with
 * the reading surface, and the status-bar icon brightness is flipped for dark themes.
 */
@Composable
fun ReaderTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val themeKey by prefs.themeMode.collectAsState(initial = ThemeMode.LIGHT.storageKey)
    val mode = ThemeMode.fromKey(themeKey)
    val colorScheme = schemeFor(mode)
    val view = LocalView.current

    if (!view.isInEditMode()) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode(mode)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ReaderTypography,
        content = content
    )
}
