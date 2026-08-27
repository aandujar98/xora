package com.arcadia.shell.scraper

import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Steam OpenID 2.0 helpers for resolving SteamID64 without embedding a Steam password.
 *
 * Flow: launch [authorizationUrl] in a Custom Tab → Steam redirects to [RETURN_TO] →
 * parse [steamId64FromReturnUri].
 *
 * The parsing is done on plain strings with [Uri] overloads layered on top, so the assertion
 * handling can be unit tested. `android.net.Uri` is a stub that throws in JVM tests, which is
 * exactly the wrong property for the one piece of code standing between a user and being signed in.
 */
object SteamOpenId {
    const val SCHEME = "sora"
    const val HOST = "steam-auth"
    /** Deep-link return / realm intercepted by MainActivity. */
    const val RETURN_TO = "$SCHEME://$HOST"
    const val REALM = RETURN_TO

    private const val LOGIN_ENDPOINT = "https://steamcommunity.com/openid/login"
    private const val OPENID_NS = "http://specs.openid.net/auth/2.0"
    private const val IDENTIFIER_SELECT = "http://specs.openid.net/auth/2.0/identifier_select"
    private val CLAIMED_ID_REGEX =
        Regex("""https?://steamcommunity\.com/openid/id/(\d{17})""", RegexOption.IGNORE_CASE)

    fun authorizationUrl(): String {
        val params = listOf(
            "openid.ns" to OPENID_NS,
            "openid.mode" to "checkid_setup",
            "openid.return_to" to RETURN_TO,
            "openid.realm" to REALM,
            "openid.identity" to IDENTIFIER_SELECT,
            "openid.claimed_id" to IDENTIFIER_SELECT,
        )
        return params.joinToString("&", prefix = "$LOGIN_ENDPOINT?") { (key, value) ->
            "${key.encoded()}=${value.encoded()}"
        }
    }

    fun isReturnUri(uri: Uri?): Boolean = isReturnUrl(uri?.toString())

    fun isReturnUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = url.substringBefore("://", missingDelimiterValue = "")
        if (!scheme.equals(SCHEME, ignoreCase = true)) return false
        val host = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
        return host.equals(HOST, ignoreCase = true)
    }

    /**
     * Extracts SteamID64 from an OpenID assertion return URI.
     * Prefers `openid.claimed_id`, then `openid.identity`.
     */
    fun steamId64FromReturnUri(uri: Uri): String? = steamId64FromReturnUrl(uri.toString())

    fun steamId64FromReturnUrl(url: String): String? {
        val claimed = url.queryParameter("openid.claimed_id")
            ?: url.queryParameter("openid.identity")
            ?: return null
        return CLAIMED_ID_REGEX.find(claimed)?.groupValues?.getOrNull(1)
    }

    /**
     * Steam sends the claimed id percent-encoded, so the value is decoded before matching. Split by
     * hand rather than through a Uri: this has to work in a plain JVM test.
     */
    private fun String.queryParameter(name: String): String? =
        substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstNotNullOfOrNull { pair ->
                if (pair.substringBefore('=') != name) {
                    null
                } else {
                    pair.substringAfter('=', missingDelimiterValue = "").decoded()
                }
            }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")

    private fun String.decoded(): String =
        runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
}
