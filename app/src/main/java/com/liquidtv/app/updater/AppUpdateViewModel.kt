package com.liquidtv.app.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadedApkFile: File? = null

    init {
        checkAutoUpdateOnLaunch()
    }

    private fun checkAutoUpdateOnLaunch() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            if (prefs?.autoCheckUpdates == true) {
                val result = appUpdateManager.checkForUpdate()
                if (result.isSuccess) {
                    val info = result.getOrNull()
                    if (info != null) {
                        _updateState.value = UpdateState.Available(info)
                    }
                }
            }
        }
    }

    fun checkForUpdate(repoOverride: String? = null) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val result = appUpdateManager.checkForUpdate(repoOverride)
            if (result.isSuccess) {
                val info = result.getOrNull()
                if (info != null) {
                    _updateState.value = UpdateState.Available(info)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            } else {
                val raw = result.exceptionOrNull()
                runCatching { android.util.Log.w("AppUpdateViewModel", "checkForUpdate failed", raw) }
                _updateState.value = UpdateState.Error("تعذّر التحقق من التحديثات حالياً، يرجى المحاولة لاحقاً.")
            }
        }
    }

    fun startDownload(updateInfo: AppUpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0f, 0L, updateInfo.apkSize)
            val result = appUpdateManager.downloadUpdate(updateInfo) { progress, downloaded, total ->
                _updateState.value = UpdateState.Downloading(progress, downloaded, total)
            }

            if (result.isSuccess) {
                val file = result.getOrNull()!!
                downloadedApkFile = file
                _updateState.value = UpdateState.ReadyToInstall(file.absolutePath)
                // Trigger installation prompt immediately
                installCurrentUpdate()
            } else {
                val raw = result.exceptionOrNull()
                runCatching { android.util.Log.w("AppUpdateViewModel", "downloadUpdate failed", raw) }
                _updateState.value = UpdateState.Error("فشل تحميل التحديث حالياً، يرجى المحاولة لاحقاً.")
            }
        }
    }

    fun installCurrentUpdate() {
        val file = downloadedApkFile ?: return
        val result = appUpdateManager.installUpdate(file)
        if (result.isFailure) {
            val raw = result.exceptionOrNull()
            runCatching { android.util.Log.w("AppUpdateViewModel", "installUpdate failed", raw) }
            val msg = raw?.message?.takeIf { !it.contains("HTTP", ignoreCase = true) && !it.contains("GitHub", ignoreCase = true) }
                ?: "فشل تثبيت التحديث"
            _updateState.value = UpdateState.Error(msg)
        }
    }

    fun dismiss() {
        val current = _updateState.value
        if (current is UpdateState.Available && current.info.isMandatory) {
            return
        }
        _updateState.value = UpdateState.Idle
    }

    fun checkAutoUpdateIfEnabled() {
        checkAutoUpdateOnLaunch()
    }

    fun setGithubRepo(repo: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGithubRepo(repo)
        }
    }
}
