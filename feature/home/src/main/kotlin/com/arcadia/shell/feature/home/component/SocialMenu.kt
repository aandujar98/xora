package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.CIRCLE_FRIEND_LIMIT
import com.arcadia.shell.datastore.CirclePin
import com.arcadia.shell.datastore.CirclePinSource
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.AccountPanelRow
import com.arcadia.shell.feature.home.ConversationReplyUiState
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.feature.home.SocialMenuTab
import com.arcadia.shell.feature.home.SocialMenuUiState
import com.arcadia.shell.feature.home.SocialPresence
import com.arcadia.shell.feature.home.SteamFriendEntry
import com.arcadia.shell.feature.home.discordFriendActivity
import com.arcadia.shell.feature.home.discordFriendPresence
import com.arcadia.shell.launcher.conversations.ConversationSource
import com.arcadia.shell.launcher.conversations.NotificationConversation
import com.arcadia.shell.launcher.discord.DiscordDmMessage
import com.arcadia.shell.launcher.discord.DiscordDmThreadUiState
import com.arcadia.shell.launcher.discord.DiscordFriendEntry
import com.arcadia.shell.launcher.discord.DiscordPresenceCapability
import kotlinx.coroutines.delay

private val OnlineGreen = Color(0xFF37D6A0)
private val AwayAmber = Color(0xFFFFC24B)
private val BusyRose = Color(0xFFFF5C6C)
private val FocusRing = Color(0xFF4AE39A)
private val SteamAccent = Color(0xFF66C0F4)
private val DiscordAccent = Color(0xFF5865F2)
private val AndroidAccent = Color(0xFF3DDC84)
private val FriendsBadge = Color(0xFF9B7BFF)
private val MessagesBadge = Color(0xFFFF8A4C)
private val SkyGlass = Color(0xFF7EC8E8)

