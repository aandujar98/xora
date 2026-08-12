package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaHazeSource
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.component.AccountPill
import com.arcadia.shell.feature.home.component.AchievementsPill
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.HeroTrailerLayer
import com.arcadia.shell.feature.home.component.ProfileEditSheet
import com.arcadia.shell.feature.home.component.SystemPill
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.TrailerRefs
import java.util.concurrent.TimeUnit


/**
 * Large artwork and metadata for whatever is currently selected.
 *
 * Rendered either as the upper portion of the single-screen layout or, on a dual-screen handheld,
 * alone on the second physical display. It is therefore given no assumptions about its own size.
 *
 * When [isLaunching] is true, UI chrome slides/fades away while the hero artwork holds (and gently
 * zooms) as the transition plate into the emulator.
 *
 * On the RSS feed page, pass [rssItem] so the hero shows article detail (and optional video).
 * On the Home hub, pass [showHomeWallpaper] so the hero shows the customizable atmospheric art.
 * On game select, the same wallpaper is drawn as a base layer under scraped ROM art so titles
 * without artwork still match Home, and titles with artwork are never covered by it.
 */
@Composable
fun HeroPane(
    game: Game?,
    profile: LocalProfile,
    profileAvatarModel: String?,
    raConfigured: Boolean,
    discordLinked: Boolean = false,
    accountPanelExpanded: Boolean,
    systemPanelExpanded: Boolean,
    achievementsPanelExpanded: Boolean,
    achievements: AchievementsUiState,
    quickLaunchGames: List<Game> = emptyList(),
    socialMenu: SocialMenuUiState = SocialMenuUiState(),
    accountPanelRows: List<AccountPanelRow> = emptyList(),
    accountPanelSelectedIndex: Int = 0,
    systemPanelSelectedIndex: Int = 0,
    systemProfile: SystemProfileCardState = SystemProfileCardState(),
    trailer: HeroTrailerState = HeroTrailerState(),
    isLaunching: Boolean = false,
    rssItem: RssFeedItem? = null,
    showHomeWallpaper: Boolean = false,
    homeWallpaperPath: String? = null,
    onToggleAccountPanel: () -> Unit,
    onToggleSystemPanel: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    notificationUnreadCount: Int = 0,
    onToggleAchievementsPanel: () -> Unit,
    onSelectSocialTab: (SocialMenuTab) -> Unit = {},
    onSelectAccountRow: (Int) -> Unit = {},
    onActivateAccountRow: (Int?) -> Unit = {},
    onSelectSystemRow: (Int) -> Unit = {},
    onActivateSystemRow: (Int?) -> Unit = {},
    onSystemStatusDraftChange: (String) -> Unit = {},
    onSaveCustomStatus: () -> Unit = {},
    onClearCustomStatus: () -> Unit = {},
    profileEditRequest: Int = 0,
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit,
    onSelectAvatarPreset: (presetId: String) -> Unit,
    onRequestLocalAvatar: () -> Unit,
    onUseRaAvatar: () -> Unit,
    onUseDiscordAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit,
    onLoginRetroAchievements: (username: String, password: String) -> Unit,
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit,
    onSignOutRetroAchievements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chrome exits quickly; artwork eases over the full cinematic hold.
    val chromeProgress by animateFloatAsState(
        targetValue = if (isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Launch),
        label = "heroLaunchChrome",
    )
    val holdProgress by animateFloatAsState(
        targetValue = if (isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.LaunchHold),
        label = "heroLaunchHold",
    )
    val accountExpanded = accountPanelExpanded && !isLaunching
    val systemExpanded = systemPanelExpanded && !isLaunching
    val achievementsExpanded = achievementsPanelExpanded && !isLaunching
    val chromeAlpha = 1f - chromeProgress
    val chromeSlidePx = chromeProgress * 72f
    val artworkScale = 1f + (holdProgress * 0.06f)
    var profileEditing by remember { mutableStateOf(false) }

    LaunchedEffect(profileEditRequest) {
        if (profileEditRequest > 0) profileEditing = true
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        val heroEnter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
        val heroExit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
        // Game-select (and empty) use the home wallpaper as a base; ROM art draws above it.
        // Home hub still owns the whole pane via [showHomeWallpaper] so chrome stays atmospheric.
        if (!showHomeWallpaper && rssItem == null) {
            HomeWallpaper(
                customPath = homeWallpaperPath,
                dim = false,
                modifier = Modifier
                    .fillMaxSize()
                    .arcadiaHazeSource(zIndex = 0f)
                    .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale },
            )
        }
        when {
            showHomeWallpaper -> {
                HomeWallpaper(
                    customPath = homeWallpaperPath,
                    dim = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .arcadiaHazeSource(zIndex = 0f)
                        .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale },
                )
            }
            rssItem != null -> {
            AnimatedContent(
                targetState = rssItem,
                transitionSpec = { heroEnter togetherWith heroExit },
                contentKey = { it.id },
                label = "rssHero",
                modifier = Modifier
                    .fillMaxSize()
                    .arcadiaHazeSource(zIndex = 0f),
            ) { article ->
                RssHeroContent(
                    item = article,
                    chromeAlpha = chromeAlpha,
                    chromeSlidePx = chromeSlidePx,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            }
            else -> {
            AnimatedContent(
                targetState = game,
                transitionSpec = { heroEnter togetherWith heroExit },
                contentKey = { it?.id },
                label = "heroArtwork",
                modifier = Modifier
                    .fillMaxSize()
                    .arcadiaHazeSource(zIndex = 0f),
            ) { current ->
                if (current == null) {
                    HeroEmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    HeroContent(
                        game = current,
                        trailer = trailer,
                        artworkScale = artworkScale,
                        chromeAlpha = chromeAlpha,
                        chromeSlidePx = chromeSlidePx,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            }
        }

        AccountPill(
            expanded = accountExpanded,
            socialMenu = socialMenu,
            profile = profile,
            profileAvatarModel = profileAvatarModel,
            accountRows = accountPanelRows,
            selectedRowIndex = accountPanelSelectedIndex,
            onToggle = onToggleAccountPanel,
            onSelectTab = onSelectSocialTab,
            onSelectRow = onSelectAccountRow,
            onActivateRow = onActivateAccountRow,
            onFriendSearchChange = onFriendSearchChange,
            onReplyDraftChange = onReplyDraftChange,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = -chromeSlidePx
                },
        )
        SystemPill(
            profile = profile,
            avatarImageModel = profileAvatarModel,
            raUsername = achievements.profile?.username,
            raScore = achievements.profile?.totalPoints,
            recentAchievements = achievements.recent,
            jumpBackGames = quickLaunchGames.take(3),
            systemProfile = systemProfile,
            expanded = systemExpanded,
            selectedRowIndex = systemPanelSelectedIndex,
            onToggle = onToggleSystemPanel,
            onSelectRow = onSelectSystemRow,
            onActivateRow = onActivateSystemRow,
            onStatusDraftChange = onSystemStatusDraftChange,
            onSaveCustomStatus = onSaveCustomStatus,
            onClearCustomStatus = onClearCustomStatus,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = -chromeSlidePx
                },
        )

        AchievementsPill(
            expanded = achievementsExpanded,
            state = achievements,
            onToggle = onToggleAchievementsPanel,
            onSelectTab = onSelectAchievementsTab,
            onLogin = onLoginRetroAchievements,
            onLoginWithApiKey = onLoginRetroAchievementsWithApiKey,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = chromeSlidePx
                },
        )

        if (profileEditing) {
            ProfileEditSheet(
                profile = profile,
                avatarImageModel = profileAvatarModel,
                raConfigured = raConfigured,
                discordLinked = discordLinked,
                onDismiss = { profileEditing = false },
                onSave = onSaveProfile,
                onSelectAvatarPreset = onSelectAvatarPreset,
                onRequestPhoto = onRequestLocalAvatar,
                onUseRaAvatar = onUseRaAvatar,
                onUseDiscordAvatar = onUseDiscordAvatar,
                onClearAvatar = onClearAvatar,
            )
        }
    }
}

