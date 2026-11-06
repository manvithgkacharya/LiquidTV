package com.liquidtv.app.ui.screens.countries

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
import androidx.compose.material3.CircularProgressIndicator
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
import com.liquidtv.app.domain.model.Country
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun CountriesScreen(
    onCountryClick: (String) -> Unit,
    viewModel: CountriesViewModel = hiltViewModel(),
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
                        text = "Browse by Country",
                        style = LiquidTypography.HeadlineMedium.copy(
                            brush = Brush.linearGradient(
                                listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight)
                            )
                        ),
                    )
                    Text(
                        text = if (uiState.isLoading) (uiState.loadingMessage ?: "Loading broadcast countries...") else "${uiState.countries.size} countries & territories",
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextTertiary,
                    )
                }
            }

            if (uiState.isLoading && uiState.countries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = LiquidColors.AccentBlue)
                        Text(
                            text = uiState.loadingMessage ?: "Fetching & indexing countries...",
                            style = LiquidTypography.BodyMedium,
                            color = LiquidColors.TextSecondary,
                        )
                    }
                }
            } else if (uiState.countries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "No countries cached yet",
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
                    items(uiState.countries, key = { it.name }) { country ->
                        CountryGridTile(
                            country = country,
                            onClick = { onCountryClick(country.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryGridTile(
    country: Country,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Seed a subtle accent color from country name hash for variety
    val accentSeed = remember(country.name) {
        val h = country.name.hashCode()
        listOf(
            LiquidColors.AccentBlue, LiquidColors.AccentViolet, LiquidColors.AccentCyan,
            LiquidColors.AccentTeal, LiquidColors.AccentGold, LiquidColors.AccentGreen,
        )[(h and 0x7FFFFFFF) % 6]
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accentSeed.copy(alpha = if (isFocused) 0.30f else 0.12f),
                        Color.White.copy(alpha = if (isFocused) 0.08f else 0.03f),
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) accentSeed.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
    ) {
        // Specular top highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (isFocused) 0.15f else 0.06f), Color.Transparent)
                    )
                ),
        )

        // Watermark globe icon in background at low alpha
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = "🌐",
                style = LiquidTypography.DisplayMedium,
                color = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Flag circle — larger when focused
            Box(
                modifier = Modifier
                    .size(if (isFocused) 58.dp else 52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(accentSeed.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.2f))
                        ),
                        CircleShape,
                    )
                    .border(1.dp, accentSeed.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = country.flagEmoji ?: "🌐",
                    style = LiquidTypography.HeadlineSmall,
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = country.name,
                    style = LiquidTypography.TitleMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${country.channelCount} channels",
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused) accentSeed.copy(alpha = 0.8f) else LiquidColors.TextTertiary,
                )
            }
        }
    }
}
