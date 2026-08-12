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
 * Pages inside the LT social overlay (Cocoon-style).
 * Your Circle stays above the tab bar; LB/RB (and L/R) cycle Discord / Steam / Messages.
 */
enum class SocialMenuTab {
    Discord,
    Steam,
    /** Notification-listener conversations (Android / other messaging apps). */
    Android,
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
}

/** Resolved pin for the Your Circle strip (Steam or Discord). */
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
    data object ManageCircle : AccountPanelRow
    /** Empty Circle slot — A opens add / manage flow. */
    data class CircleEmptySlot(val slotIndex: Int) : AccountPanelRow
    data class CircleMember(val pin: CirclePin) : AccountPanelRow
    /** Add this friend into Circle (manage mode). */
    data class AddToCircle(val pin: CirclePin) : AccountPanelRow
    /** Remove this friend from Circle (manage mode). */
    data class RemoveFromCircle(val pin: CirclePin) : AccountPanelRow
    data class SteamFriend(val steamId: String) : AccountPanelRow
    data class DiscordFriend(val userId: String) : AccountPanelRow

    data object SteamConfigure : AccountPanelRow
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
    /** Persisted mixed Circle pins (max [CIRCLE_FRIEND_LIMIT]). */
    val circlePins: List<CirclePin> = emptyList(),
    /** When true, friend lists show add/remove Circle controls. */
    val managingCircle: Boolean = false,
    /** Local filter for the friends list. */
    val friendSearchQuery: String = "",
) {
    val isReplying: Boolean get() = reply.conversationKey != null

    val isDiscordDmOpen: Boolean get() = discordDm.peerUserId != null

    val circleSlotsFilled: Int get() = circlePins.size.coerceAtMost(CIRCLE_FRIEND_LIMIT)

    val circlePinKeys: Set<String> get() = circlePins.mapTo(mutableSetOf()) { it.key }

    val circleMembers: List<CircleMemberUi>
        get() = circlePins.map { pin -> resolveCircleMember(pin) }

    /** Online Steam + Discord friends for the header badge. */
    val friendsBadgeCount: Int
        get() = steam.onlineCount + discord.friends.count { it.isOnline }

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
    /** Activity / custom status bubble. */
    data object Status : SystemPanelRow
    /** Favorite RetroAchievements game (plus placeholder when unset). */
    data object FavoriteGame : SystemPanelRow
    data object EditProfile : SystemPanelRow
    /** Clear the pinned favorite (only while the favorite picker is open). */
    data object ClearFavorite : SystemPanelRow
    /** One RetroAchievements completion-progress game in the favorite picker. */
    data class RaFavoritePick(val gameId: Int) : SystemPanelRow
}

fun buildSystemPanelRows(
    favoritePickerOpen: Boolean,
    favoritePickerGameIds: List<Int> = emptyList(),
): List<SystemPanelRow> =
    if (favoritePickerOpen) {
        buildList {
            add(SystemPanelRow.ClearFavorite)
            favoritePickerGameIds.forEach { add(SystemPanelRow.RaFavoritePick(it)) }
        }
    } else {
        listOf(
            SystemPanelRow.Status,
            SystemPanelRow.FavoriteGame,
            SystemPanelRow.EditProfile,
        )
    }
