package com.liquidtv.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AudioChannelBrand(val code: String, val displayName: String) {
    BEIN_SPORTS("bein_sports", "beIN SPORTS"),
    ALWAN("alwan", "ALWAN"),
    OTHER("other", "أخرى"),
}

enum class AudioBrandFilter(val labelAr: String) {
    ALL("الكل"),
    BEIN_SPORTS("beIN SPORTS"),
    ALWAN("ALWAN"),
}

@Serializable
data class ExternalAudioChannel(
    val id: String,
    val name: String,
    val number: String,
    val streamUrl: String,
    val brand: AudioChannelBrand,
    val categoryName: String,
    val containerExtension: String = "m3u8",
    val qualityRank: Int = 99,
    val qualityLabel: String = "",
)

@Serializable
data class SonixAudioSourceConfig(
    val configured: Boolean = false,
    @SerialName("m3u_url") val m3uUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

enum class ExternalAudioStatus {
    IDLE,
    LOADING_CATALOG,
    CONNECTING,
    BUFFERING,
    ACTIVE,
    RECOVERING,
    ERROR,
}

enum class ExternalAudioSyncStage(val defaultMessageAr: String) {
    IDLE(""),
    CONNECTING_PANEL("جارٍ الاتصال بمزود الصوتيات..."),
    DOWNLOADING_PLAYLIST("جارٍ تنزيل قوائم الصوتيات..."),
    PARSING_CHANNELS("جارٍ تحليل وتصنيف القنوات..."),
    SAVING_LOCAL("جارٍ حفظ الصوتيات على الجهاز..."),
    SUCCEEDED("تمت المزامنة بنجاح"),
    FAILED("فشلت المزامنة"),
}

data class ExternalAudioCatalogResult(
    val channels: List<ExternalAudioChannel> = emptyList(),
    val updatedAt: String? = null,
    val lastSyncedAtMs: Long? = null,
    val fromCache: Boolean = false,
    val warning: String? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null && channels.isNotEmpty()
}

data class ExternalAudioUiState(
    val isSheetVisible: Boolean = false,
    val isLoadingCatalog: Boolean = false,
    val syncStage: ExternalAudioSyncStage = ExternalAudioSyncStage.IDLE,
    val syncStageMessage: String? = null,
    val catalogError: String? = null,
    val catalogWarning: String? = null,
    val lastUpdatedAt: String? = null,
    val lastSyncedAtMs: Long? = null,
    val channels: List<ExternalAudioChannel> = emptyList(),
    val searchQuery: String = "",
    val selectedBrandFilter: AudioBrandFilter = AudioBrandFilter.ALL,
    val activeChannel: ExternalAudioChannel? = null,
    val status: ExternalAudioStatus = ExternalAudioStatus.IDLE,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 6,
    val volumePercent: Int = 100,
    val loudnessBoostEnabled: Boolean = true,
    val syncOffsetMs: Long = 0L,
    val isPcmVerified: Boolean = false,
    val connectionLimitWarning: String? = null,
) {
    val isAttached: Boolean
        get() = activeChannel != null && status != ExternalAudioStatus.IDLE
}
