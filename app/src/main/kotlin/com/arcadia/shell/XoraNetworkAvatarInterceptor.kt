package com.arcadia.shell

import coil3.intercept.Interceptor
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageResult
import com.arcadia.shell.xoranetwork.XoraNetworkAuthCookies
import java.net.URI

/**
 * account.xoranetwork.com avatar/friends APIs reject Bearer tokens. They want the Nakama JWTs as
 * `xora_at` / `xora_rt` cookies — the same pair the session store already keeps.
 */
class XoraNetworkAvatarInterceptor(
    private val cookies: XoraNetworkAuthCookies,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data as? String ?: return chain.proceed()
        val cookie = cookies.cookieHeader() ?: return chain.proceed()
        if (!isXoraAccountUrl(data)) return chain.proceed()
        val headers = NetworkHeaders.Builder(request.httpHeaders)
            .set("Cookie", cookie)
            .set("Accept", "image/gif,image/webp,image/apng,image/*,*/*;q=0.8")
            .build()
        val authed = request.newBuilder()
            .httpHeaders(headers)
            .memoryCacheKey("$data#xora-auth-anim")
            .diskCacheKey("$data#xora-auth-anim")
            .build()
        return chain.withRequest(authed).proceed()
    }

    private fun isXoraAccountUrl(data: String): Boolean {
        if (!data.startsWith("http", ignoreCase = true)) return false
        val host = runCatching { URI(data).host }.getOrNull() ?: return false
        return host.equals("account.xoranetwork.com", ignoreCase = true)
    }

}
