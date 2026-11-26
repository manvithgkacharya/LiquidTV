package com.liquidtv.app.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.liquidtv.app.data.audio.AudioNetworkSecurityValidator
import com.liquidtv.app.data.security.LogSanitizer
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ExternalAudioEngineState(
    val channelId: String? = null,
    val status: ExternalAudioStatus = ExternalAudioStatus.IDLE,
    val isPcmVerified: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val retryAttempt: Int = 0,
    val maxRetries: Int = ExternalAudioSyncPolicy.MAX_RETRY_ATTEMPTS,
)

interface ExternalAudioController {
    val engineState: StateFlow<ExternalAudioEngineState>

    fun attach(
        channel: ExternalAudioChannel,
        initialVideoPositionMs: Long,
        isVideoLive: Boolean,
        initialOffsetMs: Long,
        volumePercent: Int,
        loudnessBoostEnabled: Boolean,
        startPaused: Boolean,
    )

    fun pause()
    fun resume()
    fun setVolumePercent(percent: Int)
    fun setLoudnessBoostEnabled(enabled: Boolean)

    fun applySyncOffset(
        offsetMs: Long,
        videoPositionMs: Long,
        isVideoLive: Boolean,
    )

    fun syncWithVideoPosition(
        videoPositionMs: Long,
        isVideoLive: Boolean,
        videoLiveOffsetMs: Long = C.TIME_UNSET,
        forceSeek: Boolean = false,
    )

    fun detach()
}

