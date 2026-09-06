package com.famelack.app.player

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class ProxyMode(val label: String, val desc: String, val defaultPort: Int) {
    AETHER_MASQUE("Aether MASQUE (پروکسی هوشمند)", "پروکسی آزاد MASQUE بر پایه HTTP/3 و کلودفلر (127.0.0.1:1819)", 1819),
    LOCAL_SOCKS5("SOCKS5 (V2RayNG)", "پروکسی محلی SOCKS5 روی 127.0.0.1:10808", 10808),
    LOCAL_HTTP("HTTP Proxy", "پروکسی محلی HTTP روی 127.0.0.1:10809", 10809),
    URL_RELAY("Stream Relay (Vercel/Worker)", "رله آنلاین HLS روی ورسل یا کلودفلر", 0)
}

/**
 * Proxy configuration for bypassing stream blocks, filtering, and geo-restrictions.
 * Supports:
 * 1. Aether MASQUE (HTTP/3 & HTTP/2 censorship-circumvention on 127.0.0.1:1819)
 * 2. Local SOCKS5 Proxy (V2RayNG / Nekobox / Clash on 127.0.0.1:10808)
 * 3. Local HTTP Proxy (127.0.0.1:10809)
 * 4. URL Relay (Vercel Edge / Cloudflare Worker / VPS)
 */
object ProxyConfig {
    private const val PREFS_NAME = "famelack_proxy_prefs"
    private const val KEY_ENABLED = "proxy_enabled"
    private const val KEY_MODE = "proxy_mode"
    private const val KEY_AETHER_PORT = "aether_port"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_HTTP_PORT = "http_port"
    private const val KEY_AUTO_FALLBACK = "auto_fallback"

    var isProxyEnabled: Boolean = false
    var proxyMode: ProxyMode = ProxyMode.AETHER_MASQUE
    var aetherPort: Int = 1819
    var relayUrl: String = ""
    var localSocksPort: Int = 10808
    var localHttpPort: Int = 10809
    var autoFallbackToDirect: Boolean = true

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isProxyEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val modeName = prefs.getString(KEY_MODE, ProxyMode.AETHER_MASQUE.name) ?: ProxyMode.AETHER_MASQUE.name
        proxyMode = try { ProxyMode.valueOf(modeName) } catch (_: Exception) { ProxyMode.AETHER_MASQUE }
        aetherPort = prefs.getInt(KEY_AETHER_PORT, 1819)
        relayUrl = prefs.getString(KEY_RELAY_URL, "") ?: ""
        localSocksPort = prefs.getInt(KEY_SOCKS_PORT, 10808)
        localHttpPort = prefs.getInt(KEY_HTTP_PORT, 10809)
        autoFallbackToDirect = prefs.getBoolean(KEY_AUTO_FALLBACK, true)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, isProxyEnabled)
            .putString(KEY_MODE, proxyMode.name)
            .putInt(KEY_AETHER_PORT, aetherPort)
            .putString(KEY_RELAY_URL, relayUrl.trim())
            .putInt(KEY_SOCKS_PORT, localSocksPort)
            .putInt(KEY_HTTP_PORT, localHttpPort)
            .putBoolean(KEY_AUTO_FALLBACK, autoFallbackToDirect)
            .apply()
    }

    fun getEffectiveUrl(originalUrl: String): String {
        if (!isProxyEnabled) {
            return originalUrl
        }

        if (proxyMode == ProxyMode.URL_RELAY) {
            val base = relayUrl.trim()
            if (base.isBlank()) {
                return originalUrl
            }
            return try {
                val encoded = URLEncoder.encode(originalUrl, "UTF-8")
                if (base.endsWith("=") || base.endsWith("/")) {
                    "$base$encoded"
                } else if (base.contains("?")) {
                    "$base&url=$encoded"
                } else {
                    "$base/?url=$encoded"
                }
            } catch (e: Exception) {
                Log.e("ProxyConfig", "Failed to encode URL for relay proxy", e)
                originalUrl
            }
        }

        // For AETHER_MASQUE, SOCKS5 and HTTP, URL is handled via Socket Proxy in OkHttp
        return originalUrl
    }

    fun getSocketProxy(): Proxy? {
        if (!isProxyEnabled) return null
        return try {
            when (proxyMode) {
                ProxyMode.AETHER_MASQUE -> {
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", aetherPort))
                }
                ProxyMode.LOCAL_SOCKS5 -> {
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localSocksPort))
                }
                ProxyMode.LOCAL_HTTP -> {
                    Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localHttpPort))
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("ProxyConfig", "Error creating socket proxy", e)
            null
        }
    }

    /**
     * Test the configured proxy connectivity.
     * Returns Pair<Boolean, String> (isSuccess, message / latency).
     */
    suspend fun testConnection(context: Context? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (proxyMode == ProxyMode.AETHER_MASQUE && context != null) {
            if (!AetherManager.isPortOpen(aetherPort)) {
                val started = AetherManager.ensureStarted(context, aetherPort)
                if (!started) {
                    return@withContext Pair(false, AetherManager.statusFlow.value)
                }
            }
        }

        val testStream = "https://musichls.persiana.live/hls/stream.m3u8"
        val start = System.currentTimeMillis()

        try {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            val socketProxy = getSocketProxy()
            if (socketProxy != null) {
                clientBuilder.proxy(socketProxy)
            }

            val client = clientBuilder.build()
            val targetUrl = getEffectiveUrl(testStream)

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    Pair(true, "اتصال موفق ($duration ms)")
                } else {
                    Pair(false, "پاسخ سرور: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            val msg = if (proxyMode == ProxyMode.AETHER_MASQUE && !AetherManager.isPortOpen(aetherPort)) {
                "سرویس Aether فعال نیست (پورت $aetherPort باز نشد)"
            } else {
                "خطای اتصال: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
            Pair(false, msg)
        }
    }
}
