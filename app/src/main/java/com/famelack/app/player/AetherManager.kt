package com.famelack.app.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
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

    fun isPortOpen(port: Int = DEFAULT_PORT): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureStarted(context: Context, port: Int = DEFAULT_PORT) = withContext(Dispatchers.IO) {
        if (isPortOpen(port)) {
            Log.d(TAG, "Aether SOCKS5 already running on port $port")
            return@withContext
        }

        val binary = findAetherBinary(context)
        if (binary == null || !binary.exists()) {
            Log.w(TAG, "Aether binary (libaether.so) not found in native library directory")
            return@withContext
        }

        try {
            binary.setExecutable(true, false)
            val cmd = arrayOf(
                binary.absolutePath,
                "--masque",
                "-4",
                "--scan", "balanced",
                "--noize", "balanced",
                "--bind", "127.0.0.1:$port"
            )
            Log.i(TAG, "Starting Aether MASQUE process: ${cmd.joinToString(" ")}")
            process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()

            // Drain output in background to keep process pipe clear
            Thread {
                try {
                    process?.inputStream?.bufferedReader()?.forEachLine { line ->
                        Log.d("AetherOutput", line)
                    }
                } catch (_: Exception) {}
            }.start()

            // Poll port up to 4 seconds
            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                if (isPortOpen(port)) {
                    Log.i(TAG, "Aether MASQUE successfully listening on 127.0.0.1:$port")
                    break
                }
                Thread.sleep(250)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Aether MASQUE process", e)
        }
    }

    fun stop() {
        try {
            process?.destroy()
            process = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Aether", e)
        }
    }

    private fun findAetherBinary(context: Context): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val file = File(nativeDir, "libaether.so")
        return if (file.exists()) file else null
    }
}
