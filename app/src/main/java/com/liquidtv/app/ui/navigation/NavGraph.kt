package com.liquidtv.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.liquidtv.app.ui.screens.home.HomeScreen
import com.liquidtv.app.ui.screens.livetv.LiveTVScreen
import com.liquidtv.app.ui.screens.categories.CategoriesScreen
import com.liquidtv.app.ui.screens.languages.LanguagesScreen
import com.liquidtv.app.ui.screens.countries.CountriesScreen
import com.liquidtv.app.ui.screens.search.SearchScreen
import com.liquidtv.app.ui.screens.favorites.FavoritesScreen
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.player.PlayerScreen
import com.liquidtv.app.ui.screens.ambient.AmbientScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object LiveTV : Screen("live_tv")
    object Categories : Screen("categories")
    object Languages : Screen("languages")
    object Countries : Screen("countries")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
    object Player : Screen("player/{channelId}") {
        fun createRoute(channelId: String) = "player/$channelId"
    }
    object Ambient : Screen("ambient")
    object CategoryChannels : Screen("category/{categoryName}") {
        fun createRoute(name: String) = "category/${java.net.URLEncoder.encode(name, "UTF-8")}"
    }
    object LanguageChannels : Screen("language/{languageCode}") {
        fun createRoute(code: String) = "language/${java.net.URLEncoder.encode(code, "UTF-8")}"
    }
    object CountryChannels : Screen("country/{countryCode}") {
        fun createRoute(code: String) = "country/${java.net.URLEncoder.encode(code, "UTF-8")}"
    }
}

fun NavDestination.toScreen() = when (this) {
    NavDestination.Home -> Screen.Home
    NavDestination.LiveTV -> Screen.LiveTV
    NavDestination.Categories -> Screen.Categories
    NavDestination.Languages -> Screen.Languages
    NavDestination.Countries -> Screen.Countries
    NavDestination.Favorites -> Screen.Favorites
    NavDestination.Search -> Screen.Search
    NavDestination.Settings -> Screen.Settings
}

@Composable
fun LiquidTVNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
                onSearchClick = { navController.navigate(Screen.Search.route) },
            )
        }
        composable(Screen.LiveTV.route) {
            LiveTVScreen(
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
            )
        }
        composable(Screen.Categories.route) {
            CategoriesScreen(
                onCategoryClick = { categoryName ->
                    navController.navigate(Screen.CategoryChannels.createRoute(categoryName))
                },
            )
        }
        composable(Screen.Languages.route) {
            LanguagesScreen(
                onLanguageClick = { code ->
                    navController.navigate(Screen.LanguageChannels.createRoute(code))
                },
            )
        }
        composable(Screen.Countries.route) {
            CountriesScreen(
                onCountryClick = { code ->
                    navController.navigate(Screen.CountryChannels.createRoute(code))
                },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Player.route) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
            PlayerScreen(
                channelId = channelId,
                onBack = { navController.popBackStack() },
                onAmbientMode = { navController.navigate(Screen.Ambient.route) },
            )
        }
        composable(Screen.Ambient.route) {
            AmbientScreen(
                onExit = { navController.popBackStack() },
            )
        }
        composable(Screen.CategoryChannels.route) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("categoryName") ?: return@composable
            val name = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
            LiveTVScreen(
                filterCategory = name,
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
            )
        }
        composable(Screen.LanguageChannels.route) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("languageCode") ?: return@composable
            val code = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
            LiveTVScreen(
                filterLanguage = code,
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
            )
        }
        composable(Screen.CountryChannels.route) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("countryCode") ?: return@composable
            val code = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
            LiveTVScreen(
                filterCountry = code,
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
            )
        }
    }
}

