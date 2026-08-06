package com.arcadia.shell.designsystem

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Theme tokens for clear liquid-glass chrome (blur + neutral alpha, no brand wash).
 * Provided by [ArcadiaTheme].
 *
 * - [tint] / [tintStrong] / [tintSubtle]: near-clear white/black-alpha plate fills
 * - [border]: specular rim (white/clear)
 * - [highlight]: soft top-edge sheen (white)
 * - [content] / [contentMuted]: readable ink on glass
 */
@Immutable
data class ArcadiaGlassTokens(
    val tint: Color,
    val tintStrong: Color,
    val tintSubtle: Color,
    val border: Color,
    val highlight: Color,
    val content: Color,
    val contentMuted: Color,
) {
    /** CSS-equivalent aliases for call sites / design docs. */
    val glassTint: Color get() = tint
    val glassBorder: Color get() = border
    val glassHighlight: Color get() = highlight
}

val LocalArcadiaGlass = staticCompositionLocalOf {
    darkGlassTokens()
}

/**
 * Shared [HazeState] for backdrop blur. Null means glass falls back to a denser neutral scrim
 * (no chroma) without sampling content behind the plate.
 */
val LocalArcadiaHaze = compositionLocalOf<HazeState?> { null }

object ArcadiaGlass {
    val tokens: ArcadiaGlassTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalArcadiaGlass.current

    /** Nominal frost radius passed to Haze when a backdrop source is available. */
    val DefaultBlur: Dp = 18.dp

    val PillShape: Shape = RoundedCornerShape(percent = 50)
    val PanelShape: Shape = RoundedCornerShape(18.dp)
    val CardShape: Shape = RoundedCornerShape(16.dp)
    val DockShape: Shape = RoundedCornerShape(14.dp)
    val SheetShape: Shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val ChipShape: Shape = RoundedCornerShape(10.dp)
}

/** Dark shell: clear glass with a light white-alpha frost (no ink/accent wash). */
internal fun darkGlassTokens(): ArcadiaGlassTokens = ArcadiaGlassTokens(
    tint = Color.White.copy(alpha = 0.08f),
    tintStrong = Color.White.copy(alpha = 0.14f),
    tintSubtle = Color.White.copy(alpha = 0.05f),
    border = Color.White.copy(alpha = 0.22f),
    highlight = Color.White.copy(alpha = 0.30f),
    content = Mist100,
    contentMuted = Mist300,
)

/** Light shell: clear glass with soft white-alpha frost. */
internal fun lightGlassTokens(): ArcadiaGlassTokens = ArcadiaGlassTokens(
    tint = Color.White.copy(alpha = 0.22f),
    tintStrong = Color.White.copy(alpha = 0.34f),
    tintSubtle = Color.White.copy(alpha = 0.12f),
    border = Color.White.copy(alpha = 0.55f),
    highlight = Color.White.copy(alpha = 0.62f),
    content = Slate900,
    contentMuted = Slate700,
)

/**
 * Dark-leaning clear glass for chrome over hero / artwork. Slight black scrim for legibility;
 * no blue/teal tint.
 */
internal fun overMediaGlassTokens(): ArcadiaGlassTokens = ArcadiaGlassTokens(
    tint = Color.Black.copy(alpha = 0.18f),
    tintStrong = Color.Black.copy(alpha = 0.28f),
    tintSubtle = Color.Black.copy(alpha = 0.10f),
    border = Color.White.copy(alpha = 0.28f),
    highlight = Color.White.copy(alpha = 0.36f),
    content = Color.White,
    contentMuted = Color.White.copy(alpha = 0.72f),
)

/**
 * True when the platform can run Haze's preferred [android.graphics.RenderEffect] path (API 31+).
 * Haze still blurs on API 29–30 via RenderScript; this flag only tunes plate opacity.
 */
fun supportsGlassBlurEffect(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

enum class GlassTone {
    /** Theme-adaptive glass for settings, sheets, docks. */
    Surface,

    /** Dark glass for chrome over hero artwork. */
    OverMedia,
}

enum class GlassIntensity {
    Subtle,
    Standard,
    Strong,
}

@Composable
fun rememberGlassTokens(tone: GlassTone = GlassTone.Surface): ArcadiaGlassTokens {
    val theme = LocalArcadiaGlass.current
    return remember(tone, theme) {
        when (tone) {
            GlassTone.Surface -> theme
            GlassTone.OverMedia -> overMediaGlassTokens()
        }
    }
}

/**
 * Provides a [HazeState] for descendants. Prefer wrapping screen roots (or rely on
 * [ArcadiaTheme], which already provides one). Pair with [Modifier.arcadiaHazeSource] on
 * artwork / sky layers so [Modifier.liquidGlass] can sample a true backdrop blur.
 */
@Composable
fun ProvideArcadiaHaze(
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalArcadiaHaze provides hazeState, content = content)
}

/**
 * Marks this node as a backdrop blur source when a [LocalArcadiaHaze] state is present.
 */
fun Modifier.arcadiaHazeSource(zIndex: Float = 0f): Modifier = composed {
    val hazeState = LocalArcadiaHaze.current
    if (hazeState != null) {
        hazeSource(state = hazeState, zIndex = zIndex)
    } else {
        this
    }
}

