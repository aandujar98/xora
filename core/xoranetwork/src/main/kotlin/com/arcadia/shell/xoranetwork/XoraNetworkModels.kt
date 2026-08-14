package com.arcadia.shell.xoranetwork

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * User-facing failure from the XOrA Network gateway. [message] is always a friendly sentence —
 * raw Nakama JSON never leaves the client layer.
 */
class XoraNetworkException(message: String, val statusCode: Int = 0) : Exception(message)

/** Nakama friend edge states. The public XOrA identity is always the username, never the UUID. */
enum class XoraFriendState {
    Friend,
    OutgoingInvite,
    IncomingInvite,
    Blocked,
}

data class XoraAccount(
    /** Public XOrA Network user id. */
    val username: String,
    val displayName: String,
    val email: String,
    val location: String,
    /** Nakama avatar_url when set; may be relative (`/api/avatars/…`) or a public https URL. */
    val avatarUrl: String,
) {
    /** Absolute URL Coil can fetch (website avatars need the Nakama session cookies). */
    val resolvedAvatarUrl: String get() = resolveXoraAvatarUrl(username, avatarUrl)
}

data class XoraFriend(
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val online: Boolean,
    val state: XoraFriendState,
    /** Nakama UUID when known; never shown in UI. */
    val userId: String = "",
) {
    val resolvedAvatarUrl: String get() = resolveXoraAvatarUrl(username, avatarUrl)
}

/** One entry from the website's `xora_notifications` storage inbox. */
data class XoraNotificationItem(
    val id: String,
    /** "friend_request" | "message" (website-defined). */
    val type: String,
    val fromUsername: String,
    val fromDisplayName: String,
    val body: String,
    val createdAt: String,
    val read: Boolean,
)

/** Whole signed-in surface the launcher shows. One identity per device. */
data class XoraNetworkState(
    /** False when this build has no client server key — the UI explains instead of erroring. */
    val configured: Boolean = false,
    /** True until the stored session was restored (or found absent) at launch. */
    val restoring: Boolean = false,
    val signedIn: Boolean = false,
    val account: XoraAccount? = null,
    val friends: List<XoraFriend> = emptyList(),
    val friendsLoading: Boolean = false,
    /** Friendly copy when the friends refresh failed; null when the last load succeeded. */
    val friendsError: String? = null,
    val notifications: List<XoraNotificationItem> = emptyList(),
    /** True while the Nakama realtime socket is up — REST-only sessions always look offline. */
    val selfOnline: Boolean = false,
) {
    val acceptedFriends: List<XoraFriend>
        get() = friends.filter { it.state == XoraFriendState.Friend }
    val incomingInvites: List<XoraFriend>
        get() = friends.filter { it.state == XoraFriendState.IncomingInvite }
    val outgoingInvites: List<XoraFriend>
        get() = friends.filter { it.state == XoraFriendState.OutgoingInvite }
    val onlineFriendCount: Int
        get() = acceptedFriends.count { it.online }
    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }
}

/** Shared validation for the account fields, matching the website's rules. */
object XoraIdentityRules {
    fun passwordError(password: String): String? = when {
        password.length < 8 || password.length > 128 ->
            "Password must be 8–128 characters."
        password.none { it.isLetter() } || password.none { it.isDigit() } ->
            "Password needs at least one letter and one number."
        else -> null
    }

    fun usernameError(username: String): String? = when {
        username.length < 3 || username.length > 128 ->
            "Username must be 3–128 characters."
        username.any { it.isWhitespace() || it.isISOControl() } ->
            "Username cannot contain spaces."
        else -> null
    }

    fun displayNameError(displayName: String): String? = when {
        displayName.isEmpty() || displayName.length > 64 ->
            "Display name must be 1–64 characters."
        else -> null
    }

