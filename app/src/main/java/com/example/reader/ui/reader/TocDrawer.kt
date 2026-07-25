package com.example.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.reader.db.AppDatabase
import com.example.reader.db.TocRulePrefEntity
import com.example.reader.parser.Chapter
import com.example.reader.parser.TocRules
import kotlinx.coroutines.launch

/**
 * Left navigation drawer showing the chapter list (jump) and the per-book TOC rule toggles (E05).
 */
@Composable
fun TocDrawer(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    bookId: String,
    onChapterClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).tocRulePrefDao() }
    val prefs by dao.getByBookId(bookId).collectAsState(initial = emptyList())
    val enabledMap = remember(prefs) { prefs.associate { it.ruleId to it.enabled } }

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("目录", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(chapters) { idx, ch ->
                    Text(
                        text = ch.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClick(idx) }
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (idx == currentChapterIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            Divider()
            Text("章节识别规则", style = MaterialTheme.typography.titleSmall)
            TocRules.ALL.forEach { rule ->
                val checked = enabledMap[rule.id] ?: true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text(rule.label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = checked,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                dao.upsert(TocRulePrefEntity(bookId = bookId, ruleId = rule.id, enabled = enabled))
                            }
                        }
                    )
                }
            }
        }
    }
}
