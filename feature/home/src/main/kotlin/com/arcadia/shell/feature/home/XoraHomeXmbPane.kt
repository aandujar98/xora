package com.arcadia.shell.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.arcadiaHazeSource
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.launchBackdropScale
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.designsystem.rememberLaunchCinematic
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.AccountPill
import com.arcadia.shell.feature.home.component.AchievementsPill
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.HeroTrailerLayer
import com.arcadia.shell.feature.home.component.NowPlayingPill
import com.arcadia.shell.feature.home.component.ProfileEditSheet
import com.arcadia.shell.feature.home.component.SystemPill
import com.arcadia.shell.feature.home.component.XmbStarFieldLayer
import com.arcadia.shell.libretro.XoraAspectLetterbox
import com.arcadia.shell.model.Game
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay

/**
 * PSP / PS3-style Cross Media Bar.
 *
 * Geometry: one fixed crosshair. Category icons share that Y; menu rows share that X.
 * Selected category + selected item meet at the cross. Glyphs sit in a fixed-width slot so
 * every icon column and every title column stay locked — scale/alpha via [graphicsLayer] only.
 */
@Composable
fun XoraHomeXmbPane(
    state: HomeUiState,
    onSelectCategory: (Int) -> Unit,
    onSelectItem: (Int) -> Unit,
    onActivateItem: () -> Unit,
    onToggleAccountPanel: () -> Unit = {},
    onToggleSystemPanel: () -> Unit = {},
    onToggleAchievementsPanel: () -> Unit = {},
    onSelectSocialTab: (SocialMenuTab) -> Unit = {},
    onSelectAccountRow: (Int) -> Unit = {},
    onActivateAccountRow: (Int?) -> Unit = {},
    onSelectSystemRow: (Int) -> Unit = {},
    onActivateSystemRow: (Int?) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onSystemStatusDraftChange: (String) -> Unit = {},
    onSaveCustomStatus: () -> Unit = {},
    onClearCustomStatus: () -> Unit = {},
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit = { _, _ -> },
    onSelectAvatarPreset: (presetId: String) -> Unit = {},
    onRequestLocalAvatar: () -> Unit = {},
    onUseRaAvatar: () -> Unit = {},
    onUseDiscordAvatar: () -> Unit = {},
    onUseXoraAvatar: () -> Unit = {},
    onXoraPresenceMode: (com.arcadia.shell.xoranetwork.XoraPresenceMode) -> Unit = {},
    onClearAvatar: () -> Unit = {},
    onClearNotifications: () -> Unit = {},
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit = {},
    onLoginRetroAchievements: (username: String, password: String) -> Unit = { _, _ -> },
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit = { _, _ -> },
    onSignOutRetroAchievements: () -> Unit = {},
    onToggleNowPlaying: () -> Unit = {},
    onSkipPreviousTrack: () -> Unit = {},
    onSkipNextTrack: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onPhotoCommand: (PhotoPaneCommand) -> Unit = {},
    onDashboardCommand: (DashboardCommand) -> Unit = {},
    showPillChrome: Boolean = true,
    modifier: Modifier = Modifier,
    /** Full-bleed layer above the XMB cross but below the pill chrome. */
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val xmb = state.xoraXmb
    val heroGame = xmb.focusGame?.takeIf {
        xmb.depth == XoraXmbDepth.Roms ||
            xmb.selectedItem?.action is XoraXmbAction.LaunchContinueOrFavorite ||
            xmb.selectedItem?.action is XoraXmbAction.LaunchGame
    }
    // Browsing music paints the focused album / song art; Now Playing paints the playing cover.
    val musicArtPath = when (xmb.depth) {
        XoraXmbDepth.MusicAlbums, XoraXmbDepth.MusicTracks ->
            xmb.selectedItem?.heroPath ?: xmb.selectedItem?.artPath
        XoraXmbDepth.NowPlaying -> state.music.nowPlaying.track?.albumArtUri
        XoraXmbDepth.Category -> xmb.selectedItem?.heroPath
        else -> null
    }
    val backdropArtPath = musicArtPath
        ?: heroGame?.heroImagePath ?: heroGame?.boxArtPath ?: heroGame?.logoImagePath
    val fullTrailer = state.trailer.active &&
        state.trailer.displayMode == TrailerDisplayMode.FullBackground

    val cinematic = rememberLaunchCinematic(state.isLaunching)
    val chromeAlpha = cinematic.chromeAlpha
    val trayOpen = state.homeHub.vitaShortcutTrayOpen
    val trayRecede by animateFloatAsState(
        targetValue = if (trayOpen) 1f else 0f,
        animationSpec = if (trayOpen) {
            spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            arcadiaTween(ArcadiaMotion.Medium)
        },
        label = "xmbTrayRecede",
    )
    val recedeScale = 1f - (trayRecede * 0.12f)
    val recedeAlpha = 1f - trayRecede
    val artworkScale = launchBackdropScale(cinematic.zoom)
    val backdropMotion = xmbBackdropMotion(
        launchScale = artworkScale,
        wallpaperAlpha = cinematic.wallpaperAlpha,
    )

    XoraAspectLetterbox(
        mode = state.xoraEmulator.aspectMode,
        modifier = modifier.fillMaxSize(),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Theme / custom wallpaper must remain the base plate — it zooms, then fades to black.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .arcadiaHazeSource(zIndex = 0f),
        ) {
            HomeWallpaper(
                customPath = state.homeHub.wallpaperPath,
                dim = false,
                alignX = state.homeHub.wallpaperAlignX,
                alignY = state.homeHub.wallpaperAlignY,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropMotion),
            )

            // Keep mounted so focus / back / cancel always crossfade (never unmount-snap).
            XoraRomHeroBackdrop(
                artPath = backdropArtPath,
                settleMs = if (xmb.depth == XoraXmbDepth.Roms) {
                    XMB_GAME_SELECT_SETTLE_MS
                } else {
                    XMB_FOCUS_SETTLE_MS
                },
                scrimAlpha = chromeAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropMotion)
                    .graphicsLayer { alpha = recedeAlpha },
            )

            HeroTrailerLayer(
                state = state.trailer,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropMotion)
                    .graphicsLayer { alpha = recedeAlpha },
            )

            if (fullTrailer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = chromeAlpha }
                        .background(Color.Black.copy(alpha = 0.38f)),
                )
            }
        }

        // Everything over the wallpaper is chrome. It fades out before the backdrop zooms.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chromeAlpha },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = recedeAlpha
                        scaleX = recedeScale
                        scaleY = recedeScale
                    },
            ) {
                // Wallpaper + XMB are Haze sources so Friends/Profile/RA plates blur only
                // the pixels sitting under the modal, not the rest of the chrome.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .arcadiaHazeSource(zIndex = 1f),
                ) {
                if (!fullTrailer) {
                    // PS5-style ambient dust between the wallpaper and the menu chrome.
                    XmbStarFieldLayer(
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // System and ROM browsing are card rungs of the same menu, so drilling slides sideways
                // between them the way the PSP / PS3 shells do rather than cutting.
                val depthSlideMs = motionMillis(XMB_DEPTH_SLIDE_MS)
                AnimatedContent(
                    targetState = xmb.depth,
                    transitionSpec = {
                        val drillingIn = targetState.ordinal > initialState.ordinal
                        val slide = tween<IntOffset>(depthSlideMs, easing = FastOutSlowInEasing)
                        val enter = slideInHorizontally(slide) { width ->
                            if (drillingIn) width / 2 else -width / 2
                        } + fadeIn(tween(depthSlideMs, easing = FastOutSlowInEasing))
                        val exit = slideOutHorizontally(slide) { width ->
                            if (drillingIn) -width / 3 else width / 3
                        } + fadeOut(tween(depthSlideMs / 2, easing = FastOutSlowInEasing))
                        enter togetherWith exit
                    },
                    label = "xmbDepth",
                    modifier = Modifier.fillMaxSize(),
                ) { depth ->
                when (depth) {
                    XoraXmbDepth.NowPlaying -> XoraNowPlayingPane(
                        state = state.music.nowPlaying,
                        onTogglePlayPause = onToggleNowPlaying,
                        onSkipPrevious = onSkipPreviousTrack,
                        onSkipNext = onSkipNextTrack,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        modifier = Modifier.fillMaxSize(),
                    )
                    XoraXmbDepth.Photos -> XoraPhotoViewerPane(
                        state = state.photos,
                        onCommand = onPhotoCommand,
                        modifier = Modifier.fillMaxSize(),
                    )
                    XoraXmbDepth.Dashboard -> XoraDashboardPane(
                        state = state.dashboard,
                        achievements = state.achievements,
                        onCommand = onDashboardCommand,
                        modifier = Modifier.fillMaxSize(),
                    )
                    XoraXmbDepth.Systems,
                    XoraXmbDepth.Roms,
                    XoraXmbDepth.DspAccounts,
                    XoraXmbDepth.MusicAlbums,
                    XoraXmbDepth.MusicTracks,
                    -> XoraCardBrowsePane(
                        items = xmb.items,
                        selectedIndex = xmb.itemIndex,
                        mode = when (depth) {
                            XoraXmbDepth.Systems -> CardBrowseMode.Systems
                            XoraXmbDepth.Roms -> CardBrowseMode.Roms
                            XoraXmbDepth.MusicAlbums -> CardBrowseMode.MusicAlbums
                            XoraXmbDepth.MusicTracks -> CardBrowseMode.MusicTracks
                            else -> CardBrowseMode.DspAccounts
                        },
                        onSelectItem = onSelectItem,
                        onActivateItem = onActivateItem,
                        modifier = Modifier.fillMaxSize(),
                        titleStyle = xmb.titleStyle,
                        trailer = state.trailer,
                    )
                    else -> XmbCross(
                        xmb = xmb,
                        introReveal = state.homeIntroReveal,
                        onSelectCategory = onSelectCategory,
                        onSelectItem = onSelectItem,
                        onActivateItem = onActivateItem,
                        modifier = Modifier.fillMaxSize(),
                        trailer = state.trailer,
                    )
                }
                }
                }

            }

            // Vita tray rides above the receding XMB. Pill chrome stays put so LT/RT
            // remain visible over the bubbles.
            overlayContent()

            if (showPillChrome) {
                XoraXmbPillChrome(
                    state = state,
                    onToggleAccountPanel = onToggleAccountPanel,
                    onToggleSystemPanel = onToggleSystemPanel,
                    onToggleAchievementsPanel = onToggleAchievementsPanel,
                    onSelectSocialTab = onSelectSocialTab,
                    onSelectAccountRow = onSelectAccountRow,
                    onActivateAccountRow = onActivateAccountRow,
                    onSelectSystemRow = onSelectSystemRow,
                    onActivateSystemRow = onActivateSystemRow,
                    onOpenNotifications = onOpenNotifications,
                    onSystemStatusDraftChange = onSystemStatusDraftChange,
                    onSaveCustomStatus = onSaveCustomStatus,
                    onClearCustomStatus = onClearCustomStatus,
                    onSaveProfile = onSaveProfile,
                    onSelectAvatarPreset = onSelectAvatarPreset,
                    onRequestLocalAvatar = onRequestLocalAvatar,
                    onUseRaAvatar = onUseRaAvatar,
                    onUseDiscordAvatar = onUseDiscordAvatar,
                    onUseXoraAvatar = onUseXoraAvatar,
                    onXoraPresenceMode = onXoraPresenceMode,
                    onClearAvatar = onClearAvatar,
                    onClearNotifications = onClearNotifications,
                    onFriendSearchChange = onFriendSearchChange,
                    onReplyDraftChange = onReplyDraftChange,
                    onSelectAchievementsTab = onSelectAchievementsTab,
                    onLoginRetroAchievements = onLoginRetroAchievements,
                    onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
                    onSignOutRetroAchievements = onSignOutRetroAchievements,
                )
            }
        }
    }
    }
}

