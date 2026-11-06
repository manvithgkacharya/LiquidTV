package com.liquidtv.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.ui.components.*
import com.liquidtv.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onChannelClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    AppBackground(
        modifier = Modifier.fillMaxSize(),
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp), // below nav bar
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header
            item {
                HomeHeader(
                    onSearchClick = onSearchClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .padding(top = 8.dp),
                )
            }

            // Hero panel
            state.featuredChannel?.let { channel ->
                item {
                    HeroPanel(
                        channel = channel,
                        onWatchClick = { onChannelClick(channel.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(channel.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .padding(horizontal = 40.dp),
                        focusRequester = initialFocusRequester,
                    )
                }
            }

            // Loading state
            if (state.isLoading && state.recentlyWatched.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(32.dp))
                        LoadingIndicator(
                            message = state.loadingMessage ?: "Loading channels…",
                            progress = state.loadingProgress,
                        )
                    }
                }
            }

            // Continue Watching
            if (state.recentlyWatched.isNotEmpty()) {
                item {
                    ChannelRow(
                        title = "Continue Watching",
                        channels = state.recentlyWatched,
                        onChannelClick = onChannelClick,
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    )
                }
            }

            // Favorites
            if (state.favoriteChannels.isNotEmpty()) {
                item {
                    ChannelRow(
                        title = "My Favorites",
                        channels = state.favoriteChannels,
                        onChannelClick = onChannelClick,
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    )
                }
            }

            // Popular Categories
            if (state.popularCategories.isNotEmpty()) {
                item {
                    CategoryRow(
                        title = "Browse Categories",
                        categories = state.popularCategories,
                        onCategoryClick = { /* handled by parent nav */ },
                    )
                }
            }

            // All Channels preview
            if (state.allChannels.isNotEmpty()) {
                item {
                    ChannelRow(
                        title = "All Channels",
                        channels = state.allChannels.take(20),
                        onChannelClick = onChannelClick,
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    )
                }
            }
        }

        // Error state
        state.error?.let { error ->
            GlassErrorBanner(
                message = error,
                onRetry = { viewModel.refreshPlaylists() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { Calendar.getInstance() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    var currentTime by remember { mutableStateOf(timeFormat.format(now.time)) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormat.format(Calendar.getInstance().time)
            kotlinx.coroutines.delay(30_000)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // App logo / wordmark
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Logo circle with glow
            Box(contentAlignment = Alignment.Center) {
                // Glow behind the logo
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(LiquidColors.AccentBlue.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                            ),
                            CircleShape,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Column {
                Text(
                    text = "LIQUID TV",
                    style = LiquidTypography.HeadlineLarge.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                LiquidColors.TextPrimary,
                                LiquidColors.AccentBlueLight,
                                LiquidColors.AccentVioletLight,
                            )
                        )
                    ),
                )
                Text(
                    text = "Premium Streaming",
                    style = LiquidTypography.LabelSmall,
                    color = LiquidColors.AccentBlue.copy(alpha = 0.7f),
                )
            }
        }

        // Time and date
        Column(horizontalAlignment = Alignment.End) {
            Text(currentTime, style = LiquidTypography.HeadlineMedium, color = LiquidColors.TextPrimary)
            Text(dateFormat.format(Calendar.getInstance().time), style = LiquidTypography.BodySmall, color = LiquidColors.TextSecondary)
        }
    }
}

@Composable
private fun HeroPanel(
    channel: Channel,
    onWatchClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
) {
    val watchInteraction = remember { MutableInteractionSource() }
    val watchFocused by watchInteraction.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 24.dp,
        glassAlpha = 0.18f,
        borderAlpha = 0.3f,
        highlightAlpha = 0.55f,
        accentGlowColor = LiquidColors.AccentBlue,
    ) {
        // Cinematic gradient scrim overlay inside the hero
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.0f),
                        0.4f to Color.Black.copy(alpha = 0.0f),
                        1.0f to Color.Black.copy(alpha = 0.35f),
                    )
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel logo with vivid glow behind it
            Box(contentAlignment = Alignment.Center) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(LiquidColors.AccentBlue.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    LiquidColors.AccentBlue.copy(alpha = 0.18f),
                                    LiquidColors.GlassPrimary,
                                )
                            ),
                            RoundedCornerShape(24.dp),
                        )
                        .border(1.dp, LiquidColors.GlassBorder, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (channel.logoUrl != null) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(Icons.Filled.LiveTv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.5f), modifier = Modifier.size(72.dp))
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    channel.groupTitle?.let { group ->
                        Box(
                            modifier = Modifier
                                .background(LiquidColors.AccentBlue.copy(alpha = 0.18f), RoundedCornerShape(50.dp))
                                .border(1.dp, LiquidColors.AccentBlue.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(group, style = LiquidTypography.LabelMedium, color = LiquidColors.AccentBlueLight)
                        }
                    }
                }
                Text(
                    text = channel.name,
                    style = LiquidTypography.DisplayMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    channel.country?.let { InfoChip(it) }
                    channel.language?.let { InfoChip(it) }
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Watch Now button
                    GlassButton(
                        text = "Watch Now",
                        icon = Icons.Filled.PlayArrow,
                        isPrimary = true,
                        onClick = onWatchClick,
                        interactionSource = watchInteraction,
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                    // Favorite button
                    GlassButton(
                        text = if (channel.isFavorite) "Favorited" else "Favorite",
                        icon = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        isPrimary = false,
                        onClick = onFavoriteClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .background(LiquidColors.GlassPrimary, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = LiquidTypography.LabelSmall, color = LiquidColors.TextSecondary)
    }
}

@Composable
private fun GlassButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "btn_scale")

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .then(
                if (isPrimary) Modifier.background(
                    Brush.linearGradient(listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)),
                    RoundedCornerShape(50.dp),
                ) else Modifier.background(LiquidColors.GlassPrimary, RoundedCornerShape(50.dp))
                    .border(1.dp, if (isFocused) LiquidColors.GlassBorderFocused else LiquidColors.GlassBorder, RoundedCornerShape(50.dp))
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LiquidColors.TextPrimary, modifier = Modifier.size(18.dp))
            Text(text, style = LiquidTypography.LabelLarge, color = LiquidColors.TextPrimary)
        }
    }
}