@Composable
fun SocialMenuPanel(
    social: SocialMenuUiState,
    profile: LocalProfile,
    profileAvatarModel: String?,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    onSelectTab: (SocialMenuTab) -> Unit,
    onSelectRow: (Int) -> Unit,
    onActivateRow: (Int?) -> Unit,
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)

    Column(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(28.dp),
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
                shimmer = true,
            )
            .padding(14.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SocialHeader(
            profile = profile,
            profileAvatarModel = profileAvatarModel,
            friendsCount = social.friendsBadgeCount,
            messagesCount = social.messagesBadgeCount,
            glassMuted = glass.contentMuted,
            onMessagesClick = { onSelectTab(SocialMenuTab.Android) },
        )

        YourCircleSection(
            social = social,
            accountRows = accountRows,
            selectedRowIndex = selectedRowIndex,
            glassContent = glass.content,
            glassMuted = glass.contentMuted,
            onActivateRow = onActivateRow,
        )

        SocialPlatformTabBar(
            selected = social.tab,
            onSelect = onSelectTab,
            muted = glass.contentMuted,
        )

        when (social.tab) {
            SocialMenuTab.Discord -> DiscordTabContent(
                social = social,
                accountRows = accountRows,
                selectedRowIndex = selectedRowIndex,
                glassMuted = glass.contentMuted,
                onActivateRow = onActivateRow,
                onFriendSearchChange = onFriendSearchChange,
                onDmDraftChange = onReplyDraftChange,
            )
            SocialMenuTab.Steam -> SteamTabContent(
                social = social,
                accountRows = accountRows,
                selectedRowIndex = selectedRowIndex,
                glassMuted = glass.contentMuted,
                onActivateRow = onActivateRow,
                onFriendSearchChange = onFriendSearchChange,
            )
            SocialMenuTab.Android -> AndroidTabContent(
                social = social,
                accountRows = accountRows,
                selectedRowIndex = selectedRowIndex,
                glassMuted = glass.contentMuted,
                onActivateRow = onActivateRow,
                onReplyDraftChange = onReplyDraftChange,
            )
        }

        Text(
            text = when {
                social.isDiscordDmOpen -> "A send · B back · type to message"
                social.isReplying -> "A send · B cancel reply · type on keyboard"
                social.managingCircle -> "A pin/unpin · Manage to finish · L/R tabs"
                else -> "LT close · L/R tabs · U/D · A chat · Manage to pin"
            },
            style = MaterialTheme.typography.labelSmall,
            color = glass.contentMuted.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SocialHeader(
    profile: LocalProfile,
    profileAvatarModel: String?,
    friendsCount: Int,
    messagesCount: Int,
    glassMuted: Color,
    onMessagesClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TriggerGlyph(letter = "LB")
        PresenceAvatar(
            displayName = profile.displayName,
            presetId = profile.avatarPresetId,
            size = 52.dp,
            imageModel = profileAvatarModel,
            presence = SocialPresence.Online,
            selected = false,
        )
        Spacer(modifier = Modifier.weight(1f))
        CountBadge(
            glyph = "◎",
            count = friendsCount,
            accent = FriendsBadge,
        )
        CountBadge(
            glyph = "◌",
            count = messagesCount,
            accent = MessagesBadge,
            onClick = onMessagesClick,
        )
    }
}

@Composable
private fun CountBadge(
    glyph: String,
    count: Int,
    accent: Color,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

@Composable
private fun YourCircleSection(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassContent: Color,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your Circle  ${social.circleSlotsFilled}/$CIRCLE_FRIEND_LIMIT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = glassContent,
            )
            val manageIndex = accountRows.indexOfFirst { it is AccountPanelRow.ManageCircle }
            Text(
                text = if (social.managingCircle) "Done" else "Manage",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (manageIndex >= 0 && manageIndex == selectedRowIndex) {
                    FocusRing
                } else {
                    SkyGlass
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (manageIndex >= 0 && manageIndex == selectedRowIndex) {
                            Modifier
                                .background(FocusRing.copy(alpha = 0.18f))
                                .border(1.dp, FocusRing.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        if (manageIndex >= 0) onActivateRow(manageIndex)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        YourCircleRow(
            social = social,
            accountRows = accountRows,
            selectedRowIndex = selectedRowIndex,
            onActivateRow = onActivateRow,
        )

        if (social.managingCircle) {
            Text(
                text = "A on a friend below to pin or unpin · Discord & Steam mix OK",
                style = MaterialTheme.typography.labelSmall,
                color = glassMuted,
            )
        }
    }
}

@Composable
private fun YourCircleRow(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    onActivateRow: (Int?) -> Unit,
) {
    val stripScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(stripScroll),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(CIRCLE_FRIEND_LIMIT) { slot ->
            val pin = social.circlePins.getOrNull(slot)
            val member = pin?.let { social.resolveCircleMember(it) }
            val rowIndex = if (pin != null) {
                accountRows.indexOfFirst {
                    it is AccountPanelRow.CircleMember && it.pin.key == pin.key
                }
            } else {
                accountRows.indexOfFirst {
                    it is AccountPanelRow.CircleEmptySlot && it.slotIndex == slot
                }
            }
            val selected = rowIndex >= 0 && rowIndex == selectedRowIndex
            val bringIntoViewRequester = remember(slot) { BringIntoViewRequester() }
            LaunchedEffect(selected) {
                if (selected) {
                    delay(16)
                    bringIntoViewRequester.bringIntoView()
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(72.dp)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .clickable {
                        if (rowIndex >= 0) onActivateRow(rowIndex)
                    },
            ) {
                if (member != null) {
                    PresenceAvatar(
                        displayName = member.displayName,
                        presetId = "preset_0",
                        size = if (selected) 64.dp else 56.dp,
                        imageModel = member.avatarUrl,
                        presence = member.presence,
                        selected = selected,
                        gameBadge = !member.activityLabel.isNullOrBlank() &&
                            member.presence != SocialPresence.Offline,
                        sourceTint = when (member.pin.source) {
                            CirclePinSource.Steam -> SteamAccent
                            CirclePinSource.Discord -> DiscordAccent
                        },
                    )
                    Text(
                        text = member.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    EmptyCircleSlot(selected = selected)
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCircleSlot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = if (selected) 2.5.dp else 1.5.dp,
                color = if (selected) FocusRing else Color.White.copy(alpha = 0.25f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun SocialPlatformTabBar(
    selected: SocialMenuTab,
    onSelect: (SocialMenuTab) -> Unit,
    muted: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, SkyGlass.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.labelLarge,
            color = muted.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialMenuTab.entries.forEach { tab ->
                val active = tab == selected
                val accent = when (tab) {
                    SocialMenuTab.Discord -> DiscordAccent
                    SocialMenuTab.Steam -> SteamAccent
                    SocialMenuTab.Android -> AndroidAccent
                }
                val iconRes = when (tab) {
                    SocialMenuTab.Discord -> R.drawable.ic_brand_discord
                    SocialMenuTab.Steam -> R.drawable.ic_brand_steam
                    SocialMenuTab.Android -> R.drawable.ic_brand_android
                }
                val contentDescription = when (tab) {
                    SocialMenuTab.Discord -> "Discord"
                    SocialMenuTab.Steam -> "Steam"
                    SocialMenuTab.Android -> "Android"
                }
                Box(
                    modifier = Modifier
                        .size(if (active) 40.dp else 36.dp)
                        .clip(CircleShape)
                        .then(
                            if (active) {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .border(2.dp, FocusRing.copy(alpha = 0.85f), CircleShape)
                            } else {
                                Modifier.background(Color.White.copy(alpha = 0.05f))
                            },
                        )
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = contentDescription,
                        tint = if (active) accent else muted.copy(alpha = 0.55f),
                        modifier = Modifier.size(if (active) 22.dp else 18.dp),
                    )
                }
            }
        }
        Text(
            text = "+",
            style = MaterialTheme.typography.labelLarge,
            color = muted.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun DiscordTabContent(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onFriendSearchChange: (String) -> Unit,
    onDmDraftChange: (String) -> Unit,
) {
    if (social.discordDm.peerUserId != null) {
        // Full chat lives in DiscordConversationWindow; keep a compact resume chip here.
        DiscordDmOpenChip(
            thread = social.discordDm,
            glassMuted = glassMuted,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscordPresenceCta(
            social = social,
            accountRows = accountRows,
            selectedRowIndex = selectedRowIndex,
            glassMuted = glassMuted,
            onActivateRow = onActivateRow,
        )

        FriendSearchField(
            query = social.friendSearchQuery,
            onQueryChange = onFriendSearchChange,
            muted = glassMuted,
            placeholder = "Search Discord friends",
        )

        val friends = if (social.managingCircle) {
            social.filteredDiscordFriends
        } else {
            social.filteredDiscordFriends.filter {
                CirclePin(CirclePinSource.Discord, it.userId).key !in social.circlePinKeys
            }
        }

        when {
            social.discord.presence.capability != DiscordPresenceCapability.Connected &&
                friends.isEmpty() -> {
                Text(
                    text = when (social.discord.presence.capability) {
                        DiscordPresenceCapability.NeedsAccountLink ->
                            "Link Discord above to load friends"
                        DiscordPresenceCapability.NeedsDiscordApp ->
                            "Install Discord to see friends"
                        DiscordPresenceCapability.SdkMissing ->
                            "Discord SDK missing — see Settings"
                        DiscordPresenceCapability.NotConfigured ->
                            "Add Discord Application ID in Settings"
                        DiscordPresenceCapability.Failed ->
                            "Retry Discord link above"
                        DiscordPresenceCapability.Connected ->
                            "No Discord friends yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMuted,
                )
            }
            friends.isEmpty() -> {
                Text(
                    text = if (social.friendSearchQuery.isNotBlank()) {
                        "No matches"
                    } else if (social.managingCircle) {
                        "No Discord friends loaded"
                    } else {
                        "Everyone is in your Circle — or link Discord"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMuted,
                )
            }
            else -> {
                friends.forEach { friend ->
                    val pin = CirclePin(CirclePinSource.Discord, friend.userId)
                    val inCircle = pin.key in social.circlePinKeys
                    val rowIndex = accountRows.indexOfFirst { row ->
                        when {
                            social.managingCircle && inCircle ->
                                row is AccountPanelRow.RemoveFromCircle && row.pin.key == pin.key
                            social.managingCircle && !inCircle ->
                                row is AccountPanelRow.AddToCircle && row.pin.key == pin.key
                            else ->
                                row is AccountPanelRow.DiscordFriend && row.userId == friend.userId
                        }
                    }
                    DiscordFriendRow(
                        friend = friend,
                        selected = rowIndex >= 0 && rowIndex == selectedRowIndex,
                        trailingHint = when {
                            social.managingCircle && inCircle -> "Unpin"
                            social.managingCircle && !inCircle ->
                                if (social.circleSlotsFilled >= CIRCLE_FRIEND_LIMIT) "Full" else "Pin"
                            else -> null
                        },
                        hasUnread = social.conversations.discordConversations.any {
                            it.title.equals(friend.displayName, ignoreCase = true)
                        },
                        onClick = {
                            if (rowIndex >= 0) onActivateRow(rowIndex)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscordDmOpenChip(
    thread: DiscordDmThreadUiState,
    glassMuted: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DiscordAccent.copy(alpha = 0.18f))
            .border(1.dp, DiscordAccent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PresenceAvatar(
            displayName = thread.peerDisplayName,
            presetId = "preset_0",
            size = 48.dp,
            imageModel = thread.peerAvatarUrl,
            presence = SocialPresence.Online,
            selected = false,
            sourceTint = DiscordAccent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chatting with ${thread.peerDisplayName.ifBlank { "friend" }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Conversation window open · B closes chat",
                style = MaterialTheme.typography.labelSmall,
                color = glassMuted,
            )
        }
    }
}

/**
 * Single Discord Rich Presence CTA — only when not linked / publishing subtly once connected.
 */
@Composable
private fun DiscordPresenceCta(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
) {
    val presence = social.discord.presence
    if (presence.capability == DiscordPresenceCapability.NotConfigured &&
        !social.discord.hasApplicationId
    ) {
        return
    }
    if (presence.capability == DiscordPresenceCapability.Connected) {
        if (presence.presencePublishing) {
            Text(
                text = "Publishing presence · ${presence.shareText}",
                style = MaterialTheme.typography.labelSmall,
                color = glassMuted.copy(alpha = 0.85f),
            )
        }
        return
    }

    val connectIndex = accountRows.indexOfFirst { it is AccountPanelRow.DiscordConnect }
    val title = when {
        presence.connecting -> "Connecting Discord…"
        presence.capability == DiscordPresenceCapability.NeedsAccountLink ->
            "Link Discord"
        presence.capability == DiscordPresenceCapability.NeedsDiscordApp ->
            "Install Discord"
        presence.capability == DiscordPresenceCapability.Failed ->
            "Retry Discord link"
        presence.capability == DiscordPresenceCapability.SdkMissing ->
            "Discord SDK missing — Settings"
        else -> "Link Discord"
    }
    SocialListRow(
        title = title,
        subtitle = presence.statusLine,
        selected = connectIndex >= 0 && connectIndex == selectedRowIndex,
        accent = DiscordAccent,
        onClick = {
            if (connectIndex >= 0) onActivateRow(connectIndex)
        },
    )
}

@Composable
private fun SteamTabContent(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onFriendSearchChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!social.steam.isConfigured) {
            val cfg = accountRows.indexOfFirst { it is AccountPanelRow.SteamConfigure }
            SocialListRow(
                title = "Connect Steam",
                subtitle = "Sign in with Steam + Web API key in Settings",
                selected = cfg >= 0 && cfg == selectedRowIndex,
                accent = SteamAccent,
                onClick = {
                    if (cfg >= 0) onActivateRow(cfg)
                },
            )
            return
        }

        FriendSearchField(
            query = social.friendSearchQuery,
            onQueryChange = onFriendSearchChange,
            muted = glassMuted,
            placeholder = "Search Steam friends",
        )

        when {
            social.steam.isLoading && social.steam.friends.isEmpty() -> {
                Text(
                    text = "Loading friends…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassMuted,
                )
            }
            social.steam.error != null && social.steam.friends.isEmpty() -> {
                Text(
                    text = social.steam.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = BusyRose,
                )
            }
            else -> {
                val friendsToShow = if (social.managingCircle) {
                    social.steam.friends.filter {
                        val q = social.friendSearchQuery.trim()
                        q.isEmpty() || it.displayName.contains(q, ignoreCase = true)
                    }
                } else {
                    social.restOfSteamFriends
                }
                if (friendsToShow.isEmpty()) {
                    Text(
                        text = if (social.friendSearchQuery.isNotBlank()) {
                            "No matches"
                        } else if (social.managingCircle) {
                            "No Steam friends loaded"
                        } else {
                            "Everyone is in your Circle — or connect Steam"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = glassMuted,
                    )
                } else {
                    friendsToShow.forEach { friend ->
                        val pin = CirclePin(CirclePinSource.Steam, friend.steamId)
                        val inCircle = pin.key in social.circlePinKeys
                        val rowIndex = accountRows.indexOfFirst { row ->
                            when {
                                social.managingCircle && inCircle ->
                                    row is AccountPanelRow.RemoveFromCircle &&
                                        row.pin.key == pin.key
                                social.managingCircle && !inCircle ->
                                    row is AccountPanelRow.AddToCircle &&
                                        row.pin.key == pin.key
                                else ->
                                    row is AccountPanelRow.SteamFriend &&
                                        row.steamId == friend.steamId
                            }
                        }
                        SteamFriendRow(
                            friend = friend,
                            selected = rowIndex >= 0 && rowIndex == selectedRowIndex,
                            trailingHint = when {
                                social.managingCircle && inCircle -> "Unpin"
                                social.managingCircle && !inCircle ->
                                    if (social.circleSlotsFilled >= CIRCLE_FRIEND_LIMIT) {
                                        "Full"
                                    } else {
                                        "Pin"
                                    }
                                else -> null
                            },
                            hasUnread = social.conversations.steamConversations.any { convo ->
                                convo.steamIdHint == friend.steamId ||
                                    convo.title.equals(friend.displayName, ignoreCase = true)
                            },
                            onClick = {
                                if (rowIndex >= 0) onActivateRow(rowIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidTabContent(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onReplyDraftChange: (String) -> Unit,
) {
    ConversationsSection(
        title = "Conversations",
        conversations = social.conversations.conversations,
        listenerEnabled = social.conversations.listenerEnabled,
        accountRows = accountRows,
        selectedRowIndex = selectedRowIndex,
        glassMuted = glassMuted,
        emptyWhenEnabled = "No recent message notifications yet",
        reply = social.reply,
        onActivateRow = onActivateRow,
        onReplyDraftChange = onReplyDraftChange,
        footnote = "Shows message previews when notification access is on",
    )
}

@Composable
private fun FriendSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    muted: Color,
    placeholder: String = "Search Friends",
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        cursorBrush = SolidColor(FocusRing),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted.copy(alpha = 0.55f),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ConversationsSection(
    title: String,
    conversations: List<NotificationConversation>,
    listenerEnabled: Boolean,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    emptyWhenEnabled: String?,
    onActivateRow: (Int?) -> Unit,
    reply: ConversationReplyUiState = ConversationReplyUiState(),
    onReplyDraftChange: (String) -> Unit = {},
    footnote: String? = null,
) {
    SectionLabel(title, glassMuted)
    if (!listenerEnabled) {
        val enableIndex = accountRows.indexOfFirst { it is AccountPanelRow.EnableNotificationAccess }
        SocialListRow(
            title = "Enable notification access",
            subtitle = "Required for Conversations",
            selected = enableIndex >= 0 && enableIndex == selectedRowIndex,
            accent = MessagesBadge,
            onClick = {
                if (enableIndex >= 0) onActivateRow(enableIndex)
            },
        )
        Text(
            text = "Shows message previews from apps on this device when notification access is on",
            style = MaterialTheme.typography.labelSmall,
            color = glassMuted,
        )
        return
    }

    val replyKey = reply.conversationKey
    if (replyKey != null) {
        val sendIndex = accountRows.indexOfFirst {
            it is AccountPanelRow.ConversationReplySend && it.conversationKey == replyKey
        }
        val target = conversations.firstOrNull { it.key == replyKey }
        ReplyComposer(
            title = target?.title ?: "Reply",
            draft = reply.draft,
            selected = sendIndex >= 0 && sendIndex == selectedRowIndex,
            onDraftChange = onReplyDraftChange,
            onSend = {
                if (sendIndex >= 0) onActivateRow(sendIndex)
            },
        )
    }

    if (conversations.isEmpty()) {
        if (!emptyWhenEnabled.isNullOrBlank()) {
            Text(
                text = emptyWhenEnabled,
                style = MaterialTheme.typography.bodySmall,
                color = glassMuted,
            )
        }
    } else {
        conversations.forEach { convo ->
            val rowIndex = accountRows.indexOfFirst {
                it is AccountPanelRow.Conversation && it.key == convo.key
            }
            ConversationRow(
                conversation = convo,
                selected = rowIndex >= 0 && rowIndex == selectedRowIndex,
                onClick = {
                    if (rowIndex >= 0) onActivateRow(rowIndex)
                },
            )
        }
    }

    if (!footnote.isNullOrBlank()) {
        Text(
            text = footnote,
            style = MaterialTheme.typography.labelSmall,
            color = glassMuted.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ReplyComposer(
    title: String,
    draft: String,
    selected: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.18f else 0.08f))
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MessagesBadge.copy(alpha = 0.9f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSend)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Reply to $title",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(MessagesBadge),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box(modifier = Modifier.heightIn(min = 20.dp)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Write a reply…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                    inner()
                }
            },
        )
        Text(
            text = "A · Send reply",
            style = MaterialTheme.typography.labelSmall,
            color = MessagesBadge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: NotificationConversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = when (conversation.source) {
        ConversationSource.Steam -> SteamAccent
        ConversationSource.Discord -> DiscordAccent
        ConversationSource.Other -> MessagesBadge
    }
    val subtitle = buildString {
        append(conversation.appLabel)
        if (conversation.text.isNotBlank()) {
            append(" · ")
            append(conversation.text)
        }
        if (conversation.canReply) append(" · Reply")
    }
    SocialListRow(
        title = conversation.title,
        subtitle = subtitle,
        selected = selected,
        accent = accent,
        onClick = onClick,
    )
}

@Composable
private fun SteamFriendRow(
    friend: SteamFriendEntry,
    selected: Boolean,
    onClick: () -> Unit,
    trailingHint: String? = null,
    hasUnread: Boolean = false,
) {
    SocialListRow(
        leading = {
            PresenceAvatar(
                displayName = friend.displayName,
                presetId = "preset_0",
                size = 48.dp,
                imageModel = friend.avatarUrl,
                presence = friend.presence,
                selected = false,
                sourceTint = SteamAccent,
            )
        },
        title = friend.displayName,
        subtitle = buildString {
            friend.currentGame?.let { append(it) }
                ?: append(presenceLabel(friend.presence))
            trailingHint?.let {
                append(" · ")
                append(it)
            }
        },
        selected = selected,
        accent = SteamAccent,
        trailing = if (hasUnread) {
            {
                UnreadBubble()
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

@Composable
private fun DiscordFriendRow(
    friend: DiscordFriendEntry,
    selected: Boolean,
    onClick: () -> Unit,
    trailingHint: String? = null,
    hasUnread: Boolean = false,
) {
    val presence = discordFriendPresence(friend)
    val activity = discordFriendActivity(friend)
    SocialListRow(
        leading = {
            PresenceAvatar(
                displayName = friend.displayName,
                presetId = "preset_0",
                size = 48.dp,
                imageModel = friend.avatarUrl,
                presence = presence,
                selected = false,
                sourceTint = DiscordAccent,
            )
        },
        title = friend.displayName,
        subtitle = buildString {
            append(activity ?: presenceLabel(presence))
            trailingHint?.let {
                append(" · ")
                append(it)
            }
        },
        selected = selected,
        accent = DiscordAccent,
        trailing = if (hasUnread) {
            {
                UnreadBubble()
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

@Composable
private fun UnreadBubble() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MessagesBadge.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun SocialListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            delay(16)
            bringIntoViewRequester.bringIntoView()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, accent.copy(alpha = 0.75f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        if (trailing == null) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
fun PresenceAvatar(
    displayName: String,
    presetId: String,
    size: Dp,
    presence: SocialPresence,
    selected: Boolean,
    modifier: Modifier = Modifier,
    imageModel: String? = null,
    gameBadge: Boolean = false,
    sourceTint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.size(size + 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .clip(CircleShape)
                    .background(FocusRing.copy(alpha = 0.28f)),
            )
        }
        Box(
            modifier = Modifier
                .size(size + 6.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, FocusRing, CircleShape)
                    } else if (sourceTint != null) {
                        Modifier.border(1.5.dp, sourceTint.copy(alpha = 0.45f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                displayName = displayName,
                presetId = presetId,
                size = size,
                imageModel = imageModel,
                onClick = onClick,
                borderColor = if (selected) {
                    FocusRing.copy(alpha = 0.35f)
                } else {
                    Color.White.copy(alpha = 0.28f)
                },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFF0C1524))
                .padding(2.dp)
                .clip(CircleShape)
                .background(presenceDotColor(presence)),
        )
        if (gameBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
            )
        }
    }
}

private fun presenceDotColor(presence: SocialPresence): Color = when (presence) {
    SocialPresence.Online, SocialPresence.InGame -> OnlineGreen
    SocialPresence.Away -> AwayAmber
    SocialPresence.Busy -> BusyRose
    SocialPresence.Offline -> Color.White.copy(alpha = 0.35f)
}

fun presenceLabel(presence: SocialPresence): String = when (presence) {
    SocialPresence.Online -> "Online"
    SocialPresence.Away -> "Away"
    SocialPresence.Busy -> "Busy"
    SocialPresence.InGame -> "In-game"
    SocialPresence.Offline -> "Offline"
}

fun steamPersonaToPresence(personaState: Int, inGame: Boolean): SocialPresence = when {
    inGame -> SocialPresence.InGame
    personaState == 0 -> SocialPresence.Offline
    personaState == 2 -> SocialPresence.Busy
    personaState == 3 || personaState == 4 -> SocialPresence.Away
    personaState > 0 -> SocialPresence.Online
    else -> SocialPresence.Offline
}
