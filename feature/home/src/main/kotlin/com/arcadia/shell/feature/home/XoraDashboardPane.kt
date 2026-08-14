package com.arcadia.shell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.model.Game
import com.arcadia.shell.xoranetwork.XoraFriend
import com.arcadia.shell.xoranetwork.XoraFriendState
import com.arcadia.shell.xoranetwork.XoraNetworkClient
import com.arcadia.shell.xoranetwork.XoraNetworkState
import com.arcadia.shell.xoranetwork.xoraAppearanceLabel

private val TileShape = RoundedCornerShape(14.dp)
private val Ink = Color.White
private val InkMuted = Color.White.copy(alpha = 0.72f)
private val FocusEdge = Color.White.copy(alpha = 0.9f)
private val RestEdge = Color.White.copy(alpha = 0.22f)
private val OnlineGreen = Color(0xFF34C759)
private val InviteAmber = Color(0xFFFFB020)
private val AwayAmber = Color(0xFFFFC24B)
private val BusyRose = Color(0xFFFF5C6C)

/** PlayStation-family accent glazes over the glass, one per tile, Xbox-360-tile sized. */
private fun tileAccent(tile: DashboardTile): Color = when (tile) {
    DashboardTile.Profile -> Color(0xFF0070D1)
    DashboardTile.Friends -> Color(0xFF00439C)
    DashboardTile.Achievements -> Color(0xFFB8860B)
    DashboardTile.RecentGames -> Color(0xFF2E6DB4)
    DashboardTile.Notifications -> Color(0xFF5A2CA0)
    DashboardTile.CloudSaves -> Color(0xFF1E3A5F)
    DashboardTile.Netplay -> Color(0xFF1E3A5F)
    DashboardTile.Sharing -> Color(0xFF1E3A5F)
    DashboardTile.DeviceLink -> Color(0xFF1E3A5F)
    DashboardTile.ManageAccount -> Color(0xFF2C2C54)
    DashboardTile.SignOut -> Color(0xFF7A1F2B)
}

/**
 * XOrA Network → Dashboard: an Xbox-360-style tile board with PlayStation glass, shown over the
 * shell wallpaper at [XoraXmbDepth.Dashboard]. Touch and gamepad both funnel into [onCommand].
 */
@Composable
fun XoraDashboardPane(
    state: XoraDashboardUiState,
    achievements: AchievementsUiState,
    onCommand: (DashboardCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val network = state.network
    Column(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        DashboardHeader(state)
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !network.configured -> DashboardMessageCard(
                    title = "XOrA Network isn't configured",
                    body = "This build was made without the XOrA Network client key, so sign-in is " +
                        "unavailable. Accounts are managed at account.xoranetwork.com.",
                )
                network.restoring -> DashboardMessageCard(
                    title = "Connecting to XOrA Network…",
                    body = "Restoring your session.",
                    showSpinner = true,
                )
                !network.signedIn -> DashboardAuthCard(state, onCommand)
                state.view == DashboardView.Friends -> DashboardFriendsView(state, onCommand)
                state.view == DashboardView.EditProfile -> DashboardEditProfileView(state, onCommand)
                else -> DashboardTileBoard(state, achievements, onCommand)
            }
        }
    }
}

