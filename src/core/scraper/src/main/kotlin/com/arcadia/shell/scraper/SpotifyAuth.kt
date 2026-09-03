package com.arcadia.shell.scraper

import android.net.Uri
import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify Authorization Code + PKCE helpers for linking a user's account.
 *
 * Flow: [beginAuthorization] → Custom Tab → Spotify redirects to [RETURN_TO] →
 * [exchangeReturnUri] swaps the code for tokens (no client secret; public native client).
 */
@Singleton
class SpotifyAuth @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenStore: SpotifyTokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    @Volatile
    private var pendingVerifier: String? = null

    fun isConfigured(): Boolean = clientId().isNotBlank()

    fun clientId(): String = BuildConfig.SPOTIFY_CLIENT_ID.trim()

    /**
     * Builds the authorize URL and remembers the PKCE verifier for the return exchange.
     * Returns null when the Spotify Client ID is not configured.
     */
    fun beginAuthorization(): String? {
        val id = clientId()
        if (id.isBlank()) return null
        val verifier = newCodeVerifier()
        pendingVerifier = verifier
        tokenStore.savePendingVerifier(verifier)
        val challenge = codeChallengeS256(verifier)
        val params = listOf(
            "client_id" to id,
            "response_type" to "code",
            "redirect_uri" to RETURN_TO,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
            "scope" to SCOPES,
            "show_dialog" to "true",
        )
        return params.joinToString("&", prefix = "$AUTHORIZE_ENDPOINT?") { (key, value) ->
            "${Uri.encode(key)}=${Uri.encode(value)}"
        }
    }

    fun isReturnUri(uri: Uri?): Boolean = Companion.isReturnUri(uri)

    /**
     * Exchanges the auth-code return for access/refresh tokens and persists them.
     * Returns a short user-facing status message.
     */
    fun exchangeReturnUri(uri: Uri): SpotifyLinkResult {
        if (!isReturnUri(uri)) return SpotifyLinkResult.Ignored
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            clearPendingVerifier()
            return SpotifyLinkResult.Failed(error.replace('_', ' '))
        }
        val code = uri.getQueryParameter("code")?.trim().orEmpty()
        if (code.isBlank()) {
            clearPendingVerifier()
            return SpotifyLinkResult.Failed("Missing authorization code")
        }
        val verifier = pendingVerifier
            ?: tokenStore.readPendingVerifier()
            ?: run {
                clearPendingVerifier()
                return SpotifyLinkResult.Failed("Sign-in expired — try again")
            }
        val id = clientId()
        if (id.isBlank()) {
            clearPendingVerifier()
            return SpotifyLinkResult.Failed("Spotify Client ID not configured")
        }
        return runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", RETURN_TO)
                .add("client_id", id)
                .add("code_verifier", verifier)
                .build()
            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    clearPendingVerifier()
                    return SpotifyLinkResult.Failed("Spotify token exchange failed (${response.code})")
                }
                val tokens = json.decodeFromString(SpotifyTokenResponse.serializer(), raw)
                tokenStore.save(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken.orEmpty(),
                    expiresInSeconds = tokens.expiresIn,
                )
                clearPendingVerifier()
                SpotifyLinkResult.Linked
            }
        }.getOrElse { error ->
            clearPendingVerifier()
            SpotifyLinkResult.Failed(error.message ?: "Spotify link failed")
        }
    }

    /**
     * A usable access token, refreshing first when the stored one has expired.
     * Returns null when the account is not linked or the refresh was rejected.
     */
    fun accessToken(): String? {
        val tokens = tokenStore.read() ?: return null
        if (tokens.hasAccess && !tokens.isExpired) return tokens.accessToken
        val refresh = tokens.refreshToken.takeIf { it.isNotBlank() } ?: return null
        return refreshAccessToken(refresh)
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        val id = clientId().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", id)
                .build()
            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // A refused refresh means the grant is gone; make the card show unlinked.
                    if (response.code == 400 || response.code == 401) tokenStore.clear()
                    return null
                }
                val tokens = json.decodeFromString(
                    SpotifyTokenResponse.serializer(),
                    response.body.string(),
                )
                tokenStore.save(
                    accessToken = tokens.accessToken,
                    // Spotify only returns a new refresh token sometimes; keep the old one otherwise.
                    refreshToken = tokens.refreshToken?.takeIf { it.isNotBlank() } ?: refreshToken,
                    expiresInSeconds = tokens.expiresIn,
                )
                tokens.accessToken
            }
        }.getOrNull()
    }

    private fun clearPendingVerifier() {
        pendingVerifier = null
        tokenStore.clearPendingVerifier()
    }

    private fun newCodeVerifier(): String {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            .take(128)
    }

    private fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val SCHEME = "sora"
        const val HOST = "spotify-auth"
        const val RETURN_TO = "$SCHEME://$HOST"

        private const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

        /** Enough for account proof now; playback scopes ready for the media player later. */
        private const val SCOPES = "user-read-email user-read-private " +
            "user-library-read playlist-read-private " +
            "user-read-playback-state user-modify-playback-state streaming"

        fun isReturnUri(uri: Uri?): Boolean = isReturnUrl(uri?.toString())

        fun isReturnUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val scheme = url.substringBefore("://", missingDelimiterValue = "")
            if (!scheme.equals(SCHEME, ignoreCase = true)) return false
            val host = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
            return host.equals(HOST, ignoreCase = true)
        }
    }
}

sealed interface SpotifyLinkResult {
    data object Ignored : SpotifyLinkResult
    data object Linked : SpotifyLinkResult
    data class Failed(val message: String) : SpotifyLinkResult
}

@Serializable
internal data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
)
