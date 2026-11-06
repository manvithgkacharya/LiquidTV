package com.liquidtv.app.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
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

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val loadingMessage: String? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            combine(
                channelRepository.getAllCategories(),
                channelRepository.getPlaylistInfo(),
            ) { categories, playlistInfo ->
                val isLoading = playlistInfo.isLoading || (categories.isEmpty() && playlistInfo.lastRefreshed == 0L)
                val message = if (playlistInfo.isLoading) {
                    "Loading categories & streams (${(playlistInfo.loadingProgress * 100).toInt()}%)..."
                } else null
                CategoriesUiState(
                    categories = categories,
                    isLoading = isLoading,
                    loadingMessage = message,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false) }
            }.collect { state ->
                _uiState.value = state
                if (state.categories.isEmpty() && !state.isLoading) {
                    channelRepository.refreshPlaylists()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            channelRepository.refreshPlaylists()
        }
    }
}
