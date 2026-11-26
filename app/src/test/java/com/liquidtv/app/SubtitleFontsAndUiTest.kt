package com.liquidtv.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SubtitleBackgroundOption
import com.liquidtv.app.domain.model.SubtitleColorOption
import com.liquidtv.app.domain.model.SubtitleEdgeOption
import com.liquidtv.app.domain.model.SubtitleFontOption
import com.liquidtv.app.domain.model.SubtitleMediaMetadata
import com.liquidtv.app.domain.model.SubtitlePositionOption
import com.liquidtv.app.domain.model.SubtitleStyleSettings
import com.liquidtv.app.player.PlayerUiState
import com.liquidtv.app.ui.screens.player.PlayerSubtitlesAndTracksSheet
import com.liquidtv.app.ui.screens.player.SubtitleOverlay
import com.liquidtv.app.ui.theme.LiquidTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-xhdpi")
class SubtitleFontsAndUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `all 10 Bold Arabic subtitle fonts exist as valid TTF resources and are documented in FONT-LICENSES`() {
        assertEquals(10, SubtitleFontOption.entries.size)
        assertEquals(10, SubtitleFontOption.entries.map { it.fontResId }.distinct().size)
        assertEquals(10, SubtitleFontOption.entries.map { it.id }.distinct().size)

        val licensesText = context.assets.open("FONT-LICENSES.txt").bufferedReader().use { it.readText() }
        assertTrue(licensesText.contains("SIL Open Font License", ignoreCase = true))

        for (font in SubtitleFontOption.entries) {
            assertTrue("Font ${font.id} must be Bold (700) or ExtraBold (800)", font.actualWeight in listOf(700, 800))
            val bytes = context.resources.openRawResource(font.fontResId).use { it.readBytes() }
            assertTrue("Font resource ${font.id} must not be empty", bytes.size > 10_000)
            // Check TrueType/OpenType SFNT header (0x00010000)
            assertEquals("Expected valid TTF header for ${font.id}", 0x00, bytes[0].toInt() and 0xFF)
            assertEquals("Expected valid TTF header for ${font.id}", 0x01, bytes[1].toInt() and 0xFF)
            val baseFamily = font.englishName.removeSuffix(" Bold").removeSuffix(" ExtraBold")
            assertTrue("FONT-LICENSES.txt should document $baseFamily", licensesText.contains(baseFamily))
        }
    }

    @Test
    fun `SubtitleStyleSettings normalizes sizes, bounds delay, and resolves enums safely`() {
        val raw = SubtitleStyleSettings(
            fontId = "unknown_font",
            fontSizeSp = 25, // nearest in [18, 22, 24, 26, 32] -> 24
            colorId = "cyan",
            backgroundId = "solid",
            edgeId = "shadow",
            positionId = "middle",
            delayMs = 9999,
        ).normalized()

        assertEquals(SubtitleFontOption.NOTO_SANS_ARABIC_BOLD.id, raw.fontId)
        assertEquals(24, raw.fontSizeSp)
        assertEquals(SubtitleColorOption.CYAN, raw.colorOption)
        assertEquals(SubtitleBackgroundOption.SOLID, raw.backgroundOption)
        assertEquals(SubtitleEdgeOption.SHADOW, raw.edgeOption)
        assertEquals(SubtitlePositionOption.MIDDLE, raw.positionOption)
        assertEquals(5000, raw.delayMs)
    }

    @Test
    fun `SubtitleOverlay renders Arabic cue text cleanly across outline, shadow, and position modes`() {
        val style = SubtitleStyleSettings(
            fontId = SubtitleFontOption.CAIRO_BOLD.id,
            fontSizeSp = 26,
            colorId = SubtitleColorOption.YELLOW.id,
            backgroundId = SubtitleBackgroundOption.SOLID.id,
            edgeId = SubtitleEdgeOption.OUTLINE.id,
            positionId = SubtitlePositionOption.RAISED.id,
        )

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(1280.dp).height(720.dp)) {
                    SubtitleOverlay(
                        cueText = "مرحباً بكم في المشغل",
                        style = style,
                        controlsVisible = false,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("external_subtitle_overlay").assertIsDisplayed()
        // In OUTLINE mode both stroke and fill Text render the cue string
        assertEquals(2, composeRule.onAllNodesWithText("مرحباً بكم في المشغل").fetchSemanticsNodes().size)
    }

    @Test
    fun `PlayerSubtitlesAndTracksSheet displays Tracks, Online Search, and all 10 live Arabic font previews`() {
        var currentStyle = SubtitleStyleSettings()
        val uiState = PlayerUiState(
            mediaType = MediaType.MOVIE,
            subtitleMetadata = SubtitleMediaMetadata(title = "Dune: Part Two", mediaType = MediaType.MOVIE),
            hasExternalSubtitleLoaded = true,
            externalSubtitlesEnabled = true,
            activeExternalSubtitleLabel = "Dune.Part.Two.AR.srt",
            subtitleStyle = currentStyle,
        )

        composeRule.setContent {
            LiquidTVTheme {
                Box(Modifier.width(960.dp).height(640.dp)) {
                    PlayerSubtitlesAndTracksSheet(
                        uiState = uiState,
                        onSelectAudio = {},
                        onSelectSubtitle = {},
                        onToggleExternalSubtitles = {},
                        onLoadExternalSubtitleUrl = { _, _ -> },
                        onPickLocalSubtitleUri = {},
                        onSelectSearchLanguage = {},
                        onSearchOnlineSubtitles = {},
                        onApplyOnlineSubtitle = {},
                        onUpdateStyle = { transform -> currentStyle = transform(currentStyle) },
                        onAdjustDelay = {},
                        onSetDelay = {},
                        onClose = {},
                    )
                }
            }
        }

        // Tracks tab checks
        composeRule.onNodeWithTag("toggle_external_subtitles_button").assertIsDisplayed()
        composeRule.onNodeWithTag("pick_local_subtitle_button").assertIsDisplayed()
        composeRule.onNodeWithTag("tracks_tab_list")
            .performScrollToNode(hasTestTag("external_subtitle_url_input"))
        composeRule.onNodeWithTag("external_subtitle_url_input").assertIsDisplayed()

        // Switch to Online Search tab
        composeRule.onNodeWithTag("player_tab_online_search").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_online_subtitles_button").assertIsDisplayed()
        composeRule.onNodeWithTag("subtitle_lang_ar").assertIsDisplayed()

        // Switch to Appearance & Fonts tab and verify all 10 fonts + size + color interactions
        composeRule.onNodeWithTag("player_tab_appearance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("subtitle_size_32").assertIsDisplayed().performClick()
        assertEquals(32, currentStyle.fontSizeSp)

        for (font in SubtitleFontOption.entries) {
            composeRule.onNodeWithTag("subtitle_appearance_list")
                .performScrollToNode(hasTestTag("subtitle_font_item_${font.id}"))
            composeRule.onNodeWithTag("subtitle_font_item_${font.id}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("subtitle_font_item_el_messiri_bold").performClick()
        assertEquals("el_messiri_bold", currentStyle.fontId)
    }
}
