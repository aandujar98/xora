package com.arcadia.shell.libretro

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.audio.UiSoundController
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.label
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LocalArcadiaHaze
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
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
 * In-process Libretro session. Frames are presented via [ImageView] (AndroidView) under
 * Compose pause chrome. Compose [Image]/mutable [Bitmap] was leaving a milky wash after
 * overlay Resume; ImageView stays mounted and only invalidates after [Bitmap.setPixels].
 *
 * All Libretro core entry points run on a dedicated single OS thread. Cores such as
 * Mupen64Plus-Next use libco; [retro_init] / [retro_run] / serialize must share that thread or
 * N64 sessions SIGSEGV on the first frame or savestate.
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

    private var menuOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var achievementsOpen by mutableStateOf(false)
    private var controllersOpen by mutableStateOf(false)
    private var mappingOpen by mutableStateOf(false)
    /** When non-null, the next gamepad key assigns that Libretro button. */
    private var waitingForMapButton by mutableStateOf<Int?>(null)
    private var statusText by mutableStateOf("Loading…")
    /** Full-screen XOrA plate until the first frame (or a hard error) — no Android slide feel. */
    private var bootOverlayVisible by mutableStateOf(true)
    private var raStatusText by mutableStateOf<String?>(null)
    private var paused by mutableStateOf(false)
    /** True while the activity is backgrounded (home/recents) — pauses the frame loop. */
    @Volatile private var activityInBackground = false
    private var gameLoaded = false
    private var focusedMenuIndex by mutableIntStateOf(0)
    private var raAchievements by mutableStateOf<List<RaLiveAchievement>>(emptyList())
    private var raSession: LibretroRaSession? = null

    private var runJob: Job? = null
    private var audioTrack: AudioTrack? = null
    /** Live gameplay bitmap presented via ImageView (updated in place + invalidate). */
    private var gameBitmap by mutableStateOf<Bitmap?>(null)
    private var bottomBitmap by mutableStateOf<Bitmap?>(null)
    private var frameTick by mutableIntStateOf(0)
    private var primaryGameView: ImageView? = null
    private var secondaryGameView: ImageView? = null
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
    private var xoraSettings by mutableStateOf(XoraEmulatorSettings())
    private var raSettings by mutableStateOf(RetroAchievementsSettings())
    private var netplayStatus by mutableStateOf<String?>(null)
    private var expandActive by mutableStateOf(false)
    private var secondaryDisplayId by mutableStateOf<Int?>(null)

    private var inputManager: InputManager? = null
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) = Unit
    }

    private fun buildMenuActions(): List<MenuAction> {
        val hardcore = raSettings.hardcore && raSettings.enabled
        val actions = mutableListOf(
            MenuAction("Resume") { closeMenu() },
            MenuAction("Settings") { openInGameSettings() },
            MenuAction("RetroAchievements") { openAchievements() },
        )
        if (!hardcore) {
            actions += MenuAction("Save state") {
                lifecycleScope.launch(emuDispatcher) {
                    saveState(0)
                    withContext(Dispatchers.Main.immediate) {
                        statusText = "State saved (slot 0)"
                    }
                }
            }
            actions += MenuAction("Load state") {
                lifecycleScope.launch(emuDispatcher) {
                    val ok = loadState(0)
                    withContext(Dispatchers.Main.immediate) {
                        statusText = if (ok) "State loaded (slot 0)" else "No save in slot 0"
                    }
                }
            }
        } else {
            actions += MenuAction("Save state (hardcore off)") {
                statusText = "Hardcore mode — save states disabled"
            }
        }
        actions += MenuAction("Reset") {
            lifecycleScope.launch(emuDispatcher) {
                LibretroNative.nativeReset()
                withContext(Dispatchers.Main.immediate) {
                    raSession?.onEmulatorReset()
                    statusText = "Reset"
                    closeMenu()
                }
            }
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
        achievementsOpen = false
        controllersOpen = false
        mappingOpen = false
        waitingForMapButton = null
        focusedMenuIndex = 0
        statusText = ""
    }

    private fun closeInGameSettings() {
        settingsOpen = false
        controllersOpen = false
        mappingOpen = false
        waitingForMapButton = null
        focusedMenuIndex = 0
    }

    private fun openAchievements() {
        achievementsOpen = true
        settingsOpen = false
        controllersOpen = false
        mappingOpen = false
        waitingForMapButton = null
        focusedMenuIndex = 0
        statusText = ""
        refreshAchievementList()
    }

    private fun closeAchievements() {
        achievementsOpen = false
        focusedMenuIndex = 0
    }

    private fun openControllers() {
        controllersOpen = true
        mappingOpen = false
        waitingForMapButton = null
        focusedMenuIndex = 0
    }

    private fun closeControllers() {
        controllersOpen = false
        mappingOpen = false
        waitingForMapButton = null
        focusedMenuIndex = 0
    }

    private fun openButtonMapping() {
        mappingOpen = true
        waitingForMapButton = null
        focusedMenuIndex = 0
    }

    private fun closeButtonMapping() {
        mappingOpen = false
        waitingForMapButton = null
        focusedMenuIndex = 0
    }

    private fun refreshAchievementList() {
        raAchievements = runCatching {
            LibretroNative.nativeRaListAchievements()
                ?.mapNotNull { RaLiveAchievement.fromNativeRow(it) }
                .orEmpty()
        }.getOrDefault(emptyList())
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

            // Always dark emulator chrome — light theme glass tokens use white frost that
            // reads as a lasting bright wash over the framebuffer after Resume.
            ArcadiaTheme(
                darkTheme = true,
                shellThemeId = shellThemeId,
                uiTextScale = textScale,
            ) {
                // Kill Haze for the whole session. liquidGlass white sheen / frost over the
                // live bitmap was the white tint left after pause submenus and Resume.
                CompositionLocalProvider(LocalArcadiaHaze provides null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black),
                    ) {
                    // Keep the ImageView framebuffer mounted under the pause plate. Unmounting /
                    // remounting Compose Image around overlays left a milky wash after Resume.
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
                            onImageView = { primaryGameView = it },
                        )
                    } else if (xora.bezelsEnabled && !expandActive) {
                        XoraBezelBackdrop(
                            platformId = platformId,
                            opacity = xora.bezelOpacity * 0.7f,
                        )
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
                                        onImageView = { secondaryGameView = it },
                                    )
                                }
                            }
                        }
                    }

                    if (menuOpen) {
                        // Fully opaque pause plate over the still-mounted ImageView.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0B0D12)),
                        ) {
                            when {
                                    mappingOpen -> {
                                        XoraEmulatorMappingPanel(
                                            mappings = xora.buttonMappings,
                                            waitingForButton = waitingForMapButton,
                                            focusedIndex = focusedMenuIndex,
                                            onFocus = { focusedMenuIndex = it },
                                            onBack = {
                                                uiSounds.playCancel()
                                                closeButtonMapping()
                                            },
                                            onStartCapture = { button ->
                                                uiSounds.playConfirm()
                                                waitingForMapButton = button
                                            },
                                        )
                                        BackHandler {
                                            uiSounds.playCancel()
                                            closeButtonMapping()
                                        }
                                    }
                                    controllersOpen -> {
                                        XoraEmulatorControllersPanel(
                                            preferredName = xora.preferredControllerName,
                                            mappingCount = xora.buttonMappings.size,
                                            focusedIndex = focusedMenuIndex,
                                            onFocus = { focusedMenuIndex = it },
                                            onBack = {
                                                uiSounds.playCancel()
                                                closeControllers()
                                            },
                                            onCycleController = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    val list = listOf("") + LibretroPad.connectedControllerNames()
                                                    val current = xora.preferredControllerName
                                                    val idx = list.indexOf(current).let {
                                                        if (it >= 0) it else 0
                                                    }
                                                    val next = list[(idx + 1) % list.size]
                                                    preferences.setXoraPreferredControllerName(next)
                                                }
                                            },
                                            onOpenMapping = {
                                                uiSounds.playConfirm()
                                                openButtonMapping()
                                            },
                                            onResetDefaults = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    preferences.clearXoraButtonMappings()
                                                    preferences.setXoraPreferredControllerName("")
                                                }
                                            },
                                        )
                                        BackHandler {
                                            uiSounds.playCancel()
                                            closeControllers()
                                        }
                                    }
                                    settingsOpen -> {
                                        XoraEmulatorSettingsPanel(
                                            xora = xora,
                                            ra = raPrefs,
                                            platformId = platformId,
                                            dualDisplayAvailable = secondaryDisplayId != null,
                                            focusedIndex = focusedMenuIndex,
                                            onFocus = { focusedMenuIndex = it },
                                            onBack = {
                                                uiSounds.playCancel()
                                                closeInGameSettings()
                                            },
                                            onToggleExpand = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    preferences.setXoraExpandDualDisplay(!xora.expandDualDisplay)
                                                    refreshExpandTopology()
                                                }
                                            },
                                            onCycleAspect = {
                                                uiSounds.playConfirm()
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
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    preferences.setXoraBezelsEnabled(!xora.bezelsEnabled)
                                                }
                                            },
                                            onCycleInternalRes = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    val values = com.arcadia.shell.datastore.XoraInternalResolution.entries
                                                    val i = values.indexOf(xora.internalResolution)
                                                    val next = values[(i + 1) % values.size]
                                                    preferences.setXoraInternalResolution(next)
                                                    applyCoreOptionsLive(xora.copy(internalResolution = next))
                                                }
                                            },
                                            onCycleNdsLayout = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    val values = com.arcadia.shell.datastore.DualScreenLayout.entries
                                                    val i = values.indexOf(xora.ndsScreenLayout)
                                                    val next = values[(i + 1) % values.size]
                                                    preferences.setXoraNdsScreenLayout(next)
                                                    applyCoreOptionsLive(xora.copy(ndsScreenLayout = next))
                                                }
                                            },
                                            onCycle3dsLayout = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    val values = com.arcadia.shell.datastore.ThreeDsScreenLayout.entries
                                                    val i = values.indexOf(xora.threeDsScreenLayout)
                                                    val next = values[(i + 1) % values.size]
                                                    preferences.setXora3dsScreenLayout(next)
                                                    applyCoreOptionsLive(xora.copy(threeDsScreenLayout = next))
                                                }
                                            },
                                            onToggleRa = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    preferences.setRaEnabled(!raPrefs.enabled)
                                                }
                                            },
                                            onToggleHardcore = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    val next = !raPrefs.hardcore
                                                    preferences.setRaHardcore(next)
                                                    LibretroNative.nativeRaSetHardcore(next)
                                                }
                                            },
                                            onToggleNetplay = {
                                                uiSounds.playConfirm()
                                                lifecycleScope.launch {
                                                    preferences.setXoraNetplayEnabled(!xora.netplayEnabled)
                                                }
                                            },
                                            onOpenControllers = {
                                                uiSounds.playConfirm()
                                                openControllers()
                                            },
                                        )
                                        BackHandler {
                                            uiSounds.playCancel()
                                            closeInGameSettings()
                                        }
                                    }
                                    achievementsOpen -> {
                                        XoraEmulatorAchievementsPanel(
                                            title = gameTitle,
                                            summary = raStatusText ?: LibretroNative.nativeRaSummary()?.let { "RA: $it" },
                                            achievements = raAchievements,
                                            focusedIndex = focusedMenuIndex,
                                            onFocus = { focusedMenuIndex = it },
                                            onBack = {
                                                uiSounds.playCancel()
                                                closeAchievements()
                                            },
                                            onRefresh = {
                                                uiSounds.playConfirm()
                                                refreshAchievementList()
                                            },
                                        )
                                        BackHandler {
                                            uiSounds.playCancel()
                                            closeAchievements()
                                        }
                                    }
                                    else -> {
                                        XoraEmulatorPauseMenu(
                                            title = gameTitle,
                                            subtitle = coreName.ifBlank { "XOrA Emulator" },
                                            status = statusText,
                                            raStatus = raStatusText,
                                            netplayStatus = netplayStatus,
                                            actions = menuActions.map { it.label },
                                            focusedIndex = focusedMenuIndex,
                                            onFocus = { focusedMenuIndex = it },
                                            onActivate = { index -> activateMenuAction(index) },
                                            onDismiss = {
                                                uiSounds.playCancel()
                                                closeMenu()
                                            },
                                        )
                                        BackHandler {
                                            uiSounds.playCancel()
                                            closeMenu()
                                        }
                                    }
                                }
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
                                    .xoraEmulatorPanel()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
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
                statusText = withContext(emuDispatcher) {
                    LibretroNative.nativeLastError()
                } ?: "Failed to load game"
                bootOverlayVisible = false
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
            statusText = when {
                !saveImport.message.isNullOrBlank() && restored ->
                    "${saveImport.message} · Resumed previous session"
                !saveImport.message.isNullOrBlank() -> saveImport.message!!
                restored -> "Resumed previous session"
                expandActive -> "Expanded · top primary / bottom secondary"
                else -> ""
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
                    if (mappingOpen && waitingForMapButton != null) {
                        val button = waitingForMapButton
                        if (button != null) {
                            if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
                                waitingForMapButton = null
                                uiSounds.playCancel()
                                return true
                            }
                            lifecycleScope.launch {
                                val current = preferences.xoraEmulatorSettings.first()
                                    .buttonMappings.toMutableMap()
                                current.entries.removeAll { it.value == button }
                                current[keyCode] = button
                                preferences.setXoraButtonMappings(current)
                                waitingForMapButton = null
                            }
                            uiSounds.playConfirm()
                            return true
                        }
                    }
                    if (mappingOpen) {
                        val mappingCount = 1 + LibretroPad.MAPPABLE_BUTTONS.size
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusedMenuIndex =
                                    (focusedMenuIndex - 1 + mappingCount) % mappingCount
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusedMenuIndex = (focusedMenuIndex + 1) % mappingCount
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                            -> {
                                if (focusedMenuIndex == 0) {
                                    uiSounds.playCancel()
                                    closeButtonMapping()
                                } else {
                                    val button = LibretroPad.MAPPABLE_BUTTONS
                                        .getOrNull(focusedMenuIndex - 1)?.first
                                    if (button != null) {
                                        uiSounds.playConfirm()
                                        waitingForMapButton = button
                                    }
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                                uiSounds.playCancel()
                                closeButtonMapping()
                                return true
                            }
                        }
                        return LibretroPad.run { event.isFromGameController(customMappings) }
                    }
                    if (controllersOpen) {
                        val controllersCount = CONTROLLERS_ROW_COUNT
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusedMenuIndex =
                                    (focusedMenuIndex - 1 + controllersCount) % controllersCount
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusedMenuIndex = (focusedMenuIndex + 1) % controllersCount
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                            -> {
                                activateControllersRow(focusedMenuIndex)
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                                uiSounds.playCancel()
                                closeControllers()
                                return true
                            }
                        }
                        return LibretroPad.run { event.isFromGameController(customMappings) }
                    }
                    if (settingsOpen) {
                        val settingsCount = IN_GAME_SETTINGS_ROW_COUNT
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusedMenuIndex =
                                    (focusedMenuIndex - 1 + settingsCount) % settingsCount
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusedMenuIndex = (focusedMenuIndex + 1) % settingsCount
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                            -> {
                                activateInGameSetting(focusedMenuIndex)
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                                uiSounds.playCancel()
                                closeInGameSettings()
                                return true
                            }
                        }
                        return LibretroPad.run { event.isFromGameController(customMappings) }
                    }
                    if (achievementsOpen) {
                        // 0 = Back, 1 = Refresh, then one row per achievement.
                        val count = 2 + raAchievements.size
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusedMenuIndex =
                                    (focusedMenuIndex - 1 + count.coerceAtLeast(1)) %
                                        count.coerceAtLeast(1)
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusedMenuIndex =
                                    (focusedMenuIndex + 1) % count.coerceAtLeast(1)
                                uiSounds.playCursor()
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                            -> {
                                when (focusedMenuIndex) {
                                    0 -> {
                                        uiSounds.playCancel()
                                        closeAchievements()
                                    }
                                    1 -> {
                                        uiSounds.playConfirm()
                                        refreshAchievementList()
                                    }
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                                uiSounds.playCancel()
                                closeAchievements()
                                return true
                            }
                        }
                        return LibretroPad.run { event.isFromGameController(customMappings) }
                    }
                    val actions = buildMenuActions()
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusedMenuIndex =
                                (focusedMenuIndex - 1 + actions.size) % actions.size
                            uiSounds.playCursor()
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedMenuIndex = (focusedMenuIndex + 1) % actions.size
                            uiSounds.playCursor()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                        -> {
                            activateMenuAction(focusedMenuIndex)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                            uiSounds.playCancel()
                            closeMenu()
                            return true
                        }
                    }
                    return LibretroPad.run { event.isFromGameController(customMappings) }
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
                        LibretroPad.keyCodeToButton(keyCode, customMappings) != null
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

    private fun applyCoreOptionsLive(settings: XoraEmulatorSettings) {
        refreshExpandTopology()
        val vars = XoraCoreOptions.variablesFor(
            platformId = platformId,
            coreName = coreName,
            settings = settings,
            expandActive = expandActive,
        )
        lifecycleScope.launch(emuDispatcher) {
            vars.forEach { (key, value) ->
                LibretroNative.nativeSetCoreVariable(key, value)
            }
        }
    }

    private fun activateInGameSetting(index: Int) {
        if (index == 0) {
            uiSounds.playCancel()
        } else {
            uiSounds.playConfirm()
        }
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
            8 -> openControllers()
        }
    }

    private fun activateControllersRow(index: Int) {
        if (index == 0) {
            uiSounds.playCancel()
        } else {
            uiSounds.playConfirm()
        }
        when (index) {
            0 -> closeControllers()
            1 -> lifecycleScope.launch {
                val list = listOf("") + LibretroPad.connectedControllerNames()
                val current = xoraSettings.preferredControllerName
                val idx = list.indexOf(current).let { if (it >= 0) it else 0 }
                val next = list[(idx + 1) % list.size]
                preferences.setXoraPreferredControllerName(next)
            }
            2 -> openButtonMapping()
            3 -> lifecycleScope.launch {
                preferences.clearXoraButtonMappings()
                preferences.setXoraPreferredControllerName("")
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

    private fun openMenu() {
        focusedMenuIndex = 0
        settingsOpen = false
        achievementsOpen = false
        controllersOpen = false
        mappingOpen = false
        waitingForMapButton = null
        menuOpen = true
        paused = true
        uiSounds.playConfirm()
    }

    private fun closeMenu() {
        menuOpen = false
        settingsOpen = false
        achievementsOpen = false
        controllersOpen = false
        mappingOpen = false
        waitingForMapButton = null
        paused = false
        statusText = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        ImmersiveMode.apply(window)
        window.decorView.requestFocus()
    }

    private fun activateMenuAction(index: Int) {
        uiSounds.playConfirm()
        buildMenuActions().getOrNull(index)?.onClick?.invoke()
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
        private const val IN_GAME_SETTINGS_ROW_COUNT = 9
        private const val CONTROLLERS_ROW_COUNT = 4
    }
}

private data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
)

/** Opaque dark chrome for in-emulator menus — never use [liquidGlass] here (white sheen wash). */
private val XoraEmulatorPanelShape = RoundedCornerShape(18.dp)
private val XoraEmulatorPanelColor = Color(0xFF14161C)
private val XoraEmulatorPanelBorder = Color.White.copy(alpha = 0.14f)

private fun Modifier.xoraEmulatorPanel(shape: Shape = XoraEmulatorPanelShape): Modifier =
    this
        .clip(shape)
        .background(XoraEmulatorPanelColor, shape)
        .border(width = 1.dp, color = XoraEmulatorPanelBorder, shape = shape)

@Composable
private fun XoraPrimaryGameFrame(
    bitmap: Bitmap,
    frameTick: Int,
    platformId: String,
    aspectMode: XoraAspectMode,
    integerScale: Int,
    bezelsEnabled: Boolean,
    bezelOpacity: Float,
    onImageView: (ImageView?) -> Unit = {},
) {
    val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    val frame: @Composable () -> Unit = {
        XoraScaledGameFrame(
            contentWidthPx = bitmap.width,
            contentHeightPx = bitmap.height,
            mode = aspectMode,
            integerScaleCap = integerScale,
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            XoraGameImageView(
                bitmap = bitmap,
                frameTick = frameTick,
                aspectMode = aspectMode,
                onImageView = onImageView,
                modifier = Modifier.fillMaxSize(),
            )
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

/**
 * Classic View framebuffer — avoids Compose Image + asImageBitmap texture/premul wash after
 * pause overlays. The same Bitmap instance is mutated via setPixels; we only invalidate.
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
                .xoraEmulatorPanel()
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
    netplayStatus: String?,
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
                    .xoraEmulatorPanel()
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
            if (!netplayStatus.isNullOrBlank()) {
                XoraSecondaryText(
                    text = netplayStatus,
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
    onOpenControllers: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val controllerSubtitle = when {
        xora.preferredControllerName.isNotBlank() -> xora.preferredControllerName
        else -> "Any controller"
    }
    val mappingSubtitle = if (xora.buttonMappings.isNotEmpty()) {
        "${xora.buttonMappings.size} custom"
    } else {
        "Default"
    }
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
        "Controllers" to "$controllerSubtitle · $mappingSubtitle",
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
                    .xoraEmulatorPanel()
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
                        8 -> onOpenControllers()
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

@Composable
private fun XoraEmulatorControllersPanel(
    preferredName: String,
    mappingCount: Int,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onBack: () -> Unit,
    onCycleController: () -> Unit,
    onOpenMapping: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val controllerLabel = preferredName.ifBlank { "Any controller" }
    val mappingLabel = if (mappingCount > 0) "$mappingCount custom" else "Default"
    val rows = listOf(
        "Back" to "Return to settings",
        "Controller" to controllerLabel,
        "Button mapping" to mappingLabel,
        "Reset to defaults" to "Clear preferred pad and remaps",
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
                    .xoraEmulatorPanel()
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
                    text = "Controllers",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    maxLines = 1,
                )
                XoraSecondaryText(
                    text = "A cycles · B / Back returns",
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
                            1 -> onCycleController()
                            2 -> onOpenMapping()
                            3 -> onResetDefaults()
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (focused) {
                                    Color.White.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
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

@Composable
private fun XoraEmulatorMappingPanel(
    mappings: Map<Int, Int>,
    waitingForButton: Int?,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onBack: () -> Unit,
    onStartCapture: (Int) -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val buttonRows = LibretroPad.MAPPABLE_BUTTONS.map { (button, label) ->
        val keyCode = mappings.entries.firstOrNull { it.value == button }?.key
        val bound = if (keyCode != null) LibretroPad.keyCodeLabel(keyCode) else "Default"
        label to bound
    }

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
                    .xoraEmulatorPanel()
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
                    text = "Button mapping",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    maxLines = 1,
                )
                XoraSecondaryText(
                    text = "A assigns · B / Back returns",
                    fontSize = 12.sp,
                    fillColor = glass.contentMuted,
                    maxLines = 1,
                )
                if (waitingForButton != null) {
                    Text(
                        text = "Press a button for ${LibretroPad.buttonLabel(waitingForButton)}…",
                        color = glass.content,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                val backFocused = focusedIndex == 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (backFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            onFocus(0)
                            onBack()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Back",
                        color = if (backFocused) glass.content else glass.contentMuted,
                        fontWeight = if (backFocused) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (backFocused) 17.sp else 14.sp,
                    )
                    Text(
                        text = "Return to controllers",
                        color = glass.contentMuted,
                        fontSize = 11.sp,
                    )
                }
                buttonRows.forEachIndexed { index, (title, subtitle) ->
                    val rowIndex = index + 1
                    val focused = rowIndex == focusedIndex
                    val button = LibretroPad.MAPPABLE_BUTTONS[index].first
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                onFocus(rowIndex)
                                onStartCapture(button)
                            }
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

@Composable
private fun XoraEmulatorAchievementsPanel(
    title: String,
    summary: String?,
    achievements: List<RaLiveAchievement>,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val listState = rememberLazyListState()
    val unlocked = achievements.count { it.unlocked }
    val progressLabel = if (achievements.isEmpty()) {
        summary?.takeIf { it.isNotBlank() } ?: "No achievements loaded yet"
    } else {
        "$unlocked / ${achievements.size} unlocked"
    }

    LaunchedEffect(focusedIndex, achievements.size) {
        val listIndex = when {
            focusedIndex <= 1 -> 0
            else -> (focusedIndex - 1).coerceIn(0, achievements.size.coerceAtLeast(1))
        }
        listState.animateScrollToItem(listIndex)
    }

    // Opaque constrained panel over the shared pause dim — never a full-bleed frost plate.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.78f),
        ) {
            val panelHeight = maxHeight * 0.88f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .xoraEmulatorPanel(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        XoraTitleText(
                            text = "RetroAchievements",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp,
                            maxLines = 1,
                        )
                        XoraSecondaryText(
                            text = "$title · $progressLabel",
                            fontSize = 13.sp,
                            fillColor = glass.contentMuted,
                            maxLines = 1,
                        )
                    }
                    AchievementsToolbarChip(
                        label = "Back",
                        focused = focusedIndex == 0,
                        onClick = {
                            onFocus(0)
                            onBack()
                        },
                        content = glass.content,
                        muted = glass.contentMuted,
                    )
                    AchievementsToolbarChip(
                        label = "Refresh",
                        focused = focusedIndex == 1,
                        onClick = {
                            onFocus(1)
                            onRefresh()
                        },
                        content = glass.content,
                        muted = glass.contentMuted,
                    )
                }

                if (achievements.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        XoraSecondaryText(
                            text = "Achievements appear here after RetroAchievements finishes loading this ROM.",
                            fontSize = 14.sp,
                            fillColor = glass.contentMuted,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        itemsIndexed(achievements, key = { _, item -> item.id }) { index, achievement ->
                            val rowFocus = index + 2
                            EmulatorAchievementRow(
                                achievement = achievement,
                                focused = focusedIndex == rowFocus,
                                onClick = { onFocus(rowFocus) },
                                content = glass.content,
                                muted = glass.contentMuted,
                            )
                        }
                    }
                }

                XoraSecondaryText(
                    text = "B / Back returns · A selects · D-pad scrolls",
                    fontSize = 11.sp,
                    fillColor = glass.contentMuted,
                )
            }
        }
    }
}

@Composable
private fun AchievementsToolbarChip(
    label: String,
    focused: Boolean,
    onClick: () -> Unit,
    content: Color,
    muted: Color,
) {
    Text(
        text = label,
        color = if (focused) content else muted,
        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
        fontSize = 14.sp,
        modifier = Modifier
            .background(
                if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun EmulatorAchievementRow(
    achievement: RaLiveAchievement,
    focused: Boolean,
    onClick: () -> Unit,
    content: Color,
    muted: Color,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(achievement.badgeUrl.takeIf { it.isNotBlank() })
                .crossfade(120)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = if (achievement.unlocked) 0.12f else 0.08f)),
            alpha = if (achievement.unlocked) 1f else 0.45f,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = achievement.title,
                color = if (achievement.unlocked) content else muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(achievement.description)
                    if (achievement.progress.isNotBlank() && !achievement.unlocked) {
                        append(" · ")
                        append(achievement.progress)
                    }
                    if (achievement.hardcore) append(" · Hardcore")
                },
                color = muted,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${achievement.points}",
            color = if (focused || achievement.unlocked) content else muted,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}
