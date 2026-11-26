package com.liquidtv.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.data.worker.PlaylistSyncWorker
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SonixControlRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import com.liquidtv.app.ui.theme.AppThemeCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val activeSubscription: Subscription? = null,
    val sonixState: SonixSyncState = SonixSyncState(),
    val categories: List<Category> = emptyList(),
    // Playback preferences
    val defaultQuality: String = "Auto",
    val autoPlay: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val preferredSubtitleLanguage: String = "en",
    // UI preferences
    val appTheme: String = AppThemeCatalog.DEFAULT_THEME_ID,
    val theme: String = "midnight_glass",
    val clockVisible: Boolean = true,
    val ambientEnabled: Boolean = true,
    val screenSaverTimeout: Int = 5,
    // Playlist / network
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshInterval: Int = 24,
    val connectionTimeout: Int = 15,
    val maxRetries: Int = 3,
    // Parental controls
    val parentalControlEnabled: Boolean = false,
    val hasParentalPin: Boolean = false,
    // Updates & GitHub
    val githubRepo: String = "kyanik010/LiquidTV",
    val autoCheckUpdates: Boolean = true,
    // State
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val channelRepository: ChannelRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val sonixControlRepository: SonixControlRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val sonixFlow = sonixControlRepository?.state ?: MutableStateFlow(SonixSyncState())
            combine(
                userPreferencesRepository.userPreferences,
                subscriptionRepository.activeSubscription,
                channelRepository.getAllCategories(MediaType.LIVE),
                sonixFlow,
            ) { prefs, sub, categories, sonix ->
                _uiState.value.copy(
                    activeSubscription = sub,
                    sonixState = sonix,
                    categories = categories,
                    defaultQuality = prefs.defaultQuality,
                    autoPlay = prefs.autoPlay,
                    subtitlesEnabled = prefs.subtitlesEnabled,
                    preferredSubtitleLanguage = prefs.preferredSubtitleLanguage,
                    appTheme = AppThemeCatalog.resolve(prefs.appTheme).id,
                    theme = prefs.theme,
                    clockVisible = prefs.clockVisible,
                    ambientEnabled = prefs.ambientEnabled,
                    screenSaverTimeout = prefs.screenSaverTimeout,
                    autoRefreshEnabled = prefs.autoRefreshEnabled,
                    autoRefreshInterval = prefs.autoRefreshInterval,
                    connectionTimeout = prefs.connectionTimeout,
                    maxRetries = prefs.maxRetries,
                    parentalControlEnabled = prefs.parentalControlEnabled,
                    hasParentalPin = prefs.hasParentalPin,
                    githubRepo = prefs.githubRepo,
                    autoCheckUpdates = prefs.autoCheckUpdates,
                    isLoading = false,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false) }
            }.collect { updated ->
                _uiState.value = updated
            }
        }
    }

    // --- Playback ---

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultQuality(quality)
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoPlay(enabled)
        }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSubtitlesEnabled(enabled)
        }
    }

    fun setPreferredSubtitleLanguage(language: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredSubtitleLanguage(language)
        }
    }

    fun setConnectionTimeout(seconds: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setConnectionTimeout(seconds)
        }
    }

    fun setMaxRetries(retries: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMaxRetries(retries)
        }
    }

    // --- Appearance & Ambient ---

    fun selectAppTheme(themeId: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppTheme(themeId)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            userPreferencesRepository.setTheme(theme)
        }
    }

    fun setClockVisible(visible: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setClockVisible(visible)
        }
    }

    fun setAmbientEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAmbientEnabled(enabled)
        }
    }

    fun setScreenSaverTimeout(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setScreenSaverTimeout(minutes)
        }
    }

    // --- Subscriptions & Auto-Sync ---

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoRefreshEnabled(enabled)
            PlaylistSyncWorker.schedule(context, enabled, _uiState.value.autoRefreshInterval)
        }
    }

    fun setAutoRefreshInterval(hours: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoRefreshInterval(hours)
            PlaylistSyncWorker.schedule(context, _uiState.value.autoRefreshEnabled, hours)
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, successMessage = null) }
            val result = subscriptionRepository.syncActiveSubscription()
            result.onSuccess {
                _uiState.update { it.copy(isRefreshing = false, successMessage = "تم تحديث قائمة الاشتراك بنجاح") }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isRefreshing = false, error = throwable.message ?: "فشل تحديث قائمة الاشتراك")
                }
            }
        }
    }

    fun refreshEpg() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, successMessage = null) }
            val result = subscriptionRepository.syncActiveEpg()
            result.onSuccess {
                _uiState.update { it.copy(isRefreshing = false, successMessage = "تم تحديث دليل البرامج بنجاح") }
            }.onFailure { throwable ->
                runCatching { android.util.Log.w("SettingsViewModel", "refreshEpg failed", throwable) }
                _uiState.update {
                    it.copy(isRefreshing = false, error = "تعذر تحديث دليل البرامج حالياً، يرجى المحاولة لاحقاً.")
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            runCatching {
                channelRepository.clearWatchHistory()
                userPreferencesRepository.clearSearchHistory()
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, successMessage = "تم مسح سجل المشاهدة والبحث المؤقت") }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.message ?: "فشل مسح البيانات المؤقتة")
                }
            }
        }
    }

    // --- Parental Controls ---

    suspend fun verifyParentalPin(pin: String): Boolean {
        return userPreferencesRepository.verifyParentalPin(pin)
    }

    fun enableParentalWithPin(pin: String) {
        viewModelScope.launch {
            userPreferencesRepository.setParentalPin(pin)
            userPreferencesRepository.setParentalControlEnabled(true)
            _uiState.update { it.copy(successMessage = "تم تفعيل الرقابة الأبوية") }
        }
    }

    fun updateParentalPin(newPin: String) {
        viewModelScope.launch {
            userPreferencesRepository.setParentalPin(newPin)
            _uiState.update { it.copy(successMessage = "تم تحديث رمز الحماية") }
        }
    }

    fun disableParentalControl() {
        viewModelScope.launch {
            userPreferencesRepository.setParentalControlEnabled(false)
            _uiState.update { it.copy(successMessage = "تم إيقاف الرقابة الأبوية") }
        }
    }

    fun toggleCategoryLocked(categoryName: String, currentlyLocked: Boolean) {
        viewModelScope.launch {
            channelRepository.setCategoryLocked(categoryName, !currentlyLocked)
        }
    }

    // --- Updates ---

    fun setGithubRepo(repo: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGithubRepo(repo)
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoCheckUpdates(enabled)
        }
    }

    fun syncWithControlPanel() {
        val repo = sonixControlRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, successMessage = null) }
            repo.syncNow(force = true)
                .onSuccess {
                    _uiState.update {
                        it.copy(isRefreshing = false, successMessage = "تم تحديث حالة الجهاز والإعدادات بنجاح")
                    }
                }
                .onFailure { throwable ->
                    runCatching { android.util.Log.w("SettingsViewModel", "syncWithControlPanel failed", throwable) }
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = throwable.message?.takeIf { msg -> !msg.contains("HTTP", ignoreCase = true) }
                                ?: "تعذرت مزامنة الإعدادات حالياً، يرجى المحاولة لاحقاً.",
                        )
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
