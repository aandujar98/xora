package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LiquidGlassSurface
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.AccountPill
import com.arcadia.shell.feature.home.component.AchievementsPill
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.HeroTrailerLayer
import com.arcadia.shell.feature.home.component.ProfileEditSheet
import com.arcadia.shell.feature.home.component.SystemPill
import com.arcadia.shell.feature.home.component.xmb.PlatformIcon
import com.arcadia.shell.feature.home.component.xmb.XmbGameTile
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewHomeUi
import com.arcadia.shell.model.Game
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Full-bleed single-screen vertical XMB.
 *
 * Proportion targets (matched to the Zelda / Spirit Tracks reference):
 * - Focused case ≈ 40% of pane height; neighbors clearly smaller
 * - Slim LT/RT pills along the top edge (~5%)
 * - Title + playtime in the middle-right with generous air
 * - One media preview bottom-right, slightly wider than the focused case (not a strip / not full-bleed)
 * - Soft high-key hero behind everything; hint bar is hidden in single-screen mode
 */
@Composable
fun VerticalGameSelectorPane(
    state: HomeUiState,
    onSelectTab: (Int) -> Unit,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
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
    onSignOutRetroAchievements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launchProgress by animateFloatAsState(
        targetValue = if (state.isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Launch),
        label = "verticalSelectorLaunch",
    )
    val chromeAlpha = 1f - launchProgress
    val chromeSlidePx = launchProgress * 72f
    var profileEditing by remember { mutableStateOf(false) }

    LaunchedEffect(state.profileEditRequest) {
        if (state.profileEditRequest > 0) profileEditing = true
    }

    val fullBackgroundTrailer =
        state.trailer.active && state.trailer.displayMode == TrailerDisplayMode.FullBackground

    // Wallpaper base → ROM hero on top when scraped art exists (never the reverse).
    Box(modifier = modifier.fillMaxSize()) {
        HomeWallpaper(
            customPath = state.homeHub.wallpaperPath,
            dim = false,
            modifier = Modifier.fillMaxSize(),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val metrics = remember(maxHeight, maxWidth) {
                VerticalSelectorMetrics.from(maxHeight, maxWidth)
            }

            SoftHeroBackdrop(
                game = state.selectedGame,
                dimForTrailer = fullBackgroundTrailer,
                modifier = Modifier.fillMaxSize(),
            )
            HeroTrailerLayer(
                state = state.trailer,
                modifier = Modifier.fillMaxSize(),
            )
            if (fullBackgroundTrailer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.40f),
                                ),
                            ),
                        ),
                )
            }

            // Soft high-key veil for title legibility — keeps hero visible full-bleed.
            val density = LocalDensity.current
            val washCenter = with(density) {
                Offset(maxWidth.toPx() * 0.55f, maxHeight.toPx() * 0.35f)
            }
            val washRadius = with(density) { maxWidth.toPx() * 0.85f }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = washCenter,
                            radius = washRadius,
                        ),
                    ),
            )

            if (state.games.isEmpty()) {
                VerticalEmptyNotice(
                    isScanning = state.scanProgress.isRunning,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = metrics.topChrome, bottom = 12.dp)
                        .graphicsLayer {
                            alpha = chromeAlpha
                            translationY = chromeSlidePx
                        },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 8.dp,
                            end = 20.dp,
                            top = metrics.topChrome,
                            bottom = 8.dp,
                        )
                        .graphicsLayer {
                            alpha = chromeAlpha
                            translationY = chromeSlidePx
                        },
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    VerticalGameColumn(
                        games = state.games,
                        selectedIndex = state.selectedGameIndex,
                        selectedTab = state.selectedTab,
                        metrics = metrics,
                        onSelectGame = onSelectGame,
                        onLaunchGame = onLaunchGame,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(metrics.stripWidth),
                    )

                    VerticalDetailPane(
                        game = state.selectedGame,
                        insight = state.insight,
                        mediaWidth = metrics.mediaWidth,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = -chromeSlidePx
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AccountPill(
                    expanded = state.accountPanelExpanded && !state.isLaunching,
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
                )
                SystemPill(
                    profile = state.profile,
                    avatarImageModel = state.profileAvatarModel,
                    raScore = state.achievements.profile?.totalPoints,
                    recentAchievements = state.achievements.recent,
                    jumpBackGames = state.quickLaunchGames.take(3),
                    expanded = state.systemPanelExpanded && !state.isLaunching,
                    selectedRowIndex = state.systemPanelSelectedIndex,
                    onToggle = onToggleSystemPanel,
                    onSelectRow = onSelectSystemRow,
                    onActivateRow = onActivateSystemRow,
                )
            }

            AchievementsPill(
                expanded = state.achievementsPanelExpanded && !state.isLaunching,
                state = state.achievements,
                onToggle = onToggleAchievementsPanel,
                onSelectTab = onSelectAchievementsTab,
                onLogin = onLoginRetroAchievements,
                onLoginWithApiKey = onLoginRetroAchievementsWithApiKey,
                onSignOut = onSignOutRetroAchievements,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = metrics.mediaHeight + 20.dp,
                    )
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = chromeSlidePx
                    },
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
}

