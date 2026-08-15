package com.arcadia.shell.libretro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.view.ViewTreeObserver
import android.view.WindowManager
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
import com.arcadia.shell.datastore.AvatarSource
import com.arcadia.shell.datastore.ProfileAvatarStore
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.XoraInternalResolution
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.LocalArcadiaHaze
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.display.ImmersiveMode
import com.arcadia.shell.display.SecondaryDisplayPane
import com.arcadia.shell.feature.home.EmulatorMenuAction
import com.arcadia.shell.feature.home.EmulatorSaveSlotUi
import com.arcadia.shell.feature.home.LocalInGameXmbController
import com.arcadia.shell.feature.home.XoraEmulatorSideMenu
import com.arcadia.shell.feature.home.XoraInGameXmbController
import com.arcadia.shell.libretro.netplay.nudgeIpv4
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.libretro.netplay.XoraNetplaySession
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState
import com.arcadia.shell.retroachievements.RaProfile
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scraper.RomHasher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * In-process Libretro session.
 *
 * The framebuffer lives on an opaque [ImageView] inside [XoraEmulatorStage]. Back / PS-style
 * chord opens an Azahar-style side menu that never covers the game with a translucent Compose
 * sheet — that stacking is what left the milky white wash after Resume.
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
    @Inject lateinit var avatarStore: ProfileAvatarStore

    @Volatile private var menuOpen = false
    /** True while the in-game menu is showing or the user left Pause on. */
    @Volatile private var paused = false
    /** Stays paused after the side menu closes until Resume is chosen. */
    @Volatile private var userPaused = false
    private var userPausedUi by mutableStateOf(false)
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
    private var stage: XoraEmulatorStage? = null
    private var xmbOverlay: ComposeView? = null
    private var overlayLayoutListening = false
    private val overlayLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        applyGameStageInsets()
    }
    private var profileName by mutableStateOf("Player")
    /** Feedback shown inside the pause menu. A toast would pull focus off the game window. */
    private var menuMessage by mutableStateOf<String?>(null)
    private var menuMessageJob: Job? = null
    private var saveSlots by mutableStateOf(List(10) { EmulatorSaveSlotUi(it, false, "Empty") })
    private var joinAddress by mutableStateOf("")
    private var netplayUi by mutableStateOf(XoraNetplayUiState())
    private var netplaySession: XoraNetplaySession? = null
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
    private var romFilePath: String? = null
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
        // Force dark system-bar scrims. Transparent bars were leaving a near-white contrast
        // overlay on the letterboxed game after pause submenus.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        ImmersiveMode.apply(window)

        val romPath = intent.getStringExtra(XoraLibretroPlayers.EXTRA_ROM_PATH)
        romFilePath = romPath
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
            clipChildren = true
            clipToPadding = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        gameRoot = root
        val stageView = XoraEmulatorStage(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val aspect = NsoBezelCatalog.defaultAspect(platformId)
            contentWidthPx = 4
            contentHeightPx = 3
            if (aspect > 0f) {
                contentWidthPx = (aspect * 1000).toInt().coerceAtLeast(1)
                contentHeightPx = 1000
            }
        }
        stage = stageView
        primaryGameView = stageView.gameView
        root.addView(stageView)

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
            // Opaque wrap-content side menu. The game stage is laid out to its right so Compose
            // never composites over live pixels (that stacking is the white wash after submenus).
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(View.LAYER_TYPE_NONE, null)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            visibility = View.GONE
        }
        xmbOverlay = xmb
        // Not attached until Back opens the in-game XMB.

        setContentView(root)
        netplaySession = XoraNetplaySession(lifecycleScope)
        lifecycleScope.launch {
            netplaySession?.state?.collect { ui ->
                netplayUi = ui
                ui.error?.let { showMenuMessage(it) }
                if (ui.linked && menuOpen) {
                    setUserPaused(false)
                    closeMenu()
                    showMenuMessage("Netplay linked · ${ui.peerName.ifBlank { "P2" }}")
                }
            }
        }
        lifecycleScope.launch {
            profileName = preferences.profile.first().displayName
            joinAddress = preferences.xoraEmulatorSettings.first().netplayHostAddress
            refreshOverlayFile()
            loadProfileAvatar()
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
                applyStageSettings(xora)
                applyAudioVolume(xora.audioVolume)
                if (joinAddress.isBlank() && xora.netplayHostAddress.isNotBlank()) {
                    joinAddress = xora.netplayHostAddress
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

            // Always dark + Haze killed for the whole emulator session. liquidGlass frost over
            // anything near the framebuffer was the wash left after pause submenus / Resume.
            ArcadiaTheme(
                darkTheme = true,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
            ) {
                CompositionLocalProvider(LocalArcadiaHaze provides null) {
                    Box(modifier = Modifier.wrapContentSize(align = Alignment.TopStart)) {
                        NotificationBannerHost(center = shellNotifications)
                    }
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
                CompositionLocalProvider(
                    LocalArcadiaHaze provides null,
                    LocalInGameXmbController provides inGameXmbController,
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(align = Alignment.TopStart)
                            .background(androidx.compose.ui.graphics.Color.Black),
                    ) {
                        XoraEmulatorSideMenu(
                            gameTitle = gameTitle,
                            paused = userPausedUi,
                            hardcore = raPrefs.hardcore && raPrefs.enabled,
                            settings = xora,
                            saveSlots = saveSlots,
                            netplay = netplayUi,
                            joinAddress = joinAddress.ifBlank { xora.netplayHostAddress },
                            message = menuMessage,
                            onAction = { handleEmulatorMenuAction(it) },
                            onDismiss = { closeMenu() },
                        )
                    }
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
        // Regaining focus is exactly when a toast or system window has just gone away, which is
        // when the wash used to appear — so restore the whole window state, not just immersive.
        if (hasFocus && !menuOpen) restoreImmersiveFocus()
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
                appContext = applicationContext,
                coreName = coreName,
                raSettings = prefs,
                onEmulatorResetRequested = {
                    lifecycleScope.launch(emuDispatcher) {
                        LibretroNative.nativeReset()
                        LibretroNative.nativeRaReset()
                        raSession?.onEmulatorReset()
                    }
                },
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
        refreshSaveSlots()
        if (overlay.parent == null) {
            root.addView(overlay)
        }
        overlay.setBackgroundColor(AndroidColor.BLACK)
        overlay.visibility = View.VISIBLE
        root.bringChildToFront(overlay)
        listenOverlayLayout(overlay, true)
        menuOpen = true
        applyGameStageInsets()
        overlay.post { if (menuOpen) applyGameStageInsets() }
        syncPaused()
        uiSounds.playConfirm()
    }

    private fun syncPaused() {
        paused = menuOpen || userPaused
        runCatching {
            if (paused) audioTrack?.pause() else audioTrack?.play()
        }
    }

    private fun setUserPaused(value: Boolean) {
        userPaused = value
        userPausedUi = value
        syncPaused()
    }

    private fun showMenuMessage(text: String) {
        menuMessage = text
        menuMessageJob?.cancel()
        menuMessageJob = lifecycleScope.launch {
            delay(MENU_MESSAGE_MS)
            menuMessage = null
        }
    }

    private fun closeMenu() {
        if (!menuOpen && xmbOverlay?.parent == null) return
        menuOpen = false
        syncPaused()
        val overlay = xmbOverlay
        listenOverlayLayout(overlay, false)
        overlay?.visibility = View.GONE
        (overlay?.parent as? ViewGroup)?.removeView(overlay)
        menuMessageJob?.cancel()
        menuMessage = null
        applyGameStageInsets()
        scrubOverlayWash()
        primaryGameView?.post { if (!menuOpen && !isFinishing) scrubOverlayWash() }
    }

    private fun handleEmulatorMenuAction(action: EmulatorMenuAction) {
        when (action) {
            EmulatorMenuAction.TogglePause -> {
                setUserPaused(!userPaused)
                showMenuMessage(if (userPaused) "Paused" else "Resumed")
            }
            is EmulatorMenuAction.SaveSlot -> saveSlotFromMenu(action.slot)
            is EmulatorMenuAction.LoadSlot -> loadSlotFromMenu(action.slot)
            EmulatorMenuAction.SetFullScreen -> lifecycleScope.launch {
                preferences.setXoraAspectMode(XoraAspectMode.Stretch)
            }
            EmulatorMenuAction.SetNativeRatio -> lifecycleScope.launch {
                preferences.setXoraAspectMode(XoraAspectMode.Core)
            }
            EmulatorMenuAction.ToggleBezel -> lifecycleScope.launch {
                preferences.setXoraBezelsEnabled(!xoraSettings.bezelsEnabled)
            }
            EmulatorMenuAction.ClearWhiteTint -> {
                lifecycleScope.launch {
                    preferences.setXoraBlockOverlayWash(true)
                }
                scrubOverlayWash()
                applyGameStageInsets()
                showMenuMessage("White tint cleared")
            }
            EmulatorMenuAction.ToggleBlockOverlayWash -> lifecycleScope.launch {
                val next = !xoraSettings.blockOverlayWash
                preferences.setXoraBlockOverlayWash(next)
                withContext(Dispatchers.Main.immediate) {
                    applyGameStageInsets()
                    if (next) scrubOverlayWash()
                    showMenuMessage(if (next) "White tint blocked" else "Tint block off")
                }
            }
            EmulatorMenuAction.CycleInternalResolution -> lifecycleScope.launch {
                val values = XoraInternalResolution.entries
                val i = values.indexOf(xoraSettings.internalResolution).coerceAtLeast(0)
                preferences.setXoraInternalResolution(values[(i + 1) % values.size])
            }
            EmulatorMenuAction.CycleIntegerScale -> lifecycleScope.launch {
                val next = (xoraSettings.integerScale + 1).let { if (it > 6) 0 else it }
                preferences.setXoraIntegerScale(next)
                preferences.setXoraAspectMode(XoraAspectMode.Integer)
            }
            EmulatorMenuAction.ToggleExpandDual -> lifecycleScope.launch {
                preferences.setXoraExpandDualDisplay(!xoraSettings.expandDualDisplay)
            }
            EmulatorMenuAction.ToggleNetplayEnabled -> lifecycleScope.launch {
                preferences.setXoraNetplayEnabled(!xoraSettings.netplayEnabled)
            }
            EmulatorMenuAction.HostNetplay -> startHostNetplay()
            EmulatorMenuAction.JoinNetplay -> startJoinNetplay()
            EmulatorMenuAction.DisconnectNetplay -> {
                netplaySession?.stop()
                showMenuMessage("Netplay disconnected")
            }
            EmulatorMenuAction.ToggleSpectator -> lifecycleScope.launch {
                preferences.setXoraNetplaySpectator(!xoraSettings.netplaySpectator)
            }
            is EmulatorMenuAction.NudgeJoinOctet -> lifecycleScope.launch {
                val current = joinAddress.ifBlank { xoraSettings.netplayHostAddress }
                val next = nudgeIpv4(current, action.octetIndex, action.delta)
                joinAddress = next
                preferences.setXoraNetplayHostAddress(next)
            }
            EmulatorMenuAction.CyclePreferredController -> lifecycleScope.launch {
                val names = listOf("") + LibretroPad.connectedControllerNames()
                val idx = names.indexOf(xoraSettings.preferredControllerName).let {
                    if (it >= 0) it else 0
                }
                preferences.setXoraPreferredControllerName(names[(idx + 1) % names.size])
            }
            EmulatorMenuAction.ClearMappings -> lifecycleScope.launch {
                preferences.clearXoraButtonMappings()
                showMenuMessage("Custom mappings cleared")
            }
            EmulatorMenuAction.VolumeUp -> lifecycleScope.launch {
                preferences.setXoraAudioVolume((xoraSettings.audioVolume + 0.1f).coerceAtMost(1f))
            }
            EmulatorMenuAction.VolumeDown -> lifecycleScope.launch {
                preferences.setXoraAudioVolume((xoraSettings.audioVolume - 0.1f).coerceAtLeast(0f))
            }
            EmulatorMenuAction.ResetDefaults -> lifecycleScope.launch {
                preferences.resetXoraEmulatorPlaySettings()
                showMenuMessage("Emulator defaults restored")
            }
            EmulatorMenuAction.ReturnHome -> {
                closeMenu()
                finish()
            }
        }
    }

    private fun saveSlotFromMenu(slot: Int) {
        val hardcore = raSettings.hardcore && raSettings.enabled
        if (hardcore) {
            showMenuMessage("Hardcore mode — save states disabled")
            return
        }
        lifecycleScope.launch(emuDispatcher) {
            saveState(slot)
            withContext(Dispatchers.Main.immediate) {
                refreshSaveSlots()
                showMenuMessage("Saved slot $slot")
            }
        }
    }

    private fun loadSlotFromMenu(slot: Int) {
        val hardcore = raSettings.hardcore && raSettings.enabled
        if (hardcore) {
            showMenuMessage("Hardcore mode — load states disabled")
            return
        }
        lifecycleScope.launch(emuDispatcher) {
            val ok = loadState(slot)
            withContext(Dispatchers.Main.immediate) {
                showMenuMessage(if (ok) "Loaded slot $slot" else "Slot $slot is empty")
                if (ok) {
                    setUserPaused(false)
                    closeMenu()
                }
            }
        }
    }

    private fun startHostNetplay() {
        if (raSettings.hardcore && raSettings.enabled) {
            showMenuMessage("Hardcore — netplay disabled")
            return
        }
        lifecycleScope.launch { preferences.setXoraNetplayEnabled(true) }
        val hello = netplayHello()
        netplaySession?.host(xoraSettings.netplayPort, hello) {
            withContext(emuDispatcher) { LibretroNative.nativeSerialize() }
        }
        showMenuMessage("Waiting for a player…")
    }

    private fun startJoinNetplay() {
        if (raSettings.hardcore && raSettings.enabled) {
            showMenuMessage("Hardcore — netplay disabled")
            return
        }
        val address = joinAddress.ifBlank { xoraSettings.netplayHostAddress }
        if (address.isBlank()) {
            showMenuMessage("Set a join IP first")
            return
        }
        lifecycleScope.launch { preferences.setXoraNetplayEnabled(true) }
        val hello = netplayHello()
        netplaySession?.join(address, xoraSettings.netplayPort, hello) { bytes ->
            withContext(emuDispatcher) { LibretroNative.nativeUnserialize(bytes) }
        }
        showMenuMessage("Joining $address…")
    }

    private fun netplayHello() = XoraNetplayProtocol.Hello(
        nickname = xoraSettings.netplayNickname,
        coreName = coreName,
        platformId = platformId,
        romName = gameTitle,
    )

    private fun refreshSaveSlots() {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        saveSlots = (0..9).map { slot ->
            val file = coreStore.stateFile(platformId, gameId, slot)
            if (file.isFile && file.length() > 0L) {
                EmulatorSaveSlotUi(
                    slot = slot,
                    occupied = true,
                    subtitle = fmt.format(Date(file.lastModified())),
                )
            } else {
                EmulatorSaveSlotUi(slot = slot, occupied = false, subtitle = "Empty")
            }
        }
    }

    private fun applyStageSettings(xora: XoraEmulatorSettings) {
        val stageView = stage ?: return
        stageView.aspectMode = xora.aspectMode
        stageView.integerScaleCap = xora.integerScale
        stageView.bezelsEnabled = xora.bezelsEnabled
        primaryGameView?.scaleType = ImageView.ScaleType.FIT_XY
        refreshOverlayFile()
        applyGameStageInsets()
    }

    private fun applyAudioVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        runCatching { audioTrack?.setVolume(v) }
    }

    private fun refreshOverlayFile() {
        val file = NsoBezelLocator.resolve(
            platformId = platformId,
            coreName = coreName,
            romFilePath = romFilePath,
            overlaysDir = coreStore.overlaysDir,
            preferFull = xoraSettings.aspectMode == XoraAspectMode.Stretch,
        )
        stage?.setOverlayFile(file)
    }

    private suspend fun loadProfileAvatar() {
        val profile = preferences.profile.first()
        val initial = profile.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"
        val fill = when (profile.avatarPresetId) {
            "preset_1" -> AndroidColor.rgb(55, 214, 160)
            "preset_2" -> AndroidColor.rgb(255, 194, 75)
            "preset_3" -> AndroidColor.rgb(255, 92, 108)
            "preset_4" -> AndroidColor.rgb(166, 174, 255)
            "preset_5" -> AndroidColor.rgb(78, 205, 196)
            else -> AndroidColor.rgb(110, 123, 255)
        }
        val bitmap = withContext(Dispatchers.IO) {
            when (profile.avatarSource) {
                AvatarSource.Local -> avatarStore.resolveFile(profile.localAvatarFileName)
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                AvatarSource.RetroAchievements -> {
                    val user = preferences.retroAchievements.first().username
                    if (user.isBlank()) null else decodeBitmapUrl(RaProfile.userPicUrlFor(user))
                }
                else -> null
            }
        }
        withContext(Dispatchers.Main.immediate) {
            stage?.bezelView?.setAvatar(bitmap, initial, fill)
        }
    }

    private fun decodeBitmapUrl(url: String): Bitmap? = runCatching {
        okHttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }.getOrNull()

    /**
     * Puts the window back exactly as gameplay needs it.
     *
     * Opening the in-game XMB (especially Settings / XOrA Emulator depth) can leave the window
     * with a translucent surface format or the system's light contrast scrims back on. The result
     * is a washed-out picture with grey letterbox bars instead of black — a full-window lift, not
     * a tint on the frame alone. Re-stating all of it is cheap, so do it on every return rather
     * than only once at startup.
     */
    private fun restoreImmersiveFocus() {
        window.setFormat(PixelFormat.OPAQUE)
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        // Re-assert dark edge-to-edge bars. A light nav scrim after overlay dismiss is what made
        // letterboxes read grey even when the ImageView itself was fine.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = AndroidColor.BLACK
            window.navigationBarColor = AndroidColor.BLACK
        }
        window.setBackgroundDrawableResource(android.R.color.black)
        window.decorView.setBackgroundColor(AndroidColor.BLACK)
        ImmersiveMode.apply(window)
        window.decorView.requestFocus()
        gameRoot?.setBackgroundColor(AndroidColor.BLACK)
        xmbOverlay?.setBackgroundColor(AndroidColor.BLACK)
        primaryGameView?.apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(AndroidColor.BLACK)
            alpha = 1f
            colorFilter = null
            imageAlpha = 255
            invalidate()
        }
        stage?.setBackgroundColor(AndroidColor.BLACK)
        stage?.invalidate()
    }

    /**
     * Lays the live framebuffer beside the opaque pause menu so Compose never blends over it.
     */
    private fun applyGameStageInsets() {
        val stageView = stage ?: return
        val lp = stageView.layoutParams as? FrameLayout.LayoutParams ?: return
        val overlay = xmbOverlay
        val parentW = gameRoot?.width ?: 0
        val overlayW = overlay
            ?.takeIf { menuOpen && it.parent != null && it.visibility == View.VISIBLE }
            ?.width
            ?: 0
        val inset = if (!menuOpen || !xoraSettings.blockOverlayWash) {
            0
        } else if (parentW <= 0) {
            overlayW
        } else {
            overlayW.coerceAtMost((parentW * 0.55f).toInt())
        }
        if (lp.marginStart != inset) {
            lp.marginStart = inset
            stageView.layoutParams = lp
        }
    }

    private fun listenOverlayLayout(overlay: View?, listen: Boolean) {
        if (overlay == null) {
            overlayLayoutListening = false
            return
        }
        val observer = overlay.viewTreeObserver
        if (!observer.isAlive) return
        if (listen && !overlayLayoutListening) {
            observer.addOnGlobalLayoutListener(overlayLayoutListener)
            overlayLayoutListening = true
        } else if (!listen && overlayLayoutListening) {
            observer.removeOnGlobalLayoutListener(overlayLayoutListener)
            overlayLayoutListening = false
        }
    }

    /** Rebind the framebuffer and kill leftover compositor wash after pause submenus. */
    private fun scrubOverlayWash() {
        restoreImmersiveFocus()
        applyGameStageInsets()
        synchronized(bitmapLock) {
            val src = gameBitmap
            val view = primaryGameView
            if (src != null && !src.isRecycled && view != null) {
                view.setImageDrawable(null)
                view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                view.setImageBitmap(src)
                view.setLayerType(View.LAYER_TYPE_NONE, null)
                view.invalidate()
            }
        }
        stage?.invalidate()
        stage?.bezelView?.invalidate()
        gameRoot?.invalidate()
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
            .also {
                it.play()
                applyAudioVolume(xoraSettings.audioVolume)
            }
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
                    val localButtons = keyPadButtons.get() or axisPadButtons.get()
                    val session = netplaySession
                    if (session?.linkedNow == true) {
                        val frameIndex = session.nextFrameIndex()
                        val local = XoraNetplayProtocol.PadFrame(
                            frame = frameIndex,
                            buttons = if (xoraSettings.netplaySpectator && !session.hosting) {
                                0
                            } else {
                                localButtons
                            },
                            lx = axisLx,
                            ly = axisLy,
                            rx = axisRx,
                            ry = axisRy,
                        )
                        val remote = session.exchange(local)
                        if (session.hosting) {
                            LibretroNative.nativeSetPadStatePort(0, local.buttons, local.lx, local.ly, local.rx, local.ry)
                            LibretroNative.nativeSetPadStatePort(1, remote.buttons, remote.lx, remote.ly, remote.rx, remote.ry)
                        } else {
                            LibretroNative.nativeSetPadStatePort(0, remote.buttons, remote.lx, remote.ly, remote.rx, remote.ry)
                            LibretroNative.nativeSetPadStatePort(1, local.buttons, local.lx, local.ly, local.rx, local.ry)
                        }
                    } else {
                        LibretroNative.nativeSetPadState(
                            localButtons,
                            axisLx,
                            axisLy,
                            axisRx,
                            axisRy,
                        )
                    }
                    LibretroNative.nativeRunFrame()
                    raSession?.doFrame()
                    LibretroNative.nativeCopyFrameRgba()?.let { packed ->
                        presentFrame(packed)
                    }
                    LibretroNative.nativeDrainAudio()?.let { pcm ->
                        audioTrack?.write(pcm, 0, pcm.size)
                    }
                    val elapsed = System.nanoTime() - start
                    val sleepMs = ((frameNs - elapsed) / 1_000_000L).coerceAtLeast(0L)
                    // delay/yield (not Thread.sleep) so serialize / unload can run here too.
                    if (sleepMs > 0) delay(sleepMs) else yield()
                } else {
                    // Nothing is being emulated or drawn, so frame pacing would just wake the CPU
                    // sixty times a second behind a paused session. Idle RA at a slow tick instead.
                    raSession?.idle()
                    delay(IDLE_TICK_MS)
                }
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
                    stage?.let { stageView ->
                        if (stageView.contentWidthPx != w || stageView.contentHeightPx != topH) {
                            stageView.contentWidthPx = w
                            stageView.contentHeightPx = topH
                        }
                    }
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
                    stage?.let { stageView ->
                        if (stageView.contentWidthPx != w || stageView.contentHeightPx != h) {
                            stageView.contentWidthPx = w
                            stageView.contentHeightPx = h
                        }
                    }
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
        netplaySession?.stop()
        netplaySession = null
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
        /** Loop tick while paused / backgrounded, where no frames are produced. */
        private const val IDLE_TICK_MS = 200L
        private const val MENU_MESSAGE_MS = 2_600L
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
