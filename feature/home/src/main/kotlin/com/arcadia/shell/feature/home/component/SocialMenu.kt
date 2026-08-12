package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.arcadia.shell.designsystem.XoraFonts
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
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.toCopy
import kotlinx.coroutines.delay

private val OnlineGreen = Color(0xFF37D6A0)
private val AwayAmber = Color(0xFFFFC24B)
private val BusyRose = Color(0xFFFF5C6C)
private val FocusRing = Color(0xFF4AE39A)
private val SteamAccent = Color(0xFF66C0F4)
private val DiscordAccent = Color(0xFF5865F2)
private val XoraAccent = Color(0xFF5B9DFF)
private val MessagesBadge = Color(0xFFFF8A4C)
private val SkyGlass = Color(0xFF7EC8E8)
private val NotificationRed = Color(0xFFFF3B30)

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
        PinnedFriendsSection(
            social = social,
            accountRows = accountRows,
            selectedRowIndex = selectedRowIndex,
            glassContent = glass.content,
            glassMuted = glass.contentMuted,
            onActivateRow = onActivateRow,
        )

        if (social.notificationsOpen) {
            NotificationCenterPanel(
                social = social,
                accountRows = accountRows,
                selectedRowIndex = selectedRowIndex,
                glassContent = glass.content,
                glassMuted = glass.contentMuted,
                onActivateRow = onActivateRow,
                onReplyDraftChange = onReplyDraftChange,
            )
        } else {
            val showSearch = social.tab != SocialMenuTab.XoraNetwork && !social.isDiscordDmOpen
            SocialTabSearchBar(
                selected = social.tab,
                onSelect = onSelectTab,
                query = social.friendSearchQuery,
                onQueryChange = onFriendSearchChange,
                muted = glass.contentMuted,
                showSearch = showSearch,
            )

            when (social.tab) {
                SocialMenuTab.Discord -> DiscordTabContent(
                    social = social,
                    accountRows = accountRows,
                    selectedRowIndex = selectedRowIndex,
                    glassMuted = glass.contentMuted,
                    onActivateRow = onActivateRow,
                    onDmDraftChange = onReplyDraftChange,
                )
                SocialMenuTab.Steam -> SteamTabContent(
                    social = social,
                    accountRows = accountRows,
                    selectedRowIndex = selectedRowIndex,
                    glassMuted = glass.contentMuted,
                    onActivateRow = onActivateRow,
                )
                SocialMenuTab.XoraNetwork -> XoraNetworkTabContent(
                    social = social,
                    accountRows = accountRows,
                    selectedRowIndex = selectedRowIndex,
                    glassMuted = glass.contentMuted,
                    onActivateRow = onActivateRow,
                    onReplyDraftChange = onReplyDraftChange,
                )
            }
        }

        Text(
            text = when {
                social.notificationsOpen -> "B closes"
                social.isDiscordDmOpen -> "A send · B back · type to message"
                social.isReplying -> "A send · B cancel reply · type on keyboard"
                social.managingCircle -> "A pin/unpin · Done to finish · L/R tabs"
                else -> "LT close · LB/RB tabs · U/D · A chat"
            },
            style = MaterialTheme.typography.labelSmall,
            color = glass.contentMuted.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PinnedFriendsSection(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassContent: Color,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PinnedFriendsHeader(
            social = social,
            accountRows = accountRows,
            selectedRowIndex = selectedRowIndex,
            glassContent = glassContent,
            onActivateRow = onActivateRow,
        )

        PinnedFriendsRow(
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
private fun PinnedFriendsHeader(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassContent: Color,
    onActivateRow: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "PINNED FRIENDS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = glassContent,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${social.circleSlotsFilled}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SkyGlass,
                )
                Text(
                    text = "/$CIRCLE_FRIEND_LIMIT",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = glassContent.copy(alpha = 0.55f),
                )
            }
        }

        val manageIndex = accountRows.indexOfFirst { it is AccountPanelRow.ManageCircle }
        val notificationsIndex = accountRows.indexOfFirst { it is AccountPanelRow.OpenNotifications }
        val badgeCount = social.messagesBadgeCount + social.recentNotifications.size
        NotificationsPill(
            label = if (social.managingCircle) "Done" else "Notifications",
            badgeCount = if (social.managingCircle) 0 else badgeCount,
            selected = if (social.managingCircle) {
                manageIndex >= 0 && manageIndex == selectedRowIndex
            } else {
                notificationsIndex >= 0 && notificationsIndex == selectedRowIndex
            },
            onClick = {
                val index = if (social.managingCircle) manageIndex else notificationsIndex
                if (index >= 0) onActivateRow(index)
            },
        )
    }
}

