package com.arcadia.shell.designsystem

import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val FlowSkyTop = Color(0xFF2ACBFD)
private val FlowSkyBottom = Color.White

/** Artboard the wave layers were exported against. */
private const val ART_WIDTH = 1920f
private const val ART_HEIGHT = 1080f

/**
 * Bleed baked into the drawables on every side, in artboard units. The bands continue past the
 * artboard, so neither the drift nor the swell can pull a cut edge into frame.
 */
private const val WAVE_BLEED = 340f

/**
 * Every period divides this evenly, so wrapping the clock here leaves each phase exactly where it
 * was. That keeps the loop seamless and stops float precision from coarsening over a long session.
 */
private const val LOOP_SECONDS = 420.0

/**
 * Both bands run along the same shallow diagonal — roughly the axis the artwork's own crests lie
 * on, so a band travels along its length and the crest reads as flowing rather than bobbing.
 */
private const val WAVE_ANGLE_DEG = 16f

/**
 * Grid the bands are warped on — around eight samples across the shortest ripple, so the swell
 * reads as a curve rather than facets.
 */
private const val MESH_COLUMNS = 52
private const val MESH_ROWS = 30

/**
 * Troughs are flattened to this fraction of the crest. Real waves are shaped that way — peaked
 * crests over broad shallow troughs — and it also earns headroom: the artwork's own upper boundary
 * sits only ~150 units above the frame in the left half, so a symmetric swell of this size would
 * drag that boundary into view as a hard edge.
 */
private const val TROUGH_FLATTEN = 0.45f

private const val TWO_PI = (Math.PI * 2).toFloat()
private val WaveAngleCos = cos(WAVE_ANGLE_DEG * Math.PI.toFloat() / 180f)
private val WaveAngleSin = sin(WAVE_ANGLE_DEG * Math.PI.toFloat() / 180f)

/**
 * How a band moves: it slides along the diagonal, and its surface swells as two travelling waves
 * of different lengths run down that same axis, plus a slow cross-swell so the crest is never a
 * uniform ridge. Summing waves of unlike length is what stops the surface looking like one
 * repeating ripple.
 */
private class WaveBand(
    val driftPeriod: Float,
    val driftAlong: Float,
    val driftAcross: Float,
    val driftPhase: Float,
    /**
     * Sliding along the diagonal barely changes how a band looks, because a band is nearly
     * unchanged along its own length — so drift alone leaves the pair looking glued together. This
     * is a slow lift across the diagonal on its own period, which opens and closes the gap between
     * the two and is what actually reads as independence.
     */
    val swayPeriod: Float,
    val swayAmplitude: Float,
    val swayPhase: Float,
    val swellPeriod: Float,
    val swellAmplitude: Float,
    val swellLength: Float,
    val ripplePeriod: Float,
    val rippleAmplitude: Float,
    val rippleLength: Float,
    val crossPeriod: Float,
    val crossAmplitude: Float,
    val crossLength: Float,
    val swellPhase: Float,
)

private val BackBand = WaveBand(
    driftPeriod = 28f,
    driftAlong = 76f,
    driftAcross = 20f,
    driftPhase = 0f,
    swayPeriod = 35f,
    swayAmplitude = 42f,
    swayPhase = 0f,
    swellPeriod = 12f,
    swellAmplitude = 52f,
    swellLength = 1150f,
    ripplePeriod = 6f,
    rippleAmplitude = 30f,
    rippleLength = 430f,
    crossPeriod = 20f,
    crossAmplitude = 16f,
    crossLength = 900f,
    swellPhase = 0f,
)

private val FrontBand = WaveBand(
    driftPeriod = 21f,
    driftAlong = 100f,
    driftAcross = 26f,
    // Set off counter to the back band, so the two cross instead of sliding in convoy.
    driftPhase = TWO_PI / 2f,
    swayPeriod = 20f,
    swayAmplitude = 54f,
    // Counter to the back band's sway, so the gap between them breathes instead of holding.
    swayPhase = TWO_PI / 2f,
    swellPeriod = 7f,
    swellAmplitude = 68f,
    swellLength = 950f,
    ripplePeriod = 5f,
    rippleAmplitude = 36f,
    rippleLength = 390f,
    crossPeriod = 15f,
    crossAmplitude = 20f,
    crossLength = 780f,
    swellPhase = TWO_PI / 3f,
)

/**
 * Default shell wallpaper: the authored HOME bands over a cyan → white sky, flowing like water.
 *
 * The bands ship as pre-rendered drawables rather than paths. Each carries a white inner shadow in
 * the source art — the glow along its edges — and Compose has no inner-shadow primitive, so the
 * layers are baked from the authored vectors and composited here with Lighten exactly as authored.
 * Lighten also means a band can only brighten the sky, so the pale bottom of the gradient never
 * muddies and crossing edges pick up a sheen.
 *
 * Baked art would normally be stuck moving as a rigid slab, so each band is drawn through a warped
 * vertex mesh instead: the artwork keeps its exact pixels while its surface deforms. Per frame this
 * is a few hundred vertices and one textured draw, which leaves headroom at 120Hz; the vertex
 * buffers are allocated once and rewritten in place rather than per frame.
 */
