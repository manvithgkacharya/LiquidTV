package com.liquidtv.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.repository.SonixControlRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginSection {
    SERVER_SELECTION,
    PLAYLISTS,
}

data class LoginUiState(
    val activeSection: LoginSection = LoginSection.SERVER_SELECTION,
    val isServerSelectorExpanded: Boolean = false,
    val isServerFormDialogOpen: Boolean = false,
    val editingSubscriptionId: String? = null,
    val editingOrigin: SubscriptionOrigin? = null,
    val serverMode: SubscriptionOrigin = SubscriptionOrigin.NORMAL_URL,
    val isNormalUrlSelected: Boolean = false,
    val selectedPanelServerId: String? = null,
    val selectedPanelServerName: String? = null,
    val displayName: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val isConnecting: Boolean = false,
    val busyPlaylistId: String? = null,
    val progress: Float = 0f,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val playlistMessage: String? = null,
    val isSuccess: Boolean = false,
    val savedPlaylists: List<Subscription> = emptyList(),
    val sonixState: SonixSyncState = SonixSyncState(),
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val sonixControlRepository: SonixControlRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var connectJob: Job? = null

    init {
        // Always register/sync this installation and fetch the active server catalog on app startup.
        viewModelScope.launch { sonixControlRepository.refreshAvailableServers(forceSync = true) }
        viewModelScope.launch {
            combine(
                subscriptionRepository.allSubscriptions,
                sonixControlRepository.state,
                subscriptionRepository.syncInfo,
            ) { playlists, sonix, sync ->
                Triple(playlists, sonix, sync)
            }.collect { (playlists, sonix, sync) ->
                _uiState.update { current ->
                    val effectiveServers = if (sonix.hasLoadedCatalog) {
                        sonix.availableServers
                    } else {
                        sonix.availableServers.ifEmpty { sonix.assignedServers }
                    }
                    val panelUnavailable = !sonix.catalogError.isNullOrBlank() || effectiveServers.isEmpty()

                    // Auto-respect admin policy when panel is healthy, never blocking Normal URL if panel fails/empty.
                    val resolvedMode = when {
                        current.editingOrigin == SubscriptionOrigin.PANEL_SERVER -> SubscriptionOrigin.PANEL_SERVER
                        current.serverMode == SubscriptionOrigin.NORMAL_URL &&
                            !sonix.settings.allowNormalUrlLogin &&
                            !panelUnavailable &&
                            sonix.settings.allowPanelServers -> SubscriptionOrigin.PANEL_SERVER
                        current.serverMode == SubscriptionOrigin.PANEL_SERVER &&
                            !sonix.settings.allowPanelServers &&
                            sonix.settings.allowNormalUrlLogin -> SubscriptionOrigin.NORMAL_URL
                        else -> current.serverMode
                    }

                    val availableMatch = current.selectedPanelServerId?.let { selId ->
                        effectiveServers.firstOrNull { it.id == selId }
                    }
                    val serverRemovedFromCatalog = sonix.hasLoadedCatalog &&
                        current.selectedPanelServerId != null &&
                        availableMatch == null &&
                        current.editingOrigin != SubscriptionOrigin.PANEL_SERVER

                    current.copy(
                        savedPlaylists = playlists,
                        sonixState = sonix,
                        serverMode = resolvedMode,
                        selectedPanelServerId = if (serverRemovedFromCatalog) null else (availableMatch?.id ?: current.selectedPanelServerId),
                        selectedPanelServerName = if (serverRemovedFromCatalog) null else (availableMatch?.name ?: current.selectedPanelServerName),
                        isServerFormDialogOpen = if (serverRemovedFromCatalog && !current.isNormalUrlSelected) false else current.isServerFormDialogOpen,
                        progress = if (current.isConnecting) sync.loadingProgress else current.progress,
                        statusMessage = if (current.isConnecting) (sync.statusMessage ?: current.statusMessage) else current.statusMessage,
                    )
                }
            }
        }
    }

    fun selectSection(section: LoginSection) {
        if (_uiState.value.isConnecting) return
        _uiState.update { it.copy(activeSection = section, errorMessage = null, playlistMessage = null) }
    }

    fun toggleServerSelector() {
        val current = _uiState.value
        if (current.isConnecting) return
        val nextExpanded = !current.isServerSelectorExpanded
        _uiState.update { state ->
            state.copy(
                isServerSelectorExpanded = nextExpanded,
                errorMessage = null,
            )
        }
        if (nextExpanded && !_uiState.value.sonixState.isCatalogLoading) {
            viewModelScope.launch {
                sonixControlRepository.refreshAvailableServers(forceSync = false)
            }
        }
    }

    fun collapseServerSelector() {
        if (_uiState.value.isConnecting) return
        _uiState.update { state ->
            state.copy(
                isServerSelectorExpanded = false,
                errorMessage = null,
            )
        }
    }

    fun dismissServerFormDialog() {
        val current = _uiState.value
        if (current.isConnecting) return
        if (current.editingSubscriptionId != null) {
            clearEditMode()
            return
        }
        _uiState.update { state ->
            state.copy(
                isServerFormDialogOpen = false,
                isNormalUrlSelected = false,
                selectedPanelServerId = null,
                selectedPanelServerName = null,
                errorMessage = null,
            )
        }
    }

    fun switchServerFromDialog() {
        val current = _uiState.value
        if (current.isConnecting || current.editingOrigin == SubscriptionOrigin.PANEL_SERVER) return
        _uiState.update { state ->
            state.copy(
                isServerFormDialogOpen = false,
                isServerSelectorExpanded = true,
                errorMessage = null,
            )
        }
        if (!_uiState.value.sonixState.isCatalogLoading) {
            viewModelScope.launch {
                sonixControlRepository.refreshAvailableServers(forceSync = false)
            }
        }
    }

    fun toggleNormalUrlSection() {
        val current = _uiState.value
        if (current.isConnecting) return
        if (current.editingOrigin == SubscriptionOrigin.PANEL_SERVER) {
            // Never expose/convert a panel-managed playlist to normal Host URL edit
            return
        }
        _uiState.update { state ->
            val shouldClose = state.isNormalUrlSelected && state.isServerFormDialogOpen
            val nextOpen = !shouldClose
            state.copy(
                isServerSelectorExpanded = false,
                isServerFormDialogOpen = nextOpen,
                serverMode = if (nextOpen) SubscriptionOrigin.NORMAL_URL else state.serverMode,
                isNormalUrlSelected = nextOpen,
                selectedPanelServerId = if (nextOpen) null else state.selectedPanelServerId,
                selectedPanelServerName = if (nextOpen) null else state.selectedPanelServerName,
                errorMessage = null,
            )
        }
    }

    fun selectServerMode(mode: SubscriptionOrigin) {
        val current = _uiState.value
        if (current.isConnecting) return
        if (current.editingOrigin == SubscriptionOrigin.PANEL_SERVER && mode == SubscriptionOrigin.NORMAL_URL) {
            // Never expose/convert a panel-managed playlist to normal Host URL edit
            return
        }
        _uiState.update { state ->
            state.copy(
                isServerSelectorExpanded = false,
                isServerFormDialogOpen = mode == SubscriptionOrigin.NORMAL_URL,
                serverMode = mode,
                isNormalUrlSelected = mode == SubscriptionOrigin.NORMAL_URL,
                selectedPanelServerId = if (mode == SubscriptionOrigin.NORMAL_URL) null else state.selectedPanelServerId,
                selectedPanelServerName = if (mode == SubscriptionOrigin.NORMAL_URL) null else state.selectedPanelServerName,
                errorMessage = null,
            )
        }
    }

    fun selectPanelServer(serverId: String, serverName: String) {
        val current = _uiState.value
        if (current.isConnecting) return
        val isSameServerActive = !current.isNormalUrlSelected &&
            current.selectedPanelServerId == serverId &&
            current.isServerFormDialogOpen
        if (isSameServerActive && current.editingOrigin != SubscriptionOrigin.PANEL_SERVER) {
            _uiState.update {
                it.copy(
                    isServerSelectorExpanded = false,
                    isServerFormDialogOpen = false,
                    selectedPanelServerId = null,
                    selectedPanelServerName = null,
                    errorMessage = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isServerSelectorExpanded = false,
                isServerFormDialogOpen = true,
                serverMode = SubscriptionOrigin.PANEL_SERVER,
                isNormalUrlSelected = false,
                selectedPanelServerId = serverId,
                selectedPanelServerName = serverName,
                errorMessage = null,
            )
        }
    }

    fun refreshPanelServers() {
        val sonix = _uiState.value.sonixState
        if (sonix.isSyncing || sonix.isCatalogLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = sonixControlRepository.refreshAvailableServers(forceSync = true)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "تعذّر تحديث قائمة السيرفرات، يرجى المحاولة لاحقاً")
                }
            }
        }
    }

    fun notifyDeviceIdCopied(deviceCode: String) {
        _uiState.update {
            it.copy(
                playlistMessage = "تم نسخ معرّف الجهاز: $deviceCode",
                errorMessage = null,
            )
        }
    }

    fun loadForEdit(subscriptionId: String?) {
        if (subscriptionId.isNullOrBlank()) return
        if (_uiState.value.editingSubscriptionId == subscriptionId) return
        viewModelScope.launch {
            val sub = subscriptionRepository.getSubscriptionById(subscriptionId) ?: return@launch
            loadSubscriptionIntoEditor(sub)
        }
    }

    fun startEditingPlaylist(sub: Subscription) {
        viewModelScope.launch {
            loadSubscriptionIntoEditor(sub)
        }
    }

    fun clearEditMode() {
        _uiState.update {
            it.copy(
                isServerSelectorExpanded = false,
                isServerFormDialogOpen = false,
                editingSubscriptionId = null,
                editingOrigin = null,
                isNormalUrlSelected = false,
                displayName = "",
                serverUrl = "",
                username = "",
                password = "",
                selectedPanelServerId = null,
                selectedPanelServerName = null,
                errorMessage = null,
            )
        }
    }

    private suspend fun loadSubscriptionIntoEditor(sub: Subscription) {
        val existingPass = subscriptionRepository.getDecryptedXtreamPassword(sub.id).orEmpty()
        _uiState.update {
            it.copy(
                activeSection = LoginSection.SERVER_SELECTION,
                isServerSelectorExpanded = false,
                isServerFormDialogOpen = true,
                editingSubscriptionId = sub.id,
                editingOrigin = sub.origin,
                serverMode = sub.origin,
                isNormalUrlSelected = sub.origin == SubscriptionOrigin.NORMAL_URL,
                selectedPanelServerId = sub.panelServerId,
                selectedPanelServerName = sub.panelServerName,
                displayName = sub.name,
                // Security: NEVER copy raw Host URL into form state for PANEL_SERVER playlists
                serverUrl = if (sub.origin == SubscriptionOrigin.NORMAL_URL) sub.serverUrl else "",
                username = sub.username.orEmpty(),
                password = existingPass,
                errorMessage = null,
                playlistMessage = null,
            )
        }
    }

    fun setDisplayName(value: String) = _uiState.update { it.copy(displayName = value, errorMessage = null) }
    fun setServerUrl(value: String) = _uiState.update { it.copy(serverUrl = value, errorMessage = null) }
    fun setUsername(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null, playlistMessage = null) }

    fun connect() {
        val current = _uiState.value
        if (current.isConnecting) return
        val validationError = validateInputs(current)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConnecting = true,
                    progress = 0.05f,
                    statusMessage = "جارٍ التحقق والاتصال بالسيرفر...",
                    errorMessage = null,
                    isSuccess = false,
                )
            }

            val result = when (current.serverMode) {
                SubscriptionOrigin.NORMAL_URL -> {
                    subscriptionRepository.addOrUpdateXtreamSubscription(
                        existingId = current.editingSubscriptionId,
                        displayName = current.displayName,
                        serverUrl = current.serverUrl,
                        username = current.username,
                        password = current.password,
                        origin = SubscriptionOrigin.NORMAL_URL,
                        panelServerId = null,
                        panelServerName = null,
                        activate = true,
                    )
                }

                SubscriptionOrigin.PANEL_SERVER -> {
                    val serverId = current.selectedPanelServerId.orEmpty()
                    val resolvedRes = sonixControlRepository.resolveAssignedServer(serverId)
                    if (resolvedRes.isFailure) {
                        val err = resolvedRes.exceptionOrNull()
                            ?: IllegalStateException("تعذّر التحقق من السيرفر المحدد")
                        Result.failure(err)
                    } else {
                        val resolved = resolvedRes.getOrThrow()
                        subscriptionRepository.addOrUpdateXtreamSubscription(
                            existingId = current.editingSubscriptionId,
                            displayName = current.displayName,
                            serverUrl = resolved.serverUrl,
                            username = current.username,
                            password = current.password,
                            origin = SubscriptionOrigin.PANEL_SERVER,
                            panelServerId = resolved.id,
                            panelServerName = resolved.name,
                            activate = true,
                        )
                    }
                }
            }

            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        progress = 1f,
                        statusMessage = "تم الاتصال بنجاح",
                        errorMessage = null,
                        isSuccess = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        progress = 0f,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "تعذّر الاتصال. تأكد من صحة البيانات وحاول مجدداً.",
                        isSuccess = false,
                    )
                }
            }
        }
    }

    fun activatePlaylist(subscriptionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyPlaylistId = subscriptionId, errorMessage = null, playlistMessage = null) }
            subscriptionRepository.activateSubscription(subscriptionId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            busyPlaylistId = null,
                            playlistMessage = "تم تفعيل قائمة التشغيل",
                            isSuccess = true,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            busyPlaylistId = null,
                            errorMessage = err.message ?: "تعذّر تفعيل القائمة",
                        )
                    }
                }
        }
    }

    fun syncPlaylist(subscriptionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyPlaylistId = subscriptionId, errorMessage = null, playlistMessage = null) }
            subscriptionRepository.syncSubscription(subscriptionId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            busyPlaylistId = null,
                            playlistMessage = "تمت مزامنة قائمة التشغيل بنجاح",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            busyPlaylistId = null,
                            errorMessage = err.message ?: "فشلت المزامنة",
                        )
                    }
                }
        }
    }

    fun deletePlaylist(subscriptionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyPlaylistId = subscriptionId, errorMessage = null, playlistMessage = null) }
            subscriptionRepository.deleteSubscription(subscriptionId)
                .onSuccess {
                    _uiState.update { current ->
                        val resetEdit = current.editingSubscriptionId == subscriptionId
                        current.copy(
                            busyPlaylistId = null,
                            isServerFormDialogOpen = if (resetEdit) false else current.isServerFormDialogOpen,
                            editingSubscriptionId = if (resetEdit) null else current.editingSubscriptionId,
                            editingOrigin = if (resetEdit) null else current.editingOrigin,
                            playlistMessage = "تم حذف قائمة التشغيل",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            busyPlaylistId = null,
                            errorMessage = err.message ?: "فشل حذف القائمة",
                        )
                    }
                }
        }
    }

    private fun validateInputs(state: LoginUiState): String? {
        val effectiveServers = if (state.sonixState.hasLoadedCatalog) {
            state.sonixState.availableServers
        } else {
            state.sonixState.availableServers.ifEmpty { state.sonixState.assignedServers }
        }
        val panelUnavailable = !state.sonixState.catalogError.isNullOrBlank() || effectiveServers.isEmpty()

        return when (state.serverMode) {
            SubscriptionOrigin.NORMAL_URL -> {
                if (!state.sonixState.settings.allowNormalUrlLogin &&
                    !panelUnavailable &&
                    state.editingOrigin != SubscriptionOrigin.NORMAL_URL
                ) {
                    return "تم تعطيل إضافة روابط URL عادية مؤقتاً."
                }
                val url = state.serverUrl.trim()
                when {
                    state.displayName.isBlank() -> "يرجى إدخال اسم قائمة التشغيل."
                    state.username.isBlank() -> "يرجى إدخال اسم المستخدم."
                    state.password.isBlank() -> "يرجى إدخال كلمة المرور."
                    url.isEmpty() -> "يرجى إدخال Host URL (يبدأ بـ http:// أو https://)."
                    !url.startsWith("http://", true) && !url.startsWith("https://", true) ->
                        "يجب أن يبدأ Host URL بـ http:// أو https://"
                    else -> null
                }
            }

            SubscriptionOrigin.PANEL_SERVER -> {
                if (!state.sonixState.settings.allowPanelServers &&
                    state.editingOrigin != SubscriptionOrigin.PANEL_SERVER
                ) {
                    return "تم تعطيل السيرفرات المخصصة مؤقتاً."
                }
                when {
                    state.sonixState.isBlockedOrRevoked -> "تم إيقاف هذا الجهاز حالياً. لا يمكن متابعة الاتصال."
                    state.sonixState.isPanelExpired -> "انتهت صلاحية اشتراك هذا الجهاز."
                    state.selectedPanelServerId.isNullOrBlank() -> "يرجى اختيار سيرفر من القائمة أولاً."
                    state.displayName.isBlank() -> "يرجى إدخال اسم قائمة التشغيل."
                    state.username.isBlank() -> "يرجى إدخال اسم المستخدم."
                    state.password.isBlank() -> "يرجى إدخال كلمة المرور."
                    else -> null
                }
            }
        }
    }
}
