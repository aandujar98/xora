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

        /** Website-hosted avatar; may 401 without a web session — callers fall back to initials. */
        fun avatarUrlFor(username: String): String =
            "$ACCOUNT_SITE/api/avatars/${username.trim()}"
    }

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

    internal suspend fun listFriends(accessToken: String, limit: Int = 200): List<ApiFriendDto> {
        val url = "$BASE_URL/friend".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .build()
        val list: ApiFriendListDto = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build(),
        )
        return list.friends
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
