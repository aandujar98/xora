package com.arcadia.shell.xoranetwork

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    /** Nakama avatar_url when set; may be a website-hosted avatar that 401s outside a web session. */
    val avatarUrl: String,
) {
    /** Website avatar endpoint fallback; the UI shows initials if neither URL loads. */
    val websiteAvatarUrl: String get() = XoraNetworkClient.avatarUrlFor(username)
}

data class XoraFriend(
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val online: Boolean,
    val state: XoraFriendState,
) {
    val websiteAvatarUrl: String get() = XoraNetworkClient.avatarUrlFor(username)
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
    val notifications: List<XoraNotificationItem> = emptyList(),
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
    val state: Int = 0,
)

@Serializable
internal data class ApiFriendListDto(
    val friends: List<ApiFriendDto> = emptyList(),
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
