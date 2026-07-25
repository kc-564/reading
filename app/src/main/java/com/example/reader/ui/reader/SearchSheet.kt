package com.example.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.reader.feature.export.ExportManager
import com.example.reader.feature.search.SearchHit
import android.widget.Toast

/**
 * Full-text search sheet (D02). Typing filters the book; tapping a result jumps to its position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    viewModel: ReaderViewModel,
    onJump: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val hits: List<SearchHit> = remember(query) { viewModel.search(query) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportManager = remember { ExportManager(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("搜索", style = MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入关键词") },
                singleLine = true
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val content = buildString {
                            hits.forEachIndexed { i, h -> append("${i + 1}. ${h.previewText}\n") }
                        }
                        val path = exportManager.exportText("search_${query.ifBlank { "all" }}.txt", content)
                        Toast.makeText(
                            context,
                            if (path != null) "已导出结果" else "导出失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导出结果") }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(hits) { hit ->
                    Text(
                        text = hit.previewText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onJump(hit.chapterIndex, hit.charOffset)
                                onDismiss()
                            }
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
