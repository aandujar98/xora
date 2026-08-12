package com.arcadia.shell.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.designsystem.rememberAmbientMotionActive
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.AccountPill
import com.arcadia.shell.feature.home.component.AchievementsPill
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.HeroTrailerLayer
import com.arcadia.shell.feature.home.component.NowPlayingPill
import com.arcadia.shell.feature.home.component.ProfileEditSheet
import com.arcadia.shell.feature.home.component.SystemPill
import com.arcadia.shell.model.Game
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

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
    onClearAvatar: () -> Unit = {},
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
        XoraXmbDepth.MusicAlbums, XoraXmbDepth.MusicTracks -> xmb.selectedItem?.artPath
        XoraXmbDepth.NowPlaying -> state.music.nowPlaying.track?.albumArtUri
        else -> null
    }
    val backdropArtPath = musicArtPath
        ?: heroGame?.heroImagePath ?: heroGame?.boxArtPath ?: heroGame?.logoImagePath
    val fullTrailer = state.trailer.active &&
        state.trailer.displayMode == TrailerDisplayMode.FullBackground

    // Chrome exits quickly; backdrop eases over the full cinematic hold (matches HeroPane).
    val chromeProgress by animateFloatAsState(
        targetValue = if (state.isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Launch),
        label = "xmbLaunchChrome",
    )
    val holdProgress by animateFloatAsState(
        targetValue = if (state.isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.LaunchHold),
        label = "xmbLaunchHold",
    )
    val chromeAlpha = 1f - chromeProgress
    val chromeSlidePx = chromeProgress * 72f
    val artworkScale = 1f + (holdProgress * 0.06f)
    val backdropMotion = rememberXmbBackdropMotion(
        categoryIndex = xmb.categoryIndex,
        itemIndex = xmb.itemIndex,
        launchScale = artworkScale,
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Theme / custom wallpaper must remain the base plate.
        HomeWallpaper(
            customPath = state.homeHub.wallpaperPath,
            dim = false,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropMotion),
        )
        // Soft readability wash — dim theme / wallpaper slightly under the XMB.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.40f),
                        ),
                    ),
                ),
        )

        // Keep mounted so focus / back / cancel always crossfade (never unmount-snap).
        XoraRomHeroBackdrop(
            artPath = backdropArtPath,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropMotion),
        )

        HeroTrailerLayer(
            state = state.trailer,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropMotion),
        )

        if (fullTrailer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = chromeSlidePx
                },
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
                )
                else -> XmbCross(
                    xmb = xmb,
                    onSelectCategory = onSelectCategory,
                    onSelectItem = onSelectItem,
                    onActivateItem = onActivateItem,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

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
                onClearAvatar = onClearAvatar,
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
    onClearAvatar: () -> Unit = {},
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

    val chromeProgress by animateFloatAsState(
        targetValue = if (state.isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Launch),
        label = "xmbHeroLaunchChrome",
    )
    val holdProgress by animateFloatAsState(
        targetValue = if (state.isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.LaunchHold),
        label = "xmbHeroLaunchHold",
    )
    val chromeAlpha = 1f - chromeProgress
    val chromeSlidePx = chromeProgress * 72f
    val artworkScale = 1f + (holdProgress * 0.06f)
    val backdropMotion = rememberXmbBackdropMotion(
        categoryIndex = xmb.categoryIndex,
        itemIndex = xmb.itemIndex,
        launchScale = artworkScale,
    )

    Box(modifier = modifier.fillMaxSize()) {
        HomeWallpaper(
            customPath = state.homeHub.wallpaperPath,
            dim = false,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropMotion),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.26f),
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.38f),
                        ),
                    ),
                ),
        )
        XoraRomHeroBackdrop(
            artPath = heroGame?.takeIf {
                xmb.depth == XoraXmbDepth.Roms ||
                    xmb.selectedItem?.action is XoraXmbAction.LaunchContinueOrFavorite ||
                    xmb.selectedItem?.action is XoraXmbAction.LaunchGame
            }?.let { it.heroImagePath ?: it.boxArtPath ?: it.logoImagePath },
            modifier = Modifier
                .fillMaxSize()
                .then(backdropMotion),
        )
        HeroTrailerLayer(
            state = state.trailer,
            modifier = Modifier
                .fillMaxSize()
                .then(backdropMotion),
        )

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
                .padding(28.dp)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = chromeSlidePx
                },
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
                onClearAvatar = onClearAvatar,
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

