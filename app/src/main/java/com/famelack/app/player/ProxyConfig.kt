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

enum class ProxyMode(val label: String, val desc: String) {
    URL_RELAY("Stream Relay (Vercel/Worker)", "Routes stream via your Vercel or Cloudflare Worker URL"),
    LOCAL_SOCKS5("Local SOCKS5 (V2RayNG)", "Routes via local VPN/v2ray proxy on 127.0.0.1:10808"),
    LOCAL_HTTP("Local HTTP Proxy", "Routes via local HTTP proxy on 127.0.0.1:10809")
}

/**
 * Proxy configuration for bypassing stream blocks, filtering, and geo-restrictions.
 * Supports:
 * 1. URL Relay (Vercel Edge / Cloudflare Worker / VPS)
 * 2. Local SOCKS5 Proxy (V2RayNG / Nekobox / Clash / Shadowsocks on 127.0.0.1:10808)
 * 3. Local HTTP Proxy (127.0.0.1:10809)
 * Settings persist to SharedPreferences.
 */
object ProxyConfig {
    private const val PREFS_NAME = "famelack_proxy_prefs"
    private const val KEY_ENABLED = "proxy_enabled"
    private const val KEY_MODE = "proxy_mode"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_HTTP_PORT = "http_port"
    private const val KEY_AUTO_FALLBACK = "auto_fallback"

    var isProxyEnabled: Boolean = false
    var proxyMode: ProxyMode = ProxyMode.URL_RELAY
    var relayUrl: String = "" // User's deployed Vercel or Worker URL: e.g. https://my-proxy.vercel.app/?url=
    var localSocksPort: Int = 10808
    var localHttpPort: Int = 10809
    var autoFallbackToDirect: Boolean = true

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isProxyEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val modeName = prefs.getString(KEY_MODE, ProxyMode.URL_RELAY.name) ?: ProxyMode.URL_RELAY.name
        proxyMode = try { ProxyMode.valueOf(modeName) } catch (_: Exception) { ProxyMode.URL_RELAY }
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
                // No proxy URL configured yet — do NOT break original stream!
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

        // For LOCAL_SOCKS5 and LOCAL_HTTP, the URL is handled at socket level in OkHttp
        return originalUrl
    }

    fun getSocketProxy(): Proxy? {
        if (!isProxyEnabled) return null
        return try {
            when (proxyMode) {
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
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val testStream = "https://musichls.persiana.live/hls/stream.m3u8"
        val start = System.currentTimeMillis()

        try {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
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
                    Pair(true, "Connected successfully (${duration}ms)")
                } else {
                    Pair(false, "Server returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
