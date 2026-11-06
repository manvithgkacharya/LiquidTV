package com.liquidtv.app.ui.screens.livetv

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelFilter
import com.liquidtv.app.domain.model.SortOrder
import com.liquidtv.app.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ViewMode { GRID, LIST }

data class LiveTVUiState(
    val channels: List<Channel> = emptyList(),
    val filter: ChannelFilter = ChannelFilter(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.GRID,
)

@HiltViewModel
class LiveTVViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val filterCategory: String? = savedStateHandle["categoryName"] ?: savedStateHandle["filterCategory"]
    private val filterLanguage: String? = savedStateHandle["languageCode"] ?: savedStateHandle["filterLanguage"]
    private val filterCountry: String? = savedStateHandle["countryCode"] ?: savedStateHandle["filterCountry"]

    private val _filter = MutableStateFlow(
        ChannelFilter(
            category = filterCategory,
            language = filterLanguage,
            country = filterCountry,
        )
    )

    private val _viewMode = MutableStateFlow(ViewMode.GRID)

    val uiState: StateFlow<LiveTVUiState> = combine(
        channelRepository.getAllChannels(),
        _filter,
        _viewMode,
    ) { allChannels, filter, viewMode ->
        val filtered = applyFilter(allChannels, filter)
        LiveTVUiState(
            channels = filtered,
            filter = filter,
            isLoading = false,
            viewMode = viewMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LiveTVUiState(
            filter = ChannelFilter(
                category = filterCategory,
                language = filterLanguage,
                country = filterCountry,
            ),
            isLoading = true,
        ),
    )

    private fun applyFilter(channels: List<Channel>, filter: ChannelFilter): List<Channel> {
        var result = channels

        filter.category?.takeIf { it.isNotBlank() }?.let { cat ->
            val cleanCat = cat.trim().lowercase()
            result = result.filter { ch ->
                val chCat = ch.groupTitle?.trim()?.lowercase() ?: return@filter false
                chCat == cleanCat || chCat.contains(cleanCat) || cleanCat.contains(chCat)
            }
        }

        filter.language?.takeIf { it.isNotBlank() }?.let { lang ->
            val cleanLang = lang.trim().lowercase()
            result = result.filter { ch ->
                val chLang = ch.language?.trim()?.lowercase() ?: return@filter false
                chLang == cleanLang ||
                chLang.contains(cleanLang) ||
                cleanLang.contains(chLang) ||
                chLang.split(";", ",", "/", " ").any { it.trim() == cleanLang }
            }
        }

        filter.country?.takeIf { it.isNotBlank() }?.let { country ->
            val cleanCountry = country.trim().lowercase()
            result = result.filter { ch ->
                val chCountry = ch.country?.trim()?.lowercase() ?: return@filter false
                chCountry == cleanCountry ||
                chCountry.contains(cleanCountry) ||
                cleanCountry.contains(chCountry)
            }
        }

        if (filter.showFavoritesOnly) {
            result = result.filter { it.isFavorite }
        }

        result = when (filter.sortOrder) {
            SortOrder.NAME_ASC -> result.sortedBy { it.name }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.name }
            SortOrder.RECENTLY_WATCHED -> result.sortedByDescending { it.lastWatchedAt ?: 0L }
            SortOrder.CATEGORY -> result.sortedBy { it.groupTitle ?: "" }
            SortOrder.LANGUAGE -> result.sortedBy { it.language ?: "" }
            SortOrder.COUNTRY -> result.sortedBy { it.country ?: "" }
            SortOrder.FAVORITES_FIRST -> result.sortedByDescending { it.isFavorite }
        }

        return result
    }

    fun setFilter(filter: ChannelFilter) {
        _filter.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _filter.update { it.copy(sortOrder = order) }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(channelId)
        }
    }
}
