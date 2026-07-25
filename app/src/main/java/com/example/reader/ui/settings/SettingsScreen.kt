package com.example.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.reader.prefs.AppPrefs
import com.example.reader.ui.theme.FontFamilyKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * System settings screen (v1.1).
 *
 * Provides inline controls for theme mode and default font, plus navigation
 * entries for TOC rules sub-screen and the "About" section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTocRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    val themeMode by prefs.themeMode.collectAsState(initial = "light")
    val defaultFont by prefs.fontFamily.collectAsState(initial = "default")

    var fontDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── Theme mode ──
            Text("主题模式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val themeOptions = listOf(
                "light" to "浅色",
                "dark" to "深色",
                "follow_system" to "跟随系统"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(themeOptions) { (key, label) ->
                    FilterChip(
                        selected = themeMode == key,
                        onClick = { scope.launch { prefs.setThemeMode(key) } },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            // ── Default font ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "默认字体",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Row(
                        modifier = Modifier.clickable { fontDropdownExpanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FontFamilyKey.entries.firstOrNull { it.storageKey == defaultFont }?.name
                                ?: "系统默认",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = fontDropdownExpanded,
                        onDismissRequest = { fontDropdownExpanded = false }
                    ) {
                        FontFamilyKey.entries.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(key.name) },
                                onClick = {
                                    scope.launch { prefs.setFontFamily(key.storageKey) }
                                    fontDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Divider()

            // ── TOC rules ──
            SettingsEntryRow(
                label = "目录规则",
                subtitle = "章节识别规则开关",
                onClick = onNavigateToTocRules
            )
            Divider()

            // ── About ──
            SettingsEntryRow(
                label = "关于",
                subtitle = "阅读器 v1.1.0"
            )
        }
    }
}

@Composable
private fun SettingsEntryRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}
