package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.AddonPreferencesDataStore
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.remote.AddonEndpoints
import com.liquidtv.app.data.remote.StremioAddonClient
import com.liquidtv.app.domain.model.AddonSource
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.Country
import com.liquidtv.app.domain.model.DefaultAddonsRegistry
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubtitleStyleSettings
import com.liquidtv.app.domain.repository.AddonPreferencesRepository
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.navigation.NavDestination
import com.liquidtv.app.ui.screens.addons.AddonsScreen
import com.liquidtv.app.ui.screens.addons.AddonsTab
import com.liquidtv.app.ui.screens.addons.AddonsViewModel
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.settings.SettingsViewModel
import com.liquidtv.app.ui.theme.LiquidTVTheme
import com.liquidtv.app.updater.AppUpdateManager
import com.liquidtv.app.updater.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class AddonsSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val server = MockWebServer()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        Dispatchers.resetMain()
    }

    @Test
    fun `DefaultAddonsRegistry seeds Cinemeta and OpenSubtitles and strictly excludes SONIX AUDIO`() {
        val defaults = DefaultAddonsRegistry.defaultAddons
        assertEquals(3, defaults.size)
        assertTrue(defaults.any { it.id == "com.linvo.cinemeta" && it.version == "3.0.14" })
        assertTrue(defaults.any { it.id == "org.stremio.opensubtitlesv3" && it.version == "1.0.0" })
        assertTrue(defaults.any { it.id == "opensubtitles-pro" && it.version == "0.1.0" })
        assertFalse(defaults.any { it.id.equals("sonix.audio", true) || it.name.contains("SONIX AUDIO", true) })

        val legacyList = listOf(
            StremioAddon(
                id = "sonix.audio",
                name = "SONIX AUDIO",
                version = "1.0.0",
                url = "https://example.com/sonix-audio/manifest.json",
            ),
            defaults.first().copy(enabled = false),
        )
        val cleaned = DefaultAddonsRegistry.ensureDefaults(legacyList)
        assertFalse(cleaned.any { it.id == "sonix.audio" || it.name == "SONIX AUDIO" })
        assertEquals(3, cleaned.size)
        assertFalse(cleaned.first { it.id == "com.linvo.cinemeta" }.enabled)
    }

    @Test
    fun `AddonPreferencesDataStore persists defaults, custom addons, toggles, and excludes legacy audio addon`() = runTest {
        val store = AddonPreferencesDataStore(context)
        val initial = store.getInstalledAddonsOnce()
        assertEquals(3, initial.size)

        // Attempt to upsert legacy sonix.audio must be silently ignored
        store.upsertAddon(
            StremioAddon(
                id = "sonix.audio",
                name = "SONIX AUDIO",
                url = "https://example.com/audio/manifest.json",
            )
        )
        assertFalse(store.getInstalledAddonsOnce().any { it.id == "sonix.audio" })

        val custom = StremioAddon(
            id = "org.custom.subs",
            name = "Custom Arabic Subs",
            version = "2.1.0",
            url = "https://subs.example.com/manifest.json",
            resources = listOf("subtitles"),
            source = AddonSource.CUSTOM,
        )
        store.upsertAddon(custom)
        assertEquals(4, store.getInstalledAddonsOnce().size)

        store.setAddonEnabled("org.custom.subs", false)
        assertFalse(store.getInstalledAddonsOnce().first { it.id == "org.custom.subs" }.enabled)

        // Defaults cannot be deleted
        store.removeAddon("com.linvo.cinemeta")
        assertTrue(store.getInstalledAddonsOnce().any { it.id == "com.linvo.cinemeta" })

        // Custom addon can be removed
        store.removeAddon("org.custom.subs")
        assertEquals(3, store.getInstalledAddonsOnce().size)

        // Language & style persistence
        store.setPreferredSubtitleSearchLanguage("en")
        assertEquals("en", store.getPreferredSubtitleSearchLanguageOnce())
        store.updateSubtitleStyle {
            it.copy(fontId = "tajawal_extrabold", fontSizeSp = 32, delayMs = 750)
        }
        val persistedStyle = store.getSubtitleStyleOnce()
        assertEquals("tajawal_extrabold", persistedStyle.fontId)
        assertEquals(32, persistedStyle.fontSizeSp)
        assertEquals(750, persistedStyle.delayMs)
    }

    @Test
    fun `StremioAddonClient fetches official catalog and falls back to secondary community URL when primary fails`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/official.json" -> MockResponse().setResponseCode(200).setBody(
                        """
                        [
                          {
                            "manifest": {
                              "id": "com.official.subs",
                              "name": "Official Subs Pack",
                              "version": "1.2.0",
                              "description": "Official subtitles",
                              "resources": ["subtitles"],
                              "types": ["movie", "series"]
                            },
                            "transportUrl": "$baseUrl/official-subs/manifest.json"
                          },
                          {
                            "manifest": {
                              "id": "sonix.audio",
                              "name": "SONIX AUDIO",
                              "version": "1.0.0"
                            },
                            "transportUrl": "$baseUrl/sonix-audio/manifest.json"
                          }
                        ]
                        """.trimIndent()
                    )
                    "/community-primary" -> MockResponse().setResponseCode(503).setBody("unavailable")
                    "/community-fallback" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "addons": [
                            {
                              "id": "com.community.arabsubs",
                              "name": "ArabSubs Community",
                              "version": "0.9.5",
                              "url": "$baseUrl/arabsubs/manifest.json",
                              "resources": ["subtitles"],
                              "behaviorHints": { "configurationRequired": true }
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = StremioAddonClient(OkHttpClient(), context).apply {
            endpoints = AddonEndpoints(
                officialCatalogUrl = "$baseUrl/official.json",
                communityCatalogPrimaryUrl = "$baseUrl/community-primary",
                communityCatalogFallbackUrl = "$baseUrl/community-fallback",
                cinemetaBaseUrl = baseUrl,
            )
        }

        val market = client.fetchMarketCatalogs()
        assertEquals(1, market.official.size)
        assertEquals("com.official.subs", market.official.first().id)
        assertEquals(1, market.community.size)
        assertEquals("com.community.arabsubs", market.community.first().id)
        assertTrue(market.community.first().configurationRequired)
        assertTrue(market.community.first().configureUrl.endsWith("/arabsubs/configure"))
    }

    @Test
    fun `AddonsViewModel installs manifest URL, prevents duplicates, refreshes version, and opens configure URL when needed`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        var manifestVersion = "1.0.0"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/official.json" -> MockResponse().setResponseCode(200).setBody("[]")
                    "/community.json" -> MockResponse().setResponseCode(200).setBody("[]")
                    "/my-addon/manifest.json" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "id": "com.example.mysubs",
                          "name": "My Custom Subtitles",
                          "version": "$manifestVersion",
                          "description": "Fast subtitles",
                          "resources": ["subtitles"],
                          "types": ["movie", "series"]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val repo = InMemoryAddonPreferencesRepository()
        val client = StremioAddonClient(OkHttpClient(), context).apply {
            endpoints = AddonEndpoints(
                officialCatalogUrl = "$baseUrl/official.json",
                communityCatalogPrimaryUrl = "$baseUrl/community.json",
                communityCatalogFallbackUrl = "$baseUrl/community.json",
                cinemetaBaseUrl = baseUrl,
            )
        }
        val vm = AddonsViewModel(repo, client)

        // Normalized without trailing /manifest.json
        vm.onManifestUrlChanged("$baseUrl/my-addon")
        vm.installManualManifestUrl()
        waitUntilCondition { vm.uiState.value.installedAddons.size == 4 }
        val installed = vm.uiState.value.installedAddons
        assertEquals(4, installed.size)
        val added = installed.first { it.id == "com.example.mysubs" }
        assertEquals("1.0.0", added.version)

        // Duplicate prevention
        vm.onManifestUrlChanged("$baseUrl/my-addon/manifest.json")
        vm.installManualManifestUrl()
        waitUntilCondition { vm.uiState.value.statusMessage.orEmpty().contains("مسبقاً") }
        assertEquals(4, vm.uiState.value.installedAddons.size)
        assertTrue(vm.uiState.value.statusMessage.orEmpty().contains("مسبقاً"))

        // Refresh updates manifest version
        manifestVersion = "1.4.2"
        vm.refreshInstalledAddon(added)
        waitUntilCondition { vm.uiState.value.installedAddons.first { it.id == "com.example.mysubs" }.version == "1.4.2" }
        assertEquals("1.4.2", vm.uiState.value.installedAddons.first { it.id == "com.example.mysubs" }.version)

        // Configure-required addon triggers pendingConfigureUrl instead of blind install
        val configurableAddon = StremioAddon(
            id = "com.config.required",
            name = "Configurable Subs",
            url = "$baseUrl/[config]/manifest.json",
            configurationRequired = true,
            source = AddonSource.COMMUNITY,
        )
        vm.installFromMarket(configurableAddon)
        assertNotNull(vm.uiState.value.pendingConfigureUrl)
        assertTrue(vm.uiState.value.pendingConfigureUrl!!.endsWith("/configure"))
    }

    private suspend fun waitUntilCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) break
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                kotlinx.coroutines.delay(15L)
            }
        }
    }

    @Test
    fun `SettingsScreen hosts Addons section and MirrorBubbleNavigation excludes Addons`() {
        // MirrorBubbleNavigation must stay clean: no Addons destination in top bar
        assertFalse(NavDestination.entries.any { it.name.contains("ADDON", ignoreCase = true) })

        val prefs = UserPreferencesDataStore(context)
        val settingsVm = SettingsViewModel(
            context = context,
            userPreferencesRepository = prefs,
            channelRepository = AddonTestChannelRepository(),
            subscriptionRepository = AddonTestSubscriptionRepository(),
        )
        val updateVm = AppUpdateViewModel(AppUpdateManager(context, OkHttpClient(), prefs), prefs)
        var openedAddons = false

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1000.dp).height(650.dp)) {
                    SettingsScreen(
                        onOpenAddons = { openedAddons = true },
                        viewModel = settingsVm,
                        updateViewModel = updateVm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_section_addons").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_open_addons").assertIsDisplayed().performClick()
        assertTrue("Expected clicking Addons setting tile to navigate to AddonsScreen", openedAddons)
    }

    @Test
    fun `AddonsScreen renders tabs, filters addons by search, and supports market install`() {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/official.json" -> MockResponse().setResponseCode(200).setBody(
                        """
                        [
                          {
                            "manifest": {
                              "id": "org.market.subhero",
                              "name": "SubHero Official",
                              "version": "2.0.0",
                              "resources": ["subtitles"]
                            },
                            "transportUrl": "$baseUrl/subhero/manifest.json"
                          }
                        ]
                        """.trimIndent()
                    )
                    "/community.json" -> MockResponse().setResponseCode(200).setBody("[]")
                    "/subhero/manifest.json" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "id": "org.market.subhero",
                          "name": "SubHero Official",
                          "version": "2.0.0",
                          "resources": ["subtitles"],
                          "types": ["movie", "series"]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val repo = InMemoryAddonPreferencesRepository()
        val client = StremioAddonClient(OkHttpClient(), context).apply {
            endpoints = AddonEndpoints(
                officialCatalogUrl = "$baseUrl/official.json",
                communityCatalogPrimaryUrl = "$baseUrl/community.json",
                communityCatalogFallbackUrl = "$baseUrl/community.json",
                cinemetaBaseUrl = baseUrl,
            )
        }
        val vm = AddonsViewModel(repo, client)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1200.dp).height(720.dp)) {
                    AddonsScreen(onBack = {}, viewModel = vm)
                }
            }
        }

        composeRule.onNodeWithTag("addon_card_com.linvo.cinemeta").assertIsDisplayed()
        composeRule.onNodeWithTag("addons_search_input").performTextInput("PRO")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("addon_card_opensubtitles-pro").assertIsDisplayed()
        composeRule.onAllNodesWithTag("addon_card_com.linvo.cinemeta").fetchSemanticsNodes().let {
            assertTrue(it.isEmpty())
        }

        // Clear search and switch to Official market tab
        vm.onSearchQueryChanged("")
        composeRule.onNodeWithTag("addons_tab_official").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("addon_card_org.market.subhero").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("addon_install_org.market.subhero").performClick()
        composeRule.waitUntil(5_000) {
            vm.uiState.value.installedAddons.any { it.id == "org.market.subhero" }
        }
        composeRule.onNodeWithTag("addons_tab_installed").performClick()
        composeRule.onNodeWithTag("addons_list").performScrollToNode(hasTestTag("addon_card_org.market.subhero"))
        composeRule.onNodeWithText("SubHero Official").assertIsDisplayed()
    }
}

