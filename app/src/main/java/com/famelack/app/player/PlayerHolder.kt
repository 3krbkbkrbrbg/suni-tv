package com.famelack.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Singleton holder for MediaController bound to [PlaybackService].
 * All controller interactions run on the Main Executor.
 */
object PlayerHolder {
    private var controller: MediaController? = null
    private var isBinding: Boolean = false

    fun get(context: Context): MediaController? = controller

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
                    onReady?.invoke(ctrl)
                } catch (e: Throwable) {
                    isBinding = false
                    Log.e(TAG, "Failed to bind MediaController", e)
                }
            },
            executor
        )
    }

    fun playStream(context: Context, url: String, title: String) {
        bind(context) { c ->
            try {
                val lowerUrl = url.lowercase()
                val mimeType = when {
                    lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8") || lowerUrl.contains("/hls") -> MimeTypes.APPLICATION_M3U8
                    lowerUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                    lowerUrl.contains(".mp3") -> MimeTypes.AUDIO_MPEG
                    lowerUrl.contains(".aac") -> MimeTypes.AUDIO_AAC
                    else -> null // Let ExoPlayer auto-detect format
                }

                val item = MediaItem.Builder()
                    .setUri(url)
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

                c.setMediaItem(item)
                c.prepare()
                c.playWhenReady = true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to play stream: $url", e)
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
        isBinding = false
    }

    private const val TAG = "PlayerHolder"
}