@Composable
private fun ChannelRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section title with accent bar
        Row(
            modifier = Modifier.padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Text(
                text = title,
                style = LiquidTypography.HeadlineSmall,
                color = LiquidColors.TextPrimary,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                MiniChannelCard(
                    channel = channel,
                    onClick = { onChannelClick(channel.id) },
                    onFavoriteToggle = { onFavoriteToggle(channel.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    categories: List<Category>,
    onCategoryClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section title with accent bar
        Row(
            modifier = Modifier.padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(listOf(LiquidColors.AccentCyan, LiquidColors.AccentBlue)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Text(title, style = LiquidTypography.HeadlineSmall, color = LiquidColors.TextPrimary)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                MiniCategoryCard(category = category, onClick = { onCategoryClick(category.id) })
            }
        }
    }
}

@Composable
private fun MiniChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .width(170.dp)
            .height(195.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassAlpha = if (isFocused) 0.30f else 0.16f,
        borderAlpha = if (isFocused) 0.85f else 0.25f,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            // Logo area with gradient background and bottom scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LiquidColors.AccentBlue.copy(alpha = if (isFocused) 0.20f else 0.08f),
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
                        modifier = Modifier.size(78.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.LiveTv, null, tint = LiquidColors.AccentBlue.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                }
                // Bottom fade-in name scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))
                        ),
                )
                // LIVE badge top-left
                LiveBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }

            // Info
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(channel.name, style = LiquidTypography.TitleSmall, color = LiquidColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                channel.groupTitle?.let {
                    Text(it, style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlue.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MiniCategoryCard(category: Category, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val colors = getCategoryCardColors(category.name)

    Box(
        modifier = Modifier
            .width(150.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.first.copy(alpha = if (isFocused) 0.55f else 0.30f), colors.second.copy(alpha = if (isFocused) 0.45f else 0.20f))
                )
            )
            .border(
                1.dp,
                if (isFocused) colors.first.copy(alpha = 0.9f) else colors.first.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        // Specular highlight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (isFocused) 0.18f else 0.08f), Color.Transparent)
                    )
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(category.name, style = LiquidTypography.TitleSmall, color = LiquidColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${category.channelCount} ch", style = LiquidTypography.LabelSmall, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

/** Returns (startColor, endColor) gradient for a given category name. */
private fun getCategoryCardColors(name: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when {
    name.contains("sport", ignoreCase = true) -> LiquidColors.CategorySports to LiquidColors.CategorySportsEnd
    name.contains("news", ignoreCase = true) -> LiquidColors.CategoryNews to LiquidColors.CategoryNewsEnd
    name.contains("movie", ignoreCase = true) || name.contains("film", ignoreCase = true) || name.contains("cinema", ignoreCase = true) -> LiquidColors.CategoryMovies to LiquidColors.CategoryMoviesEnd
    name.contains("music", ignoreCase = true) -> LiquidColors.CategoryMusic to LiquidColors.CategoryMusicEnd
    name.contains("kid", ignoreCase = true) || name.contains("child", ignoreCase = true) || name.contains("cartoon", ignoreCase = true) -> LiquidColors.CategoryKids to LiquidColors.CategoryKidsEnd
    name.contains("doc", ignoreCase = true) -> LiquidColors.CategoryDoc to LiquidColors.CategoryDocEnd
    name.contains("entertain", ignoreCase = true) || name.contains("general", ignoreCase = true) -> LiquidColors.CategoryEnt to LiquidColors.CategoryEntEnd
    else -> LiquidColors.AccentBlue to LiquidColors.AccentViolet
}

@Composable
private fun LoadingIndicator(message: String, progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            progress = { if (progress > 0f) progress else 0f },
            color = LiquidColors.AccentBlue,
            modifier = Modifier.size(48.dp),
        )
        Text(message, style = LiquidTypography.BodyMedium, color = LiquidColors.TextSecondary)
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                color = LiquidColors.AccentBlue,
                trackColor = LiquidColors.GlassPrimary,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun GlassErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 12.dp,
        glassAlpha = 0.25f,
        borderAlpha = 0.4f,
        glassColor = LiquidColors.ErrorRed,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = LiquidColors.ErrorRed)
            Text(message, style = LiquidTypography.BodyMedium, color = LiquidColors.TextPrimary, modifier = Modifier.weight(1f))
            val retryInteraction = remember { MutableInteractionSource() }
            val retryFocused by retryInteraction.collectIsFocusedAsState()
            Text(
                "Retry",
                style = LiquidTypography.LabelLarge,
                color = LiquidColors.AccentBlue,
                modifier = Modifier
                    .clickable(retryInteraction, null, onClick = onRetry)
                    .focusable(interactionSource = retryInteraction)
                    .alpha(if (retryFocused) 1f else 0.8f),
            )
        }
    }
}

