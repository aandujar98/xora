package com.arcadia.shell.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.arcadia.shell.R
import com.arcadia.shell.datastore.DEFAULT_UI_SFX_VOLUME
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.input.GamepadDispatcher
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.ShellSystemNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short UI navigation one-shots (cursor / confirm / cancel). Kept separate from
 * [BackgroundMusicController] so BGM mute does not silence menu clicks.
 *
 * Hooks [GamepadDispatcher.actions] once so every screen that consumes NavActions gets the same
 * feedback without each ViewModel knowing about audio. D-pad keys, hat switches, and the left
 * analog stick all emit the same directional [NavAction]s — each step (including hold-repeat)
 * plays the cursor click and a light haptic tick (XMB-style navigate feel).
 *
 * Also plays a confirm chime when a shell notification banner becomes active, if the
 * notification-sound preference is on.
 */
@Singleton
class UiSoundController @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: ShellPreferences,
    private val gamepadDispatcher: GamepadDispatcher,
    notificationCenter: ShellNotificationCenter,
    systemNotifier: ShellSystemNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var soundPool: SoundPool? = null
    private var cursorId: Int = 0
    private var okId: Int = 0
    private var ngId: Int = 0
    /** Friend online / download complete / RA unlock banner chime (`notif_banner.wav`). */
    private var notificationId: Int = 0

    private var volume: Float = DEFAULT_UI_SFX_VOLUME
    private var notificationSoundEnabled: Boolean = true
    private var foreground: Boolean = false

    /** Light debounce when hat + DPAD key both emit the same direction for one physical press. */
    private var lastCursorAction: NavAction? = null
    private var lastCursorAtMs: Long = 0L

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()?.takeIf { it.hasVibrator() }
    }

    init {
        ensurePool()
        scope.launch {
            preferences.settings
                .map { it.uiSfxVolume }
                .distinctUntilChanged()
                .collect { next ->
                    volume = next.coerceIn(0f, 1f)
                }
        }
        scope.launch {
            preferences.settings
                .map { it.notificationsEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    notificationCenter.notificationsEnabled = enabled
                }
        }
        scope.launch {
            preferences.settings
                .map { it.notificationSoundEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    notificationSoundEnabled = enabled
                    systemNotifier.soundEnabled = enabled
                }
        }
        scope.launch {
            notificationCenter.active.collect { active ->
                if (active != null && notificationSoundEnabled && foreground) {
                    playNotificationChime()
                }
            }
        }
        scope.launch {
            gamepadDispatcher.actions.collect { action ->
                if (foreground) playFor(action)
            }
        }
    }

    fun onForeground() {
        foreground = true
        ensurePool()
    }

    fun onBackground() {
        foreground = false
        runCatching { soundPool?.autoPause() }
    }

    /** Drop SoundPool samples under memory pressure; rebuilt on next foreground. */
    fun releaseForTrim() {
        runCatching { soundPool?.release() }
        soundPool = null
        cursorId = 0
        okId = 0
        ngId = 0
        notificationId = 0
    }

    /** Banner appear chime — friend online, download complete, RetroAchievement unlock. */
    fun playNotificationChime() {
        play(if (notificationId != 0) notificationId else okId)
    }

    /** Select / confirm one-shot — launcher Confirm and XOrA Emulator overlay activate. */
    fun playConfirm() = play(okId)

    /** Cancel / back one-shot. */
    fun playCancel() = play(ngId)

    /** Cursor / focus-move one-shot. */
    fun playCursor() = play(cursorId)

    private fun playFor(action: NavAction) {
        val soundId = when (action) {
            NavAction.Left,
            NavAction.Right,
            NavAction.Up,
            NavAction.Down,
            NavAction.PreviousPlatform,
            NavAction.NextPlatform,
            NavAction.ToggleAccountPanel,
            NavAction.ToggleSystemPanel,
            NavAction.ToggleAchievementsPanel,
            -> {
                if (shouldSuppressDuplicateCursor(action)) return
                vibrateCursor()
                cursorId
            }

            NavAction.Confirm -> okId

            NavAction.Menu ->
                // Flag is still the pre-toggle state when this action is observed.
                if (gamepadDispatcher.startSettingsOpen) ngId else okId

            NavAction.ToggleGuide ->
                // Flag is still the pre-toggle state when this action is observed.
                if (gamepadDispatcher.guideOpen) ngId else okId
            NavAction.Cancel -> ngId

            NavAction.Options,
            NavAction.ScrapeMenu,
            NavAction.ToggleFavorite,
            NavAction.SwapScreens,
            -> return
        }
        play(soundId)
    }

    private fun shouldSuppressDuplicateCursor(action: NavAction): Boolean {
        val now = SystemClock.uptimeMillis()
        val duplicate = action == lastCursorAction && (now - lastCursorAtMs) < CURSOR_DEBOUNCE_MS
        lastCursorAction = action
        lastCursorAtMs = now
        return duplicate
    }

    private fun play(soundId: Int) {
        if (soundId == 0) return
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        // SoundPool.play returns 0 until the sample finishes loading; retries on later NavActions.
        runCatching {
            soundPool?.play(soundId, v, v, /* priority */ 1, /* loop */ 0, /* rate */ 1f)
        }
    }

    /** Short XMB-style tick on each launcher cursor step (independent of UI SFX volume). */
    private fun vibrateCursor() {
        val vibrator = vibrator ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private fun ensurePool() {
        if (soundPool != null) return
        val pool = runCatching {
            SoundPool.Builder()
                // Hold-repeat can fire cursor every ~70ms; keep enough streams that clicks stay audible.
                .setMaxStreams(8)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // GAME (not SONIFICATION): handhelds/TV often keep the system stream muted
                        // while media/game volume is up — UI clicks must follow the latter.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
                .also { created ->
                    cursorId = created.loadQuietly(R.raw.snd_cursor)
                    okId = created.loadQuietly(R.raw.snd_system_ok)
                    ngId = created.loadQuietly(R.raw.snd_system_ng)
                    notificationId = created.loadQuietly(R.raw.notif_banner)
                }
        }.getOrNull()
        soundPool = pool
    }

    private fun SoundPool.loadQuietly(resId: Int): Int =
        runCatching { load(context, resId, /* priority */ 1) }.getOrDefault(0)

    private companion object {
        const val CURSOR_DEBOUNCE_MS = 30L
    }
}
