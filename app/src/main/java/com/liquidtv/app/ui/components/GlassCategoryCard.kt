package com.liquidtv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Glass category card used in the category grid on the home/browse screens.
 *
 * Derives an accent gradient and representative Material icon from the
 * [category]'s [Category.iconName] field.  Focus triggers a scale-up animation
 * and a brighter border glow via [LiquidGlassSurface].
 */
@Composable
fun GlassCategoryCard(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 0.95f
        isFocused -> 1.08f
        else -> 1.00f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "categoryCardScale",
    )

    val borderColor = if (isFocused) LiquidColors.GlassBorderFocused else LiquidColors.GlassBorder

    val accentColors = accentGradientFor(category.iconName)
    val icon = iconForCategory(category.iconName)

    LiquidGlassSurface(
        modifier = modifier
            .scale(scale)
            .width(180.dp)
            .height(140.dp)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 20.dp,
        borderColor = borderColor,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle gradient accent overlay in the top half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter),
            ) {
                // Drawn via the surface background; we layer a tinted overlay here.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = accentColors.first.copy(alpha = if (isFocused) 0.22f else 0.12f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {}
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Icon with gradient tint
                Icon(
                    imageVector = icon,
                    contentDescription = category.name,
                    tint = accentColors.first,
                    modifier = Modifier.size(48.dp),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = category.name,
                        style = LiquidTypography.TitleMedium,
                        color = LiquidColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${category.channelCount} channels",
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Focus glow border accent (inner highlight line at top)
            if (isFocused) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter),
                    color = accentColors.first.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers – accent palette & icon mapping
// ─────────────────────────────────────────────────────────────────────────────

/** Returns a (primary, secondary) accent [Color] pair for the given iconName. */
private fun accentGradientFor(iconName: String?): Pair<Color, Color> =
    when (iconName?.lowercase()) {
        "sports"        -> Pair(Color(0xFF00C853), Color(0xFF1DE9B6))
        "news"          -> Pair(Color(0xFFD50000), Color(0xFFFF6D00))
        "movies", "film"-> Pair(Color(0xFFAA00FF), Color(0xFF6200EA))
        "music"         -> Pair(Color(0xFFFF6D00), Color(0xFFFFD600))
        "kids"          -> Pair(Color(0xFFFFD600), Color(0xFF00BCD4))
        "documentary"   -> Pair(Color(0xFF00B0FF), Color(0xFF00BCD4))
        "entertainment" -> Pair(Color(0xFFE91E63), Color(0xFFAA00FF))
        "cooking", "food"->Pair(Color(0xFFFF6D00), Color(0xFFFF8F00))
        "travel"        -> Pair(Color(0xFF00BCD4), Color(0xFF0091EA))
        "science", "tech"->Pair(Color(0xFF00B0FF), Color(0xFF1DE9B6))
        else            -> Pair(LiquidColors.AccentBlue, Color(0xFF00B0FF))
    }

/** Maps an iconName string to a Material [ImageVector]. */
private fun iconForCategory(iconName: String?): ImageVector =
    when (iconName?.lowercase()) {
        "sports"                -> Icons.Default.SportsSoccer
        "news"                  -> Icons.Default.Newspaper
        "movies", "film"        -> Icons.Default.Movie
        "music"                 -> Icons.Default.MusicNote
        "kids"                  -> Icons.Default.ChildCare
        "documentary"           -> Icons.Default.VideoLibrary
        "entertainment"         -> Icons.Default.LiveTv
        "cooking", "food"       -> Icons.Default.Restaurant
        "travel"                -> Icons.Default.Flight
        "science", "tech"       -> Icons.Default.Science
        "gaming", "games"       -> Icons.Default.SportsEsports
        "lifestyle"             -> Icons.Default.Spa
        "business", "finance"   -> Icons.Default.BusinessCenter
        "health", "fitness"     -> Icons.Default.FitnessCenter
        else                    -> Icons.Default.Tv
    }
