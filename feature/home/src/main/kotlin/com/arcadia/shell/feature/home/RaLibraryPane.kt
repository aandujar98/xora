package com.arcadia.shell.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.SkyBackground
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.ProfileAvatar
import com.arcadia.shell.feature.home.component.xmb.drawableResForPlatformId
import com.arcadia.shell.retroachievements.RaProfile

/**
 * RetroAchievements library — games-with-progress list over the SORA sky,
 * liquid-glass rows (not white cards), medium-blue focus rim.
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
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val ra = state.raLibrary
    val visible = ra.visibleGames
    val listState = rememberLazyListState()

    LaunchedEffect(ra.selectedIndex, visible.size, ra.tab, ra.platformFilter) {
        if (visible.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(ra.selectedIndex.coerceIn(0, visible.lastIndex))
    }

    SkyBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RaLibraryHeader(
                profile = state.profile,
                profileAvatarModel = state.profileAvatarModel,
                raProfile = state.achievements.profile,
                tab = ra.tab,
                onSelectTab = onSelectTab,
                glassContent = glass.content,
                glassMuted = glass.contentMuted,
            )

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
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = ra.error,
                            style = MaterialTheme.typography.bodyMedium,
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
                        Text(
                            text = if (!state.achievements.credentials.isConfigured) {
                                "Sign in to RetroAchievements to see your library."
                            } else {
                                "No RetroAchievements progress yet."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = glass.contentMuted,
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
}

@Composable
private fun RaLibraryHeader(
    profile: com.arcadia.shell.datastore.LocalProfile,
    profileAvatarModel: String?,
    raProfile: RaProfile?,
    tab: RaLibraryTab,
    onSelectTab: (RaLibraryTab) -> Unit,
    glassContent: Color,
    glassMuted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .liquidGlass(
                    shape = ArcadiaGlass.PillShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Standard,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileAvatar(
                displayName = profile.displayName,
                presetId = profile.avatarPresetId,
                size = 28.dp,
                imageModel = profileAvatarModel,
            )
            Column {
                Text(
                    text = raProfile?.username ?: profile.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = glassContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                raProfile?.let {
                    Text(
                        text = "${it.totalPoints} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassMuted,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            MaterialTheme.colorScheme.primary
        } else {
            Color.White.copy(alpha = 0.14f)
        },
        label = "raTabBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            Color.White.copy(alpha = 0.78f)
        },
        label = "raTabFg",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun RaLibraryGameRowCard(
    row: RaLibraryGameRow,
    selected: Boolean,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = row.game.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.recentBadgeUrls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.recentBadgeUrls.take(8).forEach { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(80)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.10f)),
                        )
                    }
                }
            } else {
                Text(
                    text = row.game.consoleName,
                    style = MaterialTheme.typography.labelSmall,
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
                style = MaterialTheme.typography.labelMedium,
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
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.7f),
            maxLines = 1,
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