@Composable
private fun NotificationsPill(
    label: String,
    badgeCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.92f))
            .then(
                if (selected) Modifier.border(1.5.dp, FocusRing, shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF13202D),
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(NotificationRed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PinnedFriendsRow(
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
                    .width(64.dp)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .clickable {
                        if (rowIndex >= 0) onActivateRow(rowIndex)
                    },
            ) {
                if (member != null) {
                    PresenceAvatar(
                        displayName = member.displayName,
                        presetId = "preset_0",
                        size = if (selected) 54.dp else 48.dp,
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
            .size(54.dp)
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

/**
 * Combined platform-tab + friend-search pill. Search is hidden on the XOrA Network tab and
 * while a Discord DM is open (both replace the friend list with different content).
 */
@Composable
private fun SocialTabSearchBar(
    selected: SocialMenuTab,
    onSelect: (SocialMenuTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    muted: Color,
    showSearch: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, SkyGlass.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialMenuTab.entries.forEach { tab ->
                val active = tab == selected
                val accent = when (tab) {
                    SocialMenuTab.Discord -> DiscordAccent
                    SocialMenuTab.Steam -> SteamAccent
                    SocialMenuTab.XoraNetwork -> XoraAccent
                }
                val iconRes = when (tab) {
                    SocialMenuTab.Discord -> R.drawable.ic_brand_discord
                    SocialMenuTab.Steam -> R.drawable.ic_brand_steam
                    SocialMenuTab.XoraNetwork -> R.drawable.ic_brand_xora
                }
                val contentDescription = when (tab) {
                    SocialMenuTab.Discord -> "Discord"
                    SocialMenuTab.Steam -> "Steam"
                    SocialMenuTab.XoraNetwork -> "XOrA Network"
                }
                Box(
                    modifier = Modifier
                        .size(if (active) 38.dp else 34.dp)
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
                        modifier = Modifier.size(if (active) 20.dp else 17.dp),
                    )
                }
            }
        }

        if (showSearch) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(FocusRing),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "search friends...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = muted.copy(alpha = 0.55f),
                                )
                            }
                            inner()
                        }
                    },
                )
                Text(
                    text = "⌕",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = muted.copy(alpha = 0.7f),
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FriendsOnlineHeader(online: Int, total: Int, muted: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FRIENDS ONLINE",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$online",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SkyGlass,
            )
            Text(
                text = "/$total",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = muted.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun DiscordTabContent(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onDmDraftChange: (String) -> Unit,
) {
    if (social.discordDm.peerUserId != null) {
        DiscordDmPane(
            thread = social.discordDm,
            accountRows = accountRows,
            selectedRowIndex = selectedRowIndex,
            glassMuted = glassMuted,
            onActivateRow = onActivateRow,
            onDraftChange = onDmDraftChange,
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

        FriendsOnlineHeader(
            online = social.discord.friends.count { it.isOnline },
            total = social.discord.friends.size,
            muted = glassMuted,
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
private fun DiscordDmPane(
    thread: DiscordDmThreadUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onDraftChange: (String) -> Unit,
) {
    val closeIndex = accountRows.indexOfFirst { it is AccountPanelRow.DiscordDmClose }
    val sendIndex = accountRows.indexOfFirst { it is AccountPanelRow.DiscordDmSend }
    val sendSelected = sendIndex >= 0 && sendIndex == selectedRowIndex
    val closeSelected = closeIndex >= 0 && closeIndex == selectedRowIndex
    val listState = rememberLazyListState()
    LaunchedEffect(thread.messages.size) {
        if (thread.messages.isNotEmpty()) {
            listState.animateScrollToItem(thread.messages.lastIndex)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (closeSelected) DiscordAccent.copy(alpha = 0.22f)
                    else Color.White.copy(alpha = 0.06f),
                )
                .then(
                    if (closeSelected) {
                        Modifier.border(1.5.dp, DiscordAccent.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                )
                .clickable {
                    if (closeIndex >= 0) onActivateRow(closeIndex)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PresenceAvatar(
                displayName = thread.peerDisplayName,
                presetId = "preset_0",
                size = 36.dp,
                imageModel = thread.peerAvatarUrl,
                presence = SocialPresence.Online,
                selected = false,
                sourceTint = DiscordAccent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.peerDisplayName.ifBlank { "Discord" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "B · Back to friends",
                    style = MaterialTheme.typography.labelSmall,
                    color = glassMuted,
                )
            }
        }

        when {
            thread.loading && thread.messages.isEmpty() -> {
                Text(
                    text = "Loading messages…",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMuted,
                )
            }
            thread.messages.isEmpty() -> {
                Text(
                    text = "No messages yet — say hello",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMuted,
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(thread.messages, key = { it.messageId }) { message ->
                        DiscordDmBubble(message = message)
                    }
                }
            }
        }

        val threadError = thread.error
        if (!threadError.isNullOrBlank()) {
            Text(
                text = threadError,
                style = MaterialTheme.typography.labelSmall,
                color = BusyRose,
            )
        }

        val shape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White.copy(alpha = if (sendSelected) 0.18f else 0.08f))
                .then(
                    if (sendSelected) {
                        Modifier.border(1.5.dp, DiscordAccent.copy(alpha = 0.9f), shape)
                    } else {
                        Modifier
                    },
                )
                .clickable {
                    if (sendIndex >= 0) onActivateRow(sendIndex)
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = thread.draft,
                onValueChange = onDraftChange,
                singleLine = true,
                enabled = !thread.sending,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(DiscordAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box(modifier = Modifier.heightIn(min = 20.dp)) {
                        if (thread.draft.isEmpty()) {
                            Text(
                                text = "Message ${thread.peerDisplayName.ifBlank { "friend" }}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.45f),
                            )
                        }
                        inner()
                    }
                },
            )
            Text(
                text = if (thread.sending) "Sending…" else "A · Send",
                style = MaterialTheme.typography.labelSmall,
                color = DiscordAccent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DiscordDmBubble(message: DiscordDmMessage) {
    val mine = message.isMine
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = message.content.ifBlank { " " },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (mine) DiscordAccent.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.12f),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
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

        FriendsOnlineHeader(
            online = social.steam.onlineCount,
            total = social.steam.friends.size,
            muted = glassMuted,
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
private fun XoraNetworkTabContent(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onReplyDraftChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "XOrA Network",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = XoraFonts.Title,
            fontWeight = FontWeight.Bold,
            color = XoraAccent,
        )
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
}

/** LT notification center — recent shell notifications, then conversations. Shown in place of tabs/friends. */
@Composable
private fun NotificationCenterPanel(
    social: SocialMenuUiState,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    glassContent: Color,
    glassMuted: Color,
    onActivateRow: (Int?) -> Unit,
    onReplyDraftChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "NOTIFICATIONS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = glassContent,
        )

        if (social.recentNotifications.isEmpty()) {
            Text(
                text = "No recent notifications",
                style = MaterialTheme.typography.bodySmall,
                color = glassMuted,
            )
        } else {
            social.recentNotifications.forEach { notification ->
                NotificationHistoryRow(notification = notification)
            }
        }

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
        )
    }
}

@Composable
private fun NotificationHistoryRow(notification: ShellNotification) {
    val copy = notification.toCopy()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = copy.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SkyGlass,
            )
            Text(
                text = copy.body,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    FriendListRow(
        displayName = friend.displayName,
        avatarUrl = friend.avatarUrl,
        presence = friend.presence,
        activityLabel = friend.currentGame,
        sourceTint = SteamAccent,
        selected = selected,
        hasUnread = hasUnread,
        trailingHint = trailingHint,
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
    FriendListRow(
        displayName = friend.displayName,
        avatarUrl = friend.avatarUrl,
        presence = presence,
        activityLabel = activity,
        sourceTint = DiscordAccent,
        selected = selected,
        hasUnread = hasUnread,
        trailingHint = trailingHint,
        onClick = onClick,
    )
}

/**
 * Restyled friend row: uppercase bold name, blocky neon-green activity line, dimmed offline
 * state, thin focus-ring border when selected, and a trailing speech-bubble/unread badge.
 */
@Composable
private fun FriendListRow(
    displayName: String,
    avatarUrl: String?,
    presence: SocialPresence,
    activityLabel: String?,
    sourceTint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    hasUnread: Boolean = false,
    trailingHint: String? = null,
) {
    val offline = presence == SocialPresence.Offline
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
            .background(if (selected) FocusRing.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .then(
                if (selected) Modifier.border(1.dp, FocusRing.copy(alpha = 0.85f), shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PresenceAvatar(
            displayName = displayName,
            presetId = "preset_0",
            size = 36.dp,
            imageModel = avatarUrl,
            presence = presence,
            selected = false,
            sourceTint = sourceTint,
            modifier = if (offline) Modifier.alpha(0.45f) else Modifier,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = displayName.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = XoraFonts.Title,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    offline -> "OFFLINE"
                    !activityLabel.isNullOrBlank() -> activityLabel.uppercase()
                    else -> presenceLabel(presence).uppercase()
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = XoraFonts.Title,
                fontWeight = FontWeight.Bold,
                color = if (offline) Color.White.copy(alpha = 0.4f) else OnlineGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingHint != null) {
            Text(
                text = trailingHint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (trailingHint == "Full") BusyRose else SkyGlass,
            )
        } else {
            SpeechBubbleIcon(hasUnread = hasUnread)
        }
    }
}

/** Simple drawn speech-bubble glyph (avoids relying on an emoji font) with an unread badge. */
@Composable
private fun SpeechBubbleIcon(
    hasUnread: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(26.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val bubbleHeight = size.height * 0.7f
            val strokeWidth = size.minDimension * 0.11f
            val tint = Color.White.copy(alpha = if (hasUnread) 0.85f else 0.4f)
            drawRoundRect(
                color = tint,
                size = Size(size.width, bubbleHeight),
                cornerRadius = CornerRadius(size.minDimension * 0.28f, size.minDimension * 0.28f),
                style = Stroke(width = strokeWidth),
            )
            val tailPath = Path().apply {
                moveTo(size.width * 0.26f, bubbleHeight - strokeWidth / 2f)
                lineTo(size.width * 0.20f, size.height)
                lineTo(size.width * 0.46f, bubbleHeight - strokeWidth / 2f)
                close()
            }
            drawPath(tailPath, color = tint)
        }
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(BusyRose),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "1",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
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
