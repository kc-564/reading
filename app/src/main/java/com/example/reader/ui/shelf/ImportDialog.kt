package com.example.reader.ui.shelf

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.reader.feature.import.ImportManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * Bottom-sheet batch importer (D03 / C05).
 *
 * Lets the user pick multiple files (TXT / ZIP / RAR) or an entire folder tree. Selected
 * documents are copied into the app cache (so SAF `content://` URIs become plain file paths),
 * then handed to [ImportManager.importArchives] which extracts archives and upserts books.
 *
 * @param onImported Called with the number of successfully imported books.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onImported: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importManager = remember { ImportManager(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val paths = uris.mapNotNull { copyUriToCache(context, it) }
        runImport(scope, importManager, paths, onImported, onDismiss)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        val paths = collectTreePaths(context, treeUri)
        runImport(scope, importManager, paths, onImported, onDismiss)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("导入书籍", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    filePicker.launch("*/*")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("选择文件 / 压缩包（可多选）") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("选择文件夹") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

private fun runImport(
    scope: kotlinx.coroutines.CoroutineScope,
    importManager: ImportManager,
    paths: List<String>,
    onImported: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    scope.launch {
        val count = runCatching { importManager.importArchives(paths) }.getOrElse { 0 }
        onImported(count)
        onDismiss()
    }
}

/** Copies a `content://` document into the app cache and returns its file path, or null. */
private fun copyUriToCache(context: Context, uri: Uri): String? = runCatching {
    val safeName = "imp_${System.nanoTime()}_${uri.lastPathSegment ?: "file"}"
        .replace(Regex("[^\\w.\\-]"), "_")
    val dir = File(context.cacheDir, "imports")
    if (!dir.exists()) dir.mkdirs()
    val target = File(dir, safeName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { out -> input.copyTo(out) }
    }
    target.absolutePath
}.getOrNull()

/** Recursively collects TXT / ZIP / RAR files under a document tree. */
private fun collectTreePaths(context: Context, treeUri: Uri): List<String> {
    val result = mutableListOf<String>()
    val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return result
    val stack = ArrayDeque<DocumentFile>().apply { add(tree) }
    while (stack.isNotEmpty()) {
        val dir = stack.removeFirst()
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                stack.add(child)
            } else {
                val name = child.name ?: ""
                if (name.endsWith(".txt", true) || name.endsWith(".zip", true) || name.endsWith(".rar", true)) {
                    copyUriToCache(context, child.uri)?.let { result.add(it) }
                }
            }
        }
    }
    return result
}
