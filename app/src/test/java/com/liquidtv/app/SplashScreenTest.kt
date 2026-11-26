package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.ui.StartupViewModel
import com.liquidtv.app.ui.components.SONIX_SPLASH_TOTAL_DURATION_MS
import com.liquidtv.app.ui.components.SonixSplashOverlay
import com.liquidtv.app.ui.components.computeSplashChoreographyFrame
import com.liquidtv.app.ui.theme.LiquidTVTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class SplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `choreography milestones follow 5-stage emblem-first SONIX-then-IPTV sequence cleanly`() {
        // Stage 1 (0.0s - 0.5s): ambient black & soft glow only; icon & text hidden
        val stage1 = computeSplashChoreographyFrame(0.06f)
        assertTrue(stage1.backdropAlpha > 0f)
        assertEquals(0f, stage1.emblemAlpha, 0.001f)
        assertEquals(0f, stage1.sonixAlpha, 0.001f)
        assertEquals(0f, stage1.iptvAlpha, 0.001f)

        // Stage 2 (0.5s - 1.8s): emblem visible & centered first, NO text yet
        val stage2 = computeSplashChoreographyFrame(0.24f)
        assertTrue(stage2.emblemAlpha > 0.3f)
        assertTrue(stage2.sheenProgress in 0f..1f)
        assertEquals(1f, stage2.emblemCenterFraction, 0.001f)
        assertEquals(0f, stage2.sonixAlpha, 0.001f)
        assertEquals(0f, stage2.iptvAlpha, 0.001f)

        // Stage 3a (1.8s - 2.45s): SONIX appears first while IPTV starts slightly after
        val stage3a = computeSplashChoreographyFrame(0.41f)
        assertTrue(stage3a.sonixAlpha > 0.3f)
        assertEquals(0f, stage3a.iptvAlpha, 0.001f)

        // Stage 3b (2.2s - 2.8s): IPTV appears below SONIX
        val stage3b = computeSplashChoreographyFrame(0.51f)
        assertTrue(stage3b.sonixAlpha > 0.9f)
        assertTrue(stage3b.iptvAlpha > 0.4f)

        // Stage 4 (2.8s - 4.5s): settled static rest (zero looping jitter)
        val stage4Early = computeSplashChoreographyFrame(0.65f)
        val stage4Late = computeSplashChoreographyFrame(0.84f)
        assertEquals(1f, stage4Early.emblemAlpha, 0.001f)
        assertEquals(1f, stage4Early.sonixAlpha, 0.001f)
        assertEquals(1f, stage4Early.iptvAlpha, 0.001f)
        assertEquals(1f, stage4Early.overallExitAlpha, 0.001f)
        assertEquals(stage4Early.emblemScale, stage4Late.emblemScale, 0.0001f)
        assertEquals(stage4Early.ambientGlowAlpha, stage4Late.ambientGlowAlpha, 0.0001f)

        // Stage 5 (4.5s - 5.0s): smooth fade out
        val stage5 = computeSplashChoreographyFrame(0.95f)
        assertTrue(stage5.overallExitAlpha in 0.05f..0.95f)
        val stageEnd = computeSplashChoreographyFrame(1.0f)
        assertEquals(0f, stageEnd.overallExitAlpha, 0.001f)
    }

    @Test
    fun `SonixSplashOverlay runs full 5 seconds when ready early`() {
        composeRule.mainClock.autoAdvance = false
        var finishedCount = 0

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1280.dp).height(720.dp)) {
                    SonixSplashOverlay(
                        isAppReady = true,
                        totalDurationMs = SONIX_SPLASH_TOTAL_DURATION_MS,
                        onSplashFinished = { finishedCount++ },
                    )
                }
            }
        }

        // At 1.0s: emblem layer visible, wordmark still hidden
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag("sonix_splash_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("sonix_splash_emblem_layer").assertExists()
        composeRule.onNodeWithTag("sonix_splash_wordmark_layer").assertDoesNotExist()
        assertEquals(0, finishedCount)

        // At 2.0s: SONIX wordmark starts revealing
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.onNodeWithTag("sonix_splash_wordmark_layer").assertExists()
        assertEquals(0, finishedCount)

        // At 4.4s: still on splash (must not exit prematurely before 5 seconds)
        composeRule.mainClock.advanceTimeBy(2_400L)
        assertEquals(0, finishedCount)
        composeRule.onNodeWithTag("sonix_splash_logo").assertIsDisplayed()

        // After 5.0s total: fades out and completes once
        composeRule.mainClock.advanceTimeBy(750L)
        assertEquals(1, finishedCount)
    }

    @Test
    fun `SonixSplashOverlay holds calmly when startup checks exceed 5 seconds then finishes smoothly`() {
        composeRule.mainClock.autoAdvance = false
        var slowReady by mutableStateOf(false)
        var slowFinished = false
        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(412.dp).height(915.dp)) {
                    SonixSplashOverlay(
                        isAppReady = slowReady,
                        totalDurationMs = SONIX_SPLASH_TOTAL_DURATION_MS,
                        onSplashFinished = { slowFinished = true },
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(6_000L)
        assertFalse("Splash must keep holding calmly while startup check is still pending", slowFinished)
        composeRule.onNodeWithTag("sonix_splash_overlay").assertIsDisplayed()

        slowReady = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(700L)
        composeRule.waitForIdle()
        assertTrue("Splash must finish smoothly once startup check resolves", slowFinished)
    }

    @Test
    fun `StartupViewModel keeps splash completion sticky across recomposition and configuration recreation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        val vm = StartupViewModel(fakeSubRepo, UserPreferencesDataStore(context), FakeSonixControlRepository(SonixSyncState()))

        assertFalse(vm.isSplashFinished.value)
        assertEquals(0f, vm.splashResumeProgress, 0.001f)

        vm.updateSplashProgress(0.45f)
        assertEquals(0.45f, vm.splashResumeProgress, 0.001f)

        vm.markSplashFinished()
        assertTrue(vm.isSplashFinished.value)

        // Progress updates after completion must not reset splash back
        vm.updateSplashProgress(0.10f)
        assertTrue(vm.isSplashFinished.value)
        assertEquals(1f, vm.splashResumeProgress, 0.001f)
    }
}
