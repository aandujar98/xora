package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.XoraOutlinedText
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.feature.home.component.ProfileAvatar
import com.arcadia.shell.feature.home.component.xmb.drawableResForPlatformId
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaGameProgress
import com.arcadia.shell.retroachievements.RaProfile
import kotlinx.coroutines.delay

/**
 * RetroAchievements library over the shell wallpaper. The XMB recedes underneath;
 * cheevo badges then populate in a short stagger.
 */
@Composable
fun RaLibraryPane(
    state: HomeUiState,
    onSelectIndex: (Int) -> Unit,
    onSelectTab: (RaLibraryTab) -> Unit,
    onSelectPlatformFilter: (String?) -> Unit,
    onActivate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    populateCheevos: Boolean = true,
    onSelectCheevoIndex: (Int) -> Unit = {},
    onCloseGameDetail: () -> Unit = {},
) {
    val ra = state.raLibrary
    val visible = ra.visibleGames
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    var cheevosReady by remember { mutableStateOf(reduceMotion || !populateCheevos) }

    LaunchedEffect(ra.selectedIndex, visible.size, ra.tab, ra.platformFilter) {
        if (visible.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(ra.selectedIndex.coerceIn(0, visible.lastIndex))
    }

    LaunchedEffect(populateCheevos, visible.isNotEmpty(), reduceMotion) {
        if (!populateCheevos || visible.isEmpty() || reduceMotion) {
            cheevosReady = true
            return@LaunchedEffect
        }
        cheevosReady = false
        delay(ArcadiaMotion.Medium.toLong())
        cheevosReady = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 24.dp, top = 28.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RaLibrarySidePanel(
                profile = state.profile,
                profileAvatarModel = state.profileAvatarModel,
                raProfile = state.achievements.profile,
                tab = ra.tab,
                platformFilter = ra.platformFilter,
                onSelectTab = onSelectTab,
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    ra.isLoading && visible.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    ra.error != null && visible.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                12.dp,
                                Alignment.CenterVertically,
                            ),
                        ) {
                            Text(
                                text = ra.error,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = XoraFonts.Secondary,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }

                    visible.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            XoraOutlinedText(
                                text = if (!state.achievements.credentials.isConfigured) {
                                    "Sign in to RetroAchievements to see your library."
                                } else {
                                    "No RetroAchievements progress yet."
                                },
                                fontFamily = XoraFonts.Secondary,
                                fontSize = 18.sp,
                                outlineWidth = 2.dp,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            itemsIndexed(
                                items = visible,
                                key = { _, row -> row.game.gameId },
                            ) { index, row ->
                                RaLibraryGameRowCard(
                                    row = row,
                                    selected = index == ra.selectedIndex,
                                    populateCheevos = cheevosReady,
                                    appearIndex = index,
                                    onClick = {
                                        onSelectIndex(index)
                                        onActivate()
                                    },
                                )
                            }
                        }
                    }
                }

                if (ra.platforms.isNotEmpty()) {
                    RaPlatformFilterRow(
                        platforms = ra.platforms,
                        selected = ra.platformFilter,
                        onSelect = onSelectPlatformFilter,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = ra.gameDetailOpen,
            enter = fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(tween(ArcadiaMotion.Fast, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(ArcadiaMotion.Fast, easing = FastOutSlowInEasing),
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            RaGameCheevoWindow(
                ra = ra,
                onSelectCheevo = onSelectCheevoIndex,
                onRetry = onActivate,
                onClose = onCloseGameDetail,
            )
        }
    }
}

@Composable
private fun RaLibrarySidePanel(
    profile: com.arcadia.shell.datastore.LocalProfile,
    profileAvatarModel: String?,
    raProfile: RaProfile?,
    tab: RaLibraryTab,
    platformFilter: String?,
    onSelectTab: (RaLibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = modifier
            .xmbAssetShadow(
                unit = 1f,
                shape = shape,
                alpha = XoraForegroundShadow.Alpha,
            )
            .liquidGlass(
                shape = shape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
                shimmer = true,
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        XoraOutlinedText(
            text = "RetroAchievements",
            fontFamily = XoraFonts.Title,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = XoraFonts.TitleLetterSpacing,
            maxLines = 2,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileAvatar(
                displayName = profile.displayName,
                presetId = profile.avatarPresetId,
                size = 48.dp,
                imageModel = profileAvatarModel,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                XoraOutlinedText(
                    text = raProfile?.username ?: profile.displayName,
                    fontFamily = XoraFonts.XmbLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    outlineWidth = 2.dp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                raProfile?.let {
                    XoraOutlinedText(
                        text = "${it.totalPoints} pts",
                        fontFamily = XoraFonts.Secondary,
                        fontSize = 13.sp,
                        outlineWidth = 1.5.dp,
                        fillColor = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RaTabChip(
                label = "By Platform",
                selected = tab == RaLibraryTab.ByPlatform,
                onClick = { onSelectTab(RaLibraryTab.ByPlatform) },
            )
            RaTabChip(
                label = "Recently Earned",
                selected = tab == RaLibraryTab.RecentlyEarned,
                onClick = { onSelectTab(RaLibraryTab.RecentlyEarned) },
            )
            RaTabChip(
                label = "Completion",
                selected = tab == RaLibraryTab.Completion,
                onClick = { onSelectTab(RaLibraryTab.Completion) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        XoraOutlinedText(
            text = "LB / RB  ${platformFilter ?: "All platforms"}",
            fontFamily = XoraFonts.Secondary,
            fontSize = 13.sp,
            outlineWidth = 1.5.dp,
            fillColor = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RaTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        label = "raTabBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) {
            Color.White
        } else {
            Color.White.copy(alpha = 0.70f)
        },
        label = "raTabFg",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = XoraFonts.XmbLabel,
        ),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(
                        1.5.dp,
                        Color.White.copy(alpha = 0.55f),
                        RoundedCornerShape(20.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun RaLibraryGameRowCard(
    row: RaLibraryGameRow,
    selected: Boolean,
    populateCheevos: Boolean,
    appearIndex: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val rim = MaterialTheme.colorScheme.primary
    val borderBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                rim.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.55f),
                rim.copy(alpha = 0.75f),
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
        )
    }
    val reduceMotion = rememberReduceMotion()
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(appearIndex, reduceMotion) {
        if (reduceMotion) {
            appear.snapTo(1f)
            return@LaunchedEffect
        }
        appear.snapTo(0f)
        delay((appearIndex.coerceAtMost(12) * 28L))
        appear.animateTo(
            1f,
            tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = appear.value
                alpha = t
                translationY = (1f - t) * 14f
            }
            .xmbAssetShadow(
                unit = 1f,
                shape = shape,
                alpha = if (selected) XoraForegroundShadow.Alpha else XoraForegroundShadow.TitleAlpha,
            )
            .liquidGlass(
                shape = shape,
                tone = GlassTone.OverMedia,
                intensity = if (selected) GlassIntensity.Strong else GlassIntensity.Standard,
            )
            .border(width = if (selected) 2.dp else 0.dp, brush = borderBrush, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(row.game.imageIconUrl.takeIf { it.isNotBlank() })
                .crossfade(120)
                .build(),
            contentDescription = row.game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = row.game.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = XoraFonts.XmbLabel,
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.recentBadgeUrls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.recentBadgeUrls.take(8).forEachIndexed { badgeIndex, url ->
                        CheevoBadge(
                            url = url,
                            populate = populateCheevos,
                            index = badgeIndex,
                        )
                    }
                }
            } else {
                Text(
                    text = row.game.consoleName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = XoraFonts.Secondary,
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(88.dp),
        ) {
            Text(
                text = row.game.progressLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = XoraFonts.Secondary,
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (row.game.isMastered) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    Color.White.copy(alpha = 0.88f)
                },
            )
            RaProgressBar(
                fraction = row.game.completionFraction,
                mastered = row.game.isMastered,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            )
        }
    }
}

@Composable
private fun CheevoBadge(
    url: String,
    populate: Boolean,
    index: Int,
) {
    val reduceMotion = rememberReduceMotion()
    val appear = remember { Animatable(if (reduceMotion || populate) 0f else 0f) }
    LaunchedEffect(populate, index, reduceMotion) {
        if (reduceMotion) {
            appear.snapTo(if (populate) 1f else 0f)
            return@LaunchedEffect
        }
        if (!populate) {
            appear.snapTo(0f)
            return@LaunchedEffect
        }
        appear.snapTo(0f)
        delay(index * 35L)
        appear.animateTo(
            1f,
            tween(180, easing = FastOutSlowInEasing),
        )
    }
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                val t = appear.value
                alpha = t
                scaleX = 0.55f + 0.45f * t
                scaleY = 0.55f + 0.45f * t
            }
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
private fun RaProgressBar(
    fraction: Float,
    mastered: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = Color.White.copy(alpha = 0.18f)
    val fill = if (mastered) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(fill),
        )
    }
}

@Composable
private fun RaPlatformFilterRow(
    platforms: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "all") {
            PlatformChip(
                label = "All",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        itemsIndexed(platforms, key = { _, name -> name }) { _, name ->
            PlatformChip(
                label = name,
                selected = selected == name,
                onClick = { onSelect(name) },
                platformHint = name,
            )
        }
    }
}

@Composable
private fun PlatformChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    platformHint: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val platformId = platformHint?.let { guessPlatformId(it) }
    Row(
        modifier = Modifier
            .liquidGlass(
                shape = shape,
                tone = GlassTone.OverMedia,
                intensity = if (selected) GlassIntensity.Standard else GlassIntensity.Subtle,
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (platformId != null) {
            androidx.compose.material3.Icon(
                painter = painterResource(drawableResForPlatformId(platformId)),
                contentDescription = label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = shortPlatformLabel(label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = XoraFonts.XmbLabel,
            ),
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.7f),
            maxLines = 1,
        )
    }
}

private val CheevoEarnedEdge = Color(0xFFEFBD17)
private val CheevoHardcoreEdge = Color(0xFFFFC95E)

@Composable
private fun RaGameCheevoWindow(
    ra: RaLibraryUiState,
    onSelectCheevo: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onClose)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .xmbAssetShadow(unit = 1f, shape = shape, alpha = XoraForegroundShadow.Alpha)
                .liquidGlass(
                    shape = shape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                    shimmer = true,
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val detail = ra.gameDetail
            val headerGame = detail?.title ?: ra.selectedGame?.game?.title.orEmpty()
            val headerConsole = detail?.consoleName ?: ra.selectedGame?.game?.consoleName.orEmpty()
            val headerProgress = detail?.progressLabel ?: ra.selectedGame?.game?.progressLabel.orEmpty()
            val headerIcon = detail?.imageIconUrl ?: ra.selectedGame?.game?.imageIconUrl.orEmpty()
            val context = LocalContext.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(headerIcon.takeIf { it.isNotBlank() })
                        .crossfade(120)
                        .build(),
                    contentDescription = headerGame,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    XoraOutlinedText(
                        text = headerGame.ifBlank { "Achievements" },
                        fontFamily = XoraFonts.Title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = XoraFonts.TitleLetterSpacing,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    XoraOutlinedText(
                        text = listOf(headerConsole, headerProgress)
                            .filter { it.isNotBlank() }
                            .joinToString("  ·  "),
                        fontFamily = XoraFonts.Secondary,
                        fontSize = 14.sp,
                        outlineWidth = 1.5.dp,
                        fillColor = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                ra.gameDetailLoading && detail == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                ra.gameDetailError != null && detail == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            12.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        Text(
                            text = ra.gameDetailError,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = XoraFonts.Secondary,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                detail == null || detail.achievements.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        XoraOutlinedText(
                            text = "No achievements for this game.",
                            fontFamily = XoraFonts.Secondary,
                            fontSize = 16.sp,
                            outlineWidth = 2.dp,
                        )
                    }
                }

                else -> {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(ra.cheevoIndex, detail.achievements.size) {
                        if (detail.achievements.isEmpty()) return@LaunchedEffect
                        gridState.animateScrollToItem(
                            ra.cheevoIndex.coerceIn(0, detail.achievements.lastIndex),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(RA_CHEEVO_GRID_COLUMNS),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        itemsIndexed(
                            items = detail.achievements,
                            key = { _, cheevo -> cheevo.id },
                        ) { index, cheevo ->
                            RaCheevoGridTile(
                                cheevo = cheevo,
                                selected = index == ra.cheevoIndex,
                                onClick = { onSelectCheevo(index) },
                            )
                        }
                    }
                }
            }

            val selected = ra.selectedCheevo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selected != null) {
                    val status = when {
                        selected.earnedHardcore -> "Hardcore · ${selected.points} pts"
                        selected.earned -> "Earned · ${selected.points} pts"
                        else -> "Locked · ${selected.points} pts"
                    }
                    Text(
                        text = selected.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = XoraFonts.XmbLabel,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = XoraFonts.Secondary,
                        ),
                        color = if (selected.earned) CheevoHardcoreEdge else Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                    Text(
                        text = selected.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = XoraFonts.Secondary,
                        ),
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RaCheevoGridTile(
    cheevo: RaAchievement,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val earned = cheevo.earned
    val edge = when {
        selected -> MaterialTheme.colorScheme.primary
        cheevo.earnedHardcore -> CheevoHardcoreEdge
        earned -> CheevoEarnedEdge
        else -> Color.White.copy(alpha = 0.22f)
    }
    val grayMatrix = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = if (earned) 0.12f else 0.42f))
            .border(if (selected) 2.5.dp else 1.5.dp, edge, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(cheevo.badgeUrl)
                .crossfade(80)
                .build(),
            contentDescription = cheevo.title,
            contentScale = ContentScale.Crop,
            colorFilter = if (earned) null else ColorFilter.colorMatrix(grayMatrix),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (earned) {
                        Modifier
                    } else {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.28f))
                        }
                    },
                ),
        )
    }
}

private fun shortPlatformLabel(name: String): String = when {
    name.contains("PlayStation Portable", ignoreCase = true) -> "PSP"
    name.contains("PlayStation Vita", ignoreCase = true) -> "Vita"
    name.contains("PlayStation 2", ignoreCase = true) -> "PS2"
    name.contains("PlayStation", ignoreCase = true) &&
        !name.contains("2") && !name.contains("3") -> "PS1"
    name.contains("Nintendo DS", ignoreCase = true) -> "NDS"
    name.contains("Game Boy Advance", ignoreCase = true) -> "GBA"
    name.contains("GameCube", ignoreCase = true) -> "GCN"
    name.contains("Mega Drive", ignoreCase = true) ||
        name.contains("Genesis", ignoreCase = true) -> "Genesis"
    name.length > 14 -> name.take(12) + "…"
    else -> name
}

private fun guessPlatformId(consoleName: String): String? {
    val n = consoleName.lowercase()
    return when {
        "playstation portable" in n || n == "psp" -> "psp"
        "playstation vita" in n -> "psvita"
        "playstation 2" in n -> "ps2"
        "playstation" in n -> "ps1"
        "nintendo ds" in n -> "nds"
        "nintendo 3ds" in n || "3ds" in n -> "3ds"
        "game boy advance" in n -> "gba"
        "game boy color" in n -> "gbc"
        "game boy" in n -> "gb"
        "gamecube" in n -> "gamecube"
        "nintendo 64" in n -> "n64"
        "super nintendo" in n || "snes" in n -> "snes"
        "nes" in n || "famicom" in n -> "nes"
        "dreamcast" in n -> "dreamcast"
        "saturn" in n -> "saturn"
        "genesis" in n || "mega drive" in n -> "genesis"
        "wii u" in n -> "wiiu"
        "wii" in n -> "wii"
        "switch" in n -> "switch"
        "arcade" in n -> "arcade"
        else -> null
    }
}
