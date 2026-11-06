package com.liquidtv.app.domain.repository

import com.liquidtv.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getAllChannels(): Flow<List<Channel>>
    fun getFavoriteChannels(): Flow<List<Channel>>
    fun getRecentlyWatched(): Flow<List<Channel>>
    fun getChannelsByCategory(category: String): Flow<List<Channel>>
    fun getChannelsByLanguage(language: String): Flow<List<Channel>>
    fun getChannelsByCountry(country: String): Flow<List<Channel>>
    fun searchChannels(query: String): Flow<List<Channel>>
    fun getAllCategories(): Flow<List<Category>>
    fun getAllLanguages(): Flow<List<Language>>
    fun getAllCountries(): Flow<List<Country>>
    suspend fun getChannelById(id: String): Channel?
    suspend fun toggleFavorite(channelId: String)
    suspend fun recordWatch(channelId: String)
    suspend fun refreshPlaylists(): Result<Unit>
    suspend fun clearWatchHistory()
    fun getPlaylistInfo(): Flow<PlaylistInfo>
}

