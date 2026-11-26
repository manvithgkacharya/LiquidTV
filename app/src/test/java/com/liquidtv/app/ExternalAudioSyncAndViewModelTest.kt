package com.liquidtv.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.audio.ExternalAudioCatalogRepository
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.remote.StremioAddonClient
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ExternalAudioCatalogResult
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.player.ExternalAudioController
import com.liquidtv.app.player.ExternalAudioEngineState
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.player.PlayerViewModel
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalAudioSyncAndViewModelTest {

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
    fun `ExternalAudioSyncPolicy calculates non-cumulative VOD targets, live speeds, and deltas`() {
        // VOD reference position is never cumulative
        assertEquals(58_500L, ExternalAudioSyncPolicy.targetVodPositionMs(60_000L, 1_500L, 120_000L))
        assertEquals(62_000L, ExternalAudioSyncPolicy.targetVodPositionMs(60_000L, -2_000L, 120_000L))
        assertEquals(0L, ExternalAudioSyncPolicy.targetVodPositionMs(500L, 2_000L, 120_000L))

        assertTrue(ExternalAudioSyncPolicy.shouldResyncVod(60_000L, 58_000L, 0L, 120_000L, 2_000L))
        assertFalse(ExternalAudioSyncPolicy.shouldResyncVod(60_000L, 59_800L, 0L, 120_000L, 2_000L))
        assertFalse(ExternalAudioSyncPolicy.shouldResyncVod(60_000L, 50_000L, 0L, 120_000L, 400L))

        // Live speed drift correction
        assertEquals(1.02f, ExternalAudioSyncPolicy.livePlaybackSpeed(1_000L, 1_500L, 0L))
        assertEquals(0.98f, ExternalAudioSyncPolicy.livePlaybackSpeed(1_500L, 1_000L, 0L))
        assertEquals(1.0f, ExternalAudioSyncPolicy.livePlaybackSpeed(1_000L, 1_100L, 0L))

        // Non-cumulative live delta adjustment
        val first = ExternalAudioSyncPolicy.computeLiveDeltaAdjustment(
            desiredOffsetMs = 1_000L,
            alreadyAppliedOffsetMs = 0L,
            currentAudioPositionMs = 5_000L,
            isCurrentItemSeekable = true,
            seekableWindowStartMs = 0L,
            seekableWindowEndMs = 20_000L,
        )
        assertEquals(4_000L, first.seekTargetMs)
        assertEquals(1_000L, first.newAppliedOffsetMs)

        // Pressing again with same desiredOffsetMs produces no-op
        val repeated = ExternalAudioSyncPolicy.computeLiveDeltaAdjustment(
            desiredOffsetMs = 1_000L,
            alreadyAppliedOffsetMs = first.newAppliedOffsetMs,
            currentAudioPositionMs = 4_000L,
            isCurrentItemSeekable = true,
            seekableWindowStartMs = 0L,
            seekableWindowEndMs = 20_000L,
        )
        assertNull(repeated.seekTargetMs)
        assertEquals(0L, repeated.holdDelayMs)

        // Retry backoff 1s -> 2s -> 4s -> 8s -> 16s -> 30s
        assertEquals(1_000L, ExternalAudioSyncPolicy.backoffDelayMs(1))
        assertEquals(4_000L, ExternalAudioSyncPolicy.backoffDelayMs(3))
        assertEquals(30_000L, ExternalAudioSyncPolicy.backoffDelayMs(6))
    }

    @Test
    fun `PlayerViewModel does not infer shared IPTV account from matching hosts, mutes only after PCM, and unmutes on detach or channel switch`() = runBlocking {
        var primaryVolume = 1.0f
        val primaryCommands = mutableListOf<String>()
        val playerProxy = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            if (method.name in setOf("setMediaItem", "prepare", "stop")) {
                primaryCommands += method.name
            }
            when (method.name) {
                "getCurrentPosition" -> 10_000L
                "getDuration" -> 0L
                "isPlaying" -> true
                "getPlayWhenReady" -> true
                "getPlaybackState" -> Player.STATE_READY
                "getCurrentTracks" -> Tracks.EMPTY
                "getVolume" -> primaryVolume
                "setVolume" -> {
                    primaryVolume = args?.get(0) as Float
                    Unit
                }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 1.0f
                    else -> null
                }
            }
        } as Player

        val ch1 = Channel(
            id = "ch-1",
            subscriptionId = "sub-1",
            externalId = "1",
            mediaType = MediaType.LIVE,
            name = "Sky Sports Main Event",
            streamUrl = "http://same-iptv.example.com/live/1.ts",
        )
        val ch2 = Channel(
            id = "ch-2",
            subscriptionId = "sub-1",
            externalId = "2",
            mediaType = MediaType.LIVE,
            name = "Sky Sports Premier",
            streamUrl = "http://same-iptv.example.com/live/2.ts",
        )
        val channelRepo = object : AddonTestChannelRepository() {
            override fun getAllChannels(): Flow<List<Channel>> = flowOf(listOf(ch1, ch2))
            override suspend fun getChannelById(id: String): Channel? = listOf(ch1, ch2).firstOrNull { it.id == id }
        }
        val prefsRepo = UserPreferencesDataStore(context)
        val addonPrefsRepo = InMemoryAddonPreferencesRepository()
        val addonClient = StremioAddonClient(OkHttpClient(), context)
        val subRepo = object : SubscriptionRepository {
            val singleConnSub = Subscription(
                id = "sub-1",
                name = "Main IPTV",
                serverUrl = "http://same-iptv.example.com",
                maxConnections = 1,
                isActive = true,
            )
            override val allSubscriptions: Flow<List<Subscription>> = flowOf(listOf(singleConnSub))
            override val activeSubscription: Flow<Subscription?> = flowOf(singleConnSub)
            override val syncInfo: Flow<PlaylistInfo> = flowOf(PlaylistInfo())
            override suspend fun getActiveSubscriptionOnce(): Subscription = singleConnSub
            override suspend fun getSubscriptionById(id: String): Subscription = singleConnSub
            override suspend fun hasAnySubscription(): Boolean = true
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

        val extChannels = listOf(
            ExternalAudioChannel("ext-1", "beIN SPORTS 1 HD", "1", "http://same-iptv.example.com/audio/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityRank = 4, qualityLabel = "HD"),
            ExternalAudioChannel("ext-2", "ALWAN 1 HD", "2", "http://other-audio.example.org/alwan1.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityRank = 4, qualityLabel = "HD"),
        )
        val fakeCatalogRepo = object : ExternalAudioCatalogRepository {
            override suspend fun loadCached() = ExternalAudioCatalogResult(channels = extChannels, fromCache = true)
            override suspend fun syncFromPanel(forceRefresh: Boolean) = ExternalAudioCatalogResult(channels = extChannels)
        }
        val fakeAudioCtrl = object : ExternalAudioController {
            val state = MutableStateFlow(ExternalAudioEngineState())
            override val engineState = state.asStateFlow()
            var attachedChannelId: String? = null
            var detachedCount = 0
            override fun attach(
                channel: ExternalAudioChannel, initialVideoPositionMs: Long, isVideoLive: Boolean,
                initialOffsetMs: Long, volumePercent: Int, loudnessBoostEnabled: Boolean, startPaused: Boolean,
            ) {
                attachedChannelId = channel.id
                state.value = ExternalAudioEngineState(
                    channelId = channel.id,
                    status = ExternalAudioStatus.CONNECTING,
                    isPcmVerified = false,
                )
            }
            override fun pause() {}
            override fun resume() {}
            override fun setVolumePercent(percent: Int) {}
            override fun setLoudnessBoostEnabled(enabled: Boolean) {}
            override fun applySyncOffset(offsetMs: Long, videoPositionMs: Long, isVideoLive: Boolean) {}
            override fun syncWithVideoPosition(videoPositionMs: Long, isVideoLive: Boolean, videoLiveOffsetMs: Long, forceSeek: Boolean) {}
            override fun detach() {
                detachedCount++
                attachedChannelId = null
                state.value = ExternalAudioEngineState()
            }
        }

        val vmStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            vmStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        channelRepository = channelRepo,
                        userPreferencesRepository = prefsRepo,
                        addonPreferencesRepository = addonPrefsRepo,
                        stremioAddonClient = addonClient,
                        subscriptionRepository = subRepo,
                        externalAudioCatalogRepository = fakeCatalogRepo,
                        externalAudioController = fakeAudioCtrl,
                        context = context,
                    ) as T
                }
            },
        )[PlayerViewModel::class.java]

        try {
            viewModel.attachPlayerForTesting(playerProxy)
            viewModel.loadItem("ch-1", MediaType.LIVE)
            delay(50)
            primaryCommands.clear()

            // Separate panel M3U: matching hostname alone must NOT trigger a shared-account warning.
            viewModel.openExternalAudioSheet()
            delay(50)
            assertEquals(2, viewModel.state.value.externalAudio.channels.size)

            viewModel.selectExternalAudioChannel(extChannels[0])
            delay(50)
            assertNull(viewModel.state.value.externalAudio.connectionLimitWarning)
            assertEquals(1.0f, primaryVolume, 0.001f)
            assertTrue("Primary player must NEVER reload during external audio activation", primaryCommands.isEmpty())

            // Once PCM output advances -> primary A is muted smoothly
            fakeAudioCtrl.state.value = ExternalAudioEngineState(
                channelId = extChannels[0].id,
                status = ExternalAudioStatus.ACTIVE,
                isPcmVerified = true,
                statusMessage = "نشط: beIN SPORTS 1 HD",
            )
            delay(50)
            assertEquals(0.0f, primaryVolume, 0.001f)

            // Closing the sheet MUST NOT stop external audio
            viewModel.closeExternalAudioSheet()
            assertFalse(viewModel.state.value.externalAudio.isSheetVisible)
            assertTrue(viewModel.state.value.externalAudio.isAttached)
            assertEquals(0.0f, primaryVolume, 0.001f)

            // Switching to channel 2 automatically detaches external audio & restores primary volume
            viewModel.loadItem("ch-2", MediaType.LIVE)
            delay(50)
            assertFalse(viewModel.state.value.externalAudio.isAttached)
            assertEquals(1.0f, primaryVolume, 0.001f)
            assertTrue(fakeAudioCtrl.detachedCount >= 1)
        } finally {
            vmStore.clear()
        }
    }



    @Test
    fun `openExternalAudioSheet displays local cache offline with zero network, and syncExternalAudioCatalog handles stages, double-taps, and failures`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cachedChannels = listOf(
            ExternalAudioChannel("ext-c1", "beIN SPORTS 1 HD", "1", "http://audio.example.org/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityRank = 4, qualityLabel = "HD"),
        )
        val refreshedChannels = cachedChannels + ExternalAudioChannel(
            "ext-c2", "ALWAN 1 FHD", "2", "http://audio.example.org/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityRank = 3, qualityLabel = "FHD",
        )
        val syncGate = CompletableDeferred<ExternalAudioCatalogResult>()
        val syncCallCount = AtomicInteger(0)
        var mode = "gated"

        val fakeCatalogRepo = object : ExternalAudioCatalogRepository {
            override fun peekCached() = ExternalAudioCatalogResult(
                channels = cachedChannels,
                fromCache = true,
                updatedAt = "t1",
                lastSyncedAtMs = 1764060000000L,
            )

            override suspend fun loadCached() = peekCached()

            override suspend fun syncFromPanel(forceRefresh: Boolean): ExternalAudioCatalogResult =
                syncFromPanelWithProgress(forceRefresh) {}

            override suspend fun syncFromPanelWithProgress(
                forceRefresh: Boolean,
                onProgress: (ExternalAudioSyncStage) -> Unit,
            ): ExternalAudioCatalogResult {
                syncCallCount.incrementAndGet()
                return when (mode) {
                    "gated" -> {
                        onProgress(ExternalAudioSyncStage.CONNECTING_PANEL)
                        onProgress(ExternalAudioSyncStage.DOWNLOADING_PLAYLIST)
                        val res = syncGate.await()
                        onProgress(ExternalAudioSyncStage.PARSING_CHANNELS)
                        onProgress(ExternalAudioSyncStage.SAVING_LOCAL)
                        onProgress(ExternalAudioSyncStage.SUCCEEDED)
                        res
                    }
                    "error" -> {
                        onProgress(ExternalAudioSyncStage.CONNECTING_PANEL)
                        onProgress(ExternalAudioSyncStage.DOWNLOADING_PLAYLIST)
                        onProgress(ExternalAudioSyncStage.FAILED)
                        ExternalAudioCatalogResult(
                            channels = refreshedChannels,
                            lastSyncedAtMs = 1764070000000L,
                            fromCache = true,
                            warning = "فشلت المزامنة: انتهت مهلة قراءة قائمة الصوت — تم الاحتفاظ بالنسخة المحلية",
                        )
                    }
                    "throw" -> throw IllegalStateException("unexpected runtime crash")
                    else -> ExternalAudioCatalogResult(
                        channels = refreshedChannels,
                        updatedAt = "t2",
                        lastSyncedAtMs = 1764070000000L,
                    )
                }
            }
        }

        val viewModel = buildViewModelForTest(context, fakeCatalogRepo)
        val vmStore = ViewModelStore()
        try {
            // 1. Preload in init populates cached channels + lastSyncedAtMs with zero network sync
            assertEquals(1, viewModel.state.value.externalAudio.channels.size)
            assertEquals(1764060000000L, viewModel.state.value.externalAudio.lastSyncedAtMs)
            assertEquals(0, syncCallCount.get())

            // 2. Open sheet -> cached channels appear immediately offline with ZERO network calls
            viewModel.openExternalAudioSheet()
            assertTrue(viewModel.state.value.externalAudio.isSheetVisible)
            assertFalse(viewModel.state.value.externalAudio.isLoadingCatalog)
            assertEquals(1, viewModel.state.value.externalAudio.channels.size)
            assertEquals(0, syncCallCount.get())

            // 3. Click "مزامنة الصوتيات" -> launches sync, emits stages live, and blocks rapid duplicate taps
            viewModel.syncExternalAudioCatalog()
            assertTrue(viewModel.state.value.externalAudio.isLoadingCatalog)
            assertEquals(ExternalAudioSyncStage.DOWNLOADING_PLAYLIST, viewModel.state.value.externalAudio.syncStage)
            viewModel.syncExternalAudioCatalog()
            viewModel.syncExternalAudioCatalog()
            assertEquals(1, syncCallCount.get())

            // Complete background sync -> updates channels, lastSyncedAtMs, SUCCEEDED, resets isLoadingCatalog
            syncGate.complete(
                ExternalAudioCatalogResult(
                    channels = refreshedChannels,
                    updatedAt = "t2",
                    lastSyncedAtMs = 1764070000000L,
                ),
            )
            delay(30)
            assertFalse(viewModel.state.value.externalAudio.isLoadingCatalog)
            assertEquals(2, viewModel.state.value.externalAudio.channels.size)
            assertEquals(1764070000000L, viewModel.state.value.externalAudio.lastSyncedAtMs)
            assertEquals(ExternalAudioSyncStage.SUCCEEDED, viewModel.state.value.externalAudio.syncStage)

            // 4. Failure result -> preserves previous channels + lastSyncedAtMs, sets FAILED + failure message, resets isLoadingCatalog
            mode = "error"
            viewModel.syncExternalAudioCatalog()
            delay(30)
            assertFalse(viewModel.state.value.externalAudio.isLoadingCatalog)
            assertEquals(2, viewModel.state.value.externalAudio.channels.size)
            assertEquals(1764070000000L, viewModel.state.value.externalAudio.lastSyncedAtMs)
            assertEquals(ExternalAudioSyncStage.FAILED, viewModel.state.value.externalAudio.syncStage)
            assertTrue(viewModel.state.value.externalAudio.syncStageMessage.orEmpty().contains("فشلت المزامنة"))

            // 5. Uncaught runtime exception -> keeps cached channels, sets FAILED, resets isLoadingCatalog = false
            mode = "throw"
            viewModel.syncExternalAudioCatalog()
            delay(30)
            assertFalse(viewModel.state.value.externalAudio.isLoadingCatalog)
            assertEquals(2, viewModel.state.value.externalAudio.channels.size)
            assertEquals(ExternalAudioSyncStage.FAILED, viewModel.state.value.externalAudio.syncStage)
        } finally {
            vmStore.clear()
        }
    }

    @Test
    fun `empty first-time open triggers initial sync, while reopened sheet after preload stays offline-ready`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val syncedChannels = listOf(
            ExternalAudioChannel("ext-1", "beIN SPORTS 1 4K", "1", "http://audio.example.org/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityRank = 1, qualityLabel = "4K"),
        )
        var savedDiskChannels: List<ExternalAudioChannel> = emptyList()
        var savedAtMs: Long? = null
        val syncCalls = AtomicInteger(0)

        val fakeCatalogRepo = object : ExternalAudioCatalogRepository {
            override fun peekCached(): ExternalAudioCatalogResult? =
                if (savedDiskChannels.isEmpty()) null else ExternalAudioCatalogResult(
                    channels = savedDiskChannels,
                    lastSyncedAtMs = savedAtMs,
                    fromCache = true,
                )

            override suspend fun loadCached(): ExternalAudioCatalogResult =
                peekCached() ?: ExternalAudioCatalogResult(channels = emptyList())

            override suspend fun syncFromPanel(forceRefresh: Boolean): ExternalAudioCatalogResult =
                syncFromPanelWithProgress(forceRefresh) {}

            override suspend fun syncFromPanelWithProgress(
                forceRefresh: Boolean,
                onProgress: (ExternalAudioSyncStage) -> Unit,
            ): ExternalAudioCatalogResult {
                syncCalls.incrementAndGet()
                onProgress(ExternalAudioSyncStage.CONNECTING_PANEL)
                onProgress(ExternalAudioSyncStage.SAVING_LOCAL)
                savedDiskChannels = syncedChannels
                savedAtMs = 1764075555000L
                onProgress(ExternalAudioSyncStage.SUCCEEDED)
                return ExternalAudioCatalogResult(
                    channels = savedDiskChannels,
                    lastSyncedAtMs = savedAtMs,
                )
            }
        }

        // First session (empty local disk) -> opening sheet automatically runs 1 initial sync
        val vm1 = buildViewModelForTest(context, fakeCatalogRepo)
        vm1.openExternalAudioSheet()
        delay(30)
        assertEquals(1, syncCalls.get())
        assertEquals(1, vm1.state.value.externalAudio.channels.size)
        assertEquals(1764075555000L, vm1.state.value.externalAudio.lastSyncedAtMs)

        // Second session ("after app close and reopen") -> loads local disk immediately, open sheet makes 0 sync calls
        val vm2 = buildViewModelForTest(context, fakeCatalogRepo)
        vm2.openExternalAudioSheet()
        delay(30)
        assertEquals(1, syncCalls.get())
        assertEquals(1, vm2.state.value.externalAudio.channels.size)
        assertEquals("beIN SPORTS 1 4K", vm2.state.value.externalAudio.channels.first().name)
        assertEquals(1764075555000L, vm2.state.value.externalAudio.lastSyncedAtMs)
    }

    private fun buildViewModelForTest(
        context: Context,
        fakeCatalogRepo: ExternalAudioCatalogRepository,
    ): PlayerViewModel {
        val channelRepo = object : AddonTestChannelRepository() {
            override fun getAllChannels(): Flow<List<Channel>> = flowOf(emptyList())
        }
        val prefsRepo = UserPreferencesDataStore(context)
        val addonPrefsRepo = InMemoryAddonPreferencesRepository()
        val addonClient = StremioAddonClient(OkHttpClient(), context)
        val subRepo = object : SubscriptionRepository {
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
        val fakeAudioCtrl = object : ExternalAudioController {
            override val engineState = MutableStateFlow(ExternalAudioEngineState()).asStateFlow()
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
        return ViewModelProvider(
            ViewModelStore(),
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        channelRepository = channelRepo,
                        userPreferencesRepository = prefsRepo,
                        addonPreferencesRepository = addonPrefsRepo,
                        stremioAddonClient = addonClient,
                        subscriptionRepository = subRepo,
                        externalAudioCatalogRepository = fakeCatalogRepo,
                        externalAudioController = fakeAudioCtrl,
                        context = context,
                    ) as T
                }
            },
        )[PlayerViewModel::class.java]
    }
}
