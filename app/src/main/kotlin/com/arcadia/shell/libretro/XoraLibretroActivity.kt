package com.arcadia.shell.libretro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcadia.shell.audio.UiSoundController
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.AvatarSource
import com.arcadia.shell.datastore.DEFAULT_NETPLAY_PORT
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
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.libretro.netplay.XoraNetplaySession
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState
import com.arcadia.shell.libretro.netplay.formatJoinHostPort
import com.arcadia.shell.libretro.netplay.parseJoinHostPort
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaProfile
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scraper.RomHasher
import com.arcadia.shell.xoranetwork.XoraNetworkAuthCookies
import com.arcadia.shell.xoranetwork.XoraNetworkRepository
import com.arcadia.shell.xoranetwork.XoraNetworkState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
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
    @Inject lateinit var xoraNetwork: XoraNetworkRepository
    @Inject lateinit var xoraCookies: XoraNetworkAuthCookies

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
    /** Topmost profile disc — tap clears a leftover white wash. */
    private var profileChip: FrameLayout? = null
    private var profileChipImage: ImageView? = null
    private var profileChipLetter: TextView? = null
    private var profileName by mutableStateOf("Player")
    /** Feedback shown inside the pause menu. A toast would pull focus off the game window. */
    private var menuMessage by mutableStateOf<String?>(null)
    private var menuMessageJob: Job? = null
    private var saveSlots by mutableStateOf(List(10) { EmulatorSaveSlotUi(it, false, "Empty") })
    private var joinAddress by mutableStateOf("")
    private var joinPort by mutableStateOf(DEFAULT_NETPLAY_PORT)
    private var netplayUi by mutableStateOf(XoraNetplayUiState())
    private var netplaySession: XoraNetplaySession? = null
    private var xoraNetworkUi by mutableStateOf(XoraNetworkState())
    private var raAchievements by mutableStateOf<List<RaAchievement>>(emptyList())
    private var raAchievementSummary by mutableStateOf("")
    private var raStatusLine by mutableStateOf<String?>(null)
    private var lastMenuStickDir = 0
    private var lastMenuStickAt = 0L
    private val inGameXmbController = XoraInGameXmbController()
    private val bitmapLock = Any()
    /** Keeps pinning the window opaque while this activity is in the foreground. */
    private var washGuardJob: Job? = null
    private var washFramePosted = false
    private val washFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            washFramePosted = false
            if (isFinishing || activityInBackground) return
            pinOpaqueWindow()
            if (!menuOpen) pinGameplaySurface()
            keepProfileChipOnTop()
            postWashFrame()
        }
    }

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
        // Never enableEdgeToEdge here. That call switches the window to a translucent format
        // and is what painted a white wash over the game after the pause menu closed.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyOpaqueWindow()

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
        stageView.bezelView.onAvatarClick = { clearWhiteTintFromProfileTap() }
        stageView.bezelView.isClickable = true
        stageView.bezelView.setAvatarDrawn(false)
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
            // Opaque wrap-content side menu. A transparent Compose host is what washed the game.
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(View.LAYER_TYPE_NONE, null)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.GONE
        }
        xmbOverlay = xmb
        root.addView(xmb)
        root.addView(createProfileChip())

        setContentView(root)
        applyOpaqueWindow()
        startWashGuard()
        netplaySession = XoraNetplaySession(lifecycleScope)
        lifecycleScope.launch {
            netplaySession?.state?.collect { ui ->
                netplayUi = ui
                ui.error?.let { showMenuMessage(it) }
                if (ui.linked) {
                    lifecycleScope.launch { disableHardcoreForNetplay() }
                }
                if (ui.linked && menuOpen) {
                    setUserPaused(false)
                    closeMenu()
                    showMenuMessage("Netplay linked · ${ui.peerName.ifBlank { "P2" }}")
                }
            }
        }
        lifecycleScope.launch {
            var avatarKey: String? = null
            xoraNetwork.state.collect { state ->
                xoraNetworkUi = state
                val key = if (state.signedIn) {
                    state.account?.resolvedAvatarUrl?.ifBlank { "signed-in" } ?: "signed-in"
                } else {
                    "signed-out"
                }
                if (key != avatarKey) {
                    avatarKey = key
                    loadProfileAvatar()
                }
            }
        }
        lifecycleScope.launch {
            if (!xoraNetwork.state.value.signedIn) {
                xoraNetwork.restore()
            }
            xoraNetwork.setPlayingLine("playing $gameTitle")
        }
        lifecycleScope.launch {
            profileName = preferences.profile.first().displayName
            val stored = preferences.xoraEmulatorSettings.first()
            val parsed = parseJoinHostPort(stored.netplayHostAddress, stored.netplayPort)
            joinAddress = parsed.host
            joinPort = parsed.port
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
                    XoraEmulatorSideMenu(
                        gameTitle = gameTitle,
                        paused = userPausedUi,
                        hardcore = raPrefs.hardcore && raPrefs.enabled,
                        settings = xora,
                        saveSlots = saveSlots,
                        netplay = netplayUi,
                        joinAddress = joinAddress,
                        joinPort = joinPort,
                        message = menuMessage,
                        onAction = { handleEmulatorMenuAction(it) },
                        onDismiss = { closeMenu() },
                        network = xoraNetworkUi,
                        achievements = raAchievements,
                        achievementSummary = raAchievementSummary,
                        raStatus = raStatusLine,
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
        applyOpaqueWindow()
        startWashGuard()
        uiSounds.onForeground()
        xoraNetwork.setRealtimeEnabled(true)
        xoraNetwork.setPlayingLine("playing $gameTitle")
        if (!menuOpen) window.decorView.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Regaining focus is exactly when a toast or system window has just gone away, which is
        // when the wash used to appear — so restore the whole window state, not just immersive.
        if (hasFocus && !menuOpen) applyOpaqueWindow()
    }

    override fun onPause() {
        // Persist progress before Android may kill the process after a home / recents swipe.
        if (!isChangingConfigurations) {
            activityInBackground = true
            persistSessionForBackground()
        }
        stopWashGuard()
        uiSounds.onBackground()
        xoraNetwork.setRealtimeEnabled(false)
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
        if (menuOpen) {
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
            val y = if (kotlin.math.abs(hatY) > 0.5f) hatY else stickY
            val dir = when {
                y < -0.55f -> -1
                y > 0.55f -> 1
                else -> 0
            }
            val now = SystemClock.uptimeMillis()
            if (dir != 0 && (dir != lastMenuStickDir || now - lastMenuStickAt > 220L)) {
                lastMenuStickDir = dir
                lastMenuStickAt = now
                inGameXmbController.moveItem?.invoke(dir)
                uiSounds.playCursor()
            }
            if (dir == 0) lastMenuStickDir = 0
            return true
        }
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
            session.onGameIdentified = { refreshAchievementList() }
            lifecycleScope.launch {
                session.status.collect { raStatusLine = it }
            }
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
        val overlay = xmbOverlay ?: return
        refreshSaveSlots()
        refreshAchievementList()
        menuOpen = true
        syncPaused()
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        overlay.setBackgroundColor(AndroidColor.BLACK)
        overlay.bringToFront()
        keepProfileChipOnTop()
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
        if (!menuOpen) return
        menuOpen = false
        syncPaused()
        dissolveWashLayers()
        menuMessageJob?.cancel()
        menuMessage = null
        applyOpaqueWindow()
        pinGameplaySurface()
        keepProfileChipOnTop()
        postWashFrame()
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
                val enable = !xoraSettings.netplayEnabled
                if (enable) disableHardcoreForNetplay()
                preferences.setXoraNetplayEnabled(enable)
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
            is EmulatorMenuAction.SetJoinTarget -> applyJoinTarget(action.address, action.port)
            EmulatorMenuAction.ClearJoinTarget -> lifecycleScope.launch {
                joinAddress = ""
                joinPort = DEFAULT_NETPLAY_PORT
                preferences.setXoraNetplayHostAddress("")
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
            EmulatorMenuAction.ToggleRaHardcore -> lifecycleScope.launch {
                if (netplayUi.linked || xoraSettings.netplayEnabled) {
                    disableHardcoreForNetplay()
                    showMenuMessage("Hardcore stays off during netplay")
                    return@launch
                }
                val next = !(raSettings.hardcore && raSettings.enabled)
                preferences.setRaHardcore(next)
                withContext(emuDispatcher) { LibretroNative.nativeRaSetHardcore(next) }
                raSession?.applyHardcore(next)
                raSettings = raSettings.copy(hardcore = next)
                showMenuMessage(if (next) "Hardcore on" else "Hardcore off")
                refreshAchievementList()
            }
            is EmulatorMenuAction.ShowAchievement -> {
                val detail = action.description.ifBlank { action.title }
                showMenuMessage("${action.title} — $detail")
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
        lifecycleScope.launch {
            disableHardcoreForNetplay()
            preferences.setXoraNetplayEnabled(true)
            withContext(Dispatchers.Main.immediate) {
                val hello = netplayHello()
                netplaySession?.host(xoraSettings.netplayPort, hello) {
                    withContext(emuDispatcher) { LibretroNative.nativeSerialize() }
                }
                showMenuMessage("Waiting for a player…")
            }
        }
    }

    private fun applyJoinTarget(address: String, port: Int) {
        val parsed = parseJoinHostPort(address, port)
        joinAddress = parsed.host
        joinPort = parsed.port
        lifecycleScope.launch {
            preferences.setXoraNetplayHostAddress(formatJoinHostPort(parsed.host, parsed.port))
        }
    }

    private fun startJoinNetplay() {
        val parsed = parseJoinHostPort(
            joinAddress.ifBlank { xoraSettings.netplayHostAddress },
            joinPort,
        )
        val address = parsed.host
        if (address.isBlank()) {
            showMenuMessage("Type a join IP first")
            return
        }
        joinAddress = address
        joinPort = parsed.port
        lifecycleScope.launch {
            disableHardcoreForNetplay()
            preferences.setXoraNetplayEnabled(true)
            preferences.setXoraNetplayHostAddress(formatJoinHostPort(address, parsed.port))
            withContext(Dispatchers.Main.immediate) {
                val hello = netplayHello()
                netplaySession?.join(address, parsed.port, hello) { bytes ->
                    withContext(emuDispatcher) { LibretroNative.nativeUnserialize(bytes) }
                }
                showMenuMessage("Joining $address:${parsed.port}…")
            }
        }
    }

    private suspend fun disableHardcoreForNetplay() {
        if (!raSettings.hardcore) return
        preferences.setRaHardcore(false)
        withContext(emuDispatcher) { LibretroNative.nativeRaSetHardcore(false) }
        raSession?.applyHardcore(false)
        raSettings = raSettings.copy(hardcore = false)
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

    private fun refreshAchievementList() {
        lifecycleScope.launch {
            val id = raSession?.raGameId
            if (id == null) {
                raAchievementSummary = raStatusLine?.removePrefix("RA: ")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Identifying game…"
                return@launch
            }
            val result = retroAchievements.fetchGameProgress(id)
            result.fold(
                onSuccess = { progress ->
                    raAchievements = progress.achievements.sortedBy { it.displayOrder }
                    raAchievementSummary = progress.progressLabel
                },
                onFailure = { error ->
                    raAchievementSummary = error.message ?: "Could not load achievements"
                },
            )
        }
    }

    private fun applyStageSettings(xora: XoraEmulatorSettings) {
        val stageView = stage ?: return
        stageView.aspectMode = xora.aspectMode
        stageView.integerScaleCap = xora.integerScale
        stageView.bezelsEnabled = xora.bezelsEnabled
        primaryGameView?.scaleType = ImageView.ScaleType.FIT_XY
        refreshOverlayFile()
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
        val network = xoraNetwork.state.value
        val initial = when {
            network.signedIn -> network.account?.displayName?.trim()?.firstOrNull()
                ?: network.account?.username?.trim()?.firstOrNull()
                ?: profile.displayName.trim().firstOrNull()
            else -> profile.displayName.trim().firstOrNull()
        }?.uppercaseChar()?.toString() ?: "P"
        val fill = when (profile.avatarPresetId) {
            "preset_1" -> AndroidColor.rgb(55, 214, 160)
            "preset_2" -> AndroidColor.rgb(255, 194, 75)
            "preset_3" -> AndroidColor.rgb(255, 92, 108)
            "preset_4" -> AndroidColor.rgb(166, 174, 255)
            "preset_5" -> AndroidColor.rgb(78, 205, 196)
            else -> AndroidColor.rgb(110, 123, 255)
        }
        val bitmap = withContext(Dispatchers.IO) {
            val networkUrl = network.account?.resolvedAvatarUrl.orEmpty()
            if (network.signedIn && networkUrl.isNotBlank()) {
                decodeBitmapUrl(networkUrl)
            } else {
                when (profile.avatarSource) {
                    AvatarSource.Local -> avatarStore.resolveFile(profile.localAvatarFileName)
                        ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    AvatarSource.RetroAchievements -> {
                        val user = preferences.retroAchievements.first().username
                        if (user.isBlank()) null else decodeBitmapUrl(RaProfile.userPicUrlFor(user))
                    }
                    AvatarSource.XoraNetwork -> {
                        if (networkUrl.isBlank()) null else decodeBitmapUrl(networkUrl)
                    }
                    else -> null
                }
            }
        }
        withContext(Dispatchers.Main.immediate) {
            stage?.bezelView?.setAvatar(bitmap, initial, fill)
            bindProfileChip(bitmap, initial, fill)
        }
    }

    private fun decodeBitmapUrl(url: String): Bitmap? = runCatching {
        val builder = okhttp3.Request.Builder().url(url)
        val cookie = xoraCookies.cookieHeader()
        if (!cookie.isNullOrBlank() && url.contains("account.xoranetwork.com", ignoreCase = true)) {
            builder.header("Cookie", cookie)
        }
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }.getOrNull()

    /**
     * Opaque black window. Do **not** call enableEdgeToEdge — that flips the window translucent
     * and is what painted white over the game after Resume.
     */
    private fun applyOpaqueWindow() {
        pinOpaqueWindow()
        if (!menuOpen) {
            pinGameplaySurface()
            window.decorView.requestFocus()
        }
    }

    private fun pinOpaqueWindow() {
        if (isFinishing) return
        window.setFormat(PixelFormat.OPAQUE)
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
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
        gameRoot?.setBackgroundColor(AndroidColor.BLACK)
        stage?.apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(AndroidColor.BLACK)
            alpha = 1f
        }
    }

    /**
     * Gameplay-only pin: overlay closed, user is playing. Drive leftover overlay opacity all
     * the way to transparent so a white scrim cannot sit on the framebuffer.
     */
    private fun pinGameplaySurface() {
        if (isFinishing || menuOpen) return
        dissolveWashLayers()
        gameRoot?.setBackgroundColor(AndroidColor.BLACK)
        primaryGameView?.apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(AndroidColor.BLACK)
            alpha = 1f
            colorFilter = null
            imageAlpha = 255
            visibility = View.VISIBLE
        }
        stage?.apply {
            setBackgroundColor(AndroidColor.BLACK)
            alpha = 1f
            visibility = View.VISIBLE
        }
        keepProfileChipOnTop()
        synchronized(bitmapLock) {
            val src = gameBitmap
            val view = primaryGameView
            if (src != null && !src.isRecycled && view != null) {
                view.invalidate()
            }
        }
    }

    /**
     * If a wash is still attached, fade it to fully transparent instead of leaving it opaque
     * and GONE (some OEMs still composite a GONE ComposeView).
     */
    private fun dissolveWashLayers() {
        if (isFinishing) return
        val attrs = window.attributes
        if (attrs.dimAmount != 0f) {
            attrs.dimAmount = 0f
            window.attributes = attrs
        }
        xmbOverlay?.apply {
            alpha = 0f
            visibility = View.GONE
            setBackgroundColor(AndroidColor.TRANSPARENT)
            isClickable = false
            isFocusable = false
        }
    }

    /** Tap the profile disc — the kill switch when the automatic pin still leaves a wash. */
    private fun clearWhiteTintFromProfileTap() {
        if (isFinishing) return
        pinOpaqueWindow()
        if (menuOpen) {
            val attrs = window.attributes
            if (attrs.dimAmount != 0f) {
                attrs.dimAmount = 0f
                window.attributes = attrs
            }
            showMenuMessage("White tint cleared")
        } else {
            dissolveWashLayers()
            pinGameplaySurface()
        }
        keepProfileChipOnTop()
        uiSounds.playConfirm()
    }

    private fun keepProfileChipOnTop() {
        val root = gameRoot ?: return
        val chip = profileChip ?: return
        chip.visibility = View.VISIBLE
        chip.alpha = 1f
        if (root.getChildAt(root.childCount - 1) !== chip) {
            chip.bringToFront()
        }
    }

    private fun createProfileChip(): FrameLayout {
        val density = resources.displayMetrics.density
        val size = (56f * density).toInt().coerceAtLeast(40)
        val pad = (18f * density).toInt()
        val stroke = (3f * density).toInt().coerceAtLeast(2)
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val letter = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.WHITE)
            textSize = 20f
            paint.isFakeBoldText = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val chip = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                rightMargin = pad
                topMargin = pad
            }
            foreground = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AndroidColor.TRANSPARENT)
                setStroke(stroke, AndroidColor.WHITE)
            }
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            contentDescription = "Clear white tint"
            elevation = 24f * density
            setOnClickListener { clearWhiteTintFromProfileTap() }
        }
        chip.addView(image)
        chip.addView(letter)
        profileChip = chip
        profileChipImage = image
        profileChipLetter = letter
        bindProfileChip(null, "P", AndroidColor.rgb(110, 123, 255))
        return chip
    }

    private fun bindProfileChip(bitmap: Bitmap?, initial: String, fillColor: Int) {
        val oval = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }
        profileChipImage?.apply {
            background = oval
            if (bitmap != null && !bitmap.isRecycled) {
                setImageBitmap(bitmap)
            } else {
                setImageDrawable(null)
            }
        }
        profileChipLetter?.apply {
            text = initial
            visibility = if (bitmap != null && !bitmap.isRecycled) View.GONE else View.VISIBLE
        }
    }

    /**
     * Runs while the activity is RESUMED — overlay open **and** closed / playing.
     * Vsync is used so the pin is not starved by the frame present queue.
     */
    private fun startWashGuard() {
        washGuardJob?.cancel()
        washGuardJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                postWashFrame()
                try {
                    awaitCancellation()
                } finally {
                    Choreographer.getInstance().removeFrameCallback(washFrameCallback)
                    washFramePosted = false
                }
            }
        }
        postWashFrame()
    }

    private fun stopWashGuard() {
        washGuardJob?.cancel()
        washGuardJob = null
        Choreographer.getInstance().removeFrameCallback(washFrameCallback)
        washFramePosted = false
    }

    private fun postWashFrame() {
        if (washFramePosted || isFinishing || activityInBackground) return
        washFramePosted = true
        Choreographer.getInstance().postFrameCallback(washFrameCallback)
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
            if (!menuOpen) pinGameplaySurface()
        }
    }

    override fun finish() {
        // Explicit quit — drop the autosave so the next launch is a fresh boot.
        runCatching { coreStore.autosaveFile(platformId, gameId).delete() }
        gameLoaded = false
        xoraNetwork.setPlayingLine(null)
        xoraNetwork.setRealtimeEnabled(false)
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
        stopWashGuard()
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
