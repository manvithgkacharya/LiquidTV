package com.liquidtv.app.player

import java.util.Locale
import kotlin.math.abs

data class LiveOffsetAdjustment(
    val seekTargetMs: Long? = null,
    val holdDelayMs: Long = 0L,
    val newAppliedOffsetMs: Long,
)

/**
 * Reference-based (non-cumulative) synchronization policy for External Audio.
 *
 * Sign convention:
 * - Positive offsetMs (> 0): external audio trails video (delayed relative to video).
 * - Negative offsetMs (< 0): external audio leads video (advanced relative to video).
 */
object ExternalAudioSyncPolicy {
    const val MIN_OFFSET_MS = -10_000L
    const val MAX_OFFSET_MS = 10_000L
    const val STEP_MS = 500L
    const val VOD_DRIFT_RESYNC_THRESHOLD_MS = 350L
    const val VOD_SEEK_COOLDOWN_MS = 1_500L
    const val MAX_RETRY_ATTEMPTS = 6
    const val BUFFERING_WATCHDOG_MS = 25_000L

    fun clampOffsetMs(offsetMs: Long): Long =
        offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)

    fun formatOffsetAr(offsetMs: Long): String {
        val clamped = clampOffsetMs(offsetMs)
        if (clamped == 0L) return "متزامن (0.0 ث)"
        val seconds = abs(clamped) / 1000.0
        val formatted = String.format(Locale.US, "%.1f", seconds)
        return if (clamped > 0L) {
            "تأخير الصوت +${formatted} ث"
        } else {
            "تقديم الصوت -${formatted} ث"
        }
    }

    fun targetVodPositionMs(
        videoPositionMs: Long,
        offsetMs: Long,
        audioDurationMs: Long,
    ): Long {
        val target = (videoPositionMs.coerceAtLeast(0L) - clampOffsetMs(offsetMs)).coerceAtLeast(0L)
        return if (audioDurationMs > 0L) target.coerceAtMost(audioDurationMs) else target
    }

    fun shouldResyncVod(
        videoPositionMs: Long,
        audioPositionMs: Long,
        offsetMs: Long,
        audioDurationMs: Long,
        lastSeekElapsedMs: Long,
    ): Boolean {
        if (lastSeekElapsedMs < VOD_SEEK_COOLDOWN_MS) return false
        val target = targetVodPositionMs(videoPositionMs, offsetMs, audioDurationMs)
        return abs(audioPositionMs - target) > VOD_DRIFT_RESYNC_THRESHOLD_MS
    }

    /**
     * Gentle playback-rate drift correction when both streams expose valid live offsets.
     */
    fun livePlaybackSpeed(
        videoLiveOffsetMs: Long,
        audioLiveOffsetMs: Long,
        desiredOffsetMs: Long,
        toleranceMs: Long = 180L,
    ): Float {
        if (videoLiveOffsetMs < 0L || audioLiveOffsetMs < 0L) return 1.0f
        val drift = (audioLiveOffsetMs - videoLiveOffsetMs) - clampOffsetMs(desiredOffsetMs)
        return when {
            drift > toleranceMs -> 1.02f
            drift < -toleranceMs -> 0.98f
            else -> 1.0f
        }
    }

    /**
     * Computes a non-cumulative relative adjustment when the user modifies syncOffsetMs
     * during a live IPTV stream. Tracks what offset has already been applied to avoid
     * accumulating old seeks or holds.
     */
    fun computeLiveDeltaAdjustment(
        desiredOffsetMs: Long,
        alreadyAppliedOffsetMs: Long,
        currentAudioPositionMs: Long,
        isCurrentItemSeekable: Boolean,
        seekableWindowStartMs: Long,
        seekableWindowEndMs: Long,
    ): LiveOffsetAdjustment {
        val clampedDesired = clampOffsetMs(desiredOffsetMs)
        val clampedApplied = clampOffsetMs(alreadyAppliedOffsetMs)
        val delta = clampedDesired - clampedApplied
        if (delta == 0L) {
            return LiveOffsetAdjustment(newAppliedOffsetMs = clampedApplied)
        }

        if (isCurrentItemSeekable && seekableWindowEndMs > seekableWindowStartMs) {
            val candidate = currentAudioPositionMs - delta
            if (candidate in seekableWindowStartMs..seekableWindowEndMs) {
                return LiveOffsetAdjustment(
                    seekTargetMs = candidate,
                    newAppliedOffsetMs = clampedDesired,
                )
            }
        }

        if (delta > 0L) {
            val boundedHold = delta.coerceAtMost(5_000L)
            return LiveOffsetAdjustment(
                holdDelayMs = boundedHold,
                newAppliedOffsetMs = clampOffsetMs(clampedApplied + boundedHold),
            )
        }

        return LiveOffsetAdjustment(newAppliedOffsetMs = clampedApplied)
    }

    fun backoffDelayMs(attempt: Int): Long = when (attempt) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 4_000L
        4 -> 8_000L
        5 -> 16_000L
        else -> 30_000L
    }
}
