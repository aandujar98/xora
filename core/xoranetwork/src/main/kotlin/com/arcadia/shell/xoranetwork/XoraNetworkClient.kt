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

        /** Collection names shared with the website. Never invent new website-owned ones. */
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

    /**
     * Website sign-in — same `/api/auth/login` as account.xoranetwork.com, so username OR email
     * works. Tokens arrive as `xora_at` / `xora_rt` cookies (never logged).
     */
    internal suspend fun websiteLogin(identifier: String, password: String): ApiSessionDto {
        var csrf = fetchAnonymousCsrf()
        suspend fun post(csrfToken: String): Triple<Int, List<String>, String> = withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("identifier", identifier.trim())
                put("password", password)
                put("rememberMe", true)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$ACCOUNT_SITE/api/auth/login")
                .header("Cookie", "xora_csrf=$csrfToken")
                .header("x-csrf-token", csrfToken)
                .header("Accept", "application/json")
                .post(payload)
                .build()
            httpClient.newCall(request).execute().use { response ->
                Triple(response.code, response.headers("Set-Cookie"), response.body.string())
            }
        }
        var (code, cookies, raw) = post(csrf)
        if (code == 403) {
            csrfToken.set(null)
            csrf = fetchAnonymousCsrf()
            val retry = post(csrf)
            code = retry.first
            cookies = retry.second
            raw = retry.third
        }
        if (code !in 200..299) throw friendlyError(code, raw)
        val access = cookieValue(cookies, "xora_at")
        val refresh = cookieValue(cookies, "xora_rt")
        if (access.isBlank() || refresh.isBlank()) {
            throw XoraNetworkException("Couldn't complete XOrA Network sign-in.")
        }
        return ApiSessionDto(token = access, refreshToken = refresh, created = false)
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

    /** Website notification inbox — DMs and friend events the site writes on send. */
    internal suspend fun listWebsiteNotifications(
        accessToken: String,
        refreshToken: String,
    ): List<InboxItemDto> {
        val envelope: WebsiteNotificationsResponseDto = execute(
            Request.Builder()
                .url("$ACCOUNT_SITE/api/notifications")
                .header("Cookie", websiteCookieHeader(accessToken, refreshToken))
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        return envelope.data.items
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

    /**
     * Writes one storage object. [permissionRead] 1 lets other signed-in accounts read it
     * (friends poll our netplay invite outbox). [permissionWrite] must be 1 (owner write):
     * 0 means **no client write at all** — not even the owner — so the second write to the
     * same key is rejected with "storage write rejected - permission denied".
     *
     * Nakama's protobuf `WriteStorageObject.value` is a **string**. Passing a JSON object here
     * makes the gateway reject the body with `invalid value for string field value: {`.
     */
    internal suspend fun writeStorageObject(
        accessToken: String,
        collection: String,
        key: String,
        value: String,
        permissionRead: Int = 1,
        permissionWrite: Int = 1,
    ) {
        val body = buildStorageWriteBody(
            collection = collection,
            key = key,
            valueJson = value,
            permissionRead = permissionRead,
            permissionWrite = permissionWrite,
        )
        executeUnit(
            Request.Builder()
                .url("$BASE_URL/storage")
                .header("Authorization", "Bearer $accessToken")
                .put(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
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

    private suspend fun fetchAnonymousCsrf(): String {
        csrfToken.get()?.let { return it }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$ACCOUNT_SITE/login")
                .header("Accept", "text/html")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val value = cookieValue(response.headers("Set-Cookie"), "xora_csrf")
                if (value.isEmpty()) throw XoraNetworkException("Couldn't start XOrA Network sign-in.")
                csrfToken.set(value)
                value
            }
        }
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
            rawMessage.contains("proto:", ignoreCase = true) ||
                rawMessage.contains("invalid value for string field", ignoreCase = true) ->
                "Couldn't send that invite."
            rawMessage.contains("storage write rejected", ignoreCase = true) ||
                rawMessage.contains("permission denied", ignoreCase = true) ->
                "Couldn't send that invite. Make sure both devices run the latest XOrA build."
            rawMessage.contains("invalid", ignoreCase = true) &&
                rawMessage.length <= 120 &&
                looksLikePlainSentence(rawMessage) ->
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

/**
 * Nakama storage PUT body. [valueJson] must be a JSON **string** (the serialized object),
 * not a nested JSON object — protobuf `WriteStorageObject.value` is `string`.
 * [permissionWrite] 1 = owner write; 0 would make the object permanently client-immutable.
 */
internal fun buildStorageWriteBody(
    collection: String,
    key: String,
    valueJson: String,
    permissionRead: Int = 1,
    permissionWrite: Int = 1,
): JsonObject = buildJsonObject {
    put(
        "objects",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("collection", collection)
                    put("key", key)
                    put("value", valueJson)
                    put("permission_read", permissionRead)
                    put("permission_write", permissionWrite)
                },
            )
        },
    )
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
