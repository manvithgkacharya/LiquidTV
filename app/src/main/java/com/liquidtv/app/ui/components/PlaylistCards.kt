package com.liquidtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liquidtv.app.data.sonix.SonixAccessStatus
import com.liquidtv.app.data.sonix.SonixSyncState
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.utils.formatIsoDateShortAr
import com.liquidtv.app.ui.utils.localizedSonixAccessStatusAr
import com.liquidtv.app.ui.utils.localizedSubscriptionOriginAr
import com.liquidtv.app.ui.utils.localizedSubscriptionStatusAr
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SonixDeviceStatusBanner(
    sonixState: SonixSyncState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val statusColor = when (sonixState.accessStatus) {
        SonixAccessStatus.ACTIVE -> colors.success
        SonixAccessStatus.EXPIRED -> colors.warning
        SonixAccessStatus.BLOCKED, SonixAccessStatus.REVOKED -> colors.danger
        SonixAccessStatus.UNKNOWN -> colors.muted
    }

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonix_device_status_banner"),
        cornerRadius = 16.dp,
        glassColor = colors.panel,
        glassAlpha = 0.84f,
        borderColor = statusColor.copy(alpha = 0.5f),
        borderAlpha = 0.7f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Devices,
                        contentDescription = "معرّف الجهاز",
                        tint = colors.accentText,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = "معرّف الجهاز (Device ID)",
                            style = LiquidTypography.LabelSmall,
                            color = colors.muted,
                        )
                        Text(
                            text = sonixState.deviceCode?.takeIf { it.isNotBlank() } ?: "جارٍ التسجيل...",
                            style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                            modifier = Modifier.testTag("sonix_device_code_text"),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaylistBadgeChip(
                        text = localizedSonixAccessStatusAr(sonixState.accessStatus),
                        color = statusColor,
                    )
                    PlaylistActionPill(
                        text = if (sonixState.isSyncing) "تحديث..." else "مزامنة",
                        icon = Icons.Filled.Refresh,
                        isPrimary = false,
                        enabled = !sonixState.isSyncing,
                        onClick = onRefresh,
                        modifier = Modifier.testTag("sonix_refresh_button"),
                    )
                }
            }

            sonixState.subscription?.let { sub ->
                val endDate = formatIsoDateShortAr(sub.endsAt)
                val trialPrefix = if (sub.isTrial) "فترة تجريبية" else sub.planName.ifBlank { "اشتراك مفعّل" }
                val expiryText = buildString {
                    append(trialPrefix)
                    if (endDate != null) append(" • ينتهي: $endDate")
                    if (sub.daysRemaining > 0) append(" (${sub.daysRemaining} يوم)")
                }
                Text(
                    text = expiryText,
                    style = LiquidTypography.LabelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("sonix_subscription_expiry_text"),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistSubscriptionCard(
    subscription: Subscription,
    deviceCode: String?,
    isBusy: Boolean,
    onConnectOrActivate: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    val colors = AppTheme.colors
    val statusColor = when (subscription.connectionStatus) {
        SubscriptionStatus.CONNECTED -> colors.success
        SubscriptionStatus.EXPIRED -> colors.warning
        SubscriptionStatus.ERROR -> colors.danger
        SubscriptionStatus.DISCONNECTED -> colors.muted
    }

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subscription_card_${subscription.id}"),
        cornerRadius = 18.dp,
        glassColor = if (subscription.isActive) colors.selected else colors.card,
        glassAlpha = if (subscription.isActive) 0.90f else 0.78f,
        borderAlpha = if (subscription.isActive) 0.85f else 0.42f,
        borderColor = if (subscription.isActive) colors.accent else colors.border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactMode) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 8.dp),
                    ) {
                        Text(
                            text = subscription.name,
                            style = if (compactMode) LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold) else LiquidTypography.TitleMedium,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PlaylistBadgeChip(
                            text = localizedSubscriptionOriginAr(subscription.origin),
                            color = colors.accentText,
                            compactMode = compactMode,
                        )
                        if (subscription.isActive) {
                            PlaylistBadgeChip(
                                text = "نشط",
                                color = colors.success,
                                compactMode = compactMode,
                            )
                        }
                        if (compactMode) {
                            PlaylistBadgeChip(
                                text = localizedSubscriptionStatusAr(subscription.connectionStatus),
                                color = statusColor,
                                compactMode = true,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // For PANEL_SERVER subscriptions, NEVER reveal raw Host URL — show only panel server name!
                    if (subscription.origin == SubscriptionOrigin.PANEL_SERVER) {
                        Text(
                            text = "السيرفر: ${subscription.panelServerName?.takeIf { it.isNotBlank() } ?: "سيرفر مخصص"}",
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = subscription.serverUrl,
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        subscription.username?.let { user ->
                            Text(
                                text = "المستخدم: $user",
                                style = LiquidTypography.LabelSmall,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        deviceCode?.takeIf { it.isNotBlank() }?.let { code ->
                            Text(
                                text = "Device ID: $code",
                                style = LiquidTypography.LabelSmall,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (!compactMode) {
                    Spacer(Modifier.width(8.dp))
                    PlaylistBadgeChip(
                        text = localizedSubscriptionStatusAr(subscription.connectionStatus),
                        color = statusColor,
                    )
                }
            }

            if (subscription.accountStatus != null ||
                subscription.expDateEpochSec != null ||
                subscription.maxConnections != null
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subscription.accountStatus?.let {
                        Text("الحالة: $it", style = LiquidTypography.LabelSmall, color = colors.textSecondary)
                    }
                    subscription.expDateEpochSec?.let { exp ->
                        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("ar")).format(Date(exp * 1000L))
                        Text("ينتهي في: $dateText", style = LiquidTypography.LabelSmall, color = colors.textSecondary)
                    }
                    if (subscription.activeConnections != null || subscription.maxConnections != null) {
                        Text(
                            "الاتصالات: ${subscription.activeConnections ?: "؟"} / ${subscription.maxConnections ?: "؟"}",
                            style = LiquidTypography.LabelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            subscription.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    style = LiquidTypography.BodySmall,
                    color = colors.danger,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 8.dp),
            ) {
                PlaylistActionPill(
                    text = if (subscription.isActive) "فتح القائمة" else "اتصال وتفعيل",
                    icon = Icons.Filled.CheckCircle,
                    isPrimary = true,
                    enabled = !isBusy,
                    compactMode = compactMode,
                    onClick = onConnectOrActivate,
                    modifier = Modifier.testTag("playlist_connect_${subscription.id}"),
                )
                PlaylistActionPill(
                    text = if (isBusy) "جارٍ التحديث…" else "تحديث",
                    icon = Icons.Filled.Refresh,
                    isPrimary = false,
                    enabled = !isBusy,
                    compactMode = compactMode,
                    onClick = onSync,
                    modifier = Modifier.testTag("playlist_sync_${subscription.id}"),
                )
                PlaylistActionPill(
                    text = "تعديل",
                    icon = Icons.Filled.Edit,
                    isPrimary = false,
                    enabled = !isBusy,
                    compactMode = compactMode,
                    onClick = onEdit,
                    modifier = Modifier.testTag("playlist_edit_${subscription.id}"),
                )
                PlaylistActionPill(
                    text = "حذف",
                    icon = Icons.Filled.Delete,
                    isPrimary = false,
                    enabled = !isBusy,
                    tint = colors.danger,
                    compactMode = compactMode,
                    onClick = onDelete,
                    modifier = Modifier.testTag("playlist_delete_${subscription.id}"),
                )
            }
        }
    }
}

@Composable
fun PlaylistBadgeChip(text: String, color: Color, compactMode: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(
                horizontal = if (compactMode) 7.dp else 10.dp,
                vertical = if (compactMode) 2.dp else 3.dp,
            ),
    ) {
        Text(
            text = text,
            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun PlaylistActionPill(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    enabled: Boolean = true,
    tint: Color? = null,
    compactMode: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val resolvedTint = if (isPrimary) colors.onAccent else (tint ?: colors.text)

    Box(
        modifier = modifier
            .height(if (compactMode) 40.dp else 48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    isPrimary -> colors.accent
                    isFocused -> colors.selected
                    else -> colors.surface.copy(alpha = 0.80f)
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else if (isPrimary) colors.border.copy(alpha = 0.5f) else colors.border,
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = if (compactMode) 10.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp),
        ) {
            if (isPrimary && !enabled) {
                CircularProgressIndicator(
                    color = resolvedTint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(if (compactMode) 14.dp else 16.dp),
                )
            } else {
                Icon(icon, contentDescription = null, tint = resolvedTint, modifier = Modifier.size(if (compactMode) 15.dp else 18.dp))
            }
            Text(
                text = text,
                style = if (compactMode) LiquidTypography.LabelSmall else LiquidTypography.LabelLarge,
                color = resolvedTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
