package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.label
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState
import com.arcadia.shell.libretro.netplay.parseIpv4

enum class EmulatorMenuPane {
    None,
    Save,
    Load,
    Display,
    Netplay,
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
    data object ClearWhiteTint : EmulatorMenuAction()
    data object ToggleBlockOverlayWash : EmulatorMenuAction()
    data object CycleInternalResolution : EmulatorMenuAction()
    data object CycleIntegerScale : EmulatorMenuAction()
    data object ToggleExpandDual : EmulatorMenuAction()
    data object ToggleNetplayEnabled : EmulatorMenuAction()
    data object HostNetplay : EmulatorMenuAction()
    data object JoinNetplay : EmulatorMenuAction()
    data object DisconnectNetplay : EmulatorMenuAction()
    data object ToggleSpectator : EmulatorMenuAction()
    data class NudgeJoinOctet(val octetIndex: Int, val delta: Int) : EmulatorMenuAction()
    data object CyclePreferredController : EmulatorMenuAction()
    data object ClearMappings : EmulatorMenuAction()
    data object VolumeUp : EmulatorMenuAction()
    data object VolumeDown : EmulatorMenuAction()
    data object ResetDefaults : EmulatorMenuAction()
    data object ReturnHome : EmulatorMenuAction()
}

internal data class MenuRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: XmbIcon,
    val pane: EmulatorMenuPane? = null,
    val action: EmulatorMenuAction? = null,
)

internal fun emulatorRootRows(
    gameTitle: String,
    paused: Boolean,
    hardcore: Boolean,
    settings: XoraEmulatorSettings,
): List<MenuRow> = listOf(
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
        subtitle = if (settings.netplayEnabled) "On" else "Off",
        icon = XmbIcon.Network,
        pane = EmulatorMenuPane.Netplay,
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

internal fun paneTitle(pane: EmulatorMenuPane): String = when (pane) {
    EmulatorMenuPane.Save -> "Save state"
    EmulatorMenuPane.Load -> "Load state"
    EmulatorMenuPane.Display -> "Display"
    EmulatorMenuPane.Netplay -> "Netplay"
    EmulatorMenuPane.Mods -> "Mods"
    EmulatorMenuPane.Settings -> "Settings"
    EmulatorMenuPane.Gamepad -> "Gamepad"
    EmulatorMenuPane.Graphics -> "Graphics"
    EmulatorMenuPane.Audio -> "Audio"
    EmulatorMenuPane.None -> ""
}

internal fun paneRows(
    pane: EmulatorMenuPane,
    settings: XoraEmulatorSettings,
    saveSlots: List<EmulatorSaveSlotUi>,
    netplay: XoraNetplayUiState,
    joinAddress: String,
    hardcore: Boolean,
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
        MenuRow(
            id = "wash",
            title = "Remove white tint",
            subtitle = "Re-pin the window opaque",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.ClearWhiteTint,
        ),
        MenuRow(
            id = "block-wash",
            title = if (settings.blockOverlayWash) "Block white tint on" else "Block white tint off",
            subtitle = "Pause menu sits on the emulator stage",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.ToggleBlockOverlayWash,
        ),
    )
    EmulatorMenuPane.Netplay -> listOf(
        MenuRow(
            id = "np-enable",
            title = if (settings.netplayEnabled) "Netplay on" else "Netplay off",
            subtitle = "A toggles",
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
            subtitle = joinAddress.ifBlank { "Set join address below" },
            icon = XmbIcon.Friends,
            action = EmulatorMenuAction.JoinNetplay,
        ),
        MenuRow(
            id = "np-oct0",
            title = "Join IP octet 1",
            subtitle = octetLabel(joinAddress, 0),
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.NudgeJoinOctet(0, 1),
        ),
        MenuRow(
            id = "np-oct1",
            title = "Join IP octet 2",
            subtitle = octetLabel(joinAddress, 1),
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.NudgeJoinOctet(1, 1),
        ),
        MenuRow(
            id = "np-oct2",
            title = "Join IP octet 3",
            subtitle = octetLabel(joinAddress, 2),
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.NudgeJoinOctet(2, 1),
        ),
        MenuRow(
            id = "np-oct3",
            title = "Join IP octet 4",
            subtitle = octetLabel(joinAddress, 3),
            icon = XmbIcon.Network,
            action = EmulatorMenuAction.NudgeJoinOctet(3, 1),
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
            id = "g-wash",
            title = "Remove white tint",
            subtitle = "Re-pin the window opaque",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.ClearWhiteTint,
        ),
        MenuRow(
            id = "g-block-wash",
            title = if (settings.blockOverlayWash) "Block white tint on" else "Block white tint off",
            subtitle = "Pause menu sits on the emulator stage",
            icon = XmbIcon.Display,
            action = EmulatorMenuAction.ToggleBlockOverlayWash,
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

private fun octetLabel(address: String, index: Int): String {
    val parts = parseIpv4(address)
    return parts.getOrNull(index)?.toString() ?: "0"
}

internal val EmulatorMenuSidebarDp = 300
internal val EmulatorMenuPanelDp = 280
