package com.liquidtv.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.audio.AudioM3uParser
import com.liquidtv.app.data.audio.AudioNetworkSecurityValidator
import com.liquidtv.app.data.audio.DefaultExternalAudioCatalogRepository
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.sonix.HttpSonixControlApi
import com.liquidtv.app.data.sonix.SonixControlRepositoryImpl
import com.liquidtv.app.data.sonix.SonixDeviceIdentityStore
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.model.SonixAudioSourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
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
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioM3uParserAndCatalogTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private val plainCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("enc:")
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "liquidtv_external_audio_catalog_v1.enc").delete()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `AudioNetworkSecurityValidator blocks SSRF targets and allows private IPTV LAN`() {
        val publicDns: (String) -> Array<InetAddress> = { host ->
            when (host) {
                "192.168.1.50" -> arrayOf(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 50)))
                "evil.local" -> arrayOf(InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte())))
                else -> arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
            }
        }

        AudioNetworkSecurityValidator.assertSafeUrl("http://192.168.1.50:8080/live/1.ts", publicDns)
        AudioNetworkSecurityValidator.assertSafeUrl("https://cdn.example.com/bein1.m3u8", publicDns)

        val blocked = listOf(
            "http://localhost:8080/live.m3u8",
            "http://127.0.0.1/live.ts",
            "http://169.254.169.254/latest/meta-data",
            "http://evil.local/stream.m3u8",
            "file:///etc/passwd",
        )
        blocked.forEach { url ->
            val failed = runCatching { AudioNetworkSecurityValidator.assertSafeUrl(url, publicDns) }.isFailure
            assertTrue("Expected blocked URL: $url", failed)
        }
    }

    @Test
    fun `AudioM3uParser parses BOM, EXTGRP, relative URLs, deduplicates, and filters non-target channels`() {
        val m3u = "\uFEFF#EXTM3U\n" +
            "#EXTINF:-1 tvg-chno=\"10\" group-title=\"beIN Sports\",beIN SPORTS 1 HD\n" +
            "http://iptv.example.com/live/bein1.m3u8\n" +
            "#EXTINF:-1 tvg-name=\"SSC 1 HD\" group-title=\"SSC\",SSC 1 HD\n" +
            "http://iptv.example.com/live/ssc1.m3u8\n" +
            "#EXTGRP:قنوات ألوان\n" +
            "#EXTINF:-1,ALWAN 2 FHD\n" +
            "/audio/alwan2.ts\n" +
            "#EXTINF:-1,beIN SPORTS 1 HD Duplicate\n" +
            "http://iptv.example.com/live/bein1.m3u8\n"

        val channels = AudioM3uParser.parse(
            input = ByteArrayInputStream(m3u.toByteArray(Charsets.UTF_8)),
            baseUrl = "https://panel.example.com/playlist.m3u",
            dnsResolver = testPublicDns(),
        )

        assertEquals(2, channels.size)
        assertEquals(AudioChannelBrand.BEIN_SPORTS, channels[0].brand)
        assertEquals("10", channels[0].number)
        assertEquals("HD", channels[0].qualityLabel)

        assertEquals(AudioChannelBrand.ALWAN, channels[1].brand)
        assertEquals("https://panel.example.com/audio/alwan2.ts", channels[1].streamUrl)
        assertEquals("FHD", channels[1].qualityLabel)
        assertEquals("ts", channels[1].containerExtension)
    }

    @Test
    fun `SonixControlRepositoryImpl parses audio_source in sync and fetches audio_sources without blocking behind unrelated syncNow`() = runTest {
        val syncJson = """
            {
              "device_id": "d-1",
              "device_uid": "uid-1",
              "device_code": "SNX123",
              "device_status": "active",
              "access_status": "active",
              "access_allowed": true,
              "device_token": "tok-1",
              "audio_source": {
                "configured": true,
                "m3u_url": "http://audio.example.org/list.m3u",
                "updated_at": "2026-11-25T11:00:00Z"
              }
            }
        """.trimIndent()
        val audioSourcesJson = """
            {
              "status": "active",
              "configured": true,
              "m3u_url": "http://audio.example.org/updated.m3u",
              "updated_at": "2026-11-25T12:00:00Z"
            }
        """.trimIndent()

        val slowSyncBody = syncJson
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return if (body.contains("\"audio.sources\"")) {
                    MockResponse().setResponseCode(200).setBody(audioSourcesJson)
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setBodyDelay(700, TimeUnit.MILLISECONDS)
                        .setBody(slowSyncBody)
                }
            }
        }

        val base = server.url("/").toString().trimEnd('/')
        val api = HttpSonixControlApi(OkHttpClient(), base, "anon-key")
        val identityStore = SonixDeviceIdentityStore(context, plainCrypto)
        val repo = SonixControlRepositoryImpl(context, api, identityStore)

        // Seed token first
        val firstSync = repo.syncNow(force = true)
        assertTrue(firstSync.isSuccess)
        assertEquals("http://audio.example.org/list.m3u", repo.state.value.audioSource.m3uUrl)

        // Start a slow unrelated syncNow occupying deviceAuthMutex; fetchAudioSources must complete immediately without waiting
        val slowSyncJob = async(Dispatchers.IO) { repo.syncNow(force = true) }
        delay(50)
        val startMs = System.currentTimeMillis()
        val fetchRes = repo.fetchAudioSources()
        val elapsedMs = System.currentTimeMillis() - startMs
        assertTrue(fetchRes.isSuccess)
        assertEquals("http://audio.example.org/updated.m3u", fetchRes.getOrNull()?.m3uUrl)
        assertTrue("fetchAudioSources waited on unrelated sync mutex ($elapsedMs ms)", elapsedMs < 400L)
        slowSyncJob.await()
    }

    @Test
    fun `DefaultExternalAudioCatalogRepository blocks localhost SSRF and falls back offline`() = runTest {
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(
                    SonixAudioSourceConfig(
                        configured = true,
                        m3uUrl = server.url("/audio.m3u").toString(),
                        updatedAt = "2026-11-25T10:00:00Z",
                    ),
                )
            }
        }
        val repo = DefaultExternalAudioCatalogRepository(
            context = context,
            okHttpClient = OkHttpClient(),
            credentialCrypto = plainCrypto,
            sonixControlRepository = fakeSonixRepo,
        )

        val blockedRes = repo.syncFromPanel(forceRefresh = true)
        assertNotNull(blockedRes.error)
        assertTrue(blockedRes.channels.isEmpty())

        // Verify encrypted cache roundtrip & offline fallback
        val cacheFile = File(context.filesDir, "liquidtv_external_audio_catalog_v1.enc")
        val seedJson = """{"version":2,"updatedAt":"2026-11-25T10:00:00Z","lastSyncedEpochMs":1764060000000,"sourceHash":"h1","channels":[{"id":"c1","name":"beIN SPORTS 1 HD","number":"1","streamUrl":"http://cdn.example.org/1.m3u8","brand":"BEIN_SPORTS","categoryName":"beIN SPORTS","containerExtension":"m3u8","qualityRank":4,"qualityLabel":"HD"}]}"""
        cacheFile.writeText(plainCrypto.encrypt(seedJson), Charsets.UTF_8)

        val cached = repo.loadCached()
        assertTrue(cached.fromCache)
        assertEquals(1, cached.channels.size)
        assertEquals(1764060000000L, cached.lastSyncedAtMs)

        fakeSonixRepo.audioSourcesHandler = { Result.failure(IllegalStateException("network down")) }
        val fallback = repo.syncFromPanel(forceRefresh = true)
        assertEquals(1, fallback.channels.size)
        assertNotNull(fallback.warning)
    }

    private fun testPublicDns(failUnknown: Boolean = false): (String) -> Array<InetAddress> = { host ->
        if (failUnknown) throw UnknownHostException("NXDOMAIN: $host")
        arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
    }

    private fun testRepo(
        hostName: String,
        sonixRepo: FakeSonixControlRepository,
        dnsResolver: (String) -> Array<InetAddress> = testPublicDns(),
        connectTimeoutMs: Int = 1_500,
        readTimeoutMs: Int = 800,
        clockMs: () -> Long = { 1764064800000L },
    ): DefaultExternalAudioCatalogRepository {
        return DefaultExternalAudioCatalogRepository(
            context = context,
            okHttpClient = OkHttpClient(),
            credentialCrypto = plainCrypto,
            sonixControlRepository = sonixRepo,
            dnsResolver = dnsResolver,
            connectionFactory = { url ->
                val targetUrl = if (url.host.equals(hostName, ignoreCase = true)) {
                    URL("http", "127.0.0.1", server.port, url.file)
                } else {
                    url
                }
                targetUrl.openConnection() as HttpURLConnection
            },
            m3uConnectTimeoutMs = connectTimeoutMs,
            m3uReadTimeoutMs = readTimeoutMs,
            clockMs = clockMs,
        )
    }

    @Test
    fun `AudioM3uParser handles payloads above 16 MiB up to 128 MiB and rejects HTML, missing header, and above 128 MiB`() {
        assertEquals(128L * 1024L * 1024L, AudioM3uParser.MAX_M3U_BYTES)

        val htmlErr = runCatching {
            AudioM3uParser.parse(
                ByteArrayInputStream("<!DOCTYPE html><html><body>502 Bad Gateway</body></html>".toByteArray()),
                "https://cdn.example.com/list.m3u",
                testPublicDns(),
            )
        }
        assertTrue(htmlErr.isFailure)

        val noHeaderErr = runCatching {
            AudioM3uParser.parse(
                ByteArrayInputStream("plain error without extm3u".toByteArray()),
                "https://cdn.example.com/list.m3u",
                testPublicDns(),
            )
        }
        assertTrue(noHeaderErr.isFailure)

        // 18 MiB valid M3U with 1 target beIN channel + filler entries -> MUST SUCCEED (> 16 MiB old cap!)
        val streamAbove16Mb = object : java.io.InputStream() {
            private val prefix = ("#EXTM3U\n#EXTINF:-1 tvg-chno=\"1\" group-title=\"beIN SPORTS\",beIN SPORTS 1 HD\nhttp://cdn.example.com/b1.m3u8\n").toByteArray()
            private val spacerLine = "#EXTINF:-1 group-title=\"VOD\",Filler Movie Entry With Metadata 0123456789ABCDEF\nhttp://cdn.example.com/vod/1.mp4\n".toByteArray()
            private val targetTotal = 18L * 1024L * 1024L
            private var sent = 0L
            private var prefixOffset = 0
            private var lineOffset = 0
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (prefixOffset < prefix.size) {
                    val c = minOf(len, prefix.size - prefixOffset)
                    System.arraycopy(prefix, prefixOffset, b, off, c)
                    prefixOffset += c
                    sent += c
                    return c
                }
                if (sent >= targetTotal) return -1
                var written = 0
                while (written < len && sent < targetTotal) {
                    val avail = spacerLine.size - lineOffset
                    val chunk = minOf(len - written, avail)
                    System.arraycopy(spacerLine, lineOffset, b, off + written, chunk)
                    lineOffset = (lineOffset + chunk) % spacerLine.size
                    written += chunk
                    sent += chunk
                }
                return written
            }
        }
        val parsed18Mb = AudioM3uParser.parse(streamAbove16Mb, "https://cdn.example.com/large.m3u", testPublicDns())
        assertEquals(1, parsed18Mb.size)
        assertEquals("beIN SPORTS 1 HD", parsed18Mb.first().name)

        // Exceeds 128 MiB limit -> MUST REJECT
        val hugeStream = object : java.io.InputStream() {
            private val header = "#EXTM3U\n".toByteArray()
            private var idx = 0L
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (idx == 0L) {
                    val c = minOf(len, header.size)
                    System.arraycopy(header, 0, b, off, c)
                    idx += c
                    return c
                }
                val chunk = minOf(len, 65536)
                java.util.Arrays.fill(b, off, off + chunk, 'a'.code.toByte())
                idx += chunk
                return chunk
            }
        }
        val largeErr = runCatching {
            AudioM3uParser.parse(hugeStream, "https://cdn.example.com/huge.m3u", testPublicDns())
        }
        assertTrue(largeErr.isFailure)
    }

    @Test
    fun `syncFromPanelWithProgress emits live stages, persists encrypted lastSyncedAtMs, and survives fresh repository restart`() = runTest {
        val host = "cdn-sync.example.test"
        val urlV1 = "http://$host:${server.port}/playlist_secret_user_pass.m3u?token=supersecret"
        val m3uV1 = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 HD
            http://stream.example.com/bein1.m3u8
            #EXTINF:-1 tvg-chno="2" group-title="ALWAN",ALWAN 1 FHD
            http://stream.example.com/alwan1.m3u8
        """.trimIndent()
        val gzipped = java.io.ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gz -> gz.write(m3uV1.toByteArray(Charsets.UTF_8)) }
            baos.toByteArray()
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/x-mpegurl")
                .setHeader("Content-Encoding", "gzip")
                .setBody(Buffer().write(gzipped)),
        )

        val currentClock = AtomicLong(1764070000000L)
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = urlV1, updatedAt = "2026-11-25T10:00:00Z"))
            }
        }
        val repo1 = testRepo(host, fakeSonixRepo, clockMs = { currentClock.get() })
        val observedStages = mutableListOf<ExternalAudioSyncStage>()

        val result = repo1.syncFromPanelWithProgress(forceRefresh = true) { stage ->
            observedStages += stage
        }

        assertFalse(result.fromCache)
        assertNull(result.error)
        assertEquals(2, result.channels.size)
        assertEquals(1764070000000L, result.lastSyncedAtMs)
        assertEquals(
            listOf(
                ExternalAudioSyncStage.CONNECTING_PANEL,
                ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
                ExternalAudioSyncStage.PARSING_CHANNELS,
                ExternalAudioSyncStage.SAVING_LOCAL,
                ExternalAudioSyncStage.SUCCEEDED,
            ),
            observedStages,
        )

        // Ensure raw M3U URL & secret token were NOT written to the encrypted envelope plaintext
        val diskRaw = plainCrypto.decrypt(File(context.filesDir, "liquidtv_external_audio_catalog_v1.enc").readText())
        assertFalse(diskRaw.contains("supersecret"))
        assertFalse(diskRaw.contains("playlist_secret_user_pass"))
        assertTrue(diskRaw.contains(host))

        // Simulate full app close & reopen with a fresh repository instance offline -> instant cached load!
        fakeSonixRepo.audioSourcesHandler = { Result.failure(IllegalStateException("offline")) }
        val repoAfterRestart = testRepo(host, fakeSonixRepo, clockMs = { currentClock.get() })
        assertNull(repoAfterRestart.peekCached())
        val loadedOffline = repoAfterRestart.preload()
        assertTrue(loadedOffline.fromCache)
        assertEquals(2, loadedOffline.channels.size)
        assertEquals(1764070000000L, loadedOffline.lastSyncedAtMs)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `HttpURLConnection handles slow chunked playlist beyond single readTimeout when each chunk arrives on time`() = runTest {
        val host = "cdn-slow-valid.example.test"
        val url = "http://$host:${server.port}/slow_playlist.m3u"
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 HD
            http://stream.example.com/bein1.m3u8
            #EXTINF:-1 tvg-chno="2" group-title="ALWAN",ALWAN 1 HD
            http://stream.example.com/alwan1.m3u8
        """.trimIndent()
        // Total transfer ~600ms > readTimeoutMs(350ms), each small chunk arrives well under 350ms -> MUST SUCCEED!
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(m3u)
                .throttleBody(40, 100, TimeUnit.MILLISECONDS),
        )
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = url, updatedAt = "v1"))
            }
        }
        val repo = testRepo(host, fakeSonixRepo, readTimeoutMs = 350)
        val res = repo.syncFromPanel(forceRefresh = true)
        assertNull("Expected slow active stream to succeed without artificial callTimeout", res.error)
        assertEquals(2, res.channels.size)
    }

    @Test
    fun `isolated error handling covers DNS, read timeout, HTTP status, redirects, truncated gzip, and preserves old cache`() = runTest {
        val host = "cdn-errors.example.test"
        val urlV1 = "http://$host:${server.port}/v1.m3u?password=p1"
        val urlV2 = "http://$host:${server.port}/v2.m3u?password=p2"
        val validM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="beIN SPORTS",beIN SPORTS 1 HD
            http://stream.example.com/bein1.m3u8
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(validM3u))
        var activeUrl = urlV1
        var activeUpdated = "t1"
        val failDns = AtomicBoolean(false)
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = activeUrl, updatedAt = activeUpdated))
            }
        }
        val repo = testRepo(
            hostName = host,
            sonixRepo = fakeSonixRepo,
            dnsResolver = { h ->
                if (failDns.get()) throw UnknownHostException("NXDOMAIN: $h")
                arrayOf(InetAddress.getByAddress(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)))
            },
            readTimeoutMs = 250,
            clockMs = { 1764071111000L },
        )
        assertEquals(1, repo.syncFromPanel(forceRefresh = true).channels.size)

        // 1. Read Timeout -> stalled socket with NO response
        activeUrl = urlV2
        activeUpdated = "t2"
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val stagesTimeout = mutableListOf<ExternalAudioSyncStage>()
        val timeoutRes = repo.syncFromPanelWithProgress(forceRefresh = true) { stagesTimeout += it }
        assertEquals(1, timeoutRes.channels.size)
        assertEquals(1764071111000L, timeoutRes.lastSyncedAtMs)
        assertEquals(ExternalAudioSyncStage.FAILED, stagesTimeout.last())
        assertTrue(timeoutRes.warning.orEmpty().contains("انتهت مهلة"))

        // 2. HTTP 503 error
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val httpRes = repo.syncFromPanel(forceRefresh = true)
        assertEquals(1, httpRes.channels.size)
        assertTrue(httpRes.warning.orEmpty().contains("503"))

        // 3. Redirect without Location header
        server.enqueue(MockResponse().setResponseCode(302))
        val redirectRes = repo.syncFromPanel(forceRefresh = true)
        assertEquals(1, redirectRes.channels.size)
        assertTrue(redirectRes.warning.orEmpty().contains("تحويل"))

        // 4. Truncated GZIP stream
        val fullGz = java.io.ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gz -> gz.write(validM3u.toByteArray()) }
            baos.toByteArray()
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Encoding", "gzip")
                .setBody(Buffer().write(fullGz.copyOf(fullGz.size / 2))),
        )
        val truncRes = repo.syncFromPanel(forceRefresh = true)
        assertEquals(1, truncRes.channels.size)
        assertNotNull(truncRes.warning)
        assertFalse(truncRes.warning.orEmpty().contains("p2"))

        // 5. DNS failure when connecting
        failDns.set(true)
        server.enqueue(MockResponse().setResponseCode(200).setBody(validM3u))
        // Force a fresh repo with no cache to verify DNS user error message directly
        File(context.filesDir, "liquidtv_external_audio_catalog_v1.enc").delete()
        val uncachedRepo = DefaultExternalAudioCatalogRepository(
            context = context,
            okHttpClient = OkHttpClient(),
            credentialCrypto = plainCrypto,
            sonixControlRepository = fakeSonixRepo,
            dnsResolver = { h -> throw UnknownHostException("NXDOMAIN: $h") },
            connectionFactory = { _ -> throw UnknownHostException("NXDOMAIN") },
            m3uConnectTimeoutMs = 500,
            m3uReadTimeoutMs = 500,
            clockMs = { 1764071111000L },
        )
        val dnsErrRes = uncachedRepo.syncFromPanel(forceRefresh = true)
        assertTrue(dnsErrRes.channels.isEmpty())
        assertTrue(dnsErrRes.error.orEmpty().contains("DNS"))
    }

    @Test
    fun `concurrent syncFromPanel calls are deduplicated into a single network sync`() = runTest {
        val host = "cdn-dedupe.example.test"
        val url = "http://$host:${server.port}/audio.m3u"
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="1" group-title="ALWAN",ALWAN 1 HD
            http://stream.example.com/alwan1.m3u8
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(150, TimeUnit.MILLISECONDS).setBody(m3u))

        val panelCalls = AtomicInteger(0)
        val fakeSonixRepo = FakeSonixControlRepository().apply {
            audioSourcesHandler = {
                panelCalls.incrementAndGet()
                Result.success(SonixAudioSourceConfig(configured = true, m3uUrl = url, updatedAt = "2026-11-25T12:00:00Z"))
            }
        }
        val repo = testRepo(host, fakeSonixRepo)

        val results = (1..5).map {
            async { repo.syncFromPanel(forceRefresh = false) }
        }.awaitAll()

        assertTrue(results.all { it.channels.size == 1 })
        assertEquals(1, panelCalls.get())
        assertEquals(1, server.requestCount)
    }
}
