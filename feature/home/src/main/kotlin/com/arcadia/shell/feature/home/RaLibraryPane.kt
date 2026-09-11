package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.XoraOutlinedText
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.feature.home.component.ProfileAvatar
import com.arcadia.shell.feature.home.component.xmb.drawableResForPlatformId
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaFollowedUser
import com.arcadia.shell.retroachievements.RaProfile
import kotlinx.coroutines.delay
import com.arcadia.shell.designsystem.XoraSettingsPanelDefaults
import com.arcadia.shell.designsystem.XoraSettingsPanelRule
import com.arcadia.shell.designsystem.xoraFocusHighlight
import com.arcadia.shell.designsystem.xoraSettingsPanelSurface
import com.arcadia.shell.feature.home.component.rememberAvatarAccentColor
import androidx.compose.foundation.Canvas
import com.arcadia.shell.designsystem.XoraSheetScrim

/**
 * RetroAchievements library over the shell wallpaper. The XMB recedes underneath;
 * cheevo badges then populate in a short stagger.
 */
@Composable
fun RaLibraryPane(
    state: HomeUiState,
    onSelectIndex: (Int) -> Unit,
    onSelectTab: (RaLibraryTab) -> Unit,
    onToggleSortMenu: () -> Unit = {},
    onSelectPlatformFilter: (String?) -> Unit,
    onActivate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    populateCheevos: Boolean = true,
    onSelectCheevoIndex: (Int) -> Unit = {},
    onCloseGameDetail: () -> Unit = {},
    onSelectFollowingIndex: (Int) -> Unit = {},
    onToggleCompare: () -> Unit = {},
) {
    val ra = state.raLibrary
    val visible = ra.visibleGames
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    var cheevosReady by remember { mutableStateOf(reduceMotion || !populateCheevos) }

    LaunchedEffect(ra.selectedIndex, visible.size, ra.tab, ra.platformFilter) {
        if (visible.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(ra.selectedIndex.coerceIn(0, visible.lastIndex))
    }

    LaunchedEffect(populateCheevos, visible.isNotEmpty(), reduceMotion) {
        if (!populateCheevos || visible.isEmpty() || reduceMotion) {
            cheevosReady = true
            return@LaunchedEffect
        }
        cheevosReady = false
        delay(ArcadiaMotion.Medium.toLong())
        cheevosReady = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 24.dp, top = 28.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RaLibrarySidePanel(
                profile = state.profile,
                profileAvatarModel = state.profileAvatarModel,
                raProfile = state.achievements.profile,
                ra = ra,
                onSelectTab = onSelectTab,
                onSelectFollowingIndex = { index ->
                    onSelectFollowingIndex(index)
                    onActivate()
                },
                onToggleSortMenu = onToggleSortMenu,
                onToggleCompare = onToggleCompare,
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    ra.isLoading && visible.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    ra.viewedUserLoading && visible.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    ra.error != null && visible.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                12.dp,
                                Alignment.CenterVertically,
                            ),
                        ) {
                            Text(
                                text = ra.error,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = XoraFonts.Secondary,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }

                    visible.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            XoraOutlinedText(
                                text = if (!state.achievements.credentials.isConfigured) {
                                    "Sign in to RetroAchievements to see your library."
                                } else if (ra.viewingFollower) {
                                    "No RetroAchievements progress for ${ra.viewedUser}."
                                } else {
                                    "No RetroAchievements progress yet."
                                },
                                fontFamily = XoraFonts.Secondary,
                                fontSize = 18.sp,
                                outlineWidth = 2.dp,
                            )
                        }
                    }

                    else -> {
                        if (ra.viewingFollower) {
                            XoraOutlinedText(
                                text = "${ra.viewedUser}'s games",
                                fontFamily = XoraFonts.Title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                outlineWidth = 2.dp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            itemsIndexed(
                                items = visible,
                                key = { _, row -> row.game.gameId },
                            ) { index, row ->
                                RaLibraryGameRowCard(
                                    row = row,
                                    selected = ra.focusColumn == RaLibraryFocusColumn.Games &&
                                        index == ra.selectedIndex,
                                    populateCheevos = cheevosReady,
                                    appearIndex = index,
                                    onClick = {
                                        onSelectIndex(index)
                                        onActivate()
                                    },
                                )
                            }
                        }
                    }
                }

                if (ra.platforms.isNotEmpty()) {
                    RaPlatformFilterRow(
                        platforms = ra.platforms,
                        selected = ra.platformFilter,
                        onSelect = onSelectPlatformFilter,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = ra.gameDetailOpen,
            enter = fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(tween(ArcadiaMotion.Fast, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(ArcadiaMotion.Fast, easing = FastOutSlowInEasing),
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            RaGameCheevoWindow(
                ra = ra,
                onSelectCheevo = onSelectCheevoIndex,
                onRetry = onActivate,
                onClose = onCloseGameDetail,
                onToggleCompare = onToggleCompare,
            )
        }
    }
}

@Composable
private fun RaLibrarySidePanel(
    profile: com.arcadia.shell.datastore.LocalProfile,
    profileAvatarModel: String?,
    raProfile: RaProfile?,
    ra: RaLibraryUiState,
    onSelectTab: (RaLibraryTab) -> Unit,
    onToggleSortMenu: () -> Unit,
    onSelectFollowingIndex: (Int) -> Unit,
    onToggleCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .xmbAssetShadow(
                unit = 1f,
                shape = XoraSettingsPanelDefaults.Shape,
                alpha = XoraForegroundShadow.Alpha,
            )
            .xoraSettingsPanelSurface(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ra_logo),
            contentDescription = "RetroAchievements",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 96.dp),
        )
        XoraSettingsPanelRule()

        val viewedFollower = if (ra.viewingFollower) ra.comparePeer else null
        val headerName = viewedFollower?.username
            ?: raProfile?.username
            ?: profile.displayName
        val headerPic = viewedFollower?.userPicUrl ?: profileAvatarModel
        val headerPoints = viewedFollower?.points ?: raProfile?.totalPoints
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileAvatar(
                displayName = headerName,
                presetId = if (viewedFollower != null) "preset_0" else profile.avatarPresetId,
                size = 48.dp,
                imageModel = headerPic,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Same rule as the profile card: the name wears the avatar's own colour.
                val nameAccent = rememberAvatarAccentColor(
                    imageModel = headerPic,
                    fallback = Color.White,
                )
                XoraOutlinedText(
                    text = headerName,
                    fontFamily = XoraFonts.XmbLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    outlineWidth = 2.dp,
                    fillColor = nameAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                headerPoints?.let { points ->
                    RaPointsLine(points = points, fontSize = 13.sp)
                }
            }
        }

        RaSortDropdown(
            tab = ra.tab,
            expanded = ra.sortMenuOpen,
            highlightedIndex = ra.sortMenuIndex,
            focused = ra.focusColumn == RaLibraryFocusColumn.Sort,
            onToggle = onToggleSortMenu,
            onSelect = onSelectTab,
        )

        // Compare no longer has a chip of its own in the design; Options still toggles it, so
        // the state stays visible as a line rather than disappearing silently.
        if (ra.compareEnabled) {
            val peer = ra.comparePeer?.username ?: ra.viewedUser
            XoraOutlinedText(
                text = if (peer.isNullOrBlank()) "Comparing cheevos" else "Comparing vs $peer",
                fontFamily = XoraFonts.Secondary,
                fontSize = 12.sp,
                outlineWidth = 1.5.dp,
                fillColor = Color(0xFFFFC24A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onToggleCompare),
            )
        }

        XoraSettingsPanelRule()

        RaFollowingLeaderboard(
            ra = ra,
            onSelectFollowingIndex = onSelectFollowingIndex,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        XoraOutlinedText(
            text = "LB / RB  ${ra.platformFilter ?: "All platforms"}",
            fontFamily = XoraFonts.Secondary,
            fontSize = 13.sp,
            outlineWidth = 1.5.dp,
            fillColor = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RaFollowingLeaderboard(
    ra: RaLibraryUiState,
    onSelectFollowingIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(ra.followingIndex, ra.following.size) {
        if (ra.following.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(
            ra.followingIndex.coerceIn(0, ra.following.lastIndex),
        )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        XoraOutlinedText(
            text = "FOLLOWING",
            fontFamily = XoraFonts.XmbLabel,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            outlineWidth = 2.dp,
        )
        when {
            ra.followingLoading && ra.following.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            }
            ra.following.isEmpty() -> {
                XoraOutlinedText(
                    text = ra.followingError
                        ?: "Follow people on RetroAchievements to see their scores.",
                    fontFamily = XoraFonts.Secondary,
                    fontSize = 12.sp,
                    outlineWidth = 1.5.dp,
                    fillColor = Color.White.copy(alpha = 0.7f),
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = ra.following,
                        key = { _, user -> user.username },
                    ) { index, user ->
                        RaFollowedUserRow(
                            user = user,
                            selected = ra.focusColumn == RaLibraryFocusColumn.Following &&
                                index == ra.followingIndex,
                            viewing = user.username.equals(ra.viewedUser, ignoreCase = true),
                            onClick = { onSelectFollowingIndex(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RaFollowedUserRow(
    user: RaFollowedUser,
    selected: Boolean,
    viewing: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(
        targetValue = when {
            selected -> Color.White.copy(alpha = 0.22f)
            viewing -> Color.White.copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = 0.06f)
        },
        label = "raFollowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(
                if (selected || viewing) {
                    Modifier.border(
                        1.5.dp,
                        Color.White.copy(alpha = if (selected) 0.55f else 0.28f),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfileAvatar(
            displayName = user.username,
            presetId = "preset_0",
            size = 32.dp,
            imageModel = user.userPicUrl.takeIf { it.isNotBlank() },
        )
        Column(modifier = Modifier.weight(1f)) {
            val nameAccent = rememberAvatarAccentColor(
                imageModel = user.userPicUrl.takeIf { it.isNotBlank() },
                fallback = Color.White,
            )
            Text(
                text = user.username,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = XoraFonts.XmbLabel,
                ),
                fontWeight = FontWeight.SemiBold,
                color = nameAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RaPointsLine(points = user.points, fontSize = 11.sp)
        }
    }
}

/** Trophy + POINTS + amber value — the score line shared by the profile and every follower. */
@Composable
private fun RaPointsLine(points: Int, fontSize: androidx.compose.ui.unit.TextUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        XmbVectorIcon(
            icon = XmbIcon.Trophy,
            tint = Color.White.copy(alpha = 0.85f),
            size = fontSize.value.dp + 3.dp,
        )
        XoraOutlinedText(
            text = "POINTS",
            fontFamily = XoraFonts.Secondary,
            fontSize = fontSize,
            outlineWidth = 1.dp,
            fillColor = Color.White.copy(alpha = 0.85f),
        )
        XoraOutlinedText(
            text = formatRaPoints(points),
            fontFamily = XoraFonts.Secondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            outlineWidth = 1.dp,
            fillColor = RaPointsAmber,
        )
    }
}

private val RaPointsAmber = Color(0xFFFFA92E)

/** Solid ▼, drawn rather than pulled from an icon font the module does not ship. */
@Composable
private fun RaDropdownCaret() {
    Canvas(modifier = Modifier.size(14.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.34f)
            lineTo(size.width * 0.85f, size.height * 0.34f)
            lineTo(size.width * 0.5f, size.height * 0.72f)
            close()
        }
        drawPath(path, Color.White)
    }
}

private fun formatRaPoints(points: Int): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(points)

/**
 * The sort control from the design: a pill showing the current mode, expanding into the three
 * options. Collapsed it is one focus stop for the pad; expanded it takes the pad outright (see
 * [HomeViewModel.onRaLibraryNavAction]) so Up/Down cannot move the lists behind it.
 */
@Composable
private fun RaSortDropdown(
    tab: RaLibraryTab,
    expanded: Boolean,
    highlightedIndex: Int,
    focused: Boolean,
    onToggle: () -> Unit,
    onSelect: (RaLibraryTab) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.16f),
                        ),
                    ),
                    shape,
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.32f),
                    shape = shape,
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = XoraFonts.Secondary,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RaDropdownCaret()
        }
        if (!expanded) return@Column
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF0C1017), shape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                .padding(4.dp),
        ) {
            RaLibraryTab.entries.forEachIndexed { index, option ->
                val highlighted = index == highlightedIndex
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = XoraFonts.Secondary,
                    ),
                    fontWeight = if (option == tab) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .xoraFocusHighlight(highlighted, shape = RoundedCornerShape(10.dp))
                        .clickable { onSelect(option) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun RaTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        label = "raTabBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) {
            Color.White
        } else {
            Color.White.copy(alpha = 0.70f)
        },
        label = "raTabFg",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = XoraFonts.XmbLabel,
        ),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(
                        1.5.dp,
                        Color.White.copy(alpha = 0.55f),
                        RoundedCornerShape(20.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun RaLibraryGameRowCard(
    row: RaLibraryGameRow,
    selected: Boolean,
    populateCheevos: Boolean,
    appearIndex: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val rim = MaterialTheme.colorScheme.primary
    val borderBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                rim.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.55f),
                rim.copy(alpha = 0.75f),
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
        )
    }
    val reduceMotion = rememberReduceMotion()
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(appearIndex, reduceMotion) {
        if (reduceMotion) {
            appear.snapTo(1f)
            return@LaunchedEffect
        }
        appear.snapTo(0f)
        delay((appearIndex.coerceAtMost(12) * 28L))
        appear.animateTo(
            1f,
            tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = appear.value
                alpha = t
                translationY = (1f - t) * 14f
            }
            .xmbAssetShadow(
                unit = 1f,
                shape = shape,
                alpha = if (selected) XoraForegroundShadow.Alpha else XoraForegroundShadow.TitleAlpha,
            )
            .liquidGlass(
                shape = shape,
                tone = GlassTone.OverMedia,
                intensity = if (selected) GlassIntensity.Strong else GlassIntensity.Standard,
            )
            .border(width = if (selected) 2.dp else 0.dp, brush = borderBrush, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(row.game.imageIconUrl.takeIf { it.isNotBlank() })
                .crossfade(120)
                .build(),
            contentDescription = row.game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = row.game.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = XoraFonts.XmbLabel,
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.recentBadgeUrls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.recentBadgeUrls.take(8).forEachIndexed { badgeIndex, url ->
                        CheevoBadge(
                            url = url,
                            populate = populateCheevos,
                            index = badgeIndex,
                        )
                    }
                }
            } else {
                Text(
                    text = row.game.consoleName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = XoraFonts.Secondary,
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(88.dp),
        ) {
            Text(
                text = row.game.progressLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = XoraFonts.Secondary,
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (row.game.isMastered) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    Color.White.copy(alpha = 0.88f)
                },
            )
            RaProgressBar(
                fraction = row.game.completionFraction,
                mastered = row.game.isMastered,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            )
        }
    }
}

@Composable
private fun CheevoBadge(
    url: String,
    populate: Boolean,
    index: Int,
) {
    val reduceMotion = rememberReduceMotion()
    val appear = remember { Animatable(if (reduceMotion || populate) 0f else 0f) }
    LaunchedEffect(populate, index, reduceMotion) {
        if (reduceMotion) {
            appear.snapTo(if (populate) 1f else 0f)
            return@LaunchedEffect
        }
        if (!populate) {
            appear.snapTo(0f)
            return@LaunchedEffect
        }
        appear.snapTo(0f)
        delay(index * 35L)
        appear.animateTo(
            1f,
            tween(180, easing = FastOutSlowInEasing),
        )
    }
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                val t = appear.value
                alpha = t
                scaleX = 0.55f + 0.45f * t
                scaleY = 0.55f + 0.45f * t
            }
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
private fun RaProgressBar(
    fraction: Float,
    mastered: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = Color.White.copy(alpha = 0.18f)
    val fill = if (mastered) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(fill),
        )
    }
}

