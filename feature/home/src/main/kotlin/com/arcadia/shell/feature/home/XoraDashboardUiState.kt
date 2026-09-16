package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import com.arcadia.shell.xoranetwork.XoraNetworkState
import com.arcadia.shell.xoranetwork.XoraPresenceMode

/** Layers inside the XOrA Network Dashboard (Network → Dashboard). */
enum class DashboardView {
    /** Metro-style tile board (signed in). */
    Tiles,
    /** Friends list + invites + add-by-username. */
    Friends,
    /** Avatar, username and XOrA Network status — 0.5.6's Edit Profile. */
    EditProfile,
}

/**
 * Tile board order — Xbox 360 blade-dashboard tiles with PlayStation glass. Rows drive both the
 * layout and D-pad math so focus always matches what is on screen.
 */
enum class DashboardTile(val label: String) {
    Profile("Profile"),
    Friends("Friends"),
    Achievements("RetroAchievements"),
    RecentGames("Recently played"),
    Notifications("Notifications"),
    CloudSaves("Cloud Saves"),
    Netplay("Netplay"),
    Sharing("Sharing"),
    DeviceLink("Device Link"),
    ManageAccount("Manage account"),
    SignOut("Sign out"),
}

val DASHBOARD_TILE_ROWS: List<List<DashboardTile>> = listOf(
    listOf(DashboardTile.Profile, DashboardTile.Friends, DashboardTile.Achievements),
    listOf(DashboardTile.RecentGames, DashboardTile.Notifications, DashboardTile.CloudSaves),
    listOf(DashboardTile.Netplay, DashboardTile.Sharing, DashboardTile.DeviceLink),
    listOf(DashboardTile.ManageAccount, DashboardTile.SignOut),
)

val DASHBOARD_TILES: List<DashboardTile> = DASHBOARD_TILE_ROWS.flatten()

/** Planned-but-not-live features. They render as tiles that clearly say they are not enabled. */
val DASHBOARD_PLACEHOLDER_TILES: Set<DashboardTile> = setOf(
    DashboardTile.CloudSaves,
    DashboardTile.Netplay,
    DashboardTile.Sharing,
    DashboardTile.DeviceLink,
)

enum class DashboardAuthMode { SignIn, Register }

/** Focusable rows on the signed-out card. Both modes intentionally have six rows. */
enum class DashboardAuthRow { Email, Password, Username, DisplayName, Submit, SwitchMode, ForgotPassword, ManageAccount }

fun dashboardAuthRows(mode: DashboardAuthMode): List<DashboardAuthRow> = when (mode) {
    DashboardAuthMode.SignIn -> listOf(
        DashboardAuthRow.Email,
        DashboardAuthRow.Password,
        DashboardAuthRow.Submit,
        DashboardAuthRow.SwitchMode,
        DashboardAuthRow.ForgotPassword,
        DashboardAuthRow.ManageAccount,
    )
    DashboardAuthMode.Register -> listOf(
        DashboardAuthRow.Email,
        DashboardAuthRow.Password,
        DashboardAuthRow.Username,
        DashboardAuthRow.DisplayName,
        DashboardAuthRow.Submit,
        DashboardAuthRow.SwitchMode,
    )
}

data class DashboardAuthFormState(
    val mode: DashboardAuthMode = DashboardAuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val displayName: String = "",
    val focusIndex: Int = 0,
    /** Bumped when gamepad Confirm lands on a text row so the pane can raise the IME. */
    val fieldFocusTick: Int = 0,
) {
    val rows: List<DashboardAuthRow> get() = dashboardAuthRows(mode)
    val focusedRow: DashboardAuthRow? get() = rows.getOrNull(focusIndex)
}

/** Editable fields on the auth + profile forms (touch and gamepad funnel here). */
enum class DashboardField { Email, Password, Username, DisplayName, Location, FriendQuery }

/**
 * Edit Profile focus sections, in 0.5.6's order: the avatar bubble on the left, then the
 * USERNAME and XORA NETWORK STATUS rows stacked beside it.
 */
enum class EditProfileSection { Avatar, Username, Status }

/** One row of 0.5.6's PROFILE PICTURE sheet. [disabledHint] shows when the source isn't linked. */
data class ProfilePictureSource(
    val label: String,
    val disabledHint: String? = null,
    val enabled: Boolean = true,
)

