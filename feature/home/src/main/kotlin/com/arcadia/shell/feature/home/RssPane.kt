package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass

private val RowShape = RoundedCornerShape(14.dp)
private val ThumbShape = RoundedCornerShape(10.dp)

/**
 * XOrA News: a single readable column of stories over the shell wallpaper. The focused row is
 * mirrored full-bleed by the hero pane, so this side stays a quiet list rather than an art grid.
 */
@Composable
fun RssPane(
    state: HomeUiState,
    onSelectItem: (Int) -> Unit,
    onOpenItem: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rss = state.rss
    val listState = rememberLazyListState()

    LaunchedEffect(rss.selectedIndex, rss.items.size) {
        if (rss.items.isEmpty()) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == rss.selectedIndex }) {
            listState.animateScrollToItem((rss.selectedIndex - 1).coerceAtLeast(0))
        }
    }

    val theme = LocalShellTheme.current.colors
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    theme.background.copy(alpha = 0.97f),
                    theme.surface.copy(alpha = 0.94f),
                ),
            ),
        ),
    ) {
        RssHeader(
            feedTitle = rss.feedTitle,
            storyCount = rss.items.size,
        )

        val phaseEnter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
        val phaseExit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
        AnimatedContent(
            targetState = when {
                rss.isLoading && rss.items.isEmpty() -> RssPhase.Loading
                rss.error != null && rss.items.isEmpty() -> RssPhase.Error
                rss.isEmpty -> RssPhase.Empty
                else -> RssPhase.Content
            },
            transitionSpec = { phaseEnter togetherWith phaseExit },
            label = "rssPhase",
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                RssPhase.Loading -> RssMessage {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.85f))
                }

                RssPhase.Error -> RssMessage {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = rss.error ?: "Could not load the feed.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = XoraFonts.Secondary,
                            ),
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRetry) {
                            Text(text = "Retry", color = Color.White)
                        }
                    }
                }

                RssPhase.Empty -> RssMessage {
                    Text(
                        text = "No stories in this feed right now.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = XoraFonts.Secondary,
                        ),
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }

                RssPhase.Content -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 2.dp,
                        bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = rss.items,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        RssStoryRow(
                            item = item,
                            position = index + 1,
                            selected = index == rss.selectedIndex,
                            onClick = {
                                if (index == rss.selectedIndex) {
                                    onOpenItem(index)
                                } else {
                                    onSelectItem(index)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private enum class RssPhase { Loading, Error, Empty, Content }

@Composable
private fun RssHeader(
    feedTitle: String?,
    storyCount: Int,
) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "XOrA News",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = XoraFonts.Title,
                letterSpacing = XoraFonts.TitleLetterSpacing,
            ),
            color = Color.White,
        )
        Text(
            text = buildString {
                append(feedTitle?.takeIf { it.isNotBlank() } ?: "Gaming & emulation feed")
                if (storyCount > 0) {
                    append(" · ")
                    append(storyCount)
                    append(if (storyCount == 1) " story" else " stories")
                }
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = XoraFonts.Secondary,
            ),
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RssMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun RssStoryRow(
    item: RssFeedItem,
    position: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalShellTheme.current.colors
    val highlight by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "rssRowFocus",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .then(
                if (selected) {
                    Modifier.liquidGlass(
                        shape = RowShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Standard,
                    )
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.05f))
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        theme.focusStart.copy(alpha = 0.75f * highlight),
                        theme.focusEnd.copy(alpha = 0.5f * highlight),
                    ),
                ),
                shape = RowShape,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(58.dp)
                .clip(ThumbShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageUrl != null) {
                val platformContext = LocalPlatformContext.current
                AsyncImage(
                    model = ImageRequest.Builder(platformContext)
                        .data(item.imageUrl)
                        .crossfade(160)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.source.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = XoraFonts.Title,
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = XoraFonts.XmbLabel,
                ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = Color.White.copy(alpha = if (selected) 1f else 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(item.source)
                    item.publishedAt?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = XoraFonts.Secondary,
                ),
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = position.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = XoraFonts.Secondary,
            ),
            color = Color.White.copy(alpha = 0.22f + 0.4f * highlight),
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End,
        )
    }
}