@Composable
private fun RaPlatformFilterRow(
    platforms: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "all") {
            PlatformChip(
                label = "All",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        itemsIndexed(platforms, key = { _, name -> name }) { _, name ->
            PlatformChip(
                label = name,
                selected = selected == name,
                onClick = { onSelect(name) },
                platformHint = name,
            )
        }
    }
}

@Composable
private fun PlatformChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    platformHint: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val platformId = platformHint?.let { guessPlatformId(it) }
    Row(
        modifier = Modifier
            .liquidGlass(
                shape = shape,
                tone = GlassTone.OverMedia,
                intensity = if (selected) GlassIntensity.Standard else GlassIntensity.Subtle,
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (platformId != null) {
            androidx.compose.material3.Icon(
                painter = painterResource(drawableResForPlatformId(platformId)),
                contentDescription = label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = shortPlatformLabel(label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = XoraFonts.XmbLabel,
            ),
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.7f),
            maxLines = 1,
        )
    }
}

private val CheevoEarnedEdge = Color(0xFFEFBD17)
private val CheevoHardcoreEdge = Color(0xFFFFC95E)

@Composable
private fun RaGameCheevoWindow(
    ra: RaLibraryUiState,
    onSelectCheevo: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onToggleCompare: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        XoraSheetScrim(visible = true, onClick = onClose)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .xmbAssetShadow(unit = 1f, shape = shape, alpha = XoraForegroundShadow.Alpha)
                .liquidGlass(
                    shape = shape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                    shimmer = true,
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val detail = ra.gameDetail
            val headerGame = detail?.title ?: ra.selectedGame?.game?.title.orEmpty()
            val headerConsole = detail?.consoleName ?: ra.selectedGame?.game?.consoleName.orEmpty()
            val headerProgress = if (ra.compareEnabled) {
                val you = if (ra.viewingFollower) ra.compareProgress else detail
                val them = if (ra.viewingFollower) detail else ra.compareProgress
                val themName = ra.viewedUser ?: ra.selectedFollower?.username ?: "Them"
                listOfNotNull(
                    you?.progressLabel?.let { "You $it" },
                    them?.progressLabel?.let { "$themName $it" },
                ).joinToString("  ·  ").ifBlank {
                    detail?.progressLabel ?: ra.selectedGame?.game?.progressLabel.orEmpty()
                }
            } else {
                detail?.progressLabel ?: ra.selectedGame?.game?.progressLabel.orEmpty()
            }
            val headerIcon = detail?.imageIconUrl ?: ra.selectedGame?.game?.imageIconUrl.orEmpty()
            val context = LocalContext.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(headerIcon.takeIf { it.isNotBlank() })
                        .crossfade(120)
                        .build(),
                    contentDescription = headerGame,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    XoraOutlinedText(
                        text = headerGame.ifBlank { "Achievements" },
                        fontFamily = XoraFonts.Title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = XoraFonts.TitleLetterSpacing,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    XoraOutlinedText(
                        text = listOf(headerConsole, headerProgress)
                            .filter { it.isNotBlank() }
                            .joinToString("  ·  "),
                        fontFamily = XoraFonts.Secondary,
                        fontSize = 14.sp,
                        outlineWidth = 1.5.dp,
                        fillColor = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RaTabChip(
                    label = if (ra.compareEnabled) "Comparing" else "Compare",
                    selected = ra.compareEnabled,
                    onClick = onToggleCompare,
                )
            }

            when {
                ra.gameDetailLoading && detail == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                ra.gameDetailError != null && detail == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            12.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        Text(
                            text = ra.gameDetailError,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = XoraFonts.Secondary,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                detail == null || detail.achievements.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        XoraOutlinedText(
                            text = "No achievements for this game.",
                            fontFamily = XoraFonts.Secondary,
                            fontSize = 16.sp,
                            outlineWidth = 2.dp,
                        )
                    }
                }

                else -> {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(ra.cheevoIndex, detail.achievements.size) {
                        if (detail.achievements.isEmpty()) return@LaunchedEffect
                        gridState.animateScrollToItem(
                            ra.cheevoIndex.coerceIn(0, detail.achievements.lastIndex),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(RA_CHEEVO_GRID_COLUMNS),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        itemsIndexed(
                            items = detail.achievements,
                            key = { _, cheevo -> cheevo.id },
                        ) { index, cheevo ->
                            RaCheevoGridTile(
                                cheevo = cheevo,
                                selected = index == ra.cheevoIndex,
                                compareEnabled = ra.compareEnabled,
                                otherEarned = ra.compareAchievement(cheevo.id)?.earned == true,
                                otherHardcore = ra.compareAchievement(cheevo.id)?.earnedHardcore == true,
                                onClick = { onSelectCheevo(index) },
                            )
                        }
                    }
                }
            }

            val selected = ra.selectedCheevo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selected != null) {
                    val status = ra.cheevoStatusLine(selected)
                    Text(
                        text = selected.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = XoraFonts.XmbLabel,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = XoraFonts.Secondary,
                        ),
                        color = if (selected.earned) CheevoHardcoreEdge else Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                    Text(
                        text = selected.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = XoraFonts.Secondary,
                        ),
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun RaCheevoGridTile(
    cheevo: RaAchievement,
    selected: Boolean,
    onClick: () -> Unit,
    compareEnabled: Boolean = false,
    otherEarned: Boolean = false,
    otherHardcore: Boolean = false,
) {
    val earned = cheevo.earned
    val edge = when {
        selected -> MaterialTheme.colorScheme.primary
        cheevo.earnedHardcore -> CheevoHardcoreEdge
        earned -> CheevoEarnedEdge
        else -> Color.White.copy(alpha = 0.22f)
    }
    val grayMatrix = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = if (earned) 0.12f else 0.42f))
            .border(if (selected) 2.5.dp else 1.5.dp, edge, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(cheevo.badgeUrl)
                .crossfade(80)
                .build(),
            contentDescription = cheevo.title,
            contentScale = ContentScale.Crop,
            colorFilter = if (earned) null else ColorFilter.colorMatrix(grayMatrix),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (earned) {
                        Modifier
                    } else {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.28f))
                        }
                    },
                ),
        )
        if (compareEnabled && otherEarned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (otherHardcore) CheevoHardcoreEdge else CheevoEarnedEdge)
                    .border(1.dp, Color.Black.copy(alpha = 0.45f), RoundedCornerShape(percent = 50)),
            )
        }
    }
}

