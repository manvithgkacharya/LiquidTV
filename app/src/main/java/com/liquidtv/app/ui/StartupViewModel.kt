package com.liquidtv.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.SonixControlRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppStartupGate {
    data object Checking : AppStartupGate
    data object NeedsLogin : AppStartupGate
    data class Ready(val activeSubscription: Subscription) : AppStartupGate
}

data class RootUiState(
    val gate: AppStartupGate = AppStartupGate.Checking,
    val preferences: UserPreferences = UserPreferences(),
    val sonixState: SonixSyncState = SonixSyncState(),
)

@HiltViewModel
class StartupViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val sonixControlRepository: SonixControlRepository,
) : ViewModel() {

    private val _isSplashFinished = MutableStateFlow(false)
    val isSplashFinished: StateFlow<Boolean> = _isSplashFinished.asStateFlow()

    @Volatile
    var splashResumeProgress: Float = 0f
        private set

    val rootState: StateFlow<RootUiState> = combine(
        subscriptionRepository.allSubscriptions,
        subscriptionRepository.activeSubscription,
        userPreferencesRepository.userPreferences,
        sonixControlRepository.state,
    ) { allSubscriptions, activeSubscription, prefs, sonix ->
        val gate = when {
            allSubscriptions.isEmpty() || activeSubscription == null -> AppStartupGate.NeedsLogin
            else -> AppStartupGate.Ready(activeSubscription)
        }
        RootUiState(
            gate = gate,
            preferences = prefs,
            sonixState = sonix,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootUiState(),
    )

    fun updateSplashProgress(progress: Float) {
        if (!_isSplashFinished.value) {
            splashResumeProgress = progress.coerceIn(0f, 0.98f)
        }
    }

    fun markSplashFinished() {
        splashResumeProgress = 1f
        _isSplashFinished.value = true
    }

    fun refreshPanelStatus() {
        viewModelScope.launch {
            sonixControlRepository.syncNow(force = true)
        }
    }

    fun acknowledgeMessage(messageId: String) {
        viewModelScope.launch {
            sonixControlRepository.acknowledgeMessage(messageId)
        }
    }
}
