package com.liquidtv.app.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val channel: Channel? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val showControls: Boolean = true,
    val allChannels: List<Channel> = emptyList(),
    val currentChannelIndex: Int = -1,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING,
                isPlaying = exoPlayer.isPlaying,
                isError = playbackState == Player.STATE_IDLE && exoPlayer.playerError != null,
            ) }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(
                isError = true,
                isBuffering = false,
                errorMessage = "Stream temporarily unavailable.\n(${error.localizedMessage ?: error.errorCodeName})",
            ) }
        }
    }

    // Initialize ExoPlayer immediately so PlayerView in Compose is guaranteed to bind to it
    val exoPlayer: ExoPlayer = run {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("LiquidTV/1.0 (Linux; Android TV; ExoPlayer)")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                player.addListener(playerListener)
            }
    }

    init {
        viewModelScope.launch {
            channelRepository.getAllChannels().collect { channels ->
                val currentId = _state.value.channel?.id
                val index = channels.indexOfFirst { it.id == currentId }
                _state.update { it.copy(
                    allChannels = channels,
                    currentChannelIndex = if (index >= 0) index else it.currentChannelIndex,
                ) }
            }
        }
    }

    fun initializePlayer(context: Context) {
        // Player already initialized eagerly
    }

    fun loadChannel(channelId: String) {
        viewModelScope.launch {
            val channel = channelRepository.getChannelById(channelId) ?: return@launch
            val allChannels = _state.value.allChannels
            val index = allChannels.indexOfFirst { it.id == channelId }

            _state.update { it.copy(
                channel = channel,
                currentChannelIndex = index,
                isError = false,
                isBuffering = true,
                errorMessage = null,
                showControls = true,
            ) }

            playStream(channel.streamUrl)
            channelRepository.recordWatch(channelId)
        }
    }

    private fun playStream(url: String) {
        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        val lowerUrl = url.lowercase()
        when {
            lowerUrl.contains(".m3u8") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            lowerUrl.contains(".mpd") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            lowerUrl.contains(".ism") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
            }
            lowerUrl.contains(".ts") -> {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            }
            // If unknown or direct MP4/stream, let ExoPlayer auto-detect format
        }

        val mediaItem = mediaItemBuilder.build()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun play() { exoPlayer.play() }
    fun pause() { exoPlayer.pause() }

    fun nextChannel() {
        val channels = _state.value.allChannels
        if (channels.isEmpty()) return
        val index = _state.value.currentChannelIndex
        val next = (index + 1) % channels.size
        loadChannel(channels[next].id)
    }

    fun previousChannel() {
        val channels = _state.value.allChannels
        if (channels.isEmpty()) return
        val index = _state.value.currentChannelIndex
        val prev = if (index <= 0) channels.size - 1 else index - 1
        loadChannel(channels[prev].id)
    }

    fun toggleFavorite() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            channelRepository.toggleFavorite(channel.id)
            val updated = channelRepository.getChannelById(channel.id)
            _state.update { it.copy(channel = updated ?: it.channel) }
        }
    }

    fun retry() {
        val channel = _state.value.channel ?: return
        _state.update { it.copy(isError = false, isBuffering = true, errorMessage = null) }
        playStream(channel.streamUrl)
    }

    fun showControls() = _state.update { it.copy(showControls = true) }
    fun hideControls() = _state.update { it.copy(showControls = false) }

    override fun onCleared() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }
}
