package com.liquidtv.app.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Navigation destinations mapped to the Mirror Bubble nav bar.
 */
enum class NavDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Default.Home),
    LiveTV("Live TV", Icons.Default.LiveTv),
    Categories("Categories", Icons.Default.GridView),
    Languages("Languages", Icons.Default.Language),
    Countries("Countries", Icons.Default.Public),
    Favorites("Favorites", Icons.Default.Favorite),
    Search("Search", Icons.Default.Search),
    Settings("Settings", Icons.Default.Settings),
}

private val NAV_ITEMS = NavDestination.entries

/**
 * Floating Mirror Bubble navigation bar — the app's signature visual identity.
 */
@Composable
fun MirrorBubbleNavigation(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Outer glass pill
    Box(
        modifier = modifier
            .wrapContentWidth()
            .height(64.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val cr = h / 2f

                // Subtle ambient glow behind the nav pill
                drawRoundRect(
                    color = LiquidColors.AccentBlue.copy(alpha = 0.10f),
                    topLeft = Offset(-4.dp.toPx(), -2.dp.toPx()),
                    size = Size(w + 8.dp.toPx(), h + 4.dp.toPx()),
                    cornerRadius = CornerRadius(cr + 4.dp.toPx()),
                )

                // Glass fill
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    cornerRadius = CornerRadius(cr),
                )
                // Top specular highlight
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                        startY = 0f,
                        endY = h * 0.45f,
                    ),
                    cornerRadius = CornerRadius(cr),
                )
                // Border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(50)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NAV_ITEMS.forEach { destination ->
                NavTab(
                    destination = destination,
                    isSelected = destination == selectedDestination,
                    onSelected = { onDestinationSelected(destination) },
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    destination: NavDestination,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale_${destination.name}",
    )

    val tabWidth: Dp by animateDpAsState(
        targetValue = if (isSelected) 110.dp else if (isFocused) 72.dp else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabWidth_${destination.name}",
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else if (isFocused) 0.5f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bubbleAlpha_${destination.name}",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "labelAlpha_${destination.name}",
    )

    Box(
        modifier = Modifier
            .width(tabWidth)
            .height(48.dp)
            .drawBehind {
                if (bubbleAlpha > 0.01f) {
                    val w = size.width
                    val h = size.height
                    val cr = h / 2f
                    // Gradient fill for active bubble (blue into violet)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = 0.35f * bubbleAlpha),
                                LiquidColors.AccentViolet.copy(alpha = 0.25f * bubbleAlpha),
                            )
                        ),
                        cornerRadius = CornerRadius(cr),
                        size = Size(w, h),
                    )
                    // Specular highlight on top half
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f * bubbleAlpha),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h * 0.5f,
                        ),
                        cornerRadius = CornerRadius(cr),
                    )
                    // Border
                    drawRoundRect(
                        color = LiquidColors.AccentBlue.copy(alpha = 0.85f * bubbleAlpha),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    // Lens glint
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f * bubbleAlpha),
                        radius = 3.dp.toPx(),
                        center = Offset(cr, cr * 0.5f),
                    )
                }
            }
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = if (isSelected) Color.White else if (isFocused) LiquidColors.AccentBlueLight else LiquidColors.IconInactive,
                modifier = Modifier
                    .size(20.dp)
                    .scale(iconScale),
            )
            if (labelAlpha > 0.01f) {
                Text(
                    text = destination.label,
                    style = LiquidTypography.LabelSmall,
                    color = Color.White.copy(alpha = labelAlpha),
                    maxLines = 1,
                )
            }
        }
    }
}

