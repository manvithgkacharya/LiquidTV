package com.liquidtv.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.liquidtv.app.data.security.PinHasher
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import com.liquidtv.app.ui.theme.AppThemeCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "liquid_tv_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    private object Keys {
        val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
        val BLUR_INTENSITY = floatPreferencesKey("blur_intensity")
        val ANIMATION_INTENSITY = floatPreferencesKey("animation_intensity")
        val APP_THEME = stringPreferencesKey("app_theme")
        val AMBIENT_THEME = stringPreferencesKey("ambient_theme")
        val AMBIENT_ENABLED = booleanPreferencesKey("ambient_enabled")
        val AMBIENT_IDLE_DURATION = longPreferencesKey("ambient_idle_duration")
        val AMBIENT_CLOCK_VISIBLE = booleanPreferencesKey("ambient_clock_visible")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val STREAM_TIMEOUT = intPreferencesKey("stream_timeout")
        val RETRY_ATTEMPTS = intPreferencesKey("retry_attempts")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val SCREENSAVER_TIMEOUT = intPreferencesKey("screensaver_timeout")
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val AUTO_REFRESH_INTERVAL = intPreferencesKey("auto_refresh_interval")
        val PARENTAL_CONTROL = booleanPreferencesKey("parental_control")
        val PARENTAL_PIN_HASH = stringPreferencesKey("parental_pin_hash")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_SEARCH_QUERY = stringPreferencesKey("last_search_query")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    private val store get() = context.dataStore

    private fun <T> Flow<Preferences>.safeMap(default: T, transform: (Preferences) -> T): Flow<T> =
        catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> transform(prefs) }

    override val userPreferences: Flow<UserPreferences> =
        store.data.safeMap(UserPreferences()) { prefs ->
            val screensaverMin = (prefs[Keys.SCREENSAVER_TIMEOUT] ?: 5).coerceIn(1, 60)
            val pinHash = prefs[Keys.PARENTAL_PIN_HASH]
            UserPreferences(
                glassIntensity = (prefs[Keys.GLASS_INTENSITY] ?: 0.8f).coerceIn(0.2f, 1.0f),
                blurIntensity = (prefs[Keys.BLUR_INTENSITY] ?: 0.6f).coerceIn(0f, 1.0f),
                animationIntensity = (prefs[Keys.ANIMATION_INTENSITY] ?: 1.0f).coerceIn(0f, 1.5f),
                appTheme = AppThemeCatalog.resolve(prefs[Keys.APP_THEME]).id,
                theme = prefs[Keys.AMBIENT_THEME] ?: "midnight_glass",
                ambientEnabled = prefs[Keys.AMBIENT_ENABLED] ?: true,
                ambientIdleDuration = prefs[Keys.AMBIENT_IDLE_DURATION] ?: (screensaverMin * 60_000L),
                clockVisible = prefs[Keys.AMBIENT_CLOCK_VISIBLE] ?: true,
                screenSaverTimeout = screensaverMin,
                defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "Auto",
                autoPlay = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
                subtitlesEnabled = prefs[Keys.SUBTITLES_ENABLED] ?: false,
                preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANG] ?: "en",
                autoRefreshEnabled = prefs[Keys.AUTO_REFRESH_ENABLED] ?: true,
                autoRefreshInterval = (prefs[Keys.AUTO_REFRESH_INTERVAL] ?: 24).coerceIn(1, 168),
                connectionTimeout = (prefs[Keys.STREAM_TIMEOUT] ?: 15).coerceIn(5, 60),
                maxRetries = (prefs[Keys.RETRY_ATTEMPTS] ?: 3).coerceIn(0, 10),
                parentalControlEnabled = (prefs[Keys.PARENTAL_CONTROL] ?: false) && !pinHash.isNullOrBlank(),
                hasParentalPin = !pinHash.isNullOrBlank(),
                githubRepo = prefs[Keys.GITHUB_REPO] ?: "kyanik010/LiquidTV",
                autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            )
        }

    override val searchHistory: Flow<List<String>> =
        store.data.safeMap(emptyList()) { prefs ->
            prefs[Keys.SEARCH_HISTORY]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?.take(20)
                ?: emptyList()
        }

    override fun getGlassIntensity(): Flow<Float> =
        store.data.safeMap(0.8f) { it[Keys.GLASS_INTENSITY] ?: 0.8f }

    override fun getBlurIntensity(): Flow<Float> =
        store.data.safeMap(0.6f) { it[Keys.BLUR_INTENSITY] ?: 0.6f }

    override fun getAnimationIntensity(): Flow<Float> =
        store.data.safeMap(1.0f) { it[Keys.ANIMATION_INTENSITY] ?: 1.0f }

    override fun getAppTheme(): Flow<String> =
        store.data.safeMap(AppThemeCatalog.DEFAULT_THEME_ID) {
            AppThemeCatalog.resolve(it[Keys.APP_THEME]).id
        }

    override fun getAmbientTheme(): Flow<String> =
        store.data.safeMap("midnight_glass") { it[Keys.AMBIENT_THEME] ?: "midnight_glass" }

    override fun getAmbientEnabled(): Flow<Boolean> =
        store.data.safeMap(true) { it[Keys.AMBIENT_ENABLED] ?: true }

    override fun getAmbientIdleDuration(): Flow<Long> =
        store.data.safeMap(300_000L) {
            it[Keys.AMBIENT_IDLE_DURATION] ?: ((it[Keys.SCREENSAVER_TIMEOUT] ?: 5) * 60_000L)
        }

    override fun getAmbientClockVisible(): Flow<Boolean> =
        store.data.safeMap(true) { it[Keys.AMBIENT_CLOCK_VISIBLE] ?: true }

    override fun getAutoPlayNext(): Flow<Boolean> =
        store.data.safeMap(true) { it[Keys.AUTO_PLAY_NEXT] ?: true }

    override fun getStreamTimeout(): Flow<Int> =
        store.data.safeMap(15) { it[Keys.STREAM_TIMEOUT] ?: 15 }

    override fun getRetryAttempts(): Flow<Int> =
        store.data.safeMap(3) { it[Keys.RETRY_ATTEMPTS] ?: 3 }

    override fun getLastSearchQuery(): Flow<String> =
        store.data.safeMap("") { it[Keys.LAST_SEARCH_QUERY] ?: "" }

    override suspend fun setGlassIntensity(value: Float) =
        store.edit { it[Keys.GLASS_INTENSITY] = value.coerceIn(0.2f, 1.0f) }

    override suspend fun setBlurIntensity(value: Float) =
        store.edit { it[Keys.BLUR_INTENSITY] = value.coerceIn(0f, 1.0f) }

    override suspend fun setAnimationIntensity(value: Float) =
        store.edit { it[Keys.ANIMATION_INTENSITY] = value.coerceIn(0f, 1.5f) }

    override suspend fun setAppTheme(themeId: String) {
        val resolvedId = AppThemeCatalog.resolve(themeId).id
        store.edit { it[Keys.APP_THEME] = resolvedId }
    }

    override suspend fun setAmbientTheme(theme: String) =
        store.edit { it[Keys.AMBIENT_THEME] = theme }

    override suspend fun setAmbientEnabled(enabled: Boolean) =
        store.edit { it[Keys.AMBIENT_ENABLED] = enabled }

    override suspend fun setAmbientIdleDuration(ms: Long) =
        store.edit {
            it[Keys.AMBIENT_IDLE_DURATION] = ms.coerceAtLeast(60_000L)
            it[Keys.SCREENSAVER_TIMEOUT] = (ms / 60_000L).toInt().coerceAtLeast(1)
        }

    override suspend fun setAmbientClockVisible(visible: Boolean) =
        store.edit { it[Keys.AMBIENT_CLOCK_VISIBLE] = visible }

    override suspend fun setAutoPlayNext(enabled: Boolean) =
        store.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }

    override suspend fun setStreamTimeout(seconds: Int) =
        store.edit { it[Keys.STREAM_TIMEOUT] = seconds.coerceIn(5, 60) }

    override suspend fun setRetryAttempts(count: Int) =
        store.edit { it[Keys.RETRY_ATTEMPTS] = count.coerceIn(0, 10) }

    override suspend fun setDefaultQuality(quality: String) =
        store.edit { it[Keys.DEFAULT_QUALITY] = quality }

    override suspend fun setAutoPlay(enabled: Boolean) =
        store.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }

    override suspend fun setSubtitlesEnabled(enabled: Boolean) =
        store.edit { it[Keys.SUBTITLES_ENABLED] = enabled }

    override suspend fun setPreferredSubtitleLanguage(language: String) =
        store.edit { it[Keys.PREFERRED_SUBTITLE_LANG] = language }

    override suspend fun setTheme(theme: String) =
        store.edit { it[Keys.AMBIENT_THEME] = theme }

    override suspend fun setClockVisible(visible: Boolean) =
        store.edit { it[Keys.AMBIENT_CLOCK_VISIBLE] = visible }

    override suspend fun setScreenSaverTimeout(minutes: Int) =
        store.edit {
            val mins = minutes.coerceIn(1, 60)
            it[Keys.SCREENSAVER_TIMEOUT] = mins
            it[Keys.AMBIENT_IDLE_DURATION] = mins * 60_000L
        }

    override suspend fun setAutoRefreshEnabled(enabled: Boolean) =
        store.edit { it[Keys.AUTO_REFRESH_ENABLED] = enabled }

    override suspend fun setAutoRefreshInterval(hours: Int) =
        store.edit { it[Keys.AUTO_REFRESH_INTERVAL] = hours.coerceIn(1, 168) }

    override suspend fun setConnectionTimeout(seconds: Int) =
        store.edit { it[Keys.STREAM_TIMEOUT] = seconds.coerceIn(5, 60) }

    override suspend fun setMaxRetries(retries: Int) =
        store.edit { it[Keys.RETRY_ATTEMPTS] = retries.coerceIn(0, 10) }

    override suspend fun setParentalControlEnabled(enabled: Boolean) =
        store.edit { it[Keys.PARENTAL_CONTROL] = enabled }

    override suspend fun setParentalPin(pin: String) {
        val hash = PinHasher.hashPin(pin)
        store.edit {
            it[Keys.PARENTAL_PIN_HASH] = hash
            it[Keys.PARENTAL_CONTROL] = true
        }
    }

    override suspend fun verifyParentalPin(pin: String): Boolean {
        val storedHash = store.data.first()[Keys.PARENTAL_PIN_HASH]
        return PinHasher.verifyPin(pin, storedHash)
    }

    override suspend fun clearParentalPin() {
        store.edit {
            it.remove(Keys.PARENTAL_PIN_HASH)
            it[Keys.PARENTAL_CONTROL] = false
        }
    }

    override suspend fun setGithubRepo(repo: String) =
        store.edit { it[Keys.GITHUB_REPO] = repo }

    override suspend fun setAutoCheckUpdates(enabled: Boolean) =
        store.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }

    override suspend fun addSearchHistory(query: String) {
        store.edit { prefs ->
            val history = (prefs[Keys.SEARCH_HISTORY] ?: "")
                .split("|")
                .filter { it.isNotBlank() && it != query }
                .toMutableList()
            history.add(0, query)
            prefs[Keys.SEARCH_HISTORY] = history.take(20).joinToString("|")
        }
    }

    override suspend fun removeSearchHistory(query: String) {
        store.edit { prefs ->
            val history = (prefs[Keys.SEARCH_HISTORY] ?: "")
                .split("|")
                .filter { it.isNotBlank() && it != query }
            prefs[Keys.SEARCH_HISTORY] = history.joinToString("|")
        }
    }

    override suspend fun clearSearchHistory() =
        store.edit { it.remove(Keys.SEARCH_HISTORY) }
}

private suspend fun DataStore<Preferences>.edit(transform: (MutablePreferences) -> Unit) {
    this.updateData { prefs -> prefs.toMutablePreferences().also(transform) }
}
