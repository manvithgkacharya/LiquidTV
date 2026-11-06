package com.liquidtv.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LocalLiquidGlassTokens

/**
 * Highly-optimized Liquid Glass surface composable for Android TV.
 *
 * Designed for 60fps performance on TV hardware by minimizing object allocations
 * in DrawScope and using snappy TV-friendly animations.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    glassAlpha: Float = 0.18f,
    borderAlpha: Float = 0.25f,
    highlightAlpha: Float = 0.45f,
    shadowAlpha: Float = 0.3f,
    glassColor: Color = Color.White,
    borderColor: Color = Color.White,
    accentGlowColor: Color = LiquidColors.AccentBlue,
    isFocused: Boolean = false,
    isPressed: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalLiquidGlassTokens.current

    // Snappy animations to avoid multi-second frame drops during D-pad navigation
    val focusProgress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "glass_focus",
    )

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.98f
            isFocused -> tokens.focusScale
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "glass_scale",
    )

    val activeBorderAlpha = borderAlpha + focusProgress * (tokens.focusBorderAlpha - borderAlpha)
    val activeGlassAlpha = glassAlpha + focusProgress * 0.10f
    val activeHighlightAlpha = highlightAlpha + focusProgress * 0.2f

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .drawBehind {
                drawOptimizedLiquidGlass(
                    glassColor = glassColor,
                    glassAlpha = activeGlassAlpha,
                    borderColor = if (isFocused) LiquidColors.AccentBlue else borderColor,
                    borderAlpha = activeBorderAlpha,
                    highlightAlpha = activeHighlightAlpha,
                    cornerRadius = cornerRadius.toPx(),
                    isFocused = isFocused,
                    focusProgress = focusProgress,
                    accentGlowColor = accentGlowColor,
                )
            }
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            ),
        content = content,
    )
}

/**
 * Variant with self-managed focus state via InteractionSource.
 */
@Composable
fun LiquidGlassSurfaceFocusable(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    glassAlpha: Float = 0.15f,
    borderAlpha: Float = 0.2f,
    highlightAlpha: Float = 0.35f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    LiquidGlassSurface(
        modifier = modifier.focusable(interactionSource = interactionSource),
        cornerRadius = cornerRadius,
        glassAlpha = glassAlpha,
        borderAlpha = borderAlpha,
        highlightAlpha = highlightAlpha,
        isFocused = isFocused,
        isPressed = isPressed,
        onClick = onClick,
        interactionSource = interactionSource,
        content = content,
    )
}private fun DrawScope.drawOptimizedLiquidGlass(
    glassColor: Color,
    glassAlpha: Float,
    borderColor: Color,
    borderAlpha: Float,
    highlightAlpha: Float,
    cornerRadius: Float,
    isFocused: Boolean,
    focusProgress: Float = 0f,
    accentGlowColor: Color = LiquidColors.AccentBlue,
) {
    val w = size.width
    val h = size.height
    val cr = cornerRadius.coerceAtMost(minOf(w, h) / 2f)
    val cRadius = CornerRadius(cr)

    // 0. Outer glow — painted first (behind everything) when focused
    if (focusProgress > 0.01f) {
        val glowSpread = 12.dp.toPx() * focusProgress
        val glowCr = CornerRadius(cr + glowSpread * 0.5f)
        drawRoundRect(
            color = accentGlowColor.copy(alpha = 0.22f * focusProgress),
            topLeft = Offset(-glowSpread, -glowSpread),
            size = Size(w + glowSpread * 2f, h + glowSpread * 2f),
            cornerRadius = glowCr,
        )
        // inner glow ring
        drawRoundRect(
            color = accentGlowColor.copy(alpha = 0.12f * focusProgress),
            topLeft = Offset(-glowSpread * 0.5f, -glowSpread * 0.5f),
            size = Size(w + glowSpread, h + glowSpread),
            cornerRadius = CornerRadius(cr + glowSpread * 0.25f),
        )
    }

    // 1. Translucent glass fill with subtle diagonal shimmer
    drawRoundRect(
        color = glassColor.copy(alpha = glassAlpha.coerceIn(0f, 1f)),
        cornerRadius = cRadius,
    )

    // 2. Specular top highlight (simulates glass refraction) — richer
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = highlightAlpha.coerceIn(0f, 0.65f)),
            0.35f to Color.White.copy(alpha = (highlightAlpha * 0.15f).coerceIn(0f, 0.15f)),
            0.6f to Color.Transparent,
            startY = 0f,
            endY = h,
        ),
        cornerRadius = cRadius,
    )

    // 3. Bottom edge subtle dark gradient (depth)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.7f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.18f),
            startY = 0f,
            endY = h,
        ),
        cornerRadius = cRadius,
    )

    // 4. Glass border — 2.5dp when focused for crisp pop
    val borderWidth = if (isFocused) 2.5.dp.toPx() else 1.2.dp.toPx()
    drawRoundRect(
        color = borderColor.copy(alpha = borderAlpha.coerceIn(0f, 1f)),
        cornerRadius = cRadius,
        style = Stroke(width = borderWidth),
    )
}