private fun shortPlatformLabel(name: String): String = when {
    name.contains("PlayStation Portable", ignoreCase = true) -> "PSP"
    name.contains("PlayStation Vita", ignoreCase = true) -> "Vita"
    name.contains("PlayStation 2", ignoreCase = true) -> "PS2"
    name.contains("PlayStation", ignoreCase = true) &&
        !name.contains("2") && !name.contains("3") -> "PS1"
    name.contains("Nintendo DS", ignoreCase = true) -> "NDS"
    name.contains("Game Boy Advance", ignoreCase = true) -> "GBA"
    name.contains("GameCube", ignoreCase = true) -> "GCN"
    name.contains("Mega Drive", ignoreCase = true) ||
        name.contains("Genesis", ignoreCase = true) -> "Genesis"
    name.length > 14 -> name.take(12) + "…"
    else -> name
}

private fun guessPlatformId(consoleName: String): String? {
    val n = consoleName.lowercase()
    return when {
        "playstation portable" in n || n == "psp" -> "psp"
        "playstation vita" in n -> "psvita"
        "playstation 2" in n -> "ps2"
        "playstation" in n -> "ps1"
        "nintendo ds" in n -> "nds"
        "nintendo 3ds" in n || "3ds" in n -> "3ds"
        "game boy advance" in n -> "gba"
        "game boy color" in n -> "gbc"
        "game boy" in n -> "gb"
        "gamecube" in n -> "gamecube"
        "nintendo 64" in n -> "n64"
        "super nintendo" in n || "snes" in n -> "snes"
        "nes" in n || "famicom" in n -> "nes"
        "dreamcast" in n -> "dreamcast"
        "saturn" in n -> "saturn"
        "genesis" in n || "mega drive" in n -> "genesis"
        "wii u" in n -> "wiiu"
        "wii" in n -> "wii"
        "switch" in n -> "switch"
        "arcade" in n -> "arcade"
        else -> null
    }
}
