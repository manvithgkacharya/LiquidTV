package com.liquidtv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.ui.screens.addons.AddonsScreen
import com.liquidtv.app.ui.screens.ambient.AmbientScreen
import com.liquidtv.app.ui.screens.categories.CategoriesScreen
import com.liquidtv.app.ui.screens.countries.CountriesScreen
import com.liquidtv.app.ui.screens.favorites.FavoritesScreen
import com.liquidtv.app.ui.screens.home.HomeScreen
import com.liquidtv.app.ui.screens.languages.LanguagesScreen
import com.liquidtv.app.ui.screens.livetv.LiveTVScreen
import com.liquidtv.app.ui.screens.login.LoginScreen
import com.liquidtv.app.ui.screens.movies.MoviesScreen
import com.liquidtv.app.ui.screens.player.PlayerScreen
import com.liquidtv.app.ui.screens.search.SearchScreen
import com.liquidtv.app.ui.screens.series.SeriesDetailScreen
import com.liquidtv.app.ui.screens.series.SeriesScreen
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.subscriptions.SubscriptionsScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Login : Screen("login?subId={subId}&allowBack={allowBack}") {
        fun createRoute(subId: String? = null, allowBack: Boolean = false): String {
            val encodedId = URLEncoder.encode(subId.orEmpty(), "UTF-8")
            return "login?subId=$encodedId&allowBack=$allowBack"
        }
    }
    object Subscriptions : Screen("subscriptions")
    object LiveTV : Screen("live_tv")
    object Movies : Screen("movies")
    object Series : Screen("series")
    object SeriesDetail : Screen("series_detail/{seriesId}") {
        fun createRoute(seriesId: String) = "series_detail/${URLEncoder.encode(seriesId, "UTF-8")}"
    }
    object Categories : Screen("categories")
    object Languages : Screen("languages")
    object Countries : Screen("countries")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
    object Addons : Screen("addons")
    object Player : Screen("player/{channelId}?mediaType={mediaType}") {
        fun createRoute(channelId: String, mediaType: MediaType = MediaType.LIVE): String {
            val encodedId = URLEncoder.encode(channelId, "UTF-8")
            return "player/$encodedId?mediaType=${mediaType.name}"
        }
    }
    object Ambient : Screen("ambient")
    object CategoryChannels : Screen("category/{categoryName}") {
        fun createRoute(name: String) = "category/${URLEncoder.encode(name, "UTF-8")}"
    }
    object LanguageChannels : Screen("language/{languageCode}") {
        fun createRoute(code: String) = "language/${URLEncoder.encode(code, "UTF-8")}"
    }
    object CountryChannels : Screen("country/{countryCode}") {
        fun createRoute(code: String) = "country/${URLEncoder.encode(code, "UTF-8")}"
    }
}

fun NavDestination.toScreen(): Screen = when (this) {
    NavDestination.Home -> Screen.Home
    NavDestination.LiveTV -> Screen.LiveTV
    NavDestination.Movies -> Screen.Movies
    NavDestination.Series -> Screen.Series
    NavDestination.Categories -> Screen.Categories
    NavDestination.Favorites -> Screen.Favorites
    NavDestination.Search -> Screen.Search
    NavDestination.Settings -> Screen.Settings
}

@Composable
fun LiquidTVNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(
            route = Screen.Login.route,
            arguments = listOf(
                navArgument("subId") { type = NavType.StringType; defaultValue = "" },
                navArgument("allowBack") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { backStackEntry ->
            val rawSubId = backStackEntry.arguments?.getString("subId").orEmpty()
            val subId = rawSubId.takeIf { it.isNotBlank() }?.let { decode(it) }
            val allowBack = backStackEntry.arguments?.getBoolean("allowBack") ?: false
            LoginScreen(
                editingSubscriptionId = subId,
                allowBack = allowBack,
                onLoginSuccess = {
                    if (allowBack && navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Subscriptions.route) {
            SubscriptionsScreen(
                onAddSubscription = {
                    navController.navigate(Screen.Login.createRoute(allowBack = true))
                },
                onEditSubscription = { subId ->
                    navController.navigate(Screen.Login.createRoute(subId = subId, allowBack = true))
                },
                onActivated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onChannelClick = { itemId, mediaType ->
                    navController.navigate(Screen.Player.createRoute(itemId, mediaType))
                },
                onCategoryClick = { categoryName ->
                    navController.navigate(Screen.CategoryChannels.createRoute(categoryName))
                },
                onSeriesClick = { seriesId ->
                    navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onSubscriptionsClick = { navController.navigate(Screen.Subscriptions.route) },
            )
        }

        composable(Screen.LiveTV.route) {
            LiveTVScreen(
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId, MediaType.LIVE))
                },
            )
        }

        composable(Screen.Movies.route) {
            MoviesScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.Player.createRoute(movieId, MediaType.MOVIE))
                },
            )
        }

        composable(Screen.Series.route) {
            SeriesScreen(
                onSeriesClick = { seriesId ->
                    navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                },
            )
        }

        composable(Screen.SeriesDetail.route) {
            SeriesDetailScreen(
                onEpisodeClick = { episodeId ->
                    navController.navigate(Screen.Player.createRoute(episodeId, MediaType.SERIES_EPISODE))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Categories.route) {
            CategoriesScreen(
                onCategoryClick = { categoryName ->
                    navController.navigate(Screen.CategoryChannels.createRoute(categoryName))
                },
                onLanguagesClick = { navController.navigate(Screen.Languages.route) },
                onCountriesClick = { navController.navigate(Screen.Countries.route) },
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
                onChannelClick = { itemId, mediaType ->
                    navController.navigate(Screen.Player.createRoute(itemId, mediaType))
                },
                onSeriesClick = { seriesId ->
                    navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onChannelClick = { itemId, mediaType ->
                    navController.navigate(Screen.Player.createRoute(itemId, mediaType))
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onManageSubscriptions = {
                    navController.navigate(Screen.Subscriptions.route)
                },
                onOpenAddons = {
                    navController.navigate(Screen.Addons.route)
                },
            )
        }

        composable(Screen.Addons.route) {
            AddonsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType; defaultValue = MediaType.LIVE.name },
            ),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("channelId") ?: return@composable
            val channelId = decode(rawId)
            val mediaTypeName = backStackEntry.arguments?.getString("mediaType") ?: MediaType.LIVE.name
            val mediaType = runCatching { MediaType.valueOf(mediaTypeName) }.getOrDefault(MediaType.LIVE)
            PlayerScreen(
                channelId = channelId,
                mediaType = mediaType,
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
            val name = decode(raw)
            LiveTVScreen(
                filterCategory = name,
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId, MediaType.LIVE))
                },
            )
        }

        composable(Screen.LanguageChannels.route) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("languageCode") ?: return@composable
            val code = decode(raw)
            LiveTVScreen(
                filterLanguage = code,
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId, MediaType.LIVE))
                },
            )
        }

        composable(Screen.CountryChannels.route) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("countryCode") ?: return@composable
            val code = decode(raw)
            LiveTVScreen(
                filterCountry = code,
                onChannelClick = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId, MediaType.LIVE))
                },
            )
        }
    }
}

private fun decode(raw: String): String =
    try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
