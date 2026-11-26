package com.liquidtv.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidtv.app.data.audio.AudioSourceClassifier
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import com.liquidtv.app.domain.model.ExternalAudioStatus
import com.liquidtv.app.domain.model.ExternalAudioSyncStage
import com.liquidtv.app.domain.model.ExternalAudioUiState
import com.liquidtv.app.player.ExternalAudioSyncPolicy
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FINE_SYNC_STEP_MS = 100L

@Composable
fun ExternalAudioSheet(
    state: ExternalAudioUiState,
    onSelectChannel: (ExternalAudioChannel) -> Unit,
    onDetachAudio: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectBrandFilter: (AudioBrandFilter) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleLoudnessBoost: () -> Unit,
    onAdjustSyncOffset: (Long) -> Unit,
    onResetSyncOffset: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    val initialFilterFocus = remember { FocusRequester() }
    val filteredChannels = remember(state.channels, state.selectedBrandFilter, state.searchQuery) {
        AudioSourceClassifier.filterChannels(
            channels = state.channels,
            brandFilter = state.selectedBrandFilter,
            query = state.searchQuery,
        )
    }
    val beinCount = remember(state.channels) {
        state.channels.count { it.brand == AudioChannelBrand.BEIN_SPORTS }
    }
    val alwanCount = remember(state.channels) {
        state.channels.count { it.brand == AudioChannelBrand.ALWAN }
    }

    LaunchedEffect(Unit) {
        try {
            initialFilterFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val adaptiveMaxHeight = if (maxWidth < 540.dp) 680.dp else 560.dp
        val isShortViewport = maxHeight < 440.dp
        PlayerOverlaySheetCard(
            testTag = "external_audio_sheet",
            onDismiss = onClose,
            maxCardWidth = 840.dp,
            maxCardHeight = adaptiveMaxHeight,
        ) {
            // 1. Top Header Bar (matches reference: waveform icon + title + count badge | status pill + sync button + close)
            Column(modifier = Modifier.fillMaxWidth()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    ExternalAudioTopHeaderBar(
                        state = state,
                        showConnectionPill = maxWidth >= 460.dp,
                        compact = isShortViewport,
                        onSyncCatalog = onRefreshCatalog,
                        onClose = onClose,
                    )
                }
                Spacer(Modifier.height(if (isShortViewport) 3.dp else 6.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.border.copy(alpha = 0.28f),
                )
            }

            // 2. Main Body: Right (Start in RTL) = Control Panel, Left (End in RTL) = Sources List
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val isWideSplit = maxWidth >= 540.dp
                val compactVertical = maxHeight < 340.dp
                val rowHeight = if (compactVertical) 33.dp else 44.dp
                val rowSpacing = if (compactVertical) 2.dp else 4.dp

            if (isWideSplit) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Start (Right side in RTL): Audio Control & Sync Panel
                    Column(
                        modifier = Modifier
                            .width(276.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compactVertical) 6.dp else 8.dp),
                    ) {
                        AudioSheetAlerts(state = state)
                        AudioControlSectionCard(
                            volumePercent = state.volumePercent,
                            loudnessBoostEnabled = state.loudnessBoostEnabled,
                            compact = compactVertical,
                            onVolumeChange = onVolumeChange,
                            onToggleLoudnessBoost = onToggleLoudnessBoost,
                        )
                        AudioSyncSectionCard(
                            syncOffsetMs = state.syncOffsetMs,
                            compact = compactVertical,
                            onAdjustSyncOffset = onAdjustSyncOffset,
                            onResetSyncOffset = onResetSyncOffset,
                        )
                        if (state.isAttached) {
                            DetachAudioButton(
                                onClick = onDetachAudio,
                                compact = compactVertical,
                            )
                        }
                    }

                    // End (Left side in RTL): Audio Sources Container (Search + Filters on top, 6+ channels below)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface.copy(alpha = 0.42f))
                            .border(1.dp, colors.border.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                            .padding(if (compactVertical) 4.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactVertical) 4.dp else 7.dp),
                    ) {
                        SourcesToolbarRow(
                            searchQuery = state.searchQuery,
                            selectedFilter = state.selectedBrandFilter,
                            allCount = state.channels.size,
                            beinCount = beinCount,
                            alwanCount = alwanCount,
                            initialFocusRequester = initialFilterFocus,
                            onSearchQueryChange = onSearchQueryChange,
                            onSelectBrandFilter = onSelectBrandFilter,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            ExternalAudioChannelListContent(
                                state = state,
                                filteredChannels = filteredChannels,
                                rowHeight = rowHeight,
                                rowSpacing = rowSpacing,
                                onSelectChannel = onSelectChannel,
                                onRefreshCatalog = onRefreshCatalog,
                            )
                        }
                    }
                }
            } else {
                // Narrow Portrait fallback: keep all controls & 6+ channels readable with zero clipping
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AudioSheetAlerts(state = state)
                    SourcesToolbarRow(
                        searchQuery = state.searchQuery,
                        selectedFilter = state.selectedBrandFilter,
                        allCount = state.channels.size,
                        beinCount = beinCount,
                        alwanCount = alwanCount,
                        initialFocusRequester = initialFilterFocus,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectBrandFilter = onSelectBrandFilter,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface.copy(alpha = 0.42f))
                            .border(1.dp, colors.border.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                            .padding(6.dp),
                    ) {
                        ExternalAudioChannelListContent(
                            state = state,
                            filteredChannels = filteredChannels,
                            rowHeight = 36.dp,
                            rowSpacing = 3.dp,
                            onSelectChannel = onSelectChannel,
                            onRefreshCatalog = onRefreshCatalog,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AudioControlSectionCard(
                            volumePercent = state.volumePercent,
                            loudnessBoostEnabled = state.loudnessBoostEnabled,
                            compact = true,
                            onVolumeChange = onVolumeChange,
                            onToggleLoudnessBoost = onToggleLoudnessBoost,
                        )
                        AudioSyncSectionCard(
                            syncOffsetMs = state.syncOffsetMs,
                            compact = true,
                            onAdjustSyncOffset = onAdjustSyncOffset,
                            onResetSyncOffset = onResetSyncOffset,
                        )
                        if (state.isAttached) {
                            DetachAudioButton(
                                onClick = onDetachAudio,
                                compact = true,
                            )
                        }
                    }
                }
            }
        }

            // 3. Bottom Footer Bar: Selected source info + preserved sync status & last sync timestamp
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.border.copy(alpha = 0.28f),
                )
                Spacer(Modifier.height(if (isShortViewport) 2.dp else 4.dp))
                ExternalAudioFooterBar(state = state)
            }
        }
    }
}

