package com.liquidtv.app.ui.screens.player

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.OnlineSubtitleResult
import com.liquidtv.app.domain.model.SubtitleBackgroundOption
import com.liquidtv.app.domain.model.SubtitleColorOption
import com.liquidtv.app.domain.model.SubtitleEdgeOption
import com.liquidtv.app.domain.model.SubtitleFontOption
import com.liquidtv.app.domain.model.SubtitleLanguageCatalog
import com.liquidtv.app.domain.model.SubtitlePositionOption
import com.liquidtv.app.domain.model.SubtitleStyleSettings
import com.liquidtv.app.domain.model.TrackInfo
import com.liquidtv.app.player.PlayerUiState
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

private enum class PlayerSheetTab(val labelAr: String, val icon: ImageVector) {
    TRACKS("المسارات", Icons.Filled.Subtitles),
    ONLINE_SEARCH("بحث الإضافات", Icons.Filled.Search),
    APPEARANCE("المظهر والخطوط", Icons.Filled.Palette),
}

@Composable
fun PlayerSubtitlesAndTracksSheet(
    uiState: PlayerUiState,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit,
    onToggleExternalSubtitles: () -> Unit,
    onLoadExternalSubtitleUrl: (String, String) -> Unit,
    onPickLocalSubtitleUri: (Uri) -> Unit,
    onSelectSearchLanguage: (String) -> Unit,
    onSearchOnlineSubtitles: () -> Unit,
    onApplyOnlineSubtitle: (OnlineSubtitleResult) -> Unit,
    onUpdateStyle: ((SubtitleStyleSettings) -> SubtitleStyleSettings) -> Unit,
    onAdjustDelay: (Int) -> Unit,
    onSetDelay: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    var activeTab by remember { mutableStateOf(PlayerSheetTab.TRACKS) }
    var externalSubUrl by remember { mutableStateOf("") }

    val openSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onPickLocalSubtitleUri(uri)
        }
    }

    PlayerOverlaySheetCard(
        testTag = "player_tracks_sheet",
        onDismiss = onClose,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "الصوت والترجمة والمظهر",
                    style = LiquidTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                uiState.activeExternalSubtitleLabel?.let { label ->
                    Text(
                        text = if (uiState.externalSubtitlesEnabled) {
                            "✓ ترجمة خارجية مفعّلة: $label"
                        } else {
                            "ترجمة خارجية متوقفة مؤقتاً: $label"
                        },
                        style = LiquidTypography.LabelSmall,
                        color = if (uiState.externalSubtitlesEnabled) colors.success else colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tracks_sheet_close"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = colors.text, modifier = Modifier.size(20.dp))
            }
        }

        // Tabs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("player_subtitle_tabs"),
        ) {
            items(PlayerSheetTab.entries, key = { it.name }) { tab ->
                SheetTabPill(
                    label = tab.labelAr,
                    icon = tab.icon,
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    modifier = Modifier.testTag("player_tab_${tab.name.lowercase()}"),
                )
            }
        }

        when (activeTab) {
            PlayerSheetTab.TRACKS -> {
                TracksTabContent(
                    uiState = uiState,
                    externalSubUrl = externalSubUrl,
                    onExternalSubUrlChange = { externalSubUrl = it },
                    onSelectAudio = onSelectAudio,
                    onSelectSubtitle = onSelectSubtitle,
                    onToggleExternalSubtitles = onToggleExternalSubtitles,
                    onLoadExternalSubtitleUrl = onLoadExternalSubtitleUrl,
                    onLaunchFilePicker = {
                        openSubtitleLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/x-subrip",
                                "application/ttml+xml",
                                "application/zip",
                                "application/gzip",
                                "application/octet-stream",
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            PlayerSheetTab.ONLINE_SEARCH -> {
                OnlineSearchTabContent(
                    uiState = uiState,
                    onSelectSearchLanguage = onSelectSearchLanguage,
                    onSearchOnlineSubtitles = onSearchOnlineSubtitles,
                    onApplyOnlineSubtitle = onApplyOnlineSubtitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            PlayerSheetTab.APPEARANCE -> {
                AppearanceTabContent(
                    style = uiState.subtitleStyle,
                    onUpdateStyle = onUpdateStyle,
                    onAdjustDelay = onAdjustDelay,
                    onSetDelay = onSetDelay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TracksTabContent(
    uiState: PlayerUiState,
    externalSubUrl: String,
    onExternalSubUrlChange: (String) -> Unit,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit,
    onToggleExternalSubtitles: () -> Unit,
    onLoadExternalSubtitleUrl: (String, String) -> Unit,
    onLaunchFilePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.testTag("tracks_tab_list"),
    ) {
        if (uiState.hasExternalSubtitleLoaded) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onToggleExternalSubtitles,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.externalSubtitlesEnabled) colors.success else colors.surface,
                            contentColor = if (uiState.externalSubtitlesEnabled) colors.onSuccess else colors.text,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("toggle_external_subtitles_button"),
                    ) {
                        Icon(
                            imageVector = if (uiState.externalSubtitlesEnabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (uiState.externalSubtitlesEnabled) "إيقاف الترجمة الخارجية" else "تفعيل الترجمة الخارجية",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onLaunchFilePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("pick_local_subtitle_button"),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = colors.accentText)
                Spacer(Modifier.width(8.dp))
                Text("اختيار ملف ترجمة من الجهاز (.srt / .vtt / .ass / .zip)", color = colors.text)
            }
        }

        item {
            Text("مسارات الصوت", style = LiquidTypography.TitleSmall, color = colors.accentText)
        }
        if (uiState.audioTracks.isEmpty()) {
            item {
                Text("لا توجد مسارات صوتية إضافية في هذا البث.", style = LiquidTypography.BodySmall, color = colors.muted)
            }
        } else {
            items(uiState.audioTracks, key = { "a_${it.groupIndex}_${it.trackIndex}" }) { track ->
                SheetOptionRow(
                    label = track.label,
                    selected = track.isSelected,
                    onClick = { onSelectAudio(track) },
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("الترجمات المدمجة", style = LiquidTypography.TitleSmall, color = colors.accentText)
        }
        item {
            SheetOptionRow(
                label = "إيقاف جميع الترجمات",
                selected = !uiState.externalSubtitlesEnabled && uiState.subtitleTracks.none { it.isSelected },
                onClick = { onSelectSubtitle(null) },
                modifier = Modifier.testTag("disable_all_subtitles_row"),
            )
        }
        if (uiState.subtitleTracks.isEmpty()) {
            item {
                Text("لا توجد مسارات ترجمة مدمجة في هذا البث.", style = LiquidTypography.BodySmall, color = colors.muted)
            }
        } else {
            items(uiState.subtitleTracks, key = { "s_${it.groupIndex}_${it.trackIndex}" }) { track ->
                SheetOptionRow(
                    label = track.label,
                    selected = !uiState.externalSubtitlesEnabled && track.isSelected,
                    onClick = { onSelectSubtitle(track) },
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("رابط ترجمة خارجية مباشر (بدون إعادة تحميل)", style = LiquidTypography.TitleSmall, color = colors.accentText)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = externalSubUrl,
                    onValueChange = onExternalSubUrlChange,
                    singleLine = true,
                    placeholder = { Text("https://.../subs.srt", color = colors.muted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.accent,
                        focusedBorderColor = colors.focus,
                        unfocusedBorderColor = colors.border,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("external_subtitle_url_input"),
                )
                Button(
                    onClick = { onLoadExternalSubtitleUrl(externalSubUrl, uiState.subtitleSearchLanguage) },
                    enabled = !uiState.isApplyingSubtitle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier
                        .heightIn(min = 50.dp)
                        .testTag("load_external_subtitle_button"),
                ) {
                    if (uiState.isApplyingSubtitle) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.onAccent, strokeWidth = 2.dp)
                    } else {
                        Text("تفعيل")
                    }
                }
            }
            uiState.externalSubtitleError?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(err, style = LiquidTypography.LabelSmall, color = colors.danger)
            }
        }
    }
}

@Composable
private fun OnlineSearchTabContent(
    uiState: PlayerUiState,
    onSelectSearchLanguage: (String) -> Unit,
    onSearchOnlineSubtitles: () -> Unit,
    onApplyOnlineSubtitle: (OnlineSubtitleResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val meta = uiState.subtitleMetadata

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.testTag("online_subtitle_search_list"),
    ) {
        item {
            LiquidGlassSurface(
                cornerRadius = 14.dp,
                glassColor = colors.surface,
                glassAlpha = 0.25f,
                borderColor = colors.border,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = meta?.effectiveSearchTitle ?: "محتوى قيد التشغيل",
                        style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                    )
                    val subtitleInfo = when {
                        meta == null || meta.mediaType == MediaType.LIVE ->
                            "البحث عن الترجمة متاح للأفلام والمسلسلات فقط (غير متاح للبث المباشر)."
                        meta.mediaType == MediaType.SERIES_EPISODE ->
                            "مسلسل • الموسم ${meta.seasonNumber} الحلقة ${meta.episodeNumber}" +
                                (uiState.resolvedImdbId?.let { " • $it" } ?: "")
                        else ->
                            "فيلم" + (uiState.resolvedImdbId?.let { " • $it" } ?: "")
                    }
                    Text(subtitleInfo, style = LiquidTypography.BodySmall, color = colors.textSecondary)
                }
            }
        }

        if (meta != null && meta.isSearchEligible) {
            item {
                Text("لغة الترجمة المطلوبة", style = LiquidTypography.TitleSmall, color = colors.accentText)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SubtitleLanguageCatalog.choices, key = { it.code }) { choice ->
                        val isSelected = uiState.subtitleSearchLanguage == choice.code
                        StyleChip(
                            label = choice.labelAr,
                            selected = isSelected,
                            onClick = { onSelectSearchLanguage(choice.code) },
                            modifier = Modifier.testTag("subtitle_lang_${choice.code}"),
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onSearchOnlineSubtitles,
                    enabled = !uiState.isSearchingSubtitles,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("search_online_subtitles_button"),
                ) {
                    if (uiState.isSearchingSubtitles) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.onAccent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ البحث عبر الإضافات...")
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("البحث عن ترجمة عبر الإضافات المثبتة", fontWeight = FontWeight.Bold)
                    }
                }
            }

            uiState.subtitleSearchError?.let { err ->
                item {
                    Text(err, style = LiquidTypography.BodySmall, color = colors.danger)
                }
            }

            uiState.subtitleSearchStatus?.let { status ->
                item {
                    Text(status, style = LiquidTypography.BodySmall, color = colors.accentText)
                }
            }

            if (uiState.subtitleSearchResults.isNotEmpty()) {
                items(uiState.subtitleSearchResults, key = { it.url }) { result ->
                    val isApplying = uiState.isApplyingSubtitle && uiState.applyingSubtitleUrl == result.url
                    val isActive = uiState.externalSubtitlesEnabled && uiState.activeExternalSubtitleLabel == result.label
                    OnlineSubtitleResultCard(
                        result = result,
                        isApplying = isApplying,
                        isActive = isActive,
                        onClick = { onApplyOnlineSubtitle(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceTabContent(
    style: SubtitleStyleSettings,
    onUpdateStyle: ((SubtitleStyleSettings) -> SubtitleStyleSettings) -> Unit,
    onAdjustDelay: (Int) -> Unit,
    onSetDelay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = modifier.testTag("subtitle_appearance_list"),
    ) {
        // Timing / Delay
        item {
            Text(
                text = "توقيت الترجمة (Delay): ${if (style.delayMs > 0) "+${style.delayMs}" else style.delayMs} ms",
                style = LiquidTypography.TitleSmall,
                color = colors.accentText,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(-500, -250, 0, 250, 500).forEach { delta ->
                    OutlinedButton(
                        onClick = { if (delta == 0) onSetDelay(0) else onAdjustDelay(delta) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = if (delta == 0) "صفر" else if (delta > 0) "+$delta" else "$delta",
                            style = LiquidTypography.LabelSmall,
                            color = colors.text,
                        )
                    }
                }
            }
            Slider(
                value = style.delayMs.toFloat(),
                onValueChange = { onSetDelay(it.toInt()) },
                valueRange = SubtitleStyleSettings.MIN_DELAY_MS.toFloat()..SubtitleStyleSettings.MAX_DELAY_MS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.border,
                ),
            )
        }

        // Size
        item {
            Text("حجم الخط", style = LiquidTypography.TitleSmall, color = colors.accentText)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubtitleStyleSettings.SUPPORTED_SIZES) { sizeSp ->
                    StyleChip(
                        label = "${sizeSp}pt",
                        selected = style.fontSizeSp == sizeSp,
                        onClick = { onUpdateStyle { it.copy(fontSizeSp = sizeSp) } },
                        modifier = Modifier.testTag("subtitle_size_$sizeSp"),
                    )
                }
            }
        }

        // Colors
        item {
            Text("لون النص", style = LiquidTypography.TitleSmall, color = colors.accentText)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubtitleColorOption.entries, key = { it.id }) { colorOption ->
                    val selected = style.colorOption == colorOption
                    StyleChip(
                        label = colorOption.labelAr,
                        selected = selected,
                        leadingDotColor = Color(colorOption.argbHex.toInt()),
                        onClick = { onUpdateStyle { it.copy(colorId = colorOption.id) } },
                        modifier = Modifier.testTag("subtitle_color_${colorOption.id}"),
                    )
                }
            }
        }

        // Background
        item {
            Text("خلفية الترجمة", style = LiquidTypography.TitleSmall, color = colors.accentText)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubtitleBackgroundOption.entries, key = { it.id }) { bg ->
                    StyleChip(
                        label = bg.labelAr,
                        selected = style.backgroundOption == bg,
                        onClick = { onUpdateStyle { it.copy(backgroundId = bg.id) } },
                    )
                }
            }
        }

        // Edge / Outline
        item {
            Text("الحدود والظل", style = LiquidTypography.TitleSmall, color = colors.accentText)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubtitleEdgeOption.entries, key = { it.id }) { edge ->
                    StyleChip(
                        label = edge.labelAr,
                        selected = style.edgeOption == edge,
                        onClick = { onUpdateStyle { it.copy(edgeId = edge.id) } },
                    )
                }
            }
        }

        // Position
        item {
            Text("موضع الترجمة", style = LiquidTypography.TitleSmall, color = colors.accentText)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubtitlePositionOption.entries, key = { it.id }) { pos ->
                    StyleChip(
                        label = pos.labelAr,
                        selected = style.positionOption == pos,
                        onClick = { onUpdateStyle { it.copy(positionId = pos.id) } },
                    )
                }
            }
        }

        // 10 Bold Arabic Fonts Picker with Live Preview in each FontFamily
        item {
            Text("خط الترجمة العربي (معاينة حيّة)", style = LiquidTypography.TitleSmall, color = colors.accentText)
        }
        items(SubtitleFontOption.entries, key = { it.id }) { font ->
            val selected = style.fontOption == font
            val fontFamily = remember(font) { font.toComposeFontFamily() }
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            selected -> colors.selected
                            isFocused -> colors.surface
                            else -> colors.card.copy(alpha = 0.65f)
                        },
                    )
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = when {
                            isFocused -> colors.focus
                            selected -> colors.accent
                            else -> colors.border.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(interactionSource, null) {
                        onUpdateStyle { it.copy(fontId = font.id) }
                    }
                    .focusable(interactionSource = interactionSource)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("subtitle_font_item_${font.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = font.englishName,
                            style = LiquidTypography.LabelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )
                        Text(
                            text = "${font.displayNameAr} (${font.actualWeight})",
                            style = LiquidTypography.LabelSmall,
                            color = colors.muted,
                        )
                    }
                    Text(
                        text = "معاينة الترجمة العربية الواضحة على الشاشة • 123",
                        fontFamily = fontFamily,
                        fontWeight = FontWeight(font.actualWeight),
                        fontSize = 16.sp,
                        color = Color(style.colorOption.argbHex.toInt()),
                    )
                }
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = "محدد", tint = colors.accentText)
                }
            }
        }
    }
}

