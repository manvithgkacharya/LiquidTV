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
                val msg = result.exceptionOrNull()?.message ?: "Check failed"
                _updateState.value = UpdateState.Error(msg)
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
                val errorMsg = result.exceptionOrNull()?.message ?: "Download failed"
                _updateState.value = UpdateState.Error(errorMsg)
            }
        }
    }

    fun installCurrentUpdate() {
        val file = downloadedApkFile ?: return
        val result = appUpdateManager.installUpdate(file)
        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "Installation failed"
            _updateState.value = UpdateState.Error(msg)
        }
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }

    fun setGithubRepo(repo: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGithubRepo(repo)
        }
    }
}
