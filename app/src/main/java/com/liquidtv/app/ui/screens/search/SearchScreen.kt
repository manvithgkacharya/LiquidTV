package com.liquidtv.app.ui.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
fun SearchScreen(
    onChannelClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AppBackground(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp, start = 36.dp, end = 36.dp),
        ) {
            // Search Input Container
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                cornerRadius = 50.dp,
                glassAlpha = 0.20f,
                borderAlpha = 0.40f,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = LiquidColors.AccentBlue,
                        modifier = Modifier.size(24.dp),
                    )
                    BasicTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.setQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        textStyle = LiquidTypography.BodyLarge.copy(color = LiquidColors.TextPrimary),
                        cursorBrush = SolidColor(LiquidColors.AccentBlue),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (uiState.query.isEmpty()) {
                                Text(
                                    text = "Search channels by name, genre, country, or language...",
                                    style = LiquidTypography.BodyLarge,
                                    color = LiquidColors.TextDisabled,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearQuery() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = LiquidColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                uiState.query.isEmpty() -> {
                    // Recent searches & Discovery suggestions
                    if (uiState.searchHistory.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Recent Searches",
                                    style = LiquidTypography.TitleSmall,
                                    color = LiquidColors.TextSecondary,
                                )
                                Text(
                                    "Clear All",
                                    style = LiquidTypography.LabelSmall,
                                    color = LiquidColors.AccentBlue,
                                    modifier = Modifier.clickable { viewModel.clearHistory() },
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.searchHistory) { historyItem ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isFocused by interactionSource.collectIsFocusedAsState()

                                    LiquidGlassSurface(
                                        modifier = Modifier
                                            .clickable(interactionSource, null) { viewModel.setQuery(historyItem) }
                                            .focusable(interactionSource = interactionSource),
                                        cornerRadius = 20.dp,
                                        glassAlpha = if (isFocused) 0.3f else 0.12f,
                                        borderAlpha = if (isFocused) 0.6f else 0.18f,
                                        isFocused = isFocused,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(Icons.Filled.History, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(14.dp))
                                            Text(historyItem, style = LiquidTypography.BodySmall, color = LiquidColors.TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.Search, null, tint = LiquidColors.TextDisabled, modifier = Modifier.size(56.dp))
                            Text(
                                "Explore Global Live Television",
                                style = LiquidTypography.TitleMedium,
                                color = LiquidColors.TextSecondary,
                            )
                            Text(
                                "Type any keyword on your remote or on-screen keyboard",
                                style = LiquidTypography.BodySmall,
                                color = LiquidColors.TextTertiary,
                            )
                        }
                    }
                }
                uiState.isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LiquidColors.AccentBlue)
                    }
                }
                uiState.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LiquidGlassSurface(
                            cornerRadius = 20.dp,
                            glassAlpha = 0.15f,
                        ) {
                            Column(
                                modifier = Modifier.padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(48.dp))
                                Text(
                                    "No channels found for \"${uiState.query}\"",
                                    style = LiquidTypography.TitleMedium,
                                    color = LiquidColors.TextPrimary,
                                )
                                Text(
                                    "Check your spelling or search by country code, genre, or language",
                                    style = LiquidTypography.BodySmall,
                                    color = LiquidColors.TextTertiary,
                                )
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = "${uiState.results.size} channels found",
                        style = LiquidTypography.LabelMedium,
                        color = LiquidColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.results, key = { it.id }) { channel ->
                            SearchResultGridCard(
                                channel = channel,
                                onClick = { onChannelClick(channel.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultGridCard(
    channel: Channel,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "searchCardScale",
    )

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
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
                    .height(90.dp)
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
                    Icon(Icons.Filled.Tv, null, tint = LiquidColors.TextTertiary, modifier = Modifier.size(32.dp))
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
