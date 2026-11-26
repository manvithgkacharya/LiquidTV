package com.liquidtv.app.data.sonix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.liquidtv.app.data.security.CredentialCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sonixDeviceStore: DataStore<Preferences> by preferencesDataStore(name = "sonix_control_device")

@Singleton
class SonixDeviceIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: CredentialCrypto,
) {
    private object Keys {
        val DEVICE_UID = stringPreferencesKey("device_uid")
        val ENCRYPTED_DEVICE_TOKEN = stringPreferencesKey("encrypted_device_token")
        val CACHED_SNAPSHOT_JSON = stringPreferencesKey("cached_snapshot_json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getOrCreateDeviceUid(): String {
        val prefs = safePrefs()
        val existing = prefs[Keys.DEVICE_UID]?.takeIf { it.length >= 8 }
        if (existing != null) return existing
        val generated = "ltv_${UUID.randomUUID()}"
        context.sonixDeviceStore.edit { mutable ->
            val current = mutable[Keys.DEVICE_UID]
            if (current.isNullOrBlank()) {
                mutable[Keys.DEVICE_UID] = generated
            }
        }
        return safePrefs()[Keys.DEVICE_UID] ?: generated
    }

    suspend fun getDeviceToken(): String? {
        val encrypted = safePrefs()[Keys.ENCRYPTED_DEVICE_TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { crypto.decrypt(encrypted) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun saveDeviceToken(rawToken: String) {
        if (rawToken.isBlank()) return
        val encrypted = crypto.encrypt(rawToken.trim())
        context.sonixDeviceStore.edit { mutable ->
            mutable[Keys.ENCRYPTED_DEVICE_TOKEN] = encrypted
        }
    }

    suspend fun clearDeviceToken() {
        context.sonixDeviceStore.edit { mutable ->
            mutable.remove(Keys.ENCRYPTED_DEVICE_TOKEN)
        }
    }

    suspend fun loadCachedSnapshot(): SonixSyncSnapshot? {
        val raw = safePrefs()[Keys.CACHED_SNAPSHOT_JSON]?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString<SonixSyncSnapshot>(raw) }.getOrNull()
    }

    suspend fun saveCachedSnapshot(snapshot: SonixSyncSnapshot) {
        val encoded = json.encodeToString(snapshot)
        context.sonixDeviceStore.edit { mutable ->
            mutable[Keys.CACHED_SNAPSHOT_JSON] = encoded
        }
    }

    private suspend fun safePrefs(): Preferences =
        context.sonixDeviceStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
}
