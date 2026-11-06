package com.liquidtv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Premium glass channel card for grid layouts on Android TV.
 *
 * Animates scale on focus/press, shows channel logo via Coil,
 * and surfaces a LIVE badge, language tag, and favorite toggle.
 */
@Composable
fun GlassChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderColor = if (isFocused) LiquidColors.GlassBorderFocused else LiquidColors.GlassBorder

    LiquidGlassSurface(
        modifier = modifier
            .width(200.dp)
            .wrapContentHeight()
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { /* handled via interactionSource */ },
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused) 0.32f else 0.16f,
        borderAlpha = if (isFocused) 0.85f else 0.22f,
        borderColor = borderColor,
        isFocused = isFocused,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Logo ──────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = if (isFocused) 0.22f else 0.08f),
                                LiquidColors.GlassTertiary,
                            )
                        ),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    )
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
            ) {
                SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = "${channel.name} logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = LiquidColors.AccentBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = LiquidColors.TextTertiary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )

                // Bottom scrim for contrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        ),
                )

                // LIVE badge – top-left
                LiveBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )

                // Favorite icon – top-right
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) LiquidColors.LiveRed else LiquidColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Info ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!channel.groupTitle.isNullOrBlank()) {
                    Text(
                        text = channel.groupTitle,
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!channel.language.isNullOrBlank()) {
                        LanguageTag(language = channel.language)
                    }
                    if (!channel.country.isNullOrBlank()) {
                        Text(
                            text = channel.country,
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compact horizontal list row variant
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact horizontal list row variant of the channel card, suitable for list
 * screens and search results.
 */
@Composable
fun GlassChannelListItem(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderColor = if (isFocused) LiquidColors.GlassBorderFocused else LiquidColors.GlassBorder

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.32f else 0.14f,
        borderAlpha = if (isFocused) 0.85f else 0.20f,
        borderColor = borderColor,
        isFocused = isFocused,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Channel number
            channel.channelNumber?.let { num ->
                Text(
                    text = num.toString().padStart(3, '0'),
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused) LiquidColors.AccentBlueLight else LiquidColors.TextTertiary,
                    modifier = Modifier.width(36.dp),
                )
            }

            // Small logo with background
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = if (isFocused) 0.25f else 0.12f),
                                LiquidColors.GlassTertiary,
                            )
                        ),
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = "${channel.name} logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            // Name + meta
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!channel.language.isNullOrBlank()) {
                        LanguageTag(language = channel.language)
                    }
                    if (!channel.country.isNullOrBlank()) {
                        Text(
                            text = channel.country,
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.TextTertiary,
                        )
                    }
                }
            }

            // LIVE badge
            LiveBadge()

            // Favorite icon
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) LiquidColors.LiveRed else LiquidColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small internal composable – language pill tag
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LanguageTag(language: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = LiquidColors.AccentBlue.copy(alpha = 0.18f),
    ) {
        Text(
            text = language.uppercase(),
            style = LiquidTypography.LabelSmall,
            color = LiquidColors.AccentBlue,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
