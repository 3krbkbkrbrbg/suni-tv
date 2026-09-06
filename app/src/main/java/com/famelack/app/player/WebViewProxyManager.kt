package com.famelack.app.player

import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

/**
 * Configures WebView process-level proxy routing.
 * Used to route YouTube WebView traffic through FCAE HTTP proxy (127.0.0.1:1820).
 *
 * WebView does not support SOCKS5 proxy natively — this uses the FCAE HTTP proxy port
 * which is available on the same engine. The ProxyController API from AndroidX WebKit
 * sets the proxy for ALL WebView instances in the process.
 */
object WebViewProxyManager {
    private const val TAG = "WebViewProxyManager"

    /** Currently active proxy target, null = direct (no proxy) */
    private var activeHost: String? = null

    /**
     * Route all WebView traffic through the specified HTTP proxy.
     * Falls back to direct if WebKit ProxyController is not available.
     */
    fun setProxy(host: String, port: Int) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY)) {
            Log.w(TAG, "WebViewFeature.PROXY not supported on this device — WebView will not use proxy")
            return
        }

        val rule = "$host:$port"
        if (activeHost == rule) {
            Log.d(TAG, "Proxy already set to $rule — skipping")
            return
        }

        try {
            val config = ProxyConfig.Builder()
                .addProxyRule(rule)
                .addDirect()
                .build()

            ProxyController.getInstance().setProxyOverride(
                config,
                Runnable::run,
                object : ProxyController.ProxyListener {
                    override fun onCount(count: Int) {
                        Log.i(TAG, "WebView proxy set to $rule — $count connections routed")
                        activeHost = rule
                    }
                    override fun onError(error: String) {
                        Log.e(TAG, "Failed to set WebView proxy: $error")
                        activeHost = null
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception setting WebView proxy", e)
            activeHost = null
        }
    }

    /**
     * Reset WebView proxy to direct (no proxy).
     */
    fun clearProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY)) return
        if (activeHost == null) return

        try {
            val config = ProxyConfig.Builder()
                .addDirect()
                .build()

            ProxyController.getInstance().setProxyOverride(
                config,
                Runnable::run,
                object : ProxyController.ProxyListener {
                    override fun onCount(count: Int) {
                        Log.i(TAG, "WebView proxy cleared — $count connections direct")
                        activeHost = null
                    }
                    override fun onError(error: String) {
                        Log.e(TAG, "Failed to clear WebView proxy: $error")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception clearing WebView proxy", e)
            activeHost = null
        }
    }

    /**
     * Convenience: apply proxy based on current ProxyConfig state.
     * Call this before loading YouTube in WebView.
     */
    fun applyFromProxyConfig() {
        if (ProxyConfig.isProxyEnabled && ProxyConfig.proxyMode == ProxyMode.FCAE_VPN) {
            setProxy("127.0.0.1", FcaeVpnManager.HTTP_PORT)
        } else if (ProxyConfig.isProxyEnabled && ProxyConfig.proxyMode == ProxyMode.LOCAL_HTTP) {
            setProxy("127.0.0.1", ProxyConfig.localHttpPort)
        } else {
            clearProxy()
        }
    }

    /** True if proxy is currently applied to WebView */
    fun isProxyActive(): Boolean = activeHost != null
}
