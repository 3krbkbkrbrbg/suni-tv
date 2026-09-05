package com.famelack.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.famelack.app.FamelackApp
import com.famelack.app.data.Channel
import com.famelack.app.data.codeToFlag
import com.famelack.app.player.PlayerHolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = FamelackApp.instance

    var controller by remember { mutableStateOf<MediaController?>(PlayerHolder.get(context)) }
    var isFavorite by remember { mutableStateOf(false) }

    LaunchedEffect(channel.id) {
        app.favoritesStore.ids.collect { ids -> isFavorite = channel.id in ids }
    }

    LaunchedEffect(channel.id) {
        PlayerHolder.bind(context) { ctrl ->
            controller = ctrl
        }
        if (!channel.isYoutube && !channel.streamUrls.isNullOrEmpty()) {
            PlayerHolder.playStream(
                context = context,
                url = channel.streamUrls.first(),
                title = channel.name
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = channel.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { app.favoritesStore.toggle(channel.id) }
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Video / Stream Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when {
                    channel.isYoutube && channel.youtubeId != null -> {
                        // YouTube Live via WebChromeClient
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                    }
                                    webViewClient = WebViewClient()
                                    webChromeClient = WebChromeClient()

                                    val html = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                            <style>
                                                html, body { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                                                iframe { width: 100%; height: 100%; border: 0; }
                                            </style>
                                        </head>
                                        <body>
                                            <iframe 
                                                src="https://www.youtube-nocookie.com/embed/${channel.youtubeId}?autoplay=1&playsinline=1&enablejsapi=1&rel=0&modestbranding=1" 
                                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                                                allowfullscreen>
                                            </iframe>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                                }
                            }
                        )
                    }

                    !channel.streamUrls.isNullOrEmpty() -> {
                        // Native Media3 PlayerView for HLS / Live Stream
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                    controller?.let { this.player = it }
                                }
                            },
                            update = { playerView ->
                                controller?.let { playerView.player = it }
                            }
                        )
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No playable stream found", color = Color.White)
                        }
                    }
                }
            }

            // Controls and Channel Details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Country and Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${codeToFlag(channel.country.uppercase())}  ${channel.country.uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.weight(1f))
                    if (channel.isGeoBlocked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Geo-Restricted",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action Buttons: Reconnect / External Player / Copy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!channel.isYoutube && !channel.streamUrls.isNullOrEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                PlayerHolder.playStream(
                                    context = context,
                                    url = channel.streamUrls.first(),
                                    title = channel.name
                                )
                                Toast.makeText(context, "Reconnecting...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reconnect")
                        }

                        OutlinedButton(
                            onClick = {
                                val url = channel.primaryUrl ?: return@OutlinedButton
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(url), "video/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Play with"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No external player found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("External")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Languages
                if (channel.languages.isNotEmpty()) {
                    Text(
                        text = "Languages: ${channel.languages.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Stream URL + Copy Button
                channel.primaryUrl?.let { streamUrl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = streamUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Stream URL", streamUrl))
                                Toast.makeText(context, "Copied stream URL", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
