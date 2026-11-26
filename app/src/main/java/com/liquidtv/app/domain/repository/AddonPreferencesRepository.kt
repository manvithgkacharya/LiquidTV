package com.liquidtv.app.domain.repository

import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.domain.model.SubtitleStyleSettings
import kotlinx.coroutines.flow.Flow

interface AddonPreferencesRepository {
    val installedAddons: Flow<List<StremioAddon>>
    val subtitleStyle: Flow<SubtitleStyleSettings>
    val preferredSubtitleSearchLanguage: Flow<String>

    suspend fun getInstalledAddonsOnce(): List<StremioAddon>
    suspend fun getSubtitleStyleOnce(): SubtitleStyleSettings
    suspend fun getPreferredSubtitleSearchLanguageOnce(): String

    suspend fun upsertAddon(addon: StremioAddon)
    suspend fun setAddonEnabled(addonId: String, enabled: Boolean)
    suspend fun removeAddon(addonId: String)
    suspend fun replaceAddons(addons: List<StremioAddon>)

    suspend fun updateSubtitleStyle(transform: (SubtitleStyleSettings) -> SubtitleStyleSettings)
    suspend fun saveSubtitleStyle(style: SubtitleStyleSettings)
    suspend fun setPreferredSubtitleSearchLanguage(languageCode: String)
}
