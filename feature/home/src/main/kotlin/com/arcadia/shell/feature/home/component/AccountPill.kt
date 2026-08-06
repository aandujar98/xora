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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.arcadia.shell.feature.home.AccountPanelRow
import com.arcadia.shell.feature.home.CircleMemberUi
import com.arcadia.shell.feature.home.SocialMenuTab
import com.arcadia.shell.feature.home.SocialMenuUiState
import com.arcadia.shell.feature.home.SocialPresence

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
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val pillFriends = remember(socialMenu.circlePins, socialMenu.steam.friends, socialMenu.discord.friends) {
        circlePillAvatars(socialMenu)
    }
    val onlineAcross = socialMenu.friendsBadgeCount
    val extraOnline = (onlineAcross - pillFriends.size).coerceAtLeast(0)
    // Use window pixels ÷ current (fitted) density so Auto UI-fit cannot push the panel off-screen.
    val view = LocalView.current
    val density = LocalDensity.current
    val windowCap = remember(view, density) {
        val heightPx = view.rootView.height.takeIf { it > 0 }
            ?: view.resources.displayMetrics.heightPixels
        with(density) { (heightPx * 0.72f).toDp() }
    }

    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = if (expanded) 400.dp else 240.dp)
            .heightIn(max = windowCap + 56.dp),
    ) {
        // Cap against parent constraints and the real window so Auto UI-fit cannot clip LT.
        val panelCap = if (maxHeight.value.isFinite()) {
            min(windowCap, (maxHeight - 56.dp).coerceAtLeast(120.dp))
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
                Row(
                    modifier = Modifier
                        .liquidGlass(
                            shape = ArcadiaGlass.PillShape,
                            tone = GlassTone.OverMedia,
                            intensity = GlassIntensity.Standard,
                        )
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StackedCircleAvatars(members = pillFriends)
                    if (extraOnline > 0) {
                        Text(
                            text = "+$extraOnline",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = glass.contentMuted,
                        )
                    }
                    if (pillFriends.isEmpty()) {
                        Text(
                            text = if (onlineAcross > 0) "$onlineAcross online" else "Social",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    TriggerGlyph(letter = "LT")
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
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(max = panelCap)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun StackedCircleAvatars(members: List<CircleMemberUi>) {
    if (members.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        members.forEachIndexed { index, member ->
            Box(
                modifier = Modifier
                    .zIndex((members.size - index).toFloat())
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF0C1524), CircleShape),
            ) {
                ProfileAvatar(
                    displayName = member.displayName,
                    presetId = "preset_0",
                    size = 28.dp,
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
