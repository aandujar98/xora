package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.min
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.feature.home.AccountPanelRow
import com.arcadia.shell.feature.home.CircleMemberUi
import com.arcadia.shell.feature.home.SocialMenuTab
import com.arcadia.shell.feature.home.SocialMenuUiState
import com.arcadia.shell.feature.home.SocialPresence

private val NotificationRed = Color(0xFFFF3B30)

/**
 * Collapsed LT chrome. 48dp discs keep the stacked-pill silhouette without crowding the corner
 * (80 / 64 were too large; Figma `56:160` was 73px).
 */
private val AvatarSize = 48.dp
/** Center-to-center step; half of [AvatarSize] so each friend covers half the previous disc. */
private val AvatarPitch = 24.dp
private val PillPad = 6.dp
private val BadgeSize = 22.dp

@Composable
fun AccountPill(
    expanded: Boolean,
    socialMenu: SocialMenuUiState,
    profile: LocalProfile,
    profileAvatarModel: String?,
    accountRows: List<AccountPanelRow>,
    selectedRowIndex: Int,
    onToggle: () -> Unit,
    onSelectTab: (SocialMenuTab) -> Unit,
    onSelectRow: (Int) -> Unit,
    onActivateRow: (Int?) -> Unit,
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onClearNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val pillFriends = remember(socialMenu.circlePins, socialMenu.steam.friends, socialMenu.discord.friends) {
        circlePillAvatars(socialMenu)
    }
    // Same tally as the panel's Notifications pill so the collapsed badge cannot disagree.
    val notificationCount = socialMenu.messagesBadgeCount + socialMenu.recentNotifications.size
    // Use window pixels ÷ current (fitted) density so Auto UI-fit cannot push the panel off-screen.
    val view = LocalView.current
    val density = LocalDensity.current
    val windowCap = remember(view, density) {
        val heightPx = view.rootView.height.takeIf { it > 0 }
            ?: view.resources.displayMetrics.heightPixels
        with(density) { (heightPx * 0.90f).toDp() }
    }

    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = if (expanded) 400.dp else 240.dp)
            .heightIn(max = windowCap + 24.dp),
    ) {
        // Cap against parent constraints and the real window so Auto UI-fit cannot clip LT.
        val panelCap = if (maxHeight.value.isFinite()) {
            min(windowCap, (maxHeight - 8.dp).coerceAtLeast(160.dp))
        } else {
            windowCap
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.heightIn(max = maxHeight),
        ) {
            // Collapsed LT chrome hides while the panel is open; Back / LT restores it.
            AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
                    animationSpec = arcadiaTween(ArcadiaMotion.Medium),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.1f, 0f),
                ),
                exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
                    animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(0.1f, 0f),
                ),
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .xoraForegroundShadow(ArcadiaGlass.PillShape)
                            .liquidGlass(
                                shape = ArcadiaGlass.PillShape,
                                tone = GlassTone.OverMedia,
                                intensity = GlassIntensity.Strong,
                                shimmer = true,
                            )
                            .clickable(onClick = onToggle)
                            .padding(PillPad),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Avatars only — the Figma pill carries no online-count label.
                        if (pillFriends.isEmpty()) {
                            ProfileAvatar(
                                displayName = profile.displayName,
                                presetId = profile.avatarPresetId,
                                size = AvatarSize,
                                imageModel = profileAvatarModel,
                                borderColor = Color.Transparent,
                            )
                        } else {
                            StackedCircleAvatars(members = pillFriends)
                        }
                    }
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-3).dp)
                                .size(BadgeSize)
                                .clip(CircleShape)
                                .background(NotificationRed)
                                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (notificationCount > 9) "9+" else "$notificationCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
                    animationSpec = arcadiaTween(ArcadiaMotion.Medium),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.1f, 0f),
                ),
                exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
                    animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(0.1f, 0f),
                ),
            ) {
                SocialMenuPanel(
                    social = socialMenu,
                    profile = profile,
                    profileAvatarModel = profileAvatarModel,
                    accountRows = accountRows,
                    selectedRowIndex = selectedRowIndex,
                    onSelectTab = onSelectTab,
                    onSelectRow = onSelectRow,
                    onActivateRow = onActivateRow,
                    onFriendSearchChange = onFriendSearchChange,
                    onReplyDraftChange = onReplyDraftChange,
                    onClearNotifications = onClearNotifications,
                    maxHeight = panelCap,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StackedCircleAvatars(members: List<CircleMemberUi>) {
    if (members.isEmpty()) return
    val stackWidth = AvatarSize + AvatarPitch * (members.lastIndex)
    Box(
        modifier = Modifier.size(width = stackWidth, height = AvatarSize),
    ) {
        members.forEachIndexed { index, member ->
            Box(
                modifier = Modifier
                    .offset(x = AvatarPitch * index)
                    .zIndex(index.toFloat())
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF0C1524), CircleShape),
            ) {
                ProfileAvatar(
                    displayName = member.displayName,
                    presetId = "preset_0",
                    size = AvatarSize,
                    imageModel = member.avatarUrl,
                    borderColor = Color.Transparent,
                )
            }
        }
    }
}

private fun circlePillAvatars(social: SocialMenuUiState): List<CircleMemberUi> {
    val circle = social.circleMembers
    if (circle.isNotEmpty()) {
        return circle
            .sortedByDescending { it.presence != SocialPresence.Offline }
            .take(4)
    }
    // Fallback: online Steam friends as soft preview before Circle is configured.
    return social.steam.friends
        .filter { it.presence != SocialPresence.Offline }
        .take(4)
        .map {
            CircleMemberUi(
                pin = com.arcadia.shell.datastore.CirclePin(
                    source = com.arcadia.shell.datastore.CirclePinSource.Steam,
                    id = it.steamId,
                ),
                displayName = it.displayName,
                avatarUrl = it.avatarUrl,
                presence = it.presence,
                activityLabel = it.currentGame,
            )
        }
        .ifEmpty {
            social.steam.friends.take(4).map {
                CircleMemberUi(
                    pin = com.arcadia.shell.datastore.CirclePin(
                        source = com.arcadia.shell.datastore.CirclePinSource.Steam,
                        id = it.steamId,
                    ),
                    displayName = it.displayName,
                    avatarUrl = it.avatarUrl,
                    presence = it.presence,
                    activityLabel = it.currentGame,
                )
            }
        }
}

@Composable
internal fun TriggerGlyph(letter: String, modifier: Modifier = Modifier) {
    val wide = letter.length > 1
    Box(
        modifier = modifier
            .then(if (wide) Modifier.widthIn(min = 28.dp) else Modifier.size(22.dp))
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = if (wide) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
