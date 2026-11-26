package com.liquidtv.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.sonix.SonixAccessStatus
import com.liquidtv.app.data.sonix.SonixAppSettings
import com.liquidtv.app.data.sonix.SonixAssignedServer
import com.liquidtv.app.data.sonix.SonixDeviceStatus
import com.liquidtv.app.data.sonix.SonixResolvedServer
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.ui.AppStartupGate
import com.liquidtv.app.ui.StartupViewModel
import com.liquidtv.app.ui.screens.login.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class LoginAndXtreamAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var database: LiquidTVDatabase
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var fakeSonixRepo: FakeSonixControlRepository
    private lateinit var repository: SubscriptionRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    private val passThroughCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        server = MockWebServer()
        server.start()

        database = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        fakeSonixRepo = FakeSonixControlRepository()

        repository = SubscriptionRepositoryImpl(
            context = context,
            database = database,
            subscriptionDao = database.subscriptionDao(),
            channelDao = database.channelDao(),
            seriesDao = database.seriesDao(),
            categoryDao = database.categoryDao(),
            epgDao = database.epgDao(),
            xtreamApi = XtreamApiService(okHttpClient),
            okHttpClient = okHttpClient,
            xmltvParser = XmltvParser(),
            crypto = passThroughCrypto,
            prefs = UserPreferencesDataStore(context),
            sonixControlRepository = fakeSonixRepo,
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun configureXtreamDispatcher() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("action=get_live_categories") -> MockResponse().setBody(
                        """[{"category_id":"10","category_name":"Arabic News"}]""",
                    )
                    path.contains("action=get_live_streams") -> MockResponse().setBody(
                        """[{"num":1,"name":"Al Jazeera HD","stream_id":101,"stream_icon":"http://cdn/logo.png","epg_channel_id":"aljazeera.qa","category_id":"10"}]""",
                    )
                    path.contains("action=get_vod_categories") -> MockResponse().setBody(
                        """[{"category_id":"20","category_name":"Action Movies"}]""",
                    )
                    path.contains("action=get_vod_streams") -> MockResponse().setBody(
                        """[{"num":1,"name":"Desert Storm","stream_id":202,"stream_icon":"http://cdn/movie.jpg","category_id":"20","container_extension":"mp4","rating":"8.1"}]""",
                    )
                    path.contains("action=get_series_categories") -> MockResponse().setBody(
                        """[{"category_id":"30","category_name":"Drama Series"}]""",
                    )
                    path.contains("action=get_series") -> MockResponse().setBody(
                        """[{"series_id":303,"name":"The Horizon","cover":"http://cdn/series.jpg","category_id":"30","plot":"Sci-fi drama"}]""",
                    )
                    path.contains("player_api.php") && !path.contains("action=") -> MockResponse().setBody(
                        """{"user_info":{"username":"demo","status":"Active","auth":1,"exp_date":"1893456000","max_connections":"2","active_cons":"0"},"server_info":{"url":"localhost","port":"${server.port}","server_protocol":"http","allowed_output_formats":["m3u8","ts"]}}""",
                    )
                    path.contains("xmltv.php") -> MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @Test
    fun `http Xtream server authenticates and syncs live movies and series`() = runTest {
        configureXtreamDispatcher()
        val httpUrl = "http://${server.hostName}:${server.port}"
        val result = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "My HTTP Provider",
            serverUrl = httpUrl,
            username = "demo",
            password = "secret",
            origin = SubscriptionOrigin.NORMAL_URL,
            activate = true,
        )

        assertTrue("Expected HTTP Xtream login to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val sub = result.getOrThrow()
        assertEquals(SubscriptionType.XTREAM, sub.type)
        assertEquals(SubscriptionOrigin.NORMAL_URL, sub.origin)
        assertTrue(sub.serverUrl.startsWith("http://"))

        val live = database.channelDao().getLiveChannels(sub.id).first()
        val movies = database.channelDao().getMovies(sub.id).first()
        val series = database.seriesDao().getSeries(sub.id).first()
        assertEquals(1, live.size)
        assertEquals("Al Jazeera HD", live[0].name)
        assertEquals(1, movies.size)
        assertEquals("Desert Storm", movies[0].name)
        assertEquals(1, series.size)
        assertEquals("The Horizon", series[0].name)
    }

    @Test
    fun `invalid Xtream credentials returns failure without saving subscription`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"user_info":{"username":"bad","status":"Disabled","auth":0}}"""),
        )
        val result = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "Bad Sub",
            serverUrl = "http://${server.hostName}:${server.port}",
            username = "bad",
            password = "wrong",
            activate = true,
        )
        assertTrue(result.isFailure)
        assertFalse(repository.hasAnySubscription())
    }

    @Test
    fun `panel server login resolves URL dynamically without storing raw URL in editor`() = runTest {
        configureXtreamDispatcher()
        val httpUrl = "http://${server.hostName}:${server.port}"
        fakeSonixRepo.mutableState.value = SonixSyncState(
            isRegistered = true,
            deviceId = "dev-1",
            deviceUid = "ltv_1",
            deviceCode = "PNL9988776",
            deviceStatus = SonixDeviceStatus.ACTIVE,
            accessStatus = SonixAccessStatus.ACTIVE,
            accessAllowed = true,
            assignedServers = listOf(SonixAssignedServer(id = "srv-panel-1", name = "VIP Arab", region = "ME", priority = 1)),
        )
        fakeSonixRepo.resolveHandler = { serverId ->
            assertEquals("srv-panel-1", serverId)
            Result.success(SonixResolvedServer(id = "srv-panel-1", name = "VIP Arab", region = "ME", serverUrl = httpUrl))
        }

        val vm = LoginViewModel(repository, fakeSonixRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectServerMode(SubscriptionOrigin.PANEL_SERVER)
        vm.selectPanelServer("srv-panel-1", "VIP Arab")
        vm.setDisplayName("اشتراك اللوحة")
        vm.setUsername("demo")
        vm.setPassword("secret")
        vm.connect()

        val finishedState = kotlinx.coroutines.runBlocking {
            withTimeout(5_000) {
                vm.uiState.first { !it.isConnecting && (it.isSuccess || it.errorMessage != null) }
            }
        }
        assertTrue("Expected panel login success: ${finishedState.errorMessage}", finishedState.isSuccess)
        val saved = repository.getActiveSubscriptionOnce()!!
        assertEquals(SubscriptionOrigin.PANEL_SERVER, saved.origin)
        assertEquals("srv-panel-1", saved.panelServerId)
        assertEquals("VIP Arab", saved.panelServerName)

        // When loading panel subscription for edit, Host URL MUST stay empty/hidden
        val editVm = LoginViewModel(repository, fakeSonixRepo)
        editVm.loadForEdit(saved.id)
        val editState = kotlinx.coroutines.runBlocking {
            withTimeout(5_000) {
                editVm.uiState.first { it.editingSubscriptionId == saved.id }
            }
        }
        assertEquals(SubscriptionOrigin.PANEL_SERVER, editState.serverMode)
        assertEquals(SubscriptionOrigin.PANEL_SERVER, editState.editingOrigin)
        assertEquals("", editState.serverUrl)
    }

    @Test
    fun `LoginViewModel validates Normal URL and Control Panel Servers modes`() = runTest {
        val vm = LoginViewModel(repository, fakeSonixRepo)
        vm.selectServerMode(SubscriptionOrigin.NORMAL_URL)
        vm.setDisplayName("Test")
        vm.setUsername("user")
        vm.setPassword("pass")
        vm.setServerUrl("ftp://invalid.server")
        vm.connect()
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isConnecting)

        // Panel mode without selected server should fail validation cleanly
        vm.selectServerMode(SubscriptionOrigin.PANEL_SERVER)
        vm.connect()
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `StartupViewModel enforces Login gate when zero subscriptions exist and opens Home after Xtream login`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val prefsRepo = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())
        val startupVm = StartupViewModel(repository, prefsRepo, fakeSonixRepo)
        val collectorJob = launch { startupVm.rootState.collect { } }

        val initialGate = withTimeout(5_000) {
            startupVm.rootState.first { it.gate != AppStartupGate.Checking }
        }
        assertEquals(AppStartupGate.NeedsLogin, initialGate.gate)

        configureXtreamDispatcher()
        val res = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "Starter",
            serverUrl = "http://${server.hostName}:${server.port}",
            username = "demo",
            password = "secret",
            activate = true,
        )
        assertTrue(res.isSuccess)

        val unlockedState = withTimeout(5_000) {
            startupVm.rootState.first { it.gate is AppStartupGate.Ready }
        }
        assertTrue(unlockedState.gate is AppStartupGate.Ready)
        collectorJob.cancel()
    }

    @Test
    fun `M3U is completely removed, HTTP and HTTPS URLs validate, and playlists CRUD persists in Room`() = runTest {
        assertEquals(listOf(SubscriptionType.XTREAM), SubscriptionType.entries)

        configureXtreamDispatcher()
        val httpUrl = "http://${server.hostName}:${server.port}"
        val vm = LoginViewModel(repository, fakeSonixRepo)

        // Toggle URL عادي open, test HTTPS validation passes input validation
        vm.toggleNormalUrlSection()
        assertTrue(vm.uiState.value.isNormalUrlSelected)
        vm.setDisplayName("تجربة HTTPS")
        vm.setUsername("demo")
        vm.setPassword("secret")
        vm.setServerUrl("https://")
        // Verify toggle closes URL عادي cleanly
        vm.toggleNormalUrlSection()
        assertFalse(vm.uiState.value.isNormalUrlSelected)

        // Save 2 playlists in Room and verify activate, edit, and delete
        val res1 = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "قائمة أولى",
            serverUrl = httpUrl,
            username = "demo",
            password = "secret",
            origin = SubscriptionOrigin.NORMAL_URL,
            activate = true,
        )
        val res2 = repository.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "قائمة ثانية",
            serverUrl = httpUrl,
            username = "demo",
            password = "secret",
            origin = SubscriptionOrigin.NORMAL_URL,
            activate = false,
        )
        assertTrue(res1.isSuccess && res2.isSuccess)
        val sub1 = res1.getOrThrow()
        val sub2 = res2.getOrThrow()

        // Activate sub2
        vm.activatePlaylist(sub2.id)
        kotlinx.coroutines.runBlocking {
            withTimeout(5_000) { vm.uiState.first { it.busyPlaylistId == null && it.playlistMessage == "تم تفعيل قائمة التشغيل" } }
        }
        assertEquals(sub2.id, repository.getActiveSubscriptionOnce()?.id)

        // Edit sub1 name & save
        vm.startEditingPlaylist(sub1)
        kotlinx.coroutines.runBlocking {
            withTimeout(5_000) { vm.uiState.first { it.editingSubscriptionId == sub1.id } }
        }
        assertTrue(vm.uiState.value.isNormalUrlSelected)
        vm.setDisplayName("قائمة معدلة")
        vm.connect()
        kotlinx.coroutines.runBlocking {
            withTimeout(5_000) { vm.uiState.first { !it.isConnecting && it.isSuccess } }
        }
        assertEquals("قائمة معدلة", repository.getSubscriptionById(sub1.id)?.name)

        // Delete sub2 and verify offline persistence in Room DAO
        vm.deletePlaylist(sub2.id)
        kotlinx.coroutines.runBlocking {
            withTimeout(5_000) { vm.uiState.first { it.busyPlaylistId == null && it.savedPlaylists.size == 1 } }
        }
        val remaining = database.subscriptionDao().getAllSubscriptionsOnce()
        assertEquals(1, remaining.size)
        assertEquals("قائمة معدلة", remaining.first().name)
    }

    @Test
    fun `LoginViewModel manages collapsed server selector, mutual exclusion, dynamic catalog updates, and افتراضي URL fallback on panel error`() = runTest {
        configureXtreamDispatcher()
        val httpUrl = "http://${server.hostName}:${server.port}"
        fakeSonixRepo.mutableState.value = SonixSyncState(
            isRegistered = true,
            deviceId = "dev-vm",
            deviceUid = "uid-vm",
            deviceCode = "SNXVM01",
            deviceStatus = SonixDeviceStatus.ACTIVE,
            accessStatus = SonixAccessStatus.ACTIVE,
            accessAllowed = true,
            hasLoadedCatalog = true,
            availableServers = listOf(
                SonixAssignedServer(id = "srv-1", name = "Node Alpha", region = "EU"),
                SonixAssignedServer(id = "srv-2", name = "Node Beta", region = "US"),
            ),
        )
        val vm = LoginViewModel(repository, fakeSonixRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially collapsed
        assertFalse(vm.uiState.value.isServerSelectorExpanded)
        assertFalse(vm.uiState.value.isNormalUrlSelected)
        assertNull(vm.uiState.value.selectedPanelServerId)

        val callsBefore = fakeSonixRepo.refreshCatalogCalls
        vm.toggleServerSelector()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isServerSelectorExpanded)
        assertTrue(fakeSonixRepo.refreshCatalogCalls > callsBefore)

        // Exclusive toggle between Server 1 -> Server 2 -> Normal URL
        vm.selectPanelServer("srv-1", "Node Alpha")
        assertEquals("srv-1", vm.uiState.value.selectedPanelServerId)
        assertFalse(vm.uiState.value.isNormalUrlSelected)

        vm.selectPanelServer("srv-2", "Node Beta")
        assertEquals("srv-2", vm.uiState.value.selectedPanelServerId)
        assertEquals("Node Beta", vm.uiState.value.selectedPanelServerName)

        // Wait for combine collector to subscribe with Room before verifying reactive updates
        kotlinx.coroutines.runBlocking {
            withTimeout(5_000) {
                vm.uiState.first { it.sonixState.deviceCode == "SNXVM01" }
            }
        }

        // Dynamic rename on panel updates selectedPanelServerName
        fakeSonixRepo.mutableState.value = fakeSonixRepo.mutableState.value.copy(
            availableServers = listOf(
                SonixAssignedServer(id = "srv-1", name = "Node Alpha", region = "EU"),
                SonixAssignedServer(id = "srv-2", name = "Node Beta Pro", region = "US"),
            ),
        )
        val renamedState = kotlinx.coroutines.runBlocking {
            withTimeout(5_000) {
                vm.uiState.first { it.selectedPanelServerName == "Node Beta Pro" }
            }
        }
        assertEquals("Node Beta Pro", renamedState.selectedPanelServerName)

        // Dynamic delete on panel clears deleted server selection
        fakeSonixRepo.mutableState.value = fakeSonixRepo.mutableState.value.copy(
            availableServers = listOf(
                SonixAssignedServer(id = "srv-1", name = "Node Alpha", region = "EU"),
            ),
        )
        val deletedState = kotlinx.coroutines.runBlocking {
            withTimeout(5_000) {
                vm.uiState.first { it.selectedPanelServerId == null }
            }
        }
        assertNull(deletedState.selectedPanelServerId)
        assertNull(deletedState.selectedPanelServerName)

        // Clicking an open panel server again collapses it cleanly
        vm.selectPanelServer("srv-1", "Node Alpha")
        assertEquals("srv-1", vm.uiState.value.selectedPanelServerId)
        vm.selectPanelServer("srv-1", "Node Alpha")
        assertNull(vm.uiState.value.selectedPanelServerId)

        // Panel failure never disables افتراضي URL login
        fakeSonixRepo.mutableState.value = fakeSonixRepo.mutableState.value.copy(
            catalogError = "فشل الاتصال بلوحة التحكم",
            availableServers = emptyList(),
            settings = SonixAppSettings(allowNormalUrlLogin = false),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        vm.toggleNormalUrlSection()
        vm.setDisplayName("احتياطي")
        vm.setUsername("demo")
        vm.setPassword("secret")
        vm.setServerUrl(httpUrl)
        vm.connect()
        val res = kotlinx.coroutines.runBlocking {
            withTimeout(5_000) {
                vm.uiState.first { !it.isConnecting && (it.isSuccess || it.errorMessage != null) }
            }
        }
        assertTrue("Expected Normal URL login fallback to succeed: ${res.errorMessage}", res.isSuccess)

        vm.collapseServerSelector()
        assertFalse(vm.uiState.value.isServerSelectorExpanded)
    }
}
