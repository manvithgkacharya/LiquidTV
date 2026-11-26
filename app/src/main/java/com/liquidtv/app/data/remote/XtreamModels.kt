package com.liquidtv.app.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Xtream Codes server & user info returned by `player_api.php`.
 * Uses defensive JSON parsing because real-world IPTV panels vary wildly
 * in types (e.g., `auth` as 1 vs "1", `stream_id` as Int vs String, `exp_date` as null/"null"/epoch).
 */
data class XtreamAuthResponse(
    val userInfo: XtreamUserInfo?,
    val serverInfo: XtreamServerInfo?,
)

data class XtreamUserInfo(
    val username: String?,
    val status: String?,
    val auth: Int,
    val expDateEpochSec: Long?,
    val isTrial: Boolean?,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val allowedOutputFormats: List<String>,
)

data class XtreamServerInfo(
    val url: String?,
    val port: String?,
    val httpsPort: String?,
    val serverProtocol: String?,
    val timezone: String?,
    val timeNow: String?,
)

data class XtreamCategoryDto(
    val categoryId: String,
    val categoryName: String,
)

data class XtreamLiveStreamDto(
    val streamId: String,
    val name: String,
    val streamIcon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val num: Int?,
    val directSource: String?,
)

data class XtreamVodStreamDto(
    val streamId: String,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val containerExtension: String?,
    val rating: String?,
    val directSource: String?,
)

data class XtreamSeriesDto(
    val seriesId: String,
    val name: String,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val lastModified: Long?,
    val categoryId: String?,
)

data class XtreamSeriesInfoDto(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val cover: String?,
    val episodes: List<XtreamEpisodeDto>,
)

data class XtreamEpisodeDto(
    val id: String,
    val episodeNum: Int,
    val seasonNumber: Int,
    val title: String,
    val containerExtension: String?,
    val plot: String?,
    val durationSec: Int?,
    val coverUrl: String?,
    val directSource: String?,
)

data class XtreamShortEpgItemDto(
    val title: String,
    val description: String?,
    val startTimestampSec: Long?,
    val stopTimestampSec: Long?,
    val startText: String?,
    val endText: String?,
)

object XtreamJsonParser {

