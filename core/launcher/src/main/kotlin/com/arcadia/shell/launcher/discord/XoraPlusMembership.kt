package com.arcadia.shell.launcher.discord

import android.util.Log
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Public invite for the XOrA Discord used during onboarding. */
const val XORA_DISCORD_INVITE_URL = "https://discord.gg/CRTXSPTeK"

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
    /**
     * In the guild, but nothing on the device can name the guild's roles.
     *
     * Discord only hands role *snowflakes* to a user OAuth token — names need a bot token. Until
     * a role id is configured this is as far as the check can get, so it is treated as a pass:
     * locking every member (including the owner) out of the launcher is the worse failure.
     */
    InGuildUnverified,
    /** Linked Discord account is not in the XOrA guild. */
    NotInGuild,
    /** Token / scopes / network could not complete the check. */
    CheckFailed,
}

data class XoraPlusCheckState(
    val status: XoraPlusStatus = XoraPlusStatus.NotLinked,
    val detail: String = "",
    val checking: Boolean = false,
    /** Role snowflakes the linked account holds in the XOrA guild, for the onboarding hint. */
    val roleIds: List<String> = emptyList(),
) {
    /** True when onboarding may advance past the Discord step. */
    val hasPlus: Boolean
        get() = status == XoraPlusStatus.HasPlus || status == XoraPlusStatus.InGuildUnverified

    /** True only when a role actually matched, so copy can stay honest about it. */
    val plusConfirmed: Boolean get() = status == XoraPlusStatus.HasPlus
}

/**
 * Confirms the linked Discord account holds **XOrA Plus** in the community guild.
 *
 * Uses the Social SDK OAuth token against Discord REST (`guilds` + `guilds.members.read`), which
 * returns the member's role ids. Role *names* are bot-only, so a match needs either
 * [BuildConfig.DISCORD_BOT_TOKEN], a build-time id list, or an id pasted during onboarding.
 */
@Singleton
class XoraPlusMembership @Inject constructor(
    private val tokenStore: DiscordTokenStore,
    private val preferences: ShellPreferences,
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
                roleIds = _state.value.roleIds,
            )
            val configured = configuredPlusRoleIds()
            val result = withContext(Dispatchers.IO) { fetchMembership(token, configured) }
            _state.value = result
        }
    }

    /** Player-supplied role snowflake, used when no bot token can name the guild's roles. */
    suspend fun setPlusRoleIds(raw: String) {
        preferences.setXoraPlusRoleIds(splitRoleIds(raw).joinToString(","))
        refresh()
    }

    private suspend fun configuredPlusRoleIds(): Set<String> =
        splitRoleIds(BuildConfig.XORA_PLUS_ROLE_IDS) +
            splitRoleIds(preferences.xoraPlusRoleIds.first())

    private fun fetchMembership(token: String, configuredRoleIds: Set<String>): XoraPlusCheckState {
        val member = restGet(
            url = "$API/users/@me/guilds/$XORA_DISCORD_GUILD_ID/member",
            authorization = "Bearer $token",
        )
        when (member.code) {
            401, 403 -> return membershipWithoutRoles(token, member.code)
            404 -> return XoraPlusCheckState(
                status = XoraPlusStatus.NotInGuild,
                detail = "This account is not in the XOrA Discord. " +
                    "Join $XORA_DISCORD_INVITE_URL, get XOrA Plus, then check again.",
            )
            in 200..299 -> Unit
            else -> return XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Could not reach Discord (${member.code}). " +
                    "Link Discord again, then check XOrA Plus.",
            )
        }

        val roleIds = parseMemberRoleIds(member.body)
        if (roleIds.any { it in configuredRoleIds }) {
            return XoraPlusCheckState(
                status = XoraPlusStatus.HasPlus,
                detail = "XOrA detected the XOrA Plus role.",
                roleIds = roleIds.toList(),
            )
        }

        val named = fetchGuildRoleNames(token)
        return when {
            hasXoraPlusRole(roleIds, named, configuredRoleIds) -> XoraPlusCheckState(
                status = XoraPlusStatus.HasPlus,
                detail = "XOrA detected the XOrA Plus role.",
                roleIds = roleIds.toList(),
            )
            named.isNotEmpty() || configuredRoleIds.isNotEmpty() -> XoraPlusCheckState(
                status = XoraPlusStatus.InGuildNoPlus,
                detail = "XOrA did not detect the XOrA Plus role on this Discord account.",
                roleIds = roleIds.toList(),
            )
            else -> XoraPlusCheckState(
                status = XoraPlusStatus.InGuildUnverified,
                detail = "You're in the XOrA Discord. XOrA could not confirm the XOrA Plus role.",
                roleIds = roleIds.toList(),
            )
        }
    }

    /**
     * `guilds.members.read` was refused, so roles are out of reach on this token. The plain
     * `guilds` scope still proves the account is in the XOrA server, which is as strict as the
     * gate can get without asking the player to re-link.
     */
    private fun membershipWithoutRoles(token: String, memberCode: Int): XoraPlusCheckState {
        val guilds = restGet("$API/users/@me/guilds", authorization = "Bearer $token")
        if (guilds.code !in 200..299) {
            return XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Discord refused the membership check ($memberCode/${guilds.code}). " +
                    "Re-link Discord and accept the server-membership request.",
            )
        }
        if (!guilds.body.contains(XORA_DISCORD_GUILD_ID)) {
            return XoraPlusCheckState(
                status = XoraPlusStatus.NotInGuild,
                detail = "This account is not in the XOrA Discord. " +
                    "Join $XORA_DISCORD_INVITE_URL, get XOrA Plus, then check again.",
            )
        }
        return XoraPlusCheckState(
            status = XoraPlusStatus.InGuildUnverified,
            detail = "You're in the XOrA Discord. XOrA could not confirm the XOrA Plus role " +
                "because Discord did not share your roles ($memberCode). Re-link Discord and " +
                "accept the server-members request.",
        )
    }

    /**
     * Guild roles are a bot-only endpoint. Try a build-time bot token first, then the player's
     * bearer token in case Discord ever opens the endpoint up to OAuth.
     */
    private fun fetchGuildRoleNames(token: String): Map<String, String> {
        val url = "$API/guilds/$XORA_DISCORD_GUILD_ID/roles"
        val botToken = BuildConfig.DISCORD_BOT_TOKEN.trim()
        if (botToken.isNotBlank()) {
            val asBot = restGet(url, authorization = "Bot $botToken")
            if (asBot.code in 200..299) return parseGuildRoleNames(asBot.body)
            Log.i(TAG, "Bot role lookup -> ${asBot.code}")
        }
        val asUser = restGet(url, authorization = "Bearer $token")
        return if (asUser.code in 200..299) parseGuildRoleNames(asUser.body) else emptyMap()
    }

    private fun restGet(url: String, authorization: String): RestResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", authorization)
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

    private companion object {
        const val TAG = "XoraPlus"
        const val API = "https://discord.com/api/v10"
    }
}

