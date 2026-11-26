package com.liquidtv.app.ui.screens.livetv

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.ChannelFilter
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SortOrder
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ViewMode { GRID, LIST }

data class LiveTVUiState(
    val channels: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val totalChannelsCount: Int = 0,
    val favoritesCount: Int = 0,
    val selectedChannel: Channel? = null,
    val selectedEpg: ChannelEpgSummary = ChannelEpgSummary(),
    val filter: ChannelFilter = ChannelFilter(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTVViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    userPreferencesRepository: UserPreferencesRepository,
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

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    private val _selectedChannelId = MutableStateFlow<String?>(null)
    private val _shortEpgOverride = MutableStateFlow<Pair<String, ChannelEpgSummary>?>(null)

    private val baseStateFlow: Flow<LiveTVUiState> = combine(
        channelRepository.getAllChannels(),
        channelRepository.getAllCategories(MediaType.LIVE),
        _filter,
        _viewMode,
        combine(
            channelRepository.getLockedCategoryNames(),
            userPreferencesRepository.userPreferences,
            _selectedChannelId,
        ) { locked, prefs, selectedId -> Triple(locked, prefs, selectedId) },
    ) { allChannels, allCategories, filter, viewMode, (lockedCategories, prefs, selectedId) ->
        val enforceLock = prefs.parentalControlEnabled &&
            prefs.hasParentalPin &&
            lockedCategories.isNotEmpty()

        val protectAllMode = enforceLock && filter.category.isNullOrBlank()

        val unlockedChannels = if (protectAllMode) {
            allChannels.filter { ch ->
                val cat = ch.groupTitle?.trim()?.lowercase()
                cat == null || cat !in lockedCategories
            }
        } else {
            allChannels
        }

        val visibleCategories = if (enforceLock) {
            allCategories.filter { it.name.trim().lowercase() !in lockedCategories }
        } else {
            allCategories
        }

        val filtered = applyFilter(unlockedChannels, filter)
        val activeChannel = filtered.firstOrNull { it.id == selectedId } ?: filtered.firstOrNull()

        LiveTVUiState(
            channels = filtered,
            categories = visibleCategories,
            totalChannelsCount = unlockedChannels.size,
            favoritesCount = unlockedChannels.count { it.isFavorite },
            selectedChannel = activeChannel,
            filter = filter,
            isLoading = false,
            viewMode = viewMode,
        )
    }

    val uiState: StateFlow<LiveTVUiState> = baseStateFlow
        .flatMapLatest { base ->
            val selected = base.selectedChannel
            if (selected == null) {
                flowOf(base)
            } else {
                combine(
                    channelRepository.observeChannelEpg(selected),
                    _shortEpgOverride,
                ) { dbEpg, quick ->
                    val resolved = if (dbEpg.current != null || dbEpg.next != null) {
                        dbEpg
                    } else if (quick != null && quick.first == selected.id) {
                        quick.second
                    } else {
                        dbEpg
                    }
                    base.copy(selectedEpg = resolved)
                }
            }
        }
        .catch { throwable ->
            emit(
                LiveTVUiState(
                    filter = _filter.value,
                    isLoading = false,
                    error = throwable.message ?: "تعذّر تحميل القنوات",
                )
            )
        }
        .stateIn(
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

    private var lastFetchedEpgChannelId: String? = null

    init {
        viewModelScope.launch {
            uiState
                .mapNotNull { it.selectedChannel }
                .distinctUntilChangedBy { it.id }
                .collect { ch ->
                    if (lastFetchedEpgChannelId != ch.id) {
                        lastFetchedEpgChannelId = ch.id
                        runCatching {
                            val summary = channelRepository.fetchShortEpgIfNeeded(ch)
                            if (summary.current != null || summary.next != null) {
                                _shortEpgOverride.value = ch.id to summary
                            }
                        }
                    }
                }
        }
    }

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
            SortOrder.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
            SortOrder.RECENTLY_WATCHED -> result.sortedByDescending { it.lastWatchedAt ?: 0L }
            SortOrder.CATEGORY -> result.sortedBy { it.groupTitle?.lowercase() ?: "" }
            SortOrder.LANGUAGE -> result.sortedBy { it.language?.lowercase() ?: "" }
            SortOrder.COUNTRY -> result.sortedBy { it.country?.lowercase() ?: "" }
            SortOrder.FAVORITES_FIRST -> result.sortedByDescending { it.isFavorite }
        }

        return result
    }

    fun selectCategory(category: String?) {
        _filter.update { it.copy(category = category, showFavoritesOnly = false) }
    }

    fun selectFavoritesFilter() {
        _filter.update { it.copy(category = null, showFavoritesOnly = true) }
    }

    fun selectChannelForPreview(channel: Channel) {
        _selectedChannelId.value = channel.id
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

    fun refresh() {
        viewModelScope.launch {
            channelRepository.refreshPlaylists()
        }
    }
}