class InMemoryAddonPreferencesRepository(
    initialAddons: List<StremioAddon> = DefaultAddonsRegistry.defaultAddons,
    initialStyle: SubtitleStyleSettings = SubtitleStyleSettings(),
    initialLang: String = "ar",
) : AddonPreferencesRepository {
    private val _addons = MutableStateFlow(DefaultAddonsRegistry.ensureDefaults(initialAddons))
    private val _style = MutableStateFlow(initialStyle.normalized())
    private val _lang = MutableStateFlow(initialLang)

    override val installedAddons: Flow<List<StremioAddon>> = _addons.asStateFlow()
    override val subtitleStyle: Flow<SubtitleStyleSettings> = _style.asStateFlow()
    override val preferredSubtitleSearchLanguage: Flow<String> = _lang.asStateFlow()

    override suspend fun getInstalledAddonsOnce(): List<StremioAddon> = _addons.value
    override suspend fun getSubtitleStyleOnce(): SubtitleStyleSettings = _style.value
    override suspend fun getPreferredSubtitleSearchLanguageOnce(): String = _lang.value

    override suspend fun upsertAddon(addon: StremioAddon) {
        if (DefaultAddonsRegistry.isExcludedLegacyAddon(addon.id, addon.name)) return
        _addons.update { current ->
            val mutable = current.toMutableList()
            val idx = mutable.indexOfFirst { it.id == addon.id || it.url == addon.url }
            if (idx >= 0) mutable[idx] = addon else mutable.add(addon)
            DefaultAddonsRegistry.ensureDefaults(mutable)
        }
    }

    override suspend fun setAddonEnabled(addonId: String, enabled: Boolean) {
        _addons.update { current ->
            current.map { if (it.id == addonId) it.copy(enabled = enabled) else it }
        }
    }

    override suspend fun removeAddon(addonId: String) {
        if (DefaultAddonsRegistry.isDefaultAddon(addonId)) return
        _addons.update { current -> current.filterNot { it.id == addonId } }
    }

    override suspend fun replaceAddons(addons: List<StremioAddon>) {
        _addons.value = DefaultAddonsRegistry.ensureDefaults(addons)
    }

    override suspend fun updateSubtitleStyle(transform: (SubtitleStyleSettings) -> SubtitleStyleSettings) {
        _style.update { transform(it).normalized() }
    }

    override suspend fun saveSubtitleStyle(style: SubtitleStyleSettings) {
        _style.value = style.normalized()
    }

    override suspend fun setPreferredSubtitleSearchLanguage(languageCode: String) {
        _lang.value = languageCode
    }
}

