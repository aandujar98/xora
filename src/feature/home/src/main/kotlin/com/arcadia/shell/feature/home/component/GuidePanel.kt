package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.AchievementsUiState
import com.arcadia.shell.feature.home.GuideRow
import com.arcadia.shell.feature.home.GuideUiState
import com.arcadia.shell.model.Game

/**
 * Xbox-Guide-inspired overlay: profile, quick launch, friends, and shell shortcuts.
 * Controller-first — U/D moves [GuideUiState.selectedIndex], A confirms, B / Start+Select closes.
 */
@Composable
fun GuidePanel(
    guide: GuideUiState,
    profile: LocalProfile,
    profileAvatarModel: String?,
    achievements: AchievementsUiState,
    onSelectIndex: (Int) -> Unit,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val listState = rememberLazyListState()

    LaunchedEffect(guide.selectedIndex, guide.rows.size) {
        if (guide.rows.isEmpty()) return@LaunchedEffect
        val index = guide.selectedIndex.coerceIn(0, guide.rows.lastIndex)
        listState.animateScrollToItem(index)
    }

    AnimatedVisibility(
        visible = guide.open,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
            scaleIn(
                animationSpec = arcadiaTween(ArcadiaMotion.Medium),
                initialScale = 0.96f,
            ),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
            scaleOut(
                animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                targetScale = 0.98f,
            ),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.88f)
                    .liquidGlass(
                        shape = ArcadiaGlass.PanelShape,
                        tone = GlassTone.Surface,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .clickable(enabled = false, onClick = {})
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "Guide",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = glass.content,
                )
                Text(
                    text = "Start+Select to close · B Back · A Select",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.contentMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(
                        items = guide.rows,
                        key = { index, row -> guideRowKey(row, index) },
                    ) { index, row ->
                        val selected = index == guide.selectedIndex
                        when (row) {
                            GuideRow.Profile -> {
                                SectionLabel(text = "Profile", color = glass.contentMuted)
                                GuideProfileRow(
                                    profile = profile,
                                    avatarModel = profileAvatarModel,
                                    achievements = achievements,
                                    selected = selected,
                                    onClick = {
                                        onSelectIndex(index)
                                        onActivate()
                                    },
                                )
                            }

                            is GuideRow.QuickLaunch -> {
                                if (isFirstOfKind(guide.rows, index) { it is GuideRow.QuickLaunch }) {
                                    SectionLabel(text = "Quick launch", color = glass.contentMuted)
                                }
                                GuideGameRow(
                                    game = row.game,
                                    selected = selected,
                                    onClick = {
                                        onSelectIndex(index)
                                        onActivate()
                                    },
                                )
                            }

                            is GuideRow.Friend -> {
                                if (isFirstOfKind(guide.rows, index) { it is GuideRow.Friend }) {
                                    SectionLabel(text = "Friends", color = glass.contentMuted)
                                }
                                GuideFriendRow(
                                    friend = row,
                                    selected = selected,
                                    onClick = {
                                        onSelectIndex(index)
                                        onActivate()
                                    },
                                )
                            }

                            GuideRow.Settings,
                            GuideRow.Achievements,
                            GuideRow.SwapScreens,
                            GuideRow.SignInRa,
                            -> {
                                if (isFirstShortcut(guide.rows, index)) {
                                    SectionLabel(text = "Shortcuts", color = glass.contentMuted)
                                }
                                GuideShortcutRow(
                                    label = shortcutLabel(row),
                                    selected = selected,
                                    onClick = {
                                        onSelectIndex(index)
                                        onActivate()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun GuideProfileRow(
    profile: LocalProfile,
    avatarModel: String?,
    achievements: AchievementsUiState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val points = achievements.profile?.totalPoints
    GuideFocusRow(selected = selected, onClick = onClick) {
        ProfileAvatar(
            displayName = profile.displayName,
            presetId = profile.avatarPresetId,
            size = 48.dp,
            imageModel = avatarModel,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    !achievements.credentials.isConfigured -> "Local profile"
                    points != null -> "$points RA points"
                    else -> "RetroAchievements signed in"
                },
                style = MaterialTheme.typography.bodySmall,
                color = glass.contentMuted,
            )
        }
    }
}

@Composable
private fun GuideGameRow(
    game: Game,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val platformContext = LocalPlatformContext.current
    GuideFocusRow(selected = selected, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(glass.tintSubtle),
            contentAlignment = Alignment.Center,
        ) {
            if (!game.gridArt.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(platformContext)
                        .data(game.gridArt)
                        .crossfade(120)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = game.title.take(1),
                    color = glass.contentMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyLarge,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(game.platform.shortName)
                    if (game.favorite) append(" · Favourite")
                    if (game.lastPlayedAt != null) append(" · Recent")
                },
                style = MaterialTheme.typography.labelSmall,
                color = glass.contentMuted,
            )
        }
        Text(
            text = "A",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun GuideFriendRow(
    friend: GuideRow.Friend,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    GuideFocusRow(selected = selected, onClick = onClick) {
        ProfileAvatar(
            displayName = friend.displayName,
            presetId = "preset_0",
            size = 36.dp,
            imageModel = friend.avatarUrl,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            friend.currentGame?.takeIf { it.isNotBlank() }?.let { game ->
                Text(
                    text = game,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.contentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (friend.online) "Online" else "Offline",
            style = MaterialTheme.typography.labelSmall,
            color = if (friend.online) {
                Color(0xFF37D6A0)
            } else {
                glass.contentMuted.copy(alpha = 0.55f)
            },
        )
    }
}

@Composable
private fun GuideShortcutRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    GuideFocusRow(selected = selected, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = glass.content,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GuideFocusRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                        )
                } else {
                    Modifier.background(glass.tintSubtle.copy(alpha = 0.35f))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private fun shortcutLabel(row: GuideRow): String = when (row) {
    GuideRow.Settings -> "Settings"
    GuideRow.Achievements -> "Achievements"
    GuideRow.SwapScreens -> "Swap screens"
    GuideRow.SignInRa -> "Sign in to RetroAchievements"
    else -> ""
}

private fun guideRowKey(row: GuideRow, index: Int): String = when (row) {
    GuideRow.Profile -> "profile"
    is GuideRow.QuickLaunch -> "game:${row.game.id}"
    is GuideRow.Friend -> "friend:${row.id}"
    GuideRow.Settings -> "settings"
    GuideRow.Achievements -> "achievements"
    GuideRow.SwapScreens -> "swap"
    GuideRow.SignInRa -> "signin"
} + "#$index"

private fun isFirstOfKind(rows: List<GuideRow>, index: Int, predicate: (GuideRow) -> Boolean): Boolean {
    if (!predicate(rows[index])) return false
    return rows.indexOfFirst(predicate) == index
}

private fun isFirstShortcut(rows: List<GuideRow>, index: Int): Boolean =
    isFirstOfKind(rows, index) {
        it == GuideRow.Settings ||
            it == GuideRow.Achievements ||
            it == GuideRow.SwapScreens ||
            it == GuideRow.SignInRa
    }
