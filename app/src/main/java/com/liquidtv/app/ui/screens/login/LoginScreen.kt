package com.liquidtv.app.ui.screens.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.R
import com.liquidtv.app.data.sonix.SonixAssignedServer
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.PlaylistBadgeChip
import com.liquidtv.app.ui.components.PlaylistSubscriptionCard
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.delay

/**
 * Two completely independent side-by-side Liquid Glass cards:
 * 1. اختيار السيرفر (`server_selection_main_card`):
 *    - Compact toggle button «اختيار سيرفر» with anchored floating Popup dropdown (`server_selector_list`)
 *    - Selecting «افتراضي URL» or any dynamic server opens a floating Liquid Glass Dialog without stretching either card
 * 2. قوائم التشغيل (`playlists_main_card`):
 *    - Saved subscriptions (Connect/Activate, Sync, Edit, Delete) or empty state
 *    - Device ID card inside playlists card with Copy action
 */
@Composable
fun LoginScreen(
    editingSubscriptionId: String? = null,
    allowBack: Boolean = false,
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val selectorToggleRequester = remember { FocusRequester() }
    val primaryInputRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    var wasOverlayOpen by remember { mutableStateOf(false) }

    val sonix = state.sonixState
    val isPanelLockedEdit = state.editingOrigin == SubscriptionOrigin.PANEL_SERVER
    val catalogServers = remember(sonix.hasLoadedCatalog, sonix.availableServers, sonix.assignedServers) {
        if (sonix.hasLoadedCatalog) {
            sonix.availableServers
        } else {
            sonix.availableServers.ifEmpty { sonix.assignedServers }
        }
    }
    val lockedFallbackServer = remember(isPanelLockedEdit, catalogServers, state.selectedPanelServerId, state.selectedPanelServerName) {
        val selId = state.selectedPanelServerId
        if (isPanelLockedEdit && !selId.isNullOrBlank() && catalogServers.none { it.id == selId }) {
            SonixAssignedServer(
                id = selId,
                name = state.selectedPanelServerName?.takeIf { it.isNotBlank() } ?: "سيرفر مخصص",
            )
        } else {
            null
        }
    }
    val displayServers = remember(catalogServers, lockedFallbackServer) {
        if (lockedFallbackServer != null) catalogServers + lockedFallbackServer else catalogServers
    }
    val showNormalUrlOption = !isPanelLockedEdit &&
        (sonix.settings.allowNormalUrlLogin || !sonix.catalogError.isNullOrBlank() || catalogServers.isEmpty())

    val isNormalUrlOpen = !isPanelLockedEdit &&
        state.isServerFormDialogOpen &&
        state.isNormalUrlSelected
    val activePanelServer = remember(
        state.isServerFormDialogOpen,
        isNormalUrlOpen,
        state.serverMode,
        state.selectedPanelServerId,
        displayServers,
    ) {
        if (state.isServerFormDialogOpen &&
            !isNormalUrlOpen &&
            state.serverMode == SubscriptionOrigin.PANEL_SERVER &&
            !state.selectedPanelServerId.isNullOrBlank()
        ) {
            displayServers.firstOrNull { it.id == state.selectedPanelServerId }
        } else {
            null
        }
    }
    val isFormDialogVisible = isNormalUrlOpen || activePanelServer != null

    LaunchedEffect(editingSubscriptionId) {
        viewModel.loadForEdit(editingSubscriptionId)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onLoginSuccess()
        }
    }

    // Restore D-pad focus cleanly to «اختيار سيرفر» after dismissing dropdown or dialog overlays.
    LaunchedEffect(state.isServerSelectorExpanded, isFormDialogVisible) {
        val currentlyOpen = state.isServerSelectorExpanded || isFormDialogVisible
        if (wasOverlayOpen && !currentlyOpen) {
            runCatching { selectorToggleRequester.requestFocus() }
        }
        wasOverlayOpen = currentlyOpen
    }

    BackHandler(enabled = !state.isConnecting && (isFormDialogVisible || state.isServerSelectorExpanded || allowBack)) {
        when {
            isFormDialogVisible -> viewModel.dismissServerFormDialog()
            state.isServerSelectorExpanded -> viewModel.collapseServerSelector()
            allowBack -> onBack()
        }
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val viewportWidth = maxWidth
            val viewportHeight = maxHeight
            val isCompactHeight = viewportHeight < 480.dp
            val isCompactWidth = viewportWidth < 520.dp
            val isCompactCards = viewportWidth < 600.dp
            val maxContainerWidth = when {
                maxWidth < 680.dp -> maxWidth
                maxWidth < 1100.dp -> minOf(maxWidth * 0.96f, 760.dp)
                else -> minOf(maxWidth * 0.88f, 920.dp)
            }
            val fieldInputHeight = when {
                isCompactHeight -> 36.dp
                isCompactWidth -> 38.dp
                else -> 42.dp
            }
            val cardsGap = if (isCompactWidth) 6.dp else 12.dp
            val outerHorizontalPadding = if (isCompactWidth) 6.dp else 16.dp
            val outerVerticalPadding = if (isCompactHeight) 8.dp else 16.dp
            val effectiveContainerWidth = minOf(maxWidth - outerHorizontalPadding * 2, maxContainerWidth)
            val singleCardWidth = (effectiveContainerWidth - cardsGap) / 2f
            val stackDeviceIdVertically = singleCardWidth < 235.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                val colors = AppTheme.colors
                Column(
                    modifier = Modifier
                        .widthIn(max = maxContainerWidth)
                        .fillMaxWidth()
                        .testTag("login_card"),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (allowBack) {
                            IconButton(
                                onClick = { if (!state.isConnecting) onBack() },
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
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Image(
                            painter = painterResource(id = R.drawable.liquid_logo_icon),
                            contentDescription = "SONIX IPTV",
                            modifier = Modifier.size(if (isCompactWidth) 36.dp else 42.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            text = if (state.editingSubscriptionId != null) "تعديل قائمة التشغيل" else "SONIX IPTV",
                            style = LiquidTypography.TitleMedium.copy(
                                brush = Brush.linearGradient(
                                    listOf(colors.text, colors.accentText),
                                ),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (!isFormDialogVisible && !state.isServerSelectorExpanded && !state.errorMessage.isNullOrBlank()) {
                        LoginErrorBanner(message = state.errorMessage.orEmpty())
                    }

                    // Two completely independent equal-width, equal-height Liquid Glass cards side by side.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .testTag("login_two_cards_row"),
                        horizontalArrangement = Arrangement.spacedBy(cardsGap),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ServerSelectionSection(
                            state = state,
                            displayServers = displayServers,
                            showNormalUrlOption = showNormalUrlOption,
                            isPanelLockedEdit = isPanelLockedEdit,
                            isFormDialogVisible = isFormDialogVisible,
                            isNormalUrlOpen = isNormalUrlOpen,
                            activePanelServer = activePanelServer,
                            screenWidth = viewportWidth,
                            screenHeight = viewportHeight,
                            isCompactHeight = isCompactHeight,
                            isCompactCards = isCompactCards,
                            selectorToggleRequester = selectorToggleRequester,
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        PlaylistsSection(
                            state = state,
                            isCompactHeight = isCompactHeight,
                            isCompactCards = isCompactCards,
                            stackDeviceIdVertically = stackDeviceIdVertically,
                            viewModel = viewModel,
                            onRequestDelete = { pendingDelete = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }

            if (isFormDialogVisible) {
                ServerLoginFormDialog(
                    state = state,
                    isNormalUrlOpen = isNormalUrlOpen,
                    activePanelServer = activePanelServer,
                    isPanelLockedEdit = isPanelLockedEdit,
                    fieldInputHeight = fieldInputHeight,
                    isCompactHeight = isCompactHeight,
                    isCompactCards = isCompactCards,
                    primaryInputRequester = primaryInputRequester,
                    viewModel = viewModel,
                )
            }

            pendingDelete?.let { sub ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("حذف قائمة التشغيل", color = AppTheme.colors.text) },
                    text = {
                        Text(
                            "هل تريد حذف \"${sub.name}\" وجميع قنواته وتصنيفاته ومفضلته ودليل البرامج المخزن؟",
                            color = AppTheme.colors.textSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deletePlaylist(sub.id)
                                pendingDelete = null
                            },
                        ) {
                            Text("حذف", color = AppTheme.colors.danger)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text("إلغاء", color = AppTheme.colors.accentText)
                        }
                    },
                    containerColor = AppTheme.colors.panel,
                )
            }
        }
    }
}

@Composable
private fun ServerSelectionSection(
    state: LoginUiState,
    displayServers: List<SonixAssignedServer>,
    showNormalUrlOption: Boolean,
    isPanelLockedEdit: Boolean,
    isFormDialogVisible: Boolean,
    isNormalUrlOpen: Boolean,
    activePanelServer: SonixAssignedServer?,
    screenWidth: Dp,
    screenHeight: Dp,
    isCompactHeight: Boolean,
    isCompactCards: Boolean,
    selectorToggleRequester: FocusRequester,
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val anchorWidthDp = with(density) { anchorWidthPx.toDp() }
    val popupWidth = maxOf(anchorWidthDp, if (isCompactCards) 248.dp else 280.dp)
        .coerceAtMost((screenWidth - 20.dp).coerceAtLeast(220.dp))
    val popupMaxHeight = (screenHeight * 0.56f).coerceIn(190.dp, 360.dp)
    val positionProvider = remember(density) {
        with(density) {
            AnchoredServerDropdownPositionProvider(
                verticalGapPx = 6.dp.roundToPx(),
                horizontalMarginPx = 10.dp.roundToPx(),
                verticalMarginPx = 12.dp.roundToPx(),
            )
        }
    }

    // Card height stays strictly compact and never expands when dropdown or form dialogs open.
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag("server_selection_main_card"),
        cornerRadius = 18.dp,
        glassColor = colors.panel,
        glassAlpha = 0.50f,
        borderColor = colors.border,
        borderAlpha = 0.45f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isCompactCards) 8.dp else 14.dp,
                    vertical = if (isCompactHeight) 10.dp else 14.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(if (isCompactCards) 16.dp else 18.dp),
                )
                Text(
                    text = "اختيار السيرفر",
                    style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                        .copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = "اختر السيرفر أو افتراضي URL لإدخال بيانات الاشتراك في نافذة مستقلة.",
                style = if (isCompactCards) LiquidTypography.LabelSmall else LiquidTypography.BodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { anchorWidthPx = it.width },
            ) {
                ServerSelectorToggleButton(
                    expanded = state.isServerSelectorExpanded,
                    enabled = !state.isConnecting,
                    isCompactCards = isCompactCards,
                    selectedSummary = when {
                        isFormDialogVisible && isNormalUrlOpen -> "افتراضي URL"
                        isFormDialogVisible && activePanelServer != null -> activePanelServer.name
                        else -> null
                    },
                    focusRequester = selectorToggleRequester,
                    onClick = viewModel::toggleServerSelector,
                )

                if (state.isServerSelectorExpanded) {
                    Popup(
                        popupPositionProvider = positionProvider,
                        onDismissRequest = {
                            if (!state.isConnecting) {
                                viewModel.collapseServerSelector()
                            }
                        },
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = !state.isConnecting,
                            dismissOnClickOutside = !state.isConnecting,
                        ),
                    ) {
                        ServerSelectorDropdownOverlay(
                            state = state,
                            displayServers = displayServers,
                            showNormalUrlOption = showNormalUrlOption,
                            isPanelLockedEdit = isPanelLockedEdit,
                            popupWidth = popupWidth,
                            popupMaxHeight = popupMaxHeight,
                            isCompactCards = isCompactCards,
                            viewModel = viewModel,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private class AnchoredServerDropdownPositionProvider(
    private val verticalGapPx: Int,
    private val horizontalMarginPx: Int,
    private val verticalMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - horizontalMarginPx)
            .coerceAtLeast(horizontalMarginPx)
        val rawX = if (layoutDirection == LayoutDirection.Rtl) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val x = rawX.coerceIn(horizontalMarginPx, maxX)

        val spaceBelow = windowSize.height - anchorBounds.bottom - verticalGapPx - verticalMarginPx
        val spaceAbove = anchorBounds.top - verticalGapPx - verticalMarginPx
        val y = if (popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove) {
            (anchorBounds.bottom + verticalGapPx)
                .coerceAtMost(
                    (windowSize.height - popupContentSize.height - verticalMarginPx)
                        .coerceAtLeast(verticalMarginPx),
                )
        } else {
            (anchorBounds.top - verticalGapPx - popupContentSize.height)
                .coerceAtLeast(verticalMarginPx)
        }
        return IntOffset(x, y)
    }
}

@Composable
private fun ServerSelectorDropdownOverlay(
    state: LoginUiState,
    displayServers: List<SonixAssignedServer>,
    showNormalUrlOption: Boolean,
    isPanelLockedEdit: Boolean,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    isCompactCards: Boolean,
    viewModel: LoginViewModel,
) {
    val colors = AppTheme.colors
    val firstOptionRequester = remember { FocusRequester() }
    val popupScroll = rememberScrollState()

    LaunchedEffect(Unit) {
        runCatching { firstOptionRequester.requestFocus() }
    }

    LiquidGlassSurface(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = popupMaxHeight)
            .testTag("server_selector_list"),
        cornerRadius = 16.dp,
        glassColor = colors.panel,
        glassAlpha = 0.96f,
        borderColor = colors.accent,
        borderAlpha = 0.65f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompactCards) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "قائمة السيرفرات",
                    style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                        .copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OverlayCloseIconButton(
                    contentDescription = "إغلاق قائمة السيرفرات",
                    testTag = "close_server_selector_button",
                    enabled = !state.isConnecting,
                    onClick = viewModel::collapseServerSelector,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(popupScroll),
                verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
            ) {
                if (showNormalUrlOption) {
                    NormalUrlAccordionHeader(
                        expanded = false,
                        enabled = !state.isConnecting,
                        isCompactCards = isCompactCards,
                        focusRequester = firstOptionRequester,
                        onClick = viewModel::toggleNormalUrlSection,
                    )
                }

                if (state.sonixState.settings.allowPanelServers || isPanelLockedEdit) {
                    PanelServersAccordionSection(
                        state = state,
                        displayServers = displayServers,
                        isPanelLockedEdit = isPanelLockedEdit,
                        isCompactCards = isCompactCards,
                        firstServerFocusRequester = if (!showNormalUrlOption) firstOptionRequester else null,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerLoginFormDialog(
    state: LoginUiState,
    isNormalUrlOpen: Boolean,
    activePanelServer: SonixAssignedServer?,
    isPanelLockedEdit: Boolean,
    fieldInputHeight: Dp,
    isCompactHeight: Boolean,
    isCompactCards: Boolean,
    primaryInputRequester: FocusRequester,
    viewModel: LoginViewModel,
) {
    val colors = AppTheme.colors
    val dialogScroll = rememberScrollState()

    LaunchedEffect(isNormalUrlOpen, activePanelServer?.id, state.editingSubscriptionId) {
        runCatching { primaryInputRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = {
            if (!state.isConnecting) {
                viewModel.dismissServerFormDialog()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.isConnecting,
            dismissOnClickOutside = !state.isConnecting,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .pointerInput(state.isConnecting) {
                    detectTapGestures {
                        if (!state.isConnecting) {
                            viewModel.dismissServerFormDialog()
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogMaxWidth = if (maxWidth < 520.dp) {
                minOf(maxWidth, 430.dp)
            } else {
                minOf(maxWidth * 0.65f, 480.dp)
            }
            val dialogMaxHeight = (maxHeight - 8.dp).coerceAtLeast(240.dp).coerceAtMost(580.dp)

            LiquidGlassSurface(
                modifier = Modifier
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Consume taps inside the dialog card without merging semantics */ }
                    }
                    .testTag("server_login_form_dialog"),
                cornerRadius = 20.dp,
                glassColor = colors.panel,
                glassAlpha = 0.96f,
                borderColor = colors.accent,
                borderAlpha = 0.68f,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isCompactCards) 12.dp else 18.dp,
                            vertical = if (isCompactHeight) 12.dp else 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isNormalUrlOpen) Icons.Filled.Language else Icons.Filled.Dns,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val titleText = when {
                                state.editingSubscriptionId != null && isNormalUrlOpen -> "تعديل قائمة التشغيل"
                                state.editingSubscriptionId != null && activePanelServer != null -> "تعديل: ${activePanelServer.name}"
                                isNormalUrlOpen -> "افتراضي URL"
                                else -> activePanelServer?.name ?: "تسجيل الدخول"
                            }
                            Text(
                                text = titleText,
                                style = LiquidTypography.TitleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            activePanelServer?.region?.takeIf { it.isNotBlank() }?.let { region ->
                                Text(
                                    text = "المنطقة: $region",
                                    style = LiquidTypography.LabelSmall,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (!isPanelLockedEdit) {
                            val switchInteraction = remember { MutableInteractionSource() }
                            val switchFocused by switchInteraction.collectIsFocusedAsState()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (switchFocused) colors.selected else colors.surface.copy(alpha = 0.80f))
                                    .border(
                                        width = if (switchFocused) 2.dp else 1.dp,
                                        color = if (switchFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(50),
                                    )
                                    .clickable(
                                        interactionSource = switchInteraction,
                                        indication = null,
                                        enabled = !state.isConnecting,
                                        onClick = viewModel::switchServerFromDialog,
                                    )
                                    .focusable(interactionSource = switchInteraction)
                                    .padding(horizontal = 10.dp)
                                    .testTag("dialog_switch_server_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = colors.accentText,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "تغيير السيرفر",
                                    style = LiquidTypography.LabelSmall,
                                    color = colors.text,
                                    maxLines = 1,
                                )
                            }
                        }

                        OverlayCloseIconButton(
                            contentDescription = "إغلاق نافذة تسجيل الدخول",
                            testTag = "close_server_form_dialog_button",
                            enabled = !state.isConnecting,
                            onClick = viewModel::dismissServerFormDialog,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(dialogScroll),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 7.dp else 9.dp),
                    ) {
                        if (isNormalUrlOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("default_url_form"),
                                verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
                            ) {
                                // 4 fields in strict order: Name -> Username -> Password -> Host URL
                                CompactGlassTextField(
                                    value = state.displayName,
                                    onValueChange = viewModel::setDisplayName,
                                    label = "اسم قائمة التشغيل",
                                    placeholder = "مثال: قائمة المنزل",
                                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                    fieldModifier = Modifier
                                        .focusRequester(primaryInputRequester)
                                        .testTag("input_subscription_name"),
                                )

                                CompactGlassTextField(
                                    value = state.username,
                                    onValueChange = viewModel::setUsername,
                                    label = "اسم المستخدم",
                                    placeholder = "username",
                                    leadingIcon = Icons.Filled.Person,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                    fieldModifier = Modifier.testTag("input_xtream_username"),
                                )

                                PasswordGlassTextField(
                                    value = state.password,
                                    onValueChange = viewModel::setPassword,
                                    showPassword = state.showPassword,
                                    onToggleVisibility = viewModel::togglePasswordVisibility,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    onDone = null,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                )

                                CompactGlassTextField(
                                    value = state.serverUrl,
                                    onValueChange = viewModel::setServerUrl,
                                    label = "Host URL",
                                    placeholder = "https://example.com:8080 أو http://...",
                                    leadingIcon = Icons.Filled.Language,
                                    enabled = !state.isConnecting,
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done,
                                    onDone = viewModel::connect,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                    fieldModifier = Modifier.testTag("input_xtream_url"),
                                )

                                LoginStatusAndSubmitBlock(
                                    state = state,
                                    isCompactHeight = isCompactHeight,
                                    isCompactCards = isCompactCards,
                                    onConnect = viewModel::connect,
                                )
                            }
                        } else if (activePanelServer != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("panel_server_form_${activePanelServer.id}"),
                                verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
                            ) {
                                // Strictly 3 fields for custom servers (NEVER Host URL).
                                CompactGlassTextField(
                                    value = state.displayName,
                                    onValueChange = viewModel::setDisplayName,
                                    label = "اسم قائمة التشغيل",
                                    placeholder = "مثال: اشتراكي الرئيسي",
                                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                    fieldModifier = Modifier
                                        .focusRequester(primaryInputRequester)
                                        .testTag("input_subscription_name"),
                                )

                                CompactGlassTextField(
                                    value = state.username,
                                    onValueChange = viewModel::setUsername,
                                    label = "اسم المستخدم",
                                    placeholder = "username",
                                    leadingIcon = Icons.Filled.Person,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Next,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                    fieldModifier = Modifier.testTag("input_xtream_username"),
                                )

                                PasswordGlassTextField(
                                    value = state.password,
                                    onValueChange = viewModel::setPassword,
                                    showPassword = state.showPassword,
                                    onToggleVisibility = viewModel::togglePasswordVisibility,
                                    enabled = !state.isConnecting,
                                    imeAction = ImeAction.Done,
                                    onDone = viewModel::connect,
                                    inputHeight = fieldInputHeight,
                                    isCompactCards = isCompactCards,
                                )

                                LoginStatusAndSubmitBlock(
                                    state = state,
                                    isCompactHeight = isCompactHeight,
                                    isCompactCards = isCompactCards,
                                    onConnect = viewModel::connect,
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
private fun OverlayCloseIconButton(
    contentDescription: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (focused) colors.selected else colors.surface.copy(alpha = 0.78f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.focus else colors.border.copy(alpha = 0.50f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interaction)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = contentDescription,
            tint = colors.text,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ServerSelectorToggleButton(
    expanded: Boolean,
    enabled: Boolean,
    isCompactCards: Boolean,
    selectedSummary: String?,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (expanded) colors.selected else colors.surface.copy(alpha = 0.80f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> colors.focus
                    expanded -> colors.accent
                    else -> colors.border.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = if (isCompactCards) 8.dp else 12.dp)
            .testTag("server_selector_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = if (expanded) colors.accentText else colors.textSecondary,
                modifier = Modifier.size(if (isCompactCards) 16.dp else 18.dp),
            )
            Text(
                text = "اختيار سيرفر",
                style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                    .copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!expanded && !selectedSummary.isNullOrBlank() && !isCompactCards) {
                PlaylistBadgeChip(text = selectedSummary, color = colors.accentText, compactMode = true)
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "طي قائمة السيرفرات" else "فتح قائمة السيرفرات",
            tint = colors.accentText,
            modifier = Modifier.size(if (isCompactCards) 18.dp else 20.dp),
        )
    }
}

@Composable
private fun NormalUrlAccordionHeader(
    expanded: Boolean,
    enabled: Boolean,
    isCompactCards: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusReqModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (expanded) colors.selected.copy(alpha = 0.72f) else colors.panel.copy(alpha = 0.58f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else if (expanded) colors.accent else colors.border.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
            )
            .then(focusReqModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(
                horizontal = if (isCompactCards) 8.dp else 12.dp,
                vertical = if (isCompactCards) 8.dp else 10.dp,
            )
            .testTag("tab_normal_url"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = if (expanded) colors.accentText else colors.textSecondary,
                modifier = Modifier.size(if (isCompactCards) 16.dp else 18.dp),
            )
            Text(
                text = "افتراضي URL",
                style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                    .copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = "فتح افتراضي URL",
            tint = colors.accentText,
            modifier = Modifier.size(if (isCompactCards) 18.dp else 20.dp),
        )
    }
}

@Composable
private fun PanelServersAccordionSection(
    state: LoginUiState,
    displayServers: List<SonixAssignedServer>,
    isPanelLockedEdit: Boolean,
    isCompactCards: Boolean,
    firstServerFocusRequester: FocusRequester?,
    viewModel: LoginViewModel,
) {
    val colors = AppTheme.colors
    val sonix = state.sonixState
    val isRefreshing = sonix.isSyncing || sonix.isCatalogLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_servers_container"),
        verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isCompactCards) "السيرفرات المتاحة" else "السيرفرات المخصصة",
                style = if (isCompactCards) LiquidTypography.LabelMedium else LiquidTypography.LabelLarge,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            val refreshInteraction = remember { MutableInteractionSource() }
            val refreshFocused by refreshInteraction.collectIsFocusedAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (refreshFocused) colors.selected else colors.panel.copy(alpha = 0.78f))
                    .border(
                        width = if (refreshFocused) 2.dp else 1.dp,
                        color = if (refreshFocused) colors.focus else colors.border.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable(
                        interactionSource = refreshInteraction,
                        indication = null,
                        enabled = !isRefreshing && !state.isConnecting,
                        onClick = viewModel::refreshPanelServers,
                    )
                    .focusable(interactionSource = refreshInteraction)
                    .padding(horizontal = if (isCompactCards) 8.dp else 10.dp, vertical = 5.dp)
                    .testTag("refresh_panel_servers_button"),
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(13.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "تحديث السيرفرات",
                        tint = colors.accentText,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = if (isRefreshing) "جارٍ التحديث..." else "تحديث",
                    style = LiquidTypography.LabelSmall,
                    color = colors.text,
                    maxLines = 1,
                )
            }
        }

        when {
            sonix.isBlockedOrRevoked -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.danger,
                    glassAlpha = 0.22f,
                    borderColor = colors.danger,
                    modifier = Modifier.testTag("panel_servers_blocked"),
                ) {
                    Text(
                        text = "تم إيقاف هذا الجهاز حالياً. لا يمكن استخدام السيرفرات المخصصة.",
                        style = LiquidTypography.BodySmall,
                        color = colors.text,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            sonix.isPanelExpired -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.warning,
                    glassAlpha = 0.22f,
                    borderColor = colors.warning,
                    modifier = Modifier.testTag("panel_servers_expired"),
                ) {
                    Text(
                        text = "انتهت صلاحية اشتراك الجهاز (${sonix.deviceCode ?: ""}). يمكنك استخدام افتراضي URL.",
                        style = LiquidTypography.BodySmall,
                        color = colors.text,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            sonix.isCatalogLoading && displayServers.isEmpty() -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.surface,
                    glassAlpha = 0.68f,
                    borderColor = colors.border,
                    modifier = Modifier.testTag("panel_servers_loading"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "جارٍ تحميل السيرفرات المتاحة...",
                            style = LiquidTypography.BodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            !sonix.catalogError.isNullOrBlank() -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.warning,
                    glassAlpha = 0.20f,
                    borderColor = colors.warning,
                    modifier = Modifier.testTag("panel_servers_error"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = sonix.catalogError,
                            style = LiquidTypography.BodySmall,
                            color = colors.text,
                        )
                        Text(
                            text = "يمكنك استخدام «افتراضي URL» أعلاه أو إعادة المحاولة.",
                            style = LiquidTypography.LabelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            displayServers.isEmpty() -> {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    glassColor = colors.surface,
                    glassAlpha = 0.65f,
                    borderColor = colors.border,
                    modifier = Modifier.testTag("panel_servers_empty"),
                ) {
                    Text(
                        text = "لا توجد سيرفرات متاحة لهذا الجهاز حالياً (${sonix.deviceCode ?: "—"}).",
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }

        if (!sonix.isBlockedOrRevoked && !sonix.isPanelExpired && displayServers.isNotEmpty()) {
            displayServers.forEachIndexed { index, server ->
                val isSelectedServer = state.selectedPanelServerId == server.id && !state.isNormalUrlSelected
                val isItemEnabled = !state.isConnecting && (!isPanelLockedEdit || isSelectedServer)
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val firstServerMod = if (index == 0 && firstServerFocusRequester != null) {
                    Modifier.focusRequester(firstServerFocusRequester)
                } else {
                    Modifier
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelectedServer) colors.selected.copy(alpha = 0.72f) else colors.surface.copy(alpha = 0.78f),
                        )
                        .border(
                            width = if (focused) 2.dp else if (isSelectedServer) 1.5.dp else 1.dp,
                            color = when {
                                focused -> colors.focus
                                isSelectedServer -> colors.accent
                                else -> colors.border.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .then(firstServerMod)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = isItemEnabled,
                            onClick = { viewModel.selectPanelServer(server.id, server.name) },
                        )
                        .focusable(interactionSource = interaction)
                        .padding(
                            horizontal = if (isCompactCards) 8.dp else 12.dp,
                            vertical = if (isCompactCards) 8.dp else 10.dp,
                        )
                        .testTag("panel_server_item_${server.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (isSelectedServer) Icons.Filled.CheckCircle else Icons.Filled.Dns,
                            contentDescription = null,
                            tint = if (isSelectedServer) colors.accent else colors.accentText,
                            modifier = Modifier.size(if (isCompactCards) 16.dp else 18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                                    .copy(fontWeight = FontWeight.SemiBold),
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            server.region?.takeIf { it.isNotBlank() }?.let { region ->
                                Text(
                                    text = "المنطقة: $region",
                                    style = LiquidTypography.LabelSmall,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isSelectedServer && !isCompactCards) {
                            PlaylistBadgeChip(text = "محدد", color = colors.accentText, compactMode = true)
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "فتح ${server.name}",
                            tint = colors.accentText,
                            modifier = Modifier.size(if (isCompactCards) 18.dp else 20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: (() -> Unit)?,
    inputHeight: Dp,
    isCompactCards: Boolean,
) {
    val colors = AppTheme.colors
    CompactGlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = "كلمة المرور",
        placeholder = "password",
        leadingIcon = Icons.Filled.Lock,
        enabled = enabled,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onDone = onDone,
        inputHeight = inputHeight,
        isCompactCards = isCompactCards,
        fieldModifier = Modifier.testTag("input_xtream_password"),
        trailingContent = {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier
                    .size(if (isCompactCards) 30.dp else 36.dp)
                    .testTag("toggle_password_visibility"),
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "إظهار أو إخفاء كلمة المرور",
                    tint = colors.muted,
                    modifier = Modifier.size(if (isCompactCards) 14.dp else 16.dp),
                )
            }
        },
    )
}

@Composable
private fun LoginStatusAndSubmitBlock(
    state: LoginUiState,
    isCompactHeight: Boolean,
    isCompactCards: Boolean,
    onConnect: () -> Unit,
) {
    val colors = AppTheme.colors

    AnimatedVisibility(visible = state.isConnecting) {
        LiquidGlassSurface(
            modifier = Modifier.testTag("login_progress_card"),
            cornerRadius = 12.dp,
            glassAlpha = 0.72f,
            borderAlpha = 0.45f,
            glassColor = colors.surface,
            borderColor = colors.accent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = state.statusMessage ?: "جارٍ الاتصال...",
                        style = LiquidTypography.LabelMedium,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0.05f, 1f) },
                    color = colors.accent,
                    trackColor = colors.border.copy(alpha = 0.30f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }

    AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
        LoginErrorBanner(message = state.errorMessage.orEmpty())
    }

    val connectInteraction = remember { MutableInteractionSource() }
    val connectFocused by connectInteraction.collectIsFocusedAsState()
    val connectScale by animateFloatAsState(
        targetValue = if (connectFocused) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
        label = "connect_btn_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompactHeight) 44.dp else 48.dp)
            .scale(connectScale)
            .clip(RoundedCornerShape(50))
            .background(
                if (state.isConnecting) colors.accent.copy(alpha = 0.55f) else colors.accent,
            )
            .border(
                width = if (connectFocused) 2.5.dp else 1.dp,
                color = if (connectFocused) colors.focus else colors.border.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = connectInteraction,
                indication = null,
                enabled = !state.isConnecting,
                onClick = onConnect,
            )
            .focusable(interactionSource = connectInteraction)
            .padding(horizontal = 10.dp)
            .testTag("connect_button"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                state.isConnecting -> "جارٍ الاتصال..."
                state.editingSubscriptionId != null -> "حفظ وإعادة الاتصال"
                else -> "تسجيل الدخول"
            },
            style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                .copy(fontWeight = FontWeight.Bold),
            color = colors.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoginErrorBanner(message: String) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = Modifier.testTag("login_error_banner"),
        cornerRadius = 10.dp,
        glassAlpha = 0.25f,
        borderAlpha = 0.55f,
        glassColor = colors.danger,
        borderColor = colors.danger,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "خطأ",
                tint = colors.danger,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = message,
                style = LiquidTypography.LabelMedium,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DeviceIdCompactCard(
    deviceCode: String?,
    deviceUid: String,
    stackVertically: Boolean,
    onCopy: (String) -> Unit,
) {
    val colors = AppTheme.colors
    // Show the locally generated stable installation ID immediately; replace it with the panel code after registration.
    val validCode = deviceCode?.takeIf { it.isNotBlank() } ?: deviceUid.takeIf { it.isNotBlank() }
    var copiedFeedback by remember(validCode) { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(2000L)
            copiedFeedback = false
        }
    }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_id_compact_card"),
        cornerRadius = 14.dp,
        glassColor = colors.surface,
        glassAlpha = 0.72f,
        borderColor = colors.border,
        borderAlpha = 0.55f,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (stackVertically) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = "Device ID",
                                tint = colors.accentText,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Device ID",
                                style = LiquidTypography.LabelSmall,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DeviceIdCopyButton(
                            enabled = validCode != null,
                            copiedFeedback = copiedFeedback,
                            onClick = {
                                if (validCode != null) {
                                    onCopy(validCode)
                                    copiedFeedback = true
                                }
                            },
                        )
                    }
                    Text(
                        text = validCode ?: "جارٍ تسجيل الجهاز...",
                        style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_id_value"),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Devices,
                        contentDescription = "Device ID",
                        tint = colors.accentText,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Device ID", style = LiquidTypography.LabelSmall, color = colors.muted)
                        Text(
                            text = validCode ?: "جارٍ تسجيل الجهاز...",
                            style = LiquidTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("device_id_value"),
                        )
                    }
                    DeviceIdCopyButton(
                        enabled = validCode != null,
                        copiedFeedback = copiedFeedback,
                        onClick = {
                            if (validCode != null) {
                                onCopy(validCode)
                                copiedFeedback = true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIdCopyButton(
    enabled: Boolean,
    copiedFeedback: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val copyInteraction = remember { MutableInteractionSource() }
    val copyFocused by copyInteraction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    copiedFeedback -> colors.success.copy(alpha = 0.24f)
                    copyFocused -> colors.selected
                    else -> colors.panel.copy(alpha = 0.85f)
                },
            )
            .border(
                width = if (copyFocused) 2.dp else 1.dp,
                color = when {
                    copyFocused -> colors.focus
                    copiedFeedback -> colors.success
                    else -> colors.border.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = copyInteraction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = copyInteraction)
            .padding(horizontal = 8.dp)
            .testTag("copy_device_id_button"),
    ) {
        Icon(
            imageVector = if (copiedFeedback) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = "نسخ Device ID",
            tint = if (copiedFeedback) colors.success else colors.accentText,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (copiedFeedback) "تم النسخ" else "نسخ",
            style = LiquidTypography.LabelSmall,
            color = if (copiedFeedback) colors.success else colors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaylistsSection(
    state: LoginUiState,
    isCompactHeight: Boolean,
    isCompactCards: Boolean,
    stackDeviceIdVertically: Boolean,
    viewModel: LoginViewModel,
    onRequestDelete: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val clipboardManager = LocalClipboardManager.current

    // Completely independent card aligned tightly to server_selection_main_card.
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag("playlists_main_card"),
        cornerRadius = 18.dp,
        glassColor = colors.panel,
        glassAlpha = 0.50f,
        borderColor = colors.border,
        borderAlpha = 0.45f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isCompactCards) 8.dp else 14.dp,
                    vertical = if (isCompactHeight) 10.dp else 14.dp,
                )
                .testTag("login_playlists_section"),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompactCards) 6.dp else 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(if (isCompactCards) 16.dp else 18.dp),
                )
                Text(
                    text = "قوائم التشغيل (${state.savedPlaylists.size})",
                    style = (if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall)
                        .copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            state.playlistMessage?.let { msg ->
                LiquidGlassSurface(
                    modifier = Modifier.testTag("playlist_feedback_banner"),
                    cornerRadius = 12.dp,
                    glassAlpha = 0.22f,
                    borderAlpha = 0.45f,
                    glassColor = colors.success,
                    borderColor = colors.success,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = msg,
                            style = LiquidTypography.LabelSmall,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearError) {
                            Text("إغلاق", style = LiquidTypography.LabelSmall, color = colors.accentText)
                        }
                    }
                }
            }

            if (state.savedPlaylists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .testTag("playlists_empty_state"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(if (isCompactCards) 24.dp else 28.dp),
                    )
                    Text(
                        text = "لا توجد قوائم تشغيل محفوظة",
                        style = if (isCompactCards) LiquidTypography.LabelLarge else LiquidTypography.TitleSmall,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Text(
                        text = "اضغط «اختيار سيرفر» لإضافة قائمة عبر افتراضي URL أو السيرفرات المخصصة.",
                        style = if (isCompactCards) LiquidTypography.LabelSmall else LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlists_list_container"),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactCards) 8.dp else 10.dp),
                ) {
                    state.savedPlaylists.forEach { sub ->
                        PlaylistSubscriptionCard(
                            subscription = sub,
                            deviceCode = state.sonixState.deviceCode,
                            isBusy = state.busyPlaylistId == sub.id,
                            onConnectOrActivate = { viewModel.activatePlaylist(sub.id) },
                            onSync = { viewModel.syncPlaylist(sub.id) },
                            onEdit = { viewModel.startEditingPlaylist(sub) },
                            onDelete = { onRequestDelete(sub) },
                            compactMode = isCompactCards,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Device ID remains strictly inside the playlists card (Card 2).
            DeviceIdCompactCard(
                deviceCode = state.sonixState.deviceCode,
                deviceUid = state.sonixState.deviceUid,
                stackVertically = stackDeviceIdVertically,
                onCopy = { code ->
                    clipboardManager.setText(AnnotatedString(code))
                    viewModel.notifyDeviceIdCopied(code)
                },
            )
        }
    }
}

@Composable
private fun CompactGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    inputHeight: Dp = 38.dp,
    isCompactCards: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelSmall,
            color = if (isFocused) colors.accentText else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(inputHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface.copy(alpha = if (isFocused) 0.92f else 0.72f))
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) colors.focus else colors.border,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = if (isCompactCards) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompactCards) 5.dp else 7.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(if (isCompactCards) 14.dp else 16.dp),
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = LiquidTypography.BodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = LiquidTypography.BodySmall.copy(
                        color = if (enabled) colors.text else colors.muted,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onDone?.invoke() },
                    ),
                    modifier = fieldModifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
