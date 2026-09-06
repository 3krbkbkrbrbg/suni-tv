package com.famelack.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famelack.app.player.FcaeVpnManager
import com.famelack.app.player.ProxyConfig
import com.famelack.app.player.ProxyMode
import kotlinx.coroutines.launch

@Composable
fun ProxySettingsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(ProxyConfig.isProxyEnabled) }
    var selectedMode by remember { mutableStateOf(ProxyConfig.proxyMode) }
    var fcaePort by remember { mutableStateOf(ProxyConfig.fcaePort.toString()) }
    var relayUrl by remember { mutableStateOf(ProxyConfig.relayUrl) }
    var socksPort by remember { mutableStateOf(ProxyConfig.localSocksPort.toString()) }
    var httpPort by remember { mutableStateOf(ProxyConfig.localHttpPort.toString()) }
    var autoFallback by remember { mutableStateOf(ProxyConfig.autoFallbackToDirect) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isFcaeRunning by remember { mutableStateOf(false) }

    val fcaeStatus by FcaeVpnManager.statusFlow.collectAsState()

    LaunchedEffect(Unit) {
        isFcaeRunning = FcaeVpnManager.isPortOpen(fcaePort.toIntOrNull() ?: 1819)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = if (isEnabled) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("تنظیمات پروکسی ضد تحریم", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Enable switch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "فعال‌سازی پروکسی",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (isEnabled) "روشن (عبور از فیلترینگ)" else "خاموش (اتصال مستقیم)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isEnabled) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E676),
                                checkedTrackColor = Color(0xFF004D40)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Mode Selector Chips
                Text("نوع پروکسی:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProxyMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(mode.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(mode.desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Input fields per mode
                when (selectedMode) {
                    ProxyMode.FCAE_VPN -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isFcaeRunning) Color(0xFF00E676) else Color(0xFFFFB74D))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = fcaeStatus,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isFcaeRunning) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "موتور قدرتمند FCAE VPN v1.2.9 متصل به شبکه کلودفلر و MASQUE؛ بدون نیاز به ابزار خارجی ترافیک استریم‌ها را از تحریم و فیلتر عبور می‌دهد.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = fcaePort,
                            onValueChange = {
                                fcaePort = it
                                isFcaeRunning = FcaeVpnManager.isPortOpen(it.toIntOrNull() ?: 1819)
                            },
                            label = { Text("پورت SOCKS5") },
                            placeholder = { Text("1819 (پیش‌فرض FCAE)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val port = fcaePort.toIntOrNull() ?: 1819
                                    FcaeVpnManager.start(context, port)
                                    isFcaeRunning = FcaeVpnManager.isPortOpen(port)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("راه‌اندازی فوری پروکسی FCAE VPN", fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(6.dp))

                        val fcaeAppIntent = remember { context.packageManager.getLaunchIntentForPackage("com.fc.fcaevpn") }
                        if (fcaeAppIntent != null) {
                            OutlinedButton(
                                onClick = { context.startActivity(fcaeAppIntent) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("باز کردن برنامه FCAE VPN", fontSize = 11.sp)
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/FCFlenkchy/FCAE_VPN/releases/tag/v1.2.9")
                                        )
                                        context.startActivity(intent)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("دانلود برنامه FCAE VPN v1.2.9 از گیت‌هاب", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                        }
                    }
                    ProxyMode.URL_RELAY -> {
                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("آدرس رله ورکر یا ورسل") },
                            placeholder = { Text("https://my-proxy.vercel.app/?url=") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/3krbkbkrbrbg/famelack-stream-proxy")
                                    )
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("ساخت رایگان با یک کلیک در گیت‌هاب (Vercel/Worker)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                    }
                    ProxyMode.LOCAL_SOCKS5 -> {
                        OutlinedTextField(
                            value = socksPort,
                            onValueChange = { socksPort = it },
                            label = { Text("پورت SOCKS5 محلی") },
                            placeholder = { Text("10808 (پیش‌فرض V2RayNG)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "آدرس 127.0.0.1 با پورت بالا برای اتصال از طریق V2RayNG یا فیلترشکن گوشی",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    ProxyMode.LOCAL_HTTP -> {
                        OutlinedTextField(
                            value = httpPort,
                            onValueChange = { httpPort = it },
                            label = { Text("پورت HTTP محلی") },
                            placeholder = { Text("10809 (پیش‌فرض V2RayNG)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Auto fallback switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("بازگشت خودکار به مستقیم", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("در صورت قطعی پروکسی، استریم متوقف نمی‌شود", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoFallback,
                        onCheckedChange = { autoFallback = it }
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                // Test Connection Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val prevEnabled = ProxyConfig.isProxyEnabled
                                val prevMode = ProxyConfig.proxyMode
                                val prevFcae = ProxyConfig.fcaePort
                                val prevRelay = ProxyConfig.relayUrl
                                val prevSocks = ProxyConfig.localSocksPort
                                val prevHttp = ProxyConfig.localHttpPort

                                ProxyConfig.isProxyEnabled = true
                                ProxyConfig.proxyMode = selectedMode
                                ProxyConfig.fcaePort = fcaePort.toIntOrNull() ?: 1819
                                ProxyConfig.relayUrl = relayUrl
                                ProxyConfig.localSocksPort = socksPort.toIntOrNull() ?: 10808
                                ProxyConfig.localHttpPort = httpPort.toIntOrNull() ?: 10809

                                val res = ProxyConfig.testConnection(context)
                                testResult = res
                                isFcaeRunning = FcaeVpnManager.isPortOpen(ProxyConfig.fcaePort)
                                isTesting = false

                                ProxyConfig.isProxyEnabled = prevEnabled
                                ProxyConfig.proxyMode = prevMode
                                ProxyConfig.fcaePort = prevFcae
                                ProxyConfig.relayUrl = prevRelay
                                ProxyConfig.localSocksPort = prevSocks
                                ProxyConfig.localHttpPort = prevHttp
                            }
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("در حال تست...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تست اتصال", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    testResult?.let { (success, msg) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (success) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                color = if (success) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ProxyConfig.isProxyEnabled = isEnabled
                    ProxyConfig.proxyMode = selectedMode
                    ProxyConfig.fcaePort = fcaePort.toIntOrNull() ?: 1819
                    ProxyConfig.relayUrl = relayUrl.trim()
                    ProxyConfig.localSocksPort = socksPort.toIntOrNull() ?: 10808
                    ProxyConfig.localHttpPort = httpPort.toIntOrNull() ?: 10809
                    ProxyConfig.autoFallbackToDirect = autoFallback
                    ProxyConfig.save(context)
                    if (isEnabled && selectedMode == ProxyMode.FCAE_VPN) {
                        scope.launch {
                            FcaeVpnManager.start(context, ProxyConfig.fcaePort)
                        }
                    }
                    onSaved()
                    onDismiss()
                }
            ) {
                Text("ذخیره و اعمال")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}
