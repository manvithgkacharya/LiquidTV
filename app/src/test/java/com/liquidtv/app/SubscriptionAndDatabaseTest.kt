package com.liquidtv.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.epg.XmltvParser
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.entity.CategoryEntity
import com.liquidtv.app.data.local.entity.ChannelEntity
import com.liquidtv.app.data.local.entity.SubscriptionEntity
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.domain.model.SubscriptionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionAndDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: LiquidTVDatabase
    private lateinit var server: MockWebServer
    private lateinit var fakeSonix: FakeSonixControlRepository
    private lateinit var repo: SubscriptionRepositoryImpl

    private val passThroughCrypto = object : CredentialCrypto {
        override fun encrypt(plaintext: String): String = plaintext
        override fun decrypt(ciphertext: String): String = ciphertext
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LiquidTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        fakeSonix = FakeSonixControlRepository()

        val okHttp = OkHttpClient()
        repo = SubscriptionRepositoryImpl(
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
            crypto = passThroughCrypto,
            prefs = UserPreferencesDataStore(context),
            sonixControlRepository = fakeSonix,
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun `multi-subscription strict data isolation and switching restores each account data`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val isUserB = path.contains("username=userB")
                return when {
                    path.contains("action=get_live_categories") -> MockResponse().setBody(
                        if (isUserB) "[]" else """[{"category_id":"1","category_name":"Sports"}]""",
                    )
                    path.contains("action=get_live_streams") -> MockResponse().setBody(
                        if (isUserB) "[]" else """[{"num":1,"name":"SubA Sports","stream_id":10,"category_id":"1"}]""",
                    )
                    path.contains("action=get_vod_categories") -> MockResponse().setBody(
                        if (isUserB) """[{"category_id":"2","category_name":"Movies"}]""" else "[]",
                    )
                    path.contains("action=get_vod_streams") -> MockResponse().setBody(
                        if (isUserB) """[{"num":1,"name":"SubB Cinema","stream_id":20,"category_id":"2","container_extension":"mp4"}]""" else "[]",
                    )
                    path.contains("action=get_series_categories") || path.contains("action=get_series") ->
                        MockResponse().setBody("[]")
                    path.contains("player_api.php") && !path.contains("action=") -> MockResponse().setBody(
                        """{"user_info":{"username":"demo","status":"Active","auth":1},"server_info":{"url":"localhost","port":"${server.port}","server_protocol":"http"}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val baseUrl = "http://${server.hostName}:${server.port}"
        val subA = repo.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "Provider A",
            serverUrl = baseUrl,
            username = "userA",
            password = "pA",
            origin = SubscriptionOrigin.NORMAL_URL,
            activate = true,
        ).getOrThrow()

        val subB = repo.addOrUpdateXtreamSubscription(
            existingId = null,
            displayName = "Provider B",
            serverUrl = baseUrl,
            username = "userB",
            password = "pB",
            origin = SubscriptionOrigin.NORMAL_URL,
            activate = true,
        ).getOrThrow()

        val activeNow = repo.getActiveSubscriptionOnce()!!
        assertEquals(subB.id, activeNow.id)
        val channelsB = db.channelDao().getMovies(activeNow.id).first()
        assertEquals(listOf("SubB Cinema"), channelsB.map { it.name })
        assertTrue(db.channelDao().getLiveChannels(activeNow.id).first().isEmpty())

        repo.activateSubscription(subA.id)
        val switched = repo.getActiveSubscriptionOnce()!!
        assertEquals(subA.id, switched.id)
        val channelsA = db.channelDao().getLiveChannels(switched.id).first()
        assertEquals(listOf("SubA Sports"), channelsA.map { it.name })

        repo.deleteSubscription(subA.id)
        assertTrue(db.channelDao().getAllChannelsOnce(subA.id).isEmpty())
        assertEquals(1, db.channelDao().getAllChannelsOnce(subB.id).size)
    }

    @Test
    fun `atomic sync preserves favorites and resume state, and failed sync preserves existing channels`() = runTest {
        var playlistVersion = 1
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (playlistVersion == 3) return MockResponse().setResponseCode(500)
                return when {
                    path.contains("action=get_live_categories") -> MockResponse().setBody("""[{"category_id":"1","category_name":"Live"}]""")
                    path.contains("action=get_live_streams") -> MockResponse().setBody(
                        if (playlistVersion == 1) {
                            """[{"num":1,"name":"Kept Channel","stream_id":101,"category_id":"1"}]"""
                        } else {
                            """[{"num":1,"name":"Kept Channel","stream_id":101,"category_id":"1"},{"num":2,"name":"Brand New Channel","stream_id":102,"category_id":"1"}]"""
                        },
                    )
                    path.contains("action=get_vod_categories") -> MockResponse().setBody("""[{"category_id":"2","category_name":"VOD"}]""")
                    path.contains("action=get_vod_streams") -> MockResponse().setBody(
                        """[{"num":1,"name":"Movie One","stream_id":201,"category_id":"2","container_extension":"mp4"}]""",
                    )
                    path.contains("action=get_series_categories") || path.contains("action=get_series") ->
                        MockResponse().setBody("[]")
                    path.contains("player_api.php") && !path.contains("action=") -> MockResponse().setBody(
                        """{"user_info":{"username":"demo","status":"Active","auth":1},"server_info":{"url":"localhost","port":"${server.port}","server_protocol":"http"}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val url = "http://${server.hostName}:${server.port}"
        val sub = repo.addOrUpdateXtreamSubscription(null, "SyncTest", url, "demo", "pass", activate = true).getOrThrow()
        val initialChannels = db.channelDao().getAllChannelsOnce(sub.id)
        val kept = initialChannels.first { it.name == "Kept Channel" }
        val movie = initialChannels.first { it.name == "Movie One" }
        db.channelDao().toggleFavorite(kept.id)
        db.channelDao().updateResumePosition(movie.id, 123_456L)

        playlistVersion = 2
        assertTrue(repo.syncSubscription(sub.id).isSuccess)
        val updated = db.channelDao().getAllChannelsOnce(sub.id)
        assertEquals(3, updated.size)
        assertTrue(updated.first { it.id == kept.id }.isFavorite)
        assertEquals(123_456L, updated.first { it.id == movie.id }.resumePositionMs)

        playlistVersion = 3
        val syncFail = repo.syncSubscription(sub.id)
        assertTrue(syncFail.isFailure)
        val afterFail = db.channelDao().getAllChannelsOnce(sub.id)
        assertEquals(3, afterFail.size)
        assertTrue(afterFail.first { it.id == kept.id }.isFavorite)
        val updatedSub = db.subscriptionDao().getSubscriptionById(sub.id)!!
        assertEquals(SubscriptionStatus.ERROR.name, updatedSub.connectionStatus)
    }

    @Test
    fun `MIGRATION_1_2 and MIGRATION_2_3 upgrade legacy databases safely while preserving Xtream data and dropping M3U`() = runTest {
        val dbFile = File(context.cacheDir, "migration_v2_v3_test_${System.currentTimeMillis()}.db")
        val v2Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `subscriptions` (
                                    `id` TEXT NOT NULL,
                                    `name` TEXT NOT NULL,
                                    `type` TEXT NOT NULL,
                                    `serverUrl` TEXT NOT NULL,
                                    `username` TEXT,
                                    `encryptedPassword` TEXT,
                                    `encryptedM3uUrl` TEXT,
                                    `userAgent` TEXT,
                                    `referer` TEXT,
                                    `xmltvUrl` TEXT,
                                    `isActive` INTEGER NOT NULL,
                                    `connectionStatus` TEXT NOT NULL,
                                    `lastError` TEXT,
                                    `accountStatus` TEXT,
                                    `expDateEpochSec` INTEGER,
                                    `maxConnections` INTEGER,
                                    `activeConnections` INTEGER,
                                    `isTrial` INTEGER,
                                    `lastSyncedAt` INTEGER,
                                    `createdAt` INTEGER NOT NULL,
                                    PRIMARY KEY(`id`)
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `channels` (
                                    `id` TEXT NOT NULL, `subscriptionId` TEXT NOT NULL, `externalId` TEXT, `mediaType` TEXT NOT NULL,
                                    `name` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, `logoUrl` TEXT, `groupTitle` TEXT,
                                    `categoryId` TEXT, `tvgId` TEXT, `tvgName` TEXT, `country` TEXT, `language` TEXT,
                                    `channelNumber` INTEGER, `httpHeadersJson` TEXT, `containerExtension` TEXT,
                                    `plot` TEXT, `rating` TEXT, `releaseYear` TEXT, `durationSec` INTEGER,
                                    `resumePositionMs` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL,
                                    `lastWatchedAt` INTEGER, `addedAt` INTEGER NOT NULL,
                                    PRIMARY KEY(`id`),
                                    FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `series` (
                                    `id` TEXT NOT NULL, `subscriptionId` TEXT NOT NULL, `externalSeriesId` TEXT NOT NULL,
                                    `name` TEXT NOT NULL, `coverUrl` TEXT, `categoryId` TEXT, `categoryName` TEXT,
                                    `plot` TEXT, `cast` TEXT, `director` TEXT, `genre` TEXT, `releaseDate` TEXT,
                                    `rating` TEXT, `lastModified` INTEGER, `isFavorite` INTEGER NOT NULL,
                                    `lastWatchedAt` INTEGER, `addedAt` INTEGER NOT NULL,
                                    PRIMARY KEY(`id`),
                                    FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `episodes` (
                                    `id` TEXT NOT NULL, `subscriptionId` TEXT NOT NULL, `seriesId` TEXT NOT NULL,
                                    `externalEpisodeId` TEXT NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL,
                                    `title` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, `containerExtension` TEXT,
                                    `plot` TEXT, `durationSec` INTEGER, `coverUrl` TEXT, `lastWatchedAt` INTEGER,
                                    `resumePositionMs` INTEGER NOT NULL,
                                    PRIMARY KEY(`id`),
                                    FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                                    FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `categories` (
                                    `id` TEXT NOT NULL, `subscriptionId` TEXT NOT NULL, `externalCategoryId` TEXT,
                                    `contentType` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL,
                                    `isLocked` INTEGER NOT NULL,
                                    PRIMARY KEY(`id`),
                                    FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `watch_history` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `subscriptionId` TEXT NOT NULL DEFAULT '',
                                    `channelId` TEXT NOT NULL,
                                    `channelName` TEXT NOT NULL,
                                    `mediaType` TEXT NOT NULL DEFAULT 'LIVE',
                                    `positionMs` INTEGER NOT NULL DEFAULT 0,
                                    `durationMs` INTEGER NOT NULL DEFAULT 0,
                                    `watchedAt` INTEGER NOT NULL
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `epg_programs` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `subscriptionId` TEXT NOT NULL,
                                    `channelTvgId` TEXT NOT NULL,
                                    `title` TEXT NOT NULL,
                                    `description` TEXT,
                                    `startTimeMs` INTEGER NOT NULL,
                                    `endTimeMs` INTEGER NOT NULL,
                                    `category` TEXT,
                                    FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                                )
                                """.trimIndent(),
                            )

                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId` ON `channels` (`subscriptionId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_mediaType` ON `channels` (`subscriptionId`, `mediaType`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_groupTitle` ON `channels` (`subscriptionId`, `groupTitle`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_isFavorite` ON `channels` (`subscriptionId`, `isFavorite`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_tvgId` ON `channels` (`subscriptionId`, `tvgId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_name` ON `channels` (`subscriptionId`, `name`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId` ON `series` (`subscriptionId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId_categoryName` ON `series` (`subscriptionId`, `categoryName`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId_isFavorite` ON `series` (`subscriptionId`, `isFavorite`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId_name` ON `series` (`subscriptionId`, `name`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_subscriptionId` ON `episodes` (`subscriptionId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_seriesId_seasonNumber_episodeNumber` ON `episodes` (`seriesId`, `seasonNumber`, `episodeNumber`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_subscriptionId_contentType` ON `categories` (`subscriptionId`, `contentType`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_subscriptionId_watchedAt` ON `watch_history` (`subscriptionId`, `watchedAt`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_channelId` ON `watch_history` (`channelId`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_subscriptionId_channelTvgId_startTimeMs_endTimeMs` ON `epg_programs` (`subscriptionId`, `channelTvgId`, `startTimeMs`, `endTimeMs`)")

                            // Insert 1 active M3U row (should be removed) + 1 inactive XTREAM row (should survive AND become active!)
                            db.execSQL(
                                "INSERT INTO subscriptions (id, name, type, serverUrl, encryptedM3uUrl, isActive, connectionStatus, createdAt) VALUES ('sub-m3u', 'Legacy M3U', 'M3U', 'http://m3u.host', 'enc-url', 1, 'CONNECTED', 100)",
                            )
                            db.execSQL(
                                "INSERT INTO subscriptions (id, name, type, serverUrl, username, encryptedPassword, isActive, connectionStatus, createdAt) VALUES ('sub-xt', 'Surviving Xtream', 'XTREAM', 'http://xt.host:8080', 'u1', 'ks1:cipher', 0, 'CONNECTED', 200)",
                            )
                            db.execSQL(
                                "INSERT INTO channels (id, subscriptionId, mediaType, name, streamUrl, resumePositionMs, isFavorite, addedAt) VALUES ('ch-m3u', 'sub-m3u', 'LIVE', 'M3U Ch', 'http://m3u/1.ts', 0, 0, 10)",
                            )
                            db.execSQL(
                                "INSERT INTO channels (id, subscriptionId, mediaType, name, streamUrl, resumePositionMs, isFavorite, addedAt) VALUES ('ch-xt', 'sub-xt', 'LIVE', 'Xtream Ch', 'http://xt/1.ts', 4200, 1, 20)",
                            )
                            db.execSQL(
                                "INSERT INTO series (id, subscriptionId, externalSeriesId, name, isFavorite, addedAt) VALUES ('sr-xt', 'sub-xt', '50', 'Surviving Series', 1, 20)",
                            )
                            db.execSQL(
                                "INSERT INTO episodes (id, subscriptionId, seriesId, externalEpisodeId, seasonNumber, episodeNumber, title, streamUrl, resumePositionMs) VALUES ('ep-xt', 'sub-xt', 'sr-xt', '500', 1, 1, 'Ep 1', 'http://xt/ep1.mp4', 900)",
                            )
                            db.execSQL(
                                "INSERT INTO watch_history (subscriptionId, channelId, channelName, watchedAt) VALUES ('sub-xt', 'ch-xt', 'Xtream Ch', 1000)",
                            )
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        v2Helper.writableDatabase.close()
        v2Helper.close()

        val migratedDb = Room.databaseBuilder(context, LiquidTVDatabase::class.java, dbFile.absolutePath)
            .addMigrations(LiquidTVDatabase.MIGRATION_1_2, LiquidTVDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // Legacy M3U row and its channel must be removed cleanly
        assertNull(migratedDb.subscriptionDao().getSubscriptionById("sub-m3u"))
        assertTrue(migratedDb.channelDao().getAllChannelsOnce("sub-m3u").isEmpty())

        // Surviving Xtream subscription, encryptedPassword, channels, series, episodes, watch history must all be intact!
        val survivingSub = migratedDb.subscriptionDao().getSubscriptionById("sub-xt")!!
        assertEquals("Surviving Xtream", survivingSub.name)
        assertEquals("ks1:cipher", survivingSub.encryptedPassword)
        assertEquals("NORMAL_URL", survivingSub.origin)
        assertTrue("Surviving Xtream should become active if M3U was active", survivingSub.isActive)

        val survivingChannels = migratedDb.channelDao().getAllChannelsOnce("sub-xt")
        assertEquals(1, survivingChannels.size)
        assertEquals("Xtream Ch", survivingChannels[0].name)
        assertTrue(survivingChannels[0].isFavorite)
        assertEquals(4200L, survivingChannels[0].resumePositionMs)

        assertEquals(1, migratedDb.seriesDao().getSeries("sub-xt").first().size)
        assertEquals(1, migratedDb.seriesDao().getEpisodesForSeries("sr-xt").first().size)
        assertEquals(1, migratedDb.watchHistoryDao().getHistory("sub-xt").first().size)

        migratedDb.close()
        dbFile.delete()
    }
}
