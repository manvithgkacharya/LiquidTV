package com.liquidtv.app.ui.components

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.liquidtv.app.R
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Total duration of the SONIX IPTV cinematic startup sequence (ms).
 */
const val SONIX_SPLASH_TOTAL_DURATION_MS = 5_000L

private const val STAGE_HOLD_CHECK_PROGRESS = 0.90f // 4.5s mark: hold peacefully if startup checks are still pending

private val CinemaEaseOut = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
private val CinemaEaseInOut = CubicBezierEasing(0.42f, 0.0f, 0.22f, 1.0f)

private val SplashObsidianBlack = Color(0xFF030307)
private val BrandPurpleGlow = Color(0xFF7B3FE4)
private val BrandBlueGlow = Color(0xFF2E6EF7)

/**
 * Computed per-frame visual state for [SonixSplashOverlay].
 * Exposed for deterministic unit verification of choreography milestones.
 */
@VisibleForTesting
internal data class SplashChoreographyFrame(
    val backdropAlpha: Float,
    val ambientGlowAlpha: Float,
    val emblemAlpha: Float,
    val emblemScale: Float,
    val emblemCenterFraction: Float,
    val sheenProgress: Float,
    val sonixAlpha: Float,
    val sonixTranslationFraction: Float,
    val iptvAlpha: Float,
    val iptvTranslationFraction: Float,
    val overallExitAlpha: Float,
)

@VisibleForTesting
internal fun computeSplashChoreographyFrame(progress: Float): SplashChoreographyFrame {
    val t = progress.coerceIn(0f, 1f)

    // Stage 1 (0.0s - 0.5s -> 0.00..0.10): black canvas + soft ambient glow fade-in
    val backdropIn = CinemaEaseOut.transform((t / 0.10f).coerceIn(0f, 1f))
    val glowIn = CinemaEaseOut.transform((t / 0.16f).coerceIn(0f, 1f))

    // Stage 2 (0.5s - 1.8s -> 0.10..0.36): Emblem fades/scales in first, initially dead-center
    val emblemRaw = ((t - 0.10f) / 0.24f).coerceIn(0f, 1f)
    val emblemEased = CinemaEaseOut.transform(emblemRaw)
    val emblemAlpha = emblemEased
    val emblemScale = 0.86f + (0.14f * emblemEased)

    // Diagonal light sheen sweeps once across the emblem cleanly between 0.85s and 1.75s (0.17..0.35)
    val sheenProgress = if (t in 0.17f..0.35f) {
        CinemaEaseInOut.transform((t - 0.17f) / 0.18f)
    } else {
        -1f
    }

    // Emblem glides from dead-center to its authentic left lockup slot between 1.65s and 2.35s (0.33..0.47)
    val glideT = CinemaEaseInOut.transform(((t - 0.33f) / 0.14f).coerceIn(0f, 1f))
    val emblemCenterFraction = 1f - glideT

    // Stage 3a (1.8s - 2.45s -> 0.36..0.49): "SONIX" wordmark reveals right after emblem stabilizes
    val sonixRaw = ((t - 0.36f) / 0.13f).coerceIn(0f, 1f)
    val sonixEased = CinemaEaseOut.transform(sonixRaw)

    // Stage 3b (2.2s - 2.8s -> 0.44..0.56): "IPTV" submark reveals underneath "SONIX"
    val iptvRaw = ((t - 0.44f) / 0.12f).coerceIn(0f, 1f)
    val iptvEased = CinemaEaseOut.transform(iptvRaw)

    // Ambient glow settles softly when the full lockup rests (2.8s - 4.5s -> 0.56..0.90)
    val revealBoost = if (t in 0.10f..0.56f) {
        val peak = 1f - kotlin.math.abs((t - 0.33f) / 0.23f).coerceIn(0f, 1f)
        0.18f * peak
    } else {
        0f
    }
    val ambientGlowAlpha = (glowIn * (0.72f + revealBoost)).coerceIn(0f, 1f)

    // Stage 5 (4.5s - 5.0s -> 0.90..1.00): smooth fade out into destination
    val exitRaw = ((t - STAGE_HOLD_CHECK_PROGRESS) / (1f - STAGE_HOLD_CHECK_PROGRESS)).coerceIn(0f, 1f)
    val overallExitAlpha = 1f - CinemaEaseInOut.transform(exitRaw)

    return SplashChoreographyFrame(
        backdropAlpha = backdropIn,
        ambientGlowAlpha = ambientGlowAlpha,
        emblemAlpha = emblemAlpha,
        emblemScale = emblemScale,
        emblemCenterFraction = emblemCenterFraction,
        sheenProgress = sheenProgress,
        sonixAlpha = sonixEased,
        sonixTranslationFraction = 1f - sonixEased,
        iptvAlpha = iptvEased,
        iptvTranslationFraction = 1f - iptvEased,
        overallExitAlpha = overallExitAlpha,
    )
}

