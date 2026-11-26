package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.Country
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.settings.SettingsViewModel
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.AppThemeCatalog
import com.liquidtv.app.ui.theme.LiquidTVTheme
import com.liquidtv.app.ui.theme.ThemeAtmosphere
import com.liquidtv.app.updater.AppUpdateManager
import com.liquidtv.app.updater.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class ThemeSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            UserPreferencesDataStore(context).setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            UserPreferencesDataStore(context).setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `AppThemeCatalog exposes all 10 premium dark themes with exact sort order default and HEX tokens`() {
        val themes = AppThemeCatalog.allThemes
        assertEquals(10, themes.size)
        assertEquals((10..19).toList(), themes.map { it.sortOrder })
        assertTrue(themes.all { it.isActive })
        assertEquals(listOf("royal-navy"), themes.filter { it.isDefault }.map { it.id })
        assertEquals("royal-navy", AppThemeCatalog.defaultTheme.id)

        val expectedIds = listOf(
            "royal-navy",
            "emerald-palace",
            "black-onyx",
            "ruby-velvet",
            "celestial-cyan",
            "imperial-amethyst",
            "carrara-marble",
            "imperial-bronze",
            "midnight-sapphire",
            "pearl-marble",
        )
        assertEquals(expectedIds, themes.map { it.id })
        assertEquals(10, themes.map { it.atmosphere }.toSet().size)

        // All ten app themes are dark; Ambient has its own independent preference.
        assertTrue(themes.all { !it.isLight })

        // Exact ARGB token verification for all ten premium palettes
        val expectedHexTokens = mapOf(
            "royal-navy" to listOf(
                0xFF050B18, 0xFF10264A, 0xFF183D65, 0xFF10223B, 0xFFF4F8FF, 0xFFC7D5E8,
                0xFF8EA5C1, 0xFF09182B, 0xFF102B45, 0xFF5B9DFF, 0xFF385A82, 0xFFA8D1FF,
                0xFF173A60, 0xFFF07887, 0xFF59D6A0, 0xFF2867A6, 0xFF4B83C8,
            ),
            "emerald-palace" to listOf(
                0xFF04120F, 0xFF10372C, 0xFF1A5945, 0xFF0E2A20, 0xFFF4FBF7, 0xFFC7E7D9,
                0xFF8EAA98, 0xFF081D17, 0xFF103326, 0xFF36D6A0, 0xFF356C59, 0xFFA0F0D0,
                0xFF174735, 0xFFE8797D, 0xFF66D49A, 0xFF1C8A64, 0xFF4BBE91,
            ),
            "black-onyx" to listOf(
                0xFF080907, 0xFF1C1A14, 0xFF3B3020, 0xFF211D15, 0xFFF8F5EC, 0xFFD9D1C0,
                0xFFA79A80, 0xFF12110D, 0xFF211C14, 0xFFD9B36C, 0xFF665438, 0xFFF0D8A4,
                0xFF3A2E1C, 0xFFE97C6F, 0xFF7AC99B, 0xFF9D783D, 0xFFD9B36C,
            ),
            "ruby-velvet" to listOf(
                0xFF10070B, 0xFF351321, 0xFF5A2035, 0xFF2A111E, 0xFFFFF5F6, 0xFFE8CDD4,
                0xFFB895A2, 0xFF1B0C14, 0xFF321524, 0xFFF06A82, 0xFF71354B, 0xFFFFC0CE,
                0xFF4B1C30, 0xFFFF7187, 0xFF72CEA2, 0xFFB83C61, 0xFFE05B79,
            ),
            "celestial-cyan" to listOf(
                0xFF041116, 0xFF0B3039, 0xFF12515A, 0xFF0D2931, 0xFFF2FEFF, 0xFFC0E5E8,
                0xFF82B7BE, 0xFF071D24, 0xFF0D333C, 0xFF36C6C9, 0xFF286A73, 0xFFA1F0EE,
                0xFF14505B, 0xFFFF7E91, 0xFF65D8AE, 0xFF0D8796, 0xFF2FB4B9,
            ),
            "imperial-amethyst" to listOf(
                0xFF0B0714, 0xFF28163E, 0xFF48265F, 0xFF241532, 0xFFFBF6FF, 0xFFDCCCE8,
                0xFFA998B9, 0xFF170D24, 0xFF2C1A3D, 0xFFB58AFF, 0xFF60447A, 0xFFE0C9FF,
                0xFF41265A, 0xFFF07E98, 0xFF6DCDA5, 0xFF7847B0, 0xFFA36BD2,
            ),
            "carrara-marble" to listOf(
                0xFF090C11, 0xFF202A36, 0xFF3B4655, 0xFF1B232D, 0xFFF7F8FA, 0xFFCED3DA,
                0xFF929DA9, 0xFF111821, 0xFF222D39, 0xFFB9C8D9, 0xFF566579, 0xFFE5EDF5,
                0xFF344456, 0xFFE77A83, 0xFF72C79D, 0xFF6E849C, 0xFFA3B4C7,
            ),
            "imperial-bronze" to listOf(
                0xFF100A08, 0xFF382016, 0xFF60351F, 0xFF2B1A12, 0xFFFFF8F2, 0xFFE5D2BB,
                0xFFB29A7F, 0xFF1B100B, 0xFF362015, 0xFFE69A62, 0xFF795039, 0xFFFFD0A8,
                0xFF4C2B19, 0xFFE97C6F, 0xFF7AC99B, 0xFFA85B31, 0xFFD07C49,
            ),
            "midnight-sapphire" to listOf(
                0xFF07091B, 0xFF171D4B, 0xFF293A75, 0xFF171C3A, 0xFFF5F6FF, 0xFFCDD2F4,
                0xFF969FC9, 0xFF10142D, 0xFF1D2550, 0xFF7185FF, 0xFF4653A0, 0xFFC0C8FF,
                0xFF2D3973, 0xFFEF7889, 0xFF68D2A2, 0xFF4659CC, 0xFF7788F0,
            ),
            "pearl-marble" to listOf(
                0xFF10090D, 0xFF35202A, 0xFF53333D, 0xFF2A1922, 0xFFFFF5F5, 0xFFE8CDD4,
                0xFFB895A2, 0xFF1A1017, 0xFF35212B, 0xFFE6A4A0, 0xFF754D5A, 0xFFF8D0C9,
                0xFF4C2E3A, 0xFFFF7187, 0xFF72CEA2, 0xFFB86E78, 0xFFD58D91,
            ),
        )

        themes.forEach { theme ->
            val p = theme.palette
            val actual = listOf(
                p.background, p.backgroundTop, p.backgroundBottom, p.card, p.text, p.textSecondary,
                p.muted, p.panel, p.surface, p.accent, p.border, p.focus,
                p.selected, p.danger, p.success, p.waveOne, p.waveTwo,
            ).map { it.toArgb() }
            val expected = expectedHexTokens.getValue(theme.id).map { it.toInt() }
            assertEquals("Exact SONIX token mismatch for ${theme.id}", expected, actual)
        }
    }

    @Test
    fun `All 10 premium dark themes preserve strong text, button, and accent contrast`() {
        AppThemeCatalog.allThemes.forEach { theme ->
            val p = theme.palette
            assertTrue("${theme.id}: text on background", contrastRatio(p.text, p.background) >= 4.5f)
            assertTrue("${theme.id}: text on card", contrastRatio(p.text, p.card) >= 4.5f)
            assertTrue("${theme.id}: text on panel", contrastRatio(p.text, p.panel) >= 4.5f)
            assertTrue("${theme.id}: text on surface", contrastRatio(p.text, p.surface) >= 4.5f)
            assertTrue("${theme.id}: text on selected", contrastRatio(p.text, p.selected) >= 4.5f)
            assertTrue("${theme.id}: textSecondary on card", contrastRatio(p.textSecondary, p.card) >= 4.0f)
            assertTrue("${theme.id}: onAccent on accent", contrastRatio(p.onAccent, p.accent) >= 4.5f)
            assertTrue("${theme.id}: onDanger on danger", contrastRatio(p.onDanger, p.danger) >= 4.0f)
            assertTrue("${theme.id}: accentText on card", contrastRatio(p.accentText, p.card) >= 3.5f)
            assertTrue("${theme.id}: accentText on background", contrastRatio(p.accentText, p.background) >= 3.5f)
        }
        assertEquals(ThemeAtmosphere.entries.size, 10)
    }

    @Test
    fun `UserPreferencesDataStore persists global app theme independently from screensaver ambient theme`() = runTest {
        val store = UserPreferencesDataStore(context)

        // Fresh UserPreferences defaults to Royal Navy
        assertEquals("royal-navy", com.liquidtv.app.domain.model.UserPreferences().appTheme)
        assertEquals("royal-navy", store.getAppTheme().first())
        val initialAmbient = store.getAmbientTheme().first()

        // Persisting every SONIX theme leaves ambient theme untouched
        AppThemeCatalog.allThemes.forEach { theme ->
            store.setAppTheme(theme.id)
            val prefs = store.userPreferences.first()
            assertEquals(theme.id, prefs.appTheme)
            assertEquals(initialAmbient, prefs.theme)
        }

        // Unknown / corrupted ID falls back safely to Royal Navy
        store.setAppTheme("non_existent_theme")
        assertEquals("royal-navy", store.getAppTheme().first())
        assertEquals("royal-navy", AppThemeCatalog.resolve(null).id)
        assertEquals("royal-navy", AppThemeCatalog.resolve("  ").id)
        store.setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
    }

    @Test
    fun `Settings Themes section displays all 10 themes and switches global AppTheme immediately`() {
        val store = UserPreferencesDataStore(context)
        runBlocking {
            store.setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)
        }

        val settingsVm = SettingsViewModel(
            context = context,
            userPreferencesRepository = store,
            channelRepository = StubChannelRepository(),
            subscriptionRepository = StubSubscriptionRepository(),
        )
        val updateVm = AppUpdateViewModel(AppUpdateManager(context, OkHttpClient(), store), store)

        var activeThemeId = ""
        var activeIsLight = false
        var activeBgArgb = 0

        composeRule.setContent {
            val prefs by store.userPreferences.collectAsState(initial = com.liquidtv.app.domain.model.UserPreferences())
            val resolvedTheme = AppThemeCatalog.resolve(prefs.appTheme)
            LiquidTVTheme(theme = resolvedTheme) {
                activeThemeId = AppTheme.current.id
                activeIsLight = AppTheme.current.isLight
                activeBgArgb = AppTheme.colors.background.toArgb()

                Box(Modifier.width(1080.dp).height(700.dp)) {
                    SettingsScreen(
                        viewModel = settingsVm,
                        updateViewModel = updateVm,
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { activeThemeId == "royal-navy" }
        composeRule.waitForIdle()
        assertEquals("royal-navy", activeThemeId)
        assertFalse(activeIsLight)

        // Open the new "الثيمات" tab in Settings
        composeRule.onNodeWithTag("settings_section_themes").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("الثيم المفعّل حالياً", substring = true).assertIsDisplayed()

        // Select Rose Gold Noir (stable stored ID: pearl-marble)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_pearl-marble"))
        composeRule.onNodeWithTag("theme_card_pearl-marble").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            activeThemeId == "pearl-marble" && settingsVm.uiState.value.appTheme == "pearl-marble"
        }
        composeRule.waitForIdle()

        assertEquals("pearl-marble", activeThemeId)
        assertFalse(activeIsLight)
        assertEquals(0xFF10090D.toInt(), activeBgArgb)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_pearl-marble"))
        composeRule.onNodeWithTag("theme_card_pearl-marble")
            .assertIsDisplayed()
            .assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("theme_active_badge_pearl-marble", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("theme_active_badge_pearl-marble", useUnmergedTree = true)
            .assertIsDisplayed()

        // Switch immediately to Dark theme "مخمل العقيق" (Ruby Velvet)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_ruby-velvet"))
        composeRule.onNodeWithTag("theme_card_ruby-velvet").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            activeThemeId == "ruby-velvet" && settingsVm.uiState.value.appTheme == "ruby-velvet"
        }
        composeRule.waitForIdle()

        assertEquals("ruby-velvet", activeThemeId)
        assertFalse(activeIsLight)
        assertEquals(0xFF10070B.toInt(), activeBgArgb)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_ruby-velvet"))
        composeRule.onNodeWithTag("theme_card_ruby-velvet")
            .assertIsDisplayed()
            .assertIsSelected()

        // Scroll to the end and verify Black Onyx (#10, sortOrder 19) is selectable
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_black-onyx"))
        composeRule.onNodeWithTag("theme_card_black-onyx").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            activeThemeId == "black-onyx" && settingsVm.uiState.value.appTheme == "black-onyx"
        }
        composeRule.waitForIdle()

        assertEquals("black-onyx", activeThemeId)
        assertEquals(0xFF080907.toInt(), activeBgArgb)
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("theme_card_black-onyx"))
        composeRule.onNodeWithTag("theme_card_black-onyx")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

private class StubSubscriptionRepository : SubscriptionRepository {
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

private class StubChannelRepository : ChannelRepository {
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