/** Dual-screen hero companion for the focused XMB row. */
@Composable
fun XoraXmbHeroDetail(
    state: HomeUiState,
    onToggleAccountPanel: () -> Unit = {},
    onToggleSystemPanel: () -> Unit = {},
    onToggleAchievementsPanel: () -> Unit = {},
    onSelectSocialTab: (SocialMenuTab) -> Unit = {},
    onSelectAccountRow: (Int) -> Unit = {},
    onActivateAccountRow: (Int?) -> Unit = {},
    onSelectSystemRow: (Int) -> Unit = {},
    onActivateSystemRow: (Int?) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onSystemStatusDraftChange: (String) -> Unit = {},
    onSaveCustomStatus: () -> Unit = {},
    onClearCustomStatus: () -> Unit = {},
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit = { _, _ -> },
    onSelectAvatarPreset: (presetId: String) -> Unit = {},
    onRequestLocalAvatar: () -> Unit = {},
    onUseRaAvatar: () -> Unit = {},
    onUseDiscordAvatar: () -> Unit = {},
    onUseXoraAvatar: () -> Unit = {},
    onXoraPresenceMode: (com.arcadia.shell.xoranetwork.XoraPresenceMode) -> Unit = {},
    onClearAvatar: () -> Unit = {},
    onClearNotifications: () -> Unit = {},
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit = {},
    onLoginRetroAchievements: (username: String, password: String) -> Unit = { _, _ -> },
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit = { _, _ -> },
    onSignOutRetroAchievements: () -> Unit = {},
    showPillChrome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val xmb = state.xoraXmb
    val heroGame = xmb.focusGame
    val fullTrailer = state.trailer.active &&
        state.trailer.displayMode == TrailerDisplayMode.FullBackground
    val titleEnter = fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(ArcadiaMotion.Slow, easing = FastOutSlowInEasing)) { it / 5 } +
        scaleIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing), initialScale = 0.97f)
    val titleExit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(tween(120, easing = FastOutSlowInEasing)) { -it / 14 }

    val cinematic = rememberLaunchCinematic(state.isLaunching)
    val chromeAlpha = cinematic.chromeAlpha
    val artworkScale = launchBackdropScale(cinematic.zoom)
    val backdropMotion = xmbBackdropMotion(
        launchScale = artworkScale,
        wallpaperAlpha = cinematic.wallpaperAlpha,
    )
    val trayOpen = state.homeHub.vitaShortcutTrayOpen
    val trayRecede by animateFloatAsState(
        targetValue = if (trayOpen) 1f else 0f,
        animationSpec = if (trayOpen) {
            spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            arcadiaTween(ArcadiaMotion.Medium)
        },
        label = "xmbHeroTrayRecede",
    )
    val recedeAlpha = 1f - trayRecede

    XoraAspectLetterbox(
        mode = state.xoraEmulator.aspectMode,
        modifier = modifier.fillMaxSize(),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            HomeWallpaper(
                customPath = state.homeHub.wallpaperPath,
                dim = false,
                alignX = state.homeHub.wallpaperAlignX,
                alignY = state.homeHub.wallpaperAlignY,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropMotion),
            )
            XoraRomHeroBackdrop(
                artPath = xmb.selectedItem?.heroPath
                    ?: xmb.selectedItem?.artPath?.takeIf {
                        xmb.depth == XoraXmbDepth.MusicAlbums ||
                            xmb.depth == XoraXmbDepth.MusicTracks
                    }
                    ?: heroGame?.takeIf {
                    xmb.depth == XoraXmbDepth.Roms ||
                        xmb.selectedItem?.action is XoraXmbAction.LaunchContinueOrFavorite ||
                        xmb.selectedItem?.action is XoraXmbAction.LaunchGame
                }?.let { it.heroImagePath ?: it.boxArtPath ?: it.logoImagePath },
                settleMs = if (xmb.depth == XoraXmbDepth.Roms) {
                    XMB_GAME_SELECT_SETTLE_MS
                } else {
                    XMB_FOCUS_SETTLE_MS
                },
                scrimAlpha = chromeAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropMotion)
                    .graphicsLayer { alpha = recedeAlpha },
            )
            HeroTrailerLayer(
                state = state.trailer,
                modifier = Modifier
                    .fillMaxSize()
                    .then(backdropMotion)
                    .graphicsLayer { alpha = recedeAlpha },
            )
            if (fullTrailer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = chromeAlpha }
                        .background(Color.Black.copy(alpha = 0.38f)),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chromeAlpha },
        ) {
            AnimatedContent(
                targetState = Triple(
                    xmb.focusTitle,
                    xmb.focusSubtitle,
                    heroGame?.id to heroGame?.logoImagePath,
                ),
                transitionSpec = { titleEnter togetherWith titleExit },
                label = "xmbHeroTitle",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(28.dp),
            ) { (title, subtitle, logoKey) ->
                val logoPath = logoKey.second
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!logoPath.isNullOrBlank()) {
                        ArtworkImage(
                            path = logoPath,
                            contentDescription = title,
                            fallbackText = title,
                            contentScale = ContentScale.Fit,
                            cacheInMemory = false,
                            decodeMaxEdgePx = 720,
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .height(96.dp)
                                .fillMaxWidth(0.7f),
                        )
                    } else {
                        XoraTitleText(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            maxLines = 2,
                        )
                    }
                    subtitle?.let {
                        XoraSecondaryText(
                            text = it,
                            fontSize = 13.sp,
                            fillColor = Color.White,
                            maxLines = 2,
                        )
                    }
                }
            }

            if (showPillChrome) {
                XoraXmbPillChrome(
                    state = state,
                    onToggleAccountPanel = onToggleAccountPanel,
                    onToggleSystemPanel = onToggleSystemPanel,
                    onToggleAchievementsPanel = onToggleAchievementsPanel,
                    onSelectSocialTab = onSelectSocialTab,
                    onSelectAccountRow = onSelectAccountRow,
                    onActivateAccountRow = onActivateAccountRow,
                    onSelectSystemRow = onSelectSystemRow,
                    onActivateSystemRow = onActivateSystemRow,
                    onOpenNotifications = onOpenNotifications,
                    onSystemStatusDraftChange = onSystemStatusDraftChange,
                    onSaveCustomStatus = onSaveCustomStatus,
                    onClearCustomStatus = onClearCustomStatus,
                    onSaveProfile = onSaveProfile,
                    onSelectAvatarPreset = onSelectAvatarPreset,
                    onRequestLocalAvatar = onRequestLocalAvatar,
                    onUseRaAvatar = onUseRaAvatar,
                    onUseDiscordAvatar = onUseDiscordAvatar,
                    onUseXoraAvatar = onUseXoraAvatar,
                    onXoraPresenceMode = onXoraPresenceMode,
                    onClearAvatar = onClearAvatar,
                    onClearNotifications = onClearNotifications,
                    onFriendSearchChange = onFriendSearchChange,
                    onReplyDraftChange = onReplyDraftChange,
                    onSelectAchievementsTab = onSelectAchievementsTab,
                    onLoginRetroAchievements = onLoginRetroAchievements,
                    onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
                    onSignOutRetroAchievements = onSignOutRetroAchievements,
                )
            }
        }
    }
    }
}


