package com.liquidtv.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.repository.SonixControlRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionsUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val activeSubscription: Subscription? = null,
    val syncInfo: PlaylistInfo = PlaylistInfo(),
    val sonixState: SonixSyncState = SonixSyncState(),
    val busySubscriptionId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val sonixControlRepository: SonixControlRepository? = null,
) : ViewModel() {

    private val _busyId = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private val sonixFlow = sonixControlRepository?.state ?: MutableStateFlow(SonixSyncState())

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        combine(
            subscriptionRepository.allSubscriptions,
            subscriptionRepository.activeSubscription,
            subscriptionRepository.syncInfo,
        ) { all, active, sync -> Triple(all, active, sync) },
        combine(_busyId, _message, _error) { busyId, msg, err -> Triple(busyId, msg, err) },
        sonixFlow,
    ) { (all, active, sync), (busyId, msg, err), sonix ->
        SubscriptionsUiState(
            subscriptions = all,
            activeSubscription = active,
            syncInfo = sync,
            sonixState = sonix,
            busySubscriptionId = busyId,
            message = msg,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionsUiState(),
    )

    fun refreshPanel() {
        val repo = sonixControlRepository ?: return
        viewModelScope.launch {
            _error.value = null
            repo.syncNow(force = true)
                .onSuccess { _message.value = "تم تحديث حالة الجهاز والسيرفرات بنجاح" }
                .onFailure { _error.value = it.message ?: "تعذّرت مزامنة السيرفرات، يرجى المحاولة لاحقاً" }
        }
    }

    fun activate(subscriptionId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busyId.value = subscriptionId
            _error.value = null
            subscriptionRepository.activateSubscription(subscriptionId)
                .onSuccess {
                    _message.value = "تم تفعيل قائمة التشغيل المحددة"
                    onSuccess()
                }
                .onFailure { _error.value = it.message ?: "تعذّر تفعيل قائمة التشغيل" }
            _busyId.value = null
        }
    }

    fun sync(subscriptionId: String) {
        viewModelScope.launch {
            _busyId.value = subscriptionId
            _error.value = null
            subscriptionRepository.syncSubscription(subscriptionId)
                .onSuccess { _message.value = "تمت مزامنة قائمة التشغيل بنجاح" }
                .onFailure { _error.value = it.message ?: "فشلت المزامنة" }
            _busyId.value = null
        }
    }

    fun delete(subscriptionId: String) {
        viewModelScope.launch {
            _busyId.value = subscriptionId
            _error.value = null
            subscriptionRepository.deleteSubscription(subscriptionId)
                .onSuccess { _message.value = "تم حذف قائمة التشغيل" }
                .onFailure { _error.value = it.message ?: "فشل حذف القائمة" }
            _busyId.value = null
        }
    }

    fun clearFeedback() {
        _message.value = null
        _error.value = null
    }
}
