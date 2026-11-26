package com.liquidtv.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.OnlineSubtitleResult
import com.liquidtv.app.domain.model.SubtitleMediaMetadata
import com.liquidtv.app.domain.model.TrackInfo
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.player.PlayerUiState
import com.liquidtv.app.ui.screens.player.ExternalAudioSheet
import com.liquidtv.app.ui.screens.player.PlayerOverlaySheetDefaults
import com.liquidtv.app.ui.screens.player.PlayerSubtitlesAndTracksSheet
import com.liquidtv.app.ui.theme.LiquidTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class ExternalAudioSheetUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `ExternalAudioSheet renders reference layout sections, controls, and triggers all callbacks`() {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var selectedFilter = AudioBrandFilter.ALL
        var detachedCalled = false
        var offsetDelta = 0L
        var resetCalled = false
        var boostToggled = false
        var latestVolume = 80
        var clickedChannelId: String? = null
        var closedCalled = false

        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        channels = channels,
                        activeChannel = channels[0],
                        status = ExternalAudioStatus.ACTIVE,
                        statusMessage = "نشط: beIN SPORTS 1 HD",
                        selectedBrandFilter = selectedFilter,
                        volumePercent = latestVolume,
                    ),
                    onSelectChannel = { clickedChannelId = it.id },
                    onDetachAudio = { detachedCalled = true },
                    onRefreshCatalog = {},
                    onSearchQueryChange = {},
                    onSelectBrandFilter = { selectedFilter = it },
                    onVolumeChange = { latestVolume = it },
                    onToggleLoudnessBoost = { boostToggled = true },
                    onAdjustSyncOffset = { offsetDelta += it },
                    onResetSyncOffset = { resetCalled = true },
                    onClose = { closedCalled = true },
                )
            }
        }

        composeRule.waitForIdle()
        // Top header bar
        composeRule.onNodeWithTag("external_audio_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("مصدر الصوت الخارجي").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_count_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_connection_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_sync_catalog_button").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_close_button").assertIsDisplayed()

        // Control panel sections
        composeRule.onNodeWithText("التحكم بالصوت").assertIsDisplayed()
        composeRule.onNodeWithText("مستوى الصوت").assertIsDisplayed()
        composeRule.onNodeWithText("تعزيز الصوت").assertIsDisplayed()
        composeRule.onNodeWithText("مزامنة الصوت").assertIsDisplayed()
        composeRule.onNodeWithText("التأخير الحالي").assertIsDisplayed()
        composeRule.onNodeWithText("تصفير التأخير").assertIsDisplayed()

        // Sources & active badge & footer
        composeRule.onNodeWithText("beIN SPORTS 1 HD").assertIsDisplayed()
        composeRule.onNodeWithText("ALWAN 2 FHD").assertIsDisplayed()
        composeRule.onNodeWithText("نشط").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_footer_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_selected_footer_text").assertIsDisplayed()

        // Test interactive controls
        composeRule.onNodeWithTag("external_audio_channel_2").performClick()
        composeRule.onNodeWithTag("external_audio_vol_up").performClick()
        composeRule.onNodeWithTag("external_audio_vol_down").performClick()
        composeRule.onNodeWithTag("external_audio_boost_toggle").performClick()
        composeRule.onNodeWithTag("external_audio_sync_plus").performClick()
        composeRule.onNodeWithTag("external_audio_sync_fine_plus").performClick()
        composeRule.onNodeWithTag("external_audio_sync_fine_minus").performClick()
        composeRule.onNodeWithTag("external_audio_sync_minus").performClick()
        composeRule.onNodeWithTag("external_audio_sync_reset").performClick()
        composeRule.onNodeWithTag("external_audio_detach_button").performClick()
        composeRule.onNodeWithTag("external_audio_filter_alwan").performClick()
        composeRule.onNodeWithTag("external_audio_close_button").performClick()

        assertEquals("2", clickedChannelId)
        assertTrue(boostToggled)
        assertEquals(0L, offsetDelta)
        assertTrue(resetCalled)
        assertTrue(detachedCalled)
        assertEquals(AudioBrandFilter.ALWAN, selectedFilter)
        assertTrue(closedCalled)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w850dp-h390dp-xhdpi")
    fun `ExternalAudioSheet shows at least 6 full channels simultaneously on Phone Landscape`() {
        verifyAtLeastSixChannelsVisible()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
    fun `ExternalAudioSheet shows at least 6 full channels simultaneously on TV Landscape`() {
        verifyAtLeastSixChannelsVisible()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w390dp-h800dp-xhdpi")
    fun `ExternalAudioSheet shows at least 6 full channels simultaneously on Phone Portrait`() {
        verifyAtLeastSixChannelsVisible()
    }

    @Test
    fun `ExternalAudioSheet keeps side-by-side reference alignment in RTL on TV`() {
        val channels = (1..6).map { idx ->
            ExternalAudioChannel("$idx", "beIN SPORTS $idx SD", idx.toString(), "http://ex/$idx.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "SD")
        }
        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        channels = channels,
                        activeChannel = channels[0],
                        status = ExternalAudioStatus.ACTIVE,
                    ),
                    onSelectChannel = {},
                    onDetachAudio = {},
                    onRefreshCatalog = {},
                    onSearchQueryChange = {},
                    onSelectBrandFilter = {},
                    onVolumeChange = {},
                    onToggleLoudnessBoost = {},
                    onAdjustSyncOffset = {},
                    onResetSyncOffset = {},
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        val sheetBounds = composeRule.onNodeWithTag("external_audio_sheet").getUnclippedBoundsInRoot()
        val listBounds = composeRule.onNodeWithTag("external_audio_channel_list").getUnclippedBoundsInRoot()
        val controlTitleBounds = composeRule.onNodeWithText("التحكم بالصوت").getUnclippedBoundsInRoot()
        val footerBounds = composeRule.onNodeWithTag("external_audio_footer_bar").getUnclippedBoundsInRoot()

        // In RTL reference image: Channels list is on the left, Control panel is on the right, Footer is at bottom
        assertTrue("Expected channels list left of control panel in RTL", listBounds.right.value <= controlTitleBounds.left.value + 16f)
        assertTrue("Expected footer below channel list", footerBounds.top.value >= listBounds.bottom.value - 2f)
        assertTrue("Expected sheet within 840dp ceiling", sheetBounds.width.value <= 840.5f)
    }

    @Test
    fun `ExternalAudioSheet and PlayerSubtitlesAndTracksSheet maintain stable dimensions across states`() {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var showAudio by mutableStateOf(true)
        var audioUiState by mutableStateOf(
            ExternalAudioUiState(isSheetVisible = true, isLoadingCatalog = true, channels = emptyList()),
        )
        val playerUiState = PlayerUiState(
            mediaType = MediaType.MOVIE,
            subtitleMetadata = SubtitleMediaMetadata(title = "Test Movie", mediaType = MediaType.MOVIE),
            audioTracks = listOf(TrackInfo(0, 0, "ar", "Arabic Stereo", false)),
            subtitleSearchResults = listOf(
                OnlineSubtitleResult(url = "http://ex/s1.srt", label = "Arabic WEB-DL Subtitles.srt", language = "ar", addonName = "OpenSubtitles"),
            ),
        )

        composeRule.setContent {
            LiquidTVTheme {
                if (showAudio) {
                    ExternalAudioSheet(
                        state = audioUiState,
                        onSelectChannel = {},
                        onDetachAudio = {},
                        onRefreshCatalog = {},
                        onSearchQueryChange = {},
                        onSelectBrandFilter = {},
                        onVolumeChange = {},
                        onToggleLoudnessBoost = {},
                        onAdjustSyncOffset = {},
                        onResetSyncOffset = {},
                        onClose = {},
                    )
                } else {
                    PlayerSubtitlesAndTracksSheet(
                        uiState = playerUiState,
                        onSelectAudio = {},
                        onSelectSubtitle = {},
                        onToggleExternalSubtitles = {},
                        onLoadExternalSubtitleUrl = { _, _ -> },
                        onPickLocalSubtitleUri = {},
                        onSelectSearchLanguage = {},
                        onSearchOnlineSubtitles = {},
                        onApplyOnlineSubtitle = {},
                        onUpdateStyle = {},
                        onAdjustDelay = {},
                        onSetDelay = {},
                        onClose = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val audioLoadingBounds = composeRule.onNodeWithTag("external_audio_sheet").getUnclippedBoundsInRoot()

        audioUiState = ExternalAudioUiState(
            isSheetVisible = true,
            isLoadingCatalog = false,
            channels = channels,
            activeChannel = channels[0],
            status = ExternalAudioStatus.ACTIVE,
            statusMessage = "نشط: beIN SPORTS 1 HD",
        )
        composeRule.waitForIdle()
        val audioActiveBounds = composeRule.onNodeWithTag("external_audio_sheet").getUnclippedBoundsInRoot()
        assertTrue(abs(audioLoadingBounds.width.value - audioActiveBounds.width.value) < 0.5f)
        assertTrue(abs(audioLoadingBounds.height.value - audioActiveBounds.height.value) < 0.5f)
        assertTrue(audioActiveBounds.width.value <= 840.5f)

        showAudio = false
        composeRule.waitForIdle()
        val subTracksBounds = composeRule.onNodeWithTag("player_tracks_sheet").getUnclippedBoundsInRoot()
        val subOptionRowBounds = composeRule.onNodeWithTag("disable_all_subtitles_row").getUnclippedBoundsInRoot()
        assertTrue(subTracksBounds.width.value <= PlayerOverlaySheetDefaults.SheetMaxWidth.value + 0.5f)
        assertTrue(abs(subOptionRowBounds.height.value - PlayerOverlaySheetDefaults.OptionCardHeight.value) < 0.5f)
    }

    @Test
    fun `ExternalAudioSheet search input filters and updates cleanly without clipping`() {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var latestQuery by mutableStateOf("")
        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        channels = channels,
                        searchQuery = latestQuery,
                    ),
                    onSelectChannel = {},
                    onDetachAudio = {},
                    onRefreshCatalog = {},
                    onSearchQueryChange = { latestQuery = it },
                    onSelectBrandFilter = {},
                    onVolumeChange = {},
                    onToggleLoudnessBoost = {},
                    onAdjustSyncOffset = {},
                    onResetSyncOffset = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithTag("external_audio_search_input").performTextInput("ALWAN")
        composeRule.waitForIdle()
        assertEquals("ALWAN", latestQuery)
        composeRule.onNodeWithText("ALWAN 2 FHD").assertIsDisplayed()
    }

    @Test
    fun `ExternalAudioSheet displays sync button, stage progress, last synced timestamp, and guards double taps`() {
        val channels = listOf(
            ExternalAudioChannel("1", "beIN SPORTS 1 HD", "1", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN SPORTS", qualityLabel = "HD"),
            ExternalAudioChannel("2", "ALWAN 2 FHD", "2", "http://ex/2.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityLabel = "FHD"),
        )
        var syncClicks = 0
        var loadingState by mutableStateOf(false)
        var stage by mutableStateOf(ExternalAudioSyncStage.SUCCEEDED)
        var stageMsg by mutableStateOf("تمت المزامنة بنجاح")

        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        isLoadingCatalog = loadingState,
                        syncStage = stage,
                        syncStageMessage = stageMsg,
                        lastSyncedAtMs = 1764070000000L,
                        channels = channels,
                    ),
                    onSelectChannel = {},
                    onDetachAudio = {},
                    onRefreshCatalog = { syncClicks++ },
                    onSearchQueryChange = {},
                    onSelectBrandFilter = {},
                    onVolumeChange = {},
                    onToggleLoudnessBoost = {},
                    onAdjustSyncOffset = {},
                    onResetSyncOffset = {},
                    onClose = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("external_audio_sync_catalog_button").assertIsDisplayed()
        composeRule.onNodeWithText("مزامنة الصوتيات").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_sync_status_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_last_synced_text").assertIsDisplayed()
        composeRule.onNodeWithText("تمت المزامنة بنجاح").assertIsDisplayed()

        composeRule.onNodeWithTag("external_audio_sync_catalog_button").performClick()
        assertEquals(1, syncClicks)

        loadingState = true
        stage = ExternalAudioSyncStage.DOWNLOADING_PLAYLIST
        stageMsg = ExternalAudioSyncStage.DOWNLOADING_PLAYLIST.defaultMessageAr
        composeRule.waitForIdle()
        composeRule.onNodeWithText("جارٍ تنزيل قوائم الصوتيات...").assertIsDisplayed()
        composeRule.onNodeWithTag("external_audio_sync_catalog_button").performClick()
        assertEquals(1, syncClicks)
        composeRule.onNodeWithText("beIN SPORTS 1 HD").assertIsDisplayed()

        loadingState = false
        stage = ExternalAudioSyncStage.FAILED
        stageMsg = "فشلت المزامنة: انتهت مهلة القراءة أثناء تحميل قائمة الصوت"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("فشلت المزامنة: انتهت مهلة القراءة أثناء تحميل قائمة الصوت").assertIsDisplayed()
        composeRule.onNodeWithText("beIN SPORTS 1 HD").assertIsDisplayed()
    }

    private fun verifyAtLeastSixChannelsVisible() {
        val tenChannels = (1..10).map { idx ->
            ExternalAudioChannel(
                id = "ext-$idx",
                name = "beIN SPORTS $idx SD",
                number = idx.toString(),
                streamUrl = "http://ex/$idx.m3u8",
                brand = if (idx <= 7) AudioChannelBrand.BEIN_SPORTS else AudioChannelBrand.ALWAN,
                categoryName = if (idx <= 7) "beIN SPORTS" else "ALWAN",
                qualityLabel = "SD",
            )
        }
        composeRule.setContent {
            LiquidTVTheme {
                ExternalAudioSheet(
                    state = ExternalAudioUiState(
                        isSheetVisible = true,
                        syncStage = ExternalAudioSyncStage.SUCCEEDED,
                        syncStageMessage = "تمت المزامنة بنجاح",
                        lastSyncedAtMs = 1764070000000L,
                        activeChannel = tenChannels.first(),
                        status = ExternalAudioStatus.ACTIVE,
                        statusMessage = "نشط: beIN SPORTS 1 SD",
                        channels = tenChannels,
                    ),
                    onSelectChannel = {},
                    onDetachAudio = {},
                    onRefreshCatalog = {},
                    onSearchQueryChange = {},
                    onSelectBrandFilter = {},
                    onVolumeChange = {},
                    onToggleLoudnessBoost = {},
                    onAdjustSyncOffset = {},
                    onResetSyncOffset = {},
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        val listBounds = composeRule.onNodeWithTag("external_audio_channel_list").getUnclippedBoundsInRoot()
        for (i in 1..6) {
            composeRule.onNodeWithTag("external_audio_channel_$i").assertIsDisplayed()
            val itemBounds = composeRule.onNodeWithTag("external_audio_channel_$i").getUnclippedBoundsInRoot()
            assertTrue(
                "Channel $i bottom (${itemBounds.bottom.value}) exceeded list bottom (${listBounds.bottom.value})",
                itemBounds.bottom.value <= listBounds.bottom.value + 1f,
            )
        }
    }
}
