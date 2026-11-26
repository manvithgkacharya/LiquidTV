package com.liquidtv.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Channel> = emptyList(),
    val seriesResults: List<SeriesItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasResults: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            userPreferencesRepository.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }

        viewModelScope.launch {
            _queryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                results = emptyList(),
                                seriesResults = emptyList(),
                                hasResults = false,
                            )
                        }
                        flowOf(emptyList<Channel>() to emptyList<SeriesItem>())
                    } else {
                        _uiState.update { it.copy(isSearching = true) }
                        combine(
                            channelRepository.searchChannels(q),
                            channelRepository.searchSeries(q),
                            channelRepository.getLockedCategoryNames(),
                            userPreferencesRepository.userPreferences,
                        ) { channels, series, lockedCats, prefs ->
                            val filterLocked = prefs.parentalControlEnabled && prefs.hasParentalPin && lockedCats.isNotEmpty()
                            val filteredChannels = if (filterLocked) {
                                channels.filter { it.groupTitle?.trim()?.lowercase() !in lockedCats }
                            } else channels
                            val filteredSeries = if (filterLocked) {
                                series.filter { it.categoryName?.trim()?.lowercase() !in lockedCats }
                            } else series
                            filteredChannels to filteredSeries
                        }
                    }
                }
                .catch { }
                .collect { (channels, series) ->
                    _uiState.update {
                        it.copy(
                            results = channels,
                            seriesResults = series,
                            isSearching = false,
                            hasResults = channels.isNotEmpty() || series.isNotEmpty(),
                        )
                    }
                }
        }
    }

    fun setQuery(q: String) {
        _uiState.update { it.copy(query = q) }
        _queryFlow.value = q

        if (q.isNotBlank()) {
            viewModelScope.launch {
                userPreferencesRepository.addSearchHistory(q)
            }
        }
    }

    fun clearQuery() {
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                seriesResults = emptyList(),
                hasResults = false,
                isSearching = false,
            )
        }
        _queryFlow.value = ""
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(channelId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userPreferencesRepository.clearSearchHistory()
            _uiState.update { it.copy(searchHistory = emptyList()) }
        }
    }

    fun removeHistoryItem(q: String) {
        viewModelScope.launch {
            userPreferencesRepository.removeSearchHistory(q)
        }
    }
}