@Composable
private fun XmbCross(
    xmb: XoraXmbUiState,
    onSelectCategory: (Int) -> Unit,
    onSelectItem: (Int) -> Unit,
    onActivateItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    // Ease-out slide (~PS3 XMB / reference clip) — one shared cursor, no nested springs.
    val scrollSpec = remember(reduceMotion) {
        if (reduceMotion) {
            tween(0)
        } else {
            tween<Float>(durationMillis = XMB_SCROLL_MS, easing = FastOutSlowInEasing)
        }
    }

    // Continuous scroll cursors — scale/alpha/slide derive from these (no nested animators).
    val categoryScroll = remember { Animatable(xmb.categoryIndex.toFloat()) }
    val itemScroll = remember { Animatable(xmb.itemIndex.toFloat()) }
    val listEnterAlpha = remember { Animatable(1f) }
    LaunchedEffect(xmb.categoryIndex) {
        categoryScroll.animateTo(xmb.categoryIndex.toFloat(), scrollSpec)
    }
    LaunchedEffect(xmb.itemIndex) {
        val target = xmb.itemIndex.toFloat()
            .coerceIn(0f, (xmb.items.size - 1).coerceAtLeast(0).toFloat())
        itemScroll.animateTo(target, scrollSpec)
    }
    LaunchedEffect(xmb.depth, xmb.categoryIndex, xmb.drilledPlatformId) {
        itemScroll.snapTo(
            xmb.itemIndex.toFloat()
                .coerceIn(0f, (xmb.items.size - 1).coerceAtLeast(0).toFloat()),
        )
        if (reduceMotion) {
            listEnterAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        listEnterAlpha.snapTo(0.28f)
        listEnterAlpha.animateTo(1f, tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val crossX = maxWidth * CROSS_X_FRACTION
        val catY = maxHeight * CATEGORY_Y_FRACTION
        val itemFocusY = catY + CATEGORY_TO_ITEM_GAP
        val categories = XoraXmbCategory.entries
        val items = xmb.items
        val atRoot = xmb.depth == XoraXmbDepth.Category
        val browsingRoms = xmb.depth == XoraXmbDepth.Roms
        val browsingSystems = xmb.depth == XoraXmbDepth.Systems
        val browsingBoxes = browsingRoms || browsingSystems
        val catIcon = CATEGORY_ICON
        val itemIcon = when {
            browsingRoms -> ROM_BOX_WIDTH
            browsingSystems -> SYSTEM_BOX_WIDTH
            else -> ITEM_ICON
        }
        val itemRow = when {
            browsingRoms -> ROM_ITEM_ROW
            browsingSystems -> SYSTEM_ITEM_ROW
            else -> ITEM_ROW
        }
        val itemPitch = when {
            browsingRoms -> ROM_ITEM_PITCH
            browsingSystems -> SYSTEM_ITEM_PITCH
            else -> ITEM_PITCH
        }
        val boxAspect = when {
            browsingSystems -> SYSTEM_BOX_ASPECT
            else -> ROM_BOX_ASPECT
        }
        val boxFocusWidth = when {
            browsingSystems -> SYSTEM_BOX_WIDTH_FOCUS
            else -> ROM_BOX_WIDTH_FOCUS
        }
        val categoryPitchPx = with(density) { CATEGORY_PITCH.toPx() }
        val itemPitchPx = with(density) { itemPitch.toPx() }
        val catIconPx = with(density) { catIcon.toPx() }
        val itemRowPx = with(density) { itemRow.toPx() }
        val crossXPx = with(density) { crossX.toPx() }
        val catYPx = with(density) { catY.toPx() }
        val itemFocusYPx = with(density) { itemFocusY.toPx() }
        val glyphSlot = if (browsingBoxes) boxFocusWidth else itemIcon
        val glyphSlotPx = with(density) { glyphSlot.toPx() }
        val glyphGap = if (browsingBoxes) 18.dp else 14.dp
        val glyphGapPx = with(density) { glyphGap.toPx() }
        val catScroll = categoryScroll.value
        val rowScroll = itemScroll.value
        val enterAlpha = listEnterAlpha.value

        // ——— Horizontal categories ———
        // Icons stay on a fixed pitch grid (no focus slide) so labels can share one axis.
        categories.forEachIndexed { index, category ->
            val delta = index - catScroll
            val distance = abs(delta)
            val scale = when {
                distance < 0.5f -> lerp(1.12f, CATEGORY_FOCUS_SCALE, 1f - distance / 0.5f)
                distance < 1.5f -> lerp(0.86f, 1.12f, 1.5f - distance)
                distance < 2.5f -> lerp(0.72f, 0.86f, 2.5f - distance)
                else -> 0.58f
            }
            val alpha = when {
                distance < 0.5f -> if (atRoot) 1f else 0.35f
                distance < 1.5f -> if (atRoot) 0.58f else 0.18f
                distance < 2.5f -> if (atRoot) 0.34f else 0.1f
                else -> 0.08f
            }
            val xPx = crossXPx - catIconPx / 2f + categoryPitchPx * delta
            val yPx = catYPx - catIconPx / 2f

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = xPx
                        translationY = yPx
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin.Center
                    }
                    .size(catIcon)
                    .clickable(
                        interactionSource = remember(index) { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectCategory(index) },
                contentAlignment = Alignment.Center,
            ) {
                XmbVectorIcon(
                    icon = category.toXmbIcon(),
                    tint = Color.White,
                    size = 34.dp,
                )
            }
        }

        // Category label — centered under the focused icon (same cross X).
        val catLabel = when {
            atRoot -> xmb.category.label
            xmb.depth == XoraXmbDepth.Systems -> "All Games"
            xmb.depth == XoraXmbDepth.Roms -> "Games"
            xmb.depth == XoraXmbDepth.Emulator -> "XOrA Emulator"
            xmb.depth == XoraXmbDepth.DspAccounts -> "Link DSP Accounts"
            xmb.depth == XoraXmbDepth.MusicAlbums -> "Playlist"
            xmb.depth == XoraXmbDepth.MusicTracks -> "Songs"
            xmb.depth == XoraXmbDepth.NowPlaying -> "Now Playing"
            else -> xmb.category.label
        }
        val catLabelWidth = if (xmb.depth == XoraXmbDepth.DspAccounts) 220.dp else 160.dp
        AnimatedContent(
            targetState = catLabel,
            // Cross-fade only: sliding the label sideways pulled it off the icon it names.
            transitionSpec = {
                fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(110, easing = FastOutSlowInEasing))
            },
            label = "catLabel",
            modifier = Modifier
                .graphicsLayer {
                    translationX = crossXPx - with(density) { catLabelWidth.toPx() } / 2f
                    // Clear the focused icon at its enlarged size — half the unscaled height
                    // left the label sitting on top of it.
                    translationY = catYPx + (catIconPx * CATEGORY_FOCUS_SCALE / 2f) +
                        with(density) { CATEGORY_LABEL_GAP.toPx() }
                    alpha = if (atRoot) 0.95f else 0.45f
                }
                .width(catLabelWidth),
        ) { label ->
            XoraTitleText(
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ——— Vertical items (glyphs slide through a fixed focus slot) ———
        if (items.isEmpty()) {
            XoraSecondaryText(
                text = "Nothing here yet",
                fontSize = 18.sp,
                fillColor = Color.White,
                modifier = Modifier.graphicsLayer {
                    translationX = crossXPx + glyphSlotPx / 2f + glyphGapPx
                    translationY = itemFocusYPx - with(density) { 10.dp.toPx() }
                    alpha = enterAlpha
                },
            )
        } else {
            val first = (rowScroll - VISIBLE_ITEM_RADIUS - 1f).toInt().coerceAtLeast(0)
            val last = (rowScroll + VISIBLE_ITEM_RADIUS + 1f).roundToInt()
                .coerceAtMost(items.lastIndex)
            for (index in first..last) {
                val item = items[index]
                val delta = index - rowScroll
                val distance = abs(delta)
                val focus = (1f - distance).coerceIn(0f, 1f)
                val selected = index == xmb.itemIndex
                val scale = when {
                    distance < 0.5f -> if (browsingBoxes) {
                        lerp(1f, 1.22f, focus)
                    } else {
                        lerp(1f, 1.48f, focus)
                    }
                    distance < 1.5f -> if (browsingBoxes) 0.9f else 0.88f
                    distance < 2.5f -> if (browsingBoxes) 0.8f else 0.78f
                    else -> 0.7f
                }
                val alpha = when {
                    distance < 0.5f -> 1f
                    distance < 1.5f -> 0.72f
                    distance < 2.5f -> 0.42f
                    distance < 3.5f -> 0.24f
                    else -> 0.1f
                }
                val boxWidth = if (browsingBoxes) {
                    lerpDp(itemIcon, boxFocusWidth, focus)
                } else {
                    itemIcon
                }
                val boxHeight = if (browsingBoxes) boxWidth * boxAspect else boxWidth
                // Expand spacing around the focus slot so the selected glyph can breathe.
                val yPx = itemFocusYPx - itemRowPx / 2f + xmbItemOffsetY(delta, itemPitchPx)
                val xPx = crossXPx - glyphSlotPx / 2f

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = xPx
                            translationY = yPx
                            this.alpha = alpha * enterAlpha
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin.Center
                        }
                        .width(glyphSlot)
                        .height(itemRow)
                        .clickable(
                            interactionSource = remember(item.id) { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (selected) onActivateItem() else onSelectItem(index)
                        },
                ) {
                    XmbItemGlyph(
                        title = item.title,
                        artPath = item.artPath,
                        icon = item.icon,
                        selected = selected,
                        width = boxWidth,
                        height = boxHeight,
                        boxArt = browsingBoxes,
                    )
                }
            }

            // Title / metadata stay in the focus slot — fade out old, slide in new from the right.
            val focusItem = items.getOrNull(xmb.itemIndex)
            if (focusItem != null) {
                val detailX = crossXPx + glyphSlotPx / 2f + glyphGapPx
                AnimatedContent(
                    targetState = FocusDetail(
                        id = focusItem.id,
                        title = focusItem.title,
                        subtitle = focusItem.subtitle,
                        logoPath = focusItem.logoPath,
                        playTimeMs = focusItem.playTimeMs,
                        browsingRoms = browsingRoms,
                        titleStyle = xmb.titleStyle,
                    ),
                    transitionSpec = {
                        (
                            fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(
                                    tween(XMB_SCROLL_MS, easing = FastOutSlowInEasing),
                                ) { it / 5 }
                            ) togetherWith fadeOut(tween(110, easing = FastOutSlowInEasing))
                    },
                    contentKey = { it.id },
                    label = "xmbFocusDetail",
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = detailX
                            translationY = itemFocusYPx - with(density) { 28.dp.toPx() }
                            alpha = enterAlpha
                        }
                        .widthIn(max = if (browsingBoxes) 420.dp else 360.dp),
                ) { detail ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (detail.browsingRoms) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .width(1.5.dp)
                                    .height(72.dp)
                                    .background(Color.White.copy(alpha = 0.35f)),
                            )
                        }
                        if (detail.browsingRoms) {
                            XmbRomTitle(
                                title = detail.title,
                                logoPath = detail.logoPath,
                                subtitle = detail.subtitle,
                                selected = true,
                                titleStyle = detail.titleStyle,
                                playTimeMs = detail.playTimeMs,
                            )
                        } else {
                            Column(modifier = Modifier.widthIn(max = 360.dp)) {
                                XoraTitleText(
                                    text = detail.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp,
                                    maxLines = 2,
                                )
                                if (!detail.subtitle.isNullOrBlank()) {
                                    XoraSecondaryText(
                                        text = detail.subtitle,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FocusDetail(
    val id: String,
    val title: String,
    val subtitle: String?,
    val logoPath: String?,
    val playTimeMs: Long,
    val browsingRoms: Boolean,
    val titleStyle: XmbTitleStyle,
)

/** Ambient drift + selection parallax for wallpaper / hero plates. */
@Composable
private fun rememberXmbBackdropMotion(
    categoryIndex: Int,
    itemIndex: Int,
    launchScale: Float,
): Modifier {
    val reduceMotion = rememberReduceMotion()
    val parallaxX by animateFloatAsState(
        targetValue = if (reduceMotion) 0f else categoryIndex * 14f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else XMB_SCROLL_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "xmbParallaxX",
    )
    val parallaxY by animateFloatAsState(
        targetValue = if (reduceMotion) 0f else itemIndex * 10f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else XMB_SCROLL_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "xmbParallaxY",
    )
    // The drift moves the wallpaper, hero art and trailer layers, so leaving it running while the
    // shell is not being looked at redraws the largest surfaces in the app for nothing.
    val phase = if (rememberAmbientMotionActive()) {
        val ambientPhase by rememberInfiniteTransition(label = "xmbAmbient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "xmbAmbientPhase",
        )
        ambientPhase
    } else {
        0f
    }
    val ambX = sin(phase * PI * 2.0).toFloat() * 12f
    val ambY = cos(phase * PI * 2.0).toFloat() * 8f
    return Modifier.graphicsLayer {
        val base = 1.045f * launchScale
        scaleX = base
        scaleY = base
        translationX = -parallaxX + ambX
        translationY = -parallaxY * 0.55f + ambY
    }
}

/** Vertical distance from focus with extra breathing room around the selected slot. */
private fun xmbItemOffsetY(delta: Float, pitchPx: Float): Float {
    val absDelta = abs(delta)
    val expand = 0.32f
    val shaped = if (absDelta <= 1f) {
        absDelta * (1f + expand)
    } else {
        (1f + expand) + (absDelta - 1f)
    }
    return sign(delta) * shaped * pitchPx
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

private fun lerpDp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t.coerceIn(0f, 1f)

@Composable
private fun XmbRomTitle(
    title: String,
    logoPath: String?,
    subtitle: String?,
    selected: Boolean,
    titleStyle: XmbTitleStyle,
    playTimeMs: Long,
) {
    val logoHeight = if (selected) ROM_LOGO_HEIGHT_FOCUS else ROM_LOGO_HEIGHT
    val showLogo = titleStyle == XmbTitleStyle.TitleIcons && !logoPath.isNullOrBlank()
    Column(
        modifier = Modifier.widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showLogo) {
            ArtworkImage(
                path = logoPath,
                contentDescription = title,
                fallbackText = title,
                contentScale = ContentScale.Fit,
                cacheInMemory = true,
                decodeMaxEdgePx = 720,
                modifier = Modifier
                    .height(logoHeight)
                    .widthIn(max = if (selected) 360.dp else 280.dp)
                    .fillMaxWidth(),
            )
        } else {
            XoraTitleText(
                text = title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (selected) 18.sp else 14.sp,
                maxLines = 2,
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(ArcadiaMotion.Fast)) +
                scaleIn(tween(ArcadiaMotion.Fast), initialScale = 0.96f),
            exit = fadeOut(tween(ArcadiaMotion.Fast)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!subtitle.isNullOrBlank()) {
                    XoraSecondaryText(
                        text = subtitle,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                XoraSecondaryText(
                    text = "Playtime: ${formatXmbPlaytime(playTimeMs)}",
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun XmbItemGlyph(
    title: String,
    artPath: String?,
    icon: XmbIcon,
    selected: Boolean,
    width: Dp,
    height: Dp,
    boxArt: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = if (boxArt) RoundedCornerShape(16.dp) else CircleShape
    val cornerPx = 16.dp
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val rim = Color.White.copy(alpha = 0.95f)
    val isVectorIcon = artPath.isNullOrBlank() && !boxArt
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .then(
                when {
                    boxArt -> Modifier
                        .graphicsLayer {
                            // Soft drop only — no mirror / oval reflection under the tile.
                            shadowElevation = if (selected) 14f else 8f
                            this.shape = shape
                            clip = false
                            ambientShadowColor = Color.Black.copy(alpha = 0.45f)
                            spotShadowColor = Color.Black.copy(alpha = 0.55f)
                        }
                        .drawWithContent {
                            drawContent()
                            if (selected) {
                                val stroke = 3.5.dp.toPx()
                                val inset = stroke / 2f
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            glow.copy(alpha = 0.55f),
                                            rim.copy(alpha = 0.75f),
                                            glow.copy(alpha = 0.45f),
                                        ),
                                        start = Offset.Zero,
                                        end = Offset(size.width, size.height),
                                    ),
                                    topLeft = Offset(inset, inset),
                                    size = Size(size.width - stroke, size.height - stroke),
                                    cornerRadius = CornerRadius(cornerPx.toPx(), cornerPx.toPx()),
                                    style = Stroke(width = stroke),
                                )
                            }
                        }
                        .clip(shape)
                        .border(
                            width = if (selected) 2.5.dp else 0.dp,
                            color = if (selected) rim else Color.Transparent,
                            shape = shape,
                        )
                    isVectorIcon -> Modifier
                        .clip(shape)
                        .border(
                            width = if (selected) 2.dp else 1.5.dp,
                            color = Color.Black,
                            shape = shape,
                        )
                    else -> Modifier.clip(shape)
                },
            )
            .background(
                when {
                    !artPath.isNullOrBlank() -> Color.Black.copy(alpha = 0.35f)
                    // Solid plate — no frosted / translucent circle behind vector glyphs.
                    isVectorIcon -> if (selected) Color(0xFF1A1D24) else Color(0xFF101218)
                    selected -> Color.White.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!artPath.isNullOrBlank()) {
            ArtworkImage(
                path = artPath,
                contentDescription = title,
                fallbackText = title.take(1),
                contentScale = ContentScale.Crop,
                cacheInMemory = true,
                decodeMaxEdgePx = if (boxArt) 512 else 256,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            XmbVectorIcon(
                icon = icon,
                tint = Color.White,
                size = minOf(width, height) * 0.5f,
                outlined = true,
            )
        }
    }
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
) {
    val reduceMotion = rememberReduceMotion()
    // Crossfade (not AnimatedContent): empty ↔ art and art ↔ art always fade,
    // including when focus clears on B / cancel / leaving Games.
    Crossfade(
        targetState = artPath.orEmpty(),
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else ArcadiaMotion.Medium,
            easing = FastOutSlowInEasing,
        ),
        label = "xmbRomHero",
        modifier = modifier,
    ) { path ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (path.isNotBlank()) {
                ArtworkImage(
                    path = path,
                    contentDescription = null,
                    fallbackText = "",
                    contentScale = ContentScale.Crop,
                    cacheInMemory = false,
                    decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.45f to Color.Black.copy(alpha = 0.32f),
                                1f to Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.58f),
                                0.4f to Color.Black.copy(alpha = 0.12f),
                                1f to Color.Black.copy(alpha = 0.42f),
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
    onClearAvatar: () -> Unit,
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
            onToggle = onToggleAccountPanel,
            onSelectTab = onSelectSocialTab,
            onSelectRow = onSelectAccountRow,
            onActivateRow = onActivateAccountRow,
            onFriendSearchChange = onFriendSearchChange,
            onReplyDraftChange = onReplyDraftChange,
            modifier = Modifier
                .align(Alignment.TopStart)
                .heightIn(max = paneMaxHeight - 24.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = if (launching) 0f else 1f },
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
                .graphicsLayer { alpha = if (launching) 0f else 1f },
        )

        // Music owns this corner while browsing; the full Now Playing page already has transport,
        // so the mini player hides there and comes back on exit.
        val musicFocused = state.xoraXmb.category == XoraXmbCategory.Music
        val showMiniPlayer = musicFocused && state.xoraXmb.depth != XoraXmbDepth.NowPlaying
        if (showMiniPlayer) {
            NowPlayingPill(
                state = state.music.nowPlaying,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = if (launching) 0f else 1f },
            )
        } else if (!musicFocused) {
            AchievementsPill(
                expanded = achievementsExpanded,
                state = state.achievements,
                onToggle = onToggleAchievementsPanel,
                onSelectTab = onSelectAchievementsTab,
                onLogin = onLoginRetroAchievements,
                onLoginWithApiKey = onLoginRetroAchievementsWithApiKey,
                onSignOut = onSignOutRetroAchievements,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = if (launching) 0f else 1f },
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
                onClearAvatar = onClearAvatar,
            )
        }
    }
}

/** Drill in / out slide between XMB rungs (PSP / PS3 shell feel). */
private const val XMB_DEPTH_SLIDE_MS = 300
private const val CROSS_X_FRACTION = 0.28f
/** Category strip sits in the upper third (PS3 XMB). */
private const val CATEGORY_Y_FRACTION = 0.30f
/** Ease-out slide duration for category / item cursors and focus titles. */
private const val XMB_SCROLL_MS = 340
/** Focused item center sits below the category strip + label. */
private val CATEGORY_TO_ITEM_GAP = 110.dp
private const val VISIBLE_ITEM_RADIUS = 4
private val CATEGORY_PITCH = 96.dp
private val ITEM_PITCH = 58.dp
private val CATEGORY_ICON = 52.dp
/** Focused category icon scale — the label is placed clear of the icon at this size. */
private const val CATEGORY_FOCUS_SCALE = 1.32f
private val CATEGORY_LABEL_GAP = 8.dp
private val ITEM_ICON = 42.dp
private val ITEM_ROW = 56.dp
/** Landscape 16:9 ROM box (height = width × [ROM_BOX_ASPECT]). */
private val ROM_BOX_WIDTH = 128.dp
private val ROM_BOX_WIDTH_FOCUS = 176.dp
private const val ROM_BOX_ASPECT = 9f / 16f
private val ROM_ITEM_ROW = 118.dp
private val ROM_ITEM_PITCH = 104.dp
private val ROM_LOGO_HEIGHT = 42.dp
private val ROM_LOGO_HEIGHT_FOCUS = 64.dp
/** Console product art (ScreenScraper illustration/photo) — near-square card. */
private val SYSTEM_BOX_WIDTH = 96.dp
private val SYSTEM_BOX_WIDTH_FOCUS = 132.dp
private const val SYSTEM_BOX_ASPECT = 1.05f
private val SYSTEM_ITEM_ROW = 156.dp
private val SYSTEM_ITEM_PITCH = 140.dp
