package com.liquidtv.app.ui.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoviesUiState(
    val movies: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val favoritesOnly: Boolean = false,
    val totalCount: Int = 0,
    val favoritesCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _favoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<MoviesUiState> = combine(
        channelRepository.getMovies(),
        channelRepository.getAllCategories(MediaType.MOVIE),
        channelRepository.getLockedCategoryNames(),
        userPreferencesRepository.userPreferences,
        combine(_selectedCategory, _favoritesOnly) { cat, favOnly -> cat to favOnly },
    ) { movies, categories, lockedCats, prefs, (selectedCat, favOnly) ->
        val enforceLock = prefs.parentalControlEnabled && prefs.hasParentalPin && lockedCats.isNotEmpty()
        val unlockedMovies = movies.filter { movie ->
            !enforceLock || (movie.groupTitle?.trim()?.lowercase() !in lockedCats)
        }
        val visibleMovies = unlockedMovies.filter { movie ->
            val matchesCategory = selectedCat == null || movie.groupTitle.equals(selectedCat, ignoreCase = true)
            val matchesFav = !favOnly || movie.isFavorite
            matchesCategory && matchesFav
        }
        val visibleCategories = if (enforceLock) {
            categories.filter { it.name.trim().lowercase() !in lockedCats }
        } else {
            categories
        }
        MoviesUiState(
            movies = visibleMovies,
            categories = visibleCategories,
            selectedCategory = selectedCat,
            favoritesOnly = favOnly,
            totalCount = unlockedMovies.size,
            favoritesCount = unlockedMovies.count { it.isFavorite },
            isLoading = false,
            error = null,
        )
    }.catch { throwable ->
        emit(
            MoviesUiState(
                isLoading = false,
                error = throwable.message ?: "تعذّر تحميل الأفلام",
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoviesUiState(),
    )

    fun selectCategory(category: String?) {
        _favoritesOnly.value = false
        _selectedCategory.value = category
    }

    fun selectFavoritesOnly() {
        _selectedCategory.value = null
        _favoritesOnly.value = true
    }

    fun toggleFavorite(movieId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(movieId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            channelRepository.refreshPlaylists()
        }
    }
}
