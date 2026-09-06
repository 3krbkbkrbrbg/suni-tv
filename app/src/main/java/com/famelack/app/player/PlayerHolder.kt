package com.famelack.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.famelack.app.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class StreamQuality(
    val label: String,
    val description: String,
    val maxW: Int,
    val maxH: Int,
    val maxBitrate: Int,
    val maxAudioBitrate: Int
) {
    AUTO("Auto (HD)", "Best quality", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
    MEDIUM("480p", "Balanced (480p)", 854, 480, 1_000_000, 128_000),
    DATA_SAVER("360p", "Data Saver (-70%)", 640, 360, 500_000, 64_000),
    ULTRA_SAVER("240p", "Ultra Saver (-85%)", 426, 240, 250_000, 32_000)
}

/**
 * Singleton holder for MediaController bound to [PlaybackService].
 * Manages playback, stream quality (data saver), proxy routing, auto-fallback, and current channel state.
 */
object PlayerHolder {
    private var controller: MediaController? = null
    private var isBinding: Boolean = false
    private var appContext: Context? = null
    private var isUsingProxyForCurrent: Boolean = false

    var currentQuality: StreamQuality = StreamQuality.AUTO
        private set

    private val _currentChannelFlow = MutableStateFlow<Channel?>(null)
    val currentChannelFlow: StateFlow<Channel?> = _currentChannelFlow.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _lastErrorFlow = MutableStateFlow<String?>(null)
    val lastErrorFlow: StateFlow<String?> = _lastErrorFlow.asStateFlow()

    fun get(context: Context): MediaController? = controller

    fun setCurrentChannel(channel: Channel?) {
        _currentChannelFlow.value = channel
    }

    fun bind(context: Context, onReady: ((MediaController) -> Unit)? = null) {
        appContext = context.applicationContext
        val current = controller
        if (current != null && current.isConnected) {
            onReady?.invoke(current)
            return
        }
        if (isBinding) return
        isBinding = true

        val ctx = context.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        val executor = ContextCompat.getMainExecutor(ctx)

        future.addListener(
            {
                try {
                    val ctrl = future.get()
                    controller = ctrl
                    isBinding = false

                    ctrl.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlayingFlow.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                _isPlayingFlow.value = false
                            } else if (playbackState == Player.STATE_READY) {
                                _lastErrorFlow.value = null
                                retryAttempts = 0
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            onPlaybackError(error)
                        }
                    })

                    applyQuality(ctrl, currentQuality)
                    onReady?.invoke(ctrl)
                } catch (e: Throwable) {
                    isBinding = false
                    Log.e(TAG, "Failed to bind MediaController", e)
                }
            },
            executor
        )
    }

    // Proxy auto-reconnect state
    @Volatile private var lastRequestedUrl: String? = null
    @Volatile private var lastRequestedTitle: String? = null
    @Volatile private var lastRequestedChannel: Channel? = null
    @Volatile private var proxyCollectorStarted = false
    private var retryAttempts = 0
    private val maxRetries = 3

    private fun ensureProxyCollector() {
        if (proxyCollectorStarted) return
        proxyCollectorStarted = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            FcaeVpnManager.statusFlow.collect { status ->
                val ready = status.contains("متصل شد") || status.contains("متصل روی")
                if (ready) {
                    val curCtx = appContext ?: return@collect
                    val ch = lastRequestedChannel ?: _currentChannelFlow.value
                    val url = lastRequestedUrl ?: ch?.primaryUrl
                    val title = lastRequestedTitle ?: ch?.name
                    if (ch != null && url != null && title != null) {
                        val ctrl = controller
                        val isPlaying = ctrl?.isPlaying == true
                        val hasError = _lastErrorFlow.value != null
                        val isIdle = ctrl?.playbackState == Player.STATE_IDLE
                        // Also catch BUFFERING that got stuck due to proxy drop
                        val isStuck = ctrl?.playbackState == Player.STATE_BUFFERING && hasError
                        if (!isPlaying && (hasError || isIdle || isStuck)) {
                            android.util.Log.i(TAG, "Proxy ready ($status) -> auto-replay $title")
                            _lastErrorFlow.value = "پروکسی متصل شد — اتصال مجدد خودکار..."
                            kotlinx.coroutines.delay(700)
                            isUsingProxyForCurrent = ProxyConfig.isProxyEnabled
                            val targetUrl = ProxyConfig.getEffectiveUrl(url)
                            playUrlInternal(curCtx, targetUrl, title)
                            retryAttempts = 0
                        }
                    }
                }
            }
        }
    }

    fun playStream(context: Context, url: String, title: String, channel: Channel? = null) {
        appContext = context.applicationContext
        _lastErrorFlow.value = null
        lastRequestedUrl = url
        lastRequestedTitle = title
        lastRequestedChannel = channel
        retryAttempts = 0
        if (channel != null) {
            _currentChannelFlow.value = channel
        }
        ensureProxyCollector()

        // Start FCAE in background if needed, but NEVER block playback — play direct immediately, collector will re-route when ready
        if (ProxyConfig.isProxyEnabled && ProxyConfig.proxyMode == ProxyMode.FCAE_VPN && !FcaeVpnManager.isPortOpen(ProxyConfig.fcaePort)) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                FcaeVpnManager.start(context, ProxyConfig.fcaePort)
                // on success, collector auto-replays via proxy if needed
            }
            // If proxy not yet open, PlaybackService DataSource will transparently use direct (no SOCKS), so this play succeeds instantly
        }

        isUsingProxyForCurrent = ProxyConfig.isProxyEnabled
        // Only use proxy URL/relay if it actually changes the URL; SOCKS routing is done at DataSource level and gated by isPortOpen there
        val targetUrl = ProxyConfig.getEffectiveUrl(url)
        playUrlInternal(context, targetUrl, title)
    }

    fun replayLast(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        val ch = lastRequestedChannel ?: _currentChannelFlow.value ?: return
        val url = lastRequestedUrl ?: ch.primaryUrl ?: return
        val title = lastRequestedTitle ?: ch.name
        _lastErrorFlow.value = null
        isUsingProxyForCurrent = ProxyConfig.isProxyEnabled
        val targetUrl = ProxyConfig.getEffectiveUrl(url)
        playUrlInternal(ctx, targetUrl, title)
    }

    private fun playUrlInternal(context: Context, effectiveUrl: String, title: String) {
        bind(context) { c ->
            try {
                val lowerUrl = effectiveUrl.lowercase()
                val mimeType = when {
                    lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8") || lowerUrl.contains("/hls") -> MimeTypes.APPLICATION_M3U8
                    lowerUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                    lowerUrl.contains(".mp3") -> MimeTypes.AUDIO_MPEG
                    lowerUrl.contains(".aac") -> MimeTypes.AUDIO_AAC
                    else -> null
                }

                val item = MediaItem.Builder()
                    .setUri(effectiveUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .build()
                    ).apply {
                        if (mimeType != null) {
                            setMimeType(mimeType)
                        }
                    }
                    .build()

                applyQuality(c, currentQuality)
                c.setMediaItem(item)
                c.prepare()
                c.playWhenReady = true
                _isPlayingFlow.value = true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to play stream: $effectiveUrl", e)
                _lastErrorFlow.value = e.localizedMessage ?: "Playback failed"
            }
        }
    }

    fun onPlaybackError(error: PlaybackException) {
        val lastChannel = _currentChannelFlow.value
        val ctx = appContext

        // If we were using proxy and it is currently reconnecting -> retry via proxy instead of immediate fallback
        val proxyStillConnecting = ProxyConfig.isProxyEnabled && ProxyConfig.proxyMode == ProxyMode.FCAE_VPN
                && !FcaeVpnManager.isPortOpen(ProxyConfig.fcaePort)
                && (FcaeVpnManager.statusFlow.value.contains("در حال") || FcaeVpnManager.statusFlow.value.contains("اتصال"))

        if (lastChannel != null && ctx != null && isUsingProxyForCurrent) {
            if (proxyStillConnecting && retryAttempts < maxRetries) {
                retryAttempts++
                Log.w(TAG, "Proxy stream failed [${error.errorCodeName}] but proxy reconnecting (attempt $retryAttempts/$maxRetries) -> will auto-retry when ready")
                _lastErrorFlow.value = "اتصال پروکسی قطع شد — در حال اتصال مجدد خودکار... ($retryAttempts/$maxRetries)"
                // Ensure VPN is (re)starting; collector will replay when status becomes ready
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    FcaeVpnManager.start(ctx, ProxyConfig.fcaePort)
                }
                return
            }
            if (ProxyConfig.autoFallbackToDirect) {
                Log.w(TAG, "Proxy stream failed [${error.errorCodeName}]. Falling back to direct stream...")
                isUsingProxyForCurrent = false
                val directUrl = lastRequestedUrl ?: lastChannel.primaryUrl ?: return
                _lastErrorFlow.value = "پروکسی ناموفق — تلاش با اتصال مستقیم..."
                playUrlInternal(ctx, directUrl, lastChannel.name)
                return
            }
        }

        _lastErrorFlow.value = "Playback error: ${error.errorCodeName} (${error.message ?: "Connection dropped"})"
    }

    fun setCustomError(msg: String?) {
        _lastErrorFlow.value = msg
    }

    fun setQuality(quality: StreamQuality) {
        currentQuality = quality
        val c = controller ?: return
        applyQuality(c, quality)
    }

    private fun applyQuality(c: MediaController, quality: StreamQuality) {
        try {
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(quality.maxW, quality.maxH)
                .setMaxVideoBitrate(quality.maxBitrate)
                .setMaxAudioBitrate(quality.maxAudioBitrate)
                .setForceHighestSupportedBitrate(false)
                .build()
            appliedQuality = quality
            Log.d(TAG, "Applied quality: ${quality.label} (videoMax:${quality.maxBitrate} audioMax:${quality.maxAudioBitrate} ${quality.maxW}x${quality.maxH})")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to apply track selection quality", e)
        }
    }

    @Volatile var appliedQuality: StreamQuality = StreamQuality.AUTO
        private set

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            _isPlayingFlow.value = false
        } else {
            c.play()
            _isPlayingFlow.value = true
        }
    }

    fun pause() {
        controller?.pause()
        _isPlayingFlow.value = false
    }

    fun stop() {
        controller?.stop()
        _currentChannelFlow.value = null
        _isPlayingFlow.value = false
        _lastErrorFlow.value = null
    }

    fun release() {
        controller?.release()
        controller = null
        isBinding = false
        _currentChannelFlow.value = null
        _isPlayingFlow.value = false
        _lastErrorFlow.value = null
    }

    private const val TAG = "PlayerHolder"
}
