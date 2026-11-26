package com.liquidtv.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.ThemeAtmosphere

/**
 * Shared atmospheric background used by all LIQUID TV screens.
 *
 * Uses [AppTheme.current] (`backgroundTop`, `background`, `backgroundBottom`,
 * `waveOne`, `waveTwo`, `accent`, and `atmosphere`) inside `drawWithCache` so
 * brushes and wave paths are only built when size or active theme changes.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = AppTheme.current
    val colors = theme.palette
    val isLight = theme.isLight

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)

                val baseBrush = Brush.verticalGradient(
                    0.0f to colors.backgroundTop,
                    0.48f to colors.background,
                    1.0f to colors.backgroundBottom,
                    startY = 0f,
                    endY = h,
                )

                val (glow1Center, glow2Center, waveShift) = when (theme.atmosphere) {
                    ThemeAtmosphere.ROYAL_NAVY -> Triple(Offset(w * 0.16f, h * 0.14f), Offset(w * 0.85f, h * 0.82f), 0.00f)
                    ThemeAtmosphere.CELESTIAL_CYAN -> Triple(Offset(w * 0.80f, h * 0.16f), Offset(w * 0.18f, h * 0.86f), 0.06f)
                    ThemeAtmosphere.RUBY_VELVET -> Triple(Offset(w * 0.22f, h * 0.18f), Offset(w * 0.78f, h * 0.80f), -0.05f)
                    ThemeAtmosphere.PEARL_MARBLE -> Triple(Offset(w * 0.74f, h * 0.12f), Offset(w * 0.24f, h * 0.88f), 0.04f)
                    ThemeAtmosphere.EMERALD_PALACE -> Triple(Offset(w * 0.18f, h * 0.22f), Offset(w * 0.84f, h * 0.78f), -0.04f)
                    ThemeAtmosphere.MIDNIGHT_SAPPHIRE -> Triple(Offset(w * 0.25f, h * 0.12f), Offset(w * 0.82f, h * 0.84f), 0.03f)
                    ThemeAtmosphere.CARRARA_MARBLE -> Triple(Offset(w * 0.20f, h * 0.14f), Offset(w * 0.80f, h * 0.86f), -0.03f)
                    ThemeAtmosphere.IMPERIAL_AMETHYST -> Triple(Offset(w * 0.78f, h * 0.15f), Offset(w * 0.20f, h * 0.85f), 0.05f)
                    ThemeAtmosphere.IMPERIAL_BRONZE -> Triple(Offset(w * 0.15f, h * 0.18f), Offset(w * 0.82f, h * 0.80f), -0.06f)
                    ThemeAtmosphere.BLACK_ONYX -> Triple(Offset(w * 0.72f, h * 0.14f), Offset(w * 0.25f, h * 0.84f), 0.02f)
                }

                val waveOneAlpha = if (isLight) 0.17f else 0.22f
                val waveTwoAlpha = if (isLight) 0.13f else 0.17f
                val accentGlowAlpha = if (isLight) 0.10f else 0.12f

                val radialGlow1 = Brush.radialGradient(
                    colors = listOf(colors.waveOne.copy(alpha = waveOneAlpha), Color.Transparent),
                    center = glow1Center,
                    radius = maxOf(w, h) * 0.65f,
                )
                val radialGlow2 = Brush.radialGradient(
                    colors = listOf(colors.waveTwo.copy(alpha = waveTwoAlpha), Color.Transparent),
                    center = glow2Center,
                    radius = maxOf(w, h) * 0.58f,
                )
                val accentGlow = Brush.radialGradient(
                    colors = listOf(colors.accent.copy(alpha = accentGlowAlpha), Color.Transparent),
                    center = Offset(w * 0.50f, h * 0.04f),
                    radius = maxOf(w, h) * 0.52f,
                )

                val waveOnePath = Path().apply {
                    moveTo(0f, h * (0.64f + waveShift))
                    cubicTo(
                        w * 0.28f, h * (0.50f - waveShift),
                        w * 0.64f, h * (0.78f + waveShift),
                        w, h * (0.58f - waveShift),
                    )
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                val waveOneBrush = Brush.linearGradient(
                    colors = listOf(
                        colors.waveOne.copy(alpha = if (isLight) 0.14f else 0.16f),
                        colors.waveOne.copy(alpha = if (isLight) 0.04f else 0.03f),
                    ),
                    start = Offset(0f, h * 0.52f),
                    end = Offset(w, h),
                )

                val waveTwoPath = Path().apply {
                    moveTo(0f, h * (0.77f - waveShift))
                    cubicTo(
                        w * 0.34f, h * (0.90f + waveShift),
                        w * 0.70f, h * (0.62f - waveShift),
                        w, h * (0.74f + waveShift),
                    )
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                val waveTwoBrush = Brush.linearGradient(
                    colors = listOf(
                        colors.waveTwo.copy(alpha = if (isLight) 0.11f else 0.12f),
                        Color.Transparent,
                    ),
                    start = Offset(w, h * 0.60f),
                    end = Offset(0f, h),
                )

                onDrawBehind {
                    drawRect(brush = baseBrush)
                    drawRect(brush = radialGlow1)
                    drawRect(brush = radialGlow2)
                    drawRect(brush = accentGlow)
                    drawPath(path = waveOnePath, brush = waveOneBrush)
                    drawPath(path = waveTwoPath, brush = waveTwoBrush)
                }
            },
        content = content,
    )
}

