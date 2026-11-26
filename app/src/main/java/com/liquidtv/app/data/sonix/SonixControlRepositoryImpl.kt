package com.liquidtv.app.data.sonix

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.domain.model.SonixAudioSourceConfig
import com.liquidtv.app.domain.repository.SonixControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SonixControlRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SonixControlApi,
    private val identityStore: SonixDeviceIdentityStore,
) : SonixControlRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    // Serialize token recovery and persistence only; ordinary API requests stay concurrent.
    private val deviceAuthMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SonixSyncState())
    override val state: StateFlow<SonixSyncState> = _state.asStateFlow()

    @Volatile
    private var lastForegroundTriggerMs: Long = 0L

    init {
        scope.launch {
            val uid = identityStore.getOrCreateDeviceUid()
            val cached = identityStore.loadCachedSnapshot()
            _state.update { current ->
                if (cached != null) {
                    current.copy(
                        isRegistered = !cached.deviceCode.isNullOrBlank() || !cached.deviceId.isNullOrBlank(),
                        deviceId = cached.deviceId,
                        deviceUid = uid,
                        deviceCode = cached.deviceCode,
                        deviceStatus = SonixDeviceStatus.fromApi(cached.deviceStatus),
                        accessStatus = SonixAccessStatus.fromApi(cached.accessStatus),
                        accessAllowed = cached.accessAllowed,
                        subscription = cached.subscription,
                        settings = cached.settings,
                        assignedServers = cached.assignedServers,
                        availableServers = cached.availableServers,
                        pendingMessages = cached.pendingMessages,
                        audioSource = cached.audioSource,
                        lastSyncedAt = cached.lastSyncedAt,
                    )
                } else {
                    current.copy(deviceUid = uid)
                }
            }
        }
    }

    override suspend fun onAppForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundTriggerMs < FOREGROUND_DEBOUNCE_MS) return
        lastForegroundTriggerMs = now
        syncNow(force = false)
    }

    override suspend fun syncNow(force: Boolean): Result<SonixSyncState> = syncMutex.withLock {
        val currentBeforeSync = _state.value
        val lastSynced = currentBeforeSync.lastSyncedAt
        if (!force &&
            currentBeforeSync.isRegistered &&
            lastSynced != null &&
            (System.currentTimeMillis() - lastSynced) < FOREGROUND_DEBOUNCE_MS
        ) {
            return@withLock Result.success(currentBeforeSync)
        }
        _state.update { it.copy(isSyncing = true, lastError = null) }

        val uid = identityStore.getOrCreateDeviceUid()
        // Publish the stable local installation ID before any network request so the
        // login screen never waits for panel registration to display an identifier.
        _state.update { it.copy(deviceUid = uid) }
        val metadataPayload = buildMetadataPayload(uid)
        val result = executeWithTokenRecovery("sync", metadataPayload)
        lastForegroundTriggerMs = System.currentTimeMillis()

        when (result) {
            is SonixApiResult.Success -> {
                val updatedState = applySyncPayload(uid, result.data)
                Result.success(updatedState)
            }
            is SonixApiResult.Forbidden -> {
                val errState = applyForbiddenStatus(uid, result.errorCode)
                _state.value = errState
                Result.failure(IllegalStateException(localizeApiError(result.errorCode)))
            }
            is SonixApiResult.Unauthorized -> {
                val msg = localizeApiError(result.errorCode)
                _state.update { it.copy(isSyncing = false, deviceUid = uid, lastError = msg) }
                Result.failure(IllegalStateException(msg))
            }
            is SonixApiResult.Failure -> {
                val msg = localizeApiError(result.message)
                _state.update { it.copy(isSyncing = false, deviceUid = uid, lastError = msg) }
                Result.failure(IllegalStateException(msg))
            }
        }
    }

    override suspend fun refreshAssignedServers(): Result<List<SonixAssignedServer>> {
        val syncRes = syncNow(force = true)
        val current = _state.value
        return when {
            current.isBlockedOrRevoked -> Result.failure(IllegalStateException("تم إيقاف هذا الجهاز من قبل مزود الخدمة"))
            current.isPanelExpired -> Result.failure(IllegalStateException("انتهت صلاحية اشتراك هذا الجهاز"))
            syncRes.isSuccess -> Result.success(current.assignedServers)
            else -> Result.failure(syncRes.exceptionOrNull() ?: IllegalStateException("تعذّر جلب السيرفرات المخصصة"))
        }
    }

    override suspend fun refreshAvailableServers(): Result<List<SonixAssignedServer>> =
        refreshAvailableServers(forceSync = false)

    override suspend fun refreshAvailableServers(forceSync: Boolean): Result<List<SonixAssignedServer>> {
        val uid = identityStore.getOrCreateDeviceUid()
        _state.update { it.copy(deviceUid = uid, isCatalogLoading = true, catalogError = null) }

        // The catalog contains only active server names/IDs (never server URLs).
        // Load it independently from device authentication so a fresh installation
        // can display the panel's server choices immediately.
        val catalogResult = api.callAction("servers.catalog", buildMetadataPayload(uid), null)
        when (catalogResult) {
            is SonixApiResult.Success -> {
                val servers = catalogResult.data["servers"]?.let { element ->
                    runCatching { json.decodeFromJsonElement<List<SonixAssignedServer>>(element) }.getOrNull()
                } ?: emptyList()
                _state.update {
                    it.copy(
                        availableServers = servers,
                        isCatalogLoading = false,
                        hasLoadedCatalog = true,
                        catalogError = null,
                    )
                }
                identityStore.saveCachedSnapshot(_state.value.toSnapshot())
            }
            is SonixApiResult.Forbidden -> {
                val msg = localizeApiError(catalogResult.errorCode)
                _state.update { it.copy(isCatalogLoading = false, catalogError = msg) }
                return Result.failure(IllegalStateException(msg))
            }
            is SonixApiResult.Unauthorized -> {
                val msg = localizeApiError(catalogResult.errorCode)
                _state.update { it.copy(isCatalogLoading = false, catalogError = msg) }
                return Result.failure(IllegalStateException(msg))
            }
            is SonixApiResult.Failure -> {
                val msg = localizeApiError(catalogResult.message)
                _state.update { it.copy(isCatalogLoading = false, catalogError = msg) }
                return Result.failure(IllegalStateException(msg))
            }
        }

        // Registration/entitlement sync still runs, but it must not hide a catalog
        // that was successfully loaded when an old device token needs admin relinking.
        syncNow(force = forceSync)
        return Result.success(_state.value.availableServers)
    }

    override suspend fun resolveAssignedServer(serverId: String): Result<SonixResolvedServer> {
        val uid = identityStore.getOrCreateDeviceUid()
        val payload = buildMetadataPayload(uid, extra = mapOf("server_id" to JsonPrimitive(serverId)))
        return when (val result = executeWithTokenRecovery("server.resolve", payload)) {
            is SonixApiResult.Success -> {
                val serverEl = result.data["server"]
                    ?: return Result.failure(IllegalStateException("لم يُعد الخادم رابط السيرفر"))
                val resolved = runCatching {
                    json.decodeFromJsonElement<SonixResolvedServer>(serverEl)
                }.getOrElse {
                    return Result.failure(IllegalStateException("بيانات السيرفر غير صالحة"))
                }
                Result.success(resolved)
            }
            is SonixApiResult.Forbidden -> {
                val msg = localizeApiError(result.errorCode)
                if (result.errorCode == "subscription_expired" || result.errorCode.startsWith("device_")) {
                    syncNow(force = true)
                }
                Result.failure(IllegalStateException(msg))
            }
            is SonixApiResult.Unauthorized -> Result.failure(IllegalStateException(localizeApiError(result.errorCode)))
            is SonixApiResult.Failure -> Result.failure(IllegalStateException(localizeApiError(result.message)))
        }
    }

    override suspend fun acknowledgeMessage(messageId: String): Result<Unit> {
        val uid = identityStore.getOrCreateDeviceUid()
        val payload = buildJsonObject {
            put("device_uid", uid)
            put("message_id", messageId)
        }
        return when (val result = executeWithTokenRecovery("message.ack", payload)) {
            is SonixApiResult.Success -> {
                _state.update { current ->
                    val remaining = current.pendingMessages.filterNot { it.id == messageId }
                    current.copy(pendingMessages = remaining)
                }
                identityStore.saveCachedSnapshot(_state.value.toSnapshot())
                Result.success(Unit)
            }
            is SonixApiResult.Forbidden -> Result.failure(IllegalStateException(localizeApiError(result.errorCode)))
            is SonixApiResult.Unauthorized -> Result.failure(IllegalStateException(localizeApiError(result.errorCode)))
            is SonixApiResult.Failure -> Result.failure(IllegalStateException(localizeApiError(result.message)))
        }
    }

    override suspend fun fetchAudioSources(): Result<SonixAudioSourceConfig> {
        val uid = identityStore.getOrCreateDeviceUid()
        val payload = buildMetadataPayload(uid)
        val initialToken = identityStore.getDeviceToken()
        val firstAttempt = api.callAction("audio.sources", payload, initialToken)
        val res = if (firstAttempt is SonixApiResult.Unauthorized && !initialToken.isNullOrBlank()) {
            executeWithTokenRecovery("audio.sources", payload)
        } else {
            if (firstAttempt is SonixApiResult.Success) {
                val issued = firstAttempt.data["device_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                if (issued != null) identityStore.saveDeviceToken(issued)
            }
            firstAttempt
        }
        return when (res) {
            is SonixApiResult.Success -> {
                val config = runCatching {
                    json.decodeFromJsonElement<SonixAudioSourceConfig>(res.data)
                }.getOrElse {
                    SonixAudioSourceConfig(
                        configured = res.data["configured"]?.jsonPrimitive?.booleanOrNull ?: false,
                        m3uUrl = res.data["m3u_url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                        updatedAt = res.data["updated_at"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    )
                }
                val updated = _state.value.copy(audioSource = config)
                _state.value = updated
                identityStore.saveCachedSnapshot(updated.toSnapshot())
                Result.success(config)
            }
            is SonixApiResult.Forbidden -> {
                val errState = applyForbiddenStatus(uid, res.errorCode)
                _state.value = errState
                Result.failure(IllegalStateException(localizeApiError(res.errorCode)))
            }
            is SonixApiResult.Unauthorized ->
                Result.failure(IllegalStateException(localizeApiError(res.errorCode)))
            is SonixApiResult.Failure ->
                Result.failure(IllegalStateException(localizeApiError(res.message)))
        }
    }

    override suspend fun checkForPanelUpdate(): Result<SonixReleaseUpdate?> {
        val uid = identityStore.getOrCreateDeviceUid()
        val payload = buildMetadataPayload(uid)
        return when (val result = executeWithTokenRecovery("app.update.check", payload)) {
            is SonixApiResult.Success -> {
                val available = result.data["update_available"]?.jsonPrimitive?.booleanOrNull ?: false
                val updateEl = result.data["update"]
                if (!available || updateEl == null || updateEl.toString() == "null") {
                    Result.success(null)
                } else {
                    val release = runCatching {
                        json.decodeFromJsonElement<SonixReleaseUpdate>(updateEl)
                    }.getOrNull()
                    Result.success(release)
                }
            }
            is SonixApiResult.Forbidden -> Result.success(null)
            is SonixApiResult.Unauthorized -> Result.failure(IllegalStateException(localizeApiError(result.errorCode)))
            is SonixApiResult.Failure -> Result.failure(IllegalStateException(localizeApiError(result.message)))
        }
    }

    private suspend fun executeWithTokenRecovery(
        action: String,
        payload: JsonObject,
    ): SonixApiResult<JsonObject> {
        // Do not hold the auth mutex during ordinary network I/O. In particular,
        // audio.sources must not queue behind the app's foreground "sync" request.
        val tokenUsed = identityStore.getDeviceToken()
        val first = api.callAction(action, payload, tokenUsed)

        if (first is SonixApiResult.Unauthorized && !tokenUsed.isNullOrBlank()) {
            return deviceAuthMutex.withLock {
                // Another concurrent request may already have recovered the token.
                // Reuse that token instead of clearing/reissuing it a second time.
                val currentToken = identityStore.getDeviceToken()
                val retryToken = if (currentToken == tokenUsed) {
                    identityStore.clearDeviceToken()
                    null
                } else {
                    currentToken
                }
                val retry = api.callAction(action, payload, retryToken)
                if (retry is SonixApiResult.Success) {
                    val issued = retry.data["device_token"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotBlank() }
                    if (issued != null) identityStore.saveDeviceToken(issued)
                }
                retry
            }
        }

        if (first is SonixApiResult.Success) {
            val issued = first.data["device_token"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
            if (issued != null) {
                deviceAuthMutex.withLock {
                    // Never let a late response overwrite a token recovered by
                    // another request while this request was in flight.
                    val currentToken = identityStore.getDeviceToken()
                    if (currentToken == tokenUsed || currentToken.isNullOrBlank()) {
                        identityStore.saveDeviceToken(issued)
                    }
                }
            }
        }
        return first
    }

    private suspend fun applySyncPayload(uid: String, data: JsonObject): SonixSyncState {
        // Deliberately ignore "theme" / "themes" to preserve LiquidTV's independent theme system.
        val deviceId = data["device_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val deviceCode = data["device_code"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val deviceStatus = SonixDeviceStatus.fromApi(data["device_status"]?.jsonPrimitive?.content)
        val accessStatus = SonixAccessStatus.fromApi(
            data["access_status"]?.jsonPrimitive?.content ?: data["status"]?.jsonPrimitive?.content,
        )
        val accessAllowed = data["access_allowed"]?.jsonPrimitive?.booleanOrNull
            ?: (accessStatus == SonixAccessStatus.ACTIVE && deviceStatus == SonixDeviceStatus.ACTIVE)

        val subscription = data["subscription"]
            ?.takeIf { it.toString() != "null" }
            ?.let { runCatching { json.decodeFromJsonElement<SonixSubscriptionSummary>(it) }.getOrNull() }

        val settings = data["settings"]
            ?.takeIf { it.toString() != "null" }
            ?.let { runCatching { json.decodeFromJsonElement<SonixAppSettings>(it) }.getOrNull() }
            ?: SonixAppSettings()

        val servers = data["servers"]
            ?.takeIf { it.toString() != "null" }
            ?.let { runCatching { json.decodeFromJsonElement<List<SonixAssignedServer>>(it) }.getOrNull() }
            ?: emptyList()

        val messages = data["messages"]
            ?.takeIf { it.toString() != "null" }
            ?.let { runCatching { json.decodeFromJsonElement<List<SonixMessage>>(it) }.getOrNull() }
            ?: emptyList()
        val audioSource = data["audio_source"]
            ?.takeIf { it.toString() != "null" }
            ?.let { runCatching { json.decodeFromJsonElement<SonixAudioSourceConfig>(it) }.getOrNull() }
            ?: _state.value.audioSource

        val now = System.currentTimeMillis()
        val next = SonixSyncState(
            isSyncing = false,
            isCatalogLoading = _state.value.isCatalogLoading,
            hasLoadedCatalog = _state.value.hasLoadedCatalog,
            catalogError = _state.value.catalogError,
            isRegistered = !deviceCode.isNullOrBlank() || !deviceId.isNullOrBlank(),
            deviceId = deviceId,
            deviceUid = uid,
            deviceCode = deviceCode,
            deviceStatus = deviceStatus,
            accessStatus = accessStatus,
            accessAllowed = accessAllowed,
            subscription = subscription,
            settings = settings,
            assignedServers = if (accessAllowed) servers else emptyList(),
            availableServers = _state.value.availableServers,
            pendingMessages = if (deviceStatus == SonixDeviceStatus.ACTIVE) messages else emptyList(),
            audioSource = if (accessAllowed) audioSource else SonixAudioSourceConfig(),
            lastSyncedAt = now,
            lastError = null,
        )
        _state.value = next
        identityStore.saveCachedSnapshot(next.toSnapshot())
        return next
    }

    private suspend fun applyForbiddenStatus(uid: String, errorCode: String): SonixSyncState {
        val current = _state.value
        val nextDeviceStatus = when (errorCode) {
            "device_blocked" -> SonixDeviceStatus.BLOCKED
            "device_revoked" -> SonixDeviceStatus.REVOKED
            else -> current.deviceStatus
        }
        val nextAccessStatus = when (errorCode) {
            "device_blocked" -> SonixAccessStatus.BLOCKED
            "device_revoked" -> SonixAccessStatus.REVOKED
            "subscription_expired" -> SonixAccessStatus.EXPIRED
            else -> current.accessStatus
        }
        val next = current.copy(
            isSyncing = false,
            deviceUid = uid,
            deviceStatus = nextDeviceStatus,
            accessStatus = nextAccessStatus,
            accessAllowed = false,
            assignedServers = emptyList(),
            audioSource = SonixAudioSourceConfig(),
            // Keep the public, URL-free server catalog visible even if this device
            // is blocked/revoked; server.resolve remains protected by device auth.
            lastError = localizeApiError(errorCode),
        )
        identityStore.saveCachedSnapshot(next.toSnapshot())
        return next
    }

    private fun SonixSyncState.toSnapshot(): SonixSyncSnapshot = SonixSyncSnapshot(
        deviceId = deviceId,
        deviceUid = deviceUid,
        deviceCode = deviceCode,
        deviceStatus = deviceStatus.name,
        accessStatus = accessStatus.name,
        accessAllowed = accessAllowed,
        subscription = subscription,
        settings = settings,
        assignedServers = assignedServers,
        availableServers = availableServers,
        pendingMessages = pendingMessages,
        audioSource = audioSource,
        lastSyncedAt = lastSyncedAt,
    )

    private fun buildMetadataPayload(
        uid: String,
        extra: Map<String, JsonPrimitive> = emptyMap(),
    ): JsonObject {
        val abis = Build.SUPPORTED_ABIS.orEmpty().toList().ifEmpty { listOf("arm64-v8a") }
        val platform = detectPlatform()
        val deviceName = listOfNotNull(
            Build.MANUFACTURER?.trim()?.takeIf { it.isNotBlank() },
            Build.MODEL?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { "LiquidTV Device" }

        return buildJsonObject {
            put("device_uid", uid)
            put("device_name", deviceName)
            put("platform", platform)
            put("manufacturer", Build.MANUFACTURER.orEmpty())
            put("model", Build.MODEL.orEmpty())
            put("os_version", Build.VERSION.RELEASE.orEmpty())
            put("app_version", BuildConfig.VERSION_NAME)
            put("app_version_code", BuildConfig.VERSION_CODE)
            put("app_abi", abis.firstOrNull() ?: "unknown")
            put("app_abis", buildJsonArray { abis.forEach { add(JsonPrimitive(it)) } })
            extra.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun detectPlatform(): String {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvUi = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return if (isTvUi || hasLeanback) "android_tv" else "android"
    }

    private fun localizeApiError(code: String): String = when (code.trim().lowercase()) {
        "invalid_device_credentials" -> "تعذّر التحقق من رمز أمان الجهاز. يُرجى إعادة المحاولة."
        "device_blocked" -> "تم إيقاف هذا الجهاز حالياً."
        "device_revoked" -> "تم إلغاء صلاحية هذا الجهاز."
        "subscription_expired" -> "انتهت صلاحية اشتراك هذا الجهاز."
        "server_not_assigned" -> "السيرفر المحدد غير متاح أو تم إيقافه."
        "invalid_device_uid" -> "معرّف الجهاز غير صالح."
        else -> {
            val trimmed = code.trim()
            if (trimmed.isBlank() ||
                trimmed.startsWith("HTTP ", ignoreCase = true) ||
                trimmed.startsWith("server_error_") ||
                trimmed == "network_error"
            ) {
                "تعذّر الاتصال بخادم الخدمة حالياً، يرجى المحاولة لاحقاً."
            } else {
                trimmed
            }
        }
    }

    companion object {
        private const val FOREGROUND_DEBOUNCE_MS = 2_000L
    }
}
