package com.liquidtv.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Channel> = emptyList(),
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
        // Observe search history from preferences
        viewModelScope.launch {
            userPreferencesRepository.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }

        // Debounced search pipeline
        viewModelScope.launch {
            _queryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) {
                        _uiState.update { it.copy(isSearching = false, results = emptyList(), hasResults = false) }
                        flowOf(emptyList())
                    } else {
                        _uiState.update { it.copy(isSearching = true) }
                        channelRepository.searchChannels(q)
                    }
                }
                .map { results -> results }
                .catch { /* swallow errors silently; results remain empty */ }
                .collect { results ->
                    _uiState.update {
                        it.copy(
                            results = results,
                            isSearching = false,
                            hasResults = results.isNotEmpty(),
                        )
                    }
                }
        }
    }

    fun setQuery(q: String) {
        _uiState.update { it.copy(query = q) }
        _queryFlow.value = q

        // Persist non-blank queries to history after the user has settled
        if (q.isNotBlank()) {
            viewModelScope.launch {
                userPreferencesRepository.addSearchHistory(q)
            }
        }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "", results = emptyList(), hasResults = false, isSearching = false) }
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
