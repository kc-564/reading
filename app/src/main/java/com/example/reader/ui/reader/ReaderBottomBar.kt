package com.example.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.reader.prefs.AppPrefs
import com.example.reader.ui.theme.FontFamilyKey
import com.example.reader.ui.theme.ThemeMode
import kotlinx.coroutines.launch

// ── Background colour presets for the SunMenu ──
private val BG_COLORS = listOf(
    BgPreset("白色", Color(0xFFFDFDFD), "light"),
    BgPreset("米色", Color(0xFFF5F0E0), "cream"),
    BgPreset("灰色", Color(0xFF555555), "dark"),
    BgPreset("黑色", Color(0xFF1A1A1A), "oled")
)

private data class BgPreset(val label: String, val color: Color, val themeKey: String)

/**
 * Bottom action bar for the reader screen (v1.1). Provides quick-access controls:
 * Sun icon → brightness / background / day-night menu,
 * Chapter position display, TOC drawer opener, and typesetting bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBottomBar(
    currentChapterIndex: Int,
    totalChapters: Int,
    onToc: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    val brightness by prefs.brightness.collectAsState(initial = -1f)
    val themeMode by prefs.themeMode.collectAsState(initial = "light")
    val fontScale by prefs.fontScale.collectAsState(initial = 1.0f)
    val lineSpacing by prefs.lineSpacing.collectAsState(initial = 1.6f)
    val pageMargin by prefs.pageMargin.collectAsState(initial = 16)
    val alignment by prefs.alignment.collectAsState(initial = "start")
    val fontFamily by prefs.fontFamily.collectAsState(initial = "default")

    var sunMenuExpanded by remember { mutableStateOf(false) }
    var showTypesettingSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Sun button → brightness / background / day-night ──
            Box {
                IconButton(onClick = { sunMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "亮度与主题",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                SunMenu(
                    expanded = sunMenuExpanded,
                    onDismiss = { sunMenuExpanded = false },
                    brightness = brightness,
                    themeMode = themeMode,
                    onBrightnessChange = { scope.launch { prefs.setBrightness(it) } },
                    onThemeModeChange = { scope.launch { prefs.setThemeMode(it) } }
                )
            }

            // ── Chapter info ──
            Text(
                text = "第${currentChapterIndex + 1}章/${totalChapters}章",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── TOC button ──
            IconButton(onClick = onToc) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "目录",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // ── Typesetting button → ModalBottomSheet ──
            IconButton(onClick = { showTypesettingSheet = true }) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = "排版设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // ── Typesetting bottom sheet ──
    if (showTypesettingSheet) {
        TypesettingSheet(
            fontScale = fontScale,
            lineSpacing = lineSpacing,
            pageMargin = pageMargin,
            alignment = alignment,
            fontFamily = fontFamily,
            onFontScaleChange = { scope.launch { prefs.setFontScale(it) } },
            onLineSpacingChange = { scope.launch { prefs.setLineSpacing(it) } },
            onPageMarginChange = { scope.launch { prefs.setPageMargin(it) } },
            onAlignmentChange = { scope.launch { prefs.setAlignment(it) } },
            onFontFamilyChange = { scope.launch { prefs.setFontFamily(it) } },
            onDismiss = { showTypesettingSheet = false }
        )
    }
}

// ── SunMenu: brightness slider + background color circles + theme toggle ──

@Composable
private fun SunMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    brightness: Float,
    themeMode: String,
    onBrightnessChange: (Float) -> Unit,
    onThemeModeChange: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("亮度调节", style = MaterialTheme.typography.labelMedium) },
            onClick = {},
            enabled = false
        )
        DropdownMenuItem(
            text = {
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -1f..1f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onClick = {},
            enabled = false
        )
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("背景色", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BG_COLORS.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                                    .then(
                                        if (themeMode == preset.themeKey)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { onThemeModeChange(preset.themeKey) }
                            )
                        }
                    }
                }
            },
            onClick = {},
            enabled = false
        )
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("夜间模式")
                    Switch(
                        checked = themeMode == "dark" || themeMode == "oled",
                        onCheckedChange = { checked ->
                            onThemeModeChange(if (checked) "dark" else "light")
                        }
                    )
                }
            },
            onClick = {}
        )
    }
}

// ── TypesettingSheet: font scale, line spacing, margin, alignment, font ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypesettingSheet(
    fontScale: Float,
    lineSpacing: Float,
    pageMargin: Int,
    alignment: String,
    fontFamily: String,
    onFontScaleChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onPageMarginChange: (Int) -> Unit,
    onAlignmentChange: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("排版设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            LabeledSlider("字号", fontScale, 0.5f, 3f, 0.1f, onFontScaleChange)
            LabeledSlider("行间距", lineSpacing, 1f, 3f, 0.1f, onLineSpacingChange)
            LabeledSlider("页边距", pageMargin.toFloat(), 0f, 64f, 1f) {
                onPageMarginChange(it.toInt())
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("对齐方式")
            ChipRow(
                options = listOf("start" to "起始", "center" to "居中", "justify" to "两端", "end" to "末尾"),
                selected = alignment,
                onSelect = onAlignmentChange
            )

            SectionLabel("字体")
            ChipRow(
                options = FontFamilyKey.entries.map { it.storageKey to it.name },
                selected = fontFamily,
                onSelect = onFontFamilyChange
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("完成")
            }
        }
    }
}

// ── Shared helpers (also used in SettingsSheet.kt, duplicated here for independence) ──

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onValue: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "$label: ${if (step >= 1f) value.toInt().toString() else "%.1f".format(value)}",
            style = MaterialTheme.typography.bodyMedium
        )
        val steps = ((max - min) / step).toInt().coerceAtLeast(0)
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = min..max,
            steps = (steps - 1).coerceAtLeast(0)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        items(options) { (key, label) ->
            FilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                label = { Text(label) }
            )
        }
    }
}