/**
 * Launch-hold zoom for wallpaper / hero plates. Category and item navigation must not
 * pan or drift the backdrop — the menu moves; the sky stays put.
 */
private fun xmbBackdropMotion(launchScale: Float, wallpaperAlpha: Float = 1f): Modifier =
    Modifier.graphicsLayer {
        scaleX = launchScale
        scaleY = launchScale
        alpha = wallpaperAlpha
        translationX = 0f
        translationY = 0f
    }

internal data class IntroAppear(
    val scale: Float,
    val alpha: Float,
    val dropPx: Float,
)

@Composable
internal fun rememberIntroAppear(
    reveal: Boolean,
    delayMs: Int,
    reduceMotion: Boolean,
): IntroAppear {
    val progress = remember { Animatable(if (reveal) 1f else 0f) }
    LaunchedEffect(reveal, delayMs, reduceMotion) {
        if (!reveal) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (progress.value >= 0.999f) return@LaunchedEffect
        if (reduceMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(delayMs.toLong())
        progress.animateTo(
            1f,
            spring(dampingRatio = 0.48f, stiffness = 420f),
        )
    }
    val p = progress.value
    return IntroAppear(
        scale = 0.22f + 0.78f * p,
        alpha = p.coerceIn(0f, 1f),
        dropPx = (1f - p.coerceIn(0f, 1f)) * -22f,
    )
}

@Composable
private fun rememberIntroSlide(
    reveal: Boolean,
    delayMs: Int,
    reduceMotion: Boolean,
): Float {
    val slide = remember { Animatable(if (reveal) 0f else 1f) }
    LaunchedEffect(reveal, delayMs, reduceMotion) {
        if (!reveal) {
            slide.snapTo(1f)
            return@LaunchedEffect
        }
        if (slide.value <= 0.001f) return@LaunchedEffect
        if (reduceMotion) {
            slide.snapTo(0f)
            return@LaunchedEffect
        }
        delay(delayMs.toLong())
        slide.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
    }
    return slide.value
}

internal fun formatXmbPlaytime(millis: Long): String {
    if (millis < 60_000L) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours <= 0L -> "$minutes min"
        minutes == 0L -> if (hours == 1L) "1 hour" else "$hours hours"
        else -> "${hours}h ${minutes}m"
    }
}

