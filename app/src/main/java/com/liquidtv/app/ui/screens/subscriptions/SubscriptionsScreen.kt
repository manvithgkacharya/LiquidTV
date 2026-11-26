package com.liquidtv.app.ui.screens.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.PlaylistActionPill
import com.liquidtv.app.ui.components.PlaylistSubscriptionCard
import com.liquidtv.app.ui.components.SonixDeviceStatusBanner
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    onAddSubscription: () -> Unit,
    onEditSubscription: (String) -> Unit,
    onActivated: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    val colors = AppTheme.colors

    BackHandler { onBack() }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val hPad = if (maxWidth < 600.dp) 16.dp else 40.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.surface.copy(alpha = 0.82f))
                                .border(1.dp, colors.border.copy(alpha = 0.55f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = colors.text,
                            )
                        }
                        Column {
                            Text(
                                text = "قوائم التشغيل",
                                style = LiquidTypography.HeadlineMedium.copy(
                                    brush = Brush.linearGradient(
                                        listOf(colors.text, colors.accentText),
                                    ),
                                ),
                            )
                            Text(
                                text = "إدارة قوائم URL عادي والسيرفرات المخصصة",
                                style = LiquidTypography.BodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    PlaylistActionPill(
                        text = "إضافة قائمة",
                        icon = Icons.Filled.Add,
                        isPrimary = true,
                        onClick = onAddSubscription,
                        modifier = Modifier.testTag("add_subscription_button"),
                    )
                }

                SonixDeviceStatusBanner(
                    sonixState = state.sonixState,
                    onRefresh = viewModel::refreshPanel,
                )

                state.message?.let { msg ->
                    LiquidGlassSurface(
                        cornerRadius = 14.dp,
                        glassAlpha = 0.22f,
                        borderAlpha = 0.45f,
                        glassColor = colors.success,
                        borderColor = colors.success,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(msg, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearFeedback) {
                                Text("إغلاق", color = colors.accentText)
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    LiquidGlassSurface(
                        cornerRadius = 14.dp,
                        glassAlpha = 0.25f,
                        borderAlpha = 0.5f,
                        glassColor = colors.danger,
                        borderColor = colors.danger,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(err, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearFeedback) {
                                Text("إغلاق", color = colors.text)
                            }
                        }
                    }
                }

                if (state.subscriptions.isEmpty()) {
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        glassAlpha = 0.18f,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Subscriptions,
                                contentDescription = null,
                                tint = colors.accentText,
                                modifier = Modifier.size(48.dp),
                            )
                            Text("لا توجد قوائم تشغيل محفوظة", style = LiquidTypography.TitleMedium, color = colors.text)
                            Text(
                                "أضف قائمة تشغيل عبر URL عادي أو اختر من السيرفرات المخصصة لتحميل القنوات والمحتوى.",
                                style = LiquidTypography.BodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(state.subscriptions, key = { it.id }) { sub ->
                            PlaylistSubscriptionCard(
                                subscription = sub,
                                deviceCode = state.sonixState.deviceCode,
                                isBusy = state.busySubscriptionId == sub.id ||
                                    (state.syncInfo.isLoading && state.syncInfo.subscriptionId == sub.id),
                                onConnectOrActivate = {
                                    viewModel.activate(sub.id, onSuccess = onActivated)
                                },
                                onSync = { viewModel.sync(sub.id) },
                                onEdit = { onEditSubscription(sub.id) },
                                onDelete = { pendingDelete = sub },
                            )
                        }
                    }
                }
            }

            pendingDelete?.let { sub ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("حذف قائمة التشغيل", color = colors.text) },
                    text = {
                        Text(
                            "هل تريد حذف \"${sub.name}\" وجميع قنواته وتصنيفاته ومفضلته ودليل البرامج المخزن؟",
                            color = colors.textSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.delete(sub.id)
                                pendingDelete = null
                            },
                        ) {
                            Text("حذف", color = colors.danger)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text("إلغاء", color = colors.accentText)
                        }
                    },
                    containerColor = colors.panel,
                )
            }
        }
    }
}
