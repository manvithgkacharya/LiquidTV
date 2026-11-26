package com.liquidtv.app.ui.screens.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Language
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

data class LanguagesUiState(
    val languages: List<Language> = emptyList(),
    val isLoading: Boolean = true,
    val loadingMessage: String? = null,
)

@HiltViewModel
class LanguagesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguagesUiState())
    val uiState: StateFlow<LanguagesUiState> = _uiState.asStateFlow()

    init {
        loadLanguages()
    }

    private fun loadLanguages() {
        viewModelScope.launch {
            combine(
                channelRepository.getAllLanguages(),
                channelRepository.getPlaylistInfo(),
            ) { languages, playlistInfo ->
                val message = if (playlistInfo.isLoading) {
                    playlistInfo.statusMessage
                        ?: "جارٍ مزامنة اللغات (${(playlistInfo.loadingProgress * 100).toInt()}%)..."
                } else null
                LanguagesUiState(
                    languages = languages,
                    isLoading = playlistInfo.isLoading,
                    loadingMessage = message,
                )
            }.catch {
                _uiState.update { it.copy(isLoading = false) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            channelRepository.refreshPlaylists()
        }
    }
}
