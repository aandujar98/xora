package com.arcadia.shell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.DEFAULT_NETPLAY_PORT
import com.arcadia.shell.datastore.MAX_NETPLAY_PORT
import com.arcadia.shell.datastore.MIN_NETPLAY_PORT
import com.arcadia.shell.datastore.NdsWfcServer
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.label
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationHistoryItem
import com.arcadia.shell.launcher.notifications.toCopy
import com.arcadia.shell.libretro.netplay.AzaharLobbyUi
import com.arcadia.shell.libretro.netplay.AzaharPretendoUi
import com.arcadia.shell.libretro.netplay.AzaharPublicLobbies
import com.arcadia.shell.libretro.netplay.PublicLobbyKind
import com.arcadia.shell.libretro.netplay.XoraNetplayRole
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState
import com.arcadia.shell.libretro.netplay.publicLobbyKind
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.xoranetwork.XoraFriendState
import com.arcadia.shell.xoranetwork.XoraNetworkState

enum class EmulatorMenuPane {
    None,
    Save,
    Load,
    Display,
    Netplay,
    RetroAchievements,
    Achievements,
    XoraNetwork,
    FriendActions,
    Notifications,
    Mods,
    Settings,
    Gamepad,
    Graphics,
    Audio,
    PublicLobbies,
    Pretendo,
}

data class EmulatorSaveSlotUi(
    val slot: Int,
    val occupied: Boolean,
    val subtitle: String,
)

sealed class EmulatorMenuAction {
    data object TogglePause : EmulatorMenuAction()
    data object ResetGame : EmulatorMenuAction()
    data class SaveSlot(val slot: Int) : EmulatorMenuAction()
    data class LoadSlot(val slot: Int) : EmulatorMenuAction()
    data object SetFullScreen : EmulatorMenuAction()
    data object SetNativeRatio : EmulatorMenuAction()
    data object CycleAspectMode : EmulatorMenuAction()
    data object ToggleBezel : EmulatorMenuAction()
    data object CycleInternalResolution : EmulatorMenuAction()
    data object CycleIntegerScale : EmulatorMenuAction()
    data object ToggleExpandDual : EmulatorMenuAction()
    data object ToggleNetplayEnabled : EmulatorMenuAction()
    data object ToggleNetplayOnline : EmulatorMenuAction()
    data object HostNetplay : EmulatorMenuAction()
    data object JoinNetplay : EmulatorMenuAction()
    data object HostOnlineNetplay : EmulatorMenuAction()
    data object JoinOnlineNetplay : EmulatorMenuAction()
    data object DisconnectNetplay : EmulatorMenuAction()
    data object ToggleSpectator : EmulatorMenuAction()
    data class SetJoinTarget(val address: String, val port: Int) : EmulatorMenuAction()
    data class SetJoinCode(val code: String) : EmulatorMenuAction()
    data object ClearJoinTarget : EmulatorMenuAction()
    data object ToggleRaHardcore : EmulatorMenuAction()
    data class ShowAchievement(val title: String, val description: String) : EmulatorMenuAction()
    data object CyclePreferredController : EmulatorMenuAction()
    data object ClearMappings : EmulatorMenuAction()
    data object VolumeUp : EmulatorMenuAction()
    data object VolumeDown : EmulatorMenuAction()
    data object ResetDefaults : EmulatorMenuAction()
    data object ReturnHome : EmulatorMenuAction()
    data class InviteFriendToSession(val username: String) : EmulatorMenuAction()
    data class MessageFriendComingSoon(val username: String) : EmulatorMenuAction()
    /** Opens the Player 2–4 seat picker (taken seats greyed out) for online joiners. */
    data object ChoosePlayerSeat : EmulatorMenuAction()
    /** A on a notification row — netplay invites open Accept / Decline. */
    data class OpenNotification(val id: String) : EmulatorMenuAction()
    data object ClearAllNotifications : EmulatorMenuAction()
    /** Fired when the Notifications pane opens so unread counts reset. */
    data object NotificationsSeen : EmulatorMenuAction()
    /** melonDS public WFC: Kaeru → Wiimmfi → AltWFC → Off. */
    data object CycleNdsWfc : EmulatorMenuAction()
    /** Refresh Citra/Azahar `GET {api}/lobby` rooms. */
    data object RefreshAzaharLobbies : EmulatorMenuAction()
    /** Launch installed standalone Azahar (libretro cannot join those rooms). */
    data object OpenStandaloneAzahar : EmulatorMenuAction()
    data class SelectAzaharRoom(
        val name: String,
        val game: String,
        val ip: String = "",
        val port: Int = 0,
        val hasPassword: Boolean = false,
    ) : EmulatorMenuAction()
    data object TogglePretendoPrep : EmulatorMenuAction()
    data object RefreshPretendoStatus : EmulatorMenuAction()
}

private data class MenuRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: XmbIcon,
    val pane: EmulatorMenuPane? = null,
    val action: EmulatorMenuAction? = null,
)

/**
 * Azahar-style in-game side menu. The Compose host is wrap-content and fully opaque so it never
 * tints the live framebuffer sitting to its right.
 */
