package com.example.reader.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.data.ReadFilter
import com.example.reader.data.SortMode
import com.example.reader.db.BookEntity
import kotlinx.coroutines.launch

/**
 * Home / shelf screen (C04 / F05 / D03 / E02 / F07).
 *
 * Shows sort + filter controls, a "recent reading" strip, and a 3-column grid of [BookCover]
 * tiles. The top bar exposes import (opens [ImportDialog]), WiFi transfer and reading stats
 * navigation; each book tile's overflow menu exposes edit / delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onNavigateToReader: (String) -> Unit = {},
) {
    val viewModel: ShelfViewModel = viewModel()
    val books by viewModel.books.collectAsStateWithLifecycle(emptyList())
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle(SortMode.LAST_OPENED)
    val readFilter by viewModel.readFilter.collectAsStateWithLifecycle(ReadFilter.ALL)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showImport by remember { mutableStateOf(false) }
    var editBook by remember { mutableStateOf<BookEntity?>(null) }
    var menuBook by remember { mutableStateOf<BookEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "导入书籍")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImport = true }) {
                Icon(Icons.Filled.Add, contentDescription = "导入书籍")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SortFilterBar(
                        sortMode = sortMode,
                        readFilter = readFilter,
                        onSortChanged = viewModel::setSortMode,
                        onFilterChanged = viewModel::setReadFilter
                    )
                }

                item {
                    Text("全部书籍", style = MaterialTheme.typography.titleMedium)
                }

                if (books.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("书架空空如也，去导入书籍吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(books.chunked(3)) { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { book ->
                                Box(modifier = Modifier.weight(1f).padding(4.dp)) {
                                    BookCover(
                                        book = book,
                                        onOpen = { onNavigateToReader(book.bookId) },
                                        onMenu = { menuBook = book }
                                    )
                                }
                            }
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Per-book actions shown as a bottom sheet (long-press on a cover triggers it).
    if (menuBook != null) {
        ModalBottomSheet(
            onDismissRequest = { menuBook = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("操作", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        editBook = menuBook
                        menuBook = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("编辑信息") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        menuBook?.let { viewModel.deleteBook(it) }
                        menuBook = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("删除") }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImported = { count ->
                scope.launch {
                    snackbarHostState.showSnackbar("已导入 $count 本")
                }
            }
        )
    }

    if (editBook != null) {
        BookMetaEditor(
            book = editBook!!,
            onSave = { title, author, coverUri ->
                viewModel.saveBookMeta(editBook!!.bookId, title, author, coverUri)
                editBook = null
            },
            onDismiss = { editBook = null }
        )
    }
}
