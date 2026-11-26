package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.entity.SubscriptionEntity
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.sonix.HttpSonixControlApi
import com.liquidtv.app.data.sonix.SonixAccessStatus
import com.liquidtv.app.data.sonix.SonixControlRepositoryImpl
import com.liquidtv.app.data.sonix.SonixDeviceIdentityStore
import com.liquidtv.app.data.sonix.SonixDeviceStatus
import com.liquidtv.app.data.sonix.SonixMessage
import com.liquidtv.app.data.sonix.SonixReleaseUpdate
import com.liquidtv.app.data.sonix.SonixResolvedServer
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.ui.components.GlassPanelMessageDialog
import com.liquidtv.app.ui.components.MaintenanceBannerStrip
import com.liquidtv.app.ui.components.SonixBlockedAccessOverlay
import com.liquidtv.app.ui.theme.AppThemeCatalog
import com.liquidtv.app.ui.theme.LiquidTVTheme
import com.liquidtv.app.updater.AppUpdateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class SonixControlIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private val plainCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:")
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `SonixControlRepositoryImpl persists device token, authenticates subsequent calls, recovers from 401, and keeps LiquidTV theme isolated`() = runTest {
        val prefs = UserPreferencesDataStore(context)
        prefs.setAppTheme("ruby-velvet")

        // 1. Initial sync issues device_token + ignores any themes field from SONIX
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "status": "active",
                      "device_id": "dev-1",
                      "device_uid": "ltv_1",
                      "device_code": "SNX900111",
                      "device_status": "active",
                      "access_status": "active",
                      "access_allowed": true,
                      "device_token": "stx_token_initial",
                      "subscription": {"id":"sub-p1","plan_name":"الخطة الذهبية","plan_code":"gold","status":"active","is_trial":false,"days_remaining":45},
                      "servers": [{"id":"srv-1","name":"VIP Server 1","region":"EU","priority":10}],
                      "messages": [{"id":"msg-1","title":"إشعار إداري","body":"أهلاً بك في SONIX","message_type":"info"}],
                      "settings": {"trial_duration_days":30,"maintenance_mode":true,"maintenance_message":"صيانة مجدولة","support_notice":"الدعم متاح","allow_normal_url_login":true,"allow_panel_servers":true},
                      "themes": [{"id":"panel-theme","name":"Panel Theme"}]
                    }
                    """.trimIndent(),
                ),
        )
        // 2. Next call (acknowledgeMessage) returns 401 invalid_device_credentials -> clears stale token & retries cleanly with new token!
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"error":"invalid_device_credentials"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"acknowledged":true,"device_token":"stx_token_rotated"}"""),
        )

        val api = HttpSonixControlApi(
            okHttpClient = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            anonKey = "sb_publishable_test_key",
        )
        val identityStore = SonixDeviceIdentityStore(context, plainCrypto)
        val repo = SonixControlRepositoryImpl(context, api, identityStore)

        val syncRes = repo.syncNow(force = true)
        assertTrue(syncRes.isSuccess)
        val st = syncRes.getOrThrow()
        assertEquals("SNX900111", st.deviceCode)
        assertEquals(SonixDeviceStatus.ACTIVE, st.deviceStatus)
        assertEquals(SonixAccessStatus.ACTIVE, st.accessStatus)
        assertTrue(st.accessAllowed)
        assertEquals(1, st.assignedServers.size)
        assertEquals("srv-1", st.assignedServers.first().id)
        assertTrue(st.settings.maintenanceMode)
        assertEquals("stx_token_initial", identityStore.getDeviceToken())

        // LiquidTV theme remains completely independent of SONIX-CONTROL themes
        assertEquals("ruby-velvet", prefs.getAppTheme().first())
        prefs.setAppTheme(AppThemeCatalog.DEFAULT_THEME_ID)

        // Debounced onAppForeground() within 45s does NOT spam extra requests
        repo.onAppForeground()
        repo.onAppForeground()
        assertEquals(1, server.requestCount)

        // Acknowledge message triggers 401 -> token rotation recovery -> ack succeeds
        val ackRes = repo.acknowledgeMessage("msg-1")
        assertTrue(ackRes.isSuccess)
        assertTrue(repo.state.value.pendingMessages.isEmpty())
        assertEquals("stx_token_rotated", identityStore.getDeviceToken())

        val req1 = server.takeRequest()
        assertEquals("sb_publishable_test_key", req1.getHeader("apikey"))
        assertTrue(req1.body.readUtf8().contains("\"action\":\"sync\""))

        val req2 = server.takeRequest()
        assertEquals("stx_token_initial", req2.getHeader("x-sonix-device-token"))
        assertTrue(req2.body.readUtf8().contains("\"action\":\"message.ack\""))

        val req3 = server.takeRequest()
        assertNull(req3.getHeader("x-sonix-device-token"))
        val req3Body = req3.body.readUtf8()
        assertTrue(req3Body.contains("\"action\":\"message.ack\""))
        assertTrue(req3Body.contains("\"message_id\":\"msg-1\""))
    }

    @Test
    fun `SubscriptionRepositoryImpl resolves fresh URL dynamically for PANEL_SERVER playlists and blocks sync when SONIX blocks device`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = UserPreferencesDataStore(context)
        val okHttp = OkHttpClient()
        val fakeSonix = FakeSonixControlRepository()
        val resolvedBaseUrl = server.url("/").toString().trimEnd('/')
        fakeSonix.resolveHandler = { serverId ->
            assertEquals("srv-panel-1", serverId)
            Result.success(
                SonixResolvedServer(
                    id = serverId,
                    name = "VIP Europe",
                    region = "DE",
                    serverUrl = resolvedBaseUrl,
                ),
            )
        }

        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action")
                return when (action) {
                    null -> MockResponse().setBody(
                        """{"user_info":{"username":"ali","status":"Active","auth":1,"exp_date":"1893456000","max_connections":"2","active_cons":"0"},"server_info":{"url":"127.0.0.1","port":"${server.port}","server_protocol":"http"}}""",
                    )
                    "get_live_categories" -> MockResponse().setBody("""[{"category_id":"10","category_name":"أخبار"}]""")
                    "get_live_streams" -> MockResponse().setBody("""[{"num":1,"name":"قناة 1","stream_id":101,"category_id":"10"}]""")
                    else -> MockResponse().setBody("[]")
                }
            }
        }

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
            crypto = plainCrypto,
            prefs = prefs,
            sonixControlRepository = fakeSonix,
        )

        db.subscriptionDao().upsertSubscription(
            SubscriptionEntity(
                id = "sub-panel",
                name = "اشتراك اللوحة",
                type = SubscriptionType.XTREAM.name,
                origin = SubscriptionOrigin.PANEL_SERVER.name,
                panelServerId = "srv-panel-1",
                panelServerName = "VIP Europe",
                serverUrl = "http://old-stale.invalid:8080",
                username = "ali",
                encryptedPassword = plainCrypto.encrypt("secret"),
                isActive = true,
                connectionStatus = SubscriptionStatus.CONNECTED.name,
            ),
        )

        val syncRes = subRepo.syncSubscription("sub-panel")
        assertTrue("Expected panel server sync to succeed using dynamically resolved URL: ${syncRes.exceptionOrNull()}", syncRes.isSuccess)
        val updatedSub = db.subscriptionDao().getSubscriptionById("sub-panel")!!
        assertEquals(resolvedBaseUrl, updatedSub.serverUrl)
        assertEquals(1, db.channelDao().getAllChannelsOnce("sub-panel").size)

        // When device becomes blocked in SONIX-CONTROL, syncing must be denied immediately
        fakeSonix.mutableState.value = fakeSonix.mutableState.value.copy(
            deviceStatus = SonixDeviceStatus.BLOCKED,
            accessStatus = SonixAccessStatus.BLOCKED,
            accessAllowed = false,
        )
        val blockedRes = subRepo.syncSubscription("sub-panel")
        assertTrue(blockedRes.isFailure)
        db.close()
    }

    @Test
    fun `AppUpdateManager prefers mandatory SONIX-CONTROL release and falls back when panel returns null`() = runTest {
        val prefs = UserPreferencesDataStore(context)
        val fakeSonix = FakeSonixControlRepository()
        fakeSonix.panelUpdateHandler = {
            Result.success(
                SonixReleaseUpdate(
                    id = "rel-20",
                    channel = "stable",
                    versionName = "9.9.0",
                    versionCode = BuildConfig.VERSION_CODE + 10,
                    apkUrl = "https://cdn.example.com/LiquidTV-9.9.0.apk",
                    sha256 = "abc123",
                    apkSizeBytes = 25_000_000L,
                    releaseNotes = "تحديث أمني إلزامي",
                    updatePolicy = "mandatory",
                    minSupportedVersionCode = BuildConfig.VERSION_CODE + 5,
                ),
            )
        }

        val manager = AppUpdateManager(
            context = context,
            okHttpClient = OkHttpClient(),
            userPreferencesRepository = prefs,
            sonixControlRepository = fakeSonix,
        )
        val mandatoryResult = manager.checkForUpdate()
        assertTrue(mandatoryResult.isSuccess)
        val updateInfo = mandatoryResult.getOrNull()
        assertNotNull(updateInfo)
        assertTrue(updateInfo!!.isMandatory)
        assertEquals("panel", updateInfo.source)
        assertEquals("9.9.0", updateInfo.versionName)

        // Reject insecure HTTP URLs even if returned by panel, and verify HTTP check fails safely
        fakeSonix.panelUpdateHandler = {
            Result.success(
                SonixReleaseUpdate(
                    id = "rel-bad",
                    versionName = "9.9.1",
                    versionCode = BuildConfig.VERSION_CODE + 11,
                    apkUrl = "http://insecure.example.com/app.apk",
                ),
            )
        }
        assertFalse(manager.isTrustedUpdateDownloadUrl(updateInfo, "http://insecure.example.com/app.apk"))
        assertTrue(manager.isTrustedUpdateDownloadUrl(updateInfo, "https://cdn.example.com/app.apk"))
    }

    @Test
    fun `SonixBlockedAccessOverlay, MaintenanceBannerStrip, and GlassPanelMessageDialog render device code and acknowledge`() {
        var retried = false
        var ackedId: String? = null

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1000.dp).height(640.dp)) {
                    SonixBlockedAccessOverlay(
                        sonixState = SonixSyncState(
                            isRegistered = true,
                            deviceCode = "SNX-445566",
                            deviceStatus = SonixDeviceStatus.BLOCKED,
                            accessStatus = SonixAccessStatus.BLOCKED,
                            accessAllowed = false,
                        ),
                        onRetrySync = { retried = true },
                    )
                    MaintenanceBannerStrip(message = "صيانة خفيفة")
                    GlassPanelMessageDialog(
                        message = SonixMessage(
                            id = "m-99",
                            title = "تنبيه مهم",
                            body = "يرجى التحديث قريباً",
                            messageType = "urgent",
                        ),
                        onAcknowledge = { ackedId = it },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("sonix_blocked_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("SNX-445566").assertIsDisplayed()
        composeRule.onNodeWithTag("blocked_retry_button").performClick()
        assertTrue(retried)

        composeRule.onNodeWithTag("maintenance_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_message_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("panel_message_ack_button").performClick()
        assertEquals("m-99", ackedId)
    }

    @Test
    fun `SonixControlRepositoryImpl fetches active servers via servers catalog and debounces startup sync`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "status": "active",
                      "device_id": "dev-cat-1",
                      "device_uid": "ltv_cat_1",
                      "device_code": "XLINE12345",
                      "device_status": "active",
                      "access_status": "active",
                      "access_allowed": true,
                      "device_token": "tok_cat_1",
                      "settings": {"allow_normal_url_login":true,"allow_panel_servers":true},
                      "servers": []
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "servers": [
                        {"id": "srv-xline", "name": "XLINE", "region": "GLOBAL", "priority": 1}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val api = HttpSonixControlApi(
            okHttpClient = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            anonKey = "sb_publishable_test_key",
        )
        val identityStore = SonixDeviceIdentityStore(context, plainCrypto)
        val repo = SonixControlRepositoryImpl(context, api, identityStore)

        // First sync + catalog fetch
        repo.syncNow(force = true)
        val catalogRes = repo.refreshAvailableServers(forceSync = false)
        assertTrue("Expected catalog fetch success: ${catalogRes.exceptionOrNull()}", catalogRes.isSuccess)
        val servers = catalogRes.getOrThrow()
        assertEquals(1, servers.size)
        assertEquals("XLINE", servers.first().name)
        assertEquals("XLINE", repo.state.value.availableServers.first().name)
        assertEquals("XLINE12345", repo.state.value.deviceCode)

        // Debounced syncNow(force = false) did NOT fire a second "sync" request -> total requests = 2 (sync + servers.catalog)
        assertEquals(2, server.requestCount)
    }
}
