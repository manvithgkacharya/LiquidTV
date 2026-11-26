package com.liquidtv.app.domain.model

import kotlinx.serialization.Serializable

enum class SubscriptionType {
    XTREAM,
}

enum class SubscriptionOrigin {
    NORMAL_URL,
    PANEL_SERVER,
}

enum class SubscriptionStatus {
    CONNECTED,
    EXPIRED,
    ERROR,
    DISCONNECTED,
}

enum class MediaType {
    LIVE,
    MOVIE,
    SERIES_EPISODE,
}

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val type: SubscriptionType = SubscriptionType.XTREAM,
    val origin: SubscriptionOrigin = SubscriptionOrigin.NORMAL_URL,
    val serverUrl: String,
    val panelServerId: String? = null,
    val panelServerName: String? = null,
    val username: String? = null,
    val maskedCredentialHint: String? = null,
    val xmltvUrl: String? = null,
    val isActive: Boolean = false,
    val connectionStatus: SubscriptionStatus = SubscriptionStatus.DISCONNECTED,
    val lastError: String? = null,
    val accountStatus: String? = null,
    val expDateEpochSec: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val isTrial: Boolean? = null,
    val lastSyncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Unified channel / playable media domain model supporting Live TV, Movies (VOD),
 * and Series episodes for Xtream Codes subscriptions.
 */
@Serializable
data class Channel(
    val id: String,
    val subscriptionId: String = "",
    val externalId: String? = null,
    val mediaType: MediaType = MediaType.LIVE,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val categoryId: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val country: String? = null,
    val language: String? = null,
    val channelNumber: Int? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val containerExtension: String? = null,
    val plot: String? = null,
    val rating: String? = null,
    val releaseYear: String? = null,
    val durationSec: Int? = null,
    val resumePositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class SeriesItem(
    val id: String,
    val subscriptionId: String,
    val externalSeriesId: String,
    val name: String,
    val coverUrl: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val lastModified: Long? = null,
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
)

@Serializable
data class EpisodeItem(
    val id: String,
    val subscriptionId: String,
    val seriesId: String,
    val externalEpisodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val containerExtension: String? = null,
    val plot: String? = null,
    val durationSec: Int? = null,
    val coverUrl: String? = null,
    val lastWatchedAt: Long? = null,
    val resumePositionMs: Long = 0L,
)

data class SeasonGroup(
    val seasonNumber: Int,
    val episodes: List<EpisodeItem>,
)

@Serializable
data class Category(
    val id: String,
    val subscriptionId: String = "",
    val externalCategoryId: String? = null,
    val contentType: MediaType = MediaType.LIVE,
    val name: String,
    val channelCount: Int = 0,
    val iconName: String? = null,
    val isLocked: Boolean = false,
)

@Serializable
data class Language(
    val code: String,
    val name: String,
    val channelCount: Int = 0,
)

@Serializable
data class Country(
    val code: String,
    val name: String,
    val flagEmoji: String? = null,
    val channelCount: Int = 0,
)

@Serializable
data class EpgProgram(
    val id: Long = 0L,
    val subscriptionId: String,
    val channelTvgId: String,
    val title: String,
    val description: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val category: String? = null,
) {
    fun progressFraction(nowMs: Long = System.currentTimeMillis()): Float {
        val total = (endTimeMs - startTimeMs).coerceAtLeast(1L)
        return ((nowMs - startTimeMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}

data class ChannelEpgSummary(
    val current: EpgProgram? = null,
    val next: EpgProgram? = null,
)

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val bitrate: Int = -1,
    val height: Int = -1,
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
    val mediaType: MediaType = MediaType.LIVE,
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
    val subscriptionId: String? = null,
    val url: String = "",
    val name: String = "",
    val totalChannels: Int = 0,
    val totalMovies: Int = 0,
    val totalSeries: Int = 0,
    val lastRefreshed: Long? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val statusMessage: String? = null,
    val error: String? = null,
)

data class WatchHistoryEntry(
    val channelId: String,
    val channelName: String,
    val mediaType: MediaType = MediaType.LIVE,
    val watchedAt: Long,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class UserPreferences(
    val glassIntensity: Float = 0.8f,
    val blurIntensity: Float = 0.6f,
    val animationIntensity: Float = 1.0f,
    val appTheme: String = "royal-navy",
    val theme: String = "midnight_glass",
    val ambientEnabled: Boolean = true,
    val ambientIdleDuration: Long = 180_000L,
    val clockVisible: Boolean = true,
    val screenSaverTimeout: Int = 5,
    val defaultQuality: String = "Auto",
    val autoPlay: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val preferredSubtitleLanguage: String = "en",
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshInterval: Int = 24,
    val connectionTimeout: Int = 15,
    val maxRetries: Int = 3,
    val parentalControlEnabled: Boolean = false,
    val hasParentalPin: Boolean = false,
    val githubRepo: String = "kyanik010/LiquidTV",
    val autoCheckUpdates: Boolean = true,
)
