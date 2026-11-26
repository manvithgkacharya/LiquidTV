package com.liquidtv.app.ui.screens.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.player.PlayerViewModel
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: String,
    mediaType: MediaType = MediaType.LIVE,
    onBack: () -> Unit,
    onAmbientMode: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val boundPlayer by viewModel.boundPlayer.collectAsState()
    val colors = AppTheme.colors

    val screenFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    var focusedActionText by remember { mutableStateOf<String?>("تشغيل / إيقاف مؤقت") }
    var interactionCounter by remember { mutableIntStateOf(0) }

    val handleExit = {
        viewModel.stopAndExit()
        onBack()
    }

    BackHandler {
        when {
            uiState.externalAudio.isSheetVisible -> viewModel.closeExternalAudioSheet()
            uiState.showTrackSheet -> viewModel.closeTrackSheet()
            uiState.showControls -> viewModel.hideControls()
            else -> handleExit()
        }
    }

    LaunchedEffect(channelId, mediaType) {
        viewModel.loadItem(channelId, mediaType)
    }

    LaunchedEffect(Unit) {
        try {
            screenFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val anySheetOpen = uiState.showTrackSheet || uiState.externalAudio.isSheetVisible
    LaunchedEffect(uiState.showControls, anySheetOpen, interactionCounter) {
        if (uiState.showControls && !anySheetOpen) {
            delay(6000)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(uiState.showControls, anySheetOpen) {
        if (uiState.showControls && !anySheetOpen) {
            delay(150)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    interactionCounter++
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.showControls && !anySheetOpen) {
                                viewModel.showControls()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!uiState.showControls && uiState.mediaType != MediaType.LIVE) {
                                viewModel.seekBy(5_000L)
                                viewModel.showControls()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!uiState.showControls && uiState.mediaType != MediaType.LIVE) {
                                viewModel.seekBy(-5_000L)
                                viewModel.showControls()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (uiState.isPlaying) viewModel.pause() else viewModel.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            viewModel.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            viewModel.pause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.nextChannel()
                            viewModel.showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.previousChannel()
                            viewModel.showControls()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        // Unified Media3 PlayerView bound cleanly to MediaController
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

        DisposableEffect(playerViewRef, boundPlayer) {
            val view = playerViewRef
            if (view != null) {
                view.player = boundPlayer
            }
            onDispose {
                if (view != null) {
                    view.player = null
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    interactionCounter++
                    if (uiState.showControls) viewModel.hideControls() else viewModel.showControls()
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    player = boundPlayer
                    playerViewRef = this
                }
            },
            update = { view ->
                if (view.player !== boundPlayer) {
                    view.player = boundPlayer
                }
            },
        )

        SubtitleOverlay(
            cueText = uiState.activeExternalCueText,
            style = uiState.subtitleStyle,
            controlsVisible = uiState.showControls,
        )

        // External Audio floating status pill (visible when controls shown or connecting/recovering/error)
        val extAudio = uiState.externalAudio
        if (extAudio.isAttached && !extAudio.isSheetVisible &&
            (uiState.showControls || extAudio.status != ExternalAudioStatus.ACTIVE)
        ) {
            val pillAccent = when (extAudio.status) {
                ExternalAudioStatus.ACTIVE -> colors.success
                ExternalAudioStatus.ERROR -> colors.danger
                else -> colors.accent
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.panel.copy(alpha = 0.88f))
                    .border(1.dp, pillAccent.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .clickable { viewModel.openExternalAudioSheet() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("external_audio_status_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "حالة الصوت الخارجي",
                    tint = pillAccent,
                    modifier = Modifier.size(16.dp),
                )
                val pillText = when (extAudio.status) {
                    ExternalAudioStatus.ACTIVE ->
                        "صوت خارجي: ${extAudio.activeChannel?.name.orEmpty()} (${ExternalAudioSyncPolicy.formatOffsetAr(extAudio.syncOffsetMs)})"
                    ExternalAudioStatus.ERROR ->
                        extAudio.errorMessage ?: "تعذّر تشغيل الصوت الخارجي"
                    else ->
                        extAudio.statusMessage ?: "جارٍ توصيل الصوت الخارجي..."
                }
                Text(
                    text = pillText,
                    style = LiquidTypography.LabelSmall,
                    color = colors.text,
                    maxLines = 1,
                )
            }
        }

        // Buffering / Auto-Reconnecting Indicator
        if ((uiState.isBuffering || uiState.isRetrying) && !uiState.isError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    glassAlpha = 0.88f,
                    borderAlpha = 0.65f,
                    borderColor = colors.border,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = if (uiState.isRetrying) {
                                uiState.errorMessage ?: "جارٍ إعادة الاتصال بالبث…"
                            } else {
                                "جارٍ تحميل البث…"
                            },
                            style = LiquidTypography.TitleMedium,
                            color = colors.text,
                        )
                    }
                }
            }
        }

        // Error Glass Overlay
        if (uiState.isError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    modifier = Modifier.widthIn(min = 300.dp, max = 520.dp).padding(20.dp),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.94f,
                    glassColor = colors.panel,
                    borderAlpha = 0.85f,
                    borderColor = colors.danger,
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "مشكلة في التشغيل",
                            style = LiquidTypography.TitleLarge,
                            color = colors.text,
                        )
                        Text(
                            text = uiState.errorMessage ?: "رابط البث غير متاح أو تعذر الوصول إليه.",
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            HighVisButton(
                                icon = Icons.Filled.Refresh,
                                text = "إعادة المحاولة",
                                onClick = { viewModel.retry() },
                            )
                            HighVisButton(
                                icon = Icons.Filled.SkipNext,
                                text = "التالي",
                                onClick = { viewModel.nextChannel() },
                            )
                            HighVisButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                text = "خروج",
                                onClick = handleExit,
                            )
                        }
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(animationSpec = spring(stiffness = 500f)),
            exit = fadeOut(animationSpec = spring(stiffness = 500f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                // Bottom Dock
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // نص الزر المحدد (يظهر فوق شريط الوقت)
                    focusedActionText?.let { actionText ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = colors.panel.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .border(1.dp, colors.focus.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = actionText,
                                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = colors.text,
                            )
                        }
                    }

                    // شريط الوقت (VOD فقط) - يطابق عرض شريط الأدوات
                    if (uiState.mediaType != MediaType.LIVE && uiState.durationMs > 0L) {
                        var sliderDrag by remember { mutableStateOf<Float?>(null) }
                        val shownPosition = sliderDrag?.toLong() ?: uiState.currentPositionMs

                        Row(
                            modifier = Modifier
                                .widthIn(max = 620.dp)
                                .fillMaxWidth()
                                .background(colors.panel.copy(alpha = 0.84f), RoundedCornerShape(16.dp))
                                .border(1.dp, colors.border.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = formatDuration(shownPosition),
                                style = LiquidTypography.LabelSmall,
                                color = colors.text,
                            )
                            Slider(
                                value = (sliderDrag ?: uiState.currentPositionMs.toFloat()),
                                onValueChange = { sliderDrag = it },
                                onValueChangeFinished = {
                                    sliderDrag?.let { viewModel.seekTo(it.toLong()) }
                                    sliderDrag = null
                                },
                                valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent,
                                    inactiveTrackColor = colors.border.copy(alpha = 0.45f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatDuration(uiState.durationMs),
                                style = LiquidTypography.LabelSmall,
                                color = colors.text,
                            )
                        }
                    }

                    // شريط الأدوات (مباشرة تحت شريط الوقت)
                    LiquidGlassSurface(
                        modifier = Modifier.wrapContentWidth(),
                        cornerRadius = 40.dp,
                        glassAlpha = 0.88f,
                        borderAlpha = 0.65f,
                        borderColor = colors.border,
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PlayerControlButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                description = "رجوع إلى القائمة",
                                onFocused = { focusedActionText = "رجوع" },
                                onClick = handleExit,
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.SkipPrevious,
                                description = "السابق",
                                onFocused = { focusedActionText = "السابق" },
                                onClick = {
                                    viewModel.previousChannel()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                description = if (uiState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                                isPrimary = true,
                                focusRequester = playPauseFocusRequester,
                                onFocused = {
                                    focusedActionText = if (uiState.isPlaying) "إيقاف مؤقت" else "تشغيل"
                                },
                                onClick = {
                                    if (uiState.isPlaying) viewModel.pause() else viewModel.play()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.SkipNext,
                                description = "التالي",
                                onFocused = { focusedActionText = "التالي" },
                                onClick = {
                                    viewModel.nextChannel()
                                    interactionCounter++
                                },
                            )

                            PlayerControlButton(
                                icon = Icons.Filled.Subtitles,
                                description = "الصوت والترجمة",
                                onFocused = { focusedActionText = "الصوت والترجمة" },
                                onClick = {
                                    viewModel.openTrackSheet()
                                    interactionCounter++
                                },
                                modifier = Modifier.testTag("player_tracks_button"),
                            )
                            val extActiveTint = when (uiState.externalAudio.status) {
                                ExternalAudioStatus.ACTIVE -> colors.success
                                ExternalAudioStatus.CONNECTING,
                                ExternalAudioStatus.BUFFERING,
                                ExternalAudioStatus.RECOVERING -> colors.accent
                                ExternalAudioStatus.ERROR -> colors.danger
                                else -> colors.text
                            }
                            PlayerControlButton(
                                icon = Icons.Filled.GraphicEq,
                                description = "الصوت الخارجي",
                                iconTint = extActiveTint,
                                onFocused = { focusedActionText = "الصوت الخارجي" },
                                onClick = {
                                    viewModel.openExternalAudioSheet()
                                    interactionCounter++
                                },
                                modifier = Modifier.testTag("player_external_audio_button"),
                            )

                            if (uiState.mediaType != MediaType.SERIES_EPISODE) {
                                PlayerControlButton(
                                    icon = if (uiState.channel?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    description = if (uiState.channel?.isFavorite == true) "إزالة من المفضلة" else "إضافة للمفضلة",
                                    iconTint = if (uiState.channel?.isFavorite == true) colors.danger else colors.text,
                                    onFocused = {
                                        focusedActionText = if (uiState.channel?.isFavorite == true) "إزالة من المفضلة" else "إضافة للمفضلة"
                                    },
                                    onClick = {
                                        viewModel.toggleFavorite()
                                        interactionCounter++
                                    },
                                )
                            }

                            PlayerControlButton(
                                icon = Icons.Filled.NightlightRound,
                                description = "الوضع المحيطي",
                                onFocused = { focusedActionText = "الوضع المحيطي" },
                                onClick = onAmbientMode,
                            )
                        }
                    }
                }
            }
        }

        // Audio, Embedded Subtitles, Online Stremio Search & Subtitle Appearance Dialog
        if (uiState.showTrackSheet) {
            PlayerSubtitlesAndTracksSheet(
                uiState = uiState,
                onSelectAudio = { viewModel.selectAudioTrack(it) },
                onSelectSubtitle = { viewModel.selectSubtitleTrack(it) },
                onToggleExternalSubtitles = { viewModel.toggleExternalSubtitles() },
                onLoadExternalSubtitleUrl = { url, lang -> viewModel.loadExternalSubtitle(url, lang) },
                onPickLocalSubtitleUri = { uri -> viewModel.applyLocalSubtitleUri(uri) },
                onSelectSearchLanguage = { lang -> viewModel.setSubtitleSearchLanguage(lang) },
                onSearchOnlineSubtitles = { viewModel.searchOnlineSubtitles() },
                onApplyOnlineSubtitle = { res -> viewModel.applyOnlineSubtitle(res) },
                onUpdateStyle = { transform -> viewModel.updateSubtitleStyle(transform) },
                onAdjustDelay = { delta -> viewModel.adjustSubtitleDelay(delta) },
                onSetDelay = { delayMs -> viewModel.setSubtitleDelay(delayMs) },
                onClose = { viewModel.closeTrackSheet() },
            )
        }

        // External Audio Sheet (beIN SPORTS / ALWAN)
        if (uiState.externalAudio.isSheetVisible) {
            ExternalAudioSheet(
                state = uiState.externalAudio,
                onSelectChannel = { viewModel.selectExternalAudioChannel(it) },
                onDetachAudio = { viewModel.detachExternalAudio() },
                onRefreshCatalog = { viewModel.syncExternalAudioCatalog() },
                onSearchQueryChange = { viewModel.setExternalAudioSearchQuery(it) },
                onSelectBrandFilter = { viewModel.setExternalAudioBrandFilter(it) },
                onVolumeChange = { viewModel.setExternalAudioVolume(it) },
                onToggleLoudnessBoost = { viewModel.toggleExternalAudioLoudnessBoost() },
                onAdjustSyncOffset = { viewModel.adjustExternalAudioOffset(it) },
                onResetSyncOffset = { viewModel.resetExternalAudioOffset() },
                onClose = { viewModel.closeExternalAudioSheet() },
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    description: String,
    isPrimary: Boolean = false,
    iconTint: Color = Color.Unspecified,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val resolvedTint = if (iconTint != Color.Unspecified) iconTint else colors.text
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "btnScale",
    )

    val buttonSize = if (isPrimary) 46.dp else 40.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .then(
                if (isFocused) {
                    Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(colors.accent, colors.accentSecondary)
                            )
                        )
                        .border(BorderStroke(3.5.dp, colors.focus), CircleShape)
                } else if (isPrimary) {
                    Modifier
                        .background(colors.accent.copy(alpha = 0.90f))
                        .border(BorderStroke(1.5.dp, colors.focus.copy(alpha = 0.55f)), CircleShape)
                } else {
                    Modifier
                        .background(colors.surface.copy(alpha = 0.88f))
                        .border(BorderStroke(1.dp, colors.border.copy(alpha = 0.65f)), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused || isPrimary) colors.onAccent else resolvedTint,
            modifier = Modifier.size(if (isPrimary) 24.dp else 20.dp),
        )
    }
}

@Composable
private fun HighVisButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isFocused) {
                    Modifier
                        .background(colors.accent)
                        .border(BorderStroke(3.dp, colors.focus), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                        .background(colors.surface)
                        .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(20.dp))
                }
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fg = if (isFocused) colors.onAccent else colors.text
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                color = fg,
            )
        }
    }
}
