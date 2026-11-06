package com.liquidtv.app.ui.screens.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.liquidtv.app.player.PlayerViewModel
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: String,
    onBack: () -> Unit,
    onAmbientMode: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()

    val screenFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    var focusedActionText by remember { mutableStateOf<String?>("Play / Pause") }
    var interactionCounter by remember { mutableIntStateOf(0) }

    // Intercept back key: hide controls if visible, else exit player
    BackHandler {
        if (uiState.showControls) {
            viewModel.hideControls()
        } else {
            onBack()
        }
    }

    LaunchedEffect(channelId) {
        viewModel.loadChannel(channelId)
    }

    LaunchedEffect(Unit) {
        try {
            screenFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Auto-hide controls after 6 seconds of no user interaction
    LaunchedEffect(uiState.showControls, interactionCounter) {
        if (uiState.showControls) {
            delay(6000)
            viewModel.hideControls()
        }
    }

    // When controls become visible, shift focus to Play/Pause button
    LaunchedEffect(uiState.showControls) {
        if (uiState.showControls) {
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
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!uiState.showControls) {
                                viewModel.showControls()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.showControls) {
                                viewModel.showControls()
                                true
                            } else {
                                false
                            }
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
        // Fullscreen ExoPlayer AndroidView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                    player = viewModel.exoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player == null) {
                    playerView.player = viewModel.exoPlayer
                }
            },
        )

        // Buffering Indicator
        if (uiState.isBuffering && !uiState.isError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    glassAlpha = 0.28f,
                    borderAlpha = 0.5f,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = LiquidColors.AccentBlue,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = "Buffering Stream…",
                            style = LiquidTypography.TitleMedium,
                            color = LiquidColors.TextPrimary,
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
                    modifier = Modifier.width(520.dp),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.35f,
                    glassColor = Color(0xFF1E0A0A),
                    borderAlpha = 0.6f,
                    borderColor = LiquidColors.ErrorRed,
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = LiquidColors.WarningAmber,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Playback Issue",
                            style = LiquidTypography.TitleLarge,
                            color = LiquidColors.TextPrimary,
                        )
                        Text(
                            text = uiState.errorMessage ?: "Stream URL is offline or geo-restricted.",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HighVisButton(
                                icon = Icons.Filled.Refresh,
                                text = "Retry Stream",
                                onClick = { viewModel.retry() },
                            )
                            HighVisButton(
                                icon = Icons.Filled.SkipNext,
                                text = "Next Channel",
                                onClick = { viewModel.nextChannel() },
                            )
                        }
                    }
                }
            }
        }

        // Overlay Controls (Top Info & Bottom Control Bar)
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(animationSpec = spring(stiffness = 500f)),
            exit = fadeOut(animationSpec = spring(stiffness = 500f)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Channel Info Bar
                uiState.channel?.let { channel ->
                    LiquidGlassSurface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 36.dp, top = 36.dp)
                            .wrapContentWidth(),
                        cornerRadius = 20.dp,
                        glassAlpha = 0.35f,
                        borderAlpha = 0.45f,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (channel.logoUrl != null) {
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = channel.name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = channel.name,
                                        style = LiquidTypography.TitleMedium,
                                        color = LiquidColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    LiveBadge()
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    channel.groupTitle?.let {
                                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlue)
                                    }
                                    channel.country?.let {
                                        Text("• $it", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                                    }
                                    channel.language?.let {
                                        Text("• $it", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Floating Control Bar with High-Visibility Focus
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Action Label Tooltip (Tells TV user exactly what the focused button does)
                    focusedActionText?.let { actionText ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xDD000000),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .border(1.dp, LiquidColors.AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = actionText,
                                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = Color.White,
                            )
                        }
                    }

                    // Floating Glass Dock
                    LiquidGlassSurface(
                        modifier = Modifier.wrapContentWidth(),
                        cornerRadius = 40.dp,
                        glassAlpha = 0.38f,
                        borderAlpha = 0.50f,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Back to Browse
                            PlayerControlButton(
                                icon = Icons.Filled.ArrowBack,
                                description = "Back to Menu",
                                onFocused = { focusedActionText = "← Back to Menu" },
                                onClick = onBack,
                            )

                            // Previous Channel
                            PlayerControlButton(
                                icon = Icons.Filled.SkipPrevious,
                                description = "Previous Channel",
                                onFocused = { focusedActionText = "⏮ Previous Channel" },
                                onClick = {
                                    viewModel.previousChannel()
                                    interactionCounter++
                                },
                            )

                            // Play / Pause (Primary focus receiver)
                            PlayerControlButton(
                                icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                description = if (uiState.isPlaying) "Pause" else "Play",
                                isPrimary = true,
                                focusRequester = playPauseFocusRequester,
                                onFocused = {
                                    focusedActionText = if (uiState.isPlaying) "⏸ Pause" else "▶ Play"
                                },
                                onClick = {
                                    if (uiState.isPlaying) viewModel.pause() else viewModel.play()
                                    interactionCounter++
                                },
                            )

                            // Next Channel
                            PlayerControlButton(
                                icon = Icons.Filled.SkipNext,
                                description = "Next Channel",
                                onFocused = { focusedActionText = "⏭ Next Channel" },
                                onClick = {
                                    viewModel.nextChannel()
                                    interactionCounter++
                                },
                            )

                            // Favorite Toggle
                            PlayerControlButton(
                                icon = if (uiState.channel?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                description = if (uiState.channel?.isFavorite == true) "Remove from Favorites" else "Add to Favorites",
                                iconTint = if (uiState.channel?.isFavorite == true) LiquidColors.LiveRed else LiquidColors.TextPrimary,
                                onFocused = {
                                    focusedActionText = if (uiState.channel?.isFavorite == true) "❤️ Remove from Favorites" else "🤍 Add to Favorites"
                                },
                                onClick = {
                                    viewModel.toggleFavorite()
                                    interactionCounter++
                                },
                            )

                            // Ambient mode shortcut
                            PlayerControlButton(
                                icon = Icons.Filled.NightlightRound,
                                description = "Ambient Mode",
                                onFocused = { focusedActionText = "⛶ Ambient Mode" },
                                onClick = onAmbientMode,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-visibility TV Remote Button with an unmissable focus state (3.5dp bright white ring + AccentBlue background).
 */
@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    description: String,
    isPrimary: Boolean = false,
    iconTint: Color = LiquidColors.TextPrimary,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "btnScale",
    )

    val buttonSize = if (isPrimary) 56.dp else 46.dp

    Box(
        modifier = Modifier
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
                                colors = listOf(LiquidColors.AccentBlue, LiquidColors.AccentBlueLight)
                            )
                        )
                        .border(BorderStroke(3.5.dp, Color.White), CircleShape)
                } else if (isPrimary) {
                    Modifier
                        .background(LiquidColors.AccentBlue.copy(alpha = 0.85f))
                        .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused) Color.White else if (isPrimary) Color.White else iconTint,
            modifier = Modifier.size(if (isPrimary) 28.dp else 22.dp),
        )
    }
}

@Composable
private fun HighVisButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isFocused) {
                    Modifier
                        .background(LiquidColors.AccentBlue)
                        .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
                }
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}
