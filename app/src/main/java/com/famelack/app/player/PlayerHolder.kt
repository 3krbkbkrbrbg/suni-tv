package com.famelack.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.famelack.app.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StreamQuality(
    val label: String,
    val description: String,
    val maxW: Int,
    val maxH: Int,
    val maxBitrate: Int
) {
    AUTO("Auto (HD)", "Best quality", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
    MEDIUM("480p", "Balanced (480p)", 854, 480, 1_000_000),
    DATA_SAVER("360p", "Data Saver (-70%)", 640, 360, 500_000),
    ULTRA_SAVER("240p", "Ultra Saver (-85%)", 426, 240, 250_000)
}

/**
 * Singleton holder for MediaController bound to [PlaybackService].
 * Manages playback, stream quality (data saver), proxy routing, and current channel state.
 */
object PlayerHolder {
    private var controller: MediaController? = null
    private var isBinding: Boolean = false

    var currentQuality: StreamQuality = StreamQuality.AUTO
        private set

    private val _currentChannelFlow = MutableStateFlow<Channel?>(null)
    val currentChannelFlow: StateFlow<Channel?> = _currentChannelFlow.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    fun get(context: Context): MediaController? = controller

    fun setCurrentChannel(channel: Channel?) {
        _currentChannelFlow.value = channel
    }

    fun bind(context: Context, onReady: ((MediaController) -> Unit)? = null) {
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
                            }
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

    fun playStream(context: Context, url: String, title: String, channel: Channel? = null) {
        if (channel != null) {
            _currentChannelFlow.value = channel
        }

        bind(context) { c ->
            try {
                val effectiveUrl = ProxyConfig.getEffectiveUrl(url)
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
                Log.e(TAG, "Failed to play stream: $url", e)
            }
        }
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
                .build()
            Log.d(TAG, "Applied quality: ${quality.label} (maxBitrate: ${quality.maxBitrate})")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to apply track selection quality", e)
        }
    }

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
    }

    fun release() {
        controller?.release()
        controller = null
        isBinding = false
        _currentChannelFlow.value = null
        _isPlayingFlow.value = false
    }

    private const val TAG = "PlayerHolder"
}
