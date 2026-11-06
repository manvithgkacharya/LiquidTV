package com.liquidtv.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val featuredChannel: Channel? = null,
    val allChannels: List<Channel> = emptyList(),
    val recentlyWatched: List<Channel> = emptyList(),
    val favoriteChannels: List<Channel> = emptyList(),
    val popularCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val loadingProgress: Float = 0f,
    val loadingMessage: String? = null,
    val playlistInfo: PlaylistInfo? = null,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val uiState: StateFlow<HomeUiState> = state

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            combine(
                channelRepository.getAllChannels(),
                channelRepository.getRecentlyWatched(),
                channelRepository.getFavoriteChannels(),
                channelRepository.getAllCategories(),
                channelRepository.getPlaylistInfo(),
            ) { allChannels, recentlyWatched, favorites, categories, playlistInfo ->
                val featured = allChannels.firstOrNull { it.logoUrl != null } ?: allChannels.firstOrNull()
                HomeUiState(
                    featuredChannel = featured,
                    allChannels = allChannels,
                    recentlyWatched = recentlyWatched,
                    favoriteChannels = favorites,
                    popularCategories = categories.take(12),
                    isLoading = playlistInfo.isLoading,
                    loadingProgress = playlistInfo.loadingProgress,
                    loadingMessage = if (playlistInfo.isLoading) "Fetching IPTV streams..." else null,
                    playlistInfo = playlistInfo,
                    error = playlistInfo.error,
                )
            }.catch { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.message ?: "Unknown error")
                }
            }.collect { state ->
                _uiState.value = state

                // Trigger a playlist refresh if we have no channels yet, or if language metadata is missing
                val missingMetadata = state.allChannels.isNotEmpty() && state.allChannels.all { it.language == null }
                if ((state.allChannels.isEmpty() || missingMetadata) && state.playlistInfo?.isLoading != true) {
                    refreshPlaylists()
                }
            }
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(channelId)
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = channelRepository.refreshPlaylists()
            result.onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.message ?: "Failed to refresh playlists")
                }
            }
        }
    }

    fun recordWatch(channelId: String) {
        viewModelScope.launch {
            channelRepository.recordWatch(channelId)
        }
    }
}
