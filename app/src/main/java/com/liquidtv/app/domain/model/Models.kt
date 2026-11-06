package com.liquidtv.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Core channel domain model derived from M3U playlist parsing.
 */
@Serializable
data class Channel(
    val id: String,                    // tvg-id or generated UUID
    val name: String,                  // display name (after comma in EXTINF)
    val streamUrl: String,             // actual stream URL
    val logoUrl: String? = null,       // tvg-logo
    val groupTitle: String? = null,    // group-title (category)
    val tvgId: String? = null,         // EPG tvg-id
    val tvgName: String? = null,       // tvg-name (may differ from display name)
    val country: String? = null,       // tvg-country (ISO 3166-1 alpha-2)
    val language: String? = null,      // tvg-language
    val channelNumber: Int? = null,    // tvg-chno
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val channelCount: Int = 0,
    val iconName: String? = null,
)

@Serializable
data class Language(
    val code: String,      // ISO 639-1 or full name from playlist
    val name: String,
    val channelCount: Int = 0,
)

@Serializable
data class Country(
    val code: String,      // ISO 3166-1 alpha-2
    val name: String,
    val flagEmoji: String? = null,
    val channelCount: Int = 0,
)

data class PlaybackState(
    val channel: Channel? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class ChannelFilter(
    val category: String? = null,
    val language: String? = null,
    val country: String? = null,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val showFavoritesOnly: Boolean = false,
)

enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    RECENTLY_WATCHED,
    CATEGORY,
    LANGUAGE,
    COUNTRY,
    FAVORITES_FIRST,
}

data class PlaylistInfo(
    val url: String,
    val name: String,
    val totalChannels: Int = 0,
    val lastRefreshed: Long? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val error: String? = null,
)

data class WatchHistoryEntry(
    val channelId: String,
    val channelName: String,
    val watchedAt: Long,
)

data class UserPreferences(
    val glassIntensity: Float = 0.8f,
    val blurIntensity: Float = 0.6f,
    val animationIntensity: Float = 1.0f,
    val theme: String = "midnight_glass",
    val ambientEnabled: Boolean = true,
    val ambientIdleDuration: Long = 180_000L,
    val clockVisible: Boolean = true,
    val screenSaverTimeout: Int = 5,
    val defaultQuality: String = "Auto",
    val autoPlay: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val preferredSubtitleLanguage: String = "en",
    val playlistUrl: String = "https://iptv-org.github.io/iptv/index.m3u",
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshInterval: Int = 24,
    val connectionTimeout: Int = 15,
    val maxRetries: Int = 3,
    val parentalControlEnabled: Boolean = false,
    val githubRepo: String = "manvithgkacharya/LiquidTV",
    val autoCheckUpdates: Boolean = true,
)
