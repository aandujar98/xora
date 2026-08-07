package com.arcadia.shell.feature.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs
import kotlin.math.sqrt

/** Beyond this much tilt (radians) the bubbles are already at full deflection. */
private const val TILT_FULL_SCALE_RADIANS = 0.42f

/**
 * How fast the neutral pose chases the current pose, in units of "fraction per second".
 * Holding the device at an angle drifts back to rest; only fresh motion deflects the bubbles.
 */
private const val TILT_REST_RELAX_PER_SECOND = 0.85f

private const val SPRING_STIFFNESS = 52f
private const val SPRING_DAMPING_RATIO = 0.34f
private const val MAX_FRAME_SECONDS = 1f / 30f

/**
 * Normalised device tilt on the screen's own axes, each component in `-1..1`.
 *
 * The value is relative to a slowly relaxing rest pose, so it reads as "how much the device just
 * moved" rather than "how the device is being held" — a couch player leaning back does not leave
 * the tray permanently skewed.
 */
@Composable
fun rememberDeviceTilt(active: Boolean): State<Offset> {
    val context = LocalContext.current
    val view = LocalView.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(active, context, view) {
        if (!active) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val sensor = rotationSensor ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }

        val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
        val listener = TiltListener(
            usesRotationVector = rotationSensor != null,
            displayRotation = displayRotation,
            output = tilt,
        )
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            manager.unregisterListener(listener)
            tilt.value = Offset.Zero
        }
    }
    return tilt
}

