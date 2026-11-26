package com.liquidtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.liquidtv.app.ui.components.GlassPanelMessageDialog
import com.liquidtv.app.ui.components.MaintenanceBannerStrip
import com.liquidtv.app.ui.components.SONIX_SPLASH_TOTAL_DURATION_MS
import com.liquidtv.app.ui.components.SonixBlockedAccessOverlay
import com.liquidtv.app.ui.components.SonixSplashOverlay
import com.liquidtv.app.ui.navigation.LiquidTVNavGraph
import com.liquidtv.app.ui.navigation.MirrorBubbleNavigation
import com.liquidtv.app.ui.navigation.NavDestination
import com.liquidtv.app.ui.navigation.Screen
import com.liquidtv.app.ui.navigation.toScreen
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.GlassUpdateDialog
import kotlinx.coroutines.delay

/**
 * Root composable that coordinates the 5-second cinematic SONIX IPTV startup splash,
 * subscription startup gate, device status/messages/updates overlays,
 * inactivity screensaver timer, and Mirror Bubble navigation.
 */
@Composable
fun LiquidTVApp(
    startupViewModel: StartupViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
    splashDurationMs: Long = SONIX_SPLASH_TOTAL_DURATION_MS,
) {
    val rootState by startupViewModel.rootState.collectAsState()
    val isSplashFinished by startupViewModel.isSplashFinished.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val lastInteractionMs = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var splashFadeStarted by remember { mutableStateOf(false) }

    val gate = rootState.gate
    val sonixState = rootState.sonixState
    val isStartupResolved = gate !is AppStartupGate.Checking
    val showSplash = !isSplashFinished || !isStartupResolved
    val showDestinationUnderlay = isStartupResolved && (isSplashFinished || splashFadeStarted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030307)),
    ) {
        if (showDestinationUnderlay) {
            if (sonixState.isBlockedOrRevoked) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SonixBlockedAccessOverlay(
                        sonixState = sonixState,
                        onRetrySync = startupViewModel::refreshPanelStatus,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isSplashFinished) {
                        GlassUpdateDialog(
                            updateState = updateState,
                            onUpdateClick = { info -> updateViewModel.startDownload(info) },
                            onInstallClick = { updateViewModel.installCurrentUpdate() },
                            onDismiss = { updateViewModel.dismiss() },
                        )
                    }
                }
            } else {
                val navController = rememberNavController()
                val navBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStack?.destination?.route

                val startDestination = remember(gate is AppStartupGate.Ready) {
                    if (gate is AppStartupGate.Ready) Screen.Home.route else Screen.Login.createRoute(allowBack = false)
                }

                // If active subscriptions are all deleted while using the app, bounce back to Login immediately
                LaunchedEffect(gate, currentRoute) {
                    if (gate is AppStartupGate.NeedsLogin &&
                        currentRoute != null &&
                        !currentRoute.startsWith("login")
                    ) {
                        navController.navigate(Screen.Login.createRoute(allowBack = false)) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                val isFullScreenOverlayRoute = currentRoute == null ||
                    currentRoute.startsWith("player/") ||
                    currentRoute == Screen.Ambient.route ||
                    currentRoute.startsWith("login") ||
                    currentRoute == Screen.Subscriptions.route ||
                    currentRoute == Screen.Addons.route ||
                    currentRoute.startsWith("series_detail/")
                val showNavBar = !isFullScreenOverlayRoute

                // Real inactivity screensaver timer respecting user preferences without recomposing on pointer moves
                LaunchedEffect(
                    isSplashFinished,
                    rootState.preferences.ambientEnabled,
                    rootState.preferences.screenSaverTimeout,
                    currentRoute,
                    gate,
                ) {
                    if (!isSplashFinished) return@LaunchedEffect
                    val prefs = rootState.preferences
                    val eligibleRoute = currentRoute != null &&
                        !currentRoute.startsWith("player/") &&
                        currentRoute != Screen.Ambient.route &&
                        !currentRoute.startsWith("login")

                    if (gate is AppStartupGate.Ready && prefs.ambientEnabled && eligibleRoute) {
                        val timeoutMs = (prefs.screenSaverTimeout.coerceAtLeast(1) * 60_000L)
                        while (true) {
                            val elapsed = System.currentTimeMillis() - lastInteractionMs.longValue
                            val remaining = timeoutMs - elapsed
                            if (remaining <= 0L) {
                                navController.navigate(Screen.Ambient.route) {
                                    launchSingleTop = true
                                }
                                break
                            }
                            delay(remaining.coerceAtMost(5_000L))
                        }
                    }
                }

                val selectedDestination = when {
                    currentRoute?.startsWith("home") == true -> NavDestination.Home
                    currentRoute?.startsWith("live_tv") == true -> NavDestination.LiveTV
                    currentRoute?.startsWith("movies") == true -> NavDestination.Movies
                    currentRoute?.startsWith("series") == true -> NavDestination.Series
                    currentRoute?.startsWith("categories") == true -> NavDestination.Categories
                    currentRoute?.startsWith("category/") == true -> NavDestination.Categories
                    currentRoute?.startsWith("languages") == true -> NavDestination.Categories
                    currentRoute?.startsWith("language/") == true -> NavDestination.Categories
                    currentRoute?.startsWith("countries") == true -> NavDestination.Categories
                    currentRoute?.startsWith("country/") == true -> NavDestination.Categories
                    currentRoute?.startsWith("favorites") == true -> NavDestination.Favorites
                    currentRoute?.startsWith("search") == true -> NavDestination.Search
                    currentRoute?.startsWith("settings") == true -> NavDestination.Settings
                    else -> NavDestination.Home
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppTheme.colors.background)
                        .onPreviewKeyEvent {
                            lastInteractionMs.longValue = System.currentTimeMillis()
                            false
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    lastInteractionMs.longValue = System.currentTimeMillis()
                                }
                            }
                        },
                ) {
                    LiquidTVNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (showNavBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MirrorBubbleNavigation(
                                selectedDestination = selectedDestination,
                                onDestinationSelected = { destination ->
                                    val screen = destination.toScreen()
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (sonixState.settings.maintenanceMode &&
                        sonixState.settings.maintenanceMessage.isNotBlank() &&
                        !currentRoute.orEmpty().startsWith("player/")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MaintenanceBannerStrip(message = sonixState.settings.maintenanceMessage)
                        }
                    }

                    if (isSplashFinished) {
                        GlassPanelMessageDialog(
                            message = sonixState.pendingMessages.firstOrNull(),
                            onAcknowledge = startupViewModel::acknowledgeMessage,
                        )

                        GlassUpdateDialog(
                            updateState = updateState,
                            onUpdateClick = { info -> updateViewModel.startDownload(info) },
                            onInstallClick = { updateViewModel.installCurrentUpdate() },
                            onDismiss = { updateViewModel.dismiss() },
                        )
                    }
                }
            }
        }

        if (showSplash) {
            val initialProgress = remember { startupViewModel.splashResumeProgress }
            SonixSplashOverlay(
                isAppReady = isStartupResolved,
                initialProgress = initialProgress,
                totalDurationMs = splashDurationMs,
                onProgressChanged = { progress ->
                    startupViewModel.updateSplashProgress(progress)
                    if (progress >= 0.88f && !splashFadeStarted) {
                        splashFadeStarted = true
                    }
                },
                onSplashFinished = startupViewModel::markSplashFinished,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
