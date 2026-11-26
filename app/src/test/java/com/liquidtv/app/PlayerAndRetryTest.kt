package com.liquidtv.app

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.liquidtv.app.player.PlayerMediaHelper
import com.liquidtv.app.player.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.net.Uri
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerAndRetryTest {

    @Test
    fun `PlayerViewModel does not hold or instantiate ExoPlayer directly (single engine in service)`() {
        val fields = PlayerViewModel::class.java.declaredFields
        val hasDirectExoPlayer = fields.any { ExoPlayer::class.java.isAssignableFrom(it.type) }
        assertFalse(
            "PlayerViewModel must connect via MediaController rather than owning an ExoPlayer instance",
            hasDirectExoPlayer,
        )
    }

    @Test
    fun `detectMimeType identifies HLS DASH TS MP4 and leaves extensionless Xtream URLs auto-detectable`() {
        assertEquals(MimeTypes.APPLICATION_M3U8, PlayerMediaHelper.detectMimeType("http://tv.local/live/u/p/10.m3u8"))
        assertEquals(MimeTypes.APPLICATION_MPD, PlayerMediaHelper.detectMimeType("http://tv.local/dash/manifest.mpd"))
        assertEquals(MimeTypes.VIDEO_MP2T, PlayerMediaHelper.detectMimeType("http://tv.local/live/u/p/10.ts"))
        assertEquals(MimeTypes.VIDEO_MP4, PlayerMediaHelper.detectMimeType("http://tv.local/movie/u/p/22.mp4"))
        assertEquals(MimeTypes.VIDEO_MATROSKA, PlayerMediaHelper.detectMimeType("http://tv.local/movie/u/p/22", "mkv"))
        // Crucial: extensionless URLs must return null so Media3 sniffs container rather than forcing HLS
        assertNull(PlayerMediaHelper.detectMimeType("http://tv.local:8080/live/user/pass/999"))
    }

    @Test
    fun `buildMediaItem preserves custom HTTP headers and external subtitles`() {
        val item = PlayerMediaHelper.buildMediaItem(
            mediaId = "ch-99",
            title = "Sport 1",
            streamUrl = "http://stream.local:8080/live/99.ts",
            headers = mapOf("User-Agent" to "LiquidTV/1.1", "Referer" to "http://portal.local/"),
            externalSubtitleUrl = "http://subs.local/ar.vtt",
            externalSubtitleLang = "ar",
        )

        val headers = PlayerMediaHelper.extractHeaders(item)
        assertEquals("LiquidTV/1.1", headers["User-Agent"])
        assertEquals("http://portal.local/", headers["Referer"])
        assertEquals(1, item.localConfiguration?.subtitleConfigurations?.size)
        assertEquals(MimeTypes.TEXT_VTT, item.localConfiguration?.subtitleConfigurations?.first()?.mimeType)
    }

    @Test
    fun `isPermanentError distinguishes HTTP 401 403 404 from transient network errors and computes bounded backoff`() {
        val dataSpec = DataSpec(Uri.parse("http://stream.local/1.ts"))
        val http403 = HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), dataSpec, ByteArray(0))
        val permanentEx = PlaybackException("Forbidden", http403, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertTrue(PlayerMediaHelper.isPermanentError(permanentEx))

        val transientEx = PlaybackException("Timeout", IOException("timeout"), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        assertFalse(PlayerMediaHelper.isPermanentError(transientEx))

        assertEquals(1500L, PlayerMediaHelper.backoffDelayMs(1))
        assertEquals(3000L, PlayerMediaHelper.backoffDelayMs(2))
        assertEquals(6000L, PlayerMediaHelper.backoffDelayMs(3))
        assertEquals(10_000L, PlayerMediaHelper.backoffDelayMs(4))
        assertEquals(10_000L, PlayerMediaHelper.backoffDelayMs(10))
    }
}