    fun emailError(email: String): String? = when {
        email.isBlank() || !email.contains('@') || !email.substringAfter('@').contains('.') ->
            "Enter a valid email address."
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Wire DTOs for the Nakama v2 REST gateway. proto3 omits default values, so every field needs a
// safe default here.
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class ApiSessionDto(
    val token: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val created: Boolean = false,
)

@Serializable
internal data class ApiUserDto(
    val id: String = "",
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val location: String = "",
    val online: Boolean = false,
)

@Serializable
internal data class ApiAccountDto(
    val user: ApiUserDto = ApiUserDto(),
    val email: String = "",
)

@Serializable
internal data class ApiFriendDto(
    val user: ApiUserDto = ApiUserDto(),
    /** 0 friend, 1 outgoing invite, 2 incoming invite, 3 banned. Omitted = 0. */
    @Serializable(with = FriendStateAsIntSerializer::class)
    val state: Int = 0,
)

@Serializable
internal data class ApiFriendListDto(
    val friends: List<ApiFriendDto> = emptyList(),
    val cursor: String = "",
)

/** Website `/api/friends` envelope — same accounts as Nakama, camelCase + string states. */
@Serializable
internal data class WebsiteFriendsResponseDto(
    val ok: Boolean = false,
    val data: WebsiteFriendsDataDto = WebsiteFriendsDataDto(),
)

@Serializable
internal data class WebsiteFriendsDataDto(
    val friends: List<WebsiteFriendDto> = emptyList(),
    val incoming: List<WebsiteFriendDto> = emptyList(),
    val outgoing: List<WebsiteFriendDto> = emptyList(),
)

@Serializable
internal data class WebsiteFriendDto(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val online: Boolean = false,
    val state: String = "",
)

@Serializable
internal data class ApiStorageObjectDto(
    val collection: String = "",
    val key: String = "",
    @SerialName("user_id") val userId: String = "",
    /** JSON-encoded string payload. */
    val value: String = "",
)

@Serializable
internal data class ApiStorageObjectsDto(
    val objects: List<ApiStorageObjectDto> = emptyList(),
)

@Serializable
internal data class ApiErrorDto(
    val message: String = "",
    val code: Int = 0,
)

/** Website notification inbox payload: `{ items: [...] }`. */
@Serializable
internal data class InboxValueDto(
    val items: List<InboxItemDto> = emptyList(),
)

@Serializable
internal data class InboxItemDto(
    val id: String = "",
    val type: String = "",
    val fromUsername: String = "",
    val fromDisplayName: String = "",
    val body: String = "",
    val href: String = "",
    val createdAt: String = "",
    val read: Boolean = false,
)

/** Nakama uses ints; the website uses `"friend"` / `"incoming"` / `"outgoing"`. */
internal fun parseXoraFriendState(raw: Int): XoraFriendState? = when (raw) {
    0 -> XoraFriendState.Friend
    1 -> XoraFriendState.OutgoingInvite
    2 -> XoraFriendState.IncomingInvite
    else -> null
}

internal fun parseXoraFriendState(raw: String): XoraFriendState? {
    raw.toIntOrNull()?.let { return parseXoraFriendState(it) }
    return when (raw.trim().lowercase()) {
        "friend" -> XoraFriendState.Friend
        "outgoing", "invite_sent", "invite sent" -> XoraFriendState.OutgoingInvite
        "incoming", "invite_received", "invite received" -> XoraFriendState.IncomingInvite
        else -> null
    }
}

internal fun resolveXoraAvatarUrl(username: String, avatarUrl: String): String {
    val raw = avatarUrl.trim()
    return when {
        raw.startsWith("https://", ignoreCase = true) ||
            raw.startsWith("http://", ignoreCase = true) -> raw
        raw.startsWith("/") -> "${XoraNetworkClient.ACCOUNT_SITE}$raw"
        username.isNotBlank() -> XoraNetworkClient.avatarUrlFor(username)
        else -> raw
    }
}

internal fun ApiFriendDto.toXoraFriend(): XoraFriend? {
    val username = user.username.trim()
    if (username.isEmpty()) return null
    val friendState = parseXoraFriendState(state) ?: return null
    return XoraFriend(
        username = username,
        displayName = user.displayName.ifBlank { username },
        avatarUrl = user.avatarUrl,
        online = user.online,
        state = friendState,
        userId = user.id,
    )
}

internal fun WebsiteFriendDto.toXoraFriend(fallbackState: XoraFriendState): XoraFriend? {
    val name = username.trim().ifBlank { id.trim() }
    if (name.isEmpty()) return null
    val friendState = parseXoraFriendState(state) ?: fallbackState
    return XoraFriend(
        username = name,
        displayName = displayName.ifBlank { name },
        avatarUrl = avatarUrl,
        online = online,
        state = friendState,
    )
}

internal fun mergeXoraFriends(vararg groups: List<XoraFriend>): List<XoraFriend> {
    val byUsername = LinkedHashMap<String, XoraFriend>()
    groups.forEach { group ->
        group.forEach { friend ->
            val key = friend.username.lowercase()
            val existing = byUsername[key]
            byUsername[key] = if (existing == null) {
                friend
            } else {
                existing.copy(
                    displayName = existing.displayName.ifBlank { friend.displayName },
                    avatarUrl = existing.avatarUrl.ifBlank { friend.avatarUrl },
                    online = existing.online || friend.online,
                    state = mergeFriendState(existing.state, friend.state),
                    userId = existing.userId.ifBlank { friend.userId },
                )
            }
        }
    }
    return byUsername.values.sortedWith(
        compareBy<XoraFriend> { it.state != XoraFriendState.IncomingInvite }
            .thenBy { !it.online }
            .thenBy { it.username.lowercase() },
    )
}

private fun mergeFriendState(a: XoraFriendState, b: XoraFriendState): XoraFriendState = when {
    a == XoraFriendState.Friend || b == XoraFriendState.Friend -> XoraFriendState.Friend
    a == XoraFriendState.IncomingInvite || b == XoraFriendState.IncomingInvite ->
        XoraFriendState.IncomingInvite
    a == XoraFriendState.OutgoingInvite || b == XoraFriendState.OutgoingInvite ->
        XoraFriendState.OutgoingInvite
    else -> a
}

/**
 * Accepts Nakama's integer `state` and the website's string enum so a single DTO can parse both
 * shapes if a gateway ever mixes them.
 */
internal object FriendStateAsIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("XoraFriendStateInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0
            primitive.intOrNull?.let { return it }
            return when (parseXoraFriendState(primitive.content)) {
                XoraFriendState.Friend -> 0
                XoraFriendState.OutgoingInvite -> 1
                XoraFriendState.IncomingInvite -> 2
                XoraFriendState.Blocked -> 3
                null -> 0
            }
        }
        return decoder.decodeInt()
    }
}
