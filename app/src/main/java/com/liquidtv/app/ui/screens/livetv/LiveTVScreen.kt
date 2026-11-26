package com.liquidtv.app.ui.screens.livetv

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.liquidtv.app.domain.model.ChannelEpgSummary
import com.liquidtv.app.domain.model.ChannelFilter
import com.liquidtv.app.domain.model.SortOrder
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.LiveBadge
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.utils.wideCategoriesSidebarWidth
import com.liquidtv.app.ui.utils.calculateEpgProgress
import com.liquidtv.app.ui.utils.formatEpgTimeRange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTVScreen(
    filterCategory: String? = null,
    filterLanguage: String? = null,
    filterCountry: String? = null,
    onChannelClick: (String) -> Unit,
    viewModel: LiveTVViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCategoriesSheet by remember { mutableStateOf(false) }

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
            val paneSpacing = if (maxWidth >= 1000.dp) 12.dp else 8.dp
            val categoriesWidth = wideCategoriesSidebarWidth(maxWidth - hPad * 2)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad, start = hPad, end = hPad),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 5.dp else 6.dp),
            ) {
                LiveTVHeader(
                    filterTitle = filterLanguage ?: filterCountry ?: uiState.filter.category
                        ?: if (uiState.filter.showFavoritesOnly) "القنوات المفضلة" else "البث المباشر",
                    channelsCount = uiState.channels.size,
                    sortOrder = uiState.filter.sortOrder,
                    viewMode = uiState.viewMode,
                    isWide = isWide,
                    compactHeight = isCompactHeight,
                    onOpenCategoriesSheet = { showCategoriesSheet = true },
                    onSortSelected = viewModel::setSortOrder,
                    onViewModeSelected = viewModel::setViewMode,
                )

                if (uiState.isLoading && uiState.channels.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppTheme.colors.accent)
                    }
                } else if (uiState.error != null && uiState.channels.isEmpty()) {
                    val colors = AppTheme.colors
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
                    // Wide TV / Tablet / Landscape 3-Pane Liquid Glass Layout (~20% / 34% / 46%):
                    // Categories Sidebar | Channels List/Grid | Channel Info + EPG Preview
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 6.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(paneSpacing),
                    ) {
                        LiveCategoriesSidebar(
                            categories = uiState.categories,
                            totalCount = uiState.totalChannelsCount,
                            favoritesCount = uiState.favoritesCount,
                            selectedCategory = uiState.filter.category,
                            favoritesOnly = uiState.filter.showFavoritesOnly,
                            compactHeight = isCompactHeight,
                            onSelectAll = { viewModel.selectCategory(null) },
                            onSelectFavorites = { viewModel.selectFavoritesFilter() },
                            onSelectCategory = { viewModel.selectCategory(it) },
                            modifier = Modifier
                                .width(categoriesWidth)
                                .fillMaxHeight(),
                        )

                        LiquidGlassSurface(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .testTag("live_channels_pane"),
                            cornerRadius = 20.dp,
                            glassAlpha = 0.12f,
                            borderAlpha = 0.20f,
                        ) {
                            LiveChannelsPane(
                                channels = uiState.channels,
                                selectedChannelId = uiState.selectedChannel?.id,
                                viewMode = uiState.viewMode,
                                compactHeight = isCompactHeight,
                                onChannelFocused = viewModel::selectChannelForPreview,
                                onChannelClicked = { channel ->
                                    if (uiState.selectedChannel?.id == channel.id) {
                                        onChannelClick(channel.id)
                                    } else {
                                        viewModel.selectChannelForPreview(channel)
                                    }
                                },
                                onChannelPlayDirect = { channel -> onChannelClick(channel.id) },
                                onFavoriteToggle = viewModel::toggleFavorite,
                                contentPadding = PaddingValues(if (isCompactHeight) 5.dp else 6.dp),
                            )
                        }

                        LiveChannelInfoEpgPane(
                            channel = uiState.selectedChannel,
                            epg = uiState.selectedEpg,
                            compactHeight = isCompactHeight,
                            onPlayClick = { ch -> onChannelClick(ch.id) },
                            onFavoriteToggle = { ch -> viewModel.toggleFavorite(ch.id) },
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight()
                                .testTag("live_preview_pane"),
                        )
                    }
                } else {
                    // Compact Portrait Phone Layout: Quick filter pills + selected EPG strip + full-height channel list
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 1.dp),
                    ) {
                        item {
                            CompactFilterChip(
                                label = "الكل (${uiState.totalChannelsCount})",
                                selected = uiState.filter.category == null && !uiState.filter.showFavoritesOnly,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        item {
                            CompactFilterChip(
                                label = "المفضلة (${uiState.favoritesCount})",
                                selected = uiState.filter.showFavoritesOnly,
                                onClick = { viewModel.selectFavoritesFilter() },
                            )
                        }
                        items(uiState.categories.take(12), key = { it.id }) { cat ->
                            CompactFilterChip(
                                label = "${cat.name} (${cat.channelCount})",
                                selected = uiState.filter.category.equals(cat.name, ignoreCase = true),
                                onClick = { viewModel.selectCategory(cat.name) },
                            )
                        }
                    }

                    uiState.selectedChannel?.let { selected ->
                        val cur = uiState.selectedEpg.current
                        if (cur != null) {
                            CompactSelectedEpgStrip(
                                channel = selected,
                                epg = uiState.selectedEpg,
                                onPlayClick = { onChannelClick(selected.id) },
                            )
                        }
                    }

                    LiveChannelsPane(
                        channels = uiState.channels,
                        selectedChannelId = uiState.selectedChannel?.id,
                        viewMode = uiState.viewMode,
                        compactHeight = false,
                        onChannelFocused = viewModel::selectChannelForPreview,
                        onChannelClicked = { ch -> onChannelClick(ch.id) },
                        onChannelPlayDirect = { ch -> onChannelClick(ch.id) },
                        onFavoriteToggle = viewModel::toggleFavorite,
                        contentPadding = PaddingValues(bottom = 16.dp),
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
                    Text(
                        text = "تصنيفات القنوات",
                        style = LiquidTypography.TitleLarge,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            CategorySidebarRow(
                                label = "جميع القنوات",
                                count = uiState.totalChannelsCount,
                                isSelected = uiState.filter.category == null && !uiState.filter.showFavoritesOnly,
                                compact = false,
                                onClick = {
                                    viewModel.selectCategory(null)
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        item {
                            CategorySidebarRow(
                                label = "المفضلة",
                                count = uiState.favoritesCount,
                                isSelected = uiState.filter.showFavoritesOnly,
                                compact = false,
                                onClick = {
                                    viewModel.selectFavoritesFilter()
                                    showCategoriesSheet = false
                                },
                            )
                        }
                        items(uiState.categories, key = { it.id }) { cat ->
                            CategorySidebarRow(
                                label = cat.name,
                                count = cat.channelCount,
                                isSelected = uiState.filter.category.equals(cat.name, ignoreCase = true),
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
private fun LiveTVHeader(
    filterTitle: String,
    channelsCount: Int,
    sortOrder: SortOrder,
    viewMode: ViewMode,
    isWide: Boolean,
    compactHeight: Boolean,
    onOpenCategoriesSheet: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onViewModeSelected: (ViewMode) -> Unit,
) {
    val colors = AppTheme.colors
    val sortOrders = remember {
        listOf(
            "أ - ي" to SortOrder.NAME_ASC,
            "ي - أ" to SortOrder.NAME_DESC,
            "المفضلة" to SortOrder.FAVORITES_FIRST,
            "التصنيف" to SortOrder.CATEGORY,
        )
    }

    if (isWide) {
        // Unified single-row bar: Title + Count | Sort Pills | Circular ViewMode toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compactHeight) 36.dp else 40.dp)
                .testTag("live_tv_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = filterTitle,
                    style = (if (compactHeight) LiquidTypography.TitleMedium else LiquidTypography.TitleLarge).copy(
                        brush = Brush.linearGradient(
                            listOf(colors.text, colors.accentText),
                        ),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                LiquidGlassSurface(
                    cornerRadius = 10.dp,
                    glassAlpha = 0.22f,
                    borderAlpha = 0.28f,
                ) {
                    Text(
                        text = "$channelsCount قناة متاحة",
                        style = LiquidTypography.LabelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("live_channels_count"),
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(sortOrders, key = { it.second.name }) { (label, order) ->
                    CompactFilterChip(
                        label = label,
                        selected = sortOrder == order,
                        onClick = { onSortSelected(order) },
                        compact = compactHeight,
                        modifier = Modifier.testTag("live_sort_${order.name.lowercase()}"),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewModeIconButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = "عرض القائمة",
                    selected = viewMode == ViewMode.LIST,
                    compact = compactHeight,
                    onClick = { onViewModeSelected(ViewMode.LIST) },
                    modifier = Modifier.testTag("live_view_list"),
                )
                ViewModeIconButton(
                    icon = Icons.Filled.GridView,
                    contentDescription = "عرض الشبكة",
                    selected = viewMode == ViewMode.GRID,
                    compact = compactHeight,
                    onClick = { onViewModeSelected(ViewMode.GRID) },
                    modifier = Modifier.testTag("live_view_grid"),
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_tv_header"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = filterTitle,
                        style = LiquidTypography.TitleLarge.copy(
                            brush = Brush.linearGradient(
                                listOf(colors.text, colors.accentText),
                            ),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    LiquidGlassSurface(
                        cornerRadius = 10.dp,
                        glassAlpha = 0.22f,
                        borderAlpha = 0.28f,
                    ) {
                        Text(
                            text = "$channelsCount قناة متاحة",
                            style = LiquidTypography.LabelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .testTag("live_channels_count"),
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidGlassSurface(
                        modifier = Modifier
                            .height(38.dp)
                            .clickable(onClick = onOpenCategoriesSheet),
                        cornerRadius = 19.dp,
                        glassAlpha = 0.28f,
                        borderColor = colors.accent,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = "التصنيفات", tint = colors.accentText, modifier = Modifier.size(16.dp))
                            Text("التصنيفات", style = LiquidTypography.LabelMedium, color = colors.text)
                        }
                    }

                    ViewModeIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        contentDescription = "عرض القائمة",
                        selected = viewMode == ViewMode.LIST,
                        onClick = { onViewModeSelected(ViewMode.LIST) },
                        modifier = Modifier.testTag("live_view_list"),
                    )
                    ViewModeIconButton(
                        icon = Icons.Filled.GridView,
                        contentDescription = "عرض الشبكة",
                        selected = viewMode == ViewMode.GRID,
                        onClick = { onViewModeSelected(ViewMode.GRID) },
                        modifier = Modifier.testTag("live_view_grid"),
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sortOrders, key = { it.second.name }) { (label, order) ->
                    CompactFilterChip(
                        label = label,
                        selected = sortOrder == order,
                        onClick = { onSortSelected(order) },
                        modifier = Modifier.testTag("live_sort_${order.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val btnSize = if (compact) 36.dp else 40.dp
    LiquidGlassSurface(
        modifier = modifier
            .size(btnSize)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = btnSize / 2,
        glassColor = if (selected) colors.selected else colors.surface,
        glassAlpha = if (selected) 0.92f else 0.72f,
        borderAlpha = if (isFocused) 0.95f else if (selected) 0.75f else 0.45f,
        borderColor = if (isFocused) colors.focus else if (selected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) colors.accentText else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LiveCategoriesSidebar(
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
        modifier = modifier.testTag("live_categories_pane"),
        cornerRadius = 20.dp,
        glassAlpha = 0.18f,
        borderAlpha = 0.28f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = if (compactHeight) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "التصنيفات",
                style = if (compactHeight) LiquidTypography.TitleSmall else LiquidTypography.TitleMedium,
                color = colors.text,
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("live_categories_sidebar"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "__all__") {
                    CategorySidebarRow(
                        label = "جميع القنوات",
                        count = totalCount,
                        isSelected = selectedCategory == null && !favoritesOnly,
                        compact = compactHeight,
                        onClick = onSelectAll,
                    )
                }
                item(key = "__fav__") {
                    CategorySidebarRow(
                        label = "المفضلة",
                        count = favoritesCount,
                        isSelected = favoritesOnly,
                        compact = compactHeight,
                        onClick = onSelectFavorites,
                    )
                }
                items(categories, key = { it.id }) { category ->
                    CategorySidebarRow(
                        label = category.name,
                        count = category.channelCount,
                        isSelected = !favoritesOnly && selectedCategory.equals(category.name, ignoreCase = true),
                        compact = compactHeight,
                        onClick = { onSelectCategory(category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySidebarRow(
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
private fun LiveChannelsPane(
    channels: List<Channel>,
    selectedChannelId: String?,
    viewMode: ViewMode,
    compactHeight: Boolean,
    onChannelFocused: (Channel) -> Unit,
    onChannelClicked: (Channel) -> Unit,
    onChannelPlayDirect: (Channel) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        val colors = AppTheme.colors
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Tv, null, tint = colors.muted, modifier = Modifier.size(46.dp))
                Text("لا توجد قنوات مطابقة", style = LiquidTypography.TitleLarge, color = colors.text)
                Text("جرّب اختيار تصنيف آخر أو تحديث القائمة من الإعدادات.", style = LiquidTypography.BodySmall, color = colors.textSecondary)
            }
        }
        return
    }

    when (viewMode) {
        ViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (compactHeight) 115.dp else 132.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
                    .fillMaxSize()
                    .testTag("live_channels_grid"),
            ) {
                items(channels, key = { it.id }) { channel ->
                    LiveTVGridCard(
                        channel = channel,
                        isSelected = channel.id == selectedChannelId,
                        compactHeight = compactHeight,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelPlayDirect(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel.id) },
                    )
                }
            }
        }
        ViewMode.LIST -> {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier
                    .fillMaxSize()
                    .testTag("live_channels_list"),
            ) {
                items(channels.size, key = { idx -> channels[idx].id }) { idx ->
                    val channel = channels[idx]
                    LiveTVListRow(
                        index = idx + 1,
                        channel = channel,
                        isSelected = channel.id == selectedChannelId,
                        compactHeight = compactHeight,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelClicked(channel) },
                        onPlayClick = { onChannelPlayDirect(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChannelInfoEpgPane(
    channel: Channel?,
    epg: ChannelEpgSummary,
    compactHeight: Boolean,
    onPlayClick: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 20.dp,
        glassAlpha = 0.20f,
        borderAlpha = 0.28f,
    ) {
        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "اختر قناة لعرض معلوماتها ودليل البرامج",
                    style = LiquidTypography.BodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@LiquidGlassSurface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compactHeight) 10.dp else 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 12.dp),
        ) {
            // Uncropped channel logo showcase
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactHeight) 76.dp else 118.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.16f),
                                colors.surface.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (compactHeight) 8.dp else 12.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = colors.accentText, modifier = Modifier.size(if (compactHeight) 34.dp else 44.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    LiveBadge()
                }
                IconButton(
                    onClick = { onFavoriteToggle(channel) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp),
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (channel.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (channel.isFavorite) colors.danger else colors.textSecondary,
                    )
                }
            }

            // Channel title & metadata
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    style = if (compactHeight) LiquidTypography.TitleMedium else LiquidTypography.TitleLarge,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    channel.groupTitle?.takeIf { it.isNotBlank() }?.let { group ->
                        Text(group, style = LiquidTypography.LabelSmall, color = colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    channel.country?.takeIf { it.isNotBlank() }?.let { country ->
                        Text("• $country", style = LiquidTypography.LabelSmall, color = colors.muted, maxLines = 1)
                    }
                    channel.language?.takeIf { it.isNotBlank() }?.let { lang ->
                        Text("• $lang", style = LiquidTypography.LabelSmall, color = colors.muted, maxLines = 1)
                    }
                }
            }

            // Play CTA
            Button(
                onClick = { onPlayClick(channel) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactHeight) 40.dp else 46.dp)
                    .testTag("live_preview_play_button"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("تشغيل القناة", style = LiquidTypography.LabelLarge.copy(fontWeight = FontWeight.Bold))
            }

            // EPG Current & Next
            LiquidGlassSurface(
                cornerRadius = 14.dp,
                glassAlpha = 0.16f,
                borderAlpha = 0.25f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (compactHeight) 10.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 8.dp),
                ) {
                    Text(
                        text = "دليل البرامج المباشر",
                        style = LiquidTypography.TitleSmall,
                        color = colors.accentText,
                    )

                    val current = epg.current
                    val next = epg.next

                    if (current == null && next == null) {
                        Text(
                            text = "لا تتوفر بيانات دليل البرامج لهذه القناة حالياً",
                            style = LiquidTypography.BodySmall,
                            color = colors.muted,
                        )
                    } else {
                        current?.let { prog ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("يُعرض الآن", style = LiquidTypography.LabelSmall, color = colors.accentText)
                                    Text(
                                        text = formatEpgTimeRange(prog.startTimeMs, prog.endTimeMs),
                                        style = LiquidTypography.LabelSmall,
                                        color = colors.muted,
                                    )
                                }
                                Text(
                                    text = prog.title,
                                    style = LiquidTypography.TitleSmall,
                                    color = colors.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                prog.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = LiquidTypography.BodySmall,
                                        color = colors.textSecondary,
                                        maxLines = if (compactHeight) 2 else 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { calculateEpgProgress(prog.startTimeMs, prog.endTimeMs) },
                                    color = colors.accent,
                                    trackColor = colors.border.copy(alpha = 0.30f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            }
                        }

                        next?.let { prog ->
                            HorizontalDivider(color = colors.border.copy(alpha = 0.28f))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("التالي", style = LiquidTypography.LabelSmall, color = colors.accentText)
                                    Text(
                                        text = formatEpgTimeRange(prog.startTimeMs, prog.endTimeMs),
                                        style = LiquidTypography.LabelSmall,
                                        color = colors.muted,
                                    )
                                }
                                Text(
                                    text = prog.title,
                                    style = LiquidTypography.BodySmall,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
private fun CompactSelectedEpgStrip(
    channel: Channel,
    epg: ChannelEpgSummary,
    onPlayClick: () -> Unit,
) {
    val current = epg.current ?: return
    val colors = AppTheme.colors
    LiquidGlassSurface(
        cornerRadius = 14.dp,
        glassAlpha = 0.22f,
        borderColor = colors.accent.copy(alpha = 0.65f),
        onClick = onPlayClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveBadge()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${channel.name} • ${current.title}",
                    style = LiquidTypography.LabelMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatEpgTimeRange(current.startTimeMs, current.endTimeMs),
                    style = LiquidTypography.LabelSmall,
                    color = colors.muted,
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = "تشغيل", tint = colors.accentText)
        }
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val chipHeight = if (compact) 32.dp else 34.dp
    LiquidGlassSurface(
        modifier = modifier
            .height(chipHeight)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = chipHeight / 2,
        glassColor = if (selected) colors.selected else colors.surface,
        glassAlpha = if (selected) 0.92f else if (isFocused) 0.80f else 0.65f,
        borderAlpha = if (isFocused) 0.95f else if (selected) 0.75f else 0.35f,
        borderColor = if (isFocused) colors.focus else if (selected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 11.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelMedium,
                color = if (selected) colors.text else colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LiveTVGridCard(
    channel: Channel,
    isSelected: Boolean,
    compactHeight: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compactHeight) 136.dp else 156.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassColor = if (isSelected) colors.selected else colors.card,
        glassAlpha = if (isFocused || isSelected) 0.88f else 0.72f,
        borderAlpha = if (isFocused || isSelected) 0.92f else 0.38f,
        borderColor = if (isFocused) colors.focus else if (isSelected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.accent.copy(alpha = if (isFocused) 0.18f else 0.08f),
                                colors.surface.copy(alpha = 0.85f),
                            ),
                        ),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Tv, null, tint = colors.accentText.copy(alpha = 0.55f), modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    LiveBadge()
                }
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "المفضلة",
                        tint = if (channel.isFavorite) colors.danger else colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
                    color = if (isFocused || isSelected) colors.accentText else colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveTVListRow(
    index: Int,
    channel: Channel,
    isSelected: Boolean,
    compactHeight: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val displayNumber = String.format(Locale.US, "%03d", channel.channelNumber ?: index)

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactHeight) 42.dp else 44.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 12.dp,
        glassColor = if (isSelected) colors.selected else colors.surface,
        glassAlpha = if (isFocused || isSelected) 0.90f else 0.65f,
        borderAlpha = if (isFocused) 0.95f else if (isSelected) 0.72f else 0.30f,
        borderColor = if (isFocused) colors.focus else if (isSelected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val showLiveBadgeInline = maxWidth >= 320.dp
            val showPlayIcon = maxWidth >= 280.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Channel Number Badge
                Box(
                    modifier = Modifier
                        .widthIn(min = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.panel.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayNumber,
                        style = LiquidTypography.LabelSmall,
                        color = if (isFocused || isSelected) colors.accentText else colors.muted,
                    )
                }

                // Uncropped Logo Container
                Box(
                    modifier = Modifier
                        .size(if (compactHeight) 32.dp else 34.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.accent.copy(alpha = if (isFocused) 0.22f else 0.12f),
                                    colors.panel.copy(alpha = 0.85f),
                                ),
                            ),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(Icons.Filled.Tv, null, tint = colors.accentText.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }

                // Channel Name & Metadata always receive guaranteed flexible width
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(
                        text = channel.name,
                        style = LiquidTypography.TitleSmall,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        channel.groupTitle?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = LiquidTypography.LabelSmall,
                                color = colors.accentText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        channel.language?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = LiquidTypography.LabelSmall, color = colors.muted, maxLines = 1)
                        }
                        channel.country?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = LiquidTypography.LabelSmall, color = colors.muted, maxLines = 1)
                        }
                    }
                }

                if (showLiveBadgeInline) {
                    LiveBadge()
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (channel.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (channel.isFavorite) colors.danger else colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (showPlayIcon) {
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.22f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "تشغيل القناة",
                            tint = colors.accentText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
