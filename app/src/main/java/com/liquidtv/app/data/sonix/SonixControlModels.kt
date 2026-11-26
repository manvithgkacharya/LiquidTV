package com.liquidtv.app.data.sonix

import kotlinx.serialization.SerialName
import com.liquidtv.app.domain.model.SonixAudioSourceConfig
import kotlinx.serialization.Serializable

enum class SonixDeviceStatus {
    ACTIVE,
    BLOCKED,
    REVOKED,
    UNKNOWN;

    companion object {
        fun fromApi(raw: String?): SonixDeviceStatus = when (raw?.trim()?.lowercase()) {
            "active" -> ACTIVE
            "blocked" -> BLOCKED
            "revoked" -> REVOKED
            else -> UNKNOWN
        }
    }
}

enum class SonixAccessStatus {
    ACTIVE,
    EXPIRED,
    BLOCKED,
    REVOKED,
    UNKNOWN;

    companion object {
        fun fromApi(raw: String?): SonixAccessStatus = when (raw?.trim()?.lowercase()) {
            "active" -> ACTIVE
            "expired" -> EXPIRED
            "blocked" -> BLOCKED
            "revoked" -> REVOKED
            else -> UNKNOWN
        }
    }
}

@Serializable
data class SonixSubscriptionSummary(
    val id: String? = null,
    @SerialName("plan_name") val planName: String = "",
    @SerialName("plan_code") val planCode: String = "",
    val status: String = "expired",
    @SerialName("is_trial") val isTrial: Boolean = false,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("days_remaining") val daysRemaining: Int = 0,
)

@Serializable
data class SonixAssignedServer(
    val id: String,
    val name: String,
    val region: String? = null,
    val priority: Int = 100,
)

@Serializable
data class SonixResolvedServer(
    val id: String,
    val name: String,
    val region: String? = null,
    @SerialName("server_url") val serverUrl: String,
)

@Serializable
data class SonixMessage(
    val id: String,
    val title: String,
    val body: String,
    @SerialName("message_type") val messageType: String = "info",
    @SerialName("publish_at") val publishAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("target_type") val targetType: String = "all",
)

@Serializable
data class SonixAppSettings(
    @SerialName("trial_duration_days") val trialDurationDays: Int = 30,
    @SerialName("maintenance_mode") val maintenanceMode: Boolean = false,
    @SerialName("maintenance_message") val maintenanceMessage: String = "",
    @SerialName("support_notice") val supportNotice: String = "",
    @SerialName("allow_normal_url_login") val allowNormalUrlLogin: Boolean = true,
    @SerialName("allow_panel_servers") val allowPanelServers: Boolean = true,
)

@Serializable
data class SonixReleaseUpdate(
    val id: String,
    val channel: String = "stable",
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("release_url") val releaseUrl: String = "",
    val sha256: String = "",
    @SerialName("apk_size_bytes") val apkSizeBytes: Long? = null,
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("update_policy") val updatePolicy: String = "optional",
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 0,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class SonixSyncSnapshot(
    val deviceId: String? = null,
    val deviceUid: String = "",
    val deviceCode: String? = null,
    val deviceStatus: String = "UNKNOWN",
    val accessStatus: String = "UNKNOWN",
    val accessAllowed: Boolean = true,
    val subscription: SonixSubscriptionSummary? = null,
    val settings: SonixAppSettings = SonixAppSettings(),
    val assignedServers: List<SonixAssignedServer> = emptyList(),
    val availableServers: List<SonixAssignedServer> = emptyList(),
    val pendingMessages: List<SonixMessage> = emptyList(),
    @SerialName("audio_source") val audioSource: SonixAudioSourceConfig = SonixAudioSourceConfig(),
    val lastSyncedAt: Long? = null,
)

data class SonixSyncState(
    val isSyncing: Boolean = false,
    val isCatalogLoading: Boolean = false,
    val hasLoadedCatalog: Boolean = false,
    val catalogError: String? = null,
    val isRegistered: Boolean = false,
    val deviceId: String? = null,
    val deviceUid: String = "",
    val deviceCode: String? = null,
    val deviceStatus: SonixDeviceStatus = SonixDeviceStatus.UNKNOWN,
    val accessStatus: SonixAccessStatus = SonixAccessStatus.UNKNOWN,
    val accessAllowed: Boolean = true,
    val subscription: SonixSubscriptionSummary? = null,
    val settings: SonixAppSettings = SonixAppSettings(),
    val assignedServers: List<SonixAssignedServer> = emptyList(),
    val availableServers: List<SonixAssignedServer> = emptyList(),
    val pendingMessages: List<SonixMessage> = emptyList(),
    val audioSource: SonixAudioSourceConfig = SonixAudioSourceConfig(),
    val lastSyncedAt: Long? = null,
    val lastError: String? = null,
) {
    val isBlockedOrRevoked: Boolean
        get() = deviceStatus == SonixDeviceStatus.BLOCKED ||
            deviceStatus == SonixDeviceStatus.REVOKED ||
            accessStatus == SonixAccessStatus.BLOCKED ||
            accessStatus == SonixAccessStatus.REVOKED

    val isPanelExpired: Boolean
        get() = accessStatus == SonixAccessStatus.EXPIRED || (!accessAllowed && !isBlockedOrRevoked && isRegistered)
}
