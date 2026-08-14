package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.CIRCLE_FRIEND_LIMIT
import com.arcadia.shell.datastore.CirclePin
import com.arcadia.shell.datastore.CirclePinSource
import com.arcadia.shell.datastore.DiscordSocialSettings
import com.arcadia.shell.datastore.SteamWebApiCredentials
import com.arcadia.shell.launcher.conversations.ConversationsUiState
import com.arcadia.shell.launcher.conversations.NotificationConversation
import com.arcadia.shell.launcher.discord.DiscordDmThreadUiState
import com.arcadia.shell.launcher.discord.DiscordFriendEntry
import com.arcadia.shell.launcher.discord.DiscordPresenceUiState

/**
 * Pages inside the LT social overlay.
 * Pinned Friends stay above the tab bar; LB/RB (and L/R) cycle Discord / Steam / XOrA Network.
 */
enum class SocialMenuTab {
    Discord,
    Steam,
    /** XOrA Network — friends, presence, and website DMs. */
    XoraNetwork,
}

enum class SocialPresence {
    Online,
    Away,
    Busy,
    Offline,
    InGame,
}

data class SteamFriendEntry(
    val steamId: String,
    val displayName: String,
    val avatarUrl: String?,
    val presence: SocialPresence,
    val currentGame: String?,
    val profileUrl: String?,
)

data class SteamFriendsUiState(
    val credentials: SteamWebApiCredentials = SteamWebApiCredentials(),
    val isLoading: Boolean = false,
    val friends: List<SteamFriendEntry> = emptyList(),
    val error: String? = null,
) {
    val isConfigured: Boolean get() = credentials.isConfigured

    val onlineCount: Int get() = friends.count { it.presence != SocialPresence.Offline }
}

data class DiscordSocialUiState(
    val settings: DiscordSocialSettings = DiscordSocialSettings(),
    val presence: DiscordPresenceUiState = DiscordPresenceUiState(),
) {
    val hasLink: Boolean get() = settings.hasLink
    val hasApplicationId: Boolean get() = settings.hasApplicationId
    val friends: List<DiscordFriendEntry>
        get() = presence.friends

    /** The linked account resolved far enough to borrow its profile picture. */
    val avatarAvailable: Boolean get() = !presence.currentUserAvatarUrl.isNullOrBlank()
}

/** Resolved pin for the Pinned Friends strip (Steam or Discord). */
data class CircleMemberUi(
    val pin: CirclePin,
    val displayName: String,
    val avatarUrl: String?,
    val presence: SocialPresence,
    /** Current game / rich presence line when known. */
    val activityLabel: String?,
    val hasUnread: Boolean = false,
)

/**
 * Focusable rows inside the expanded LT social menu (flat list for U/D within the active tab).
 */
sealed interface AccountPanelRow {
    /** Opens the LT notification center (recent banners + message inbox). */
    data object OpenNotifications : AccountPanelRow
    data object ManageCircle : AccountPanelRow
    /** Empty pinned-friend slot — A opens add / manage flow. */
    data class CircleEmptySlot(val slotIndex: Int) : AccountPanelRow
    data class CircleMember(val pin: CirclePin) : AccountPanelRow
    /** Add this friend into Pinned Friends (manage mode). */
    data class AddToCircle(val pin: CirclePin) : AccountPanelRow
    /** Remove this friend from Pinned Friends (manage mode). */
    data class RemoveFromCircle(val pin: CirclePin) : AccountPanelRow
    data class SteamFriend(val steamId: String) : AccountPanelRow
    data class DiscordFriend(val userId: String) : AccountPanelRow
    data class XoraFriend(val username: String) : AccountPanelRow

    data object SteamConfigure : AccountPanelRow
    /** Opens XOrA Network Dashboard so the user can sign in. */
    data object XoraNetworkSignIn : AccountPanelRow
    /** Opens system Notification Listener settings so conversations can appear. */
    data object EnableNotificationAccess : AccountPanelRow
    data class Conversation(val key: String) : AccountPanelRow
    /** Confirms the in-panel reply draft for [conversationKey]. */
    data class ConversationReplySend(val conversationKey: String) : AccountPanelRow

    /** Single Discord Rich Presence link CTA (Discord tab only when not linked). */
    data object DiscordConnect : AccountPanelRow
    data object DiscordOpenApp : AccountPanelRow
    /** Sends the in-launcher Discord DM draft. */
    data object DiscordDmSend : AccountPanelRow
    /** Closes the in-launcher Discord DM pane. */
    data object DiscordDmClose : AccountPanelRow
    data object XoraDmSend : AccountPanelRow
    data object XoraDmClose : AccountPanelRow
}

