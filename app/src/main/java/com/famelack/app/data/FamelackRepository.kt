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
 * Loads bundled Famelack data once into memory with full error resilience and case-insensitivity.
 */
class FamelackRepository(private val context: Context) {

    private var root: JSONObject? = null

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (root != null) return@withContext
        val start = System.currentTimeMillis()
        var stream: InputStream? = null
        var isGzip = true

        try {
            // Prioritize loading famelack_data.bin (guaranteed compressed and untouched by AAPT)
            stream = try {
                isGzip = true
                context.assets.open("famelack_data.bin")
            } catch (_: Exception) {
                try {
                    isGzip = true
                    context.assets.open("famelack_data.json.gz")
                } catch (_: Exception) {
                    isGzip = false
                    context.assets.open("famelack_data.json")
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

    /**
     * Channels for a given country code. Handles case-insensitivity:
     * meta keys are uppercase (e.g. "IR"), but by_country keys are lowercase (e.g. "ir").
     */
    fun channelsByCountry(kind: MediaKind, code: String): List<Channel> {
        val r = root ?: return emptyList()
        val byCountry = r.optJSONObject(kind.slug)?.optJSONObject("by_country") ?: return emptyList()
        val lowerCode = code.lowercase()
        val upperCode = code.uppercase()

        val arr = byCountry.optJSONArray(lowerCode)
            ?: byCountry.optJSONArray(code)
            ?: byCountry.optJSONArray(upperCode)
            ?: return emptyList()

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
        val o = meta.optJSONObject(code.uppercase())
            ?: meta.optJSONObject(code)
            ?: meta.optJSONObject(code.lowercase())
            ?: return null
        return CountryInfo.fromJson(code, o)
    }

    /**
     * Pick a random channel from this media kind.
     * Uses the global "all" category array for high-speed uniform selection across all channels.
     */
    fun randomChannel(kind: MediaKind, countryCode: String? = null): Channel? {
        if (countryCode != null) {
            return channelsByCountry(kind, countryCode).randomOrNull()
        }

        // Direct selection from the "all" category array (e.g. 6612 channels for TV)
        val allArr = root?.optJSONObject(kind.slug)
            ?.optJSONObject("by_category")
            ?.optJSONArray("all")

        if (allArr != null && allArr.length() > 0) {
            val randomIndex = (0 until allArr.length()).random()
            val obj = allArr.optJSONObject(randomIndex)
            if (obj != null) {
                return Channel.fromJson(obj)
            }
        }

        // Fallback: pick a country with channels, then pick a channel from it
        val countries = countriesFor(kind).filter { it.hasChannels }
        if (countries.isEmpty()) return null
        val pick = countries.random()
        return channelsByCountry(kind, pick.code).randomOrNull()
    }

    fun search(kind: MediaKind, query: String, limit: Int = 200): List<Channel> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Channel>(64)

        // Try searching the "all" category array directly if available
        val allArr = root?.optJSONObject(kind.slug)
            ?.optJSONObject("by_category")
            ?.optJSONArray("all")

        if (allArr != null && allArr.length() > 0) {
            for (i in 0 until allArr.length()) {
                val obj = allArr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").lowercase()
                if (name.contains(q)) {
                    out.add(Channel.fromJson(obj))
                    if (out.size >= limit) return out
                }
            }
            return out
        }

        // Fallback: scan by_country
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
