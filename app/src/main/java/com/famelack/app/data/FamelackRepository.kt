package com.famelack.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Loads the bundled famelack_data.json.gz (≈5 MB) once and keeps it in memory.
 * Provides country lists, channels and category queries.
 *
 * Data structure:
 *   {
 *     "tv":      { "by_country": {"ir":[...57 chans...]}, "by_category": {...}, "meta": {...} },
 *     "radio":   {...},
 *     "webcams": {...}
 *   }
 */
class FamelackRepository(private val context: Context) {

    private var root: JSONObject? = null

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (root != null) return@withContext
        val start = System.currentTimeMillis()
        context.assets.open("famelack_data.json.gz").use { fis ->
            GZIPInputStream(fis).use { gz ->
                BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(16 * 1024)
                    var n: Int
                    while (reader.read(buf).also { n = it } > 0) {
                        sb.append(buf, 0, n)
                    }
                    root = JSONObject(sb.toString())
                }
            }
        }
        Log.i(TAG, "Famelack data loaded in ${System.currentTimeMillis() - start} ms")
    }

    fun countriesFor(kind: MediaKind): List<CountryInfo> {
        val r = root ?: return emptyList()
        val meta = r.getJSONObject(kind.slug).getJSONObject("meta")
        val list = mutableListOf<CountryInfo>()
        val keys = meta.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val o = meta.getJSONObject(code)
            list.add(CountryInfo.fromJson(code, o))
        }
        // Sort: countries with channels first, then by channel count desc, then by name
        return list.sortedWith(
            compareByDescending<CountryInfo> { it.hasChannels }
                .thenByDescending { it.channelCount }
                .thenBy { it.name }
        )
    }

    fun channelsByCountry(kind: MediaKind, code: String): List<Channel> {
        val r = root ?: return emptyList()
        val arr = r.getJSONObject(kind.slug)
            .getJSONObject("by_country")
            .optJSONArray(code) ?: return emptyList()
        return (0 until arr.length()).map { Channel.fromJson(arr.getJSONObject(it)) }
    }

    fun channelsByCategory(kind: MediaKind, category: String): List<Channel> {
        val r = root ?: return emptyList()
        val arr = r.getJSONObject(kind.slug)
            .getJSONObject("by_category")
            .optJSONArray(category) ?: return emptyList()
        return (0 until arr.length()).map { Channel.fromJson(arr.getJSONObject(it)) }
    }

    fun categoriesFor(kind: MediaKind): List<String> {
        val r = root ?: return emptyList()
        val cats = r.getJSONObject(kind.slug).getJSONObject("by_category")
        return cats.keys().asSequence().toList().sorted()
    }

    fun countryByCode(kind: MediaKind, code: String): CountryInfo? {
        val r = root ?: return null
        val meta = r.getJSONObject(kind.slug).getJSONObject("meta")
        val o = meta.optJSONObject(code) ?: return null
        return CountryInfo.fromJson(code, o)
    }

    /** Pick a random channel from a kind (used by the "Random" button). */
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

    /** Search across all channels in a kind by name. */
    fun search(kind: MediaKind, query: String, limit: Int = 200): List<Channel> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<Channel>(64)
        val byCountry = root?.getJSONObject(kind.slug)?.getJSONObject("by_country") ?: return emptyList()
        val keys = byCountry.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val arr = byCountry.getJSONArray(code)
            for (i in 0 until arr.length()) {
                val c = Channel.fromJson(arr.getJSONObject(i))
                if (c.name.lowercase().contains(q)) {
                    out.add(c)
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }

    /** Total counts. */
    fun counts(kind: MediaKind): Int = root
        ?.getJSONObject(kind.slug)
        ?.getJSONObject("by_category")
        ?.getJSONArray("all")
        ?.length() ?: 0

    companion object { private const val TAG = "FamelackRepo" }
}
