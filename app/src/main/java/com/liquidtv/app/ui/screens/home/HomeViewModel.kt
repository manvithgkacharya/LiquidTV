package com.liquidtv.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeSubscription: Subscription? = null,
    val featuredChannel: Channel? = null,
    val featuredEpg: ChannelEpgSummary = ChannelEpgSummary(),
    val allChannels: List<Channel> = emptyList(),
    val featuredMovies: List<Channel> = emptyList(),
    val featuredSeries: List<SeriesItem> = emptyList(),
    val recentlyWatched: List<Channel> = emptyList(),
    val favoriteChannels: List<Channel> = emptyList(),
    val popularCategories: List<Category> = emptyList(),
    val clockVisible: Boolean = true,
    val isLoading: Boolean = true,
    val loadingProgress: Float = 0f,
    val loadingMessage: String? = null,
    val playlistInfo: PlaylistInfo? = null,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val uiState: StateFlow<HomeUiState> = state

    private val _manualError = MutableStateFlow<String?>(null)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val baseFlow = combine(
                subscriptionRepository.activeSubscription,
                channelRepository.getAllChannels(),
                channelRepository.getMovies(),
                channelRepository.getSeries(),
                channelRepository.getRecentlyWatched(),
                channelRepository.getFavoriteChannels(),
                channelRepository.getAllCategories(MediaType.LIVE),
                channelRepository.getPlaylistInfo(),
                userPreferencesRepository.userPreferences,
                channelRepository.getLockedCategoryNames(),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val subscription = values[0] as Subscription?
                @Suppress("UNCHECKED_CAST")
                val allChannels = values[1] as List<Channel>
                @Suppress("UNCHECKED_CAST")
                val movies = values[2] as List<Channel>
                @Suppress("UNCHECKED_CAST")
                val series = values[3] as List<SeriesItem>
                @Suppress("UNCHECKED_CAST")
                val recentlyWatched = values[4] as List<Channel>
                @Suppress("UNCHECKED_CAST")
                val favorites = values[5] as List<Channel>
                @Suppress("UNCHECKED_CAST")
                val categories = values[6] as List<Category>
                val playlistInfo = values[7] as PlaylistInfo
                val prefs = values[8] as com.liquidtv.app.domain.model.UserPreferences
                @Suppress("UNCHECKED_CAST")
                val lockedCats = values[9] as Set<String>

                val visibleChannels = if (prefs.parentalControlEnabled && lockedCats.isNotEmpty()) {
                    allChannels.filter { ch ->
                        val cat = ch.groupTitle?.trim()?.lowercase()
                        cat == null || cat !in lockedCats
                    }
                } else {
                    allChannels
                }

                val featured = visibleChannels.firstOrNull { !it.logoUrl.isNullOrBlank() } ?: visibleChannels.firstOrNull()
                HomeUiState(
                    activeSubscription = subscription,
                    featuredChannel = featured,
                    allChannels = visibleChannels.take(24),
                    featuredMovies = movies.take(15),
                    featuredSeries = series.take(15),
                    recentlyWatched = recentlyWatched,
                    favoriteChannels = favorites,
                    popularCategories = categories.take(12),
                    clockVisible = prefs.clockVisible,
                    isLoading = playlistInfo.isLoading,
                    loadingProgress = playlistInfo.loadingProgress,
                    loadingMessage = playlistInfo.statusMessage ?: if (playlistInfo.isLoading) "جارٍ مزامنة الاشتراك..." else null,
                    playlistInfo = playlistInfo,
                    error = _manualError.value ?: playlistInfo.error ?: subscription?.lastError,
                )
            }

            baseFlow
                .flatMapLatest { state ->
                    val featured = state.featuredChannel
                    if (featured == null) {
                        flowOf(state)
                    } else {
                        channelRepository.observeChannelEpg(featured).combine(flowOf(state)) { epg, s ->
                            s.copy(featuredEpg = epg)
                        }
                    }
                }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message ?: "حدث خطأ غير متوقع")
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(channelId)
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _manualError.value = null
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = channelRepository.refreshPlaylists()
            result.onFailure { throwable ->
                val message = throwable.message ?: "فشل تحديث الاشتراك"
                _manualError.value = message
                _uiState.update {
                    it.copy(isLoading = false, error = message)
                }
            }
        }
    }

    fun dismissError() {
        _manualError.value = null
        _uiState.update { it.copy(error = null) }
    }
}
