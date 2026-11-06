package com.liquidtv.app.ui.screens.ambient

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.ambient.AmbientViewModel
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientScreen(
    onExit: () -> Unit,
    viewModel: AmbientViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    BackHandler {
        onExit()
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Time tracking
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormat.format(Calendar.getInstance().time)) }
    var currentDate by remember { mutableStateOf(dateFormat.format(Calendar.getInstance().time)) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance().time
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            kotlinx.coroutines.delay(10_000)
        }
    }

    // Infinite ambient motion for floating glass spheres
    val infiniteTransition = rememberInfiniteTransition(label = "ambientMotion")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase1",
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(38000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase2",
    )

    // OLED Burn-in pixel shift (shifts clock and UI subtly every minute)
    val burnInShiftX = (sin(phase1.toDouble()) * 18.0).dp
    val burnInShiftY = (cos(phase2.toDouble()) * 14.0).dp

    // Theme color palette
    val (startColor, midColor, accentOrbColor) = when (uiState.theme.lowercase()) {
        "aurora" -> Triple(
            LiquidColors.AmbientAuroraStart,
            Color(0xFF04292B),
            LiquidColors.AmbientAuroraGreen,
        )
        "ocean" -> Triple(
            LiquidColors.AmbientOceanStart,
            Color(0xFF001F3F),
            LiquidColors.AmbientOceanBlue,
        )
        "cosmic" -> Triple(
            LiquidColors.AmbientCosmicStart,
            Color(0xFF1E0B36),
            LiquidColors.AmbientCosmicPurple,
        )
        "minimal" -> Triple(
            LiquidColors.AmbientMinimalStart,
            Color(0xFF000000),
            Color(0xFF222222),
        )
        else -> Triple(
            LiquidColors.AmbientMidnightStart,
            Color(0xFF0A1020),
            LiquidColors.AccentBlueDark,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onExit()
                    true
                } else {
                    false
                }
            },
    ) {
        // Deep Ambient Radial Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(midColor, startColor, Color.Black),
                        center = Offset(900f + sin(phase1) * 200f, 500f + cos(phase2) * 150f),
                        radius = 1200f,
                    )
                ),
        )

        // Slow Floating Glass Orb 1
        Box(
            modifier = Modifier
                .offset(
                    x = (200f + cos(phase1) * 180f).dp,
                    y = (150f + sin(phase2) * 120f).dp,
                )
                .size(320.dp)
                .alpha(0.35f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentOrbColor.copy(alpha = 0.5f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        // Slow Floating Glass Orb 2
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = (-160f + sin(phase2) * 140f).dp,
                    y = (-100f + cos(phase1) * 100f).dp,
                )
                .size(400.dp)
                .alpha(0.28f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(LiquidColors.AccentViolet.copy(alpha = 0.45f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        // Floating Favorite Channel Artworks (if available)
        if (uiState.favoriteChannels.isNotEmpty()) {
            val featured = uiState.favoriteChannels.firstOrNull { it.logoUrl != null }
            if (featured != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(
                            x = (-120f + sin(phase1) * 40f).dp,
                            y = (cos(phase2) * 30f).dp,
                        )
                        .alpha(0.45f),
                ) {
                    LiquidGlassSurface(
                        cornerRadius = 24.dp,
                        glassAlpha = 0.15f,
                        borderAlpha = 0.25f,
                    ) {
                        AsyncImage(
                            model = featured.logoUrl,
                            contentDescription = featured.name,
                            modifier = Modifier
                                .size(160.dp)
                                .padding(24.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        // Minimalist Clock & Date Panel (Shifted for OLED burn-in prevention)
        if (uiState.clockVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 64.dp, bottom = 64.dp)
                    .offset(x = burnInShiftX, y = burnInShiftY),
            ) {
                Text(
                    text = currentTime,
                    style = LiquidTypography.ClockLarge,
                    color = Color.White.copy(alpha = 0.88f),
                )
                Text(
                    text = currentDate,
                    style = LiquidTypography.ClockSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        // Exit Hint Pill at Top Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(36.dp),
        ) {
            LiquidGlassSurface(
                cornerRadius = 20.dp,
                glassAlpha = 0.10f,
                borderAlpha = 0.15f,
            ) {
                Text(
                    text = "Press any button to resume",
                    style = LiquidTypography.LabelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