data class DashboardEditProfileState(
    val displayName: String = "",
    val username: String = "",
    val location: String = "",
    val focusIndex: Int = 0,
    val fieldFocusTick: Int = 0,
    val statusExpanded: Boolean = false,
    /** Which picture sources are actually linked, resolved when the screen opens. */
    val discordLinked: Boolean = false,
    val raSignedIn: Boolean = false,
    val steamLinked: Boolean = false,
    val pictureSheetOpen: Boolean = false,
    val pictureIndex: Int = 0,
    /** The username row is a live text field rather than a label. */
    val usernameEditing: Boolean = false,
) {
    val section: EditProfileSection
        get() = EditProfileSection.entries.getOrElse(focusIndex) { EditProfileSection.Avatar }

    companion object {
        /** Avatar, Username, Status. */
        const val ROW_COUNT = 3
    }
}

/**
 * 0.5.6's picture sources, in order. Everything but upload and the colour swatch needs an
 * account linked first, and says which one in its hint.
 */
/** The four appearances 0.5.6's status menu offers, in its order. */
val XORA_PRESENCE_MENU_MODES: List<XoraPresenceMode> = listOf(
    XoraPresenceMode.Online,
    XoraPresenceMode.Away,
    XoraPresenceMode.Busy,
    XoraPresenceMode.Invisible,
)

/** 0.5.6 shows the Invisible appearance to its own owner as "Offline". */
fun xoraPresenceModeLabel(mode: XoraPresenceMode): String = when (mode) {
    XoraPresenceMode.Online -> "Online"
    XoraPresenceMode.Away -> "Away"
    XoraPresenceMode.Busy -> "Busy"
    XoraPresenceMode.Invisible -> "Offline"
}

/** How many rows the PROFILE PICTURE sheet has. */
const val PROFILE_PICTURE_SOURCE_COUNT = 5

fun profilePictureSources(
    discordLinked: Boolean,
    raSignedIn: Boolean,
    steamLinked: Boolean,
): List<ProfilePictureSource> = listOf(
    ProfilePictureSource("Upload a new picture"),
    ProfilePictureSource("Use your Discord picture", "Link Discord first", discordLinked),
    ProfilePictureSource(
        "Use your RetroAchievements picture",
        "Sign in to RetroAchievements first",
        raSignedIn,
    ),
    ProfilePictureSource("Use your Steam picture", "Add your Steam key and ID first", steamLinked),
    ProfilePictureSource("Use a colour instead"),
)

data class XoraDashboardUiState(
    val network: XoraNetworkState = XoraNetworkState(),
    val view: DashboardView = DashboardView.Tiles,
    val tileIndex: Int = 0,
    val auth: DashboardAuthFormState = DashboardAuthFormState(),
    /** Friends view: 0 = add-friend field, then one row per invite / friend. */
    val friendsIndex: Int = 0,
    val addFriendQuery: String = "",
    val friendFieldFocusTick: Int = 0,
    val edit: DashboardEditProfileState = DashboardEditProfileState(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /** Games most recently played in XOrA (local library history). */
    val recentGames: List<Game> = emptyList(),
    /** Library titles that have actually been launched at least once. */
    val gamesPlayedCount: Int = 0,
    val totalPlayTimeMs: Long = 0,
) {
    val focusedTile: DashboardTile? get() = DASHBOARD_TILES.getOrNull(tileIndex)

    /** Ordered rows behind [friendsIndex]: the add field, invites first, then friends. */
    val friendRows: List<com.arcadia.shell.xoranetwork.XoraFriend>
        get() = network.incomingInvites + network.outgoingInvites + network.acceptedFriends
}

/** Everything the Dashboard pane can ask the shell to do — touch and gamepad both land here. */
sealed interface DashboardCommand {
    data class FocusTile(val index: Int) : DashboardCommand
    data class ActivateTile(val index: Int) : DashboardCommand

    data class FocusAuthRow(val index: Int) : DashboardCommand
    data class ActivateAuthRow(val index: Int) : DashboardCommand
    data class EditField(val field: DashboardField, val value: String) : DashboardCommand
    data object SubmitAuth : DashboardCommand
    data object SwitchAuthMode : DashboardCommand

    data class FocusFriendRow(val index: Int) : DashboardCommand
    data class ActivateFriendRow(val index: Int) : DashboardCommand
    /** X on a friend row: remove friend / cancel outgoing / decline incoming. */
    data class RemoveFriendRow(val index: Int) : DashboardCommand
    data object SubmitAddFriend : DashboardCommand

    data class FocusEditRow(val index: Int) : DashboardCommand
    data class ActivateEditRow(val index: Int) : DashboardCommand
    /** Open / close the PROFILE PICTURE sheet behind the avatar bubble. */
    data object ToggleProfilePictureSheet : DashboardCommand
    data class PickProfilePicture(val index: Int) : DashboardCommand
    /** Open / close the XORA NETWORK STATUS dropdown. */
    data object ToggleStatusMenu : DashboardCommand
    data class SetPresenceMode(val mode: XoraPresenceMode) : DashboardCommand

    data object Refresh : DashboardCommand
    data object Back : DashboardCommand
}
