package com.famelack.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Media kind. */
enum class MediaKind(val slug: String) {
    TV("tv"),
    RADIO("radio"),
    WEBCAM("webcams");

    companion object {
        fun fromSlug(slug: String): MediaKind =
            entries.firstOrNull { it.slug == slug } ?: TV
    }
}

/** A single channel (TV / radio / webcam). */
data class Channel(
    val id: String,
    val name: String,
    val country: String,
    val languages: List<String>,
    val isGeoBlocked: Boolean,
    val streamUrls: List<String>,
    val youtubeId: String?
) {
    val hasStreams: Boolean get() = streamUrls.isNotEmpty()
    val isYoutubeOnly: Boolean get() = streamUrls.isEmpty() && youtubeId != null
    val isYoutube: Boolean get() = youtubeId != null
    val primaryUrl: String?
        get() = when {
            streamUrls.isNotEmpty() -> streamUrls.first()
            youtubeId != null -> "https://www.youtube.com/watch?v=$youtubeId"
            else -> null
        }

    companion object {
        fun fromJson(o: JSONObject): Channel {
            val sources = o.optJSONObject("sources")
            val streamsJson = sources?.optJSONArray("streams") ?: JSONArray()
            val streams = (0 until streamsJson.length()).mapNotNull { i -> streamsJson.optString(i, null) }
            val ytJson = sources?.optJSONArray("youtube")
            var ytId: String? = null
            if (ytJson != null && ytJson.length() > 0) {
                val url = ytJson.optString(0)
                // youtube embed url -> extract id
                ytId = extractYoutubeId(url)
            }
            val langsJson = o.optJSONArray("languages") ?: JSONArray()
            val langs = (0 until langsJson.length()).mapNotNull { langsJson.optString(it, null) }
            return Channel(
                id = o.optString("nanoid"),
                name = o.optString("name"),
                country = o.optString("country"),
                languages = langs,
                isGeoBlocked = o.optBoolean("isGeoBlocked", false),
                streamUrls = streams,
                youtubeId = ytId
            )
        }

        private fun extractYoutubeId(url: String): String? {
            // Patterns: youtube.com/embed/ID, youtu.be/ID, youtube.com/watch?v=ID
            val patterns = listOf(
                Regex("""embed/([A-Za-z0-9_-]{6,})"""),
                Regex("""youtu\.be/([A-Za-z0-9_-]{6,})"""),
                Regex("""[?&]v=([A-Za-z0-9_-]{6,})""")
            )
            for (p in patterns) {
                val m = p.find(url) ?: continue
                return m.groupValues[1]
            }
            return null
        }
    }
}

/** Country metadata. */
data class CountryInfo(
    val code: String,
    val name: String,
    val capital: String?,
    val timeZone: String?,
    val hasChannels: Boolean,
    val channelCount: Int
) {
    val flagEmoji: String get() = codeToFlag(code)

    companion object {
        fun fromJson(code: String, o: JSONObject): CountryInfo = CountryInfo(
            code = code,
            name = o.optString("country"),
            capital = o.optString("capital", null),
            timeZone = o.optString("timeZone", null),
            hasChannels = o.optBoolean("hasChannels", false),
            channelCount = o.optInt("channelCount", 0)
        )
    }
}

/** Convert ISO-2 country code to emoji flag. */
fun codeToFlag(code: String): String {
    if (code.length != 2) return "🏳️"
    val c1 = code[0].code - 'A'.code + 0x1F1E6
    val c2 = code[1].code - 'A'.code + 0x1F1E6
    return String(Character.toChars(c1)) + String(Character.toChars(c2))
}