@Composable
private fun XoraRomHeroBackdrop(
    artPath: String?,
    modifier: Modifier = Modifier,
    settleMs: Long = XMB_FOCUS_SETTLE_MS,
    scrimAlpha: Float = 1f,
) {
    val reduceMotion = rememberReduceMotion()
    // Wait out the focus settle so a held d-pad does not strobe every ROM's hero.
    // Keep the last art on screen while scrolling — clearing it first is the flicker.
    val target = artPath.orEmpty()
    var committed by remember { mutableStateOf(target) }
    LaunchedEffect(target, reduceMotion, settleMs) {
        if (target == committed) return@LaunchedEffect
        if (target.isBlank()) {
            committed = ""
            return@LaunchedEffect
        }
        if (!reduceMotion) delay(settleMs)
        committed = target
    }
    // Crossfade (not AnimatedContent): empty ↔ art and art ↔ art always fade,
    // including when focus clears on B / cancel / leaving Games.
    Crossfade(
        targetState = committed,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else ArcadiaMotion.HeroCrossfade,
            easing = FastOutSlowInEasing,
        ),
        label = "xmbRomHero",
        modifier = modifier,
    ) { path ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (path.isNotBlank()) {
                if (path.isVideoMediaPath()) {
                    LoopingWallpaperVideo(
                        uri = if (path.startsWith("file:", ignoreCase = true) ||
                            path.startsWith("content:", ignoreCase = true) ||
                            path.startsWith("http", ignoreCase = true)
                        ) {
                            path
                        } else {
                            "file://$path"
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ArtworkImage(
                        path = path,
                        contentDescription = null,
                        fallbackText = "",
                        contentScale = ContentScale.Crop,
                        cacheInMemory = true,
                        decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.10f),
                                0.45f to Color.Black.copy(alpha = 0.04f),
                                1f to Color.Black.copy(alpha = 0.10f),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.10f),
                                0.4f to Color.Black.copy(alpha = 0.02f),
                                1f to Color.Black.copy(alpha = 0.08f),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun XoraXmbPillChrome(
    state: HomeUiState,
    onToggleAccountPanel: () -> Unit,
    onToggleSystemPanel: () -> Unit,
    onToggleAchievementsPanel: () -> Unit,
    onSelectSocialTab: (SocialMenuTab) -> Unit,
    onSelectAccountRow: (Int) -> Unit,
    onActivateAccountRow: (Int?) -> Unit,
    onSelectSystemRow: (Int) -> Unit,
    onActivateSystemRow: (Int?) -> Unit,
    onOpenNotifications: () -> Unit,
    onSystemStatusDraftChange: (String) -> Unit,
    onSaveCustomStatus: () -> Unit,
    onClearCustomStatus: () -> Unit,
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit,
    onSelectAvatarPreset: (presetId: String) -> Unit,
    onRequestLocalAvatar: () -> Unit,
    onUseRaAvatar: () -> Unit,
    onUseDiscordAvatar: () -> Unit,
    onUseXoraAvatar: () -> Unit,
    onXoraPresenceMode: (com.arcadia.shell.xoranetwork.XoraPresenceMode) -> Unit = {},
    onClearAvatar: () -> Unit,
    onClearNotifications: () -> Unit = {},
    onFriendSearchChange: (String) -> Unit,
    onReplyDraftChange: (String) -> Unit,
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit,
    onLoginRetroAchievements: (username: String, password: String) -> Unit,
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit,
    onSignOutRetroAchievements: () -> Unit,
) {
    var profileEditing by remember { mutableStateOf(false) }
    LaunchedEffect(state.profileEditRequest) {
        if (state.profileEditRequest > 0) profileEditing = true
    }
    val launching = state.isLaunching
    val reduceMotion = rememberReduceMotion()
    val introSlide = rememberIntroSlide(
        reveal = state.homeIntroReveal,
        delayMs = 110,
        reduceMotion = reduceMotion,
    )
    val slidePx = with(LocalDensity.current) { 72.dp.toPx() } * introSlide
    val introAlpha = (1f - introSlide).coerceIn(0f, 1f)
    val accountExpanded = state.accountPanelExpanded && !launching
    val systemExpanded = state.systemPanelExpanded && !launching
    val achievementsExpanded = state.achievementsPanelExpanded && !launching

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneMaxHeight = this.maxHeight
        // Only the expanded pill's own collapsed chrome hides (inside each pill).
        // Sibling pills and the XMB stay visible.
        AccountPill(
            expanded = accountExpanded,
            socialMenu = state.socialMenu,
            profile = state.profile,
            profileAvatarModel = state.profileAvatarModel,
            accountRows = state.accountPanelRows,
            selectedRowIndex = state.accountPanelSelectedIndex,
            hideCollapsedChrome = state.activeNotificationPresent ||
                state.photos.chromeOverlayOpen,
            onToggle = onToggleAccountPanel,
            onSelectTab = onSelectSocialTab,
            onSelectRow = onSelectAccountRow,
            onActivateRow = onActivateAccountRow,
            onFriendSearchChange = onFriendSearchChange,
            onReplyDraftChange = onReplyDraftChange,
            onClearNotifications = onClearNotifications,
            modifier = Modifier
                .align(Alignment.TopStart)
                .heightIn(max = paneMaxHeight - 24.dp)
                .padding(start = 20.dp, top = 21.dp, end = 16.dp, bottom = 12.dp)
                .graphicsLayer {
                    alpha = introAlpha
                    translationX = -slidePx
                },
        )
        SystemPill(
            profile = state.profile,
            avatarImageModel = state.profileAvatarModel,
            raUsername = state.achievements.profile?.username,
            raScore = state.achievements.profile?.totalPoints,
            recentAchievements = state.achievements.recent,
            jumpBackGames = state.quickLaunchGames.take(3),
            systemProfile = state.systemProfile,
            expanded = systemExpanded,
            selectedRowIndex = state.systemPanelSelectedIndex,
            hideCollapsedChrome = state.photos.chromeOverlayOpen,
            onToggle = onToggleSystemPanel,
            onSelectRow = onSelectSystemRow,
            onActivateRow = onActivateSystemRow,
            onStatusDraftChange = onSystemStatusDraftChange,
            onSaveCustomStatus = onSaveCustomStatus,
            onClearCustomStatus = onClearCustomStatus,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .heightIn(max = paneMaxHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = introAlpha
                    translationX = slidePx
                },
        )

        // Music owns this corner while browsing; the full Now Playing page already has transport,
        // so the mini player hides there and comes back on exit. The RA card stays hidden until
        // the XMB is actually sitting on a game (recents / a ROM / in-session Resume).
        val musicFocused = state.xoraXmb.category == XoraXmbCategory.Music
        val launchGame = state.homeHub.vitaShortcutLaunch?.game
        val showMiniPlayer = launchGame == null &&
            musicFocused &&
            state.xoraXmb.depth != XoraXmbDepth.NowPlaying
        val showAchievementsCard = launchGame == null &&
            !musicFocused &&
            state.xoraXmb.showsAchievementsCard
        if (showMiniPlayer) {
            NowPlayingPill(
                state = state.music.nowPlaying,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer {
                        alpha = introAlpha
                        translationY = slidePx * 0.85f
                    },
            )
        }
        AnimatedVisibility(
            visible = showAchievementsCard,
            enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
                animationSpec = arcadiaTween(ArcadiaMotion.Medium),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(1f, 1f),
            ),
            exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
                animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                targetScale = 0.96f,
                transformOrigin = TransformOrigin(1f, 1f),
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = introAlpha
                    translationY = slidePx * 0.85f
                },
        ) {
            AchievementsPill(
                expanded = achievementsExpanded,
                state = state.achievements,
                onToggle = onToggleAchievementsPanel,
                onSelectTab = onSelectAchievementsTab,
                onLogin = onLoginRetroAchievements,
                onLoginWithApiKey = onLoginRetroAchievementsWithApiKey,
            )
        }

        if (profileEditing) {
            ProfileEditSheet(
                profile = state.profile,
                avatarImageModel = state.profileAvatarModel,
                raConfigured = state.achievements.credentials.isConfigured,
                discordLinked = state.socialMenu.discord.avatarAvailable,
                onDismiss = { profileEditing = false },
                onSave = onSaveProfile,
                onSelectAvatarPreset = onSelectAvatarPreset,
                onRequestPhoto = onRequestLocalAvatar,
                onUseRaAvatar = onUseRaAvatar,
                onUseDiscordAvatar = onUseDiscordAvatar,
                onUseXoraAvatar = onUseXoraAvatar,
                onXoraPresenceMode = onXoraPresenceMode,
                onClearAvatar = onClearAvatar,
                xoraSignedIn = state.dashboard.network.signedIn,
            )
        }
    }
}

/** Drill in / out slide between XMB rungs (PSP / PS3 shell feel). */
private const val XMB_DEPTH_SLIDE_MS = 300