@Composable
fun XoraFlowBackdrop(modifier: Modifier = Modifier) {
    val back = rememberWaveBitmap(R.drawable.xora_wave_back)
    val front = rememberWaveBitmap(R.drawable.xora_wave_front)
    val animate = rememberAmbientMotionActive()

    // Written from the frame clock and read only while drawing, so a new frame invalidates the
    // draw without dragging composition or layout along with it.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        var origin = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (origin == 0L) origin = nanos
                val elapsed = (nanos - origin) / 1_000_000_000.0
                clock.floatValue = (elapsed % LOOP_SECONDS).toFloat()
            }
        }
    }

    val paint = remember {
        AndroidPaint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            blendMode = AndroidBlendMode.LIGHTEN
        }
    }
    val backMesh = remember { WaveMesh() }
    val frontMesh = remember { WaveMesh() }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = Brush.verticalGradient(listOf(FlowSkyTop, FlowSkyBottom)))
                val seconds = clock.floatValue
                drawWaveBand(back, backMesh, BackBand, seconds, paint)
                drawWaveBand(front, frontMesh, FrontBand, seconds, paint)
            },
    )
}

/** [drawBitmapMesh] needs a platform bitmap, and the conversion should happen once. */
@Composable
private fun rememberWaveBitmap(resId: Int): android.graphics.Bitmap {
    val image = ImageBitmap.imageResource(resId)
    return remember(image) { image.asAndroidBitmap() }
}

private fun DrawScope.drawWaveBand(
    bitmap: android.graphics.Bitmap,
    mesh: WaveMesh,
    band: WaveBand,
    seconds: Float,
    paint: AndroidPaint,
) {
    // Cover the pane the way the artboard crops, then bleed outwards on every side.
    val scale = max(size.width / ART_WIDTH, size.height / ART_HEIGHT)
    val offsetX = (size.width - ART_WIDTH * scale) / 2f
    val offsetY = (size.height - ART_HEIGHT * scale) / 2f

    val driftPhase = band.driftPhase + TWO_PI * (seconds / band.driftPeriod)
    val swayPhase = band.swayPhase + TWO_PI * (seconds / band.swayPeriod)
    val along = sin(driftPhase) * band.driftAlong
    val across = cos(driftPhase) * band.driftAcross + sin(swayPhase) * band.swayAmplitude
    val driftX = along * WaveAngleCos - across * WaveAngleSin
    val driftY = along * WaveAngleSin + across * WaveAngleCos

    mesh.update(
        band = band,
        seconds = seconds,
        driftX = driftX,
        driftY = driftY,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
    )

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmapMesh(
            bitmap,
            MESH_COLUMNS,
            MESH_ROWS,
            mesh.vertices,
            0,
            null,
            0,
            paint,
        )
    }
}

/**
 * Destination vertices for one band. Allocated once; [update] rewrites it in place so a frame at
 * 120Hz costs arithmetic rather than garbage.
 */
private class WaveMesh {
    val vertices = FloatArray((MESH_COLUMNS + 1) * (MESH_ROWS + 1) * 2)

    fun update(
        band: WaveBand,
        seconds: Float,
        driftX: Float,
        driftY: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val swellPhase = band.swellPhase + TWO_PI * (seconds / band.swellPeriod)
        // The shorter ripple runs at its own rate, so crests overtake each other.
        val ripplePhase = TWO_PI * (seconds / band.ripplePeriod)
        val crossPhase = TWO_PI * (seconds / band.crossPeriod)
        val swellK = TWO_PI / band.swellLength
        val rippleK = TWO_PI / band.rippleLength
        val crossK = TWO_PI / band.crossLength

        val spanX = ART_WIDTH + WAVE_BLEED * 2f
        val spanY = ART_HEIGHT + WAVE_BLEED * 2f
        var index = 0
        for (row in 0..MESH_ROWS) {
            val designY = -WAVE_BLEED + spanY * row / MESH_ROWS
            for (column in 0..MESH_COLUMNS) {
                val designX = -WAVE_BLEED + spanX * column / MESH_COLUMNS
                // Distance along the diagonal, and across it, for this vertex.
                val alongAxis = designX * WaveAngleCos + designY * WaveAngleSin
                val acrossAxis = -designX * WaveAngleSin + designY * WaveAngleCos
                val swell = sin(alongAxis * swellK - swellPhase) * band.swellAmplitude +
                    sin(alongAxis * rippleK - ripplePhase) * band.rippleAmplitude +
                    sin(acrossAxis * crossK + crossPhase) * band.crossAmplitude
                // Negative lifts the surface, so crests keep their full height while troughs stay
                // shallow. Peaked crests read as water, and the flat side is the one with no room.
                val lift = if (swell < 0f) swell else swell * TROUGH_FLATTEN
                // The surface rises across the axis it travels along, as water does.
                val x = designX + driftX - lift * WaveAngleSin
                val y = designY + driftY + lift * WaveAngleCos
                vertices[index++] = offsetX + x * scale
                vertices[index++] = offsetY + y * scale
            }
        }
    }
}
