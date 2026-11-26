package com.liquidtv.app.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.ParentalPinDialog
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (String) -> Unit,
    onLanguagesClick: () -> Unit = {},
    onCountriesClick: () -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var pendingProtectedCategory by remember { mutableStateOf<Category?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val colors = AppTheme.colors
            val isCompact = maxWidth < 600.dp
            val hPad = if (isCompact) 16.dp else 36.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (isCompact) 76.dp else 88.dp, start = hPad, end = hPad),
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column {
                        Text(
                            text = "تصفح التصنيفات",
                            style = LiquidTypography.HeadlineMedium.copy(
                                brush = Brush.linearGradient(
                                    listOf(colors.text, colors.accentText)
                                )
                            ),
                        )
                        Text(
                            text = if (uiState.isLoading) {
                                uiState.loadingMessage ?: "جارٍ تحميل التصنيفات…"
                            } else {
                                "${uiState.categories.size} تصنيف متاح"
                            },
                            style = LiquidTypography.BodySmall,
                            color = colors.muted,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeaderQuickPill(
                            label = "اللغات",
                            icon = Icons.Filled.Language,
                            onClick = onLanguagesClick,
                        )
                        HeaderQuickPill(
                            label = "الدول",
                            icon = Icons.Filled.Public,
                            onClick = onCountriesClick,
                        )
                    }
                }

                if (uiState.isLoading && uiState.categories.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = colors.accent)
                            Text(
                                text = uiState.loadingMessage ?: "جارٍ فهرسة التصنيفات…",
                                style = LiquidTypography.BodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                } else if (uiState.categories.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "لا توجد تصنيفات متاحة في القائمة النشطة",
                                style = LiquidTypography.HeadlineSmall,
                                color = colors.textSecondary,
                            )
                            LiquidGlassSurface(
                                modifier = Modifier
                                    .clickable { viewModel.refresh() },
                                cornerRadius = 14.dp,
                                glassAlpha = 0.28f,
                                borderColor = colors.accent,
                            ) {
                                Text(
                                    "تحديث القائمة",
                                    style = LiquidTypography.LabelLarge,
                                    color = colors.accentText,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isCompact) 160.dp else 230.dp),
                        contentPadding = PaddingValues(bottom = if (isCompact) 96.dp else 32.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.categories, key = { it.id }) { category ->
                            val isLocked = viewModel.isCategoryLocked(category)
                            CategoryGridTile(
                                category = category,
                                isLocked = isLocked,
                                onClick = {
                                    if (isLocked) {
                                        pinError = null
                                        pendingProtectedCategory = category
                                    } else {
                                        onCategoryClick(category.name)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            ParentalPinDialog(
                visible = pendingProtectedCategory != null,
                title = "التصنيف مقفل",
                subtitle = "أدخل رمز الرقابة الأبوية لفتح \"${pendingProtectedCategory?.name.orEmpty()}\"",
                confirmLabel = "فتح",
                errorMessage = pinError,
                onConfirm = { enteredPin ->
                    val target = pendingProtectedCategory ?: return@ParentalPinDialog
                    scope.launch {
                        if (viewModel.verifyPin(enteredPin)) {
                            pendingProtectedCategory = null
                            pinError = null
                            onCategoryClick(target.name)
                        } else {
                            pinError = "رمز التحقق غير صحيح، حاول مرة أخرى."
                        }
                    }
                },
                onDismiss = {
                    pendingProtectedCategory = null
                    pinError = null
                },
            )
        }
    }
}

@Composable
private fun HeaderQuickPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .height(48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 24.dp,
        glassAlpha = if (isFocused) 0.34f else 0.18f,
        borderAlpha = if (isFocused) 0.90f else 0.30f,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = colors.accentText, modifier = Modifier.size(18.dp))
            Text(label, style = LiquidTypography.LabelMedium, color = colors.text)
        }
    }
}

