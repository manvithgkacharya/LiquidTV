package com.liquidtv.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.liquidtv.app.data.remote.LoadedExternalSubtitle
import com.liquidtv.app.data.audio.AudioSourceClassifier
import com.liquidtv.app.data.audio.ExternalAudioCatalogRepository
import com.liquidtv.app.data.remote.StremioAddonClient
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.OnlineSubtitleResult
import com.liquidtv.app.domain.model.SubtitleMediaMetadata
import com.liquidtv.app.domain.model.SubtitleStyleSettings
import com.liquidtv.app.domain.model.TrackInfo
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.AddonPreferencesRepository
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val channel: Channel? = null,
    val mediaType: MediaType = MediaType.LIVE,
    val epgSummary: ChannelEpgSummary = ChannelEpgSummary(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val isError: Boolean = false,
    val isRetrying: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 3,
    val errorMessage: String? = null,
    val showControls: Boolean = true,
    val showTrackSheet: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val allChannels: List<Channel> = emptyList(),
    val currentChannelIndex: Int = -1,
    val nextEpisodeId: String? = null,
    val previousEpisodeId: String? = null,
    val externalSubtitleError: String? = null,
    // Stremio Addons & Zero-Reload Subtitles state
    val subtitleMetadata: SubtitleMediaMetadata? = null,
    val subtitleSearchLanguage: String = "ar",
    val subtitleSearchResults: List<OnlineSubtitleResult> = emptyList(),
    val resolvedImdbId: String? = null,
    val isSearchingSubtitles: Boolean = false,
    val subtitleSearchStatus: String? = null,
    val subtitleSearchError: String? = null,
    val isApplyingSubtitle: Boolean = false,
    val applyingSubtitleUrl: String? = null,
    val activeExternalSubtitleLabel: String? = null,
    val externalSubtitlesEnabled: Boolean = false,
    val hasExternalSubtitleLoaded: Boolean = false,
    val activeExternalCueText: String? = null,
    val subtitleStyle: SubtitleStyleSettings = SubtitleStyleSettings(),
    // External Audio (الصوت الخارجي) state
    val externalAudio: ExternalAudioUiState = ExternalAudioUiState(),
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val addonPreferencesRepository: AddonPreferencesRepository,
    private val stremioAddonClient: StremioAddonClient,
    private val subscriptionRepository: SubscriptionRepository,
    private val externalAudioCatalogRepository: ExternalAudioCatalogRepository,
    private val externalAudioController: ExternalAudioController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _boundPlayer = MutableStateFlow<Player?>(null)
    val boundPlayer: StateFlow<Player?> = _boundPlayer.asStateFlow()

    private val externalSubtitleEngine = ExternalSubtitleEngine()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerConnectJob: Job? = null
    private var activePlayer: Player? = null
    private var currentPrefs: UserPreferences = UserPreferences()
    private var retryJob: Job? = null
    private var progressJob: Job? = null
    private var subtitleSyncJob: Job? = null
    private var epgJob: Job? = null
    private var externalAudioCatalogJob: Job? = null
    @Volatile
    private var externalAudioCatalogGeneration: Long = 0L
    private var activeSeriesId: String? = null
    private var activeItemKey: String? = null
    private var lastSubmittedUrl: String? = null
    private var pendingStartRequest: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = activePlayer ?: return
            val hasError = playbackState == Player.STATE_IDLE && player.playerError != null
            if (playbackState == Player.STATE_READY) {
                retryJob?.cancel()
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isRetrying = false,
                        isError = false,
                        retryAttempt = 0,
                        errorMessage = null,
                        durationMs = player.duration.coerceAtLeast(0L),
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                }
                refreshActiveSubtitleCue()
            } else {
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isPlaying = player.isPlaying,
                        isError = hasError,
                    )
                }
            }

            if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                persistResumePosition()
            }
            refreshActiveSubtitleCue()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (_state.value.externalAudio.isAttached) {
                if (playWhenReady) {
                    externalAudioController.resume()
                } else {
                    externalAudioController.pause()
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            refreshActiveSubtitleCue(newPosition.positionMs)
            if (_state.value.externalAudio.isAttached &&
                _state.value.mediaType != MediaType.LIVE &&
                reason == Player.DISCONTINUITY_REASON_SEEK
            ) {
                externalAudioController.syncWithVideoPosition(
                    videoPositionMs = newPosition.positionMs.coerceAtLeast(0L),
                    isVideoLive = false,
                    forceSeek = true,
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(
                    audioTracks = PlayerMediaHelper.extractTracks(tracks, C.TRACK_TYPE_AUDIO),
                    subtitleTracks = PlayerMediaHelper.extractTracks(tracks, C.TRACK_TYPE_TEXT),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    init {
        connectMediaController()
        observeChannelsAndPreferences()
        observeExternalAudioEngine()
        preloadExternalAudioCatalog()
    }

    private fun connectMediaController() {
        controllerConnectJob = viewModelScope.launch {
            try {
                val sessionToken = SessionToken(
                    context,
                    ComponentName(context, LiquidTVPlaybackService::class.java),
                )
                val future = MediaController.Builder(context, sessionToken).buildAsync()
                controllerFuture = future
                val controller = future.await()
                activePlayer = controller
                controller.addListener(playerListener)
                _boundPlayer.value = controller
                startProgressLoop()
                startSubtitleSyncLoop()
                pendingStartRequest?.invoke()
                pendingStartRequest = null
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isError = true,
                        errorMessage = "تعذّر بدء خدمة التشغيل (${e.localizedMessage ?: "خطأ"}).",
                    )
                }
            }
        }
    }

    @VisibleForTesting
    internal fun attachPlayerForTesting(player: Player) {
        controllerConnectJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        activePlayer?.removeListener(playerListener)
        activePlayer = player
        player.addListener(playerListener)
        _boundPlayer.value = player
        startSubtitleSyncLoop()
        refreshActiveSubtitleCue()
    }

    private fun observeExternalAudioEngine() {
        viewModelScope.launch {
            externalAudioController.engineState.collect { engine ->
                val currentExt = _state.value.externalAudio
                val matchedChannel = engine.channelId?.let { id ->
                    currentExt.activeChannel?.takeIf { it.id == id }
                        ?: currentExt.channels.firstOrNull { it.id == id }
                }
                val isAttached = matchedChannel != null && engine.status != ExternalAudioStatus.IDLE
                _state.update {
                    it.copy(
                        externalAudio = it.externalAudio.copy(
                            activeChannel = if (engine.status == ExternalAudioStatus.IDLE) null else matchedChannel ?: it.externalAudio.activeChannel,
                            status = engine.status,
                            isPcmVerified = engine.isPcmVerified,
                            statusMessage = engine.statusMessage,
                            errorMessage = engine.errorMessage,
                            retryAttempt = engine.retryAttempt,
                            maxRetries = engine.maxRetries,
                            connectionLimitWarning = if (engine.status == ExternalAudioStatus.IDLE) null else it.externalAudio.connectionLimitWarning,
                        ),
                    )
                }
                syncPrimaryPlayerAudioDucking(
                    isExternalAttached = isAttached,
                    status = engine.status,
                    isPcmVerified = engine.isPcmVerified,
                )
            }
        }
    }

    private fun syncPrimaryPlayerAudioDucking(
        isExternalAttached: Boolean,
        status: ExternalAudioStatus,
        isPcmVerified: Boolean,
    ) {
        val controller = activePlayer ?: return
        val shouldMutePrimary = isExternalAttached && when (status) {
            ExternalAudioStatus.ACTIVE -> isPcmVerified
            ExternalAudioStatus.BUFFERING -> isPcmVerified
            ExternalAudioStatus.IDLE,
            ExternalAudioStatus.LOADING_CATALOG,
            ExternalAudioStatus.CONNECTING,
            ExternalAudioStatus.RECOVERING,
            ExternalAudioStatus.ERROR -> false
        }
        val targetVolume = if (shouldMutePrimary) 0f else 1f
        if (controller.volume != targetVolume) {
            controller.volume = targetVolume
        }
    }

    private fun observeChannelsAndPreferences() {
        viewModelScope.launch {
            channelRepository.getAllChannels().collect { channels ->
                val currentId = _state.value.channel?.id
                val index = channels.indexOfFirst { it.id == currentId }
                _state.update {
                    it.copy(
                        allChannels = channels,
                        currentChannelIndex = if (index >= 0) index else it.currentChannelIndex,
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { prefs ->
                currentPrefs = prefs
                _state.update { it.copy(maxRetries = prefs.maxRetries) }
            }
        }
        viewModelScope.launch {
            addonPreferencesRepository.subtitleStyle.collect { style ->
                _state.update { it.copy(subtitleStyle = style) }
                refreshActiveSubtitleCue()
            }
        }
        viewModelScope.launch {
            addonPreferencesRepository.preferredSubtitleSearchLanguage.collect { lang ->
                _state.update { it.copy(subtitleSearchLanguage = lang) }
            }
        }
    }

    fun loadItem(itemId: String, mediaType: MediaType = MediaType.LIVE) {
        val key = "${mediaType.name}:$itemId"
        if (activeItemKey == key && !_state.value.isError && lastSubmittedUrl != null) {
            return
        }
        retryJob?.cancel()
        epgJob?.cancel()
        activeItemKey = key
        activeSeriesId = null
        resetExternalSubtitlesForNewItem()
        detachExternalAudio()

        viewModelScope.launch {
            when (mediaType) {
                MediaType.SERIES_EPISODE -> {
                    val episode = channelRepository.getEpisodeById(itemId)
                    if (episode == null) {
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                isError = true,
                                errorMessage = "تعذّر العثور على الحلقة.",
                            )
                        }
                        return@launch
                    }
                    activeSeriesId = episode.seriesId
                    val series = channelRepository.getSeriesById(episode.seriesId).firstOrNull()
                    val siblings = channelRepository.getEpisodesForSeries(episode.seriesId).firstOrNull().orEmpty()
                    val epIdx = siblings.indexOfFirst { it.id == episode.id }
                    val prevEp = if (epIdx > 0) siblings[epIdx - 1].id else null
                    val nextEp = if (epIdx in 0 until siblings.lastIndex) siblings[epIdx + 1].id else null

                    val seriesDisplayName = series?.name?.takeIf { it.isNotBlank() }
                    val syntheticChannel = Channel(
                        id = episode.id,
                        subscriptionId = episode.subscriptionId,
                        externalId = episode.externalEpisodeId,
                        mediaType = MediaType.SERIES_EPISODE,
                        name = "${seriesDisplayName ?: episode.title} • S${episode.seasonNumber}E${episode.episodeNumber}",
                        streamUrl = episode.streamUrl,
                        logoUrl = episode.coverUrl,
                        groupTitle = "المسلسلات",
                        containerExtension = episode.containerExtension,
                        plot = episode.plot,
                        durationSec = episode.durationSec,
                        resumePositionMs = episode.resumePositionMs,
                    )
                    val subtitleMeta = SubtitleMediaMetadata(
                        title = episode.title,
                        mediaType = MediaType.SERIES_EPISODE,
                        seriesName = seriesDisplayName ?: episode.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                    )
                    _state.update {
                        it.copy(
                            channel = syntheticChannel,
                            mediaType = MediaType.SERIES_EPISODE,
                            subtitleMetadata = subtitleMeta,
                            epgSummary = ChannelEpgSummary(),
                            isError = false,
                            isRetrying = false,
                            retryAttempt = 0,
                            isBuffering = true,
                            errorMessage = null,
                            showControls = true,
                            nextEpisodeId = nextEp,
                            previousEpisodeId = prevEp,
                        )
                    }
                    startPlayback(syntheticChannel, resumeMs = episode.resumePositionMs)
                    channelRepository.recordEpisodeWatch(episode.id, episode.resumePositionMs, (episode.durationSec ?: 0) * 1000L)
                }
                MediaType.LIVE, MediaType.MOVIE -> {
                    val channel = channelRepository.getChannelById(itemId)
                    if (channel == null) {
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                isError = true,
                                errorMessage = "تعذّر العثور على البث.",
                            )
                        }
                        return@launch
                    }
                    val allChannels = _state.value.allChannels
                    val index = allChannels.indexOfFirst { it.id == itemId }
                    val subtitleMeta = SubtitleMediaMetadata(
                        title = channel.name,
                        mediaType = channel.mediaType,
                    )
                    _state.update {
                        it.copy(
                            channel = channel,
                            mediaType = channel.mediaType,
                            subtitleMetadata = subtitleMeta,
                            currentChannelIndex = index,
                            isError = false,
                            isRetrying = false,
                            retryAttempt = 0,
                            isBuffering = true,
                            errorMessage = null,
                            showControls = true,
                            nextEpisodeId = null,
                            previousEpisodeId = null,
                        )
                    }
                    val startPos = if (channel.mediaType == MediaType.MOVIE) channel.resumePositionMs else 0L
                    startPlayback(channel, resumeMs = startPos)
                    channelRepository.recordWatch(channel.id, startPos, (channel.durationSec ?: 0) * 1000L)
                    if (channel.mediaType == MediaType.LIVE) {
                        observeEpgForChannel(channel)
                    } else {
                        _state.update { it.copy(epgSummary = ChannelEpgSummary()) }
                    }
                }
            }
        }
    }

    private fun resetExternalSubtitlesForNewItem() {
        externalSubtitleEngine.clear()
        _state.update {
            it.copy(
                subtitleSearchResults = emptyList(),
                resolvedImdbId = null,
                subtitleSearchStatus = null,
                subtitleSearchError = null,
                activeExternalSubtitleLabel = null,
                externalSubtitlesEnabled = false,
                hasExternalSubtitleLoaded = false,
                activeExternalCueText = null,
                externalSubtitleError = null,
            )
        }
    }

    private fun observeEpgForChannel(channel: Channel) {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            launch {
                val quickSummary = channelRepository.fetchShortEpgIfNeeded(channel)
                if (quickSummary.current != null || quickSummary.next != null) {
                    _state.update { it.copy(epgSummary = quickSummary) }
                }
            }
            channelRepository.observeChannelEpg(channel).collect { summary ->
                if (summary.current != null || summary.next != null) {
                    _state.update { it.copy(epgSummary = summary) }
                }
            }
        }
    }

    private fun startPlayback(
        channel: Channel,
        resumeMs: Long = 0L,
    ) {
        lastSubmittedUrl = channel.streamUrl
        val action = {
            val controller = activePlayer
            if (controller != null) {
                val mediaItem = PlayerMediaHelper.buildMediaItem(
                    mediaId = channel.id,
                    title = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    explicitExtension = channel.containerExtension,
                    headers = channel.httpHeaders,
                )
                controller.stop()
                controller.clearMediaItems()
                if (resumeMs > 5_000L && channel.mediaType != MediaType.LIVE) {
                    controller.setMediaItem(mediaItem, resumeMs)
                } else {
                    controller.setMediaItem(mediaItem)
                }
                controller.prepare()
                controller.playWhenReady = true
            }
        }
        if (activePlayer != null) {
            action()
        } else {
            pendingStartRequest = action
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val currentState = _state.value
        val activeChannel = currentState.channel ?: return

        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            activePlayer?.let {
                it.seekToDefaultPosition()
                it.prepare()
            }
            return
        }

        val maxRetries = currentPrefs.maxRetries.coerceIn(0, 5)
        val permanent = PlayerMediaHelper.isPermanentError(error)
        val nextAttempt = currentState.retryAttempt + 1

        if (!permanent && nextAttempt <= maxRetries) {
            retryJob?.cancel()
            retryJob = viewModelScope.launch {
                _state.update {
                    it.copy(
                        isRetrying = true,
                        isBuffering = true,
                        isError = false,
                        retryAttempt = nextAttempt,
                        maxRetries = maxRetries,
                        errorMessage = "إعادة الاتصال ($nextAttempt/$maxRetries)...",
                    )
                }
                delay(PlayerMediaHelper.backoffDelayMs(nextAttempt))
                if (isActive && _state.value.channel?.id == activeChannel.id) {
                    val resumeAt = if (activeChannel.mediaType != MediaType.LIVE) {
                        activePlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    } else 0L
                    startPlayback(activeChannel, resumeMs = resumeAt)
                }
            }
        } else {
            _state.update {
                it.copy(
                    isRetrying = false,
                    isBuffering = false,
                    isError = true,
                    errorMessage = PlayerMediaHelper.formatPlaybackError(error),
                )
            }
        }
    }

    private fun handlePlaybackEnded() {
        val currentState = _state.value
        if (!currentPrefs.autoPlay) return
        when (currentState.mediaType) {
            MediaType.SERIES_EPISODE -> {
                currentState.nextEpisodeId?.let { nextId ->
                    loadItem(nextId, MediaType.SERIES_EPISODE)
                }
            }
            MediaType.LIVE -> nextChannel()
            MediaType.MOVIE -> Unit
        }
    }

    fun play() { activePlayer?.play() }
    fun pause() { activePlayer?.pause() }

    fun seekBy(deltaMs: Long) {
        val controller = activePlayer ?: return
        val duration = controller.duration.coerceAtLeast(0L)
        if (duration > 0L) {
            val target = (controller.currentPosition + deltaMs).coerceIn(0L, duration)
            controller.seekTo(target)
            refreshActiveSubtitleCue(target)
            if (_state.value.externalAudio.isAttached && _state.value.mediaType != MediaType.LIVE) {
                externalAudioController.syncWithVideoPosition(
                    videoPositionMs = target,
                    isVideoLive = false,
                    forceSeek = true,
                )
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        activePlayer?.seekTo(clamped)
        refreshActiveSubtitleCue(clamped)
        if (_state.value.externalAudio.isAttached && _state.value.mediaType != MediaType.LIVE) {
            externalAudioController.syncWithVideoPosition(
                videoPositionMs = clamped,
                isVideoLive = false,
                forceSeek = true,
            )
        }
    }

    fun nextChannel() {
        val nextEp = _state.value.nextEpisodeId
        if (_state.value.mediaType == MediaType.SERIES_EPISODE && nextEp != null) {
            loadItem(nextEp, MediaType.SERIES_EPISODE)
            return
        }
        val channels = _state.value.allChannels
        if (channels.isEmpty()) return
        val index = _state.value.currentChannelIndex
        val next = (index + 1) % channels.size
        loadItem(channels[next].id, MediaType.LIVE)
    }

    fun previousChannel() {
        val prevEp = _state.value.previousEpisodeId
        if (_state.value.mediaType == MediaType.SERIES_EPISODE && prevEp != null) {
            loadItem(prevEp, MediaType.SERIES_EPISODE)
            return
        }
        val channels = _state.value.allChannels
        if (channels.isEmpty()) return
        val index = _state.value.currentChannelIndex
        val prev = if (index <= 0) channels.size - 1 else index - 1
        loadItem(channels[prev].id, MediaType.LIVE)
    }

    fun toggleFavorite() {
        val item = _state.value.channel ?: return
        if (item.mediaType == MediaType.SERIES_EPISODE) return
        viewModelScope.launch {
            channelRepository.toggleFavorite(item.id)
            val updated = channelRepository.getChannelById(item.id)
            _state.update { it.copy(channel = updated ?: it.channel) }
        }
    }

    fun retry() {
        val channel = _state.value.channel ?: return
        retryJob?.cancel()
        _state.update {
            it.copy(
                isError = false,
                isRetrying = false,
                retryAttempt = 0,
                isBuffering = true,
                errorMessage = null,
            )
        }
        val resumeAt = if (channel.mediaType != MediaType.LIVE) {
            activePlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        } else 0L
        startPlayback(channel, resumeMs = resumeAt)
    }

    fun selectAudioTrack(track: TrackInfo) {
        val controller = activePlayer ?: return
        val groups = controller.currentTracks.groups
        if (track.groupIndex !in groups.indices) return
        val trackGroup = groups[track.groupIndex].mediaTrackGroup
        val updated = controller.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(trackGroup, track.trackIndex))
            .build()
        controller.trackSelectionParameters = updated
    }

    fun selectSubtitleTrack(track: TrackInfo?) {
        val controller = activePlayer ?: return
        if (track == null) {
            externalSubtitleEngine.disableAllSubtitles(controller)
            _state.update {
                it.copy(
                    externalSubtitlesEnabled = false,
                    activeExternalCueText = null,
                )
            }
        } else {
            externalSubtitleEngine.activateEmbeddedSubtitleTrack(
                player = controller,
                groupIndex = track.groupIndex,
                trackIndex = track.trackIndex,
            )
            _state.update {
                it.copy(
                    externalSubtitlesEnabled = false,
                    activeExternalCueText = null,
                )
            }
        }
    }

    fun setSubtitleSearchLanguage(languageCode: String) {
        viewModelScope.launch {
            addonPreferencesRepository.setPreferredSubtitleSearchLanguage(languageCode)
        }
    }

    fun searchOnlineSubtitles() {
        val meta = _state.value.subtitleMetadata
        if (meta == null || !meta.isSearchEligible) {
            _state.update {
                it.copy(
                    subtitleSearchError = "البحث عن الترجمة متاح للأفلام والحلقات فقط.",
                    isSearchingSubtitles = false,
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSearchingSubtitles = true,
                    subtitleSearchError = null,
                    subtitleSearchStatus = "جارٍ البحث عن المعرّف والترجمات عبر الإضافات المثبتة...",
                )
            }
            runCatching {
                val addons = addonPreferencesRepository.getInstalledAddonsOnce()
                val lang = _state.value.subtitleSearchLanguage
                stremioAddonClient.searchSubtitles(meta, lang, addons)
            }.onSuccess { outcome ->
                _state.update {
                    it.copy(
                        isSearchingSubtitles = false,
                        resolvedImdbId = outcome.identity.imdbId,
                        subtitleSearchResults = outcome.results,
                        subtitleSearchError = null,
                        subtitleSearchStatus = if (outcome.results.isNotEmpty()) {
                            "تم العثور على ${outcome.results.size} ترجمة (${outcome.identity.resourceId})"
                        } else {
                            "لم يتم العثور على ترجمة مطابقة (${outcome.identity.resourceId})"
                        },
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isSearchingSubtitles = false,
                        subtitleSearchError = err.message ?: "تعذر البحث عن الترجمة حالياً.",
                        subtitleSearchStatus = null,
                    )
                }
            }
        }
    }

    fun applyOnlineSubtitle(result: OnlineSubtitleResult) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isApplyingSubtitle = true,
                    applyingSubtitleUrl = result.url,
                    externalSubtitleError = null,
                    subtitleSearchError = null,
                )
            }
            runCatching {
                stremioAddonClient.downloadAndParseSubtitle(
                    subtitleUrl = result.url,
                    labelHint = result.label,
                    preferredLang = result.language,
                )
            }.onSuccess { loaded ->
                activateLoadedSubtitleZeroReload(loaded)
                _state.update {
                    it.copy(
                        isApplyingSubtitle = false,
                        applyingSubtitleUrl = null,
                        subtitleSearchStatus = "تم تفعيل الترجمة بدون إعادة تحميل: ${loaded.label}",
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isApplyingSubtitle = false,
                        applyingSubtitleUrl = null,
                        subtitleSearchError = err.message ?: "تعذر تحميل ملف الترجمة",
                        externalSubtitleError = err.message ?: "تعذر تحميل ملف الترجمة",
                    )
                }
            }
        }
    }

    /**
     * Zero-reload activation for direct external subtitle URLs (.srt, .vtt, .ass, .ssa, .ttml, .zip).
     * Never calls stop(), clearMediaItems(), setMediaItem(), or prepare().
     */
    fun loadExternalSubtitle(url: String, language: String = "und") {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            _state.update { it.copy(externalSubtitleError = "يجب أن يبدأ رابط الترجمة بـ http:// أو https://") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isApplyingSubtitle = true,
                    applyingSubtitleUrl = trimmed,
                    externalSubtitleError = null,
                )
            }
            runCatching {
                stremioAddonClient.downloadAndParseSubtitle(
                    subtitleUrl = trimmed,
                    labelHint = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { "ترجمة خارجية" },
                    preferredLang = language.ifBlank { "und" },
                )
            }.onSuccess { loaded ->
                activateLoadedSubtitleZeroReload(loaded)
                _state.update {
                    it.copy(
                        isApplyingSubtitle = false,
                        applyingSubtitleUrl = null,
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isApplyingSubtitle = false,
                        applyingSubtitleUrl = null,
                        externalSubtitleError = err.message ?: "تعذر تحميل الترجمة الخارجية",
                    )
                }
            }
        }
    }

    /**
     * Zero-reload activation for user-selected local subtitle URIs via Storage Access Framework.
     */
    fun applyLocalSubtitleUri(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isApplyingSubtitle = true,
                    externalSubtitleError = null,
                    subtitleSearchError = null,
                )
            }
            runCatching {
                stremioAddonClient.readAndParseLocalUri(uri)
            }.onSuccess { loaded ->
                activateLoadedSubtitleZeroReload(loaded)
                _state.update {
                    it.copy(
                        isApplyingSubtitle = false,
                        subtitleSearchStatus = "تم تفعيل الترجمة المحلية: ${loaded.label}",
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isApplyingSubtitle = false,
                        externalSubtitleError = err.message ?: "تعذر قراءة ملف الترجمة المحلي",
                    )
                }
            }
        }
    }

    @VisibleForTesting
    internal fun activateLoadedSubtitleZeroReload(loaded: LoadedExternalSubtitle) {
        externalSubtitleEngine.setLoadedSubtitles(
            cues = loaded.cues,
            label = loaded.label,
            localFilePath = loaded.localFilePath,
            player = activePlayer,
        )
        val cue = externalSubtitleEngine.currentCueText(
            positionMs = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.currentPositionMs,
            delayMs = _state.value.subtitleStyle.delayMs,
        )
        _state.update {
            it.copy(
                activeExternalSubtitleLabel = loaded.label,
                externalSubtitlesEnabled = true,
                hasExternalSubtitleLoaded = true,
                activeExternalCueText = cue,
                externalSubtitleError = null,
            )
        }
    }

    fun toggleExternalSubtitles() {
        if (!externalSubtitleEngine.hasLoadedTrack()) {
            _state.update { it.copy(externalSubtitleError = "لا يوجد ملف ترجمة خارجي محمّل حالياً.") }
            return
        }
        val next = !externalSubtitleEngine.isEnabled
        externalSubtitleEngine.setEnabled(next, activePlayer)
        val cue = if (next) {
            externalSubtitleEngine.currentCueText(
                positionMs = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.currentPositionMs,
                delayMs = _state.value.subtitleStyle.delayMs,
            )
        } else {
            null
        }
        _state.update {
            it.copy(
                externalSubtitlesEnabled = next,
                activeExternalCueText = cue,
            )
        }
    }

    fun updateSubtitleStyle(transform: (SubtitleStyleSettings) -> SubtitleStyleSettings) {
        viewModelScope.launch {
            addonPreferencesRepository.updateSubtitleStyle(transform)
        }
    }

    fun adjustSubtitleDelay(deltaMs: Int) {
        updateSubtitleStyle { current ->
            current.copy(delayMs = (current.delayMs + deltaMs).coerceIn(SubtitleStyleSettings.MIN_DELAY_MS, SubtitleStyleSettings.MAX_DELAY_MS))
        }
    }

    fun setSubtitleDelay(delayMs: Int) {
        updateSubtitleStyle { current ->
            current.copy(delayMs = delayMs.coerceIn(SubtitleStyleSettings.MIN_DELAY_MS, SubtitleStyleSettings.MAX_DELAY_MS))
        }
    }

    private fun refreshActiveSubtitleCue(explicitPosMs: Long? = null) {
        if (!externalSubtitleEngine.isEnabled || !externalSubtitleEngine.hasLoadedTrack()) {
            if (_state.value.activeExternalCueText != null) {
                _state.update { it.copy(activeExternalCueText = null) }
            }
            return
        }
        val pos = (explicitPosMs ?: activePlayer?.currentPosition ?: _state.value.currentPositionMs).coerceAtLeast(0L)
        val nextCue = externalSubtitleEngine.currentCueText(pos, _state.value.subtitleStyle.delayMs)
        if (_state.value.activeExternalCueText != nextCue) {
            _state.update { it.copy(activeExternalCueText = nextCue) }
        }
    }

    // -------------------------------------------------------------------------
    // External Audio (الصوت الخارجي) Controls
    // -------------------------------------------------------------------------

    private fun preloadExternalAudioCatalog() {
        externalAudioCatalogRepository.peekCached()?.let { preloaded ->
            if (preloaded.channels.isNotEmpty()) {
                _state.update { current ->
                    current.copy(
                        externalAudio = current.externalAudio.copy(
                            channels = preloaded.channels,
                            lastUpdatedAt = preloaded.updatedAt ?: current.externalAudio.lastUpdatedAt,
                            lastSyncedAtMs = preloaded.lastSyncedAtMs ?: current.externalAudio.lastSyncedAtMs,
                            catalogWarning = preloaded.warning,
                            catalogError = null,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            val cached = externalAudioCatalogRepository.preload()
            if (cached.channels.isNotEmpty()) {
                _state.update { current ->
                    val seeded = if (current.externalAudio.channels.isEmpty()) cached.channels else current.externalAudio.channels
                    current.copy(
                        externalAudio = current.externalAudio.copy(
                            channels = seeded,
                            lastUpdatedAt = cached.updatedAt ?: current.externalAudio.lastUpdatedAt,
                            lastSyncedAtMs = cached.lastSyncedAtMs ?: current.externalAudio.lastSyncedAtMs,
                            catalogWarning = current.externalAudio.catalogWarning ?: cached.warning,
                            catalogError = null,
                        ),
                    )
                }
            }
        }
    }

    fun openExternalAudioSheet() {
        val ramCached = externalAudioCatalogRepository.peekCached()
        _state.update { current ->
            val seededChannels = if (current.externalAudio.channels.isNotEmpty()) {
                current.externalAudio.channels
            } else {
                ramCached?.channels.orEmpty()
            }
            current.copy(
                showTrackSheet = false,
                externalAudio = current.externalAudio.copy(
                    isSheetVisible = true,
                    channels = seededChannels,
                    lastUpdatedAt = ramCached?.updatedAt ?: current.externalAudio.lastUpdatedAt,
                    lastSyncedAtMs = current.externalAudio.lastSyncedAtMs ?: ramCached?.lastSyncedAtMs,
                    catalogError = null,
                ),
            )
        }

        // Local catalog is decoupled from panel network sync:
        // if channels already exist locally, display them immediately without triggering network I/O.
        if (_state.value.externalAudio.channels.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            val diskCached = externalAudioCatalogRepository.preload()
            if (diskCached.channels.isNotEmpty()) {
                _state.update { current ->
                    val seeded = if (current.externalAudio.channels.isEmpty()) {
                        diskCached.channels
                    } else {
                        current.externalAudio.channels
                    }
                    current.copy(
                        externalAudio = current.externalAudio.copy(
                            channels = seeded,
                            lastUpdatedAt = diskCached.updatedAt ?: current.externalAudio.lastUpdatedAt,
                            lastSyncedAtMs = current.externalAudio.lastSyncedAtMs ?: diskCached.lastSyncedAtMs,
                            catalogWarning = diskCached.warning,
                            catalogError = null,
                        ),
                    )
                }
            } else if (_state.value.externalAudio.channels.isEmpty()) {
                // First-time open with no saved local cache yet: perform initial sync automatically.
                syncExternalAudioCatalog(forceRefresh = false)
            }
        }
    }

    fun closeExternalAudioSheet() {
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(isSheetVisible = false))
        }
    }

    fun refreshExternalAudioCatalog() {
        syncExternalAudioCatalog(forceRefresh = true)
    }

    fun syncExternalAudioCatalog(forceRefresh: Boolean = true) {
        // Prevent duplicate concurrent syncs on rapid double-taps.
        if (externalAudioCatalogJob?.isActive == true) {
            return
        }
        val generation = ++externalAudioCatalogGeneration
        externalAudioCatalogJob = viewModelScope.launch {
            var lastEmittedStage: ExternalAudioSyncStage? = null
            try {
                _state.update { current ->
                    current.copy(
                        externalAudio = current.externalAudio.copy(
                            isLoadingCatalog = true,
                            syncStage = ExternalAudioSyncStage.CONNECTING_PANEL,
                            syncStageMessage = ExternalAudioSyncStage.CONNECTING_PANEL.defaultMessageAr,
                            catalogError = null,
                            catalogWarning = null,
                        ),
                    )
                }

                val result = externalAudioCatalogRepository.syncFromPanelWithProgress(
                    forceRefresh = forceRefresh,
                ) { stage ->
                    lastEmittedStage = stage
                    if (externalAudioCatalogGeneration == generation &&
                        stage != ExternalAudioSyncStage.SUCCEEDED &&
                        stage != ExternalAudioSyncStage.FAILED
                    ) {
                        _state.update { current ->
                            current.copy(
                                externalAudio = current.externalAudio.copy(
                                    syncStage = stage,
                                    syncStageMessage = stage.defaultMessageAr,
                                ),
                            )
                        }
                    }
                }

                _state.update { current ->
                    val failed = lastEmittedStage == ExternalAudioSyncStage.FAILED ||
                        result.error != null ||
                        result.channels.isEmpty()
                    val mergedChannels = if (result.channels.isNotEmpty()) {
                        result.channels
                    } else {
                        current.externalAudio.channels
                    }
                    if (!failed) {
                        current.copy(
                            externalAudio = current.externalAudio.copy(
                                channels = mergedChannels,
                                lastUpdatedAt = result.updatedAt ?: current.externalAudio.lastUpdatedAt,
                                lastSyncedAtMs = result.lastSyncedAtMs ?: current.externalAudio.lastSyncedAtMs,
                                syncStage = ExternalAudioSyncStage.SUCCEEDED,
                                syncStageMessage = ExternalAudioSyncStage.SUCCEEDED.defaultMessageAr,
                                catalogWarning = result.warning,
                                catalogError = null,
                            ),
                        )
                    } else {
                        val reason = result.error
                            ?: result.warning
                            ?: ExternalAudioSyncStage.FAILED.defaultMessageAr
                        val failStageMsg = if (reason.startsWith(ExternalAudioSyncStage.FAILED.defaultMessageAr)) {
                            reason
                        } else {
                            "${ExternalAudioSyncStage.FAILED.defaultMessageAr}: $reason"
                        }
                        current.copy(
                            externalAudio = current.externalAudio.copy(
                                channels = mergedChannels,
                                lastUpdatedAt = result.updatedAt ?: current.externalAudio.lastUpdatedAt,
                                lastSyncedAtMs = current.externalAudio.lastSyncedAtMs ?: result.lastSyncedAtMs,
                                syncStage = ExternalAudioSyncStage.FAILED,
                                syncStageMessage = failStageMsg,
                                catalogWarning = result.warning,
                                catalogError = if (mergedChannels.isEmpty()) reason else null,
                            ),
                        )
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update { current ->
                    val hasChannels = current.externalAudio.channels.isNotEmpty()
                    val reason = e.message ?: "تعذّر تحميل قائمة الصوت الخارجي"
                    current.copy(
                        externalAudio = current.externalAudio.copy(
                            syncStage = ExternalAudioSyncStage.FAILED,
                            syncStageMessage = "${ExternalAudioSyncStage.FAILED.defaultMessageAr}: $reason",
                            catalogWarning = if (hasChannels) {
                                "${ExternalAudioSyncStage.FAILED.defaultMessageAr}: $reason — تم الاحتفاظ بالنسخة المحلية"
                            } else {
                                null
                            },
                            catalogError = if (hasChannels) null else reason,
                        ),
                    )
                }
            } finally {
                if (externalAudioCatalogGeneration == generation) {
                    _state.update { current ->
                        current.copy(
                            externalAudio = current.externalAudio.copy(isLoadingCatalog = false),
                        )
                    }
                }
            }
        }
    }

    fun setExternalAudioSearchQuery(query: String) {
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(searchQuery = query))
        }
    }

    fun setExternalAudioBrandFilter(filter: AudioBrandFilter) {
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(selectedBrandFilter = filter))
        }
    }

    fun selectExternalAudioChannel(channel: ExternalAudioChannel) {
        viewModelScope.launch {
            val warning = checkConcurrentConnectionWarning(channel)
            val extState = _state.value.externalAudio
            val videoPos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.currentPositionMs
            val isLive = _state.value.mediaType == MediaType.LIVE
            val isPrimaryPaused = !(activePlayer?.playWhenReady ?: true)

            _state.update {
                it.copy(
                    externalAudio = it.externalAudio.copy(
                        activeChannel = channel,
                        status = ExternalAudioStatus.CONNECTING,
                        statusMessage = "جارٍ الاتصال بـ ${channel.name}...",
                        errorMessage = null,
                        isPcmVerified = false,
                        connectionLimitWarning = warning,
                    ),
                )
            }
            externalAudioController.attach(
                channel = channel,
                initialVideoPositionMs = videoPos,
                isVideoLive = isLive,
                initialOffsetMs = extState.syncOffsetMs,
                volumePercent = extState.volumePercent,
                loudnessBoostEnabled = extState.loudnessBoostEnabled,
                startPaused = isPrimaryPaused,
            )
        }
    }

    fun detachExternalAudio() {
        externalAudioController.detach()
        activePlayer?.let { controller ->
            if (controller.volume != 1f) {
                controller.volume = 1f
            }
        }
        _state.update {
            it.copy(
                externalAudio = it.externalAudio.copy(
                    activeChannel = null,
                    status = ExternalAudioStatus.IDLE,
                    statusMessage = null,
                    errorMessage = null,
                    retryAttempt = 0,
                    isPcmVerified = false,
                    connectionLimitWarning = null,
                ),
            )
        }
    }

    fun setExternalAudioVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(volumePercent = clamped))
        }
        externalAudioController.setVolumePercent(clamped)
    }

    fun toggleExternalAudioLoudnessBoost() {
        val next = !_state.value.externalAudio.loudnessBoostEnabled
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(loudnessBoostEnabled = next))
        }
        externalAudioController.setLoudnessBoostEnabled(next)
    }

    fun adjustExternalAudioOffset(deltaMs: Long) {
        val current = _state.value.externalAudio.syncOffsetMs
        val next = ExternalAudioSyncPolicy.clampOffsetMs(current + deltaMs)
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(syncOffsetMs = next))
        }
        val videoPos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.currentPositionMs
        externalAudioController.applySyncOffset(
            offsetMs = next,
            videoPositionMs = videoPos,
            isVideoLive = _state.value.mediaType == MediaType.LIVE,
        )
    }

    fun resetExternalAudioOffset() {
        _state.update {
            it.copy(externalAudio = it.externalAudio.copy(syncOffsetMs = 0L))
        }
        val videoPos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: _state.value.currentPositionMs
        externalAudioController.applySyncOffset(
            offsetMs = 0L,
            videoPositionMs = videoPos,
            isVideoLive = _state.value.mediaType == MediaType.LIVE,
        )
    }

    /**
     * External-audio channels are imported from the separately configured panel M3U.
     * Hostname equality cannot prove that the audio and video streams share an IPTV
     * account: providers commonly use shared domains/CDNs for independent accounts.
     * Do not warn or block playback based on hostname or the primary subscription's
     * max-connections setting. Let the external provider's actual response determine
     * whether its own account limit has been reached.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun checkConcurrentConnectionWarning(audioChannel: ExternalAudioChannel): String? = null

    fun openTrackSheet() = _state.update {
        it.copy(
            showTrackSheet = true,
            externalSubtitleError = null,
            externalAudio = it.externalAudio.copy(isSheetVisible = false),
        )
    }
    fun closeTrackSheet() = _state.update { it.copy(showTrackSheet = false, externalSubtitleError = null) }

    fun showControls() = _state.update { it.copy(showControls = true) }
    fun hideControls() = _state.update { it.copy(showControls = false) }

    fun stopAndExit() {
        retryJob?.cancel()
        progressJob?.cancel()
        subtitleSyncJob?.cancel()
        epgJob?.cancel()
        detachExternalAudio()
        persistResumePosition()
        activePlayer?.let { controller ->
            controller.volume = 1f
            controller.stop()
            controller.clearMediaItems()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val controller = activePlayer
                if (controller != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _state.update { it.copy(currentPositionMs = pos, durationMs = dur) }
                    if (_state.value.externalAudio.isAttached && controller.isPlaying) {
                        externalAudioController.syncWithVideoPosition(
                            videoPositionMs = pos,
                            isVideoLive = _state.value.mediaType == MediaType.LIVE,
                            videoLiveOffsetMs = controller.currentLiveOffset,
                            forceSeek = false,
                        )
                    }
                    if (tick % 5 == 0 && controller.isPlaying) {
                        persistResumePosition()
                    }
                }
                tick++
                delay(1_000L)
            }
        }
    }

    private fun startSubtitleSyncLoop() {
        subtitleSyncJob?.cancel()
        subtitleSyncJob = viewModelScope.launch {
            while (isActive) {
                val controller = activePlayer
                if (controller != null && externalSubtitleEngine.isEnabled && externalSubtitleEngine.hasLoadedTrack()) {
                    refreshActiveSubtitleCue(controller.currentPosition)
                    delay(if (controller.isPlaying) 100L else 250L)
                } else {
                    delay(250L)
                }
            }
        }
    }

    private fun persistResumePosition() {
        val currentChannel = _state.value.channel ?: return
        if (currentChannel.mediaType == MediaType.LIVE) return
        val pos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: return
        if (pos > 0L) {
            viewModelScope.launch {
                channelRepository.updateResumePosition(currentChannel.id, currentChannel.mediaType, pos)
            }
        }
    }

    override fun onCleared() {
        retryJob?.cancel()
        progressJob?.cancel()
        subtitleSyncJob?.cancel()
        epgJob?.cancel()
        externalAudioController.detach()
        activePlayer?.volume = 1f
        activePlayer?.removeListener(playerListener)
        activePlayer = null
        _boundPlayer.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }
}
