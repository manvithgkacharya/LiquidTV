package com.liquidtv.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liquidtv.app.R
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Navigation destinations mapped to the Mirror Bubble nav bar.
 */
enum class NavDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Default.Home),
    LiveTV(R.string.nav_live_tv, Icons.Default.LiveTv),
    Movies(R.string.nav_movies, Icons.Default.Movie),
    Series(R.string.nav_series, Icons.Default.VideoLibrary),
    Categories(R.string.nav_categories, Icons.Default.GridView),
    Favorites(R.string.nav_favorites, Icons.Default.Favorite),
    Search(R.string.nav_search, Icons.Default.Search),
    Settings(R.string.nav_settings, Icons.Default.Settings),
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
    val scrollState = rememberScrollState()
    val colors = AppTheme.colors
    val isLight = colors.isLight

    // Outer glass pill
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .wrapContentWidth()
            .height(56.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val cr = h / 2f

                // Subtle ambient glow behind the nav pill
                drawRoundRect(
                    color = colors.accent.copy(alpha = if (isLight) 0.14f else 0.12f),
                    topLeft = Offset(-4.dp.toPx(), -2.dp.toPx()),
                    size = Size(w + 8.dp.toPx(), h + 4.dp.toPx()),
                    cornerRadius = CornerRadius(cr + 4.dp.toPx()),
                )

                // Theme-tinted glass pill fill
                drawRoundRect(
                    color = colors.panel.copy(alpha = if (isLight) 0.90f else 0.80f),
                    cornerRadius = CornerRadius(cr),
                )
                // Top specular highlight
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = if (isLight) 0.55f else 0.18f), Color.Transparent),
                        startY = 0f,
                        endY = h * 0.45f,
                    ),
                    cornerRadius = CornerRadius(cr),
                )
                // Border
                drawRoundRect(
                    color = colors.border.copy(alpha = if (isLight) 0.72f else 0.55f),
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
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
    val colors = AppTheme.colors
    val isLight = colors.isLight
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelText = stringResource(destination.labelRes)

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else if (isFocused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale_${destination.name}",
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else if (isFocused) 0.55f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bubbleAlpha_${destination.name}",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "labelAlpha_${destination.name}",
    )

    val activeTint = if (isSelected) colors.text else if (isFocused) colors.accentText else colors.textSecondary

    Box(
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .widthIn(min = if (isSelected) 112.dp else if (isFocused) 64.dp else 50.dp)
            .height(48.dp)
            .drawBehind {
                if (bubbleAlpha > 0.01f) {
                    val w = size.width
                    val h = size.height
                    val cr = h / 2f
                    // Gradient fill for active bubble tinted with theme selected + accent
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                colors.selected.copy(alpha = (if (isLight) 0.92f else 0.85f) * bubbleAlpha),
                                colors.accent.copy(alpha = (if (isLight) 0.28f else 0.24f) * bubbleAlpha),
                            )
                        ),
                        cornerRadius = CornerRadius(cr),
                        size = Size(w, h),
                    )
                    // Specular highlight on top half
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = (if (isLight) 0.50f else 0.26f) * bubbleAlpha),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h * 0.5f,
                        ),
                        cornerRadius = CornerRadius(cr),
                    )
                    // Border
                    drawRoundRect(
                        color = (if (isFocused) colors.focus else colors.accent).copy(alpha = 0.88f * bubbleAlpha),
                        cornerRadius = CornerRadius(cr),
                        style = Stroke(width = if (isFocused) 1.6.dp.toPx() else 1.2.dp.toPx()),
                    )
                    // Lens glint
                    drawCircle(
                        color = colors.accent.copy(alpha = 0.65f * bubbleAlpha),
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
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = if (isSelected) 14.dp else 10.dp)
            .testTag("nav_tab_${destination.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = labelText,
                tint = activeTint,
                modifier = Modifier
                    .size(20.dp)
                    .scale(iconScale),
            )
            if (isSelected && labelAlpha > 0.01f) {
                Text(
                    text = labelText,
                    style = LiquidTypography.NavLabel,
                    color = colors.text.copy(alpha = labelAlpha),
                    maxLines = 1,
                )
            }
        }
    }
}
