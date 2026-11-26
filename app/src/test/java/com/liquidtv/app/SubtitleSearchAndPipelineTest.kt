package com.liquidtv.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.remote.AddonEndpoints
import com.liquidtv.app.data.remote.StremioAddonClient
import com.liquidtv.app.data.subtitles.SubtitlePipeline
import com.liquidtv.app.domain.model.AddonSource
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.domain.model.SubtitleMediaMetadata
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleSearchAndPipelineTest {

    private lateinit var context: Context
    private val server = MockWebServer()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `Subtitle search refuses Live TV, resolves Cinemeta series S05E14 to imdbId_season_episode, and deduplicates OpenSubtitles`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/catalog/series/top/search=") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "metas": [
                            { "id": "tt9999999", "name": "Breaking News" },
                            { "id": "tt0903747", "name": "Breaking Bad" }
                          ]
                        }
                        """.trimIndent()
                    )
                    path == "/v3/subtitles/series/tt0903747%3A5%3A14.json" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "subtitles": [
                            { "url": "$baseUrl/dl/ar1.srt", "lang": "ara", "label": "Breaking.Bad.S05E14.AR" },
                            { "url": "$baseUrl/dl/en1.srt", "lang": "eng", "label": "Breaking.Bad.S05E14.EN" }
                          ]
                        }
                        """.trimIndent()
                    )
                    path == "/pro/subtitles/series/tt0903747%3A5%3A14.json" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "subtitles": [
                            { "url": "$baseUrl/dl/ar1.srt", "lang": "ar", "label": "Duplicate URL ignored" },
                            { "url": "$baseUrl/dl/ar2.vtt", "lang": "Arabic", "label": "Ozymandias Arabic Pro" }
                          ]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = StremioAddonClient(OkHttpClient(), context).apply {
            endpoints = AddonEndpoints(
                officialCatalogUrl = "$baseUrl/official.json",
                communityCatalogPrimaryUrl = "$baseUrl/community.json",
                communityCatalogFallbackUrl = "$baseUrl/community.json",
                cinemetaBaseUrl = baseUrl,
            )
        }

        val addons = listOf(
            StremioAddon(
                id = "org.stremio.opensubtitlesv3",
                name = "OpenSubtitles V3",
                url = "$baseUrl/v3/manifest.json",
                resources = listOf("subtitles"),
                enabled = true,
            ),
            StremioAddon(
                id = "opensubtitles-pro",
                name = "OpenSubtitles PRO",
                url = "$baseUrl/pro/manifest.json",
                resources = listOf("subtitles"),
                enabled = true,
            ),
            StremioAddon(
                id = "com.disabled.sub",
                name = "Disabled Sub",
                url = "$baseUrl/disabled/manifest.json",
                resources = listOf("subtitles"),
                enabled = false,
            ),
        )

        // Live TV must fail cleanly
        var liveRejected = false
        try {
            client.searchSubtitles(
                SubtitleMediaMetadata("Al Jazeera", MediaType.LIVE),
                preferredLanguage = "ar",
                installedAddons = addons,
            )
        } catch (_: IllegalArgumentException) {
            liveRejected = true
        }
        assertTrue(liveRejected)

        // Series Episode resolves IMDb + formats tt0903747:5:14 + deduplicates + filters Arabic
        val arOutcome = client.searchSubtitles(
            SubtitleMediaMetadata(
                title = "Ozymandias",
                mediaType = MediaType.SERIES_EPISODE,
                seriesName = "Breaking Bad [1080p]",
                seasonNumber = 5,
                episodeNumber = 14,
            ),
            preferredLanguage = "ar",
            installedAddons = addons,
        )
        assertEquals("tt0903747", arOutcome.identity.imdbId)
        assertEquals("tt0903747:5:14", arOutcome.identity.resourceId)
        assertEquals(2, arOutcome.results.size)
        assertTrue(arOutcome.results.all { it.language == "ar" })

        // "all" returns deduplicated ar1 + en1 + ar2 = 3 results
        val allOutcome = client.searchSubtitles(
            SubtitleMediaMetadata(
                title = "Ozymandias",
                mediaType = MediaType.SERIES_EPISODE,
                seriesName = "Breaking Bad",
                seasonNumber = 5,
                episodeNumber = 14,
            ),
            preferredLanguage = "all",
            installedAddons = addons,
        )
        assertEquals(3, allOutcome.results.size)
    }

    @Test
    fun `IMDb resolution falls back to enabled addon search catalog when Cinemeta fails`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/cinemeta/") -> MockResponse().setResponseCode(500)
                    path.startsWith("/altcat/catalog/movie/alt_search/search=") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "metas": [
                            { "imdb_id": "tt0133093", "name": "The Matrix" }
                          ]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = StremioAddonClient(OkHttpClient(), context).apply {
            endpoints = AddonEndpoints(
                officialCatalogUrl = "$baseUrl/official.json",
                communityCatalogPrimaryUrl = "$baseUrl/community.json",
                communityCatalogFallbackUrl = "$baseUrl/community.json",
                cinemetaBaseUrl = "$baseUrl/cinemeta",
            )
        }
        val fallbackCatalogAddon = StremioAddon(
            id = "com.alt.catalog",
            name = "Alt Catalog",
            url = "$baseUrl/altcat/manifest.json",
            resources = listOf("catalog"),
            hasSearchCatalog = true,
            searchCatalogIdsByType = mapOf("movie" to listOf("alt_search")),
            enabled = true,
            source = AddonSource.COMMUNITY,
        )

        val identity = client.resolveImdb(
            SubtitleMediaMetadata(title = "The Matrix (1999) [4K]", mediaType = MediaType.MOVIE),
            installedAddons = listOf(fallbackCatalogAddon),
        )
        assertEquals("movie", identity.type)
        assertEquals("tt0133093", identity.imdbId)
        assertEquals("tt0133093", identity.resourceId)
    }

    @Test
    fun `SubtitlePipeline parses SRT, VTT, ASS, SSA, TTML, and TXT formats accurately`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            مرحباً <i>بكم</i> &amp; أهلاً
        """.trimIndent()
        val srtCues = SubtitlePipeline.parse(srt, "srt")
        assertEquals(1, srtCues.size)
        assertEquals(1000L, srtCues[0].startMs)
        assertEquals(4000L, srtCues[0].endMs)
        assertEquals("مرحباً بكم & أهلاً", srtCues[0].text)

        val vtt = """
            WEBVTT

            00:10.500 --> 00:15.000
            السطر الأول
            السطر الثاني
        """.trimIndent()
        val vttCues = SubtitlePipeline.parse(vtt, "vtt")
        assertEquals(1, vttCues.size)
        assertEquals(10_500L, vttCues[0].startMs)
        assertEquals("السطر الأول\nالسطر الثاني", vttCues[0].text)

        val ass = """
            [Script Info]
            Title: Demo
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:02.00,0:00:05.50,Default,,0,0,0,,{\an8}ترجمة عربية\Nممتازة
        """.trimIndent()
        val assCues = SubtitlePipeline.parse(ass, "ass")
        assertEquals(1, assCues.size)
        assertEquals(2000L, assCues[0].startMs)
        assertEquals("ترجمة عربية\nممتازة", assCues[0].text)

        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:03.000" end="00:00:07.000">ترجمة<br/>TTML</p>
              </div></body>
            </tt>
        """.trimIndent()
        val ttmlCues = SubtitlePipeline.parse(ttml, "ttml")
        assertEquals(1, ttmlCues.size)
        assertEquals("ترجمة\nTTML", ttmlCues[0].text)
    }

    @Test
    fun `SubtitlePipeline decompresses ZIP, GZIP, Deflate and decodes UTF-16LE, Windows-1256, and ISO-8859-6`() {
        val arabicSample = "1\n00:00:01,000 --> 00:00:05,000\nترجمة عربية دقيقة\n"

        // GZIP
        val gzipOut = ByteArrayOutputStream().also { baos ->
            GZIPOutputStream(baos).use { it.write(arabicSample.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        assertEquals("ترجمة عربية دقيقة", SubtitlePipeline.decodeAndParse(gzipOut, "gzip", "ar.srt.gz").cues[0].text)

        // Deflate
        val deflateOut = ByteArrayOutputStream().also { baos ->
            DeflaterOutputStream(baos).use { it.write(arabicSample.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        assertEquals("ترجمة عربية دقيقة", SubtitlePipeline.decodeAndParse(deflateOut, "deflate", "ar.srt").cues[0].text)

        // ZIP preferring .srt over .txt
        val zipOut = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("ignore".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("movie.ar.srt"))
                zip.write(arabicSample.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }.toByteArray()
        val zipParsed = SubtitlePipeline.decodeAndParse(zipOut, null, "archive.zip")
        assertEquals("srt", zipParsed.decoded.extension)
        assertEquals("ترجمة عربية دقيقة", zipParsed.cues[0].text)

        // Windows-1256 & ISO-8859-6
        val cp1256Bytes = arabicSample.toByteArray(Charset.forName("windows-1256"))
        assertEquals("ترجمة عربية دقيقة", SubtitlePipeline.decodeAndParse(cp1256Bytes, null, "cp1256.srt").cues[0].text)

        val isoBytes = arabicSample.toByteArray(Charset.forName("ISO-8859-6"))
        assertEquals("ترجمة عربية دقيقة", SubtitlePipeline.decodeAndParse(isoBytes, null, "iso.srt").cues[0].text)

        // UTF-16LE with BOM
        val utf16LeWithBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + arabicSample.toByteArray(Charsets.UTF_16LE)
        assertEquals("ترجمة عربية دقيقة", SubtitlePipeline.decodeAndParse(utf16LeWithBom, null, "u16.srt").cues[0].text)
    }

    @Test
    fun `SubtitlePipeline rejects HTML, JSON, empty payloads, Zip Slip traversal, and computes delay`() {
        val html = "<!DOCTYPE html><html><body>403 Forbidden</body></html>".toByteArray()
        var rejectedHtml = false
        try {
            SubtitlePipeline.decodeAndParse(html, null, "error.srt")
        } catch (_: IllegalArgumentException) {
            rejectedHtml = true
        }
        assertTrue(rejectedHtml)

        val jsonErr = """{"error":"rate limit"}""".toByteArray()
        var rejectedJson = false
        try {
            SubtitlePipeline.decodeAndParse(jsonErr, null, "error.srt")
        } catch (_: IllegalArgumentException) {
            rejectedJson = true
        }
        assertTrue(rejectedJson)

        val zipSlipBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("../../etc/evil.srt"))
                zip.write("1\n00:00:01,000 --> 00:00:03,000\nbad".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        var rejectedSlip = false
        try {
            SubtitlePipeline.decodeAndParse(zipSlipBytes, null, "bad.zip")
        } catch (_: IllegalArgumentException) {
            rejectedSlip = true
        }
        assertTrue(rejectedSlip)

        val cues = SubtitlePipeline.parse("1\n00:00:05,000 --> 00:00:08,000\nسطر التوقيت", "srt")
        // With delay = +2000ms, cue appears from 7000ms to 10000ms
        assertEquals(null, SubtitlePipeline.findActiveCueText(cues, positionMs = 5_500L, delayMs = 2000))
        assertEquals("سطر التوقيت", SubtitlePipeline.findActiveCueText(cues, positionMs = 8_500L, delayMs = 2000))
    }
}
