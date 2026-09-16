package com.arcadia.shell.xoranetwork

/** Sign out locally after this long without a successful foreground session use. */
internal const val XORA_SESSION_IDLE_LIMIT_MS: Long = 7L * 24 * 60 * 60 * 1000

internal fun sessionIdleExpired(
    lastActiveEpochMs: Long,
    nowEpochMs: Long,
    limitMs: Long = XORA_SESSION_IDLE_LIMIT_MS,
): Boolean {
    if (lastActiveEpochMs <= 0L) return false
    return nowEpochMs - lastActiveEpochMs >= limitMs
}

internal fun isExpiredAuth(statusCode: Int): Boolean = statusCode == 401

/** First cookie value for [name] from `Set-Cookie` headers. Never logs the value. */
internal fun cookieValue(setCookieHeaders: List<String>, name: String): String {
    val prefix = "$name="
    for (header in setCookieHeaders) {
        val first = header.substringBefore(';').trim()
        if (first.startsWith(prefix, ignoreCase = true)) {
            return first.substring(prefix.length).trim()
        }
    }
    return ""
}
