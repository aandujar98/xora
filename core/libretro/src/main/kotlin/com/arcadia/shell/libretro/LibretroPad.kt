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
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_W,
        -> UP
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_S,
        -> DOWN
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_A,
        -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_D,
        -> RIGHT
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_K,
        KeyEvent.KEYCODE_X,
        -> A
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_I,
        -> X
        KeyEvent.KEYCODE_BUTTON_L1 -> L
        KeyEvent.KEYCODE_BUTTON_R1 -> R
        KeyEvent.KEYCODE_BUTTON_L2 -> L2
        KeyEvent.KEYCODE_BUTTON_R2 -> R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> R3
        KeyEvent.KEYCODE_BUTTON_C -> A
        KeyEvent.KEYCODE_BUTTON_Z -> B
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
        KeyEvent.KEYCODE_BUTTON_13 -> L
        KeyEvent.KEYCODE_BUTTON_14 -> R
        KeyEvent.KEYCODE_BUTTON_15 -> L2
        KeyEvent.KEYCODE_BUTTON_16 -> R2
        KeyEvent.KEYCODE_Z,
        KeyEvent.KEYCODE_J,
        -> B
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

    /**
     * Anbernic / RG Rotate / gpio-keys often expose D-pad + face buttons as KEYBOARD or
     * DPAD instead of GAMEPAD. Those still have to count as extra local seats (P2–P4)
     * and as a joiner's assigned pad.
     */
    fun looksLikeHandheldPad(name: String?, sources: Int = 0): Boolean {
        val dpad = sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        val touch = sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
        if (dpad && !touch) return true
        val n = name.orEmpty().lowercase()
        if (n.isBlank()) return false
        if (n.contains("gpio") ||
            n.contains("retrogame") ||
            n.contains("joypad") ||
            n.contains("joystick") ||
            n.contains("adc") ||
            n.contains("anbernic") ||
            n.contains("odroid") ||
            n.contains("h700") ||
            n.contains("rk3566") ||
            n.contains("rk3326") ||
            n.contains("singleadc") ||
            n.contains("nvec")
        ) {
            return true
        }
        return n.contains("rg") && (
            n.contains("rotate") || n.contains("353") || n.contains("405") ||
                n.contains("arc") || n.contains("cube") || n.contains("35xx") ||
                n.contains("28xx") || n.contains("40xx") || n.contains("slide") ||
                n.contains("flip") || n.contains("503") || n.contains("552") ||
                n.contains("351") || n.contains("556") || n.contains("476")
            )
    }

    /** Connected pads as (id, name). Includes gpio-keys / DPAD handhelds, not only GAMEPAD. */
    fun connectedControllers(): List<Pair<Int, String>> {
        val ids = runCatching { InputDevice.getDeviceIds() }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        for (id in ids) {
            val device = runCatching { InputDevice.getDevice(id) }.getOrNull() ?: continue
            val sources = device.sources
            val isPad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                looksLikeHandheldPad(device.name, sources)
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

    fun MotionEvent.isFromGameController(): Boolean {
        if (isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD)
        ) {
            return true
        }
        val sources = device?.sources ?: source
        return sources and InputDevice.SOURCE_CLASS_JOYSTICK == InputDevice.SOURCE_CLASS_JOYSTICK
    }

    /**
     * On Anbernic handhelds B is often KEYCODE_BACK. Phone Back must still open the pause
     * menu; only pads / gpio-keys should treat Back as RetroPad B.
     */
    fun KeyEvent.handheldBackIsB(): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return false
        if (isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD)
        ) {
            return true
        }
        val device = device ?: return false
        return looksLikeHandheldPad(device.name, device.sources)
    }

    fun padButtonFor(event: KeyEvent, customMappings: Map<Int, Int> = emptyMap()): Int? {
        if (event.handheldBackIsB()) return B
        return keyCodeToButton(event.keyCode, customMappings)
    }

    /** ADC joysticks / hats that are not SOURCE_GAMEPAD still have to drive the mixer. */
    fun MotionEvent.shouldDrivePad(): Boolean {
        if (isFromGameController()) return true
        val device = device
        if (device != null && looksLikeHandheldPad(device.name, device.sources)) return true
        if (digitalPadFromAxes(this) != 0) return true
        val (left, right) = readAxes(this)
        return left.first.toInt() != 0 || left.second.toInt() != 0 ||
            right.first.toInt() != 0 || right.second.toInt() != 0
    }

    fun descriptorOf(deviceId: Int): String =
        runCatching { InputDevice.getDevice(deviceId)?.descriptor }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "id:$deviceId"

    fun controllerNumberOf(deviceId: Int): Int =
        runCatching { InputDevice.getDevice(deviceId)?.controllerNumber ?: 0 }.getOrDefault(0)

    /** Human-readable names of currently connected game controllers. */
    fun connectedControllerNames(): List<String> = connectedControllers().map { it.second }
}

