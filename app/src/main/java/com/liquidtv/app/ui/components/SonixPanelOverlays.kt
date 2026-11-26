package com.liquidtv.app.ui.components

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liquidtv.app.data.sonix.SonixDeviceStatus
import com.liquidtv.app.data.sonix.SonixMessage
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun SonixBlockedAccessOverlay(
    sonixState: SonixSyncState,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    val isRevoked = sonixState.deviceStatus == SonixDeviceStatus.REVOKED
    val title = if (isRevoked) "تم إلغاء صلاحية هذا الجهاز" else "تم إيقاف الوصول لهذا الجهاز"
    val subtitle = if (isRevoked) {
        "تم سحب تصريح الوصول لهذا الجهاز من قبل مزود الخدمة."
    } else {
        "تم إيقاف الوصول لهذا الجهاز مؤقتاً من قبل مزود الخدمة."
    }

    AppBackground(modifier = modifier.fillMaxSize().testTag("sonix_blocked_overlay")) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                cornerRadius = 24.dp,
                glassColor = colors.panel,
                glassAlpha = 0.94f,
                borderColor = colors.danger,
                borderAlpha = 0.72f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(colors.danger.copy(alpha = 0.18f))
                            .border(1.dp, colors.danger.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = title,
                            tint = colors.danger,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    Text(
                        text = title,
                        style = LiquidTypography.HeadlineSmall,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = subtitle,
                        style = LiquidTypography.BodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )

                    sonixState.deviceCode?.takeIf { it.isNotBlank() }?.let { code ->
                        LiquidGlassSurface(
                            cornerRadius = 14.dp,
                            glassColor = colors.surface,
                            glassAlpha = 0.85f,
                            borderColor = colors.border,
                            borderAlpha = 0.55f,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "معرّف الجهاز (Device ID)",
                                    style = LiquidTypography.LabelSmall,
                                    color = colors.muted,
                                )
                                Text(
                                    text = code,
                                    style = LiquidTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.accentText,
                                    modifier = Modifier.testTag("blocked_device_code"),
                                )
                            }
                        }
                    }

                    sonixState.settings.supportNotice.takeIf { it.isNotBlank() }?.let { notice ->
                        Text(
                            text = notice,
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (focused) 1.04f else 1f,
                        animationSpec = spring(stiffness = 460f),
                        label = "blocked_retry_scale",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .scale(scale)
                            .focusRequester(focusRequester)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(colors.accent, colors.waveTwo)))
                            .border(
                                width = if (focused) 2.5.dp else 1.dp,
                                color = if (focused) colors.focus else colors.border,
                                shape = RoundedCornerShape(50),
                            )
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                enabled = !sonixState.isSyncing,
                                onClick = onRetrySync,
                            )
                            .focusable(interactionSource = interaction)
                            .testTag("blocked_retry_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sonixState.isSyncing) {
                            CircularProgressIndicator(
                                color = colors.onAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "إعادة التحقق",
                                    style = LiquidTypography.LabelLarge,
                                    color = colors.onAccent,
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
fun GlassPanelMessageDialog(
    message: SonixMessage?,
    onAcknowledge: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(animationSpec = spring(stiffness = 480f)),
        exit = fadeOut(animationSpec = spring(stiffness = 480f)),
    ) {
        if (message == null) return@AnimatedVisibility
        val ackFocusRequester = remember(message.id) { FocusRequester() }
        LaunchedEffect(message.id) {
            try { ackFocusRequester.requestFocus() } catch (_: Exception) {}
        }

        val (badgeText, badgeColor, icon) = when (message.messageType.lowercase()) {
            "urgent" -> Triple("تنبيه عاجل", colors.danger, Icons.Filled.Warning)
            "warning" -> Triple("تنبيه مهم", colors.warning, Icons.Filled.Campaign)
            else -> Triple("رسالة من الإدارة", colors.accentText, Icons.Filled.Info)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.dialogScrim)
                .testTag("panel_message_dialog"),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(),
                cornerRadius = 24.dp,
                glassColor = colors.panel,
                glassAlpha = 0.95f,
                borderColor = badgeColor.copy(alpha = 0.7f),
                borderAlpha = 0.8f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                        Text(
                            text = badgeText,
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                        )
                    }

                    Text(
                        text = message.title,
                        style = LiquidTypography.TitleLarge,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface.copy(alpha = 0.85f))
                            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = message.body,
                            style = LiquidTypography.BodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Start,
                        )
                    }

                    val ackInteraction = remember { MutableInteractionSource() }
                    val focused by ackInteraction.collectIsFocusedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (focused) 1.04f else 1f,
                        animationSpec = spring(stiffness = 480f),
                        label = "ack_msg_scale",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .scale(scale)
                            .focusRequester(ackFocusRequester)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(colors.accent, colors.waveTwo)))
                            .border(
                                width = if (focused) 2.5.dp else 1.dp,
                                color = if (focused) colors.focus else colors.accent.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(50),
                            )
                            .clickable(
                                interactionSource = ackInteraction,
                                indication = null,
                                onClick = { onAcknowledge(message.id) },
                            )
                            .focusable(interactionSource = ackInteraction)
                            .testTag("panel_message_ack_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "حسناً — تأكيد الاستلام",
                                style = LiquidTypography.LabelLarge,
                                color = colors.onAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaintenanceBannerStrip(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .testTag("maintenance_banner"),
        cornerRadius = 14.dp,
        glassColor = colors.warning,
        glassAlpha = 0.22f,
        borderColor = colors.warning,
        borderAlpha = 0.65f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Construction,
                contentDescription = "إشعار الصيانة",
                tint = colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = LiquidTypography.BodySmall,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
