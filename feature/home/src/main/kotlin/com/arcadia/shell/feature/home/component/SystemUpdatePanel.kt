package com.arcadia.shell.feature.home.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.SystemUpdatePhase
import com.arcadia.shell.feature.home.SystemUpdateUiState

private val PanelShape = RoundedCornerShape(22.dp)
private val ButtonShape = RoundedCornerShape(percent = 50)
private val BarShape = RoundedCornerShape(3.dp)

/**
 * Settings → Update window: checks GitHub Releases, then downloads and hands the APK to the
 * package installer.
 *
 * Overlay rather than Dialog for the same reason as Start settings — dual-screen
 * [android.app.Presentation] panes cannot host a nested window.
 */
@Composable
fun SystemUpdatePanel(
    state: SystemUpdateUiState,
    onPrimary: () -> Unit,
    onSelectButton: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val enterMs = motionMillis(ArcadiaMotion.Slow)
    val enterSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    BackHandler(enabled = state.open, onBack = onDismiss)

    AnimatedVisibility(
        visible = state.open,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
            animationSpec = if (enterMs == 0) arcadiaTween(0) else enterSpring,
            initialScale = 0.9f,
        ),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
            animationSpec = arcadiaTween(ArcadiaMotion.Fast),
            targetScale = 0.95f,
        ),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        // A download keeps running in the background, but do not let a stray
                        // scrim tap wipe the only progress readout.
                        enabled = !state.busy,
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(0.7f)
                    .liquidGlass(
                        shape = PanelShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "System Update",
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.contentMuted,
                )
                Text(
                    text = state.headline,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.content,
                )
                Text(
                    text = state.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.contentMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )

                if (state.busy) {
                    val fraction = state.progress
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(BarShape),
                            color = LocalShellTheme.current.colors.focusEnd,
                            trackColor = glass.content.copy(alpha = 0.14f),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(BarShape),
                            color = LocalShellTheme.current.colors.focusEnd,
                            trackColor = glass.content.copy(alpha = 0.14f),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val primaryLabel = state.primaryLabel
                    if (primaryLabel != null) {
                        UpdateButton(
                            label = primaryLabel,
                            selected = state.selectedButton == 0,
                            emphasis = true,
                            content = glass.content,
                            onClick = {
                                onSelectButton(0)
                                onPrimary()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    UpdateButton(
                        label = if (state.phase == SystemUpdatePhase.Downloading) {
                            "Hide"
                        } else {
                            "Close"
                        },
                        selected = state.selectedButton == 1 || primaryLabel == null,
                        emphasis = false,
                        content = glass.content,
                        onClick = {
                            onSelectButton(1)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateButton(
    label: String,
    selected: Boolean,
    emphasis: Boolean,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalShellTheme.current.colors
    val highlight by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "systemUpdateButtonFocus",
    )
    val baseAlpha = if (emphasis) 0.22f else 0.12f
    Box(
        modifier = modifier
            .clip(ButtonShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        theme.focusStart.copy(alpha = baseAlpha + 0.34f * highlight),
                        theme.focusEnd.copy(alpha = baseAlpha + 0.3f * highlight),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}
