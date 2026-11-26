package com.liquidtv.app.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
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
    val lockedCategories: Set<String> = emptySet(),
    val parentalControlActive: Boolean = false,
    val isLoading: Boolean = true,
    val loadingMessage: String? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            combine(
                channelRepository.getAllCategories(MediaType.LIVE),
                channelRepository.getPlaylistInfo(),
                channelRepository.getLockedCategoryNames(),
                userPreferencesRepository.userPreferences,
            ) { categories, playlistInfo, lockedNames, prefs ->
                val message = if (playlistInfo.isLoading) {
                    playlistInfo.statusMessage
                        ?: "جارٍ مزامنة التصنيفات (${(playlistInfo.loadingProgress * 100).toInt()}%)..."
                } else null
                CategoriesUiState(
                    categories = categories,
                    lockedCategories = lockedNames,
                    parentalControlActive = prefs.parentalControlEnabled && prefs.hasParentalPin,
                    isLoading = playlistInfo.isLoading,
                    loadingMessage = message,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun isCategoryLocked(category: Category): Boolean {
        val state = _uiState.value
        if (!state.parentalControlActive) return false
        return category.isLocked || category.name.trim().lowercase() in state.lockedCategories
    }

    suspend fun verifyPin(pin: String): Boolean {
        return userPreferencesRepository.verifyParentalPin(pin)
    }

    fun toggleCategoryLock(categoryName: String, currentlyLocked: Boolean) {
        viewModelScope.launch {
            channelRepository.setCategoryLocked(categoryName, !currentlyLocked)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            channelRepository.refreshPlaylists()
        }
    }
}
