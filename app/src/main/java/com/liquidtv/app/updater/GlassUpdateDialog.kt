package com.liquidtv.app.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.unit.dp
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Premium TV-friendly Liquid Glass dialog that prompts the user to download
 * and install updates from GitHub.
 */
@Composable
fun GlassUpdateDialog(
    updateState: UpdateState,
    onUpdateClick: (AppUpdateInfo) -> Unit,
    onInstallClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = updateState !is UpdateState.Idle && updateState !is UpdateState.UpToDate
    val colors = AppTheme.colors

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = spring(stiffness = 500f)),
        exit = fadeOut(animationSpec = spring(stiffness = 500f)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.dialogScrim),
            contentAlignment = Alignment.Center,
        ) {
            when (updateState) {
                is UpdateState.Available -> {
                    UpdateAvailableContent(
                        info = updateState.info,
                        onUpdateClick = { onUpdateClick(updateState.info) },
                        onDismiss = onDismiss,
                    )
                }
                is UpdateState.Downloading -> {
                    DownloadingContent(
                        state = updateState,
                        onCancel = onDismiss,
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    ReadyToInstallContent(
                        onInstallClick = onInstallClick,
                        onDismiss = onDismiss,
                    )
                }
                is UpdateState.Error -> {
                    ErrorContent(
                        message = updateState.message,
                        onDismiss = onDismiss,
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    info: AppUpdateInfo,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val updateButtonRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { updateButtonRequester.requestFocus() } catch (_: Exception) {}
    }

    LiquidGlassSurface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 500.dp)
            .fillMaxWidth(),
        cornerRadius = 24.dp,
        glassColor = colors.panel,
        glassAlpha = 0.95f,
        borderColor = colors.border,
        borderAlpha = 0.75f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(colors.accent.copy(alpha = 0.35f), Color.Transparent)
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(colors.accent, colors.waveTwo)
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "تحديث جديد متاح!",
                    style = LiquidTypography.HeadlineSmall.copy(
                        brush = Brush.linearGradient(
                            listOf(colors.text, colors.accentText)
                        )
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.surface, RoundedCornerShape(50))
                            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "الحالي: v${BuildConfig.VERSION_NAME}",
                            style = LiquidTypography.LabelSmall,
                            color = colors.muted,
                        )
                    }
                    Text("←", style = LiquidTypography.LabelSmall, color = colors.accentText)
                    Box(
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.20f), RoundedCornerShape(50))
                            .border(1.dp, colors.accent.copy(alpha = 0.65f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "الجديد: v${info.versionName}",
                            style = LiquidTypography.LabelSmall,
                            color = colors.accentText,
                        )
                    }
                }
            }

            if (info.releaseNotes.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = info.releaseNotes,
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            if (info.isMandatory) {
                Text(
                    text = "تحديث إجباري مطلوب لمواصلة استخدام المزايا المدعومة",
                    style = LiquidTypography.LabelMedium,
                    color = colors.danger,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!info.isMandatory) {
                    DialogButton(
                        text = "لاحقاً",
                        isPrimary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
                DialogButton(
                    text = "تحديث الآن",
                    isPrimary = true,
                    onClick = onUpdateClick,
                    focusRequester = updateButtonRequester,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    state: UpdateState.Downloading,
    onCancel: () -> Unit,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        cornerRadius = 24.dp,
        glassColor = colors.panel,
        glassAlpha = 0.95f,
        borderColor = colors.border,
        borderAlpha = 0.75f,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(26.dp),
                )
            }

            Text(
                text = "جارٍ تنزيل التحديث...",
                style = LiquidTypography.TitleMedium,
                color = colors.text,
            )

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = colors.accent,
                trackColor = colors.surface,
            )

            val pct = (state.progress * 100).toInt()
            val dlMb = String.format(java.util.Locale.US, "%.1f", state.downloadedBytes / (1024f * 1024f))
            val totalMb = if (state.totalBytes > 0) String.format(java.util.Locale.US, "%.1f", state.totalBytes / (1024f * 1024f)) else "؟"
            Text(
                text = "$pct% ($dlMb م.ب / $totalMb م.ب)",
                style = LiquidTypography.LabelSmall,
                color = colors.textSecondary,
            )

            DialogButton(
                text = "إلغاء",
                isPrimary = false,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.55f),
            )
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    onInstallClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        cornerRadius = 24.dp,
        glassColor = colors.panel,
        glassAlpha = 0.95f,
        borderColor = colors.border,
        borderAlpha = 0.75f,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.SystemUpdate, null, tint = colors.success, modifier = Modifier.size(40.dp))
            Text("التحديث جاهز للتثبيت!", style = LiquidTypography.TitleMedium, color = colors.text)
            Text(
                "اكتمل تنزيل التحديث بنجاح وهو جاهز الآن للتثبيت.",
                style = LiquidTypography.BodySmall,
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("إغلاق", isPrimary = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton("تثبيت الآن", isPrimary = true, onClick = onInstallClick, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        cornerRadius = 24.dp,
        glassColor = colors.panel,
        glassAlpha = 0.95f,
        borderColor = colors.border,
        borderAlpha = 0.75f,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(40.dp))
            Text("التحقق من التحديثات", style = LiquidTypography.TitleMedium, color = colors.text)
            Text(message, style = LiquidTypography.BodySmall, color = colors.textSecondary)
            DialogButton("إغلاق", isPrimary = false, onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.55f))
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "dlg_btn_scale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .then(
                if (isPrimary) {
                    Modifier
                        .background(Brush.linearGradient(listOf(colors.accent, colors.waveTwo)))
                        .border(
                            if (isFocused) 2.5.dp else 1.dp,
                            if (isFocused) colors.focus else colors.accent.copy(alpha = 0.7f),
                            RoundedCornerShape(50),
                        )
                } else {
                    Modifier
                        .background(if (isFocused) colors.selected else colors.surface)
                        .border(
                            if (isFocused) 2.dp else 1.dp,
                            if (isFocused) colors.focus else colors.border,
                            RoundedCornerShape(50),
                        )
                }
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LiquidTypography.LabelLarge,
            color = if (isPrimary) colors.onAccent else colors.text,
        )
    }
}
