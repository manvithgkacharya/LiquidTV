package com.liquidtv.app.updater

import kotlinx.serialization.Serializable

/**
 * Metadata for an available application update hosted on SONIX-CONTROL or GitHub Releases.
 */
@Serializable
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int? = null,
    val releaseTitle: String = "Update $versionName",
    val releaseNotes: String = "",
    val apkDownloadUrl: String,
    val apkSize: Long = 0L,
    val sha256: String? = null,
    val publishedAt: String? = null,
    val isMandatory: Boolean = false,
    val source: String = "github",
)

/**
 * State of the update process observed by the UI.
 */
sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    object UpToDate : UpdateState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState
    data class ReadyToInstall(val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
