package com.famelack.app.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Manages the embedded Aether MASQUE censorship-circumvention client.
 * Upstream project: https://github.com/3krbkbkrbrbg/aether-android / https://github.com/CluvexStudio/Aether
 * Encapsulates traffic over HTTP/3 (QUIC) and HTTP/2 with traffic obfuscation.
 * Exposes local SOCKS5 proxy on 127.0.0.1:1819.
 */
object AetherManager {
    private const val TAG = "AetherManager"
    const val DEFAULT_PORT = 1819

    private var process: Process? = null

    private val _statusFlow = MutableStateFlow("غیرفعال")
    val statusFlow: StateFlow<String> = _statusFlow.asStateFlow()

    private val _lastLogFlow = MutableStateFlow("")
    val lastLogFlow: StateFlow<String> = _lastLogFlow.asStateFlow()

    fun isPortOpen(port: Int = DEFAULT_PORT): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 350)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureStarted(context: Context, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        if (isPortOpen(port)) {
            Log.d(TAG, "Aether SOCKS5 already running on port $port")
            _statusFlow.value = "فعال (روی پورت $port)"
            return@withContext true
        }

        val binary = prepareNative(context, "libaether.so")
        if (binary == null || !binary.exists()) {
            val err = "فایل باینری libaether.so در دستگاه یافت نشد"
            Log.w(TAG, err)
            _statusFlow.value = err
            _lastLogFlow.value = err
            return@withContext false
        }

        try {
            val workDir = File(context.filesDir, "aether_data").apply { mkdirs() }
            val configFile = File(workDir, "aether.toml")

            val cmd = arrayOf(
                binary.absolutePath,
                "--masque",
                "-4",
                "--turbo",
                "--noize", "balanced",
                "--config", configFile.absolutePath,
                "--bind", "127.0.0.1:$port"
            )

            Log.i(TAG, "Starting Aether MASQUE: ${cmd.joinToString(" ")}")
            _statusFlow.value = "در حال راه‌اندازی و اسکن سرورها..."

            val builder = ProcessBuilder(*cmd)
                .directory(workDir)
                .redirectErrorStream(true)

            builder.environment().apply {
                put("HOME", workDir.absolutePath)
                put("TMPDIR", context.cacheDir.absolutePath)
                put("AETHER_SOCKS", "127.0.0.1:$port")
            }

            val proc = builder.start()
            process = proc

            // Read output logs in background
            Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        Log.d("AetherLog", line)
                        _lastLogFlow.value = line
                        if (line.contains("listening") || line.contains("1819") || line.contains("connected") || line.contains("ready")) {
                            _statusFlow.value = "متصل شد"
                        }
                    }
                } catch (_: Exception) {}
            }.start()

            // Poll port up to 12 seconds
            val deadline = System.currentTimeMillis() + 12000
            while (System.currentTimeMillis() < deadline) {
                if (!proc.isAlive) {
                    val exit = try { proc.exitValue() } catch (_: Exception) { -1 }
                    val msg = "Aether متوقف شد (کد خروج $exit): ${_lastLogFlow.value}"
                    Log.e(TAG, msg)
                    _statusFlow.value = msg
                    return@withContext false
                }

                if (isPortOpen(port)) {
                    Log.i(TAG, "Aether MASQUE successfully open on 127.0.0.1:$port")
                    _statusFlow.value = "فعال و متصل (پورت $port)"
                    return@withContext true
                }
                Thread.sleep(300)
            }

            val timeoutMsg = "تایم‌اوت اتصال (پورت $port هنوز پاسخ نداد)"
            _statusFlow.value = timeoutMsg
            Log.w(TAG, timeoutMsg)
            return@withContext isPortOpen(port)
        } catch (e: Exception) {
            val err = "خطا در استارت Aether: ${e.message}"
            Log.e(TAG, err, e)
            _statusFlow.value = err
            _lastLogFlow.value = err
            return@withContext false
        }
    }

    fun stop() {
        try {
            process?.destroy()
            process = null
            _statusFlow.value = "متوقف شد"
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Aether", e)
        }
    }

    private fun prepareNative(context: Context, libName: String): File? {
        val dir = context.applicationInfo.nativeLibraryDir
        val lib = File(dir, libName)
        if (lib.exists() && lib.isFile && lib.length() > 0) {
            if (!lib.canExecute()) {
                try {
                    ProcessBuilder("chmod", "755", lib.absolutePath).start().waitFor()
                } catch (_: Exception) {}
                lib.setExecutable(true, false)
            }
            return lib
        }
        return null
    }
}
