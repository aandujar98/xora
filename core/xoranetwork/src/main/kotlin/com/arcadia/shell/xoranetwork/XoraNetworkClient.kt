package com.arcadia.shell.xoranetwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin client for the existing XOrA Network Nakama 3.x deployment, talking to the same
 * `api.xoranetwork.com:443` REST gateway the website uses so accounts stay shared.
 *
 * Auth is the Nakama client server key (Basic) or the user's session token (Bearer). Nothing here
 * ever logs credentials, tokens, or emails, and the recovery RPCs are intentionally absent —
 * password reset is website-only.
 */
@Singleton
class XoraNetworkClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    companion object {
        const val ACCOUNT_SITE = "https://account.xoranetwork.com"
        const val FORGOT_PASSWORD_URL = "$ACCOUNT_SITE/forgot-password"
        const val MANAGE_ACCOUNT_URL = "$ACCOUNT_SITE/login"

        private const val BASE_URL = "https://api.xoranetwork.com/v2"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Collection names shared with the website. Never invent new ones. */
        const val NOTIFICATIONS_COLLECTION = "xora_notifications"
        const val MESSAGES_COLLECTION = "xora_messages"

        val isConfigured: Boolean
            get() = BuildConfig.XORA_NETWORK_SERVER_KEY.isNotBlank()

        /** Website-hosted avatar. Requires Nakama access+refresh cookies; otherwise 401s. */
        fun avatarUrlFor(username: String): String =
            "$ACCOUNT_SITE/api/avatars/${username.trim()}"

