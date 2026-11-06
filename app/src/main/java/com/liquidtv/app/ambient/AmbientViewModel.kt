package com.liquidtv.app.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Channel
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

data class AmbientUiState(
    val theme: String = "dark",
    val clockVisible: Boolean = true,
    val favoriteChannels: List<Channel> = emptyList(),
)

@HiltViewModel
class AmbientViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AmbientUiState())
    val uiState: StateFlow<AmbientUiState> = _uiState.asStateFlow()

    init {
        loadAmbientData()
    }

    private fun loadAmbientData() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.userPreferences,
                channelRepository.getFavoriteChannels(),
            ) { prefs, favorites ->
                AmbientUiState(
                    theme = prefs.theme,
                    clockVisible = prefs.clockVisible,
                    favoriteChannels = favorites,
                )
            }.catch { throwable ->
                // Keep current state on error; ambient mode should fail silently
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
