package com.arcadia.shell.feature.home.component.xmb

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LiquidGlassSurface
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.GameInsightUiState
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewInsight

/**
 * Lower XMB detail area: combined About + Highlights card, plus a screenshots column.
 * Always visible under the game strip.
 */
@Composable
fun XmbInsightPanel(
    insight: GameInsightUiState,
    gameTitle: String?,
    modifier: Modifier = Modifier,
) {
    val enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
    val exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
    AnimatedContent(
        targetState = Triple(insight.gameId, insight.isLoading, insight.summary),
        transitionSpec = { enter togetherWith exit },
        label = "xmbInsight",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AboutCard(
                insight = insight,
                gameTitle = gameTitle,
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight(),
            )
            ScreenshotsCard(
                insight = insight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AboutCard(
    insight: GameInsightUiState,
    gameTitle: String?,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    LiquidGlassSurface(
        modifier = modifier,
        shape = ArcadiaGlass.PanelShape,
        tone = GlassTone.Surface,
        intensity = GlassIntensity.Standard,
        shimmer = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = gameTitle ?: "Select a game",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val source = insight.summarySourceLabel
            Text(
                text = when {
                    insight.isLoading -> "Looking up notes…"
                    source != null -> "About · $source"
                    else -> "About"
                },
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )

            MetaChipRow(insight = insight)

            when {
                insight.isLoading && !insight.hasMainCopy -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                insight.hasMainCopy -> {
                    Text(
                        text = insight.summary.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.content.copy(alpha = 0.92f),
                    )
                }
                else -> {
                    Text(
                        text = "No summary yet — release, genre, and library notes appear below when available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.contentMuted,
                    )
                }
            }

            if (!insight.speedrunBlurb.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                HighlightBlock(
                    label = "Speedrun",
                    body = insight.speedrunBlurb,
                    contentColor = glass.content,
                    mutedColor = glass.contentMuted,
                )
            }

            val triviaLines = insight.trivia
                .filter { it != insight.summary }
                .take(3)
            if (triviaLines.isNotEmpty()) {
                HighlightBlock(
                    label = "Notes",
                    body = triviaLines.joinToString("\n"),
                    contentColor = glass.content,
                    mutedColor = glass.contentMuted,
                )
            }

            if (!insight.isLoading && !insight.hasHighlights && !insight.hasMainCopy) {
                Text(
                    text = "More details will show release year, genre, and speedrun notes when available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.contentMuted,
                )
            }
        }
    }
}

@Composable
private fun ScreenshotsCard(
    insight: GameInsightUiState,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    LiquidGlassSurface(
        modifier = modifier,
        shape = ArcadiaGlass.PanelShape,
        tone = GlassTone.Surface,
        intensity = GlassIntensity.Subtle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Screenshots",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
            )

            when {
                insight.screenshotsLoading && !insight.hasScreenshots -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                insight.hasScreenshots -> {
                    ScreenshotGallery(
                        paths = insight.screenshotPaths.take(MAX_VISIBLE_SCREENSHOTS),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No screenshots yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.contentMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact gallery: featured still on top, then a horizontal strip for the rest so 4–6
 * screenshots stay readable without clipping the About column.
 */
@Composable
private fun ScreenshotGallery(
    paths: List<String>,
    modifier: Modifier = Modifier,
) {
    val featured = paths.firstOrNull() ?: return
    val rest = paths.drop(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f),
            shape = ArcadiaGlass.CardShape,
            tone = GlassTone.Surface,
            intensity = GlassIntensity.Standard,
        ) {
            ArtworkImage(
                path = featured,
                contentDescription = null,
                fallbackText = "",
                contentScale = ContentScale.Crop,
                cacheInMemory = false,
                decodeMaxEdgePx = 960,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (rest.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rest.forEach { path ->
                    LiquidGlassSurface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(132.dp),
                        shape = ArcadiaGlass.CardShape,
                        tone = GlassTone.Surface,
                        intensity = GlassIntensity.Subtle,
                    ) {
                        ArtworkImage(
                            path = path,
                            contentDescription = null,
                            fallbackText = "",
                            contentScale = ContentScale.Crop,
                            cacheInMemory = false,
                            decodeMaxEdgePx = 480,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChipRow(insight: GameInsightUiState) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val chips = buildList {
        insight.releaseYear?.let { add("Released $it") }
        insight.genre?.let { add(it) }
        insight.developer?.let { add(it) }
        insight.platformLabel?.let { add(it) }
    }
    if (chips.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { chip ->
            Text(
                text = chip,
                style = MaterialTheme.typography.labelLarge,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HighlightBlock(
    label: String,
    body: String,
    contentColor: Color,
    mutedColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = mutedColor,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.92f),
        )
    }
}

private const val MAX_VISIBLE_SCREENSHOTS = 6

@XoraPreview
@Composable
private fun XmbInsightPanelPreview() {
    XoraPreviewTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            XmbInsightPanel(
                insight = previewInsight(),
                gameTitle = "The Legend of Zelda",
            )
        }
    }
}
