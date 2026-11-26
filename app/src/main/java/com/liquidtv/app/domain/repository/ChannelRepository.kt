package com.liquidtv.app.domain.repository

import com.liquidtv.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getAllChannels(): Flow<List<Channel>>
    fun getMovies(): Flow<List<Channel>>
    fun getSeries(): Flow<List<SeriesItem>>
    fun getSeriesById(seriesId: String): Flow<SeriesItem?>
    fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeItem>>
    suspend fun loadSeriesDetails(seriesId: String): Result<Unit>

    fun getFavoriteChannels(): Flow<List<Channel>>
    fun getRecentlyWatched(): Flow<List<Channel>>
    fun getChannelsByCategory(category: String, mediaType: MediaType = MediaType.LIVE): Flow<List<Channel>>
    fun getChannelsByLanguage(language: String): Flow<List<Channel>>
    fun getChannelsByCountry(country: String): Flow<List<Channel>>
    fun searchChannels(query: String): Flow<List<Channel>>
    fun searchSeries(query: String): Flow<List<SeriesItem>>
    fun getAllCategories(mediaType: MediaType = MediaType.LIVE): Flow<List<Category>>
    fun getAllLanguages(): Flow<List<Language>>
    fun getAllCountries(): Flow<List<Country>>
    fun getLockedCategoryNames(): Flow<Set<String>>
    suspend fun setCategoryLocked(categoryName: String, locked: Boolean)

    suspend fun getChannelById(id: String): Channel?
    suspend fun getEpisodeById(episodeId: String): EpisodeItem?
    suspend fun toggleFavorite(channelId: String)
    suspend fun toggleSeriesFavorite(seriesId: String)
    suspend fun recordWatch(channelId: String, positionMs: Long = 0L, durationMs: Long = 0L)
    suspend fun recordEpisodeWatch(episodeId: String, positionMs: Long = 0L, durationMs: Long = 0L)
    suspend fun updateResumePosition(itemId: String, mediaType: MediaType, positionMs: Long)

    fun observeChannelEpg(channel: Channel): Flow<ChannelEpgSummary>
    suspend fun fetchShortEpgIfNeeded(channel: Channel): ChannelEpgSummary

    suspend fun refreshPlaylists(): Result<Unit>
    suspend fun clearWatchHistory()
    fun getPlaylistInfo(): Flow<PlaylistInfo>
}