@OptIn(UnstableApi::class)
@Singleton
class Media3ExternalAudioController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : ExternalAudioController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _engineState = MutableStateFlow(ExternalAudioEngineState())
    override val engineState: StateFlow<ExternalAudioEngineState> = _engineState.asStateFlow()

    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var generation: Long = 0L
    private var holdGeneration: Long = 0L
    private var activeChannel: ExternalAudioChannel? = null
    private var isVideoLive: Boolean = true
    private var lastKnownVideoPosMs: Long = 0L
    private var desiredOffsetMs: Long = 0L
    private var appliedLiveOffsetMs: Long = 0L
    private var volumeFloat: Float = 1.0f
    private var boostEnabled: Boolean = true
    private var pausedByPrimary: Boolean = false
    private var pcmVerified: Boolean = false
    private var vodInitialSeekApplied: Boolean = false
    private var retryAttempt: Int = 0
    private var lastVodSeekRealtimeMs: Long = 0L

    private var retryRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null
    private var holdResumeRunnable: Runnable? = null

    override fun attach(
        channel: ExternalAudioChannel,
        initialVideoPositionMs: Long,
        isVideoLive: Boolean,
        initialOffsetMs: Long,
        volumePercent: Int,
        loudnessBoostEnabled: Boolean,
        startPaused: Boolean,
    ) {
        runOnMain {
            generation++
            holdGeneration++
            val activeGen = generation
            cancelAllRunnables()
            releasePlayerInternal()

            this.activeChannel = channel
            this.isVideoLive = isVideoLive
            this.lastKnownVideoPosMs = initialVideoPositionMs.coerceAtLeast(0L)
            this.desiredOffsetMs = ExternalAudioSyncPolicy.clampOffsetMs(initialOffsetMs)
            this.appliedLiveOffsetMs = 0L
            this.volumeFloat = (volumePercent.coerceIn(0, 100) / 100f)
            this.boostEnabled = loudnessBoostEnabled
            this.pausedByPrimary = startPaused
            this.pcmVerified = false
            this.vodInitialSeekApplied = false
            this.retryAttempt = 0
            this.lastVodSeekRealtimeMs = 0L

            _engineState.value = ExternalAudioEngineState(
                channelId = channel.id,
                status = ExternalAudioStatus.CONNECTING,
                isPcmVerified = false,
                statusMessage = "جارٍ الاتصال بـ ${channel.name}...",
                errorMessage = null,
                retryAttempt = 0,
                maxRetries = ExternalAudioSyncPolicy.MAX_RETRY_ATTEMPTS,
            )

            startPlayer(activeGen, channel)
        }
    }

    override fun pause() {
        runOnMain {
            pausedByPrimary = true
            holdGeneration++
            holdResumeRunnable?.let(mainHandler::removeCallbacks)
            holdResumeRunnable = null
            player?.pause()
        }
    }

    override fun resume() {
        runOnMain {
            pausedByPrimary = false
            if (activeChannel != null) {
                player?.play()
            }
        }
    }

    override fun setVolumePercent(percent: Int) {
        runOnMain {
            volumeFloat = percent.coerceIn(0, 100) / 100f
            player?.volume = volumeFloat
        }
    }

    override fun setLoudnessBoostEnabled(enabled: Boolean) {
        runOnMain {
            boostEnabled = enabled
            player?.let(::applyLoudnessEnhancer)
        }
    }

    override fun applySyncOffset(
        offsetMs: Long,
        videoPositionMs: Long,
        isVideoLive: Boolean,
    ) {
        runOnMain {
            this.isVideoLive = isVideoLive
            this.lastKnownVideoPosMs = videoPositionMs.coerceAtLeast(0L)
            this.desiredOffsetMs = ExternalAudioSyncPolicy.clampOffsetMs(offsetMs)

            val activePlayer = player ?: return@runOnMain
            if (activePlayer.playbackState != Player.STATE_READY) return@runOnMain

            if (!isVideoLive) {
                val target = ExternalAudioSyncPolicy.targetVodPositionMs(
                    videoPositionMs = lastKnownVideoPosMs,
                    offsetMs = desiredOffsetMs,
                    audioDurationMs = activePlayer.duration.coerceAtLeast(0L),
                )
                seekPlayerVod(activePlayer, target)
            } else {
                applyLiveAdjustment(activePlayer)
            }
        }
    }

    override fun syncWithVideoPosition(
        videoPositionMs: Long,
        isVideoLive: Boolean,
        videoLiveOffsetMs: Long,
        forceSeek: Boolean,
    ) {
        runOnMain {
            this.isVideoLive = isVideoLive
            this.lastKnownVideoPosMs = videoPositionMs.coerceAtLeast(0L)
            val activePlayer = player ?: return@runOnMain
            if (activePlayer.playbackState != Player.STATE_READY) return@runOnMain

            if (!isVideoLive) {
                val duration = activePlayer.duration.coerceAtLeast(0L)
                val target = ExternalAudioSyncPolicy.targetVodPositionMs(
                    videoPositionMs = lastKnownVideoPosMs,
                    offsetMs = desiredOffsetMs,
                    audioDurationMs = duration,
                )
                val elapsed = SystemClock.elapsedRealtime() - lastVodSeekRealtimeMs
                if (forceSeek || ExternalAudioSyncPolicy.shouldResyncVod(
                        videoPositionMs = lastKnownVideoPosMs,
                        audioPositionMs = activePlayer.currentPosition.coerceAtLeast(0L),
                        offsetMs = desiredOffsetMs,
                        audioDurationMs = duration,
                        lastSeekElapsedMs = elapsed,
                    )
                ) {
                    seekPlayerVod(activePlayer, target)
                }
            } else {
                val audioLiveOffset = activePlayer.currentLiveOffset
                val speed = ExternalAudioSyncPolicy.livePlaybackSpeed(
                    videoLiveOffsetMs = videoLiveOffsetMs,
                    audioLiveOffsetMs = audioLiveOffset,
                    desiredOffsetMs = desiredOffsetMs,
                )
                if (activePlayer.playbackParameters.speed != speed) {
                    activePlayer.playbackParameters = PlaybackParameters(speed)
                }
            }
        }
    }

    override fun detach() {
        runOnMain {
            generation++
            holdGeneration++
            activeChannel = null
            pausedByPrimary = false
            pcmVerified = false
            vodInitialSeekApplied = false
            retryAttempt = 0
            cancelAllRunnables()
            releasePlayerInternal()
            _engineState.value = ExternalAudioEngineState()
        }
    }

    private fun startPlayer(activeGen: Long, channel: ExternalAudioChannel) {
        if (!isCurrentGeneration(activeGen)) return
        val url = channel.streamUrl.trim()
        try {
            AudioNetworkSecurityValidator.assertSafeUrl(url)
        } catch (e: Exception) {
            failPermanently(activeGen, e.message ?: "رابط مصدر الصوت غير صالح")
            return
        }

        try {
            val audioHttpClient = okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val httpFactory = OkHttpDataSource.Factory(audioHttpClient)
                .setUserAgent("LiquidTV/1.1 (Android TV; ExternalAudio)")

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(4_000, 25_000, 1_200, 3_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            // CRITICAL: handleAudioFocus = false so secondary audio never pauses primary video A.
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()

            val audioPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
                .build()

            player = audioPlayer
            audioPlayer.volume = volumeFloat

            audioPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (!isCurrentPlayer(activeGen, audioPlayer)) return
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            scheduleWatchdog(activeGen)
                            val msg = if (pcmVerified) {
                                "جارٍ التخزين المؤقت للصوت..."
                            } else {
                                "جارٍ تحميل الصوت الخارجي..."
                            }
                            _engineState.value = _engineState.value.copy(
                                status = ExternalAudioStatus.BUFFERING,
                                statusMessage = msg,
                            )
                        }
                        Player.STATE_READY -> {
                            val hasSelectedAudio = audioPlayer.currentTracks.groups.any {
                                it.type == C.TRACK_TYPE_AUDIO && it.isSelected
                            }
                            if (!hasSelectedAudio) {
                                scheduleRetry(
                                    activeGen,
                                    "مصدر الصوت المختار لا يحتوي على مسار صوتي قابل للتشغيل",
                                    isPermanent = true,
                                )
                                return
                            }
                            cancelRetry()
                            cancelWatchdog()
                            retryAttempt = 0
                            applyLoudnessEnhancer(audioPlayer)
                            if (pcmVerified && audioPlayer.playWhenReady) {
                                _engineState.value = _engineState.value.copy(
                                    status = ExternalAudioStatus.ACTIVE,
                                    isPcmVerified = true,
                                    statusMessage = "نشط: ${channel.name}",
                                    errorMessage = null,
                                    retryAttempt = 0,
                                )
                            }

                            if (!isVideoLive && !vodInitialSeekApplied) {
                                vodInitialSeekApplied = true
                                val target = ExternalAudioSyncPolicy.targetVodPositionMs(
                                    videoPositionMs = lastKnownVideoPosMs,
                                    offsetMs = desiredOffsetMs,
                                    audioDurationMs = audioPlayer.duration.coerceAtLeast(0L),
                                )
                                if (target > 500L) {
                                    seekPlayerVod(audioPlayer, target)
                                }
                            } else if (isVideoLive && desiredOffsetMs != appliedLiveOffsetMs) {
                                applyLiveAdjustment(audioPlayer)
                            }
                        }
                        Player.STATE_ENDED -> {
                            scheduleRetry(
                                activeGen,
                                "انقطع بث الصوت الخارجي؛ جارٍ إعادة الاتصال...",
                                isPermanent = false,
                            )
                        }
                        Player.STATE_IDLE -> Unit
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (!isCurrentPlayer(activeGen, audioPlayer)) return
                    applyLoudnessEnhancer(audioPlayer)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!isCurrentPlayer(activeGen, audioPlayer)) return
                    Log.w(
                        TAG,
                        "External audio failed for ${LogSanitizer.sanitizeUrl(url)}: ${error.errorCodeName}",
                    )
                    val cause = error.cause
                    val httpErr = cause as? HttpDataSource.InvalidResponseCodeException
                    val permanent = PlayerMediaHelper.isPermanentError(error)
                    val message = if (httpErr != null) {
                        "تعذّر تشغيل مصدر الصوت الخارجي. تحقق من صلاحية الرابط وحالة المزود."
                    } else {
                        PlayerMediaHelper.formatPlaybackError(error)
                    }
                    scheduleRetry(activeGen, message, isPermanent = permanent)
                }
            })

            audioPlayer.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioPositionAdvancing(
                    eventTime: AnalyticsListener.EventTime,
                    playoutStartSystemTimeMs: Long,
                ) {
                    if (!isCurrentPlayer(activeGen, audioPlayer)) return
                    cancelWatchdog()
                    val alreadyVerified = pcmVerified
                    pcmVerified = true
                    if (!alreadyVerified || _engineState.value.status != ExternalAudioStatus.ACTIVE) {
                        _engineState.value = _engineState.value.copy(
                            status = ExternalAudioStatus.ACTIVE,
                            isPcmVerified = true,
                            statusMessage = "نشط: ${channel.name}",
                            errorMessage = null,
                            retryAttempt = 0,
                        )
                    }
                }
            })

            val mediaItem = buildAudioMediaItem(channel)
            audioPlayer.setMediaItem(mediaItem)
            audioPlayer.prepare()
            audioPlayer.playWhenReady = !pausedByPrimary
            scheduleWatchdog(activeGen)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Media3 external audio player", e)
            scheduleRetry(activeGen, e.message ?: "تعذّر بدء مشغل الصوت الخارجي", isPermanent = false)
        }
    }

    private fun buildAudioMediaItem(channel: ExternalAudioChannel): MediaItem {
        val uri = Uri.parse(channel.streamUrl)
        val mimeType = detectAudioStreamMimeType(channel.streamUrl, channel.containerExtension)
        return MediaItem.Builder()
            .setMediaId("ext-audio:${channel.id}")
            .setUri(uri)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .build()
    }

    private fun detectAudioStreamMimeType(url: String, extensionHint: String?): String? {
        val cleanExt = extensionHint?.trim()?.lowercase()?.removePrefix(".")
        val path = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault("")
        return when {
            cleanExt == "m3u8" || path.endsWith(".m3u8") || url.contains(".m3u8", true) ->
                MimeTypes.APPLICATION_M3U8
            cleanExt == "mpd" || path.endsWith(".mpd") || url.contains(".mpd", true) ->
                MimeTypes.APPLICATION_MPD
            cleanExt == "ism" || path.endsWith(".ism") || path.endsWith(".isml") ->
                MimeTypes.APPLICATION_SS
            cleanExt == "ts" || cleanExt == "mpegts" || path.endsWith(".ts") ->
                MimeTypes.VIDEO_MP2T
            cleanExt == "aac" || path.endsWith(".aac") ->
                MimeTypes.AUDIO_AAC
            cleanExt == "mp3" || path.endsWith(".mp3") ->
                MimeTypes.AUDIO_MPEG
            else -> null
        }
    }

    private fun seekPlayerVod(activePlayer: ExoPlayer, targetMs: Long) {
        val clampedTarget = targetMs.coerceAtLeast(0L)
        lastVodSeekRealtimeMs = SystemClock.elapsedRealtime()
        activePlayer.seekTo(clampedTarget)
    }

    private fun applyLiveAdjustment(activePlayer: ExoPlayer) {
        val windowStart = if (activePlayer.currentTimeline.isEmpty) 0L else 0L
        val duration = activePlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        val adjustment = ExternalAudioSyncPolicy.computeLiveDeltaAdjustment(
            desiredOffsetMs = desiredOffsetMs,
            alreadyAppliedOffsetMs = appliedLiveOffsetMs,
            currentAudioPositionMs = activePlayer.currentPosition.coerceAtLeast(0L),
            isCurrentItemSeekable = activePlayer.isCurrentMediaItemSeekable && duration > 0L,
            seekableWindowStartMs = windowStart,
            seekableWindowEndMs = duration,
        )
        appliedLiveOffsetMs = adjustment.newAppliedOffsetMs

        adjustment.seekTargetMs?.let { target ->
            activePlayer.seekTo(target)
            return
        }

        if (adjustment.holdDelayMs > 0L && !pausedByPrimary) {
            val activeHoldGen = ++holdGeneration
            val activeGen = generation
            holdResumeRunnable?.let(mainHandler::removeCallbacks)
            activePlayer.pause()
            val runnable = Runnable {
                holdResumeRunnable = null
                if (isCurrentPlayer(activeGen, activePlayer) &&
                    holdGeneration == activeHoldGen &&
                    !pausedByPrimary
                ) {
                    activePlayer.play()
                }
            }
            holdResumeRunnable = runnable
            mainHandler.postDelayed(runnable, adjustment.holdDelayMs)
        }
    }

    private fun applyLoudnessEnhancer(activePlayer: ExoPlayer) {
        releaseLoudnessEnhancer()
        if (!boostEnabled) return
        val sessionId = activePlayer.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(500) // +5 dB safe clarity boost
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer unavailable on device: ${e.message}")
            loudnessEnhancer = null
        }
    }

    private fun scheduleWatchdog(activeGen: Long) {
        cancelWatchdog()
        val runnable = Runnable {
            watchdogRunnable = null
            if (!isCurrentGeneration(activeGen)) return@Runnable
            val p = player ?: return@Runnable
            if (p.playbackState != Player.STATE_READY || !pcmVerified) {
                scheduleRetry(
                    activeGen,
                    "انتهت مهلة التخزين المؤقت للصوت الخارجي؛ جارٍ إعادة المحاولة...",
                    isPermanent = false,
                )
            }
        }
        watchdogRunnable = runnable
        mainHandler.postDelayed(runnable, ExternalAudioSyncPolicy.BUFFERING_WATCHDOG_MS)
    }

    private fun scheduleRetry(activeGen: Long, message: String, isPermanent: Boolean) {
        if (!isCurrentGeneration(activeGen)) return
        val channel = activeChannel ?: return
        cancelWatchdog()
        cancelRetry()
        releasePlayerInternal()
        pcmVerified = false

        val nextAttempt = retryAttempt + 1
        if (isPermanent || nextAttempt > ExternalAudioSyncPolicy.MAX_RETRY_ATTEMPTS) {
            failPermanently(activeGen, message)
            return
        }

        retryAttempt = nextAttempt
        val delayMs = ExternalAudioSyncPolicy.backoffDelayMs(nextAttempt)
        _engineState.value = ExternalAudioEngineState(
            channelId = channel.id,
            status = ExternalAudioStatus.RECOVERING,
            isPcmVerified = false,
            statusMessage = "إعادة الاتصال بالصوت ($nextAttempt/${ExternalAudioSyncPolicy.MAX_RETRY_ATTEMPTS})...",
            errorMessage = message,
            retryAttempt = nextAttempt,
            maxRetries = ExternalAudioSyncPolicy.MAX_RETRY_ATTEMPTS,
        )

        val runnable = Runnable {
            retryRunnable = null
            if (isCurrentGeneration(activeGen)) {
                startPlayer(activeGen, channel)
            }
        }
        retryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun failPermanently(activeGen: Long, message: String) {
        if (!isCurrentGeneration(activeGen)) return
        val channel = activeChannel
        cancelAllRunnables()
        releasePlayerInternal()
        pcmVerified = false
        _engineState.value = ExternalAudioEngineState(
            channelId = channel?.id,
            status = ExternalAudioStatus.ERROR,
            isPcmVerified = false,
            statusMessage = null,
            errorMessage = message,
            retryAttempt = retryAttempt,
            maxRetries = ExternalAudioSyncPolicy.MAX_RETRY_ATTEMPTS,
        )
    }

    private fun isCurrentGeneration(targetGen: Long): Boolean =
        generation == targetGen && activeChannel != null

    private fun isCurrentPlayer(targetGen: Long, candidate: ExoPlayer): Boolean =
        isCurrentGeneration(targetGen) && player === candidate

    private fun cancelRetry() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let(mainHandler::removeCallbacks)
        watchdogRunnable = null
    }

    private fun cancelAllRunnables() {
        cancelRetry()
        cancelWatchdog()
        holdResumeRunnable?.let(mainHandler::removeCallbacks)
        holdResumeRunnable = null
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
        } catch (_: Throwable) {
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun releasePlayerInternal() {
        releaseLoudnessEnhancer()
        val old = player
        player = null
        if (old != null) {
            try {
                old.stop()
                old.clearMediaItems()
            } catch (_: Exception) {
            }
            old.release()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "ExternalAudioCtrl"
    }
}
