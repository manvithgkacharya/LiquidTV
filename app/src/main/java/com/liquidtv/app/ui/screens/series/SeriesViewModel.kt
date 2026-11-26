package com.liquidtv.app.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesUiState(
    val series: List<SeriesItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val favoritesOnly: Boolean = false,
    val totalCount: Int = 0,
    val favoritesCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _favoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<SeriesUiState> = combine(
        channelRepository.getSeries(),
        channelRepository.getAllCategories(MediaType.SERIES_EPISODE),
        channelRepository.getLockedCategoryNames(),
        userPreferencesRepository.userPreferences,
        combine(_selectedCategory, _favoritesOnly) { cat, favOnly -> cat to favOnly },
    ) { seriesList, categories, lockedCats, prefs, (selectedCat, favOnly) ->
        val enforceLock = prefs.parentalControlEnabled && prefs.hasParentalPin && lockedCats.isNotEmpty()
        val unlockedSeries = seriesList.filter { item ->
            !enforceLock || (item.categoryName?.trim()?.lowercase() !in lockedCats)
        }
        val visibleSeries = unlockedSeries.filter { item ->
            val matchesCat = selectedCat == null || item.categoryName.equals(selectedCat, ignoreCase = true)
            val matchesFav = !favOnly || item.isFavorite
            matchesCat && matchesFav
        }
        val visibleCategories = if (enforceLock) {
            categories.filter { it.name.trim().lowercase() !in lockedCats }
        } else {
            categories
        }
        SeriesUiState(
            series = visibleSeries,
            categories = visibleCategories,
            selectedCategory = selectedCat,
            favoritesOnly = favOnly,
            totalCount = unlockedSeries.size,
            favoritesCount = unlockedSeries.count { it.isFavorite },
            isLoading = false,
            error = null,
        )
    }.catch { throwable ->
        emit(
            SeriesUiState(
                isLoading = false,
                error = throwable.message ?: "تعذّر تحميل المسلسلات",
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SeriesUiState(),
    )

    fun selectCategory(category: String?) {
        _favoritesOnly.value = false
        _selectedCategory.value = category
    }

    fun selectFavoritesOnly() {
        _selectedCategory.value = null
        _favoritesOnly.value = true
    }

    fun toggleFavorite(seriesId: String) {
        viewModelScope.launch {
            channelRepository.toggleSeriesFavorite(seriesId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            channelRepository.refreshPlaylists()
        }
    }
}
