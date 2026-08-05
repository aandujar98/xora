package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LiquidGlassSurface
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.currentShellTheme
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.ManualViewer

/**
 * Bottom-screen companion while a single-screen game runs in dual-screen mode: the game's scraped
 * fanart under a scrim, with About and Manual as the only two actions.
 *
 * Everything here is sized for fingers. The controller belongs to the game at this point, so touch
 * is the only input that reliably reaches this screen.
 */
@Composable
fun GameCompanionPane(
    companion: GameCompanionUiState,
    onSelectAction: (GameCompanionAction) -> Unit,
    onActivateAction: () -> Unit,
    onDismissOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CompanionBackdrop(
            path = companion.backdropPath,
            title = companion.title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.45f to Color.Black.copy(alpha = 0.2f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = companion.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = companion.platformLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
                companion.raProgressLabel?.let { progress ->
                    Text(
                        text = "RetroAchievements · $progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CompanionActionButton(
                    label = "About this game",
                    subtitle = if (companion.about.isLoading) "Loading details…" else "Story, credits, notes",
                    focused = companion.focusedAction == GameCompanionAction.About,
                    enabled = true,
                    onClick = {
                        onSelectAction(GameCompanionAction.About)
                        onActivateAction()
                    },
                    modifier = Modifier.weight(1f),
                )
                CompanionActionButton(
                    label = "Game manual",
                    subtitle = when {
                        companion.manualLoading -> "Looking up…"
                        companion.manualMissing -> "No manual found"
                        companion.hasManual -> "Read the manual"
                        else -> "Game manual"
                    },
                    focused = companion.focusedAction == GameCompanionAction.Manual,
                    enabled = !companion.manualMissing,
                    onClick = {
                        onSelectAction(GameCompanionAction.Manual)
                        onActivateAction()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        CompanionOverlayHost(
            companion = companion,
            onDismiss = onDismissOverlay,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Scraped art when the ROM has some, and a theme-tinted wash when it does not. */
@Composable
private fun CompanionBackdrop(
    path: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (path != null) {
        ArtworkImage(
            path = path,
            contentDescription = title,
            fallbackText = title,
            contentScale = ContentScale.Crop,
            cacheInMemory = false,
            decodeMaxEdgePx = BACKDROP_DECODE_MAX_EDGE_PX,
            modifier = modifier,
        )
        return
    }

    val theme = currentShellTheme()
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    theme.colors.background,
                    theme.colors.surface,
                    theme.colors.accent.copy(alpha = 0.35f),
                ),
            ),
        ),
    )
}

@Composable
private fun CompanionActionButton(
    label: String,
    subtitle: String,
    focused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val shape = RoundedCornerShape(18.dp)
    LiquidGlassSurface(
        modifier = modifier
            .height(112.dp)
            .then(
                if (focused) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        tone = GlassTone.OverMedia,
        intensity = if (focused) GlassIntensity.Strong else GlassIntensity.Standard,
        shimmer = focused,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) glass.content else glass.contentMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompanionOverlayHost(
    companion: GameCompanionUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = companion.overlay != GameCompanionOverlay.None
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
            scaleIn(animationSpec = arcadiaTween(ArcadiaMotion.Medium), initialScale = 0.97f),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
            scaleOut(animationSpec = arcadiaTween(ArcadiaMotion.Fast), targetScale = 0.98f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            when (companion.overlay) {
                GameCompanionOverlay.About -> CompanionAboutSheet(
                    companion = companion,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.88f)
                        // Swallows taps so the scrim's dismiss does not fire through the sheet.
                        .clickable(enabled = false, onClick = {}),
                )
                GameCompanionOverlay.Manual -> CompanionManualSheet(
                    companion = companion,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.92f)
                        .clickable(enabled = false, onClick = {}),
                )
                GameCompanionOverlay.None -> Unit
            }
        }
    }
}

@Composable
private fun CompanionAboutSheet(
    companion: GameCompanionUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val insight = companion.about
    LiquidGlassSurface(
        modifier = modifier,
        shape = ArcadiaGlass.PanelShape,
        tone = GlassTone.Surface,
        intensity = GlassIntensity.Strong,
        shimmer = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            CompanionSheetHeader(
                title = "About this game",
                onDismiss = onDismiss,
                contentColor = glass.content,
            )
            Text(
                text = companion.title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            Text(
                text = companion.factLine,
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    insight.isLoading && !insight.hasMainCopy -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                    insight.hasMainCopy -> {
                        Text(
                            text = insight.summary.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = glass.content.copy(alpha = 0.94f),
                        )
                        insight.summarySourceLabel?.let { source ->
                            Text(
                                text = "Source: $source",
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.contentMuted,
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "No description is available for this title yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = glass.contentMuted,
                        )
                    }
                }

                companion.raTitle?.let { raTitle ->
                    CompanionSectionTitle("RetroAchievements", glass.content)
                    Text(
                        text = buildString {
                            append(raTitle)
                            companion.raProgressLabel?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.content.copy(alpha = 0.9f),
                    )
                }

                if (!insight.speedrunBlurb.isNullOrBlank()) {
                    CompanionSectionTitle("Speedrun", glass.content)
                    Text(
                        text = insight.speedrunBlurb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.content.copy(alpha = 0.9f),
                    )
                }

                val trivia = insight.trivia.filter { it != insight.summary }.take(MAX_TRIVIA)
                if (trivia.isNotEmpty()) {
                    CompanionSectionTitle("Notes", glass.content)
                    Text(
                        text = trivia.joinToString("\n\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.content.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionManualSheet(
    companion: GameCompanionUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    LiquidGlassSurface(
        modifier = modifier,
        shape = ArcadiaGlass.PanelShape,
        tone = GlassTone.Surface,
        intensity = GlassIntensity.Strong,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            CompanionSheetHeader(
                title = "Game manual",
                onDismiss = onDismiss,
                contentColor = glass.content,
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                companion.manualLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Looking for a manual…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.contentMuted,
                            )
                        }
                    }
                }
                companion.hasManual -> {
                    ManualViewer(
                        path = companion.manualPath.orEmpty(),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No manual found",
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.content,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Drop a PDF next to the ROM, or add ScreenScraper " +
                                    "credentials in Settings to download one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.contentMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
        Box(
            modifier = Modifier
                .heightIn(min = TOUCH_TARGET_DP.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Close",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CompanionSectionTitle(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

private const val BACKDROP_DECODE_MAX_EDGE_PX = 1600
private const val MAX_TRIVIA = 4
private const val TOUCH_TARGET_DP = 48
