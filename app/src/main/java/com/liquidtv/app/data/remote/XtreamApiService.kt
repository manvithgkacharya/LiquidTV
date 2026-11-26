package com.liquidtv.app.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Xtream Codes HTTP/HTTPS API client.
 * Supports arbitrary ports, paths, HTTP and HTTPS, configurable timeouts,
 * and never logs credentials or raw password query parameters.
 */
@Singleton
class XtreamApiService @Inject constructor(
    private val baseOkHttpClient: OkHttpClient,
) {
    fun normalizeServerUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        require(trimmed.isNotBlank()) { "Server URL cannot be empty" }
        require(
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true),
        ) {
            "Server URL must start with http:// or https://"
        }

        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL format")

        // Strip trailing player_api.php / get.php / xmltv.php if user pasted a full endpoint
        val cleanPath = parsed.encodedPath
            .replace(Regex("/(player_api\\.php|get\\.php|xmltv\\.php).*$", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')

        val defaultPort = (parsed.scheme == "http" && parsed.port == 80) ||
            (parsed.scheme == "https" && parsed.port == 443)
        val portPart = if (defaultPort) "" else ":${parsed.port}"
        return "${parsed.scheme}://${parsed.host}$portPart$cleanPath"
    }

    private fun clientWithTimeout(timeoutSeconds: Int): OkHttpClient {
        val sec = timeoutSeconds.coerceIn(5, 60).toLong()
        return baseOkHttpClient.newBuilder()
            .connectTimeout(sec, TimeUnit.SECONDS)
            .readTimeout((sec * 2).coerceAtLeast(25L), TimeUnit.SECONDS)
            .writeTimeout(sec, TimeUnit.SECONDS)
            .build()
    }

    private fun buildApiUrl(
        serverUrl: String,
        username: String,
        password: String,
        action: String? = null,
        extraParams: Map<String, String> = emptyMap(),
    ): String {
        val base = normalizeServerUrl(serverUrl)
        val httpUrl = "$base/player_api.php".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL")
        val builder = httpUrl.newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
        if (!action.isNullOrBlank()) {
            builder.addQueryParameter("action", action)
        }
        extraParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    fun buildXmltvUrl(serverUrl: String, username: String, password: String): String {
        val base = normalizeServerUrl(serverUrl)
        val httpUrl = "$base/xmltv.php".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL")
        return httpUrl.newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .build()
            .toString()
    }

    fun buildLiveStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        allowedFormats: List<String>,
        directSource: String? = null,
    ): String {
        if (!directSource.isNullOrBlank() &&
            (directSource.startsWith("http://", true) || directSource.startsWith("https://", true))
        ) {
            return directSource
        }
        val base = normalizeServerUrl(serverUrl)
        val ext = when {
            allowedFormats.any { it.equals("ts", true) } -> "ts"
            allowedFormats.any { it.equals("m3u8", true) } -> "m3u8"
            else -> "ts"
        }
        return "$base/live/$username/$password/$streamId.$ext"
    }

    fun buildVodStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        containerExtension: String?,
        directSource: String? = null,
    ): String {
        if (!directSource.isNullOrBlank() &&
            (directSource.startsWith("http://", true) || directSource.startsWith("https://", true))
        ) {
            return directSource
        }
        val base = normalizeServerUrl(serverUrl)
        val ext = containerExtension?.trimStart('.')?.takeIf { it.isNotBlank() } ?: "mp4"
        return "$base/movie/$username/$password/$streamId.$ext"
    }

    fun buildSeriesStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        episodeId: String,
        containerExtension: String?,
        directSource: String? = null,
    ): String {
        if (!directSource.isNullOrBlank() &&
            (directSource.startsWith("http://", true) || directSource.startsWith("https://", true))
        ) {
            return directSource
        }
        val base = normalizeServerUrl(serverUrl)
        val ext = containerExtension?.trimStart('.')?.takeIf { it.isNotBlank() } ?: "mp4"
        return "$base/series/$username/$password/$episodeId.$ext"
    }

    fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        timeoutSeconds: Int = 15,
    ): XtreamAuthResponse {
        val url = buildApiUrl(serverUrl, username, password)
        val raw = executeGet(url, timeoutSeconds)
        val authResponse = try {
            XtreamJsonParser.parseAuth(raw)
        } catch (e: Exception) {
            throw IOException("Server returned an unexpected response (not a valid Xtream Codes API)")
        }

        val userInfo = authResponse.userInfo
            ?: throw IOException("Authentication failed: missing account info from server")

        if (userInfo.auth != 1) {
            val statusMsg = userInfo.status?.takeIf { it.isNotBlank() }
            throw SecurityException(
                if (statusMsg != null) "Authentication rejected ($statusMsg). Please check username and password."
                else "Invalid username or password.",
            )
        }

        if (userInfo.status.equals("Expired", ignoreCase = true)) {
            throw SecurityException("This subscription has expired.")
        }
        if (userInfo.status.equals("Banned", ignoreCase = true) ||
            userInfo.status.equals("Disabled", ignoreCase = true)
        ) {
            throw SecurityException("This account is ${userInfo.status}.")
        }
        val exp = userInfo.expDateEpochSec
        if (exp != null && exp > 0 && exp * 1000L < System.currentTimeMillis()) {
            throw SecurityException("This subscription expired on ${java.text.DateFormat.getDateInstance().format(java.util.Date(exp * 1000L))}.")
        }

        return authResponse
    }

    fun fetchLiveCategories(serverUrl: String, username: String, password: String, timeoutSeconds: Int): List<XtreamCategoryDto> =
        runCatching {
            XtreamJsonParser.parseCategories(
                executeGet(buildApiUrl(serverUrl, username, password, "get_live_categories"), timeoutSeconds),
            )
        }.getOrDefault(emptyList())

    fun fetchLiveStreams(serverUrl: String, username: String, password: String, timeoutSeconds: Int): List<XtreamLiveStreamDto> =
        XtreamJsonParser.parseLiveStreams(
            executeGet(buildApiUrl(serverUrl, username, password, "get_live_streams"), timeoutSeconds),
        )

    fun fetchVodCategories(serverUrl: String, username: String, password: String, timeoutSeconds: Int): List<XtreamCategoryDto> =
        runCatching {
            XtreamJsonParser.parseCategories(
                executeGet(buildApiUrl(serverUrl, username, password, "get_vod_categories"), timeoutSeconds),
            )
        }.getOrDefault(emptyList())

    fun fetchVodStreams(serverUrl: String, username: String, password: String, timeoutSeconds: Int): List<XtreamVodStreamDto> =
        runCatching {
            XtreamJsonParser.parseVodStreams(
                executeGet(buildApiUrl(serverUrl, username, password, "get_vod_streams"), timeoutSeconds),
            )
        }.getOrDefault(emptyList())

    fun fetchSeriesCategories(serverUrl: String, username: String, password: String, timeoutSeconds: Int): List<XtreamCategoryDto> =
        runCatching {
            XtreamJsonParser.parseCategories(
                executeGet(buildApiUrl(serverUrl, username, password, "get_series_categories"), timeoutSeconds),
            )
        }.getOrDefault(emptyList())

    fun fetchSeriesList(serverUrl: String, username: String, password: String, timeoutSeconds: Int): List<XtreamSeriesDto> =
        runCatching {
            XtreamJsonParser.parseSeriesList(
                executeGet(buildApiUrl(serverUrl, username, password, "get_series"), timeoutSeconds),
            )
        }.getOrDefault(emptyList())

    fun fetchSeriesInfo(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: String,
        timeoutSeconds: Int,
    ): XtreamSeriesInfoDto {
        val raw = executeGet(
            buildApiUrl(
                serverUrl,
                username,
                password,
                action = "get_series_info",
                extraParams = mapOf("series_id" to seriesId),
            ),
            timeoutSeconds,
        )
        return XtreamJsonParser.parseSeriesInfo(raw)
    }

    fun fetchShortEpg(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        limit: Int = 4,
        timeoutSeconds: Int = 10,
    ): List<XtreamShortEpgItemDto> = runCatching {
        val raw = executeGet(
            buildApiUrl(
                serverUrl,
                username,
                password,
                action = "get_short_epg",
                extraParams = mapOf("stream_id" to streamId, "limit" to limit.toString()),
            ),
            timeoutSeconds,
        )
        XtreamJsonParser.parseShortEpg(raw)
    }.getOrDefault(emptyList())

    private fun executeGet(url: String, timeoutSeconds: Int): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LiquidTV/1.1 (Android TV)")
            .get()
            .build()
        clientWithTimeout(timeoutSeconds).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response from server")
            if (body.isBlank()) throw IOException("Server returned an empty payload")
            return body
        }
    }
}
