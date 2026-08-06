package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewHomeUi

/**
 * Grid-style RSS feed page for Home. Selection is index-driven (same model as the old game grid).
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
    val columns = state.gridColumns.coerceIn(2, 6)
    val gridState = rememberLazyGridState()

    LaunchedEffect(rss.selectedIndex, rss.items.size, columns) {
        if (rss.items.isEmpty()) return@LaunchedEffect
        val visible = gridState.layoutInfo.visibleItemsInfo
        val isVisible = visible.any { it.index == rss.selectedIndex }
        if (!isVisible) {
            val target = (rss.selectedIndex - columns).coerceAtLeast(0)
            gridState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = rss.feedTitle?.takeIf { it.isNotBlank() } ?: "Gaming news",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
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
                RssPhase.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                RssPhase.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = rss.error ?: "Could not load the feed.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }

                RssPhase.Empty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No stories in this feed right now.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                RssPhase.Content -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = rss.items,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        RssCard(
                            item = item,
                            isSelected = index == rss.selectedIndex,
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

@XoraPreview
@Composable
private fun RssPanePreview() {
    XoraPreviewTheme {
        RssPane(
            state = previewHomeUi(homePage = HomePage.RssFeed),
            onSelectItem = {},
            onOpenItem = {},
            onRetry = {},
        )
    }
}

private enum class RssPhase { Loading, Error, Empty, Content }

@Composable
private fun RssCard(
    item: RssFeedItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusTween = arcadiaTween<Float>(ArcadiaMotion.Medium)
    val focusDpTween = arcadiaTween<Dp>(ArcadiaMotion.Medium)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = focusTween,
        label = "rssCardScale",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = focusDpTween,
        label = "rssCardBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "rssCardTitle",
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
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.imageUrl != null) {
                val platformContext = LocalPlatformContext.current
                AsyncImage(
                    model = ImageRequest.Builder(platformContext)
                        .data(item.imageUrl)
                        .crossfade(180)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.source.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = buildString {
                append(item.source)
                item.publishedAt?.let {
                    append(" · ")
                    append(it)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
