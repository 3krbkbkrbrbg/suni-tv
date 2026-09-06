package com.famelack.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.famelack.app.FamelackApp
import com.famelack.app.data.Channel
import com.famelack.app.data.codeToFlag
import com.famelack.app.player.PlayerHolder
import com.famelack.app.player.ProxyConfig
import com.famelack.app.player.StreamQuality
import com.famelack.app.player.WebViewProxyManager
import com.famelack.app.ui.components.ProxySettingsDialog
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = FamelackApp.instance
    val activity = remember(context) { context.findActivity() }

    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isManualLandscape by remember { mutableStateOf<Boolean?>(null) }
    val isLandscape = isManualLandscape ?: isDeviceLandscape

    var controller by remember { mutableStateOf<MediaController?>(PlayerHolder.get(context)) }
    var isFavorite by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(PlayerHolder.currentQuality) }
    var isProxyActive by remember { mutableStateOf(ProxyConfig.isProxyEnabled) }
    var showProxyDialog by remember { mutableStateOf(false) }

    // If channel has only YouTube, default to YouTube mode; otherwise default to HLS stream
    var showYoutubeMode by remember(channel.id) { mutableStateOf(channel.isYoutubeOnly) }

    val lastError by PlayerHolder.lastErrorFlow.collectAsState()

    fun toggleOrientation() {
        val nextLandscape = !isLandscape
        isManualLandscape = nextLandscape
        activity?.requestedOrientation = if (nextLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Clear WebView proxy on exit to avoid leaking proxy config to other WebView users
            WebViewProxyManager.clearProxy()
        }
    }

    LaunchedEffect(channel.id) {
        app.favoritesStore.ids.collect { ids -> isFavorite = channel.id in ids }
    }

    fun startPlayback() {
        if (!showYoutubeMode && channel.hasStreams) {
            PlayerHolder.playStream(
                context = context,
                url = channel.streamUrls.first(),
                title = channel.name,
                channel = channel
            )
        }
    }

    LaunchedEffect(channel.id, showYoutubeMode) {
        PlayerHolder.bind(context) { ctrl ->
            controller = ctrl
        }
        if (!showYoutubeMode) {
            startPlayback()
            WebViewProxyManager.clearProxy()
        } else {
            PlayerHolder.pause()
            // Route YouTube WebView traffic through FCAE proxy if active
            WebViewProxyManager.applyFromProxyConfig()
        }
    }

    fun launchYouTubeApp() {
        val yId = channel.youtubeId ?: return
        val ytUri = Uri.parse("https://www.youtube.com/watch?v=$yId")
        val intent = Intent(Intent.ACTION_VIEW, ytUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            intent.setPackage("com.google.android.youtube")
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(Intent.createChooser(intent, "پخش در برنامه یوتیوب"))
            } catch (e: Exception) {
                Toast.makeText(context, "برنامه‌ای برای باز کردن یوتیوب پیدا نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLandscape) {
            // Fullscreen Landscape Video Area
            VideoBox(
                channel = channel,
                showYoutubeMode = showYoutubeMode,
                controller = controller,
                isLandscape = true,
                onToggleOrientation = { toggleOrientation() },
                onBack = { toggleOrientation() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Portrait Layout: TopAppBar + 16:9 Video Box + Controls
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
                        // Orientation / Rotate Screen Button
                        IconButton(onClick = { toggleOrientation() }) {
                            Icon(
                                imageVector = Icons.Filled.ScreenRotation,
                                contentDescription = "چرخش افقی تصویر",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showProxyDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = "Proxy Settings",
                                tint = if (isProxyActive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                            )
                        }
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

                // 16:9 Video Display Area
                VideoBox(
                    channel = channel,
                    showYoutubeMode = showYoutubeMode,
                    controller = controller,
                    isLandscape = false,
                    onToggleOrientation = { toggleOrientation() },
                    onBack = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                // Controls, Error Banner, Quality, Proxy, and Details
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // If channel has YouTube ID, show 1-click YouTube App button
                    if (channel.youtubeId != null) {
                        Button(
                            onClick = { launchYouTubeApp() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("پخش مستقیم در برنامه یوتیوب (YouTube App)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // If channel has BOTH HLS and YouTube, provide stream source switcher
                    if (channel.hasStreams && channel.youtubeId != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !showYoutubeMode,
                                onClick = {
                                    showYoutubeMode = false
                                    startPlayback()
                                },
                                label = { Text("پخش HLS (پلیر داخلی)") },
                                leadingIcon = if (!showYoutubeMode) {
                                    { Icon(Icons.Filled.LiveTv, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = showYoutubeMode,
                                onClick = {
                                    showYoutubeMode = true
                                    PlayerHolder.pause()
                                },
                                label = { Text("پخش یوتیوب (YouTube)") },
                                leadingIcon = if (showYoutubeMode) {
                                    { Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Error Alert Banner (if stream failed)
                    lastError?.let { err ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "خطای پخش استریم",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = err,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (channel.youtubeId != null) {
                                        Button(
                                            onClick = { launchYouTubeApp() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("باز کردن در یوتیوب", fontSize = 11.sp)
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { showProxyDialog = true },
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("تنظیم پروکسی", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (showYoutubeMode) {
                                                Toast.makeText(context, "بارگذاری مجدد...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                startPlayback()
                                            }
                                        },
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("تلاش مجدد", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Quality Selector (Data Saver) — only applicable to native HLS streams
                    if (!showYoutubeMode && channel.hasStreams) {
                        Text(
                            text = "Quality / Data Saver (کاهش مصرف اینترنت)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StreamQuality.entries.forEach { q ->
                                FilterChip(
                                    selected = selectedQuality == q,
                                    onClick = {
                                        selectedQuality = q
                                        PlayerHolder.setQuality(q)
                                        startPlayback()
                                        Toast.makeText(context, "Quality: ${q.description}", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text(q.label) },
                                    leadingIcon = if (selectedQuality == q) {
                                        { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(14.dp))

                        // Proxy Toggle Card (Bypass Restrictions)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProxyDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = if (isProxyActive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "پروکسی ضد تحریم",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.Settings,
                                            contentDescription = "Config",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isProxyActive) {
                                            "روشن (${ProxyConfig.proxyMode.label})"
                                        } else {
                                            "خاموش (اتصال مستقیم — برای تنظیم لمس کنید)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isProxyActive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isProxyActive,
                                    onCheckedChange = { active ->
                                        isProxyActive = active
                                        ProxyConfig.isProxyEnabled = active
                                        ProxyConfig.save(context)
                                        // If YouTube mode is active, also update WebView proxy routing
                                        if (showYoutubeMode) {
                                            WebViewProxyManager.applyFromProxyConfig()
                                        }
                                        startPlayback()
                                        val msg = if (active) "پروکسی فعال شد" else "اتصال مستقیم فعال شد"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00E676),
                                        checkedTrackColor = Color(0xFF004D40)
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(14.dp))
                    }

                    // Country & Restriction Info
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

                    // Action Buttons: Reconnect / External Player
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (showYoutubeMode) {
                                    Toast.makeText(context, "بارگذاری مجدد...", Toast.LENGTH_SHORT).show()
                                } else {
                                    startPlayback()
                                    Toast.makeText(context, "اتصال مجدد...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reconnect")
                        }

                        OutlinedButton(
                            onClick = {
                                if (showYoutubeMode || channel.isYoutubeOnly) {
                                    launchYouTubeApp()
                                } else {
                                    val url = channel.primaryUrl ?: return@OutlinedButton
                                    val effective = ProxyConfig.getEffectiveUrl(url)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(effective), "video/*")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Play with (VLC/MX Player)"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No external player found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (showYoutubeMode || channel.isYoutubeOnly) "YouTube App" else "VLC / External")
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
                        val effective = if (showYoutubeMode) streamUrl else ProxyConfig.getEffectiveUrl(streamUrl)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = effective,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Stream URL", effective))
                                    Toast.makeText(context, "Copied link", Toast.LENGTH_SHORT).show()
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

    if (showProxyDialog) {
        ProxySettingsDialog(
            onDismiss = { showProxyDialog = false },
            onSaved = {
                isProxyActive = ProxyConfig.isProxyEnabled
                // Also update WebView proxy for YouTube mode
                if (showYoutubeMode) {
                    WebViewProxyManager.applyFromProxyConfig()
                }
                startPlayback()
            }
        )
    }
}

@Composable
private fun VideoBox(
    channel: Channel,
    showYoutubeMode: Boolean,
    controller: MediaController?,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            showYoutubeMode && channel.youtubeId != null -> {
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
                                allowFileAccess = false
                                allowContentAccess = false
                                databaseEnabled = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val uri = request?.url ?: return false
                                    val uriStr = uri.toString()
                                    if (uriStr.contains("youtube.com/watch") || uriStr.contains("youtu.be/")) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            return true
                                        } catch (_: Exception) {}
                                    }
                                    return false
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        PlayerHolder.setCustomError("پلیر یوتیوب لود نشد (از دکمه قرمز پایین برای باز کردن در برنامه یوتیوب استفاده کنید)")
                                    }
                                }
                            }
                            webChromeClient = WebChromeClient()

                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <style>
                                        * { margin: 0; padding: 0; box-sizing: border-box; }
                                        html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                                        iframe { width: 100%; height: 100%; border: 0; }
                                    </style>
                                </head>
                                <body>
                                    <iframe 
                                        src="https://www.youtube-nocookie.com/embed/${channel.youtubeId}?autoplay=1&playsinline=1&enablejsapi=1&origin=https://famelack.com&widget_referrer=https://famelack.com&rel=0&modestbranding=1" 
                                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                        allowfullscreen>
                                    </iframe>
                                </body>
                                </html>
                            """.trimIndent()
                            loadDataWithBaseURL("https://famelack.com", html, "text/html", "UTF-8", null)
                        }
                    }
                )
            }

            channel.hasStreams -> {
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

        // Floating Fullscreen / Rotate Screen Toggle Button (Bottom-Right)
        IconButton(
            onClick = onToggleOrientation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(38.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
        ) {
            Icon(
                imageVector = if (isLandscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isLandscape) "خروج از حالت تمام صفحه" else "چرخش و تمام صفحه",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Floating Header Overlay in Landscape Mode (Top-Left Back & Title)
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Portrait Mode",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.weight(1f))

                // Quick Rotate Toggle in Landscape Top-Right
                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ScreenRotation,
                        contentDescription = "بازگشت به عمودی",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
