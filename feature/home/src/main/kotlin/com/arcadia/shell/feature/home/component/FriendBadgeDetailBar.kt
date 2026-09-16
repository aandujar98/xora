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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.retroachievements.RaRecentUnlock

/** Matches the card above it, so the two read as one object that grew a drawer. */
private val DetailShape = RoundedCornerShape(14.dp)
private val DetailBadgeSize = 52.dp
private val DetailHardcore = Color(0xFFFFA22B)
private val DetailRim = Color(0xFFFECF67)

/**
 * Figma 973:2055: the inspected badge's details, sitting under the Profile Card — art on the left,
 * name over description in the middle, and points / hardcore / date stacked on the right.
 */
@Composable
fun FriendBadgeDetailBar(
    unlock: RaRecentUnlock,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .width(FriendBadgeDetailWidth)
            .xoraModalGlass(DetailShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(DetailBadgeSize)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(2.dp, DetailRim, RoundedCornerShape(8.dp))
                .padding(2.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(unlock.badgeUrl)
                    .crossfade(120)
                    .build(),
                contentDescription = unlock.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = unlock.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A card-published badge carries no description; the game stands in for it.
            val detail = unlock.description.trim().ifBlank { unlock.gameTitle.trim() }
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (unlock.points > 0) {
                Text(
                    text = "${unlock.points} PTS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.End,
                )
            }
            if (unlock.hardcore) {
                Text(
                    text = "HARDCORE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DetailHardcore,
                    textAlign = TextAlign.End,
                )
            }
            val date = unlock.date.trim()
            if (date.isNotEmpty()) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Wider than the card, as the reference has it, so the details are not cramped under it. */
val FriendBadgeDetailWidth = 480.dp
