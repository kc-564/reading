package com.example.reader.ui.shelf

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.reader.feature.import.ImportManager
import com.example.reader.feature.import.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bottom-sheet batch importer (D03 / C05).
 *
 * Lets the user pick multiple files (TXT / EPUB / ZIP / RAR) or an entire folder tree. Selected
 * documents are copied into the app cache (so SAF `content://` URIs become plain file paths),
 * then handed to [ImportManager.importArchives] which extracts archives and upserts books.
 *
 * Heavy work (copying files out of SAF + parsing) runs on [Dispatchers.IO] so the UI never
 * blocks; a progress indicator is shown while importing. When books are skipped because they
 * were already imported, a [Toast] informs the user ("已导入 N 本，已跳过重复 M 本").
 *
 * @param onImported Called with the [ImportResult] (imported / skipped counts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onImported: (ImportResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importManager = remember { ImportManager(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isImporting by remember { mutableStateOf(false) }

    /** Runs the import, shows a toast for duplicates, and forwards the result to [onImported]. */
    suspend fun runImport(paths: List<String>): ImportResult {
        val result = withContext(Dispatchers.IO) {
            runCatching { importManager.importArchives(paths) }.getOrElse { ImportResult(0, 0) }
        }
        if (result.imported > 0 && result.skipped > 0) {
            Toast.makeText(
                context,
                "已导入 ${result.imported} 本，已跳过重复 ${result.skipped} 本",
                Toast.LENGTH_LONG
            ).show()
        } else if (result.skipped > 0) {
            Toast.makeText(context, "已跳过重复 ${result.skipped} 本", Toast.LENGTH_LONG).show()
        } else if (result.imported > 0) {
            Toast.makeText(context, "已导入 ${result.imported} 本", Toast.LENGTH_SHORT).show()
        }
        return result
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            val paths = withContext(Dispatchers.IO) { uris.mapNotNull { copyUriToCache(context, it) } }
            val result = runImport(paths)
            isImporting = false
            onImported(result)
            onDismiss()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            val paths = withContext(Dispatchers.IO) { collectTreePaths(context, treeUri) }
            val result = runImport(paths)
            isImporting = false
            onImported(result)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isImporting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("导入书籍", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (isImporting) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("导入中…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Button(
                    onClick = { filePicker.launch("*/*") },
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
}

/** Copies a `content://` document into the app cache and returns its file path, or null. */
private suspend fun copyUriToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        // Try to preserve the original filename from the SAF URI
        val originalName = uri.lastPathSegment
            ?.replace(Regex("[^\\w.\\-\\u4e00-\\u9fff]"), "_")
            ?: "file"
        val safeName = "imp_${System.nanoTime()}_$originalName"
        val dir = File(context.cacheDir, "imports")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        target.absolutePath
    }.getOrNull()
}

/** Recursively collects TXT / ZIP / RAR / EPUB files under a document tree. */
private suspend fun collectTreePaths(context: Context, treeUri: Uri): List<String> = withContext(Dispatchers.IO) {
    val result = mutableListOf<String>()
    val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext result
    val stack = ArrayDeque<DocumentFile>().apply { add(tree) }
    while (stack.isNotEmpty()) {
        val dir = stack.removeFirst()
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                stack.add(child)
            } else {
                val name = child.name ?: ""
                if (name.endsWith(".txt", true) || name.endsWith(".zip", true) ||
                    name.endsWith(".rar", true) || name.endsWith(".epub", true)
                ) {
                    copyUriToCache(context, child.uri)?.let { result.add(it) }
                }
            }
        }
    }
    result
}
