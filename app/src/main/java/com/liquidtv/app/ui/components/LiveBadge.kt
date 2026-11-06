package com.liquidtv.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

// ─────────────────────────────────────────────────────────────────────────────
// LiveBadge
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pulsing red LIVE indicator pill badge.
 *
 * Contains a small blinking dot and "LIVE" text.  The dot pulses between
 * full and reduced alpha via an [InfiniteTransition].
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveDotAlpha",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        LiquidColors.LiveRed.copy(alpha = 0.92f),
                        Color(0xFFFF2D55).copy(alpha = 0.88f),
                    )
                )
            )
            .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Pulsing dot with subtle glow
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha)),
            )

            Text(
                text = "LIVE",
                style = LiquidTypography.LabelSmall,
                color = Color.White,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TVFocusIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps [content] in a [Box] that paints a glass-style focus ring whenever the
 * inner content (or this wrapper itself) is focused.
 *
 * Usage:
 * ```
 * TVFocusIndicator(cornerRadius = 16.dp) {
 *     MyFocusableContent()
 * }
 * ```
 */
@Composable
fun TVFocusIndicator(
    cornerRadius: Dp = 16.dp,
    focusBorderColor: Color = LiquidColors.GlassBorderFocused,
    focusBorderWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "focusRingAlpha",
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "focusRingScale",
    )

    Box(
        modifier = modifier
            .scale(animatedScale)
            .focusable(interactionSource = interactionSource)
            .then(
                if (isFocused) {
                    Modifier.border(
                        border = BorderStroke(focusBorderWidth, focusBorderColor.copy(alpha = animatedAlpha)),
                        shape = RoundedCornerShape(cornerRadius),
                    )
                } else Modifier,
            ),
        content = content,
    )
}

/**
 * [Modifier] extension that adds a glass focus ring to any composable.
 * Relies on the provided [MutableInteractionSource] for focus tracking.
 */
fun Modifier.glassFocusRing(
    isFocused: Boolean,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = LiquidColors.GlassBorderFocused,
    borderWidth: Dp = 2.dp,
): Modifier = this.then(
    if (isFocused) {
        Modifier.border(
            border = BorderStroke(borderWidth, borderColor),
            shape = RoundedCornerShape(cornerRadius),
        )
    } else Modifier,
)

// ─────────────────────────────────────────────────────────────────────────────
// LoadingSpinner
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Animated circular loading indicator styled to match the glass design system.
 *
 * @param size Overall diameter of the spinner.
 * @param strokeWidth Width of the circular arc stroke.
 * @param color Spinner arc colour; defaults to [LiquidColors.AccentBlue].
 * @param trackColor Background ring colour.
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = LiquidColors.AccentBlue,
    trackColor: Color = LiquidColors.GlassBorder,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinnerRotation")

    // Sweep angle oscillates to create an expanding/contracting arc effect
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spinnerSweep",
    )

    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round,
    )
}