@Composable
private fun DashboardHeader(state: XoraDashboardUiState) {
    val network = state.network
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "XOrA Network",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            val status = state.error
                ?: state.notice
                ?: network.account?.let {
                    "Signed in as ${it.username} · ${xoraAppearanceLabel(network.presenceMode, network.selfOnline)}"
                }
                ?: "One account for the launcher and account.xoranetwork.com"
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) Color(0xFFFF8A80) else InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Ink,
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        val account = network.account
        if (account != null) {
            XoraNetworkAvatar(
                username = account.username,
                displayName = account.displayName,
                avatarUrl = account.resolvedAvatarUrl,
                size = 44.dp,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Tile board
// -------------------------------------------------------------------------------------------

@Composable
private fun DashboardTileBoard(
    state: XoraDashboardUiState,
    achievements: AchievementsUiState,
    onCommand: (DashboardCommand) -> Unit,
) {
    val rowWeights = listOf(1.5f, 1.25f, 0.9f, 0.62f)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DASHBOARD_TILE_ROWS.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(rowWeights.getOrElse(rowIndex) { 1f }),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { tile ->
                    val index = DASHBOARD_TILES.indexOf(tile)
                    val weight = when (tile) {
                        DashboardTile.Profile, DashboardTile.RecentGames -> 2f
                        else -> 1f
                    }
                    DashboardTileCard(
                        tile = tile,
                        focused = state.tileIndex == index,
                        onFocus = { onCommand(DashboardCommand.FocusTile(index)) },
                        onActivate = { onCommand(DashboardCommand.ActivateTile(index)) },
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight(),
                    ) {
                        when (tile) {
                            DashboardTile.Profile -> ProfileTileContent(state)
                            DashboardTile.Friends -> FriendsTileContent(state)
                            DashboardTile.Achievements -> AchievementsTileContent(achievements)
                            DashboardTile.RecentGames -> RecentGamesTileContent(state.recentGames)
                            DashboardTile.Notifications -> NotificationsTileContent(state)
                            DashboardTile.ManageAccount -> SimpleTileContent(
                                title = "Manage account",
                                body = "account.xoranetwork.com",
                            )
                            DashboardTile.SignOut -> SimpleTileContent(
                                title = "Sign out",
                                body = state.network.account?.username.orEmpty(),
                            )
                            else -> PlaceholderTileContent(tile)
                        }
                    }
                }
                // The last row has two tiles; keep them tile-sized instead of half-screen wide.
                if (row.size == 2) Spacer(modifier = Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun DashboardTileCard(
    tile: DashboardTile,
    focused: Boolean,
    onFocus: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = tileAccent(tile)
    Box(
        modifier = modifier
            .xoraForegroundShadow(TileShape)
            .liquidGlass(
                shape = TileShape,
                tone = GlassTone.OverMedia,
                intensity = if (focused) GlassIntensity.Strong else GlassIntensity.Standard,
                shimmer = focused,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusEdge else RestEdge,
                shape = TileShape,
            )
            .clip(TileShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = if (focused) 0.42f else 0.30f),
                        accent.copy(alpha = if (focused) 0.20f else 0.12f),
                    ),
                ),
            )
            .clickable {
                onFocus()
                onActivate()
            }
            .padding(14.dp),
    ) {
        content()
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Ink else InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun ProfileTileContent(state: XoraDashboardUiState) {
    val account = state.network.account ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize(),
    ) {
        XoraNetworkAvatar(
            username = account.username,
            displayName = account.displayName,
            avatarUrl = account.resolvedAvatarUrl,
            size = 96.dp,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.username,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            XoraSelfPresenceChip(state.network)
            if (account.location.isNotBlank()) {
                Text(
                    text = account.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${state.gamesPlayedCount} games played • ${formatPlayHours(state.totalPlayTimeMs)} on XOrA",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FriendsTileContent(state: XoraDashboardUiState) {
    val network = state.network
    Column(modifier = Modifier.fillMaxSize()) {
        val preview = network.acceptedFriends.take(4)
        if (preview.isEmpty()) {
            Text(
                text = "No friends yet.\nPress A to add by username.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                preview.forEach { friend ->
                    XoraNetworkAvatar(
                        username = friend.username,
                        displayName = friend.displayName,
                        avatarUrl = friend.resolvedAvatarUrl,
                        size = 40.dp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${network.acceptedFriends.size} friends • ${network.onlineFriendCount} online",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        if (network.incomingInvites.isNotEmpty()) {
            Text(
                text = "${network.incomingInvites.size} pending requests",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = InviteAmber,
            )
        }
    }
}

@Composable
private fun AchievementsTileContent(achievements: AchievementsUiState) {
    val profile = achievements.profile
    if (!achievements.credentials.isConfigured || profile == null) {
        Column {
            Text(
                text = "Connect RetroAchievements",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            Text(
                text = "Press A to open the RA panel and sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = profile.userPicUrl,
            contentDescription = profile.username,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.8f), CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = profile.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.totalPoints} pts",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFD700),
            )
            achievements.recent.firstOrNull()?.let { unlock ->
                Text(
                    text = unlock.gameTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentGamesTileContent(recentGames: List<Game>) {
    if (recentGames.isEmpty()) {
        Text(
            text = "Nothing played yet — launch something from Games.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        recentGames.take(4).forEach { game ->
            Column(modifier = Modifier.weight(1f)) {
                ArtworkImage(
                    path = game.boxArtPath,
                    contentDescription = game.title,
                    fallbackText = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, RestEdge, RoundedCornerShape(8.dp)),
                )
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NotificationsTileContent(state: XoraDashboardUiState) {
    val network = state.network
    Column {
        Text(
            text = if (network.unreadNotificationCount > 0) {
                "${network.unreadNotificationCount} unread"
            } else {
                "All caught up"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        val latest = network.notifications.firstOrNull()
        if (latest != null) {
            Text(
                text = "${latest.fromDisplayName}: ${latest.body}",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "Friend requests and messages from the website show here.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaceholderTileContent(tile: DashboardTile) {
    Column {
        Text(
            text = tile.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Text(
            text = "Not enabled yet",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SimpleTileContent(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        if (body.isNotBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Signed-out card (sign in / create account)
// -------------------------------------------------------------------------------------------

@Composable
private fun DashboardAuthCard(
    state: XoraDashboardUiState,
    onCommand: (DashboardCommand) -> Unit,
) {
    val auth = state.auth
    val rows = auth.rows
    val requesters = remember { mutableMapOf<DashboardAuthRow, FocusRequester>() }
    fun requesterFor(row: DashboardAuthRow) = requesters.getOrPut(row) { FocusRequester() }

    LaunchedEffect(auth.fieldFocusTick) {
        if (auth.fieldFocusTick > 0) {
            auth.focusedRow?.let { row -> runCatching { requesterFor(row).requestFocus() } }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 480.dp)
                .xoraForegroundShadow(ArcadiaGlass.PanelShape)
                .liquidGlass(
                    shape = ArcadiaGlass.PanelShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                )
                .border(1.5.dp, RestEdge, ArcadiaGlass.PanelShape)
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = if (auth.mode == DashboardAuthMode.SignIn) "Sign in" else "Create account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            Text(
                text = "Same account as account.xoranetwork.com.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            rows.forEachIndexed { index, row ->
                val focused = auth.focusIndex == index
                when (row) {
                    DashboardAuthRow.Email -> DashboardTextField(
                        value = auth.email,
                        onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.Email, it)) },
                        label = "Email",
                        focused = focused,
                        focusRequester = requesterFor(row),
                        keyboardType = KeyboardType.Email,
                        onTap = { onCommand(DashboardCommand.FocusAuthRow(index)) },
                    )
                    DashboardAuthRow.Password -> DashboardTextField(
                        value = auth.password,
                        onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.Password, it)) },
                        label = if (auth.mode == DashboardAuthMode.Register) {
                            "Password (8+ chars, letter + number)"
                        } else {
                            "Password"
                        },
                        focused = focused,
                        focusRequester = requesterFor(row),
                        password = true,
                        onTap = { onCommand(DashboardCommand.FocusAuthRow(index)) },
                    )
                    DashboardAuthRow.Username -> DashboardTextField(
                        value = auth.username,
                        onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.Username, it)) },
                        label = "Username (public id, no spaces)",
                        focused = focused,
                        focusRequester = requesterFor(row),
                        onTap = { onCommand(DashboardCommand.FocusAuthRow(index)) },
                    )
                    DashboardAuthRow.DisplayName -> DashboardTextField(
                        value = auth.displayName,
                        onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.DisplayName, it)) },
                        label = "Display name",
                        focused = focused,
                        focusRequester = requesterFor(row),
                        onTap = { onCommand(DashboardCommand.FocusAuthRow(index)) },
                    )
                    DashboardAuthRow.Submit -> DashboardButton(
                        label = if (auth.mode == DashboardAuthMode.SignIn) "Sign in" else "Create account",
                        focused = focused,
                        primary = true,
                        onClick = { onCommand(DashboardCommand.ActivateAuthRow(index)) },
                    )
                    DashboardAuthRow.SwitchMode -> DashboardButton(
                        label = if (auth.mode == DashboardAuthMode.SignIn) {
                            "New here? Create an account"
                        } else {
                            "Have an account? Sign in"
                        },
                        focused = focused,
                        onClick = { onCommand(DashboardCommand.ActivateAuthRow(index)) },
                    )
                    DashboardAuthRow.ForgotPassword -> DashboardButton(
                        label = "Forgot password? (opens the website)",
                        focused = focused,
                        onClick = { onCommand(DashboardCommand.ActivateAuthRow(index)) },
                    )
                    DashboardAuthRow.ManageAccount -> DashboardButton(
                        label = "Manage account on the website",
                        focused = focused,
                        onClick = { onCommand(DashboardCommand.ActivateAuthRow(index)) },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Friends view
// -------------------------------------------------------------------------------------------

@Composable
private fun DashboardFriendsView(
    state: XoraDashboardUiState,
    onCommand: (DashboardCommand) -> Unit,
) {
    val fieldRequester = remember { FocusRequester() }
    LaunchedEffect(state.friendFieldFocusTick) {
        if (state.friendFieldFocusTick > 0) runCatching { fieldRequester.requestFocus() }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .xoraForegroundShadow(ArcadiaGlass.PanelShape)
            .liquidGlass(
                shape = ArcadiaGlass.PanelShape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
            )
            .border(1.5.dp, RestEdge, ArcadiaGlass.PanelShape)
            .padding(20.dp),
    ) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink,
        )
        state.network.account?.let { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                XoraNetworkAvatar(
                    username = account.username,
                    displayName = account.displayName,
                    avatarUrl = account.resolvedAvatarUrl,
                    size = 38.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "You · ${account.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                XoraSelfPresenceChip(state.network)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DashboardTextField(
                value = state.addFriendQuery,
                onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.FriendQuery, it)) },
                label = "Add a friend by username",
                focused = state.friendsIndex == 0,
                focusRequester = fieldRequester,
                onTap = { onCommand(DashboardCommand.FocusFriendRow(0)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            DashboardButton(
                label = "Add",
                focused = false,
                primary = true,
                fillMaxWidth = false,
                onClick = { onCommand(DashboardCommand.SubmitAddFriend) },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val rows = state.friendRows
            when {
                rows.isEmpty() && state.network.friendsLoading -> {
                    item {
                        Text(
                            text = "Loading friends…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                rows.isEmpty() && state.network.friendsError != null -> {
                    item {
                        Text(
                            text = state.network.friendsError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF8A80),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                rows.isEmpty() -> {
                    item {
                        Text(
                            text = "No friends yet. Invites you send and receive show up here and on the website.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                else -> {
                    itemsIndexed(rows, key = { _, friend -> friend.username }) { rowIndex, friend ->
                        FriendRow(
                            friend = friend,
                            focused = state.friendsIndex == rowIndex + 1,
                            onClick = { onCommand(DashboardCommand.ActivateFriendRow(rowIndex + 1)) },
                            onRemove = { onCommand(DashboardCommand.RemoveFriendRow(rowIndex + 1)) },
                        )
                    }
                }
            }
        }
        Text(
            text = "A accepts an incoming request • X removes / cancels / declines • B back",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
        )
    }
}

@Composable
private fun FriendRow(
    friend: XoraFriend,
    focused: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        XoraNetworkAvatar(
            username = friend.username,
            displayName = friend.displayName,
            avatarUrl = friend.resolvedAvatarUrl,
            size = 38.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = friend.username,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (friend.state) {
            XoraFriendState.IncomingInvite -> StateChip("Wants to be friends", InviteAmber)
            XoraFriendState.OutgoingInvite -> StateChip("Invite sent", InkMuted)
            else -> XoraFriendPresenceChip(friend)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "✕",
            color = InkMuted,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(6.dp),
        )
    }
}

@Composable
private fun XoraSelfPresenceChip(network: XoraNetworkState) {
    val label = xoraAppearanceLabel(network.presenceMode, network.selfOnline)
    val color = when (label) {
        "Away" -> AwayAmber
        "Busy" -> BusyRose
        "Online" -> OnlineGreen
        else -> InkMuted
    }
    StateChip(label, color)
}

@Composable
private fun XoraFriendPresenceChip(friend: XoraFriend) {
    val presence = xoraFriendPresence(friend)
    val activity = xoraFriendActivity(friend)
    val (label, color) = when (presence) {
        SocialPresence.Offline -> "Offline" to InkMuted
        SocialPresence.Away -> "Away" to AwayAmber
        SocialPresence.Busy -> "Busy" to BusyRose
        SocialPresence.InGame -> (activity ?: "In game") to OnlineGreen
        SocialPresence.Online -> (activity?.takeUnless { it.equals("Online", ignoreCase = true) } ?: "Online") to OnlineGreen
    }
    StateChip(label, color)
}

@Composable
private fun StateChip(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// -------------------------------------------------------------------------------------------
// Edit profile view
// -------------------------------------------------------------------------------------------

@Composable
private fun DashboardEditProfileView(
    state: XoraDashboardUiState,
    onCommand: (DashboardCommand) -> Unit,
) {
    val edit = state.edit
    val requesters = remember { List(3) { FocusRequester() } }
    LaunchedEffect(edit.fieldFocusTick) {
        if (edit.fieldFocusTick > 0 && edit.focusIndex in 0..2) {
            runCatching { requesters[edit.focusIndex].requestFocus() }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 480.dp)
                .xoraForegroundShadow(ArcadiaGlass.PanelShape)
                .liquidGlass(
                    shape = ArcadiaGlass.PanelShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                )
                .border(1.5.dp, RestEdge, ArcadiaGlass.PanelShape)
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Edit profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            DashboardTextField(
                value = edit.displayName,
                onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.DisplayName, it)) },
                label = "Display name",
                focused = edit.focusIndex == 0,
                focusRequester = requesters[0],
                onTap = { onCommand(DashboardCommand.FocusEditRow(0)) },
            )
            DashboardTextField(
                value = edit.username,
                onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.Username, it)) },
                label = "Username (public id)",
                focused = edit.focusIndex == 1,
                focusRequester = requesters[1],
                onTap = { onCommand(DashboardCommand.FocusEditRow(1)) },
            )
            DashboardTextField(
                value = edit.location,
                onValueChange = { onCommand(DashboardCommand.EditField(DashboardField.Location, it)) },
                label = "Location",
                focused = edit.focusIndex == 2,
                focusRequester = requesters[2],
                onTap = { onCommand(DashboardCommand.FocusEditRow(2)) },
            )
            DashboardButton(
                label = "Save",
                focused = edit.focusIndex == 3,
                primary = true,
                onClick = { onCommand(DashboardCommand.ActivateEditRow(3)) },
            )
            DashboardButton(
                label = "Cancel",
                focused = edit.focusIndex == 4,
                onClick = { onCommand(DashboardCommand.ActivateEditRow(4)) },
            )
            Text(
                text = "Avatar and password changes live on account.xoranetwork.com.",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Shared bits
// -------------------------------------------------------------------------------------------

@Composable
private fun DashboardMessageCard(title: String, body: String, showSpinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 520.dp)
                .xoraForegroundShadow(ArcadiaGlass.PanelShape)
                .liquidGlass(
                    shape = ArcadiaGlass.PanelShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                )
                .border(1.5.dp, RestEdge, ArcadiaGlass.PanelShape)
                .padding(24.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp,
                    color = Ink,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
    }
}

@Composable
private fun DashboardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) Ink else InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(Ink),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = if (focused) 0.42f else 0.28f))
                .border(
                    width = 1.dp,
                    color = if (focused) FocusEdge else RestEdge,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onTap)
                .focusRequester(focusRequester)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.contentMuted.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun DashboardButton(
    label: String,
    focused: Boolean,
    primary: Boolean = false,
    fillMaxWidth: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        focused -> Color.White.copy(alpha = 0.28f)
        primary -> Color(0xFF0070D1).copy(alpha = 0.55f)
        else -> Color.Black.copy(alpha = 0.28f)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (primary || focused) FontWeight.SemiBold else FontWeight.Normal,
        color = Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (focused) FocusEdge else RestEdge,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/**
 * Avatar with initials fallback. Prefers a public https avatar_url; otherwise the website
 * `/api/avatars/{username}` endpoint (Coil attaches the Nakama session cookies).
 */
@Composable
fun XoraNetworkAvatar(
    username: String,
    displayName: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val model = avatarUrl?.takeIf { it.isNotBlank() }
        ?: XoraNetworkClient.avatarUrlFor(username)
    var failed by remember(model) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (failed || username.isBlank()) {
            InitialsAvatar(username = username, displayName = displayName, size = size)
        } else {
            AsyncImage(
                model = model,
                contentDescription = username,
                contentScale = ContentScale.Crop,
                onState = { imageState ->
                    if (imageState is coil3.compose.AsyncImagePainter.State.Error) failed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun InitialsAvatar(username: String, displayName: String, size: androidx.compose.ui.unit.Dp) {
    val source = displayName.ifBlank { username }
    val initials = source
        .split(' ', '_', '-')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    // Stable hue per user so the same person always gets the same disc colour.
    val hue = (username.lowercase().hashCode().let { if (it < 0) -it else it } % 360).toFloat()
    val background = Color.hsv(hue, 0.55f, 0.55f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            fontSize = (size.value * 0.34f).sp,
        )
    }
}

private fun formatPlayHours(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
