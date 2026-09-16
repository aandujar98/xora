package com.arcadia.shell.xoranetwork

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Access + refresh tokens for the one signed-in XOrA Network identity. */
data class StoredXoraSession(
    val accessToken: String,
    val refreshToken: String,
    /** Email or username used at sign-in. Never logged. */
    val identifier: String = "",
    /** Sealed with the tokens so the launcher can refresh after Nakama's short-lived JWTs die. */
    val password: String = "",
    val lastActiveEpochMs: Long = 0L,
) {
    val canSilentReauth: Boolean get() = identifier.isNotBlank() && password.isNotEmpty()
}

/**
 * Encrypted-at-rest session storage: tokens are sealed with an AndroidKeyStore AES-GCM key before
 * touching SharedPreferences, so nothing sensitive sits in plaintext on disk (equivalent to
 * EncryptedSharedPreferences without the deprecated dependency). Tokens are never logged.
 */
@Singleton
class XoraSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private companion object {
        const val PREFS_NAME = "xora_network_session"
        const val PREF_BLOB = "session_blob"
        const val KEY_ALIAS = "xora_network_session_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): StoredXoraSession? {
        val blob = prefs.getString(PREF_BLOB, null) ?: return null
        return runCatching {
            val parts = blob.split(':')
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val payload = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            StoredXoraSession(
                accessToken = payload.optString("access"),
                refreshToken = payload.optString("refresh"),
                identifier = payload.optString("identifier"),
                password = payload.optString("password"),
                lastActiveEpochMs = payload.optLong("lastActive", 0L),
            ).takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
        }.getOrElse {
            // Key invalidated or blob corrupted — treat as signed out rather than crash-looping.
            clear()
            null
        }
    }

    fun write(session: StoredXoraSession) {
        runCatching {
            val payload = JSONObject()
                .put("access", session.accessToken)
                .put("refresh", session.refreshToken)
                .put("identifier", session.identifier)
                .put("password", session.password)
                .put("lastActive", session.lastActiveEpochMs)
                .toString()
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(PREF_BLOB, blob).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(PREF_BLOB).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }
}