/**
 * 5-second cinematic SONIX IPTV splash overlay reusing the authentic bundled logo
 * ([R.drawable.liquid_logo_horizontal]) split cleanly into Emblem, "SONIX", and "IPTV" layers.
 */
@Composable
fun SonixSplashOverlay(
    isAppReady: Boolean,
    initialProgress: Float = 0f,
    totalDurationMs: Long = SONIX_SPLASH_TOTAL_DURATION_MS,
    onProgressChanged: (Float) -> Unit = {},
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestAppReady by rememberUpdatedState(isAppReady)
    val latestOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val latestOnSplashFinished by rememberUpdatedState(onSplashFinished)

    val safeDurationMs = totalDurationMs.coerceAtLeast(500L)
    val startProgress = initialProgress.coerceIn(0f, 0.98f)
    val timeline = remember(startProgress) { Animatable(startProgress) }
    var currentProgress by remember(startProgress) { mutableFloatStateOf(startProgress) }

    LaunchedEffect(safeDurationMs, startProgress) {
        if (timeline.value < STAGE_HOLD_CHECK_PROGRESS) {
            val remainingPreExitMs =
                ((STAGE_HOLD_CHECK_PROGRESS - timeline.value) * safeDurationMs).roundToInt().coerceAtLeast(1)
            timeline.animateTo(
                targetValue = STAGE_HOLD_CHECK_PROGRESS,
                animationSpec = tween(durationMillis = remainingPreExitMs, easing = LinearEasing),
            ) {
                currentProgress = value
                latestOnProgressChanged(value)
            }
        }

        // If subscription/device checks take longer than 4.5s, hold calmly at the settled complete logo.
        if (!latestAppReady) {
            snapshotFlow { latestAppReady }.first { it }
        }

        if (timeline.value < 1f) {
            val remainingExitMs =
                ((1f - timeline.value) * safeDurationMs).roundToInt().coerceAtLeast(160)
            timeline.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = remainingExitMs, easing = LinearEasing),
            ) {
                currentProgress = value
                latestOnProgressChanged(value)
            }
        }
        latestOnSplashFinished()
    }

    val frame = computeSplashChoreographyFrame(currentProgress)
    val logoPainter = painterResource(id = R.drawable.liquid_logo_horizontal)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = frame.overallExitAlpha }
            .background(SplashObsidianBlack.copy(alpha = 0.94f + 0.06f * frame.backdropAlpha))
            .onPreviewKeyEvent { currentProgress < STAGE_HOLD_CHECK_PROGRESS }
            .semantics { contentDescription = "SONIX IPTV" }
            .testTag("sonix_splash_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle brand purple & blue radial ambient glow backdrop (zero continuous loop)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val glowAlpha = frame.ambientGlowAlpha
            if (glowAlpha > 0.005f) {
                val purpleCenter = Offset(
                    x = size.width * (0.5f - 0.08f * (1f - frame.emblemCenterFraction)),
                    y = size.height * 0.46f,
                )
                val blueCenter = Offset(
                    x = size.width * (0.5f + 0.12f * (1f - frame.emblemCenterFraction)),
                    y = size.height * 0.56f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BrandPurpleGlow.copy(alpha = 0.24f * glowAlpha),
                            BrandPurpleGlow.copy(alpha = 0.08f * glowAlpha),
                            Color.Transparent,
                        ),
                        center = purpleCenter,
                        radius = minDim * 0.62f,
                    ),
                    radius = minDim * 0.62f,
                    center = purpleCenter,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BrandBlueGlow.copy(alpha = 0.20f * glowAlpha),
                            BrandBlueGlow.copy(alpha = 0.06f * glowAlpha),
                            Color.Transparent,
                        ),
                        center = blueCenter,
                        radius = minDim * 0.58f,
                    ),
                    radius = minDim * 0.58f,
                    center = blueCenter,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val targetWidth = (maxWidth * 0.58f).coerceIn(240.dp, 490.dp)
            val useUnifiedLockup = currentProgress >= 0.56f

            Box(
                modifier = Modifier
                    .widthIn(max = targetWidth)
                    .aspectRatio(1920f / 1078f)
                    .testTag("sonix_splash_logo"),
                contentAlignment = Alignment.Center,
            ) {
                if (useUnifiedLockup) {
                    Image(
                        painter = logoPainter,
                        contentDescription = "SONIX IPTV",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    // Layer 1: Authentic Hexagon + Wave Emblem reveals first (centered, then glides into lockup)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = frame.emblemAlpha
                                scaleX = frame.emblemScale
                                scaleY = frame.emblemScale
                                translationX = size.width * 0.235f * frame.emblemCenterFraction
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                val emblemMask = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width * 0.478f, 0f)
                                    lineTo(size.width * 0.478f, size.height * 0.452f)
                                    lineTo(size.width * 0.455f, size.height * 0.480f)
                                    lineTo(size.width * 0.432f, size.height * 0.545f)
                                    lineTo(size.width * 0.432f, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                clipPath(emblemMask) {
                                    this@drawWithContent.drawContent()
                                    if (frame.sheenProgress in 0f..1f) {
                                        val sweepX = size.width * (-0.10f + 0.68f * frame.sheenProgress)
                                        val bandW = size.width * 0.14f
                                        drawRect(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.White.copy(alpha = 0.22f),
                                                    Color.Transparent,
                                                ),
                                                start = Offset(sweepX - bandW, 0f),
                                                end = Offset(sweepX + bandW, size.height),
                                            ),
                                            blendMode = BlendMode.SrcAtop,
                                        )
                                    }
                                }
                            }
                            .testTag("sonix_splash_emblem_layer"),
                    ) {
                        Image(
                            painter = logoPainter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    // Layer 2: "SONIX" wordmark reveals cleanly after the emblem stabilizes
                    if (frame.sonixAlpha > 0.002f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = frame.sonixAlpha
                                    translationX = size.width * 0.035f * frame.sonixTranslationFraction
                                    val s = 0.96f + 0.04f * frame.sonixAlpha
                                    scaleX = s
                                    scaleY = s
                                }
                                .drawWithContent {
                                    val sonixMask = Path().apply {
                                        moveTo(size.width * 0.478f, size.height * 0.445f)
                                        lineTo(size.width, size.height * 0.445f)
                                        lineTo(size.width, size.height * 0.662f)
                                        lineTo(size.width * 0.432f, size.height * 0.662f)
                                        lineTo(size.width * 0.432f, size.height * 0.545f)
                                        lineTo(size.width * 0.455f, size.height * 0.480f)
                                        close()
                                    }
                                    clipPath(sonixMask) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                                .testTag("sonix_splash_wordmark_layer"),
                        ) {
                            Image(
                                painter = logoPainter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    // Layer 3: "IPTV" submark reveals underneath "SONIX"
                    if (frame.iptvAlpha > 0.002f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = frame.iptvAlpha
                                    translationY = size.height * 0.025f * frame.iptvTranslationFraction
                                }
                                .drawWithContent {
                                    val iptvMask = Path().apply {
                                        moveTo(size.width * 0.432f, size.height * 0.662f)
                                        lineTo(size.width, size.height * 0.662f)
                                        lineTo(size.width, size.height)
                                        lineTo(size.width * 0.432f, size.height)
                                        close()
                                    }
                                    clipPath(iptvMask) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                                .testTag("sonix_splash_iptv_layer"),
                        ) {
                            Image(
                                painter = logoPainter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }
    }
}
