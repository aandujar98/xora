package com.arcadia.shell.feature.home.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.launcher.discord.preferAnimatedDiscordAvatarUrl
import com.arcadia.shell.launcher.notifications.FriendNetwork
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.toCopy

/** Matches collapsed LT: 16dp start + 12dp top + 48dp avatar + 6dp pad × 2 + 8dp gap. */
private val BannerBelowLtTop = 80.dp
private val BannerStart = 16.dp
private val BannerShape = ArcadiaGlass.PillShape
private val CardEdge = Color.White.copy(alpha = 0.25f)

/**
 * Top-left toast host, parked under the LT Social pill. Observes [ShellNotificationCenter.active].
 * Host only on the primary Activity composition in dual-display mode.
 */
@Composable
fun BoxScope.NotificationBannerHost(
    center: ShellNotificationCenter,
    modifier: Modifier = Modifier,
    ltExpanded: Boolean = false,
    onActivate: ((ShellNotification) -> Unit)? = null,
) {
    val active by center.active.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()

    AnimatedVisibility(
        visible = active != null && !ltExpanded,
        modifier = modifier
            .align(Alignment.TopStart)
            .padding(top = BannerBelowLtTop, start = BannerStart, end = 20.dp),
        enter = if (reduceMotion) {
            fadeIn()
        } else {
            slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetX = { -it },
            ) + fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            )
        },
        exit = if (reduceMotion) {
            fadeOut()
        } else {
            slideOutHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                targetOffsetX = { -it },
            ) + fadeOut()
        },
        label = "shellNotificationBanner",
    ) {
        val notification = active
        if (notification != null) {
            NotificationBanner(
                notification = notification,
                onDismiss = center::dismiss,
                onActivate = onActivate,
            )
        }
    }
}

@Composable
fun NotificationBanner(
    notification: ShellNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onActivate: ((ShellNotification) -> Unit)? = null,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val content = bannerContent(notification)
    val accessibility = listOfNotNull(
        content.category,
        content.body,
        content.subtitle.takeIf { it.isNotBlank() },
    ).joinToString(". ")

    Row(
        modifier = modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .xoraForegroundShadow(BannerShape)
            .liquidGlass(
                shape = BannerShape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
                shimmer = true,
            )
            .border(1.5.dp, CardEdge, BannerShape)
            .clickable(onClick = {
                if (onActivate != null) onActivate(notification)
                onDismiss()
            })
            .semantics { contentDescription = accessibility }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BannerAvatar(
            url = content.avatarUrl,
            fallback = content.avatarFallback,
            accent = content.accent,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Image(
                    painter = painterResource(content.categoryIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    colorFilter = ColorFilter.tint(glass.content),
                )
                Text(
                    text = content.category,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = content.body,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (content.subtitle.isNotBlank()) {
                Text(
                    text = content.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.contentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            val progressFraction = content.progressFraction
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = content.accent,
                    trackColor = glass.content.copy(alpha = 0.12f),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_banner_sora_mark),
            contentDescription = "XOrA",
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.CenterVertically),
        )
    }
}

@Composable
private fun BannerAvatar(
    url: String?,
    fallback: String,
    accent: Color,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(preferAnimatedDiscordAvatarUrl(url) ?: url)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = fallback.take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

private data class BannerContent(
    val category: String,
    @DrawableRes val categoryIconRes: Int,
    val body: String,
    val subtitle: String,
    val avatarUrl: String?,
    val avatarFallback: String,
    val accent: Color,
    val progressFraction: Float? = null,
)

private fun bannerContent(notification: ShellNotification): BannerContent {
    val copy = notification.toCopy()
    return when (notification) {
        is ShellNotification.AchievementUnlocked -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_trophy,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.badgeUrl,
            avatarFallback = "RA",
            accent = if (notification.hardcore) Color(0xFFFFC24B) else Color(0xFF37D6A0),
        )

        is ShellNotification.RetroAchievementsSignedIn -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_trophy,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = null,
            avatarFallback = "RA",
            accent = if (notification.hardcore) Color(0xFFFFC24B) else Color(0xFF37D6A0),
        )

        is ShellNotification.DiscordMessage -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_messages,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.sender.take(1).ifBlank { "D" },
            accent = Color(0xFF5865F2),
        )

        is ShellNotification.SteamMessage -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_messages,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.sender.take(1).ifBlank { "S" },
            accent = Color(0xFF66C0F4),
        )

        is ShellNotification.XoraMessage -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_messages,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.sender.take(1).ifBlank { "X" },
            accent = Color(0xFF0070D1),
        )

        is ShellNotification.XoraFriendRequest -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_friends,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.displayName.take(1).ifBlank { "X" },
            accent = Color(0xFF0070D1),
        )

        is ShellNotification.XoraNetplayInvite -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_friends,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.displayName.take(1).ifBlank { "X" },
            accent = Color(0xFF0070D1),
        )

        is ShellNotification.XoraSessionJoined -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_friends,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.displayName.take(1).ifBlank { "P" },
            accent = Color(0xFF0070D1),
        )

        is ShellNotification.FriendOnline -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_friends,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = notification.avatarUrl,
            avatarFallback = notification.displayName.take(1).ifBlank { "F" },
            accent = when (notification.network) {
                FriendNetwork.Discord -> Color(0xFF5865F2)
                FriendNetwork.Steam -> Color(0xFF66C0F4)
                FriendNetwork.Xora -> Color(0xFF0070D1)
            },
        )

        is ShellNotification.GameDownloading -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_download,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = null,
            avatarFallback = "↓",
            accent = Color(0xFF4A9BE0),
            progressFraction = notification.progressFraction,
        )

        is ShellNotification.InstallComplete -> BannerContent(
            category = copy.category,
            categoryIconRes = R.drawable.ic_banner_download,
            body = copy.body,
            subtitle = copy.subtitle,
            avatarUrl = null,
            avatarFallback = "✓",
            accent = Color(0xFF37D6A0),
        )
    }
}
