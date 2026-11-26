package com.liquidtv.app.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.R
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.ParentalPinDialog
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.AppThemeCatalog
import com.liquidtv.app.ui.theme.LiquidTypography
import com.liquidtv.app.ui.theme.ThemeDefinition
import com.liquidtv.app.ui.utils.localizedQualityAr
import com.liquidtv.app.ui.utils.localizedSubscriptionStatusAr
import com.liquidtv.app.ui.utils.formatIsoDateShortAr
import com.liquidtv.app.ui.utils.localizedSonixAccessStatusAr
import com.liquidtv.app.ui.utils.localizedSubscriptionOriginAr
import com.liquidtv.app.ui.utils.localizedSubtitleLangAr
import com.liquidtv.app.ui.utils.localizedThemeAr
import com.liquidtv.app.updater.AppUpdateViewModel
import com.liquidtv.app.updater.UpdateState
import kotlinx.coroutines.launch

private val LocalSettingsCompactHeight = staticCompositionLocalOf { false }

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Subscriptions("الاشتراكات والمزامنة", Icons.Filled.Subscriptions),
    Addons("الإضافات", Icons.Filled.Extension),
    Themes("الثيمات", Icons.Filled.ColorLens),
    Playback("التشغيل والبث", Icons.Filled.PlayCircle),
    Parental("الرقابة الأبوية", Icons.Filled.Lock),
    Appearance("المظهر", Icons.Filled.Palette),
    Ambient("وضع التوقف", Icons.Filled.NightlightRound),
    Updates("التحديثات", Icons.Filled.SystemUpdate),
    About("حول التطبيق", Icons.Filled.Info),
}

private enum class PinDialogAction {
    NONE,
    SETUP_ENABLE,
    VERIFY_DISABLE,
    CHANGE_PIN,
    VERIFY_LOCK_CATEGORIES,
}

@Composable
private fun themedSwitchColors() = AppTheme.colors.let { colors ->
    SwitchDefaults.colors(
        checkedThumbColor = colors.onAccent,
        checkedTrackColor = colors.accent,
        uncheckedThumbColor = colors.muted,
        uncheckedTrackColor = colors.surface,
        uncheckedBorderColor = colors.border,
    )
}

