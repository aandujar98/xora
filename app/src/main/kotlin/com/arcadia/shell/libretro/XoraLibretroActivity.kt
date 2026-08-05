package com.arcadia.shell.libretro

import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arcadia.shell.audio.UiSoundController
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.label
import com.arcadia.shell.datastore.resolveDarkTheme
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.display.ImmersiveMode
import com.arcadia.shell.display.SecondaryDisplayPane
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scraper.RomHasher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * In-process Libretro session. Frames are drawn via Compose [Image] (not SurfaceView /
 * AndroidView) so opaque shell layers cannot hide gameplay. Immersive fullscreen matches the
 * main launcher.
 */
@AndroidEntryPoint
class XoraLibretroActivity : ComponentActivity() {

    @Inject lateinit var coreStore: CoreStore
    @Inject lateinit var coreDownloader: CoreDownloader
    @Inject lateinit var shellNotifications: ShellNotificationCenter
    @Inject lateinit var preferences: ShellPreferences
    @Inject lateinit var uiSounds: UiSoundController
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var retroAchievements: RetroAchievementsRepository
    @Inject lateinit var romHasher: RomHasher
    @Inject lateinit var libraryRepository: LibraryRepository

    private var menuOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var statusText by mutableStateOf("Loading…")
    /** Full-screen XOrA plate until the first frame (or a hard error) — no Android slide feel. */
    private var bootOverlayVisible by mutableStateOf(true)
    private var raStatusText by mutableStateOf<String?>(null)
    private var controllerStatus by mutableStateOf<String?>(null)
    private var paused by mutableStateOf(false)
    /** True while the activity is backgrounded (home/recents) — pauses the frame loop. */
    @Volatile private var activityInBackground = false
    private var gameLoaded = false
    private var freezeFrame by mutableStateOf<Bitmap?>(null)
    private var focusedMenuIndex by mutableIntStateOf(0)
    private var raSession: LibretroRaSession? = null

    private var runJob: Job? = null
    private var audioTrack: AudioTrack? = null
    /** Live gameplay bitmap shown via Compose Image (updated in place + frameTick). */
    private var gameBitmap by mutableStateOf<Bitmap?>(null)
    private var bottomBitmap by mutableStateOf<Bitmap?>(null)
    private var frameTick by mutableIntStateOf(0)
    private val bitmapLock = Any()

    private val keyPadButtons = AtomicInteger(0)
    private val axisPadButtons = AtomicInteger(0)
    @Volatile private var axisLx: Short = 0
    @Volatile private var axisLy: Short = 0
    @Volatile private var axisRx: Short = 0
    @Volatile private var axisRy: Short = 0

    private var platformId: String = "unknown"
    private var gameId: String = "game"
    private var gameTitle: String = "Game"
    private var coreName: String = ""
    private var selectHeld = false
    private var startHeld = false
    private var xoraSettings by mutableStateOf(XoraEmulatorSettings())
    private var raSettings by mutableStateOf(RetroAchievementsSettings())
    private var netplayStatus by mutableStateOf<String?>(null)
    private var expandActive by mutableStateOf(false)
    private var secondaryDisplayId by mutableStateOf<Int?>(null)