        internal fun websiteCookieHeader(
            accessToken: String,
            refreshToken: String,
            csrf: String? = null,
        ): String = buildString {
            append("xora_at=$accessToken; xora_rt=$refreshToken")
            if (!csrf.isNullOrBlank()) append("; xora_csrf=$csrf")
        }
    }

    private val csrfToken = java.util.concurrent.atomic.AtomicReference<String?>(null)

    private fun serverKeyAuth(): String =
        Credentials.basic(BuildConfig.XORA_NETWORK_SERVER_KEY, "")

    /**
     * Email register/login against the shared account system. `create=false` on the login screen
     * so it can never mint accounts; `create=true` + username only from the register form.
     */
    internal suspend fun authenticateEmail(
        email: String,
        password: String,
        create: Boolean,
        username: String? = null,
    ): ApiSessionDto {
        val url = "$BASE_URL/account/authenticate/email".toHttpUrl().newBuilder()
            .addQueryParameter("create", create.toString())
            .apply { if (!username.isNullOrBlank()) addQueryParameter("username", username.trim()) }
            .build()
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }
        return execute(
            request = Request.Builder()
                .url(url)
                .header("Authorization", serverKeyAuth())
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    internal suspend fun refreshSession(refreshToken: String): ApiSessionDto {
        val body = buildJsonObject { put("token", refreshToken) }
        return execute(
            request = Request.Builder()
                .url("$BASE_URL/account/session/refresh")
                .header("Authorization", serverKeyAuth())
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    internal suspend fun logout(accessToken: String, refreshToken: String) {
        val body = buildJsonObject {
            put("token", accessToken)
            put("refresh_token", refreshToken)
        }
        executeUnit(
            Request.Builder()
                .url("$BASE_URL/session/logout")
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    internal suspend fun getAccount(accessToken: String): ApiAccountDto = execute(
        Request.Builder()
            .url("$BASE_URL/account")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build(),
    )

    /** Only non-null fields are sent, so untouched account values stay as-is. */
    internal suspend fun updateAccount(
        accessToken: String,
        displayName: String? = null,
        username: String? = null,
        location: String? = null,
    ) {
        val body = buildJsonObject {
            if (displayName != null) put("display_name", displayName)
            if (username != null) put("username", username)
            if (location != null) put("location", location)
        }
        executeUnit(
            Request.Builder()
                .url("$BASE_URL/account")
                .header("Authorization", "Bearer $accessToken")
                .put(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    internal suspend fun listFriends(accessToken: String, limit: Int = 100): List<ApiFriendDto> {
        val collected = ArrayList<ApiFriendDto>()
        var cursor: String? = null
        var pages = 0
        do {
            val url = "$BASE_URL/friend".toHttpUrl().newBuilder()
                .addQueryParameter("limit", limit.toString())
                .apply { if (!cursor.isNullOrBlank()) addQueryParameter("cursor", cursor) }
                .build()
            val page: ApiFriendListDto = execute(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build(),
            )
            collected += page.friends
            cursor = page.cursor.takeIf { it.isNotBlank() }
            pages++
        } while (cursor != null && pages < 20)

        if (collected.isEmpty()) {
            // Some gateways omit mixed-state rows unless `state` is set explicitly.
            for (state in 0..2) {
                val url = "$BASE_URL/friend".toHttpUrl().newBuilder()
                    .addQueryParameter("limit", limit.toString())
                    .addQueryParameter("state", state.toString())
                    .build()
                val page: ApiFriendListDto = execute(
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $accessToken")
                        .get()
                        .build(),
                )
                collected += page.friends
            }
        }
        return collected.distinctBy { it.user.username.ifBlank { it.user.id } }
    }

    /**
     * Website friends list (same Nakama graph). Auth is the `xora_at` + `xora_rt` cookies, not
     * Bearer — a Bearer token 401s even when the JWTs are valid.
     */
    internal suspend fun listWebsiteFriends(
        accessToken: String,
        refreshToken: String,
    ): WebsiteFriendsDataDto {
        val envelope: WebsiteFriendsResponseDto = execute(
            Request.Builder()
                .url("$ACCOUNT_SITE/api/friends")
                .header("Cookie", websiteCookieHeader(accessToken, refreshToken))
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        val data = envelope.data
        if (!envelope.ok && data.friends.isEmpty() && data.incoming.isEmpty() && data.outgoing.isEmpty()) {
            throw XoraNetworkException("Couldn't load friends right now.")
        }
        return data
    }

    internal suspend fun listMessageThreads(
        accessToken: String,
        refreshToken: String,
    ): List<WebsiteMessageThreadDto> {
        val envelope: WebsiteMessagesListResponseDto = execute(
            Request.Builder()
                .url("$ACCOUNT_SITE/api/messages")
                .header("Cookie", websiteCookieHeader(accessToken, refreshToken))
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        return envelope.data.threads
    }

    internal suspend fun getMessageThread(
        accessToken: String,
        refreshToken: String,
        username: String,
    ): WebsiteMessageThreadDataDto {
        val envelope: WebsiteMessageThreadResponseDto = execute(
            Request.Builder()
                .url("$ACCOUNT_SITE/api/messages/${username.trim()}")
                .header("Cookie", websiteCookieHeader(accessToken, refreshToken))
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        return envelope.data
    }

    internal suspend fun sendWebsiteMessage(
        accessToken: String,
        refreshToken: String,
        username: String,
        body: String,
    ): WebsiteMessageThreadDataDto {
        val payload = buildJsonObject { put("body", body) }.toString().toRequestBody(JSON_MEDIA_TYPE)
        suspend fun post(csrf: String): Pair<Int, String> = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$ACCOUNT_SITE/api/messages/${username.trim()}")
                .header("Cookie", websiteCookieHeader(accessToken, refreshToken, csrf))
                .header("x-csrf-token", csrf)
                .header("Accept", "application/json")
                .post(payload)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.code to response.body.string()
            }
        }
        var csrf = ensureCsrf(accessToken, refreshToken)
        var (code, raw) = post(csrf)
        if (code == 403) {
            csrfToken.set(null)
            csrf = ensureCsrf(accessToken, refreshToken)
            val retry = post(csrf)
            code = retry.first
            raw = retry.second
        }
        if (code !in 200..299) throw friendlyError(code, raw)
        val envelope = json.decodeFromString<WebsiteMessageThreadResponseDto>(raw.ifBlank { "{}" })
        if (!envelope.ok) throw XoraNetworkException("Couldn't send that message.")
        return envelope.data
    }

    /** Also accepts an incoming invite when called with that username. */
    internal suspend fun addFriends(accessToken: String, usernames: List<String>) {
        executeUnit(
            Request.Builder()
                .url(friendUrl(usernames))
                .header("Authorization", "Bearer $accessToken")
                .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    /** Removes a friend, cancels an outgoing invite, or declines an incoming one. */
    internal suspend fun deleteFriends(accessToken: String, usernames: List<String>) {
        executeUnit(
            Request.Builder()
                .url(friendUrl(usernames))
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build(),
        )
    }

    private fun friendUrl(usernames: List<String>): HttpUrl =
        "$BASE_URL/friend".toHttpUrl().newBuilder()
            .apply { usernames.forEach { addQueryParameter("usernames", it.trim()) } }
            .build()

    /**
     * Reads storage objects from the website's shared collections. [ownerIds] null entries mean
     * system-owned objects (the website writes some server-side).
     */
    internal suspend fun readStorageObjects(
        accessToken: String,
        collection: String,
        key: String,
        ownerIds: List<String?>,
    ): List<ApiStorageObjectDto> {
        val body = buildJsonObject {
            put(
                "object_ids",
                buildJsonArray {
                    ownerIds.forEach { owner ->
                        add(
                            buildJsonObject {
                                put("collection", collection)
                                put("key", key)
                                if (!owner.isNullOrBlank()) put("user_id", owner)
                            },
                        )
                    }
                },
            )
        }
        val result: ApiStorageObjectsDto = execute(
            Request.Builder()
                .url("$BASE_URL/storage")
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        return result.objects
    }

    // -------------------------------------------------------------------------------------------

    private suspend inline fun <reified T> execute(request: Request): T = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) throw friendlyError(response.code, payload)
            json.decodeFromString<T>(payload.ifBlank { "{}" })
        }
    }

    private suspend fun executeUnit(request: Request) = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw friendlyError(response.code, response.body.string())
            }
        }
    }

    internal fun clearCsrf() {
        csrfToken.set(null)
    }

    private suspend fun ensureCsrf(accessToken: String, refreshToken: String): String {
        csrfToken.get()?.let { return it }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$ACCOUNT_SITE/messages")
                .header("Cookie", websiteCookieHeader(accessToken, refreshToken))
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val header = response.headers("Set-Cookie").firstOrNull { it.contains("xora_csrf=") }
                    ?: throw XoraNetworkException("Couldn't start a message.")
                val value = header.substringAfter("xora_csrf=").substringBefore(';').trim()
                if (value.isEmpty()) throw XoraNetworkException("Couldn't start a message.")
                csrfToken.set(value)
                value
            }
        }
    }

    /** Maps gateway failures to friendly copy. Raw JSON stays here. */
    private fun friendlyError(statusCode: Int, payload: String): XoraNetworkException {
        val rawMessage = runCatching {
            json.decodeFromString<ApiErrorDto>(payload).message
        }.getOrDefault("")
        val friendly = when {
            rawMessage.contains("server key", ignoreCase = true) ->
                "This build isn't connected to XOrA Network yet."
            statusCode == 404 || rawMessage.contains("not found", ignoreCase = true) ->
                "No account matches those details."
            statusCode == 401 ->
                "XOrA Network sign-in expired or was rejected."
            statusCode == 409 || rawMessage.contains("already in use", ignoreCase = true) ->
                "That username or email is already taken."
            rawMessage.contains("invalid", ignoreCase = true) && rawMessage.length <= 120 ->
                sanitizeMessage(rawMessage)
            statusCode >= 500 ->
                "XOrA Network is having trouble right now. Try again in a bit."
            rawMessage.isNotBlank() && rawMessage.length <= 120 && looksLikePlainSentence(rawMessage) ->
                sanitizeMessage(rawMessage)
            else ->
                "XOrA Network request failed. Check your connection and try again."
        }
        return XoraNetworkException(friendly, statusCode)
    }

    private fun looksLikePlainSentence(message: String): Boolean =
        message.none { it == '{' || it == '}' || it == '[' || it == ']' || it == '"' }

    private fun sanitizeMessage(message: String): String =
        message.trim().let { if (it.endsWith('.')) it else "$it." }
}

/** Parses the payload segment of a Nakama JWT without any signature checks (client-side expiry). */
internal object XoraJwt {
    fun claims(token: String, json: Json): JsonObject? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return null
        val decoded = android.util.Base64.decode(
            payload,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as? JsonObject
    }.getOrNull()

    fun expirySeconds(token: String, json: Json): Long =
        (claims(token, json)?.get("exp") as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    /** Nakama's internal UUID — kept internal, never shown in UI. */
    fun userId(token: String, json: Json): String =
        (claims(token, json)?.get("uid") as? JsonPrimitive)?.content.orEmpty()

    fun username(token: String, json: Json): String =
        (claims(token, json)?.get("usn") as? JsonPrimitive)?.content.orEmpty()
}