@Composable
private fun OnlineSubtitleResultCard(
    result: OnlineSubtitleResult,
    isApplying: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    PlayerCompactOptionCard(
        selected = isActive,
        isActive = isActive,
        enabled = !isApplying,
        onClick = onClick,
        modifier = Modifier.testTag("online_subtitle_result_card"),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.label,
                style = LiquidTypography.BodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = result.language.uppercase(),
                    style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = colors.accentText,
                    maxLines = 1,
                )
                Text(
                    text = "• ${result.addonName}",
                    style = LiquidTypography.LabelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            isApplying -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
            isActive -> Icon(Icons.Filled.Check, contentDescription = "مفعّلة", tint = colors.success, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Filled.CloudDownload, contentDescription = "تحميل وتفعيل", tint = colors.accentText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SheetTabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) colors.selected else colors.surface.copy(alpha = 0.7f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else if (selected) colors.accent else colors.border,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.accentText else colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}

@Composable
private fun StyleChip(
    label: String,
    selected: Boolean,
    leadingDotColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.selected else colors.card.copy(alpha = 0.7f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else if (selected) colors.accent else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDotColor != null) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(leadingDotColor)
                    .border(1.dp, colors.border, CircleShape),
            )
        }
        Text(
            text = label,
            style = LiquidTypography.LabelMedium,
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}

@Composable
private fun SheetOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    PlayerCompactOptionCard(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = LiquidTypography.BodySmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            ),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "محدد",
                tint = colors.accentText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
