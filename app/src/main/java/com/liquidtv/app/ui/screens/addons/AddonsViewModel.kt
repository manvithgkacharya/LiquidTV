package com.liquidtv.app.ui.screens.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.data.remote.StremioAddonClient
import com.liquidtv.app.domain.model.AddonSource
import com.liquidtv.app.domain.model.DefaultAddonsRegistry
import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.domain.repository.AddonPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AddonsTab(val titleAr: String) {
    INSTALLED("المثبّتة"),
    OFFICIAL("السوق الرسمي"),
    COMMUNITY("سوق المجتمع"),
}

data class AddonsUiState(
    val activeTab: AddonsTab = AddonsTab.INSTALLED,
    val searchQuery: String = "",
    val manifestUrlInput: String = "",
    val installedAddons: List<StremioAddon> = DefaultAddonsRegistry.defaultAddons,
    val officialMarket: List<StremioAddon> = emptyList(),
    val communityMarket: List<StremioAddon> = emptyList(),
    val isLoadingMarket: Boolean = false,
    val busyAddonId: String? = null,
    val isInstallingManualUrl: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val pendingConfigureUrl: String? = null,
) {
    fun isInstalled(addon: StremioAddon): Boolean =
        installedAddons.any { it.id == addon.id || it.url.equals(addon.url, ignoreCase = true) }

    fun visibleAddons(): List<StremioAddon> {
        val source = when (activeTab) {
            AddonsTab.INSTALLED -> installedAddons
            AddonsTab.OFFICIAL -> officialMarket
            AddonsTab.COMMUNITY -> communityMarket
        }
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) return source
        return source.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.id.lowercase().contains(q)
        }
    }
}

