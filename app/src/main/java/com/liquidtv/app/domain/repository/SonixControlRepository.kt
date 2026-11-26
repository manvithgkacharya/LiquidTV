package com.liquidtv.app.domain.repository

import com.liquidtv.app.data.sonix.SonixAssignedServer
import com.liquidtv.app.data.sonix.SonixReleaseUpdate
import com.liquidtv.app.data.sonix.SonixResolvedServer
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.SonixAudioSourceConfig
import kotlinx.coroutines.flow.StateFlow

interface SonixControlRepository {
    val state: StateFlow<SonixSyncState>

    /**
     * Triggered strictly on app launch and returning to foreground (never a 5-minute polling loop).
     */
    suspend fun onAppForeground()

    suspend fun syncNow(force: Boolean = false): Result<SonixSyncState>

    suspend fun refreshAssignedServers(): Result<List<SonixAssignedServer>>

    suspend fun refreshAvailableServers(): Result<List<SonixAssignedServer>>

    suspend fun refreshAvailableServers(forceSync: Boolean): Result<List<SonixAssignedServer>> =
        refreshAvailableServers()

    suspend fun resolveAssignedServer(serverId: String): Result<SonixResolvedServer>

    suspend fun acknowledgeMessage(messageId: String): Result<Unit>

    suspend fun checkForPanelUpdate(): Result<SonixReleaseUpdate?>
    suspend fun fetchAudioSources(): Result<SonixAudioSourceConfig> =
        Result.success(state.value.audioSource)
}
