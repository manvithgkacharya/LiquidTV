package com.liquidtv.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.liquidtv.app.MainActivity
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Single ExoPlayer + MediaSession owner for LIQUID TV.
 * Handles dynamic connection timeouts, per-stream HTTP headers,
 * quality/subtitle TrackSelectionParameters, and TV-tuned buffering.
 */
@AndroidEntryPoint
class LiquidTVPlaybackService : MediaSessionService() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @Volatile
    private var dynamicTimeoutMs: Int = 15_000

    private val streamHeadersByUri = ConcurrentHashMap<String, Map<String, String>>()
    private val streamHeadersByHost = ConcurrentHashMap<String, Map<String, String>>()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        initializePlayerAndSession()
        observePreferences()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayerAndSession() {
        val playerClient = okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val timeout = dynamicTimeoutMs.coerceIn(5_000, 60_000)
                chain
                    .withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .withReadTimeout(timeout + 5_000, TimeUnit.MILLISECONDS)
                    .proceed(chain.request())
            }
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(playerClient)
            .setUserAgent("LiquidTV/1.1 (Android TV; Media3)")

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(okHttpDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            val host = dataSpec.uri.host?.lowercase()
            val headers = streamHeadersByUri[uriString]
                ?: host?.let { streamHeadersByHost[it] }
                ?: emptyMap()
            if (headers.isEmpty()) {
                dataSpec
            } else {
                dataSpec.withAdditionalHeaders(headers)
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000,
                35_000,
                2_000,
                4_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        player = exoPlayer

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val callback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> {
                cacheHeaders(mediaItems)
                return Futures.immediateFuture(mediaItems)
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                cacheHeaders(mediaItems)
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
        }

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(callback)
            .build()
    }

    private fun cacheHeaders(mediaItems: List<MediaItem>) {
        mediaItems.forEach { item ->
            val headers = PlayerMediaHelper.extractHeaders(item)
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
            if (uri != null) {
                if (headers.isNotEmpty()) {
                    streamHeadersByUri[uri.toString()] = headers
                    uri.host?.lowercase()?.let { host ->
                        streamHeadersByHost[host] = headers
                    }
                } else {
                    streamHeadersByUri.remove(uri.toString())
                }
            }
        }
    }

    private fun observePreferences() {
        serviceScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                applyPreferences(prefs)
            }
        }
    }

    private fun applyPreferences(prefs: UserPreferences) {
        dynamicTimeoutMs = (prefs.connectionTimeout.coerceIn(5, 60) * 1_000)
        val currentPlayer = player ?: return

        val builder = currentPlayer.trackSelectionParameters.buildUpon()
        when (prefs.defaultQuality.lowercase()) {
            "1080p", "fhd" -> builder.setMaxVideoSize(1920, 1080)
            "720p", "hd" -> builder.setMaxVideoSize(1280, 720)
            "480p", "sd" -> builder.setMaxVideoSize(854, 480)
            "data saver", "360p" -> builder.setMaxVideoSize(640, 360)
            else -> builder.clearVideoSizeConstraints()
        }

        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.subtitlesEnabled)
        if (prefs.subtitlesEnabled && prefs.preferredSubtitleLanguage.isNotBlank()) {
            builder.setPreferredTextLanguage(prefs.preferredSubtitleLanguage)
        } else {
            builder.setPreferredTextLanguages()
        }

        currentPlayer.trackSelectionParameters = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
            mediaSession = null
        }
        player = null
        streamHeadersByUri.clear()
        streamHeadersByHost.clear()
        super.onDestroy()
    }
}
