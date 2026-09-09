package com.arcadia.shell.launcher.discord

import android.util.Log
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
     * In the guild, but Plus could not be verified (no role ids, or Discord hid them).
     * Next stays locked until [HasPlus].
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
    /** Discord 429 wait, when [status] is [XoraPlusStatus.CheckFailed] from rate limiting. */
    val retryAfterMs: Long = 0L,
) {
    /** True when onboarding may advance: the Plus role was actually matched. */
    val hasPlus: Boolean get() = status == XoraPlusStatus.HasPlus

    /** True only when a role actually matched, so copy can stay honest about it. */
    val plusConfirmed: Boolean get() = status == XoraPlusStatus.HasPlus
}

/**
 * Confirms the linked Discord account holds **XOrA Plus** in the community guild.
 *
 * Uses the Social SDK OAuth token against Discord REST (`guilds` + `guilds.members.read`), which
 * returns the member's role ids. Role *names* are bot-only, so a match needs either
 * [BuildConfig.DISCORD_BOT_TOKEN] or a build-time id list.
 */
@Singleton
class XoraPlusMembership @Inject constructor(
    private val tokenStore: DiscordTokenStore,
    private val preferences: ShellPreferences,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(XoraPlusCheckState())
    val state: StateFlow<XoraPlusCheckState> = _state.asStateFlow()

    private var lastToken: String = ""
    private var lastCacheable: XoraPlusCheckState? = null
    private var lastCacheableAtMs: Long = 0L
    private var rateLimitedUntilMs: Long = 0L
    private var cachedNamedRoles: Map<String, String> = emptyMap()
    private var cachedNamedRolesAtMs: Long = 0L

    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val token = tokenStore.read()?.accessToken?.trim().orEmpty()
            if (token.isBlank()) {
                lastToken = ""
                lastCacheable = null
                _state.value = XoraPlusCheckState(
                    status = XoraPlusStatus.NotLinked,
                    detail = "Link Discord to check XOrA Plus.",
                )
                return
            }
            val now = android.os.SystemClock.elapsedRealtime()
            val cached = lastCacheable
            if (!force && shouldReusePlusCheck(token, lastToken, cached, now, lastCacheableAtMs)) {
                _state.value = cached!!
                return
            }
            if (now < rateLimitedUntilMs) {
                val wait = discordRetryWaitMs(rateLimitedUntilMs - now)
                _state.value = XoraPlusCheckState(
                    status = XoraPlusStatus.Checking,
                    detail = "Waiting for Discord…",
                    checking = true,
                    roleIds = _state.value.roleIds,
                )
                delay(wait)
            }
            _state.value = XoraPlusCheckState(
                status = XoraPlusStatus.Checking,
                detail = "Checking XOrA Plus…",
                checking = true,
                roleIds = _state.value.roleIds,
            )
            val configured = configuredPlusRoleIds()
            val result = withContext(Dispatchers.IO) { fetchMembership(token, configured) }
            lastToken = token
            if (plusCheckIsCacheable(result.status)) {
                lastCacheable = result
                lastCacheableAtMs = android.os.SystemClock.elapsedRealtime()
            }
            if (result.retryAfterMs > 0L) {
                rateLimitedUntilMs = android.os.SystemClock.elapsedRealtime() + result.retryAfterMs
            }
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
            429 -> {
                val fallback = membershipWithoutRoles(token, member.code)
                if (fallback.status != XoraPlusStatus.CheckFailed) return fallback
                return XoraPlusCheckState(
                    status = XoraPlusStatus.CheckFailed,
                    detail = XORA_PLUS_RATE_LIMITED_DETAIL,
                    retryAfterMs = discordRetryWaitMs(member.retryAfterMs),
                )
            }
            in 200..299 -> Unit
            else -> return XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = "Could not reach Discord (${member.code}). " +
                    "Wait a moment, then tap Check XOrA Plus again.",
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

        val named = fetchGuildRoleNames()
        return when {
            hasXoraPlusRole(roleIds, named, configuredRoleIds) -> XoraPlusCheckState(
                status = XoraPlusStatus.HasPlus,
                detail = "XOrA detected the XOrA Plus role.",
                roleIds = roleIds.toList(),
            )
            else -> XoraPlusCheckState(
                status = XoraPlusStatus.InGuildNoPlus,
                detail = "XOrA did not detect the XOrA Plus role on this Discord account.",
                roleIds = roleIds.toList(),
            )
        }
    }

    /**
     * `guilds.members.read` was refused, so role ids are out of reach on this token.
     * Guild membership alone does not unlock Next.
     */
    private fun membershipWithoutRoles(token: String, memberCode: Int): XoraPlusCheckState {
        val guilds = restGet("$API/users/@me/guilds", authorization = "Bearer $token")
        if (guilds.code == 429) {
            return XoraPlusCheckState(
                status = XoraPlusStatus.CheckFailed,
                detail = XORA_PLUS_RATE_LIMITED_DETAIL,
                retryAfterMs = discordRetryWaitMs(guilds.retryAfterMs),
            )
        }
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
     * Guild roles are a bot-only endpoint. A user OAuth token always 403s here and burns the
     * same rate-limit bucket as the membership check, so we never fall through to Bearer.
     */
    private fun fetchGuildRoleNames(): Map<String, String> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cachedNamedRoles.isNotEmpty() && now - cachedNamedRolesAtMs < ROLE_NAME_CACHE_MS) {
            return cachedNamedRoles
        }
        val botToken = BuildConfig.DISCORD_BOT_TOKEN.trim()
        if (botToken.isBlank()) return cachedNamedRoles
        val url = "$API/guilds/$XORA_DISCORD_GUILD_ID/roles"
        val asBot = restGet(url, authorization = "Bot $botToken")
        if (asBot.code in 200..299) {
            cachedNamedRoles = parseGuildRoleNames(asBot.body)
            cachedNamedRolesAtMs = android.os.SystemClock.elapsedRealtime()
            return cachedNamedRoles
        }
        Log.i(TAG, "Bot role lookup -> ${asBot.code}")
        return cachedNamedRoles
    }

    private fun restGet(url: String, authorization: String): RestResponse {
        var last = RestResponse(-1, "")
        repeat(DISCORD_429_MAX_ATTEMPTS) { attempt ->
            last = restGetOnce(url, authorization)
            if (last.code != 429) return last
            val wait = discordRetryWaitMs(last.retryAfterMs)
            Log.i(TAG, "GET $url -> 429, retry ${attempt + 1} in ${wait}ms")
            if (attempt < DISCORD_429_MAX_ATTEMPTS - 1) {
                runCatching { Thread.sleep(wait) }
            }
        }
        return last
    }

    private fun restGetOnce(url: String, authorization: String): RestResponse {
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
            RestResponse(
                code = code,
                body = body,
                retryAfterMs = parseDiscordRetryAfterMs(
                    code = code,
                    retryAfterHeader = connection.getHeaderField("Retry-After"),
                    body = body,
                ),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "GET $url failed", t)
            RestResponse(-1, t.message.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class RestResponse(
        val code: Int,
        val body: String,
        val retryAfterMs: Long = 0L,
    )

    private companion object {
        const val TAG = "XoraPlus"
        const val API = "https://discord.com/api/v10"
        const val ROLE_NAME_CACHE_MS = 10 * 60 * 1000L
    }
}

internal const val DISCORD_429_MAX_ATTEMPTS = 4
internal const val DISCORD_429_MIN_WAIT_MS = 400L
internal const val DISCORD_429_MAX_WAIT_MS = 5_000L
internal const val PLUS_CHECK_CACHE_MS = 2_500L

internal const val XORA_PLUS_RATE_LIMITED_DETAIL =
    "Discord is busy (rate limited). Wait a few seconds, then tap Check XOrA Plus again."

internal fun discordRetryWaitMs(retryAfterMs: Long): Long =
    retryAfterMs.coerceIn(DISCORD_429_MIN_WAIT_MS, DISCORD_429_MAX_WAIT_MS)

internal fun parseDiscordRetryAfterMs(
    code: Int,
    retryAfterHeader: String?,
    body: String,
): Long {
    if (code != 429) return 0L
    val fromJson = Regex(""""retry_after"\s*:\s*([0-9]*\.?[0-9]+)""")
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
    if (fromJson != null) return (fromJson * 1_000.0).toLong()
    val header = retryAfterHeader?.trim()?.toDoubleOrNull()
    if (header != null) return (header * 1_000.0).toLong()
    return DISCORD_429_MIN_WAIT_MS
}

internal fun plusCheckIsCacheable(status: XoraPlusStatus): Boolean = when (status) {
    XoraPlusStatus.HasPlus,
    XoraPlusStatus.InGuildNoPlus,
    XoraPlusStatus.InGuildUnverified,
    XoraPlusStatus.NotInGuild,
    -> true
    else -> false
}

internal fun shouldReusePlusCheck(
    token: String,
    previousToken: String,
    previous: XoraPlusCheckState?,
    nowMs: Long,
    previousAtMs: Long,
    cacheTtlMs: Long = PLUS_CHECK_CACHE_MS,
): Boolean {
    if (previous == null || token.isBlank() || token != previousToken) return false
    if (!plusCheckIsCacheable(previous.status)) return false
    return nowMs - previousAtMs in 0 until cacheTtlMs
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
        "XOrA did not detect the XOrA Plus role on this Discord account."
    plus.status == XoraPlusStatus.NotInGuild ->
        "This account is not in the XOrA Discord. Join $XORA_DISCORD_INVITE_URL, get XOrA Plus, " +
            "then check again."
    plus.status == XoraPlusStatus.CheckFailed ->
        plus.detail.ifBlank { XORA_PLUS_RATE_LIMITED_DETAIL }
    plus.status == XoraPlusStatus.NotLinked ->
        "Link Discord so XOrA can detect whether you have the XOrA Plus role."
    plus.detail.isNotBlank() -> plus.detail
    else -> "Link Discord so XOrA can detect whether you have the XOrA Plus role."
}

fun discordAccountLinked(state: DiscordPresenceUiState): Boolean =
    state.capability == DiscordPresenceCapability.Connected ||
        !state.currentUserId.isNullOrBlank()

/** OAuth finished (tokens or Ready) even if Social SDK Connect() is still catching up. */
fun discordOnboardingSessionReady(state: DiscordPresenceUiState): Boolean =
    discordAccountLinked(state) || state.connecting

fun discordOnboardingMayAdvance(
    bypass: Boolean,
    plus: XoraPlusCheckState,
    presence: DiscordPresenceUiState,
): Boolean = bypass || (plus.plusConfirmed && discordOnboardingSessionReady(presence))

fun discordOnboardingLinkLabel(state: DiscordPresenceUiState): String = when {
    discordAccountLinked(state) -> "Discord linked"
    state.connecting -> "Connecting Discord…"
    state.capability == DiscordPresenceCapability.NeedsDiscordApp -> "Install Discord"
    state.capability == DiscordPresenceCapability.Failed -> "Retry Discord link"
    state.capability == DiscordPresenceCapability.SdkMissing -> "Discord SDK missing"
    else -> "Link Discord"
}

fun discordOnboardingLinkEnabled(state: DiscordPresenceUiState): Boolean {
    if (state.capability == DiscordPresenceCapability.SdkMissing) return false
    if (state.connecting) return true
    return state.capability == DiscordPresenceCapability.NeedsAccountLink ||
        state.capability == DiscordPresenceCapability.NeedsDiscordApp ||
        state.capability == DiscordPresenceCapability.Failed ||
        state.capability == DiscordPresenceCapability.Connected ||
        (state.capability == DiscordPresenceCapability.NotConfigured &&
            state.applicationId.isNotBlank())
}

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
