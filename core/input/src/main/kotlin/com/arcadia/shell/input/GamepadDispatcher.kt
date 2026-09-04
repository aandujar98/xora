package com.arcadia.shell.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Translates raw controller input into [NavAction]s.
 *
 * Compose's focus traversal is not used for grid movement. It resolves the "next" focusable
 * geometrically and lazily, which breaks down when a d-pad is held down across a large lazy grid
 * whose items have not been composed yet. An explicit index model always knows where the selection
 * is going, even into rows that do not exist on screen yet.
 *
 * Auto-repeat is generated here rather than taken from the system's key repeat, so that a held
 * d-pad, a hat switch, and an analog stick all scroll at exactly the same cadence.
 *
 * Start+Select is treated as a chord for [NavAction.ToggleGuide]. Menu / ScrapeMenu are deferred
 * briefly so a simultaneous press does not also open Settings and the scrape sheet.
 */
@Singleton
class GamepadDispatcher @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Set to false while a screen other than the library is showing. Those screens are ordinary
     * scrolling forms where Compose's own focus traversal is the right behaviour, and letting them
     * fall through avoids the grid silently scrolling behind a dialog.
     */
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) reset()
        }

    /**
     * Mirrored from the Home Guide UI so [NavAction.ToggleGuide] can pick Ok vs Ng for SFX
     * without the audio layer depending on feature modules.
     */
    @Volatile
    var guideOpen: Boolean = false

    /**
     * Mirrored from the Start settings popup so [NavAction.Menu] can pick Ok vs Ng for SFX
     * without the audio layer depending on feature modules.
     */
    @Volatile
    var startSettingsOpen: Boolean = false

    /**
     * True when B will dismiss the LT friends or RT profile window rather than step back
     * inside it. The audio layer skips the generic cancel click so [UiOneShot.NavClose]
     * can play instead.
     */
    @Volatile
    var heroPanelClosesOnCancel: Boolean = false

    /**
     * True while the Vita shortcut tray is open on a bubble (not the isolated launch
     * page). Confirm then plays [UiOneShot.BubbleLaunch] instead of the generic ok click.
     */
    @Volatile
    var vitaBubbleLaunchSfx: Boolean = false

    /**
     * App audio layer registers here so Home can fire LT/RT open/close one-shots (including
     * touch toggles) without feature modules depending on `:app`.
     */
    @Volatile
    var uiOneShotPlayer: UiOneShotPlayer? = null

    private val _actions = MutableSharedFlow<NavAction>(extraBufferCapacity = 64)
    val actions: SharedFlow<NavAction> = _actions.asSharedFlow()

    /** The direction currently being auto-repeated, and the job driving it. */
    private var heldDirection: NavAction? = null
    private var repeatJob: Job? = null

    /** Last stick/hat direction reported, so movement is only emitted on a change of state. */
    private var analogDirection: NavAction? = null

    /** Directional DPAD keys currently down (so a centered stick event cannot cancel a key hold). */
    private val pressedDirections = mutableSetOf<NavAction>()

    // --- Start+Select chord ---------------------------------------------------------------

    private var startDown: Boolean = false
    private var selectDown: Boolean = false
    private var chordFired: Boolean = false
    private var menuEmitted: Boolean = false
    private var scrapeEmitted: Boolean = false
    private var menuDelayJob: Job? = null
    private var scrapeDelayJob: Job? = null

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled || !event.isFromController()) return false

        // The system's own repeats are dropped in favour of this class's uniform timing.
        if (event.repeatCount > 0) return true

        if (keyCode.isStartChordKey()) {
            onStartDown()
            return true
        }
        if (keyCode.isSelectChordKey()) {
            onSelectDown()
            return true
        }

        val action = keyCode.toNavAction() ?: return false

        if (action.isDirectional) {
            pressedDirections += action
            beginHold(action)
        } else {
            emit(action)
        }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled || !event.isFromController()) return false

        if (keyCode.isStartChordKey()) {
            onStartUp()
            return true
        }
        if (keyCode.isSelectChordKey()) {
            onSelectUp()
            return true
        }

        val action = keyCode.toNavAction() ?: return false

        if (action.isDirectional) {
            pressedDirections -= action
            if (heldDirection == action) {
                // Prefer an analog direction that is still active; otherwise stop.
                val analog = analogDirection
                if (analog != null) beginHold(analog) else endHold()
            }
        }
        return true
    }

    /**
     * Handles hat switches and analog sticks. Many handhelds report their d-pad as a hat axis
     * rather than as key events, so this path is not optional for controller support.
     *
     * Left stick ([MotionEvent.AXIS_X] / [MotionEvent.AXIS_Y]) is merged with the hat so either
     * control can drive UI navigation. Right-stick axes are ignored — they do not move the shell
     * cursor (thumb-click is Options / scrape, not locomotion).
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false
        if (!event.isFromController()) return false

        val x = mergeAxes(event, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_X)
        val y = mergeAxes(event, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_Y)

        val direction = resolveDirection(x, y)

        if (direction != analogDirection) {
            analogDirection = direction
            when {
                direction != null -> beginHold(direction)
                // Stick/hat returned to center. Do not cancel a physical DPAD key that is still down
                // (motion events also arrive when triggers/other axes change).
                heldDirection != null && heldDirection !in pressedDirections -> endHold()
            }
        }
        return true
    }

    /** Releases any held direction, for example when the shell loses focus mid-scroll. */
    fun reset() {
        analogDirection = null
        pressedDirections.clear()
        endHold()
        resetChordState()
    }

    private fun onStartDown() {
        startDown = true
        menuEmitted = false
        if (selectDown) {
            fireChord()
        } else {
            menuDelayJob?.cancel()
            menuDelayJob = scope.launch {
                delay(CHORD_WINDOW_MS)
                // Still holding Start alone past the window → Settings (not a chord).
                if (startDown && !selectDown && !chordFired && !menuEmitted) {
                    menuEmitted = true
                    emit(NavAction.Menu)
                }
            }
        }
    }

    private fun onStartUp() {
        menuDelayJob?.cancel()
        menuDelayJob = null
        if (startDown && !chordFired && !menuEmitted) {
            // Quick tap Start without Select joining in the window.
            menuEmitted = true
            emit(NavAction.Menu)
        }
        startDown = false
        if (!selectDown) {
            chordFired = false
        }
    }

    private fun onSelectDown() {
        selectDown = true
        scrapeEmitted = false
        if (startDown) {
            fireChord()
        } else {
            scrapeDelayJob?.cancel()
            scrapeDelayJob = scope.launch {
                delay(CHORD_WINDOW_MS)
                if (selectDown && !startDown && !chordFired && !scrapeEmitted) {
                    scrapeEmitted = true
                    emit(NavAction.ScrapeMenu)
                }
            }
        }
    }

    private fun onSelectUp() {
        scrapeDelayJob?.cancel()
        scrapeDelayJob = null
        if (selectDown && !chordFired && !scrapeEmitted) {
            scrapeEmitted = true
            emit(NavAction.ScrapeMenu)
        }
        selectDown = false
        if (!startDown) {
            chordFired = false
        }
    }

    private fun fireChord() {
        if (chordFired) return
        menuDelayJob?.cancel()
        menuDelayJob = null
        scrapeDelayJob?.cancel()
        scrapeDelayJob = null
        chordFired = true
        menuEmitted = true
        scrapeEmitted = true
        emit(NavAction.ToggleGuide)
    }

    private fun resetChordState() {
        menuDelayJob?.cancel()
        scrapeDelayJob?.cancel()
        menuDelayJob = null
        scrapeDelayJob = null
        startDown = false
        selectDown = false
        chordFired = false
        menuEmitted = false
        scrapeEmitted = false
    }

    /**
     * Applies hysteresis: crossing [PRESS_THRESHOLD] starts movement but it only stops below
     * [RELEASE_THRESHOLD]. A single threshold makes a stick resting near the edge chatter between
     * pressed and released.
     */
    private fun resolveDirection(x: Float, y: Float): NavAction? {
        val threshold = if (analogDirection == null) PRESS_THRESHOLD else RELEASE_THRESHOLD

        // The dominant axis wins, so a diagonal push does not fire two directions at once.
        return when {
            abs(x) >= threshold && abs(x) >= abs(y) ->
                if (x < 0) NavAction.Left else NavAction.Right
            abs(y) >= threshold ->
                if (y < 0) NavAction.Up else NavAction.Down
            else -> null
        }
    }

    /**
     * Hat and left stick share navigation. Prefer the larger magnitude so a resting noisy axis
     * cannot mask a real deflection on the other.
     */
    private fun mergeAxes(event: MotionEvent, hatAxis: Int, stickAxis: Int): Float {
        val hat = event.getAxisValue(hatAxis)
        val stick = event.getAxisValue(stickAxis)
        return if (abs(hat) >= abs(stick)) hat else stick
    }

    private fun beginHold(action: NavAction) {
        // Same direction already repeating (e.g. DPAD key + hat for one physical press) — keep the
        // existing cadence instead of emitting a duplicate step.
        if (heldDirection == action && repeatJob?.isActive == true) return

        repeatJob?.cancel()
        heldDirection = action
        emit(action)

        repeatJob = scope.launch {
            delay(INITIAL_REPEAT_DELAY_MS)
            while (isActive) {
                emit(action)
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    private fun endHold() {
        repeatJob?.cancel()
        repeatJob = null
        heldDirection = null
    }

    private fun emit(action: NavAction) {
        _actions.tryEmit(action)
    }

    private fun KeyEvent.isFromController(): Boolean =
        isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD) ||
            isFromSource(InputDevice.SOURCE_KEYBOARD)

    private fun MotionEvent.isFromController(): Boolean =
        isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_DPAD)

    /**
     * Maps key codes that are not part of the Start+Select chord path.
     * Start / physical Select are handled separately so Menu and ScrapeMenu can be deferred.
     * Left-thumb click still opens scrape immediately (not part of the Guide chord).
     */
    private fun Int.toNavAction(): NavAction? = when (this) {
        KeyEvent.KEYCODE_DPAD_LEFT -> NavAction.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> NavAction.Right
        KeyEvent.KEYCODE_DPAD_UP -> NavAction.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> NavAction.Down

        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> NavAction.Confirm

        // KEYCODE_BACK is deliberately absent. Mapping it here would consume the system back
        // gesture, and a debug build that cannot be backed out of is genuinely hard to escape.
        KeyEvent.KEYCODE_BUTTON_B -> NavAction.Cancel

        KeyEvent.KEYCODE_BUTTON_X -> NavAction.ToggleAchievementsPanel
        // Y swaps dual-screen roles; favourite lives in the Select scrape menu.
        KeyEvent.KEYCODE_BUTTON_Y -> NavAction.SwapScreens
        KeyEvent.KEYCODE_BUTTON_THUMBR -> NavAction.Options

        // LB / RB → Home pages (RSS feed / game selector); routed by HomeViewModel.
        KeyEvent.KEYCODE_BUTTON_L1 -> NavAction.PreviousPlatform
        KeyEvent.KEYCODE_BUTTON_R1 -> NavAction.NextPlatform
        KeyEvent.KEYCODE_BUTTON_L2 -> NavAction.ToggleAccountPanel
        KeyEvent.KEYCODE_BUTTON_R2 -> NavAction.ToggleSystemPanel

        // Left stick click: scrape menu (not chorded with Start).
        KeyEvent.KEYCODE_BUTTON_THUMBL -> NavAction.ScrapeMenu

        else -> null
    }

    private companion object {
        const val PRESS_THRESHOLD = 0.5f
        const val RELEASE_THRESHOLD = 0.35f

        const val INITIAL_REPEAT_DELAY_MS = 350L
        const val REPEAT_INTERVAL_MS = 70L

        /** Both buttons must land within this window (or the first still held) to count as a chord. */
        const val CHORD_WINDOW_MS = 250L
    }

    private fun Int.isStartChordKey(): Boolean =
        this == KeyEvent.KEYCODE_BUTTON_START || this == KeyEvent.KEYCODE_MENU

    private fun Int.isSelectChordKey(): Boolean =
        this == KeyEvent.KEYCODE_BUTTON_SELECT
}