/**
 * Tracks every plugged-in pad separately so local P2 is a second controller, not a
 * merge into P1. Button and axis InputDevices that share a descriptor stay one player.
 */
class LibretroPadMixer {
    data class Snapshot(
        val buttons: Int = 0,
        val lx: Short = 0,
        val ly: Short = 0,
        val rx: Short = 0,
        val ry: Short = 0,
    ) {
        fun hasInput(): Boolean =
            buttons != 0 || lx.toInt() != 0 || ly.toInt() != 0 ||
                rx.toInt() != 0 || ry.toInt() != 0

        fun merge(other: Snapshot): Snapshot {
            val magThis = mag()
            val magOther = other.mag()
            val analog = if (magOther > magThis) other else this
            return Snapshot(
                buttons = buttons or other.buttons,
                lx = analog.lx,
                ly = analog.ly,
                rx = analog.rx,
                ry = analog.ry,
            )
        }

        private fun mag(): Int =
            kotlin.math.abs(lx.toInt()) + kotlin.math.abs(ly.toInt()) +
                kotlin.math.abs(rx.toInt()) + kotlin.math.abs(ry.toInt())
    }

    data class PlayerPads(
        val p1: Snapshot = Snapshot(),
        val p2: Snapshot = Snapshot(),
        val p3: Snapshot = Snapshot(),
        val p4: Snapshot = Snapshot(),
    )

    private class DeviceState {
        var keyButtons: Int = 0
        var axisButtons: Int = 0
        var lx: Short = 0
        var ly: Short = 0
        var rx: Short = 0
        var ry: Short = 0
    }

    private val lock = Any()
    private val devices = LinkedHashMap<Int, DeviceState>()
    private val order = ArrayList<Int>()

    fun setDigital(deviceId: Int, buttons: Int) {
        synchronized(lock) {
            val state = stateFor(deviceId)
            state.keyButtons = buttons
            state.axisButtons = 0
        }
    }

    fun keyDown(deviceId: Int, bit: Int) {
        synchronized(lock) {
            val state = stateFor(deviceId)
            state.keyButtons = state.keyButtons or (1 shl bit)
        }
    }

    fun keyUp(deviceId: Int, bit: Int) {
        synchronized(lock) {
            val state = devices[deviceId] ?: return
            state.keyButtons = state.keyButtons and (1 shl bit).inv()
        }
    }

    fun motion(deviceId: Int, lx: Short, ly: Short, rx: Short, ry: Short, axisButtons: Int) {
        synchronized(lock) {
            val state = stateFor(deviceId)
            state.lx = lx
            state.ly = ly
            state.rx = rx
            state.ry = ry
            state.axisButtons = axisButtons
        }
    }

    fun forget(deviceId: Int) {
        synchronized(lock) {
            devices.remove(deviceId)
            order.remove(deviceId)
        }
    }

    /** All local pads OR'd together — netplay sends this as "this player's" input. */
    fun snapshot(): Snapshot = synchronized(lock) {
        devices.keys.fold(Snapshot()) { acc, id -> acc.merge(snapshotLocked(id)) }
    }

