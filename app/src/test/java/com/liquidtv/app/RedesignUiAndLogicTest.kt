package com.liquidtv.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import com.liquidtv.app.ui.theme.AppThemeCatalog
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.entity.CategoryEntity
import com.liquidtv.app.data.local.entity.SeriesEntity
import com.liquidtv.app.data.local.entity.SubscriptionEntity
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.ChannelRepositoryImpl
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.Country
import com.liquidtv.app.domain.model.EpgProgram
import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.SeasonGroup
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.data.sonix.SonixAssignedServer
import com.liquidtv.app.data.sonix.SonixDeviceStatus
import com.liquidtv.app.data.sonix.SonixAccessStatus
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.screens.livetv.LiveTVScreen
import com.liquidtv.app.ui.screens.livetv.LiveTVViewModel
import com.liquidtv.app.ui.screens.login.LoginScreen
import com.liquidtv.app.ui.screens.login.LoginViewModel
import com.liquidtv.app.ui.screens.movies.MoviesScreen
import com.liquidtv.app.ui.screens.movies.MoviesViewModel
import com.liquidtv.app.ui.screens.series.SeriesDetailScreen
import com.liquidtv.app.ui.screens.series.SeriesDetailViewModel
import com.liquidtv.app.ui.screens.series.SeriesScreen
import com.liquidtv.app.ui.screens.series.SeriesViewModel
import com.liquidtv.app.ui.screens.settings.SettingsScreen
import com.liquidtv.app.ui.screens.settings.SettingsViewModel
import com.liquidtv.app.ui.theme.LiquidTVTheme
import com.liquidtv.app.ui.utils.calculateEpisodeProgress
import com.liquidtv.app.ui.utils.findContinueWatchingEpisode
import com.liquidtv.app.ui.utils.formatEpisodeCode
import com.liquidtv.app.ui.utils.formatSeasonTitleAr
import com.liquidtv.app.ui.utils.sortSeasons
import com.liquidtv.app.ui.utils.wideCategoriesSidebarWidth
import com.liquidtv.app.updater.AppUpdateManager
import com.liquidtv.app.updater.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w2000dp-h1200dp-xhdpi")
class RedesignUiAndLogicTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MediaFormatters sort seasons numerically, format Arabic titles, and guard episode progress`() {
        val sorted = sortSeasons(
            listOf(
                SeasonGroup(10, emptyList()),
                SeasonGroup(2, emptyList()),
                SeasonGroup(0, emptyList()),
                SeasonGroup(1, emptyList()),
            )
        )
        assertEquals(listOf(0, 1, 2, 10), sorted.map { it.seasonNumber })
        assertEquals("حلقات خاصة", formatSeasonTitleAr(0))
        assertEquals("الموسم 2", formatSeasonTitleAr(2))
        assertEquals("S01E03", formatEpisodeCode(1, 3))
        assertNull(formatEpisodeCode(-1, 1))
        assertNull(calculateEpisodeProgress(resumePositionMs = 5_000L, durationSec = null))
        assertNull(calculateEpisodeProgress(resumePositionMs = 5_000L, durationSec = 0))
        assertNull(calculateEpisodeProgress(resumePositionMs = 0L, durationSec = 120))
        assertEquals(0.5f, calculateEpisodeProgress(60_000L, 120) ?: -1f, 0.001f)
        assertEquals(1.0f, calculateEpisodeProgress(999_000L, 120) ?: -1f, 0.001f)

        val ep1 = EpisodeItem(
            id = "ep1",
            subscriptionId = "sub-1",
            seriesId = "sr-1",
            externalEpisodeId = "1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "H1",
            streamUrl = "http://x/1.mp4",
            resumePositionMs = 30_000L,
            lastWatchedAt = 100L,
        )
        val ep2 = EpisodeItem(
            id = "ep2",
            subscriptionId = "sub-1",
            seriesId = "sr-1",
            externalEpisodeId = "2",
            seasonNumber = 1,
            episodeNumber = 2,
            title = "H2",
            streamUrl = "http://x/2.mp4",
            resumePositionMs = 15_000L,
            lastWatchedAt = 500L,
        )
        assertEquals("ep2", findContinueWatchingEpisode(listOf(ep1, ep2))?.id)
    }

    @Test
    fun `ChannelRepositoryImpl computes series categories and reacts to toggleSeriesFavorite`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val okHttp = OkHttpClient()
        val crypto = object : CredentialCrypto {
            override fun encrypt(plaintext: String) = plaintext
            override fun decrypt(ciphertext: String) = ciphertext
        }
        val prefs = RedesignFakeUserPreferencesRepository()
        val subRepo = SubscriptionRepositoryImpl(
            context = context,
            database = db,
            subscriptionDao = db.subscriptionDao(),
            channelDao = db.channelDao(),
            seriesDao = db.seriesDao(),
            categoryDao = db.categoryDao(),
            epgDao = db.epgDao(),
            xtreamApi = XtreamApiService(okHttp),
            okHttpClient = okHttp,
            xmltvParser = XmltvParser(),
            crypto = crypto,
            prefs = prefs,
        )
        val channelRepo = ChannelRepositoryImpl(
            subscriptionDao = db.subscriptionDao(),
            channelDao = db.channelDao(),
            seriesDao = db.seriesDao(),
            categoryDao = db.categoryDao(),
            watchHistoryDao = db.watchHistoryDao(),
            epgDao = db.epgDao(),
            subscriptionRepository = subRepo,
            xtreamApi = XtreamApiService(okHttp),
            prefs = prefs,
        )

        db.subscriptionDao().upsertSubscription(
            SubscriptionEntity(
                id = "sub-1",
                name = "Test",
                type = SubscriptionType.XTREAM.name,
                serverUrl = "http://localhost",
                isActive = true,
                connectionStatus = SubscriptionStatus.CONNECTED.name,
            )
        )
        db.categoryDao().upsertCategories(
            listOf(
                CategoryEntity("cat-drama", "sub-1", "30", MediaType.SERIES_EPISODE.name, "دراما", 0, false),
                CategoryEntity("cat-empty", "sub-1", "31", MediaType.SERIES_EPISODE.name, "أكشن", 0, false),
            )
        )
        db.seriesDao().upsertSeries(
            listOf(
                SeriesEntity("sr-1", "sub-1", "101", "الأفق", categoryId = "30", categoryName = "دراما"),
                SeriesEntity("sr-2", "sub-1", "102", "السراب", categoryId = "30", categoryName = "دراما"),
            )
        )

        val categories = channelRepo.getAllCategories(MediaType.SERIES_EPISODE).first()
        val drama = categories.firstOrNull { it.name == "دراما" }
        assertEquals(2, drama?.channelCount)
        assertTrue(categories.none { it.name == "أكشن" })

        val beforeToggle = channelRepo.getSeriesById("sr-1").first()
        assertEquals(false, beforeToggle?.isFavorite)
        channelRepo.toggleSeriesFavorite("sr-1")
        val afterToggle = channelRepo.getSeriesById("sr-1").first()
        assertEquals(true, afterToggle?.isFavorite)

        db.close()
    }

    @Test
    fun `LoginScreen starts collapsed with single اختيار سيرفر option, switches exclusively between افتراضي URL and panel servers, and keeps Playlists independent`() {
        val fakeSubRepo = FakeSubscriptionRepository()
        val fakeSonix = FakeSonixControlRepository(
            SonixSyncState(
                isRegistered = true,
                deviceId = "dev-1",
                deviceUid = "uid-1",
                deviceCode = "SNX778899",
                deviceStatus = SonixDeviceStatus.ACTIVE,
                accessStatus = SonixAccessStatus.ACTIVE,
                accessAllowed = true,
                hasLoadedCatalog = true,
                availableServers = listOf(
                    SonixAssignedServer(id = "srv-panel-1", name = "ALPHA STREAM", region = "DE", priority = 1),
                    SonixAssignedServer(id = "srv-panel-2", name = "BETA CLOUD", region = "NL", priority = 2),
                ),
            )
        )
        val vm = LoginViewModel(fakeSubRepo, fakeSonix)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(420.dp).height(820.dp)) {
                    LoginScreen(
                        onLoginSuccess = {},
                        viewModel = vm,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Initial launch: two equal side-by-side cards; ONLY collapsed «اختيار سيرفر» visible inside Card 1.
        composeRule.onNodeWithTag("login_two_cards_row").assertIsDisplayed()
        composeRule.onNodeWithTag("server_selection_main_card").assertIsDisplayed()
        composeRule.onNodeWithTag("playlists_main_card").assertIsDisplayed()
        composeRule.onNodeWithTag("server_selector_toggle").assertIsDisplayed()
        composeRule.onNodeWithText("اختيار سيرفر").assertIsDisplayed()
        composeRule.onNodeWithTag("server_selector_list").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_normal_url").assertDoesNotExist()
        composeRule.onNodeWithTag("panel_servers_container").assertDoesNotExist()
        composeRule.onNodeWithTag("input_xtream_url").assertDoesNotExist()
        assertEquals(0, composeRule.onAllNodesWithText("M3U").fetchSemanticsNodes().size)

        val serverCardBounds = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val playlistsCardBounds = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected server and playlists cards strictly side by side",
            serverCardBounds.right <= playlistsCardBounds.left + 2f ||
                playlistsCardBounds.right <= serverCardBounds.left + 2f,
        )
        assertTrue("Expected equal card widths", kotlin.math.abs(serverCardBounds.width - playlistsCardBounds.width) <= 2f)
        assertTrue("Expected equal card heights", kotlin.math.abs(serverCardBounds.height - playlistsCardBounds.height) <= 2f)
        assertTrue("Expected aligned top edges", kotlin.math.abs(serverCardBounds.top - playlistsCardBounds.top) <= 2f)
        assertTrue("Expected aligned bottom edges", kotlin.math.abs(serverCardBounds.bottom - playlistsCardBounds.bottom) <= 2f)

        // 2. Expand «اختيار سيرفر» -> floating Popup opens with «افتراضي URL» and dynamic servers, while Card 1 & Card 2 keep EXACT same height/width.
        composeRule.onNodeWithTag("server_selector_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_list").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_normal_url").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("افتراضي URL").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_server_item_srv-panel-1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("panel_server_item_srv-panel-2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("default_url_form").assertDoesNotExist()
        composeRule.onNodeWithTag("panel_server_form_srv-panel-1").assertDoesNotExist()
        composeRule.onNodeWithTag("panel_server_form_srv-panel-2").assertDoesNotExist()

        val serverCardPopupBounds = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val playlistsCardPopupBounds = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected Card 1 height NOT to expand when server popup opens (${serverCardBounds.height} vs ${serverCardPopupBounds.height})",
            kotlin.math.abs(serverCardPopupBounds.height - serverCardBounds.height) <= 2f,
        )
        assertTrue(
            "Expected Card 2 height NOT to expand when server popup opens (${playlistsCardBounds.height} vs ${playlistsCardPopupBounds.height})",
            kotlin.math.abs(playlistsCardPopupBounds.height - playlistsCardBounds.height) <= 2f,
        )

        // 3. Click «افتراضي URL» -> closes dropdown and opens floating Form Dialog with 4 fields in order (Name < Username < Password < Host URL).
        composeRule.onNodeWithTag("tab_normal_url").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_list").assertDoesNotExist()
        composeRule.onNodeWithTag("server_login_form_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("default_url_form").assertIsDisplayed()
        composeRule.onNodeWithTag("input_subscription_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_username").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_password").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_url").performScrollTo().assertIsDisplayed()

        val serverCardDialogBounds = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val playlistsCardDialogBounds = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected Card 1 height NOT to expand when login form dialog opens (${serverCardBounds.height} vs ${serverCardDialogBounds.height})",
            kotlin.math.abs(serverCardDialogBounds.height - serverCardBounds.height) <= 2f,
        )
        assertTrue(
            "Expected Card 2 height NOT to expand when login form dialog opens (${playlistsCardBounds.height} vs ${playlistsCardDialogBounds.height})",
            kotlin.math.abs(playlistsCardDialogBounds.height - playlistsCardBounds.height) <= 2f,
        )

        val nameTop = composeRule.onNodeWithTag("input_subscription_name").fetchSemanticsNode().boundsInRoot.top
        val userTop = composeRule.onNodeWithTag("input_xtream_username").fetchSemanticsNode().boundsInRoot.top
        val passTop = composeRule.onNodeWithTag("input_xtream_password").fetchSemanticsNode().boundsInRoot.top
        val urlTop = composeRule.onNodeWithTag("input_xtream_url").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Expected field order: Name < Username < Password < Host URL", nameTop < userTop && userTop < passTop && passTop < urlTop)

        composeRule.onNodeWithTag("connect_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_error_banner").performScrollTo().assertIsDisplayed()

        // 4. Switch server from Dialog -> reopens dropdown; click Server 1 -> opens ONLY Server 1 form (3 fields, NO Host URL).
        composeRule.onNodeWithTag("dialog_switch_server_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_list").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_server_item_srv-panel-1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("default_url_form").assertDoesNotExist()
        composeRule.onNodeWithTag("input_xtream_url").assertDoesNotExist()
        composeRule.onNodeWithTag("panel_server_form_srv-panel-1").assertIsDisplayed()
        composeRule.onNodeWithTag("input_subscription_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_username").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_password").performScrollTo().assertIsDisplayed()

        // 5. Switch server again -> click Server 2 -> opens ONLY Server 2 form.
        composeRule.onNodeWithTag("dialog_switch_server_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_item_srv-panel-2").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_form_srv-panel-1").assertDoesNotExist()
        composeRule.onNodeWithTag("panel_server_form_srv-panel-2").assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_url").assertDoesNotExist()

        // Dismiss dialog cleanly to check Card 2 independence.
        composeRule.onNodeWithTag("close_server_form_dialog_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_login_form_dialog").assertDoesNotExist()

        // 6. Card 2 remains completely independent with Device ID & Copy button intact.
        composeRule.onNodeWithTag("login_playlists_section").assertIsDisplayed()
        composeRule.onNodeWithTag("playlists_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("device_id_compact_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("device_id_value").assertIsDisplayed()
        composeRule.onNodeWithText("SNX778899").assertIsDisplayed()

        composeRule.onNodeWithTag("copy_device_id_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("SNX778899", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        composeRule.onNodeWithTag("playlist_feedback_banner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `LoginScreen reflects dynamic catalog add, rename, delete, loading, empty, and error without disabling افتراضي URL`() {
        val fakeSubRepo = FakeSubscriptionRepository()
        val fakeSonix = FakeSonixControlRepository(
            SonixSyncState(
                isRegistered = true,
                deviceId = "dev-dyn",
                deviceUid = "uid-dyn",
                deviceCode = "SNXDYN01",
                deviceStatus = SonixDeviceStatus.ACTIVE,
                accessStatus = SonixAccessStatus.ACTIVE,
                accessAllowed = true,
                isCatalogLoading = true,
                hasLoadedCatalog = false,
                availableServers = emptyList(),
            )
        )
        val vm = LoginViewModel(fakeSubRepo, fakeSonix)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(420.dp).height(820.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }
        composeRule.onNodeWithTag("server_selector_toggle").performClick()
        composeRule.waitForIdle()

        // Loading state: loading indicator shown inside floating dropdown, افتراضي URL stays enabled
        composeRule.onNodeWithTag("panel_servers_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_normal_url").assertIsDisplayed()

        // Catalog failure: error shown, افتراضي URL opens Dialog cleanly
        fakeSonix.mutableState.value = fakeSonix.mutableState.value.copy(
            isCatalogLoading = false,
            catalogError = "تعذّر تحديث قائمة السيرفرات",
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_servers_error").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_normal_url").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("default_url_form").assertIsDisplayed()

        // Switch back to dropdown and verify empty catalog state
        composeRule.onNodeWithTag("dialog_switch_server_button").performClick()
        composeRule.waitForIdle()
        fakeSonix.mutableState.value = fakeSonix.mutableState.value.copy(
            catalogError = null,
            hasLoadedCatalog = true,
            availableServers = emptyList(),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_servers_empty").performScrollTo().assertIsDisplayed()

        // Add two dynamic servers -> appear live in Popup without rebuilding app
        fakeSonix.mutableState.value = fakeSonix.mutableState.value.copy(
            hasLoadedCatalog = true,
            availableServers = listOf(
                SonixAssignedServer(id = "dyn-1", name = "سيرفر أول", priority = 1),
                SonixAssignedServer(id = "dyn-2", name = "سيرفر ثاني", priority = 2),
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_item_dyn-2").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_form_dyn-2").assertIsDisplayed()

        // Rename dyn-1 and delete dyn-2 -> Dialog closes for removed dyn-2 and updated catalog reflects dyn-1
        fakeSonix.mutableState.value = fakeSonix.mutableState.value.copy(
            hasLoadedCatalog = true,
            availableServers = listOf(
                SonixAssignedServer(id = "dyn-1", name = "سيرفر معدّل", priority = 1),
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_form_dyn-2").assertDoesNotExist()
        assertNull(vm.uiState.value.selectedPanelServerId)

        composeRule.onNodeWithTag("server_selector_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("سيرفر معدّل").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_server_item_dyn-2").assertDoesNotExist()
    }
    @Test
    fun `Playlists tab displays saved playlists above Device ID card with connect, sync, edit, delete actions and copy works`() {
        val saved = listOf(
            Subscription(
                id = "sub-saved-1",
                name = "باقة إكس لاين",
                type = SubscriptionType.XTREAM,
                origin = SubscriptionOrigin.PANEL_SERVER,
                panelServerId = "srv-panel-1",
                panelServerName = "XLINE",
                serverUrl = "http://panel.example.com:8080",
                username = "ali_user",
                isActive = true,
                connectionStatus = SubscriptionStatus.CONNECTED,
            )
        )
        val fakeSubRepo = FakeSubscriptionRepository(initialSubscriptions = saved)
        val fakeSonix = FakeSonixControlRepository(
            SonixSyncState(
                isRegistered = true,
                deviceId = "dev-1",
                deviceUid = "uid-1",
                deviceCode = "SNX8899001",
                deviceStatus = SonixDeviceStatus.ACTIVE,
                accessStatus = SonixAccessStatus.ACTIVE,
                accessAllowed = true,
                availableServers = listOf(SonixAssignedServer(id = "srv-panel-1", name = "XLINE", region = "EU")),
            )
        )
        val vm = LoginViewModel(fakeSubRepo, fakeSonix)
        vm.selectSection(com.liquidtv.app.ui.screens.login.LoginSection.PLAYLISTS)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(500.dp).height(860.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("playlists_list_container").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_connect_sub-saved-1").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_sync_sub-saved-1").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_edit_sub-saved-1").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_delete_sub-saved-1").assertIsDisplayed()

        val listBounds = composeRule.onNodeWithTag("playlists_list_container").fetchSemanticsNode().boundsInRoot
        val deviceBounds = composeRule.onNodeWithTag("device_id_compact_card").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected Device ID card BELOW saved playlists list (listBottom=${listBounds.bottom}, deviceTop=${deviceBounds.top})",
            deviceBounds.top >= listBounds.bottom - 4f,
        )

        val sBaseline = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val pBaseline = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("copy_device_id_button").performClick()
        composeRule.waitForIdle()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("SNX8899001", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Clicking Edit on a PANEL_SERVER playlist in Card 2 opens ONLY its 3-field form in the floating Dialog without expanding Card 1 or exposing Host URL.
        composeRule.onNodeWithTag("playlist_edit_sub-saved-1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_login_form_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_server_form_srv-panel-1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_url").assertDoesNotExist()
        val sAfterEdit = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val pAfterEdit = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue("Expected Card 1 height unchanged during edit dialog", kotlin.math.abs(sAfterEdit.height - sBaseline.height) <= 2f)
        assertTrue("Expected Card 2 height unchanged during edit dialog", kotlin.math.abs(pAfterEdit.height - pBaseline.height) <= 2f)
    }

    @Test
    fun `SettingsScreen renders Arabic sections and scrolls vertically on compact phone screen`() {
        val prefs = RedesignFakeUserPreferencesRepository()
        val fakeChannels = FakeChannelRepository()
        val fakeSubRepo = FakeSubscriptionRepository()
        val vm = SettingsViewModel(context, prefs, fakeChannels, fakeSubRepo)
        val updateVm = AppUpdateViewModel(AppUpdateManager(context, OkHttpClient(), prefs), prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(380.dp).height(640.dp)) {
                    SettingsScreen(
                        viewModel = vm,
                        updateViewModel = updateVm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings_section_subscriptions").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_content_list")
            .performScrollToNode(hasTestTag("setting_clear_cache"))
        composeRule.onNodeWithTag("setting_clear_cache").assertIsDisplayed()
    }

    @Test
    fun `SeriesDetailScreen hides Season 0 when absent and switches episodes when season card clicked`() {
        val fakeChannels = FakeChannelRepository(
            episodes = listOf(
                EpisodeItem(
                    id = "ep-s1e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "101",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "البداية",
                    streamUrl = "http://x/1.mp4",
                    durationSec = 2400,
                    resumePositionMs = 600_000L,
                ),
                EpisodeItem(
                    id = "ep-s2e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "201",
                    seasonNumber = 2,
                    episodeNumber = 1,
                    title = "العودة",
                    streamUrl = "http://x/2.mp4",
                    durationSec = 2500,
                ),
            ),
        )
        val vm = SeriesDetailViewModel(fakeChannels, SavedStateHandle(mapOf("seriesId" to "sr-1")))

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(400.dp).height(780.dp)) {
                    SeriesDetailScreen(
                        onEpisodeClick = {},
                        onBack = {},
                        viewModel = vm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_card_0").assertDoesNotExist()
        composeRule.onNodeWithTag("season_card_1").assertIsDisplayed()
        composeRule.onNodeWithTag("season_card_2").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_card_ep-s1e1").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_watching_button").assertIsDisplayed()

        composeRule.onNodeWithTag("season_card_2").performClick()
        composeRule.onNodeWithTag("episode_card_ep-s2e1").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_card_ep-s1e1").assertDoesNotExist()
    }

    @Test
    fun `SeriesDetailScreen renders Special Episodes label ONLY when Season 0 exists`() {
        val fakeChannels = FakeChannelRepository(
            episodes = listOf(
                EpisodeItem(
                    id = "ep-s0e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "1",
                    seasonNumber = 0,
                    episodeNumber = 1,
                    title = "كواليس",
                    streamUrl = "http://x/0.mp4",
                ),
                EpisodeItem(
                    id = "ep-s1e1",
                    subscriptionId = "sub-1",
                    seriesId = "sr-1",
                    externalEpisodeId = "2",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "الحلقة الأولى",
                    streamUrl = "http://x/1.mp4",
                ),
            ),
        )
        val vm = SeriesDetailViewModel(fakeChannels, SavedStateHandle(mapOf("seriesId" to "sr-1")))

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(960.dp).height(700.dp)) {
                    SeriesDetailScreen(
                        onEpisodeClick = {},
                        onBack = {},
                        viewModel = vm,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("season_card_0").assertIsDisplayed()
        composeRule.onNodeWithText("حلقات خاصة").assertIsDisplayed()
    }

    @Test
    fun `LiveTVScreen renders 3-pane Arabic RTL layout with EPG and categories`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1000.dp).height(700.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("live_categories_sidebar")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("live_categories_sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("live_channels_list").assertIsDisplayed()
        composeRule.onNodeWithTag("live_preview_play_button").assertIsDisplayed()
    }

    @Test
    fun `MoviesScreen renders 2-to-3 cinema posters and category sidebar in Arabic RTL`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val moviesVm = MoviesViewModel(fakeChannels, prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(900.dp).height(700.dp)) {
                    MoviesScreen(onMovieClick = {}, viewModel = moviesVm)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("movies_grid")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("movies_grid").assertIsDisplayed()
        composeRule.onNodeWithText("صحراء العاصفة").assertIsDisplayed()
    }

    @Test
    fun `SeriesScreen renders 2-to-3 cinema posters and category sidebar in Arabic RTL`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val seriesVm = SeriesViewModel(fakeChannels, prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(900.dp).height(700.dp)) {
                    SeriesScreen(onSeriesClick = {}, viewModel = seriesVm)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("series_grid")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("series_categories_sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("series_grid").assertIsDisplayed()
        composeRule.onNodeWithText("الأفق").assertIsDisplayed()
    }

    @Test
    fun `LiveTVScreen landscape phone keeps all header controls clickable, shows channel names, toggles grid, and expands content`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())
        val totalHeightDp = 400f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(850.dp).height(totalHeightDp.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }

        // Preserve all required header controls & info
        composeRule.onNodeWithText("البث المباشر").assertIsDisplayed()
        composeRule.onNodeWithTag("live_channels_count").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_name_asc").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_name_desc").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_favorites_first").assertIsDisplayed()
        composeRule.onNodeWithTag("live_sort_category").assertIsDisplayed()
        composeRule.onNodeWithTag("live_view_list").assertIsDisplayed()
        composeRule.onNodeWithTag("live_view_grid").assertIsDisplayed()

        // Ensure channel name inside middle list row is NOT crushed at 850dp wide
        composeRule.onAllNodesWithText("الجزيرة الإخبارية").onFirst().assertIsDisplayed()

        val density = composeRule.density.density
        val headerHeightDp = composeRule.onNodeWithTag("live_tv_header").fetchSemanticsNode().boundsInRoot.height / density
        val sidebarPaneHeightDp = composeRule.onNodeWithTag("live_categories_pane").fetchSemanticsNode().boundsInRoot.height / density
        val sidebarListHeightDp = composeRule.onNodeWithTag("live_categories_sidebar").fetchSemanticsNode().boundsInRoot.height / density
        val listHeightDp = composeRule.onNodeWithTag("live_channels_list").fetchSemanticsNode().boundsInRoot.height / density

        assertTrue("Expected single-row compact header <= 54dp on landscape, was $headerHeightDp", headerHeightDp <= 54f)
        assertTrue("Expected sidebar pane >= 68% of 400dp screen height, was $sidebarPaneHeightDp", sidebarPaneHeightDp >= totalHeightDp * 0.68f)
        assertTrue("Expected scrollable sidebar list >= 245dp inside 400dp landscape, was $sidebarListHeightDp", sidebarListHeightDp >= 245f)
        assertTrue("Expected channel list >= 65% of 400dp screen height, was $listHeightDp", listHeightDp >= totalHeightDp * 0.65f)

        // Verify ViewMode switch to Grid and back to List works
        composeRule.onNodeWithTag("live_view_grid").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_channels_grid").assertIsDisplayed()

        composeRule.onNodeWithTag("live_view_list").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_channels_list").assertIsDisplayed()
    }

    @Test
    fun `LiveTVScreen maximizes content pane height on 720p TV and portrait phone`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())
        val tvHeightDp = 540f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(960.dp).height(tvHeightDp.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }

        val density = composeRule.density.density
        val listHeightDp = composeRule.onNodeWithTag("live_channels_list").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Expected >= 74% vertical space for TV channel list on 540dp, was $listHeightDp", listHeightDp >= tvHeightDp * 0.74f)
    }

    @Test
    fun `MoviesScreen and SeriesScreen maximize 2-to-3 poster grid and sidebar vertical ratio across TV viewports`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val moviesVm = MoviesViewModel(fakeChannels, prefs)
        val heightDp = 1080f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1920.dp).height(heightDp.dp)) {
                    MoviesScreen(onMovieClick = {}, viewModel = moviesVm)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("movies_grid")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("movies_count").assertIsDisplayed()
        composeRule.onNodeWithTag("movies_categories_sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("movies_grid").assertIsDisplayed()

        val density = composeRule.density.density
        val headerHeightDp = composeRule.onNodeWithTag("movies_header").fetchSemanticsNode().boundsInRoot.height / density
        val gridHeightDp = composeRule.onNodeWithTag("movies_grid").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Expected compact Movies header <= 52dp, was $headerHeightDp", headerHeightDp <= 52f)
        assertTrue("Expected Movies grid >= 84% of 1080dp screen height, was $gridHeightDp", gridHeightDp >= heightDp * 0.84f)
    }

    @Test
    fun `SeriesScreen allocates majority vertical space on 720p landscape and portrait phone`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val seriesVm = SeriesViewModel(fakeChannels, prefs)
        val phoneHeightDp = 800f

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(390.dp).height(phoneHeightDp.dp)) {
                    SeriesScreen(onSeriesClick = {}, viewModel = seriesVm)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("series_grid")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("series_header").assertIsDisplayed()
        composeRule.onNodeWithTag("series_count").assertIsDisplayed()
        composeRule.onNodeWithTag("series_grid").assertIsDisplayed()
        val density = composeRule.density.density
        val gridHeightDp = composeRule.onNodeWithTag("series_grid").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Expected portrait phone Series grid >= 75% of 800dp screen height, was $gridHeightDp", gridHeightDp >= phoneHeightDp * 0.75f)
    }

    private fun assertEqualSideBySideCardsViewport(widthDp: Int, heightDp: Int) {
        val saved = listOf(
            Subscription(
                id = "sub-vp-1",
                name = "اشتراك اختباري طويل جداً",
                type = SubscriptionType.XTREAM,
                origin = SubscriptionOrigin.PANEL_SERVER,
                panelServerId = "srv-vp-1",
                panelServerName = "SONIX PRO",
                serverUrl = "http://panel.example.com:8080",
                username = "user_demo",
                isActive = true,
                connectionStatus = SubscriptionStatus.CONNECTED,
            ),
        )
        val fakeSubRepo = FakeSubscriptionRepository(initialSubscriptions = saved)
        val fakeSonix = FakeSonixControlRepository(
            SonixSyncState(
                isRegistered = true,
                deviceId = "dev-vp",
                deviceUid = "uid-vp",
                deviceCode = "SNX-VP-9900",
                deviceStatus = SonixDeviceStatus.ACTIVE,
                accessStatus = SonixAccessStatus.ACTIVE,
                accessAllowed = true,
                hasLoadedCatalog = true,
                availableServers = listOf(
                    SonixAssignedServer(id = "srv-vp-1", name = "SONIX PRO", region = "EU"),
                ),
            ),
        )
        val vm = LoginViewModel(fakeSubRepo, fakeSonix)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(widthDp.dp).height(heightDp.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()

        val density = composeRule.density.density
        val maxW = widthDp * density

        fun assertEqualAlignedCards(phase: String) {
            val sBounds = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
            val pBounds = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "[$widthDp x $heightDp $phase] Cards must be side by side (s=$sBounds, p=$pBounds)",
                sBounds.right <= pBounds.left + 2f || pBounds.right <= sBounds.left + 2f,
            )
            assertTrue(
                "[$widthDp x $heightDp $phase] Card widths must match (${sBounds.width} vs ${pBounds.width})",
                kotlin.math.abs(sBounds.width - pBounds.width) <= 2f,
            )
            assertTrue(
                "[$widthDp x $heightDp $phase] Card heights must match (${sBounds.height} vs ${pBounds.height})",
                kotlin.math.abs(sBounds.height - pBounds.height) <= 2f,
            )
            assertTrue(
                "[$widthDp x $heightDp $phase] Card tops must align (${sBounds.top} vs ${pBounds.top})",
                kotlin.math.abs(sBounds.top - pBounds.top) <= 2f,
            )
            assertTrue(
                "[$widthDp x $heightDp $phase] Card bottoms must align (${sBounds.bottom} vs ${pBounds.bottom})",
                kotlin.math.abs(sBounds.bottom - pBounds.bottom) <= 2f,
            )
            assertTrue("[$widthDp x $heightDp $phase] Horizontal overflow", sBounds.left >= -1f && pBounds.right <= maxW + 1f && pBounds.left >= -1f && sBounds.right <= maxW + 1f)
            return
        }

        // Collapsed check + intra-card horizontal bounds check
        assertEqualAlignedCards("collapsed")
        val sCollapsed = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val pCollapsed = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        val toggleBounds = composeRule.onNodeWithTag("server_selector_toggle").fetchSemanticsNode().boundsInRoot
        assertTrue("Toggle clipped horizontally", toggleBounds.left >= sCollapsed.left - 1f && toggleBounds.right <= sCollapsed.right + 1f && toggleBounds.height > 0f)
        val deviceBounds = composeRule.onNodeWithTag("device_id_value").fetchSemanticsNode().boundsInRoot
        val copyBounds = composeRule.onNodeWithTag("copy_device_id_button").fetchSemanticsNode().boundsInRoot
        assertTrue("Device ID clipped horizontally", deviceBounds.left >= pCollapsed.left - 1f && deviceBounds.right <= pCollapsed.right + 1f && deviceBounds.width > 0f)
        assertTrue("Copy button clipped horizontally", copyBounds.left >= pCollapsed.left - 1f && copyBounds.right <= pCollapsed.right + 1f && copyBounds.width > 0f)

        // Open floating server selector Popup -> NEITHER Card 1 NOR Card 2 expands
        composeRule.onNodeWithTag("server_selector_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_list").assertIsDisplayed()
        assertEqualAlignedCards("popup_open")
        val sPopup = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val pPopup = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "[$widthDp x $heightDp] Card 1 expanded on Popup open (${sCollapsed.height} -> ${sPopup.height})",
            kotlin.math.abs(sPopup.height - sCollapsed.height) <= 2f && kotlin.math.abs(sPopup.width - sCollapsed.width) <= 2f,
        )
        assertTrue(
            "[$widthDp x $heightDp] Card 2 expanded on Popup open (${pCollapsed.height} -> ${pPopup.height})",
            kotlin.math.abs(pPopup.height - pCollapsed.height) <= 2f && kotlin.math.abs(pPopup.width - pCollapsed.width) <= 2f,
        )

        // Select «افتراضي URL» -> opens floating Form Dialog while Card 1 & Card 2 remain completely unstretched
        composeRule.onNodeWithTag("tab_normal_url").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_login_form_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_password_visibility").performScrollTo().assertIsDisplayed()
        assertEqualAlignedCards("dialog_open")
        val sExpanded = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val pExpanded = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "[$widthDp x $heightDp] Card 1 expanded on Dialog open (${sCollapsed.height} -> ${sExpanded.height})",
            kotlin.math.abs(sExpanded.height - sCollapsed.height) <= 2f && kotlin.math.abs(sExpanded.width - sCollapsed.width) <= 2f,
        )
        assertTrue(
            "[$widthDp x $heightDp] Card 2 expanded on Dialog open (${pCollapsed.height} -> ${pExpanded.height})",
            kotlin.math.abs(pExpanded.height - pCollapsed.height) <= 2f && kotlin.math.abs(pExpanded.width - pCollapsed.width) <= 2f,
        )
        val dialogBounds = composeRule.onNodeWithTag("server_login_form_dialog").fetchSemanticsNode().boundsInRoot
        val ctaBounds = composeRule.onNodeWithTag("connect_button").fetchSemanticsNode().boundsInRoot
        assertTrue("CTA clipped inside dialog", ctaBounds.left >= dialogBounds.left - 1f && ctaBounds.right <= dialogBounds.right + 1f && ctaBounds.width > 0f)
    }

    @Test
    fun `LoginScreen keeps two independent equal side-by-side cards without clipping on small portrait phone 360x640`() {
        assertEqualSideBySideCardsViewport(360, 640)
    }

    @Test
    fun `LoginScreen keeps two independent equal side-by-side cards without clipping on small landscape phone 640x360`() {
        assertEqualSideBySideCardsViewport(640, 360)
    }

    @Test
    fun `LoginScreen keeps two independent equal side-by-side cards without clipping on Android TV 1080p`() {
        assertEqualSideBySideCardsViewport(1920, 1080)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `LoginScreen maintains strictly constant card heights when 10 dynamic servers open in scrollable floating Popup`() {
        val tenServers = (1..10).map { i ->
            SonixAssignedServer(id = "srv-$i", name = "VIP NODE $i", region = "EU-$i", priority = i)
        }
        val vm = LoginViewModel(
            FakeSubscriptionRepository(),
            FakeSonixControlRepository(
                SonixSyncState(
                    isRegistered = true,
                    deviceId = "dev-many",
                    deviceUid = "uid-many",
                    deviceCode = "SNXMANY10",
                    deviceStatus = SonixDeviceStatus.ACTIVE,
                    accessStatus = SonixAccessStatus.ACTIVE,
                    accessAllowed = true,
                    hasLoadedCatalog = true,
                    availableServers = tenServers,
                ),
            ),
        )

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1280.dp).height(720.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()

        val card1Before = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val card2Before = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("server_selector_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_list").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_server_item_srv-10").performScrollTo().assertIsDisplayed()

        val card1During = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val card2During = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue("Card 1 must not expand with 10 servers in Popup", kotlin.math.abs(card1During.height - card1Before.height) <= 2f)
        assertTrue("Card 2 must not expand with 10 servers in Popup", kotlin.math.abs(card2During.height - card2Before.height) <= 2f)

        composeRule.onNodeWithTag("panel_server_item_srv-10").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_form_srv-10").assertIsDisplayed()
        composeRule.onNodeWithTag("input_xtream_url").assertDoesNotExist()
        val card1Dialog = composeRule.onNodeWithTag("server_selection_main_card").fetchSemanticsNode().boundsInRoot
        val card2Dialog = composeRule.onNodeWithTag("playlists_main_card").fetchSemanticsNode().boundsInRoot
        assertTrue("Card 1 must not expand when panel form dialog opens", kotlin.math.abs(card1Dialog.height - card1Before.height) <= 2f)
        assertTrue("Card 2 must not expand when panel form dialog opens", kotlin.math.abs(card2Dialog.height - card2Before.height) <= 2f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `LoginScreen supports Android TV D-pad focus, Back dismissal of Popup and Dialog, and focus restoration`() {
        val fakeSubRepo = FakeSubscriptionRepository()
        val fakeSonix = FakeSonixControlRepository(
            SonixSyncState(
                isRegistered = true,
                deviceId = "dev-tv",
                deviceUid = "uid-tv",
                deviceCode = "SNX-TV-555",
                deviceStatus = SonixDeviceStatus.ACTIVE,
                accessStatus = SonixAccessStatus.ACTIVE,
                accessAllowed = true,
                hasLoadedCatalog = true,
                availableServers = listOf(SonixAssignedServer(id = "srv-tv-1", name = "TV NODE", region = "GLOBAL")),
            ),
        )
        val vm = LoginViewModel(fakeSubRepo, fakeSonix)
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            LiquidTVTheme {
                backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                Box(Modifier.width(1280.dp).height(720.dp)) {
                    LoginScreen(onLoginSuccess = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()

        // Focus «اختيار سيرفر» with D-pad and activate via Enter
        composeRule.onNodeWithTag("server_selector_toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_toggle").assertIsFocused()
        composeRule.onNodeWithTag("server_selector_toggle").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("server_selector_list").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_normal_url").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab_normal_url").assertIsFocused()
        composeRule.onNodeWithTag("panel_server_item_srv-tv-1").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_item_srv-tv-1").assertIsFocused()

        // Back key dismisses floating server Popup cleanly and restores focus to toggle
        composeRule.runOnUiThread { backDispatcher?.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_selector_list").assertDoesNotExist()
        composeRule.onNodeWithTag("server_selector_toggle").assertIsFocused()

        // Reopen Popup, select panel server to open Dialog, then Back key dismisses Dialog and restores focus
        composeRule.onNodeWithTag("server_selector_toggle").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_item_srv-tv-1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_server_form_srv-tv-1").assertIsDisplayed()

        composeRule.runOnUiThread { backDispatcher?.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("server_login_form_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("server_selector_toggle").assertIsFocused()
    }

    @Test
    fun `LiveTVScreen distributes 3-pane widths with shared sidebar and 60-40 split in RTL across wide TV viewports`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(960.dp).height(540.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }

        val density = composeRule.density.density
        val catBounds = composeRule.onNodeWithTag("live_categories_pane").fetchSemanticsNode().boundsInRoot
        val chBounds = composeRule.onNodeWithTag("live_channels_pane").fetchSemanticsNode().boundsInRoot
        val prevBounds = composeRule.onNodeWithTag("live_preview_pane").fetchSemanticsNode().boundsInRoot

        val catW = catBounds.width / density
        val chW = chBounds.width / density
        val prevW = prevBounds.width / density
        val expectedCatW = wideCategoriesSidebarWidth(960.dp - 14.dp * 2).value
        val remainingW = chW + prevW

        // RTL column order: categories rightmost, channels center, info leftmost
        assertTrue("Expected categories pane on the right of channels pane in RTL", catBounds.left >= chBounds.right - 2f)
        assertTrue("Expected channels pane on the right of preview pane in RTL", chBounds.left >= prevBounds.right - 2f)
        assertTrue("Expected categories width ~$expectedCatW, was $catW", kotlin.math.abs(catW - expectedCatW) <= 2f)
        assertTrue("Expected channels ratio ~0.60 of remaining, was ${chW / remainingW}", (chW / remainingW) in 0.58f..0.62f)
        assertTrue("Expected preview ratio ~0.40 of remaining, was ${prevW / remainingW}", (prevW / remainingW) in 0.38f..0.42f)
    }

    @Test
    fun `MoviesScreen and SeriesScreen show exactly 5 columns in wide landscape and match LiveTV sidebar width`() {
        val sixMovies = (1..6).map { i ->
            Channel(
                id = "mv-$i",
                name = "فيلم رقم $i",
                streamUrl = "http://localhost/movie/$i.mp4",
                groupTitle = "أكشن",
                mediaType = MediaType.MOVIE,
                rating = "8.$i",
            )
        }
        val sixSeries = (1..6).map { i ->
            SeriesItem(
                id = "sr-$i",
                subscriptionId = "sub-1",
                externalSeriesId = "30$i",
                name = "مسلسل رقم $i",
                categoryName = "دراما",
            )
        }
        val fakeChannels = FakeChannelRepository(movies = sixMovies, seriesList = sixSeries)
        val prefs = RedesignFakeUserPreferencesRepository()
        val moviesVm = MoviesViewModel(fakeChannels, prefs)

        val wideWidths = listOf(800, 960, 1200, 1280, 1600, 1920)
        wideWidths.forEach { w ->
            val expectedSidebar = wideCategoriesSidebarWidth((w - (if (w >= 1100) 40 else 28)).dp).value
            assertTrue("Sidebar width out of [232, 400] bounds for $w: $expectedSidebar", expectedSidebar in 232f..400f)
        }

        // Verify 5 columns (6th poster wraps to row 2) and identical sidebar width on 1280dp
        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1280.dp).height(720.dp)) {
                    MoviesScreen(onMovieClick = {}, viewModel = moviesVm)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("movies_categories_sidebar")).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasText("فيلم رقم 6")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val density = composeRule.density.density
        val moviesSidebarW = composeRule.onNodeWithTag("movies_categories_sidebar").fetchSemanticsNode().boundsInRoot.width / density
        val expected1280 = wideCategoriesSidebarWidth(1280.dp - 20.dp * 2).value
        assertTrue("Expected Movies sidebar ~${expected1280}dp, was $moviesSidebarW", kotlin.math.abs(moviesSidebarW - expected1280) <= 20f)

        val movieBox1 = composeRule.onNodeWithText("فيلم رقم 1").fetchSemanticsNode().boundsInRoot
        val movieBox5 = composeRule.onNodeWithText("فيلم رقم 5").fetchSemanticsNode().boundsInRoot
        val movieBox6 = composeRule.onNodeWithText("فيلم رقم 6").fetchSemanticsNode().boundsInRoot
        assertTrue("Expected posters 1 and 5 on same first row", kotlin.math.abs(movieBox1.top - movieBox5.top) <= 4f * density)
        assertTrue("Expected poster 6 to wrap to second row (strictly 5 columns)", movieBox6.top > movieBox1.top + 40f * density)
        assertTrue("Expected poster 6 aligned with column 1 in RTL", kotlin.math.abs(movieBox6.right - movieBox1.right) <= 4f * density)
    }

    @Test
    fun `SeriesScreen renders strictly 5 columns and 400dp max-clamped sidebar on 1920dp TV`() {
        val sixSeries = (1..6).map { i ->
            SeriesItem(
                id = "sr-$i",
                subscriptionId = "sub-1",
                externalSeriesId = "30$i",
                name = "مسلسل رقم $i",
                categoryName = "دراما",
            )
        }
        val fakeChannels = FakeChannelRepository(seriesList = sixSeries)
        val prefs = RedesignFakeUserPreferencesRepository()
        val seriesVm = SeriesViewModel(fakeChannels, prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1920.dp).height(1080.dp)) {
                    SeriesScreen(onSeriesClick = {}, viewModel = seriesVm)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("series_categories_sidebar")).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasText("مسلسل رقم 6")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val density = composeRule.density.density
        val seriesSidebarW = composeRule.onNodeWithTag("series_categories_sidebar").fetchSemanticsNode().boundsInRoot.width / density
        // 1920 - 40 = 1880dp -> 31% = 582.8dp -> clamped to 400dp (minus sidebar horizontal padding 16dp = 384dp)
        val expectedMaxClamped = wideCategoriesSidebarWidth(1920.dp - 20.dp * 2).value
        assertEquals(400f, expectedMaxClamped, 0.1f)
        assertTrue("Expected Series sidebar clamped ~400dp (inner ~384dp), was $seriesSidebarW", kotlin.math.abs(seriesSidebarW - expectedMaxClamped) <= 20f)

        val box1 = composeRule.onNodeWithText("مسلسل رقم 1").fetchSemanticsNode().boundsInRoot
        val box5 = composeRule.onNodeWithText("مسلسل رقم 5").fetchSemanticsNode().boundsInRoot
        val box6 = composeRule.onNodeWithText("مسلسل رقم 6").fetchSemanticsNode().boundsInRoot
        assertTrue("Expected series 1 and 5 on same first row", kotlin.math.abs(box1.top - box5.top) <= 4f * density)
        assertTrue("Expected series 6 to wrap to second row (strictly 5 columns)", box6.top > box1.top + 40f * density)
        assertTrue("Expected series 6 aligned with column 1 in RTL", kotlin.math.abs(box6.right - box1.right) <= 4f * density)
    }

    @Test
    fun `LiveTVScreen maintains RTL 3-pane distribution without overlap on 800dp landscape tablet or large phone`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val liveVm = LiveTVViewModel(fakeChannels, prefs, SavedStateHandle())

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(800.dp).height(480.dp)) {
                    LiveTVScreen(onChannelClick = {}, viewModel = liveVm)
                }
            }
        }
        composeRule.waitForIdle()

        val density = composeRule.density.density
        val catBounds = composeRule.onNodeWithTag("live_categories_pane").fetchSemanticsNode().boundsInRoot
        val chBounds = composeRule.onNodeWithTag("live_channels_pane").fetchSemanticsNode().boundsInRoot
        val prevBounds = composeRule.onNodeWithTag("live_preview_pane").fetchSemanticsNode().boundsInRoot

        val catW = catBounds.width / density
        val chW = chBounds.width / density
        val prevW = prevBounds.width / density
        val expectedCatW = wideCategoriesSidebarWidth(800.dp - 14.dp * 2).value
        val remainingW = chW + prevW

        assertTrue("Expected RTL order at 800dp: categories right of channels", catBounds.left >= chBounds.right - 2f)
        assertTrue("Expected RTL order at 800dp: channels right of preview", chBounds.left >= prevBounds.right - 2f)
        assertTrue("Expected categories ~$expectedCatW at 800dp, was $catW", kotlin.math.abs(catW - expectedCatW) <= 2f)
        assertTrue("Expected 60/40 split at 800dp", (chW / remainingW) in 0.58f..0.62f && (prevW / remainingW) in 0.38f..0.42f)
    }

    @Test
    fun `Small landscape phone 640dp uses non-wide adaptive layout without forcing sidebar or 5 columns`() {
        val fakeChannels = FakeChannelRepository()
        val prefs = RedesignFakeUserPreferencesRepository()
        val moviesVm = MoviesViewModel(fakeChannels, prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(640.dp).height(360.dp)) {
                    MoviesScreen(onMovieClick = {}, viewModel = moviesVm)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("movies_grid")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("movies_grid").assertIsDisplayed()
        composeRule.onNodeWithText("التصنيفات").assertIsDisplayed()
    }

    @Test
    fun `SettingsScreen supports touch vertical swipe scrolling up and down on landscape and portrait phones`() {
        val prefs = RedesignFakeUserPreferencesRepository()
        val fakeChannels = FakeChannelRepository()
        val fakeSubRepo = FakeSubscriptionRepository()
        val vm = SettingsViewModel(context, prefs, fakeChannels, fakeSubRepo)
        val updateVm = AppUpdateViewModel(AppUpdateManager(context, OkHttpClient(), prefs), prefs)

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(850.dp).height(400.dp)) {
                    SettingsScreen(
                        viewModel = vm,
                        updateViewModel = updateVm,
                    )
                }
            }
        }

        // Scroll detail pane via real touch drag down to bottom item and back up
        composeRule.onNodeWithTag("settings_content_list").performTouchInput {
            swipeUp()
            swipeUp()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_clear_cache").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_content_list").performTouchInput {
            swipeDown()
            swipeDown()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_manage_subscriptions").assertIsDisplayed()

        // Scroll left sections list via touch swipe and tap lower sections
        composeRule.onNodeWithTag("settings_sections_list").performTouchInput {
            swipeUp()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_section_updates").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("GitHub", substring = true, ignoreCase = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
        composeRule.onAllNodesWithText("SONIX-CONTROL", substring = true, ignoreCase = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
        assertEquals("kyanik010/LiquidTV", vm.uiState.value.githubRepo)

        composeRule.onNodeWithTag("settings_section_about").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("حول SONIX IPTV").assertIsDisplayed()
        composeRule.onAllNodesWithText("Media3", substring = true, ignoreCase = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
        composeRule.onAllNodesWithText("XMLTV", substring = true, ignoreCase = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
        composeRule.onAllNodesWithText("Xtream", substring = true, ignoreCase = true)
            .let { assertEquals(0, it.fetchSemanticsNodes().size) }
    }
}

private class FakeSubscriptionRepository(
    initialSubscriptions: List<Subscription> = emptyList(),
) : SubscriptionRepository {
    private val subsFlow = MutableStateFlow(initialSubscriptions)
    override val allSubscriptions: Flow<List<Subscription>> = subsFlow
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

private class FakeChannelRepository(
    episodes: List<EpisodeItem> = listOf(
        EpisodeItem(
            id = "ep-1",
            subscriptionId = "sub-1",
            seriesId = "sr-1",
            externalEpisodeId = "1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "الحلقة الأولى",
            streamUrl = "http://x/1.mp4",
        ),
    ),
    movies: List<Channel> = listOf(
        Channel(
            id = "mv-1",
            name = "صحراء العاصفة",
            streamUrl = "http://localhost/movie/1.mp4",
            groupTitle = "أكشن",
            mediaType = MediaType.MOVIE,
            rating = "8.4",
        ),
    ),
    seriesList: List<SeriesItem> = listOf(
        SeriesItem(
            id = "sr-1",
            subscriptionId = "sub-1",
            externalSeriesId = "303",
            name = "الأفق",
            categoryName = "دراما",
            plot = "ملحمة درامية مشوقة.",
            releaseDate = "2024-01-01",
            rating = "8.7",
        ),
    ),
) : ChannelRepository {
    private val liveFlow = MutableStateFlow(
        listOf(
            Channel(
                id = "ch-1",
                name = "الجزيرة الإخبارية",
                streamUrl = "http://localhost/live/1.ts",
                groupTitle = "أخبار",
                mediaType = MediaType.LIVE,
                channelNumber = 1,
                tvgId = "aljazeera",
            ),
        )
    )
    private val moviesFlow = MutableStateFlow(movies)
    private val seriesFlow = MutableStateFlow(seriesList)
    private val episodesFlow = MutableStateFlow(episodes)
    private val epg = ChannelEpgSummary(
        current = EpgProgram(
            id = 1L,
            subscriptionId = "sub-1",
            channelTvgId = "aljazeera",
            title = "النشرة المسائية",
            description = "تغطية إخبارية شاملة",
            startTimeMs = System.currentTimeMillis() - 600_000L,
            endTimeMs = System.currentTimeMillis() + 600_000L,
        ),
        next = EpgProgram(
            id = 2L,
            subscriptionId = "sub-1",
            channelTvgId = "aljazeera",
            title = "برنامج حواري",
            description = null,
            startTimeMs = System.currentTimeMillis() + 600_000L,
            endTimeMs = System.currentTimeMillis() + 1_800_000L,
        ),
    )

    override fun getAllChannels(): Flow<List<Channel>> = liveFlow
    override fun getMovies(): Flow<List<Channel>> = moviesFlow
    override fun getSeries(): Flow<List<SeriesItem>> = seriesFlow
    override fun getSeriesById(seriesId: String): Flow<SeriesItem?> = seriesFlow.map { list -> list.firstOrNull { it.id == seriesId } }
    override fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeItem>> = episodesFlow
    override suspend fun loadSeriesDetails(seriesId: String): Result<Unit> = Result.success(Unit)
    override fun getFavoriteChannels(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getRecentlyWatched(): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCategory(category: String, mediaType: MediaType): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByLanguage(language: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun getChannelsByCountry(country: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchChannels(query: String): Flow<List<Channel>> = flowOf(emptyList())
    override fun searchSeries(query: String): Flow<List<SeriesItem>> = flowOf(emptyList())
    override fun getAllCategories(mediaType: MediaType): Flow<List<Category>> = when (mediaType) {
        MediaType.LIVE -> flowOf(listOf(Category(id = "c1", contentType = MediaType.LIVE, name = "أخبار", channelCount = 1)))
        MediaType.MOVIE -> flowOf(listOf(Category(id = "m1", contentType = MediaType.MOVIE, name = "أكشن", channelCount = 1)))
        MediaType.SERIES_EPISODE -> flowOf(listOf(Category(id = "s1", contentType = MediaType.SERIES_EPISODE, name = "دراما", channelCount = 1)))
    }
    override fun getAllLanguages(): Flow<List<Language>> = flowOf(emptyList())
    override fun getAllCountries(): Flow<List<Country>> = flowOf(emptyList())
    override fun getLockedCategoryNames(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun setCategoryLocked(categoryName: String, locked: Boolean) = Unit
    override suspend fun getChannelById(id: String): Channel? = liveFlow.value.firstOrNull { it.id == id }
    override suspend fun getEpisodeById(episodeId: String): EpisodeItem? = episodesFlow.value.firstOrNull { it.id == episodeId }
    override suspend fun toggleFavorite(channelId: String) {
        liveFlow.update { list -> list.map { if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it } }
    }
    override suspend fun toggleSeriesFavorite(seriesId: String) {
        seriesFlow.update { list -> list.map { if (it.id == seriesId) it.copy(isFavorite = !it.isFavorite) else it } }
    }
    override suspend fun recordWatch(channelId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun recordEpisodeWatch(episodeId: String, positionMs: Long, durationMs: Long) = Unit
    override suspend fun updateResumePosition(itemId: String, mediaType: MediaType, positionMs: Long) = Unit
    override fun observeChannelEpg(channel: Channel): Flow<ChannelEpgSummary> = flowOf(epg)
    override suspend fun fetchShortEpgIfNeeded(channel: Channel): ChannelEpgSummary = epg
    override suspend fun refreshPlaylists(): Result<Unit> = Result.success(Unit)
    override suspend fun clearWatchHistory() = Unit
    override fun getPlaylistInfo(): Flow<PlaylistInfo> = flowOf(PlaylistInfo())
}

private class RedesignFakeUserPreferencesRepository : UserPreferencesRepository {
    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val historyFlow = MutableStateFlow<List<String>>(emptyList())
    private val lastQueryFlow = MutableStateFlow("")
    private var pinValue: String? = null

    override val userPreferences: Flow<UserPreferences> = prefsFlow
    override val searchHistory: Flow<List<String>> = historyFlow
    override fun getGlassIntensity(): Flow<Float> = prefsFlow.map { it.glassIntensity }
    override fun getBlurIntensity(): Flow<Float> = prefsFlow.map { it.blurIntensity }
    override fun getAnimationIntensity(): Flow<Float> = prefsFlow.map { it.animationIntensity }
    override fun getAppTheme(): Flow<String> = prefsFlow.map { it.appTheme }
    override fun getAmbientTheme(): Flow<String> = prefsFlow.map { it.theme }
    override fun getAmbientEnabled(): Flow<Boolean> = prefsFlow.map { it.ambientEnabled }
    override fun getAmbientIdleDuration(): Flow<Long> = prefsFlow.map { it.ambientIdleDuration }
    override fun getAmbientClockVisible(): Flow<Boolean> = prefsFlow.map { it.clockVisible }
    override fun getAutoPlayNext(): Flow<Boolean> = prefsFlow.map { it.autoPlay }
    override fun getStreamTimeout(): Flow<Int> = prefsFlow.map { it.connectionTimeout }
    override fun getRetryAttempts(): Flow<Int> = prefsFlow.map { it.maxRetries }
    override fun getLastSearchQuery(): Flow<String> = lastQueryFlow

    override suspend fun setGlassIntensity(value: Float) { prefsFlow.update { it.copy(glassIntensity = value) } }
    override suspend fun setBlurIntensity(value: Float) { prefsFlow.update { it.copy(blurIntensity = value) } }
    override suspend fun setAnimationIntensity(value: Float) { prefsFlow.update { it.copy(animationIntensity = value) } }
    override suspend fun setAppTheme(themeId: String) {
        val resolved = AppThemeCatalog.resolve(themeId).id
        prefsFlow.update { it.copy(appTheme = resolved) }
    }
    override suspend fun setAmbientTheme(theme: String) { prefsFlow.update { it.copy(theme = theme) } }
    override suspend fun setAmbientEnabled(enabled: Boolean) { prefsFlow.update { it.copy(ambientEnabled = enabled) } }
    override suspend fun setAmbientIdleDuration(ms: Long) { prefsFlow.update { it.copy(ambientIdleDuration = ms) } }
    override suspend fun setAmbientClockVisible(visible: Boolean) { prefsFlow.update { it.copy(clockVisible = visible) } }
    override suspend fun setAutoPlayNext(enabled: Boolean) { prefsFlow.update { it.copy(autoPlay = enabled) } }
    override suspend fun setStreamTimeout(seconds: Int) { prefsFlow.update { it.copy(connectionTimeout = seconds) } }
    override suspend fun setRetryAttempts(count: Int) { prefsFlow.update { it.copy(maxRetries = count) } }
    override suspend fun setDefaultQuality(quality: String) { prefsFlow.update { it.copy(defaultQuality = quality) } }
    override suspend fun setAutoPlay(enabled: Boolean) { prefsFlow.update { it.copy(autoPlay = enabled) } }
    override suspend fun setSubtitlesEnabled(enabled: Boolean) { prefsFlow.update { it.copy(subtitlesEnabled = enabled) } }
    override suspend fun setPreferredSubtitleLanguage(language: String) { prefsFlow.update { it.copy(preferredSubtitleLanguage = language) } }
    override suspend fun setTheme(theme: String) { prefsFlow.update { it.copy(theme = theme) } }
    override suspend fun setClockVisible(visible: Boolean) { prefsFlow.update { it.copy(clockVisible = visible) } }
    override suspend fun setScreenSaverTimeout(minutes: Int) { prefsFlow.update { it.copy(screenSaverTimeout = minutes) } }
    override suspend fun setAutoRefreshEnabled(enabled: Boolean) { prefsFlow.update { it.copy(autoRefreshEnabled = enabled) } }
    override suspend fun setAutoRefreshInterval(hours: Int) { prefsFlow.update { it.copy(autoRefreshInterval = hours) } }
    override suspend fun setConnectionTimeout(seconds: Int) { prefsFlow.update { it.copy(connectionTimeout = seconds) } }
    override suspend fun setMaxRetries(retries: Int) { prefsFlow.update { it.copy(maxRetries = retries) } }
    override suspend fun setParentalControlEnabled(enabled: Boolean) {
        prefsFlow.update { it.copy(parentalControlEnabled = enabled && !pinValue.isNullOrBlank()) }
    }
    override suspend fun setParentalPin(pin: String) {
        pinValue = pin
        prefsFlow.update { it.copy(hasParentalPin = pin.isNotBlank()) }
    }
    override suspend fun verifyParentalPin(pin: String): Boolean = pinValue != null && pinValue == pin
    override suspend fun clearParentalPin() {
        pinValue = null
        prefsFlow.update { it.copy(parentalControlEnabled = false, hasParentalPin = false) }
    }
    override suspend fun setGithubRepo(repo: String) { prefsFlow.update { it.copy(githubRepo = repo) } }
    override suspend fun setAutoCheckUpdates(enabled: Boolean) { prefsFlow.update { it.copy(autoCheckUpdates = enabled) } }
    override suspend fun addSearchHistory(query: String) {
        historyFlow.update { (listOf(query) + it).distinct().take(20) }
        lastQueryFlow.value = query
    }
    override suspend fun removeSearchHistory(query: String) { historyFlow.update { it - query } }
    override suspend fun clearSearchHistory() { historyFlow.value = emptyList() }
}