@Composable
fun XoraEmulatorSideMenu(
    gameTitle: String,
    paused: Boolean,
    hardcore: Boolean,
    settings: XoraEmulatorSettings,
    saveSlots: List<EmulatorSaveSlotUi>,
    netplay: XoraNetplayUiState,
    joinAddress: String,
    joinPort: Int = DEFAULT_NETPLAY_PORT,
    joinCode: String = "",
    message: String?,
    onAction: (EmulatorMenuAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    network: XoraNetworkState = XoraNetworkState(),
    achievements: List<RaAchievement> = emptyList(),
    achievementSummary: String = "",
    raStatus: String? = null,
    notifications: List<ShellNotificationHistoryItem> = emptyList(),
    notificationUnread: Int = 0,
    platformId: String = "",
    publicLobbies: AzaharLobbyUi = AzaharLobbyUi(),
    pretendo: AzaharPretendoUi = AzaharPretendoUi(),
) {
    var rootIndex by remember { mutableIntStateOf(0) }
    var pane by remember { mutableStateOf(EmulatorMenuPane.None) }
    var paneIndex by remember { mutableIntStateOf(0) }
    var friendActionUsername by remember { mutableStateOf("") }

    LaunchedEffect(pane) {
        if (pane == EmulatorMenuPane.Notifications) {
            onAction(EmulatorMenuAction.NotificationsSeen)
        }
        if (pane == EmulatorMenuPane.PublicLobbies &&
            publicLobbyKind(platformId) == PublicLobbyKind.AzaharRooms
        ) {
            onAction(EmulatorMenuAction.RefreshAzaharLobbies)
        }
        if (pane == EmulatorMenuPane.Pretendo) {
            onAction(EmulatorMenuAction.RefreshPretendoStatus)
        }
    }

    val rootRows = remember(
        paused,
        settings.netplayEnabled,
        settings.netplayUseRelay,
        gameTitle,
        hardcore,
        settings.aspectMode,
        network.signedIn,
        network.restoring,
        network.selfOnline,
        network.account?.username,
        achievementSummary,
        notificationUnread,
        notifications.size,
    ) {
        listOf(
            MenuRow(
                id = "pause",
                title = if (paused) "Resume emulator" else "Pause emulator",
                subtitle = gameTitle,
                icon = if (paused) XmbIcon.Play else XmbIcon.Pause,
                action = EmulatorMenuAction.TogglePause,
            ),
            MenuRow(
                id = "save",
                title = "Save state",
                subtitle = if (hardcore) "Hardcore — disabled" else "Slots 0–9",
                icon = XmbIcon.Folder,
                pane = EmulatorMenuPane.Save,
            ),
            MenuRow(
                id = "load",
                title = "Load state",
                subtitle = if (hardcore) "Hardcore — disabled" else "Slots 0–9",
                icon = XmbIcon.Folder,
                pane = EmulatorMenuPane.Load,
            ),
            MenuRow(
                id = "display",
                title = "Display",
                subtitle = settings.aspectMode.label(),
                icon = XmbIcon.Display,
                pane = EmulatorMenuPane.Display,
            ),
            MenuRow(
                id = "netplay",
                title = "Netplay",
                subtitle = when {
                    !settings.netplayEnabled -> "Off"
                    settings.netplayUseRelay -> "On · Online"
                    else -> "On · Local Wireless"
                },
                icon = XmbIcon.Network,
                pane = EmulatorMenuPane.Netplay,
            ),
            MenuRow(
                id = "ra",
                title = "RetroAchievements",
                subtitle = when {
                    hardcore -> "Hardcore · ${achievementSummary.ifBlank { "this game" }}"
                    else -> "Softcore · ${achievementSummary.ifBlank { "this game" }}"
                },
                icon = XmbIcon.Trophy,
                pane = EmulatorMenuPane.RetroAchievements,
            ),
            MenuRow(
                id = "xora-net",
                title = "XOrA Network",
                subtitle = networkRootSubtitle(network, gameTitle),
                icon = XmbIcon.Xora,
                pane = EmulatorMenuPane.XoraNetwork,
            ),
            MenuRow(
                id = "notifs",
                title = "Notifications",
                subtitle = when {
                    notificationUnread > 0 -> "$notificationUnread unread · Invites land here"
                    notifications.isNotEmpty() -> "${notifications.size} recent"
                    else -> "Invites and alerts land here"
                },
                icon = XmbIcon.Notifications,
                pane = EmulatorMenuPane.Notifications,
            ),
            MenuRow(
                id = "mods",
                title = "Mods",
                subtitle = "Coming soon",
                icon = XmbIcon.Store,
                pane = EmulatorMenuPane.Mods,
            ),
            MenuRow(
                id = "settings",
                title = "Settings",
                subtitle = "Gamepad · Graphics · Audio",
                icon = XmbIcon.Settings,
                pane = EmulatorMenuPane.Settings,
            ),
            MenuRow(
                id = "reset",
                title = "Reset game",
                subtitle = "Restart $gameTitle from the beginning",
                icon = XmbIcon.Repeat,
                action = EmulatorMenuAction.ResetGame,
            ),
            MenuRow(
                id = "home",
                title = "Return to XOrA Home",
                icon = XmbIcon.Games,
                action = EmulatorMenuAction.ReturnHome,
            ),
        )
    }
    val paneRows = paneRows(
        pane = pane,
        settings = settings,
        saveSlots = saveSlots,
        netplay = netplay,
        joinAddress = joinAddress,
        joinPort = joinPort,
        joinCode = joinCode,
        hardcore = hardcore,
        network = network,
        achievements = achievements,
        achievementSummary = achievementSummary,
        raStatus = raStatus,
        gameTitle = gameTitle,
        friendUsername = friendActionUsername,
        notifications = notifications,
        platformId = platformId,
        publicLobbies = publicLobbies,
        pretendo = pretendo,
    )
    val paneFocus = paneIndex.coerceIn(0, (paneRows.size - 1).coerceAtLeast(0))
    val rootFocus = rootIndex.coerceIn(0, rootRows.lastIndex)
    val rootListState = rememberLazyListState()
    val paneListState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val ipFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    var ipDraft by remember(joinAddress) { mutableStateOf(joinAddress) }
    var portDraft by remember(joinPort) { mutableStateOf(joinPort.toString()) }
    var codeDraft by remember(joinCode) { mutableStateOf(joinCode) }

    fun parsedJoinPort(): Int =
        portDraft.toIntOrNull()?.coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT)
            ?: DEFAULT_NETPLAY_PORT

    fun commitJoinDrafts() {
        onAction(EmulatorMenuAction.SetJoinTarget(ipDraft.trim(), parsedJoinPort()))
    }

    fun commitJoinCode() {
        onAction(EmulatorMenuAction.SetJoinCode(codeDraft))
    }

    LaunchedEffect(rootFocus, pane) {
        if (pane == EmulatorMenuPane.None && rootRows.isNotEmpty()) {
            rootListState.animateScrollToItem(rootFocus)
        }
    }
    LaunchedEffect(paneFocus, pane, paneRows.size) {
        if (pane != EmulatorMenuPane.None && paneRows.isNotEmpty()) {
            paneListState.animateScrollToItem(paneFocus)
        }
    }

    fun activateRoot() {
        val row = rootRows.getOrNull(rootFocus) ?: return
        when {
            row.pane != null -> {
                pane = row.pane
                paneIndex = 0
            }
            row.action != null -> onAction(row.action)
        }
    }

    fun activatePaneAt(index: Int) {
        paneIndex = index
        val row = paneRows.getOrNull(index) ?: return
        when (row.id) {
            "np-ip" -> {
                runCatching {
                    ipFocus.requestFocus()
                    keyboard?.show()
                }
            }
            "np-port" -> {
                runCatching {
                    portFocus.requestFocus()
                    keyboard?.show()
                }
            }
            "np-join" -> {
                if (settings.netplayUseRelay) {
                    commitJoinCode()
                    onAction(EmulatorMenuAction.JoinOnlineNetplay)
                } else {
                    commitJoinDrafts()
                    onAction(EmulatorMenuAction.JoinNetplay)
                }
            }
            "np-code" -> {
                runCatching {
                    codeFocus.requestFocus()
                    keyboard?.show()
                }
            }
            "np-clear" -> {
                ipDraft = ""
                portDraft = DEFAULT_NETPLAY_PORT.toString()
                codeDraft = ""
                onAction(EmulatorMenuAction.ClearJoinTarget)
            }
            else -> when {
                row.id.startsWith("xn-friend-") -> {
                    friendActionUsername = row.id.removePrefix("xn-friend-")
                    pane = EmulatorMenuPane.FriendActions
                    paneIndex = 0
                }
                row.pane != null -> {
                    pane = row.pane
                    paneIndex = 0
                }
                row.action != null -> onAction(row.action)
            }
        }
    }

    fun activatePane() {
        activatePaneAt(paneFocus)
    }

    fun back() {
        when (pane) {
            EmulatorMenuPane.Gamepad,
            EmulatorMenuPane.Graphics,
            EmulatorMenuPane.Audio,
            -> {
                pane = EmulatorMenuPane.Settings
                paneIndex = 0
            }
            EmulatorMenuPane.Achievements -> {
                pane = EmulatorMenuPane.RetroAchievements
                paneIndex = 0
            }
            EmulatorMenuPane.FriendActions -> {
                pane = EmulatorMenuPane.XoraNetwork
                paneIndex = 0
            }
            EmulatorMenuPane.PublicLobbies -> {
                pane = EmulatorMenuPane.Netplay
                paneIndex = 0
            }
            EmulatorMenuPane.Pretendo -> {
                pane = EmulatorMenuPane.Netplay
                paneIndex = 0
            }
            EmulatorMenuPane.None -> onDismiss()
            else -> {
                pane = EmulatorMenuPane.None
                paneIndex = 0
            }
        }
    }

    InGameMenuNavBridge(
        rootCount = rootRows.size,
        paneCount = paneRows.size,
        paneOpen = pane != EmulatorMenuPane.None,
        onMoveRoot = { delta ->
            if (pane == EmulatorMenuPane.None) {
                rootIndex = (rootFocus + delta).mod(rootRows.size)
            }
        },
        onMovePane = { delta ->
            if (pane != EmulatorMenuPane.None && paneRows.isNotEmpty()) {
                paneIndex = (paneFocus + delta).mod(paneRows.size)
            }
        },
        onOpenPane = {
            if (pane == EmulatorMenuPane.None) activateRoot()
        },
        onConfirm = {
            if (pane == EmulatorMenuPane.None) activateRoot() else activatePane()
        },
        onCancel = { back() },
    )

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.Transparent),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .width(SIDEBAR_WIDTH)
                .fillMaxHeight()
                .background(SidebarInk)
                .padding(top = 28.dp, bottom = 20.dp),
        ) {
            Text(
                text = "XOrA EMULATOR",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(start = 22.dp, end = 16.dp, bottom = 18.dp),
            )
            LazyColumn(
                state = rootListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                itemsIndexed(rootRows, key = { _, row -> row.id }) { index, row ->
                    SideMenuRow(
                        row = row,
                        selected = index == rootFocus && pane == EmulatorMenuPane.None,
                        dimmed = pane != EmulatorMenuPane.None && index != rootFocus,
                        onClick = {
                            rootIndex = index
                            activateRoot()
                        },
                    )
                }
            }
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "B back · A confirm · scroll",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 22.dp, top = 8.dp),
            )
        }

        if (pane != EmulatorMenuPane.None) {
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, top = 48.dp, end = 12.dp, bottom = 20.dp)
                    .width(PANEL_WIDTH)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PanelInk)
                    .padding(vertical = 14.dp),
            ) {
                Text(
                    text = paneTitle(
                        pane,
                        friendDisplayName(network, friendActionUsername),
                        platformId,
                    ),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                )
                LazyColumn(
                    state = paneListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    itemsIndexed(paneRows, key = { _, row -> row.id }) { index, row ->
                        val selected = index == paneFocus
                        when (row.id) {
                            "np-ip" -> JoinTargetField(
                                label = "Join IP",
                                value = ipDraft,
                                selected = selected,
                                placeholder = "192.168.1.10",
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                                focusRequester = ipFocus,
                                onValueChange = { ipDraft = it.filter { ch ->
                                    ch.isLetterOrDigit() || ch in ".:-[]"
                                }.take(128) },
                                onCommit = { commitJoinDrafts() },
                                onNext = {
                                    portFocus.requestFocus()
                                    keyboard?.show()
                                },
                                onClick = { activatePaneAt(index) },
                            )
                            "np-port" -> JoinTargetField(
                                label = "Join port",
                                value = portDraft,
                                selected = selected,
                                placeholder = DEFAULT_NETPLAY_PORT.toString(),
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                focusRequester = portFocus,
                                onValueChange = { portDraft = it.filter { ch -> ch.isDigit() }.take(5) },
                                onCommit = { commitJoinDrafts() },
                                onClick = { activatePaneAt(index) },
                            )
                            "np-code" -> JoinTargetField(
                                label = "Session code",
                                value = codeDraft,
                                selected = selected,
                                placeholder = "K7M2QX",
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done,
                                focusRequester = codeFocus,
                                onValueChange = {
                                    codeDraft = XoraNetplayProtocol.filterSessionCodeDraft(it)
                                },
                                onCommit = { commitJoinCode() },
                                onClick = { activatePaneAt(index) },
                            )
                            else -> SideMenuRow(
                                row = row,
                                selected = selected,
                                compact = true,
                                subtitleLines = if (pane == EmulatorMenuPane.Achievements) 2 else 1,
                                onClick = { activatePaneAt(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideMenuRow(
    row: MenuRow,
    selected: Boolean,
    onClick: () -> Unit,
    dimmed: Boolean = false,
    compact: Boolean = false,
    subtitleLines: Int = 1,
) {
    val bg = when {
        selected -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = remember(row.id) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Accent else Color.Transparent),
        )
        XmbVectorIcon(
            icon = row.icon,
            tint = if (dimmed) Color.White.copy(alpha = 0.35f) else Color.White,
            size = 22.dp,
            glass = false,
            castShadow = false,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = Color.White.copy(alpha = if (dimmed) 0.4f else 1f),
                fontSize = if (selected) 16.sp else 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!row.subtitle.isNullOrBlank()) {
                Text(
                    text = row.subtitle,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.pane != null) {
            Text(
                text = "›",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun InGameMenuNavBridge(
    rootCount: Int,
    paneCount: Int,
    paneOpen: Boolean,
    onMoveRoot: (Int) -> Unit,
    onMovePane: (Int) -> Unit,
    onOpenPane: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val controller = LocalInGameXmbController.current
    LaunchedEffect(
        rootCount,
        paneCount,
        paneOpen,
        onMoveRoot,
        onMovePane,
        onOpenPane,
        onConfirm,
        onCancel,
    ) {
        controller?.moveCategory = { delta ->
            if (delta > 0 && !paneOpen) onOpenPane()
            if (delta < 0 && paneOpen) onCancel()
        }
        controller?.moveItem = { delta ->
            if (paneOpen) onMovePane(delta) else onMoveRoot(delta)
        }
        controller?.confirm = onConfirm
        controller?.cancel = onCancel
    }
}

private fun networkRootSubtitle(network: XoraNetworkState, gameTitle: String): String = when {
    !network.configured -> "Not configured"
    network.restoring -> "Signing in…"
    !network.signedIn -> "Sign in from the launcher"
    network.selfOnline -> "Online · Playing $gameTitle"
    else -> "Signed in as ${network.account?.username.orEmpty().ifBlank { "you" }}"
}

private fun paneTitle(
    pane: EmulatorMenuPane,
    friendName: String = "",
    platformId: String = "",
): String = when (pane) {
    EmulatorMenuPane.Save -> "Save state"
    EmulatorMenuPane.Load -> "Load state"
    EmulatorMenuPane.Display -> "Display"
    EmulatorMenuPane.Netplay -> "Netplay"
    EmulatorMenuPane.RetroAchievements -> "RetroAchievements"
    EmulatorMenuPane.Achievements -> "This game"
    EmulatorMenuPane.XoraNetwork -> "XOrA Network"
    EmulatorMenuPane.FriendActions -> friendName.ifBlank { "Friend" }
    EmulatorMenuPane.Notifications -> "Notifications"
    EmulatorMenuPane.Mods -> "Mods"
    EmulatorMenuPane.Settings -> "Settings"
    EmulatorMenuPane.Gamepad -> "Gamepad"
    EmulatorMenuPane.Graphics -> "Graphics"
    EmulatorMenuPane.Audio -> "Audio"
    EmulatorMenuPane.PublicLobbies -> when (publicLobbyKind(platformId)) {
        PublicLobbyKind.NdsWfc -> "Nintendo WFC"
        else -> "3DS public lobbies"
    }
    EmulatorMenuPane.Pretendo -> "Pretendo"
    EmulatorMenuPane.None -> ""
}

private fun friendDisplayName(network: XoraNetworkState, username: String): String {
    val friend = network.acceptedFriends.firstOrNull { it.username.equals(username, ignoreCase = true) }
    return friend?.displayName?.ifBlank { friend.username } ?: username
}

private fun paneRows(
    pane: EmulatorMenuPane,
    settings: XoraEmulatorSettings,
    saveSlots: List<EmulatorSaveSlotUi>,
    netplay: XoraNetplayUiState,
    joinAddress: String,
    joinPort: Int,
    joinCode: String,
    hardcore: Boolean,
    network: XoraNetworkState,
    achievements: List<RaAchievement>,
    achievementSummary: String,
    raStatus: String?,
    gameTitle: String,
    friendUsername: String = "",
    notifications: List<ShellNotificationHistoryItem> = emptyList(),
    platformId: String = "",
    publicLobbies: AzaharLobbyUi = AzaharLobbyUi(),
    pretendo: AzaharPretendoUi = AzaharPretendoUi(),
): List<MenuRow> = when (pane) {
    EmulatorMenuPane.None -> emptyList()
    EmulatorMenuPane.Save -> saveSlots.map { slot ->
        MenuRow(
            id = "save-${slot.slot}",
            title = "Slot ${slot.slot}",
            subtitle = if (hardcore) "Hardcore — disabled" else slot.subtitle,
            icon = XmbIcon.Folder,
            action = EmulatorMenuAction.SaveSlot(slot.slot),
        )
    }
    EmulatorMenuPane.Load -> saveSlots.map { slot ->
        MenuRow(
            id = "load-${slot.slot}",
            title = "Slot ${slot.slot}",
            subtitle = if (hardcore) "Hardcore — disabled" else slot.subtitle,
            icon = XmbIcon.Folder,
            action = EmulatorMenuAction.LoadSlot(slot.slot),
        )
    }
    EmulatorMenuPane.Display -> listOf(
        MenuRow(
            id = "aspect",
            title = "Aspect ratio",
            subtitle = "${settings.aspectMode.label()} · A cycles Auto, 4:3, 16:9, 1:1…",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.CycleAspectMode,
        ),
        MenuRow(
            id = "full",
            title = "Full screen",
            subtitle = if (settings.aspectMode == XoraAspectMode.Stretch) "On" else "Stretch to fill",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.SetFullScreen,
        ),
        MenuRow(
            id = "native",
            title = "Auto (core)",
            subtitle = if (settings.aspectMode == XoraAspectMode.Core) "On" else "Keep framebuffer aspect",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.SetNativeRatio,
        ),
        MenuRow(
            id = "bezel",
            title = if (settings.bezelsEnabled) "NSO bezel on" else "No NSO bezel",
            subtitle = "Per-core overlay around the game",
            icon = XmbIcon.Emulator,
            action = EmulatorMenuAction.ToggleBezel,
        ),
    )
    EmulatorMenuPane.Netplay -> {
        val online = settings.netplayUseRelay
        val modeRows = if (online) {
            listOf(
                MenuRow(
                    id = "np-host",
                    title = "Host session",
                    subtitle = when {
                        !network.signedIn -> "Sign in to XOrA Network first"
                        netplay.online && netplay.sessionCode.isNotBlank() &&
                            netplay.role == XoraNetplayRole.Host ->
                            "Code ${netplay.sessionCode} — share this"
                        else -> "Share a 6-character code"
                    },
                    icon = XmbIcon.Play,
                    action = EmulatorMenuAction.HostOnlineNetplay,
                ),
                MenuRow(
                    id = "np-join",
                    title = "Join session",
                    subtitle = when {
                        !network.signedIn -> "Sign in to XOrA Network first"
                        joinCode.isBlank() -> "Type the host's code below"
                        else -> "Code $joinCode"
                    },
                    icon = XmbIcon.Friends,
                    action = EmulatorMenuAction.JoinOnlineNetplay,
                ),
                MenuRow(
                    id = "np-code",
                    title = "Session code",
                    subtitle = joinCode.ifBlank { "6 characters from the host" },
                    icon = XmbIcon.Network,
                ),
                MenuRow(
                    id = "np-clear",
                    title = "Clear session code",
                    subtitle = "Erase the code to join another lobby",
                    icon = XmbIcon.Settings,
                    action = EmulatorMenuAction.ClearJoinTarget,
                ),
            )
        } else {
            listOf(
                MenuRow(
                    id = "np-host",
                    title = "Host session",
                    subtitle = netplay.localAddresses.take(2)
                        .joinToString(" · ") { "$it:${settings.netplayPort}" }
                        .ifBlank { "Port ${settings.netplayPort}" }
                        .let { "$it · same Wi‑Fi" },
                    icon = XmbIcon.Play,
                    action = EmulatorMenuAction.HostNetplay,
                ),
                MenuRow(
                    id = "np-join",
                    title = "Join session",
                    subtitle = if (joinAddress.isBlank()) {
                        "Type IP and port below"
                    } else {
                        "$joinAddress:$joinPort"
                    },
                    icon = XmbIcon.Friends,
                    action = EmulatorMenuAction.JoinNetplay,
                ),
                MenuRow(
                    id = "np-ip",
                    title = "Join IP",
                    subtitle = joinAddress.ifBlank { "Host IP or hostname" },
                    icon = XmbIcon.Network,
                ),
                MenuRow(
                    id = "np-port",
                    title = "Join port",
                    subtitle = joinPort.toString(),
                    icon = XmbIcon.Network,
                ),
                MenuRow(
                    id = "np-clear",
                    title = "Clear join target",
                    subtitle = "Erase IP and reset port",
                    icon = XmbIcon.Settings,
                    action = EmulatorMenuAction.ClearJoinTarget,
                ),
            )
        }
        listOf(
            MenuRow(
                id = "np-enable",
                title = if (settings.netplayEnabled) "Netplay on" else "Netplay off",
                subtitle = "Turns hardcore off when enabled",
                icon = XmbIcon.Network,
                action = EmulatorMenuAction.ToggleNetplayEnabled,
            ),
            MenuRow(
                id = "np-mode",
                title = if (online) "Online" else "Local Wireless",
                subtitle = when {
                    !network.signedIn -> "XOrA Network account required for Online"
                    online -> "XOrA Network · 6-character code"
                    else -> "Same Wi‑Fi · IP and port"
                },
                icon = XmbIcon.Network,
                action = EmulatorMenuAction.ToggleNetplayOnline,
            ),
        ) + modeRows + publicLobbyNetplayRows(platformId, settings, publicLobbies, pretendo) + buildList {
            add(
                MenuRow(
                    id = "np-spec",
                    title = if (settings.netplaySpectator) "Spectator on" else "Spectator off",
                    subtitle = "Join without sending input",
                    icon = XmbIcon.User,
                    action = EmulatorMenuAction.ToggleSpectator,
                ),
            )
            if (netplay.linked && netplay.playerSlot >= 1) {
                val hostName = netplay.playerNames[1].orEmpty()
                add(
                    MenuRow(
                        id = "np-slot",
                        title = "You are Player ${netplay.playerSlot}",
                        subtitle = buildString {
                            append(if (hostName.isBlank()) "Host is Player 1" else "Host: $hostName (P1)")
                            append(" · ${netplay.playerCount} ")
                            append(if (netplay.playerCount == 1) "player connected" else "players connected")
                        },
                        icon = XmbIcon.GamePad,
                    ),
                )
                netplay.playerNames.entries
                    .filter { it.key != 1 }
                    .sortedBy { it.key }
                    .forEach { (slot, name) ->
                        add(
                            MenuRow(
                                id = "np-player-$slot",
                                title = "Player $slot · $name",
                                subtitle = if (slot == netplay.playerSlot) "You" else "In session",
                                icon = XmbIcon.User,
                            ),
                        )
                    }
                if (netplay.online && netplay.playerSlot >= 2) {
                    add(
                        MenuRow(
                            id = "np-seat",
                            title = "Choose your player",
                            subtitle = "Pick Player 2–4 · taken seats are greyed out",
                            icon = XmbIcon.GamePad,
                            action = EmulatorMenuAction.ChoosePlayerSeat,
                        ),
                    )
                }
            }
            add(
                MenuRow(
                    id = "np-status",
                    title = netplay.status,
                    subtitle = netplay.error ?: netplay.peerName.ifBlank { "Not connected" },
                    icon = XmbIcon.Notifications,
                ),
            )
            add(
                MenuRow(
                    id = "np-disc",
                    title = "Disconnect",
                    subtitle = if (netplay.linked) "Drop the current session" else "No session",
                    icon = XmbIcon.Settings,
                    action = EmulatorMenuAction.DisconnectNetplay,
                ),
            )
        }
    }
    EmulatorMenuPane.RetroAchievements -> listOf(
        MenuRow(
            id = "ra-hardcore",
            title = if (hardcore) "Hardcore on" else "Hardcore off",
            subtitle = if (hardcore) {
                "Save states off · A toggles"
            } else {
                "Softcore · A toggles"
            },
            icon = XmbIcon.Trophy,
            action = EmulatorMenuAction.ToggleRaHardcore,
        ),
        MenuRow(
            id = "ra-list",
            title = "This game's achievements",
            subtitle = achievementSummary.ifBlank { gameTitle },
            icon = XmbIcon.Trophy,
            pane = EmulatorMenuPane.Achievements,
        ),
        MenuRow(
            id = "ra-status",
            title = "Session",
            subtitle = raStatus?.removePrefix("RA: ")?.ifBlank { "Idle" } ?: "Idle",
            icon = XmbIcon.Notifications,
        ),
    )
    EmulatorMenuPane.Achievements -> if (achievements.isEmpty()) {
        listOf(
            MenuRow(
                id = "ra-empty",
                title = "No achievements loaded",
                subtitle = raStatus?.removePrefix("RA: ")
                    ?: achievementSummary.ifBlank { "Sign in under Settings → RetroAchievements" },
                icon = XmbIcon.Trophy,
            ),
        )
    } else {
        achievements.map { cheevo ->
            val earned = if (hardcore) cheevo.earnedHardcore else cheevo.earned
            MenuRow(
                id = "cheevo-${cheevo.id}",
                title = if (earned) "✓ ${cheevo.title}" else cheevo.title,
                subtitle = "${cheevo.points} pts · ${cheevo.description}",
                icon = XmbIcon.Trophy,
                action = EmulatorMenuAction.ShowAchievement(cheevo.title, cheevo.description),
            )
        }
    }
    EmulatorMenuPane.XoraNetwork -> buildList {
        add(
            MenuRow(
                id = "xn-status",
                title = when {
                    !network.configured -> "Not configured"
                    network.restoring -> "Signing in…"
                    network.signedIn -> "Signed in as ${network.account?.username.orEmpty()}"
                    else -> "Not signed in"
                },
                subtitle = when {
                    !network.configured -> "Add the XOrA Network key in the build"
                    network.signedIn && network.selfOnline -> "Playing $gameTitle"
                    network.signedIn -> "Using your launcher session"
                    else -> "Sign in from Dashboard in the launcher"
                },
                icon = XmbIcon.Xora,
            ),
        )
        if (network.signedIn) {
            add(
                MenuRow(
                    id = "xn-friends",
                    title = "${network.onlineFriendCount} friends online",
                    subtitle = "${network.acceptedFriends.size} friends",
                    icon = XmbIcon.Friends,
                ),
            )
            network.acceptedFriends.forEach { friend ->
                add(
                    MenuRow(
                        id = "xn-friend-${friend.username}",
                        title = friend.displayName.ifBlank { friend.username },
                        subtitle = when {
                            friend.online && friend.status.isNotBlank() -> friend.status
                            friend.online -> "Online"
                            friend.state == XoraFriendState.Friend -> "Offline"
                            else -> friend.state.name
                        },
                        icon = XmbIcon.User,
                    ),
                )
            }
        }
    }
    EmulatorMenuPane.FriendActions -> {
        val name = friendDisplayName(network, friendUsername)
        listOf(
            MenuRow(
                id = "xn-invite",
                title = "Invite Friend to Session",
                subtitle = "Hosts online and sends $name the code",
                icon = XmbIcon.Play,
                action = EmulatorMenuAction.InviteFriendToSession(friendUsername),
            ),
            MenuRow(
                id = "xn-message",
                title = "Message",
                subtitle = "Coming soon",
                icon = XmbIcon.Notifications,
                action = EmulatorMenuAction.MessageFriendComingSoon(friendUsername),
            ),
        )
    }
    EmulatorMenuPane.Notifications -> buildList {
        if (notifications.isEmpty()) {
            add(
                MenuRow(
                    id = "ntf-empty",
                    title = "No notifications",
                    subtitle = "Netplay invites and alerts appear here",
                    icon = XmbIcon.Notifications,
                ),
            )
        } else {
            add(
                MenuRow(
                    id = "ntf-clear",
                    title = "Clear all notifications",
                    subtitle = "${notifications.size} total",
                    icon = XmbIcon.Settings,
                    action = EmulatorMenuAction.ClearAllNotifications,
                ),
            )
            notifications.forEach { item ->
                val copy = item.notification.toCopy()
                val invite = item.notification is ShellNotification.XoraNetplayInvite
                add(
                    MenuRow(
                        id = "ntf-${item.notification.id}",
                        title = copy.body.ifBlank { copy.category },
                        subtitle = if (invite) {
                            "A · Accept or decline"
                        } else {
                            listOf(copy.category, copy.subtitle)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "A clears this notification" }
                        },
                        icon = if (invite) XmbIcon.Play else XmbIcon.Notifications,
                        action = EmulatorMenuAction.OpenNotification(item.notification.id),
                    ),
                )
            }
        }
    }
    EmulatorMenuPane.Mods -> listOf(
        MenuRow(
            id = "mods-soon",
            title = "Coming soon",
            subtitle = "Cheat / texture / ROM hacks will land here",
            icon = XmbIcon.Store,
        ),
    )
    EmulatorMenuPane.Settings -> listOf(
        MenuRow(
            id = "pad",
            title = "Gamepad settings",
            subtitle = settings.preferredControllerName.ifBlank { "Any controller" },
            icon = XmbIcon.GamePad,
            pane = EmulatorMenuPane.Gamepad,
        ),
        MenuRow(
            id = "gfx",
            title = "Graphics settings",
            subtitle = settings.aspectMode.label(),
            icon = XmbIcon.Display,
            pane = EmulatorMenuPane.Graphics,
        ),
        MenuRow(
            id = "aud",
            title = "Audio",
            subtitle = "${(settings.audioVolume * 100f).toInt()}%",
            icon = XmbIcon.Sound,
            pane = EmulatorMenuPane.Audio,
        ),
        MenuRow(
            id = "reset",
            title = "Reset to default",
            subtitle = "Display, audio & gamepad",
            icon = XmbIcon.System,
            action = EmulatorMenuAction.ResetDefaults,
        ),
    )
    EmulatorMenuPane.Gamepad -> listOf(
        MenuRow(
            id = "ctrl",
            title = "Preferred controller",
            subtitle = settings.preferredControllerName.ifBlank { "Any controller" } + " · A cycles",
            icon = XmbIcon.GamePad,
            action = EmulatorMenuAction.CyclePreferredController,
        ),
        MenuRow(
            id = "map",
            title = "Button mappings",
            subtitle = if (settings.buttonMappings.isEmpty()) {
                "Default"
            } else {
                "${settings.buttonMappings.size} custom · A clears"
            },
            icon = XmbIcon.GamePad,
            action = EmulatorMenuAction.ClearMappings,
        ),
    )
    EmulatorMenuPane.Graphics -> listOf(
        MenuRow(
            id = "g-aspect",
            title = "Aspect ratio",
            subtitle = "${settings.aspectMode.label()} · A cycles",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.CycleAspectMode,
        ),
        MenuRow(
            id = "g-full",
            title = "Full screen",
            subtitle = settings.aspectMode.label(),
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.SetFullScreen,
        ),
        MenuRow(
            id = "g-native",
            title = "Auto (core)",
            subtitle = "Framebuffer aspect",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.SetNativeRatio,
        ),
        MenuRow(
            id = "g-int",
            title = "Integer scale",
            subtitle = if (settings.integerScale == 0) "Auto" else "${settings.integerScale}×",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.CycleIntegerScale,
        ),
        MenuRow(
            id = "g-res",
            title = "Internal resolution",
            subtitle = settings.internalResolution.label(),
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.CycleInternalResolution,
        ),
        MenuRow(
            id = "g-bezel",
            title = if (settings.bezelsEnabled) "NSO bezel on" else "No NSO bezel",
            icon = XmbIcon.Emulator,
            action = EmulatorMenuAction.ToggleBezel,
        ),
        MenuRow(
            id = "g-dual",
            title = "Expand dual display",
            subtitle = if (settings.expandDualDisplay) {
                "On · each DS/3DS screen fills a panel"
            } else {
                "Off · both screens on this display"
            },
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.ToggleExpandDual,
        ),
    )
    EmulatorMenuPane.Audio -> listOf(
        MenuRow(
            id = "vol",
            title = "Volume up",
            subtitle = "${(settings.audioVolume * 100f).toInt()}%",
            icon = XmbIcon.Sound,
            action = EmulatorMenuAction.VolumeUp,
        ),
        MenuRow(
            id = "vol-down",
            title = "Volume down",
            subtitle = "−10%",
            icon = XmbIcon.Sound,
            action = EmulatorMenuAction.VolumeDown,
        ),
    )
    EmulatorMenuPane.PublicLobbies -> publicLobbyPaneRows(
        platformId = platformId,
        settings = settings,
        publicLobbies = publicLobbies,
        netplay = netplay,
        joinCode = joinCode,
        network = network,
    )
    EmulatorMenuPane.Pretendo -> pretendoPaneRows(pretendo)
}

private fun publicLobbyNetplayRows(
    platformId: String,
    settings: XoraEmulatorSettings,
    publicLobbies: AzaharLobbyUi,
    pretendo: AzaharPretendoUi = AzaharPretendoUi(),
): List<MenuRow> = when (publicLobbyKind(platformId)) {
    PublicLobbyKind.NdsWfc -> listOf(
        MenuRow(
            id = "np-wfc",
            title = "Public WFC · ${settings.ndsWfcServer.label()}",
            subtitle = "A cycles Kaeru / Wiimmfi / AltWFC / Off · then open " +
                "Nintendo Wi-Fi Connection in the game",
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.CycleNdsWfc,
        ),
        MenuRow(
            id = "np-lobbies",
            title = "Public lobbies",
            subtitle = "Nintendo WFC matchmaking is inside the game, not XOrA Host/Join",
            icon = XmbIcon.Friends,
            pane = EmulatorMenuPane.PublicLobbies,
        ),
    )
    PublicLobbyKind.AzaharRooms -> listOf(
        MenuRow(
            id = "np-lobbies",
            title = "XOrA 3DS lobbies",
            subtitle = when {
                publicLobbies.loading -> "Refreshing Azahar rooms…"
                publicLobbies.rooms.isNotEmpty() ->
                    "${publicLobbies.rooms.size} Azahar rooms with IPs · plus XOrA Online codes"
                publicLobbies.status.isNotBlank() -> publicLobbies.status
                else -> "XOrA Online codes · Azahar rooms from the community registry"
            },
            icon = XmbIcon.Friends,
            pane = EmulatorMenuPane.PublicLobbies,
        ),
        MenuRow(
            id = "np-pretendo",
            title = "Pretendo · not in XOrA",
            subtitle = pretendo.overlaySubtitle(),
            icon = XmbIcon.Network,
            pane = EmulatorMenuPane.Pretendo,
        ),
    )
    PublicLobbyKind.None -> emptyList()
}

private fun publicLobbyPaneRows(
    platformId: String,
    settings: XoraEmulatorSettings,
    publicLobbies: AzaharLobbyUi,
    netplay: XoraNetplayUiState = XoraNetplayUiState(),
    joinCode: String = "",
    network: XoraNetworkState = XoraNetworkState(),
): List<MenuRow> = when (publicLobbyKind(platformId)) {
    PublicLobbyKind.NdsWfc -> listOf(
        MenuRow(
            id = "wfc-server",
            title = "WFC server · ${settings.ndsWfcServer.label()}",
            subtitle = "A cycles Kaeru / Wiimmfi / AltWFC / Off. Reset the game if " +
                "Nintendo Wi-Fi Connection does not pick up the new DNS.",
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.CycleNdsWfc,
        ),
        MenuRow(
            id = "wfc-how",
            title = "Open Nintendo Wi-Fi Connection",
            subtitle = "Mario Kart DS and other WFC titles list public rooms in that " +
                "in-game menu. XOrA Host/Join is only local wireless.",
            icon = XmbIcon.Play,
        ),
        MenuRow(
            id = "wfc-note",
            title = "Custom DNS",
            subtitle = if (settings.ndsWfcServer == NdsWfcServer.Custom) {
                settings.ndsWfcCustomDns.ifBlank { "Set a DNS in Settings" }
            } else {
                "Type a custom WFC DNS in Settings → Nintendo DS"
            },
            icon = XmbIcon.Settings,
        ),
    )
    PublicLobbyKind.AzaharRooms -> buildList {
        add(
            MenuRow(
                id = "xora-3ds-how",
                title = "XOrA 3DS public lobby",
                subtitle = "XOrA-to-XOrA via api.xoranetwork.com. Share a 6-character " +
                    "Online code. This is not a Citra IP and Azahar cannot join it.",
                icon = XmbIcon.Network,
            ),
        )
        add(
            MenuRow(
                id = "xora-3ds-host",
                title = when {
                    netplay.online && netplay.sessionCode.isNotBlank() &&
                        netplay.role == XoraNetplayRole.Host ->
                        "XOrA lobby code ${netplay.sessionCode}"
                    else -> "Host XOrA 3DS lobby"
                },
                subtitle = if (network.signedIn) {
                    "A starts Online Host. Friends join with the code on Netplay."
                } else {
                    "Sign in to XOrA Network first"
                },
                icon = XmbIcon.Play,
                action = EmulatorMenuAction.HostOnlineNetplay,
            ),
        )
        add(
            MenuRow(
                id = "xora-3ds-join",
                title = "Join XOrA 3DS lobby",
                subtitle = when {
                    !network.signedIn -> "Sign in to XOrA Network first"
                    joinCode.isBlank() -> "Type the 6-character code on Netplay, then A"
                    else -> "Join code $joinCode"
                },
                icon = XmbIcon.Friends,
                action = EmulatorMenuAction.JoinOnlineNetplay,
            ),
        )
        add(
            MenuRow(
                id = "az-refresh",
                title = if (publicLobbies.loading) "Refreshing rooms…" else "Refresh rooms",
                subtitle = publicLobbies.status.ifBlank {
                    if (publicLobbies.sourceUrl.isNotBlank()) {
                        "Last source ${publicLobbies.sourceUrl}"
                    } else {
                        "GET ${AzaharPublicLobbies.COMMUNITY_AZAHAR_API}/lobby"
                    }
                },
                icon = XmbIcon.Repeat,
                action = EmulatorMenuAction.RefreshAzaharLobbies,
            ),
        )
        add(
            MenuRow(
                id = "az-how",
                title = "XOrA cannot sit in Azahar rooms",
                subtitle = "Azahar rooms below are ENet Direct Connect (ip:port). A copies " +
                    "the IP and opens standalone Azahar. XOrA Online codes above cannot join them.",
                icon = XmbIcon.Notifications,
            ),
        )
        add(
            MenuRow(
                id = "az-standalone",
                title = if (publicLobbies.standaloneInstalled) {
                    "Open standalone Azahar"
                } else {
                    "Standalone Azahar not installed"
                },
                subtitle = if (publicLobbies.standaloneInstalled) {
                    "XOrA cannot sit in Citra rooms. A opens Azahar for Direct Connect."
                } else {
                    "Install Azahar (Vanilla or Play) to Direct Connect to listed rooms."
                },
                icon = XmbIcon.Emulator,
                action = EmulatorMenuAction.OpenStandaloneAzahar,
            ),
        )
        add(
            MenuRow(
                id = "az-pretendo",
                title = "Pretendo · not in XOrA",
                subtitle = "Cannot run in XOrA Emulator. Open standalone Azahar for Nimbus + NAND.",
                icon = XmbIcon.Network,
                pane = EmulatorMenuPane.Pretendo,
            ),
        )
        if (publicLobbies.rooms.isEmpty() && !publicLobbies.loading) {
            add(
                MenuRow(
                    id = "az-empty",
                    title = "No rooms listed",
                    subtitle = publicLobbies.status.ifBlank {
                        "Azahar has no official lobby. Set a community GET {url}/lobby in Settings."
                    },
                    icon = XmbIcon.Notifications,
                ),
            )
        }
        publicLobbies.rooms.forEachIndexed { index, room ->
            add(
                MenuRow(
                    id = "az-room-$index",
                    title = AzaharPublicLobbies.roomTitle(room, index + 1),
                    subtitle = AzaharPublicLobbies.roomSubtitle(room),
                    icon = XmbIcon.Friends,
                    action = EmulatorMenuAction.SelectAzaharRoom(
                        name = room.name.ifBlank { "Room ${index + 1}" },
                        game = room.preferredGame,
                        ip = room.ip,
                        port = room.port,
                        hasPassword = room.hasPassword,
                    ),
                ),
            )
        }
    }
    PublicLobbyKind.None -> listOf(
        MenuRow(
            id = "lobby-none",
            title = "No public lobbies",
            subtitle = "This core does not expose a public room list.",
            icon = XmbIcon.Notifications,
        ),
    )
}

private fun pretendoPaneRows(
    pretendo: AzaharPretendoUi,
): List<MenuRow> = listOf(
    MenuRow(
        id = "pt-unavailable",
        title = "Not in XOrA Emulator",
        subtitle = "DS Kaeru works here because melonDS has a WFC DNS option. Pretendo is " +
            "Nimbus + dumped NAND + LLE modules. Azahar libretro never reads those settings.",
        icon = XmbIcon.Notifications,
    ),
    MenuRow(
        id = "pt-status",
        title = when {
            pretendo.nandPresent && pretendo.nimbusPatches ->
                "NAND and Nimbus files found · still standalone only"
            pretendo.nandPresent -> "NAND found · does not enable Pretendo in XOrA"
            pretendo.nimbusPatches -> "Nimbus files found · does not enable Pretendo in XOrA"
            else -> "No NAND / Nimbus files in the XOrA Azahar folder"
        },
        subtitle = pretendo.userDir.ifBlank { "Azahar folder is under 3DS saves" },
        icon = XmbIcon.Folder,
        action = EmulatorMenuAction.RefreshPretendoStatus,
    ),
    MenuRow(
        id = "pt-why",
        title = "Why XOrA cannot host it",
        subtitle = "Libretro Azahar cannot install CIAs, boot Home Menu, run Nimbus, or " +
            "enable 'required LLE modules for online' / the 3GX plugin loader.",
        icon = XmbIcon.Settings,
    ),
    MenuRow(
        id = "pt-play",
        title = "Play Pretendo in standalone Azahar",
        subtitle = "Set Up System Files, install nimbus.cia, copy luma to sdmc, boot Home " +
            "Menu, tap Pretendo. Friends and official-online games run in that app.",
        icon = XmbIcon.Friends,
    ),
    MenuRow(
        id = "pt-standalone",
        title = "Open standalone Azahar",
        subtitle = "That app is the Pretendo emulator. XOrA only launches 3DS games locally.",
        icon = XmbIcon.Emulator,
        action = EmulatorMenuAction.OpenStandaloneAzahar,
    ),
)

@Composable
private fun JoinTargetField(
    label: String,
    value: String,
    selected: Boolean,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onClick: () -> Unit,
    onNext: (() -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var hadFocus by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                interactionSource = remember(label) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) Accent else Color.Transparent),
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = if (selected) 16.sp else 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(text = placeholder, color = Color.White.copy(alpha = 0.35f))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onNext?.invoke() },
                onDone = {
                    onCommit()
                    keyboard?.hide()
                },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Accent,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                focusedLabelColor = Accent,
                unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, top = 6.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        onCommit()
                    }
                },
        )
    }
}

private val SidebarInk = Color(0xFF10131A)
private val PanelInk = Color(0xF01A1F2A)
private val Accent = Color(0xFF3DFFDC)
private val SIDEBAR_WIDTH = 300.dp
private val PANEL_WIDTH = 280.dp
