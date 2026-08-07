package com.arcadia.shell.libretro

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arcadia.shell.audio.UiSoundController
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.XoraInternalResolution
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.display.ImmersiveMode
import com.arcadia.shell.display.SecondaryDisplayPane
import com.arcadia.shell.feature.home.LocalInGameXmbController
import com.arcadia.shell.feature.home.XoraEmulatorXmbSetting
import com.arcadia.shell.feature.home.XoraInGameXmbController
import com.arcadia.shell.feature.home.XoraInGameXmbOverlay
import com.arcadia.shell.feature.home.XoraXmbAction
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scraper.RomHasher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * In-process Libretro session.
 *
 * Framebuffer lives on an [ImageView]. Back / PS-style chord opens the launcher XMB over a
 * blurred frozen frame (ImageView hidden while the overlay is up so Resume cannot leave a wash).
 * The XOrA Emulator XMB row is only available in that in-session overlay.
 *
 * All Libretro core entry points run on a dedicated single OS thread.
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
    @Inject lateinit var saveFileImporter: SaveFileImporter

    @Volatile private var menuOpen = false
    /** True while the in-game XMB is showing — pauses the frame loop. */
    @Volatile private var paused = false
    /** True while the activity is backgrounded (home/recents) — pauses the frame loop. */
    @Volatile private var activityInBackground = false
    private var gameLoaded = false
    private var raSession: LibretroRaSession? = null

    private var runJob: Job? = null
    private var audioTrack: AudioTrack? = null
    /** Live gameplay bitmap presented via ImageView (updated in place + invalidate). */
    private var gameBitmap: Bitmap? = null
    private var bottomBitmap by mutableStateOf<Bitmap?>(null)
    private var frameTick by mutableIntStateOf(0)
    private var primaryGameView: ImageView? = null
    private var secondaryGameView: ImageView? = null
    private var gameRoot: FrameLayout? = null
    private var xmbOverlay: ComposeView? = null
    private var frozenMenuFrame by mutableStateOf<Bitmap?>(null)
    private var profileName by mutableStateOf("Player")
    private val inGameXmbController = XoraInGameXmbController()
    private val bitmapLock = Any()

    /**
     * Dedicated emu thread for every Libretro JNI call (load / run / serialize / unload).
     * Must not be [Dispatchers.IO] or [Dispatchers.Default] — those hop OS threads and break libco.
     */
    private val emuExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xora-libretro").apply { isDaemon = true }
    }
    private val emuDispatcher = emuExecutor.asCoroutineDispatcher()

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
    private var xoraSettings = XoraEmulatorSettings()
    private var raSettings = RetroAchievementsSettings()
    private var expandActive by mutableStateOf(false)
    private var secondaryDisplayId by mutableStateOf<Int?>(null)

    private var inputManager: InputManager? = null
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) = Unit
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
        // Force dark system-bar scrims. The default light nav scrim (near-white) can wash the
        // letterboxed game surface after edge-to-edge overlays are shown/dismissed.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
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

        window.setFormat(PixelFormat.OPAQUE)
        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        gameRoot = root
        val gameView = ImageView(this).apply {
            setBackgroundColor(AndroidColor.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        primaryGameView = gameView
        root.addView(gameView)

        // Wrap-content host for RA unlock banners + secondary display + preference effects.
        val banners = ComposeView(this).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_NONE, null)
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        root.addView(banners)

        val xmb = ComposeView(this).apply {
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(View.LAYER_TYPE_NONE, null)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.GONE
        }
        xmbOverlay = xmb
        // Not attached until Back opens the in-game XMB.

        setContentView(root)
        lifecycleScope.launch {
            profileName = preferences.profile.first().displayName
        }

        banners.setContent {
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
                primaryGameView?.scaleType = when (xora.aspectMode) {
                    XoraAspectMode.Stretch -> ImageView.ScaleType.FIT_XY
                    XoraAspectMode.Integer,
                    XoraAspectMode.Core,
                    -> ImageView.ScaleType.FIT_CENTER
                }
            }
            LaunchedEffect(raPrefs) { raSettings = raPrefs }

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
                            XoraGameImageView(
                                bitmap = bottom,
                                frameTick = frameTick,
                                aspectMode = xora.aspectMode,
                                onImageView = { secondaryGameView = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            ArcadiaTheme(
                darkTheme = true,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
            ) {
                Box(modifier = Modifier.wrapContentSize(align = Alignment.TopStart)) {
                    NotificationBannerHost(center = shellNotifications)
                }
            }
        }

        xmb.setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(
                initialValue = ShellSettings(),
            )
            val xora by preferences.xoraEmulatorSettings.collectAsStateWithLifecycle(
                initialValue = XoraEmulatorSettings(),
            )
            val raPrefs by preferences.retroAchievementsSettings.collectAsStateWithLifecycle(
                initialValue = RetroAchievementsSettings(),
            )
            ArcadiaTheme(
                darkTheme = true,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
            ) {
                CompositionLocalProvider(LocalInGameXmbController provides inGameXmbController) {
                    XoraInGameXmbOverlay(
                        frozenFrame = frozenMenuFrame,
                        gameTitle = gameTitle,
                        profileName = profileName,
                        emulatorSettings = xora,
                        raHardcore = raPrefs.hardcore,
                        onAction = { handleInGameXmbAction(it) },
                        onDismiss = { closeMenu() },
                    )
                }
            }
        }

        if (romPath.isNullOrBlank() || coreName.isBlank()) {
            Toast.makeText(this, "Missing ROM or core", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val path = corePathExtra?.takeIf { File(it).isFile }
                ?: withContext(Dispatchers.IO) { coreDownloader.ensureCore(coreName) }
            if (path == null) {
                Toast.makeText(
                    this@XoraLibretroActivity,
                    "Could not install core '$coreName'. Check network / Settings → XOrA Emulator.",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val saveImport = withContext(Dispatchers.IO) {
                saveFileImporter.importForGame(platformId, romPath)
            }
            // Core init + first frames must share one OS thread (Mupen/libco).
            val ok = withContext(emuDispatcher) {
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
                val err = withContext(emuDispatcher) {
                    LibretroNative.nativeLastError()
                } ?: "Failed to load game"
                Toast.makeText(this@XoraLibretroActivity, err, Toast.LENGTH_LONG).show()
                return@launch
            }
            gameLoaded = true
            val raPrefs = preferences.retroAchievementsSettings.first()
            raSettings = raPrefs
            // Resume after an accidental home/recents swipe (softcore only — hardcore forbids it).
            val hardcore = raPrefs.hardcore && raPrefs.enabled
            val restored = if (!hardcore) {
                withContext(emuDispatcher) { loadAutosave() }
            } else {
                false
            }
            refreshExpandTopology()
            val bootMsg = when {
                !saveImport.message.isNullOrBlank() && restored ->
                    "${saveImport.message} · Resumed previous session"
                !saveImport.message.isNullOrBlank() -> saveImport.message!!
                restored -> "Resumed previous session"
                expandActive -> "Expanded · top primary / bottom secondary"
                else -> null
            }
            if (!bootMsg.isNullOrBlank()) {
                Toast.makeText(this@XoraLibretroActivity, bootMsg, Toast.LENGTH_SHORT).show()
            }
            startRaSession(romPath)
            startAudio()
            startLoop()
        }
    }

    override fun onResume() {
        super.onResume()
        activityInBackground = false
        ImmersiveMode.apply(window)
        uiSounds.onForeground()
        window.decorView.requestFocus()
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
        val customMappings = xoraSettings.buttonMappings
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0 && keyCode !in chordKeys) {
                    return LibretroPad.run { event.isFromGameController(customMappings) } ||
                        LibretroPad.keyCodeToButton(keyCode, customMappings) != null
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
                    return handleInGameXmbKey(keyCode)
                }
                if (!LibretroPad.matchesPreferredController(
                        InputDevice.getDevice(event.deviceId),
                        xoraSettings.preferredControllerName,
                    )
                ) {
                    return false
                }
                LibretroPad.keyCodeToButton(keyCode, customMappings)?.let { bit ->
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
                    return LibretroPad.run { event.isFromGameController(customMappings) } ||
                        LibretroPad.keyCodeToButton(keyCode, customMappings) != null ||
                        keyCode == KeyEvent.KEYCODE_BACK ||
                        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                }
                if (!LibretroPad.matchesPreferredController(
                        InputDevice.getDevice(event.deviceId),
                        xoraSettings.preferredControllerName,
                    )
                ) {
                    return false
                }
                LibretroPad.keyCodeToButton(keyCode, customMappings)?.let { bit ->
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
        if (!LibretroPad.matchesPreferredController(
                InputDevice.getDevice(event.deviceId),
                xoraSettings.preferredControllerName,
            )
        ) {
            return false
        }
        val (left, right) = LibretroPad.readAxes(event)
        axisLx = left.first
        axisLy = left.second
        axisRx = right.first
        axisRy = right.second
        axisPadButtons.set(LibretroPad.digitalPadFromAxes(event))
        return true
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
            session.start(romPath = romPath, platformId = platformId, gameId = gameId)
        }
    }

    private fun persistSessionForBackground() {
        if (!gameLoaded) return
        val hardcore = raSettings.hardcore && raSettings.enabled
        if (hardcore) return
        // Must run on the emu thread — Mupen serialize uses libco co_switch.
        runCatching {
            runBlocking(emuDispatcher) { saveAutosave() }
        }
    }

    /** Caller must already be on [emuDispatcher]. */
    private fun saveAutosave() {
        val data = LibretroNative.nativeSerialize() ?: return
        coreStore.autosaveFile(platformId, gameId).writeBytes(data)
    }

    /** Caller must already be on [emuDispatcher]. */
    private fun loadAutosave(): Boolean {
        val file = coreStore.autosaveFile(platformId, gameId)
        if (!file.isFile || file.length() == 0L) return false
        val bytes = file.readBytes()
        val ok = LibretroNative.nativeUnserialize(bytes)
        if (!ok) {
            // Stale / cross-thread autosaves from older builds can poison every launch.
            runCatching { file.delete() }
        }
        return ok
    }

    private fun handleInGameXmbKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                inGameXmbController.moveCategory?.invoke(-1)
                uiSounds.playCursor()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                inGameXmbController.moveCategory?.invoke(1)
                uiSounds.playCursor()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                inGameXmbController.moveItem?.invoke(-1)
                uiSounds.playCursor()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                inGameXmbController.moveItem?.invoke(1)
                uiSounds.playCursor()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                inGameXmbController.confirm?.invoke()
                uiSounds.playConfirm()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                inGameXmbController.cancel?.invoke()
                uiSounds.playCancel()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU -> {
                closeMenu()
                return true
            }
        }
        return true
    }

    private fun openMenu() {
        if (menuOpen || isFinishing) return
        val root = gameRoot ?: return
        val overlay = xmbOverlay ?: return
        // Freeze a copy of the current frame, then hide the live ImageView so the XMB Compose
        // layer is never stacked over a visible framebuffer (tint bug).
        val src = synchronized(bitmapLock) { gameBitmap }
        frozenMenuFrame = src?.takeIf { !it.isRecycled }?.let { runCatching { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }.getOrNull() }
        primaryGameView?.visibility = View.GONE
        if (overlay.parent == null) {
            root.addView(overlay)
        }
        overlay.visibility = View.VISIBLE
        root.bringChildToFront(overlay)
        menuOpen = true
        paused = true
        uiSounds.playConfirm()
    }

    private fun closeMenu() {
        if (!menuOpen && xmbOverlay?.parent == null) return
        menuOpen = false
        paused = false
        val overlay = xmbOverlay
        overlay?.visibility = View.GONE
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        val stale = frozenMenuFrame
        frozenMenuFrame = null
        stale?.recycle()
        primaryGameView?.visibility = View.VISIBLE
        primaryGameView?.invalidate()
        restoreImmersiveFocus()
    }

    private fun handleInGameXmbAction(action: XoraXmbAction) {
        when (action) {
            XoraXmbAction.ResumeGame -> closeMenu()
            XoraXmbAction.QuitGame -> {
                closeMenu()
                finish()
            }
            XoraXmbAction.SaveGameState -> {
                val hardcore = raSettings.hardcore && raSettings.enabled
                if (hardcore) {
                    Toast.makeText(this, "Hardcore mode — save states disabled", Toast.LENGTH_SHORT).show()
                    return
                }
                lifecycleScope.launch(emuDispatcher) {
                    saveState(0)
                    withContext(Dispatchers.Main.immediate) {
                        Toast.makeText(this@XoraLibretroActivity, "State saved (slot 0)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            XoraXmbAction.LoadGameState -> {
                val hardcore = raSettings.hardcore && raSettings.enabled
                if (hardcore) {
                    Toast.makeText(this, "Hardcore mode — load states disabled", Toast.LENGTH_SHORT).show()
                    return
                }
                lifecycleScope.launch(emuDispatcher) {
                    val ok = loadState(0)
                    withContext(Dispatchers.Main.immediate) {
                        Toast.makeText(
                            this@XoraLibretroActivity,
                            if (ok) "State loaded (slot 0)" else "No save in slot 0",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (ok) closeMenu()
                    }
                }
            }
            XoraXmbAction.ResetGame -> {
                lifecycleScope.launch(emuDispatcher) {
                    LibretroNative.nativeReset()
                    withContext(Dispatchers.Main.immediate) {
                        raSession?.onEmulatorReset()
                        Toast.makeText(this@XoraLibretroActivity, "Reset", Toast.LENGTH_SHORT).show()
                        closeMenu()
                    }
                }
            }
            is XoraXmbAction.ToggleXoraEmulatorSetting -> toggleInGameEmulatorSetting(action.setting)
            XoraXmbAction.OpenFullXoraEmulatorSetup ->
                Toast.makeText(
                    this,
                    "Quit to XOrA → Games → Full Setup for cores & storage",
                    Toast.LENGTH_SHORT,
                ).show()
            is XoraXmbAction.OpenSettingsCategory ->
                Toast.makeText(this, "Quit to XOrA to change launcher settings", Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }

    private fun toggleInGameEmulatorSetting(setting: XoraEmulatorXmbSetting) {
        lifecycleScope.launch {
            val current = preferences.xoraEmulatorSettings.first()
            when (setting) {
                XoraEmulatorXmbSetting.Aspect -> {
                    val next = when (current.aspectMode) {
                        XoraAspectMode.Core -> XoraAspectMode.Integer
                        XoraAspectMode.Integer -> XoraAspectMode.Stretch
                        XoraAspectMode.Stretch -> XoraAspectMode.Core
                    }
                    preferences.setXoraAspectMode(next)
                }
                XoraEmulatorXmbSetting.Bezels ->
                    preferences.setXoraBezelsEnabled(!current.bezelsEnabled)
                XoraEmulatorXmbSetting.BezelOpacity -> {
                    val stepped = ((current.bezelOpacity * 100f).toInt() + 10).let { raw ->
                        if (raw > 100) 40 else raw
                    }
                    preferences.setXoraBezelOpacity(stepped / 100f)
                }
                XoraEmulatorXmbSetting.InternalResolution -> {
                    val values = XoraInternalResolution.entries
                    val i = values.indexOf(current.internalResolution).coerceAtLeast(0)
                    preferences.setXoraInternalResolution(values[(i + 1) % values.size])
                }
                XoraEmulatorXmbSetting.ExpandDualDisplay ->
                    preferences.setXoraExpandDualDisplay(!current.expandDualDisplay)
                XoraEmulatorXmbSetting.PreferredController -> {
                    val names = listOf("") + LibretroPad.connectedControllerNames()
                    val idx = names.indexOf(current.preferredControllerName).let {
                        if (it >= 0) it else 0
                    }
                    preferences.setXoraPreferredControllerName(names[(idx + 1) % names.size])
                }
                XoraEmulatorXmbSetting.ClearButtonMappings -> {
                    preferences.clearXoraButtonMappings()
                    Toast.makeText(this@XoraLibretroActivity, "Custom mappings cleared", Toast.LENGTH_SHORT).show()
                }
                XoraEmulatorXmbSetting.Netplay ->
                    preferences.setXoraNetplayEnabled(!current.netplayEnabled)
                XoraEmulatorXmbSetting.RaHardcore -> {
                    val next = !preferences.retroAchievementsSettings.first().hardcore
                    preferences.setRaHardcore(next)
                    raSettings = raSettings.copy(hardcore = next)
                    // Apply immediately so softcore/hardcore matches the menu without relaunch.
                    LibretroNative.nativeRaSetHardcore(next)
                    Toast.makeText(
                        this@XoraLibretroActivity,
                        if (next) {
                            "Hardcore on — save states disabled"
                        } else {
                            "Hardcore off — softcore"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun restoreImmersiveFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        ImmersiveMode.apply(window)
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
        // Same OS thread as nativeLoadCore — required for Mupen/libco and GLES context affinity.
        runJob = lifecycleScope.launch(emuDispatcher) {
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
                // delay/yield (not Thread.sleep) so serialize / unload can run on this dispatcher.
                if (sleepMs > 0) delay(sleepMs) else yield()
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
        // When replacing bitmaps, leave the previous instance unreycled — ImageView may
        // still be drawing the prior Bitmap until the next bind.
        runOnUiThread {
            synchronized(bitmapLock) {
                if (split) {
                    val topH = h / 2
                    val bottomH = h - topH
                    var top = gameBitmap
                    if (top == null || top.width != w || top.height != topH || top.isRecycled) {
                        top = Bitmap.createBitmap(w, topH, Bitmap.Config.ARGB_8888)
                        gameBitmap = top
                        primaryGameView?.setImageBitmap(top)
                    }
                    top.setPixels(pixels, 0, w, 0, 0, w, topH)

                    var bottom = bottomBitmap
                    if (bottom == null || bottom.width != w || bottom.height != bottomH ||
                        bottom.isRecycled
                    ) {
                        bottom = Bitmap.createBitmap(w, bottomH, Bitmap.Config.ARGB_8888)
                        bottomBitmap = bottom
                        secondaryGameView?.setImageBitmap(bottom)
                    }
                    bottom.setPixels(pixels, topH * w, w, 0, 0, w, bottomH)
                } else {
                    if (bottomBitmap != null) {
                        bottomBitmap = null
                        secondaryGameView?.setImageDrawable(null)
                    }
                    var bmp = gameBitmap
                    if (bmp == null || bmp.width != w || bmp.height != h || bmp.isRecycled) {
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        gameBitmap = bmp
                        primaryGameView?.setImageBitmap(bmp)
                    }
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                }
            }
            primaryGameView?.invalidate()
            secondaryGameView?.invalidate()
            frameTick++
        }
    }

    override fun finish() {
        // Explicit quit — drop the autosave so the next launch is a fresh boot.
        runCatching { coreStore.autosaveFile(platformId, gameId).delete() }
        gameLoaded = false
        closeMenu()
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            com.arcadia.shell.libretro.R.anim.xora_hold,
            com.arcadia.shell.libretro.R.anim.xora_fade_out,
        )
    }

    /** Caller must already be on [emuDispatcher]. */
    private fun saveState(slot: Int) {
        val data = LibretroNative.nativeSerialize() ?: return
        coreStore.stateFile(platformId, gameId, slot).writeBytes(data)
    }

    /** Caller must already be on [emuDispatcher]. */
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
        closeMenu()
        runJob?.cancel()
        runJob = null
        raSession?.stop()
        raSession = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        synchronized(bitmapLock) {
            gameBitmap?.recycle()
            gameBitmap = null
            bottomBitmap?.recycle()
            bottomBitmap = null
        }
        runCatching {
            runBlocking(emuDispatcher) { LibretroNative.nativeUnload() }
        }
        emuDispatcher.close()
        emuExecutor.shutdownNow()
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
    }
}

/**
 * Classic View framebuffer for the secondary display pane. The same Bitmap instance is mutated
 * via setPixels; we only invalidate.
 */
@Composable
private fun XoraGameImageView(
    bitmap: Bitmap,
    frameTick: Int,
    aspectMode: XoraAspectMode,
    onImageView: (ImageView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaleType = when (aspectMode) {
        XoraAspectMode.Stretch -> ImageView.ScaleType.FIT_XY
        XoraAspectMode.Integer -> ImageView.ScaleType.FIT_XY
        XoraAspectMode.Core -> ImageView.ScaleType.FIT_CENTER
    }
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                adjustViewBounds = false
                this.scaleType = scaleType
                setImageBitmap(bitmap)
                onImageView(this)
            }
        },
        update = { view ->
            view.scaleType = scaleType
            val current = (view.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (current !== bitmap) {
                view.setImageBitmap(bitmap)
            }
            // frameTick changes every present — force a redraw of the mutated pixels.
            if (frameTick >= 0) {
                view.invalidate()
            }
            onImageView(view)
        },
        modifier = modifier,
    )
    DisposableEffect(Unit) {
        onDispose { onImageView(null) }
    }
}
