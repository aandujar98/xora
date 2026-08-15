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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.DEFAULT_NETPLAY_PORT
import com.arcadia.shell.datastore.MAX_NETPLAY_PORT
import com.arcadia.shell.datastore.MIN_NETPLAY_PORT
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.label
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState
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
    Mods,
    Settings,
    Gamepad,
    Graphics,
    Audio,
}

data class EmulatorSaveSlotUi(
    val slot: Int,
    val occupied: Boolean,
    val subtitle: String,
)

sealed class EmulatorMenuAction {
    data object TogglePause : EmulatorMenuAction()
    data class SaveSlot(val slot: Int) : EmulatorMenuAction()
    data class LoadSlot(val slot: Int) : EmulatorMenuAction()
    data object SetFullScreen : EmulatorMenuAction()
    data object SetNativeRatio : EmulatorMenuAction()
    data object ToggleBezel : EmulatorMenuAction()
    data object CycleInternalResolution : EmulatorMenuAction()
    data object CycleIntegerScale : EmulatorMenuAction()
    data object ToggleExpandDual : EmulatorMenuAction()
    data object ToggleNetplayEnabled : EmulatorMenuAction()
    data object HostNetplay : EmulatorMenuAction()
    data object JoinNetplay : EmulatorMenuAction()
    data object DisconnectNetplay : EmulatorMenuAction()
    data object ToggleSpectator : EmulatorMenuAction()
    data class SetJoinTarget(val address: String, val port: Int) : EmulatorMenuAction()
    data object ClearJoinTarget : EmulatorMenuAction()
    data object ToggleRaHardcore : EmulatorMenuAction()
    data class ShowAchievement(val title: String, val description: String) : EmulatorMenuAction()
    data object CyclePreferredController : EmulatorMenuAction()
    data object ClearMappings : EmulatorMenuAction()
    data object VolumeUp : EmulatorMenuAction()
    data object VolumeDown : EmulatorMenuAction()
    data object ResetDefaults : EmulatorMenuAction()
    data object ReturnHome : EmulatorMenuAction()
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
    message: String?,
    onAction: (EmulatorMenuAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    network: XoraNetworkState = XoraNetworkState(),
    achievements: List<RaAchievement> = emptyList(),
    achievementSummary: String = "",
    raStatus: String? = null,
) {
    var rootIndex by remember { mutableIntStateOf(0) }
    var pane by remember { mutableStateOf(EmulatorMenuPane.None) }
    var paneIndex by remember { mutableIntStateOf(0) }

    val rootRows = remember(
        paused,
        settings.netplayEnabled,
        gameTitle,
        hardcore,
        settings.aspectMode,
        network.signedIn,
        network.restoring,
        network.selfOnline,
        network.account?.username,
        achievementSummary,
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
                subtitle = if (settings.netplayEnabled) "On · hardcore off" else "Off",
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
        hardcore = hardcore,
        network = network,
        achievements = achievements,
        achievementSummary = achievementSummary,
        raStatus = raStatus,
        gameTitle = gameTitle,
    )
    val paneFocus = paneIndex.coerceIn(0, (paneRows.size - 1).coerceAtLeast(0))
    val rootFocus = rootIndex.coerceIn(0, rootRows.lastIndex)
    val rootListState = rememberLazyListState()
    val paneListState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val ipFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }
    var ipDraft by remember(joinAddress) { mutableStateOf(joinAddress) }
    var portDraft by remember(joinPort) { mutableStateOf(joinPort.toString()) }

    fun parsedJoinPort(): Int =
        portDraft.toIntOrNull()?.coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT)
            ?: DEFAULT_NETPLAY_PORT

    fun commitJoinDrafts() {
        onAction(EmulatorMenuAction.SetJoinTarget(ipDraft.trim(), parsedJoinPort()))
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
                commitJoinDrafts()
                onAction(EmulatorMenuAction.JoinNetplay)
            }
            "np-clear" -> {
                ipDraft = ""
                portDraft = DEFAULT_NETPLAY_PORT.toString()
                onAction(EmulatorMenuAction.ClearJoinTarget)
            }
            else -> when {
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
                    text = paneTitle(pane),
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

private fun paneTitle(pane: EmulatorMenuPane): String = when (pane) {
    EmulatorMenuPane.Save -> "Save state"
    EmulatorMenuPane.Load -> "Load state"
    EmulatorMenuPane.Display -> "Display"
    EmulatorMenuPane.Netplay -> "Netplay"
    EmulatorMenuPane.RetroAchievements -> "RetroAchievements"
    EmulatorMenuPane.Achievements -> "This game"
    EmulatorMenuPane.XoraNetwork -> "XOrA Network"
    EmulatorMenuPane.Mods -> "Mods"
    EmulatorMenuPane.Settings -> "Settings"
    EmulatorMenuPane.Gamepad -> "Gamepad"
    EmulatorMenuPane.Graphics -> "Graphics"
    EmulatorMenuPane.Audio -> "Audio"
    EmulatorMenuPane.None -> ""
}

private fun paneRows(
    pane: EmulatorMenuPane,
    settings: XoraEmulatorSettings,
    saveSlots: List<EmulatorSaveSlotUi>,
    netplay: XoraNetplayUiState,
    joinAddress: String,
    joinPort: Int,
    hardcore: Boolean,
    network: XoraNetworkState,
    achievements: List<RaAchievement>,
    achievementSummary: String,
    raStatus: String?,
    gameTitle: String,
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
            id = "full",
            title = "Full screen",
            subtitle = if (settings.aspectMode == XoraAspectMode.Stretch) "On" else "Stretch to fill",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.SetFullScreen,
        ),
        MenuRow(
            id = "native",
            title = "Native ratio",
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
    EmulatorMenuPane.Netplay -> listOf(
        MenuRow(
            id = "np-enable",
            title = if (settings.netplayEnabled) "Netplay on" else "Netplay off",
            subtitle = "Turns hardcore off when enabled",
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.ToggleNetplayEnabled,
        ),
        MenuRow(
            id = "np-host",
            title = "Host session",
            subtitle = netplay.localAddresses.firstOrNull()
                ?.let { "$it:${settings.netplayPort}" }
                ?: "Port ${settings.netplayPort}",
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
        MenuRow(
            id = "np-spec",
            title = if (settings.netplaySpectator) "Spectator on" else "Spectator off",
            subtitle = "Join without sending input",
            icon = XmbIcon.User,
            action = EmulatorMenuAction.ToggleSpectator,
        ),
        MenuRow(
            id = "np-status",
            title = netplay.status,
            subtitle = netplay.error ?: netplay.peerName.ifBlank { "Not connected" },
            icon = XmbIcon.Notifications,
        ),
        MenuRow(
            id = "np-disc",
            title = "Disconnect",
            subtitle = if (netplay.linked) "Drop the current session" else "No session",
            icon = XmbIcon.Settings,
            action = EmulatorMenuAction.DisconnectNetplay,
        ),
    )
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
            id = "g-full",
            title = "Full screen",
            subtitle = settings.aspectMode.label(),
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.SetFullScreen,
        ),
        MenuRow(
            id = "g-native",
            title = "Native ratio",
            subtitle = "Core aspect",
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
            subtitle = if (settings.expandDualDisplay) "On" else "Off",
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
}

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
