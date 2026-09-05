package com.famelack.app.player

import android.content.Context
import android.util.Log
import java.net.URLEncoder

/**
 * Proxy configuration for bypassing regional stream filters and geo-blocks.
 * GitHub Repo: https://github.com/3krbkbkrbrbg/famelack-stream-proxy
 */
object ProxyConfig {
    // Default public Cloudflare Worker endpoint generated from user's famelack-stream-proxy repo
    var defaultProxyBase: String = "https://famelack-stream-proxy.workers.dev/?url="
    var customProxyBase: String = ""
    var isProxyEnabled: Boolean = false

    fun getEffectiveUrl(originalUrl: String, forceProxy: Boolean = false): String {
        if (!isProxyEnabled && !forceProxy) {
            return originalUrl
        }
        val base = if (customProxyBase.isNotBlank()) customProxyBase else defaultProxyBase
        return try {
            val encoded = URLEncoder.encode(originalUrl, "UTF-8")
            "$base$encoded"
        } catch (e: Exception) {
            Log.e("ProxyConfig", "Failed to encode URL for proxy", e)
            originalUrl
        }
    }
}
