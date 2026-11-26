package com.liquidtv.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.liquidtv.app.data.audio.ExternalAudioCatalogRepository
import com.liquidtv.app.domain.repository.SonixControlRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import com.liquidtv.app.ui.LiquidTVApp
import com.liquidtv.app.ui.theme.AppThemeCatalog
import com.liquidtv.app.ui.theme.LiquidTVTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var sonixControlRepository: SonixControlRepository

    @Inject
    lateinit var externalAudioCatalogRepository: ExternalAudioCatalogRepository

    private var hasStoppedInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen immersive for TV
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val initialThemeId = runBlocking {
            userPreferencesRepository.getAppTheme().first()
        }

        // Preload cached external audio + sync once on initial launch (NOT a configuration change)
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                externalAudioCatalogRepository.preload()
            }
            lifecycleScope.launch {
                sonixControlRepository.onAppForeground()
                val panelState = sonixControlRepository.state.value
                if (panelState.accessAllowed && panelState.audioSource.configured) {
                    externalAudioCatalogRepository.syncFromPanel(forceRefresh = false)
                }
            }
        }

        setContent {
            val activeThemeId by userPreferencesRepository.getAppTheme()
                .collectAsState(initial = initialThemeId)
            val activeTheme = remember(activeThemeId) {
                AppThemeCatalog.resolve(activeThemeId)
            }

            LaunchedEffect(activeTheme.isLight) {
                insetsController.isAppearanceLightStatusBars = activeTheme.isLight
                insetsController.isAppearanceLightNavigationBars = activeTheme.isLight
            }

            LiquidTVTheme(theme = activeTheme) {
                LiquidTVApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Sync cleanly when returning to the app from background (no 5-minute polling)
        if (hasStoppedInBackground) {
            hasStoppedInBackground = false
            lifecycleScope.launch {
                sonixControlRepository.onAppForeground()
                val panelState = sonixControlRepository.state.value
                if (panelState.accessAllowed && panelState.audioSource.configured) {
                    externalAudioCatalogRepository.syncFromPanel(forceRefresh = false)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            hasStoppedInBackground = true
        }
    }
}
