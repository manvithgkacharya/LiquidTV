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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

// ─────────────────────────────────────────────────────────────────────────────
// LiveBadge
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pulsing LIVE indicator pill badge using theme danger/onDanger tokens.
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
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
                        colors.danger.copy(alpha = 0.94f),
                        colors.danger.copy(alpha = 0.84f),
                    )
                )
            )
            .border(0.8.dp, colors.onDanger.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.onDanger.copy(alpha = dotAlpha)),
            )

            Text(
                text = "مباشر",
                style = LiquidTypography.LabelSmall,
                color = colors.onDanger,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TVFocusIndicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TVFocusIndicator(
    cornerRadius: Dp = 16.dp,
    focusBorderColor: Color = Color.Unspecified,
    focusBorderWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val resolvedFocusBorder = if (focusBorderColor != Color.Unspecified) focusBorderColor else AppTheme.colors.focus

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
                        border = BorderStroke(focusBorderWidth, resolvedFocusBorder.copy(alpha = animatedAlpha)),
                        shape = RoundedCornerShape(cornerRadius),
                    )
                } else Modifier,
            ),
        content = content,
    )
}

fun Modifier.glassFocusRing(
    isFocused: Boolean,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = Color.White,
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

@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) {
    val colors = AppTheme.colors
    val resolvedColor = if (color != Color.Unspecified) color else colors.accent
    val resolvedTrack = if (trackColor != Color.Unspecified) trackColor else colors.border.copy(alpha = 0.45f)

    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = resolvedColor,
        trackColor = resolvedTrack,
        strokeWidth = strokeWidth,
        strokeCap = StrokeCap.Round,
    )
}