/** Viewport-derived sizes so the focused case stays ~40% of pane height. */
private data class VerticalSelectorMetrics(
    val focusCaseWidth: Dp,
    val focusSlotHeight: Dp,
    val neighborCaseWidth: Dp,
    val neighborSlotHeight: Dp,
    val stripWidth: Dp,
    val mediaWidth: Dp,
    val mediaHeight: Dp,
    val topChrome: Dp,
) {
    companion object {
        fun from(paneHeight: Dp, paneWidth: Dp): VerticalSelectorMetrics {
            // XmbGameTile: height = width / CASE_ASPECT (2:3) and focused scale ≈ 1.14.
            val focusVisualH = paneHeight * FOCUS_HEIGHT_FRAC
            val focusBaseW = focusVisualH / (CASE_HEIGHT_OVER_WIDTH * XMB_FOCUS_SCALE)
            val neighborVisualH = paneHeight * NEIGHBOR_HEIGHT_FRAC
            val neighborBaseW = neighborVisualH / (CASE_HEIGHT_OVER_WIDTH * XMB_NEAR_SCALE)
            val focusW = focusBaseW.coerceIn(120.dp, paneWidth * 0.28f)
            // Media docks bottom-right; ~20% larger than the focused case width.
            val mediaW = (focusW * MEDIA_SIZE_SCALE).coerceAtMost(paneWidth * 0.36f)
            val mediaH = mediaW * MEDIA_ASPECT
            val strip = SYSTEM_ICON_SLOT + 10.dp + focusBaseW + 12.dp
            return VerticalSelectorMetrics(
                focusCaseWidth = focusW,
                focusSlotHeight = (focusVisualH + 10.dp).coerceAtLeast(160.dp),
                neighborCaseWidth = neighborBaseW.coerceIn(72.dp, focusBaseW * 0.78f),
                neighborSlotHeight = (neighborVisualH + 6.dp).coerceAtLeast(96.dp),
                stripWidth = strip.coerceAtMost(paneWidth * 0.42f),
                mediaWidth = mediaW,
                mediaHeight = mediaH.coerceIn(88.dp, paneHeight * 0.32f),
                topChrome = (paneHeight * TOP_CHROME_FRAC).coerceIn(48.dp, 64.dp),
            )
        }
    }
}

