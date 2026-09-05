package com.arcadia.shell.libretro

import android.Manifest
import android.app.AppOpsManager
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.provider.Settings
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.PixelCopy
import android.graphics.drawable.GradientDrawable
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
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
import com.arcadia.shell.datastore.label
import com.arcadia.shell.datastore.next
import com.arcadia.shell.datastore.nextPublic
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.LocalArcadiaHaze
import com.arcadia.shell.display.DisplayRefresh
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.display.ExpandDualDisplayMessages
import com.arcadia.shell.display.ImmersiveMode
import com.arcadia.shell.display.OverlayPermission
import com.arcadia.shell.display.SecondDisplayAttachResult
import com.arcadia.shell.display.SecondDisplayImageHost
import com.arcadia.shell.display.applyXoraScreenOrientation
import com.arcadia.shell.feature.home.EmulatorMenuAction
import com.arcadia.shell.feature.home.EmulatorSaveSlotUi
import com.arcadia.shell.feature.home.GameCompanionController
import com.arcadia.shell.feature.home.LocalInGameXmbController
import com.arcadia.shell.feature.home.NetplayInvitePrompt
import com.arcadia.shell.feature.home.XoraEmulatorSideMenu
import com.arcadia.shell.feature.home.XoraInGameXmbController
import com.arcadia.shell.launcher.discord.DiscordPresenceActivity
import com.arcadia.shell.launcher.discord.DiscordRichPresence
import com.arcadia.shell.feature.home.component.NetplayInvitePromptDialog
import com.arcadia.shell.feature.home.component.NetplaySeatOption
import com.arcadia.shell.feature.home.component.NetplaySeatPickerDialog
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.ShellNotificationHistoryItem
import com.arcadia.shell.launcher.notifications.netplaySessionDismissalKey
import com.arcadia.shell.libretro.netplay.AzaharLobbyUi
import com.arcadia.shell.libretro.netplay.AzaharPretendo
import com.arcadia.shell.libretro.netplay.AzaharPretendoUi
import com.arcadia.shell.libretro.netplay.AzaharPublicLobbies
import com.arcadia.shell.libretro.netplay.AzaharPublicRoom
import com.arcadia.shell.libretro.netplay.NetplaySessionMode
import com.arcadia.shell.libretro.netplay.XoraNetplayExchange
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.libretro.netplay.XoraNetplayRole
import com.arcadia.shell.libretro.netplay.XoraNetplaySession
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState
import com.arcadia.shell.libretro.netplay.XoraNetplayVideo
import com.arcadia.shell.libretro.netplay.gbaLockstepGenerationKey
import com.arcadia.shell.libretro.netplay.gbaLockstepHiddenPort
import com.arcadia.shell.libretro.netplay.gbaLockstepLocalSlot
import com.arcadia.shell.libretro.netplay.gbaLockstepPlayerCount
import com.arcadia.shell.libretro.netplay.gbaNetplayClientId
import com.arcadia.shell.libretro.netplay.netplayCoreName
import com.arcadia.shell.libretro.netplay.resolveGbaLockstepRomPath
import com.arcadia.shell.libretro.netplay.shouldArmGbaLinkCable
import com.arcadia.shell.libretro.netplay.shouldMirrorGbaLockstepPartnerPad
import com.arcadia.shell.libretro.netplay.shouldStartGbaLockstep
import com.arcadia.shell.libretro.netplay.shouldStartGbaNetpacket
import com.arcadia.shell.libretro.netplay.usesGbaLockstep
import com.arcadia.shell.libretro.netplay.formatJoinHostPort
import com.arcadia.shell.libretro.netplay.parseJoinHostPort
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaProfile
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scraper.RomHasher
import com.arcadia.shell.xoranetwork.XoraNetworkAuthCookies
import com.arcadia.shell.xoranetwork.XoraNetworkRepository
import com.arcadia.shell.xoranetwork.XoraNetworkState
import com.arcadia.shell.xoranetwork.XoraNetplayInviteRecord
import com.arcadia.shell.xoranetwork.XoraNetplayInvites
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
    @Inject lateinit var discordRichPresence: DiscordRichPresence
    @Inject lateinit var gameCompanionController: GameCompanionController

    @Volatile private var menuOpen = false
    /** True while the in-game menu is showing or the user left Pause on. */
    @Volatile private var paused = false
    /** Stays paused after the side menu closes until Resume is chosen. */
    @Volatile private var userPaused = false
    private var userPausedUi by mutableStateOf(false)
    /** True while the activity is backgrounded (home/recents) — pauses the frame loop. */
    @Volatile private var activityInBackground = false
    private var gameLoaded = false
    /** True only for an explicit Quit to XOrA — unexpected finish must keep the autosave. */
    private var quitDiscardsAutosave = false
    private var raSession: LibretroRaSession? = null

    private var runJob: Job? = null
    private var audioTrack: AudioTrack? = null
    /** Live gameplay bitmap presented via ImageView (updated in place + invalidate). */
    private var gameBitmap: Bitmap? = null
    private var lastRemoteBitmap: Bitmap? = null
    private var bottomBitmap by mutableStateOf<Bitmap?>(null)
    private var frameTick by mutableIntStateOf(0)
    private var primaryGameView: ImageView? = null
    private var secondaryGameView: ImageView? = null
    private var gameRoot: FrameLayout? = null
    private var stage: XoraEmulatorStage? = null
    private var xmbOverlay: ComposeView? = null
    /** Full-screen host for invite / seat-picker dialogs (the banner view is wrap-content). */
    private var dialogOverlay: ComposeView? = null
    /** Topmost profile disc — tap clears a leftover white wash. */
    private var profileChip: FrameLayout? = null
    private var profileChipImage: ImageView? = null
    private var profileChipLetter: TextView? = null
    private var netplayTouchPad: NetplayTouchPadView? = null
    private val netplayTouchButtons = AtomicInteger(0)
    /** gpSP netpacket starts once a second player links. Unused while GBA uses lockstep. */
    private val gbaNetpacketStarted = AtomicBoolean(false)
    private val gbaNetpacketPeers = ConcurrentHashMap.newKeySet<Int>()
    /** GBA lockstep is started once per lobby generation. A failed start must not retry every frame. */
    private val gbaLockstepAttempted = AtomicBoolean(false)
    @Volatile private var gbaLockstepKey = ""
    private var filledAdvertisedHost = false
    private var profileName by mutableStateOf("Player")
    /** Feedback shown inside the pause menu. A toast would pull focus off the game window. */
    private var menuMessage by mutableStateOf<String?>(null)
    private var menuMessageJob: Job? = null
    private var saveSlots by mutableStateOf(List(10) { EmulatorSaveSlotUi(it, false, "Empty") })
    private var joinAddress by mutableStateOf("")
    private var joinPort by mutableStateOf(DEFAULT_NETPLAY_PORT)
    private var joinCode by mutableStateOf("")
    private var netplayUi by mutableStateOf(XoraNetplayUiState())
    private var azaharLobbyUi by mutableStateOf(AzaharLobbyUi())
    private var pretendoUi by mutableStateOf(AzaharPretendoUi())
    private var netplaySession: XoraNetplaySession? = null
    private var pendingNetplayHost = false
    private var pendingNetplayJoin = false
    private var pendingInvitePrompt by mutableStateOf<NetplayInvitePrompt?>(null)
    private var invitePromptOpen by mutableStateOf(false)
    private val consumedNetplayInviteKeys = linkedSetOf<String>()
    private var notificationHistory by mutableStateOf<List<ShellNotificationHistoryItem>>(emptyList())
    private var notificationUnread by mutableIntStateOf(0)
    private var seatPickerOpen by mutableStateOf(false)
    private var seatPickerFocus by mutableIntStateOf(0)
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val host = pendingNetplayHost
        val join = pendingNetplayJoin
        pendingNetplayHost = false
        pendingNetplayJoin = false
        if (!granted) {
            showMenuMessage("Allow Nearby devices so netplay can use this Wi‑Fi")
            return@registerForActivityResult
        }
        if (host) beginHostNetplay()
        if (join) beginJoinNetplay()
    }
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
    /** The window background drawable only needs loading once; reloading it churns the decor. */
    private var opaqueWindowBackgroundSet = false
    /** Force-dark is a render-time lift: views still report black, PixelCopy comes back grey. */
    private var forceDarkPinned = false
    /**
     * Overlay-hide and display-pipeline pins have no cheap getters. Re-assert on resume / focus,
     * not every vsync — [setHideOverlayWindows] talks to WindowManager.
     */
    private var displayPipelinePinned = false
    /**
     * Host for RA unlock banners and the dual-screen pane. It is added directly above the game
     * stage and below the side menu, which is the one z-band that tints the framebuffer without
     * touching the overlay — so it is kept GONE unless it genuinely has something to show.
     */
    private var bannerOverlay: ComposeView? = null
    @Volatile private var bannerHostNeeded = false
    /** Long-press the profile disc: on-screen wash report. Removed on tap. */
    private var washReport: View? = null
    private val washFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            washFramePosted = false
            if (isFinishing || activityInBackground) return
            reconcileMenuState()
            pinOpaqueWindow()
            if (!menuOpen) pinGameplaySurface()
            if (seatPickerOpen || invitePromptOpen) {
                dialogOverlay?.bringToFront()
            } else {
                keepProfileChipOnTop()
            }
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

    private val padMixer = LibretroPadMixer()
    private var emuFrameIndex = 0

    private var platformId: String = "unknown"
    private var gameId: String = "game"
    private var gameTitle: String = "Game"
    private var coreName: String = ""
    private var romFilePath: String? = null
    private var selectHeld = false
    private var startHeld = false
    /** Latches the Select+Start chord so one press is one toggle, not one per auto-repeat. */
    private var chordFired = false
    private var xoraSettings = XoraEmulatorSettings()
    private var raSettings = RetroAchievementsSettings()
    private var expandActive by mutableStateOf(false)
    private var secondaryDisplayId by mutableStateOf<Int?>(null)
    private var expandSplitKind by mutableStateOf(DualScreenSplitKind.Stacked)
    private val secondDisplayHost by lazy { SecondDisplayImageHost(this) }

    private var inputManager: InputManager? = null
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = syncTouchPad()
        override fun onInputDeviceRemoved(deviceId: Int) {
            padMixer.forget(deviceId)
            syncTouchPad()
        }
        override fun onInputDeviceChanged(deviceId: Int) = syncTouchPad()
    }

    private fun refreshExpandTopology() {
        val presentation = DisplayTopologyMonitor(this).current().presentationDisplay?.displayId
        secondaryDisplayId = presentation
        expandActive = xoraSettings.expandDualDisplay &&
            platformId in DUAL_SCREEN_PLATFORMS &&
            presentation != null
    }

    /**
     * Attach or tear down the second-display ImageView. Must run on the Activity, not inside
     * the banner Compose host — gameplay pinning disposes that composition.
     */
    private fun syncExpandDisplay(announce: Boolean): SecondDisplayAttachResult {
        refreshExpandTopology()
        applyStageSettings(xoraSettings)
        val result = when {
            !xoraSettings.expandDualDisplay || platformId !in DUAL_SCREEN_PLATFORMS -> {
                secondDisplayHost.dismiss()
                secondaryGameView = null
                expandActive = false
                SecondDisplayAttachResult.Hidden
            }
            secondaryDisplayId == null -> {
                secondDisplayHost.dismiss()
                secondaryGameView = null
                expandActive = false
                SecondDisplayAttachResult.NoDisplay
            }
            else -> {
                val attach = secondDisplayHost.show(
                    displayId = secondaryDisplayId!!,
                    restart = announce,
                )
                secondaryGameView = secondDisplayHost.imageView
                secondaryGameView?.let { view ->
                    synchronized(bitmapLock) {
                        val bmp = bottomBitmap
                        if (bmp != null && !bmp.isRecycled) {
                            view.setImageBitmap(bmp)
                        }
                    }
                    bindPointerTouch(view, expandSplitKind.bottomPointerTarget)
                }
                expandActive = attach == SecondDisplayAttachResult.ShownPresentation ||
                    attach == SecondDisplayAttachResult.ShownOverlay
                attach
            }
        }
        bindExpandPointers()
        if (announce) announceExpand(result)
        return result
    }

    private fun bindExpandPointers() {
        bindPointerTouch(
            primaryGameView,
            if (expandActive) {
                DualScreenPointerTarget.TopHalf
            } else {
                DualScreenPointerTarget.Combined
            },
        )
        if (expandActive) {
            bindPointerTouch(secondaryGameView, expandSplitKind.bottomPointerTarget)
        }
    }

    private fun announceExpand(result: SecondDisplayAttachResult) {
        showMenuMessage(ExpandDualDisplayMessages.forResult(result))
        if (result == SecondDisplayAttachResult.NeedsOverlayPermission) {
            runCatching { startActivity(OverlayPermission.settingsIntent(this)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyXoraScreenOrientation()
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
        DisplayRefresh.preferSixtyHertz(window)

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
        window.takeKeyEvents(true)

        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)

        window.setFormat(PixelFormat.OPAQUE)
        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.BLACK)
            isForceDarkAllowed = false
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
        bindPointerTouch(stageView.gameView, DualScreenPointerTarget.Combined)
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
        bannerOverlay = banners
        banners.visibility = View.GONE
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
        val dialogs = ComposeView(this).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_NONE, null)
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.GONE
        }
        dialogOverlay = dialogs
        root.addView(dialogs)
        root.addView(createProfileChip())
        root.addView(createNetplayTouchPad())

        setContentView(root)
        applyOpaqueWindow()
        startWashGuard()
        netplaySession = XoraNetplaySession(lifecycleScope)
        lifecycleScope.launch {
            var wasLinked = false
            var lastPlayerCount = 1
            var lastPlayerNames = emptyMap<Int, String>()
            netplaySession?.state?.collect { ui ->
                netplayUi = ui
                if (ui.role == XoraNetplayRole.Idle) {
                    if (wasLinked ||
                        gbaNetpacketStarted.get() ||
                        gbaLockstepAttempted.get() ||
                        gbaLockstepKey.isNotEmpty()
                    ) {
                        gbaNetpacketStarted.set(false)
                        gbaNetpacketPeers.clear()
                        gbaLockstepAttempted.set(false)
                        gbaLockstepKey = ""
                        lifecycleScope.launch(emuDispatcher) {
                            LibretroNative.nativeNetpacketStop()
                            LibretroNative.nativeGbaLinkStop()
                            LibretroNative.nativeGbaSioSetEnabled(false)
                        }
                    }
                }
                if (ui.role == XoraNetplayRole.Client &&
                    !filledAdvertisedHost &&
                    ui.advertisedHostAddresses.isNotEmpty()
                ) {
                    filledAdvertisedHost = true
                    applyJoinTarget(
                        ui.advertisedHostAddresses.first(),
                        ui.advertisedHostPort.takeIf { it > 0 } ?: DEFAULT_NETPLAY_PORT,
                    )
                }
                if (ui.role == XoraNetplayRole.Idle) filledAdvertisedHost = false
                syncTouchPad()
                ui.error?.let { showMenuMessage(it) }
                if (ui.linked) {
                    lifecycleScope.launch { disableHardcoreForNetplay() }
                }
                val hostUsername = ui.playerNames[1]
                    ?.takeIf { it.isNotBlank() } ?: "the host"
                if (ui.linked && wasLinked && ui.playerCount > lastPlayerCount) {
                    // A third or fourth player joined an already-linked session.
                    lifecycleScope.launch(emuDispatcher) {
                        applyCoreControllerOptions()
                    }
                    val newSlots = ui.playerNames.keys - lastPlayerNames.keys - ui.playerSlot
                    val joined = newSlots.sorted()
                        .firstNotNullOfOrNull { ui.playerNames[it] } ?: "A player"
                    val line = if (ui.playerSlot == 1) {
                        "$joined joined your session · ${ui.playerCount} players"
                    } else {
                        "$joined joined $hostUsername's session · ${ui.playerCount} players"
                    }
                    showMenuMessage(line)
                    shellNotifications.emit(
                        ShellNotification.XoraSessionJoined(
                            id = "xora-joined:${ui.playerCount}:${SystemClock.elapsedRealtime()}",
                            displayName = joined,
                            detail = line,
                        ),
                    )
                }
                if (!ui.linked) seatPickerOpen = false
                if (ui.linked && !wasLinked) {
                    lifecycleScope.launch(emuDispatcher) {
                        applyCoreControllerOptions()
                    }
                    audioTrack?.play()
                    hideSoftKeyboard()
                    window.decorView.requestFocus()
                    val line: String
                    val bannerName: String
                    if (ui.role == XoraNetplayRole.Host) {
                        bannerName = ui.peerName.ifBlank { "A player" }
                        line = "$bannerName joined your session"
                    } else {
                        bannerName = hostUsername
                        line = "Joined $hostUsername's session — you are Player ${ui.playerSlot}"
                        // Do not auto-open the seat picker: it used to sit in a wrap-content
                        // corner view, eat every button, and send idle P2 pads until dismissed.
                    }
                    showMenuMessage(line)
                    shellNotifications.emit(
                        ShellNotification.XoraSessionJoined(
                            id = "xora-joined:${ui.peerName}:${SystemClock.elapsedRealtime()}",
                            displayName = bannerName,
                            detail = line,
                        ),
                    )
                    if (menuOpen) {
                        setUserPaused(false)
                        closeMenu()
                    }
                }
                wasLinked = ui.linked
                lastPlayerCount = if (ui.linked) ui.playerCount.coerceAtLeast(1) else 1
                lastPlayerNames = if (ui.linked) ui.playerNames else emptyMap()
            }
        }
        lifecycleScope.launch {
            shellNotifications.history.collect { notificationHistory = it }
        }
        lifecycleScope.launch {
            shellNotifications.unreadCount.collect { notificationUnread = it }
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
            // An always-VISIBLE Compose host over a live framebuffer is a wash waiting to happen,
            // and this one has nothing to draw the vast majority of a session. Show it only while
            // a banner is up or the dual-screen pane is mounted.
            val activeBanner by shellNotifications.active.collectAsStateWithLifecycle()
            LaunchedEffect(activeBanner) {
                bannerHostNeeded = activeBanner != null
                syncBannerHost()
            }
            val raPrefs by preferences.retroAchievementsSettings.collectAsStateWithLifecycle(
                initialValue = RetroAchievementsSettings(),
            )
            LaunchedEffect(raPrefs) { raSettings = raPrefs }

            // Always dark + Haze killed for the whole emulator session. liquidGlass frost over
            // anything near the framebuffer was the wash left after pause submenus / Resume.
            ArcadiaTheme(
                darkTheme = true,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
            ) {
                CompositionLocalProvider(LocalArcadiaHaze provides null) {
                    Box(modifier = Modifier.wrapContentSize(align = Alignment.TopStart)) {
                        NotificationBannerHost(
                            center = shellNotifications,
                            onActivate = { notification ->
                                if (notification is ShellNotification.XoraNetplayInvite) {
                                    pendingInvitePrompt = promptFromNotification(notification)
                                    invitePromptOpen = true
                                    syncDialogOverlay()
                                }
                            },
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            preferences.xoraEmulatorSettings.collect { xora ->
                val expandChanged = xora.expandDualDisplay != xoraSettings.expandDualDisplay
                xoraSettings = xora
                azaharLobbyUi = azaharLobbyUi.copy(
                    standaloneInstalled =
                        AzaharPublicLobbies.installedStandalonePackage(packageManager) != null,
                )
                if (platformId.equals("3ds", ignoreCase = true)) {
                    refreshPretendoStatus(xora.threeDsPretendoPrep)
                }
                applyAudioVolume(xora.audioVolume)
                syncExpandDisplay(announce = expandChanged)
                if (gameLoaded && platformId in DUAL_SCREEN_PLATFORMS) {
                    withContext(emuDispatcher) { applyCoreControllerOptions() }
                    bindExpandPointers()
                }
            }
        }
        lifecycleScope.launch {
            DisplayTopologyMonitor(this@XoraLibretroActivity).topology().collect {
                syncExpandDisplay(announce = false)
                if (gameLoaded && platformId in DUAL_SCREEN_PLATFORMS) {
                    withContext(emuDispatcher) { applyCoreControllerOptions() }
                    bindExpandPointers()
                }
            }
        }

        dialogOverlay?.setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(
                initialValue = ShellSettings(),
            )
            LaunchedEffect(seatPickerOpen, invitePromptOpen) {
                syncDialogOverlay()
            }
            ArcadiaTheme(
                darkTheme = true,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
            ) {
                CompositionLocalProvider(LocalArcadiaHaze provides null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NetplayInvitePromptDialog(
                            prompt = pendingInvitePrompt.takeIf { invitePromptOpen },
                            onJoin = { confirmInvitePrompt() },
                            onDecline = { dismissInvitePrompt() },
                        )
                        NetplaySeatPickerDialog(
                            visible = seatPickerOpen,
                            options = seatPickerOptions(),
                            focusIndex = seatPickerFocus,
                            onPick = { slot -> pickNetplaySeat(slot) },
                            onDismiss = { seatPickerOpen = false },
                        )
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
                        joinCode = joinCode,
                        message = menuMessage,
                        onAction = { handleEmulatorMenuAction(it) },
                        onDismiss = { closeMenu() },
                        network = xoraNetworkUi,
                        achievements = raAchievements,
                        achievementSummary = raAchievementSummary,
                        raStatus = raStatusLine,
                        notifications = notificationHistory,
                        notificationUnread = notificationUnread,
                        platformId = platformId,
                        publicLobbies = azaharLobbyUi,
                        pretendo = pretendoUi,
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
            if (platformId.equals("3ds", ignoreCase = true)) {
                val crypto = withContext(Dispatchers.IO) { ThreeDsCart.inspect(romPath) }
                if (crypto == ThreeDsCartCrypto.Encrypted) {
                    Toast.makeText(
                        this@XoraLibretroActivity,
                        ThreeDsCart.LOAD_ENCRYPTED_ERROR,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
            }
            // Core init + first frames must share one OS thread (Mupen/libco).
            val ok = withContext(emuDispatcher) {
                val xora = preferences.xoraEmulatorSettings.first()
                if (platformId.equals("3ds", ignoreCase = true) && xora.threeDsPretendoPrep) {
                    AzaharPretendo.ensureDirs(coreStore.saveDirFor("3ds"))
                }
                val expand = xora.expandDualDisplay &&
                    platformId in DUAL_SCREEN_PLATFORMS &&
                    DisplayTopologyMonitor(this@XoraLibretroActivity)
                        .current().presentationDisplay != null
                LibretroNative.nativeClearCoreVariables()
                LibretroNative.nativeSetNetplayUsername(xora.netplayNickname)
                XoraCoreOptions.variablesFor(
                    platformId = platformId,
                    coreName = coreName,
                    settings = xora,
                    expandActive = expand,
                    netplay = XoraCoreOptions.NetplayContext(),
                    romPath = romPath.orEmpty(),
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
                val shown = if (platformId.equals("3ds", ignoreCase = true)) {
                    ThreeDsCart.loadFailureMessage(romPath, err)
                } else {
                    err
                }
                Toast.makeText(this@XoraLibretroActivity, shown, Toast.LENGTH_LONG).show()
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
            val expandResult = syncExpandDisplay(announce = false)
            bindExpandPointers()
            val bootMsg = when {
                !saveImport.message.isNullOrBlank() && restored ->
                    "${saveImport.message} · Resumed previous session"
                !saveImport.message.isNullOrBlank() -> saveImport.message!!
                restored -> "Resumed previous session"
                expandResult == SecondDisplayAttachResult.ShownPresentation ||
                    expandResult == SecondDisplayAttachResult.ShownOverlay ->
                    "Expanded · top primary / bottom secondary"
                else -> null
            }
            if (!bootMsg.isNullOrBlank()) {
                Toast.makeText(this@XoraLibretroActivity, bootMsg, Toast.LENGTH_SHORT).show()
            }
            startRaSession(romPath)
            startAudio()
            startLoop()
            maybeJoinPendingNetplayInvite()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var ticks = 0
                while (isActive) {
                    if (xoraNetwork.state.value.signedIn) {
                        if (ticks % 4 == 0) xoraNetwork.refreshFriends()
                        xoraNetwork.refreshNetplayInvites()
                    }
                    ticks++
                    delay(4_000)
                }
            }
        }
        lifecycleScope.launch {
            xoraNetwork.state
                .map { it.netplayInvites }
                .distinctUntilChanged()
                .collect { invites ->
                    val next = invites.firstOrNull { invite ->
                        XoraNetplayInvites.hasJoinableCode(invite) &&
                            inviteConsumeKey(
                                invite.fromUsername.ifBlank { invite.fromDisplayName },
                                invite.code,
                            ) !in consumedNetplayInviteKeys
                    } ?: return@collect
                    offerNetplayInvite(next)
                }
        }
    }

    override fun onResume() {
        super.onResume()
        activityInBackground = false
        displayPipelinePinned = false
        applyOpaqueWindow()
        pinGameplaySurfaceRepeatedly()
        DisplayRefresh.preferSixtyHertz(window)
        startWashGuard()
        if (gameLoaded) restartAudio()
        uiSounds.onForeground()
        xoraNetwork.setRealtimeEnabled(true)
        xoraNetwork.setPlayingLine("playing $gameTitle")
        if (!menuOpen) window.decorView.requestFocus()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Lid close / fold / density shifts must not reload the core. Re-pin the window only.
        applyOpaqueWindow()
        DisplayRefresh.preferSixtyHertz(window)
        ImmersiveMode.apply(window)
        refreshExpandTopology()
        applyStageSettings(xoraSettings)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Regaining focus is exactly when a toast or system window has just gone away, which is
        // when the wash used to appear — so restore the whole window state, not just immersive.
        if (!hasFocus) {
            // Key-ups are not delivered once focus moves, so the chord halves would stay latched
            // and every later key press would toggle the menu.
            selectHeld = false
            startHeld = false
            chordFired = false
        }
        if (hasFocus) {
            displayPipelinePinned = false
            reconcileMenuState()
            pinDisplayPipeline()
            if (!menuOpen) {
                applyOpaqueWindow()
                pinGameplaySurfaceRepeatedly()
            }
        }
    }

    override fun onPause() {
        // Always snapshot before Android may kill us. Skipping this on configuration
        // changes used to reboot every core after a clamshell close / fold.
        activityInBackground = true
        displayPipelinePinned = false
        persistSessionForBackground()
        stopWashGuard()
        uiSounds.onBackground()
        xoraNetwork.setRealtimeEnabled(false)
        super.onPause()
    }

    override fun onStop() {
        activityInBackground = true
        persistSessionForBackground()
        super.onStop()
    }

    /** Intercept at dispatch so Compose focus cannot swallow gamepad KeyEvents. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handlePadKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handlePadKey(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (handlePadKey(event)) return true
        return super.onKeyUp(keyCode, event)
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
                if (invitePromptOpen) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            confirmInvitePrompt()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                            dismissInvitePrompt()
                            return true
                        }
                    }
                    return true
                }
                if (seatPickerOpen) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> seatPickerFocus = (seatPickerFocus + 2) % 3
                        KeyEvent.KEYCODE_DPAD_DOWN -> seatPickerFocus = (seatPickerFocus + 1) % 3
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            val option = seatPickerOptions().getOrNull(seatPickerFocus)
                            when {
                                option == null || option.isCurrent -> seatPickerOpen = false
                                option.taken -> showMenuMessage("Player ${option.slot} is taken")
                                else -> pickNetplaySeat(option.slot)
                            }
                        }
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK ->
                            seatPickerOpen = false
                    }
                    return true
                }
                if (pendingInvitePrompt != null &&
                    !menuOpen &&
                    (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B)
                ) {
                    invitePromptOpen = true
                    syncDialogOverlay()
                    return true
                }
                if (event.repeatCount > 0 && keyCode !in chordKeys) {
                    return LibretroPad.run { event.isFromGameController(customMappings) } ||
                        LibretroPad.padButtonFor(event, customMappings) != null
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
                // Edge-triggered. Chord keys are exempt from the repeat filter above, so a
                // level-triggered test fired toggleMenu() on every auto-repeat while the chord was
                // held — flipping the menu open/closed ~20x a second and leaving menuOpen wherever
                // the release happened to land. A stale `true` then silently gates off every wash
                // defence in this file, which is exactly the tint that would not go away.
                if (selectHeld && startHeld) {
                    if (!chordFired) {
                        chordFired = true
                        toggleMenu()
                    }
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (menuOpen) {
                        handleInGameXmbKey(keyCode)
                    } else if (pendingInvitePrompt != null) {
                        invitePromptOpen = true
                        syncDialogOverlay()
                    } else {
                        toggleMenu()
                    }
                    return true
                }
                val netplayLinked = netplayUi.linked
                val mappedBit = LibretroPad.padButtonFor(event, customMappings)
                if (menuOpen) {
                    val handled = handleInGameXmbKey(keyCode)
                    if (netplayLinked) {
                        mappedBit?.let { bit ->
                            padMixer.keyDown(event.deviceId, bit)
                        }
                    }
                    return handled || netplayLinked
                }
                mappedBit?.let { bit ->
                    padMixer.keyDown(event.deviceId, bit)
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
                if (!selectHeld || !startHeld) chordFired = false
                if (menuOpen) {
                    val mappedBit = LibretroPad.padButtonFor(event, customMappings)
                    val consumed = LibretroPad.run { event.isFromGameController(customMappings) } ||
                        mappedBit != null ||
                        keyCode == KeyEvent.KEYCODE_BACK ||
                        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    if (netplayUi.linked) {
                        mappedBit?.let { bit ->
                            padMixer.keyUp(event.deviceId, bit)
                        }
                    }
                    return consumed
                }
                LibretroPad.padButtonFor(event, customMappings)?.let { bit ->
                    padMixer.keyUp(event.deviceId, bit)
                    return true
                }
            }
        }
        return false
    }

    private fun handlePadMotion(event: MotionEvent): Boolean {
        if (invitePromptOpen || seatPickerOpen) return true
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
            if (netplayUi.linked && LibretroPad.run { event.shouldDrivePad() }) {
                val (left, right) = LibretroPad.readAxes(event)
                padMixer.motion(
                    deviceId = event.deviceId,
                    lx = left.first,
                    ly = left.second,
                    rx = right.first,
                    ry = right.second,
                    axisButtons = LibretroPad.digitalPadFromAxes(event),
                )
            }
            return true
        }
        if (!LibretroPad.run { event.shouldDrivePad() }) return false
        val (left, right) = LibretroPad.readAxes(event)
        padMixer.motion(
            deviceId = event.deviceId,
            lx = left.first,
            ly = left.second,
            rx = right.first,
            ry = right.second,
            axisButtons = LibretroPad.digitalPadFromAxes(event),
        )
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
        if (!gameLoaded || quitDiscardsAutosave) return
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
        val file = coreStore.autosaveFile(platformId, gameId)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) {
            file.writeBytes(data)
            tmp.delete()
        }
    }

    /** Caller must already be on [emuDispatcher]. */
    private fun loadAutosave(): Boolean {
        val file = coreStore.autosaveFile(platformId, gameId)
        if (!file.isFile || file.length() == 0L) return false
        val bytes = file.readBytes()
        if (LibretroNative.nativeUnserialize(bytes)) return true
        // Some cores reject unserialize on the first boot frame after retro_load_game.
        repeat(3) { LibretroNative.nativeRunFrame() }
        return LibretroNative.nativeUnserialize(bytes)
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
        if (xmbOverlay == null) return
        refreshSaveSlots()
        refreshAchievementList()
        menuOpen = true
        releasePointer()
        syncPaused()
        attachMenuOverlay()
        keepProfileChipOnTop()
        uiSounds.playConfirm()
    }

    private fun syncPaused() {
        paused = menuOpen || userPaused
        runCatching {
            // Lockstep GBAs keep producing PCM on their own threads. Pausing
            // AudioTrack here drops the first drain after Host and the game
            // stays silent for the rest of the session. Keep the track live
            // whenever lockstep is running, including the Host overlay.
            if (netplaySession?.linkedNow == true ||
                LibretroNative.nativeGbaLinkActive()
            ) {
                audioTrack?.play()
            } else if (paused) {
                audioTrack?.pause()
            } else {
                audioTrack?.play()
            }
        }
    }

    private fun setUserPaused(value: Boolean) {
        userPaused = value
        userPausedUi = value
        syncPaused()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        window.decorView.requestFocus()
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
        hideSoftKeyboard()
        syncPaused()
        dissolveWashLayers()
        detachMenuOverlay()
        menuMessageJob?.cancel()
        menuMessage = null
        // Sleep/wake is what actually clears this wash: SurfaceFlinger drops the display
        // mode and HWUI destroys the overlay's hardware layer. Do both here.
        flushHardwareLayer(xmbOverlay)
        flushHardwareLayer(window.decorView)
        DisplayRefresh.rebind(window)
        opaqueWindowBackgroundSet = false
        forceDarkPinned = false
        displayPipelinePinned = false
        applyOpaqueWindow()
        pinGameplaySurfaceRepeatedly()
        keepProfileChipOnTop()
        postWashFrame()
        gameRoot?.postDelayed({ logWashProbeIfSystemHit() }, WASH_CHECK_DELAY_MS)
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
            EmulatorMenuAction.CycleAspectMode -> lifecycleScope.launch {
                preferences.setXoraAspectMode(xoraSettings.aspectMode.next())
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
                val enable = !xoraSettings.expandDualDisplay
                xoraSettings = xoraSettings.copy(expandDualDisplay = enable)
                preferences.setXoraExpandDualDisplay(enable)
                syncExpandDisplay(announce = true)
                if (gameLoaded && platformId in DUAL_SCREEN_PLATFORMS) {
                    withContext(emuDispatcher) { applyCoreControllerOptions() }
                    bindExpandPointers()
                }
            }
            EmulatorMenuAction.ToggleNetplayEnabled -> lifecycleScope.launch {
                val enable = !xoraSettings.netplayEnabled
                if (enable) disableHardcoreForNetplay()
                preferences.setXoraNetplayEnabled(enable)
            }
            EmulatorMenuAction.ToggleNetplayOnline -> lifecycleScope.launch {
                val turningOn = !xoraSettings.netplayUseRelay
                if (turningOn && !xoraNetwork.state.value.signedIn) {
                    showMenuMessage(XoraNetplayInvites.LOGIN_REQUIRED)
                    return@launch
                }
                preferences.setXoraNetplayUseRelay(turningOn)
            }
            EmulatorMenuAction.HostNetplay -> startHostNetplay()
            EmulatorMenuAction.JoinNetplay -> startJoinNetplay()
            EmulatorMenuAction.HostOnlineNetplay -> startHostOnlineNetplay()
            EmulatorMenuAction.JoinOnlineNetplay -> startJoinOnlineNetplay()
            EmulatorMenuAction.DisconnectNetplay -> {
                netplaySession?.stop()
                showMenuMessage("Netplay disconnected")
            }
            EmulatorMenuAction.ToggleSpectator -> lifecycleScope.launch {
                preferences.setXoraNetplaySpectator(!xoraSettings.netplaySpectator)
            }
            is EmulatorMenuAction.SetJoinTarget -> applyJoinTarget(action.address, action.port)
            is EmulatorMenuAction.SetJoinCode -> {
                joinCode = XoraNetplayProtocol.filterSessionCodeDraft(action.code)
            }
            EmulatorMenuAction.ClearJoinTarget -> lifecycleScope.launch {
                joinAddress = ""
                joinPort = DEFAULT_NETPLAY_PORT
                joinCode = ""
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
                quitDiscardsAutosave = true
                finish()
            }
            is EmulatorMenuAction.InviteFriendToSession -> inviteFriendToSession(action.username)
            is EmulatorMenuAction.MessageFriendComingSoon -> {
                showMenuMessage("Messaging is coming soon")
            }
            EmulatorMenuAction.ResetGame -> resetGameFromMenu()
            EmulatorMenuAction.ChoosePlayerSeat -> {
                closeMenu()
                seatPickerFocus = (netplayUi.playerSlot - 2).coerceIn(0, 2)
                seatPickerOpen = true
            }
            is EmulatorMenuAction.OpenNotification -> openNotificationFromMenu(action.id)
            EmulatorMenuAction.ClearAllNotifications -> {
                shellNotifications.clearHistory()
                showMenuMessage("Notifications cleared")
            }
            EmulatorMenuAction.NotificationsSeen -> {
                shellNotifications.markAllRead()
                lifecycleScope.launch { xoraNetwork.refreshNetplayInvites() }
            }
            EmulatorMenuAction.CycleNdsWfc -> cycleNdsWfcFromMenu()
            EmulatorMenuAction.RefreshAzaharLobbies -> refreshAzaharLobbiesFromMenu()
            EmulatorMenuAction.OpenStandaloneAzahar -> openStandaloneAzaharFromMenu()
            is EmulatorMenuAction.SelectAzaharRoom -> joinAzaharRoomFromMenu(action)
            EmulatorMenuAction.TogglePretendoPrep -> togglePretendoPrepFromMenu()
            EmulatorMenuAction.RefreshPretendoStatus -> refreshPretendoStatus()
        }
    }

    private fun cycleNdsWfcFromMenu() {
        lifecycleScope.launch {
            val next = xoraSettings.ndsWfcServer.nextPublic()
            preferences.setXoraNdsWfcServer(next)
            xoraSettings = xoraSettings.copy(ndsWfcServer = next)
            withContext(emuDispatcher) { applyCoreControllerOptions() }
            showMenuMessage(
                "WFC ${next.label()}. Open Nintendo Wi-Fi Connection in the game. " +
                    "Reset if the new DNS does not apply.",
            )
        }
    }

    private fun refreshAzaharLobbiesFromMenu() {
        if (azaharLobbyUi.loading) return
        azaharLobbyUi = azaharLobbyUi.copy(
            loading = true,
            status = "Refreshing public rooms…",
            standaloneInstalled = AzaharPublicLobbies.installedStandalonePackage(packageManager) != null,
        )
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AzaharPublicLobbies.fetchRooms(okHttpClient, xoraSettings.azaharLobbyApiUrl)
            }
            val installed = AzaharPublicLobbies.installedStandalonePackage(packageManager) != null
            azaharLobbyUi = AzaharLobbyUi(
                rooms = result.rooms,
                status = result.error ?: when {
                    result.rooms.isEmpty() -> "No public rooms on this lobby right now."
                    else -> "${result.rooms.size} rooms · A copies Direct Connect for Azahar"
                },
                loading = false,
                sourceUrl = result.sourceUrl,
                standaloneInstalled = installed,
            )
            result.error?.let { showMenuMessage(it) }
        }
    }

    private fun togglePretendoPrepFromMenu() {
        lifecycleScope.launch {
            val next = !xoraSettings.threeDsPretendoPrep
            preferences.setXoraThreeDsPretendoPrep(next)
            xoraSettings = xoraSettings.copy(threeDsPretendoPrep = next)
            if (next) {
                withContext(Dispatchers.IO) {
                    AzaharPretendo.ensureDirs(coreStore.saveDirFor("3ds"))
                }
            }
            refreshPretendoStatus(next)
            withContext(emuDispatcher) { applyCoreControllerOptions() }
            showMenuMessage(
                if (next) {
                    "Pretendo prep on. Dump NAND and run Nimbus in standalone Azahar."
                } else {
                    "Pretendo prep off"
                },
            )
        }
    }

    private fun refreshPretendoStatus(prepEnabled: Boolean = xoraSettings.threeDsPretendoPrep) {
        pretendoUi = AzaharPretendo.scan(coreStore.saveDirFor("3ds"), prepEnabled)
    }

    private fun joinAzaharRoomFromMenu(action: EmulatorMenuAction.SelectAzaharRoom) {
        val room = AzaharPublicRoom(
            name = action.name,
            preferredGame = action.game,
            ip = action.ip,
            port = action.port,
            hasPassword = action.hasPassword,
        )
        val connect = AzaharPublicLobbies.directConnect(room)
        if (connect.isNotBlank()) {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Citra Direct Connect", connect))
        }
        val opened = AzaharPublicLobbies.launchStandalone(this)
        val game = action.game.ifBlank { "no game set" }
        val password = if (action.hasPassword) " Password room." else ""
        showMenuMessage(
            when {
                connect.isNotBlank() && opened ->
                    "Copied $connect.$password Direct Connect in Azahar. " +
                        "XOrA cannot sit in Citra rooms."
                connect.isNotBlank() ->
                    "Copied $connect.$password Install standalone Azahar to Direct Connect. " +
                        "XOrA cannot sit in Citra rooms."
                else ->
                    "${action.name} · $game. This listing has no ip:port. " +
                        "XOrA cannot sit in Citra rooms."
            },
        )
    }

    private fun openStandaloneAzaharFromMenu() {
        if (AzaharPublicLobbies.launchStandalone(this)) {
            showMenuMessage("Opened standalone Azahar")
        } else {
            showMenuMessage(
                "Standalone Azahar is not installed. Install Azahar (Vanilla or Play) " +
                    "to join public rooms or set up Pretendo (Nimbus + NAND).",
            )
        }
    }

    /** Seats 2–4 for the picker; the host always owns Player 1. */
    private fun seatPickerOptions(): List<NetplaySeatOption> =
        (2..XoraNetplayProtocol.MAX_PLAYERS).map { slot ->
            NetplaySeatOption(
                slot = slot,
                takenBy = netplayUi.playerNames[slot].orEmpty(),
                isCurrent = slot == netplayUi.playerSlot,
            )
        }

    private fun pickNetplaySeat(slot: Int) {
        seatPickerOpen = false
        syncDialogOverlay()
        if (slot == netplayUi.playerSlot) return
        netplaySession?.requestSeat(slot)
        showMenuMessage("Asking the host for Player $slot…")
    }

    /** Invite / seat-picker live on a full-screen view so they cannot trap input invisibly. */
    private fun syncDialogOverlay() {
        val show = seatPickerOpen || invitePromptOpen
        dialogOverlay?.apply {
            visibility = if (show) View.VISIBLE else View.GONE
            isClickable = show
            alpha = 1f
            if (show) bringToFront()
        }
        if (!show) keepProfileChipOnTop()
    }

    /** A on a notification row: netplay invites open Accept / Decline. */
    private fun openNotificationFromMenu(id: String) {
        val item = notificationHistory.firstOrNull { it.notification.id == id } ?: return
        val notification = item.notification
        if (notification is ShellNotification.XoraNetplayInvite) {
            pendingInvitePrompt = promptFromNotification(notification)
            invitePromptOpen = true
            closeMenu()
            syncDialogOverlay()
        } else {
            shellNotifications.removeFromHistory(id)
            showMenuMessage("Notification cleared")
        }
    }

    private fun promptFromNotification(
        notification: ShellNotification.XoraNetplayInvite,
    ): NetplayInvitePrompt {
        val live = xoraNetwork.state.value.netplayInvites
        val match = live.firstOrNull { invite ->
            invite.fromUsername.equals(notification.fromUsername, ignoreCase = true) ||
                invite.fromUsername.equals(notification.displayName, ignoreCase = true) ||
                invite.fromDisplayName.equals(notification.displayName, ignoreCase = true)
        } ?: live.maxByOrNull { it.createdAtMs }
        val code = XoraNetplayProtocol.normalizeSessionCode(notification.sessionCode)
            ?: match?.code?.let { XoraNetplayProtocol.normalizeSessionCode(it) }
            ?: XoraNetplayProtocol.extractSessionCode(notification.gameTitle)
            ?: ""
        return NetplayInvitePrompt(
            hostName = notification.displayName
                .ifBlank { notification.fromUsername }
                .ifBlank { match?.fromDisplayName.orEmpty() }
                .ifBlank { "a friend" },
            gameTitle = notification.gameTitle.ifBlank { match?.gameTitle.orEmpty() },
            sessionCode = code,
            platformId = notification.platformId.ifBlank { match?.platformId.orEmpty() },
            coreName = notification.coreName.ifBlank { match?.coreName.orEmpty() },
            fromUsername = notification.fromUsername.ifBlank { notification.displayName },
        )
    }

    private fun resetGameFromMenu() {
        if (!gameLoaded) return
        if (netplaySession?.linkedNow == true) {
            // A one-sided retro_reset would fork the lockstep simulations.
            showMenuMessage("Disconnect netplay before resetting")
            return
        }
        lifecycleScope.launch(emuDispatcher) {
            LibretroNative.nativeReset()
            withContext(Dispatchers.Main.immediate) {
                showMenuMessage("Game reset")
                setUserPaused(false)
                closeMenu()
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

    private fun hasLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 37) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun withLocalNetworkPermission(host: Boolean, join: Boolean, proceed: () -> Unit) {
        if (hasLocalNetworkPermission()) {
            proceed()
            return
        }
        pendingNetplayHost = host
        pendingNetplayJoin = join
        localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private fun startHostNetplay() {
        withLocalNetworkPermission(host = true, join = false) { beginHostNetplay() }
    }

    private fun beginHostNetplay() {
        lifecycleScope.launch {
            if (!ensureGbaNetplayCore()) return@launch
            disableHardcoreForNetplay()
            preferences.setXoraNetplayEnabled(true)
            withContext(Dispatchers.Main.immediate) {
                val hello = netplayHello()
                netplaySession?.host(xoraSettings.netplayPort, hello) {
                    withContext(emuDispatcher) { LibretroNative.nativeSerialize() }
                }
                lifecycleScope.launch(emuDispatcher) { applyCoreControllerOptions() }
                showMenuMessage("Waiting for a player… this phone already has two GBAs on a Game Link cable")
            }
            armGbaGameLinkNow(host = true)
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
        if (parsed.host.isBlank()) {
            showMenuMessage("Type a join IP first")
            return
        }
        joinAddress = parsed.host
        joinPort = parsed.port
        withLocalNetworkPermission(host = false, join = true) { beginJoinNetplay() }
    }

    private fun beginJoinNetplay() {
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
            if (!ensureGbaNetplayCore()) return@launch
            disableHardcoreForNetplay()
            preferences.setXoraNetplayEnabled(true)
            preferences.setXoraNetplayHostAddress(formatJoinHostPort(address, parsed.port))
            withContext(Dispatchers.Main.immediate) {
                val hello = netplayHello()
                netplaySession?.join(address, parsed.port, hello) { bytes ->
                    applyNetplaySavestate(bytes)
                }
                lifecycleScope.launch(emuDispatcher) { applyCoreControllerOptions() }
                showMenuMessage("Joining $address:${parsed.port}…")
            }
            armGbaGameLinkNow(host = false)
        }
    }

    private fun startHostOnlineNetplay() {
        lifecycleScope.launch {
            ensureOnlineHostSession()
        }
    }

    /**
     * Starts (or reuses) an online host lobby and returns the 6-character session code.
     */
    private suspend fun ensureOnlineHostSession(): String? {
        if (!xoraNetwork.state.value.signedIn) {
            showMenuMessage(XoraNetplayInvites.LOGIN_REQUIRED)
            return null
        }
        val existing = netplayUi.sessionCode.takeIf {
            netplayUi.online &&
                it.isNotBlank() &&
                netplayUi.role == XoraNetplayRole.Host
        }
        if (existing != null) return existing
        if (!ensureGbaNetplayCore()) return null
        disableHardcoreForNetplay()
        preferences.setXoraNetplayEnabled(true)
        preferences.setXoraNetplayUseRelay(true)
        xoraNetwork.setRealtimeEnabled(true)
        val code = XoraNetplayProtocol.generateSessionCode()
        val match = xoraNetwork.openNamedMatch(
            XoraNetplayProtocol.matchNameForSessionCode(code),
        ).getOrElse { error ->
            showMenuMessage(error.message ?: "Couldn't start an online session")
            return null
        }
        val link = XoraNakamaNetplayLink(xoraNetwork, match.matchId)
        netplaySession?.hostOnLink(
            link = link,
            hello = netplayHello(),
            sessionCode = code,
            waitForPeer = { xoraNetwork.waitForMatchPeer(match.matchId, match.selfUserId) },
        ) {
            withContext(emuDispatcher) { LibretroNative.nativeSerialize() }
        }
        withContext(emuDispatcher) { applyCoreControllerOptions() }
        showMenuMessage("Code $code — share it. This phone already has two GBAs on a Game Link cable")
        armGbaGameLinkNow(host = true)
        return code
    }

    private fun startJoinOnlineNetplay() {
        if (!xoraNetwork.state.value.signedIn) {
            showMenuMessage(XoraNetplayInvites.LOGIN_REQUIRED)
            return
        }
        val code = XoraNetplayProtocol.normalizeSessionCode(joinCode)
        if (code == null) {
            showMenuMessage("Type the 6-character session code")
            return
        }
        joinCode = code
        lifecycleScope.launch {
            if (!ensureGbaNetplayCore()) return@launch
            disableHardcoreForNetplay()
            preferences.setXoraNetplayEnabled(true)
            preferences.setXoraNetplayUseRelay(true)
            xoraNetwork.setRealtimeEnabled(true)
            val match = xoraNetwork.openNamedMatch(
                XoraNetplayProtocol.matchNameForSessionCode(code),
            ).getOrElse { error ->
                showMenuMessage(error.message ?: "Couldn't join that online session")
                return@launch
            }
            val link = XoraNakamaNetplayLink(xoraNetwork, match.matchId)
            netplaySession?.joinOnLink(
                link = link,
                hello = netplayHello(),
                sessionCode = code,
            ) { bytes ->
                applyNetplaySavestate(bytes)
            }
            withContext(emuDispatcher) { applyCoreControllerOptions() }
            showMenuMessage("Joining $code…")
            preferences.clearPendingNetplayJoin()
            armGbaGameLinkNow(host = false)
        }
    }

    private fun inviteFriendToSession(username: String) {
        val target = username.trim()
        if (target.isEmpty()) return
        lifecycleScope.launch {
            val code = ensureOnlineHostSession() ?: return@launch
            val friend = xoraNetworkUi.acceptedFriends.firstOrNull {
                it.username.equals(target, ignoreCase = true)
            }
            val display = friend?.displayName?.ifBlank { target } ?: target
            xoraNetwork.sendNetplayInvite(
                toUsername = target,
                code = code,
                gameTitle = gameTitle,
                platformId = platformId,
                coreName = coreName,
            ).onSuccess {
                uiSounds.playNetplayInviteCue()
                showMenuMessage("Invited $display · code $code")
            }.onFailure { error ->
                showMenuMessage(error.message ?: "Couldn't send that invite")
            }
        }
    }

    private fun offerNetplayInvite(invite: XoraNetplayInviteRecord) {
        if (!gameLoaded) return
        if (netplayUi.linked) return
        val prompt = NetplayInvitePrompt(
            hostName = invite.fromDisplayName.ifBlank { invite.fromUsername }.ifBlank { "a friend" },
            gameTitle = invite.gameTitle,
            sessionCode = invite.code.trim(),
            platformId = invite.platformId,
            coreName = invite.coreName,
            fromUsername = invite.fromUsername.ifBlank { invite.fromDisplayName },
        )
        val banner = ShellNotification.XoraNetplayInvite(
            id = "xora-netplay:${invite.dedupeKey()}",
            displayName = prompt.hostName,
            gameTitle = invite.gameTitle,
            sessionCode = invite.code,
            platformId = invite.platformId,
            coreName = invite.coreName,
            fromUsername = invite.fromUsername,
        )
        if (shellNotifications.isSuppressed(banner)) return
        pendingInvitePrompt = prompt
        shellNotifications.emit(banner)
    }

    private fun confirmInvitePrompt() {
        val prompt = pendingInvitePrompt ?: return
        invitePromptOpen = false
        pendingInvitePrompt = null
        syncDialogOverlay()
        if (menuOpen) closeMenu()
        val hydrated = promptFromNotification(
            ShellNotification.XoraNetplayInvite(
                id = "xora-netplay-join:${prompt.sessionCode}",
                displayName = prompt.hostName,
                gameTitle = prompt.gameTitle,
                sessionCode = prompt.sessionCode,
                platformId = prompt.platformId,
                coreName = prompt.coreName,
                fromUsername = prompt.fromUsername,
            ),
        )
        acceptNetplayInvite(
            code = hydrated.sessionCode,
            platformId = hydrated.platformId,
            gameTitle = hydrated.gameTitle,
            fromUsername = hydrated.fromUsername.ifBlank { hydrated.hostName },
        )
    }

    private fun dismissInvitePrompt() {
        pendingInvitePrompt?.let { prompt ->
            consumedNetplayInviteKeys.add(
                inviteConsumeKey(prompt.fromUsername.ifBlank { prompt.hostName }, prompt.sessionCode),
            )
            netplaySessionDismissalKey(
                prompt.fromUsername.ifBlank { prompt.hostName },
                prompt.sessionCode,
            )?.let { shellNotifications.suppressKeys(listOf(it)) }
        }
        invitePromptOpen = false
        pendingInvitePrompt = null
    }

    private fun maybeJoinPendingNetplayInvite() {
        lifecycleScope.launch {
            val pending = preferences.pendingNetplayJoin.first()
            if (!pending.isActive(System.currentTimeMillis())) return@launch
            acceptNetplayInvite(
                code = pending.code,
                platformId = pending.platformId,
                gameTitle = pending.gameTitle,
                fromUsername = pending.fromUsername,
            )
        }
    }

    private fun acceptNetplayInvite(
        code: String,
        platformId: String,
        gameTitle: String,
        fromUsername: String,
    ) {
        if (!gameLoaded) return
        if (netplayUi.linked) {
            showMenuMessage("Already in a session — disconnect first")
            return
        }
        if (netplayUi.online && netplayUi.role == XoraNetplayRole.Host) {
            showMenuMessage("You are hosting — disconnect to join someone else")
            return
        }
        val normalized = XoraNetplayProtocol.normalizeSessionCode(code)
            ?: XoraNetplayProtocol.extractSessionCode(code)
        if (normalized == null) {
            showMenuMessage("That invite is missing a session code. Open Notifications and try again.")
            lifecycleScope.launch { xoraNetwork.refreshNetplayInvites() }
            return
        }
        val consumeKey = inviteConsumeKey(fromUsername, normalized)
        if (consumeKey in consumedNetplayInviteKeys) return
        val platformOk = platformId.isBlank() || platformId.equals(this.platformId, ignoreCase = true)
        if (!platformOk) {
            showMenuMessage(
                "Open ${gameTitle.ifBlank { "that game" }} to join" +
                    if (fromUsername.isNotBlank()) " ${fromUsername}'s session" else "",
            )
            return
        }
        consumedNetplayInviteKeys.add(consumeKey)
        joinCode = normalized
        startJoinOnlineNetplay()
    }

    private fun inviteConsumeKey(fromUsername: String, code: String): String =
        "${fromUsername.trim().lowercase()}|${code.trim().uppercase()}"

    private suspend fun disableHardcoreForNetplay() {
        if (!raSettings.hardcore) return
        preferences.setRaHardcore(false)
        withContext(emuDispatcher) { LibretroNative.nativeRaSetHardcore(false) }
        raSession?.applyHardcore(false)
        raSettings = raSettings.copy(hardcore = false)
    }

    private fun netplayHello(): XoraNetplayProtocol.Hello {
        netplaySession?.setSessionModeFromPlatform(platformId)
        return XoraNetplayProtocol.Hello(
        // Sessions are announced by XOrA Network username ("angel joined pal's session");
        // the local netplay nickname is only a fallback for signed-out LAN play.
        nickname = xoraNetworkUi.account?.username?.takeIf { it.isNotBlank() }
            ?: xoraSettings.netplayNickname,
        coreName = netplayCoreName(platformId, coreName),
        platformId = platformId,
        romName = gameTitle,
        hostPort = xoraSettings.netplayPort,
        )
    }

    /**
     * GBA netplay uses in-process mGBA lockstep (two cores + a real SIO cable).
     * Keep the already-loaded mGBA core; do not download gpSP.
     */
    private suspend fun ensureGbaNetplayCore(): Boolean {
        if (!usesGbaLockstep(platformId)) return true
        if (romFilePath.isNullOrBlank()) {
            showMenuMessage("Missing ROM for GBA Game Link")
            return false
        }
        return true
    }

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
        val fillEachPanel = expandActive
        stageView.aspectMode = if (fillEachPanel) XoraAspectMode.Stretch else xora.aspectMode
        stageView.integerScaleCap = xora.integerScale
        stageView.bezelsEnabled = if (fillEachPanel) false else xora.bezelsEnabled
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
            pinGameplaySurfaceAndRepaint()
            window.decorView.requestFocus()
        }
    }

    /**
     * Runs on every vsync, so every write here has to be conditional.
     *
     * The unconditional version of this was its own wash: `setFormat`, `setBackgroundDrawable*`
     * and the bar-colour setters each dispatch window attributes to WindowManager, and
     * `ImmersiveMode.apply` posts a fresh insets request. Firing all of that 60 times a second
     * kept the window in a permanent relayout / insets animation, and a window caught mid-relayout
     * blends what is behind it through *everything* it holds — the game and the opaque side menu
     * alike, which is exactly what the tint looked like. Read first, write only on a real mismatch.
     */
    private fun pinOpaqueWindow() {
        if (isFinishing) return
        if (window.attributes.format != PixelFormat.OPAQUE) {
            window.setFormat(PixelFormat.OPAQUE)
        }
        // Window.setFlags already no-ops when nothing changed.
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (window.isNavigationBarContrastEnforced) {
                window.isNavigationBarContrastEnforced = false
            }
            if (window.isStatusBarContrastEnforced) {
                window.isStatusBarContrastEnforced = false
            }
        }
        @Suppress("DEPRECATION")
        run {
            if (window.statusBarColor != AndroidColor.BLACK) {
                window.statusBarColor = AndroidColor.BLACK
            }
            if (window.navigationBarColor != AndroidColor.BLACK) {
                window.navigationBarColor = AndroidColor.BLACK
            }
        }
        if (!opaqueWindowBackgroundSet) {
            opaqueWindowBackgroundSet = true
            window.setBackgroundDrawableResource(android.R.color.black)
        }
        if (!forceDarkPinned) {
            forceDarkPinned = true
            disableForceDark(window.decorView)
        }
        paintBlack(window.decorView)
        // A window animation left mid-flight parks LayoutParams.alpha below 1, and the bright
        // shell behind then blends straight through the emulator window. Nothing reset it, so
        // that wash outlived every clear we had, the profile disc included.
        val attrs = window.attributes
        if (attrs.alpha != 1f || attrs.dimAmount != 0f) {
            attrs.alpha = 1f
            attrs.dimAmount = 0f
            window.attributes = attrs
        }
        ImmersiveMode.keepHidden(window)
        pinDisplayPipeline()
        clearParentWashLayers()
        paintBlack(gameRoot)
        stage?.apply {
            if (layerType != View.LAYER_TYPE_NONE) setLayerType(View.LAYER_TYPE_NONE, null)
            paintBlack(this)
            if (alpha != 1f) alpha = 1f
        }
    }

    /** setBackgroundColor mutates and invalidates unconditionally, so check the colour first. */
    private fun paintBlack(view: View?) {
        view ?: return
        if ((view.background as? ColorDrawable)?.color != AndroidColor.BLACK) {
            view.setBackgroundColor(AndroidColor.BLACK)
        }
    }

    /**
     * Gameplay-only pin: overlay closed, user is playing. Drive leftover overlay opacity all
     * the way to transparent so a white scrim cannot sit on the framebuffer.
     *
     * Every presented frame calls this. A timer alone loses the race: the wash is reapplied
     * from the compositor side, so anything slower than the present rate lets it show through.
     * All the setters below no-op when the value already matches, so a clean frame costs
     * a handful of field reads inside a callback that was going to run anyway.
     */
    private fun pinGameplaySurface() {
        if (isFinishing || menuOpen) return
        dissolveWashLayers()
        paintBlack(gameRoot)
        pinGameImage(primaryGameView)
        pinGameImage(secondaryGameView)
        stage?.apply {
            paintBlack(this)
            if (alpha != 1f) alpha = 1f
            if (visibility != View.VISIBLE) visibility = View.VISIBLE
        }
    }

    /** [pinGameplaySurface] plus the chip restack and a framebuffer repaint, for event paths. */
    private fun pinGameplaySurfaceAndRepaint() {
        if (isFinishing || menuOpen) return
        pinGameplaySurface()
        keepProfileChipOnTop()
        synchronized(bitmapLock) {
            val src = gameBitmap
            val view = primaryGameView
            if (src != null && !src.isRecycled && view != null) {
                view.invalidate()
            }
        }
    }

    private fun pinGameImage(view: ImageView?) {
        view?.apply {
            if (layerType != View.LAYER_TYPE_NONE) setLayerType(View.LAYER_TYPE_NONE, null)
            paintBlack(this)
            if (alpha != 1f) alpha = 1f
            if (colorFilter != null) colorFilter = null
            if (imageTintList != null) imageTintList = null
            if (backgroundTintList != null) backgroundTintList = null
            if (imageAlpha != 255) imageAlpha = 255
            (drawable as? BitmapDrawable)?.bitmap?.takeIf { it.hasAlpha() }?.setHasAlpha(false)
            if (visibility != View.VISIBLE) visibility = View.VISIBLE
        }
    }

    /**
     * OEM wash often lands a few frames after Resume / menu close. Re-pin now and again
     * shortly after so the leftover scrim cannot sit on the framebuffer.
     */
    private fun pinGameplaySurfaceRepeatedly() {
        pinGameplaySurfaceAndRepaint()
        val root = gameRoot ?: return
        val again: Runnable = Runnable {
            displayPipelinePinned = false
            pinDisplayPipeline()
            pinGameplaySurfaceAndRepaint()
        }
        root.post(again)
        root.postDelayed(again, 50)
        root.postDelayed(again, 160)
        root.postDelayed(again, 400)
    }

    /**
     * If a wash is still attached, fade it to fully transparent instead of leaving it opaque
     * and GONE (some OEMs still composite a GONE ComposeView).
     */
    private fun attachMenuOverlay() {
        val overlay = xmbOverlay ?: return
        val root = gameRoot ?: return
        if (overlay.parent == null) {
            val dialogs = dialogOverlay
            val chip = profileChip
            val index = when {
                dialogs?.parent === root -> root.indexOfChild(dialogs)
                chip?.parent === root -> root.indexOfChild(chip)
                else -> root.childCount
            }.coerceAtLeast(0)
            root.addView(overlay, index)
        }
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        overlay.setBackgroundColor(AndroidColor.BLACK)
        overlay.isClickable = true
        overlay.isFocusable = false
        overlay.bringToFront()
    }

    /**
     * GONE is not enough on some handhelds: HWUI keeps compositing the last frame of a
     * detached-but-still-parented ComposeView. Sleep/wake destroys that layer because the
     * view tree's hardware resources go with the surface. [ViewGroup.removeView] is the
     * same teardown without leaving the activity.
     */
    private fun detachMenuOverlay() {
        val overlay = xmbOverlay ?: return
        overlay.alpha = 0f
        overlay.visibility = View.GONE
        overlay.setBackgroundColor(AndroidColor.TRANSPARENT)
        overlay.isClickable = false
        overlay.isFocusable = false
        if (overlay.hasComposition) overlay.disposeComposition()
        flushHardwareLayer(overlay)
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private fun flushHardwareLayer(view: View?) {
        view ?: return
        if (view.layerType != View.LAYER_TYPE_NONE) {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun dissolveWashLayers() {
        if (isFinishing) return
        val attrs = window.attributes
        if (attrs.dimAmount != 0f) {
            attrs.dimAmount = 0f
            window.attributes = attrs
        }
        if (xmbOverlay?.parent != null) {
            detachMenuOverlay()
        }
        // The dialog host is MATCH_PARENT and stacks above the stage. syncDialogOverlay is the
        // only thing that ever hid it, so a seat picker / invite that lost its close published a
        // full-screen Compose sheet the gameplay pin never looked at.
        if (!seatPickerOpen && !invitePromptOpen) {
            dialogOverlay?.apply {
                if (visibility != View.GONE) visibility = View.GONE
                alpha = 0f
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isClickable = false
                if (hasComposition) disposeComposition()
            }
        }
        syncBannerHost()
        clearParentWashLayers()
    }

    /**
     * Keeps the banner host out of the compositor whenever it has nothing to show.
     *
     * GONE rather than transparent on purpose: a ComposeView with a live composition still hands
     * HWUI a layer to blend, and blending anything over the framebuffer is exactly the artefact
     * this window keeps growing back.
     */
    private fun syncBannerHost() {
        val host = bannerOverlay ?: return
        val want = if (bannerHostNeeded) View.VISIBLE else View.GONE
        if (host.visibility != want) host.visibility = want
        if (!bannerHostNeeded) {
            if (host.alpha != 0f) host.alpha = 0f
            if (host.layerType != View.LAYER_TYPE_NONE) {
                host.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            if (host.hasComposition) host.disposeComposition()
        } else if (host.alpha != 1f) {
            host.alpha = 1f
        }
    }

    /**
     * The layers every earlier pin missed: the stage's *parents*. A stale hardware layer, a
     * leftover foreground scrim (windowContentOverlay lives on the content parent), or a sub-1
     * alpha on the decor / content / root tints everything underneath, and no amount of
     * resetting the game ImageView or the overlay below it can shift that.
     */
    private fun clearParentWashLayers() {
        val decor = window.decorView
        clearWashOn(decor)
        clearWashOn(findViewById<View>(android.R.id.content))
        clearWashOn(gameRoot)
    }

    private fun clearWashOn(view: View?) {
        view ?: return
        if (view.alpha != 1f) view.alpha = 1f
        if (view.foreground != null) view.foreground = null
        if (view.backgroundTintList != null) view.backgroundTintList = null
        if (view.foregroundTintList != null) view.foregroundTintList = null
        if (view.layerType != View.LAYER_TYPE_NONE) view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.overlay.clear()
        if (view.isForceDarkAllowed) view.isForceDarkAllowed = false
    }

    private fun disableForceDark(view: View?) {
        view ?: return
        if (view.isForceDarkAllowed) view.isForceDarkAllowed = false
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) disableForceDark(view.getChildAt(i))
        }
    }

    private fun restoreShellPresence() {
        if (discordRichPresence.state.value.activity is DiscordPresenceActivity.Playing) {
            discordRichPresence.setActivity(DiscordPresenceActivity.InSora)
        }
        gameCompanionController.endSession()
    }

    /**
     * Every gameplay reset in this file is gated on [menuOpen]. A flag that can outlive the view it
     * describes therefore switches all of them off at once and nothing says so, so reconcile it
     * against the overlay that is actually on screen.
     */
    private fun reconcileMenuState() {
        if (!menuOpen || isFinishing) return
        val overlay = xmbOverlay ?: return
        val showing = overlay.parent != null &&
            overlay.visibility == View.VISIBLE &&
            overlay.alpha > 0.01f
        if (showing) return
        menuOpen = false
        syncPaused()
        pinGameplaySurfaceRepeatedly()
    }

    /** Tap the profile disc — the kill switch when the automatic pin still leaves a wash. */
    private fun clearWhiteTintFromProfileTap() {
        if (isFinishing) return
        // The kill switch never takes the lesser path: reconcile first, because a stale menu flag
        // is precisely what stops the real clear below from running.
        reconcileMenuState()
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
            pinGameplaySurfaceRepeatedly()
        }
        keepProfileChipOnTop()
        uiSounds.playConfirm()
        DisplayRefresh.rebind(window)
        displayPipelinePinned = false
        pinDisplayPipeline()
    }

    /**
     * The wash PixelCopy cannot see: other windows and the display colour pipeline.
     *
     * [Window.setHideOverlayWindows] drops `TYPE_APPLICATION_OVERLAY` dimmers / filters that sit
     * on this display. [Window.setPreferMinimalPostProcessing] asks the panel to skip extra
     * colour transforms (vivid modes, motion processing) that lift blacks after our buffer is
     * already clean. Companion overlay lives on the second display, so hiding overlays here
     * does not take that panel down.
     */
    private fun pinDisplayPipeline() {
        if (isFinishing) return
        if (window.colorMode != ActivityInfo.COLOR_MODE_DEFAULT) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        if (displayPipelinePinned) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setPreferMinimalPostProcessing(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && xoraSettings.blockOverlayWash) {
            runCatching { window.setHideOverlayWindows(true) }
        }
        displayPipelinePinned = true
    }

    /**
     * Answers the one question reading the code cannot: is the wash inside this window or not?
     *
     * [PixelCopy] reads back what *this window* actually composited. Comparing the centre of the
     * game rect against the same pixel in [gameBitmap] — the frame we handed the ImageView — splits
     * the problem in half. If the readback matches the source, nothing in our view tree tinted it
     * and the wash is above the window (system dim, an accessibility or OEM overlay, a display
     * colour transform). If it does not match, the wash is ours, and the layer dump says which view.
     */
    private fun runWashDiagnostics(auto: Boolean = false) {
        val root = gameRoot ?: return
        val view = primaryGameView ?: return
        // Measuring our own overlay would blame a leftover report for the wash.
        hideWashReport()
        val report = StringBuilder()

        val attrs = window.attributes
        report.appendLine("WINDOW")
        report.appendLine("  alpha=${attrs.alpha} dim=${attrs.dimAmount} format=${attrs.format}")
        report.appendLine("  flags=0x${Integer.toHexString(attrs.flags)}")
        report.appendLine("  colorMode=${window.colorMode} wide=${window.isWideColorGamut}")
        report.appendLine("  menuOpen=$menuOpen paused=$paused")
        report.appendLine(collectSystemWashProbe(includeOverlayApps = true).asText())

        report.appendLine("LAYERS >=40% of window, front to back")
        val covering = mutableListOf<String>()
        collectCoveringViews(window.decorView, root.width * root.height, covering)
        if (covering.isEmpty()) report.appendLine("  (none)") else covering.forEach {
            report.appendLine("  $it")
        }

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val sampleX = location[0] + view.width / 2
        val sampleY = location[1] + view.height / 2
        val expected = synchronized(bitmapLock) {
            val src = gameBitmap
            if (src != null && !src.isRecycled && src.width > 0 && src.height > 0) {
                src.getPixel(src.width / 2, src.height / 2)
            } else {
                null
            }
        }

        val shot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, shot, { result ->
            var washDetected = false
            report.appendLine("PIXEL AT GAME CENTRE (${sampleX}, ${sampleY})")
            if (result != PixelCopy.SUCCESS) {
                report.appendLine("  readback failed: $result")
            } else {
                val actual = runCatching { shot.getPixel(sampleX, sampleY) }.getOrNull()
                report.appendLine("  source frame  = ${hexColor(expected)}")
                report.appendLine("  composited    = ${hexColor(actual)}")
                if (expected != null && actual != null) {
                    val dr = AndroidColor.red(actual) - AndroidColor.red(expected)
                    val dg = AndroidColor.green(actual) - AndroidColor.green(expected)
                    val db = AndroidColor.blue(actual) - AndroidColor.blue(expected)
                    report.appendLine("  delta         = r$dr g$dg b$db")
                    washDetected = dr > WASH_DELTA && dg > WASH_DELTA && db > WASH_DELTA
                    report.appendLine(
                        if (washDetected) {
                            "  => WASH IS INSIDE THIS WINDOW. Blame a layer listed above."
                        } else {
                            "  => window buffer is clean. If you still see a tint, it is " +
                                "applied AFTER this window. See SYSTEM / OVERLAY APPS above."
                        },
                    )
                }
            }
            shot.recycle()
            Log.i("XoraWash", report.toString())
            if (!auto) {
                copyWashReport(report.toString())
            }
        }, root.handler)
    }

    private data class WashSystemProbe(
        val lines: List<String>,
        val hits: List<String>,
    ) {
        fun asText(): String = buildString {
            appendLine("SYSTEM")
            lines.forEach { appendLine("  $it") }
            appendLine("VERDICT")
            if (hits.isEmpty()) {
                appendLine("  no Extra Dim / Night Light / inversion / colour-correction flag is on.")
                appendLine("  if the tint is still visible, it is an OEM colour profile or a")
                appendLine("  3rd-party overlay we cannot see from this process.")
            } else {
                hits.forEach { appendLine("  ON  $it") }
            }
        }
    }

    /**
     * Reads the flags the PixelCopy dump cannot see: Extra Dim, Night Light, colour inversion,
     * colour correction, night UI, battery saver, and apps that hold SYSTEM_ALERT_WINDOW.
     */
    private fun collectSystemWashProbe(includeOverlayApps: Boolean): WashSystemProbe {
        val lines = mutableListOf<String>()
        val hits = mutableListOf<String>()

        fun flag(key: String): String =
            runCatching {
                when (Settings.Secure.getInt(contentResolver, key, -1)) {
                    1 -> "ON"
                    0 -> "off"
                    else -> "n/a"
                }
            }.getOrElse { "n/a" }

        fun hit(label: String, on: Boolean) {
            if (on) hits.add(label)
        }

        val extraDim = flag("reduce_bright_colors_activated")
        val extraDimLevel = runCatching {
            Settings.Secure.getInt(contentResolver, "reduce_bright_colors_level", -1)
        }.getOrDefault(-1)
        val nightLight = flag("night_display_activated")
        val inversion = flag("accessibility_display_inversion_enabled")
        val daltonizer = flag("accessibility_display_daltonizer_enabled")
        val highContrast = flag("high_text_contrast_enabled").let { v ->
            if (v != "n/a") v else flag("accessibility_high_text_contrast_enabled")
        }
        lines += "extra dim (reduce_bright_colors) = $extraDim" +
            if (extraDimLevel >= 0) " level=$extraDimLevel" else ""
        lines += "night light                     = $nightLight"
        lines += "colour inversion                = $inversion"
        lines += "colour correction               = $daltonizer"
        lines += "high contrast                   = $highContrast"
        hit("Extra Dim — Settings → Display → Extra dim", extraDim == "ON")
        hit("Night Light — Settings → Display → Night Light", nightLight == "ON")
        hit("Colour inversion — Settings → Accessibility → Colour inversion", inversion == "ON")
        hit("Colour correction — Settings → Accessibility → Colour correction", daltonizer == "ON")
        hit("High contrast text — Settings → Accessibility", highContrast == "ON")

        val uiMode = getSystemService(UiModeManager::class.java)
        val nightMode = when (uiMode?.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> "YES"
            UiModeManager.MODE_NIGHT_NO -> "no"
            UiModeManager.MODE_NIGHT_AUTO -> "auto"
            UiModeManager.MODE_NIGHT_CUSTOM -> "custom"
            else -> "n/a"
        }
        val cfgNight = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "YES"
            Configuration.UI_MODE_NIGHT_NO -> "no"
            else -> "undefined"
        }
        lines += "ui nightMode / config           = $nightMode / $cfgNight"
        if (nightMode == "YES" || cfgNight == "YES") {
            hit("Dark theme / night UI mode", true)
        }

        val power = getSystemService(PowerManager::class.java)
        val saver = power?.isPowerSaveMode == true
        lines += "battery saver                   = ${if (saver) "ON" else "off"}"
        hit("Battery saver (can grey the panel)", saver)

        val a11y = getSystemService(AccessibilityManager::class.java)
        val a11yOn = a11y?.isEnabled == true
        val services = a11y?.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).orEmpty()
        lines += "accessibility enabled           = $a11yOn services=${services.size}"
        services.take(8).forEach { info ->
            val id = info.id.ifBlank { info.resolveInfo?.serviceInfo?.packageName }.orEmpty()
            lines += "  a11y $id"
            if (id.isNotBlank()) hit("Accessibility service $id", true)
        }

        val display = window.decorView.display
        val mode = display?.mode
        lines += "display mode                    = ${mode?.physicalWidth}x${mode?.physicalHeight}" +
            "@${mode?.refreshRate?.let { "%.1f".format(it) }}Hz id=${mode?.modeId}"
        lines += "display wideColor / hdr         = ${display?.isWideColorGamut} / " +
            "${display?.hdrCapabilities?.supportedHdrTypes?.contentToString() ?: "[]"}"
        lines += "window colorMode                = ${window.colorMode}"
        val brightness = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        }.getOrDefault(-1)
        val brightMode = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        }.getOrDefault(-1)
        lines += "brightness / auto               = $brightness / " +
            if (brightMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "auto" else "manual"

        if (includeOverlayApps) {
            val overlays = overlayCapablePackages()
            lines += "apps allowed to draw overlays   = ${overlays.size}"
            overlays.take(12).forEach { lines += "  overlay $it" }
            overlays.filterNot {
                it == packageName || it.startsWith("com.android.") || it.startsWith("android")
            }.forEach { hit("Overlay app $it (Settings → Apps → Special access → Display over other apps)", true) }
        }

        return WashSystemProbe(lines, hits)
    }

    private fun overlayCapablePackages(): List<String> {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return emptyList()
        val apps = runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        return apps.mapNotNull { app ->
            val mode = runCatching {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    app.uid,
                    app.packageName,
                )
            }.getOrDefault(AppOpsManager.MODE_DEFAULT)
            if (mode == AppOpsManager.MODE_ALLOWED) app.packageName else null
        }.sorted()
    }

    private fun logWashProbeIfSystemHit() {
        if (isFinishing || menuOpen) return
        val probe = collectSystemWashProbe(includeOverlayApps = true)
        Log.i("XoraWash", probe.asText())
    }

    private fun copyWashReport(text: String) {
        runCatching {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("XOrA wash probe", text))
        }
    }

    private fun collectCoveringViews(view: View, windowArea: Int, out: MutableList<String>) {
        if (windowArea <= 0) return
        if (view.visibility != View.VISIBLE) return
        if (view.width * view.height * 100 >= windowArea * 40) {
            val bg = (view.background as? ColorDrawable)
                ?.let { "#" + Integer.toHexString(it.color) }
                ?: view.background?.javaClass?.simpleName
                ?: "none"
            val tint = (view as? ImageView)?.imageTintList?.defaultColor?.let {
                "#" + Integer.toHexString(it)
            } ?: "none"
            out.add(
                "${view.javaClass.simpleName} ${view.width}x${view.height} " +
                    "alpha=${view.alpha} layer=${view.layerType} bg=$bg " +
                    "fg=${view.foreground?.javaClass?.simpleName ?: "none"} " +
                    "elev=${view.elevation} forceDark=${view.isForceDarkAllowed} tint=$tint",
            )
        }
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                collectCoveringViews(view.getChildAt(i), windowArea, out)
            }
        }
    }

    private fun hexColor(color: Int?): String =
        if (color == null) "n/a" else "#" + Integer.toHexString(color).padStart(8, '0')

    private fun hideWashReport() {
        val root = gameRoot ?: return
        washReport?.let { root.removeView(it) }
        washReport = null
    }

    private fun keepProfileChipOnTop() {
        val root = gameRoot ?: return
        val chip = profileChip ?: return
        chip.visibility = View.VISIBLE
        chip.alpha = 1f
        if (root.getChildAt(root.childCount - 1) !== chip) {
            chip.bringToFront()
        }
        // Last so bottom taps hit the pad, not the SNES bezel / game ImageView.
        netplayTouchPad?.takeIf { it.visibility == View.VISIBLE }?.bringToFront()
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
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
                leftMargin = pad
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
            setOnLongClickListener {
                runWashDiagnostics()
                true
            }
        }
        chip.addView(image)
        chip.addView(letter)
        profileChip = chip
        profileChipImage = image
        profileChipLetter = letter
        bindProfileChip(null, "P", AndroidColor.rgb(110, 123, 255))
        return chip
    }

    private fun createNetplayTouchPad(): NetplayTouchPadView {
        val density = resources.displayMetrics.density
        return NetplayTouchPadView(this).apply {
            netplayTouchPad = this
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (220 * density).toInt(),
                Gravity.BOTTOM,
            )
            visibility = View.GONE
            elevation = 40f * density
            onButtonsChanged = { buttons ->
                netplayTouchButtons.set(buttons)
                padMixer.setDigital(NetplayTouchPadView.DEVICE_ID, buttons)
            }
        }
    }

    private fun syncTouchPad() {
        // Hide the overlay when this device already has a pad (RG Rotate / gpio-keys).
        // Phones without a controller still get the on-screen SNES pad.
        val hasPhysical = LibretroPad.connectedControllers().isNotEmpty()
        val show = netplayUi.linked && netplayUi.playerSlot >= 2 && !hasPhysical
        val pad = netplayTouchPad ?: return
        val next = if (show) View.VISIBLE else View.GONE
        if (pad.visibility != next) {
            pad.visibility = next
            if (!show) {
                netplayTouchButtons.set(0)
                padMixer.forget(NetplayTouchPadView.DEVICE_ID)
            }
        }
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
     * Vsync is used so the pin is not starved by the frame present queue. A 250 ms
     * ticker loses that race and the wash shows through.
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

    private fun restartAudio() {
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        startAudio()
    }

    private fun startAudio() {
        if (audioTrack != null) {
            runCatching { audioTrack?.play() }
            return
        }
        val sampleRate = LibretroNative.nativeGetSampleRate().toInt().coerceIn(8000, 96000)
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val netplayCushion = (sampleRate * 2 * 2 * 150) / 1000 // ~150ms stereo s16
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
            .setBufferSizeInBytes(maxOf(minBuf * 4, netplayCushion))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                it.play()
                applyAudioVolume(xoraSettings.audioVolume)
            }
    }

    /** Load a host savestate, then re-plug P2–P4 — cores often drop extra pads on unserialize. */
    private suspend fun applyNetplaySavestate(bytes: ByteArray): Boolean {
        return withContext(emuDispatcher) {
            val ok = LibretroNative.nativeUnserialize(bytes)
            if (ok) applyCoreControllerOptions()
            ok
        }
    }

    /** Re-apply P1–P4 core options and plug every advertised socket. */
    private fun applyCoreControllerOptions() {
        val ui = netplayUi
        XoraCoreOptions.variablesFor(
            platformId = platformId,
            coreName = coreName,
            settings = xoraSettings,
            expandActive = expandActive,
            netplay = XoraCoreOptions.NetplayContext(
                hosting = ui.role == XoraNetplayRole.Host,
                joining = ui.role == XoraNetplayRole.Client,
                hostAddress = pspAdhocHostAddress(ui),
            ),
            romPath = romFilePath.orEmpty(),
        ).forEach { (key, value) ->
            LibretroNative.nativeSetCoreVariable(key, value)
        }
        LibretroNative.nativePlugControllers()
    }

    private fun pspAdhocHostAddress(ui: XoraNetplayUiState): String {
        if (ui.role == XoraNetplayRole.Host) return "localhost"
        ui.advertisedHostAddresses.firstOrNull()?.let { return it }
        val fromJoin = joinAddress.trim()
        if (fromJoin.isNotBlank()) return fromJoin.substringBefore(':')
        return parseJoinHostPort(xoraSettings.netplayHostAddress, xoraSettings.netplayPort).host
    }

    private fun applyNativePad(port: Int, pad: LibretroPadMixer.Snapshot) {
        LibretroNative.nativeSetPadStatePort(port, pad.buttons, pad.lx, pad.ly, pad.rx, pad.ry)
    }

    private fun applyNativePad(port: Int, pad: XoraNetplayProtocol.PadFrame) {
        LibretroNative.nativeSetPadStatePort(port, pad.buttons, pad.lx, pad.ly, pad.rx, pad.ry)
    }

    private fun bindPointerTouch(view: ImageView?, target: DualScreenPointerTarget) {
        if (view == null) return
        view.isClickable = true
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.setOnTouchListener { touched, event ->
            handleGamePointer(touched as ImageView, event, target)
        }
    }

    private fun releasePointer() {
        LibretroNative.nativeSetPointerState(0, 0, false)
    }

    private fun handleGamePointer(
        view: ImageView,
        event: MotionEvent,
        target: DualScreenPointerTarget,
    ): Boolean {
        if (platformId !in DUAL_SCREEN_PLATFORMS) return false
        if (menuOpen || invitePromptOpen || !gameLoaded) {
            releasePointer()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP,
            -> {
                releasePointer()
                return true
            }
        }
        val drawable = view.drawable
        val mapped = DualScreenPointer.mapViewToPointer(
            viewX = event.x,
            viewY = event.y,
            viewW = view.width,
            viewH = view.height,
            contentW = drawable?.intrinsicWidth ?: view.width,
            contentH = drawable?.intrinsicHeight ?: view.height,
            fill = view.scaleType == ImageView.ScaleType.FIT_XY,
            target = target,
            pressed = true,
        ) ?: run {
            releasePointer()
            return false
        }
        LibretroNative.nativeSetPointerState(mapped.x, mapped.y, mapped.pressed)
        return mapped.pressed
    }

    /**
     * Lockstep cores always sit on this phone. Port 0 is Player 1, port 1 is Player 2.
     * While waiting alone, clone this phone's pad onto the hidden GBA so Kirby sees a
     * cable. Once both players are linked, stop cloning — the hidden GBA is the other
     * person, driven by their pad over the network.
     */
    private fun applyGbaLockstepPads(
        session: XoraNetplaySession,
        sent: XoraNetplayProtocol.PadFrame?,
        pads: XoraNetplayExchange?,
        local: LibretroPadMixer.Snapshot?,
    ) {
        val slot = gbaLockstepLocalSlot(
            playerSlot = session.playerSlotNow,
            hosting = session.hosting,
            joining = session.joining,
        )
        val selfPort = (slot - 1).coerceIn(0, 1)
        val hiddenPort = gbaLockstepHiddenPort(slot)
        val mirror = shouldMirrorGbaLockstepPartnerPad(session.linkedNow, session.playerCountNow)
        if (pads != null && sent != null) {
            pads.pads.forEachIndexed { port, pad -> applyNativePad(port, pad) }
            // Host lockstep already delayed this seat in exchange(). Overwriting
            // with [sent] would make Player 1 instant again and Player 2 late.
            if (mirror) applyNativePad(selfPort, sent)
        } else if (local != null) {
            applyNativePad(selfPort, local)
        }
        if (mirror) {
            when {
                sent != null -> applyNativePad(hiddenPort, sent)
                local != null -> applyNativePad(hiddenPort, local)
            }
        }
    }

    /**
     * Start or restart in-process lockstep. Must run on the emu thread.
     * @return true when this call booted a new pair of GBAs.
     */
    private fun startGbaLockstepIfNeeded(session: XoraNetplaySession): Boolean {
        val handheld = session.sessionModeNow == NetplaySessionMode.HandheldLink
        val slot = gbaLockstepLocalSlot(
            playerSlot = session.playerSlotNow,
            hosting = session.hosting,
            joining = session.joining,
        )
        if (!usesGbaLockstep(platformId) || !handheld || slot < 1) return false
        val key = gbaLockstepGenerationKey(
            localSlot = slot,
            linked = session.linkedNow,
            playerCount = session.playerCountNow,
        )
        val joinedReset = gbaLockstepKey.startsWith("solo:") && key.startsWith("linked:")
        if (gbaLockstepKey != key) {
            gbaLockstepAttempted.set(false)
            if (LibretroNative.nativeGbaLinkActive()) {
                LibretroNative.nativeGbaLinkStop()
            }
            gbaLockstepKey = key
        }
        val start = shouldStartGbaLockstep(
            platformId,
            handheldLink = handheld,
            localSlot = slot,
            alreadyActive = LibretroNative.nativeGbaLinkActive(),
            alreadyAttempted = gbaLockstepAttempted.get(),
        )
        if (!start || !gbaLockstepAttempted.compareAndSet(false, true)) return false
        val rom = resolveGbaLockstepRomPath(romFilePath.orEmpty())
        val ok = LibretroNative.nativeGbaLinkStart(
            rom,
            gbaLockstepPlayerCount(session.playerCountNow),
            slot,
        )
        if (ok) {
            LibretroNative.nativeGbaSioSetEnabled(false)
            lifecycleScope.launch(Dispatchers.Main.immediate) {
                restartAudio()
            }
            val text = if (joinedReset) {
                "Player 2 joined — both phones reset. You are your seat; the hidden GBA is the other player. Open the same 2-player / link menu together."
            } else {
                "Both GBAs rebooted with a real Game Link cable. Open the same 2-player / link menu on both devices."
            }
            lifecycleScope.launch(Dispatchers.Main.immediate) {
                showMenuMessage(text)
            }
            return true
        }
        val err = LibretroNative.nativeLastError() ?: "Could not start GBA lockstep"
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            showMenuMessage(err)
        }
        return false
    }

    private fun pumpGbaNetpacket(session: XoraNetplaySession) {
        val handheld = session.sessionModeNow == NetplaySessionMode.HandheldLink
        val start = shouldStartGbaNetpacket(
            platformId = platformId,
            handheldLink = handheld,
            localSlot = session.playerSlotNow.coerceAtLeast(if (session.hosting) 1 else 0),
            playerCount = session.playerCountNow.coerceAtLeast(1),
            alreadyStarted = gbaNetpacketStarted.get(),
        )
        if (start && gbaNetpacketStarted.compareAndSet(false, true)) {
            val ok = LibretroNative.nativeNetpacketStart(gbaNetplayClientId(session.playerSlotNow))
            if (!ok) {
                gbaNetpacketStarted.set(false)
                return
            }
            if (session.hosting) syncGbaNetpacketPeers(session)
            LibretroNative.nativeReset()
        }
        if (gbaNetpacketStarted.get()) syncGbaNetpacketPeers(session)
        session.takeNetpackets().forEach { packet ->
            LibretroNative.nativeNetpacketIncoming(packet.src, packet.payload)
        }
    }

    private suspend fun armGbaGameLinkNow(@Suppress("UNUSED_PARAMETER") host: Boolean) {
        if (!usesGbaLockstep(platformId)) return
        withContext(emuDispatcher) {
            val session = netplaySession ?: return@withContext
            startGbaLockstepIfNeeded(session)
        }
    }

    private fun applyGbaLinkCable(session: XoraNetplaySession) {
        if (!shouldArmGbaLinkCable(
                platformId = platformId,
                handheldLink = session.sessionModeNow == NetplaySessionMode.HandheldLink,
                localSlot = session.playerSlotNow,
                hosting = session.hosting,
            )
        ) {
            return
        }
        LibretroNative.nativeGbaSioSetEnabled(true)
        val snap = LibretroNative.nativeGbaSioRead()
        val multi = session.exchangeSerial(
            snap?.getOrNull(0) ?: 0,
            snap?.getOrNull(1) ?: 0,
        )
        LibretroNative.nativeGbaSioApply(
            multi,
            (session.playerSlotNow - 1).coerceIn(0, 3),
        )
    }

    private fun syncGbaNetpacketPeers(session: XoraNetplaySession) {
        if (!session.hosting) return
        val roster = session.state.value.playerNames.keys.ifEmpty {
            (1..session.playerCountNow).toSet()
        }
        val live = roster
            .filter { it != session.playerSlotNow }
            .map { gbaNetplayClientId(it) }
            .toSet()
        for (peer in live) {
            if (gbaNetpacketPeers.add(peer)) {
                if (!LibretroNative.nativeNetpacketPeerConnected(peer)) {
                    gbaNetpacketPeers.remove(peer)
                }
            }
        }
        val gone = gbaNetpacketPeers.filter { it !in live }
        for (peer in gone) {
            LibretroNative.nativeNetpacketPeerDisconnected(peer)
            gbaNetpacketPeers.remove(peer)
        }
    }

    private fun drainGbaNetpacket(session: XoraNetplaySession) {
        val packets = LibretroNative.nativeNetpacketDrainOutgoing() ?: return
        for (packed in packets) {
            if (packed.size < 4) continue
            val dest = ((packed[0].toInt() and 0xFF) shl 8) or (packed[1].toInt() and 0xFF)
            val flags = ((packed[2].toInt() and 0xFF) shl 8) or (packed[3].toInt() and 0xFF)
            val body = packed.copyOfRange(4, packed.size)
            session.sendNetpacket(dest, flags, body)
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
                val session = netplaySession
                when {
                    session?.holdEmulation == true -> {
                        // Savestate is in flight — do not advance or the joiner loads an old snapshot.
                        delay(8)
                    }
                    session?.linkedNow == true ||
                        LibretroNative.nativeGbaLinkActive() ||
                        (!paused && !menuOpen && !activityInBackground) -> {
                    val players = padMixer.snapshotPlayers(xoraSettings.preferredControllerName)
                    if (session?.linkedNow == true) {
                        if (emuFrameIndex % 30 == 0) {
                            LibretroNative.nativePlugControllers()
                        }
                        val overlayBlocksGamePad = invitePromptOpen
                        val mixer = if (overlayBlocksGamePad) {
                            LibretroPadMixer.Snapshot()
                        } else {
                            padMixer.snapshot()
                        }
                        val touch = netplayTouchButtons.get()
                        val local = mixer.copy(buttons = mixer.buttons or touch)
                        val mute = xoraSettings.netplaySpectator &&
                            !session.hosting &&
                            session.playerSlotNow < 2
                        val frameIndex = session.nextFrameIndex()
                        val sent = XoraNetplayProtocol.PadFrame(
                            frame = frameIndex,
                            buttons = if (mute) 0 else local.buttons,
                            lx = if (mute) 0 else local.lx,
                            ly = if (mute) 0 else local.ly,
                            rx = if (mute) 0 else local.rx,
                            ry = if (mute) 0 else local.ry,
                        )
                        val pads = session.exchange(
                            sent,
                            replayRemoteInOrder = usesGbaLockstep(platformId),
                        )
                        val handheld = session.sessionModeNow == NetplaySessionMode.HandheldLink
                        val lockstepWanted = usesGbaLockstep(platformId)
                        if (lockstepWanted) startGbaLockstepIfNeeded(session)
                        val lockstep = LibretroNative.nativeGbaLinkActive()
                        if (lockstep) {
                            applyGbaLockstepPads(session, sent, pads, local = null)
                        } else if (handheld) {
                            // Each handheld is its own game (link-cable style). Local pad is P1.
                            applyNativePad(0, sent)
                            applyNativePad(1, LibretroPadMixer.Snapshot())
                            applyNativePad(2, LibretroPadMixer.Snapshot())
                            applyNativePad(3, LibretroPadMixer.Snapshot())
                        } else if (session.hosting) {
                            pads.pads.forEachIndexed { port, pad ->
                                applyNativePad(port, pad)
                            }
                            val selfPort = session.playerSlotNow - 1
                            if (selfPort in 0..3) applyNativePad(selfPort, sent)
                        }
                        if (!lockstep) {
                            pumpGbaNetpacket(session)
                            applyGbaLinkCable(session)
                        }
                        if (session.runsLocalCore) {
                            if (handheld && !lockstep) {
                                LibretroNative.nativeGbaSioSetEnabled(true)
                                val snap = LibretroNative.nativeGbaSioRead()
                                val multi = session.exchangeSerial(
                                    snap?.getOrNull(0) ?: 0,
                                    snap?.getOrNull(1) ?: 0,
                                )
                                LibretroNative.nativeGbaSioApply(
                                    multi,
                                    (session.playerSlotNow - 1).coerceIn(0, 3),
                                )
                            }
                            emuFrameIndex++
                            LibretroNative.nativeRunFrame()
                            if (!lockstep) {
                                drainGbaNetpacket(session)
                                raSession?.doFrame()
                            }
                            val packed = LibretroNative.nativeCopyFrameRgba()
                            val pcm = LibretroNative.nativeDrainAudio()
                            packed?.let { presentFrame(it) }
                            pcm?.let { audioTrack?.write(it, 0, it.size) }
                            if (handheld && !lockstep) {
                                val snap = LibretroNative.nativeGbaSioRead()
                                val multi = session.exchangeSerial(
                                    snap?.getOrNull(0) ?: 0,
                                    snap?.getOrNull(1) ?: 0,
                                )
                                LibretroNative.nativeGbaSioApply(
                                    multi,
                                    (session.playerSlotNow - 1).coerceIn(0, 3),
                                )
                                applyGbaLinkCable(session)
                            }
                            if (!handheld && session.hosting && packed != null) {
                                val jpeg = XoraNetplayVideo.jpegFromPackedRgba(
                                    packed,
                                    maxWidth = XoraNetplayVideo.MAX_WIDTH,
                                    maxBytes = XoraNetplayVideo.MAX_BYTES,
                                )
                                jpeg?.let { session.sendVideo(it, pcm ?: ShortArray(0)) }
                            }
                        } else {
                            session.takeVideo()?.let { video ->
                                presentRemoteVideo(video)
                            }
                        }
                    } else {
                        if (emuFrameIndex % 180 == 0) {
                            LibretroNative.nativePlugControllers()
                        }
                        session?.takeIf {
                            usesGbaLockstep(platformId) &&
                                (it.hosting || it.joining || it.playerSlotNow >= 1)
                        }?.let { startGbaLockstepIfNeeded(it) }
                        val lockstep = LibretroNative.nativeGbaLinkActive()
                        if (lockstep && session != null) {
                            applyGbaLockstepPads(
                                session,
                                sent = null,
                                pads = null,
                                local = players.p1,
                            )
                        } else {
                            applyNativePad(0, players.p1)
                            applyNativePad(1, players.p2)
                            applyNativePad(2, players.p3)
                            applyNativePad(3, players.p4)
                            if (!lockstep) {
                                session?.let { live ->
                                    pumpGbaNetpacket(live)
                                    applyGbaLinkCable(live)
                                }
                            }
                        }
                        emuFrameIndex++
                        LibretroNative.nativeRunFrame()
                        if (!lockstep) {
                            session?.let {
                                drainGbaNetpacket(it)
                                applyGbaLinkCable(it)
                            }
                            raSession?.doFrame()
                        }
                        LibretroNative.nativeCopyFrameRgba()?.let { packed ->
                            presentFrame(packed)
                        }
                        LibretroNative.nativeDrainAudio()?.let { pcm ->
                            audioTrack?.write(pcm, 0, pcm.size)
                        }
                    }
                    val elapsed = System.nanoTime() - start
                    val sleepMs = ((frameNs - elapsed) / 1_000_000L).coerceAtLeast(0L)
                    // delay (not Thread.sleep / yield) so serialize / unload can run here too.
                    // yield() busy-spins when the frame is late and burns battery on handhelds.
                    if (sleepMs > 0) delay(sleepMs) else delay(1)
                    }
                    else -> {
                    // Nothing is being emulated or drawn, so frame pacing would just wake the CPU
                    // sixty times a second behind a paused session. Idle RA at a slow tick instead.
                    raSession?.idle()
                    delay(IDLE_TICK_MS)
                    }
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
        val geometry = if (expandActive) DualScreenFrameGeometry.split(w, h, platformId) else null
        // When replacing bitmaps, leave the previous instance unreycled — ImageView may
        // still be drawing the prior Bitmap until the next bind.
        runOnUiThread {
            synchronized(bitmapLock) {
                if (geometry != null && !geometry.top.isEmpty && !geometry.bottom.isEmpty) {
                    expandSplitKind = geometry.kind
                    val topRect = geometry.top
                    val bottomRect = geometry.bottom
                    var top = gameBitmap
                    if (top == null || top.width != topRect.width ||
                        top.height != topRect.height || top.isRecycled
                    ) {
                        top = Bitmap.createBitmap(
                            topRect.width,
                            topRect.height,
                            Bitmap.Config.ARGB_8888,
                        )
                        top.setHasAlpha(false)
                        gameBitmap = top
                        primaryGameView?.setImageBitmap(top)
                    }
                    top.setPixels(
                        pixels,
                        topRect.y * w + topRect.x,
                        w,
                        0,
                        0,
                        topRect.width,
                        topRect.height,
                    )
                    top.setHasAlpha(false)

                    var bottom = bottomBitmap
                    if (bottom == null || bottom.width != bottomRect.width ||
                        bottom.height != bottomRect.height || bottom.isRecycled
                    ) {
                        bottom = Bitmap.createBitmap(
                            bottomRect.width,
                            bottomRect.height,
                            Bitmap.Config.ARGB_8888,
                        )
                        bottom.setHasAlpha(false)
                        bottomBitmap = bottom
                        secondaryGameView?.setImageBitmap(bottom)
                    }
                    bottom.setPixels(
                        pixels,
                        bottomRect.y * w + bottomRect.x,
                        w,
                        0,
                        0,
                        bottomRect.width,
                        bottomRect.height,
                    )
                    bottom.setHasAlpha(false)
                    stage?.let { stageView ->
                        if (stageView.contentWidthPx != topRect.width ||
                            stageView.contentHeightPx != topRect.height
                        ) {
                            stageView.contentWidthPx = topRect.width
                            stageView.contentHeightPx = topRect.height
                        }
                    }
                    bindPointerTouch(secondaryGameView, geometry.bottomPointerTarget)
                } else {
                    if (bottomBitmap != null) {
                        bottomBitmap = null
                        secondaryGameView?.setImageDrawable(null)
                    }
                    var bmp = gameBitmap
                    if (bmp == null || bmp.width != w || bmp.height != h || bmp.isRecycled) {
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.setHasAlpha(false)
                        gameBitmap = bmp
                        primaryGameView?.setImageBitmap(bmp)
                    }
                    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                    bmp.setHasAlpha(false)
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

    private fun presentRemoteVideo(packet: XoraNetplayProtocol.VideoPacket) {
        if (packet.pcm.isNotEmpty()) {
            audioTrack?.write(packet.pcm, 0, packet.pcm.size)
        }
        val decoded = XoraNetplayVideo.bitmapFromJpeg(packet.jpeg) ?: return
        runOnUiThread {
            synchronized(bitmapLock) {
                val old = lastRemoteBitmap
                lastRemoteBitmap = decoded
                gameBitmap = decoded
                primaryGameView?.let { view ->
                    view.setImageBitmap(decoded)
                    (view.drawable as? BitmapDrawable)?.isFilterBitmap = false
                }
                stage?.let { stageView ->
                    if (stageView.contentWidthPx != decoded.width ||
                        stageView.contentHeightPx != decoded.height
                    ) {
                        stageView.contentWidthPx = decoded.width
                        stageView.contentHeightPx = decoded.height
                    }
                }
                primaryGameView?.post {
                    if (old != null && old !== lastRemoteBitmap && !old.isRecycled) {
                        old.recycle()
                    }
                }
            }
            primaryGameView?.invalidate()
            frameTick++
            if (!menuOpen) pinGameplaySurface()
        }
    }

    override fun finish() {
        if (quitDiscardsAutosave) {
            runCatching { coreStore.autosaveFile(platformId, gameId).delete() }
        } else {
            persistSessionForBackground()
        }
        gameLoaded = false
        xoraNetwork.setPlayingLine(null)
        xoraNetwork.setRealtimeEnabled(false)
        closeMenu()
        restoreShellPresence()
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
        when {
            invitePromptOpen -> dismissInvitePrompt()
            pendingInvitePrompt != null -> {
                invitePromptOpen = true
                syncDialogOverlay()
            }
            else -> toggleMenu()
        }
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        inputManager = null
        netplaySession?.stop()
        netplaySession = null
        restoreShellPresence()
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
        persistSessionForBackground()
        secondDisplayHost.dismiss()
        secondaryGameView = null
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
        /** Per-channel lift over the source frame before a readback counts as washed. */
        private const val WASH_DELTA = 6
        /** Long enough for the close animation and a fresh frame to land. */
        private const val WASH_CHECK_DELAY_MS = 700L
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
        XoraAspectMode.Core -> ImageView.ScaleType.FIT_CENTER
        else -> ImageView.ScaleType.FIT_XY
    }
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                adjustViewBounds = false
                isClickable = true
                isFocusable = false
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
