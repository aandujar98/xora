package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import com.arcadia.shell.xoranetwork.XoraNetworkState

/** Layers inside the XOrA Network Dashboard (Network → Dashboard). */
enum class DashboardView {
    /** Metro-style tile board (signed in). */
    Tiles,
    /** Friends list + invites + add-by-username. */
    Friends,
    /** Edit display name / username / location. */
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

data class DashboardEditProfileState(
    val displayName: String = "",
    val username: String = "",
    val location: String = "",
    val focusIndex: Int = 0,
    val fieldFocusTick: Int = 0,
) {
    companion object {
        /** DisplayName, Username, Location, Save, Cancel. */
        const val ROW_COUNT = 5
    }
}

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

    data object Refresh : DashboardCommand
    data object Back : DashboardCommand
}
