package com.liquidtv.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    // Playback preferences
    val defaultQuality: String = "Auto",
    val autoPlay: Boolean = true,
    val subtitlesEnabled: Boolean = false,
    val preferredSubtitleLanguage: String = "en",
    // UI preferences
    val theme: String = "dark",
    val clockVisible: Boolean = true,
    val screenSaverTimeout: Int = 5, // minutes
    // Playlist / network
    val playlistUrl: String = "",
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshInterval: Int = 24, // hours
    val connectionTimeout: Int = 15, // seconds
    val maxRetries: Int = 3,
    // Parental controls
    val parentalControlEnabled: Boolean = false,
    // Updates & GitHub
    val githubRepo: String = "your-github-username/LiquidTV",
    val autoCheckUpdates: Boolean = true,
    // State
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences
                .catch { _uiState.update { it.copy(isLoading = false) } }
                .collect { prefs ->
                    _uiState.update { state ->
                        state.copy(
                            defaultQuality = prefs.defaultQuality,
                            autoPlay = prefs.autoPlay,
                            subtitlesEnabled = prefs.subtitlesEnabled,
                            preferredSubtitleLanguage = prefs.preferredSubtitleLanguage,
                            theme = prefs.theme,
                            clockVisible = prefs.clockVisible,
                            screenSaverTimeout = prefs.screenSaverTimeout,
                            playlistUrl = prefs.playlistUrl,
                            autoRefreshEnabled = prefs.autoRefreshEnabled,
                            autoRefreshInterval = prefs.autoRefreshInterval,
                            connectionTimeout = prefs.connectionTimeout,
                            maxRetries = prefs.maxRetries,
                            parentalControlEnabled = prefs.parentalControlEnabled,
                            githubRepo = prefs.githubRepo,
                            autoCheckUpdates = prefs.autoCheckUpdates,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    // --- Playback ---

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            userPreferencesRepository.setDefaultQuality(quality)
            _uiState.update { it.copy(defaultQuality = quality) }
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoPlay(enabled)
            _uiState.update { it.copy(autoPlay = enabled) }
        }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSubtitlesEnabled(enabled)
            _uiState.update { it.copy(subtitlesEnabled = enabled) }
        }
    }

    fun setPreferredSubtitleLanguage(language: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredSubtitleLanguage(language)
            _uiState.update { it.copy(preferredSubtitleLanguage = language) }
        }
    }

    // --- UI ---

    fun setTheme(theme: String) {
        viewModelScope.launch {
            userPreferencesRepository.setTheme(theme)
            _uiState.update { it.copy(theme = theme) }
        }
    }

    fun setClockVisible(visible: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setClockVisible(visible)
            _uiState.update { it.copy(clockVisible = visible) }
        }
    }

    fun setScreenSaverTimeout(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setScreenSaverTimeout(minutes)
            _uiState.update { it.copy(screenSaverTimeout = minutes) }
        }
    }

    // --- Playlist / Network ---

    fun setPlaylistUrl(url: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPlaylistUrl(url)
            _uiState.update { it.copy(playlistUrl = url) }
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoRefreshEnabled(enabled)
            _uiState.update { it.copy(autoRefreshEnabled = enabled) }
        }
    }

    fun setAutoRefreshInterval(hours: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoRefreshInterval(hours)
            _uiState.update { it.copy(autoRefreshInterval = hours) }
        }
    }

    fun setConnectionTimeout(seconds: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setConnectionTimeout(seconds)
            _uiState.update { it.copy(connectionTimeout = seconds) }
        }
    }

    fun setMaxRetries(retries: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMaxRetries(retries)
            _uiState.update { it.copy(maxRetries = retries) }
        }
    }

    // --- Parental Controls ---

    fun setParentalControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setParentalControlEnabled(enabled)
            _uiState.update { it.copy(parentalControlEnabled = enabled) }
        }
    }

    // --- Actions ---

    fun refreshPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, successMessage = null) }
            val result = channelRepository.refreshPlaylists()
            result.onSuccess {
                _uiState.update { it.copy(isRefreshing = false, successMessage = "Playlists refreshed successfully") }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isRefreshing = false, error = throwable.message ?: "Failed to refresh playlists")
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
                _uiState.update { it.copy(isLoading = false, successMessage = "Cache cleared successfully") }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.message ?: "Failed to clear cache")
                }
            }
        }
    }

    fun setGithubRepo(repo: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGithubRepo(repo)
            _uiState.update { it.copy(githubRepo = repo) }
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoCheckUpdates(enabled)
            _uiState.update { it.copy(autoCheckUpdates = enabled) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
