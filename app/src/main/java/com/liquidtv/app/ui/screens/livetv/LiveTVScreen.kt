package com.liquidtv.app.ui.screens.livetv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.liquidtv.app.ui.components.AppBackground
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.ChannelFilter
import com.liquidtv.app.domain.model.SortOrder
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun LiveTVScreen(
    filterCategory: String? = null,
    filterLanguage: String? = null,
    filterCountry: String? = null,
    onChannelClick: (String) -> Unit,
    viewModel: LiveTVViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(filterCategory, filterLanguage, filterCountry) {
        if (filterCategory != null || filterLanguage != null || filterCountry != null) {
            viewModel.setFilter(
                ChannelFilter(
                    category = filterCategory,
                    language = filterLanguage,
                    country = filterCountry,
                )
            )
        }
    }

    AppBackground(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp),
        ) {
            // Header / Filter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Title and count
                Column {
                    val filterTitle = filterCategory ?: filterLanguage ?: filterCountry ?: "Live TV"
                    Text(
                        text = filterTitle,
                        style = LiquidTypography.HeadlineMedium.copy(
                            brush = Brush.linearGradient(
                                listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight)
                            )
                        ),
                    )
                    Text(
                        text = "${uiState.channels.size} channels available",
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextTertiary,
                    )
                }

                // Controls: Sort options & View Mode toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sortOrders = listOf(
                        "A-Z" to SortOrder.NAME_ASC,
                        "Z-A" to SortOrder.NAME_DESC,
                        "Favorites" to SortOrder.FAVORITES_FIRST,
                        "Category" to SortOrder.CATEGORY,
                    )

                    sortOrders.forEach { (label, order) ->
                        val isSelected = uiState.filter.sortOrder == order
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        LiquidGlassSurface(
                            modifier = Modifier
                                .clickable(interactionSource, null) { viewModel.setSortOrder(order) }
                                .focusable(interactionSource = interactionSource),
                            cornerRadius = 20.dp,
                            glassAlpha = if (isSelected) 0.35f else if (isFocused) 0.22f else 0.12f,
                            borderAlpha = if (isFocused) 0.7f else if (isSelected) 0.45f else 0.18f,
                            borderColor = if (isSelected) LiquidColors.AccentBlue else Color.White,
                            isFocused = isFocused,
                        ) {
                            Text(
                                text = label,
                                style = LiquidTypography.LabelMedium,
                                color = if (isSelected) LiquidColors.AccentBlue else LiquidColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // ViewMode Buttons
                    val gridInteraction = remember { MutableInteractionSource() }
                    val gridFocused by gridInteraction.collectIsFocusedAsState()
                    LiquidGlassSurface(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(gridInteraction, null) { viewModel.setViewMode(ViewMode.GRID) }
                            .focusable(interactionSource = gridInteraction),
                        cornerRadius = 18.dp,
                        glassAlpha = if (uiState.viewMode == ViewMode.GRID) 0.35f else 0.12f,
                        isFocused = gridFocused,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.GridView,
                                contentDescription = "Grid View",
                                tint = if (uiState.viewMode == ViewMode.GRID) LiquidColors.AccentBlue else LiquidColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    val listInteraction = remember { MutableInteractionSource() }
                    val listFocused by listInteraction.collectIsFocusedAsState()
                    LiquidGlassSurface(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(listInteraction, null) { viewModel.setViewMode(ViewMode.LIST) }
                            .focusable(interactionSource = listInteraction),
                        cornerRadius = 18.dp,
                        glassAlpha = if (uiState.viewMode == ViewMode.LIST) 0.35f else 0.12f,
                        isFocused = listFocused,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = "List View",
                                tint = if (uiState.viewMode == ViewMode.LIST) LiquidColors.AccentBlue else LiquidColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Content Area
            if (uiState.isLoading && uiState.channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiquidColors.AccentBlue)
                }
            } else if (uiState.channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LiquidGlassSurface(
                        modifier = Modifier.padding(32.dp),
                        cornerRadius = 20.dp,
                        glassAlpha = 0.15f,
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(48.dp))
                            Text("No channels found", style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
                            Text("Try adjusting your filter or refresh the playlist in Settings.", style = LiquidTypography.BodyMedium, color = LiquidColors.TextSecondary)
                        }
                    }
                }
            } else {
                when (uiState.viewMode) {
                    ViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(uiState.channels, key = { it.id }) { channel ->
                                LiveTVGridCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channel.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(channel.id) },
                                )
                            }
                        }
                    }
                    ViewMode.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(uiState.channels, key = { it.id }) { channel ->
                                LiveTVListRow(
                                    channel = channel,
                                    onClick = { onChannelClick(channel.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(channel.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTVGridCard(
    channel: Channel,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused) 0.32f else 0.15f,
        borderAlpha = if (isFocused) 0.85f else 0.22f,
        borderColor = if (isFocused) LiquidColors.AccentBlue else Color.White,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
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
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                }
                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
                    LiveBadge()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleSmall,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = channel.groupTitle ?: channel.country ?: "General",
                        style = LiquidTypography.LabelSmall,
                        color = if (isFocused) LiquidColors.AccentBlueLight else LiquidColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (channel.isFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Favorite",
                            tint = LiquidColors.LiveRed,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTVListRow(
    channel: Channel,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.30f else 0.12f,
        borderAlpha = if (isFocused) 0.85f else 0.18f,
        borderColor = if (isFocused) LiquidColors.AccentBlue else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
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
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.size(38.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = LiquidTypography.TitleMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    channel.groupTitle?.let {
                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlue)
                    }
                    channel.language?.let {
                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                    }
                    channel.country?.let {
                        Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.TextTertiary)
                    }
                }
            }

            LiveBadge()

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (channel.isFavorite) LiquidColors.LiveRed else LiquidColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
