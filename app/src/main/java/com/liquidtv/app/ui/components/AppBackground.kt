package com.liquidtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.liquidtv.app.ui.theme.LiquidColors

/**
 * Shared atmospheric background used by all LIQUID TV screens.
 *
 * Renders the deep midnight base colour plus two layered radial gradients
 * (blue top-left + violet bottom-right) for a cinematic depth effect.
 * Keeps all gradient colours as composable-level constants to avoid
 * per-frame object allocation.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LiquidColors.MidnightNavy)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        LiquidColors.AccentBlueDark.copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                    radius = 1000f,
                    center = androidx.compose.ui.geometry.Offset(220f, 180f),
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        LiquidColors.AccentViolet.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    radius = 800f,
                    center = androidx.compose.ui.geometry.Offset(1600f, 900f),
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        LiquidColors.AccentCyan.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    radius = 600f,
                    center = androidx.compose.ui.geometry.Offset(900f, 50f),
                )
            ),
        content = content,
    )
}