@Composable
fun SettingsScreen(
    onManageSubscriptions: () -> Unit = {},
    onOpenAddons: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors

    var selectedSection by remember { mutableStateOf(SettingsSection.Subscriptions) }

    var pinAction by remember { mutableStateOf(PinDialogAction.NONE) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showLockedCategoriesDialog by remember { mutableStateOf(false) }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val isWide = maxWidth >= 760.dp
            val isCompactHeight = maxHeight < 520.dp
            val horizontalPad = if (isWide) if (isCompactHeight) 16.dp else 28.dp else 16.dp
            val topNavClearance = if (isCompactHeight) 60.dp else 68.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topNavClearance, start = horizontalPad, end = horizontalPad),
            ) {
                Text(
                    text = "الإعدادات",
                    style = (if (isCompactHeight) LiquidTypography.TitleLarge else LiquidTypography.HeadlineMedium).copy(
                        brush = Brush.linearGradient(
                            listOf(colors.text, colors.accentText),
                        ),
                    ),
                    modifier = Modifier.padding(vertical = if (isCompactHeight) 2.dp else 6.dp),
                )

                uiState.successMessage?.let { msg ->
                    LiquidGlassSurface(
                        cornerRadius = 12.dp,
                        glassAlpha = 0.28f,
                        glassColor = colors.success,
                        borderColor = colors.success,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, style = LiquidTypography.BodySmall, color = colors.onSuccess, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissSuccess) {
                                Text("حسناً", color = colors.onSuccess)
                            }
                        }
                    }
                }

                uiState.error?.let { err ->
                    LiquidGlassSurface(
                        cornerRadius = 12.dp,
                        glassAlpha = 0.28f,
                        glassColor = colors.danger,
                        borderColor = colors.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(err, style = LiquidTypography.BodySmall, color = colors.onDanger, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissError) {
                                Text("إغلاق", color = colors.onDanger)
                            }
                        }
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = if (isCompactHeight) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 14.dp else 20.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .width(if (isCompactHeight) 200.dp else 230.dp)
                                .fillMaxHeight()
                                .testTag("settings_sections_list"),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 8.dp),
                        ) {
                            items(SettingsSection.entries, key = { it.name }) { section ->
                                SettingsSectionTab(
                                    section = section,
                                    isSelected = selectedSection == section,
                                    compactHeight = isCompactHeight,
                                    onClick = { selectedSection = section },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        SettingsDetailPane(
                            selectedSection = selectedSection,
                            uiState = uiState,
                            updateState = updateState,
                            viewModel = viewModel,
                            updateViewModel = updateViewModel,
                            onManageSubscriptions = onManageSubscriptions,
                            onOpenAddons = onOpenAddons,
                            onRequestPinAction = { action ->
                                pinError = null
                                pinAction = action
                            },
                            onOpenLockedCategories = { showLockedCategoriesDialog = true },
                            compactHeight = isCompactHeight,
                            contentPadding = if (isCompactHeight) {
                                PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 24.dp)
                            } else {
                                PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 36.dp)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LazyRow(
                            modifier = Modifier.testTag("settings_sections_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(SettingsSection.entries, key = { it.name }) { section ->
                                SettingsSectionPill(
                                    section = section,
                                    isSelected = selectedSection == section,
                                    onClick = { selectedSection = section },
                                )
                            }
                        }

                        SettingsDetailPane(
                            selectedSection = selectedSection,
                            uiState = uiState,
                            updateState = updateState,
                            viewModel = viewModel,
                            updateViewModel = updateViewModel,
                            onManageSubscriptions = onManageSubscriptions,
                            onOpenAddons = onOpenAddons,
                            onRequestPinAction = { action ->
                                pinError = null
                                pinAction = action
                            },
                            onOpenLockedCategories = { showLockedCategoriesDialog = true },
                            compactHeight = isCompactHeight,
                            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                        )
                    }
                }
            }
        }

        if (showLockedCategoriesDialog) {
            AlertDialog(
                onDismissRequest = { showLockedCategoriesDialog = false },
                title = { Text("التصنيفات المقفلة", color = colors.text) },
                text = {
                    if (uiState.categories.isEmpty()) {
                        Text("لا توجد تصنيفات محمّلة في الاشتراك الحالي.", color = colors.textSecondary)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.categories, key = { it.id }) { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleCategoryLocked(cat.name, cat.isLocked) }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(cat.name, style = LiquidTypography.BodyMedium, color = colors.text, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = cat.isLocked,
                                        onCheckedChange = { viewModel.toggleCategoryLocked(cat.name, cat.isLocked) },
                                        colors = themedSwitchColors(),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLockedCategoriesDialog = false }) {
                        Text("تم", color = colors.accentText)
                    }
                },
                containerColor = colors.panel,
            )
        }

        ParentalPinDialog(
            visible = pinAction != PinDialogAction.NONE,
            title = when (pinAction) {
                PinDialogAction.SETUP_ENABLE -> "تعيين رمز الحماية"
                PinDialogAction.VERIFY_DISABLE -> "إيقاف الرقابة الأبوية"
                PinDialogAction.CHANGE_PIN -> "إدخال رمز حماية جديد"
                PinDialogAction.VERIFY_LOCK_CATEGORIES -> "التحقق من رمز الحماية"
                PinDialogAction.NONE -> ""
            },
            subtitle = when (pinAction) {
                PinDialogAction.SETUP_ENABLE -> "اختر رمزاً من 4 إلى 8 أرقام لحماية التصنيفات المقفلة."
                PinDialogAction.VERIFY_DISABLE -> "أدخل الرمز الحالي لإيقاف الرقابة الأبوية."
                PinDialogAction.CHANGE_PIN -> "أدخل رمزاً جديداً مكوّناً من 4 إلى 8 أرقام."
                PinDialogAction.VERIFY_LOCK_CATEGORIES -> "أدخل الرمز الحالي لإدارة التصنيفات المقفلة."
                PinDialogAction.NONE -> ""
            },
            confirmLabel = "تأكيد",
            errorMessage = pinError,
            onConfirm = { entered ->
                when (pinAction) {
                    PinDialogAction.SETUP_ENABLE -> {
                        viewModel.enableParentalWithPin(entered)
                        pinAction = PinDialogAction.NONE
                        pinError = null
                    }
                    PinDialogAction.CHANGE_PIN -> {
                        viewModel.updateParentalPin(entered)
                        pinAction = PinDialogAction.NONE
                        pinError = null
                    }
                    PinDialogAction.VERIFY_DISABLE -> {
                        scope.launch {
                            if (viewModel.verifyParentalPin(entered)) {
                                viewModel.disableParentalControl()
                                pinAction = PinDialogAction.NONE
                                pinError = null
                            } else {
                                pinError = "رمز الحماية غير صحيح"
                            }
                        }
                    }
                    PinDialogAction.VERIFY_LOCK_CATEGORIES -> {
                        scope.launch {
                            if (viewModel.verifyParentalPin(entered)) {
                                pinAction = PinDialogAction.NONE
                                pinError = null
                                showLockedCategoriesDialog = true
                            } else {
                                pinError = "رمز الحماية غير صحيح"
                            }
                        }
                    }
                    PinDialogAction.NONE -> Unit
                }
            },
            onDismiss = {
                pinAction = PinDialogAction.NONE
                pinError = null
            },
        )
    }
}