/**
 * Clear liquid-glass plate: backdrop blur (via Haze when sourced), near-clear neutral fill,
 * white specular rim, soft top highlight. No brand-color wash.
 *
 * Without a [LocalArcadiaHaze] source, uses a denser neutral translucent scrim + border so
 * controller / TV text stays legible.
 */
fun Modifier.liquidGlass(
    shape: Shape = ArcadiaGlass.PanelShape,
    tone: GlassTone = GlassTone.Surface,
    intensity: GlassIntensity = GlassIntensity.Standard,
    blurRadius: Dp = ArcadiaGlass.DefaultBlur,
    shimmer: Boolean = false,
): Modifier = composed {
    val tokens = rememberGlassTokens(tone)
    val hazeState = LocalArcadiaHaze.current
    val baseTint = when (intensity) {
        GlassIntensity.Subtle -> tokens.tintSubtle
        GlassIntensity.Standard -> tokens.tint
        GlassIntensity.Strong -> tokens.tintStrong
    }

    val hasBackdropBlur = hazeState != null
    val plateTint = when {
        hasBackdropBlur && supportsGlassBlurEffect() ->
            baseTint.copy(alpha = (baseTint.alpha * 0.85f).coerceIn(0.04f, 0.28f))
        hasBackdropBlur ->
            // Haze RenderScript path on API 29–30 — slightly denser neutral plate.
            baseTint.copy(alpha = (baseTint.alpha + 0.06f).coerceIn(0.08f, 0.36f))
        else ->
            // No haze source: denser neutral scrim (still no chroma).
            baseTint.copy(alpha = (baseTint.alpha + 0.22f).coerceIn(0.22f, 0.52f))
    }
    val opacityBoost = (blurRadius / ArcadiaGlass.DefaultBlur).coerceIn(0.75f, 1.25f)
    val tunedTint = plateTint.copy(
        alpha = (plateTint.alpha * (2f - opacityBoost)).coerceIn(0.04f, 0.55f),
    )

    val hazeStyle = remember(tone, blurRadius, intensity) {
        clearHazeStyle(
            tone = tone,
            blurRadius = blurRadius,
            intensity = intensity,
        )
    }

    val reduceMotion = rememberReduceMotion()
    val highlightAlpha = if (shimmer && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "glassSheen")
        val pulse by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3_600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glassSheenPulse",
        )
        pulse
    } else {
        1f
    }

    var chain = this.clip(shape)
    if (hazeState != null) {
        chain = chain.hazeEffect(state = hazeState, style = hazeStyle)
    }
    chain
        .background(tunedTint, shape)
        .drawWithContent {
            val sheen = tokens.highlight.copy(alpha = tokens.highlight.alpha * highlightAlpha * 0.55f)
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to sheen,
                        0.22f to sheen.copy(alpha = sheen.alpha * 0.28f),
                        0.50f to Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(0f, size.height * 0.55f),
                    tileMode = TileMode.Clamp,
                ),
            )
            drawContent()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        tokens.highlight.copy(alpha = tokens.highlight.alpha * highlightAlpha * 0.75f),
                        Color.Transparent,
                    ),
                ),
                start = Offset(size.width * 0.08f, 1.5f),
                end = Offset(size.width * 0.92f, 1.5f),
                strokeWidth = 1.2f,
            )
        }
        .border(width = 1.dp, color = tokens.border, shape = shape)
}

private fun clearHazeStyle(
    tone: GlassTone,
    blurRadius: Dp,
    intensity: GlassIntensity,
): HazeStyle {
    // Near-clear white/black alpha only — never brand chroma.
    val frost = when (tone) {
        GlassTone.Surface -> when (intensity) {
            GlassIntensity.Subtle -> Color.White.copy(alpha = 0.04f)
            GlassIntensity.Standard -> Color.White.copy(alpha = 0.07f)
            GlassIntensity.Strong -> Color.White.copy(alpha = 0.11f)
        }
        GlassTone.OverMedia -> when (intensity) {
            GlassIntensity.Subtle -> Color.Black.copy(alpha = 0.08f)
            GlassIntensity.Standard -> Color.Black.copy(alpha = 0.14f)
            GlassIntensity.Strong -> Color.Black.copy(alpha = 0.22f)
        }
    }
    val fallback = when (tone) {
        GlassTone.Surface -> Color.Black.copy(alpha = 0.28f)
        GlassTone.OverMedia -> Color.Black.copy(alpha = 0.40f)
    }
    return HazeStyle(
        backgroundColor = Color.Unspecified,
        tints = listOf(HazeTint(frost)),
        blurRadius = blurRadius,
        noiseFactor = 0f,
        fallbackTint = HazeTint(fallback),
    )
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ArcadiaGlass.PanelShape,
    tone: GlassTone = GlassTone.Surface,
    intensity: GlassIntensity = GlassIntensity.Standard,
    blurRadius: Dp = ArcadiaGlass.DefaultBlur,
    shimmer: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            tone = tone,
            intensity = intensity,
            blurRadius = blurRadius,
            shimmer = shimmer,
        ),
        content = content,
    )
}

@Preview(device = "spec:width=1920dp,height=1080dp,dpi=240", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun LiquidGlassSurfacePreview() {
    ArcadiaTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A3A6B), Color(0xFF0E1A32)),
                    ),
                )
                .padding(48.dp),
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                tone = GlassTone.Surface,
                intensity = GlassIntensity.Standard,
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
