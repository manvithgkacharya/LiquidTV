package com.liquidtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.liquidtv.app.ui.navigation.*
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.GlassUpdateDialog

/**
 * Root composable that wires navigation + the Mirror Bubble nav bar.
 * The nav bar is hidden inside the player and ambient screens.
 */
@Composable
fun LiquidTVApp(
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val updateState by updateViewModel.updateState.collectAsState()

    // Hide nav bar in player and ambient
    val showNavBar = currentRoute != Screen.Player.route &&
            currentRoute != Screen.Ambient.route &&
            currentRoute?.startsWith("player/") != true

    // Derive selected nav destination
    val selectedDestination = when {
        currentRoute?.startsWith("home") == true -> NavDestination.Home
        currentRoute?.startsWith("live_tv") == true -> NavDestination.LiveTV
        currentRoute?.startsWith("categories") == true -> NavDestination.Categories
        currentRoute?.startsWith("category/") == true -> NavDestination.Categories
        currentRoute?.startsWith("languages") == true -> NavDestination.Languages
        currentRoute?.startsWith("language/") == true -> NavDestination.Languages
        currentRoute?.startsWith("countries") == true -> NavDestination.Countries
        currentRoute?.startsWith("country/") == true -> NavDestination.Countries
        currentRoute?.startsWith("favorites") == true -> NavDestination.Favorites
        currentRoute?.startsWith("search") == true -> NavDestination.Search
        currentRoute?.startsWith("settings") == true -> NavDestination.Settings
        else -> NavDestination.Home
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidColors.MidnightNavy),
    ) {
        // Main content area
        LiquidTVNavGraph(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )

        // Floating navigation bar at top
        if (showNavBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
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

        // Global Update Available / Downloading dialog
        GlassUpdateDialog(
            updateState = updateState,
            onUpdateClick = { info -> updateViewModel.startDownload(info) },
            onInstallClick = { updateViewModel.installCurrentUpdate() },
            onDismiss = { updateViewModel.dismiss() },
        )
    }
}

