package com.liquidtv.app.domain.repository

import com.liquidtv.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    val searchHistory: Flow<List<String>>

    fun getGlassIntensity(): Flow<Float>
    fun getBlurIntensity(): Flow<Float>
    fun getAnimationIntensity(): Flow<Float>
    fun getAmbientTheme(): Flow<String>
    fun getAmbientEnabled(): Flow<Boolean>
    fun getAmbientIdleDuration(): Flow<Long>
    fun getAmbientClockVisible(): Flow<Boolean>
    fun getAutoPlayNext(): Flow<Boolean>
    fun getStreamTimeout(): Flow<Int>
    fun getRetryAttempts(): Flow<Int>
    fun getLastSearchQuery(): Flow<String>

    suspend fun setGlassIntensity(value: Float)
    suspend fun setBlurIntensity(value: Float)
    suspend fun setAnimationIntensity(value: Float)
    suspend fun setAmbientTheme(theme: String)
    suspend fun setAmbientEnabled(enabled: Boolean)
    suspend fun setAmbientIdleDuration(ms: Long)
    suspend fun setAmbientClockVisible(visible: Boolean)
    suspend fun setAutoPlayNext(enabled: Boolean)
    suspend fun setStreamTimeout(seconds: Int)
    suspend fun setRetryAttempts(count: Int)

    suspend fun setDefaultQuality(quality: String)
    suspend fun setAutoPlay(enabled: Boolean)
    suspend fun setSubtitlesEnabled(enabled: Boolean)
    suspend fun setPreferredSubtitleLanguage(language: String)
    suspend fun setTheme(theme: String)
    suspend fun setClockVisible(visible: Boolean)
    suspend fun setScreenSaverTimeout(minutes: Int)
    suspend fun setPlaylistUrl(url: String)
    suspend fun setAutoRefreshEnabled(enabled: Boolean)
    suspend fun setAutoRefreshInterval(hours: Int)
    suspend fun setConnectionTimeout(seconds: Int)
    suspend fun setMaxRetries(retries: Int)
    suspend fun setParentalControlEnabled(enabled: Boolean)
    suspend fun setGithubRepo(repo: String)
    suspend fun setAutoCheckUpdates(enabled: Boolean)

    suspend fun addSearchHistory(query: String)
    suspend fun removeSearchHistory(query: String)
    suspend fun clearSearchHistory()
}