@Composable
private fun HeroContent(
    game: Game,
    trailer: HeroTrailerState,
    artworkScale: Float,
    chromeAlpha: Float,
    chromeSlidePx: Float,
    modifier: Modifier = Modifier,
) {
    val fullBackgroundTrailer =
        trailer.active && trailer.displayMode == TrailerDisplayMode.FullBackground
    val cornerPipTrailer =
        trailer.active && trailer.displayMode == TrailerDisplayMode.CornerPip

    Box(modifier = modifier) {
        // Only paint scraped art when it exists — ArtworkImage's empty fallback is an opaque tile
        // that would hide the home wallpaper underneath.
        val artPath = game.heroImagePath ?: game.boxArtPath
        if (!fullBackgroundTrailer && !artPath.isNullOrBlank()) {
            ArtworkImage(
                path = artPath,
                contentDescription = null,
                fallbackText = "",
                contentScale = ContentScale.Crop,
                // One hero is on screen at a time and it is the largest bitmap the app decodes, so it
                // is kept out of the memory cache to leave that budget for grid thumbnails.
                cacheInMemory = false,
                decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    },
            )
        }

        // Full-background trailer replaces artwork, then the scrim/metadata draw on top.
        if (fullBackgroundTrailer) {
            HeroTrailerLayer(
                state = trailer,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    },
            )
        }

        // Artwork brightness is unpredictable across scrapers, so text always sits on its own scrim
        // rather than relying on the image being dark enough. Soften the scrim as chrome exits so
        // the artwork becomes the launch plate.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.35f + (chromeAlpha * 0.65f) }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        // Logo + metadata float over the artwork scrim — no glass plate / border chrome.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 28.dp, end = 28.dp, bottom = 24.dp)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = chromeSlidePx
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            game.logoImagePath?.let { logo ->
                ArtworkImage(
                    path = logo,
                    contentDescription = game.title,
                    fallbackText = "",
                    contentScale = ContentScale.Fit,
                    cacheInMemory = false,
                    decodeMaxEdgePx = 720,
                    modifier = Modifier.fillMaxWidth(0.55f).fillMaxHeight(0.22f),
                )
            } ?: Text(
                text = game.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (game.playCount > 0) {
                MetadataRow(game = game)
            }
        }

        // PIP sits above the scrim so it stays visible, but HeroPane pills still draw after this.
        if (cornerPipTrailer) {
            HeroTrailerLayer(
                state = trailer,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = chromeAlpha },
            )
        }
    }
}

