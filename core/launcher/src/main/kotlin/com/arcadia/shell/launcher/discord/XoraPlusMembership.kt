package com.arcadia.shell.launcher.discord

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Public invite for the XOrA Discord used during onboarding. */
const val XORA_DISCORD_INVITE_URL = "https://discord.gg/VVR8vFKkY"

/** Guild behind [XORA_DISCORD_INVITE_URL]. */
const val XORA_DISCORD_GUILD_ID = "1539658971126694070"

/** Emergency onboarding override when Discord role lookup cannot run. */
const val XORA_PLUS_BYPASS_CODE = "0825"

enum class XoraPlusStatus {
    /** No Discord OAuth token on device yet. */
    NotLinked,
    /** Token present; REST call in flight. */
    Checking,
    /** Member of the XOrA guild with the Plus role. */
    HasPlus,
    /** In the guild, but Plus is missing. */
    InGuildNoPlus,
    /** Linked Discord account is not in the XOrA guild. */
    NotInGuild,
    /** Token / scopes / network could not complete the check. */
    CheckFailed,
}

data class XoraPlusCheckState(
    val status: XoraPlusStatus = XoraPlusStatus.NotLinked,
    val detail: String = "",
    val checking: Boolean = false,
) {
    val hasPlus: Boolean get() = status == XoraPlusStatus.HasPlus
}

/**
 * Confirms the linked Discord account holds **XOrA Plus** in the community guild.
 *
 * Uses the Social SDK OAuth token against Discord REST (`guilds` + `guilds.members.read`).
 * When those scopes are missing the check fails closed and onboarding offers the 5-tap override.
 */
@Singleton
class XoraPlusMembership @Inject constructor(
    private val tokenStore: DiscordTokenStore,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(XoraPlusCheckState())
    val state: StateFlow<XoraPlusCheckState> = _state.asStateFlow()

    suspend fun refresh() {
        mutex.withLock {
            val token = tokenStore.read()?.accessToken?.trim().orEmpty()
            if (token.isBlank()) {
                _state.value = XoraPlusCheckState(
                    status = XoraPlusStatus.NotLinked,
                    detail = "Link Discord to check XOrA Plus.",
                )
                return
            }
            _state.value = XoraPlusCheckState(
                status = XoraPlusStatus.Checking,
                detail = "Checking XOrA Plus…",
                checking = true,
            )
            val result = withContext(Dispatchers.IO) { fetchMembership(token) }
            _state.value = result
        }
    }

    private fun fetchMembership(token: String): XoraPlusCheckState {
        val member = restGet(
            url = "$API/users/@me/guilds/$XORA_DISCORD_GUILD_ID/member",
            token = token,
        )
        when (member.code) {
            401, 403 -> return XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Discord did not allow a Plus check. Tap the screen five times for an override.",
            )
            404 -> return XoraPlusCheckState(
                status = XoraPlusStatus.NotInGuild,
                detail = "Join the XOrA Discord ($XORA_DISCORD_INVITE_URL) with XOrA Plus, then link again.",
            )
            in 200..299 -> Unit
            else -> return XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Could not reach Discord (${member.code}). Tap the screen five times for an override.",
            )
        }
        val roleIds = parseMemberRoleIds(member.body)
        val rolesBody = restGet(
            url = "$API/guilds/$XORA_DISCORD_GUILD_ID/roles",
            token = token,
        )
        val named = if (rolesBody.code in 200..299) {
            parseGuildRoleNames(rolesBody.body)
        } else {
            emptyMap()
        }
        return if (hasXoraPlusRole(roleIds, named)) {
            XoraPlusCheckState(
                status = XoraPlusStatus.HasPlus,
                detail = "XOrA Plus confirmed.",
            )
        } else if (named.isEmpty() && KNOWN_PLUS_ROLE_IDS.isEmpty()) {
            XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Couldn't read the XOrA Plus role. Tap the screen five times for an override.",
            )
        } else {
            XoraPlusCheckState(
                status = XoraPlusStatus.InGuildNoPlus,
                detail = "This Discord account is in XOrA but does not have XOrA Plus.",
            )
        }
    }

    private fun restGet(url: String, token: String): RestResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "XOrA (https://github.com/aandujar98/xora)")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.i(TAG, "GET $url -> $code")
            }
            RestResponse(code, body)
        } catch (t: Throwable) {
            Log.w(TAG, "GET $url failed", t)
            RestResponse(-1, t.message.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class RestResponse(val code: Int, val body: String)

    companion object {
        private const val TAG = "XoraPlus"
        private const val API = "https://discord.com/api/v10"
        /**
         * Optional snowflakes for "XOrA Plus" if the roles list endpoint is closed.
         * Name matching is preferred when Discord returns guild roles.
         */
        val KNOWN_PLUS_ROLE_IDS: Set<String> = emptySet()
    }
}

internal fun parseMemberRoleIds(json: String): Set<String> {
    val rolesIndex = json.indexOf("\"roles\"")
    if (rolesIndex < 0) return emptySet()
    val open = json.indexOf('[', rolesIndex)
    val close = json.indexOf(']', open + 1)
    if (open < 0 || close <= open) return emptySet()
    return json.substring(open + 1, close)
        .split(',')
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() && it != "null" }
        .toSet()
}

internal fun parseGuildRoleNames(json: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val objects = Regex("""\{[^{}]*\}""").findAll(json)
    val idPattern = Regex(""""id"\s*:\s*"([^"]+)"""")
    val namePattern = Regex(""""name"\s*:\s*"((?:\\.|[^"\\])*)"""")
    for (match in objects) {
        val obj = match.value
        val id = idPattern.find(obj)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val name = namePattern.find(obj)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.trim()
            .orEmpty()
        if (id.isNotEmpty()) result[id] = name
    }
    return result
}

internal fun isXoraPlusRoleName(name: String): Boolean {
    val compact = name.lowercase()
        .replace("+", "plus")
        .filter { it.isLetterOrDigit() }
    return compact == "xoraplus" ||
        (compact.contains("xora") && compact.contains("plus"))
}

internal fun hasXoraPlusRole(
    memberRoleIds: Set<String>,
    namedRoles: Map<String, String>,
    knownIds: Set<String> = XoraPlusMembership.KNOWN_PLUS_ROLE_IDS,
): Boolean {
    if (memberRoleIds.any { it in knownIds }) return true
    return namedRoles.any { (id, name) ->
        id in memberRoleIds && isXoraPlusRoleName(name)
    }
}

fun discordAccountLinked(state: DiscordPresenceUiState): Boolean =
    state.capability == DiscordPresenceCapability.Connected ||
        !state.currentUserId.isNullOrBlank()
