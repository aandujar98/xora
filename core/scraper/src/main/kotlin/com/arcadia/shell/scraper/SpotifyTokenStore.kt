package com.arcadia.shell.scraper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local Spotify OAuth tokens for DSP account linking.
 *
 * Tokens stay in a private prefs file (not ShellPreferences / not logged).
 * [linked] is observed by the XMB so the Spotify card can show the green check.
 */
@Singleton
class SpotifyTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val linkedState = MutableStateFlow(readLinked())
    val linked: StateFlow<Boolean> = linkedState.asStateFlow()

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long,
    ) {
        val hasAccess: Boolean get() = accessToken.isNotBlank()
        val isExpired: Boolean
            get() = expiresAtEpochMs > 0L && System.currentTimeMillis() >= expiresAtEpochMs
    }

    fun isLinked(): Boolean = linkedState.value

    fun read(): Tokens? {
        val access = prefs.getString(KEY_ACCESS, null)?.trim().orEmpty()
        val refresh = prefs.getString(KEY_REFRESH, null)?.trim().orEmpty()
        if (access.isBlank() && refresh.isBlank()) return null
        return Tokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    fun save(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        val expiresAt = if (expiresInSeconds > 0) {
            System.currentTimeMillis() + expiresInSeconds * 1000L - EXPIRY_SKEW_MS
        } else {
            0L
        }
        prefs.edit()
            .putString(KEY_ACCESS, accessToken.trim())
            .putString(KEY_REFRESH, refreshToken.trim())
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        linkedState.value = true
    }

    fun clear() {
        prefs.edit().clear().apply()
        linkedState.value = false
    }

    fun savePendingVerifier(verifier: String) {
        prefs.edit().putString(KEY_PENDING_VERIFIER, verifier).apply()
    }

    fun readPendingVerifier(): String? =
        prefs.getString(KEY_PENDING_VERIFIER, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun clearPendingVerifier() {
        prefs.edit().remove(KEY_PENDING_VERIFIER).apply()
    }

    private fun readLinked(): Boolean {
        val access = prefs.getString(KEY_ACCESS, null)?.trim().orEmpty()
        val refresh = prefs.getString(KEY_REFRESH, null)?.trim().orEmpty()
        return access.isNotBlank() || refresh.isNotBlank()
    }

    companion object {
        private const val PREFS_NAME = "spotify_dsp_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at_ms"
        private const val KEY_PENDING_VERIFIER = "pending_pkce_verifier"
        private const val EXPIRY_SKEW_MS = 60_000L
    }
}
