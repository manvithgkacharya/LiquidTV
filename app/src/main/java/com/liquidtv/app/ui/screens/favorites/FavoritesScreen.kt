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
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun FavoritesScreen(
    onChannelClick: (String, MediaType) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = AppTheme.colors

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isCompact = maxWidth < 600.dp
            val hPad = if (isCompact) 16.dp else 36.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (isCompact) 76.dp else 88.dp),
            ) {
                Text(
                    text = "المفضلة وسجل المشاهدة",
                    style = LiquidTypography.HeadlineMedium.copy(
                        brush = Brush.linearGradient(
                            listOf(colors.text, colors.accentText)
                        )
                    ),
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = hPad, vertical = 12.dp),
                )

                if (uiState.isLoading && uiState.favorites.isEmpty() && uiState.recentlyWatched.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else if (uiState.favorites.isEmpty() && uiState.recentlyWatched.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LiquidGlassSurface(
                            modifier = Modifier.padding(24.dp),
                            cornerRadius = 20.dp,
                            glassAlpha = 0.80f,
                            borderColor = colors.border,
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.Favorite, null, tint = colors.danger, modifier = Modifier.size(52.dp))
                                Text("لا توجد عناصر في المفضلة بعد", style = LiquidTypography.TitleLarge, color = colors.text)
                                Text(
                                    "أضف أي قناة أو فيلم إلى المفضلة أثناء التصفح أو من داخل المشغل.",
                                    style = LiquidTypography.BodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = if (isCompact) 96.dp else 36.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (uiState.favorites.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = hPad),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(Icons.Filled.Favorite, null, tint = colors.danger, modifier = Modifier.size(20.dp))
                                            Text("العناصر المحفوظة", style = LiquidTypography.HeadlineSmall, color = colors.text)
                                        }
                                        Text(
                                            "${uiState.favorites.size} عنصر",
                                            style = LiquidTypography.LabelSmall,
                                            color = colors.muted,
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = hPad),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(uiState.favorites, key = { it.id }) { channel ->
                                            FavoriteChannelCard(
                                                channel = channel,
                                                onClick = { onChannelClick(channel.id, channel.mediaType) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.recentlyWatched.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = hPad),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(Icons.Filled.History, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                                            Text("شوهد مؤخراً", style = LiquidTypography.HeadlineSmall, color = colors.text)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .clickable { viewModel.clearHistory() }
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(Icons.Filled.DeleteSweep, null, tint = colors.muted, modifier = Modifier.size(16.dp))
                                            Text("مسح السجل", style = LiquidTypography.LabelSmall, color = colors.muted)
                                        }
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = hPad),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(uiState.recentlyWatched, key = { it.id }) { channel ->
                                            FavoriteChannelCard(
                                                channel = channel,
                                                onClick = { onChannelClick(channel.id, channel.mediaType) },
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
}

@Composable
private fun FavoriteChannelCard(
    channel: Channel,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
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
        glassAlpha = if (isFocused) 0.90f else 0.78f,
        borderAlpha = if (isFocused) 0.92f else 0.45f,
        borderColor = if (isFocused) colors.focus else colors.border,
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
                    .background(colors.surface.copy(alpha = if (colors.isLight) 0.90f else 0.72f), RoundedCornerShape(10.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = colors.muted, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    if (channel.mediaType == MediaType.LIVE) {
                        LiveBadge()
                    } else {
                        Box(
                            modifier = Modifier
                                .background(colors.accent.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (channel.mediaType == MediaType.MOVIE) "فيلم" else "حلقة",
                                style = LiquidTypography.LabelSmall,
                                color = colors.onAccent,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channel.groupTitle ?: channel.country ?: "عام",
                    style = LiquidTypography.LabelSmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