@Composable
private fun ExternalAudioTopHeaderBar(
    state: ExternalAudioUiState,
    showConnectionPill: Boolean,
    compact: Boolean,
    onSyncCatalog: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 32.dp else 38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Start (Right in RTL): Waveform icon + Title + Count Pill
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent.copy(alpha = 0.16f))
                    .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "مصدر الصوت الخارجي",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "مصدر الصوت الخارجي",
                style = LiquidTypography.TitleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface.copy(alpha = 0.72f))
                    .border(1.dp, colors.border.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
                    .testTag("external_audio_count_badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.channels.size.toString(),
                    style = LiquidTypography.LabelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = colors.text,
                    maxLines = 1,
                )
            }
        }

        // End (Left in RTL): Connection status badge + Sync button + Close button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showConnectionPill) {
                ConnectionStatusPill(state = state)
            }
            SyncCatalogPillButton(
                isSyncing = state.isLoadingCatalog,
                onClick = onSyncCatalog,
                modifier = Modifier.testTag("external_audio_sync_catalog_button"),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("external_audio_close_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "إغلاق",
                    tint = colors.text,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(state: ExternalAudioUiState) {
    val colors = AppTheme.colors
    val (label, dotColor) = when (state.status) {
        ExternalAudioStatus.ACTIVE -> "متصل" to colors.success
        ExternalAudioStatus.CONNECTING,
        ExternalAudioStatus.BUFFERING,
        ExternalAudioStatus.RECOVERING -> "جارٍ الاتصال" to colors.accent
        ExternalAudioStatus.ERROR -> "خطأ اتصال" to colors.danger
        ExternalAudioStatus.LOADING_CATALOG -> "مزامنة" to colors.accent
        ExternalAudioStatus.IDLE -> if (state.channels.isNotEmpty()) {
            "متصل" to colors.success
        } else {
            "جاهز" to colors.textSecondary
        }
    }
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(dotColor.copy(alpha = 0.16f))
            .border(1.dp, dotColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp)
            .testTag("external_audio_connection_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            color = dotColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SourcesToolbarRow(
    searchQuery: String,
    selectedFilter: AudioBrandFilter,
    allCount: Int,
    beinCount: Int,
    alwanCount: Int,
    initialFocusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSelectBrandFilter: (AudioBrandFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Start in RTL (Right): Filter Pills ("الكل count", "beIN", "ALWAN")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioBrandFilter.entries.forEach { filter ->
                val pillLabel = when (filter) {
                    AudioBrandFilter.ALL -> "${filter.labelAr} $allCount"
                    AudioBrandFilter.BEIN_SPORTS -> "beIN"
                    AudioBrandFilter.ALWAN -> "ALWAN"
                }
                FilterPillButton(
                    label = pillLabel,
                    selected = selectedFilter == filter,
                    onClick = { onSelectBrandFilter(filter) },
                    modifier = (if (filter == AudioBrandFilter.ALL) Modifier.focusRequester(initialFocusRequester) else Modifier)
                        .testTag("external_audio_filter_${filter.name.lowercase()}"),
                )
            }
        }

        // End in RTL (Left): Search Field filling remaining horizontal width
        CompactAudioSearchInput(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AudioControlSectionCard(
    volumePercent: Int,
    loudnessBoostEnabled: Boolean,
    compact: Boolean,
    onVolumeChange: (Int) -> Unit,
    onToggleLoudnessBoost: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.56f))
            .border(1.dp, colors.border.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        // Card Title Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "التحكم بالصوت",
                style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = colors.text,
                maxLines = 1,
            )
        }

        // Volume Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.panel.copy(alpha = 0.58f))
                .border(1.dp, colors.border.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "مستوى الصوت",
                    style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                )
                Text(
                    text = "$volumePercent%",
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = colors.text,
                    modifier = Modifier.testTag("external_audio_volume_text"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VolumeIconStepButton(
                    icon = Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = "خفض الصوت 10%",
                    onClick = { onVolumeChange(volumePercent - 10) },
                    modifier = Modifier.testTag("external_audio_vol_down"),
                )
                Slider(
                    value = volumePercent.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.border.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 44.dp)
                        .height(24.dp),
                )
                VolumeIconStepButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "رفع الصوت 10%",
                    onClick = { onVolumeChange(volumePercent + 10) },
                    modifier = Modifier.testTag("external_audio_vol_up"),
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = colors.border.copy(alpha = 0.25f),
            )

            // Loudness Boost Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "تعزيز الصوت",
                        style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = colors.text,
                    )
                }

                val boostBadgeColor = if (loudnessBoostEnabled) colors.success else colors.textSecondary
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (loudnessBoostEnabled) colors.success.copy(alpha = 0.20f) else colors.surface.copy(alpha = 0.70f),
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) colors.focus else boostBadgeColor.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggleLoudnessBoost,
                        )
                        .focusable(interactionSource = interactionSource)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .testTag("external_audio_boost_toggle"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (loudnessBoostEnabled) "مفعل" else "معطّل",
                        style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = boostBadgeColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSyncSectionCard(
    syncOffsetMs: Long,
    compact: Boolean,
    onAdjustSyncOffset: (Long) -> Unit,
    onResetSyncOffset: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.56f))
            .border(1.dp, colors.border.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
    ) {
        // Card Title Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "مزامنة الصوت",
                style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = colors.text,
                maxLines = 1,
            )
        }

        // Current Delay Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "التأخير الحالي",
                style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp),
                color = colors.textSecondary,
            )
            Text(
                text = ExternalAudioSyncPolicy.formatOffsetAr(syncOffsetMs),
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = if (syncOffsetMs == 0L) colors.text else colors.accent,
                maxLines = 1,
                modifier = Modifier.testTag("external_audio_sync_value"),
            )
        }

        // 4 Sync Delay Buttons (+0.5ث, +0.1ث, -0.1ث, -0.5ث)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SmallStepButton(
                label = "+0.5ث",
                onClick = { onAdjustSyncOffset(ExternalAudioSyncPolicy.STEP_MS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("external_audio_sync_plus"),
            )
            SmallStepButton(
                label = "+0.1ث",
                onClick = { onAdjustSyncOffset(FINE_SYNC_STEP_MS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("external_audio_sync_fine_plus"),
            )
            SmallStepButton(
                label = "-0.1ث",
                onClick = { onAdjustSyncOffset(-FINE_SYNC_STEP_MS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("external_audio_sync_fine_minus"),
            )
            SmallStepButton(
                label = "-0.5ث",
                onClick = { onAdjustSyncOffset(-ExternalAudioSyncPolicy.STEP_MS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("external_audio_sync_minus"),
            )
        }

        // Full-width Reset Delay Button
        val resetInteraction = remember { MutableInteractionSource() }
        val isResetFocused by resetInteraction.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isResetFocused) colors.accent.copy(alpha = 0.22f) else colors.panel.copy(alpha = 0.85f))
                .border(
                    width = if (isResetFocused) 2.dp else 1.dp,
                    color = if (isResetFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                )
                .clickable(
                    interactionSource = resetInteraction,
                    indication = null,
                    onClick = onResetSyncOffset,
                )
                .focusable(interactionSource = resetInteraction)
                .testTag("external_audio_sync_reset"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "تصفير التأخير",
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = colors.text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetachAudioButton(
    onClick: () -> Unit,
    compact: Boolean,
) {
    val colors = AppTheme.colors
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.danger.copy(alpha = 0.88f),
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 30.dp else 34.dp)
            .testTag("external_audio_detach_button"),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "فصل الصوت",
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun ExternalAudioFooterBar(state: ExternalAudioUiState) {
    val colors = AppTheme.colors
    val selectedSummary = state.activeChannel?.let { ch ->
        "${ch.name} (${ch.number})"
    } ?: "لم يتم تحديد مصدر صوت"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .testTag("external_audio_footer_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Start (Right in RTL): Selected Source Info matching reference image
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "المصدر المحدد : $selectedSummary",
                style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("external_audio_selected_footer_text"),
            )
        }

        Spacer(Modifier.width(8.dp))

        // End (Left in RTL): Preserved Sync Status & Last Synced Date/Time
        ExternalAudioSyncStatusBadge(state = state)
    }
}

@Composable
private fun AudioSheetAlerts(state: ExternalAudioUiState) {
    val colors = AppTheme.colors
    state.connectionLimitWarning?.let { warning ->
        StatusBanner(text = warning, accent = colors.warning)
    }
    if (state.syncStage != ExternalAudioSyncStage.FAILED) {
        state.catalogWarning?.let { warning ->
            StatusBanner(text = warning, accent = colors.warning)
        }
    }
    val activeError = state.errorMessage
        ?: if (state.syncStage != ExternalAudioSyncStage.FAILED) state.catalogError else null
    if (activeError != null) {
        StatusBanner(text = activeError, accent = colors.danger)
    }
}

@Composable
private fun ExternalAudioChannelListContent(
    state: ExternalAudioUiState,
    filteredChannels: List<ExternalAudioChannel>,
    rowHeight: Dp,
    rowSpacing: Dp,
    onSelectChannel: (ExternalAudioChannel) -> Unit,
    onRefreshCatalog: () -> Unit,
) {
    val colors = AppTheme.colors
    when {
        state.isLoadingCatalog && state.channels.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.syncStageMessage?.takeIf { it.isNotBlank() }
                        ?: "جارٍ تحميل وتصنيف قنوات الصوت الخارجي...",
                    style = LiquidTypography.BodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        filteredChannels.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.channels.isEmpty()) {
                        "لا توجد قنوات صوت محفوظة حالياً."
                    } else {
                        "لا توجد نتائج مطابقة لبحثك."
                    },
                    style = LiquidTypography.BodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.channels.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRefreshCatalog,
                        enabled = !state.isLoadingCatalog,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("external_audio_empty_sync_button"),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مزامنة الصوتيات", style = LiquidTypography.LabelMedium)
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("external_audio_channel_list"),
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    val isSelected = state.activeChannel?.id == channel.id
                    val isConnectingThis = isSelected &&
                        (state.status == ExternalAudioStatus.CONNECTING ||
                            state.status == ExternalAudioStatus.BUFFERING ||
                            state.status == ExternalAudioStatus.RECOVERING)
                    val isActiveThis = isSelected && state.status == ExternalAudioStatus.ACTIVE
                    ExternalAudioChannelRow(
                        channel = channel,
                        isSelected = isSelected,
                        isActive = isActiveThis,
                        isConnecting = isConnectingThis,
                        rowHeight = rowHeight,
                        onClick = { onSelectChannel(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalAudioChannelRow(
    channel: ExternalAudioChannel,
    isSelected: Boolean,
    isActive: Boolean,
    isConnecting: Boolean,
    rowHeight: Dp,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = when {
        isActive -> colors.selected
        isSelected -> colors.selected.copy(alpha = 0.72f)
        isFocused -> colors.surface.copy(alpha = 0.88f)
        else -> colors.card.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isFocused -> colors.focus
        isActive -> colors.accent
        isSelected -> colors.accent.copy(alpha = 0.7f)
        else -> colors.border.copy(alpha = 0.42f)
    }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (isFocused || isActive) 1.8.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .testTag("external_audio_channel_${channel.number}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Channel Status / Play Icon
        when {
            isConnecting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            isActive -> Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "نشط",
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "تشغيل",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        // Channel Name + Category Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = LiquidTypography.BodySmall.copy(
                    fontWeight = if (isSelected || isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                ),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.categoryName,
                style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (channel.qualityLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = channel.qualityLabel,
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }

        // Active Pill (mirrors reference green "نشط" badge)
        if (isActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.success.copy(alpha = 0.22f))
                    .border(1.dp, colors.success.copy(alpha = 0.70f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "نشط",
                    style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = colors.success,
                    maxLines = 1,
                )
            }
        }

        // Number Pill Badge
        Box(
            modifier = Modifier
                .widthIn(min = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.panel.copy(alpha = 0.90f))
                .border(1.dp, colors.border.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.number,
                style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = colors.accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactAudioSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panel.copy(alpha = 0.72f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "البحث عن مصدر صوت...",
                    style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LiquidTypography.LabelSmall.copy(color = colors.text, fontSize = 11.sp),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("external_audio_search_input"),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "مسح البحث",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterPillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        selected -> colors.selected
        else -> colors.panel.copy(alpha = 0.62f)
    }
    val border = when {
        isFocused -> colors.focus
        selected -> colors.accent
        else -> colors.border.copy(alpha = 0.45f)
    }
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(if (isFocused) 2.dp else 1.dp, border, RoundedCornerShape(15.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
            ),
            color = if (selected || isFocused) colors.text else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SmallStepButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) colors.accent.copy(alpha = 0.22f) else colors.panel.copy(alpha = 0.85f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = colors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun VolumeIconStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (isFocused) colors.accent.copy(alpha = 0.24f) else colors.surface.copy(alpha = 0.6f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.text,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SyncCatalogPillButton(
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bgColor = when {
        isSyncing -> colors.accent.copy(alpha = 0.22f)
        isFocused -> colors.accent.copy(alpha = 0.30f)
        else -> colors.accent.copy(alpha = 0.16f)
    }
    val borderColor = when {
        isFocused -> colors.focus
        else -> colors.accent.copy(alpha = 0.72f)
    }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isSyncing,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = "مزامنة الصوتيات",
            style = LiquidTypography.LabelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            ),
            color = colors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExternalAudioSyncStatusBadge(state: ExternalAudioUiState) {
    val colors = AppTheme.colors
    val accentColor = when (state.syncStage) {
        ExternalAudioSyncStage.FAILED -> colors.danger
        ExternalAudioSyncStage.SUCCEEDED -> colors.success
        ExternalAudioSyncStage.CONNECTING_PANEL,
        ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
        ExternalAudioSyncStage.PARSING_CHANNELS,
        ExternalAudioSyncStage.SAVING_LOCAL -> colors.accent
        ExternalAudioSyncStage.IDLE -> colors.border
    }
    val stageText = when {
        state.syncStage != ExternalAudioSyncStage.IDLE && !state.syncStageMessage.isNullOrBlank() ->
            state.syncStageMessage
        state.channels.isNotEmpty() ->
            "محفوظ محلياً"
        else ->
            "غير متزامن"
    }
    val textColor = when (state.syncStage) {
        ExternalAudioSyncStage.FAILED -> colors.danger
        ExternalAudioSyncStage.SUCCEEDED -> colors.success
        ExternalAudioSyncStage.CONNECTING_PANEL,
        ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
        ExternalAudioSyncStage.PARSING_CHANNELS,
        ExternalAudioSyncStage.SAVING_LOCAL -> colors.accent
        ExternalAudioSyncStage.IDLE -> colors.textSecondary
    }
    val formattedLastSync = remember(state.lastSyncedAtMs, state.lastUpdatedAt) {
        formatExternalAudioSyncTime(state.lastSyncedAtMs, state.lastUpdatedAt)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (state.syncStage == ExternalAudioSyncStage.IDLE) {
                    colors.surface.copy(alpha = 0.45f)
                } else {
                    accentColor.copy(alpha = 0.12f)
                },
            )
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("external_audio_sync_status_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (state.syncStage) {
            ExternalAudioSyncStage.CONNECTING_PANEL,
            ExternalAudioSyncStage.DOWNLOADING_PLAYLIST,
            ExternalAudioSyncStage.PARSING_CHANNELS,
            ExternalAudioSyncStage.SAVING_LOCAL -> CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = colors.accent,
            )
            ExternalAudioSyncStage.FAILED -> Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(11.dp),
            )
            ExternalAudioSyncStage.SUCCEEDED -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(11.dp),
            )
            ExternalAudioSyncStage.IDLE -> Unit
        }
        Text(
            text = stageText,
            style = LiquidTypography.LabelSmall.copy(fontSize = 10.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "• $formattedLastSync",
            style = LiquidTypography.LabelSmall.copy(fontSize = 9.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("external_audio_last_synced_text"),
        )
    }
}

private fun formatExternalAudioSyncTime(
    lastSyncedAtMs: Long?,
    fallbackUpdatedAt: String?,
): String {
    if (lastSyncedAtMs != null && lastSyncedAtMs > 0L) {
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        return "آخر مزامنة: ${formatter.format(Date(lastSyncedAtMs))}"
    }
    val updated = fallbackUpdatedAt?.trim().orEmpty()
    if (updated.isNotBlank()) {
        return "آخر تحديث: $updated"
    }
    return "لم تتم المزامنة بعد"
}
