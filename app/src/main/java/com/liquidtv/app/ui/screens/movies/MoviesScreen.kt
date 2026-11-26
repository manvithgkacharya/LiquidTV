package com.liquidtv.app.ui.screens.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.utils.wideCategoriesSidebarWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    onMovieClick: (String) -> Unit,
    viewModel: MoviesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCategoriesSheet by remember { mutableStateOf(false) }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isWide = maxWidth >= 720.dp
            val isCompactHeight = maxHeight < 520.dp
            val topPad = if (isCompactHeight) 60.dp else 64.dp
            val hPad = when {
                maxWidth >= 1100.dp -> 20.dp
                isWide -> 14.dp
                else -> 12.dp
            }
            val sidebarWidth = wideCategoriesSidebarWidth(maxWidth - hPad * 2)
            val gridMinCardWidth = when {
                isCompactHeight -> 96
                maxWidth >= 1600.dp -> 148
                maxWidth >= 1100.dp -> 124
                isWide -> 108
                else -> 112
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad, start = hPad, end = hPad),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 5.dp else 6.dp),
            ) {
                // Single-line compact header: Title + inline count pill (+ filter button on narrow portrait)
                val colors = AppTheme.colors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .testTag("movies_header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = uiState.selectedCategory
                                ?: if (uiState.favoritesOnly) "الأفلام المفضلة" else "الأفلام",
                            style = (if (isCompactHeight) LiquidTypography.TitleMedium else LiquidTypography.TitleLarge).copy(
                                brush = Brush.linearGradient(
                                    listOf(colors.text, colors.accentText),
                                ),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        LiquidGlassSurface(
                            cornerRadius = 12.dp,
                            glassAlpha = 0.22f,
                            borderAlpha = 0.28f,
                        ) {
                            Text(
                                text = "${uiState.movies.size} فيلم متاح",
                                style = LiquidTypography.LabelSmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("movies_count"),
                            )
                        }
                    }

                    if (!isWide) {
                        Spacer(Modifier.width(8.dp))
                        LiquidGlassSurface(
                            modifier = Modifier
                                .height(38.dp)
                                .clickable { showCategoriesSheet = true },
                            cornerRadius = 19.dp,
                            glassAlpha = 0.28f,
                            borderColor = colors.accent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Icons.Filled.FilterList, contentDescription = "التصنيفات", tint = colors.accentText, modifier = Modifier.size(16.dp))
                                Text("التصنيفات", style = LiquidTypography.LabelMedium, color = colors.text)
                            }
                        }
                    }
                }

                if (!isWide && (uiState.categories.isNotEmpty() || uiState.favoritesCount > 0)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            MovieFilterPill(
                                label = "الكل (${uiState.totalCount})",
                                selected = uiState.selectedCategory == null && !uiState.favoritesOnly,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        item {
                            MovieFilterPill(
                                label = "المفضلة (${uiState.favoritesCount})",
                                selected = uiState.favoritesOnly,
                                onClick = { viewModel.selectFavoritesOnly() },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            MovieFilterPill(
                                label = "${cat.name} (${cat.channelCount})",
                                selected = !uiState.favoritesOnly && uiState.selectedCategory.equals(cat.name, ignoreCase = true),
                                onClick = { viewModel.selectCategory(cat.name) },
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.movies.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else if (uiState.error != null && uiState.movies.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LiquidGlassSurface(cornerRadius = 20.dp, glassAlpha = 0.18f) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(44.dp))
                                Text(uiState.error.orEmpty(), style = LiquidTypography.TitleMedium, color = colors.text)
                                Button(
                                    onClick = viewModel::refresh,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                ) {
                                    Text("إعادة المحاولة")
                                }
                            }
                        }
                    }
                } else if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 6.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 12.dp),
                    ) {
                        MoviesCategoriesSidebar(
                            categories = uiState.categories,
                            totalCount = uiState.totalCount,
                            favoritesCount = uiState.favoritesCount,
                            selectedCategory = uiState.selectedCategory,
                            favoritesOnly = uiState.favoritesOnly,
                            compactHeight = isCompactHeight,
                            onSelectAll = { viewModel.selectCategory(null) },
                            onSelectFavorites = { viewModel.selectFavoritesOnly() },
                            onSelectCategory = { viewModel.selectCategory(it) },
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                        )

                        MoviesGridArea(
                            movies = uiState.movies,
                            onMovieClick = onMovieClick,
                            onFavoriteToggle = viewModel::toggleFavorite,
                            contentPadding = PaddingValues(bottom = 12.dp),
                            minCardWidth = gridMinCardWidth,
                            compactHeight = isCompactHeight,
                            fixedColumns = 5,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    MoviesGridArea(
                        movies = uiState.movies,
                        onMovieClick = onMovieClick,
                        onFavoriteToggle = viewModel::toggleFavorite,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        minCardWidth = gridMinCardWidth,
                        compactHeight = isCompactHeight,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    )
                }
            }
        }

        if (showCategoriesSheet) {
            val colors = AppTheme.colors
            ModalBottomSheet(
                onDismissRequest = { showCategoriesSheet = false },
                containerColor = colors.panel,
                contentColor = colors.text,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("تصنيفات الأفلام", style = LiquidTypography.TitleLarge, color = colors.text)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            MovieSidebarRow(
                                label = "جميع الأفلام",
                                count = uiState.totalCount,
                                isSelected = uiState.selectedCategory == null && !uiState.favoritesOnly,
                                compact = false,
                                onClick = {
                                    viewModel.selectCategory(null)
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        item {
                            MovieSidebarRow(
                                label = "المفضلة",
                                count = uiState.favoritesCount,
                                isSelected = uiState.favoritesOnly,
                                compact = false,
                                onClick = {
                                    viewModel.selectFavoritesOnly()
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            MovieSidebarRow(
                                label = cat.name,
                                count = cat.channelCount,
                                isSelected = !uiState.favoritesOnly && uiState.selectedCategory.equals(cat.name, ignoreCase = true),
                                compact = false,
                                onClick = {
                                    viewModel.selectCategory(cat.name)
                                    showCategoriesSheet = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviesCategoriesSidebar(
    categories: List<Category>,
    totalCount: Int,
    favoritesCount: Int,
    selectedCategory: String?,
    favoritesOnly: Boolean,
    compactHeight: Boolean,
    onSelectAll: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 20.dp,
        glassAlpha = 0.18f,
        borderAlpha = 0.28f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = if (compactHeight) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 5.dp),
        ) {
            Text(
                text = "التصنيفات",
                style = if (compactHeight) LiquidTypography.TitleSmall else LiquidTypography.TitleMedium,
                color = colors.text,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = if (compactHeight) 0.dp else 2.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("movies_categories_sidebar"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "__all__") {
                    MovieSidebarRow(
                        label = "جميع الأفلام",
                        count = totalCount,
                        isSelected = selectedCategory == null && !favoritesOnly,
                        compact = compactHeight,
                        onClick = onSelectAll,
                    )
                }
                item(key = "__fav__") {
                    MovieSidebarRow(
                        label = "المفضلة",
                        count = favoritesCount,
                        isSelected = favoritesOnly,
                        compact = compactHeight,
                        onClick = onSelectFavorites,
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    MovieSidebarRow(
                        label = cat.name,
                        count = cat.channelCount,
                        isSelected = !favoritesOnly && selectedCategory.equals(cat.name, ignoreCase = true),
                        compact = compactHeight,
                        onClick = { onSelectCategory(cat.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieSidebarRow(
    label: String,
    count: Int,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 36.dp else 38.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 11.dp,
        glassColor = if (isSelected) colors.selected else colors.surface,
        glassAlpha = if (isSelected) 0.92f else if (isFocused) 0.80f else 0.55f,
        borderAlpha = if (isFocused) 0.95f else if (isSelected) 0.75f else 0.28f,
        borderColor = if (isFocused) colors.focus else if (isSelected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = if (compact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelLarge,
                color = if (isSelected) colors.text else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = LiquidTypography.LabelSmall,
                color = if (isSelected) colors.accentText else colors.muted,
            )
        }
    }
}

@Composable
private fun MoviesGridArea(
    movies: List<Channel>,
    onMovieClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    contentPadding: PaddingValues,
    minCardWidth: Int,
    compactHeight: Boolean,
    fixedColumns: Int? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    if (movies.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LiquidGlassSurface(
                modifier = Modifier.padding(24.dp),
                cornerRadius = 20.dp,
                glassAlpha = 0.18f,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Movie, null, tint = colors.accentText, modifier = Modifier.size(46.dp))
                    Text("لا توجد أفلام متاحة", style = LiquidTypography.TitleLarge, color = colors.text)
                    Text(
                        text = "لا يحتوي هذا الاشتراك على أفلام، أو لا توجد نتائج ضمن التصنيف المختار.",
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        return
    }

    val gridGap = if (compactHeight) 8.dp else 10.dp
    LazyVerticalGrid(
        columns = if (fixedColumns != null) GridCells.Fixed(fixedColumns) else GridCells.Adaptive(minSize = minCardWidth.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gridGap),
        verticalArrangement = Arrangement.spacedBy(gridGap),
        modifier = modifier.testTag("movies_grid"),
    ) {
        items(movies, key = { it.id }) { movie ->
            MovieCinemaPosterCard(
                movie = movie,
                compactHeight = compactHeight,
                onClick = { onMovieClick(movie.id) },
                onFavoriteToggle = { onFavoriteToggle(movie.id) },
            )
        }
    }
}

@Composable
private fun MovieFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .height(36.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 18.dp,
        glassColor = if (selected) colors.selected else colors.surface,
        glassAlpha = if (selected) 0.92f else if (isFocused) 0.80f else 0.65f,
        borderAlpha = if (isFocused) 0.95f else if (selected) 0.75f else 0.35f,
        borderColor = if (isFocused) colors.focus else if (selected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelMedium,
                color = if (selected) colors.text else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MovieCinemaPosterCard(
    movie: Channel,
    compactHeight: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassColor = colors.card,
        glassAlpha = if (isFocused) 0.94f else 0.80f,
        borderAlpha = if (isFocused) 0.95f else 0.40f,
        borderColor = if (isFocused) colors.focus else colors.border,
        isFocused = isFocused,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Strict 2:3 Cinema Poster Ratio — never squashed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(colors.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!movie.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.logoUrl,
                        contentDescription = movie.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = colors.accentText.copy(alpha = 0.55f),
                        modifier = Modifier.size(44.dp),
                    )
                }

                // Bottom Cinema Gradient Scrim (kept dark for poster legibility)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        ),
                )

                movie.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "★ $rating",
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            color = LiquidColors.AccentGold,
                        )
                    }
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = if (movie.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (movie.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (movie.isFavorite) colors.danger else Color.White,
                    )
                }

                if (movie.resumePositionMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(7.dp)
                            .background(colors.accent.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "متابعة",
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onAccent,
                        )
                    }
                }

                movie.releaseYear?.takeIf { it.isNotBlank() && it != "0" }?.let { year ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(7.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = year.take(4),
                            style = LiquidTypography.LabelSmall,
                            color = Color.White,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = if (compactHeight) 4.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = movie.name,
                    style = LiquidTypography.TitleSmall,
                    color = colors.text,
                    maxLines = if (compactHeight) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                movie.groupTitle?.takeIf { it.isNotBlank() }?.let { group ->
                    Text(
                        text = group,
                        style = LiquidTypography.LabelSmall,
                        color = colors.accentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
