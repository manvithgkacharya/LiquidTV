package com.liquidtv.app.data.local.dao

import androidx.room.*
import com.liquidtv.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdAt ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY createdAt ASC")
    suspend fun getAllSubscriptionsOnce(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE isActive = 1 LIMIT 1")
    fun getActiveSubscription(): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSubscriptionOnce(): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionById(id: String): SubscriptionEntity?

    @Upsert
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET isActive = CASE WHEN id = :subscriptionId THEN 1 ELSE 0 END")
    suspend fun setActiveSubscription(subscriptionId: String)

    @Query(
        """
        UPDATE subscriptions
        SET connectionStatus = :status, lastError = :lastError, lastSyncedAt = :syncedAt
        WHERE id = :subscriptionId
        """,
    )
    suspend fun updateSyncStatus(
        subscriptionId: String,
        status: String,
        lastError: String?,
        syncedAt: Long?,
    )

    @Query("DELETE FROM subscriptions WHERE id = :subscriptionId")
    suspend fun deleteSubscriptionById(subscriptionId: String)

    @Query("SELECT COUNT(*) FROM subscriptions")
    fun getSubscriptionCount(): Flow<Int>
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId AND mediaType = 'LIVE' ORDER BY COALESCE(channelNumber, 999999), name ASC")
    fun getLiveChannels(subscriptionId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId AND mediaType = 'MOVIE' ORDER BY name ASC")
    fun getMovies(subscriptionId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId ORDER BY name ASC")
    fun getAllChannels(subscriptionId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId")
    suspend fun getAllChannelsOnce(subscriptionId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId AND isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(subscriptionId: String): Flow<List<ChannelEntity>>

    @Query("SELECT id, isFavorite, lastWatchedAt, resumePositionMs FROM channels WHERE subscriptionId = :subscriptionId AND (isFavorite = 1 OR lastWatchedAt IS NOT NULL OR resumePositionMs > 0)")
    suspend fun getPreservedUserState(subscriptionId: String): List<ChannelUserStateTuple>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN (
            SELECT channelId, MAX(watchedAt) as lastWatch
            FROM watch_history
            WHERE subscriptionId = :subscriptionId
            GROUP BY channelId
        ) wh ON c.id = wh.channelId
        WHERE c.subscriptionId = :subscriptionId
        ORDER BY wh.lastWatch DESC
        LIMIT 50
        """,
    )
    fun getRecentlyWatched(subscriptionId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId AND mediaType = :mediaType AND groupTitle = :category ORDER BY name ASC")
    fun getChannelsByCategory(subscriptionId: String, category: String, mediaType: String = "LIVE"): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId AND language = :language ORDER BY name ASC")
    fun getChannelsByLanguage(subscriptionId: String, language: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE subscriptionId = :subscriptionId AND country = :country ORDER BY name ASC")
    fun getChannelsByCountry(subscriptionId: String, country: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE subscriptionId = :subscriptionId AND (
            name LIKE '%' || :query || '%'
            OR tvgName LIKE '%' || :query || '%'
            OR groupTitle LIKE '%' || :query || '%'
            OR language LIKE '%' || :query || '%'
            OR country LIKE '%' || :query || '%'
        )
        ORDER BY
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            name ASC
        LIMIT 200
        """,
    )
    fun searchChannels(subscriptionId: String, query: String): Flow<List<ChannelEntity>>

    @Query("SELECT groupTitle, COUNT(*) as count FROM channels WHERE subscriptionId = :subscriptionId AND mediaType = :mediaType AND groupTitle IS NOT NULL AND groupTitle != '' GROUP BY groupTitle ORDER BY count DESC")
    fun getCategoriesWithCount(subscriptionId: String, mediaType: String = "LIVE"): Flow<List<CategoryCount>>

    @Query("SELECT language, COUNT(*) as count FROM channels WHERE subscriptionId = :subscriptionId AND language IS NOT NULL AND language != '' GROUP BY language ORDER BY count DESC")
    fun getLanguagesWithCount(subscriptionId: String): Flow<List<LanguageCount>>

    @Query("SELECT country, COUNT(*) as count FROM channels WHERE subscriptionId = :subscriptionId AND country IS NOT NULL AND country != '' GROUP BY country ORDER BY count DESC")
    fun getCountriesWithCount(subscriptionId: String): Flow<List<CountryCount>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Query("UPDATE channels SET isFavorite = NOT isFavorite WHERE id = :channelId")
    suspend fun toggleFavorite(channelId: String)

    @Query("UPDATE channels SET lastWatchedAt = :timestamp WHERE id = :channelId")
    suspend fun updateLastWatched(channelId: String, timestamp: Long)

    @Query("UPDATE channels SET resumePositionMs = :positionMs WHERE id = :channelId")
    suspend fun updateResumePosition(channelId: String, positionMs: Long)

    @Upsert
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Upsert
    suspend fun upsertChannel(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE subscriptionId = :subscriptionId")
    suspend fun deleteChannelsForSubscription(subscriptionId: String)

    @Query("DELETE FROM channels WHERE subscriptionId = :subscriptionId AND mediaType = :mediaType")
    suspend fun deleteChannelsForSubscriptionByType(subscriptionId: String, mediaType: String)

    @Query("SELECT COUNT(*) FROM channels WHERE subscriptionId = :subscriptionId AND mediaType = :mediaType")
    fun getChannelCountByType(subscriptionId: String, mediaType: String): Flow<Int>

    @Transaction
    suspend fun replaceChannelsAtomically(
        subscriptionId: String,
        mediaType: String?,
        newChannels: List<ChannelEntity>,
    ) {
        if (mediaType != null) {
            deleteChannelsForSubscriptionByType(subscriptionId, mediaType)
        } else {
            deleteChannelsForSubscription(subscriptionId)
        }
        newChannels.chunked(500).forEach { chunk ->
            upsertChannels(chunk)
        }
    }
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE subscriptionId = :subscriptionId ORDER BY name ASC")
    fun getSeries(subscriptionId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE subscriptionId = :subscriptionId AND categoryName = :category ORDER BY name ASC")
    fun getSeriesByCategory(subscriptionId: String, category: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE subscriptionId = :subscriptionId AND name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 100")
    fun searchSeries(subscriptionId: String, query: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    fun observeSeriesById(seriesId: String): Flow<SeriesEntity?>

    @Query("SELECT id, isFavorite, lastWatchedAt FROM series WHERE subscriptionId = :subscriptionId AND (isFavorite = 1 OR lastWatchedAt IS NOT NULL)")
    suspend fun getPreservedSeriesState(subscriptionId: String): List<SeriesUserStateTuple>

    @Query("UPDATE series SET isFavorite = NOT isFavorite WHERE id = :seriesId")
    suspend fun toggleFavorite(seriesId: String)

    @Upsert
    suspend fun upsertSeries(series: List<SeriesEntity>)

    @Upsert
    suspend fun upsertSingleSeries(series: SeriesEntity)

    @Query("DELETE FROM series WHERE subscriptionId = :subscriptionId")
    suspend fun deleteSeriesForSubscription(subscriptionId: String)

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Query("SELECT id, lastWatchedAt, resumePositionMs FROM episodes WHERE seriesId = :seriesId AND (lastWatchedAt IS NOT NULL OR resumePositionMs > 0)")
    suspend fun getPreservedEpisodeState(seriesId: String): List<EpisodeUserStateTuple>

    @Query("UPDATE episodes SET lastWatchedAt = :timestamp, resumePositionMs = :positionMs WHERE id = :episodeId")
    suspend fun updateEpisodeProgress(episodeId: String, timestamp: Long, positionMs: Long)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodesForSeries(seriesId: String)

    @Transaction
    suspend fun replaceSeriesAtomically(
        subscriptionId: String,
        seriesList: List<SeriesEntity>,
    ) {
        deleteSeriesForSubscription(subscriptionId)
        seriesList.chunked(500).forEach { chunk ->
            upsertSeries(chunk)
        }
    }

    @Transaction
    suspend fun replaceEpisodesForSeriesAtomically(
        seriesId: String,
        episodes: List<EpisodeEntity>,
    ) {
        deleteEpisodesForSeries(seriesId)
        episodes.chunked(250).forEach { chunk ->
            upsertEpisodes(chunk)
        }
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE subscriptionId = :subscriptionId AND contentType = :contentType ORDER BY sortOrder ASC, name ASC")
    fun getCategories(subscriptionId: String, contentType: String = "LIVE"): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE subscriptionId = :subscriptionId")
    suspend fun getAllCategoriesOnce(subscriptionId: String): List<CategoryEntity>

    @Query("SELECT name FROM categories WHERE subscriptionId = :subscriptionId AND isLocked = 1")
    fun getLockedCategoryNames(subscriptionId: String): Flow<List<String>>

    @Query("UPDATE categories SET isLocked = :locked WHERE subscriptionId = :subscriptionId AND name = :categoryName")
    suspend fun setCategoryLocked(subscriptionId: String, categoryName: String, locked: Boolean)

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE subscriptionId = :subscriptionId AND contentType = :contentType")
    suspend fun deleteCategoriesByType(subscriptionId: String, contentType: String)

    @Transaction
    suspend fun replaceCategoriesAtomically(
        subscriptionId: String,
        contentType: String,
        categories: List<CategoryEntity>,
    ) {
        deleteCategoriesByType(subscriptionId, contentType)
        categories.chunked(500).forEach { chunk ->
            upsertCategories(chunk)
        }
    }
}

@Dao
interface WatchHistoryDao {
    @Insert
    suspend fun insertEntry(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE subscriptionId = :subscriptionId ORDER BY watchedAt DESC LIMIT 100")
    fun getHistory(subscriptionId: String): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history WHERE subscriptionId = :subscriptionId")
    suspend fun clearHistoryForSubscription(subscriptionId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    @Query("DELETE FROM watch_history WHERE channelId = :channelId")
    suspend fun removeChannelHistory(channelId: String)
}

@Dao
interface EpgDao {
    @Query(
        """
        SELECT * FROM epg_programs
        WHERE subscriptionId = :subscriptionId
          AND LOWER(channelTvgId) = LOWER(:channelTvgId)
          AND endTimeMs > :nowMs
        ORDER BY startTimeMs ASC
        LIMIT 10
        """,
    )
    suspend fun getUpcomingPrograms(subscriptionId: String, channelTvgId: String, nowMs: Long): List<EpgProgramEntity>

    @Query(
        """
        SELECT * FROM epg_programs
        WHERE subscriptionId = :subscriptionId
          AND LOWER(channelTvgId) = LOWER(:channelTvgId)
          AND endTimeMs > :nowMs
        ORDER BY startTimeMs ASC
        LIMIT 2
        """,
    )
    fun observeCurrentAndNext(subscriptionId: String, channelTvgId: String, nowMs: Long): Flow<List<EpgProgramEntity>>

    @Query("SELECT COUNT(*) FROM epg_programs WHERE subscriptionId = :subscriptionId")
    suspend fun getEpgCount(subscriptionId: String): Int

    @Insert
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE subscriptionId = :subscriptionId")
    suspend fun deleteEpgForSubscription(subscriptionId: String)

    @Query("DELETE FROM epg_programs WHERE subscriptionId = :subscriptionId AND LOWER(channelTvgId) = LOWER(:channelTvgId)")
    suspend fun deleteEpgForChannel(subscriptionId: String, channelTvgId: String)

    @Transaction
    suspend fun replaceEpgForSubscriptionAtomically(
        subscriptionId: String,
        programs: List<EpgProgramEntity>,
    ) {
        deleteEpgForSubscription(subscriptionId)
        programs.chunked(500).forEach { chunk ->
            insertPrograms(chunk)
        }
    }
}

data class CategoryCount(val groupTitle: String, val count: Int)
data class LanguageCount(val language: String, val count: Int)
data class CountryCount(val country: String, val count: Int)

data class ChannelUserStateTuple(
    val id: String,
    val isFavorite: Boolean,
    val lastWatchedAt: Long?,
    val resumePositionMs: Long,
)

data class SeriesUserStateTuple(
    val id: String,
    val isFavorite: Boolean,
    val lastWatchedAt: Long?,
)

data class EpisodeUserStateTuple(
    val id: String,
    val lastWatchedAt: Long?,
    val resumePositionMs: Long,
)