    private var inputManager: InputManager? = null
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshControllerStatus()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshControllerStatus()
        override fun onInputDeviceChanged(deviceId: Int) = refreshControllerStatus()
    }

    private fun buildMenuActions(): List<MenuAction> {
        val hardcore = raSettings.hardcore && raSettings.enabled
        val actions = mutableListOf(
            MenuAction("Resume") { closeMenu() },
            MenuAction("Settings") { openInGameSettings() },
        )
        if (!hardcore) {
            actions += MenuAction("Save state") {
                saveState(0)
                statusText = "State saved (slot 0)"
            }
            actions += MenuAction("Load state") {
                statusText = if (loadState(0)) "State loaded (slot 0)" else "No save in slot 0"
            }
        } else {
            actions += MenuAction("Save state (hardcore off)") {
                statusText = "Hardcore mode — save states disabled"
            }
        }
        actions += MenuAction("Reset") {
            LibretroNative.nativeReset()
            raSession?.onEmulatorReset()
            statusText = "Reset"
            closeMenu()
        }
        if (xoraSettings.netplayEnabled) {
            actions += MenuAction("Netplay: Host") {
                netplayStatus =
                    "Hosting as ${xoraSettings.netplayNickname} on port ${xoraSettings.netplayPort}" +
                        if (xoraSettings.netplayUseRelay) " (relay)" else ""
                statusText = netplayStatus ?: ""
            }
            actions += MenuAction("Netplay: Join") {
                val host = xoraSettings.netplayHostAddress.ifBlank { "…" }
                netplayStatus =
                    "Join ${xoraSettings.netplayNickname} → $host:${xoraSettings.netplayPort}" +
                        if (xoraSettings.netplaySpectator) " (spectator)" else ""
                statusText = netplayStatus ?: ""
            }
        }
        actions += MenuAction("Quit to XOrA") { finish() }
        return actions
    }

    private fun openInGameSettings() {
        settingsOpen = true
        focusedMenuIndex = 0
        statusText = ""
    }

    private fun closeInGameSettings() {
        settingsOpen = false
        focusedMenuIndex = 0
    }

    private fun refreshExpandTopology() {
        val secondary = DisplayTopologyMonitor(this).current().secondary?.displayId
        secondaryDisplayId = secondary
        expandActive = xoraSettings.expandDualDisplay &&
            platformId in DUAL_SCREEN_PLATFORMS &&
            secondary != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reinforce theme animations (some OEMs ignore windowAnimationStyle on NEW_TASK).
        @Suppress("DEPRECATION")
        overridePendingTransition(
            com.arcadia.shell.libretro.R.anim.xora_fade_in,
            com.arcadia.shell.libretro.R.anim.xora_hold,
        )
        enableEdgeToEdge()
        ImmersiveMode.apply(window)

        val romPath = intent.getStringExtra(XoraLibretroPlayers.EXTRA_ROM_PATH)
        coreName = intent.getStringExtra(XoraLibretroPlayers.EXTRA_CORE_NAME).orEmpty()
        platformId = intent.getStringExtra(XoraLibretroPlayers.EXTRA_PLATFORM_ID) ?: "unknown"
        gameId = intent.getStringExtra(XoraLibretroPlayers.EXTRA_GAME_ID) ?: File(romPath.orEmpty()).name
        gameTitle = intent.getStringExtra(XoraLibretroPlayers.EXTRA_GAME_TITLE) ?: gameId
        val corePathExtra = intent.getStringExtra(XoraLibretroPlayers.EXTRA_CORE_PATH)

        // Ensure gamepad events are delivered to this activity even under Compose focus.
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()

        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)
        refreshControllerStatus()

        setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(
                initialValue = ShellSettings(),
            )
            val xora by preferences.xoraEmulatorSettings.collectAsStateWithLifecycle(
                initialValue = XoraEmulatorSettings(),
            )
            val raPrefs by preferences.retroAchievementsSettings.collectAsStateWithLifecycle(
                initialValue = RetroAchievementsSettings(),
            )
            LaunchedEffect(xora) {
                xoraSettings = xora
                refreshExpandTopology()
            }
            LaunchedEffect(raPrefs) { raSettings = raPrefs }
            val darkTheme = settings.themeMode.resolveDarkTheme(systemDark = true)
            val textScale = settings.uiTextScale
            val shellThemeId = settings.shellThemeId
            val menuActions = remember(
                xora.netplayEnabled,
                xora.netplayPort,
                xora.netplayNickname,
                raPrefs.hardcore,
                raPrefs.enabled,
            ) {
                buildMenuActions()
            }

            ArcadiaTheme(
                darkTheme = darkTheme,
                shellThemeId = shellThemeId,
                uiTextScale = textScale,
            ) {
                Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    // Live frames (hidden while pause menu shows a freeze-frame plate).
                    if (!menuOpen) {
                        val bmp = gameBitmap
                        if (bmp != null) {
                            XoraPrimaryGameFrame(
                                bitmap = bmp,
                                frameTick = frameTick,
                                platformId = platformId,
                                aspectMode = xora.aspectMode,
                                integerScale = xora.integerScale,
                                bezelsEnabled = xora.bezelsEnabled && !expandActive,
                                bezelOpacity = xora.bezelOpacity,
                            )
                        } else if (xora.bezelsEnabled && !expandActive) {
                            XoraBezelBackdrop(
                                platformId = platformId,
                                opacity = xora.bezelOpacity * 0.7f,
                            )
                        }
                    }

                    if (expandActive) {
                        SecondaryDisplayPane(displayId = secondaryDisplayId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bottom = bottomBitmap
                                if (bottom != null) {
                                    XoraPrimaryGameFrame(
                                        bitmap = bottom,
                                        frameTick = frameTick,
                                        platformId = platformId,
                                        aspectMode = xora.aspectMode,
                                        integerScale = xora.integerScale,
                                        bezelsEnabled = false,
                                        bezelOpacity = xora.bezelOpacity,
                                    )
                                }
                            }
                        }
                    }

                    if (menuOpen) {
                        freezeFrame?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color.Black),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
                                        ),
                                    ),
                                ),
                        )
                        if (settingsOpen) {
                            XoraEmulatorSettingsPanel(
                                xora = xora,
                                ra = raPrefs,
                                platformId = platformId,
                                dualDisplayAvailable = secondaryDisplayId != null,
                                focusedIndex = focusedMenuIndex,
                                onFocus = { focusedMenuIndex = it },
                                onBack = { closeInGameSettings() },
                                onToggleExpand = {
                                    lifecycleScope.launch {
                                        preferences.setXoraExpandDualDisplay(!xora.expandDualDisplay)
                                        refreshExpandTopology()
                                    }
                                },
                                onCycleAspect = {
                                    lifecycleScope.launch {
                                        val next = when (xora.aspectMode) {
                                            XoraAspectMode.Core -> XoraAspectMode.Integer
                                            XoraAspectMode.Integer -> XoraAspectMode.Stretch
                                            XoraAspectMode.Stretch -> XoraAspectMode.Core
                                        }
                                        preferences.setXoraAspectMode(next)
                                    }
                                },
                                onToggleBezels = {
                                    lifecycleScope.launch {
                                        preferences.setXoraBezelsEnabled(!xora.bezelsEnabled)
                                    }
                                },
                                onCycleInternalRes = {
                                    lifecycleScope.launch {
                                        val values = com.arcadia.shell.datastore.XoraInternalResolution.entries
                                        val i = values.indexOf(xora.internalResolution)
                                        val next = values[(i + 1) % values.size]
                                        preferences.setXoraInternalResolution(next)
                                        applyCoreOptionsLive(xora.copy(internalResolution = next))
                                    }
                                },
                                onCycleNdsLayout = {
                                    lifecycleScope.launch {
                                        val values = com.arcadia.shell.datastore.DualScreenLayout.entries
                                        val i = values.indexOf(xora.ndsScreenLayout)
                                        val next = values[(i + 1) % values.size]
                                        preferences.setXoraNdsScreenLayout(next)
                                        applyCoreOptionsLive(xora.copy(ndsScreenLayout = next))
                                    }
                                },
                                onCycle3dsLayout = {
                                    lifecycleScope.launch {
                                        val values = com.arcadia.shell.datastore.ThreeDsScreenLayout.entries
                                        val i = values.indexOf(xora.threeDsScreenLayout)
                                        val next = values[(i + 1) % values.size]
                                        preferences.setXora3dsScreenLayout(next)
                                        applyCoreOptionsLive(xora.copy(threeDsScreenLayout = next))
                                    }
                                },
                                onToggleRa = {
                                    lifecycleScope.launch {
                                        preferences.setRaEnabled(!raPrefs.enabled)
                                    }
                                },
                                onToggleHardcore = {
                                    lifecycleScope.launch {
                                        val next = !raPrefs.hardcore
                                        preferences.setRaHardcore(next)
                                        LibretroNative.nativeRaSetHardcore(next)
                                    }
                                },
                                onToggleNetplay = {
                                    lifecycleScope.launch {
                                        preferences.setXoraNetplayEnabled(!xora.netplayEnabled)
                                    }
                                },
                            )
                            BackHandler { closeInGameSettings() }
                        } else {
                            XoraEmulatorPauseMenu(
                                title = gameTitle,
                                subtitle = coreName.ifBlank { "XOrA Emulator" },
                                status = statusText,
                                raStatus = raStatusText,
                                controllerStatus = listOfNotNull(controllerStatus, netplayStatus)
                                    .joinToString(" · ")
                                    .ifBlank { null },
                                actions = menuActions.map { it.label },
                                focusedIndex = focusedMenuIndex,
                                onFocus = { focusedMenuIndex = it },
                                onActivate = { index -> menuActions.getOrNull(index)?.onClick?.invoke() },
                                onDismiss = { closeMenu() },
                            )
                            BackHandler { closeMenu() }
                        }
                    } else if (statusText.isNotBlank() && runJob == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = statusText,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier
                                    .liquidGlass(
                                        shape = ArcadiaGlass.PanelShape,
                                        tone = GlassTone.OverMedia,
                                        intensity = GlassIntensity.Standard,
                                    )
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    } else {
                        controllerStatus?.let { status ->
                            Text(
                                text = status,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                            )
                        }
                    }

                    XoraBootOverlay(
                        visible = bootOverlayVisible,
                        title = gameTitle,
                        subtitle = if (statusText.isNotBlank()) statusText else "XOrA Emulator",
                    )

                    NotificationBannerHost(center = shellNotifications)
                }
            }
        }

        if (romPath.isNullOrBlank() || coreName.isBlank()) {
            statusText = "Missing ROM or core"
            bootOverlayVisible = false
            return
        }

        lifecycleScope.launch {
            statusText = "Preparing $coreName…"
            val path = corePathExtra?.takeIf { File(it).isFile }
                ?: withContext(Dispatchers.IO) { coreDownloader.ensureCore(coreName) }
            if (path == null) {
                statusText = "Could not install core '$coreName'. Check network / Settings → XOrA Emulator."
                bootOverlayVisible = false
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                val xora = preferences.xoraEmulatorSettings.first()
                val expand = xora.expandDualDisplay &&
                    platformId in DUAL_SCREEN_PLATFORMS &&
                    DisplayTopologyMonitor(this@XoraLibretroActivity).current().secondary != null
                LibretroNative.nativeClearCoreVariables()
                LibretroNative.nativeSetNetplayUsername(xora.netplayNickname)
                XoraCoreOptions.variablesFor(
                    platformId = platformId,
                    coreName = coreName,
                    settings = xora,
                    expandActive = expand,
                ).forEach { (key, value) ->
                    LibretroNative.nativeSetCoreVariable(key, value)
                }
                LibretroNative.nativeLoadCore(
                    path,
                    coreStore.systemDir.absolutePath,
                    coreStore.saveDirFor(platformId).absolutePath,
                ) && LibretroNative.nativeLoadGame(romPath)
            }
            if (!ok) {
                statusText = LibretroNative.nativeLastError() ?: "Failed to load game"
                bootOverlayVisible = false
                return@launch
            }
            gameLoaded = true
            val raPrefs = preferences.retroAchievementsSettings.first()
            raSettings = raPrefs
            // Resume after an accidental home/recents swipe (softcore only — hardcore forbids it).
            val hardcore = raPrefs.hardcore && raPrefs.enabled
            val restored = if (!hardcore) {
                withContext(Dispatchers.IO) { loadAutosave() }
            } else {
                false
            }
            refreshExpandTopology()
            statusText = when {
                restored -> "Resumed previous session"
                expandActive -> "Expanded · top primary / bottom secondary"
                else -> ""
            }
            startRaSession(romPath)
            startAudio()
            startLoop()
            refreshControllerStatus()
        }
    }

    private fun refreshControllerStatus() {
        val names = LibretroPad.connectedControllerNames()
        controllerStatus = when {
            names.isEmpty() -> "No controller detected — connect a gamepad"
            names.size == 1 -> "Controller: ${names[0]}"
            else -> "Controllers: ${names.joinToString(", ")}"
        }
    }

    override fun onResume() {
        super.onResume()
        activityInBackground = false
        ImmersiveMode.apply(window)
        uiSounds.onForeground()
        window.decorView.requestFocus()
        refreshControllerStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ImmersiveMode.apply(window)
    }

    override fun onPause() {
        // Persist progress before Android may kill the process after a home / recents swipe.
        if (!isChangingConfigurations) {
            activityInBackground = true
            persistSessionForBackground()
        }
        uiSounds.onBackground()
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            activityInBackground = true
            persistSessionForBackground()
        }
        super.onStop()
    }

    /** Intercept at dispatch so Compose focus cannot swallow gamepad KeyEvents. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handlePadKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (handlePadMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun handlePadKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0 && keyCode !in chordKeys) {
                    return LibretroPad.run { event.isFromGameController() } ||
                        LibretroPad.keyCodeToButton(keyCode) != null
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SPACE -> selectHeld = true
                    KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> startHeld = true
                    KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU -> {
                        toggleMenu()
                        return true
                    }
                }
                if (selectHeld && startHeld) {
                    toggleMenu()
                    return true
                }
                if (menuOpen) {
                    if (settingsOpen) {
                        val settingsCount = IN_GAME_SETTINGS_ROW_COUNT
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusedMenuIndex =
                                    (focusedMenuIndex - 1 + settingsCount) % settingsCount
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusedMenuIndex = (focusedMenuIndex + 1) % settingsCount
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                            -> {
                                activateInGameSetting(focusedMenuIndex)
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                                closeInGameSettings()
                                return true
                            }
                        }
                        return LibretroPad.run { event.isFromGameController() }
                    }
                    val actions = buildMenuActions()
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusedMenuIndex =
                                (focusedMenuIndex - 1 + actions.size) % actions.size
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedMenuIndex = (focusedMenuIndex + 1) % actions.size
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                        -> {
                            actions.getOrNull(focusedMenuIndex)?.onClick?.invoke()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                            closeMenu()
                            return true
                        }
                    }
                    return LibretroPad.run { event.isFromGameController() }
                }
                LibretroPad.keyCodeToButton(keyCode)?.let { bit ->
                    keyPadButtons.updateAndGet { it or (1 shl bit) }
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    toggleMenu()
                    return true
                }
            }
            KeyEvent.ACTION_UP -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SPACE -> selectHeld = false
                    KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> startHeld = false
                }
                if (menuOpen) {
                    return LibretroPad.run { event.isFromGameController() } ||
                        LibretroPad.keyCodeToButton(keyCode) != null
                }
                LibretroPad.keyCodeToButton(keyCode)?.let { bit ->
                    keyPadButtons.updateAndGet { it and (1 shl bit).inv() }
                    return true
                }
            }
        }
        return false
    }

    private fun handlePadMotion(event: MotionEvent): Boolean {
        if (menuOpen) return false
        if (!LibretroPad.run { event.isFromGameController() }) return false
        val (left, right) = LibretroPad.readAxes(event)
        axisLx = left.first
        axisLy = left.second
        axisRx = right.first
        axisRy = right.second
        axisPadButtons.set(LibretroPad.digitalPadFromAxes(event))
        return true
    }

    private fun applyCoreOptionsLive(settings: XoraEmulatorSettings) {
        refreshExpandTopology()
        XoraCoreOptions.variablesFor(
            platformId = platformId,
            coreName = coreName,
            settings = settings,
            expandActive = expandActive,
        ).forEach { (key, value) ->
            LibretroNative.nativeSetCoreVariable(key, value)
        }
    }

    private fun activateInGameSetting(index: Int) {
        val xora = xoraSettings
        val ra = raSettings
        when (index) {
            0 -> closeInGameSettings()
            1 -> if (platformId == "nds" || platformId == "3ds") {
                lifecycleScope.launch {
                    preferences.setXoraExpandDualDisplay(!xora.expandDualDisplay)
                    refreshExpandTopology()
                }
            }
            2 -> lifecycleScope.launch {
                val next = when (xora.aspectMode) {
                    XoraAspectMode.Core -> XoraAspectMode.Integer
                    XoraAspectMode.Integer -> XoraAspectMode.Stretch
                    XoraAspectMode.Stretch -> XoraAspectMode.Core
                }
                preferences.setXoraAspectMode(next)
            }
            3 -> lifecycleScope.launch {
                preferences.setXoraBezelsEnabled(!xora.bezelsEnabled)
            }
            4 -> when (platformId) {
                "nds" -> lifecycleScope.launch {
                    val values = com.arcadia.shell.datastore.DualScreenLayout.entries
                    val next = values[(values.indexOf(xora.ndsScreenLayout) + 1) % values.size]
                    preferences.setXoraNdsScreenLayout(next)
                    applyCoreOptionsLive(xora.copy(ndsScreenLayout = next))
                }
                "3ds" -> lifecycleScope.launch {
                    val values = com.arcadia.shell.datastore.ThreeDsScreenLayout.entries
                    val next = values[(values.indexOf(xora.threeDsScreenLayout) + 1) % values.size]
                    preferences.setXora3dsScreenLayout(next)
                    applyCoreOptionsLive(xora.copy(threeDsScreenLayout = next))
                }
                else -> lifecycleScope.launch {
                    val values = com.arcadia.shell.datastore.XoraInternalResolution.entries
                    val next = values[(values.indexOf(xora.internalResolution) + 1) % values.size]
                    preferences.setXoraInternalResolution(next)
                    applyCoreOptionsLive(xora.copy(internalResolution = next))
                }
            }
            5 -> lifecycleScope.launch {
                preferences.setRaEnabled(!ra.enabled)
            }
            6 -> lifecycleScope.launch {
                val next = !ra.hardcore
                preferences.setRaHardcore(next)
                LibretroNative.nativeRaSetHardcore(next)
            }
            7 -> lifecycleScope.launch {
                preferences.setXoraNetplayEnabled(!xora.netplayEnabled)
            }
        }
    }

    private fun startRaSession(romPath: String) {
        lifecycleScope.launch {
            val prefs = preferences.retroAchievementsSettings.first()
            raSettings = prefs
            val session = LibretroRaSession(
                scope = lifecycleScope,
                okHttpClient = okHttpClient,
                retroAchievements = retroAchievements,
                romHasher = romHasher,
                libraryRepository = libraryRepository,
                notifications = shellNotifications,
                gameTitle = gameTitle,
                raSettings = prefs,
            )
            raSession = session
            launch { session.status.collect { raStatusText = it } }
            session.start(romPath = romPath, platformId = platformId, gameId = gameId)
        }
    }

    private fun persistSessionForBackground() {
        if (!gameLoaded) return
        val hardcore = raSettings.hardcore && raSettings.enabled
        if (hardcore) return
        runCatching { saveAutosave() }
    }

    private fun saveAutosave() {
        val data = LibretroNative.nativeSerialize() ?: return
        coreStore.autosaveFile(platformId, gameId).writeBytes(data)
    }

    private fun loadAutosave(): Boolean {
        val file = coreStore.autosaveFile(platformId, gameId)
        if (!file.isFile || file.length() == 0L) return false
        return LibretroNative.nativeUnserialize(file.readBytes())
    }

    private fun openMenu() {
        freezeFrame = synchronized(bitmapLock) {
            gameBitmap?.let { src -> src.copy(Bitmap.Config.ARGB_8888, false) }
        }
        focusedMenuIndex = 0
        settingsOpen = false
        menuOpen = true
        paused = true
    }

    private fun closeMenu() {
        menuOpen = false
        settingsOpen = false
        paused = false
        freezeFrame = null
        statusText = ""
        window.decorView.requestFocus()
    }

    private fun startAudio() {
        val sampleRate = LibretroNative.nativeGetSampleRate().toInt().coerceIn(8000, 96000)
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }

    private fun startLoop() {
        runJob?.cancel()
        runJob = lifecycleScope.launch(Dispatchers.Default) {
            val fps = LibretroNative.nativeGetFps().coerceIn(30.0, 120.0)
            val frameNs = (1_000_000_000.0 / fps).toLong()
            while (coroutineContext.isActive) {
                val start = System.nanoTime()
                if (!paused && !menuOpen && !activityInBackground) {
                    LibretroNative.nativeSetPadState(
                        keyPadButtons.get() or axisPadButtons.get(),
                        axisLx,
                        axisLy,
                        axisRx,
                        axisRy,
                    )
                    LibretroNative.nativeRunFrame()
                    raSession?.doFrame()
                    LibretroNative.nativeCopyFrameRgba()?.let { packed ->
                        presentFrame(packed)
                    }
                    LibretroNative.nativeDrainAudio()?.let { pcm ->
                        audioTrack?.write(pcm, 0, pcm.size)
                    }
                } else {
                    raSession?.idle()
                }
                val elapsed = System.nanoTime() - start
                val sleepMs = ((frameNs - elapsed) / 1_000_000L).coerceAtLeast(0L)
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
        }
    }

    private fun presentFrame(packed: IntArray) {
        if (packed.size < 2) return
        val w = packed[0]
        val h = packed[1]
        if (w <= 0 || h <= 0 || packed.size < w * h + 2) return
        val pixels = packed.copyOfRange(2, 2 + w * h)
        val split = expandActive && h >= 2
        runOnUiThread {
            synchronized(bitmapLock) {
                if (split) {
                    val topH = h / 2
                    val bottomH = h - topH
                    var top = gameBitmap
                    if (top == null || top.width != w || top.height != topH || top.isRecycled) {
                        top?.recycle()
                        top = Bitmap.createBitmap(w, topH, Bitmap.Config.ARGB_8888)
                        gameBitmap = top
                    }
                    top.setPixels(pixels, 0, w, 0, 0, w, topH)

                    var bottom = bottomBitmap
                    if (bottom == null || bottom.width != w || bottom.height != bottomH ||
                        bottom.isRecycled
                    ) {
                        bottom?.recycle()
                        bottom = Bitmap.createBitmap(w, bottomH, Bitmap.Config.ARGB_8888)
                        bottomBitmap = bottom
                    }
                    bottom.setPixels(pixels, topH * w, w, 0, 0, w, bottomH)
                } else {
                    bottomBitmap?.recycle()
                    bottomBitmap = null
                    var bmp = gameBitmap
                    if (bmp == null || bmp.width != w || bmp.height != h || bmp.isRecycled) {
                        bmp?.recycle()
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        gameBitmap = bmp
                    }
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                }
            }
            frameTick++
            if (bootOverlayVisible) bootOverlayVisible = false
        }
    }

    override fun finish() {
        // Explicit quit — drop the autosave so the next launch is a fresh boot.
        runCatching { coreStore.autosaveFile(platformId, gameId).delete() }
        gameLoaded = false
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            com.arcadia.shell.libretro.R.anim.xora_hold,
            com.arcadia.shell.libretro.R.anim.xora_fade_out,
        )
    }

    private fun saveState(slot: Int) {
        val data = LibretroNative.nativeSerialize() ?: return
        coreStore.stateFile(platformId, gameId, slot).writeBytes(data)
    }

    private fun loadState(slot: Int): Boolean {
        val file = coreStore.stateFile(platformId, gameId, slot)
        if (!file.isFile) return false
        return LibretroNative.nativeUnserialize(file.readBytes())
    }

    private fun toggleMenu() {
        if (menuOpen) closeMenu() else openMenu()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        toggleMenu()
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        inputManager = null
        runJob?.cancel()
        runJob = null
        raSession?.stop()
        raSession = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        freezeFrame?.recycle()
        freezeFrame = null
        synchronized(bitmapLock) {
            gameBitmap?.recycle()
            gameBitmap = null
            bottomBitmap?.recycle()
            bottomBitmap = null
        }
        LibretroNative.nativeUnload()
        super.onDestroy()
    }

    companion object {
        private val chordKeys = setOf(
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        )
        private val DUAL_SCREEN_PLATFORMS = setOf("nds", "3ds")
        private const val IN_GAME_SETTINGS_ROW_COUNT = 8
    }
}

private data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun XoraPrimaryGameFrame(
    bitmap: Bitmap,
    frameTick: Int,
    platformId: String,
    aspectMode: XoraAspectMode,
    integerScale: Int,
    bezelsEnabled: Boolean,
    bezelOpacity: Float,
) {
    val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    val frame: @Composable () -> Unit = {
        XoraScaledGameFrame(
            contentWidthPx = bitmap.width,
            contentHeightPx = bitmap.height,
            mode = aspectMode,
            integerScaleCap = integerScale,
            modifier = Modifier.fillMaxSize(),
        ) { scale ->
            key(frameTick) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = scale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    if (bezelsEnabled && aspectMode != XoraAspectMode.Stretch) {
        XoraSystemBezel(
            platformId = platformId,
            opacity = bezelOpacity,
            contentAspect = aspect,
        ) { frame() }
    } else {
        frame()
    }
}

@Composable
private fun XoraBootOverlay(
    visible: Boolean,
    title: String,
    subtitle: String,
) {
    var show by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            show = true
        } else {
            delay(ArcadiaMotion.Medium.toLong())
            show = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = ArcadiaMotion.Medium),
        label = "xora-boot-fade",
    )
    if (!show && alpha <= 0.01f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF0A0C10),
                        androidx.compose.ui.graphics.Color(0xFF12161E),
                        androidx.compose.ui.graphics.Color(0xFF0A0C10),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(32.dp)
                .liquidGlass(
                    shape = ArcadiaGlass.PanelShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Subtle,
                )
                .padding(horizontal = 28.dp, vertical = 22.dp),
        ) {
            Text(
                text = "XOrA",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun XoraEmulatorPauseMenu(
    title: String,
    subtitle: String,
    status: String,
    raStatus: String?,
    controllerStatus: String?,
    actions: List<String>,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(start = 48.dp, end = 48.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.42f),
        ) {
            val panelMax = maxHeight * 0.88f
            Column(
                modifier = Modifier
                    .heightIn(max = panelMax)
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            XoraTitleText(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                maxLines = 2,
            )
            XoraSecondaryText(
                text = subtitle,
                fontSize = 12.sp,
                fillColor = glass.contentMuted,
                maxLines = 1,
            )
            if (status.isNotBlank()) {
                XoraSecondaryText(
                    text = status,
                    fontSize = 12.sp,
                    fillColor = glass.contentMuted,
                    maxLines = 2,
                )
            }
            if (!raStatus.isNullOrBlank()) {
                XoraSecondaryText(
                    text = raStatus,
                    fontSize = 12.sp,
                    fillColor = glass.contentMuted,
                    maxLines = 2,
                )
            }
            if (!controllerStatus.isNullOrBlank()) {
                XoraSecondaryText(
                    text = controllerStatus,
                    fontSize = 12.sp,
                    fillColor = glass.contentMuted,
                    maxLines = 2,
                )
            }
            actions.forEachIndexed { index, label ->
                val focused = index == focusedIndex
                Text(
                    text = label,
                    color = if (focused) glass.content else glass.contentMuted,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = if (focused) 18.sp else 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (focused) {
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            onFocus(index)
                            onActivate(index)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            XoraSecondaryText(
                text = "Settings · Start+Select / Back · A confirms",
                fontSize = 11.sp,
                fillColor = glass.contentMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            }
        }
    }
}

@Composable
private fun XoraEmulatorSettingsPanel(
    xora: XoraEmulatorSettings,
    ra: RetroAchievementsSettings,
    platformId: String,
    dualDisplayAvailable: Boolean,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onBack: () -> Unit,
    onToggleExpand: () -> Unit,
    onCycleAspect: () -> Unit,
    onToggleBezels: () -> Unit,
    onCycleInternalRes: () -> Unit,
    onCycleNdsLayout: () -> Unit,
    onCycle3dsLayout: () -> Unit,
    onToggleRa: () -> Unit,
    onToggleHardcore: () -> Unit,
    onToggleNetplay: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val layoutLabel = when (platformId) {
        "nds" -> "DS layout · ${xora.ndsScreenLayout.label()}"
        "3ds" -> "3DS layout · ${xora.threeDsScreenLayout.label()}"
        else -> "Internal res · ${xora.internalResolution.label()}"
    }
    val rows = listOf(
        "Back" to "Return to pause menu",
        "Expand dual display" to when {
            platformId !in setOf("nds", "3ds") -> "DS / 3DS only"
            !dualDisplayAvailable -> "No secondary display"
            xora.expandDualDisplay -> "On · top / bottom panels"
            else -> "Off"
        },
        "Aspect ratio" to xora.aspectMode.label(),
        "System bezels" to if (xora.bezelsEnabled) "On" else "Off",
        layoutLabel to "A cycles",
        "RetroAchievements" to if (ra.enabled) "On" else "Off",
        "Hardcore mode" to if (ra.hardcore) "On · no save states" else "Off",
        "Netplay menu" to if (xora.netplayEnabled) "On" else "Off",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(start = 48.dp, end = 48.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.48f),
        ) {
            val panelMax = maxHeight * 0.88f
            Column(
                modifier = Modifier
                    .heightIn(max = panelMax)
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            XoraTitleText(
                text = "XOrA Emulator Settings",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                maxLines = 1,
            )
            XoraSecondaryText(
                text = "Changes apply immediately · B / Back returns",
                fontSize = 12.sp,
                fillColor = glass.contentMuted,
                maxLines = 1,
            )
            rows.forEachIndexed { index, (title, subtitle) ->
                val focused = index == focusedIndex
                val activate: () -> Unit = {
                    onFocus(index)
                    when (index) {
                        0 -> onBack()
                        1 -> onToggleExpand()
                        2 -> onCycleAspect()
                        3 -> onToggleBezels()
                        4 -> when (platformId) {
                            "nds" -> onCycleNdsLayout()
                            "3ds" -> onCycle3dsLayout()
                            else -> onCycleInternalRes()
                        }
                        5 -> onToggleRa()
                        6 -> onToggleHardcore()
                        7 -> onToggleNetplay()
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (focused) {
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(onClick = activate)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = title,
                        color = if (focused) glass.content else glass.contentMuted,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (focused) 17.sp else 14.sp,
                    )
                    Text(
                        text = subtitle,
                        color = glass.contentMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            }
        }
    }
}
