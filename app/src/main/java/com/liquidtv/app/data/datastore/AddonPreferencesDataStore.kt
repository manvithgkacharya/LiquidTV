package com.liquidtv.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.liquidtv.app.domain.model.DefaultAddonsRegistry
import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.domain.model.SubtitleStyleSettings
import com.liquidtv.app.domain.repository.AddonPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.addonsDataStore: DataStore<Preferences> by preferencesDataStore(name = "liquid_tv_addons_prefs")

@Singleton
class AddonPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AddonPreferencesRepository {

    private object Keys {
        val INSTALLED_ADDONS_JSON = stringPreferencesKey("installed_addons_json")
        val SUBTITLE_STYLE_JSON = stringPreferencesKey("subtitle_style_json")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_search_lang")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val store get() = context.addonsDataStore

    private fun <T> Flow<Preferences>.safeMap(transform: (Preferences) -> T): Flow<T> =
        catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> transform(prefs) }

    override val installedAddons: Flow<List<StremioAddon>> =
        store.data.safeMap { prefs ->
            decodeAddons(prefs[Keys.INSTALLED_ADDONS_JSON])
        }

    override val subtitleStyle: Flow<SubtitleStyleSettings> =
        store.data.safeMap { prefs ->
            decodeStyle(prefs[Keys.SUBTITLE_STYLE_JSON])
        }

    override val preferredSubtitleSearchLanguage: Flow<String> =
        store.data.safeMap { prefs ->
            prefs[Keys.PREFERRED_SUBTITLE_LANG]?.takeIf { it.isNotBlank() } ?: "ar"
        }

    override suspend fun getInstalledAddonsOnce(): List<StremioAddon> =
        installedAddons.first()

    override suspend fun getSubtitleStyleOnce(): SubtitleStyleSettings =
        subtitleStyle.first()

    override suspend fun getPreferredSubtitleSearchLanguageOnce(): String =
        preferredSubtitleSearchLanguage.first()

    override suspend fun upsertAddon(addon: StremioAddon) {
        if (DefaultAddonsRegistry.isExcludedLegacyAddon(addon.id, addon.name)) return
        store.editAddons { prefs ->
            val current = decodeAddons(prefs[Keys.INSTALLED_ADDONS_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.id == addon.id || it.url == addon.url }
            if (idx >= 0) {
                current[idx] = addon
            } else {
                current.add(addon)
            }
            val normalized = DefaultAddonsRegistry.ensureDefaults(current)
            prefs[Keys.INSTALLED_ADDONS_JSON] = json.encodeToString(normalized)
        }
    }

    override suspend fun setAddonEnabled(addonId: String, enabled: Boolean) {
        store.editAddons { prefs ->
            val current = decodeAddons(prefs[Keys.INSTALLED_ADDONS_JSON]).map { addon ->
                if (addon.id == addonId) addon.copy(enabled = enabled) else addon
            }
            val normalized = DefaultAddonsRegistry.ensureDefaults(current)
            prefs[Keys.INSTALLED_ADDONS_JSON] = json.encodeToString(normalized)
        }
    }

    override suspend fun removeAddon(addonId: String) {
        if (DefaultAddonsRegistry.isDefaultAddon(addonId)) return
        store.editAddons { prefs ->
            val current = decodeAddons(prefs[Keys.INSTALLED_ADDONS_JSON])
                .filterNot { it.id == addonId }
            val normalized = DefaultAddonsRegistry.ensureDefaults(current)
            prefs[Keys.INSTALLED_ADDONS_JSON] = json.encodeToString(normalized)
        }
    }

    override suspend fun replaceAddons(addons: List<StremioAddon>) {
        store.editAddons { prefs ->
            val normalized = DefaultAddonsRegistry.ensureDefaults(addons)
            prefs[Keys.INSTALLED_ADDONS_JSON] = json.encodeToString(normalized)
        }
    }

    override suspend fun updateSubtitleStyle(transform: (SubtitleStyleSettings) -> SubtitleStyleSettings) {
        store.editAddons { prefs ->
            val current = decodeStyle(prefs[Keys.SUBTITLE_STYLE_JSON])
            val updated = transform(current).normalized()
            prefs[Keys.SUBTITLE_STYLE_JSON] = json.encodeToString(updated)
        }
    }

    override suspend fun saveSubtitleStyle(style: SubtitleStyleSettings) {
        store.editAddons { prefs ->
            prefs[Keys.SUBTITLE_STYLE_JSON] = json.encodeToString(style.normalized())
        }
    }

    override suspend fun setPreferredSubtitleSearchLanguage(languageCode: String) {
        val clean = languageCode.trim().lowercase().ifBlank { "ar" }
        store.editAddons { prefs ->
            prefs[Keys.PREFERRED_SUBTITLE_LANG] = clean
        }
    }

    private fun decodeAddons(raw: String?): List<StremioAddon> {
        if (raw.isNullOrBlank()) return DefaultAddonsRegistry.defaultAddons
        return runCatching {
            val parsed = json.decodeFromString<List<StremioAddon>>(raw)
            DefaultAddonsRegistry.ensureDefaults(parsed)
        }.getOrElse {
            DefaultAddonsRegistry.defaultAddons
        }
    }

    private fun decodeStyle(raw: String?): SubtitleStyleSettings {
        if (raw.isNullOrBlank()) return SubtitleStyleSettings()
        return runCatching {
            json.decodeFromString<SubtitleStyleSettings>(raw).normalized()
        }.getOrElse {
            SubtitleStyleSettings()
        }
    }
}

private suspend fun DataStore<Preferences>.editAddons(transform: (MutablePreferences) -> Unit) {
    this.updateData { prefs -> prefs.toMutablePreferences().also(transform) }
}