    /**
     * Player 1 = preferred pad, Android controllerNumber 1, or the first gamepad.
     * Players 2–4 = controllerNumber 2–4 or the next distinct gamepads in order.
     * Keyboard / unpaired devices fold into P1 so they never steal a later slot.
     * gpio-keys / Anbernic DPAD devices are extra seats, not P1 leftovers.
     */
    fun snapshotPlayers(
        preferredName: String = "",
        connected: List<Pair<Int, String>> = LibretroPad.connectedControllers(),
        descriptorOf: (Int) -> String = LibretroPad::descriptorOf,
        numberOf: (Int) -> Int = LibretroPad::controllerNumberOf,
        nameOf: (Int) -> String = { id ->
            connected.firstOrNull { it.first == id }?.second
                ?: runCatching { InputDevice.getDevice(id)?.name }.getOrNull().orEmpty()
        },
    ): PlayerPads = synchronized(lock) {
        data class Group(
            val ids: MutableList<Int> = ArrayList(),
            var number: Int = 0,
        )

        val groups = LinkedHashMap<String, Group>()
        val seen = LinkedHashSet<Int>()
        order.forEach { seen.add(it) }
        connected.forEach { seen.add(it.first) }
        devices.keys.forEach { seen.add(it) }

        for (id in seen) {
            val key = descriptorOf(id)
            val group = groups.getOrPut(key) { Group() }
            group.ids += id
            val number = numberOf(id)
            if (group.number == 0 && number > 0) group.number = number
        }

        val gamepadIds = connected.map { it.first }.toSet()
        fun Group.displayName(): String =
            connected.firstOrNull { it.first in ids }?.second.orEmpty()
        fun Group.isGamepad(): Boolean =
            ids.any { it in gamepadIds } ||
                number > 0 ||
                LibretroPad.looksLikeHandheldPad(displayName()) ||
                ids.any { LibretroPad.looksLikeHandheldPad(nameOf(it)) }

        fun mergeGroup(group: Group?): Snapshot {
            if (group == null) return Snapshot()
            return group.ids.fold(Snapshot()) { acc, id -> acc.merge(snapshotLocked(id)) }
        }

        val all = groups.values.toList()
        val pads = all.filter { it.isGamepad() }
        val extras = all.filter { !it.isGamepad() }

        val preferred = pads.firstOrNull { group ->
            preferredName.isNotBlank() &&
                group.displayName().equals(preferredName, ignoreCase = true)
        }
        val p1Group = preferred
            ?: pads.firstOrNull { it.number == 1 }
            ?: pads.firstOrNull()
        val remaining = pads.filter { it !== p1Group }
        val p2Group = remaining.firstOrNull { it.number == 2 } ?: remaining.firstOrNull()
        val afterP2 = remaining.filter { it !== p2Group }
        val p3Group = afterP2.firstOrNull { it.number == 3 } ?: afterP2.firstOrNull()
        val afterP3 = afterP2.filter { it !== p3Group }
        val p4Group = afterP3.firstOrNull { it.number == 4 } ?: afterP3.firstOrNull()

        val extraSnap = extras.fold(Snapshot()) { acc, group -> acc.merge(mergeGroup(group)) }
        PlayerPads(
            p1 = mergeGroup(p1Group).merge(extraSnap),
            p2 = mergeGroup(p2Group),
            p3 = mergeGroup(p3Group),
            p4 = mergeGroup(p4Group),
        )
    }

    private fun stateFor(deviceId: Int): DeviceState {
        return devices.getOrPut(deviceId) {
            order.add(deviceId)
            DeviceState()
        }
    }

    private fun snapshotLocked(deviceId: Int): Snapshot {
        val state = devices[deviceId] ?: return Snapshot()
        return Snapshot(
            buttons = state.keyButtons or state.axisButtons,
            lx = state.lx,
            ly = state.ly,
            rx = state.rx,
            ry = state.ry,
        )
    }
}