class AddonTestSubscriptionRepository : SubscriptionRepository {
    override val allSubscriptions: Flow<List<Subscription>> = flowOf(emptyList())
    override val activeSubscription: Flow<Subscription?> = flowOf(null)
    override val syncInfo: Flow<PlaylistInfo> = flowOf(PlaylistInfo())
    override suspend fun getActiveSubscriptionOnce(): Subscription? = null
    override suspend fun getSubscriptionById(id: String): Subscription? = null
    override suspend fun hasAnySubscription(): Boolean = false
    override suspend fun addOrUpdateXtreamSubscription(
        existingId: String?,
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
        origin: SubscriptionOrigin,
        panelServerId: String?,
        panelServerName: String?,
        activate: Boolean,
    ): Result<Subscription> = Result.failure(UnsupportedOperationException())
    override suspend fun activateSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun syncSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun syncActiveSubscription(): Result<Unit> = Result.success(Unit)
    override suspend fun syncActiveEpg(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getDecryptedXtreamPassword(subscriptionId: String): String? = null
}

open class AddonTestChannelRepository : ChannelRepository {
    override fun getAllChannels(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getMovies(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getSeries(): Flow<List<SeriesItem>> = flowOf(emptyList())
    override fun getSeriesById(seriesId: String): Flow<SeriesItem?> = flowOf(null)
    override fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeItem>> = flowOf(emptyList())
    override suspend fun loadSeriesDetails(seriesId: String): Result<Unit> = Result.success(Unit)
    override fun getFavoriteChannels(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getRecentlyWatched(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCategory(category: String, mediaType: MediaType): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByLanguage(language: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCountry(country: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchChannels(query: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchSeries(query: String): Flow<List<SeriesItem>> = flowOf(emptyList())
    override fun getAllCategories(mediaType: MediaType): Flow<List<Category>> = flowOf(emptyList())
    override fun getAllLanguages(): Flow<List<Language>> = flowOf(emptyList())
    override fun getAllCountries(): Flow<List<Country>> = flowOf(emptyList())
    override fun getLockedCategoryNames(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setCategoryLocked(categoryName: String, locked: Boolean) = Unit
    override suspend fun getChannelById(id: String): Channel? = null
    override suspend fun getEpisodeById(episodeId: String): EpisodeItem? = null
    override suspend fun toggleFavorite(channelId: String) = Unit
    override suspend fun toggleSeriesFavorite(seriesId: String) = Unit
    override suspend fun recordWatch(channelId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun recordEpisodeWatch(episodeId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun updateResumePosition(itemId: String, mediaType: MediaType, positionMs: Long) = Unit
    override fun observeChannelEpg(channel: Channel): Flow<ChannelEpgSummary> = flowOf(ChannelEpgSummary())
    override suspend fun fetchShortEpgIfNeeded(channel: Channel): ChannelEpgSummary = ChannelEpgSummary()
    override suspend fun refreshPlaylists(): Result<Unit> = Result.success(Unit)
    override suspend fun clearWatchHistory() = Unit
    override fun getPlaylistInfo(): Flow<PlaylistInfo> = flowOf(PlaylistInfo())
}