private class TiltListener(
    private val usesRotationVector: Boolean,
    displayRotation: Int,
    private val output: MutableState<Offset>,
) : SensorEventListener {

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)

    private val axisX: Int
    private val axisY: Int
    private val swapAxes: Boolean
    private val signX: Float
    private val signY: Float

    private var restX = Float.NaN
    private var restY = Float.NaN
    private var lastEventNanos = 0L

    init {
        // Sensor readings arrive in the device's natural orientation; the tray is laid out in the
        // display's orientation, so the axes have to be swapped to match what the player sees.
        when (displayRotation) {
            Surface.ROTATION_90 -> {
                axisX = SensorManager.AXIS_Y
                axisY = SensorManager.AXIS_MINUS_X
                swapAxes = true
                signX = 1f
                signY = -1f
            }
            Surface.ROTATION_180 -> {
                axisX = SensorManager.AXIS_MINUS_X
                axisY = SensorManager.AXIS_MINUS_Y
                swapAxes = false
                signX = -1f
                signY = -1f
            }
            Surface.ROTATION_270 -> {
                axisX = SensorManager.AXIS_MINUS_Y
                axisY = SensorManager.AXIS_X
                swapAxes = true
                signX = -1f
                signY = 1f
            }
            else -> {
                axisX = SensorManager.AXIS_X
                axisY = SensorManager.AXIS_Y
                swapAxes = false
                signX = 1f
                signY = 1f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val pose = when {
            usesRotationVector -> poseFromRotationVector(event)
            else -> poseFromGravity(event) ?: return
        }

        val elapsed = if (lastEventNanos == 0L) {
            0f
        } else {
            ((event.timestamp - lastEventNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
        }
        lastEventNanos = event.timestamp

        if (restX.isNaN() || restY.isNaN()) {
            restX = pose.x
            restY = pose.y
        } else {
            val relax = (TILT_REST_RELAX_PER_SECOND * elapsed).coerceIn(0f, 1f)
            restX += (pose.x - restX) * relax
            restY += (pose.y - restY) * relax
        }

        output.value = Offset(
            x = ((pose.x - restX) / TILT_FULL_SCALE_RADIANS).coerceIn(-1f, 1f),
            y = ((pose.y - restY) / TILT_FULL_SCALE_RADIANS).coerceIn(-1f, 1f),
        )
    }

    private fun poseFromRotationVector(event: SensorEvent): Offset {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)
        // orientation = [azimuth, pitch, roll]; heading is irrelevant to a tilt effect.
        return Offset(x = orientation[2], y = orientation[1])
    }

    private fun poseFromGravity(event: SensorEvent): Offset? {
        if (event.values.size < 3) return null
        // Low-pass the accelerometer so hand tremor does not read as tilt.
        for (i in 0..2) {
            gravity[i] += (event.values[i] - gravity[i]) * 0.18f
        }
        val magnitude = sqrt(
            gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2],
        )
        if (magnitude < 1e-3f) return null
        val deviceX = (gravity[0] / magnitude).coerceIn(-1f, 1f)
        val deviceY = (gravity[1] / magnitude).coerceIn(-1f, 1f)
        val screenX = if (swapAxes) deviceY else deviceX
        val screenY = if (swapAxes) deviceX else deviceY
        return Offset(
            x = -screenX * signX * TILT_FULL_SCALE_RADIANS,
            y = screenY * signY * TILT_FULL_SCALE_RADIANS,
        )
    }
}

/**
 * Per-bubble spring offsets. Each bubble owns its own snapshot state so the frame loop only
 * invalidates the layer that moved, never the whole tray.
 */
@Stable
class VitaBubbleMotion(val count: Int) {
    private val offsets: List<MutableState<Offset>> = List(count) { mutableStateOf(Offset.Zero) }

    fun offsetAt(index: Int): Offset =
        if (index in 0 until count) offsets[index].value else Offset.Zero

    internal fun setOffset(index: Int, value: Offset) {
        offsets[index].value = value
    }
}

/**
 * Drives [VitaBubbleMotion] from device tilt with an under-damped spring per bubble, so the
 * bubbles sway past the target and settle — the LiveArea wobble rather than a rigid parallax.
 * Slightly detuned springs keep neighbours from moving in lockstep.
 */
@Composable
fun rememberVitaBubbleMotion(
    count: Int,
    tilt: State<Offset>,
    maxShiftPx: Float,
    enabled: Boolean,
): VitaBubbleMotion {
    val motion = remember(count) { VitaBubbleMotion(count) }

    LaunchedEffect(motion, tilt, maxShiftPx, enabled) {
        if (!enabled || count == 0 || maxShiftPx <= 0f) {
            for (i in 0 until count) motion.setOffset(i, Offset.Zero)
            return@LaunchedEffect
        }
        val posX = FloatArray(count)
        val posY = FloatArray(count)
        val velX = FloatArray(count)
        val velY = FloatArray(count)
        var lastFrame = 0L

        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrame == 0L) {
                    0f
                } else {
                    ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                }
                lastFrame = now
                if (dt <= 0f) return@withFrameNanos

                val target = tilt.value
                for (i in 0 until count) {
                    val stiffness = SPRING_STIFFNESS * bubbleDetune(i)
                    val damping = 2f * SPRING_DAMPING_RATIO * sqrt(stiffness)
                    val amplitude = maxShiftPx * bubbleAmplitude(i)
                    val targetX = target.x * amplitude
                    val targetY = target.y * amplitude

                    velX[i] += (stiffness * (targetX - posX[i]) - damping * velX[i]) * dt
                    velY[i] += (stiffness * (targetY - posY[i]) - damping * velY[i]) * dt
                    posX[i] += velX[i] * dt
                    posY[i] += velY[i] * dt

                    val settled = abs(velX[i]) < 0.02f && abs(velY[i]) < 0.02f &&
                        abs(posX[i] - targetX) < 0.05f && abs(posY[i] - targetY) < 0.05f
                    if (settled) {
                        posX[i] = targetX
                        posY[i] = targetY
                        velX[i] = 0f
                        velY[i] = 0f
                    }
                    motion.setOffset(i, Offset(posX[i], posY[i]))
                }
            }
        }
    }
    return motion
}

/** Golden-ratio walk: a stable, well-spread spread of values in `0.75..1.25` without a PRNG. */
private fun bubbleDetune(index: Int): Float {
    val fraction = (index * 0.6180339f) % 1f
    return 0.75f + (fraction * 0.5f)
}

private fun bubbleAmplitude(index: Int): Float {
    val fraction = (index * 0.3819660f) % 1f
    return 0.78f + (fraction * 0.44f)
}