@Composable
private fun SoftHeroBackdrop(
    game: Game?,
    dimForTrailer: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val artPath = game?.heroImagePath ?: game?.boxArtPath
        // Skip the opaque empty fallback so the home wallpaper underneath stays visible.
        if (!artPath.isNullOrBlank() && !dimForTrailer) {
            ArtworkImage(
                path = artPath,
                contentDescription = null,
                fallbackText = "",
                contentScale = ContentScale.Crop,
                cacheInMemory = false,
                decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Light legibility wash over full-opacity ROM art — never dims the artwork itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.12f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.18f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun VerticalGameColumn(
    games: List<Game>,
    selectedIndex: Int,
    selectedTab: LibraryTab?,
    metrics: VerticalSelectorMetrics,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val verticalPad = ((maxHeight - metrics.focusSlotHeight) / 2f).coerceAtLeast(16.dp)

        LaunchedEffect(selectedIndex, games.size, metrics.focusSlotHeight) {
            if (games.isEmpty()) return@LaunchedEffect
            val target = selectedIndex.coerceIn(0, games.lastIndex)
            listState.animateScrollToItem(target)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = verticalPad),
            verticalArrangement = Arrangement.spacedBy(CASE_GAP),
            horizontalAlignment = Alignment.Start,
        ) {
            itemsIndexed(items = games, key = { _, game -> game.id }) { index, game ->
                val focused = index == selectedIndex
                val distance = abs(index - selectedIndex)
                val slotH = if (focused) metrics.focusSlotHeight else metrics.neighborSlotHeight
                val caseW = if (focused) metrics.focusCaseWidth else metrics.neighborCaseWidth

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotH),
                ) {
                    if (focused && selectedTab != null) {
                        PlatformIcon(
                            tab = selectedTab,
                            selected = true,
                            size = 48.dp,
                        )
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        XmbGameTile(
                            game = game,
                            focused = focused,
                            distanceFromFocus = distance,
                            onClick = {
                                if (index == selectedIndex) onLaunchGame(index)
                                else onSelectGame(index)
                            },
                            baseWidth = caseW,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalDetailPane(
    game: Game?,
    insight: GameInsightUiState,
    mediaWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
    val exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 4.dp),
        ) {
            // Push logo/title into the middle-right band with breathing room above.
            Spacer(modifier = Modifier.weight(0.22f))

            AnimatedContent(
                targetState = game?.id to (game?.logoImagePath to game?.title),
                transitionSpec = { enter togetherWith exit },
                label = "verticalFocusedTitle",
                modifier = Modifier.fillMaxWidth(),
            ) { (_, logoAndTitle) ->
                val (logoPath, title) = logoAndTitle
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!logoPath.isNullOrBlank()) {
                        ArtworkImage(
                            path = logoPath,
                            contentDescription = title,
                            fallbackText = title.orEmpty(),
                            contentScale = ContentScale.Fit,
                            cacheInMemory = false,
                            decodeMaxEdgePx = 720,
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(0.9f)
                                .height(88.dp),
                        )
                    } else {
                        Text(
                            text = title.orEmpty(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 36.sp,
                                lineHeight = 42.sp,
                                letterSpacing = (-0.5).sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 560.dp),
                        )
                    }
                    if (game != null) {
                        PlaytimePill(playTimeMs = game.playTimeMs)
                    }
                }
            }

            // Open air under the title — hero stays visible; media docks bottom-right.
            Spacer(modifier = Modifier.weight(0.78f))
        }

        MediaPreviewCard(
            insight = insight,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(mediaWidth)
                .aspectRatio(MEDIA_WIDTH_OVER_HEIGHT),
        )
    }
}

@Composable
private fun PlaytimePill(playTimeMs: Long) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    Text(
        text = "Playtime: ${formatPlaytime(playTimeMs)}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = glass.content,
        modifier = Modifier
            .liquidGlass(
                shape = ArcadiaGlass.PillShape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Subtle,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Single featured still — width matched to focused case, docks bottom-right. */
@Composable
private fun MediaPreviewCard(
    insight: GameInsightUiState,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    LiquidGlassSurface(
        modifier = modifier,
        shape = ArcadiaGlass.CardShape,
        tone = GlassTone.Surface,
        intensity = GlassIntensity.Standard,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                insight.hasScreenshots -> {
                    ArtworkImage(
                        path = insight.screenshotPaths.first(),
                        contentDescription = null,
                        fallbackText = "",
                        contentScale = ContentScale.Crop,
                        cacheInMemory = false,
                        decodeMaxEdgePx = 720,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                insight.screenshotsLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    Text(
                        text = "No media",
                        style = MaterialTheme.typography.labelMedium,
                        color = glass.contentMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Text(
                text = "Media",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .liquidGlass(
                        shape = ArcadiaGlass.PillShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Subtle,
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun VerticalEmptyNotice(isScanning: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (isScanning) {
                "Scanning your library…"
            } else {
                "No games here yet. Run a scan from Settings, or press Start."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun formatPlaytime(millis: Long): String {
    if (millis < 60_000L) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours <= 0L -> "$minutes min"
        minutes == 0L -> if (hours == 1L) "1 hour" else "$hours hours"
        else -> "${hours}h ${minutes}m"
    }
}

/** Focused case target height as a fraction of the pane. */
private const val FOCUS_HEIGHT_FRAC = 0.40f
private const val NEIGHBOR_HEIGHT_FRAC = 0.22f
private const val TOP_CHROME_FRAC = 0.055f
/** Matches [XmbGameTile] CASE_ASPECT = 2/3 → height = width * 1.5. */
private const val CASE_HEIGHT_OVER_WIDTH = 1.5f
private const val XMB_FOCUS_SCALE = 1.14f
private const val XMB_NEAR_SCALE = 0.94f
/** Landscape media preview (~16:10). */
private const val MEDIA_WIDTH_OVER_HEIGHT = 16f / 10f
private const val MEDIA_ASPECT = 10f / 16f
/** Media preview vs focused case width (~20% larger). */
private const val MEDIA_SIZE_SCALE = 1.2f
private val SYSTEM_ICON_SLOT = 48.dp
private val CASE_GAP = 10.dp

@XoraPreview
@Composable
private fun VerticalGameSelectorPanePreview() {
    XoraPreviewTheme {
        VerticalGameSelectorPane(
            state = previewHomeUi(homePage = HomePage.GameSelector),
            onSelectTab = {},
            onSelectGame = {},
            onLaunchGame = {},
            onToggleAccountPanel = {},
            onToggleSystemPanel = {},
            onToggleAchievementsPanel = {},
            onSelectSocialTab = {},
            onSelectAccountRow = {},
            onActivateAccountRow = {},
            onSelectSystemRow = {},
            onActivateSystemRow = {},
            onSaveProfile = { _, _ -> },
            onSelectAvatarPreset = {},
            onRequestLocalAvatar = {},
            onUseRaAvatar = {},
            onClearAvatar = {},
            onFriendSearchChange = {},
            onReplyDraftChange = {},
            onSelectAchievementsTab = {},
            onLoginRetroAchievements = { _, _ -> },
            onLoginRetroAchievementsWithApiKey = { _, _ -> },
            onSignOutRetroAchievements = {},
        )
    }
}
