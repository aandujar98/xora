package com.arcadia.shell.libretro

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/** Libretro RETRO_DEVICE_ID_JOYPAD_* bit indices. */
object LibretroPad {
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15

    private const val DPAD_MASK =
        (1 shl UP) or (1 shl DOWN) or (1 shl LEFT) or (1 shl RIGHT)

    /**
     * Resolve a keycode to a Libretro joypad button.
     * [customMappings] (keycode → button index) win over the built-in table when present.
     */
    fun keyCodeToButton(keyCode: Int, customMappings: Map<Int, Int> = emptyMap()): Int? {
        customMappings[keyCode]?.let { return it }
        return defaultKeyCodeToButton(keyCode)
    }

    fun defaultKeyCodeToButton(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_B -> B
        KeyEvent.KEYCODE_BUTTON_Y -> Y
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_SPACE,
        -> SELECT
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> START
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_DPAD_CENTER,
        -> A
        KeyEvent.KEYCODE_BUTTON_X -> X
        KeyEvent.KEYCODE_BUTTON_L1 -> L
        KeyEvent.KEYCODE_BUTTON_R1 -> R
        KeyEvent.KEYCODE_BUTTON_L2 -> L2
        KeyEvent.KEYCODE_BUTTON_R2 -> R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> R3
        // Generic HID pads that never get remapped to BUTTON_A/B.
        KeyEvent.KEYCODE_BUTTON_1 -> A
        KeyEvent.KEYCODE_BUTTON_2 -> B
        KeyEvent.KEYCODE_BUTTON_3 -> X
        KeyEvent.KEYCODE_BUTTON_4 -> Y
        KeyEvent.KEYCODE_BUTTON_5 -> L
        KeyEvent.KEYCODE_BUTTON_6 -> R
        KeyEvent.KEYCODE_BUTTON_7 -> SELECT
        KeyEvent.KEYCODE_BUTTON_8 -> START
        KeyEvent.KEYCODE_BUTTON_9 -> L3
        KeyEvent.KEYCODE_BUTTON_10 -> R3
        KeyEvent.KEYCODE_BUTTON_11 -> L2
        KeyEvent.KEYCODE_BUTTON_12 -> R2
        else -> null
    }

    /** Face / shoulder / system buttons offered in the remapper UI (D-pad stays on hat/keys). */
    val MAPPABLE_BUTTONS: List<Pair<Int, String>> = listOf(
        A to "A (South)",
        B to "B (East)",
        X to "X (North)",
        Y to "Y (West)",
        L to "L",
        R to "R",
        L2 to "L2",
        R2 to "R2",
        L3 to "L3",
        R3 to "R3",
        SELECT to "Select",
        START to "Start",
    )

    fun buttonLabel(button: Int): String =
        MAPPABLE_BUTTONS.firstOrNull { it.first == button }?.second ?: "Button $button"

    fun keyCodeLabel(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "Button A"
        KeyEvent.KEYCODE_BUTTON_B -> "Button B"
        KeyEvent.KEYCODE_BUTTON_X -> "Button X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Button Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
        KeyEvent.KEYCODE_DPAD_CENTER -> "D-Pad Center"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }

    /** Connected pads as (id, name). */
    fun connectedControllers(): List<Pair<Int, String>> {
        val ids = runCatching { InputDevice.getDeviceIds() }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        for (id in ids) {
            val device = runCatching { InputDevice.getDevice(id) }.getOrNull() ?: continue
            val sources = device.sources
            val isPad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if (!isPad || device.isVirtual) continue
            val name = device.name?.takeIf { it.isNotBlank() } ?: "Controller $id"
            out += id to name
        }
        return out
    }

    fun matchesPreferredController(device: InputDevice?, preferredName: String): Boolean {
        if (preferredName.isBlank()) return true
        val name = device?.name?.trim().orEmpty()
        if (name.equals(preferredName, ignoreCase = true)) return true
        // A saved name that is not plugged in must not black-hole every other pad.
        return connectedControllers().none { it.second.equals(preferredName, ignoreCase = true) }
    }

    /** Netplay (and missing preferred pads) accept every plugged-in controller. */
    fun acceptsController(
        device: InputDevice?,
        preferredName: String,
        acceptAny: Boolean,
    ): Boolean = acceptAny || matchesPreferredController(device, preferredName)

    fun axisToShort(value: Float, deadzone: Float = 0.15f): Short {
        if (abs(value) < deadzone) return 0
        return (value.coerceIn(-1f, 1f) * 0x7fff).toInt().toShort()
    }

