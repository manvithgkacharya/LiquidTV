package com.liquidtv.app.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.UpdateState

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Appearance("Appearance", Icons.Filled.Palette),
    Playback("Playback", Icons.Filled.PlayCircle),
    Data("Data & Playlist", Icons.Filled.CloudDownload),
    Updates("Updates & GitHub", Icons.Filled.SystemUpdate),
    Ambient("Ambient Mode", Icons.Filled.NightlightRound),
    About("About", Icons.Filled.Info),
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    var selectedSection by remember { mutableStateOf(SettingsSection.Appearance) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var repoInput by remember { mutableStateOf("") }

    AppBackground(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp, start = 36.dp, end = 36.dp),
        ) {
            Text(
                text = "Settings",
                style = LiquidTypography.HeadlineMedium.copy(
                    brush = Brush.linearGradient(
                        listOf(LiquidColors.TextPrimary, LiquidColors.AccentBlueLight)
                    )
                ),
                color = LiquidColors.TextPrimary,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // Left Column: Navigation Sidebar
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsSection.entries.forEach { section ->
                        val isSelected = selectedSection == section
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        LiquidGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable(interactionSource, null) { selectedSection = section }
                                .focusable(interactionSource = interactionSource),
                            cornerRadius = 14.dp,
                            glassAlpha = if (isSelected) 0.35f else if (isFocused) 0.22f else 0.10f,
                            borderAlpha = if (isFocused) 0.7f else if (isSelected) 0.4f else 0.16f,
                            borderColor = if (isSelected) LiquidColors.AccentBlue else Color.White,
                            isFocused = isFocused,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.title,
                                    tint = if (isSelected) LiquidColors.AccentBlue else LiquidColors.TextSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = section.title,
                                    style = LiquidTypography.TitleSmall,
                                    color = if (isSelected) LiquidColors.TextPrimary else LiquidColors.TextSecondary,
                                )
                            }
                        }
                    }
                }

                // Right Column: Settings Content Panel
                LiquidGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 24.dp,
                    glassAlpha = 0.14f,
                    borderAlpha = 0.22f,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        when (selectedSection) {
                            SettingsSection.Appearance -> {
                                item {
                                    SettingHeader("Visual & Glass Experience", "Configure Liquid Glass refraction and themes")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Ambient Theme",
                                        subtitle = "Theme styling for background and screensaver",
                                        action = {
                                            Text(
                                                text = uiState.theme.replaceFirstChar { it.uppercase() },
                                                style = LiquidTypography.LabelLarge,
                                                color = LiquidColors.AccentBlue,
                                            )
                                        },
                                        onClick = {
                                            val themes = listOf("midnight_glass", "aurora", "ocean", "cosmic", "minimal")
                                            val nextIndex = (themes.indexOf(uiState.theme) + 1) % themes.size
                                            viewModel.setTheme(themes[nextIndex])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Clock Display",
                                        subtitle = "Show clock in ambient mode and home header",
                                        action = {
                                            Switch(
                                                checked = uiState.clockVisible,
                                                onCheckedChange = { viewModel.setClockVisible(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setClockVisible(!uiState.clockVisible) },
                                    )
                                }
                            }
                            SettingsSection.Playback -> {
                                item {
                                    SettingHeader("Playback & Streaming", "ExoPlayer hardware decoding and buffering")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Auto-Play Next Channel",
                                        subtitle = "Automatically switch to next stream on playback finish or error",
                                        action = {
                                            Switch(
                                                checked = uiState.autoPlay,
                                                onCheckedChange = { viewModel.setAutoPlay(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = LiquidColors.AccentBlue,
                                                    checkedTrackColor = LiquidColors.AccentBlueDark,
                                                ),
                                            )
                                        },
                                        onClick = { viewModel.setAutoPlay(!uiState.autoPlay) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Connection Timeout",
                                        subtitle = "Timeout duration before stream error recovery: ${uiState.connectionTimeout}s",
                                        action = {
                                            Text("${uiState.connectionTimeout}s", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val timeouts = listOf(10, 15, 20, 30)
                                            val nextIndex = (timeouts.indexOf(uiState.connectionTimeout) + 1) % timeouts.size
                                            viewModel.setConnectionTimeout(timeouts[nextIndex])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Retry Attempts",
                                        subtitle = "Maximum automatic reconnection tries on stream failure: ${uiState.maxRetries}",
                                        action = {
                                            Text("${uiState.maxRetries}", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val retries = listOf(1, 2, 3, 5)
                                            val nextIndex = (retries.indexOf(uiState.maxRetries) + 1) % retries.size
                                            viewModel.setMaxRetries(retries[nextIndex])
                                        },
                                    )
                                }
                            }
                            SettingsSection.Data -> {
                                item {
                                    SettingHeader("IPTV Repository & Cache", "Manage M3U playlist data source and storage")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Refresh IPTV Playlist",
                                        subtitle = if (uiState.isRefreshing) "Downloading latest iptv-org index..." else "Fetch fresh stream links from iptv-org GitHub repository",
                                        action = {
                                            if (uiState.isRefreshing) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = LiquidColors.AccentBlue)
                                            } else {
                                                Icon(Icons.Filled.Refresh, null, tint = LiquidColors.AccentBlue)
                                            }
                                        },
                                        onClick = { viewModel.refreshPlaylists() },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Clear Cache & History",
                                        subtitle = "Delete watch history and search cache",
                                        action = {
                                            Icon(Icons.Filled.Delete, null, tint = LiquidColors.LiveRed)
                                        },
                                        onClick = { viewModel.clearCache() },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Default Repository Endpoint",
                                        subtitle = "https://iptv-org.github.io/iptv/index.m3u",
                                        action = {
                                            Icon(Icons.Filled.Check, null, tint = LiquidColors.SuccessGreen)
                                        },
                                        onClick = {},
                                    )
                                }
                            }
                            SettingsSection.Ambient -> {
                                item {
                                    SettingHeader("Ambient Screensaver", "Cinematic Apple TV style glass idle display")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Screensaver Timeout",
                                        subtitle = "Start screensaver after inactivity: ${uiState.screenSaverTimeout} min",
                                        action = {
                                            Text("${uiState.screenSaverTimeout} min", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            val durations = listOf(2, 5, 10, 15)
                                            val nextIndex = (durations.indexOf(uiState.screenSaverTimeout) + 1) % durations.size
                                            viewModel.setScreenSaverTimeout(durations[nextIndex])
                                        },
                                    )
                                }
                            }
                            SettingsSection.Updates -> {
                                item {
                                    SettingHeader("App Updates & GitHub", "Automatically check or manually update LIQUID TV from your GitHub repository")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Check for Updates",
                                        subtitle = when (updateState) {
                                            is UpdateState.Checking -> "Checking GitHub for newer releases..."
                                            is UpdateState.UpToDate -> "You're on the latest version! (v${BuildConfig.VERSION_NAME})"
                                            is UpdateState.Available -> "Update Available: v${(updateState as UpdateState.Available).info.versionName} • Click to View"
                                            is UpdateState.Error -> "Check failed: ${(updateState as UpdateState.Error).message}"
                                            else -> "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                                        },
                                        action = {
                                            if (updateState is UpdateState.Checking) {
                                                CircularProgressIndicator(
                                                    color = LiquidColors.AccentBlue,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(LiquidColors.AccentBlue, LiquidColors.AccentViolet)
                                                            ),
                                                            RoundedCornerShape(50),
                                                        )
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                ) {
                                                    Text("Check Now", style = LiquidTypography.LabelMedium, color = Color.White)
                                                }
                                            }
                                        },
                                        onClick = {
                                            updateViewModel.checkForUpdate(uiState.githubRepo)
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "GitHub Repository",
                                        subtitle = uiState.githubRepo.ifBlank { "Click to set repository (owner/repo)" },
                                        action = {
                                            Icon(Icons.Filled.Edit, null, tint = LiquidColors.AccentBlue)
                                        },
                                        onClick = {
                                            repoInput = uiState.githubRepo
                                            showRepoDialog = true
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Auto-Check on App Launch",
                                        subtitle = if (uiState.autoCheckUpdates) "Checks GitHub in the background when the app opens" else "Manual updates only",
                                        action = {
                                            Switch(
                                                checked = uiState.autoCheckUpdates,
                                                onCheckedChange = { viewModel.setAutoCheckUpdates(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = LiquidColors.AccentBlue,
                                                ),
                                            )
                                        },
                                        onClick = {
                                            viewModel.setAutoCheckUpdates(!uiState.autoCheckUpdates)
                                        },
                                    )
                                }
                            }
                            SettingsSection.About -> {
                                item {
                                    SettingHeader("About LIQUID TV", "Architected for Android TV & Google TV")
                                }
                                item {
                                    SettingItemTile(
                                        title = "Application Version",
                                        subtitle = "1.0.0 (Liquid Glass Edition - Production Architecture)",
                                        action = {},
                                        onClick = {},
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Open Source Channel Attribution",
                                        subtitle = "Playlists provided by iptv-org (github.com/iptv-org/iptv). All channel streams belong to their respective broadcast providers.",
                                        action = {
                                            Icon(Icons.Filled.Public, null, tint = LiquidColors.TextTertiary)
                                        },
                                        onClick = {},
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "Design Reference",
                                        subtitle = "Apple Liquid Glass Material Design & Apple TV tvOS Human Interface Guidelines.",
                                        action = {},
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRepoDialog) {
            AlertDialog(
                onDismissRequest = { showRepoDialog = false },
                title = { Text("GitHub Repository", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Enter your GitHub repository as 'owner/repo' (for example: your-name/LiquidTV):",
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                        )
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = LiquidColors.AccentBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val sanitized = repoInput.trim()
                            .removePrefix("https://github.com/")
                            .removePrefix("http://github.com/")
                            .removePrefix("github.com/")
                            .removeSuffix(".git")
                            .trim('/')
                        viewModel.setGithubRepo(sanitized)
                        showRepoDialog = false
                    }) {
                        Text("Save", color = LiquidColors.AccentBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRepoDialog = false }) {
                        Text("Cancel", color = LiquidColors.TextTertiary)
                    }
                },
                containerColor = LiquidColors.DeepSurface,
            )
        }
    }
}

@Composable
private fun SettingHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(title, style = LiquidTypography.TitleLarge, color = LiquidColors.TextPrimary)
        Text(subtitle, style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
    }
}

@Composable
private fun SettingItemTile(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "settingScale",
    )

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 16.dp,
        glassAlpha = if (isFocused) 0.22f else 0.10f,
        borderAlpha = if (isFocused) 0.65f else 0.16f,
        borderColor = if (isFocused) LiquidColors.AccentBlue else Color.White,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = LiquidTypography.TitleMedium, color = LiquidColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = LiquidTypography.BodySmall, color = LiquidColors.TextTertiary)
            }
            action()
        }
    }
}

