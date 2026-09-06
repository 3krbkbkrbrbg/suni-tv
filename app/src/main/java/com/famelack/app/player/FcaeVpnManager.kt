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

    fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun start(context: Context, socksPort: Int = SOCKS_PORT, httpPort: Int = HTTP_PORT): Boolean = withContext(Dispatchers.IO) {
        if (isPortOpen(socksPort) || isPortOpen(httpPort)) {
            isRunning = true
            _statusFlow.value = "فعال روی 127.0.0.1:$socksPort"
            return@withContext true
        }

        if (!NativeEngine.loadLibrary()) {
            _statusFlow.value = "خطا: کتابخانه بومی FCAE لود نشد"
            return@withContext false
        }

        try {
            _statusFlow.value = "در حال اتصال به شبکه FCAE (Cloudflare MASQUE)..."
            val configDir = File(context.filesDir, "fcae_vpn").apply { mkdirs() }
            val cfgPath = File(configDir, "aether.toml").absolutePath

            try { NativeEngine.nativeStop() } catch (_: Throwable) {}

            val ok = NativeEngine.nativeStart(
                protocol = 0,         // 0 = MASQUE (HTTP/3)
                mode = 0,             // 0 = Proxy (SOCKS/HTTP) - no VPN permissions needed
                lanSharing = false,
                scanMode = 0,         // 0 = Turbo
                ipVersion = 4,        // IPv4
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
                h2Enabled = false,
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
                val err = NativeEngine.nativeGetLastError().ifBlank { "اتصال اولیه ناموفق بود" }
                _statusFlow.value = "خطا: $err"
                return@withContext false
            }

            // Poll port up to 8 seconds
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline) {
                if (isPortOpen(socksPort) || isPortOpen(httpPort)) {
                    isRunning = true
                    _statusFlow.value = "متصل شد (SOCKS: $socksPort / HTTP: $httpPort)"
                    Log.i(TAG, "FCAE VPN running: socks=$socksPort, http=$httpPort")
                    return@withContext true
                }
                Thread.sleep(250)
            }

            val status = NativeEngine.nativeGetStatusMsg()
            _statusFlow.value = if (status.isNotBlank()) status else "پورت باز نشد"
            return@withContext isPortOpen(socksPort) || isPortOpen(httpPort)
        } catch (e: Exception) {
            val err = "خطا در استارت FCAE: ${e.message}"
            Log.e(TAG, err, e)
            _statusFlow.value = err
            return@withContext false
        }
    }

    fun stop() {
        try {
            NativeEngine.nativeStop()
            isRunning = false
            _statusFlow.value = "متوقف شد"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping FCAE VPN", e)
        }
    }
}
