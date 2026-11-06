package com.liquidtv.app.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Channel
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

data class FavoritesUiState(
    val favorites: List<Channel> = emptyList(),
    val recentlyWatched: List<Channel> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                channelRepository.getFavoriteChannels(),
                channelRepository.getRecentlyWatched(),
            ) { favorites, recentlyWatched ->
                FavoritesUiState(
                    favorites = favorites,
                    recentlyWatched = recentlyWatched,
                    isLoading = false,
                )
            }.catch { throwable ->
                _uiState.update { it.copy(isLoading = false) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(channelId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            channelRepository.clearWatchHistory()
        }
    }
}
