package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.retroachievements.RaRecentUnlock

/** Matches the card above it, so the two read as one object that grew a drawer. */
private val DetailShape = RoundedCornerShape(14.dp)
private val DetailBadgeSize = 44.dp
private val DetailHardcore = Color(0xFFFFA22B)
private val DetailRim = Color(0xFFFECF67)

/**
 * The light silver bar the design has, not the dark glass the rest of the card is made of — it is
 * meant to read as a caption laid over the shell rather than another panel of the same card.
 */
private val DetailFill = Brush.horizontalGradient(
    0f to Color(0xFFE8ECF0),
    0.5f to Color(0xFFD4DCE2),
    1f to Color(0xFFBFCAD4),
)

/**
 * The inspected badge's details: art on the left, name over description in the middle, and
 * hardcore over the date on the right.
 *
 * No points pill, and thinner than it first was — this sits under a card that already says how
 * many points the player has, and the badge's own score was the least interesting thing on it.
 */
@Composable
fun FriendBadgeDetailBar(
    unlock: RaRecentUnlock,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .clip(DetailShape)
            .background(DetailFill)
            .border(1.dp, Color.White.copy(alpha = 0.6f), DetailShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(DetailBadgeSize)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(2.dp, DetailRim, RoundedCornerShape(7.dp))
                .padding(2.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(unlock.badgeUrl)
                    .crossfade(120)
                    .build(),
                contentDescription = unlock.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // White over light silver only reads because of the outline these carry.
            XoraSecondaryText(
                text = unlock.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // A card-published badge carries no description; the game stands in for it.
            val detail = unlock.description.trim().ifBlank { unlock.gameTitle.trim() }
            if (detail.isNotEmpty()) {
                XoraSecondaryText(
                    text = detail,
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (unlock.hardcore) {
                XoraSecondaryText(
                    text = "HARDCORE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fillColor = DetailHardcore,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            val date = unlock.date.trim()
            if (date.isNotEmpty()) {
                XoraSecondaryText(
                    text = date,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}
