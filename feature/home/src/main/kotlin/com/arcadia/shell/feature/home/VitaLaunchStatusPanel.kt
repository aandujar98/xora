package com.arcadia.shell.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.XoraSecondaryText

// Design px on the 1920x1080 artboard, from docs/design/reference/HOME-SHORTCUT-PSO*.jpg.
private const val PANEL_INSET_X = 84f
private const val PANEL_INSET_Y = 62f
private const val COMPACT_H = 58f
private const val EXPANDED_W = 716f
private const val EXPANDED_ART = 122f

private val PanelInk = Color.White
private val TrophyGold = Color(0xFFFFA22B)

/** Dark glass for the compact pill, which sits over whatever wallpaper the title brought. */
private val CompactFill = Brush.verticalGradient(
    listOf(Color(0xCC2A2E36), Color(0xCC15171C)),
)

/** The expanded card is lighter, the way the design has it over a bright LiveArea page. */
private val ExpandedFill = Brush.verticalGradient(
    listOf(Color(0xD9767E8C), Color(0xD94A515C)),
)

private val PlatformChipFill = Brush.verticalGradient(
    listOf(Color(0xFFF0F2F4), Color(0xFFBFC6D0)),
)

/**
 * The LiveArea page's corner status: trophies for the title and the friends who are in it.
 *
 * Two shapes rather than two states the player switches between — a pill while all there is to say
 * is a score and some faces, and the full card once RetroAchievements has badge art to show. The
 * page has no free button to toggle on, and a panel that needs discovering is a panel nobody sees.
 */
@Composable
fun VitaLaunchStatusPanel(
    status: VitaLaunchStatus,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    if (status.isEmpty) return
    if (status.expanded) {
        ExpandedStatusCard(status = status, unit = unit, modifier = modifier)
    } else {
        CompactStatusPill(status = status, unit = unit, modifier = modifier)
    }
}

/** Where the panel sits: bottom-right of the sheet, inset from both edges. */
fun vitaLaunchStatusInset(unit: Float): Pair<Dp, Dp> =
    (PANEL_INSET_X * unit).dp to (PANEL_INSET_Y * unit).dp

@Composable
private fun CompactStatusPill(
    status: VitaLaunchStatus,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val height = (COMPACT_H * unit).dp
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(CompactFill)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(percent = 50))
            .padding(horizontal = (22f * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((10f * unit).dp),
    ) {
        if (status.hasTrophies) {
            Image(
                painter = painterResource(R.drawable.trophy),
                contentDescription = null,
                colorFilter = ColorFilter.tint(PanelInk),
                modifier = Modifier.size((30f * unit).dp),
            )
            XoraSecondaryText(
                text = status.progressLabel,
                fontSize = (26f * unit).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (status.friendAvatars.isNotEmpty()) {
            Image(
                painter = painterResource(R.drawable.xora_time),
                contentDescription = null,
                modifier = Modifier.size((30f * unit).dp),
            )
            FriendFaces(
                avatars = status.friendAvatars,
                overflow = status.friendOverflow,
                size = (38f * unit).dp,
                // The pill overlaps its faces; the card below spaces them out.
                overlap = true,
                unit = unit,
            )
        }
    }
}

@Composable
private fun ExpandedStatusCard(
    status: VitaLaunchStatus,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape((18f * unit).dp)
    Column(
        modifier = modifier
            .width((EXPANDED_W * unit).dp)
            .clip(shape)
            .background(ExpandedFill)
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), shape)
            .padding((14f * unit).dp),
        verticalArrangement = Arrangement.spacedBy((10f * unit).dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((12f * unit).dp)) {
            CardArt(url = status.boxArtUrl, size = (EXPANDED_ART * unit).dp, unit = unit)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy((8f * unit).dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XoraSecondaryText(
                        text = status.title,
                        fontSize = (30f * unit).sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (status.platformLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.width((10f * unit).dp))
                        PlatformChip(label = status.platformLabel, unit = unit)
                    }
                }
                BadgeStrip(
                    urls = status.badgeUrls,
                    slot = (54f * unit).dp,
                    unit = unit,
                )
            }
        }

        if (status.hasTrophies) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10f * unit).dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.trophy),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(PanelInk),
                    modifier = Modifier.size((34f * unit).dp),
                )
                XoraSecondaryText(
                    text = status.earned.toString(),
                    fontSize = (34f * unit).sp,
                    fontWeight = FontWeight.Bold,
                    fillColor = TrophyGold,
                    maxLines = 1,
                )
                XoraSecondaryText(
                    text = "/${status.total}",
                    fontSize = (22f * unit).sp,
                    fillColor = TrophyGold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width((10f * unit).dp))
                ProgressTrack(
                    fraction = status.fraction,
                    unit = unit,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (status.friendAvatars.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((12f * unit).dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.xora_time),
                    contentDescription = null,
                    modifier = Modifier.size((34f * unit).dp),
                )
                XoraSecondaryText(
                    text = "RECENTLY PLAYED:",
                    fontSize = (22f * unit).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                FriendFaces(
                    avatars = status.friendAvatars,
                    overflow = status.friendOverflow,
                    size = (44f * unit).dp,
                    overlap = false,
                    unit = unit,
                )
            }
        }
    }
}

@Composable
private fun CardArt(url: String, size: Dp, unit: Float) {
    val shape = RoundedCornerShape((10f * unit).dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), shape),
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(url)
                    .crossfade(120)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlatformChip(label: String, unit: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(PlatformChipFill)
            .padding(horizontal = (16f * unit).dp, vertical = (4f * unit).dp),
    ) {
        XoraSecondaryText(
            text = label.uppercase(),
            fontSize = (20f * unit).sp,
            fontWeight = FontWeight.Bold,
            fillColor = Color(0xFF2A2E36),
            maxLines = 1,
        )
    }
}

/**
 * The badge row. Earned art fills from the left; the rest stay empty plates rather than being
 * dropped, so the strip reads as "five of seven" at a glance.
 */
@Composable
private fun BadgeStrip(urls: List<String>, slot: Dp, unit: Float) {
    val shape = RoundedCornerShape((7f * unit).dp)
    Row(horizontalArrangement = Arrangement.spacedBy((7f * unit).dp)) {
        repeat(VITA_LAUNCH_BADGE_SLOTS) { index ->
            val url = urls.getOrNull(index)
            Box(
                modifier = Modifier
                    .size(slot)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = if (url == null) 0.18f else 0.35f))
                    .border(
                        width = 1.5.dp,
                        color = if (url == null) {
                            Color.White.copy(alpha = 0.3f)
                        } else {
                            Color(0xFFFECF67)
                        },
                        shape = shape,
                    )
                    .alpha(if (url == null) 0.55f else 1f),
            ) {
                if (url != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(url)
                            .crossfade(120)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(1.5.dp).clip(shape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(fraction: Float, unit: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height((22f * unit).dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.85f))
            .border(1.dp, Color.White, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(shape)
                .background(TrophyGold),
        )
    }
}

@Composable
private fun FriendFaces(
    avatars: List<String>,
    overflow: Int,
    size: Dp,
    overlap: Boolean,
    unit: Float,
) {
    Row(
        horizontalArrangement = if (overlap) {
            Arrangement.spacedBy(-size / 3)
        } else {
            Arrangement.spacedBy((8f * unit).dp)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatars.forEach { url ->
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(url)
                        .crossfade(120)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .height(size)
                    .clip(RoundedCornerShape(percent = 50))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = (12f * unit).dp),
                contentAlignment = Alignment.Center,
            ) {
                XoraSecondaryText(
                    text = "+$overflow",
                    fontSize = (20f * unit).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
