package com.liquidtv.app.ui.screens.languages

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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun LanguagesScreen(
    onLanguageClick: (String) -> Unit,
    viewModel: LanguagesViewModel = hiltViewModel(),
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
                    .padding(top = if (isCompact) 76.dp else 88.dp, start = hPad, end = hPad),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "تصفح حسب اللغة",
                            style = LiquidTypography.HeadlineMedium.copy(
                                brush = Brush.linearGradient(
                                    listOf(colors.text, colors.accentText)
                                )
                            ),
                        )
                        Text(
                            text = if (uiState.isLoading) {
                                uiState.loadingMessage ?: "جارٍ تحميل اللغات…"
                            } else {
                                "${uiState.languages.size} لغة متاحة"
                            },
                            style = LiquidTypography.BodySmall,
                            color = colors.muted,
                        )
                    }
                }

                if (uiState.isLoading && uiState.languages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = colors.accent)
                            Text(
                                text = uiState.loadingMessage ?: "جارٍ جلب وفهرسة اللغات…",
                                style = LiquidTypography.BodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                } else if (uiState.languages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "لا توجد وسوم لغات في القائمة النشطة",
                                style = LiquidTypography.HeadlineSmall,
                                color = colors.textSecondary,
                            )
                            LiquidGlassSurface(
                                modifier = Modifier
                                    .clickable { viewModel.refresh() }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                cornerRadius = 14.dp,
                                glassAlpha = 0.85f,
                                borderColor = colors.accent,
                            ) {
                                Text("تحديث القائمة", style = LiquidTypography.LabelLarge, color = colors.accentText)
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
                        items(uiState.languages, key = { it.name }) { lang ->
                            LanguageGridTile(
                                language = lang,
                                onClick = { onLanguageClick(lang.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageGridTile(
    language: Language,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val accentColor = remember(language.name, colors) {
        val h = language.name.hashCode()
        listOf(
            colors.accent, colors.waveOne, colors.waveTwo,
            colors.success, colors.focus, colors.accentSecondary,
        )[(h and 0x7FFFFFFF) % 6]
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isFocused) colors.selected else colors.card.copy(alpha = if (colors.isLight) 0.94f else 0.78f)
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = if (isFocused) 0.22f else 0.10f),
                        colors.surface.copy(alpha = if (colors.isLight) 0.45f else 0.30f),
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = if (colors.isLight) 0.72f else 0.48f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            (if (colors.isLight) Color.White else colors.text).copy(alpha = if (isFocused) 0.20f else 0.08f),
                            Color.Transparent,
                        )
                    )
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (isFocused) colors.accent else colors.surface,
                        CircleShape,
                    )
                    .border(
                        1.dp,
                        if (isFocused) colors.focus else accentColor.copy(alpha = 0.55f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = language.name,
                    tint = if (isFocused) colors.onAccent else colors.accentText,
                    modifier = Modifier.size(26.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = language.name.replaceFirstChar { it.uppercase() },
                    style = LiquidTypography.TitleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${language.channelCount} قناة",
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused) colors.accentText else colors.muted,
                )
            }
        }
    }
}
