package com.arcadia.shell.launcher.discord

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local Discord OAuth tokens for Social SDK reconnect.
 *
 * Public Application ID only elsewhere in the app — never a client secret.
 * Tokens stay in a private prefs file (not committed / not logged).
 *
 * [SCOPES_VERSION] bumps when Authorize scopes change (e.g. presence → communication)
 * so stored tokens without messaging scopes are cleared and the user re-links.
 */
@Singleton
class DiscordTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long,
    ) {
        val hasAccess: Boolean get() = accessToken.isNotBlank()
        val hasRefresh: Boolean get() = refreshToken.isNotBlank()
        val isExpired: Boolean
            get() = expiresAtEpochMs > 0L && System.currentTimeMillis() >= expiresAtEpochMs
    }

    fun read(): Tokens? {
        val version = prefs.getInt(KEY_SCOPES_VERSION, 0)
        if (version < SCOPES_VERSION) {
            // Presence-only tokens cannot SendUserMessage — force re-link.
            if (prefs.contains(KEY_ACCESS) || prefs.contains(KEY_REFRESH)) {
                clear()
            }
            return null
        }
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
            .putInt(KEY_SCOPES_VERSION, SCOPES_VERSION)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "discord_social_sdk_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at_ms"
        private const val KEY_SCOPES_VERSION = "scopes_version"
        /** 2 = communication scopes (presence + in-launcher DMs). */
        const val SCOPES_VERSION = 2
        /** Refresh a minute early so Connect does not race expiry. */
        private const val EXPIRY_SKEW_MS = 60_000L
    }
}
