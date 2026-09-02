package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.launcher.music.NowPlayingState

private val PlayerShape = RoundedCornerShape(20.dp)
private val ProgressStart = Color(0xFF006FFF)
private val ProgressEnd = Color(0xFF50D9FF)
private val ProgressRail = Color.White.copy(alpha = 0.22f)

/**
 * Compact Now Playing card while Music is focused. Tinted glass, square song art on the
 * left, and a blue gradient scrubber with a glass playhead — same language as the full player.
 */
@Composable
fun NowPlayingPill(
    state: NowPlayingState,
    modifier: Modifier = Modifier,
) {
    val track = state.track
    Row(
        modifier = modifier
            .width(360.dp)
            .xoraModalGlass(PlayerShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            path = track?.albumArtUri,
            fallback = track?.title,
            size = 88,
            corner = 10,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = track?.title ?: "Nothing playing",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                ),
                color = Color.White,
            )
            Text(
                text = track?.artist ?: "Pick a song from Music",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = XoraFonts.Secondary,
                    fontSize = 12.sp,
                ),
                color = Color.White.copy(alpha = 0.75f),
            )
            ProgressTrack(
                fraction = state.progress,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CoverArt(
    path: String?,
    fallback: String?,
    size: Int,
    corner: Int = 10,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(Color(0xFF101B24)),
        contentAlignment = Alignment.Center,
    ) {
        ArtworkImage(
            path = path,
            contentDescription = null,
            fallbackText = fallback?.take(2)?.uppercase().orEmpty(),
            contentScale = ContentScale.Crop,
            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Blue fill from #006FFF → #50D9FF, with a glass disc on the playhead. */
@Composable
internal fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Int = 6,
    showThumb: Boolean = true,
) {
    val frac = fraction.coerceIn(0f, 1f)
    val thumb = 14.dp
    val railH = height.dp
    BoxWithConstraints(
        modifier = modifier.height(if (showThumb) 16.dp else railH),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(railH)
                .clip(CircleShape)
                .background(ProgressRail),
        )
        Box(
            modifier = Modifier
                .width(maxWidth * frac)
                .height(railH)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(ProgressStart, ProgressEnd))),
        )
        if (showThumb) {
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumb) * frac)
                    .size(thumb)
                    .xoraForegroundShadow(
                        shape = CircleShape,
                        offset = 2.dp,
                        blur = 4.dp,
                    )
                    .liquidGlass(
                        shape = CircleShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Subtle,
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.78f), CircleShape)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
            )
        }
    }
}