@HiltViewModel
class AddonsViewModel @Inject constructor(
    private val addonPreferencesRepository: AddonPreferencesRepository,
    private val stremioAddonClient: StremioAddonClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddonsUiState())
    val uiState: StateFlow<AddonsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            addonPreferencesRepository.installedAddons.collect { addons ->
                _uiState.update { it.copy(installedAddons = addons) }
            }
        }
        loadMarket(force = false)
    }

    fun selectTab(tab: AddonsTab) {
        _uiState.update { it.copy(activeTab = tab, errorMessage = null) }
        if (tab != AddonsTab.INSTALLED && _uiState.value.officialMarket.isEmpty() && !_uiState.value.isLoadingMarket) {
            loadMarket(force = false)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onManifestUrlChanged(url: String) {
        _uiState.update { it.copy(manifestUrlInput = url) }
    }

    fun loadMarket(force: Boolean = true) {
        if (!force && (_uiState.value.officialMarket.isNotEmpty() || _uiState.value.communityMarket.isNotEmpty())) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingMarket = true,
                    errorMessage = null,
                    statusMessage = "جارٍ تحميل الإضافات الرسمية والمجتمعية...",
                )
            }
            runCatching { stremioAddonClient.fetchMarketCatalogs() }
                .onSuccess { market ->
                    _uiState.update {
                        it.copy(
                            officialMarket = market.official,
                            communityMarket = market.community,
                            isLoadingMarket = false,
                            statusMessage = market.warningMessage
                                ?: "${market.official.size} إضافة رسمية و ${market.community.size} إضافة مجتمعية",
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoadingMarket = false,
                            errorMessage = "تعذر تحديث السوق حالياً. الإضافات المثبتة تعمل بشكل مستقل.",
                            statusMessage = null,
                        )
                    }
                }
        }
    }

    fun installFromMarket(addon: StremioAddon) {
        if (_uiState.value.isInstalled(addon)) {
            _uiState.update { it.copy(statusMessage = "الإضافة مثبّتة مسبقاً: ${addon.name}") }
            return
        }
        if (addon.configurationRequired || addon.url.contains("/[config]/")) {
            val url = addon.configureUrl.ifBlank {
                addon.url.replace("/[config]", "")
                    .replace(Regex("/manifest\\.json(?:\\?.*)?$", RegexOption.IGNORE_CASE), "")
                    .trimEnd('/') + "/configure"
            }
            _uiState.update {
                it.copy(
                    pendingConfigureUrl = url,
                    statusMessage = "تتطلب هذه الإضافة إعداداً مسبقاً؛ افتح رابط التهيئة ثم الصق الرابط الناتج.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(busyAddonId = addon.id, errorMessage = null) }
            runCatching {
                stremioAddonClient.fetchAndNormalizeManifest(
                    manifestUrl = addon.url,
                    source = addon.source,
                    fallbackAddon = addon,
                )
            }.onSuccess { verified ->
                addonPreferencesRepository.upsertAddon(verified.copy(enabled = true))
                _uiState.update {
                    it.copy(
                        busyAddonId = null,
                        statusMessage = "تم تثبيت ${verified.name} بنجاح",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        busyAddonId = null,
                        errorMessage = err.message ?: "تعذر تثبيت الإضافة",
                    )
                }
            }
        }
    }

    fun installManualManifestUrl() {
        val raw = _uiState.value.manifestUrlInput.trim()
        if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) {
            _uiState.update { it.copy(errorMessage = "أدخل رابط إضافة صالحاً يبدأ بـ https:// أو http://") }
            return
        }
        val normalizedUrl = StremioAddonClient.normalizeManifestUrl(raw)
        if (_uiState.value.installedAddons.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
            _uiState.update { it.copy(statusMessage = "الإضافة مثبّتة مسبقاً") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInstallingManualUrl = true, errorMessage = null) }
            runCatching {
                stremioAddonClient.fetchAndNormalizeManifest(
                    manifestUrl = normalizedUrl,
                    source = AddonSource.CUSTOM,
                )
            }.onSuccess { verified ->
                addonPreferencesRepository.upsertAddon(verified.copy(enabled = true))
                _uiState.update {
                    it.copy(
                        isInstallingManualUrl = false,
                        manifestUrlInput = "",
                        statusMessage = "تم تثبيت ${verified.name} عبر الرابط المباشر",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isInstallingManualUrl = false,
                        errorMessage = err.message ?: "تعذر قراءة أو تثبيت رابط الإضافة",
                    )
                }
            }
        }
    }

    fun toggleAddonEnabled(addon: StremioAddon) {
        viewModelScope.launch {
            val nextEnabled = !addon.enabled
            addonPreferencesRepository.setAddonEnabled(addon.id, nextEnabled)
            _uiState.update {
                it.copy(
                    statusMessage = if (nextEnabled) "تم تفعيل ${addon.name}" else "تم تعطيل ${addon.name}",
                )
            }
        }
    }

    fun refreshInstalledAddon(addon: StremioAddon) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyAddonId = addon.id, errorMessage = null) }
            runCatching {
                stremioAddonClient.fetchAndNormalizeManifest(
                    manifestUrl = addon.url,
                    source = addon.source,
                    fallbackAddon = addon,
                )
            }.onSuccess { updated ->
                addonPreferencesRepository.upsertAddon(updated.copy(enabled = addon.enabled))
                _uiState.update {
                    it.copy(
                        busyAddonId = null,
                        statusMessage = "تم تحديث بيانات ${updated.name} (v${updated.version})",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        busyAddonId = null,
                        errorMessage = err.message ?: "تعذر تحديث بيانات الإضافة",
                    )
                }
            }
        }
    }

    fun removeAddon(addon: StremioAddon) {
        if (DefaultAddonsRegistry.isDefaultAddon(addon.id)) {
            _uiState.update { it.copy(statusMessage = "الإضافة الأساسية يمكن تعطيلها بدلاً من حذفها.") }
            return
        }
        viewModelScope.launch {
            addonPreferencesRepository.removeAddon(addon.id)
            _uiState.update { it.copy(statusMessage = "تمت إزالة ${addon.name}") }
        }
    }

    fun consumeConfigureUrl() {
        _uiState.update { it.copy(pendingConfigureUrl = null) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
