package com.liquidtv.app.ui.screens.countries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Country
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

data class CountriesUiState(
    val countries: List<Country> = emptyList(),
    val isLoading: Boolean = true,
    val loadingMessage: String? = null,
)

@HiltViewModel
class CountriesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CountriesUiState())
    val uiState: StateFlow<CountriesUiState> = _uiState.asStateFlow()

    init {
        loadCountries()
    }

    private fun loadCountries() {
        viewModelScope.launch {
            combine(
                channelRepository.getAllCountries(),
                channelRepository.getPlaylistInfo(),
            ) { countries, playlistInfo ->
                val isLoading = playlistInfo.isLoading || (countries.isEmpty() && playlistInfo.lastRefreshed == 0L)
                val message = if (playlistInfo.isLoading) {
                    "Loading countries & streams (${(playlistInfo.loadingProgress * 100).toInt()}%)..."
                } else null
                CountriesUiState(
                    countries = countries,
                    isLoading = isLoading,
                    loadingMessage = message,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false) }
            }.collect { state ->
                _uiState.value = state
                if (state.countries.isEmpty() && !state.isLoading) {
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
