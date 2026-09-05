package com.famelack.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Loads bundled Famelack data once into memory with full error resilience.
 */
class FamelackRepository(private val context: Context) {

    private var root: JSONObject? = null

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (root != null) return@withContext
        val start = System.currentTimeMillis()
        var stream: InputStream? = null
        var isGzip = true

        try {
            val assetList = context.assets.list("") ?: emptyArray()
            Log.d(TAG, "Assets in root: ${assetList.joinToString()}")

            if (assetList.contains("famelack_data.bin")) {
                stream = context.assets.open("famelack_data.bin")
                isGzip = true
            } else if (assetList.contains("famelack_data.json.gz")) {
                stream = context.assets.open("famelack_data.json.gz")
                isGzip = true
            } else if (assetList.contains("famelack_data.json")) {
                stream = context.assets.open("famelack_data.json")
                isGzip = false
            } else {
                // Fallback direct attempts
                stream = try {
                    isGzip = true
                    context.assets.open("famelack_data.bin")
                } catch (_: Exception) {
                    try {
                        isGzip = false
                        context.assets.open("famelack_data.json")
                    } catch (_: Exception) {
                        isGzip = true
                        context.assets.open("famelack_data.json.gz")
                    }
                }
            }

            val finalStream = if (isGzip) GZIPInputStream(stream) else stream
            BufferedReader(InputStreamReader(finalStream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder(2048)
                val buf = CharArray(32 * 1024)
                var n: Int
                while (reader.read(buf).also { n = it } > 0) {
                    sb.append(buf, 0, n)
                }
                root = JSONObject(sb.toString())
            }
            Log.i(TAG, "Famelack data loaded in ${System.currentTimeMillis() - start} ms")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed loading assets", e)
            root = JSONObject()
        }
    }

    fun countriesFor(kind: MediaKind): List<CountryInfo> {
        val r = root ?: return emptyList()
        val meta = r.optJSONObject(kind.slug)?.optJSONObject("meta") ?: return emptyList()
        val list = mutableListOf<CountryInfo>()
        val keys = meta.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val o = meta.optJSONObject(code) ?: continue
            list.add(CountryInfo.fromJson(code, o))
        }
        return list.sortedWith(
            compareByDescending<CountryInfo> { it.hasChannels }
                .thenByDescending { it.channelCount }
                .thenBy { it.name }
        )
    }

    fun channelsByCountry(kind: MediaKind, code: String): List<Channel> {
        val r = root ?: return emptyList()
        val arr = r.optJSONObject(kind.slug)
            ?.optJSONObject("by_country")
            ?.optJSONArray(code) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optJSONObject(idx)?.let { Channel.fromJson(it) }
        }
    }

    fun channelsByCategory(kind: MediaKind, category: String): List<Channel> {
        val r = root ?: return emptyList()
        val arr = r.optJSONObject(kind.slug)
            ?.optJSONObject("by_category")
            ?.optJSONArray(category) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optJSONObject(idx)?.let { Channel.fromJson(it) }
        }
    }

    fun categoriesFor(kind: MediaKind): List<String> {
        val r = root ?: return emptyList()
        val cats = r.optJSONObject(kind.slug)?.optJSONObject("by_category") ?: return emptyList()
        return cats.keys().asSequence().toList().sorted()
    }

    fun countryByCode(kind: MediaKind, code: String): CountryInfo? {
        val r = root ?: return null
        val meta = r.optJSONObject(kind.slug)?.optJSONObject("meta") ?: return null
        val o = meta.optJSONObject(code) ?: return null
        return CountryInfo.fromJson(code, o)
    }

    fun randomChannel(kind: MediaKind, countryCode: String? = null): Channel? {
        return if (countryCode != null) {
            channelsByCountry(kind, countryCode).randomOrNull()
        } else {
            val countries = countriesFor(kind).filter { it.hasChannels }
            if (countries.isEmpty()) return null
            val pick = countries.random()
            channelsByCountry(kind, pick.code).randomOrNull()
        }
    }

    fun search(kind: MediaKind, query: String, limit: Int = 200): List<Channel> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Channel>(64)
        val byCountry = root?.optJSONObject(kind.slug)?.optJSONObject("by_country") ?: return emptyList()
        val keys = byCountry.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val arr = byCountry.optJSONArray(code) ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val c = Channel.fromJson(obj)
                if (c.name.lowercase().contains(q)) {
                    out.add(c)
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }

    fun counts(kind: MediaKind): Int = root
        ?.optJSONObject(kind.slug)
        ?.optJSONObject("by_category")
        ?.optJSONArray("all")
        ?.length() ?: 0

    companion object { private const val TAG = "FamelackRepo" }
}
