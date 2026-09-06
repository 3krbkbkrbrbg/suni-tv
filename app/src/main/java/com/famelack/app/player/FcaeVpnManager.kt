package com.famelack.app.player

import android.content.Context
import android.util.Log
import com.fc.fcaevpn.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Manages the embedded FCAE VPN engine (v1.2.9).
 * Upstream project: https://github.com/FCFlenkchy/FCAE_VPN
 * Runs in-process via JNI NativeEngine, exposing local SOCKS5 (1819) and HTTP (1820) proxies.
 */
object FcaeVpnManager {
    private const val TAG = "FcaeVpnManager"
    const val SOCKS_PORT = 1819
    const val HTTP_PORT = 1820

    private var isRunning = false

    private val _statusFlow = MutableStateFlow("غیرفعال")
    val statusFlow: StateFlow<String> = _statusFlow.asStateFlow()

    private val _engineLogsFlow = MutableStateFlow("")
    val engineLogsFlow: StateFlow<String> = _engineLogsFlow.asStateFlow()

    fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 350)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getLogs(): String {
        return try {
            NativeEngine.nativeGetLogs()
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun start(
        context: Context,
        socksPort: Int = SOCKS_PORT,
        httpPort: Int = HTTP_PORT,
        protocol: Int = 0,
        useH2: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val socksReadyNow = isPortOpen(socksPort)
        val httpReadyNow = isPortOpen(httpPort)
        if (socksReadyNow || httpReadyNow) {
            isRunning = true
            val active = if (socksReadyNow) socksPort else httpPort
            _statusFlow.value = "متصل روی 127.0.0.1:$active"
            return@withContext true
        }

        if (!NativeEngine.loadLibrary()) {
            _statusFlow.value = "خطا: کتابخانه بومی FCAE لود نشد"
            return@withContext false
        }

        try {
            _statusFlow.value = "در حال اتصال به کلودفلر (MASQUE)..."
            val configDir = File(context.filesDir, "fcae_vpn").apply { mkdirs() }
            val cfgPath = File(configDir, "aether.toml").absolutePath

            try { NativeEngine.nativeStop() } catch (_: Throwable) {}

            val ok = NativeEngine.nativeStart(
                protocol = protocol,  // 0 = MASQUE
                mode = 0,             // 0 = Proxy (no VPN permission required)
                lanSharing = false,
                scanMode = 0,         // 0 = Turbo
                ipVersion = 4,        // 4 = IPv4
                quickReconnect = true,
                noizeProfile = "balanced",
                fragmentEnabled = false,
                fragMinSize = 16,
                fragMaxSize = 32,
                fragMinDelay = 2,
                fragMaxDelay = 10,
                socksPort = socksPort,
                httpPort = httpPort,
                forcePeer = "",
                configPath = cfgPath,
                h2Enabled = useH2,
                echEnabled = true,
                sni = "",
                sysProfile = 0,
                teamName = "",
                accessToken = "",
                accessEmail = "",
                routesFile = "",
                routesInline = ""
            )

            if (!ok) {
                val err = NativeEngine.nativeGetLastError().ifBlank { "استارت موتور بومی ناموفق بود" }
                _statusFlow.value = "خطا: $err"
                return@withContext false
            }

            // Poll state & ports up to 20 seconds with live telemetry
            val deadline = System.currentTimeMillis() + 20000
            while (System.currentTimeMillis() < deadline) {
                val state = try { NativeEngine.nativeGetState() } catch (_: Throwable) { -1 }
                val statusMsg = try { NativeEngine.nativeGetStatusMsg() } catch (_: Throwable) { "" }
                val lastErr = try { NativeEngine.nativeGetLastError() } catch (_: Throwable) { "" }
                val logs = try { NativeEngine.nativeGetLogs() } catch (_: Throwable) { "" }
                _engineLogsFlow.value = logs

                val socksReady = isPortOpen(socksPort)
                val httpReady = isPortOpen(httpPort)

                if (state == 4 || socksReady || httpReady) {
                    isRunning = true
                    val activePort = if (socksReady) socksPort else httpPort
                    _statusFlow.value = "متصل شد (پورت $activePort فعال است)"
                    Log.i(TAG, "FCAE VPN connected: socks=$socksPort, http=$httpPort")
                    return@withContext true
                }

                if (state == 5) {
                    val err = if (lastErr.isNotBlank()) lastErr else (if (statusMsg.isNotBlank()) statusMsg else "خطا در اتصال")
                    _statusFlow.value = "خطا: $err"
                    return@withContext false
                }

                if (statusMsg.isNotBlank()) {
                    _statusFlow.value = when (state) {
                        1 -> "در حال ثبت هویت در کلودفلر ($statusMsg)..."
                        2 -> "در حال اسکن گیت‌وی‌ها ($statusMsg)..."
                        3 -> "در حال برقراری تونل ($statusMsg)..."
                        else -> statusMsg
                    }
                }

                Thread.sleep(350)
            }

            val finalLogs = try { NativeEngine.nativeGetLogs() } catch (_: Throwable) { "" }
            _engineLogsFlow.value = finalLogs
            val finalStatus = NativeEngine.nativeGetStatusMsg()
            val finalErr = NativeEngine.nativeGetLastError()
            _statusFlow.value = if (finalErr.isNotBlank()) finalErr else (if (finalStatus.isNotBlank()) finalStatus else "تایم‌اوت اتصال (پورت باز نشد)")
            return@withContext isPortOpen(socksPort) || isPortOpen(httpPort)
        } catch (e: Exception) {
            val err = "خطا: ${e.message}"
            Log.e(TAG, err, e)
            _statusFlow.value = err
            return@withContext false
        }
    }

    fun stop() {
        try {
            NativeEngine.nativeStop()
            isRunning = false
            _statusFlow.value = "غیرفعال"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping FCAE VPN", e)
        }
    }
}
