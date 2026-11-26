package com.liquidtv.app

import com.liquidtv.app.data.sonix.*
import com.liquidtv.app.domain.model.SonixAudioSourceConfig
import com.liquidtv.app.domain.repository.SonixControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSonixControlRepository(
    initialState: SonixSyncState = SonixSyncState(
        isRegistered = true,
        deviceId = "dev-1",
        deviceUid = "ltv_test_uid",
        deviceCode = "SNX1234567",
        deviceStatus = SonixDeviceStatus.ACTIVE,
        accessStatus = SonixAccessStatus.ACTIVE,
        accessAllowed = true,
    ),
) : SonixControlRepository {

    val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<SonixSyncState> = mutableState.asStateFlow()

    var foregroundSyncCalls = 0
    var refreshCatalogCalls = 0
    var refreshCatalogHandler: (suspend (Boolean) -> Result<List<SonixAssignedServer>>)? = null
    var resolveHandler: suspend (String) -> Result<SonixResolvedServer> = { id ->
        Result.failure(IllegalStateException("server_not_assigned: $id"))
    }
    var panelUpdateHandler: suspend () -> Result<SonixReleaseUpdate?> = {
        Result.success(null)
    }
    var audioSourcesHandler: suspend () -> Result<SonixAudioSourceConfig> = {
        Result.success(mutableState.value.audioSource)
    }
    val acknowledgedMessageIds = mutableListOf<String>()

    override suspend fun onAppForeground() {
        foregroundSyncCalls++
    }

    override suspend fun syncNow(force: Boolean): Result<SonixSyncState> = Result.success(mutableState.value)

    override suspend fun refreshAssignedServers(): Result<List<SonixAssignedServer>> =
        Result.success(mutableState.value.assignedServers)

    override suspend fun refreshAvailableServers(): Result<List<SonixAssignedServer>> =
        refreshAvailableServers(forceSync = false)

    override suspend fun refreshAvailableServers(forceSync: Boolean): Result<List<SonixAssignedServer>> {
        refreshCatalogCalls++
        refreshCatalogHandler?.let { return it(forceSync) }
        return Result.success(mutableState.value.availableServers)
    }

    override suspend fun resolveAssignedServer(serverId: String): Result<SonixResolvedServer> =
        resolveHandler(serverId)

    override suspend fun acknowledgeMessage(messageId: String): Result<Unit> {
        acknowledgedMessageIds += messageId
        mutableState.value = mutableState.value.copy(
            pendingMessages = mutableState.value.pendingMessages.filterNot { it.id == messageId },
        )
        return Result.success(Unit)
    }

    override suspend fun checkForPanelUpdate(): Result<SonixReleaseUpdate?> = panelUpdateHandler()

    override suspend fun fetchAudioSources(): Result<SonixAudioSourceConfig> =
        audioSourcesHandler()
}
