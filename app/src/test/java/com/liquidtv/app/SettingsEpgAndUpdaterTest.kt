package com.liquidtv.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.security.LogSanitizer
import com.liquidtv.app.data.security.PinHasher
import com.liquidtv.app.updater.AppUpdateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsEpgAndUpdaterTest {

    @Test
    fun `UserPreferencesDataStore persists playback network ambient and parental settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = UserPreferencesDataStore(context)

        store.setDefaultQuality("1080p")
        store.setSubtitlesEnabled(true)
        store.setPreferredSubtitleLanguage("ar")
        store.setStreamTimeout(25)
        store.setRetryAttempts(5)
        store.setAutoPlayNext(false)
        store.setAutoRefreshEnabled(false)
        store.setAutoRefreshInterval(12)
        store.setScreenSaverTimeout(10)
        store.setAmbientEnabled(false)
        store.setAmbientClockVisible(false)
        store.setParentalPin("4821")
        store.setParentalControlEnabled(true)

        val prefs = store.userPreferences.first()
        assertEquals("1080p", prefs.defaultQuality)
        assertTrue(prefs.subtitlesEnabled)
        assertEquals("ar", prefs.preferredSubtitleLanguage)
        assertEquals(25, prefs.connectionTimeout)
        assertEquals(5, prefs.maxRetries)
        assertFalse(prefs.autoPlay)
        assertFalse(prefs.autoRefreshEnabled)
        assertEquals(12, prefs.autoRefreshInterval)
        assertEquals(10, prefs.screenSaverTimeout)
        assertFalse(prefs.ambientEnabled)
        assertFalse(prefs.clockVisible)
        assertTrue(prefs.hasParentalPin)
        assertTrue(prefs.parentalControlEnabled)
        assertTrue(store.verifyParentalPin("4821"))
        assertFalse(store.verifyParentalPin("0000"))

        store.clearParentalPin()
        assertFalse(store.userPreferences.first().parentalControlEnabled)
    }

    @Test
    fun `PinHasher and LogSanitizer protect credentials and secrets`() {
        val hash1 = PinHasher.hashPin("1234")
        val hash2 = PinHasher.hashPin("1234")
        assertTrue(hash1.startsWith("v1:"))
        assertFalse("Salted hashes should differ", hash1 == hash2)
        assertTrue(PinHasher.verifyPin("1234", hash1))
        assertFalse(PinHasher.verifyPin("9999", hash1))

        val sanitized = LogSanitizer.sanitizeUrl("http://iptv.host:8080/player_api.php?username=alice&password=supersecret&action=get_live")
        assertFalse(sanitized.contains("supersecret"))
        assertTrue(sanitized.contains("password=***"))
    }

    @Test
    fun `XmltvParser extracts current and upcoming programs with timezone offsets`() = runTest {
        val nowMs = 1_760_000_000_000L // fixed reference window
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20251009080000 +0000" stop="20251009093000 +0000" channel="bbc.uk">
                <title>Morning News</title>
                <desc>Global headlines</desc>
                <category>News</category>
              </programme>
              <programme start="20251009093000 +0000" stop="20251009110000 +0000" channel="bbc.uk">
                <title>Documentary Hour</title>
                <desc>Deep ocean exploration</desc>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmltvParser().parse(
            inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            subscriptionId = "sub-epg",
            channelTvgIdsFilter = setOf("bbc.uk"),
            windowStartMs = nowMs - 86_400_000L * 365L,
            windowEndMs = nowMs + 86_400_000L * 365L,
        )

        assertEquals(2, programs.size)
        assertEquals("Morning News", programs[0].title)
        assertEquals("Global headlines", programs[0].description)
        assertEquals("News", programs[0].category)
        assertEquals("Documentary Hour", programs[1].title)
        assertTrue(programs[1].startTimeMs == programs[0].endTimeMs)
    }

    @Test
    fun `AppUpdateManager compares semver pre-releases and enforces HTTPS GitHub download hosts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppUpdateManager(
            context = context,
            okHttpClient = OkHttpClient(),
            userPreferencesRepository = UserPreferencesDataStore(context),
        )

        // Semver comparisons including pre-releases
        assertTrue(manager.isNewer("1.1.0", "1.0.2"))
        assertTrue(manager.isNewer("v1.1.0", "1.1.0-rc.1"))
        assertTrue(manager.isNewer("1.1.0-rc.2", "1.1.0-beta.1"))
        assertFalse(manager.isNewer("1.1.0-beta.1", "1.1.0"))
        assertFalse(manager.isNewer("1.1.0", "1.1.0"))

        // Trusted GitHub HTTPS hosts only
        assertTrue(manager.isTrustedGithubDownloadUrl("https://github.com/kyanik010/LiquidTV/releases/download/v1.1.0/app-debug.apk"))
        assertTrue(manager.isTrustedGithubDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/123/app.apk"))
        assertFalse(manager.isTrustedGithubDownloadUrl("http://github.com/kyanik010/LiquidTV/releases/download/v1.1.0/app-debug.apk"))
        assertFalse(manager.isTrustedGithubDownloadUrl("https://evil.example.com/app.apk"))
    }
}