@Composable
private fun SettingsSectionTab(
    section: SettingsSection,
    isSelected: Boolean,
    compactHeight: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = modifier
            .height(if (compactHeight) 42.dp else 48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("settings_section_${section.name.lowercase()}"),
        cornerRadius = 14.dp,
        glassColor = if (isSelected) colors.selected else colors.surface,
        glassAlpha = if (isSelected) 0.18f else if (isFocused) 0.14f else 0.08f,
        borderAlpha = if (isFocused) 0.95f else if (isSelected) 0.85f else 0.45f,
        borderColor = if (isSelected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compactHeight) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = section.title,
                tint = if (isSelected) colors.accentText else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = section.title,
                style = LiquidTypography.TitleSmall,
                color = if (isSelected) colors.text else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSectionPill(
    section: SettingsSection,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LiquidGlassSurface(
        modifier = Modifier
            .height(48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .testTag("settings_section_${section.name.lowercase()}"),
        cornerRadius = 24.dp,
        glassColor = if (isSelected) colors.selected else colors.surface,
        glassAlpha = if (isSelected) 0.18f else if (isFocused) 0.14f else 0.08f,
        borderAlpha = if (isFocused) 0.95f else if (isSelected) 0.85f else 0.45f,
        borderColor = if (isSelected) colors.accent else colors.border,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = section.title,
                tint = if (isSelected) colors.accentText else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = section.title,
                style = LiquidTypography.LabelLarge,
                color = if (isSelected) colors.text else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SettingsDetailPane(
    selectedSection: SettingsSection,
    uiState: SettingsUiState,
    updateState: UpdateState,
    viewModel: SettingsViewModel,
    updateViewModel: AppUpdateViewModel,
    onManageSubscriptions: () -> Unit,
    onOpenAddons: () -> Unit,
    onRequestPinAction: (PinDialogAction) -> Unit,
    onOpenLockedCategories: () -> Unit,
    compactHeight: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 22.dp,
        glassColor = colors.panel,
        glassAlpha = 0.16f,
        borderColor = colors.border,
        borderAlpha = 0.55f,
    ) {
        key(selectedSection) {
            val listState = rememberLazyListState()
            val switchColors = themedSwitchColors()
            CompositionLocalProvider(LocalSettingsCompactHeight provides compactHeight) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useTwoThemeColumns = maxWidth >= 520.dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("settings_content_list"),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 14.dp),
                    ) {
                        when (selectedSection) {
                            SettingsSection.Subscriptions -> {
                                item {
                                    SettingHeader("قوائم التشغيل والمزامنة", "إدارة قوائم التشغيل وحالة الجهاز ودليل البرامج")
                                }
                                item {
                                    val sub = uiState.activeSubscription
                                    SettingItemTile(
                                        title = "إدارة قوائم التشغيل",
                                        subtitle = if (sub != null) {
                                            "المفعّل: ${sub.name} (${localizedSubscriptionOriginAr(sub.origin)}) • ${localizedSubscriptionStatusAr(sub.connectionStatus)}"
                                        } else {
                                            "إضافة أو تعديل أو التبديل بين حسابات IPTV"
                                        },
                                        action = {
                                            Icon(Icons.Filled.ManageAccounts, null, tint = colors.accentText)
                                        },
                                        onClick = onManageSubscriptions,
                                        testTag = "setting_manage_subscriptions",
                                    )
                                }
                                item {
                                    val sonix = uiState.sonixState
                                    val deviceIdLabel = sonix.deviceCode?.takeIf { it.isNotBlank() } ?: "غير مسجل بعد"
                                    val subLine = sonix.subscription?.let { pSub ->
                                        val end = formatIsoDateShortAr(pSub.endsAt)
                                        if (end != null) " • ينتهي: $end" else ""
                                    }.orEmpty()
                                    SettingItemTile(
                                        title = "حالة الجهاز (معرّف الجهاز: $deviceIdLabel)",
                                        subtitle = "حالة الجهاز: ${localizedSonixAccessStatusAr(sonix.accessStatus)}$subLine • اضغط للمزامنة الآن",
                                        action = {
                                            Icon(Icons.Filled.Devices, null, tint = colors.accentText)
                                        },
                                        onClick = { viewModel.syncWithControlPanel() },
                                        testTag = "setting_sonix_sync",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "تحديث الاشتراك الحالي",
                                        subtitle = if (uiState.isRefreshing) "جارٍ مزامنة القنوات والأفلام..." else "مزامنة القنوات والتصنيفات والأفلام والمسلسلات الآن",
                                        action = {
                                            if (uiState.isRefreshing) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.accent)
                                            } else {
                                                Icon(Icons.Filled.Refresh, null, tint = colors.accentText)
                                            }
                                        },
                                        onClick = { viewModel.refreshPlaylists() },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "مزامنة دليل البرامج (EPG)",
                                        subtitle = "تنزيل أحدث جدول برامج للاشتراك المفعّل",
                                        action = {
                                            Icon(Icons.Filled.Schedule, null, tint = colors.accentText)
                                        },
                                        onClick = { viewModel.refreshEpg() },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "التحديث التلقائي للقائمة",
                                        subtitle = if (uiState.autoRefreshEnabled) {
                                            "المزامنة التلقائية في الخلفية مفعّلة كل ${uiState.autoRefreshInterval} ساعة"
                                        } else {
                                            "تحديث يدوي للقائمة فقط"
                                        },
                                        action = {
                                            Switch(
                                                checked = uiState.autoRefreshEnabled,
                                                onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = { viewModel.setAutoRefreshEnabled(!uiState.autoRefreshEnabled) },
                                        testTag = "setting_auto_refresh_toggle",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "الفاصل الزمني للتحديث",
                                        subtitle = "تكرار المزامنة الدورية: كل ${uiState.autoRefreshInterval} ساعة",
                                        action = {
                                            Text("${uiState.autoRefreshInterval} س", style = LiquidTypography.LabelLarge, color = colors.accentText)
                                        },
                                        onClick = {
                                            val intervals = listOf(6, 12, 24, 48)
                                            val next = (intervals.indexOf(uiState.autoRefreshInterval) + 1) % intervals.size
                                            viewModel.setAutoRefreshInterval(intervals[next])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "مسح سجل المشاهدة والبحث",
                                        subtitle = "حذف سجل المشاهدة الأخيرة واقتراحات البحث المحفوظة",
                                        action = {
                                            Icon(Icons.Filled.Delete, null, tint = colors.danger)
                                        },
                                        onClick = { viewModel.clearCache() },
                                        testTag = "setting_clear_cache",
                                    )
                                }
                            }
                            SettingsSection.Addons -> {
                                item {
                                    SettingHeader(
                                        title = "الإضافات",
                                        subtitle = "إدارة إضافات الترجمات والمحتوى والمصادر الإضافية",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "إدارة الإضافات والترجمات",
                                        subtitle = "تصفح المتجر، التثبيت بالرابط المباشر، وتفعيل أو تعطيل المصادر",
                                        action = {
                                            Icon(Icons.Filled.Extension, null, tint = colors.accentText)
                                        },
                                        onClick = onOpenAddons,
                                        testTag = "setting_open_addons",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "الإضافات الافتراضية المدمجة",
                                        subtitle = "مصدر بيانات الأفلام والمسلسلات • مصادر الترجمات متعددة اللغات",
                                        action = {
                                            Icon(Icons.Filled.Subtitles, null, tint = colors.accentText)
                                        },
                                        onClick = onOpenAddons,
                                    )
                                }
                            }
                            SettingsSection.Themes -> {
                                item {
                                    val currentTheme = AppThemeCatalog.resolve(uiState.appTheme)
                                    SettingHeader(
                                        title = "الثيمات",
                                        subtitle = "الثيم المفعّل حالياً: ${currentTheme.arabicName} (${currentTheme.englishName}) — تتبدّل الواجهة والألوان بالكامل مباشرة",
                                    )
                                }
                                if (useTwoThemeColumns) {
                                    val themePairs = AppThemeCatalog.allThemes.chunked(2)
                                    items(themePairs, key = { row -> row.joinToString("_") { it.id } }) { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 14.dp),
                                        ) {
                                            pair.forEach { theme ->
                                                ThemeSelectionCard(
                                                    theme = theme,
                                                    isSelected = uiState.appTheme == theme.id,
                                                    compactHeight = compactHeight,
                                                    onClick = { viewModel.selectAppTheme(theme.id) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (pair.size == 1) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    items(AppThemeCatalog.allThemes, key = { it.id }) { theme ->
                                        ThemeSelectionCard(
                                            theme = theme,
                                            isSelected = uiState.appTheme == theme.id,
                                            compactHeight = compactHeight,
                                            onClick = { viewModel.selectAppTheme(theme.id) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            SettingsSection.Playback -> {
                                item {
                                    SettingHeader("التشغيل والبث", "إعدادات اختيار المسار وجودة الفيديو وإعادة الاتصال التلقائي")
                                }
                                item {
                                    SettingItemTile(
                                        title = "جودة الفيديو الافتراضية",
                                        subtitle = "الحد الأقصى المفضل للدقة: ${localizedQualityAr(uiState.defaultQuality)}",
                                        action = {
                                            Text(localizedQualityAr(uiState.defaultQuality), style = LiquidTypography.LabelLarge, color = colors.accentText)
                                        },
                                        onClick = {
                                            val qualities = listOf("Auto", "1080p", "720p", "480p", "Data Saver")
                                            val next = (qualities.indexOf(uiState.defaultQuality) + 1) % qualities.size
                                            viewModel.setDefaultQuality(qualities[next])
                                        },
                                        testTag = "setting_default_quality",
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "التشغيل التلقائي للتالي",
                                        subtitle = "تشغيل الحلقة أو القناة التالية تلقائياً عند انتهاء العرض",
                                        action = {
                                            Switch(
                                                checked = uiState.autoPlay,
                                                onCheckedChange = { viewModel.setAutoPlay(it) },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = { viewModel.setAutoPlay(!uiState.autoPlay) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "تفعيل الترجمة افتراضياً",
                                        subtitle = "اختيار مسار الترجمة المفضل تلقائياً عند توفره",
                                        action = {
                                            Switch(
                                                checked = uiState.subtitlesEnabled,
                                                onCheckedChange = { viewModel.setSubtitlesEnabled(it) },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = { viewModel.setSubtitlesEnabled(!uiState.subtitlesEnabled) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "لغة الترجمة المفضلة",
                                        subtitle = "اللغة المختارة للمسارات النصية: ${localizedSubtitleLangAr(uiState.preferredSubtitleLanguage)}",
                                        action = {
                                            Text(localizedSubtitleLangAr(uiState.preferredSubtitleLanguage), style = LiquidTypography.LabelLarge, color = colors.accentText)
                                        },
                                        onClick = {
                                            val langs = listOf("ar", "en", "fr", "es", "de", "tr")
                                            val next = (langs.indexOf(uiState.preferredSubtitleLanguage) + 1) % langs.size
                                            viewModel.setPreferredSubtitleLanguage(langs[next])
                                        },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "مهلة الاتصال بالخادم",
                                        subtitle = "مدة الانتظار القصوى لاتصالات الشبكة والبث: ${uiState.connectionTimeout} ثانية",
                                        action = {
                                            Text("${uiState.connectionTimeout} ث", style = LiquidTypography.LabelLarge, color = colors.accentText)
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
                                        title = "محاولات إعادة الاتصال",
                                        subtitle = "عدد المحاولات التلقائية عند انقطاع البث المؤقت: ${uiState.maxRetries}",
                                        action = {
                                            Text("${uiState.maxRetries}", style = LiquidTypography.LabelLarge, color = colors.accentText)
                                        },
                                        onClick = {
                                            val retries = listOf(0, 1, 2, 3, 5)
                                            val nextIndex = (retries.indexOf(uiState.maxRetries) + 1) % retries.size
                                            viewModel.setMaxRetries(retries[nextIndex])
                                        },
                                        testTag = "setting_max_retries",
                                    )
                                }
                            }
                            SettingsSection.Parental -> {
                                item {
                                    SettingHeader("الرقابة الأبوية", "حماية التصنيفات المقفلة برمز PIN خاص")
                                }
                                item {
                                    SettingItemTile(
                                        title = "تفعيل الرقابة الأبوية",
                                        subtitle = if (uiState.parentalControlEnabled && uiState.hasParentalPin) {
                                            "التصنيفات المحمية تتطلب التحقق برمز الحماية"
                                        } else {
                                            "قفل التصنيفات الحساسة برمز مكوّن من 4 إلى 8 أرقام"
                                        },
                                        action = {
                                            Switch(
                                                checked = uiState.parentalControlEnabled,
                                                onCheckedChange = {
                                                    onRequestPinAction(
                                                        if (!uiState.parentalControlEnabled) PinDialogAction.SETUP_ENABLE
                                                        else PinDialogAction.VERIFY_DISABLE,
                                                    )
                                                },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = {
                                            onRequestPinAction(
                                                if (!uiState.parentalControlEnabled) PinDialogAction.SETUP_ENABLE
                                                else PinDialogAction.VERIFY_DISABLE,
                                            )
                                        },
                                        testTag = "setting_parental_toggle",
                                    )
                                }
                                if (uiState.hasParentalPin) {
                                    item {
                                        SettingItemTile(
                                            title = "تغيير رمز الحماية",
                                            subtitle = "تحديث رمز PIN المستخدم لفتح التصنيفات المقفلة",
                                            action = {
                                                Icon(Icons.Filled.Key, null, tint = colors.accentText)
                                            },
                                            onClick = { onRequestPinAction(PinDialogAction.CHANGE_PIN) },
                                        )
                                    }
                                }
                                item {
                                    SettingItemTile(
                                        title = "التصنيفات المقفلة",
                                        subtitle = "${uiState.categories.count { it.isLocked }} من أصل ${uiState.categories.size} تصنيف مقفل",
                                        action = {
                                            Icon(Icons.Filled.Shield, null, tint = colors.accentText)
                                        },
                                        onClick = {
                                            if (uiState.parentalControlEnabled && uiState.hasParentalPin) {
                                                onRequestPinAction(PinDialogAction.VERIFY_LOCK_CATEGORIES)
                                            } else {
                                                onOpenLockedCategories()
                                            }
                                        },
                                        testTag = "setting_locked_categories",
                                    )
                                }
                            }
                            SettingsSection.Appearance -> {
                                item {
                                    SettingHeader("المظهر وتجربة الزجاج السائل", "تخصيص سمة الخلفية وعرض الساعة")
                                }
                                item {
                                    SettingItemTile(
                                        title = "سمة شاشة التوقف",
                                        subtitle = "النمط البصري لشاشة التوقف فقط: ${localizedThemeAr(uiState.theme)}",
                                        action = {
                                            Text(
                                                text = localizedThemeAr(uiState.theme),
                                                style = LiquidTypography.LabelLarge,
                                                color = colors.accentText,
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
                                        title = "إظهار الساعة",
                                        subtitle = "عرض الساعة في الشاشة الرئيسية ووضع التوقف",
                                        action = {
                                            Switch(
                                                checked = uiState.clockVisible,
                                                onCheckedChange = { viewModel.setClockVisible(it) },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = { viewModel.setClockVisible(!uiState.clockVisible) },
                                    )
                                }
                            }
                            SettingsSection.Ambient -> {
                                item {
                                    SettingHeader("وضع التوقف السينمائي", "شاشة انتظار أنيقة بتصميم الزجاج السائل")
                                }
                                item {
                                    SettingItemTile(
                                        title = "تفعيل شاشة التوقف",
                                        subtitle = if (uiState.ambientEnabled) "تعمل تلقائياً عند عدم الاستخدام" else "معطّلة",
                                        action = {
                                            Switch(
                                                checked = uiState.ambientEnabled,
                                                onCheckedChange = { viewModel.setAmbientEnabled(it) },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = { viewModel.setAmbientEnabled(!uiState.ambientEnabled) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "مهلة التشغيل التلقائي",
                                        subtitle = "تشغيل شاشة التوقف بعد مرور: ${uiState.screenSaverTimeout} دقيقة",
                                        action = {
                                            Text("${uiState.screenSaverTimeout} د", style = LiquidTypography.LabelLarge, color = colors.accentText)
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
                                    SettingHeader("تحديثات التطبيق", "فحص التحديثات وتنزيل أحدث الإصدارات الرسمية الموقّعة")
                                }
                                item {
                                    SettingItemTile(
                                        title = "التحقق من التحديثات",
                                        subtitle = when (updateState) {
                                            is UpdateState.Checking -> "جارٍ البحث عن تحديثات جديدة..."
                                            is UpdateState.UpToDate -> "أنت تستخدم أحدث إصدار! (v${BuildConfig.VERSION_NAME})"
                                            is UpdateState.Available -> "يتوفر تحديث جديد: v${updateState.info.versionName} • اضغط للعرض"
                                            is UpdateState.Error -> "تعذّر التحقق من التحديثات حالياً، يرجى المحاولة لاحقاً."
                                            else -> "الإصدار المثبّت: v${BuildConfig.VERSION_NAME} (بناء ${BuildConfig.VERSION_CODE})"
                                        },
                                        action = {
                                            if (updateState is UpdateState.Checking) {
                                                CircularProgressIndicator(
                                                    color = colors.accent,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(colors.accent, colors.waveTwo),
                                                            ),
                                                            RoundedCornerShape(50),
                                                        )
                                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                                ) {
                                                    Text("فحص الآن", style = LiquidTypography.LabelMedium, color = colors.onAccent)
                                                }
                                            }
                                        },
                                        onClick = { updateViewModel.checkForUpdate(uiState.githubRepo) },
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "فحص تلقائي عند التشغيل",
                                        subtitle = if (uiState.autoCheckUpdates) "يفحص التحديثات تلقائياً في الخلفية عند فتح التطبيق" else "فحص يدوي للتحديثات فقط",
                                        action = {
                                            Switch(
                                                checked = uiState.autoCheckUpdates,
                                                onCheckedChange = { viewModel.setAutoCheckUpdates(it) },
                                                colors = switchColors,
                                            )
                                        },
                                        onClick = { viewModel.setAutoCheckUpdates(!uiState.autoCheckUpdates) },
                                    )
                                }
                            }
                            SettingsSection.About -> {
                                item {
                                    SettingHeader("حول SONIX IPTV", "مصمم لأجهزة Android TV وGoogle TV والهواتف والأجهزة اللوحية")
                                }
                                item {
                                    Image(
                                        painter = painterResource(id = R.drawable.liquid_logo_horizontal),
                                        contentDescription = "SONIX IPTV",
                                        modifier = Modifier.width(220.dp).height(126.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "إصدار التطبيق",
                                        subtitle = "v${BuildConfig.VERSION_NAME} (بناء ${BuildConfig.VERSION_CODE}) • إصدار الزجاج السائل",
                                        action = {},
                                        onClick = {},
                                    )
                                }
                                item {
                                    SettingItemTile(
                                        title = "محرك التشغيل والبث",
                                        subtitle = "خدمة تشغيل وبث عالية الأداء تدعم القوائم المباشرة والأفلام والمسلسلات ودليل البرامج.",
                                        action = {
                                            Icon(Icons.Filled.LiveTv, null, tint = colors.accentText)
                                        },
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSelectionCard(
    theme: ThemeDefinition,
    isSelected: Boolean,
    compactHeight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColors = AppTheme.colors
    val p = theme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "theme_card_scale_${theme.id}",
    )

    LiquidGlassSurface(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .semantics { selected = isSelected }
            .testTag("theme_card_${theme.id}"),
        cornerRadius = 18.dp,
        glassColor = activeColors.card,
        glassAlpha = if (isSelected) 0.18f else 0.12f,
        borderColor = when {
            isFocused -> activeColors.focus
            isSelected -> activeColors.accent
            else -> activeColors.border
        },
        borderAlpha = if (isSelected || isFocused) 0.95f else 0.55f,
        isFocused = isFocused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactHeight) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 10.dp),
        ) {
            // Real Mini-UI Preview Canvas using the theme's own palette & wave colors
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactHeight) 86.dp else 104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) p.accent else p.border.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Background gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to p.backgroundTop,
                            0.5f to p.background,
                            1.0f to p.backgroundBottom,
                        ),
                    )

                    // Wave One ribbon
                    val wave1 = Path().apply {
                        moveTo(0f, h * 0.62f)
                        cubicTo(w * 0.30f, h * 0.44f, w * 0.68f, h * 0.84f, w, h * 0.54f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(wave1, color = p.waveOne.copy(alpha = 0.42f))

                    // Wave Two ribbon
                    val wave2 = Path().apply {
                        moveTo(0f, h * 0.78f)
                        cubicTo(w * 0.36f, h * 0.94f, w * 0.70f, h * 0.60f, w, h * 0.75f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(wave2, color = p.waveTwo.copy(alpha = 0.32f))

                    // Mini top nav pill
                    val navW = w * 0.54f
                    val navH = h * 0.16f
                    val navX = (w - navW) / 2f
                    val navY = h * 0.10f
                    drawRoundRect(
                        color = p.panel.copy(alpha = 0.12f),
                        topLeft = Offset(navX, navY),
                        size = Size(navW, navH),
                        cornerRadius = CornerRadius(navH / 2f),
                    )
                    drawRoundRect(
                        color = p.border,
                        topLeft = Offset(navX, navY),
                        size = Size(navW, navH),
                        cornerRadius = CornerRadius(navH / 2f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawRoundRect(
                        color = p.accent,
                        topLeft = Offset(navX + navW * 0.62f, navY + 2.dp.toPx()),
                        size = Size(navW * 0.30f, navH - 4.dp.toPx()),
                        cornerRadius = CornerRadius(navH / 2f),
                    )

                    // Mini sidebar panel (end / right in RTL visual preview)
                    val bodyTop = h * 0.34f
                    val bodyH = h * 0.56f
                    val sideW = w * 0.25f
                    val sideX = w - sideW - w * 0.06f
                    drawRoundRect(
                        color = p.panel.copy(alpha = 0.92f),
                        topLeft = Offset(sideX, bodyTop),
                        size = Size(sideW, bodyH),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                    drawRoundRect(
                        color = p.selected.copy(alpha = 0.24f),
                        topLeft = Offset(sideX + 4.dp.toPx(), bodyTop + 6.dp.toPx()),
                        size = Size(sideW - 8.dp.toPx(), bodyH * 0.28f),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )

                    // Mini content surface card
                    val cardW = w * 0.56f
                    val cardX = w * 0.06f
                    drawRoundRect(
                        color = p.card.copy(alpha = 0.14f),
                        topLeft = Offset(cardX, bodyTop),
                        size = Size(cardW, bodyH),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                    drawRoundRect(
                        color = p.border,
                        topLeft = Offset(cardX, bodyTop),
                        size = Size(cardW, bodyH),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    // Headline & body preview lines + CTA pill
                    drawRoundRect(
                        color = p.text,
                        topLeft = Offset(cardX + cardW * 0.28f, bodyTop + bodyH * 0.20f),
                        size = Size(cardW * 0.60f, 4.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    drawRoundRect(
                        color = p.textSecondary.copy(alpha = 0.8f),
                        topLeft = Offset(cardX + cardW * 0.38f, bodyTop + bodyH * 0.44f),
                        size = Size(cardW * 0.50f, 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    drawRoundRect(
                        color = p.accent,
                        topLeft = Offset(cardX + 8.dp.toPx(), bodyTop + bodyH * 0.66f),
                        size = Size(cardW * 0.34f, bodyH * 0.22f),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawCircle(
                        color = p.danger,
                        radius = 3.dp.toPx(),
                        center = Offset(cardX + 10.dp.toPx(), bodyTop + 10.dp.toPx()),
                    )
                }

                // Light / Dark badge + Default indicator inside preview corner
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(p.surface.copy(alpha = 0.88f))
                            .border(0.8.dp, p.border, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (theme.isLight) "فاتح" else "داكن",
                            style = LiquidTypography.LabelSmall,
                            color = p.text,
                        )
                    }
                    if (theme.isDefault) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(p.accent.copy(alpha = 0.90f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "افتراضي",
                                style = LiquidTypography.LabelSmall,
                                color = p.onAccent,
                            )
                        }
                    }
                }
            }

            // Title, Subtitle, Active Selection Indicator, and Swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = theme.arabicName,
                        style = LiquidTypography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                        color = activeColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = theme.englishName,
                        style = LiquidTypography.BodySmall,
                        color = activeColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isSelected) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(activeColors.accent)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("theme_active_badge_${theme.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "مفعّل",
                            tint = activeColors.onAccent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "مفعّل",
                            style = LiquidTypography.LabelSmall.copy(fontWeight = FontWeight.Bold),
                            color = activeColors.onAccent,
                        )
                    }
                }
            }

            // Palette Color Swatches (background, surface, card, accent, waveOne, waveTwo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(p.background, p.surface, p.card, p.accent, p.waveOne, p.waveTwo).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(1.dp, activeColors.border.copy(alpha = 0.8f), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingHeader(
    title: String,
    subtitle: String,
    compactHeight: Boolean = LocalSettingsCompactHeight.current,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(bottom = if (compactHeight) 2.dp else 6.dp)) {
        Text(
            text = title,
            style = if (compactHeight) LiquidTypography.TitleMedium else LiquidTypography.TitleLarge,
            color = colors.text,
        )
        Text(subtitle, style = LiquidTypography.BodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun SettingItemTile(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String? = null,
    compactHeight: Boolean = LocalSettingsCompactHeight.current,
) {
    val colors = AppTheme.colors
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
            .focusable(interactionSource = interactionSource)
            .let { m -> if (testTag != null) m.testTag(testTag) else m },
        cornerRadius = 16.dp,
        glassColor = if (isFocused) colors.selected else colors.card,
        glassAlpha = if (isFocused) 0.18f else 0.12f,
        borderAlpha = if (isFocused) 0.95f else 0.50f,
        borderColor = if (isFocused) colors.focus else colors.border,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compactHeight) 14.dp else 16.dp,
                    vertical = if (compactHeight) 11.dp else 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp),
            ) {
                Text(title, style = LiquidTypography.TitleMedium, color = colors.text)
                Spacer(Modifier.height(if (compactHeight) 2.dp else 4.dp))
                Text(subtitle, style = LiquidTypography.BodySmall, color = colors.textSecondary)
            }
            action()
        }
    }
}
