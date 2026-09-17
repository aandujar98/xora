package com.arcadia.shell.feature.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.gif.repeatCount
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.model.ChirperVoice

/** The icon each voice animates, one `chirper_<id>.gif` per entry. */
fun chirperIconRes(voice: ChirperVoice): Int = when (voice) {
    ChirperVoice.UsagiShade -> R.raw.chirper_usagishade
    ChirperVoice.UsagiIshii -> R.raw.chirper_usagiishii
    ChirperVoice.IPrinceAngel -> R.raw.chirper_iprinceangel
    ChirperVoice.Furogii -> R.raw.chirper_furogii
    ChirperVoice.Sora -> R.raw.chirper_sora
    ChirperVoice.Tonic -> R.raw.chirper_tonic
    ChirperVoice.Somarix -> R.raw.chirper_somarix
    ChirperVoice.Makoto -> R.raw.chirper_makoto
    ChirperVoice.Lyn -> R.raw.chirper_lyn
    ChirperVoice.Marlix -> R.raw.chirper_marlix
}

// docs/design/reference/EditProfile-SelectaChirp.jpg. The card is light glass over the dimmed,
// blurred shell — the one modal in the shell that is not dark.
private val CardTop = Color(0xFFA6ADBB)
private val CardBottom = Color(0xFFDAE0E3)
private val PillTop = Color(0xFFCAD2DE)
private val PillBottom = Color(0xFFAEB7C8)
private val PillEdge = Color.White.copy(alpha = 0.55f)
private val RowInk = Color(0xFF3D3E43)
private val RowRule = Color(0xFF3D3E43).copy(alpha = 0.35f)

private val CardShape = RoundedCornerShape(28.dp)
private val RowHeight = 62.dp
private val IconSize = 62.dp

/**
 * "Select a Chirp": one pill per voice, the icon overlapping its left edge and a speaker at the
 * right. Choosing one takes the accent gradient and dims every other row, so which voice is live
 * reads from across a room.
 *
 * [onAudition] both selects and plays a take — the chirp *is* the confirmation. The icon plays its
 * GIF through once and stops; it never loops, so the list sits still except where the cursor is.
 */
@Composable
fun ChirperPicker(
    selected: ChirperVoice,
    focusedIndex: Int,
    onAudition: (ChirperVoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voices = remember { ChirperVoice.entries.toList() }
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex in voices.indices) listState.animateScrollToItem(focusedIndex)
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.86f)
                .clip(CardShape)
                .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CardShape)
                .padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(voices) { index, voice ->
                    ChirperRow(
                        voice = voice,
                        chosen = voice == selected,
                        // Nothing is dimmed until a voice is live; then everything else steps back.
                        dimmed = voice != selected,
                        focused = index == focusedIndex,
                        onClick = { onAudition(voice) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChirperRow(
    voice: ChirperVoice,
    chosen: Boolean,
    dimmed: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(voice.accent)
    // Only the chosen row is fully lit; the rest sit at half, as the design has them.
    val fade by animateFloatAsState(
        targetValue = if (dimmed) 0.5f else 1f,
        animationSpec = tween(ArcadiaMotion.Medium),
        label = "chirperRowFade",
    )
    val edge by animateColorAsState(
        targetValue = if (focused) accent else PillEdge,
        animationSpec = tween(ArcadiaMotion.Fast),
        label = "chirperRowEdge",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The pill starts under the icon's midpoint so the icon reads as sitting on top of it.
        Row(
            modifier = Modifier
                .padding(start = IconSize / 2)
                .fillMaxWidth()
                .height(RowHeight * 0.88f)
                .alpha(fade)
                .clip(ArcadiaPillShape)
                .background(
                    if (chosen) {
                        Brush.horizontalGradient(listOf(Color.White, accent))
                    } else {
                        Brush.verticalGradient(listOf(PillTop, PillBottom))
                    },
                )
                .border(if (focused) 2.dp else 1.dp, edge, ArcadiaPillShape)
                .padding(start = IconSize / 2 + 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.displayName.uppercase(),
                    fontFamily = XoraFonts.XmbLabel,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = RowInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                // The hairline under the name, which the design carries on every row.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(RowRule),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.xmb_sound),
                contentDescription = null,
                colorFilter = ColorFilter.tint(RowInk),
                modifier = Modifier.size(26.dp),
            )
        }
        ChirperIcon(
            voice = voice,
            playing = chosen || focused,
            size = IconSize,
            modifier = Modifier.alpha(fade),
        )
    }
}

private val ArcadiaPillShape = RoundedCornerShape(percent = 50)

/**
 * A chirper's animated icon. [playing] changing to true restarts the GIF from its first frame and
 * runs it through once — the caches are off precisely so that re-running it is possible; these are
 * 50–180 KB each and only ever a handful are on screen.
 */
@Composable
fun ChirperIcon(
    voice: ChirperVoice,
    playing: Boolean,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    var plays by remember(voice) { mutableIntStateOf(0) }
    LaunchedEffect(voice, playing) {
        if (playing) plays++
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
    ) {
        key(plays) {
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(chirperIconRes(voice))
                    .repeatCount(0)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = voice.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The "Select a Chirp" speech bubble in the profile editor, per the same design. */
@Composable
fun SelectAChirpBubble(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(ArcadiaPillShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFF2F3F5), Color(0xFFBFC6D2))))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else PillEdge,
                shape = ArcadiaPillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Select a Chirp",
            fontFamily = XoraFonts.XmbLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = RowInk,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Image(
            painter = painterResource(R.drawable.xmb_sound),
            contentDescription = null,
            colorFilter = ColorFilter.tint(RowInk),
            modifier = Modifier.size(22.dp),
        )
    }
}
