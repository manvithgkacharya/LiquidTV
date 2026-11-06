package com.liquidtv.app.ui.screens.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun FavoritesScreen(
    onChannelClick: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp),
        ) {
            Text(
                text = "Favorites & History",
                style = LiquidTypography.HeadlineMedium.copy(
                    brush = Brush.linearGradient(
                        listOf(LiquidColors.TextPrimary, LiquidColors.AccentRose)
                    )
                ),
                color = LiquidColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
            )

            if (uiState.isLoading && uiState.favorites.isEmpty() && uiState.recentlyWatched.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiquidColors.AccentBlue)
                }
            } else if (uiState.favorites.isEmpty() && uiState.recentlyWatched.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LiquidGlassSurface(
                        modifier = Modifier.padding(32.dp),
                        cornerRadius = 20.dp,
                        glassAlpha = 0.15f,
                    ) {
                        Column(
                            modifier = Modifier.padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.Favorite, null, tint = LiquidColors.LiveRed, modifier = Modifier.size(52.dp))
                            Text("No Favorites Yet", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                            Text(
                                "Mark any channel as favorite while browsing or inside the video player.",
                                style = LiquidTypography.BodyMedium,
                                color = LiquidColors.TextSecondary,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Favorite channels row
                    if (uiState.favorites.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Filled.Favorite, null, tint = LiquidColors.LiveRed, modifier = Modifier.size(20.dp))
                                        Text("Saved Channels", style = LiquidTypography.HeadlineSmall, color = LiquidColors.TextPrimary)
                                    }
                                    Text(
                                        "${uiState.favorites.size} channels",
                                        style = LiquidTypography.LabelSmall,
                                        color = LiquidColors.TextTertiary,
                                    )
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(uiState.favorites, key = { it.id }) { channel ->
                                        FavoriteChannelCard(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Recently watched row
                    if (uiState.recentlyWatched.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Filled.History, null, tint = LiquidColors.AccentBlue, modifier = Modifier.size(20.dp))
                                        Text("Recently Watched", style = LiquidTypography.HeadlineSmall, color = LiquidColors.TextPrimary)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clickable { viewModel.clearHistory() }
                                            .padding(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Filled.DeleteSweep, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(16.dp))
                                        Text("Clear History", style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(uiState.recentlyWatched, key = { it.id }) { channel ->
                                        FavoriteChannelCard(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "favCardScale",
    )

    LiquidGlassSurface(
        modifier = Modifier
            .width(170.dp)
            .height(190.dp)
            .scale(scale)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.25f else 0.12f,
        borderAlpha = if (isFocused) 0.7f else 0.18f,
        borderColor = if (isFocused) LiquidColors.AccentBlue else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(LiquidColors.GlassTertiary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.size(70.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                    LiveBadge()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleSmall,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channel.groupTitle ?: channel.country ?: "General",
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

