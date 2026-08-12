package com.arcadia.shell.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.AccountPill
import com.arcadia.shell.feature.home.component.AchievementsPill
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.HeroTrailerLayer
import com.arcadia.shell.feature.home.component.ProfileEditSheet
import com.arcadia.shell.feature.home.component.SystemPill
import com.arcadia.shell.model.Game
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit = { _, _ -> },
    onSelectAvatarPreset: (presetId: String) -> Unit = {},
    onRequestLocalAvatar: () -> Unit = {},
    onUseRaAvatar: () -> Unit = {},
    onClearAvatar: () -> Unit = {},
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit = {},
    onLoginRetroAchievements: (username: String, password: String) -> Unit = { _, _ -> },
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit = { _, _ -> },
    showPillChrome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val xmb = state.xoraXmb
    val heroGame = xmb.focusGame?.takeIf {
        xmb.depth == XoraXmbDepth.Roms ||
            xmb.selectedItem?.action is XoraXmbAction.LaunchContinueOrFavorite ||
            xmb.selectedItem?.action is XoraXmbAction.LaunchGame
    }
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

    Box(modifier = modifier.fillMaxSize()) {
        // Theme / custom wallpaper must remain the base plate.
        HomeWallpaper(
            customPath = state.homeHub.wallpaperPath,
            dim = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
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
            game = heroGame,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
        )

        HeroTrailerLayer(
            state = state.trailer,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
        )

        if (fullTrailer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
            )
        }

        XmbCross(
            xmb = xmb,
            onSelectCategory = onSelectCategory,
            onSelectItem = onSelectItem,
            onActivateItem = onActivateItem,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = chromeSlidePx
                },
        )

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
                onSaveProfile = onSaveProfile,
                onSelectAvatarPreset = onSelectAvatarPreset,
                onRequestLocalAvatar = onRequestLocalAvatar,
                onUseRaAvatar = onUseRaAvatar,
                onClearAvatar = onClearAvatar,
                onFriendSearchChange = onFriendSearchChange,
                onReplyDraftChange = onReplyDraftChange,
                onSelectAchievementsTab = onSelectAchievementsTab,
                onLoginRetroAchievements = onLoginRetroAchievements,
                onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
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
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit = { _, _ -> },
    onSelectAvatarPreset: (presetId: String) -> Unit = {},
    onRequestLocalAvatar: () -> Unit = {},
    onUseRaAvatar: () -> Unit = {},
    onClearAvatar: () -> Unit = {},
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit = {},
    onLoginRetroAchievements: (username: String, password: String) -> Unit = { _, _ -> },
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit = { _, _ -> },
    showPillChrome: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val xmb = state.xoraXmb
    val heroGame = xmb.focusGame
    val fullTrailer = state.trailer.active &&
        state.trailer.displayMode == TrailerDisplayMode.FullBackground
    val titleEnter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
        scaleIn(arcadiaTween(ArcadiaMotion.Medium), initialScale = 0.96f)
    val titleExit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
        scaleOut(arcadiaTween(ArcadiaMotion.Fast), targetScale = 1.02f)

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

    Box(modifier = modifier.fillMaxSize()) {
        HomeWallpaper(
            customPath = state.homeHub.wallpaperPath,
            dim = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
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
            game = heroGame?.takeIf {
                xmb.depth == XoraXmbDepth.Roms ||
                    xmb.selectedItem?.action is XoraXmbAction.LaunchContinueOrFavorite ||
                    xmb.selectedItem?.action is XoraXmbAction.LaunchGame
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
        )
        HeroTrailerLayer(
            state = state.trailer,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
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
                onSaveProfile = onSaveProfile,
                onSelectAvatarPreset = onSelectAvatarPreset,
                onRequestLocalAvatar = onRequestLocalAvatar,
                onUseRaAvatar = onUseRaAvatar,
                onClearAvatar = onClearAvatar,
                onFriendSearchChange = onFriendSearchChange,
                onReplyDraftChange = onReplyDraftChange,
                onSelectAchievementsTab = onSelectAchievementsTab,
                onLoginRetroAchievements = onLoginRetroAchievements,
                onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
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
    // Short tween — springs + per-row animate*AsState were stacking and looking choppy.
    val scrollSpec = remember(reduceMotion) {
        if (reduceMotion) {
            tween(0)
        } else {
            tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)
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
        listEnterAlpha.snapTo(0.35f)
        listEnterAlpha.animateTo(1f, tween(ArcadiaMotion.Fast, easing = FastOutSlowInEasing))
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
                distance < 0.5f -> lerp(1.05f, 1.18f, 1f - distance / 0.5f)
                distance < 1.5f -> lerp(0.82f, 1.05f, 1.5f - distance)
                distance < 2.5f -> lerp(0.68f, 0.82f, 2.5f - distance)
                else -> 0.55f
            }
            val alpha = when {
                distance < 0.5f -> if (atRoot) 1f else 0.35f
                distance < 1.5f -> if (atRoot) 0.55f else 0.18f
                distance < 2.5f -> if (atRoot) 0.32f else 0.1f
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
            else -> xmb.category.label
        }
        val catLabelWidth = 120.dp
        AnimatedContent(
            targetState = catLabel,
            transitionSpec = {
                fadeIn(tween(ArcadiaMotion.Fast)) togetherWith fadeOut(tween(ArcadiaMotion.Fast))
            },
            label = "catLabel",
            modifier = Modifier
                .graphicsLayer {
                    translationX = crossXPx - with(density) { catLabelWidth.toPx() } / 2f
                    translationY = catYPx + catIconPx / 2f + with(density) { 4.dp.toPx() }
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

        // ——— Vertical items ———
        // Fixed glyph slot width keeps every icon column and every title column on one X.
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
                    distance < 0.5f -> if (browsingBoxes) lerp(1f, 1.08f, focus) else lerp(1f, 1.05f, focus)
                    distance < 1.5f -> 0.92f
                    distance < 2.5f -> 0.84f
                    else -> 0.76f
                }
                val alpha = when {
                    distance < 0.5f -> 1f
                    distance < 1.5f -> 0.62f
                    distance < 2.5f -> 0.38f
                    distance < 3.5f -> 0.22f
                    else -> 0.1f
                }
                val boxWidth = if (browsingBoxes) {
                    lerpDp(itemIcon, boxFocusWidth, focus)
                } else {
                    itemIcon
                }
                val boxHeight = if (browsingBoxes) boxWidth * boxAspect else boxWidth
                val yPx = itemFocusYPx - itemRowPx / 2f + itemPitchPx * delta
                val xPx = crossXPx - glyphSlotPx / 2f

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = xPx
                            translationY = yPx
                            this.alpha = alpha * enterAlpha
                        }
                        .height(itemRow)
                        .widthIn(max = if (browsingBoxes) 560.dp else 460.dp)
                        .clickable(
                            interactionSource = remember(item.id) { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (selected) onActivateItem() else onSelectItem(index)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .width(glyphSlot)
                            .height(itemRow)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin.Center
                            },
                        contentAlignment = Alignment.Center,
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
                    Spacer(modifier = Modifier.width(glyphGap))
                    if (browsingRoms) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .width(1.5.dp)
                                .fillMaxHeight(0.7f)
                                .align(Alignment.CenterVertically)
                                .background(Color.White.copy(alpha = 0.35f)),
                        )
                        XmbRomTitle(
                            title = item.title,
                            logoPath = item.logoPath,
                            subtitle = item.subtitle,
                            selected = selected,
                            titleStyle = xmb.titleStyle,
                            playTimeMs = item.playTimeMs,
                        )
                    } else {
                        Column(modifier = Modifier.widthIn(max = 360.dp)) {
                            XoraTitleText(
                                text = item.title,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = if (selected) 18.sp else 14.sp,
                                maxLines = 1,
                            )
                            if (selected && !item.subtitle.isNullOrBlank()) {
                                XoraSecondaryText(
                                    text = item.subtitle,
                                    fontSize = 11.sp,
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
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .then(
                if (boxArt) {
                    Modifier
                        .graphicsLayer {
                            shadowElevation = if (selected) 20f else 12f
                            this.shape = shape
                            clip = false
                            ambientShadowColor = Color.Black.copy(alpha = 0.55f)
                            spotShadowColor = Color.Black.copy(alpha = 0.72f)
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
                } else {
                    Modifier.clip(shape)
                },
            )
            .background(
                when {
                    !artPath.isNullOrBlank() -> Color.Black.copy(alpha = 0.35f)
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
                tint = Color.White.copy(alpha = if (selected) 1f else 0.85f),
                size = minOf(width, height) * 0.5f,
            )
        }
    }
}

private fun formatXmbPlaytime(millis: Long): String {
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
    game: Game?,
    modifier: Modifier = Modifier,
) {
    val artPath = game?.heroImagePath ?: game?.boxArtPath ?: game?.logoImagePath
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
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit,
    onSelectAvatarPreset: (presetId: String) -> Unit,
    onRequestLocalAvatar: () -> Unit,
    onUseRaAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onFriendSearchChange: (String) -> Unit,
    onReplyDraftChange: (String) -> Unit,
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit,
    onLoginRetroAchievements: (username: String, password: String) -> Unit,
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit,
) {
    var profileEditing by remember { mutableStateOf(false) }
    LaunchedEffect(state.profileEditRequest) {
        if (state.profileEditRequest > 0) profileEditing = true
    }
    val launching = state.isLaunching

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val paneMaxHeight = this.maxHeight
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .heightIn(max = paneMaxHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = if (launching) 0f else 1f },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            AccountPill(
                expanded = state.accountPanelExpanded && !launching,
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
                modifier = Modifier.heightIn(max = paneMaxHeight - 24.dp),
            )
            SystemPill(
                profile = state.profile,
                avatarImageModel = state.profileAvatarModel,
                raScore = state.achievements.profile?.totalPoints,
                recentAchievements = state.achievements.recent,
                jumpBackGames = state.quickLaunchGames.take(3),
                expanded = state.systemPanelExpanded && !launching,
                selectedRowIndex = state.systemPanelSelectedIndex,
                onToggle = onToggleSystemPanel,
                onSelectRow = onSelectSystemRow,
                onActivateRow = onActivateSystemRow,
            )
        }

        AchievementsPill(
            expanded = state.achievementsPanelExpanded && !launching,
            state = state.achievements,
            onToggle = onToggleAchievementsPanel,
            onSelectTab = onSelectAchievementsTab,
            onLogin = onLoginRetroAchievements,
            onLoginWithApiKey = onLoginRetroAchievementsWithApiKey,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = if (launching) 0f else 1f },
        )

        if (profileEditing) {
            ProfileEditSheet(
                profile = state.profile,
                avatarImageModel = state.profileAvatarModel,
                raConfigured = state.achievements.credentials.isConfigured,
                onDismiss = { profileEditing = false },
                onSave = onSaveProfile,
                onSelectAvatarPreset = onSelectAvatarPreset,
                onRequestPhoto = onRequestLocalAvatar,
                onUseRaAvatar = onUseRaAvatar,
                onClearAvatar = onClearAvatar,
            )
        }
    }
}

private const val CROSS_X_FRACTION = 0.28f
/** Category strip sits in the upper third (PS3 XMB). */
private const val CATEGORY_Y_FRACTION = 0.30f
/** Focused item center sits below the category strip + label. */
private val CATEGORY_TO_ITEM_GAP = 110.dp
private const val VISIBLE_ITEM_RADIUS = 4
private const val ITEM_SLOT_COUNT = VISIBLE_ITEM_RADIUS * 2 + 1
private val CATEGORY_PITCH = 88.dp
private val ITEM_PITCH = 52.dp
private val CATEGORY_ICON = 52.dp
private val ITEM_ICON = 42.dp
private val ITEM_ROW = 50.dp
/** Landscape 16:9 ROM box (height = width × [ROM_BOX_ASPECT]). */
private val ROM_BOX_WIDTH = 128.dp
private val ROM_BOX_WIDTH_FOCUS = 168.dp
private const val ROM_BOX_ASPECT = 9f / 16f
private val ROM_ITEM_ROW = 108.dp
private val ROM_ITEM_PITCH = 96.dp
private val ROM_LOGO_HEIGHT = 42.dp
private val ROM_LOGO_HEIGHT_FOCUS = 64.dp
/** Console product art (ScreenScraper illustration/photo) — near-square card. */
private val SYSTEM_BOX_WIDTH = 96.dp
private val SYSTEM_BOX_WIDTH_FOCUS = 124.dp
private const val SYSTEM_BOX_ASPECT = 1.05f
private val SYSTEM_ITEM_ROW = 148.dp
private val SYSTEM_ITEM_PITCH = 132.dp
