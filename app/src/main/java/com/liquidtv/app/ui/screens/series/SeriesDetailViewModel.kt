package com.liquidtv.app.ui.screens.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.SeasonGroup
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.ui.utils.findContinueWatchingEpisode
import com.liquidtv.app.ui.utils.sortSeasons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailUiState(
    val series: SeriesItem? = null,
    val seasons: List<SeasonGroup> = emptyList(),
    val selectedSeason: Int = 1,
    val totalEpisodesCount: Int = 0,
    val continueWatchingEpisode: EpisodeItem? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val currentSeasonEpisodes: List<EpisodeItem>
        get() = seasons.firstOrNull { it.seasonNumber == selectedSeason }?.episodes
            ?: seasons.firstOrNull()?.episodes
            ?: emptyList()
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = savedStateHandle["seriesId"] ?: ""
    private val _selectedSeason = MutableStateFlow<Int?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SeriesDetailUiState> = combine(
        channelRepository.getSeriesById(seriesId),
        channelRepository.getEpisodesForSeries(seriesId),
        _selectedSeason,
        _isLoading,
        _error,
    ) { series, episodes, chosenSeason, loading, err ->
        val grouped = sortSeasons(
            episodes
                .groupBy { it.seasonNumber }
                .map { (season, eps) ->
                    SeasonGroup(
                        seasonNumber = season,
                        episodes = eps.sortedBy { it.episodeNumber },
                    )
                }
        )
        val continueEp = findContinueWatchingEpisode(episodes)
        val activeSeason = chosenSeason
            ?.takeIf { s -> grouped.any { it.seasonNumber == s } }
            ?: grouped.firstOrNull()?.seasonNumber
            ?: 1

        SeriesDetailUiState(
            series = series,
            seasons = grouped,
            selectedSeason = activeSeason,
            totalEpisodesCount = episodes.size,
            continueWatchingEpisode = continueEp,
            isLoading = loading && episodes.isEmpty(),
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SeriesDetailUiState(),
    )

    init {
        if (seriesId.isNotBlank()) {
            refreshDetails()
        } else {
            _isLoading.value = false
        }
    }

    fun refreshDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            channelRepository.loadSeriesDetails(seriesId)
                .onFailure { _error.value = it.message ?: "تعذّر تحميل الحلقات" }
            _isLoading.value = false
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            channelRepository.toggleSeriesFavorite(seriesId)
        }
    }
}
