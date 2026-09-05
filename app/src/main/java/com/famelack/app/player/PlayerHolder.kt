package com.famelack.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/**
 * Singleton holder for the MediaController bound to [PlaybackService].
 * The UI obtains it once, then re-uses it across screens.
 */
object PlayerHolder {
    private var controller: MediaController? = null
    private var binding: Boolean = false

    fun get(context: Context): MediaController? = controller

    fun bind(context: Context, onReady: (MediaController) -> Unit) {
        if (controller != null) { onReady(controller!!); return }
        if (binding) return
        binding = true
        val ctx = context.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener(
            {
                controller = future.get()
                binding = false
                onReady(controller!!)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playStream(context: Context, url: String, title: String, isHls: Boolean = true) {
        bind(context) { c ->
            val mime = if (isHls) MimeTypes.APPLICATION_M3U8 else null
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .setMimeType(mime)
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.playWhenReady = true
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
