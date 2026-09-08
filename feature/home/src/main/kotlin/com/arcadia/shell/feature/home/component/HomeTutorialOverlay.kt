package com.arcadia.shell.feature.home.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraModalGlass
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.feature.home.HomeTutorialStep
import com.arcadia.shell.feature.home.HomeTutorialUiState
import com.arcadia.shell.feature.home.body
import com.arcadia.shell.feature.home.homeTutorialHole
import com.arcadia.shell.feature.home.isLast
import com.arcadia.shell.feature.home.title

/**
 * Dims Home and cuts a spotlight around the chrome the current step is teaching.
 * Tap the plate or press A to continue; B / Skip dismisses the rest.
 */
@Composable
fun HomeTutorialOverlay(
    state: HomeTutorialUiState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.open, onBack = onSkip)

    AnimatedVisibility(
        visible = state.open,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Medium)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNext,
                ),
        ) {
            val hole = remember(state.step, maxWidth, maxHeight) {
                homeTutorialHole(state.step, maxWidth, maxHeight)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawRect(Color.Black.copy(alpha = 0.52f))
                val left = hole.left.toPx()
                val top = hole.top.toPx()
                val width = hole.width.toPx().coerceAtLeast(1f)
                val height = hole.height.toPx().coerceAtLeast(1f)
                val origin = Offset(left, top)
                val size = Size(width, height)
                if (hole.oval) {
                    drawOval(
                        color = Color.Transparent,
                        topLeft = origin,
                        size = size,
                        blendMode = BlendMode.Clear,
                    )
                    drawOval(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = origin,
                        size = size,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                } else {
                    val corner = CornerRadius(hole.corner.toPx())
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = origin,
                        size = size,
                        cornerRadius = corner,
                        blendMode = BlendMode.Clear,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = origin,
                        size = size,
                        cornerRadius = corner,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }
            TutorialCoachCard(
                step = state.step,
                onNext = onNext,
                onSkip = onSkip,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 40.dp, end = 40.dp, bottom = 36.dp)
                    .widthIn(max = 640.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
        }
    }
}

@Composable
private fun TutorialCoachCard(
    step: HomeTutorialStep,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .xoraModalGlass(XoraModalGlass.Shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = step.title(),
            color = Color.White,
            fontFamily = XoraFonts.Title,
            fontSize = 28.sp,
            letterSpacing = XoraFonts.TitleLetterSpacing,
        )
        Text(
            text = step.body(),
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = XoraFonts.XmbLabel,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TutorialStepDots(step = step)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSkip) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.78f),
                        fontFamily = XoraFonts.XmbLabel,
                        fontSize = 15.sp,
                    )
                }
                TextButton(onClick = onNext) {
                    Text(
                        text = if (step.isLast()) "Got it" else "Next",
                        color = Color.White,
                        fontFamily = XoraFonts.Title,
                        fontSize = 16.sp,
                        letterSpacing = XoraFonts.TitleLetterSpacing,
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialStepDots(step: HomeTutorialStep) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTutorialStep.entries.forEach { entry ->
            val active = entry == step
            Box(
                modifier = Modifier
                    .size(if (active) 9.dp else 7.dp)
                    .background(
                        Color.White.copy(alpha = if (active) 0.95f else 0.32f),
                        CircleShape,
                    ),
            )
        }
    }
}
