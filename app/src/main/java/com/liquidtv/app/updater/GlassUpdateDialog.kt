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
import com.liquidtv.app.ui.theme.LiquidColors
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

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = spring(stiffness = 500f)),
        exit = fadeOut(animationSpec = spring(stiffness = 500f)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f)),
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
    val updateButtonRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { updateButtonRequester.requestFocus() } catch (_: Exception) {}
    }

    LiquidGlassSurface(
        modifier = Modifier
            .width(520.dp)
            .wrapContentHeight(),
        cornerRadius = 24.dp,
        glassAlpha = 0.35f,
        borderAlpha = 0.45f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Icon with glowing halo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(LiquidColors.AccentBlue.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Title & Version Chip
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "New Update Available!",
                    style = LiquidTypography.HeadlineSmall.copy(
                        brush = Brush.linearGradient(
                            listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight)
                        )
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME}",
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.TextTertiary,
                        )
                    }
                    Text("→", style = LiquidTypography.LabelSmall, color = LiquidColors.AccentBlue)
                    Box(
                        modifier = Modifier
                            .background(LiquidColors.AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .border(1.dp, LiquidColors.AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "New: v${info.versionName}",
                            style = LiquidTypography.LabelSmall,
                            color = LiquidColors.AccentBlueLight,
                        )
                    }
                }
            }

            // Release Notes Box
            if (info.releaseNotes.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LiquidColors.GlassTertiary)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = info.releaseNotes,
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextSecondary,
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Later button
                DialogButton(
                    text = "Later",
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                // Update Now button
                DialogButton(
                    text = "Update Now",
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
    LiquidGlassSurface(
        modifier = Modifier.width(480.dp),
        cornerRadius = 24.dp,
        glassAlpha = 0.35f,
        borderAlpha = 0.45f,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(LiquidColors.AccentBlue.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = LiquidColors.AccentBlue,
                    modifier = Modifier.size(26.dp),
                )
            }

            Text(
                text = "Downloading Update...",
                style = LiquidTypography.TitleMedium,
                color = LiquidColors.TextPrimary,
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = LiquidColors.AccentBlue,
                trackColor = Color.White.copy(alpha = 0.15f),
            )

            // Progress text
            val pct = (state.progress * 100).toInt()
            val dlMb = String.format("%.1f", state.downloadedBytes / (1024f * 1024f))
            val totalMb = if (state.totalBytes > 0) String.format("%.1f", state.totalBytes / (1024f * 1024f)) else "?"
            Text(
                text = "$pct% ($dlMb MB / $totalMb MB)",
                style = LiquidTypography.LabelSmall,
                color = LiquidColors.TextSecondary,
            )

            DialogButton(
                text = "Cancel",
                isPrimary = false,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    onInstallClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiquidGlassSurface(
        modifier = Modifier.width(480.dp),
        cornerRadius = 24.dp,
        glassAlpha = 0.35f,
        borderAlpha = 0.45f,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.SystemUpdate, null, tint = LiquidColors.SuccessGreen, modifier = Modifier.size(40.dp))
            Text("Update Downloaded!", style = LiquidTypography.TitleMedium, color = LiquidColors.TextPrimary)
            Text(
                "The update has finished downloading and is ready to install.",
                style = LiquidTypography.BodySmall,
                color = LiquidColors.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Dismiss", isPrimary = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton("Install Now", isPrimary = true, onClick = onInstallClick, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit,
) {
    LiquidGlassSurface(
        modifier = Modifier.width(480.dp),
        cornerRadius = 24.dp,
        glassAlpha = 0.35f,
        borderAlpha = 0.45f,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = LiquidColors.ErrorRed, modifier = Modifier.size(40.dp))
            Text("Update Check", style = LiquidTypography.TitleMedium, color = LiquidColors.TextPrimary)
            Text(message, style = LiquidTypography.BodySmall, color = LiquidColors.TextSecondary)
            DialogButton("Dismiss", isPrimary = false, onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.5f))
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
                        .background(
                            Brush.linearGradient(
                                listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                            )
                        )
                        .border(
                            if (isFocused) 2.5.dp else 1.dp,
                            if (isFocused) Color.White else Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(50),
                        )
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = if (isFocused) 0.22f else 0.10f))
                        .border(
                            if (isFocused) 2.dp else 1.dp,
                            if (isFocused) LiquidColors.AccentBlue else Color.White.copy(alpha = 0.20f),
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
            color = Color.White,
        )
    }
}