@Composable
private fun MetadataRow(
    game: Game,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetadataItem(label = "Played", value = "${game.playCount}×")
        MetadataItem(label = "Playtime", value = formatDuration(game.playTimeMs))
    }
}

@Composable
private fun MetadataItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Nothing selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RssHeroContent(
    item: RssFeedItem,
    chromeAlpha: Float,
    chromeSlidePx: Float,
    modifier: Modifier = Modifier,
) {
    var playingVideo by remember(item.id) { mutableStateOf(false) }
    val videoRef = remember(item.videoUrl) {
        item.videoUrl?.let { TrailerRefs.parse(it) }?.let(TrailerRefs::encode)
    }

    Box(modifier = modifier) {
        if (playingVideo && videoRef != null) {
            HeroTrailerLayer(
                state = HeroTrailerState(
                    active = true,
                    trailerUrl = videoRef,
                    displayMode = TrailerDisplayMode.FullBackground,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        } else if (item.imageUrl != null) {
            val platformContext = LocalPlatformContext.current
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(item.imageUrl)
                    .crossfade(180)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.2f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.85f)
                .padding(start = 28.dp, end = 28.dp, bottom = 24.dp)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = chromeSlidePx
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(item.source)
                    item.publishedAt?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            item.description?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (videoRef != null) {
                TextButton(
                    onClick = { playingVideo = !playingVideo },
                ) {
                    Text(
                        text = if (playingVideo) "Show article image" else "Play video",
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