/** Snowflakes only: anything that is not a run of digits is dropped. */
internal fun splitRoleIds(raw: String): Set<String> =
    raw.split(',', ' ', '\n', ';')
        .map { it.trim() }
        .filter { it.length >= 5 && it.all(Char::isDigit) }
        .toSet()

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
    knownIds: Set<String> = emptySet(),
): Boolean {
    if (memberRoleIds.any { it in knownIds }) return true
    return namedRoles.any { (id, name) ->
        id in memberRoleIds && isXoraPlusRoleName(name)
    }
}

/**
 * Onboarding Discord-step copy: whether XOrA detected the XOrA Plus role.
 *
 * The five-tap override stays a hidden emergency path; this line never asks for a role id or pin.
 */
fun xoraPlusOnboardingLine(
    bypass: Boolean,
    plus: XoraPlusCheckState,
): String = when {
    bypass -> "Access override accepted."
    plus.checking || plus.status == XoraPlusStatus.Checking -> "Checking XOrA Plus…"
    plus.status == XoraPlusStatus.HasPlus ->
        "XOrA detected the XOrA Plus role on this Discord account."
    plus.status == XoraPlusStatus.InGuildNoPlus ->
        "XOrA did not detect the XOrA Plus role on this Discord account."
    plus.status == XoraPlusStatus.InGuildUnverified ->
        "You're in the XOrA Discord. XOrA could not confirm the XOrA Plus role."
    plus.status == XoraPlusStatus.NotInGuild ->
        "This account is not in the XOrA Discord. Join $XORA_DISCORD_INVITE_URL, get XOrA Plus, " +
            "then check again."
    plus.status == XoraPlusStatus.CheckFailed ->
        plus.detail.ifBlank {
            "XOrA could not check the XOrA Plus role. Link Discord again, then check XOrA Plus."
        }
    plus.status == XoraPlusStatus.NotLinked ->
        "Link Discord so XOrA can detect whether you have the XOrA Plus role."
    plus.detail.isNotBlank() -> plus.detail
    else -> "Link Discord so XOrA can detect whether you have the XOrA Plus role."
}

fun discordAccountLinked(state: DiscordPresenceUiState): Boolean =
    state.capability == DiscordPresenceCapability.Connected ||
        !state.currentUserId.isNullOrBlank()

/** True when Advanced Settings should offer Sign out (linked, or OAuth still connecting). */
fun discordCanSignOut(state: DiscordPresenceUiState): Boolean =
    discordAccountLinked(state) || state.connecting

/** Label for the Advanced Settings Discord sign-in button. */
fun discordSettingsSignInLabel(state: DiscordPresenceUiState): String = when {
    state.connecting -> "Connecting Discord…"
    discordAccountLinked(state) -> "Re-link Discord"
    state.capability == DiscordPresenceCapability.Failed -> "Retry Discord sign-in"
    else -> "Sign in with Discord"
}
