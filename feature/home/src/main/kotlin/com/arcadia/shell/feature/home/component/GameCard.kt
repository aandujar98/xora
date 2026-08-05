package com.arcadia.shell.feature.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.model.Game

@Composable
fun GameCard(
    game: Game,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A subtle scale and border is enough to read as "selected" from arm's length on a handheld,
    // without the layout shifting and reflowing neighbouring tiles.
    val focusTween = arcadiaTween<Float>(ArcadiaMotion.Medium)
    val focusDpTween = arcadiaTween<Dp>(ArcadiaMotion.Medium)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = focusTween,
        label = "cardScale",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = focusDpTween,
        label = "cardBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "cardTitleColor",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BOX_ART_ASPECT)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            ArtworkImage(
                path = game.gridArt,
                contentDescription = game.title,
                fallbackText = game.title,
                decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                modifier = Modifier.fillMaxWidth().aspectRatio(BOX_ART_ASPECT),
            )

            if (game.favorite) {
                FavoriteBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(6.dp),
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Standard,
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "★",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Roughly the proportions of a physical game case, which most scraped box art matches. */
private const val BOX_ART_ASPECT = 0.72f
