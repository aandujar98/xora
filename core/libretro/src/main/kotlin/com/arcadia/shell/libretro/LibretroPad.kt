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

    fun keyCodeToButton(keyCode: Int): Int? = when (keyCode) {
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

    fun KeyEvent.isFromGameController(): Boolean =
        isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD) ||
            // Many Bluetooth pads also report as keyboard for face buttons.
            (isFromSource(InputDevice.SOURCE_KEYBOARD) && keyCodeToButton(keyCode) != null)

    fun MotionEvent.isFromGameController(): Boolean =
        isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD)

    /** Human-readable names of currently connected game controllers. */
    fun connectedControllerNames(): List<String> {
        val names = ArrayList<String>()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isPad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if (!isPad || device.isVirtual) continue
            names += device.name?.takeIf { it.isNotBlank() } ?: "Controller $id"
        }
        return names
    }
}