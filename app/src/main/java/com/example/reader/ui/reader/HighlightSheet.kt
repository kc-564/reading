package com.example.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.reader.db.HighlightEntity
import com.example.reader.feature.highlight.HighlightManager
import kotlinx.coroutines.launch

/**
 * Highlight (text markup) manager sheet (F06). Lists existing highlights, lets the user paint the
 * current page with a chosen color, and delete highlights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightSheet(
    state: ReaderUiState.Ready,
    bookId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { HighlightManager(AppDatabase.getInstance(context)) }
    val highlights by manager.getFlow(bookId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("高亮", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(8.dp))
            Text("点击颜色高亮当前页：", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightManager.COLORS.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(color))
                            .clickable {
                                val page = state.globalPages.getOrNull(state.currentGlobalPage)
                                if (page != null) {
                                    scope.launch {
                                        manager.add(
                                            bookId = bookId,
                                            chapterIndex = page.chapterIndex,
                                            startChar = page.charStart,
                                            endChar = page.charEnd,
                                            colorArgb = color
                                        )
                                    }
                                }
                            }
                    )
                }
            }
            Spacer(Modifier.padding(8.dp))
            LazyColumn {
                items(highlights) { hl: HighlightEntity ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(16.dp).background(Color(hl.colorArgb)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "第 ${hl.chapterIndex + 1} 章  ${hl.startChar}–${hl.endChar}",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { scope.launch { manager.remove(hl.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除高亮")
                        }
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}
