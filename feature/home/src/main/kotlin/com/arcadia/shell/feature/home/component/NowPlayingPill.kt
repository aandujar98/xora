package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.launcher.music.NowPlayingState

/**
 * "What's playing" card that stands in for the RetroAchievements pill while Music is focused.
 *
 * Same corner and glass language as the RA pill so the swap reads as one chrome slot changing
 * contents rather than two competing panels.
 */
@Composable
fun NowPlayingPill(
    state: NowPlayingState,
    modifier: Modifier = Modifier,
) {
    val track = state.track
    Row(
        modifier = modifier
            .width(300.dp)
            .liquidGlass(
                shape = RoundedCornerShape(18.dp),
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Standard,
            )
            .padding(10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            path = track?.albumArtUri,
            fallback = track?.title,
            size = 48,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track?.title ?: "Nothing playing",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                color = Color.White,
            )
            Text(
                text = track?.artist ?: "Pick a song from Music",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = XoraFonts.Secondary,
                    fontSize = 11.sp,
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

/** Filled meter on a dark rail, as the concept draws the scrubber. */
@Composable
internal fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Int = 6,
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.95f), Color.White),
                    ),
                ),
        )
    }
}
