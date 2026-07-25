package com.example.reader.feature.wifi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * ViewModel for the WiFi transfer screen (E02).
 *
 * Owns the lifecycle of a [WifiServer]: [start] binds the embedded HTTP server on [DEFAULT_PORT]
 * and resolves the device LAN address; [stop] tears it down. The UI polls [refreshCount] to
 * surface how many books have been imported during the current session.
 */
class WifiTransferViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface WifiStatus {
        data object Stopped : WifiStatus
        data class Running(val url: String, val importedCount: Int) : WifiStatus
        data class Error(val message: String) : WifiStatus
    }

    private val _status = MutableStateFlow<WifiStatus>(WifiStatus.Stopped)
    val status: StateFlow<WifiStatus> = _status.asStateFlow()

    private var server: WifiServer? = null

    fun start() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ip = getLocalIpAddress()
                if (ip == null) {
                    _status.value = WifiStatus.Error("无法获取本机地址，请连接 WiFi 后重试")
                    return@launch
                }
                val srv = WifiServer(DEFAULT_PORT, getApplication())
                srv.start()
                server = srv
                _status.value = WifiStatus.Running("http://$ip:$DEFAULT_PORT", 0)
            } catch (e: Exception) {
                _status.value = WifiStatus.Error(e.message ?: "启动失败")
            }
        }
    }

    fun stop() {
        runCatching { server?.stop() }
        server = null
        _status.value = WifiStatus.Stopped
    }

    /** Polls the server for the latest imported count (call from a UI tick). */
    fun refreshCount() {
        val srv = server ?: return
        val cur = _status.value
        if (cur is WifiStatus.Running) {
            _status.value = cur.copy(importedCount = srv.lastImportedCount)
        }
    }

    override fun onCleared() {
        stop()
    }

    /** Resolves the first non-loopback IPv4 address of the device. */
    private fun getLocalIpAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is InetAddress &&
                        addr.hostAddress.indexOf(':') < 0
                    ) {
                        val host = addr.hostAddress
                        if (host != null && !host.contains(':')) return host
                    }
                }
            }
            null
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
