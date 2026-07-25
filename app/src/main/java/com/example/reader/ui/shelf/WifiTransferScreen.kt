package com.example.reader.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.feature.wifi.WifiTransferViewModel
import kotlinx.coroutines.delay

/**
 * WiFi 传书 screen (E02). Shows the LAN upload URL and start/stop control. While the server is
 * running it polls [WifiTransferViewModel.refreshCount] once a second to surface the imported count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTransferScreen(onNavigateBack: () -> Unit = {}) {
    val viewModel: WifiTransferViewModel = viewModel()
    val status by viewModel.status.collectAsStateWithLifecycle(WifiTransferViewModel.WifiStatus.Stopped)

    LaunchedEffect(status) {
        if (status is WifiTransferViewModel.WifiStatus.Running) {
            while (true) {
                delay(1000)
                viewModel.refreshCount()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi 传书") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "在电脑浏览器打开下方地址，选择 TXT / ZIP / RAR 文件上传，书籍将自动入库。",
                style = MaterialTheme.typography.bodyMedium
            )

            when (val s = status) {
                is WifiTransferViewModel.WifiStatus.Stopped -> {
                    Text("服务未启动。", style = MaterialTheme.typography.bodyMedium)
                }
                is WifiTransferViewModel.WifiStatus.Running -> {
                    Text("上传地址：", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(s.url, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("本次已导入 ${s.importedCount} 本", style = MaterialTheme.typography.bodyMedium)
                }
                is WifiTransferViewModel.WifiStatus.Error -> {
                    Text("错误：${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Button(
                onClick = {
                    if (status is WifiTransferViewModel.WifiStatus.Running) viewModel.stop() else viewModel.start()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (status is WifiTransferViewModel.WifiStatus.Running) "停止服务" else "启动服务")
            }
        }
    }
}