    fun readAxes(event: MotionEvent): Pair<Pair<Short, Short>, Pair<Short, Short>> {
        val lx = axisToShort(event.getAxisValue(MotionEvent.AXIS_X))
        val ly = axisToShort(event.getAxisValue(MotionEvent.AXIS_Y))
        // Prefer RX/RY when present; fall back to Z/RZ (common on older mappings).
        val rxRaw = event.getAxisValue(MotionEvent.AXIS_RX).takeIf { it != 0f }
            ?: event.getAxisValue(MotionEvent.AXIS_Z)
        val ryRaw = event.getAxisValue(MotionEvent.AXIS_RY).takeIf { it != 0f }
            ?: event.getAxisValue(MotionEvent.AXIS_RZ)
        val rx = axisToShort(rxRaw)
        val ry = axisToShort(ryRaw)
        return (lx to ly) to (rx to ry)
    }

    /**
     * Digital bits derived only from axes (hat / left stick / triggers).
     * Combined with key bits via OR so stick centering cannot clear held DPAD keys.
     */
    fun digitalPadFromAxes(event: MotionEvent): Int {
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        val x = if (abs(hatX) >= abs(stickX)) hatX else stickX
        val y = if (abs(hatY) >= abs(stickY)) hatY else stickY

        var out = 0
        if (x < -0.5f) out = out or (1 shl LEFT)
        if (x > 0.5f) out = out or (1 shl RIGHT)
        if (y < -0.5f) out = out or (1 shl UP)
        if (y > 0.5f) out = out or (1 shl DOWN)

        val l2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val r2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        if (l2 > 0.5f) out = out or (1 shl L2)
        if (r2 > 0.5f) out = out or (1 shl R2)
        return out
    }

    /** @deprecated Use [digitalPadFromAxes] and OR with key bits. */
    fun applyDigitalPad(buttons: Int, event: MotionEvent): Int =
        (buttons and DPAD_MASK.inv() and (1 shl L2).inv() and (1 shl R2).inv()) or
            digitalPadFromAxes(event)

    fun KeyEvent.isFromGameController(customMappings: Map<Int, Int> = emptyMap()): Boolean =
        isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD) ||
            // Many Bluetooth pads also report as keyboard for face buttons.
            (isFromSource(InputDevice.SOURCE_KEYBOARD) &&
                keyCodeToButton(keyCode, customMappings) != null)

    fun MotionEvent.isFromGameController(): Boolean =
        isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD)

    /** Human-readable names of currently connected game controllers. */
    fun connectedControllerNames(): List<String> = connectedControllers().map { it.second }
}

/**
 * Merges every plugged-in pad into one RetroPad so a second Bluetooth controller
 * is not dropped when a "preferred" device is saved or when two pads share a device.
 */
class LibretroPadMixer {
    data class Snapshot(
        val buttons: Int = 0,
        val lx: Short = 0,
        val ly: Short = 0,
        val rx: Short = 0,
        val ry: Short = 0,
    )

    private class DeviceState {
        var keyButtons: Int = 0
        var axisButtons: Int = 0
        var lx: Short = 0
        var ly: Short = 0
        var rx: Short = 0
        var ry: Short = 0
    }

    private val devices = java.util.concurrent.ConcurrentHashMap<Int, DeviceState>()

    fun keyDown(deviceId: Int, bit: Int) {
        val state = devices.getOrPut(deviceId) { DeviceState() }
        synchronized(state) { state.keyButtons = state.keyButtons or (1 shl bit) }
    }

    fun keyUp(deviceId: Int, bit: Int) {
        val state = devices[deviceId] ?: return
        synchronized(state) { state.keyButtons = state.keyButtons and (1 shl bit).inv() }
    }

    fun motion(deviceId: Int, lx: Short, ly: Short, rx: Short, ry: Short, axisButtons: Int) {
        val state = devices.getOrPut(deviceId) { DeviceState() }
        synchronized(state) {
            state.lx = lx
            state.ly = ly
            state.rx = rx
            state.ry = ry
            state.axisButtons = axisButtons
        }
    }

    fun forget(deviceId: Int) {
        devices.remove(deviceId)
    }

    fun snapshot(): Snapshot {
        var buttons = 0
        var bestMag = -1
        var lx: Short = 0
        var ly: Short = 0
        var rx: Short = 0
        var ry: Short = 0
        for (state in devices.values) {
            synchronized(state) {
                buttons = buttons or state.keyButtons or state.axisButtons
                val mag = kotlin.math.abs(state.lx.toInt()) +
                    kotlin.math.abs(state.ly.toInt()) +
                    kotlin.math.abs(state.rx.toInt()) +
                    kotlin.math.abs(state.ry.toInt())
                if (mag > bestMag) {
                    bestMag = mag
                    lx = state.lx
                    ly = state.ly
                    rx = state.rx
                    ry = state.ry
                }
            }
        }
        return Snapshot(buttons = buttons, lx = lx, ly = ly, rx = rx, ry = ry)
    }
}