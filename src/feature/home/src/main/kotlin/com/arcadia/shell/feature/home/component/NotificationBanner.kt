package com.arcadia.shell.feature.home.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.launcher.notifications.FriendNetwork
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.toCopy

private val BannerPanel = Color(0xFFF2F2F2)
private val BannerText = Color(0xFF2A2A2A)
private val BannerTextMuted = Color(0xFF5A5A5A)
private val BannerShape = RoundedCornerShape(10.dp)
private val AvatarShape = RoundedCornerShape(6.dp)

/**
 * Top-left PS4-style toast host. Observes [ShellNotificationCenter.active].
 * Host only on the primary Activity composition in dual-display mode.
 */
@Composable
fun BoxScope.NotificationBannerHost(
    center: ShellNotificationCenter,
    modifier: Modifier = Modifier,
) {
    val active by center.active.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()

    AnimatedVisibility(
        visible = active != null,
        modifier = modifier
            .align(Alignment.TopStart)
            .padding(top = 20.dp, start = 20.dp, end = 20.dp),
        enter = if (reduceMotion) {
            fadeIn()
        } else {
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { -it },
            ) + fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            )
        },
        exit = if (reduceMotion) {
            fadeOut()
        } else {
            slideOutVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                targetOffsetY = { -it },
            ) + fadeOut()
        },
        label = "shellNotificationBanner",
    ) {
        val notification = active
        if (notification != null) {
            NotificationBanner(
                notification = notification,
                onDismiss = center::dismiss,
            )
        }
    }
}

@Composable
fun NotificationBanner(
    notification: ShellNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = bannerContent(notification)
    val accessibility = listOfNotNull(
        content.category,
        content.body,
        content.subtitle.takeIf { it.isNotBlank() },
    ).joinToString(". ")

    Row(
        modifier = modifier
            .widthIn(min = 280.dp, max = 420.dp)
            .shadow(
                elevation = 10.dp,
                shape = BannerShape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.22f),
            )
            .clip(BannerShape)
            .background(BannerPanel.copy(alpha = 0.96f))
            .clickable(onClick = onDismiss)
            .semantics { contentDescription = accessibility }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(BannerText),
                )
                Text(
                    text = content.category,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BannerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = content.body,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = BannerText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (content.subtitle.isNotBlank()) {
                Text(
                    text = content.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BannerTextMuted,
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
                    trackColor = BannerText.copy(alpha = 0.12f),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_banner_sora_mark),
            contentDescription = "XOrA",
            modifier = Modifier
                .size(22.dp)
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
            .size(48.dp)
            .clip(AvatarShape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(AvatarShape),
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
