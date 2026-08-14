package com.arcadia.shell.xoranetwork

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory website session cookies derived from the Nakama access + refresh JWTs.
 *
 * `account.xoranetwork.com` does not accept `Authorization: Bearer` — it wants
 * `Cookie: xora_at=…; xora_rt=…` (verified against the live site). Coil and the
 * website REST helpers read this header; the values are never logged.
 */
@Singleton
class XoraNetworkAuthCookies @Inject constructor() {
    @Volatile
    private var header: String? = null

    fun update(session: StoredXoraSession?) {
        header = session
            ?.takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
            ?.let { "xora_at=${it.accessToken}; xora_rt=${it.refreshToken}" }
    }

    fun clear() {
        header = null
    }

    fun cookieHeader(): String? = header
}
