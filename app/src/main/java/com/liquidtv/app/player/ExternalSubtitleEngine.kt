package com.liquidtv.app.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import com.liquidtv.app.data.subtitles.SubtitlePipeline
import com.liquidtv.app.domain.model.TimedSubtitleCue

/**
 * Holds active external subtitle cues and handles cue lookup + non-destructive Media3 text track toggling.
 * NEVER calls stop(), clearMediaItems(), setMediaItem(), or prepare() on the bound [Player].
 */
class ExternalSubtitleEngine {
    private var cues: List<TimedSubtitleCue> = emptyList()
    var activeLabel: String? = null
        private set
    var activeFilePath: String? = null
        private set
    var isEnabled: Boolean = false
        private set

    fun hasLoadedTrack(): Boolean = cues.isNotEmpty()

    fun setLoadedSubtitles(
        cues: List<TimedSubtitleCue>,
        label: String,
        localFilePath: String?,
        player: Player?,
    ) {
        this.cues = cues
        this.activeLabel = label
        this.activeFilePath = localFilePath
        this.isEnabled = cues.isNotEmpty()
        if (this.isEnabled && player != null) {
            setEmbeddedTextTrackDisabled(player, disabled = true)
        }
    }

    fun setEnabled(enabled: Boolean, player: Player?) {
        if (!hasLoadedTrack()) {
            this.isEnabled = false
            return
        }
        this.isEnabled = enabled
        if (enabled && player != null) {
            setEmbeddedTextTrackDisabled(player, disabled = true)
        }
    }

    fun clear() {
        cues = emptyList()
        activeLabel = null
        activeFilePath = null
        isEnabled = false
    }

    fun currentCueText(positionMs: Long, delayMs: Int): String? {
        if (!isEnabled || cues.isEmpty()) return null
        return SubtitlePipeline.findActiveCueText(
            cues = cues,
            positionMs = positionMs,
            delayMs = delayMs,
        )
    }

    fun activateEmbeddedSubtitleTrack(
        player: Player,
        groupIndex: Int,
        trackIndex: Int,
    ) {
        isEnabled = false
        val groups = player.currentTracks.groups
        if (groupIndex !in groups.indices) return
        val trackGroup = groups[groupIndex].mediaTrackGroup
        val updated = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(trackGroup, trackIndex))
            .build()
        player.trackSelectionParameters = updated
    }

    fun disableAllSubtitles(player: Player?) {
        isEnabled = false
        player ?: return
        val updated = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        player.trackSelectionParameters = updated
    }

    private fun setEmbeddedTextTrackDisabled(player: Player, disabled: Boolean) {
        val params = player.trackSelectionParameters
        if (params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) == disabled) return
        val builder = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled)
        if (disabled) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        }
        player.trackSelectionParameters = builder.build()
    }
}