@Composable
private fun CategoryGridTile(
    category: Category,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val icon = getCategoryIcon(category.name)
    val catColors = getCategoryColors(category.name)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card.copy(alpha = if (colors.isLight) 0.94f else 0.78f))
            .background(
                Brush.linearGradient(
                    listOf(
                        catColors.first.copy(alpha = if (isFocused) 0.32f else 0.16f),
                        catColors.second.copy(alpha = if (isFocused) 0.26f else 0.10f),
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else icon,
                contentDescription = null,
                tint = colors.text.copy(alpha = 0.06f),
                modifier = Modifier.size(68.dp).padding(end = 8.dp, bottom = 8.dp),
            )
        }

        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(colors.danger.copy(alpha = 0.92f), CircleShape)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "تصنيف مقفل",
                    tint = colors.onDanger,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                catColors.first.copy(alpha = if (isFocused) 0.90f else 0.72f),
                                catColors.second.copy(alpha = if (isFocused) 0.78f else 0.56f),
                            )
                        ),
                        CircleShape,
                    )
                    .border(1.dp, colors.border.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else icon,
                    contentDescription = category.name,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = category.name,
                    style = LiquidTypography.TitleMedium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isLocked) "محمي برمز • ${category.channelCount} قناة" else "${category.channelCount} قناة",
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused) colors.accentText else colors.textSecondary,
                )
            }
        }
    }
}

private fun getCategoryColors(name: String): Pair<Color, Color> = when {
    name.contains("news", ignoreCase = true) || name.contains("أخبار") -> LiquidColors.CategoryNews to LiquidColors.CategoryNewsEnd
    name.contains("sport", ignoreCase = true) || name.contains("رياض") -> LiquidColors.CategorySports to LiquidColors.CategorySportsEnd
    name.contains("movie", ignoreCase = true) || name.contains("film", ignoreCase = true) || name.contains("cinema", ignoreCase = true) || name.contains("anim", ignoreCase = true) || name.contains("أفلام") -> LiquidColors.CategoryMovies to LiquidColors.CategoryMoviesEnd
    name.contains("music", ignoreCase = true) || name.contains("موسيق") -> LiquidColors.CategoryMusic to LiquidColors.CategoryMusicEnd
    name.contains("kid", ignoreCase = true) || name.contains("child", ignoreCase = true) || name.contains("cartoon", ignoreCase = true) || name.contains("أطفال") -> LiquidColors.CategoryKids to LiquidColors.CategoryKidsEnd
    name.contains("doc", ignoreCase = true) || name.contains("وثائق") -> LiquidColors.CategoryDoc to LiquidColors.CategoryDocEnd
    name.contains("entertain", ignoreCase = true) || name.contains("general", ignoreCase = true) || name.contains("ترفيه") -> LiquidColors.CategoryEnt to LiquidColors.CategoryEntEnd
    name.contains("education", ignoreCase = true) || name.contains("school", ignoreCase = true) -> LiquidColors.AccentTeal to LiquidColors.AccentGreen
    name.contains("cook", ignoreCase = true) || name.contains("food", ignoreCase = true) -> LiquidColors.AccentGold to LiquidColors.AccentOrange
    name.contains("travel", ignoreCase = true) -> LiquidColors.AccentCyan to LiquidColors.AccentTeal
    name.contains("weather", ignoreCase = true) -> LiquidColors.AccentBlueLight to LiquidColors.AccentCyan
    name.contains("religious", ignoreCase = true) || name.contains("church", ignoreCase = true) || name.contains("إسلام") -> LiquidColors.AccentGold to LiquidColors.AccentOrange.copy(alpha = 0.5f)
    name.contains("business", ignoreCase = true) -> LiquidColors.AccentIndigo to LiquidColors.AccentBlue
    else -> LiquidColors.AccentBlue to LiquidColors.AccentViolet
}

private fun getCategoryIcon(name: String): ImageVector = when {
    name.contains("news", true) || name.contains("أخبار") -> Icons.Filled.Newspaper
    name.contains("sport", true) || name.contains("رياض") -> Icons.Filled.SportsSoccer
    name.contains("movie", true) || name.contains("anim", true) || name.contains("أفلام") -> Icons.Filled.Movie
    name.contains("music", true) || name.contains("موسيق") -> Icons.Filled.MusicNote
    name.contains("doc", true) || name.contains("وثائق") -> Icons.Filled.VideoLibrary
    name.contains("kid", true) || name.contains("أطفال") -> Icons.Filled.ChildCare
    name.contains("entertain", true) || name.contains("ترفيه") -> Icons.Filled.Celebration
    name.contains("education", true) -> Icons.Filled.School
    name.contains("business", true) -> Icons.Filled.Business
    name.contains("lifestyle", true) -> Icons.Filled.Spa
    name.contains("religious", true) -> Icons.Filled.Mosque
    name.contains("cook", true) -> Icons.Filled.Restaurant
    name.contains("travel", true) -> Icons.Filled.Flight
    name.contains("science", true) -> Icons.Filled.Science
    name.contains("weather", true) -> Icons.Filled.WbSunny
    else -> Icons.Filled.Tv
}