data class ConversationReplyUiState(
    val conversationKey: String? = null,
    val draft: String = "",
)

data class SocialMenuUiState(
    val tab: SocialMenuTab = SocialMenuTab.Discord,
    val steam: SteamFriendsUiState = SteamFriendsUiState(),
    val discord: DiscordSocialUiState = DiscordSocialUiState(),
    val conversations: ConversationsUiState = ConversationsUiState(),
    val reply: ConversationReplyUiState = ConversationReplyUiState(),
    /** In-launcher Discord DM thread (Social SDK messaging). */
    val discordDm: DiscordDmThreadUiState = DiscordDmThreadUiState(),
    val xoraNetwork: com.arcadia.shell.xoranetwork.XoraNetworkState =
        com.arcadia.shell.xoranetwork.XoraNetworkState(),
    /** Persisted mixed Pinned Friends pins (max [CIRCLE_FRIEND_LIMIT]). */
    val circlePins: List<CirclePin> = emptyList(),
    /** When true, friend lists show add/remove pin controls. */
    val managingCircle: Boolean = false,
    /** LT notification center overlay (recent shell notifications). */
    val notificationsOpen: Boolean = false,
    /** Recent shell notifications for the LT notification center. */
    val recentNotifications: List<com.arcadia.shell.launcher.notifications.ShellNotification> = emptyList(),
    /** Local filter for the friends list. */
    val friendSearchQuery: String = "",
) {
    val isReplying: Boolean get() = reply.conversationKey != null

    val isDiscordDmOpen: Boolean get() = discordDm.peerUserId != null

    val isXoraDmOpen: Boolean get() = xoraNetwork.dm.isOpen

    val circleSlotsFilled: Int get() = circlePins.size.coerceAtMost(CIRCLE_FRIEND_LIMIT)

    val circlePinKeys: Set<String> get() = circlePins.mapTo(mutableSetOf()) { it.key }

    val circleMembers: List<CircleMemberUi>
        get() = circlePins.map { pin -> resolveCircleMember(pin) }

    /** Online Steam + Discord friends for the header badge. */
    val friendsBadgeCount: Int
        get() = steam.onlineCount +
            discord.friends.count { it.isOnline } +
            xoraNetwork.onlineFriendCount

    /** Notification conversations for the header messages badge. */
    val messagesBadgeCount: Int
        get() = conversations.conversations.size

    val restOfSteamFriends: List<SteamFriendEntry>
        get() {
            val circle = circlePinKeys
            val q = friendSearchQuery.trim()
            return steam.friends
                .filter { CirclePin(CirclePinSource.Steam, it.steamId).key !in circle }
                .filter {
                    q.isEmpty() ||
                        it.displayName.contains(q, ignoreCase = true) ||
                        (it.currentGame?.contains(q, ignoreCase = true) == true)
                }
        }

    val filteredDiscordFriends: List<DiscordFriendEntry>
        get() {
            val q = friendSearchQuery.trim()
            return discord.friends.filter {
                q.isEmpty() || it.displayName.contains(q, ignoreCase = true)
            }
        }

    val filteredXoraFriends: List<com.arcadia.shell.xoranetwork.XoraFriend>
        get() {
            val q = friendSearchQuery.trim()
            return xoraNetwork.acceptedFriends.filter {
                q.isEmpty() ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    it.username.contains(q, ignoreCase = true) ||
                    it.status.contains(q, ignoreCase = true)
            }
        }

    fun conversation(key: String): NotificationConversation? =
        conversations.conversations.firstOrNull { it.key == key }

    fun resolveCircleMember(pin: CirclePin): CircleMemberUi = when (pin.source) {
        CirclePinSource.Steam -> {
            val friend = steam.friends.firstOrNull { it.steamId == pin.id }
            val unread = conversations.steamConversations.any { convo ->
                convo.steamIdHint == pin.id ||
                    (friend != null &&
                        convo.title.equals(friend.displayName, ignoreCase = true))
            }
            CircleMemberUi(
                pin = pin,
                displayName = friend?.displayName ?: "Steam friend",
                avatarUrl = friend?.avatarUrl,
                presence = friend?.presence ?: SocialPresence.Offline,
                activityLabel = friend?.currentGame,
                hasUnread = unread,
            )
        }
        CirclePinSource.Discord -> {
            val friend = discord.friends.firstOrNull { it.userId == pin.id }
            val unread = conversations.discordConversations.any { convo ->
                friend != null && convo.title.equals(friend.displayName, ignoreCase = true)
            }
            CircleMemberUi(
                pin = pin,
                displayName = friend?.displayName ?: "Discord friend",
                avatarUrl = friend?.avatarUrl,
                presence = discordFriendPresence(friend),
                activityLabel = discordFriendActivity(friend),
                hasUnread = unread,
            )
        }
        CirclePinSource.XoraNetwork -> {
            val friend = xoraNetwork.acceptedFriends.firstOrNull {
                it.username.equals(pin.id, ignoreCase = true)
            }
            val unread = xoraNetwork.dm.peerUsername.equals(pin.id, ignoreCase = true) &&
                xoraNetwork.dm.messages.isNotEmpty()
            CircleMemberUi(
                pin = pin,
                displayName = friend?.displayName ?: pin.id,
                avatarUrl = friend?.resolvedAvatarUrl,
                presence = xoraFriendPresence(friend),
                activityLabel = xoraFriendActivity(friend),
                hasUnread = unread,
            )
        }
    }
}

