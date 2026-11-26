package com.liquidtv.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts and decrypts sensitive subscription credentials (Xtream passwords,
 * authenticated M3U URLs) using hardware/OS-backed Android Keystore keys.
 */
interface CredentialCrypto {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

@Singleton
class AndroidKeystoreCredentialCrypto @Inject constructor() : CredentialCrypto {

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + encrypted.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
        return PREFIX_KEYSTORE + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        if (!ciphertext.startsWith(PREFIX_KEYSTORE)) {
            return ciphertext
        }
        val payload = Base64.decode(ciphertext.removePrefix(PREFIX_KEYSTORE), Base64.NO_WRAP)
        if (payload.size < 2) throw IllegalArgumentException("Invalid encrypted payload")
        val ivLength = payload[0].toInt() and 0xFF
        if (payload.size <= 1 + ivLength) throw IllegalArgumentException("Corrupted encrypted payload")
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "liquidtv_subscription_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFIX_KEYSTORE = "ks1:"
    }
}

/**
 * Salted SHA-256 PIN hashing utility for parental controls.
 * Stored as "v1:<saltHex>:<hashHex>" so plaintext PINs are never persisted.
 */
object PinHasher {
    private val random = SecureRandom()

    fun hashPin(pin: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val saltHex = salt.toHex()
        val digest = computeDigest(salt, pin)
        return "v1:$saltHex:${digest.toHex()}"
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val parts = storedHash.split(":")
        if (parts.size != 3 || parts[0] != "v1") return false
        val salt = parts[1].fromHex() ?: return false
        val expected = parts[2].fromHex() ?: return false
        val actual = computeDigest(salt, pin)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun computeDigest(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        var result = md.digest()
        // Key stretching rounds
        repeat(10_000) {
            md.reset()
            md.update(salt)
            result = md.digest(result)
        }
        return result
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}

/**
 * Redacts credentials from URLs and messages before logging or surfacing errors.
 */
object LogSanitizer {
    private val CREDENTIAL_QUERY_REGEX =
        Regex("([?&](?:password|pass|pwd|token|auth|username|user)=)([^&\\s]+)", RegexOption.IGNORE_CASE)
    private val BASIC_AUTH_URL_REGEX =
        Regex("(https?://)([^:/@\\s]+):([^@\\s]+)@", RegexOption.IGNORE_CASE)

    fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url
            .replace(CREDENTIAL_QUERY_REGEX, "$1***")
            .replace(BASIC_AUTH_URL_REGEX, "$1***:***@")
    }
}
