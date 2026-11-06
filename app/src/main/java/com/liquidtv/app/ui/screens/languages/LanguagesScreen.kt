package com.liquidtv.app.ui.screens.languages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.Language
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun LanguagesScreen(
    onLanguageClick: (String) -> Unit,
    viewModel: LanguagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp, start = 36.dp, end = 36.dp),
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
                        text = "Browse by Language",
                        style = LiquidTypography.HeadlineMedium.copy(
                            brush = Brush.linearGradient(
                                listOf(LiquidColors.TextPrimary, LiquidColors.AccentCyan)
                            )
                        ),
                    )
                    Text(
                        text = if (uiState.isLoading) (uiState.loadingMessage ?: "Loading broadcast languages...") else "${uiState.languages.size} languages found",
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextTertiary,
                    )
                }
            }

            if (uiState.isLoading && uiState.languages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = LiquidColors.AccentBlue)
                        Text(
                            text = uiState.loadingMessage ?: "Fetching & indexing languages...",
                            style = LiquidTypography.BodyMedium,
                            color = LiquidColors.TextSecondary,
                        )
                    }
                }
            } else if (uiState.languages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "No languages cached yet",
                            style = LiquidTypography.HeadlineSmall,
                            color = LiquidColors.TextSecondary,
                        )
                        LiquidGlassSurface(
                            modifier = Modifier
                                .clickable { viewModel.refresh() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            cornerRadius = 14.dp,
                            glassAlpha = 0.25f,
                            borderColor = LiquidColors.AccentBlue,
                        ) {
                            Text("Fetch Streams", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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

@Composable
private fun LanguageGridTile(
    language: Language,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Hash-based accent color for each language — visual variety
    val accentColor = remember(language.name) {
        val h = language.name.hashCode()
        listOf(
            LiquidColors.AccentCyan, LiquidColors.AccentBlue, LiquidColors.AccentViolet,
            LiquidColors.AccentTeal, LiquidColors.AccentGreen, LiquidColors.AccentGold,
            LiquidColors.AccentIndigo,
        )[(h and 0x7FFFFFFF) % 7]
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = if (isFocused) 0.28f else 0.10f),
                        Color.White.copy(alpha = if (isFocused) 0.07f else 0.02f),
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) accentColor.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
    ) {
        // Specular highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (isFocused) 0.14f else 0.05f), Color.Transparent)
                    )
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon circle with accent color
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(accentColor.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.2f))
                        ),
                        CircleShape,
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = language.name,
                    tint = if (isFocused) Color.White else accentColor,
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
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${language.channelCount} channels",
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused) accentColor.copy(alpha = 0.8f) else LiquidColors.TextTertiary,
                )
            }
        }
    }
}