fun xoraFriendPresence(friend: com.arcadia.shell.xoranetwork.XoraFriend?): SocialPresence {
    if (friend == null || !friend.online) return SocialPresence.Offline
    val raw = friend.status.trim()
    return when {
        raw.equals("Away", ignoreCase = true) -> SocialPresence.Away
        raw.equals("Busy", ignoreCase = true) -> SocialPresence.Busy
        raw.startsWith("Playing ", ignoreCase = true) -> SocialPresence.InGame
        else -> SocialPresence.Online
    }
}

fun xoraFriendActivity(friend: com.arcadia.shell.xoranetwork.XoraFriend?): String? {
    if (friend == null || !friend.online) return null
    val raw = friend.status.trim()
    return when {
        raw.isBlank() || raw.equals("Online", ignoreCase = true) -> "Online"
        else -> raw
    }
}

fun discordFriendPresence(friend: DiscordFriendEntry?): SocialPresence = when (friend?.group) {
    "online_game" -> SocialPresence.InGame
    "online_elsewhere" -> SocialPresence.Online
    null -> SocialPresence.Offline
    else -> SocialPresence.Offline
}

fun discordFriendActivity(friend: DiscordFriendEntry?): String? = when (friend?.group) {
    "online_game" -> "In XOrA"
    "online_elsewhere" -> "Online"
    null -> null
    else -> "Offline"
}

/**
 * Focusable rows inside the expanded RT profile card.
 */
sealed interface SystemPanelRow {
    /** RT bell notification history. */
    data object Notifications : SystemPanelRow
    /** Activity / custom status bubble. */
    data object Status : SystemPanelRow
    /** Favorite RetroAchievements game (plus placeholder when unset). */
    data object FavoriteGame : SystemPanelRow
    data object EditProfile : SystemPanelRow
    /** Recently played title shown in the expanded RT card. */
    data class JumpBack(val gameId: String) : SystemPanelRow
    /** Quick settings rows retained from the master shell. */
    data object Brightness : SystemPanelRow
    data object Wifi : SystemPanelRow
    data object Bluetooth : SystemPanelRow
    data object AllSettings : SystemPanelRow
    /** Clear the pinned favorite (only while the favorite picker is open). */
    data object ClearFavorite : SystemPanelRow
    /** One RetroAchievements completion-progress game in the favorite picker. */
    data class RaFavoritePick(val gameId: Int) : SystemPanelRow
}

fun buildSystemPanelRows(
    jumpBackGames: List<String> = emptyList(),
    favoritePickerOpen: Boolean = false,
    favoritePickerGameIds: List<Int> = emptyList(),
): List<SystemPanelRow> =
    if (favoritePickerOpen) {
        buildList {
            add(SystemPanelRow.ClearFavorite)
            favoritePickerGameIds.forEach { add(SystemPanelRow.RaFavoritePick(it)) }
        }
    } else {
        buildList {
            add(SystemPanelRow.Status)
            add(SystemPanelRow.FavoriteGame)
            add(SystemPanelRow.EditProfile)
            add(SystemPanelRow.Notifications)
            jumpBackGames.take(3).forEach { add(SystemPanelRow.JumpBack(it)) }
            add(SystemPanelRow.Brightness)
            add(SystemPanelRow.Wifi)
            add(SystemPanelRow.Bluetooth)
            add(SystemPanelRow.AllSettings)
        }
    }
