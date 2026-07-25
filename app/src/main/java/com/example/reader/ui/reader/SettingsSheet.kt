package com.example.reader.ui.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.reader.ui.theme.ClickZoneAction
import com.example.reader.feature.fonts.FontManager
import com.example.reader.prefs.AppPrefs
import com.example.reader.ui.theme.ClickZoneConfig
import com.example.reader.ui.theme.FontFamilyKey
import com.example.reader.ui.theme.PageAnimationMode
import com.example.reader.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Bottom settings sheet: typography, spacing, theme, font, animation, texture, click zones, RTL.
 * Every change is written straight to [AppPrefs]; the ViewModel's style-config flow re-paginates
 * in real time while preserving the reading position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val fontManager = remember { FontManager.getInstance(context) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { fontManager.importFontFromUri(context, uri) }
    }

    val fontScale by prefs.fontScale.collectAsState(initial = 1.0f)
    val lineSpacing by prefs.lineSpacing.collectAsState(initial = 1.6f)
    val paragraphSpacing by prefs.paragraphSpacing.collectAsState(initial = 8)
    val letterSpacing by prefs.letterSpacing.collectAsState(initial = 0.5f)
    val pageMargin by prefs.pageMargin.collectAsState(initial = 16)
    val firstLineIndent by prefs.firstLineIndent.collectAsState(initial = 0)
    val themeMode by prefs.themeMode.collectAsState(initial = "light")
    val alignment by prefs.alignment.collectAsState(initial = "start")
    val fontFamily by prefs.fontFamily.collectAsState(initial = "default")
    val pageAnimation by prefs.pageAnimation.collectAsState(initial = "smooth")
    val rtl by prefs.rtl.collectAsState(initial = false)
    val textureKey by prefs.textureKey.collectAsState(initial = "none")
    val clickZones by prefs.clickZones.collectAsState(initial = ClickZoneConfig.toKey(ClickZoneConfig()))

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            LabeledSlider("字号", fontScale, 0.5f, 3f, 0.1f) { scope.launch { prefs.setFontScale(it) } }
            LabeledSlider("行距", lineSpacing, 1f, 3f, 0.1f) { scope.launch { prefs.setLineSpacing(it) } }
            LabeledSlider("段距(px)", paragraphSpacing.toFloat(), 0f, 48f, 1f) { scope.launch { prefs.setParagraphSpacing(it.toInt()) } }
            LabeledSlider("字距", letterSpacing, 0f, 8f, 0.1f) { scope.launch { prefs.setLetterSpacing(it) } }
            LabeledSlider("页边距(px)", pageMargin.toFloat(), 0f, 64f, 1f) { scope.launch { prefs.setPageMargin(it.toInt()) } }
            LabeledSlider("首行缩进(px)", firstLineIndent.toFloat(), 0f, 64f, 1f) { scope.launch { prefs.setFirstLineIndent(it.toInt()) } }

            Spacer(Modifier.height(8.dp))
            SectionTitle("对齐")
            ChipRow(listOf("start" to "起始", "center" to "居中", "justify" to "两端", "end" to "末尾"), alignment) {
                scope.launch { prefs.setAlignment(it) }
            }

            SectionTitle("主题")
            ChipRow(ThemeMode.entries.map { it.storageKey to it.name }, themeMode) {
                scope.launch { prefs.setThemeMode(it) }
            }

            SectionTitle("字体")
            ChipRow(FontFamilyKey.entries.map { it.storageKey to it.name }, fontFamily) {
                scope.launch { prefs.setFontFamily(it) }
            }
            OutlinedButton(onClick = { fontPicker.launch("font/*") }) {
                Text("导入字体 (TTF/OTF)")
            }

            SectionTitle("翻页动画")
            ChipRow(PageAnimationMode.entries.map { it.storageKey to it.name }, pageAnimation) {
                scope.launch { prefs.setPageAnimation(it) }
            }

            SectionTitle("背景纹理")
            ChipRow(listOf("none" to "无", "paper" to "纸张", "wood" to "木纹", "linen" to "亚麻"), textureKey) {
                scope.launch { prefs.setTextureKey(it) }
            }

            SectionTitle("点击区域")
            val clickPresets = listOf(
                ClickZoneConfig() to "默认",
                ClickZoneConfig(
                    left = ClickZoneAction.PREVIOUS_PAGE, right = ClickZoneAction.NEXT_PAGE,
                    top = ClickZoneAction.NONE, bottom = ClickZoneAction.NONE, center = ClickZoneAction.OPEN_MENU
                ) to "左右翻页",
                ClickZoneConfig(
                    left = ClickZoneAction.NONE, right = ClickZoneAction.NONE,
                    top = ClickZoneAction.PREVIOUS_PAGE, bottom = ClickZoneAction.NEXT_PAGE, center = ClickZoneAction.OPEN_MENU
                ) to "上下翻页",
                ClickZoneConfig(
                    left = ClickZoneAction.OPEN_MENU, right = ClickZoneAction.OPEN_MENU,
                    top = ClickZoneAction.OPEN_MENU, bottom = ClickZoneAction.OPEN_MENU, center = ClickZoneAction.OPEN_MENU
                ) to "仅菜单"
            )
            ChipRow(clickPresets.map { ClickZoneConfig.toKey(it.first) to it.second }, clickZones) {
                scope.launch { prefs.setClickZones(it) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("从右到左 (RTL)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = rtl, onCheckedChange = { scope.launch { prefs.setRtl(it) } })
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
