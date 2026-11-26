package com.liquidtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "XTREAM", // "XTREAM"
    val origin: String = "NORMAL_URL", // "NORMAL_URL" | "PANEL_SERVER"
    val serverUrl: String,
    val panelServerId: String? = null,
    val panelServerName: String? = null,
    val username: String? = null,
    val encryptedPassword: String? = null,
    val xmltvUrl: String? = null,
    val isActive: Boolean = false,
    val connectionStatus: String = "DISCONNECTED",
    val lastError: String? = null,
    val accountStatus: String? = null,
    val expDateEpochSec: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val isTrial: Boolean? = null,
    val lastSyncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId"]),
        Index(value = ["subscriptionId", "mediaType"]),
        Index(value = ["subscriptionId", "groupTitle"]),
        Index(value = ["subscriptionId", "isFavorite"]),
        Index(value = ["subscriptionId", "tvgId"]),
        Index(value = ["subscriptionId", "name"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val externalId: String? = null,
    val mediaType: String = "LIVE", // "LIVE" | "MOVIE"
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
    val httpHeadersJson: String? = null,
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

@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId"]),
        Index(value = ["subscriptionId", "categoryName"]),
        Index(value = ["subscriptionId", "isFavorite"]),
        Index(value = ["subscriptionId", "name"]),
    ],
)
data class SeriesEntity(
    @PrimaryKey val id: String,
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
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId"]),
        Index(value = ["seriesId", "seasonNumber", "episodeNumber"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
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

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId", "contentType"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val externalCategoryId: String? = null,
    val contentType: String = "LIVE",
    val name: String,
    val sortOrder: Int = 0,
    val isLocked: Boolean = false,
)

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["subscriptionId", "watchedAt"]),
        Index(value = ["channelId"]),
    ],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: String = "",
    val channelId: String,
    val channelName: String,
    val mediaType: String = "LIVE",
    val watchedAt: Long = System.currentTimeMillis(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId", "channelTvgId", "startTimeMs", "endTimeMs"]),
    ],
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: String,
    val channelTvgId: String,
    val title: String,
    val description: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val category: String? = null,
)
