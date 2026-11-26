package com.liquidtv.app.ui.screens.addons

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.DefaultAddonsRegistry
import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun AddonsScreen(
    onBack: () -> Unit,
    viewModel: AddonsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = AppTheme.colors
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    LaunchedEffect(state.pendingConfigureUrl) {
        val url = state.pendingConfigureUrl ?: return@LaunchedEffect
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        viewModel.consumeConfigureUrl()
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("addons_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = colors.text,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Extension,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(26.dp),
                    )
                    Column {
                        Text(
                            text = "الإضافات",
                            style = LiquidTypography.TitleLarge,
                            color = colors.text,
                        )
                        Text(
                            text = "إدارة إضافات الترجمات والمحتوى",
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.loadMarket(force = true) },
                    enabled = !state.isLoadingMarket,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("addons_refresh_market_button"),
                ) {
                    if (state.isLoadingMarket) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.accentText)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("تحديث السوق", color = colors.text)
                }
            }

            // Tabs Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("addons_tabs_row"),
            ) {
                items(AddonsTab.entries, key = { it.name }) { tab ->
                    val selected = state.activeTab == tab
                    val count = when (tab) {
                        AddonsTab.INSTALLED -> state.installedAddons.size
                        AddonsTab.OFFICIAL -> state.officialMarket.size
                        AddonsTab.COMMUNITY -> state.communityMarket.size
                    }
                    AddonTabPill(
                        label = "${tab.titleAr} ($count)",
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        modifier = Modifier.testTag("addons_tab_${tab.name.lowercase()}"),
                    )
                }
            }

            // Search & Manual Manifest URL Row
            LiquidGlassSurface(
                cornerRadius = 16.dp,
                glassColor = colors.panel,
                glassAlpha = 0.22f,
                borderColor = colors.border,
                borderAlpha = 0.5f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accentText)
                        },
                        placeholder = {
                            Text("ابحث بالاسم أو الوصف أو المعرّف...", color = colors.muted)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            focusedBorderColor = colors.focus,
                            unfocusedBorderColor = colors.border,
                            cursorColor = colors.accent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("addons_search_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.manifestUrlInput,
                            onValueChange = viewModel::onManifestUrlChanged,
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.AddLink, contentDescription = null, tint = colors.accentText)
                            },
                            placeholder = {
                                Text("https://...", color = colors.muted)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.text,
                                unfocusedTextColor = colors.text,
                                focusedBorderColor = colors.focus,
                                unfocusedBorderColor = colors.border,
                                cursorColor = colors.accent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("addons_manifest_url_input"),
                        )
                        Button(
                            onClick = viewModel::installManualManifestUrl,
                            enabled = !state.isInstallingManualUrl,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                            ),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("addons_install_manifest_button"),
                        ) {
                            if (state.isInstallingManualUrl) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.onAccent,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("تثبيت الإضافة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Status / Error banners
            state.errorMessage?.let { err ->
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.danger,
                    glassAlpha = 0.25f,
                    borderColor = colors.danger,
                    modifier = Modifier.testTag("addons_error_banner"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissMessages) {
                            Text("إغلاق", color = colors.accentText)
                        }
                    }
                }
            }

            state.statusMessage?.let { msg ->
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.panel,
                    glassAlpha = 0.30f,
                    borderColor = colors.accent,
                    modifier = Modifier.testTag("addons_status_banner"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg, style = LiquidTypography.BodySmall, color = colors.text, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissMessages) {
                            Text("حسناً", color = colors.accentText)
                        }
                    }
                }
            }

            val visibleList = state.visibleAddons()

            if (state.isLoadingMarket && state.activeTab != AddonsTab.INSTALLED && visibleList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = colors.accent)
                        Text("جارٍ تحميل الإضافات...", style = LiquidTypography.BodyMedium, color = colors.textSecondary)
                    }
                }
            } else if (visibleList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("addons_empty_state"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.activeTab == AddonsTab.INSTALLED) {
                            "لا توجد إضافات مطابقة لبحثك"
                        } else {
                            "لا توجد نتائج في هذا المتجر حالياً. جرّب التحديث أو أدخل رابط الإضافة يدوياً."
                        },
                        style = LiquidTypography.BodyMedium,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("addons_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleList, key = { "${state.activeTab.name}_${it.id}_${it.url}" }) { addon ->
                        AddonCard(
                            addon = addon,
                            isInstalledTab = state.activeTab == AddonsTab.INSTALLED,
                            alreadyInstalled = state.isInstalled(addon),
                            isBusy = state.busyAddonId == addon.id,
                            onToggleEnabled = { viewModel.toggleAddonEnabled(addon) },
                            onRefreshManifest = { viewModel.refreshInstalledAddon(addon) },
                            onRemove = { viewModel.removeAddon(addon) },
                            onInstall = { viewModel.installFromMarket(addon) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = modifier
            .height(48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 24.dp,
        glassColor = if (selected) colors.selected else colors.surface,
        glassAlpha = if (selected) 0.30f else 0.14f,
        borderColor = when {
            isFocused -> colors.focus
            selected -> colors.accent
            else -> colors.border
        },
        borderAlpha = if (selected || isFocused) 0.95f else 0.5f,
        isFocused = isFocused,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.LabelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) colors.text else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AddonCard(
    addon: StremioAddon,
    isInstalledTab: Boolean,
    alreadyInstalled: Boolean,
    isBusy: Boolean,
    onToggleEnabled: () -> Unit,
    onRefreshManifest: () -> Unit,
    onRemove: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = AppTheme.colors
    val isDefault = DefaultAddonsRegistry.isDefaultAddon(addon.id)

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("addon_card_${addon.id}"),
        cornerRadius = 16.dp,
        glassColor = colors.card,
        glassAlpha = 0.20f,
        borderColor = if (addon.enabled || !isInstalledTab) colors.border else colors.muted,
        borderAlpha = 0.55f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = addon.name,
                            style = LiquidTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (addon.version.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.surface)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "v${addon.version}",
                                    style = LiquidTypography.LabelSmall,
                                    color = colors.accentText,
                                )
                            }
                        }
                        if (addon.configurationRequired || addon.url.contains("/[config]/")) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.warning.copy(alpha = 0.2f))
                                    .border(1.dp, colors.warning.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "يتطلب تهيئة",
                                    style = LiquidTypography.LabelSmall,
                                    color = colors.warning,
                                )
                            }
                        }
                    }

                    Text(
                        text = addon.description.ifBlank { "إضافة متوافقة مع البث والترجمة والمحتوى" },
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isInstalledTab) {
                    Switch(
                        checked = addon.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.muted,
                            uncheckedTrackColor = colors.surface,
                            uncheckedBorderColor = colors.border,
                        ),
                        modifier = Modifier.testTag("addon_toggle_${addon.id}"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val resSummary = buildString {
                    if (addon.resources.isNotEmpty()) {
                        append(addon.resources.joinToString(" • "))
                    }
                    if (addon.types.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append(addon.types.joinToString(", "))
                    }
                }
                Text(
                    text = resSummary.ifBlank { addon.id },
                    style = LiquidTypography.LabelSmall,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isInstalledTab) {
                        OutlinedButton(
                            onClick = onRefreshManifest,
                            enabled = !isBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("addon_refresh_${addon.id}"),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = colors.accentText)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("تحديث", color = colors.text)
                        }
                        if (!isDefault) {
                            OutlinedButton(
                                onClick = onRemove,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("addon_remove_${addon.id}"),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "إزالة", tint = colors.danger)
                                Spacer(Modifier.width(4.dp))
                                Text("إزالة", color = colors.danger)
                            }
                        }
                    } else {
                        Button(
                            onClick = onInstall,
                            enabled = !alreadyInstalled && !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alreadyInstalled) colors.surface else colors.accent,
                                contentColor = if (alreadyInstalled) colors.textSecondary else colors.onAccent,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("addon_install_${addon.id}"),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = colors.onAccent,
                                    strokeWidth = 2.dp,
                                )
                            } else if (addon.configurationRequired || addon.url.contains("/[config]/")) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("تهيئة")
                            } else {
                                Text(if (alreadyInstalled) "مثبّتة" else "تثبيت", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
