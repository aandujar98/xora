package com.arcadia.shell.feature.home.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.designsystem.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Short wake/resume greeting on the primary display: avatar + "Welcome back" + display name,
 * then auto-dismiss into Home. Tap or system Back / B dismisses early.
 */
@Composable
fun WelcomeBackOverlay(
    visible: Boolean,
    profile: LocalProfile,
    profileAvatarModel: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val holdMs = motionMillis(WELCOME_HOLD_MS)

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        if (holdMs == 0) {
            onDismiss()
            return@LaunchedEffect
        }
        delay(holdMs.toLong())
        onDismiss()
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Medium)),
        modifier = modifier.fillMaxSize(),
    ) {
        val theme = LocalShellTheme.current.colors
        val avatarScale = remember { Animatable(if (reduceMotion) 1f else 0.82f) }
        val avatarAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
        val titleAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
        val titleOffset = remember { Animatable(if (reduceMotion) 0f else 18f) }
        val nameAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
        val nameOffset = remember { Animatable(if (reduceMotion) 0f else 14f) }

        LaunchedEffect(Unit) {
            if (reduceMotion) return@LaunchedEffect
            val softSpring = spring<Float>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            launch { avatarAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            launch { avatarScale.animateTo(1f, softSpring) }
            delay(140)
            launch { titleAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
            launch { titleOffset.animateTo(0f, softSpring) }
            delay(90)
            launch { nameAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
            launch { nameOffset.animateTo(0f, softSpring) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                ProfileAvatar(
                    displayName = profile.displayName,
                    presetId = profile.avatarPresetId,
                    size = 112.dp,
                    imageModel = profileAvatarModel,
                    borderColor = theme.accent.copy(alpha = 0.55f),
                    modifier = Modifier.graphicsLayer {
                        scaleX = avatarScale.value
                        scaleY = avatarScale.value
                        alpha = avatarAlpha.value
                    },
                )
                Text(
                    text = "Welcome back",
                    color = theme.textMuted.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffset.value
                    },
                )
                Text(
                    text = profile.displayName.ifBlank { "Player" },
                    color = theme.text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = nameAlpha.value
                        translationY = nameOffset.value
                    },
                )
            }
        }
    }
}

/** Visible hold before dissolve; total moment stays ~1.5–3s with enter/exit. */
private const val WELCOME_HOLD_MS = 2_050
