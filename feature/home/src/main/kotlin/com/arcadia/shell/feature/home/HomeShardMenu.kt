package com.arcadia.shell.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewGame
import com.arcadia.shell.model.Game

private val ShardGap = 6.dp
private val ShopMuted = Color(0xAA0A0E14)

/**
 * Smash Bros–inspired irregular shard menu with a SORA glass/sky palette.
 *
 * Layout: large Continue on the left, RetroAchievements + Shop stacked on the right.
 * Launcher themes live under Start settings (not a home shard).
 */
@Composable
fun HomeShardMenu(
    continueGame: Game?,
    focused: HomeShard,
    raAvatarUrl: String?,
    onSelect: (HomeShard) -> Unit,
    onActivate: (HomeShard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalShellTheme.current.colors
    val accentFocused = theme.shardAccentFocused
    val accentIdle = theme.shardAccentIdle
    val shardFill = theme.shardFill

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val leftW = maxWidth * 0.52f
        val rightW = maxWidth - leftW - ShardGap
        val topH = (maxHeight - ShardGap) * 0.48f
        val bottomH = maxHeight - topH - ShardGap

        // Left Continue shard
        ShardButton(
            focused = focused == HomeShard.Continue,
            accentFocused = accentFocused,
            accentIdle = accentIdle,
            shardFill = shardFill,
            onFocus = { onSelect(HomeShard.Continue) },
            onActivate = { onActivate(HomeShard.Continue) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(leftW)
                .fillMaxHeight()
                .padding(end = ShardGap / 2),
            skew = ShardSkew.LeftHero,
        ) {
            ContinueShardContent(continueGame, accentFocused)
        }

        // Top-right RA
        ShardButton(
            focused = focused == HomeShard.RetroAchievements,
            accentFocused = accentFocused,
            accentIdle = accentIdle,
            shardFill = shardFill,
            onFocus = { onSelect(HomeShard.RetroAchievements) },
            onActivate = { onActivate(HomeShard.RetroAchievements) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(rightW)
                .height(topH)
                .padding(start = ShardGap / 2),
            skew = ShardSkew.RightTop,
        ) {
            RaShardContent(raAvatarUrl)
        }

        // Bottom-right Shop
        ShardButton(
            focused = focused == HomeShard.Shop,
            accentFocused = accentFocused,
            accentIdle = accentIdle,
            shardFill = shardFill,
            onFocus = { onSelect(HomeShard.Shop) },
            onActivate = { onActivate(HomeShard.Shop) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(rightW)
                .height(bottomH)
                .padding(start = ShardGap / 2),
            skew = ShardSkew.RightBottom,
            enabled = false,
        ) {
            ShopShardContent()
        }
    }
}

private enum class ShardSkew { LeftHero, RightTop, RightBottom }

@Composable
private fun ShardButton(
    focused: Boolean,
    accentFocused: Color,
    accentIdle: Color,
    shardFill: Color,
    onFocus: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    skew: ShardSkew,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val rim = if (focused) accentFocused else accentIdle
    val shape = skewToCorner(skew)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) shardFill else ShopMuted)
            .border(
                width = if (focused) 3.dp else 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(rim, rim.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f)),
                ),
                shape = shape,
            )
            .clickable(enabled = true) {
                onFocus()
                onActivate()
            },
    ) {
        // Subtle inner glass wash
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.08f)
                lineTo(size.width * 0.92f, 0f)
                lineTo(size.width, size.height * 0.9f)
                lineTo(size.width * 0.05f, size.height)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                ),
            )
            if (focused) {
                drawRect(
                    color = accentFocused.copy(alpha = 0.35f),
                )
            }
        }
        content()
    }
}

private fun skewToCorner(skew: ShardSkew): RoundedCornerShape = when (skew) {
    ShardSkew.LeftHero -> RoundedCornerShape(topStart = 18.dp, topEnd = 28.dp, bottomEnd = 22.dp, bottomStart = 14.dp)
    ShardSkew.RightTop -> RoundedCornerShape(topStart = 22.dp, topEnd = 16.dp, bottomEnd = 8.dp, bottomStart = 20.dp)
    ShardSkew.RightBottom -> RoundedCornerShape(topStart = 20.dp, topEnd = 10.dp, bottomEnd = 16.dp, bottomStart = 24.dp)
}

@Composable
private fun ContinueShardContent(game: Game?, accentFocused: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (game != null) {
            ArtworkImage(
                path = game.gridArt,
                contentDescription = game.title,
                fallbackText = game.title,
                contentScale = ContentScale.Crop,
                decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
        ) {
            Text(
                text = "CONTINUE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
                color = accentFocused,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = game?.title ?: "Browse library",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RaShardContent(raAvatarUrl: String?) {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = "RETRO",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        if (!raAvatarUrl.isNullOrBlank()) {
            ArtworkImage(
                path = raAvatarUrl,
                contentDescription = null,
                fallbackText = "RA",
                contentScale = ContentScale.Crop,
                decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun ShopShardContent() {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = "SHOP",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.45f),
            )
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@XoraPreview
@Composable
private fun HomeShardMenuPreview() {
    XoraPreviewTheme {
        HomeShardMenu(
            continueGame = previewGame(),
            focused = HomeShard.Continue,
            raAvatarUrl = null,
            onSelect = {},
            onActivate = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