    fun parseAuth(rawJson: String): XtreamAuthResponse {
        val root = JSONObject(rawJson)
        val userObj = root.optJSONObject("user_info")
        val serverObj = root.optJSONObject("server_info")

        val userInfo = userObj?.let { u ->
            val authInt = u.optFlexibleInt("auth") ?: 0
            val expSec = u.optFlexibleLong("exp_date")?.takeIf { it > 0 }
            val trial = u.optFlexibleInt("is_trial")?.let { it == 1 }
            val activeCons = u.optFlexibleInt("active_cons")
            val maxCons = u.optFlexibleInt("max_connections")
            val formats = mutableListOf<String>()
            val arr = u.optJSONArray("allowed_output_formats")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val f = arr.optString(i).trim()
                    if (f.isNotEmpty() && f != "null") formats.add(f)
                }
            }
            XtreamUserInfo(
                username = u.optCleanString("username"),
                status = u.optCleanString("status"),
                auth = authInt,
                expDateEpochSec = expSec,
                isTrial = trial,
                activeConnections = activeCons,
                maxConnections = maxCons,
                allowedOutputFormats = formats,
            )
        }

        val serverInfo = serverObj?.let { s ->
            XtreamServerInfo(
                url = s.optCleanString("url"),
                port = s.optCleanString("port"),
                httpsPort = s.optCleanString("https_port"),
                serverProtocol = s.optCleanString("server_protocol"),
                timezone = s.optCleanString("timezone"),
                timeNow = s.optCleanString("time_now"),
            )
        }

        return XtreamAuthResponse(userInfo = userInfo, serverInfo = serverInfo)
    }

    fun parseCategories(rawJson: String): List<XtreamCategoryDto> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val arr = JSONArray(trimmed)
        val result = ArrayList<XtreamCategoryDto>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optCleanString("category_id") ?: continue
            val name = obj.optCleanString("category_name") ?: continue
            result.add(XtreamCategoryDto(categoryId = id, categoryName = name))
        }
        return result
    }

    fun parseLiveStreams(rawJson: String): List<XtreamLiveStreamDto> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val arr = JSONArray(trimmed)
        val result = ArrayList<XtreamLiveStreamDto>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optCleanString("stream_id") ?: continue
            val name = obj.optCleanString("name") ?: "Channel $id"
            result.add(
                XtreamLiveStreamDto(
                    streamId = id,
                    name = name,
                    streamIcon = obj.optCleanString("stream_icon"),
                    epgChannelId = obj.optCleanString("epg_channel_id"),
                    categoryId = obj.optCleanString("category_id"),
                    num = obj.optFlexibleInt("num"),
                    directSource = obj.optCleanString("direct_source"),
                ),
            )
        }
        return result
    }

    fun parseVodStreams(rawJson: String): List<XtreamVodStreamDto> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val arr = JSONArray(trimmed)
        val result = ArrayList<XtreamVodStreamDto>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optCleanString("stream_id") ?: continue
            val name = obj.optCleanString("name") ?: "Movie $id"
            result.add(
                XtreamVodStreamDto(
                    streamId = id,
                    name = name,
                    streamIcon = obj.optCleanString("stream_icon"),
                    categoryId = obj.optCleanString("category_id"),
                    containerExtension = obj.optCleanString("container_extension"),
                    rating = obj.optCleanString("rating"),
                    directSource = obj.optCleanString("direct_source"),
                ),
            )
        }
        return result
    }

    fun parseSeriesList(rawJson: String): List<XtreamSeriesDto> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val arr = JSONArray(trimmed)
        val result = ArrayList<XtreamSeriesDto>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optCleanString("series_id") ?: continue
            val name = obj.optCleanString("name") ?: "Series $id"
            result.add(
                XtreamSeriesDto(
                    seriesId = id,
                    name = name,
                    cover = obj.optCleanString("cover"),
                    plot = obj.optCleanString("plot"),
                    cast = obj.optCleanString("cast"),
                    director = obj.optCleanString("director"),
                    genre = obj.optCleanString("genre"),
                    releaseDate = obj.optCleanString("releaseDate") ?: obj.optCleanString("release_date"),
                    rating = obj.optCleanString("rating"),
                    lastModified = obj.optFlexibleLong("last_modified"),
                    categoryId = obj.optCleanString("category_id"),
                ),
            )
        }
        return result
    }

    fun parseSeriesInfo(rawJson: String): XtreamSeriesInfoDto {
        val root = JSONObject(rawJson)
        val info = root.optJSONObject("info")
        val episodesList = mutableListOf<XtreamEpisodeDto>()
        val episodesAny = root.opt("episodes")

        when (episodesAny) {
            is JSONObject -> {
                val keys = episodesAny.keys()
                while (keys.hasNext()) {
                    val seasonKey = keys.next()
                    val arr = episodesAny.optJSONArray(seasonKey) ?: continue
                    val fallbackSeason = seasonKey.toIntOrNull() ?: 1
                    parseEpisodeArray(arr, fallbackSeason, episodesList)
                }
            }
            is JSONArray -> {
                for (i in 0 until episodesAny.length()) {
                    val item = episodesAny.opt(i)
                    if (item is JSONArray) {
                        parseEpisodeArray(item, i + 1, episodesList)
                    } else if (item is JSONObject) {
                        parseEpisodeObject(item, 1)?.let { episodesList.add(it) }
                    }
                }
            }
        }

        return XtreamSeriesInfoDto(
            plot = info?.optCleanString("plot"),
            cast = info?.optCleanString("cast"),
            director = info?.optCleanString("director"),
            genre = info?.optCleanString("genre"),
            releaseDate = info?.optCleanString("releaseDate") ?: info?.optCleanString("release_date"),
            rating = info?.optCleanString("rating"),
            cover = info?.optCleanString("cover"),
            episodes = episodesList,
        )
    }

    fun parseShortEpg(rawJson: String): List<XtreamShortEpgItemDto> {
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val result = mutableListOf<XtreamShortEpgItemDto>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawTitle = obj.optCleanString("title") ?: continue
            val title = decodeBase64IfEncoded(rawTitle)
            val rawDesc = obj.optCleanString("description")
            val desc = rawDesc?.let { decodeBase64IfEncoded(it) }
            result.add(
                XtreamShortEpgItemDto(
                    title = title,
                    description = desc,
                    startTimestampSec = obj.optFlexibleLong("start_timestamp"),
                    stopTimestampSec = obj.optFlexibleLong("stop_timestamp"),
                    startText = obj.optCleanString("start"),
                    endText = obj.optCleanString("end"),
                ),
            )
        }
        return result
    }

    private fun parseEpisodeArray(
        arr: JSONArray,
        fallbackSeason: Int,
        out: MutableList<XtreamEpisodeDto>,
    ) {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseEpisodeObject(obj, fallbackSeason)?.let { out.add(it) }
        }
    }

    private fun parseEpisodeObject(obj: JSONObject, fallbackSeason: Int): XtreamEpisodeDto? {
        val id = obj.optCleanString("id") ?: return null
        val epNum = obj.optFlexibleInt("episode_num") ?: 1
        val season = obj.optFlexibleInt("season") ?: fallbackSeason
        val title = obj.optCleanString("title") ?: "Episode $epNum"
        val infoObj = obj.optJSONObject("info")
        return XtreamEpisodeDto(
            id = id,
            episodeNum = epNum,
            seasonNumber = season,
            title = title,
            containerExtension = obj.optCleanString("container_extension"),
            plot = infoObj?.optCleanString("plot"),
            durationSec = infoObj?.optFlexibleInt("duration_secs"),
            coverUrl = infoObj?.optCleanString("movie_image"),
            directSource = obj.optCleanString("direct_source"),
        )
    }

    private fun decodeBase64IfEncoded(text: String): String {
        if (text.isBlank()) return text
        // Xtream panels commonly base64-encode EPG titles & descriptions
        if (text.length % 4 == 0 && text.matches(Regex("^[A-Za-z0-9+/]+={0,2}$"))) {
            return try {
                val decoded = String(android.util.Base64.decode(text, android.util.Base64.DEFAULT), Charsets.UTF_8)
                if (decoded.isNotBlank() && decoded.none { it.code < 9 }) decoded else text
            } catch (_: Exception) {
                text
            }
        }
        return text
    }

    private fun JSONObject.optCleanString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key)?.toString()?.trim() ?: return null
        return raw.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.optFlexibleInt(key: String): Int? {
        val s = optCleanString(key) ?: return null
        return s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt()
    }

    private fun JSONObject.optFlexibleLong(key: String): Long? {
        val s = optCleanString(key) ?: return null
        return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong()
    }
}
