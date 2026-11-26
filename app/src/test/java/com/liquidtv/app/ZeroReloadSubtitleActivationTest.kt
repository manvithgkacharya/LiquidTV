package com.liquidtv.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.remote.AddonEndpoints
import com.liquidtv.app.data.remote.StremioAddonClient
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.OnlineSubtitleResult
import com.liquidtv.app.domain.model.SubtitleFontOption
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import com.liquidtv.app.data.audio.ExternalAudioCatalogRepository
import com.liquidtv.app.domain.model.ExternalAudioCatalogResult
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.player.ExternalAudioController
import com.liquidtv.app.player.ExternalAudioEngineState
import com.liquidtv.app.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZeroReloadSubtitleActivationTest {

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
    fun `activating, switching, toggling, styling, and delaying external subtitles NEVER reloads bound Player`() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/online.srt" -> MockResponse().setResponseCode(200).setBody(
                        """
                        1
                        00:00:08,000 --> 00:00:14,000
                        الترجمة الأولى من الإنترنت
                        """.trimIndent()
                    )
                    "/direct.vtt" -> MockResponse().setResponseCode(200).setBody(
                        """
                        WEBVTT

                        00:09.000 --> 00:15.000
                        الترجمة المباشرة الثانية
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val movieChannel = Channel(
            id = "mv-1",
            name = "Inception",
            streamUrl = "http://stream.local/movie.mp4",
            groupTitle = "Movies",
            mediaType = MediaType.MOVIE,
        )
        val channelRepo = object : AddonTestChannelRepository() {
            override suspend fun getChannelById(id: String): Channel? = if (id == "mv-1") movieChannel else null
        }
        val prefsRepo = FakeUserPreferencesRepository()
        val addonPrefsRepo = InMemoryAddonPreferencesRepository()
        val addonClient = StremioAddonClient(OkHttpClient(), context).apply {
            endpoints = AddonEndpoints(
                officialCatalogUrl = "$baseUrl/o.json",
                communityCatalogPrimaryUrl = "$baseUrl/c.json",
                communityCatalogFallbackUrl = "$baseUrl/c.json",
                cinemetaBaseUrl = baseUrl,
            )
        }

        val lifecycleAndCommandCalls = Collections.synchronizedList(mutableListOf<String>())
        val recordedCommandNames = setOf(
            "stop",
            "clearMediaItems",
            "setMediaItem",
            "setMediaItems",
            "prepare",
            "setTrackSelectionParameters",
            "setPlayWhenReady",
            "play",
            "pause",
            "seekTo",
        )
        var simulatedPositionMs = 10_000L
        var trackSelectionParams = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        val playerProxy = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            if (method.name in recordedCommandNames) {
                lifecycleAndCommandCalls += method.name
            }
            when (method.name) {
                "getCurrentPosition" -> simulatedPositionMs
                "getDuration" -> 120_000L
                "isPlaying" -> true
                "getPlayWhenReady" -> true
                "getPlaybackState" -> Player.STATE_READY
                "getCurrentTracks" -> Tracks.EMPTY
                "getTrackSelectionParameters" -> trackSelectionParams
                "setTrackSelectionParameters" -> {
                    trackSelectionParams = args?.get(0) as TrackSelectionParameters
                    Unit
                }
                "addListener", "removeListener", "stop", "clearMediaItems", "setMediaItem", "setMediaItems", "prepare", "setPlayWhenReady", "play", "pause", "seekTo" -> Unit
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 1.0f
                    else -> null
                }
            }
        } as Player

        val vmStore = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val fakeSubRepo = object : SubscriptionRepository {
                    override val allSubscriptions: Flow<List<Subscription>> = flowOf(emptyList())
                    override val activeSubscription: Flow<Subscription?> = flowOf(null)
                    override val syncInfo: Flow<PlaylistInfo> = flowOf(PlaylistInfo())
                    override suspend fun getActiveSubscriptionOnce(): Subscription? = null
                    override suspend fun getSubscriptionById(id: String): Subscription? = null
                    override suspend fun hasAnySubscription(): Boolean = false
                    override suspend fun addOrUpdateXtreamSubscription(
                        existingId: String?, displayName: String, serverUrl: String, username: String, password: String,
                        origin: SubscriptionOrigin, panelServerId: String?, panelServerName: String?, activate: Boolean,
                    ): Result<Subscription> = Result.failure(UnsupportedOperationException())
                    override suspend fun activateSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
                    override suspend fun syncSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
                    override suspend fun syncActiveSubscription(): Result<Unit> = Result.success(Unit)
                    override suspend fun syncActiveEpg(): Result<Unit> = Result.success(Unit)
                    override suspend fun deleteSubscription(subscriptionId: String): Result<Unit> = Result.success(Unit)
                    override suspend fun getDecryptedXtreamPassword(subscriptionId: String): String? = null
                }
                val fakeCatalogRepo = object : ExternalAudioCatalogRepository {
                    override suspend fun loadCached() = ExternalAudioCatalogResult()
                    override suspend fun syncFromPanel(forceRefresh: Boolean) = ExternalAudioCatalogResult()
                }
                val fakeAudioCtrl = object : ExternalAudioController {
                    override val engineState = MutableStateFlow(ExternalAudioEngineState())
                    override fun attach(
                        channel: ExternalAudioChannel, initialVideoPositionMs: Long, isVideoLive: Boolean,
                        initialOffsetMs: Long, volumePercent: Int, loudnessBoostEnabled: Boolean, startPaused: Boolean,
                    ) {}
                    override fun pause() {}
                    override fun resume() {}
                    override fun setVolumePercent(percent: Int) {}
                    override fun setLoudnessBoostEnabled(enabled: Boolean) {}
                    override fun applySyncOffset(offsetMs: Long, videoPositionMs: Long, isVideoLive: Boolean) {}
                    override fun syncWithVideoPosition(videoPositionMs: Long, isVideoLive: Boolean, videoLiveOffsetMs: Long, forceSeek: Boolean) {}
                    override fun detach() {}
                }
                return PlayerViewModel(
                    channelRepository = channelRepo,
                    userPreferencesRepository = prefsRepo,
                    addonPreferencesRepository = addonPrefsRepo,
                    stremioAddonClient = addonClient,
                    subscriptionRepository = fakeSubRepo,
                    externalAudioCatalogRepository = fakeCatalogRepo,
                    externalAudioController = fakeAudioCtrl,
                    context = context,
                ) as T
            }
        }
        val viewModel = ViewModelProvider(vmStore, factory)[PlayerViewModel::class.java]

        try {
            viewModel.attachPlayerForTesting(playerProxy)
            viewModel.loadItem("mv-1", MediaType.MOVIE)
            waitUntilCondition { viewModel.state.value.channel?.id == "mv-1" }

            // Reset recorded command calls AFTER initial stream start so we strictly assert zero-reload during subtitle operations!
            lifecycleAndCommandCalls.clear()

            // 1. Activate online Stremio subtitle
            viewModel.applyOnlineSubtitle(
                OnlineSubtitleResult(
                    url = "$baseUrl/online.srt",
                    label = "Arabic Online",
                    language = "ar",
                    addonName = "OpenSubtitles V3",
                )
            )
            waitUntilCondition { viewModel.state.value.activeExternalSubtitleLabel == "Arabic Online" && !viewModel.state.value.isApplyingSubtitle }
            assertTrue(viewModel.state.value.externalSubtitlesEnabled)
            assertEquals("الترجمة الأولى من الإنترنت", viewModel.state.value.activeExternalCueText)
            assertTrue(
                "Embedded C.TRACK_TYPE_TEXT must be disabled for mutual exclusion",
                trackSelectionParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT),
            )

            // 2. Switch to direct URL subtitle
            viewModel.loadExternalSubtitle("$baseUrl/direct.vtt", "ar")
            waitUntilCondition { viewModel.state.value.activeExternalCueText == "الترجمة المباشرة الثانية" && !viewModel.state.value.isApplyingSubtitle }
            assertEquals("الترجمة المباشرة الثانية", viewModel.state.value.activeExternalCueText)

            // 3. Load local subtitle file URI
            val localFile = File(context.filesDir, "local_test.srt").apply {
                writeText("1\n00:00:05,000 --> 00:00:12,000\nترجمة محلية من الجهاز\n")
            }
            viewModel.applyLocalSubtitleUri(Uri.fromFile(localFile))
            waitUntilCondition { viewModel.state.value.activeExternalCueText == "ترجمة محلية من الجهاز" && !viewModel.state.value.isApplyingSubtitle }
            assertEquals("ترجمة محلية من الجهاز", viewModel.state.value.activeExternalCueText)

            // 4. Toggle OFF then back ON
            viewModel.toggleExternalSubtitles()
            assertFalse(viewModel.state.value.externalSubtitlesEnabled)
            assertNull(viewModel.state.value.activeExternalCueText)

            viewModel.toggleExternalSubtitles()
            assertTrue(viewModel.state.value.externalSubtitlesEnabled)
            assertEquals("ترجمة محلية من الجهاز", viewModel.state.value.activeExternalCueText)

            // 5. Delay shift (-5000ms) moves cue window [5s..12s] to [0s..7s], hiding it at 10s position
            viewModel.setSubtitleDelay(-5000)
            assertNull(viewModel.state.value.activeExternalCueText)
            viewModel.setSubtitleDelay(0)
            assertEquals("ترجمة محلية من الجهاز", viewModel.state.value.activeExternalCueText)

            // 6. Appearance change persists cleanly without reload
            viewModel.updateSubtitleStyle {
                it.copy(fontId = SubtitleFontOption.ALMARAI_EXTRABOLD.id, fontSizeSp = 32)
            }
            assertEquals("almarai_extrabold", viewModel.state.value.subtitleStyle.fontId)

            // 7. Disable all subtitles clears cue without reloading stream
            viewModel.selectSubtitleTrack(null)
            assertFalse(viewModel.state.value.externalSubtitlesEnabled)
            assertNull(viewModel.state.value.activeExternalCueText)

            val forbiddenReloadCalls = lifecycleAndCommandCalls.filter {
                it in setOf("stop", "clearMediaItems", "setMediaItem", "setMediaItems", "prepare")
            }
            assertTrue(
                "Expected ZERO reload calls during external subtitle operations, but saw: $forbiddenReloadCalls",
                forbiddenReloadCalls.isEmpty(),
            )
            assertEquals(10_000L, simulatedPositionMs)
        } finally {
            vmStore.clear()
        }
    }

    private suspend fun waitUntilCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) break
            delay(15L)
        }
    }
}

private class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val prefs = MutableStateFlow(UserPreferences())
    override val userPreferences: Flow<UserPreferences> = prefs.asStateFlow()
    override val searchHistory: Flow<List<String>> = flowOf(emptyList())
    override fun getGlassIntensity(): Flow<Float> = flowOf(1f)
    override fun getBlurIntensity(): Flow<Float> = flowOf(1f)
    override fun getAnimationIntensity(): Flow<Float> = flowOf(1f)
    override fun getAppTheme(): Flow<String> = flowOf("aurora")
    override fun getAmbientTheme(): Flow<String> = flowOf("aurora")
    override fun getAmbientEnabled(): Flow<Boolean> = flowOf(false)
    override fun getAmbientIdleDuration(): Flow<Long> = flowOf(60_000L)
    override fun getAmbientClockVisible(): Flow<Boolean> = flowOf(true)
    override fun getAutoPlayNext(): Flow<Boolean> = flowOf(true)
    override fun getStreamTimeout(): Flow<Int> = flowOf(15)
    override fun getRetryAttempts(): Flow<Int> = flowOf(3)
    override fun getLastSearchQuery(): Flow<String> = flowOf("")
    override suspend fun setGlassIntensity(value: Float) = Unit
    override suspend fun setBlurIntensity(value: Float) = Unit
    override suspend fun setAnimationIntensity(value: Float) = Unit
    override suspend fun setAppTheme(themeId: String) = Unit
    override suspend fun setAmbientTheme(theme: String) = Unit
    override suspend fun setAmbientEnabled(enabled: Boolean) = Unit
    override suspend fun setAmbientIdleDuration(ms: Long) = Unit
    override suspend fun setAmbientClockVisible(visible: Boolean) = Unit
    override suspend fun setAutoPlayNext(enabled: Boolean) = Unit
    override suspend fun setStreamTimeout(seconds: Int) = Unit
    override suspend fun setRetryAttempts(count: Int) = Unit
    override suspend fun setDefaultQuality(quality: String) = Unit
    override suspend fun setAutoPlay(enabled: Boolean) = Unit
    override suspend fun setSubtitlesEnabled(enabled: Boolean) = Unit
    override suspend fun setPreferredSubtitleLanguage(language: String) = Unit
    override suspend fun setTheme(theme: String) = Unit
    override suspend fun setClockVisible(visible: Boolean) = Unit
    override suspend fun setScreenSaverTimeout(minutes: Int) = Unit
    override suspend fun setAutoRefreshEnabled(enabled: Boolean) = Unit
    override suspend fun setAutoRefreshInterval(hours: Int) = Unit
    override suspend fun setConnectionTimeout(seconds: Int) = Unit
    override suspend fun setMaxRetries(retries: Int) = Unit
    override suspend fun setParentalControlEnabled(enabled: Boolean) = Unit
    override suspend fun setParentalPin(pin: String) = Unit
    override suspend fun verifyParentalPin(pin: String): Boolean = false
    override suspend fun clearParentalPin() = Unit
    override suspend fun setGithubRepo(repo: String) = Unit
    override suspend fun setAutoCheckUpdates(enabled: Boolean) = Unit
    override suspend fun addSearchHistory(query: String) = Unit
    override suspend fun removeSearchHistory(query: String) = Unit
    override suspend fun clearSearchHistory() = Unit
}
