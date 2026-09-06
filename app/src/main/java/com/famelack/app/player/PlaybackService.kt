package com.famelack.app.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Background media playback service.
 * Supports cross-protocol redirects, cleartext HTTP, custom mobile User-Agent,
 * dynamic socket proxies (SOCKS5 / HTTP e.g. V2RayNG), and HLS/audio streams.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ProxyConfig.init(this)

        val dynamicDataSourceFactory = object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                val okHttpClientBuilder = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)

                // Only route through SOCKS/HTTP if the local port is actually open — otherwise direct is 10x faster and avoids 15s timeout
                val socketProxy = ProxyConfig.getSocketProxy()?.takeIf {
                    val open = when (ProxyConfig.proxyMode) {
                        ProxyMode.FCAE_VPN -> FcaeVpnManager.isPortOpen(ProxyConfig.fcaePort)
                        ProxyMode.LOCAL_SOCKS5 -> FcaeVpnManager.isPortOpen(ProxyConfig.localSocksPort)
                        ProxyMode.LOCAL_HTTP -> FcaeVpnManager.isPortOpen(ProxyConfig.localHttpPort)
                        else -> true
                    }
                    if (!open) android.util.Log.w(TAG, "Proxy ${ProxyConfig.proxyMode} port not open — using direct for this request")
                    open
                }
                if (socketProxy != null) {
                    okHttpClientBuilder.proxy(socketProxy)
                }

                val client = okHttpClientBuilder.build()
                return OkHttpDataSource.Factory(client)
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .createDataSource()
            }
        }

        // Adaptive LoadControl: smaller buffers for Data Saver modes to actually reduce data + faster start on slow nets
        val saver = try { PlayerHolder.appliedQuality } catch (_: Throwable) { null }
        val loadControl = when (saver?.name) {
            "ULTRA_SAVER" -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(10_000, 30_000, 1_000, 1_000)
                .setBackBuffer(0, false)
                .build()
            "DATA_SAVER" -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 40_000, 1_500, 2_000)
                .build()
            "MEDIUM" -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(20_000, 50_000, 1_500, 2_000)
                .build()
            else -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 60_000, 1_500, 2_000)
                .build()
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dynamicDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error [${error.errorCodeName}]: ${error.message}", error)
                // Handle fallback in PlayerHolder if proxy failed
                PlayerHolder.onPlaybackError(error)
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return super.onTaskRemoved(rootIntent)
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
    }
}
