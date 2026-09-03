package com.arcadia.shell.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.datastore.AvatarSource
import com.arcadia.shell.datastore.CIRCLE_FRIEND_LIMIT
import com.arcadia.shell.datastore.CirclePin
import com.arcadia.shell.datastore.CirclePinSource
import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.GameArtAlignment
import com.arcadia.shell.datastore.GameIconIdleMedia
import com.arcadia.shell.datastore.GameCustomMediaStore
import com.arcadia.shell.datastore.HomeThemeMediaStore
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.libretro.GameSaveCatalog
import com.arcadia.shell.libretro.GameSaveEntry
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.datastore.MAX_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.MAX_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.MIN_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.MIN_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.PendingNetplayJoin
import com.arcadia.shell.datastore.PlatformArtStore
import com.arcadia.shell.datastore.ProfileAvatarStore
import com.arcadia.shell.datastore.PlatformEmulatorChoice
import com.arcadia.shell.datastore.ProfileFavoriteRaGame
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.ThemeMode
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.TrailerSourcePreference
import com.arcadia.shell.datastore.UI_TEXT_SCALE_PRESETS
import com.arcadia.shell.datastore.UiFitMode
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.XoraInternalResolution
import com.arcadia.shell.datastore.next
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.isReduceMotionPreferred
import com.arcadia.shell.feature.home.component.steamPersonaToPresence
import com.arcadia.shell.feature.home.rss.RssFeedClient
import com.arcadia.shell.input.GamepadDispatcher
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.input.UiOneShot
import com.arcadia.shell.launcher.DetectedEmulator
import com.arcadia.shell.launcher.GameLauncher
import com.arcadia.shell.launcher.InstalledAppSync
import com.arcadia.shell.launcher.LaunchResult
import com.arcadia.shell.launcher.PlatformEmulatorDetector
import com.arcadia.shell.launcher.PlayerSeeder
import com.arcadia.shell.launcher.PlaySessionTracker
import com.arcadia.shell.launcher.RetroArchCoreCatalog
import com.arcadia.shell.launcher.conversations.ConversationRepository
import com.arcadia.shell.launcher.conversations.ConversationSource
import com.arcadia.shell.launcher.conversations.ConversationsUiState
import com.arcadia.shell.launcher.conversations.NotificationConversation
import com.arcadia.shell.launcher.music.MusicAlbum
import com.arcadia.shell.launcher.music.MusicLibrary
import com.arcadia.shell.launcher.music.MusicSource
import com.arcadia.shell.launcher.music.MusicTrack
import com.arcadia.shell.launcher.music.NowPlayingController
import com.arcadia.shell.launcher.photos.DeviceMediaFolder
import com.arcadia.shell.launcher.photos.DevicePhoto
import com.arcadia.shell.launcher.photos.PhotoAccess
import com.arcadia.shell.launcher.photos.PhotoEditor
import com.arcadia.shell.launcher.photos.PhotoLibrary
import com.arcadia.shell.launcher.videos.VideoLibrary
import com.arcadia.shell.launcher.discord.DiscordDmThreadUiState
import com.arcadia.shell.launcher.discord.DiscordPresenceActivity
import com.arcadia.shell.launcher.discord.DiscordPresenceCapability
import com.arcadia.shell.launcher.discord.DiscordRichPresence
import com.arcadia.shell.launcher.notifications.AppForegroundTracker
import com.arcadia.shell.libretro.XoraCoreCatalog
import com.arcadia.shell.launcher.notifications.FriendNetwork
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.ShellSystemNotifier
import com.arcadia.shell.launcher.notifications.netplaySessionDismissalKey
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.model.RomSoundBiteLocator
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.PlatformSummary
import com.arcadia.shell.model.Player
import com.arcadia.shell.model.ScanProgress
import com.arcadia.shell.model.ScreenRole
import com.arcadia.shell.model.ShortcutSpan
import com.arcadia.shell.model.swapped
import com.arcadia.shell.retroachievements.RaConsoleIds
import com.arcadia.shell.retroachievements.RaPasswordLoginResult
import com.arcadia.shell.retroachievements.RaProfile
import com.arcadia.shell.retroachievements.RaRecentUnlock
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RetroAchievementsClient
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scanner.LibraryRootManager
import com.arcadia.shell.xoranetwork.XoraFriendState
import com.arcadia.shell.xoranetwork.XoraNetworkBannerGate
import com.arcadia.shell.xoranetwork.XoraNetworkClient
import com.arcadia.shell.xoranetwork.XoraNetworkRepository
import com.arcadia.shell.xoranetwork.XoraNetplayInviteRecord
import com.arcadia.shell.xoranetwork.XoraNetplayInvites
import com.arcadia.shell.xoranetwork.XoraPresenceMode
import com.arcadia.shell.xoranetwork.parseXoraPresenceMode
import com.arcadia.shell.scanner.LibraryScanner
import com.arcadia.shell.scanner.StorageAccess
import com.arcadia.shell.scraper.LibraryHashScheduler
import com.arcadia.shell.scraper.MusicArtRepository
import com.arcadia.shell.scraper.PlatformArtRepository
import com.arcadia.shell.scraper.ScraperPreference
import com.arcadia.shell.scraper.ScraperScheduler
import com.arcadia.shell.scraper.SpotifyAuth
import com.arcadia.shell.scraper.SpotifyLinkResult
import com.arcadia.shell.scraper.SpotifyPlaybackResult
import com.arcadia.shell.scraper.SpotifyTokenStore
import com.arcadia.shell.scraper.SpotifyWebApi
import com.arcadia.shell.scraper.SteamOpenId
import com.arcadia.shell.scraper.SteamWebApiClient
import com.arcadia.shell.scraper.TrailerResolver
import com.arcadia.shell.scraper.insight.GameInsight
import com.arcadia.shell.scraper.insight.GameInsightRepository
import com.arcadia.shell.scraper.insight.GameScreenshotRepository
import com.arcadia.shell.scraper.insight.InsightSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt
import java.util.UUID
import javax.inject.Inject

/**
 * The single owner of library state and the current selection.
 *
 * Both panes observe this one instance, including the pane rendered into a `Presentation` on the
 * second physical display. That is what makes hero art on one screen track grid movement on the
 * other with no synchronisation code of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val libraryRepository: LibraryRepository,
    private val playerRepository: PlayerRepository,
    private val rootManager: LibraryRootManager,
    private val scanner: LibraryScanner,
    private val launcher: GameLauncher,
    private val preferences: ShellPreferences,
    private val storageAccess: StorageAccess,
    private val sessionTracker: PlaySessionTracker,
    private val installedAppSync: InstalledAppSync,
    private val retroAchievements: RetroAchievementsRepository,
    private val avatarStore: ProfileAvatarStore,
    private val themeMediaStore: HomeThemeMediaStore,
    private val gameCustomMediaStore: GameCustomMediaStore,
    private val gameSaveCatalog: GameSaveCatalog,
    private val gameSoundBitePlayer: GameSoundBitePlayer,
    private val trailerResolver: TrailerResolver,
    private val scraperScheduler: ScraperScheduler,
    private val libraryHashScheduler: LibraryHashScheduler,
    private val platformArtRepository: PlatformArtRepository,
    private val platformArtStore: PlatformArtStore,
    private val appForegroundTracker: AppForegroundTracker,
    private val xoraCoreCatalog: XoraCoreCatalog,
    private val rssFeedClient: RssFeedClient,
    private val gameInsightRepository: GameInsightRepository,
    private val gameScreenshotRepository: GameScreenshotRepository,
    private val steamWebApiClient: SteamWebApiClient,
    private val spotifyAuth: SpotifyAuth,
    private val spotifyTokenStore: SpotifyTokenStore,
    private val spotifyWebApi: SpotifyWebApi,
    private val musicLibrary: MusicLibrary,
    private val musicArtRepository: MusicArtRepository,
    private val photoLibrary: PhotoLibrary,
    private val videoLibrary: VideoLibrary,
    private val photoEditor: PhotoEditor,
    private val nowPlayingController: NowPlayingController,
    private val conversationRepository: ConversationRepository,
    private val discordRichPresence: DiscordRichPresence,
    val shellNotifications: ShellNotificationCenter,
    private val shellSystemNotifier: ShellSystemNotifier,
    private val platformEmulatorDetector: PlatformEmulatorDetector,
    private val playerSeeder: PlayerSeeder,
    private val gameCompanionController: GameCompanionController,
    private val xoraNetwork: XoraNetworkRepository,
    val gamepadDispatcher: GamepadDispatcher,
    private val githubReleaseUpdater: GithubReleaseUpdater,
) : ViewModel() {

    /** Companion bottom-screen session, non-null only while a qualifying game is running. */
    val gameCompanion: StateFlow<GameCompanionUiState?> = gameCompanionController.session

    private data class Selection(val tabIndex: Int = 0, val gameIndex: Int = 0)

    private val selection = MutableStateFlow(Selection())
    private val homePage = MutableStateFlow(HomePage.Home)
    private val homeHubSection = MutableStateFlow(HomeHubSection.ShardMenu)
    private val homeShard = MutableStateFlow(HomeShard.Continue)
    private val xoraCategoryIndex = MutableStateFlow(XoraXmbCategory.Games.ordinal)
    private val xoraItemIndex = MutableStateFlow(GAMES_ITEM_RECENTS)
    private val xoraDepth = MutableStateFlow(XoraXmbDepth.Category)
    private val xoraDrilledPlatformId = MutableStateFlow<String?>(null)
    /** Last hovered item in each XMB folder, restored when backing out. */
    private val xoraReturnItemIndex = mutableMapOf<XoraXmbDepth, Int>()
    /** Last hovered ROM in each platform folder, restored when re-entering that system. */
    private val xoraReturnRomIndex = mutableMapOf<String, Int>()
    /** Drill-in parents so Cancel returns to the folder the user actually left. */
    private val xoraReturnStack = ArrayDeque<XoraXmbDepth>()
    private val homeShortcutIndex = MutableStateFlow(0)
    private val homeShortcutsEditMode = MutableStateFlow(false)
    private val homeShortcuts = MutableStateFlow<List<HomeShortcut>>(emptyList())
    private val shortcutGridColumns = MutableStateFlow(DEFAULT_HOME_SHORTCUT_GRID_COLUMNS)
    private val shortcutGridRows = MutableStateFlow(DEFAULT_HOME_SHORTCUT_GRID_ROWS)
    private val shortcutCustomizeChrome = MutableStateFlow(ShortcutCustomizeChrome.Tiles)
    /** Vita bubble tray over the XMB — toggled with Y on Home. */
    private val vitaShortcutTrayOpen = MutableStateFlow(false)
    /** Restrict add-shortcut sheet to apps/ROMs and skip tile-size when pinning from the tray. */
    private val vitaShortcutPinMode = MutableStateFlow(false)
    private val vitaShortcutLaunch = MutableStateFlow<VitaShortcutLaunchUi?>(null)
    private val vitaShortcutDepartingIndex = MutableStateFlow<Int?>(null)
    private val themesOpen = MutableStateFlow(false)
    /** Which Themes sheet tab to show when [themesOpen] becomes true. */
    private val themesSheetTab = MutableStateFlow(ThemesSheetTab.Customize)
    private val addShortcutOpen = MutableStateFlow(false)
    private val pendingShortcutKind = MutableStateFlow<PendingShortcutKind?>(null)
    private val pendingShortcutSpan = MutableStateFlow(ShortcutSpan.Default)
    private val shortcutTargetPicker = MutableStateFlow<ShortcutTargetPickerUiState?>(null)
    private val mediaPickerRequests = Channel<HomeMediaPickerRequest>(Channel.BUFFERED)
    /** Observed from the primary Activity composition only — never under a Presentation. */
    val mediaPickerRequestFlow: Flow<HomeMediaPickerRequest> = mediaPickerRequests.receiveAsFlow()

    /** Bumps when ROM options should re-scan on-disk saves. */
    private val romSaveRefresh = MutableStateFlow(0)

    private val externalAuthRequests = Channel<HomeExternalAuthRequest>(Channel.BUFFERED)
    /** Custom Tab / OAuth launches — Activity-rooted only (same hoist as media pickers). */
    val externalAuthRequestFlow: Flow<HomeExternalAuthRequest> = externalAuthRequests.receiveAsFlow()

    private val rssUi = MutableStateFlow(RssUiState())
    private val resolvedPlayerName = MutableStateFlow<String?>(null)
    private val isLaunching = MutableStateFlow(false)
    private val accountPanelExpanded = MutableStateFlow(false)
    private val accountPanelSelectedIndex = MutableStateFlow(0)
    private val socialMenuTab = MutableStateFlow(SocialMenuTab.XoraNetwork)
    private val steamFriendsUi = MutableStateFlow(SteamFriendsUiState())
    private val discordSocialUi = MutableStateFlow(DiscordSocialUiState())
    private val conversationsUi = MutableStateFlow(ConversationsUiState())
    private val conversationReply = MutableStateFlow(ConversationReplyUiState())
    private val circlePins = MutableStateFlow<List<CirclePin>>(emptyList())
    private val managingCircle = MutableStateFlow(false)
    /** LT notification center overlay (recent shell notifications + conversations). */
    private val notificationsOpen = MutableStateFlow(false)
    private val friendSearchQuery = MutableStateFlow("")
    private val profileEditRequest = MutableStateFlow(0)
    private val systemPanelExpanded = MutableStateFlow(false)
    private val systemPanelSelectedIndex = MutableStateFlow(0)
    private val notificationHistoryOpen = MutableStateFlow(false)
    private val notificationHistorySelectedIndex = MutableStateFlow(0)
    private val pendingNetplayInvite = MutableStateFlow<NetplayInvitePrompt?>(null)
    private val netplayInvitePromptOpen = MutableStateFlow(false)
    private val systemStatusEditorOpen = MutableStateFlow(false)
    private val systemStatusDraft = MutableStateFlow("")
    private val systemFavoritePickerOpen = MutableStateFlow(false)
    private val systemFavoritePickerLoading = MutableStateFlow(false)
    private val systemFavoritePickerGames = MutableStateFlow<List<RaCompletionGame>>(emptyList())
    private val systemFavoritePickerError = MutableStateFlow<String?>(null)
    private val achievementsPanelExpanded = MutableStateFlow(false)
    private val achievementsUi = MutableStateFlow(AchievementsUiState())
    private val raLibraryUi = MutableStateFlow(RaLibraryUiState())
    private var homePageBeforeRaLibrary: HomePage = HomePage.Home
    private val guideOpen = MutableStateFlow(false)
    private val guideSelectedIndex = MutableStateFlow(0)
    private val startSettingsOpen = MutableStateFlow(false)
    private val startSettingsCategory = MutableStateFlow(StartSettingsCategory.Display)
    private val startSettingsRowIndex = MutableStateFlow(0)
    private val raSettingsState = MutableStateFlow(RetroAchievementsSettings())
    private val xoraEmulatorSettingsState = MutableStateFlow(XoraEmulatorSettings())
    private val welcomeBackOpen = MutableStateFlow(false)
    private val bootIntroOpen = MutableStateFlow(false)
    private val bootIntroSkip = MutableStateFlow(false)
    private val homeIntroReveal = MutableStateFlow(true)
    private val isScraping = MutableStateFlow(false)
    private val lastInputAt = MutableStateFlow(SystemClock.elapsedRealtime())
    /** False while Settings / options dialog own the shell, or the host asks to pause trailers. */
    private val trailerGateAllowed = MutableStateFlow(true)
    private val trailerPlayback = MutableStateFlow(HeroTrailerState())
    private val insightUi = MutableStateFlow(GameInsightUiState())

    /** ElapsedRealtime when the shell last paused; null until the first onPause. */
    private var backgroundedAtElapsed: Long? = null
    /** True when the last pause happened while the display was not interactive (screen off). */
    private var pausedWhileScreenOff: Boolean = false
    /**
     * Process-start boot candidate. Consumed on the first [onResumed] that can decide
     * (onboarding complete → play boot clip; incomplete → skip without showing later).
     */
    private var pendingColdStartWelcome: Boolean = true

    /** Bumped to force a re-read of permission state, which is not observable. */
    private val refreshTrigger = MutableStateFlow(0)

    /**
     * Which physical display currently shows the grid, and which shows the other pane. Supplied by
     * the shell because display topology is a hosting concern, but needed here because a Confirm
     * from the gamepad is handled entirely inside this ViewModel.
     */
    private var gridDisplayId: Int? = null
    private var otherDisplayId: Int? = null

    /** First Steam friends pull only seeds online ids (avoids a banner storm on open). */
    private var steamOnlineSeeded = false
    private val knownOnlineSteamIds = linkedSetOf<String>()
    /** First XOrA Network snapshot after sign-in only seeds — no replaying the backlog as toasts. */
    private var xoraSocialSeeded = false
    private val knownOnlineXoraUsernames = linkedSetOf<String>()
    private val knownXoraInviteUsernames = linkedSetOf<String>()
    private val knownXoraNotificationIds = linkedSetOf<String>()
    private val knownNetplayInviteKeys = linkedSetOf<String>()
    /** First RA recent-unlock poll only seeds ids so historical unlocks do not toast. */
    private var raUnlockSeeded = false
    private val knownRaUnlockKeys = linkedSetOf<String>()
    private var libraryScanWasRunning = false

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

    /**
     * Bottom sheets (scrape & library, Choose Emulator) are touch-first Material widgets, and
     * [com.arcadia.shell.input.GamepadDispatcher] consumes controller keys during Activity key
     * dispatch, so those sheets never receive D-pad focus or stick motion on their own. The shell
     * flips [bottomSheetNavOpen] while one is showing and the sheet drives itself from
     * [sheetNavActions] instead.
     */
    private val bottomSheetNavOpen = MutableStateFlow(false)

    private val sheetNavActions = MutableSharedFlow<NavAction>(extraBufferCapacity = 32)
    val sheetNavActionFlow: SharedFlow<NavAction> = sheetNavActions.asSharedFlow()

    fun setBottomSheetNavOpen(open: Boolean) {
        bottomSheetNavOpen.value = open
    }

    private val libraryFlow = combine(
        libraryRepository.observeGames(),
        libraryRepository.observePlatformSummaries(),
        rootManager.observeRoots(),
    ) { games, summaries, roots -> Triple(games, summaries, roots) }

    private val transientFlow = combine(
        resolvedPlayerName,
        isLaunching,
        refreshTrigger,
    ) { playerName, launching, _ -> playerName to launching }

    /** The stored profile plus the linked Discord / XOrA Network avatars it may be pointing at. */
    private val profileIdentityFlow = combine(
        preferences.profile,
        discordRichPresence.state
            .map { it.currentUserAvatarUrl }
            .distinctUntilChanged(),
        xoraNetwork.state
            .map { it.account?.resolvedAvatarUrl }
            .distinctUntilChanged(),
    ) { profile, discordAvatarUrl, xoraAvatarUrl ->
        Triple(profile, discordAvatarUrl, xoraAvatarUrl)
    }

    private val panelFlow = combine(
        accountPanelExpanded,
        systemPanelExpanded,
        achievementsPanelExpanded,
        profileIdentityFlow,
        achievementsUi,
    ) { accountOpen, systemOpen, achievementsOpen, identity, achievements ->
        PanelChrome(
            accountExpanded = accountOpen,
            systemExpanded = systemOpen,
            achievementsExpanded = achievementsOpen,
            profile = identity.first,
            discordAvatarUrl = identity.second,
            xoraAvatarUrl = identity.third,
            achievements = achievements,
        )
    }

    private val chromeFlow = combine(
        combine(
            preferences.settings,
            preferences.hiddenGameIds,
            preferences.gameArtAlignments,
        ) { settings, hidden, alignments -> Triple(settings, hidden, alignments) },
        scanner.progress,
        selection,
        transientFlow,
        panelFlow,
    ) { prefs, progress, currentSelection, transient, panels ->
        val (settings, hiddenGameIds, artAlignments) = prefs
        ChromeState(
            settings = settings,
            hiddenGameIds = hiddenGameIds,
            artAlignments = artAlignments,
            progress = progress,
            selection = currentSelection,
            resolvedPlayerName = transient.first,
            isLaunching = transient.second,
            accountPanelExpanded = panels.accountExpanded,
            systemPanelExpanded = panels.systemExpanded,
            achievementsPanelExpanded = panels.achievementsExpanded,
            profile = panels.profile,
            profileAvatarModel = resolveAvatarModel(
                profile = panels.profile,
                raUsername = panels.achievements.credentials.username.takeIf {
                    panels.achievements.credentials.isConfigured
                },
                discordAvatarUrl = panels.discordAvatarUrl,
                xoraAvatarUrl = panels.xoraAvatarUrl,
            ),
            achievements = panels.achievements,
        )
    }

    private data class PanelChrome(
        val accountExpanded: Boolean,
        val systemExpanded: Boolean,
        val achievementsExpanded: Boolean,
        val profile: LocalProfile,
        val discordAvatarUrl: String?,
        val xoraAvatarUrl: String?,
        val achievements: AchievementsUiState,
    )

    private data class ChromeState(
        val settings: ShellSettings,
        val hiddenGameIds: Set<String> = emptySet(),
        val artAlignments: Map<String, GameArtAlignment> = emptyMap(),
        val progress: ScanProgress,
        val selection: Selection,
        val resolvedPlayerName: String?,
        val isLaunching: Boolean,
        val accountPanelExpanded: Boolean,
        val systemPanelExpanded: Boolean,
        val achievementsPanelExpanded: Boolean,
        val profile: LocalProfile,
        val profileAvatarModel: String?,
        val achievements: AchievementsUiState,
    )

    private val guideFlow = combine(
        guideOpen,
        guideSelectedIndex,
        combine(startSettingsOpen, startSettingsCategory, startSettingsRowIndex) { open, category, row ->
            Triple(open, category, row)
        },
        isScraping,
        raSettingsState,
    ) { open, index, start, scraping, ra ->
        GuideAndStartChrome(
            guideOpen = open,
            guideSelectedIndex = index,
            startSettingsOpen = start.first,
            startSettingsCategory = start.second,
            startSettingsRowIndex = start.third,
            isScraping = scraping,
            raSettings = ra,
        )
    }

    private data class GuideAndStartChrome(
        val guideOpen: Boolean,
        val guideSelectedIndex: Int,
        val startSettingsOpen: Boolean,
        val startSettingsCategory: StartSettingsCategory,
        val startSettingsRowIndex: Int,
        val isScraping: Boolean,
        val raSettings: RetroAchievementsSettings,
    )

    private val notificationCenterFlow = combine(
        notificationsOpen,
        shellNotifications.recent,
    ) { open, recent -> open to recent }

    private val socialPartnersFlow = combine(
        steamFriendsUi,
        discordSocialUi,
        conversationsUi,
        conversationReply,
        combine(
            combine(circlePins, managingCircle, friendSearchQuery, ::Triple),
            discordRichPresence.dmThread,
            notificationCenterFlow,
        ) { circle, dm, notifications -> Triple(circle, dm, notifications) },
    ) { steam, discord, conversations, reply, extra ->
        val (circle, dm, notifications) = extra
        SocialPartners(
            steam = steam,
            discord = discord,
            conversations = conversations,
            reply = reply,
            discordDm = dm,
            circlePins = circle.first,
            managingCircle = circle.second,
            friendSearchQuery = circle.third,
            notificationsOpen = notifications.first,
            recentNotifications = notifications.second,
        )
    }

    private data class SocialPartners(
        val steam: SteamFriendsUiState,
        val discord: DiscordSocialUiState,
        val conversations: ConversationsUiState,
        val reply: ConversationReplyUiState,
        val discordDm: DiscordDmThreadUiState,
        val circlePins: List<CirclePin>,
        val managingCircle: Boolean,
        val friendSearchQuery: String,
        val notificationsOpen: Boolean,
        val recentNotifications: List<ShellNotification>,
    )

    private val socialFlow = combine(
        socialMenuTab,
        socialPartnersFlow,
        accountPanelSelectedIndex,
        profileEditRequest,
        xoraNetwork.state,
    ) { tab, partners, accountIndex, editRequest, xora ->
        SocialChrome(
            tab = tab,
            steam = partners.steam,
            discord = partners.discord,
            conversations = partners.conversations,
            reply = partners.reply,
            discordDm = partners.discordDm,
            circlePins = partners.circlePins,
            managingCircle = partners.managingCircle,
            friendSearchQuery = partners.friendSearchQuery,
            notificationsOpen = partners.notificationsOpen,
            recentNotifications = partners.recentNotifications,
            accountPanelSelectedIndex = accountIndex,
            profileEditRequest = editRequest,
            xoraNetwork = xora,
        )
    }

    private data class SocialChrome(
        val tab: SocialMenuTab,
        val steam: SteamFriendsUiState,
        val discord: DiscordSocialUiState,
        val conversations: ConversationsUiState,
        val reply: ConversationReplyUiState,
        val discordDm: DiscordDmThreadUiState,
        val circlePins: List<CirclePin>,
        val managingCircle: Boolean,
        val friendSearchQuery: String,
        val notificationsOpen: Boolean,
        val recentNotifications: List<ShellNotification>,
        val accountPanelSelectedIndex: Int,
        val profileEditRequest: Int,
        val xoraNetwork: com.arcadia.shell.xoranetwork.XoraNetworkState,
    )

    private val overlayFlow = combine(
        combine(homePage, rssUi, raLibraryUi, ::Triple),
        guideFlow,
        socialFlow,
        xoraEmulatorSettingsState,
    ) { pageRssRa, guide, social, xoraEmulator ->
        val (page, rss, ra) = pageRssRa
        OverlayChrome(
            homePage = page,
            rss = rss,
            raLibrary = ra,
            guideOpen = guide.guideOpen,
            guideSelectedIndex = guide.guideSelectedIndex,
            startSettingsOpen = guide.startSettingsOpen,
            startSettingsCategory = guide.startSettingsCategory,
            startSettingsRowIndex = guide.startSettingsRowIndex,
            isScraping = guide.isScraping,
            raSettings = guide.raSettings,
            xoraEmulator = xoraEmulator,
            social = social,
        )
    }

    private val homeHubNavFlow = combine(
        combine(
            homeHubSection,
            homeShard,
            homeShortcutIndex,
            homeShortcutsEditMode,
        ) { section, shard, shortcutIndex, editMode ->
            HomeHubNavCore(
                section = section,
                shard = shard,
                shortcutIndex = shortcutIndex,
                editMode = editMode,
            )
        },
        combine(
            shortcutGridColumns,
            shortcutGridRows,
            shortcutCustomizeChrome,
            combine(
                vitaShortcutTrayOpen,
                vitaShortcutPinMode,
                vitaShortcutLaunch,
                vitaShortcutDepartingIndex,
            ) { open, pin, launch, departing ->
                VitaTrayChrome(open, pin, launch, departing)
            },
        ) { columns, rows, chrome, tray ->
            HomeHubLayout(
                columns = columns,
                rows = rows,
                customizeChrome = chrome,
                vitaShortcutTrayOpen = tray.open,
                vitaShortcutPinMode = tray.pin,
                vitaShortcutLaunch = tray.launch,
                vitaShortcutDepartingIndex = tray.departingIndex,
            )
        },
    ) { core, layout ->
        HomeHubNav(
            section = core.section,
            shard = core.shard,
            shortcutIndex = core.shortcutIndex,
            editMode = core.editMode,
            gridColumns = layout.columns,
            gridRows = layout.rows,
            customizeChrome = layout.customizeChrome,
            vitaShortcutTrayOpen = layout.vitaShortcutTrayOpen,
            vitaShortcutPinMode = layout.vitaShortcutPinMode,
            vitaShortcutLaunch = layout.vitaShortcutLaunch,
            vitaShortcutDepartingIndex = layout.vitaShortcutDepartingIndex,
        )
    }

    private data class HomeHubNavCore(
        val section: HomeHubSection,
        val shard: HomeShard,
        val shortcutIndex: Int,
        val editMode: Boolean,
    )

    private data class HomeHubLayout(
        val columns: Int,
        val rows: Int,
        val customizeChrome: ShortcutCustomizeChrome,
        val vitaShortcutTrayOpen: Boolean,
        val vitaShortcutPinMode: Boolean,
        val vitaShortcutLaunch: VitaShortcutLaunchUi?,
        val vitaShortcutDepartingIndex: Int?,
    )

    private data class VitaTrayChrome(
        val open: Boolean,
        val pin: Boolean,
        val launch: VitaShortcutLaunchUi?,
        val departingIndex: Int?,
    )

    private data class HomeHubNav(
        val section: HomeHubSection,
        val shard: HomeShard,
        val shortcutIndex: Int,
        val editMode: Boolean,
        val gridColumns: Int,
        val gridRows: Int,
        val customizeChrome: ShortcutCustomizeChrome,
        val vitaShortcutTrayOpen: Boolean,
        val vitaShortcutPinMode: Boolean,
        val vitaShortcutLaunch: VitaShortcutLaunchUi?,
        val vitaShortcutDepartingIndex: Int?,
    )

    private val addShortcutChromeFlow = combine(
        addShortcutOpen,
        shortcutTargetPicker,
        pendingShortcutKind,
        pendingShortcutSpan,
    ) { open, picker, kind, span ->
        AddShortcutChrome(
            open = open,
            targetPicker = picker,
            pendingKind = kind,
            pendingSpan = span,
        )
    }

    private data class AddShortcutChrome(
        val open: Boolean,
        val targetPicker: ShortcutTargetPickerUiState?,
        val pendingKind: PendingShortcutKind?,
        val pendingSpan: ShortcutSpan,
    )

    private data class XoraNavChrome(
        val categoryIndex: Int,
        val itemIndex: Int,
        val depth: XoraXmbDepth,
        val drilledPlatformId: String?,
    )

    private val xoraNavFlow = combine(
        xoraCategoryIndex,
        xoraItemIndex,
        xoraDepth,
        xoraDrilledPlatformId,
    ) { categoryIndex, itemIndex, depth, drilledPlatformId ->
        XoraNavChrome(
            categoryIndex = categoryIndex,
            itemIndex = itemIndex,
            depth = depth,
            drilledPlatformId = drilledPlatformId,
        )
    }

    private val homeThemeFlow = combine(
        preferences.settings.map {
            Triple(
                it.homeWallpaperPath,
                it.customBgmPath,
                GameArtAlignment(it.wallpaperAlignX, it.wallpaperAlignY),
            )
        },
        homeShortcuts,
        homeHubNavFlow,
        addShortcutChromeFlow,
        combine(
            combine(themesOpen, themesSheetTab) { open, tab -> open to tab },
            xoraNavFlow,
        ) { themes, xora -> themes to xora },
    ) { themePaths, shortcuts, nav, addChrome, themesAndXora ->
        val themes = themesAndXora.first
        HomeThemeChrome(
            wallpaperPath = themePaths.first,
            wallpaperAlignX = themePaths.third.x,
            wallpaperAlignY = themePaths.third.y,
            customBgmPath = themePaths.second,
            shortcuts = shortcuts,
            nav = nav,
            addShortcutOpen = addChrome.open,
            shortcutTargetPicker = addChrome.targetPicker,
            pendingShortcutKind = addChrome.pendingKind,
            pendingShortcutSpan = addChrome.pendingSpan,
            themesOpen = themes.first,
            themesSheetTab = themes.second,
            xora = themesAndXora.second,
        )
    }

    private data class HomeThemeChrome(
        val wallpaperPath: String?,
        val wallpaperAlignX: Float,
        val wallpaperAlignY: Float,
        val customBgmPath: String?,
        val shortcuts: List<HomeShortcut>,
        val nav: HomeHubNav,
        val addShortcutOpen: Boolean,
        val shortcutTargetPicker: ShortcutTargetPickerUiState?,
        val pendingShortcutKind: PendingShortcutKind?,
        val pendingShortcutSpan: ShortcutSpan,
        val themesOpen: Boolean,
        val themesSheetTab: ThemesSheetTab,
        val xora: XoraNavChrome,
    )

    private data class OverlayChrome(
        val homePage: HomePage,
        val rss: RssUiState,
        val raLibrary: RaLibraryUiState,
        val guideOpen: Boolean,
        val guideSelectedIndex: Int,
        val startSettingsOpen: Boolean,
        val startSettingsCategory: StartSettingsCategory,
        val startSettingsRowIndex: Int,
        val isScraping: Boolean,
        val raSettings: RetroAchievementsSettings,
        val xoraEmulator: XoraEmulatorSettings,
        val social: SocialChrome,
    )

    /** Banner art, built-in cores, DSP link state and music for XMB card rungs. */
    private data class PlatformChrome(
        val artByPlatformId: Map<String, String> = emptyMap(),
        val readyPlatformIds: Set<String> = emptySet(),
        val spotifyLinked: Boolean = false,
        val music: MusicUiState = MusicUiState(),
        val photoFolders: List<DeviceMediaFolder> = emptyList(),
        val videoFolders: List<DeviceMediaFolder> = emptyList(),
        val customMediaEpoch: Int = 0,
    )

    private data class MediaFolders(
        val photos: List<DeviceMediaFolder> = emptyList(),
        val videos: List<DeviceMediaFolder> = emptyList(),
    )

    private val mediaFolders = MutableStateFlow(MediaFolders())

    /** Music browse rungs; the Now Playing state is owned by the shared controller. */
    private val musicUi = MutableStateFlow(MusicUiState())

    /** Media → Photos gallery / viewer / edit / delete state. */
    private val photosUi = MutableStateFlow(PhotosUiState())

    /** XOrA Network → Dashboard navigation + form state (account data lives in the repository). */
    private val dashboardUi = MutableStateFlow(XoraDashboardUiState())
    private val dashboardFlow = combine(dashboardUi, xoraNetwork.state) { ui, network ->
        ui.copy(network = network)
    }
    private var photoSlideshowJob: Job? = null
    private var photoControlsHideJob: Job? = null
    private var raGameDetailJob: Job? = null
    private var pendingDeletePhotoId: String? = null
    /** Debounce for layer-changing photo actions so one press cannot fire through two layers. */
    private var lastPhotoLayerActionMs = 0L

    private val musicFlow = combine(
        musicUi,
        nowPlayingController.state,
    ) { music, nowPlaying -> music.copy(nowPlaying = nowPlaying) }

    /** Systems the built-in emulator ships a core for — the tick on a console card. */
    private val xoraEmulatedPlatformIds: Set<String> =
        xoraCoreCatalog.all.mapTo(mutableSetOf()) { it.platformId }

    private val customMediaEpoch = MutableStateFlow(0)

    private val platformChromeFlow = combine(
        combine(
            platformArtRepository.artByPlatformId,
            platformArtStore.bannerByPlatformId,
            spotifyTokenStore.linked,
            musicFlow,
            mediaFolders,
        ) { scraped, custom, spotifyLinked, music, folders ->
            PlatformChrome(
                // A banner the player picked themselves always beats the scraped system media.
                artByPlatformId = scraped + custom,
                readyPlatformIds = xoraEmulatedPlatformIds,
                spotifyLinked = spotifyLinked,
                music = music,
                photoFolders = folders.photos,
                videoFolders = folders.videos,
            )
        },
        customMediaEpoch,
    ) { chrome, epoch -> chrome.copy(customMediaEpoch = epoch) }

    private val libraryUiState: StateFlow<HomeUiState> = combine(
        libraryFlow,
        chromeFlow,
        overlayFlow,
        homeThemeFlow,
        platformChromeFlow,
    ) { library, chrome, overlay, theme, platformChrome ->
        buildState(
            allGames = library.first,
            summaries = library.second,
            roots = library.third,
            chrome = chrome,
            homePage = overlay.homePage,
            rss = overlay.rss,
            raLibrary = overlay.raLibrary,
            guideOpen = overlay.guideOpen,
            guideSelectedIndex = overlay.guideSelectedIndex,
            startSettingsOpen = overlay.startSettingsOpen,
            startSettingsCategory = overlay.startSettingsCategory,
            startSettingsRowIndex = overlay.startSettingsRowIndex,
            isScraping = overlay.isScraping,
            raSettings = overlay.raSettings,
            xoraEmulator = overlay.xoraEmulator,
            social = overlay.social,
            theme = theme,
            platformChrome = platformChrome,
        )
    }.stateIn(
        scope = viewModelScope,
        // Eagerly, because navigation actions read uiState.value synchronously and must never
        // observe a stale default while the UI is still attaching.
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    private val notificationChrome = combine(
        notificationHistoryOpen,
        notificationHistorySelectedIndex,
        shellNotifications.history,
        shellNotifications.unreadCount,
        combine(
            pendingNetplayInvite,
            netplayInvitePromptOpen,
            shellNotifications.active,
        ) { invite, prompt, active ->
            Triple(invite, prompt, active != null)
        },
    ) { open, selected, history, unread, invite ->
        NotificationChrome(
            open,
            selected,
            history,
            unread,
            invite.first,
            invite.second,
            invite.third,
        )
    }

    private data class NotificationChrome(
        val open: Boolean,
        val selectedIndex: Int,
        val history: List<com.arcadia.shell.launcher.notifications.ShellNotificationHistoryItem>,
        val unreadCount: Int,
        val pendingInvite: NetplayInvitePrompt?,
        val invitePromptOpen: Boolean,
        val activePresent: Boolean,
    )

    private data class WakeChrome(
        val welcomeBack: Boolean,
        val bootIntro: Boolean,
        val bootSkip: Boolean,
        val homeIntroReveal: Boolean,
        val notif: NotificationChrome,
    )

    private data class AuxChrome(
        val welcomeBack: Boolean,
        val bootIntro: Boolean,
        val bootSkip: Boolean,
        val homeIntroReveal: Boolean,
        val notif: NotificationChrome,
        val systemProfile: SystemProfileCardState,
        val photos: PhotosUiState,
        val dashboard: XoraDashboardUiState,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        libraryUiState,
        trailerPlayback,
        insightUi,
        systemPanelSelectedIndex,
        combine(
            combine(
                welcomeBackOpen,
                bootIntroOpen,
                bootIntroSkip,
                homeIntroReveal,
                notificationChrome,
            ) { welcome, boot, skip, reveal, notif ->
                WakeChrome(welcome, boot, skip, reveal, notif)
            },
            systemProfileChromeFlow(),
            photosUi,
            dashboardFlow,
        ) { wake, systemProfile, photos, dashboard ->
            AuxChrome(
                welcomeBack = wake.welcomeBack,
                bootIntro = wake.bootIntro,
                bootSkip = wake.bootSkip,
                homeIntroReveal = wake.homeIntroReveal,
                notif = wake.notif,
                systemProfile = systemProfile,
                photos = photos,
                dashboard = dashboard,
            )
        },
    ) { base, trailer, insight, systemIndex, aux ->
        base.copy(
            trailer = trailer.copy(
                iconIdleMedia = base.startSettings.settings.gameIconIdleMedia,
                screenshotPaths = run {
                    val focused = base.xoraXmb.focusGame ?: base.selectedGame
                    val fromInsight = insight.screenshotPaths.takeIf {
                        insight.gameId != null && insight.gameId == focused?.id
                    }.orEmpty()
                    fromInsight.ifEmpty { listOfNotNull(focused?.heroImagePath) }
                },
            ),
            insight = insight,
            systemPanelSelectedIndex = systemIndex,
            welcomeBackOpen = aux.welcomeBack,
            bootIntroOpen = aux.bootIntro,
            bootIntroSkip = aux.bootSkip,
            homeIntroReveal = aux.homeIntroReveal,
            notificationHistoryOpen = aux.notif.open,
            notificationHistory = aux.notif.history,
            notificationUnreadCount = aux.notif.unreadCount,
            notificationHistorySelectedIndex = aux.notif.selectedIndex
                .coerceIn(0, if (aux.notif.history.isEmpty()) 0 else aux.notif.history.size),
            activeNotificationPresent = aux.notif.activePresent,
            pendingNetplayInvite = aux.notif.pendingInvite,
            netplayInvitePromptOpen = aux.notif.invitePromptOpen,
            systemProfile = aux.systemProfile,
            photos = aux.photos,
            dashboard = aux.dashboard,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    init {
        refreshInstalledApps()
        // Warm the feed in the background; Home must not wait on network at startup.
        refreshRssFeed()
        migrateTrailerPipeline()
        // Restore the persisted XOrA Network session so sign-in survives process death.
        viewModelScope.launch {
            xoraNetwork.restore()
            if (xoraNetwork.state.value.signedIn) {
                applyXoraNetworkIdentity(forceAvatar = false)
            }
        }
        // Friend-online / friend-request / message / netplay banners from XOrA Network state diffs.
        xoraNetwork.state
            .onEach { emitXoraNetworkBanners(it) }
            .launchIn(viewModelScope)
        preferences.profile
            .map { parseXoraPresenceMode(it.xoraPresenceMode) }
            .distinctUntilChanged()
            .onEach { xoraNetwork.setPresenceMode(it) }
            .launchIn(viewModelScope)
        discordRichPresence.state
            .map { resolveActivityStatusLine(it.activity) }
            .distinctUntilChanged()
            .onEach { xoraNetwork.setPlayingLine(it) }
            .launchIn(viewModelScope)
        // Poll friends + inbox only while the shell is actually in the foreground — an asleep or
        // backgrounded device must not wake the radio every minute (battery / fan complaint).
        // Inbox is the website `/api/notifications` list (DMs never land in Nakama storage);
        // poll it often enough that DMs toast quickly. Friends ride a slower cadence.
        viewModelScope.launch {
            appForegroundTracker.isForeground.collectLatest { foreground ->
                xoraNetwork.setRealtimeEnabled(foreground)
                if (!foreground) return@collectLatest
                xoraNetwork.state
                    .map { it.signedIn }
                    .distinctUntilChanged()
                    .collectLatest { signedIn ->
                        if (!signedIn) return@collectLatest
                        var ticks = 0
                        while (isActive) {
                            xoraNetwork.refreshNotifications()
                            xoraNetwork.refreshNetplayInvites()
                            val friendEvery = (XORA_SOCIAL_POLL_MS / XORA_INBOX_POLL_MS).toInt().coerceAtLeast(1)
                            if (ticks % friendEvery == 0) xoraNetwork.refreshFriends()
                            ticks++
                            delay(XORA_INBOX_POLL_MS)
                        }
                    }
            }
        }
        viewModelScope.launch {
            appForegroundTracker.isForeground.collectLatest { foreground ->
                if (!foreground) return@collectLatest
                xoraNetwork.state
                    .map { it.signedIn }
                    .distinctUntilChanged()
                    .collectLatest { signedIn ->
                        if (!signedIn) return@collectLatest
                        while (isActive) {
                            delay(XORA_NETPLAY_INVITE_POLL_MS)
                            xoraNetwork.refreshNetplayInvites()
                        }
                    }
            }
        }
        // Live chat: while an XOrA DM is open in the foreground, poll that thread so the peer's
        // replies appear in place. Banners are suppressed for the open peer, so without this the
        // conversation looked dead — both sides thought messages weren't going through.
        viewModelScope.launch {
            appForegroundTracker.isForeground.collectLatest { foreground ->
                if (!foreground) return@collectLatest
                xoraNetwork.state
                    .map { it.dm.peerUsername }
                    .distinctUntilChanged()
                    .collectLatest { peer ->
                        if (peer.isNullOrBlank()) return@collectLatest
                        while (isActive) {
                            // openDirectMessage just loaded the thread; wait before re-pulling.
                            delay(XORA_DM_POLL_MS)
                            xoraNetwork.refreshOpenDirectMessage()
                        }
                    }
            }
        }

        scraperScheduler.isRunning()
            .onEach { isScraping.value = it }
            .launchIn(viewModelScope)

        // Console product art for XMB All Games → system rows (ScreenScraper system media).
        libraryRepository.observePlatformSummaries()
            .map { summaries ->
                summaries
                    .filter { it.gameCount > 0 && it.platform.id != "android" }
                    .map { it.platform.id }
            }
            .distinctUntilChanged()
            .onEach { platformIds ->
                runCatching { platformArtRepository.ensureArt(platformIds) }
                    .onFailure { Log.w("HomeViewModel", "Platform art scrape failed", it) }
            }
            .launchIn(viewModelScope)

        preferences.homeShortcuts
            .onEach { homeShortcuts.value = it }
            .launchIn(viewModelScope)

        preferences.favoritePhotoIds
            .onEach { favorites -> photosUi.update { it.copy(favoriteIds = favorites) } }
            .launchIn(viewModelScope)

        preferences.homeShortcutGridLayout
            .onEach { layout ->
                shortcutGridColumns.value = layout.columns
                shortcutGridRows.value = layout.rows
            }
            .launchIn(viewModelScope)

        preferences.retroAchievementsSettings
            .onEach { raSettingsState.value = it }
            .launchIn(viewModelScope)

        preferences.xoraEmulatorSettings
            .onEach { xoraEmulatorSettingsState.value = it }
            .launchIn(viewModelScope)

        gamepadDispatcher.actions
            .onEach { action ->
                noteUserActivity()
                onNavAction(action)
            }
            .launchIn(viewModelScope)

        // Keep the pre-action Cancel flag in sync so LT/RT window dismiss can use NavClose.
        combine(
            combine(accountPanelExpanded, systemPanelExpanded, ::Pair),
            combine(notificationsOpen, conversationReply, discordRichPresence.dmThread) { notifications, reply, dm ->
                Triple(notifications, reply.conversationKey != null, dm.peerUserId != null)
            },
            combine(systemFavoritePickerOpen, systemStatusEditorOpen, ::Pair),
        ) { panels, nested, pickers ->
            val (account, system) = panels
            val (notifications, replyOpen, dmOpen) = nested
            val (favoritePicker, statusEditor) = pickers
            when {
                account -> !notifications && !replyOpen && !dmOpen
                system -> !favoritePicker && !statusEditor
                else -> false
            }
        }
            .distinctUntilChanged()
            .onEach { gamepadDispatcher.heroPanelClosesOnCancel = it }
            .launchIn(viewModelScope)

        combine(vitaShortcutTrayOpen, vitaShortcutLaunch) { open, launch ->
            open && launch == null
        }
            .distinctUntilChanged()
            .onEach { gamepadDispatcher.vitaBubbleLaunchSfx = it }
            .launchIn(viewModelScope)

        observeIdleTrailer()

        retroAchievements.credentials
            .onEach { creds ->
                achievementsUi.update {
                    it.copy(credentials = creds, needsLogin = !creds.isConfigured && it.needsLogin)
                }
            }
            .launchIn(viewModelScope)

        preferences.steamWebApi
            .onEach { creds ->
                val previous = steamFriendsUi.value.credentials
                steamFriendsUi.update {
                    it.copy(
                        credentials = creds,
                        friends = if (creds.isConfigured) it.friends else emptyList(),
                        error = if (creds.isConfigured) it.error else null,
                    )
                }
                if (creds != previous && creds.isConfigured && accountPanelExpanded.value) {
                    refreshSteamFriends()
                }
                conversationsUi.update { enrichSteamHints(it, steamFriendsUi.value) }
            }
            .launchIn(viewModelScope)

        preferences.discordSocial
            .onEach { settings ->
                discordSocialUi.update {
                    it.copy(settings = settings, presence = discordRichPresence.state.value)
                }
            }
            .launchIn(viewModelScope)

        discordRichPresence.state
            .onEach { presence ->
                discordSocialUi.update { it.copy(presence = presence) }
            }
            .launchIn(viewModelScope)

        // Focused ROM sound bite: imported clip, or Game name.mp3 / .wav next to the ROM.
        combine(
            uiState
                .map { state ->
                    if (state.isLaunching) return@map null
                    val game = state.xoraXmb.focusGame
                        ?.takeIf { !it.isAndroidApp }
                        ?.takeIf {
                            state.xoraXmb.depth == XoraXmbDepth.Roms ||
                                state.xoraXmb.selectedItem?.action is XoraXmbAction.LaunchGame ||
                                state.xoraXmb.selectedItem?.action is
                                    XoraXmbAction.LaunchContinueOrFavorite
                        }
                        ?: state.selectedGame?.takeIf {
                            state.homePage == HomePage.GameSelector && !it.isAndroidApp
                        }
                    game?.let { focused ->
                        SoundBiteFocus(
                            id = focused.id,
                            importedPath = focused.soundBitePath,
                            romFilePath = focused.filePath,
                            title = focused.title,
                            fileName = focused.fileName,
                        )
                    }
                }
                .distinctUntilChanged(),
            appForegroundTracker.isForeground,
        ) { focus, foreground ->
            if (!foreground || focus == null) {
                null
            } else {
                RomSoundBiteLocator.resolve(
                    explicitPath = focus.importedPath,
                    romFilePath = focus.romFilePath,
                    title = focus.title,
                    romFileName = focus.fileName,
                )
            }
        }
            .distinctUntilChanged()
            .transformLatest { path ->
                gameSoundBitePlayer.stop()
                if (path.isNullOrBlank()) return@transformLatest
                delay(XMB_FOCUS_SETTLE_MS)
                emit(path)
            }
            .onEach { path -> gameSoundBitePlayer.play(path) }
            .launchIn(viewModelScope)

        // Track library browsing for Discord status bridge / future Social SDK presence.
        uiState
            .map { it.selectedGame }
            .distinctUntilChangedBy { it?.id }
            .onEach { game ->
                if (!discordRichPresence.state.value.isConfigured) return@onEach
                when {
                    game == null -> discordRichPresence.setActivity(DiscordPresenceActivity.InSora)
                    else -> discordRichPresence.setActivity(
                        DiscordPresenceActivity.Browsing(
                            gameTitle = game.title,
                            platformName = game.platform.displayName,
                        ),
                    )
                }
            }
            .launchIn(viewModelScope)

        preferences.circlePins
            .onEach { pins -> circlePins.value = pins }
            .launchIn(viewModelScope)

        conversationRepository.state
            .onEach { conversationsUi.value = enrichSteamHints(it, steamFriendsUi.value) }
            .launchIn(viewModelScope)

        // Re-check notification access when the social panel opens (permission changes off-process).
        accountPanelExpanded
            .onEach { open ->
                if (open) {
                    conversationRepository.refreshListenerEnabled()
                } else {
                    // Android notification-listener reply draft lives in the Social pill.
                    // Discord DMs use DiscordConversationWindow — do NOT closeDm() here or
                    // collapsing Social on open immediately kills the conversation.
                    conversationReply.value = ConversationReplyUiState()
                }
            }
            .launchIn(viewModelScope)

        // Resolving which emulator handles a game hits the database, so it is recomputed only when
        // the selection actually lands on a different game. mapLatest cancels a lookup that is
        // already in flight when the selection moves on, which is the common case while scrolling.
        @OptIn(ExperimentalCoroutinesApi::class)
        uiState
            .map { it.selectedGame?.id }
            .distinctUntilChanged()
            .mapLatest { gameId ->
                val game = gameId?.let { libraryRepository.findById(it) } ?: return@mapLatest null
                if (game.isAndroidApp) "Open app" else launcher.resolvePlayer(game)?.name
            }
            .onEach { resolvedPlayerName.value = it }
            .launchIn(viewModelScope)

        observeGameInsights()

        @OptIn(ExperimentalCoroutinesApi::class)
        combine(
            achievementsPanelExpanded,
            uiState.map { state ->
                state.homeHub.vitaShortcutLaunch?.game?.id
                    ?: state.xoraXmb.focusGame?.id?.takeIf { state.xoraXmb.showsAchievementsCard }
                    ?: state.selectedGame?.id
            }.distinctUntilChanged(),
            achievementsUi.map { it.tab }.distinctUntilChanged(),
            retroAchievements.credentials,
        ) { expanded, gameId, tab, creds ->
            AchievementsLoadRequest(expanded, gameId, tab, creds.isConfigured)
        }
            .distinctUntilChanged()
            .mapLatest { request ->
                if (!request.expanded) {
                    // mapLatest cancels an in-flight refresh when the panel closes; clear the
                    // spinner so a cancelled load cannot leave the UI stuck forever.
                    achievementsUi.update { it.copy(isLoading = false) }
                    return@mapLatest
                }
                refreshAchievements(request.gameId, request.tab, request.signedIn)
            }
            .launchIn(viewModelScope)

        uiState
            .map { state ->
                state.homePage == HomePage.Home &&
                    !state.xoraXmb.showsAchievementsCard &&
                    state.homeHub.vitaShortcutLaunch?.game == null
            }
            .distinctUntilChanged()
            .onEach { hideHomeCard ->
                if (hideHomeCard) achievementsPanelExpanded.value = false
            }
            .launchIn(viewModelScope)

        observeShellNotifications()
    }

    private data class AchievementsLoadRequest(
        val expanded: Boolean,
        val gameId: String?,
        val tab: AchievementsPaneTab,
        val signedIn: Boolean,
    )

    /**
     * Poll RA unlocks, watch library scan as download/install stand-in, and seed Steam online
     * diffs after friend refreshes. Discord friend-online + chat banners emit from launcher.
     */
    private fun observeShellNotifications() {
        // RA has no push channel, so unlocks have to be polled — but only while someone is
        // actually here. The emulator runs in this same process, so playing still counts as
        // foreground; a shell asleep in the background stops hitting the network entirely.
        viewModelScope.launch {
            appForegroundTracker.isForeground.collectLatest { foreground ->
                if (!foreground) return@collectLatest
                while (isActive) {
                    pollRetroAchievementUnlocks()
                    delay(RA_UNLOCK_POLL_MS)
                }
            }
        }

        scanner.progress
            .onEach { progress -> emitLibraryScanBanners(progress) }
            .launchIn(viewModelScope)
    }

    private suspend fun pollRetroAchievementUnlocks() {
        val raPrefs = preferences.retroAchievementsSettings.first()
        if (!raPrefs.enabled || !raPrefs.unlockNotifications) return
        val creds = retroAchievements.credentials.first()
        if (!creds.isConfigured) return
        val recent = retroAchievements.fetchRecentUnlocks().getOrElse { return }
        emitNewRaUnlockBanners(recent)
    }

    private fun emitNewRaUnlockBanners(recent: List<RaRecentUnlock>) {
        if (!raUnlockSeeded) {
            knownRaUnlockKeys.clear()
            knownRaUnlockKeys.addAll(recent.map { raUnlockKey(it) })
            raUnlockSeeded = true
            return
        }
        // API returns newest-first; emit oldest-first so the queue reads chronologically.
        val fresh = recent
            .filter { raUnlockKey(it) !in knownRaUnlockKeys }
            .asReversed()
        for (unlock in fresh) {
            val key = raUnlockKey(unlock)
            knownRaUnlockKeys.add(key)
            shellNotifications.emit(
                ShellNotification.AchievementUnlocked(
                    id = "ra:$key",
                    title = unlock.title,
                    description = unlock.description.takeIf { it.isNotBlank() },
                    points = unlock.points.takeIf { it > 0 },
                    badgeUrl = unlock.badgeUrl,
                    gameTitle = unlock.gameTitle.takeIf { it.isNotBlank() },
                    hardcore = unlock.hardcore,
                ),
            )
        }
        if (knownRaUnlockKeys.size > 200) {
            val keep = recent.map { raUnlockKey(it) }.toSet()
            knownRaUnlockKeys.retainAll(keep)
        }
    }

    private fun raUnlockKey(unlock: RaRecentUnlock): String =
        "${unlock.achievementId}|${unlock.date}|${unlock.hardcore}"

    private fun emitLibraryScanBanners(progress: ScanProgress) {
        if (progress.isRunning && !libraryScanWasRunning) {
            libraryScanWasRunning = true
            shellNotifications.emit(
                ShellNotification.GameDownloading(
                    id = "scan-start:${SystemClock.elapsedRealtime()}",
                    title = "Scanning library",
                    progressLabel = progress.currentRoot?.let { "Scanning $it…" } ?: "Looking for games…",
                ),
            )
        }
        if (!progress.isRunning && libraryScanWasRunning) {
            libraryScanWasRunning = false
            if (progress.error != null) return
            shellNotifications.emit(
                ShellNotification.InstallComplete(
                    id = "scan-done:${progress.finishedAt ?: SystemClock.elapsedRealtime()}",
                    title = "Library ready",
                    subtitle = when {
                        progress.gamesFound <= 0 -> "Scan finished"
                        progress.gamesFound == 1 -> "1 game found"
                        else -> "${progress.gamesFound} games found"
                    },
                ),
            )
        }
    }

    private fun emitSteamFriendOnlineBanners(friends: List<SteamFriendEntry>) {
        val onlineNow = friends.filter { it.presence != SocialPresence.Offline }
        val onlineIds = onlineNow.map { it.steamId }.toSet()
        if (!steamOnlineSeeded) {
            knownOnlineSteamIds.clear()
            knownOnlineSteamIds.addAll(onlineIds)
            steamOnlineSeeded = true
            return
        }
        for (friend in onlineNow) {
            if (friend.steamId in knownOnlineSteamIds) continue
            knownOnlineSteamIds.add(friend.steamId)
            shellNotifications.emit(
                ShellNotification.FriendOnline(
                    id = "steam-online:${friend.steamId}:${SystemClock.elapsedRealtime()}",
                    displayName = friend.displayName.ifBlank { "Steam friend" },
                    network = FriendNetwork.Steam,
                    avatarUrl = friend.avatarUrl,
                    activityLabel = friend.currentGame,
                ),
            )
        }
        knownOnlineSteamIds.retainAll(onlineIds)
    }

    /**
     * Banners + status-bar notifications from XOrA Network state diffs: a friend coming online,
     * an incoming friend request, a new inbox message, and a Netplay session invite.
     */
    private fun emitXoraNetworkBanners(network: com.arcadia.shell.xoranetwork.XoraNetworkState) {
        if (XoraNetworkBannerGate.shouldResetSession(network)) {
            xoraSocialSeeded = false
            knownOnlineXoraUsernames.clear()
            knownXoraInviteUsernames.clear()
            knownXoraNotificationIds.clear()
            knownNetplayInviteKeys.clear()
            return
        }
        if (XoraNetworkBannerGate.shouldWaitForInbox(network)) {
            return
        }
        val onlineNow = network.acceptedFriends.filter { it.online }
        val onlineNames = onlineNow.map { it.username.lowercase() }.toSet()
        val invitesNow = network.incomingInvites
        val inviteNames = invitesNow.map { it.username.lowercase() }.toSet()
        val notificationIds = network.notifications
            .map { it.id.ifBlank { it.createdAt + it.fromUsername + it.body } }
            .toSet()
        val netplayInviteKeys = network.netplayInvites.map { it.dedupeKey() }.toSet()
        if (!xoraSocialSeeded) {
            knownOnlineXoraUsernames.addAll(onlineNames)
            knownXoraInviteUsernames.addAll(inviteNames)
            knownXoraNotificationIds.addAll(notificationIds)
            knownNetplayInviteKeys.addAll(netplayInviteKeys)
            xoraSocialSeeded = true
            return
        }

        for (friend in onlineNow) {
            val key = friend.username.lowercase()
            if (key in knownOnlineXoraUsernames) continue
            knownOnlineXoraUsernames.add(key)
            shellNotifications.emit(
                ShellNotification.FriendOnline(
                    id = "xora-online:$key:${SystemClock.elapsedRealtime()}",
                    displayName = friend.displayName.ifBlank { friend.username },
                    network = FriendNetwork.Xora,
                    avatarUrl = friend.resolvedAvatarUrl,
                ),
            )
        }
        knownOnlineXoraUsernames.retainAll(onlineNames)

        // Friend requests can surface twice (friends list edge + inbox item) — announce once.
        val announcedRequests = mutableSetOf<String>()
        for (invite in invitesNow) {
            val key = invite.username.lowercase()
            if (key in knownXoraInviteUsernames) continue
            knownXoraInviteUsernames.add(key)
            announcedRequests.add(key)
            shellNotifications.emit(
                ShellNotification.XoraFriendRequest(
                    id = "xora-request:$key:${SystemClock.elapsedRealtime()}",
                    displayName = invite.displayName.ifBlank { invite.username },
                    avatarUrl = invite.resolvedAvatarUrl,
                ),
            )
        }
        knownXoraInviteUsernames.retainAll(inviteNames)

        for (item in network.notifications) {
            val key = item.id.ifBlank { item.createdAt + item.fromUsername + item.body }
            if (key in knownXoraNotificationIds) continue
            knownXoraNotificationIds.add(key)
            val fromKey = item.fromUsername.lowercase()
            if (fromKey.isNotBlank() &&
                fromKey == network.account?.username?.lowercase()
            ) {
                continue
            }
            if (network.dm.isOpen &&
                network.dm.peerUsername.equals(item.fromUsername, ignoreCase = true)
            ) {
                continue
            }
            val avatarUrl = XoraNetworkClient.avatarUrlFor(item.fromUsername)
            val sender = item.fromDisplayName.ifBlank { item.fromUsername }
            when {
                item.type.contains("netplay", ignoreCase = true) -> {
                    val live = network.netplayInvites.firstOrNull { invite ->
                        invite.fromUsername.equals(item.fromUsername, ignoreCase = true)
                    } ?: network.netplayInvites.maxByOrNull { it.createdAtMs }
                    val code = live?.code?.let { XoraNetplayProtocol.normalizeSessionCode(it) }
                        ?: XoraNetplayProtocol.extractSessionCode(item.body)
                        ?: ""
                    shellNotifications.emit(
                        ShellNotification.XoraNetplayInvite(
                            id = "xora-netplay:$key",
                            displayName = sender,
                            gameTitle = live?.gameTitle?.ifBlank { item.body } ?: item.body,
                            avatarUrl = avatarUrl,
                            sessionCode = code,
                            platformId = live?.platformId.orEmpty(),
                            coreName = live?.coreName.orEmpty(),
                            fromUsername = item.fromUsername.ifBlank { live?.fromUsername.orEmpty() },
                        ),
                    )
                }
                item.isFriendRequest -> {
                    if (fromKey !in announcedRequests) {
                        shellNotifications.emit(
                            ShellNotification.XoraFriendRequest(
                                id = "xora-request:$key",
                                displayName = sender,
                                avatarUrl = avatarUrl,
                            ),
                        )
                    }
                }
                item.isMessage -> shellNotifications.emit(
                    ShellNotification.XoraMessage(
                        id = "xora-message:$key",
                        sender = sender,
                        snippet = item.body,
                        avatarUrl = avatarUrl,
                    ),
                )
            }
        }
        // Keep the id set bounded to what the inbox still holds (it is capped server-side).
        knownXoraNotificationIds.retainAll(notificationIds)

        for (invite in network.netplayInvites) {
            val key = invite.dedupeKey()
            if (key in knownNetplayInviteKeys) continue
            knownNetplayInviteKeys.add(key)
            val sender = invite.fromDisplayName.ifBlank { invite.fromUsername }
            val banner = ShellNotification.XoraNetplayInvite(
                id = "xora-netplay:$key",
                displayName = sender,
                gameTitle = invite.gameTitle,
                avatarUrl = XoraNetworkClient.avatarUrlFor(invite.fromUsername),
                sessionCode = invite.code,
                platformId = invite.platformId,
                coreName = invite.coreName,
                fromUsername = invite.fromUsername,
            )
            if (shellNotifications.isSuppressed(banner)) continue
            rememberNetplayInvitePrompt(invite)
            shellNotifications.emit(banner)
        }
        knownNetplayInviteKeys.retainAll(netplayInviteKeys)
    }

    private fun rememberNetplayInvitePrompt(invite: XoraNetplayInviteRecord) {
        if (!XoraNetplayInvites.hasJoinableCode(invite)) return
        pendingNetplayInvite.value = NetplayInvitePrompt(
            hostName = invite.fromDisplayName.ifBlank { invite.fromUsername }.ifBlank { "a friend" },
            gameTitle = invite.gameTitle,
            sessionCode = invite.code.trim(),
            platformId = invite.platformId,
            coreName = invite.coreName,
            fromUsername = invite.fromUsername.ifBlank { invite.fromDisplayName },
        )
    }

    private suspend fun persistPendingNetplayJoin(invite: XoraNetplayInviteRecord) {
        if (!XoraNetplayInvites.hasJoinableCode(invite)) return
        preferences.setPendingNetplayJoin(
            PendingNetplayJoin(
                code = invite.code.trim(),
                platformId = invite.platformId,
                gameTitle = invite.gameTitle,
                fromUsername = invite.fromUsername,
                coreName = invite.coreName,
                createdAtMs = invite.createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ),
        )
    }

    fun activateShellNotification(notification: ShellNotification) {
        if (notification is ShellNotification.XoraNetplayInvite) {
            openNetplayInvitePrompt(notification)
        }
    }

    fun activateSelectedNotificationHistory() {
        val history = uiState.value.notificationHistory
        val selected = notificationHistorySelectedIndex.value
        if (history.isNotEmpty() && selected <= 0) {
            clearNotificationHistory()
            return
        }
        val item = history.getOrNull((selected - 1).coerceAtLeast(0)) ?: return
        activateShellNotification(item.notification)
    }

    fun dismissNotificationHistoryItem(id: String) {
        noteUserActivity()
        shellNotifications.removeFromHistory(id)
        val last = if (uiState.value.notificationHistory.isEmpty()) {
            0
        } else {
            uiState.value.notificationHistory.size
        }
        notificationHistorySelectedIndex.update { it.coerceIn(0, last) }
    }

    fun openNetplayInvitePrompt(notification: ShellNotification.XoraNetplayInvite) {
        noteUserActivity()
        pendingNetplayInvite.value = promptFromNotification(notification)
        netplayInvitePromptOpen.value = true
        closeNotificationHistory()
        shellNotifications.dismiss()
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
            ?: XoraNetplayProtocol.extractSessionCode(notification.sessionCode)
            ?: ""
        return NetplayInvitePrompt(
            hostName = notification.displayName.ifBlank { notification.fromUsername }
                .ifBlank { "a friend" },
            gameTitle = notification.gameTitle.ifBlank { match?.gameTitle.orEmpty() },
            sessionCode = code,
            platformId = notification.platformId.ifBlank { match?.platformId.orEmpty() },
            coreName = notification.coreName.ifBlank { match?.coreName.orEmpty() },
            fromUsername = notification.fromUsername.ifBlank { notification.displayName },
        )
    }

    fun confirmNetplayInvitePrompt() {
        val prompt = pendingNetplayInvite.value ?: return
        noteUserActivity()
        netplayInvitePromptOpen.value = false
        pendingNetplayInvite.value = null
        viewModelScope.launch {
            joinNetplayInvite(
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
        }
    }

    fun dismissNetplayInvitePrompt() {
        noteUserActivity()
        pendingNetplayInvite.value?.let { prompt ->
            val keys = buildList {
                netplaySessionDismissalKey(prompt.fromUsername.ifBlank { prompt.hostName }, prompt.sessionCode)
                    ?.let { add(it) }
            }
            if (keys.isNotEmpty()) shellNotifications.suppressKeys(keys)
        }
        netplayInvitePromptOpen.value = false
        pendingNetplayInvite.value = null
        viewModelScope.launch { preferences.clearPendingNetplayJoin() }
    }

    private suspend fun joinNetplayInvite(notification: ShellNotification.XoraNetplayInvite) {
        val hydrated = promptFromNotification(notification)
        val code = XoraNetplayProtocol.normalizeSessionCode(hydrated.sessionCode)
            ?: XoraNetplayProtocol.extractSessionCode(hydrated.sessionCode)
        if (code == null) {
            emit(HomeEvent.ShowMessage("That invite is missing a session code. Try again in a moment."))
            xoraNetwork.refreshNetplayInvites()
            return
        }
        persistPendingNetplayJoin(
            XoraNetplayInviteRecord(
                code = code,
                toUsername = xoraNetwork.state.value.account?.username.orEmpty(),
                gameTitle = hydrated.gameTitle,
                platformId = hydrated.platformId,
                coreName = hydrated.coreName,
                fromUsername = hydrated.fromUsername.ifBlank { hydrated.hostName },
                fromDisplayName = hydrated.hostName,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
        val game = findGameForNetplayInvite(hydrated.platformId, hydrated.gameTitle)
        if (game != null) {
            launchGame(game)
        } else {
            val from = hydrated.hostName.ifBlank { "your friend" }
            val title = hydrated.gameTitle.ifBlank { "that game" }
            emit(HomeEvent.ShowMessage("Launch $title to join ${from}'s session."))
        }
    }

    private suspend fun findGameForNetplayInvite(platformId: String, gameTitle: String): Game? {
        val games = libraryRepository.observeGames().first().filterNot { it.isAndroidApp }
        val title = gameTitle.trim()
        val onPlatform = if (platformId.isBlank()) {
            games
        } else {
            games.filter { it.platformId.equals(platformId, ignoreCase = true) }
        }
        fun matches(game: Game): Boolean {
            if (title.isBlank()) return false
            if (game.title.equals(title, ignoreCase = true)) return true
            if (game.fileName.equals(title, ignoreCase = true)) return true
            val a = game.title.lowercase().filter { it.isLetterOrDigit() }
            val b = title.lowercase().filter { it.isLetterOrDigit() }
            return a.isNotBlank() && a == b
        }
        return onPlatform.firstOrNull(::matches) ?: games.firstOrNull(::matches)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeGameInsights() {
        libraryUiState
            .map { it.selectedGame }
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .mapLatest { game ->
                if (game == null) {
                    insightUi.value = GameInsightUiState()
                    return@mapLatest
                }
                val cachedInsight = gameInsightRepository.cached(game.id)
                val cachedShots = gameScreenshotRepository.cached(game.id)
                if (cachedInsight != null && cachedShots != null) {
                    insightUi.value = cachedInsight.toUiState(
                        isLoading = false,
                        screenshotPaths = cachedShots,
                        screenshotsLoading = false,
                    )
                    return@mapLatest
                }
                insightUi.value = GameInsightUiState(
                    gameId = game.id,
                    isLoading = cachedInsight == null,
                    platformLabel = game.platform.displayName,
                    screenshotPaths = cachedShots.orEmpty().ifEmpty {
                        listOfNotNull(game.heroImagePath)
                    },
                    screenshotsLoading = cachedShots == null,
                    summary = cachedInsight?.summary,
                    summarySourceLabel = when (cachedInsight?.summarySource) {
                        InsightSource.Wikipedia -> "Wikipedia"
                        InsightSource.Igdb -> "IGDB"
                        InsightSource.Local -> "Library"
                        InsightSource.Speedrun -> "Speedrun.com"
                        null -> null
                    },
                    releaseYear = cachedInsight?.releaseYear,
                    developer = cachedInsight?.developer,
                    genre = cachedInsight?.genre,
                    speedrunBlurb = cachedInsight?.speedrunBlurb,
                    trivia = cachedInsight?.trivia.orEmpty(),
                )
                // Debounce while the user holds Left/Right through the strip.
                delay(INSIGHT_DEBOUNCE_MS)
                val credentials = preferences.credentials.first()
                val (insight, screenshots) = coroutineScope {
                    val insightDeferred = async {
                        cachedInsight ?: runCatching {
                            gameInsightRepository.insightFor(game, credentials)
                        }.getOrElse {
                            Log.w(TAG, "Insight lookup failed for ${game.title}", it)
                            GameInsight(
                                gameId = game.id,
                                title = game.title,
                                summary = "Part of your ${game.platform.displayName} library on XOrA.",
                                summarySource = InsightSource.Local,
                                platformLabel = game.platform.displayName,
                                trivia = listOf(
                                    "Part of your ${game.platform.displayName} library on XOrA.",
                                ),
                            )
                        }
                    }
                    val screenshotsDeferred = async {
                        cachedShots ?: runCatching {
                            gameScreenshotRepository.screenshotsFor(game, credentials)
                        }.getOrElse {
                            Log.w(TAG, "Screenshot lookup failed for ${game.title}", it)
                            listOfNotNull(game.heroImagePath)
                        }
                    }
                    insightDeferred.await() to screenshotsDeferred.await()
                }
                // Drop stale results if selection moved during the network wait.
                if (libraryUiState.value.selectedGame?.id != game.id) return@mapLatest
                insightUi.value = insight.toUiState(
                    isLoading = false,
                    screenshotPaths = screenshots,
                    screenshotsLoading = false,
                )
            }
            .launchIn(viewModelScope)
    }

    private fun GameInsight.toUiState(
        isLoading: Boolean,
        screenshotPaths: List<String> = emptyList(),
        screenshotsLoading: Boolean = false,
    ): GameInsightUiState = GameInsightUiState(
        gameId = gameId,
        isLoading = isLoading,
        summary = summary,
        summarySourceLabel = when (summarySource) {
            InsightSource.Wikipedia -> "Wikipedia"
            InsightSource.Igdb -> "IGDB"
            InsightSource.Local -> "Library"
            InsightSource.Speedrun -> "Speedrun.com"
            null -> null
        },
        releaseYear = releaseYear,
        developer = developer,
        genre = genre,
        platformLabel = platformLabel,
        speedrunBlurb = speedrunBlurb,
        trivia = trivia,
        screenshotPaths = screenshotPaths,
        screenshotsLoading = screenshotsLoading,
    )

    private fun buildState(
        allGames: List<Game>,
        summaries: List<PlatformSummary>,
        roots: List<LibraryRoot>,
        chrome: ChromeState,
        homePage: HomePage,
        rss: RssUiState,
        raLibrary: RaLibraryUiState,
        guideOpen: Boolean,
        guideSelectedIndex: Int,
        startSettingsOpen: Boolean,
        startSettingsCategory: StartSettingsCategory,
        startSettingsRowIndex: Int,
        isScraping: Boolean,
        raSettings: RetroAchievementsSettings,
        xoraEmulator: XoraEmulatorSettings,
        social: SocialChrome,
        theme: HomeThemeChrome,
        platformChrome: PlatformChrome = PlatformChrome(),
    ): HomeUiState {
        val hiddenIds = chrome.hiddenGameIds
        val showHidden = chrome.settings.showHiddenGames
        val libraryGames = if (showHidden || hiddenIds.isEmpty()) {
            allGames
        } else {
            allGames.filter { it.id !in hiddenIds }
        }
        val visibleSummaries = if (showHidden || hiddenIds.isEmpty()) {
            summaries
        } else {
            val counts = libraryGames
                .filter { !it.isAndroidApp }
                .groupingBy { it.platformId }
                .eachCount()
            summaries.map { summary ->
                summary.copy(gameCount = counts[summary.platform.id] ?: 0)
            }
        }
        val platformArtById = platformChrome.artByPlatformId
        val tabs = buildTabs(libraryGames, visibleSummaries)
        val tabIndex = chrome.selection.tabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val games = gamesForTab(libraryGames, tabs.getOrNull(tabIndex))
        val gameIndex = chrome.selection.gameIndex.coerceIn(0, (games.size - 1).coerceAtLeast(0))
        val rssIndex = rss.selectedIndex.coerceIn(0, (rss.items.size - 1).coerceAtLeast(0))
        val guideRows = buildGuideRows(
            allGames = libraryGames,
            raSignedIn = chrome.achievements.credentials.isConfigured,
            steam = social.steam,
        )
        val guideIndex = guideSelectedIndex.coerceIn(0, (guideRows.size - 1).coerceAtLeast(0))
        val startRows = buildStartSettingsRows(
            category = startSettingsCategory,
            settings = chrome.settings,
            isScraping = isScraping,
            isScanning = chrome.progress.isRunning,
            hasCustomBgm = !theme.customBgmPath.isNullOrBlank(),
            detectedResolutionLabel = detectedResolutionLabel(),
            raSettings = raSettings,
        )
        val startRowIndex = startSettingsRowIndex.coerceIn(0, (startRows.size - 1).coerceAtLeast(0))
        val quickLaunch = quickLaunchGames(libraryGames)
        val continueGame = libraryGames
            .filter { !it.isAndroidApp && it.lastPlayedAt != null }
            .maxByOrNull { it.lastPlayedAt ?: 0L }
        val favoriteGame = libraryGames
            .filter { !it.isAndroidApp && it.favorite }
            .maxByOrNull { it.lastPlayedAt ?: 0L }
            ?: libraryGames.firstOrNull { !it.isAndroidApp && it.favorite }
        val gamesSecondarySlot = when (chrome.settings.gamesSecondarySlot) {
            "Favorite" -> GamesSecondarySlot.Favorite
            else -> GamesSecondarySlot.Continue
        }
        val xmbTitleStyle = chrome.settings.xmbTitleStyle
        val xoraCategory = XoraXmbCategory.entries.getOrElse(theme.xora.categoryIndex) {
            XoraXmbCategory.Games
        }
        val xoraListDepth = when (val depth = theme.xora.depth) {
            XoraXmbDepth.RaLibrary -> xoraReturnStack.lastOrNull() ?: XoraXmbDepth.Category
            else -> depth
        }
        val xoraItems = overlayGameArtAlignment(
            items = overlayMusicCustomMedia(
            items = when (xoraListDepth) {
            XoraXmbDepth.Category -> buildXoraCategoryItems(
                category = xoraCategory,
                profileName = chrome.profile.displayName,
                profileAvatarPath = chrome.profileAvatarModel,
                gamesSecondarySlot = gamesSecondarySlot,
                continueGame = continueGame,
                favoriteGame = favoriteGame,
                nowPlayingLabel = platformChrome.music.nowPlaying.track?.let { track ->
                    "${track.title} — ${track.artist}"
                },
                nowPlayingArtPath = platformChrome.music.nowPlayingArtPath,
                homeFolderImagePath = chrome.settings.homeFolderImagePath,
                photoFolders = platformChrome.photoFolders,
                videoFolders = platformChrome.videoFolders,
                musicFolders = platformChrome.music.albums,
            )
            XoraXmbDepth.Systems -> buildXoraSystemItems(
                summaries = visibleSummaries,
                artByPlatformId = platformArtById,
                readyPlatformIds = platformChrome.readyPlatformIds,
            )
            XoraXmbDepth.Roms -> {
                val platformId = theme.xora.drilledPlatformId
                buildXoraRomItems(
                    games = libraryGames.filter { !it.isAndroidApp && it.platformId == platformId },
                    hiddenIds = if (showHidden) hiddenIds else emptySet(),
                )
            }
            XoraXmbDepth.Emulator -> buildXoraEmulatorItems(
                settings = xoraEmulator,
                raHardcore = raSettings.hardcore,
            )
            XoraXmbDepth.DspAccounts -> buildXoraDspItems(
                spotifyLinked = platformChrome.spotifyLinked,
            )
            XoraXmbDepth.MusicAlbums -> buildXoraMusicAlbumItems(platformChrome.music.albums)
            XoraXmbDepth.MusicTracks -> buildXoraMusicTrackItems(platformChrome.music.tracks)
            // The player page draws itself; the rung carries no list.
            XoraXmbDepth.NowPlaying -> emptyList()
            // The gallery pane draws itself; the rung carries no list.
            XoraXmbDepth.Photos -> emptyList()
            // The dashboard pane draws itself; the rung carries no list.
            XoraXmbDepth.Dashboard -> emptyList()
            // RA rides above a receding XMB; this rung carries no list.
            XoraXmbDepth.RaLibrary -> emptyList()
            },
            epoch = platformChrome.customMediaEpoch,
        ),
            alignments = chrome.artAlignments,
            continueGameId = continueGame?.id,
            favoriteGameId = favoriteGame?.id,
        )
        val xoraItemIndex = theme.xora.itemIndex.coerceIn(0, (xoraItems.size - 1).coerceAtLeast(0))
        val xoraSelected = xoraItems.getOrNull(xoraItemIndex)
        val xoraFocusGame = when (val action = xoraSelected?.action) {
            is XoraXmbAction.LaunchGame -> libraryGames.find { it.id == action.gameId }
                ?: allGames.find { it.id == action.gameId }
            XoraXmbAction.LaunchContinueOrFavorite -> when (gamesSecondarySlot) {
                GamesSecondarySlot.Continue -> continueGame
                GamesSecondarySlot.Favorite -> favoriteGame
            }
            else -> null
        }
        val socialMenu = SocialMenuUiState(
            tab = social.tab,
            steam = social.steam,
            discord = social.discord,
            conversations = social.conversations,
            reply = social.reply,
            discordDm = social.discordDm,
            circlePins = social.circlePins,
            managingCircle = social.managingCircle,
            notificationsOpen = social.notificationsOpen,
            recentNotifications = social.recentNotifications,
            friendSearchQuery = social.friendSearchQuery,
            xoraNetwork = social.xoraNetwork,
        )
        val accountRows = buildAccountPanelRows(
            tab = socialMenu.tab,
            steam = socialMenu.steam,
            discord = socialMenu.discord,
            conversations = socialMenu.conversations,
            reply = socialMenu.reply,
            discordDm = socialMenu.discordDm,
            circlePins = socialMenu.circlePins,
            managingCircle = socialMenu.managingCircle,
            friendSearchQuery = socialMenu.friendSearchQuery,
            notificationsOpen = socialMenu.notificationsOpen,
            xoraNetwork = socialMenu.xoraNetwork,
        )
        val accountIndex = social.accountPanelSelectedIndex.coerceIn(
            0,
            (accountRows.size - 1).coerceAtLeast(0),
        )
        val raVisible = raLibrary.visibleGames
        val raIndex = raLibrary.selectedIndex.coerceIn(0, (raVisible.size - 1).coerceAtLeast(0))
        val shortcutCount = theme.shortcuts.size + if (theme.nav.editMode) 1 else 0
        val shortcutIndex = theme.nav.shortcutIndex.coerceIn(0, (shortcutCount - 1).coerceAtLeast(0))

        return HomeUiState(
            isLoading = false,
            homePage = homePage,
            homeHub = HomeHubUiState(
                section = theme.nav.section,
                shard = theme.nav.shard,
                shortcutIndex = shortcutIndex,
                shortcutsEditMode = theme.nav.editMode,
                customizeChrome = theme.nav.customizeChrome,
                shortcutGridColumns = theme.nav.gridColumns,
                shortcutGridRows = theme.nav.gridRows,
                shortcuts = theme.shortcuts,
                vitaShortcutTrayOpen = theme.nav.vitaShortcutTrayOpen,
                vitaShortcutPinMode = theme.nav.vitaShortcutPinMode,
                vitaShortcutLaunch = theme.nav.vitaShortcutLaunch,
                vitaShortcutDepartingIndex = theme.nav.vitaShortcutDepartingIndex,
                wallpaperPath = theme.wallpaperPath,
                wallpaperAlignX = theme.wallpaperAlignX,
                wallpaperAlignY = theme.wallpaperAlignY,
                customBgmPath = theme.customBgmPath,
                continueGame = continueGame,
                themesOpen = theme.themesOpen,
                themesSheetTab = theme.themesSheetTab,
                addShortcutOpen = theme.addShortcutOpen,
                pendingShortcutKind = theme.pendingShortcutKind,
                pendingShortcutSpan = theme.pendingShortcutSpan,
                shortcutTargetPicker = theme.shortcutTargetPicker,
            ),
            xoraXmb = XoraXmbUiState(
                categoryIndex = theme.xora.categoryIndex.coerceIn(0, XoraXmbCategory.entries.lastIndex),
                itemIndex = xoraItemIndex,
                depth = theme.xora.depth,
                drilledPlatformId = theme.xora.drilledPlatformId,
                items = xoraItems,
                gamesSecondarySlot = gamesSecondarySlot,
                titleStyle = xmbTitleStyle,
                focusTitle = xoraSelected?.title ?: xoraCategory.label,
                focusSubtitle = xoraSelected?.subtitle ?: xoraCategory.label,
                focusGame = xoraFocusGame,
            ),
            xoraEmulator = xoraEmulator,
            tabs = tabs,
            selectedTabIndex = tabIndex,
            games = games,
            selectedGameIndex = gameIndex,
            hiddenGameIds = hiddenIds,
            gameArtAlignments = chrome.artAlignments,
            displayMode = chrome.settings.displayMode,
            gridColumns = chrome.settings.gridColumns.coerceIn(2, 6),
            scanProgress = chrome.progress,
            hasStorageAccess = storageAccess.hasAllFilesAccess,
            configuredRootCount = roots.size,
            platformSummaries = visibleSummaries,
            resolvedPlayerName = chrome.resolvedPlayerName,
            isLaunching = chrome.isLaunching,
            profile = chrome.profile,
            profileAvatarModel = chrome.profileAvatarModel,
            accountPanelExpanded = chrome.accountPanelExpanded,
            systemPanelExpanded = chrome.systemPanelExpanded,
            achievementsPanelExpanded = chrome.achievementsPanelExpanded,
            achievements = chrome.achievements,
            music = platformChrome.music,
            rss = rss.copy(selectedIndex = rssIndex),
            guide = GuideUiState(
                open = guideOpen,
                selectedIndex = guideIndex,
                rows = guideRows,
            ),
            startSettings = StartSettingsUiState(
                open = startSettingsOpen,
                category = startSettingsCategory,
                selectedRowIndex = startRowIndex,
                rows = startRows,
                settings = chrome.settings,
                isScraping = isScraping,
                isScanning = chrome.progress.isRunning,
            ),
            raLibrary = raLibrary.copy(selectedIndex = raIndex),
            quickLaunchGames = quickLaunch,
            socialMenu = socialMenu,
            accountPanelRows = accountRows,
            accountPanelSelectedIndex = accountIndex,
            profileEditRequest = social.profileEditRequest,
        )
    }

    private fun quickLaunchGames(allGames: List<Game>): List<Game> {
        val recent = allGames
            .filter { !it.isAndroidApp && it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(GUIDE_QUICK_LAUNCH_RECENT)
        val recentIds = recent.mapTo(mutableSetOf()) { it.id }
        val favorites = allGames
            .filter { !it.isAndroidApp && it.favorite && it.id !in recentIds }
            .take(GUIDE_QUICK_LAUNCH_FAVORITES)
        return recent + favorites
    }

    private fun buildAccountPanelRows(
        tab: SocialMenuTab,
        steam: SteamFriendsUiState,
        discord: DiscordSocialUiState,
        conversations: ConversationsUiState,
        reply: ConversationReplyUiState,
        circlePins: List<CirclePin>,
        managingCircle: Boolean,
        friendSearchQuery: String,
        discordDm: DiscordDmThreadUiState = DiscordDmThreadUiState(),
        notificationsOpen: Boolean = false,
        xoraNetwork: com.arcadia.shell.xoranetwork.XoraNetworkState =
            com.arcadia.shell.xoranetwork.XoraNetworkState(),
    ): List<AccountPanelRow> {
        val circleKeys = circlePins.mapTo(mutableSetOf()) { it.key }
        val q = friendSearchQuery.trim()

        return buildList {
            // Header chrome: Notifications + Circle slots are always focusable.
            // Pin edit is Back/Select (no Manage pill).
            add(AccountPanelRow.OpenNotifications)
            repeat(CIRCLE_FRIEND_LIMIT) { slot ->
                val pin = circlePins.getOrNull(slot)
                if (pin != null) {
                    add(AccountPanelRow.CircleMember(pin))
                } else {
                    add(AccountPanelRow.CircleEmptySlot(slot))
                }
            }

            if (notificationsOpen) {
                // Notification center shows conversations regardless of the active tab.
                if (reply.conversationKey != null) {
                    add(AccountPanelRow.ConversationReplySend(reply.conversationKey))
                }
                conversations.conversations.forEach { add(AccountPanelRow.Conversation(it.key)) }
                return@buildList
            }

            when (tab) {
                SocialMenuTab.Discord -> {
                    if (discordDm.peerUserId != null) {
                        add(AccountPanelRow.DiscordDmClose)
                        add(AccountPanelRow.DiscordDmSend)
                        return@buildList
                    }
                    val needsLink = discord.presence.capability != DiscordPresenceCapability.Connected
                    if (needsLink) {
                        add(AccountPanelRow.DiscordConnect)
                    }
                    val friends = discord.friends.filter {
                        q.isEmpty() || it.displayName.contains(q, ignoreCase = true)
                    }
                    if (managingCircle) {
                        friends.forEach { friend ->
                            val pin = CirclePin(CirclePinSource.Discord, friend.userId)
                            if (pin.key in circleKeys) {
                                add(AccountPanelRow.RemoveFromCircle(pin))
                            } else {
                                add(AccountPanelRow.AddToCircle(pin))
                            }
                        }
                    } else {
                        friends
                            .filter { CirclePin(CirclePinSource.Discord, it.userId).key !in circleKeys }
                            .forEach { add(AccountPanelRow.DiscordFriend(it.userId)) }
                    }
                }
                SocialMenuTab.Steam -> {
                    if (!steam.isConfigured) {
                        add(AccountPanelRow.SteamConfigure)
                    } else {
                        val candidates = steam.friends.filter {
                            q.isEmpty() ||
                                it.displayName.contains(q, ignoreCase = true) ||
                                (it.currentGame?.contains(q, ignoreCase = true) == true)
                        }
                        if (managingCircle) {
                            candidates.forEach { friend ->
                                val pin = CirclePin(CirclePinSource.Steam, friend.steamId)
                                if (pin.key in circleKeys) {
                                    add(AccountPanelRow.RemoveFromCircle(pin))
                                } else {
                                    add(AccountPanelRow.AddToCircle(pin))
                                }
                            }
                        } else {
                            candidates
                                .filter {
                                    CirclePin(CirclePinSource.Steam, it.steamId).key !in circleKeys
                                }
                                .forEach { add(AccountPanelRow.SteamFriend(it.steamId)) }
                        }
                    }
                }
                SocialMenuTab.XoraNetwork -> {
                    // The DM itself lives in the dedicated conversation window, not panel rows.
                    if (!xoraNetwork.signedIn) {
                        add(AccountPanelRow.XoraNetworkSignIn)
                    } else {
                        val friends = xoraNetwork.acceptedFriends.filter {
                            q.isEmpty() ||
                                it.displayName.contains(q, ignoreCase = true) ||
                                it.username.contains(q, ignoreCase = true) ||
                                it.status.contains(q, ignoreCase = true)
                        }
                        if (managingCircle) {
                            friends.forEach { friend ->
                                val pin = CirclePin(CirclePinSource.XoraNetwork, friend.username)
                                if (pin.key in circleKeys) {
                                    add(AccountPanelRow.RemoveFromCircle(pin))
                                } else {
                                    add(AccountPanelRow.AddToCircle(pin))
                                }
                            }
                        } else {
                            friends
                                .filter {
                                    CirclePin(CirclePinSource.XoraNetwork, it.username).key !in circleKeys
                                }
                                .forEach { add(AccountPanelRow.XoraFriend(it.username)) }
                        }
                    }
                }
            }
        }
    }

    private fun buildGuideRows(
        allGames: List<Game>,
        raSignedIn: Boolean,
        steam: SteamFriendsUiState,
    ): List<GuideRow> = buildList {
        add(GuideRow.Profile)

        val recent = allGames
            .filter { !it.isAndroidApp && it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(GUIDE_QUICK_LAUNCH_RECENT)
        val recentIds = recent.mapTo(mutableSetOf()) { it.id }
        val favorites = allGames
            .filter { !it.isAndroidApp && it.favorite && it.id !in recentIds }
            .take(GUIDE_QUICK_LAUNCH_FAVORITES)
        (recent + favorites).forEach { add(GuideRow.QuickLaunch(it)) }

        steam.friends.forEach { friend ->
            add(
                GuideRow.Friend(
                    id = friend.steamId,
                    displayName = friend.displayName,
                    online = friend.presence != SocialPresence.Offline,
                    avatarUrl = friend.avatarUrl,
                    profileUrl = friend.profileUrl,
                    currentGame = friend.currentGame,
                ),
            )
        }

        add(GuideRow.Settings)
        add(GuideRow.Achievements)
        add(GuideRow.SwapScreens)
        if (!raSignedIn) add(GuideRow.SignInRa)
    }

    private fun buildTabs(
        allGames: List<Game>,
        summaries: List<PlatformSummary>,
    ): List<LibraryTab> = buildList {
        val roms = allGames.filterNot { it.isAndroidApp }
        val apps = allGames.filter { it.isAndroidApp }

        add(LibraryTab("all", "All games", TabKind.All, gameCount = roms.size))

        val favorites = allGames.count { it.favorite }
        if (favorites > 0) {
            add(LibraryTab("favorites", "Favourites", TabKind.Favorites, gameCount = favorites))
        }

        val played = allGames.count { it.lastPlayedAt != null }
        if (played > 0) {
            add(LibraryTab("recent", "Recent", TabKind.Recent, gameCount = played))
        }

        if (apps.isNotEmpty()) {
            add(
                LibraryTab(
                    id = "apps",
                    label = "Apps",
                    kind = TabKind.Apps,
                    platformId = GamePlatform.Android.id,
                    gameCount = apps.size,
                ),
            )
        }

        // Summaries already exclude android (it is not in PlatformCatalog.platforms).
        summaries.forEach { summary ->
            add(
                LibraryTab(
                    id = "platform:${summary.platform.id}",
                    label = summary.platform.displayName,
                    kind = TabKind.Platform,
                    platformId = summary.platform.id,
                    gameCount = summary.gameCount,
                ),
            )
        }
    }

    private fun gamesForTab(allGames: List<Game>, tab: LibraryTab?): List<Game> = when (tab?.kind) {
        null, TabKind.All -> allGames.filterNot { it.isAndroidApp }
        TabKind.Favorites -> allGames.filter { it.favorite }
        TabKind.Recent -> allGames
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
        TabKind.Apps -> allGames.filter { it.isAndroidApp }
        TabKind.Platform -> allGames.filter { it.platformId == tab.platformId }
    }

    /**
     * Called by the shell when Settings, the options dialog, or another overlay should suppress
     * idle trailers. Returning to Home re-enables the idle timer.
     */
    fun setTrailerGateAllowed(allowed: Boolean) {
        trailerGateAllowed.value = allowed
        if (!allowed) stopTrailer()
    }

    /** Touch / focus changes that are not NavActions still count as activity. */
    fun noteUserActivity() {
        lastInputAt.value = SystemClock.elapsedRealtime()
        stopTrailer()
    }

    private fun stopTrailer() {
        if (trailerPlayback.value.active || trailerPlayback.value.trailerUrl != null) {
            trailerPlayback.value = HeroTrailerState(
                displayMode = trailerPlayback.value.displayMode,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeIdleTrailer() {
        data class IdleWatch(
            val gameId: String?,
            val enabled: Boolean,
            val mode: TrailerDisplayMode,
            val idleSeconds: Int,
            val iconIdleMedia: GameIconIdleMedia,
            val gateAllowed: Boolean,
            val launching: Boolean,
            val panelsOpen: Boolean,
            val libraryEmpty: Boolean,
            val onGameSelector: Boolean,
            val inputAt: Long,
        )

        data class IdleTrailerPrefs(
            val enabled: Boolean,
            val mode: TrailerDisplayMode,
            val idleSeconds: Int,
            val iconIdleMedia: GameIconIdleMedia,
        )

        combine(
            libraryUiState.map { state ->
                // Prefer the XMB-focused game (ROM list / Continue / Favorite).
                val xmbBrowsingGame = state.homePage == HomePage.Home &&
                    (
                        state.xoraXmb.depth == XoraXmbDepth.Roms ||
                            state.xoraXmb.selectedItem?.action is
                                XoraXmbAction.LaunchContinueOrFavorite ||
                            state.xoraXmb.selectedItem?.action is XoraXmbAction.LaunchGame
                        )
                val xmbGame = state.xoraXmb.focusGame?.takeIf { xmbBrowsingGame }
                val game = xmbGame ?: state.selectedGame
                IdleGameSnapshot(
                    gameId = game?.id,
                    isAndroidApp = game?.isAndroidApp == true,
                    launching = state.isLaunching,
                    panelsOpen = state.anyHeroPanelExpanded ||
                        state.guideOpen ||
                        state.startSettingsOpen ||
                        state.welcomeBackOpen,
                    libraryEmpty = state.needsSetup || game == null,
                    onGameSelector = state.homePage == HomePage.GameSelector ||
                        (xmbBrowsingGame && game != null),
                )
            }.distinctUntilChanged(),
            preferences.settings.map { settings ->
                IdleTrailerPrefs(
                    enabled = settings.trailerEnabled,
                    mode = settings.trailerDisplayMode,
                    idleSeconds = settings.trailerIdleSeconds,
                    iconIdleMedia = settings.gameIconIdleMedia,
                )
            }.distinctUntilChanged(),
            trailerGateAllowed,
            lastInputAt,
        ) { snap, trailerPrefs, gate, inputAt ->
            IdleWatch(
                gameId = snap.gameId,
                enabled = trailerPrefs.enabled,
                mode = trailerPrefs.mode,
                idleSeconds = trailerPrefs.idleSeconds,
                iconIdleMedia = trailerPrefs.iconIdleMedia,
                gateAllowed = gate,
                launching = snap.launching,
                panelsOpen = snap.panelsOpen,
                libraryEmpty = snap.libraryEmpty || snap.isAndroidApp,
                onGameSelector = snap.onGameSelector,
                inputAt = inputAt,
            )
        }
            .flatMapLatest { watch ->
                flow {
                    emit(HeroTrailerState(displayMode = watch.mode))
                    val gameId = watch.gameId ?: return@flow
                    val canIdle = watch.enabled &&
                        watch.gateAllowed &&
                        watch.onGameSelector &&
                        !watch.launching &&
                        !watch.panelsOpen &&
                        !watch.libraryEmpty
                    if (!canIdle) return@flow
                    // Screenshots replace in-icon trailers immediately; full-bleed / PIP still idle.
                    val skipInIconTrailer = watch.iconIdleMedia == GameIconIdleMedia.Screenshot &&
                        watch.mode == TrailerDisplayMode.InIcon
                    if (skipInIconTrailer) return@flow

                    delay(watch.idleSeconds.coerceIn(5, 60) * 1_000L)
                    Log.i(TAG, "Idle ${watch.idleSeconds}s elapsed; resolving trailer for $gameId")

                    val trailerUrl = ensureTrailerUrl(gameId)
                    if (trailerUrl == null) {
                        Log.i(TAG, "No trailer URL for $gameId after idle")
                        return@flow
                    }
                    // Bail if the user moved or the gate closed while we were resolving.
                    if (lastInputAt.value != watch.inputAt) return@flow
                    if (!trailerGateAllowed.value) return@flow
                    val stillFocused =
                        libraryUiState.value.xoraXmb.focusGame?.id == gameId ||
                            libraryUiState.value.selectedGame?.id == gameId
                    if (!stillFocused) return@flow
                    if (libraryUiState.value.isLaunching) return@flow
                    if (libraryUiState.value.anyHeroPanelExpanded) return@flow

                    Log.i(TAG, "Starting trailer playback for $gameId mode=${watch.mode}")
                    emit(
                        HeroTrailerState(
                            active = true,
                            trailerUrl = trailerUrl,
                            displayMode = watch.mode,
                        ),
                    )
                }
            }
            .onEach { trailerPlayback.value = it }
            .launchIn(viewModelScope)
    }

    private data class IdleGameSnapshot(
        val gameId: String?,
        val isAndroidApp: Boolean,
        val launching: Boolean,
        val panelsOpen: Boolean,
        val libraryEmpty: Boolean,
        val onGameSelector: Boolean,
    )

    private fun migrateTrailerPipeline() {
        viewModelScope.launch {
            if (!preferences.consumeTrailerPipelineMigration()) return@launch
            val cleared = libraryRepository.clearNullTrailerResolutions()
            Log.i(TAG, "Trailer pipeline v2: reopened $cleared null trailer lookups")
        }
    }

    private suspend fun ensureTrailerUrl(gameId: String): String? {
        gameCustomMediaStore.findIdleVideo(gameId)?.let { custom ->
            Log.i(TAG, "Using custom idle video for $gameId")
            return custom
        }
        val game = libraryRepository.findById(gameId) ?: return null
        if (!game.trailerUrl.isNullOrBlank()) {
            Log.i(TAG, "Using stored trailer for ${game.fileName}: ${game.trailerUrl}")
            return game.trailerUrl
        }
        val settings = preferences.settings.first()
        if (!settings.trailerScrapeEnabled) {
            Log.i(TAG, "Trailer scrape disabled; no URL for ${game.fileName}")
            return null
        }
        if (game.trailerResolved) {
            Log.i(TAG, "Trailer already resolved empty for ${game.fileName}")
            return null
        }
        val credentials = preferences.credentials.first()
        val resolved = runCatching {
            trailerResolver.resolve(
                game = game,
                credentials = credentials,
                scrapeEnabled = true,
                source = settings.trailerSourcePreference,
            )
        }
            .onFailure { Log.e(TAG, "Trailer resolve failed for ${game.fileName}", it) }
            .getOrNull()
        libraryRepository.setTrailer(gameId, resolved)
        Log.i(TAG, "Trailer resolve result for ${game.fileName}: $resolved")
        return resolved
    }

    /** Touch flicks land here so swipe and the pad share one routing table. */
    fun onTouchNav(action: NavAction) {
        noteUserActivity()
        onNavAction(action)
    }

    private fun onNavAction(action: NavAction) {
        val state = uiState.value

        // The cinematic launch plate holds the screen for [ArcadiaMotion.LaunchHold]. The chrome
        // has already faded off, so anything accepted here would move the shell invisibly and the
        // player would come back from the game to a menu that had shifted under them.
        if (state.isLaunching) return

        // Welcome-back wake screen / boot clip: B / Cancel / A skips; other nav is swallowed.
        if (state.bootIntroOpen) {
            if (action == NavAction.Cancel || action == NavAction.Confirm) {
                skipBootIntro()
            }
            return
        }
        if (state.welcomeBackOpen) {
            if (action == NavAction.Cancel || action == NavAction.Confirm) {
                dismissWelcomeBack()
            }
            return
        }

        if (state.netplayInvitePromptOpen) {
            noteUserActivity()
            when (action) {
                NavAction.Confirm -> confirmNetplayInvitePrompt()
                NavAction.Cancel -> dismissNetplayInvitePrompt()
                else -> Unit
            }
            return
        }

        if (action == NavAction.Cancel &&
            state.pendingNetplayInvite != null &&
            !state.notificationHistoryOpen &&
            !state.startSettingsOpen &&
            !state.guideOpen &&
            !state.accountPanelExpanded &&
            !state.systemPanelExpanded &&
            !state.achievementsPanelExpanded &&
            !bottomSheetNavOpen.value &&
            discordRichPresence.dmThread.value.peerUserId == null &&
            !xoraNetwork.state.value.dm.isOpen
        ) {
            noteUserActivity()
            netplayInvitePromptOpen.value = true
            return
        }

        // An open bottom sheet takes every action: it scrolls / moves itself, and the library
        // underneath must not move behind it. Overlays are not allowed to stack on top either.
        if (bottomSheetNavOpen.value) {
            noteUserActivity()
            sheetNavActions.tryEmit(action)
            return
        }

        if (action == NavAction.ToggleGuide) {
            if (startSettingsOpen.value) closeStartSettings()
            toggleGuide()
            return
        }

        // Start on XMB home focuses Settings; elsewhere toggles the quick-settings popup.
        if (action == NavAction.Menu) {
            if (state.guideOpen) closeGuide()
            if (state.homePage == HomePage.Home && !state.startSettingsOpen) {
                selectXoraCategory(XoraXmbCategory.Settings.ordinal)
                return
            }
            toggleStartSettings()
            return
        }

        // Start settings captures nav while open so the library underneath does not move.
        if (state.startSettingsOpen) {
            onStartSettingsNavAction(action)
            return
        }

        // Guide captures all nav while open so the library underneath does not move.
        if (state.guideOpen) {
            onGuideNavAction(action)
            return
        }

        // Discord conversation window owns A/B while a DM thread is open.
        if (discordRichPresence.dmThread.value.peerUserId != null) {
            onDiscordConversationNavAction(action)
            return
        }

        // XOrA conversation window owns A/B while a thread is open — A always sends. The old
        // in-panel chat put Close on row 0, so A right after opening closed the conversation
        // instead of sending ("messages don't go through").
        if (xoraNetwork.state.value.dm.isOpen) {
            onXoraConversationNavAction(action)
            return
        }

        // RT notification history overlay.
        if (state.notificationHistoryOpen) {
            onNotificationHistoryNavAction(action)
            return
        }

        // Expanded account panel captures U/D/A; B and LT both close it.
        if (state.accountPanelExpanded) {
            onAccountPanelNavAction(action)
            return
        }

        // Expanded system panel captures U/D/A/B (RT still toggles via ToggleSystemPanel).
        if (state.systemPanelExpanded) {
            onSystemPanelNavAction(action)
            return
        }

        // Add-shortcut overlay (type chooser or game/app target list) captures nav.
        if (state.homeHub.addShortcutOpen) {
            onAddShortcutNavAction(action, state)
            return
        }

        // Themes overlay: B dismisses; other hub nav stays blocked while open.
        if (state.homeHub.themesOpen) {
            if (action == NavAction.Cancel) dismissThemesSheet()
            return
        }

        // Vita shortcut tray over Home XMB captures pad while open (Y still closes).
        if (state.homePage == HomePage.Home && state.homeHub.vitaShortcutTrayOpen) {
            onVitaShortcutTrayNavAction(action, state)
            return
        }

        // Media → Photos pane captures the pad while open, PSP-style: A view, X options,
        // Y slideshow, B back, LB/RB page. LT/RT still toggle their panels.
        if (state.homePage == HomePage.Home && state.xoraXmb.depth == XoraXmbDepth.Photos) {
            when (action) {
                NavAction.ToggleAccountPanel -> toggleAccountPanel()
                NavAction.ToggleSystemPanel -> toggleSystemPanel()
                else -> onPhotosNavAction(action)
            }
            return
        }

        // XOrA Network Dashboard captures the pad the same way Photos does; LT/RT still work.
        if (state.homePage == HomePage.Home && state.xoraXmb.depth == XoraXmbDepth.Dashboard) {
            when (action) {
                NavAction.ToggleAccountPanel -> toggleAccountPanel()
                NavAction.ToggleSystemPanel -> toggleSystemPanel()
                else -> onDashboardNavAction(action)
            }
            return
        }

        // Retro Achievements overlay captures the pad while the XMB is receded; LT/RT still work.
        if (state.homePage == HomePage.Home && state.xoraXmb.depth == XoraXmbDepth.RaLibrary) {
            when (action) {
                NavAction.ToggleAccountPanel -> toggleAccountPanel()
                NavAction.ToggleSystemPanel -> toggleSystemPanel()
                NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
                else -> onRaLibraryNavAction(action)
            }
            return
        }

        // On XOrA XMB home, LB/RB cycle categories. Elsewhere they retain page jumps.
        when (action) {
            NavAction.PreviousPlatform -> {
                if (state.homePage == HomePage.Home) {
                    cycleXoraCategory(-1)
                } else {
                    setHomePage(HomePage.Home)
                }
                return
            }
            NavAction.NextPlatform -> {
                if (state.homePage == HomePage.Home) {
                    cycleXoraCategory(1)
                } else {
                    setHomePage(HomePage.Home)
                }
                return
            }
            else -> Unit
        }

        when (state.homePage) {
            HomePage.Home -> onXoraXmbNavAction(action, state)
            HomePage.GameSelector -> onGameSelectorNavAction(action, state)
            HomePage.RssFeed -> onRssNavAction(action, state)
            HomePage.RaLibrary -> onRaLibraryNavAction(action)
        }
    }

    private fun onXoraXmbNavAction(action: NavAction, state: HomeUiState) {
        val xmb = state.xoraXmb
        when (action) {
            NavAction.Left -> when (xmb.depth) {
                XoraXmbDepth.NowPlaying -> skipPreviousTrack()
                XoraXmbDepth.Category -> cycleXoraCategory(-1)
                else -> Unit
            }
            NavAction.Right -> when (xmb.depth) {
                XoraXmbDepth.NowPlaying -> skipNextTrack()
                XoraXmbDepth.Category -> cycleXoraCategory(1)
                else -> Unit
            }
            NavAction.Up -> if (xmb.depth == XoraXmbDepth.NowPlaying) {
                toggleShuffle()
            } else {
                moveXoraItem(-1)
            }
            NavAction.Down -> if (xmb.depth == XoraXmbDepth.NowPlaying) {
                toggleRepeat()
            } else {
                moveXoraItem(1)
            }
            // The player page has no list, so Confirm is the transport key there.
            NavAction.Confirm -> if (xmb.depth == XoraXmbDepth.NowPlaying) {
                toggleNowPlaying()
            } else {
                activateXoraSelection()
            }
            NavAction.Cancel -> drillOutXora()
            NavAction.Options -> {
                if (openMusicCustomizeIfFocused(xmb)) return
                if (xmb.depth == XoraXmbDepth.Roms) {
                    xmb.focusGame?.let { emit(HomeEvent.OpenGameOptions(it.id)) }
                        ?: state.selectedGame?.let { emit(HomeEvent.OpenGameOptions(it.id)) }
                }
            }
            NavAction.ScrapeMenu -> {
                if (openMusicCustomizeIfFocused(xmb)) return
                if (xmb.depth == XoraXmbDepth.Systems) {
                    (xmb.selectedItem?.action as? XoraXmbAction.DrillSystem)?.let {
                        requestPlatformBanner(it.platformId)
                    }
                    return
                }
                val game = when {
                    xmb.depth == XoraXmbDepth.Roms -> xmb.focusGame ?: state.selectedGame
                    xmb.focusGame != null &&
                        (xmb.selectedItem?.action is XoraXmbAction.LaunchGame ||
                            xmb.selectedItem?.action is XoraXmbAction.LaunchContinueOrFavorite) ->
                        xmb.focusGame
                    else -> null
                }
                game?.let {
                    romSaveRefresh.update { tick -> tick + 1 }
                    emit(HomeEvent.OpenScrapeMenu(it.id))
                }
            }
            NavAction.ToggleFavorite -> {
                if (xmb.depth == XoraXmbDepth.Roms && xmb.focusGame != null) {
                    focusGameInLibrary(xmb.focusGame)
                    toggleFavorite()
                }
            }
            NavAction.SwapScreens -> toggleVitaShortcutTray()
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
            else -> Unit
        }
    }

    /** Y on Home: slide the Vita shortcut tray down over the XMB (or back up to reveal it). */
    fun toggleVitaShortcutTray() {
        noteUserActivity()
        if (vitaShortcutTrayOpen.value) {
            closeVitaShortcutTray()
        } else {
            openVitaShortcutTray()
        }
    }

    fun openVitaShortcutTray(edit: Boolean = false) {
        noteUserActivity()
        collapseHeroPanels()
        homePage.value = HomePage.Home
        vitaShortcutTrayOpen.value = true
        vitaShortcutPinMode.value = true
        homeShortcutsEditMode.value = edit
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
        val count = homeShortcuts.value.size + if (edit || homeShortcuts.value.isEmpty()) 1 else 0
        homeShortcutIndex.value = homeShortcutIndex.value.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun closeVitaShortcutTray() {
        noteUserActivity()
        vitaShortcutLaunch.value = null
        vitaShortcutDepartingIndex.value = null
        vitaShortcutTrayOpen.value = false
        vitaShortcutPinMode.value = false
        homeShortcutsEditMode.value = false
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
        if (addShortcutOpen.value) dismissAddShortcutChooser()
    }

    fun prepareVitaShortcutLaunch(index: Int? = null) {
        noteUserActivity()
        val hub = uiState.value.homeHub
        if (index != null) selectHomeShortcut(index)
        val shortcut = hub.shortcuts.getOrNull(homeShortcutIndex.value) ?: return
        viewModelScope.launch {
            vitaShortcutLaunch.value = resolveVitaShortcutLaunch(shortcut)
            vitaShortcutDepartingIndex.value = null
        }
    }

    private fun beginVitaShortcutDepart(index: Int, shortcut: HomeShortcut) {
        playUiOneShot(UiOneShot.BubbleLaunch)
        vitaShortcutDepartingIndex.value = index
        viewModelScope.launch {
            val resolveJob = launch {
                vitaShortcutLaunch.value = resolveVitaShortcutLaunch(shortcut)
            }
            delay(VitaBubbleDepartMs.toLong())
            resolveJob.join()
            if (vitaShortcutDepartingIndex.value != index) return@launch
            vitaShortcutDepartingIndex.value = null
        }
    }

    fun confirmVitaShortcutLaunch() {
        val preview = vitaShortcutLaunch.value ?: return
        vitaShortcutLaunch.value = null
        openHomeShortcut(preview.shortcut)
    }

    fun cancelVitaShortcutLaunch() {
        noteUserActivity()
        vitaShortcutLaunch.value = null
        vitaShortcutDepartingIndex.value = null
    }

    private suspend fun resolveVitaShortcutLaunch(shortcut: HomeShortcut): VitaShortcutLaunchUi {
        val game = when (shortcut.kind) {
            HomeShortcutKind.Game -> libraryRepository.observeGames().first()
                .firstOrNull { it.id == shortcut.target }
            HomeShortcutKind.AndroidApp -> libraryRepository.observeGames().first()
                .firstOrNull { it.isAndroidApp && it.fileName == shortcut.target }
                ?: libraryRepository.observeGames().first()
                    .firstOrNull { it.isAndroidApp && it.id.contains(shortcut.target) }
            else -> null
        }
        val wallpaper = game?.heroImagePath
            ?: game?.boxArtPath
            ?: shortcut.artPath
            ?: shortcut.target.takeIf {
                shortcut.kind == HomeShortcutKind.Picture || shortcut.kind == HomeShortcutKind.Gif
            }
        val icon = shortcut.artPath
            ?: game?.boxArtPath
            ?: game?.heroImagePath
            ?: shortcut.target.takeIf { shortcut.kind == HomeShortcutKind.AndroidApp }
                ?.let { "${InstalledAppSync.ICON_SCHEME}$it" }
        val alignment = game?.id?.let { id ->
            preferences.gameArtAlignments.first()[id]
        } ?: GameArtAlignment()
        return VitaShortcutLaunchUi(
            shortcut = shortcut,
            wallpaperPath = wallpaper,
            iconPath = icon,
            game = game,
            artAlignX = alignment.x,
            artAlignY = alignment.y,
        )
    }

    private fun onVitaShortcutTrayNavAction(action: NavAction, state: HomeUiState) {
        val hub = state.homeHub
        if (hub.vitaShortcutLaunch != null) {
            when (action) {
                NavAction.Confirm -> confirmVitaShortcutLaunch()
                NavAction.Cancel -> cancelVitaShortcutLaunch()
                NavAction.ToggleAccountPanel -> toggleAccountPanel()
                NavAction.ToggleSystemPanel -> toggleSystemPanel()
                NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
                else -> Unit
            }
            return
        }
        when (action) {
            NavAction.Left -> moveVitaShortcutFocusHorizontal(-1, hub)
            NavAction.Right -> moveVitaShortcutFocusHorizontal(1, hub)
            NavAction.Up -> moveVitaShortcutFocusVertical(-1, hub)
            NavAction.Down -> moveVitaShortcutFocusVertical(1, hub)
            NavAction.Confirm -> activateHomeShortcut()
            NavAction.Cancel -> {
                if (hub.shortcutsEditMode) {
                    closeHomeShortcutsCustomize()
                } else {
                    closeVitaShortcutTray()
                }
            }
            NavAction.ScrapeMenu, NavAction.Options -> {
                if (hub.shortcutsEditMode) {
                    closeHomeShortcutsCustomize()
                } else {
                    openVitaShortcutEditMode()
                }
            }
            NavAction.SwapScreens -> closeVitaShortcutTray()
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
            else -> Unit
        }
    }

    private fun openVitaShortcutEditMode() {
        noteUserActivity()
        homeShortcutsEditMode.value = true
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
        vitaShortcutPinMode.value = true
        val count = homeShortcuts.value.size + 1
        homeShortcutIndex.value = homeShortcutIndex.value.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    /** Left/right stays on the focused row. Pages are reached with up/down. */
    private fun moveVitaShortcutFocusHorizontal(delta: Int, hub: HomeHubUiState) {
        noteUserActivity()
        val slotCount = vitaTraySlotCount(hub)
        val focus = hub.shortcutIndex.coerceIn(0, slotCount - 1)
        val page = focus / VITA_TRAY_PAGE_SIZE
        val rows = vitaTrayPageRows(slotCount, page)
        val row = rows.firstOrNull { focus in it } ?: return
        val nextCol = row.indexOf(focus) + delta
        if (nextCol in row.indices) {
            homeShortcutIndex.value = row[nextCol]
        }
    }

    private fun moveVitaShortcutFocusVertical(delta: Int, hub: HomeHubUiState) {
        noteUserActivity()
        val slotCount = vitaTraySlotCount(hub)
        val focus = hub.shortcutIndex.coerceIn(0, slotCount - 1)
        val page = focus / VITA_TRAY_PAGE_SIZE
        val rows = vitaTrayPageRows(slotCount, page)
        val rowIndex = rows.indexOfFirst { focus in it }
        if (rowIndex < 0) return
        val sourceRow = rows[rowIndex]
        val sourceCol = sourceRow.indexOf(focus).coerceAtLeast(0)

        fun landOn(targetRows: List<List<Int>>, targetRowIndex: Int) {
            val targetRow = targetRows.getOrNull(targetRowIndex) ?: return
            // Rows are staggered and unequal, so hold the horizontal position proportionally.
            val mappedCol = if (sourceRow.size <= 1 || targetRow.size <= 1) {
                0
            } else {
                ((sourceCol.toFloat() / (sourceRow.size - 1)) * (targetRow.size - 1)).roundToInt()
            }.coerceIn(0, targetRow.lastIndex)
            homeShortcutIndex.value = targetRow[mappedCol]
        }

        val targetRowIndex = rowIndex + delta
        if (targetRowIndex in rows.indices) {
            landOn(rows, targetRowIndex)
            return
        }
        val nextPage = page + delta
        if (nextPage !in 0 until vitaTrayPageCount(slotCount)) return
        val nextRows = vitaTrayPageRows(slotCount, nextPage)
        if (nextRows.isEmpty()) return
        landOn(nextRows, if (delta > 0) 0 else nextRows.lastIndex)
    }

    private fun vitaTraySlotCount(hub: HomeHubUiState): Int {
        val includeAdd = hub.shortcutsEditMode || hub.shortcuts.isEmpty()
        return (hub.shortcuts.size + if (includeAdd) 1 else 0).coerceAtLeast(1)
    }

    fun selectXoraCategory(index: Int) {
        noteUserActivity()
        val coerced = index.coerceIn(0, XoraXmbCategory.entries.lastIndex)
        if (xoraCategoryIndex.value == coerced &&
            xoraDepth.value == XoraXmbDepth.Category
        ) {
            return
        }
        xoraCategoryIndex.value = coerced
        xoraItemIndex.value = defaultXoraCategoryItemIndex(
            XoraXmbCategory.entries[coerced],
        )
        xoraDepth.value = XoraXmbDepth.Category
        xoraDrilledPlatformId.value = null
        xoraReturnStack.clear()
        onXoraCategoryLanded(XoraXmbCategory.entries[coerced])
    }

    fun selectXoraItem(index: Int) {
        noteUserActivity()
        val items = uiState.value.xoraXmb.items
        val last = (items.size - 1).coerceAtLeast(0)
        val coerced = index.coerceIn(0, last)
        xoraItemIndex.value = coerced
        syncLibraryFromXoraItem(items.getOrNull(coerced))
    }

    fun activateXoraSelection() {
        noteUserActivity()
        val item = uiState.value.xoraXmb.selectedItem ?: return
        when (val action = item.action) {
            XoraXmbAction.OpenProfile -> {
                if (!systemPanelExpanded.value) toggleSystemPanel()
                profileEditRequest.update { it + 1 }
            }
            XoraXmbAction.GuestModeStub ->
                emit(HomeEvent.ShowMessage("Guest Mode — coming soon."))
            is XoraXmbAction.OpenSettingsCategory -> openStartSettings(action.category)
            XoraXmbAction.InstallLatestUpdate -> installLatestGithubBuild()
            XoraXmbAction.OpenRaLibrary -> openRaLibrary()
            XoraXmbAction.LaunchContinueOrFavorite -> launchContinueOrFavorite()
            XoraXmbAction.DrillAllGames -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                xoraDepth.value = XoraXmbDepth.Systems
                xoraItemIndex.value = restoreXoraItem(XoraXmbDepth.Systems)
                xoraDrilledPlatformId.value = null
            }
            XoraXmbAction.PickHomeFolderImage -> requestHomeFolderImage()
            XoraXmbAction.DrillXoraEmulator -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                xoraDepth.value = XoraXmbDepth.Emulator
                xoraItemIndex.value = restoreXoraItem(XoraXmbDepth.Emulator)
                xoraDrilledPlatformId.value = null
            }
            is XoraXmbAction.ToggleXoraEmulatorSetting ->
                toggleXoraEmulatorSetting(action.setting)
            XoraXmbAction.OpenFullXoraEmulatorSetup ->
                emit(HomeEvent.OpenSettings)
            // In-emulator XMB actions — only handled inside XoraLibretroActivity.
            XoraXmbAction.ResumeGame,
            XoraXmbAction.QuitGame,
            XoraXmbAction.SaveGameState,
            XoraXmbAction.LoadGameState,
            XoraXmbAction.ResetGame,
            -> Unit
            XoraXmbAction.OpenPhotos -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                openPhotosRung()
            }
            is XoraXmbAction.OpenPhotoFolder -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                openPhotosRung(folderId = action.folderId, folderTitle = item.title)
            }
            XoraXmbAction.VideosStub -> activateVideos()
            is XoraXmbAction.OpenVideoFolder -> activateVideos(folderTitle = item.title)
            XoraXmbAction.OpenNowPlaying -> {
                if (!nowPlayingController.state.value.hasTrack) {
                    emit(HomeEvent.ShowMessage("Nothing playing yet — pick a song from Music."))
                    return
                }
                rememberXoraFolder(XoraXmbDepth.Category)
                xoraDepth.value = XoraXmbDepth.NowPlaying
                xoraItemIndex.value = 0
            }
            XoraXmbAction.DrillMusicAlbums -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                openMusicRung(
                    XoraXmbDepth.MusicAlbums,
                    itemIndex = restoreXoraItem(XoraXmbDepth.MusicAlbums),
                )
            }
            XoraXmbAction.DrillAllSongs -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                openMusicRung(
                    XoraXmbDepth.MusicTracks,
                    itemIndex = restoreXoraItem(XoraXmbDepth.MusicTracks),
                )
            }
            is XoraXmbAction.DrillMusicAlbum -> {
                rememberXoraFolder()
                openMusicRung(
                    depth = XoraXmbDepth.MusicTracks,
                    albumId = action.albumId,
                )
            }
            is XoraXmbAction.PlayMusicTrack -> {
                val tracks = musicUi.value.tracks
                val track = tracks.firstOrNull { it.id == action.trackId } ?: return
                // Queue the visible rung so previous / next / auto-advance have somewhere to go.
                nowPlayingController.play(track, tracks)
                if (track.source == MusicSource.Spotify) {
                    playSpotifyTrack(track, alreadyQueued = true)
                }
                rememberXoraFolder()
                xoraDepth.value = XoraXmbDepth.NowPlaying
                xoraItemIndex.value = 0
            }
            XoraXmbAction.DrillDspAccounts -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                xoraDepth.value = XoraXmbDepth.DspAccounts
                xoraItemIndex.value = restoreXoraItem(XoraXmbDepth.DspAccounts)
                xoraDrilledPlatformId.value = null
            }
            is XoraXmbAction.LinkDspAccount -> linkDspAccount(action.provider)
            XoraXmbAction.OpenDashboard -> {
                rememberXoraFolder(XoraXmbDepth.Category)
                openDashboardRung()
            }
            XoraXmbAction.StoreStub ->
                emit(HomeEvent.ShowMessage("XOrA Store — coming soon."))
            XoraXmbAction.OpenNews -> setHomePage(HomePage.RssFeed)
            is XoraXmbAction.DrillSystem -> {
                rememberXoraFolder(XoraXmbDepth.Systems)
                xoraDrilledPlatformId.value = action.platformId
                xoraDepth.value = XoraXmbDepth.Roms
                val restored = xoraReturnRomIndex[action.platformId] ?: 0
                xoraItemIndex.value = restored
                viewModelScope.launch {
                    val games = libraryRepository.observeGames().first()
                        .filter { !it.isAndroidApp && it.platformId == action.platformId }
                    games.getOrNull(restored)?.let { focusGameInLibrary(it) }
                        ?: games.firstOrNull()?.let { focusGameInLibrary(it) }
                }
            }
            is XoraXmbAction.LaunchGame -> {
                val game = uiState.value.xoraXmb.focusGame ?: return
                focusGameInLibrary(game)
                launchGame(game)
            }
        }
    }

    private fun launchContinueOrFavorite() {
        val state = uiState.value
        val target = state.xoraXmb.focusGame
        if (target == null) {
            emit(
                HomeEvent.ShowMessage(
                    when (state.xoraXmb.gamesSecondarySlot) {
                        GamesSecondarySlot.Continue ->
                            "Browse your library to start playing."
                        GamesSecondarySlot.Favorite ->
                            "Pin a favourite game first."
                    },
                ),
            )
            return
        }
        focusGameInLibrary(target)
        launchGame(target)
    }

    private fun toggleXoraEmulatorSetting(setting: XoraEmulatorXmbSetting) {
        viewModelScope.launch {
            val current = preferences.xoraEmulatorSettings.first()
            when (setting) {
                XoraEmulatorXmbSetting.Aspect -> {
                    preferences.setXoraAspectMode(current.aspectMode.next())
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
                    val names = listOf("") +
                        com.arcadia.shell.libretro.LibretroPad.connectedControllerNames()
                    val idx = names.indexOf(current.preferredControllerName).let {
                        if (it >= 0) it else 0
                    }
                    preferences.setXoraPreferredControllerName(names[(idx + 1) % names.size])
                }
                XoraEmulatorXmbSetting.ClearButtonMappings -> {
                    preferences.clearXoraButtonMappings()
                    emit(HomeEvent.ShowMessage("Custom button mappings cleared."))
                }
                XoraEmulatorXmbSetting.Netplay ->
                    preferences.setXoraNetplayEnabled(!current.netplayEnabled)
                XoraEmulatorXmbSetting.RaHardcore -> {
                    val next = !preferences.retroAchievementsSettings.first().hardcore
                    preferences.setRaHardcore(next)
                    emit(
                        HomeEvent.ShowMessage(
                            if (next) {
                                "Hardcore RetroAchievements on"
                            } else {
                                "Hardcore RetroAchievements off"
                            },
                        ),
                    )
                }
            }
        }
    }

    /** Snapshot the hovered row before drilling into a folder so Cancel can land back on it. */
    private fun rememberXoraFolder(depth: XoraXmbDepth = xoraDepth.value) {
        xoraReturnItemIndex[depth] = xoraItemIndex.value
        if (xoraReturnStack.lastOrNull() != depth) {
            xoraReturnStack.addLast(depth)
        }
    }

    private fun restoreXoraItem(depth: XoraXmbDepth, fallback: Int = 0): Int =
        xoraReturnItemIndex[depth] ?: fallback

    private fun xoraParentDepth(current: XoraXmbDepth): XoraXmbDepth = when (current) {
        XoraXmbDepth.Roms -> XoraXmbDepth.Systems
        XoraXmbDepth.MusicTracks ->
            if (musicUi.value.drilledAlbumId != null) XoraXmbDepth.MusicAlbums
            else XoraXmbDepth.Category
        XoraXmbDepth.Category -> XoraXmbDepth.Category
        else -> XoraXmbDepth.Category
    }

    private fun drillOutXora() {
        noteUserActivity()
        val current = xoraDepth.value
        if (current == XoraXmbDepth.Category) {
            if (uiState.value.anyHeroPanelExpanded) collapseHeroPanels()
            return
        }
        when (current) {
            XoraXmbDepth.Photos -> {
                stopPhotoSlideshow()
                photoControlsHideJob?.cancel()
                photosUi.update {
                    it.copy(
                        optionsOpen = false,
                        fullscreenOpen = false,
                        fullscreenControlsVisible = true,
                        deleteConfirmOpen = false,
                        edit = null,
                    )
                }
            }
            XoraXmbDepth.Dashboard -> {
                dashboardUi.update {
                    it.copy(view = DashboardView.Tiles, busy = false, error = null, notice = null)
                }
            }
            XoraXmbDepth.RaLibrary -> {
                raGameDetailJob?.cancel()
                raLibraryUi.update {
                    it.copy(
                        gameDetail = null,
                        gameDetailLoading = false,
                        gameDetailError = null,
                        cheevoIndex = 0,
                    )
                }
            }
            else -> Unit
        }
        xoraReturnItemIndex[current] = xoraItemIndex.value
        if (current == XoraXmbDepth.Roms) {
            xoraDrilledPlatformId.value?.let { platformId ->
                xoraReturnRomIndex[platformId] = xoraItemIndex.value
            }
        }
        val previous = xoraReturnStack.removeLastOrNull() ?: xoraParentDepth(current)
        val restored = restoreXoraItem(
            previous,
            fallback = if (previous == XoraXmbDepth.Category) {
                defaultXoraCategoryItemIndex(
                    XoraXmbCategory.entries.getOrElse(xoraCategoryIndex.value) {
                        XoraXmbCategory.Games
                    },
                )
            } else {
                0
            },
        )
        when (previous) {
            XoraXmbDepth.MusicAlbums ->
                openMusicRung(XoraXmbDepth.MusicAlbums, itemIndex = restored)
            XoraXmbDepth.MusicTracks ->
                openMusicRung(
                    depth = XoraXmbDepth.MusicTracks,
                    albumId = musicUi.value.drilledAlbumId,
                    itemIndex = restored,
                )
            else -> {
                xoraDepth.value = previous
                xoraItemIndex.value = restored
                if (previous != XoraXmbDepth.Roms) {
                    xoraDrilledPlatformId.value = null
                }
            }
        }
    }

    /**
     * Slides into a music rung and loads it.
     *
     * MediaStore is queried per drill rather than up front: the Music category is rarely the first
     * thing opened, and the shell should not pay for an audio scan it may never show.
     */
    private fun openMusicRung(
        depth: XoraXmbDepth,
        albumId: String? = null,
        itemIndex: Int = 0,
    ) {
        xoraDepth.value = depth
        xoraItemIndex.value = itemIndex
        val hasAccess = musicLibrary.hasAudioAccess()
        musicUi.update {
            it.copy(
                drilledAlbumId = albumId,
                isLoading = true,
                hasAudioAccess = hasAccess,
            )
        }
        viewModelScope.launch {
            val folderPath = preferences.settings.first().musicLibraryPath
            val hasFolder = !folderPath.isNullOrBlank()
            // Spotify, a Settings music folder, or MediaStore permission can each fill the rung.
            val spotifyOnly = spotifyTokenStore.isLinked() &&
                (albumId?.startsWith(SPOTIFY_ALBUM_PREFIX) == true)
            if (!hasAccess && !hasFolder && !spotifyOnly && !spotifyTokenStore.isLinked()) {
                musicUi.update { it.copy(isLoading = false) }
                emit(HomeEvent.RequestAudioAccess(musicLibrary.audioPermission()))
                return@launch
            }
            when (depth) {
                XoraXmbDepth.MusicAlbums -> {
                    // Linked Spotify playlists sit above on-device albums in the same rung.
                    val playlists = spotifyPlaylistAlbums()
                    val albums = musicLibrary.albums()
                    musicUi.update {
                        it.copy(albums = playlists + albums, isLoading = false)
                    }
                    fillMissingAlbumArt(albums)
                }
                else -> {
                    val tracks = when {
                        albumId == null -> musicLibrary.allTracks()
                        albumId.startsWith(SPOTIFY_ALBUM_PREFIX) ->
                            spotifyPlaylistTracks(albumId.removePrefix(SPOTIFY_ALBUM_PREFIX))
                        else -> musicLibrary.tracks(albumId)
                    }
                    musicUi.update { it.copy(tracks = tracks, isLoading = false) }
                    fillMissingTrackArt(tracks)
                }
            }
        }
    }

    /**
     * Fills album tiles that have no local cover (embedded tag, folder `cover.jpg`, or MediaStore).
     * iTunes / Deezer / Cover Art Archive need no extra keys; game-scraper credentials are a
     * fallback for OSTs named after the game.
     */
    private fun fillMissingAlbumArt(albums: List<MusicAlbum>) {
        val missing = albums.filter { album ->
            album.source != MusicSource.Spotify && album.artUri.isNullOrBlank()
        }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            for (album in missing) {
                val path = runCatching {
                    musicArtRepository.ensureArt(album.title, album.artist)
                }.getOrNull() ?: continue
                musicUi.update { ui ->
                    ui.copy(
                        albums = ui.albums.map { row ->
                            if (row.id == album.id) row.copy(artUri = path) else row
                        },
                    )
                }
            }
        }
    }

    private fun fillMissingTrackArt(tracks: List<MusicTrack>) {
        val missing = tracks.filter { track ->
            track.source != MusicSource.Spotify && track.albumArtUri.isNullOrBlank()
        }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val albums = missing
                .groupBy { "${it.albumTitle}\u0000${it.artist}" }
                .map { (_, songs) -> songs.first() }
            for (sample in albums) {
                val path = runCatching {
                    musicArtRepository.ensureArt(sample.albumTitle, sample.artist)
                }.getOrNull() ?: continue
                musicUi.update { ui ->
                    ui.copy(
                        tracks = ui.tracks.map { track ->
                            if (track.source != MusicSource.Spotify &&
                                track.albumArtUri.isNullOrBlank() &&
                                track.albumTitle.equals(sample.albumTitle, ignoreCase = true)
                            ) {
                                track.copy(albumArtUri = path)
                            } else {
                                track
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun spotifyPlaylistAlbums(): List<MusicAlbum> {
        if (!spotifyTokenStore.isLinked()) return emptyList()
        return spotifyWebApi.playlists().map { playlist ->
            MusicAlbum(
                id = "$SPOTIFY_ALBUM_PREFIX${playlist.id}",
                title = playlist.name,
                artist = playlist.ownerName,
                artUri = playlist.imageUrl,
                trackCount = playlist.trackCount,
                isPlaylist = true,
                source = MusicSource.Spotify,
                remoteUri = playlist.uri,
            )
        }
    }

    private suspend fun spotifyPlaylistTracks(playlistId: String): List<MusicTrack> {
        val contextUri = "spotify:playlist:$playlistId"
        return spotifyWebApi.playlistTracks(playlistId).map { track ->
            MusicTrack(
                id = "$SPOTIFY_ALBUM_PREFIX${track.id}",
                title = track.title,
                artist = track.artist,
                albumTitle = track.albumName,
                albumArtUri = track.albumArtUrl,
                durationMs = track.durationMs,
                contentUri = track.uri,
                source = MusicSource.Spotify,
                contextUri = contextUri,
            )
        }
    }

    /**
     * Spotify streams from its own player, so XOrA asks Spotify to start the track and mirrors
     * the metadata locally. A missing device or a free account is reported rather than swallowed.
     */
    private fun playSpotifyTrack(track: MusicTrack, alreadyQueued: Boolean = false) {
        if (!alreadyQueued) {
            nowPlayingController.setTrack(track, playing = true)
        } else {
            nowPlayingController.setRemotePlaying(true)
        }
        viewModelScope.launch {
            when (val result = spotifyWebApi.play(track.contentUri, track.contextUri)) {
                SpotifyPlaybackResult.Started -> nowPlayingController.setRemotePlaying(true)
                SpotifyPlaybackResult.NoActiveDevice -> {
                    nowPlayingController.setRemotePlaying(false)
                    emit(
                        HomeEvent.ShowMessage(
                            "Open the Spotify app on a phone, PC, or speaker first — " +
                                "XOrA starts playback there.",
                        ),
                    )
                }
                SpotifyPlaybackResult.NeedsPremium -> {
                    nowPlayingController.setRemotePlaying(false)
                    emit(HomeEvent.ShowMessage("Spotify Premium is required to start playback."))
                }
                is SpotifyPlaybackResult.Failed -> {
                    nowPlayingController.setRemotePlaying(false)
                    emit(HomeEvent.ShowMessage(result.message))
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Media → Photos
    // ------------------------------------------------------------------

    /**
     * Slides into the Photos gallery and loads the device library. MediaStore is queried per
     * open, like Music, so the shell never pays for an image scan it may not show.
     */
    private fun openPhotosRung(folderId: String? = null, folderTitle: String? = null) {
        xoraDepth.value = XoraXmbDepth.Photos
        xoraItemIndex.value = 0
        val access = photoLibrary.access()
        photosUi.update {
            it.copy(
                access = access,
                albumFilter = folderId,
                albumTitle = folderTitle,
                isLoading = access != PhotoAccess.Denied,
                loadError = null,
                optionsOpen = false,
                fullscreenOpen = false,
                deleteConfirmOpen = false,
                edit = null,
            )
        }
        if (access == PhotoAccess.Denied) {
            emit(HomeEvent.RequestImageAccess(photoLibrary.requiredPermissions()))
            return
        }
        loadPhotos()
    }

    private fun onXoraCategoryLanded(category: XoraXmbCategory) {
        when (category) {
            XoraXmbCategory.Media, XoraXmbCategory.Videos -> refreshMediaFolders()
            XoraXmbCategory.Music -> loadMusicAlbumsForColumn()
            else -> Unit
        }
    }

    private fun refreshMediaFolders() {
        viewModelScope.launch {
            val photos = runCatching { photoLibrary.folders() }.getOrDefault(emptyList())
            val videos = runCatching { videoLibrary.folders() }.getOrDefault(emptyList())
            mediaFolders.value = MediaFolders(photos = photos, videos = videos)
        }
    }

    private fun loadMusicAlbumsForColumn() {
        viewModelScope.launch {
            val hasAccess = musicLibrary.hasAudioAccess()
            val folderPath = preferences.settings.first().musicLibraryPath
            if (!hasAccess && folderPath.isNullOrBlank() && !spotifyTokenStore.isLinked()) {
                return@launch
            }
            val playlists = spotifyPlaylistAlbums()
            val albums = musicLibrary.albums()
            musicUi.update {
                it.copy(
                    albums = playlists + albums,
                    hasAudioAccess = hasAccess,
                )
            }
            fillMissingAlbumArt(albums)
        }
    }

    private fun activateVideos(folderTitle: String? = null) {
        val access = videoLibrary.access()
        if (access == PhotoAccess.Denied) {
            emit(HomeEvent.RequestImageAccess(videoLibrary.requiredPermissions()))
            return
        }
        refreshMediaFolders()
        val label = folderTitle?.takeIf { it.isNotBlank() } ?: "Videos"
        emit(HomeEvent.ShowMessage("$label — video player coming soon."))
    }

    private fun loadPhotos(keepFocusId: String? = null) {
        viewModelScope.launch {
            val folderId = photosUi.value.albumFilter
            val result = runCatching { photoLibrary.photos() }
            result.onSuccess { all ->
                val photos = if (folderId.isNullOrBlank()) {
                    all
                } else {
                    all.filter { photo ->
                        photo.bucketId == folderId ||
                            (photo.bucketId.isBlank() && photo.album == folderId)
                    }
                }
                photosUi.update { ui ->
                    val kept = keepFocusId?.let { id -> photos.indexOfFirst { it.id == id } }
                        ?.takeIf { it >= 0 }
                    val focus = (kept ?: ui.focusedIndex)
                        .coerceIn(0, (photos.size - 1).coerceAtLeast(0))
                    ui.copy(
                        photos = photos,
                        focusedIndex = focus,
                        isLoading = false,
                        loadError = null,
                    )
                }
            }.onFailure { error ->
                Log.w("HomeViewModel", "Photo library load failed", error)
                photosUi.update {
                    it.copy(isLoading = false, loadError = "Couldn't load your photo library.")
                }
            }
        }
    }

    /** Re-checks visual access once the permission dialog is answered, then loads. */
    fun onImageAccessResult() {
        refreshMediaFolders()
        val photoAccess = photoLibrary.access()
        photosUi.update { it.copy(access = photoAccess) }
        if (xoraDepth.value == XoraXmbDepth.Photos) {
            if (photoAccess == PhotoAccess.Denied) {
                emit(HomeEvent.ShowMessage("Photos needs access to your photo library."))
                return
            }
            photosUi.update { it.copy(isLoading = true) }
            loadPhotos()
            return
        }
        val category = XoraXmbCategory.entries.getOrElse(xoraCategoryIndex.value) {
            XoraXmbCategory.Games
        }
        if (category == XoraXmbCategory.Videos && videoLibrary.access() == PhotoAccess.Denied) {
            emit(HomeEvent.ShowMessage("Videos needs access to your video library."))
        }
    }

    /** One press must not activate two layers: gate layer-changing photo actions. */
    private fun photoLayerActionAllowed(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPhotoLayerActionMs < PHOTO_LAYER_DEBOUNCE_MS) return false
        lastPhotoLayerActionMs = now
        return true
    }

    /** Layer priority: edit → delete confirm → options popup → fullscreen viewer → gallery. */
    private fun onPhotosNavAction(action: NavAction) {
        val ui = photosUi.value

        val edit = ui.edit
        if (edit != null) {
            when (action) {
                NavAction.Left -> photoEditFocusTool(edit.toolIndex - 1)
                NavAction.Right -> photoEditFocusTool(edit.toolIndex + 1)
                NavAction.Confirm -> if (photoLayerActionAllowed()) {
                    activatePhotoEditTool(edit.toolIndex)
                }
                NavAction.Cancel -> if (photoLayerActionAllowed()) closePhotoEdit()
                else -> Unit
            }
            return
        }

        if (ui.deleteConfirmOpen) {
            when (action) {
                NavAction.Left, NavAction.Right, NavAction.Up, NavAction.Down ->
                    photosUi.update {
                        it.copy(deleteConfirmDeleteFocused = !it.deleteConfirmDeleteFocused)
                    }
                NavAction.Confirm -> if (photoLayerActionAllowed()) {
                    if (photosUi.value.deleteConfirmDeleteFocused) {
                        confirmPhotoDelete()
                    } else {
                        closePhotoDeleteConfirm()
                    }
                }
                NavAction.Cancel -> if (photoLayerActionAllowed()) closePhotoDeleteConfirm()
                else -> Unit
            }
            return
        }

        if (ui.optionsOpen) {
            when (action) {
                NavAction.Up -> movePhotoOption(-1)
                NavAction.Down -> movePhotoOption(1)
                NavAction.Confirm -> if (photoLayerActionAllowed()) {
                    activatePhotoOption(photosUi.value.optionIndex)
                }
                // Square closes its own popup, and B backs out — focus stays trapped inside.
                NavAction.Cancel, NavAction.ToggleAchievementsPanel ->
                    if (photoLayerActionAllowed()) closePhotoOptions()
                else -> Unit
            }
            return
        }

        if (ui.fullscreenOpen) {
            revealPhotoControls()
            when (action) {
                NavAction.Left -> {
                    if (ui.slideshowActive) stopPhotoSlideshow()
                    movePhotoFocus(-1)
                }
                NavAction.Right -> {
                    if (ui.slideshowActive) stopPhotoSlideshow()
                    movePhotoFocus(1)
                }
                NavAction.SwapScreens -> if (photoLayerActionAllowed()) {
                    if (ui.slideshowActive) stopPhotoSlideshow() else startPhotoSlideshow()
                }
                NavAction.Cancel -> if (photoLayerActionAllowed()) closePhotoViewer()
                else -> Unit
            }
            return
        }

        if (ui.photos.isEmpty()) {
            when (action) {
                NavAction.Confirm -> if (ui.access == PhotoAccess.Denied) {
                    emit(HomeEvent.RequestImageAccess(photoLibrary.requiredPermissions()))
                }
                NavAction.Cancel -> drillOutXora()
                else -> Unit
            }
            return
        }

        when (action) {
            NavAction.Left -> movePhotoFocus(-1)
            NavAction.Right -> movePhotoFocus(1)
            NavAction.Up -> movePhotoFocusRow(-1)
            NavAction.Down -> movePhotoFocusRow(1)
            NavAction.PreviousPlatform -> movePhotoPage(-1)
            NavAction.NextPlatform -> movePhotoPage(1)
            NavAction.Confirm -> if (photoLayerActionAllowed()) openPhotoViewer()
            NavAction.ToggleAchievementsPanel, NavAction.Options ->
                if (photoLayerActionAllowed()) openPhotoOptions()
            NavAction.SwapScreens -> if (photoLayerActionAllowed()) startPhotoSlideshow()
            NavAction.Cancel -> drillOutXora()
            else -> Unit
        }
    }

    /** Touch / click entry point — the pane funnels every tap through here. */
    fun onPhotoCommand(command: PhotoPaneCommand) {
        noteUserActivity()
        when (command) {
            is PhotoPaneCommand.Focus -> photosUi.update {
                it.copy(focusedIndex = command.index.coerceIn(0, (it.photos.size - 1).coerceAtLeast(0)))
            }
            is PhotoPaneCommand.Open -> {
                photosUi.update {
                    it.copy(focusedIndex = command.index.coerceIn(0, (it.photos.size - 1).coerceAtLeast(0)))
                }
                openPhotoViewer()
            }
            PhotoPaneCommand.OpenOptions -> openPhotoOptions()
            PhotoPaneCommand.CloseOptions -> closePhotoOptions()
            is PhotoPaneCommand.FocusOption -> photosUi.update {
                it.copy(optionIndex = command.index.coerceIn(0, PhotoOption.entries.lastIndex))
            }
            is PhotoPaneCommand.ActivateOption -> activatePhotoOption(command.index)
            PhotoPaneCommand.StartSlideshow -> startPhotoSlideshow()
            PhotoPaneCommand.CloseViewer -> closePhotoViewer()
            PhotoPaneCommand.NextPhoto -> {
                if (photosUi.value.slideshowActive) stopPhotoSlideshow()
                movePhotoFocus(1)
                revealPhotoControls()
            }
            PhotoPaneCommand.PreviousPhoto -> {
                if (photosUi.value.slideshowActive) stopPhotoSlideshow()
                movePhotoFocus(-1)
                revealPhotoControls()
            }
            PhotoPaneCommand.RevealControls -> revealPhotoControls()
            is PhotoPaneCommand.FocusDeleteChoice -> photosUi.update {
                it.copy(deleteConfirmDeleteFocused = command.delete)
            }
            PhotoPaneCommand.ConfirmDelete -> confirmPhotoDelete()
            PhotoPaneCommand.CancelDelete -> closePhotoDeleteConfirm()
            is PhotoPaneCommand.FocusEditTool -> photoEditFocusTool(command.index)
            is PhotoPaneCommand.ActivateEditTool -> activatePhotoEditTool(command.index)
            PhotoPaneCommand.RequestAccess ->
                emit(HomeEvent.RequestImageAccess(photoLibrary.requiredPermissions()))
            PhotoPaneCommand.Retry -> {
                photosUi.update { it.copy(isLoading = true, loadError = null) }
                loadPhotos()
            }
            PhotoPaneCommand.Back -> {
                val ui = photosUi.value
                when {
                    ui.edit != null -> closePhotoEdit()
                    ui.deleteConfirmOpen -> closePhotoDeleteConfirm()
                    ui.optionsOpen -> closePhotoOptions()
                    ui.fullscreenOpen -> closePhotoViewer()
                    else -> drillOutXora()
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // XOrA Network Dashboard (Network → Dashboard)
    // -------------------------------------------------------------------------------------------

    /** Slides into the Dashboard and refreshes account, friends, RA chrome, and play history. */
    private fun openDashboardRung() {
        xoraDepth.value = XoraXmbDepth.Dashboard
        xoraItemIndex.value = 0
        dashboardUi.update {
            it.copy(view = DashboardView.Tiles, tileIndex = 0, busy = false, error = null, notice = null)
        }
        viewModelScope.launch {
            val games = libraryRepository.observeGames().first()
            val played = games
                .filter { !it.isAndroidApp && it.lastPlayedAt != null }
                .sortedByDescending { it.lastPlayedAt }
            dashboardUi.update {
                it.copy(
                    recentGames = played.take(8),
                    gamesPlayedCount = played.size,
                    totalPlayTimeMs = played.sumOf { game -> game.playTimeMs },
                )
            }
        }
        viewModelScope.launch {
            if (xoraNetwork.state.value.signedIn) {
                xoraNetwork.refreshAccount()
                xoraNetwork.refreshFriends()
                xoraNetwork.refreshNotifications()
            }
        }
        // RA points + avatar for the RetroAchievements tile without opening the X pill.
        refreshSystemPanelRaChrome()
    }

    /** Layer priority: signed-out auth card → sub-views (Friends / Edit profile) → tile board. */
    private fun onDashboardNavAction(action: NavAction) {
        val ui = dashboardUi.value
        val network = xoraNetwork.state.value
        if (!network.configured || network.restoring) {
            if (action == NavAction.Cancel) drillOutXora()
            return
        }
        if (!network.signedIn) {
            when (action) {
                NavAction.Up -> focusAuthRow(ui.auth.focusIndex - 1)
                NavAction.Down -> focusAuthRow(ui.auth.focusIndex + 1)
                NavAction.Confirm -> activateAuthRow(ui.auth.focusIndex)
                NavAction.Cancel -> drillOutXora()
                else -> Unit
            }
            return
        }
        when (ui.view) {
            DashboardView.Friends -> when (action) {
                NavAction.Up -> focusFriendRow(ui.friendsIndex - 1)
                NavAction.Down -> focusFriendRow(ui.friendsIndex + 1)
                NavAction.Confirm -> activateFriendRow(ui.friendsIndex)
                NavAction.ToggleAchievementsPanel, NavAction.Options -> removeFriendRow(ui.friendsIndex)
                NavAction.Cancel -> closeDashboardSubView()
                else -> Unit
            }
            DashboardView.EditProfile -> when (action) {
                NavAction.Up -> focusEditRow(ui.edit.focusIndex - 1)
                NavAction.Down -> focusEditRow(ui.edit.focusIndex + 1)
                NavAction.Confirm -> activateEditRow(ui.edit.focusIndex)
                NavAction.Cancel -> closeDashboardSubView()
                else -> Unit
            }
            DashboardView.Tiles -> when (action) {
                NavAction.Left -> focusDashboardTile(ui.tileIndex - 1)
                NavAction.Right -> focusDashboardTile(ui.tileIndex + 1)
                NavAction.Up -> moveDashboardTileRow(-1)
                NavAction.Down -> moveDashboardTileRow(1)
                NavAction.Confirm -> activateDashboardTile(ui.tileIndex)
                NavAction.Cancel -> drillOutXora()
                else -> Unit
            }
        }
    }

    /** Touch / click entry point — the Dashboard pane funnels every tap through here. */
    fun onDashboardCommand(command: DashboardCommand) {
        noteUserActivity()
        when (command) {
            is DashboardCommand.FocusTile -> focusDashboardTile(command.index)
            is DashboardCommand.ActivateTile -> {
                focusDashboardTile(command.index)
                activateDashboardTile(command.index)
            }
            is DashboardCommand.FocusAuthRow -> focusAuthRow(command.index)
            is DashboardCommand.ActivateAuthRow -> {
                focusAuthRow(command.index)
                activateAuthRow(command.index)
            }
            is DashboardCommand.EditField -> editDashboardField(command.field, command.value)
            DashboardCommand.SubmitAuth -> submitDashboardAuth()
            DashboardCommand.SwitchAuthMode -> switchDashboardAuthMode()
            is DashboardCommand.FocusFriendRow -> focusFriendRow(command.index)
            is DashboardCommand.ActivateFriendRow -> {
                focusFriendRow(command.index)
                activateFriendRow(command.index)
            }
            is DashboardCommand.RemoveFriendRow -> removeFriendRow(command.index)
            DashboardCommand.SubmitAddFriend -> submitAddFriend()
            is DashboardCommand.FocusEditRow -> focusEditRow(command.index)
            is DashboardCommand.ActivateEditRow -> {
                focusEditRow(command.index)
                activateEditRow(command.index)
            }
            DashboardCommand.Refresh -> refreshDashboardNetwork()
            DashboardCommand.Back -> {
                val ui = dashboardUi.value
                if (ui.view != DashboardView.Tiles && xoraNetwork.state.value.signedIn) {
                    closeDashboardSubView()
                } else {
                    drillOutXora()
                }
            }
        }
    }

    private fun focusDashboardTile(index: Int) {
        dashboardUi.update { it.copy(tileIndex = index.coerceIn(0, DASHBOARD_TILES.lastIndex)) }
    }

    private fun moveDashboardTileRow(delta: Int) {
        val current = DASHBOARD_TILES.getOrNull(dashboardUi.value.tileIndex) ?: return
        val rowIndex = DASHBOARD_TILE_ROWS.indexOfFirst { current in it }
        if (rowIndex < 0) return
        val column = DASHBOARD_TILE_ROWS[rowIndex].indexOf(current)
        val targetRow = DASHBOARD_TILE_ROWS.getOrNull(rowIndex + delta) ?: return
        val target = targetRow[column.coerceIn(0, targetRow.lastIndex)]
        focusDashboardTile(DASHBOARD_TILES.indexOf(target))
    }

    private fun activateDashboardTile(index: Int) {
        val tile = DASHBOARD_TILES.getOrNull(index) ?: return
        when (tile) {
            DashboardTile.Profile -> {
                val account = xoraNetwork.state.value.account
                dashboardUi.update {
                    it.copy(
                        view = DashboardView.EditProfile,
                        edit = DashboardEditProfileState(
                            displayName = account?.displayName.orEmpty(),
                            username = account?.username.orEmpty(),
                            location = account?.location.orEmpty(),
                        ),
                        error = null,
                        notice = null,
                    )
                }
            }
            DashboardTile.Friends -> {
                dashboardUi.update {
                    it.copy(view = DashboardView.Friends, friendsIndex = 0, error = null, notice = null)
                }
                viewModelScope.launch {
                    xoraNetwork.refreshFriends().onFailure { error ->
                        dashboardUi.update { it.copy(error = error.message) }
                    }
                }
            }
            DashboardTile.Achievements -> toggleAchievementsPanel()
            DashboardTile.RecentGames ->
                emit(HomeEvent.ShowMessage("Launch games from the Games column."))
            DashboardTile.Notifications -> refreshDashboardNetwork()
            DashboardTile.CloudSaves ->
                emit(HomeEvent.ShowMessage("Cloud Saves aren't enabled yet."))
            DashboardTile.Netplay -> {
                if (!xoraNetwork.state.value.signedIn) {
                    emit(HomeEvent.ShowMessage(XoraNetplayInvites.LOGIN_REQUIRED))
                } else {
                    emit(
                        HomeEvent.ShowMessage(
                            "Invite a friend from the emulator's XOrA Network menu.",
                        ),
                    )
                }
            }
            DashboardTile.Sharing ->
                emit(HomeEvent.ShowMessage("XOrA Network sharing isn't enabled yet."))
            DashboardTile.DeviceLink ->
                emit(HomeEvent.ShowMessage("Device linking is coming soon."))
            DashboardTile.ManageAccount -> {
                if (!openExternalUrl(XoraNetworkClient.MANAGE_ACCOUNT_URL)) {
                    emit(HomeEvent.ShowError("No browser available for account.xoranetwork.com."))
                }
            }
            DashboardTile.SignOut -> viewModelScope.launch {
                dashboardUi.update { it.copy(busy = true, error = null, notice = null) }
                xoraNetwork.signOut()
                clearXoraNetworkIdentityIfNeeded()
                dashboardUi.update {
                    it.copy(
                        busy = false,
                        view = DashboardView.Tiles,
                        tileIndex = 0,
                        auth = DashboardAuthFormState(),
                        notice = "Signed out of XOrA Network.",
                    )
                }
            }
        }
    }

    private fun focusAuthRow(index: Int) {
        dashboardUi.update {
            it.copy(auth = it.auth.copy(focusIndex = index.coerceIn(0, it.auth.rows.lastIndex)))
        }
    }

    private fun activateAuthRow(index: Int) {
        when (dashboardUi.value.auth.rows.getOrNull(index)) {
            DashboardAuthRow.Email,
            DashboardAuthRow.Password,
            DashboardAuthRow.Username,
            DashboardAuthRow.DisplayName,
            -> dashboardUi.update {
                it.copy(auth = it.auth.copy(focusIndex = index, fieldFocusTick = it.auth.fieldFocusTick + 1))
            }
            DashboardAuthRow.Submit -> submitDashboardAuth()
            DashboardAuthRow.SwitchMode -> switchDashboardAuthMode()
            DashboardAuthRow.ForgotPassword -> {
                // Password reset is website-only; the app never calls recovery RPCs.
                openExternalUrl(XoraNetworkClient.FORGOT_PASSWORD_URL)
            }
            DashboardAuthRow.ManageAccount -> {
                openExternalUrl(XoraNetworkClient.MANAGE_ACCOUNT_URL)
            }
            null -> Unit
        }
    }

    private fun switchDashboardAuthMode() {
        dashboardUi.update {
            val next = if (it.auth.mode == DashboardAuthMode.SignIn) {
                DashboardAuthMode.Register
            } else {
                DashboardAuthMode.SignIn
            }
            it.copy(auth = it.auth.copy(mode = next, focusIndex = 0), error = null, notice = null)
        }
    }

    private fun submitDashboardAuth() {
        val ui = dashboardUi.value
        if (ui.busy) return
        dashboardUi.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            val auth = ui.auth
            val result = if (auth.mode == DashboardAuthMode.SignIn) {
                xoraNetwork.signIn(auth.email, auth.password)
            } else {
                xoraNetwork.register(auth.email, auth.password, auth.username, auth.displayName)
            }
            result.fold(
                onSuccess = {
                    applyXoraNetworkIdentity(forceAvatar = true)
                    val username = xoraNetwork.state.value.account?.username
                    dashboardUi.update {
                        it.copy(
                            busy = false,
                            auth = DashboardAuthFormState(),
                            view = DashboardView.Tiles,
                            tileIndex = 0,
                            notice = username?.let { name -> "Signed in as $name." },
                        )
                    }
                },
                onFailure = { error ->
                    dashboardUi.update { it.copy(busy = false, error = error.message) }
                },
            )
        }
    }

    private fun editDashboardField(field: DashboardField, value: String) {
        dashboardUi.update { ui ->
            when (field) {
                DashboardField.Email -> ui.copy(auth = ui.auth.copy(email = value))
                DashboardField.Password -> ui.copy(auth = ui.auth.copy(password = value))
                DashboardField.Username ->
                    if (ui.view == DashboardView.EditProfile) {
                        ui.copy(edit = ui.edit.copy(username = value))
                    } else {
                        ui.copy(auth = ui.auth.copy(username = value))
                    }
                DashboardField.DisplayName ->
                    if (ui.view == DashboardView.EditProfile) {
                        ui.copy(edit = ui.edit.copy(displayName = value))
                    } else {
                        ui.copy(auth = ui.auth.copy(displayName = value))
                    }
                DashboardField.Location -> ui.copy(edit = ui.edit.copy(location = value))
                DashboardField.FriendQuery -> ui.copy(addFriendQuery = value)
            }
        }
    }

    private fun focusFriendRow(index: Int) {
        dashboardUi.update { it.copy(friendsIndex = index.coerceIn(0, it.friendRows.size)) }
    }

    private fun activateFriendRow(index: Int) {
        val ui = dashboardUi.value
        if (index == 0) {
            if (ui.addFriendQuery.isBlank()) {
                dashboardUi.update { it.copy(friendFieldFocusTick = it.friendFieldFocusTick + 1) }
            } else {
                submitAddFriend()
            }
            return
        }
        val friend = ui.friendRows.getOrNull(index - 1) ?: return
        when (friend.state) {
            XoraFriendState.IncomingInvite -> viewModelScope.launch {
                dashboardUi.update { it.copy(busy = true, error = null, notice = null) }
                val result = xoraNetwork.addFriend(friend.username)
                dashboardUi.update {
                    it.copy(
                        busy = false,
                        notice = result.fold({ "You and ${friend.username} are now friends." }, { null }),
                        error = result.exceptionOrNull()?.message,
                    )
                }
            }
            XoraFriendState.OutgoingInvite ->
                dashboardUi.update { it.copy(notice = "Invite sent — press X to cancel it.") }
            else ->
                dashboardUi.update { it.copy(notice = "${friend.username} is already a friend — X removes.") }
        }
    }

    private fun removeFriendRow(index: Int) {
        val friend = dashboardUi.value.friendRows.getOrNull(index - 1) ?: return
        viewModelScope.launch {
            dashboardUi.update { it.copy(busy = true, error = null, notice = null) }
            val result = xoraNetwork.removeFriend(friend.username)
            dashboardUi.update {
                it.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.message,
                    notice = result.fold(
                        {
                            when (friend.state) {
                                XoraFriendState.IncomingInvite -> "Declined ${friend.username}'s invite."
                                XoraFriendState.OutgoingInvite -> "Cancelled the invite to ${friend.username}."
                                else -> "Removed ${friend.username}."
                            }
                        },
                        { null },
                    ),
                )
            }
        }
    }

    private fun submitAddFriend() {
        val query = dashboardUi.value.addFriendQuery.trim()
        viewModelScope.launch {
            dashboardUi.update { it.copy(busy = true, error = null, notice = null) }
            val result = xoraNetwork.addFriend(query)
            dashboardUi.update {
                it.copy(
                    busy = false,
                    addFriendQuery = if (result.isSuccess) "" else it.addFriendQuery,
                    notice = result.fold({ "Friend request sent to $query." }, { null }),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun focusEditRow(index: Int) {
        dashboardUi.update {
            it.copy(
                edit = it.edit.copy(
                    focusIndex = index.coerceIn(0, DashboardEditProfileState.ROW_COUNT - 1),
                ),
            )
        }
    }

    private fun activateEditRow(index: Int) {
        when (index) {
            0, 1, 2 -> dashboardUi.update {
                it.copy(edit = it.edit.copy(focusIndex = index, fieldFocusTick = it.edit.fieldFocusTick + 1))
            }
            3 -> submitDashboardProfile()
            4 -> closeDashboardSubView()
        }
    }

    private fun submitDashboardProfile() {
        val ui = dashboardUi.value
        if (ui.busy) return
        viewModelScope.launch {
            dashboardUi.update { it.copy(busy = true, error = null, notice = null) }
            val result = xoraNetwork.updateProfile(
                displayName = ui.edit.displayName,
                username = ui.edit.username,
                location = ui.edit.location,
            )
            dashboardUi.update {
                it.copy(
                    busy = false,
                    view = if (result.isSuccess) DashboardView.Tiles else it.view,
                    notice = result.fold({ "Profile updated." }, { null }),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun closeDashboardSubView() {
        dashboardUi.update { it.copy(view = DashboardView.Tiles, error = null, notice = null) }
    }

    private fun refreshDashboardNetwork() {
        viewModelScope.launch {
            if (!xoraNetwork.state.value.signedIn) return@launch
            dashboardUi.update { it.copy(busy = true) }
            xoraNetwork.refreshAccount()
            xoraNetwork.refreshFriends()
            xoraNetwork.refreshNotifications()
            dashboardUi.update { it.copy(busy = false, notice = "Refreshed.") }
        }
    }

    private fun movePhotoFocus(delta: Int) {
        photosUi.update { ui ->
            if (ui.photos.isEmpty()) return@update ui
            val next = if (ui.slideshowActive) {
                (ui.focusedIndex + delta + ui.photos.size) % ui.photos.size
            } else {
                (ui.focusedIndex + delta).coerceIn(0, ui.photos.lastIndex)
            }
            ui.copy(focusedIndex = next)
        }
    }

    private fun movePhotoFocusRow(delta: Int) {
        photosUi.update { ui ->
            if (ui.photos.isEmpty()) return@update ui
            val next = (ui.focusedIndex + delta * PHOTO_GRID_COLUMNS)
                .coerceIn(0, ui.photos.lastIndex)
            ui.copy(focusedIndex = next)
        }
    }

    private fun movePhotoPage(delta: Int) {
        photosUi.update { ui ->
            if (ui.photos.isEmpty()) return@update ui
            val nextPage = (ui.currentPage + delta).coerceIn(0, ui.pageCount - 1)
            val offsetInPage = ui.focusedIndex % PHOTO_PAGE_SIZE
            val next = (nextPage * PHOTO_PAGE_SIZE + offsetInPage)
                .coerceIn(0, ui.photos.lastIndex)
            ui.copy(focusedIndex = next)
        }
    }

    private fun openPhotoViewer() {
        if (photosUi.value.focusedPhoto == null) return
        photosUi.update {
            it.copy(fullscreenOpen = true, fullscreenControlsVisible = true, optionsOpen = false)
        }
        schedulePhotoControlsHide()
    }

    private fun closePhotoViewer() {
        stopPhotoSlideshow()
        photoControlsHideJob?.cancel()
        photosUi.update { it.copy(fullscreenOpen = false, fullscreenControlsVisible = true) }
    }

    private fun revealPhotoControls() {
        photosUi.update { it.copy(fullscreenControlsVisible = true) }
        schedulePhotoControlsHide()
    }

    private fun schedulePhotoControlsHide() {
        photoControlsHideJob?.cancel()
        photoControlsHideJob = viewModelScope.launch {
            delay(PHOTO_CONTROLS_HIDE_MS)
            photosUi.update { it.copy(fullscreenControlsVisible = false) }
        }
    }

    private fun startPhotoSlideshow() {
        if (photosUi.value.photos.isEmpty()) {
            emit(HomeEvent.ShowMessage("No photos to show yet."))
            return
        }
        photoSlideshowJob?.cancel()
        photosUi.update {
            it.copy(
                fullscreenOpen = true,
                slideshowActive = true,
                optionsOpen = false,
                fullscreenControlsVisible = false,
            )
        }
        photoSlideshowJob = viewModelScope.launch {
            while (isActive && photosUi.value.slideshowActive) {
                delay(PHOTO_SLIDESHOW_INTERVAL_MS)
                if (photosUi.value.slideshowActive) movePhotoFocus(1)
            }
        }
    }

    private fun stopPhotoSlideshow() {
        photoSlideshowJob?.cancel()
        photoSlideshowJob = null
        photosUi.update { it.copy(slideshowActive = false) }
    }

    private fun openPhotoOptions() {
        if (photosUi.value.focusedPhoto == null) return
        photosUi.update { it.copy(optionsOpen = true, optionIndex = 0) }
    }

    private fun closePhotoOptions() {
        photosUi.update { it.copy(optionsOpen = false) }
    }

    private fun movePhotoOption(delta: Int) {
        photosUi.update { ui ->
            ui.copy(optionIndex = (ui.optionIndex + delta).coerceIn(0, PhotoOption.entries.lastIndex))
        }
    }

    private fun activatePhotoOption(index: Int) {
        val option = PhotoOption.entries.getOrNull(index) ?: return
        val photo = photosUi.value.focusedPhoto ?: return
        when (option) {
            PhotoOption.View -> {
                closePhotoOptions()
                openPhotoViewer()
            }
            PhotoOption.Edit -> {
                closePhotoOptions()
                openPhotoEdit(photo)
            }
            PhotoOption.MarkFavorite -> togglePhotoFavorite(photo)
            PhotoOption.Delete -> {
                closePhotoOptions()
                photosUi.update {
                    it.copy(deleteConfirmOpen = true, deleteConfirmDeleteFocused = false)
                }
            }
            PhotoOption.ShareToNetwork ->
                emit(HomeEvent.ShowMessage("XOrA Network sharing is coming soon."))
        }
    }

    /** Favorite status lives in preferences, never in the image file. */
    fun togglePhotoFavorite(photo: DevicePhoto) {
        val favored = photo.id in photosUi.value.favoriteIds
        viewModelScope.launch { preferences.setPhotoFavorite(photo.id, !favored) }
    }

    private fun openPhotoEdit(photo: DevicePhoto) {
        stopPhotoSlideshow()
        photosUi.update {
            it.copy(edit = PhotoEditUiState(photo = photo), fullscreenOpen = false)
        }
    }

    private fun closePhotoEdit() {
        photosUi.update { it.copy(edit = null) }
    }

    private fun photoEditFocusTool(index: Int) {
        photosUi.update { ui ->
            val edit = ui.edit ?: return@update ui
            ui.copy(edit = edit.copy(toolIndex = index.coerceIn(0, PhotoEditTool.entries.lastIndex)))
        }
    }

    private fun activatePhotoEditTool(index: Int) {
        val edit = photosUi.value.edit ?: return
        if (edit.saving) return
        when (PhotoEditTool.entries.getOrNull(index) ?: return) {
            PhotoEditTool.RotateLeft -> photosUi.update {
                it.copy(edit = edit.copy(rotationDeg = (edit.rotationDeg + 270) % 360, toolIndex = index))
            }
            PhotoEditTool.RotateRight -> photosUi.update {
                it.copy(edit = edit.copy(rotationDeg = (edit.rotationDeg + 90) % 360, toolIndex = index))
            }
            PhotoEditTool.Crop -> photosUi.update {
                it.copy(
                    edit = edit.copy(
                        cropIndex = (edit.cropIndex + 1) % PHOTO_CROP_PRESETS.size,
                        toolIndex = index,
                    ),
                )
            }
            PhotoEditTool.Reset -> photosUi.update {
                it.copy(edit = edit.copy(rotationDeg = 0, cropIndex = 0, toolIndex = index))
            }
            PhotoEditTool.Save -> savePhotoEdit()
            PhotoEditTool.Cancel -> closePhotoEdit()
        }
    }

    /** Writes the rotated / cropped result as a NEW image; the original is never modified. */
    private fun savePhotoEdit() {
        val edit = photosUi.value.edit ?: return
        if (edit.rotationDeg == 0 && edit.cropAspect == null) {
            emit(HomeEvent.ShowMessage("No changes to save yet."))
            return
        }
        photosUi.update { it.copy(edit = edit.copy(saving = true)) }
        viewModelScope.launch {
            val saved = photoEditor.saveEditedCopy(
                sourceUri = Uri.parse(edit.photo.contentUri),
                rotationDeg = edit.rotationDeg,
                cropAspect = edit.cropAspect,
                baseName = edit.photo.displayName,
            )
            if (saved != null) {
                emit(HomeEvent.ShowMessage("Saved an edited copy to Pictures/XOrA."))
                photosUi.update { it.copy(edit = null) }
                loadPhotos(keepFocusId = photosUi.value.focusedPhoto?.id)
            } else {
                emit(HomeEvent.ShowError("Couldn't save the edited photo."))
                photosUi.update { ui ->
                    ui.copy(edit = ui.edit?.copy(saving = false))
                }
            }
        }
    }

    private fun closePhotoDeleteConfirm() {
        photosUi.update { it.copy(deleteConfirmOpen = false) }
    }

    /**
     * Deletion runs through MediaStore's consent flow: API 30+ uses createDeleteRequest, API 29
     * attempts a direct delete and falls back to the RecoverableSecurityException sender.
     */
    private fun confirmPhotoDelete() {
        val photo = photosUi.value.focusedPhoto ?: return
        closePhotoDeleteConfirm()
        pendingDeletePhotoId = photo.id
        val uri = Uri.parse(photo.contentUri)
        photoLibrary.deleteRequest(uri)?.let { sender ->
            emit(HomeEvent.RequestPhotoDelete(sender))
            return
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { photoLibrary.deleteDirect(uri) }
            val recovery = outcome.recoverySender
            when {
                outcome.deleted -> onPhotoDeleteResult(confirmed = true)
                recovery != null -> emit(HomeEvent.RequestPhotoDelete(recovery))
                else -> {
                    pendingDeletePhotoId = null
                    emit(HomeEvent.ShowError("XOrA wasn't allowed to delete that photo."))
                }
            }
        }
    }

    /** Called with the system consent result; selects the nearest remaining photo. */
    fun onPhotoDeleteResult(confirmed: Boolean) {
        val deletedId = pendingDeletePhotoId
        pendingDeletePhotoId = null
        if (!confirmed || deletedId == null) return
        photosUi.update { ui ->
            val index = ui.photos.indexOfFirst { it.id == deletedId }
            if (index < 0) return@update ui
            val remaining = ui.photos.filterNot { it.id == deletedId }
            ui.copy(
                photos = remaining,
                focusedIndex = index.coerceIn(0, (remaining.size - 1).coerceAtLeast(0)),
                fullscreenOpen = ui.fullscreenOpen && remaining.isNotEmpty(),
            )
        }
        emit(HomeEvent.ShowMessage("Photo deleted."))
        loadPhotos(keepFocusId = photosUi.value.focusedPhoto?.id)
    }

    /** Re-runs the pending music rung once the audio permission dialog is answered. */
    fun onAudioAccessResult(granted: Boolean) {
        musicUi.update { it.copy(hasAudioAccess = granted) }
        if (!granted) {
            emit(HomeEvent.ShowMessage("Music needs audio access to read songs on this device."))
            return
        }
        val depth = xoraDepth.value
        if (depth == XoraXmbDepth.MusicAlbums || depth == XoraXmbDepth.MusicTracks) {
            openMusicRung(depth, musicUi.value.drilledAlbumId)
        } else {
            loadMusicAlbumsForColumn()
        }
    }

    fun toggleNowPlaying() {
        val current = nowPlayingController.state.value
        val track = current.track ?: return
        // Device: MediaPlayer pause/resume. Spotify: flip UI then mirror to Web API.
        nowPlayingController.togglePlayPause()
        if (track.source != MusicSource.Spotify) return
        viewModelScope.launch {
            if (current.isPlaying) {
                spotifyWebApi.pause()
            } else {
                when (val result = spotifyWebApi.play(track.contentUri, track.contextUri)) {
                    SpotifyPlaybackResult.Started -> Unit
                    SpotifyPlaybackResult.NoActiveDevice -> {
                        nowPlayingController.setRemotePlaying(false)
                        emit(
                            HomeEvent.ShowMessage(
                                "Open the Spotify app on a device first — XOrA plays through it.",
                            ),
                        )
                    }
                    SpotifyPlaybackResult.NeedsPremium -> {
                        nowPlayingController.setRemotePlaying(false)
                        emit(HomeEvent.ShowMessage("Spotify Premium is required to start playback."))
                    }
                    is SpotifyPlaybackResult.Failed -> {
                        nowPlayingController.setRemotePlaying(false)
                        emit(HomeEvent.ShowMessage(result.message))
                    }
                }
            }
        }
    }

    fun toggleShuffle() {
        nowPlayingController.toggleShuffle()
    }

    fun toggleRepeat() {
        nowPlayingController.toggleRepeat()
    }

    fun skipNextTrack() {
        val track = nowPlayingController.skipNext() ?: return
        if (track.source == MusicSource.Spotify) {
            playSpotifyTrack(track, alreadyQueued = true)
        }
    }

    fun skipPreviousTrack() {
        val track = nowPlayingController.skipPrevious() ?: return
        if (track.source == MusicSource.Spotify) {
            playSpotifyTrack(track, alreadyQueued = true)
        }
    }

    private fun linkDspAccount(provider: DspProvider) {
        when (provider) {
            DspProvider.Spotify -> {
                if (spotifyTokenStore.isLinked()) {
                    emit(HomeEvent.ShowMessage("Spotify linked"))
                    return
                }
                if (!spotifyAuth.isConfigured()) {
                    emit(
                        HomeEvent.ShowMessage(
                            "Add spotify.client.id to local.properties to enable Spotify linking",
                        ),
                    )
                    return
                }
                val url = spotifyAuth.beginAuthorization()
                if (url.isNullOrBlank()) {
                    emit(HomeEvent.ShowMessage("Could not start Spotify sign-in"))
                    return
                }
                viewModelScope.launch {
                    runCatching {
                        externalAuthRequests.send(HomeExternalAuthRequest.SpotifyOAuth(url))
                    }
                }
            }
            DspProvider.AppleMusic ->
                emit(HomeEvent.ShowMessage("Apple Music — coming soon."))
            DspProvider.YoutubeMusic ->
                emit(HomeEvent.ShowMessage("YouTube Music — coming soon."))
        }
    }

    fun applySpotifyAuthReturn(uri: android.net.Uri) {
        if (!spotifyAuth.isReturnUri(uri)) return
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = spotifyAuth.exchangeReturnUri(uri)) {
                SpotifyLinkResult.Ignored -> Unit
                SpotifyLinkResult.Linked -> withContext(Dispatchers.Main.immediate) {
                    emit(HomeEvent.ShowMessage("Spotify linked"))
                }
                is SpotifyLinkResult.Failed -> withContext(Dispatchers.Main.immediate) {
                    emit(HomeEvent.ShowMessage("Spotify: ${result.message}"))
                }
            }
        }
    }

    private fun cycleXoraCategory(delta: Int) {
        noteUserActivity()
        val size = XoraXmbCategory.entries.size
        val next = (xoraCategoryIndex.value + delta).mod(size)
        xoraCategoryIndex.value = next
        xoraItemIndex.value = defaultXoraCategoryItemIndex(
            XoraXmbCategory.entries[next],
        )
        xoraDepth.value = XoraXmbDepth.Category
        xoraDrilledPlatformId.value = null
        xoraReturnStack.clear()
        onXoraCategoryLanded(XoraXmbCategory.entries[next])
    }

    private fun moveXoraItem(delta: Int) {
        noteUserActivity()
        val items = uiState.value.xoraXmb.items
        if (items.isEmpty()) return
        val next = (xoraItemIndex.value + delta).coerceIn(0, items.lastIndex)
        xoraItemIndex.value = next
        // Resolve from the item list — uiState.focusGame is still stale until combine emits.
        syncLibraryFromXoraItem(items.getOrNull(next))
    }

    /** Keep library selection / trailers in sync with the XMB-focused game row. */
    private fun syncLibraryFromXoraItem(item: XoraXmbItem?) {
        when (val action = item?.action) {
            is XoraXmbAction.LaunchGame -> viewModelScope.launch {
                libraryRepository.observeGames().first()
                    .find { it.id == action.gameId }
                    ?.let { focusGameInLibrary(it) }
            }
            XoraXmbAction.LaunchContinueOrFavorite -> {
                when (uiState.value.xoraXmb.gamesSecondarySlot) {
                    GamesSecondarySlot.Continue ->
                        uiState.value.homeHub.continueGame?.let { focusGameInLibrary(it) }
                    GamesSecondarySlot.Favorite -> viewModelScope.launch {
                        libraryRepository.observeGames().first()
                            .filter { !it.isAndroidApp && it.favorite }
                            .maxByOrNull { it.lastPlayedAt ?: 0L }
                            ?.let { focusGameInLibrary(it) }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun focusGameInLibrary(game: Game) {
        viewModelScope.launch {
            val all = libraryRepository.observeGames().first()
            val summaries = libraryRepository.observePlatformSummaries().first()
            val tabs = buildTabs(all, summaries)
            val tabIndex = tabs.indexOfFirst { it.platformId == game.platformId }
                .takeIf { it >= 0 }
                ?: tabs.indexOfFirst { it.kind == TabKind.All }.coerceAtLeast(0)
            val tabGames = gamesForTab(all, tabs.getOrNull(tabIndex))
            val gameIndex = tabGames.indexOfFirst { it.id == game.id }.coerceAtLeast(0)
            selection.value = Selection(tabIndex = tabIndex, gameIndex = gameIndex)
        }
    }

    /** Legacy Smash hub nav — retained for shortcut customize overlays opened from Themes. */
    private fun onHomeHubNavAction(action: NavAction, state: HomeUiState) {
        val hub = state.homeHub
        when (hub.section) {
            HomeHubSection.ShardMenu -> when (action) {
                NavAction.Left -> selectHomeShard(
                    when (hub.shard) {
                        HomeShard.RetroAchievements, HomeShard.Shop -> HomeShard.Continue
                        HomeShard.Continue -> HomeShard.Continue
                    },
                )
                NavAction.Right -> selectHomeShard(
                    when (hub.shard) {
                        HomeShard.Continue -> HomeShard.RetroAchievements
                        else -> hub.shard
                    },
                )
                NavAction.Up -> selectHomeShard(
                    when (hub.shard) {
                        HomeShard.Shop -> HomeShard.RetroAchievements
                        else -> hub.shard
                    },
                )
                NavAction.Down -> when (hub.shard) {
                    HomeShard.RetroAchievements -> selectHomeShard(HomeShard.Shop)
                    HomeShard.Continue, HomeShard.Shop -> {
                        homeHubSection.value = HomeHubSection.Shortcuts
                        homeShortcutIndex.value = 0
                    }
                }
                NavAction.Confirm -> activateHomeShard(hub.shard)
                NavAction.Cancel -> Unit
                NavAction.Options -> openStartSettings(StartSettingsCategory.Themes)
                NavAction.SwapScreens -> swapScreenRoles()
                NavAction.ToggleAccountPanel -> toggleAccountPanel()
                NavAction.ToggleSystemPanel -> toggleSystemPanel()
                NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
                else -> Unit
            }

            HomeHubSection.Shortcuts -> onHomeShortcutsNavAction(action, hub)
        }
    }

    private fun onHomeShortcutsNavAction(action: NavAction, hub: HomeHubUiState) {
        if (hub.shortcutsEditMode) {
            when (hub.customizeChrome) {
                ShortcutCustomizeChrome.Columns, ShortcutCustomizeChrome.Rows -> when (action) {
                    NavAction.Left -> adjustShortcutGridDimension(
                        columnsDelta = if (hub.customizeChrome == ShortcutCustomizeChrome.Columns) -1 else 0,
                        rowsDelta = if (hub.customizeChrome == ShortcutCustomizeChrome.Rows) -1 else 0,
                    )
                    NavAction.Right -> adjustShortcutGridDimension(
                        columnsDelta = if (hub.customizeChrome == ShortcutCustomizeChrome.Columns) 1 else 0,
                        rowsDelta = if (hub.customizeChrome == ShortcutCustomizeChrome.Rows) 1 else 0,
                    )
                    NavAction.Up -> {
                        if (hub.customizeChrome == ShortcutCustomizeChrome.Rows) {
                            shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Columns
                        }
                    }
                    NavAction.Down -> {
                        if (hub.customizeChrome == ShortcutCustomizeChrome.Columns) {
                            shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Rows
                        } else {
                            shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
                        }
                    }
                    NavAction.Confirm -> Unit
                    NavAction.Cancel -> closeHomeShortcutsCustomize()
                    NavAction.Options -> closeHomeShortcutsCustomize()
                    NavAction.ScrapeMenu -> {
                        // Stay on density chrome; Select already opened customize.
                    }
                    NavAction.SwapScreens -> swapScreenRoles()
                    NavAction.ToggleAccountPanel -> toggleAccountPanel()
                    NavAction.ToggleSystemPanel -> toggleSystemPanel()
                    NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
                    else -> Unit
                }
                ShortcutCustomizeChrome.Tiles -> when (action) {
                    NavAction.Left -> moveHomeShortcutSpatial(ShortcutNavDirection.Left)
                    NavAction.Right -> moveHomeShortcutSpatial(ShortcutNavDirection.Right)
                    NavAction.Up -> {
                        val includeAdd = hub.shortcutsEditMode || hub.shortcuts.isEmpty()
                        val placements = packShortcutPlacements(
                            hub.shortcuts,
                            includeAdd,
                            columns = hub.shortcutGridColumns,
                        )
                        val current = placements.firstOrNull { it.index == hub.shortcutIndex }
                        if (current != null && current.row == 0) {
                            shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Rows
                        } else {
                            moveHomeShortcutSpatial(ShortcutNavDirection.Up)
                        }
                    }
                    NavAction.Down -> moveHomeShortcutSpatial(ShortcutNavDirection.Down)
                    NavAction.Confirm -> activateHomeShortcut()
                    NavAction.Cancel -> closeHomeShortcutsCustomize()
                    NavAction.Options -> closeHomeShortcutsCustomize()
                    NavAction.ScrapeMenu -> cycleFocusedShortcutSpan()
                    NavAction.SwapScreens -> swapScreenRoles()
                    NavAction.ToggleAccountPanel -> toggleAccountPanel()
                    NavAction.ToggleSystemPanel -> toggleSystemPanel()
                    NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
                    else -> Unit
                }
            }
            return
        }

        when (action) {
            NavAction.Left -> moveHomeShortcutSpatial(ShortcutNavDirection.Left)
            NavAction.Right -> moveHomeShortcutSpatial(ShortcutNavDirection.Right)
            NavAction.Up -> {
                val includeAdd = hub.shortcuts.isEmpty()
                val placements = packShortcutPlacements(
                    hub.shortcuts,
                    includeAdd,
                    columns = hub.shortcutGridColumns,
                )
                val current = placements.firstOrNull { it.index == hub.shortcutIndex }
                if (current != null && current.row == 0) {
                    homeHubSection.value = HomeHubSection.ShardMenu
                } else {
                    moveHomeShortcutSpatial(ShortcutNavDirection.Up)
                }
            }
            NavAction.Down -> moveHomeShortcutSpatial(ShortcutNavDirection.Down)
            NavAction.Confirm -> activateHomeShortcut()
            NavAction.Cancel -> homeHubSection.value = HomeHubSection.ShardMenu
            NavAction.Options -> openHomeShortcutsCustomize()
            NavAction.ScrapeMenu -> openHomeShortcutsCustomize()
            NavAction.SwapScreens -> swapScreenRoles()
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
            else -> Unit
        }
    }

    fun selectHomeShard(shard: HomeShard) {
        noteUserActivity()
        homeHubSection.value = HomeHubSection.ShardMenu
        homeShard.value = shard
    }

    fun activateHomeShard(shard: HomeShard = homeShard.value) {
        noteUserActivity()
        homeShard.value = shard
        when (shard) {
            HomeShard.Continue -> {
                val game = uiState.value.homeHub.continueGame
                if (game != null) {
                    launchGame(game)
                } else {
                    setHomePage(HomePage.GameSelector)
                    emit(HomeEvent.ShowMessage("Browse your library to start playing."))
                }
            }
            HomeShard.RetroAchievements -> openRaLibrary()
            HomeShard.Shop -> emit(HomeEvent.ShowMessage("XOrA Store — coming soon."))
        }
    }

    fun selectHomeShortcut(index: Int) {
        noteUserActivity()
        if (!vitaShortcutTrayOpen.value) {
            homeHubSection.value = HomeHubSection.Shortcuts
        }
        val hub = uiState.value.homeHub
        val count = hub.shortcuts.size + if (hub.shortcutsEditMode || hub.shortcuts.isEmpty()) 1 else 0
        homeShortcutIndex.value = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun activateHomeShortcut(index: Int? = null) {
        noteUserActivity()
        val hub = uiState.value.homeHub
        if (index != null) selectHomeShortcut(index)
        val i = homeShortcutIndex.value
        val shortcuts = hub.shortcuts
        val addSlot = hub.shortcutsEditMode || shortcuts.isEmpty()
        if (addSlot && i >= shortcuts.size) {
            openAddShortcutChooser()
            return
        }
        val shortcut = shortcuts.getOrNull(i) ?: return
        if (hub.shortcutsEditMode) {
            removeHomeShortcut(shortcut.id)
            return
        }
        if (hub.vitaShortcutTrayOpen) {
            if (hub.vitaShortcutLaunch != null) {
                confirmVitaShortcutLaunch()
            } else if (hub.vitaShortcutDepartingIndex == null) {
                beginVitaShortcutDepart(i, shortcut)
            }
            return
        }
        openHomeShortcut(shortcut)
    }

    fun openAddShortcutChooser() {
        noteUserActivity()
        shortcutTargetPicker.value = null
        pendingShortcutKind.value = null
        pendingShortcutSpan.value = ShortcutSpan.Default
        if (vitaShortcutTrayOpen.value) {
            vitaShortcutPinMode.value = true
        }
        addShortcutOpen.value = true
    }

    fun dismissAddShortcutChooser() {
        shortcutTargetPicker.value = null
        pendingShortcutKind.value = null
        pendingShortcutSpan.value = ShortcutSpan.Default
        addShortcutOpen.value = false
        if (!vitaShortcutTrayOpen.value) {
            vitaShortcutPinMode.value = false
        }
    }

    fun beginShortcutSizeStep(kind: PendingShortcutKind) {
        noteUserActivity()
        pendingShortcutKind.value = kind
        pendingShortcutSpan.value = ShortcutSpan.Default
        shortcutTargetPicker.value = null
        addShortcutOpen.value = true
        // Vita bubbles are fixed circles — skip the Smash tile-size step.
        if (vitaShortcutPinMode.value &&
            (kind == PendingShortcutKind.LibraryGame || kind == PendingShortcutKind.AndroidApp)
        ) {
            confirmPendingShortcutSpan()
        }
    }

    fun selectPendingShortcutSpan(span: ShortcutSpan) {
        noteUserActivity()
        pendingShortcutSpan.value = span
    }

    fun cyclePendingShortcutSpan(delta: Int = 1) {
        noteUserActivity()
        val columns = shortcutGridColumns.value
        val rows = shortcutGridRows.value
        val allowed = ShortcutSpan.allowedFor(columns, rows)
        if (allowed.isEmpty()) return
        val current = pendingShortcutSpan.value.clampTo(columns, rows)
        val idx = allowed.indexOf(current).coerceAtLeast(0)
        val next = allowed[(idx + delta).mod(allowed.size)]
        pendingShortcutSpan.value = next
    }

    fun confirmPendingShortcutSpan() {
        val kind = pendingShortcutKind.value ?: return
        noteUserActivity()
        when (kind) {
            PendingShortcutKind.LibraryGame -> openLibraryGameTargetPicker()
            PendingShortcutKind.AndroidApp -> openAndroidAppTargetPicker()
            PendingShortcutKind.Picture -> requestShortcutPicturePicker()
            PendingShortcutKind.Gif -> requestShortcutGifPicker()
        }
    }

    fun cancelPendingShortcutSpan() {
        noteUserActivity()
        pendingShortcutKind.value = null
        pendingShortcutSpan.value = ShortcutSpan.Default
    }

    fun openThemesSheet(tab: ThemesSheetTab = ThemesSheetTab.Customize) {
        noteUserActivity()
        themesSheetTab.value = tab
        // Always land on Home so dual-screen Grid/Hero roles and page content stay coherent while
        // the Activity-hosted customize overlay is up.
        homePage.value = HomePage.Home
        themesOpen.value = true
    }

    fun dismissThemesSheet() {
        themesOpen.value = false
    }

    fun selectShellTheme(themeId: String) {
        noteUserActivity()
        performStartSettingsAction(StartSettingsAction.SelectShellTheme(themeId))
    }

    fun notifyShopThemesComingSoon() {
        noteUserActivity()
        performStartSettingsAction(StartSettingsAction.ShopThemesComingSoon)
    }

    fun notifyThemeUploadComingSoon() {
        noteUserActivity()
        performStartSettingsAction(StartSettingsAction.UploadThemeToShopComingSoon)
    }

    fun requestShortcutPicturePicker() {
        noteUserActivity()
        shortcutTargetPicker.value = null
        // Keep pending span; clear kind step UI by closing the sheet into the picker.
        pendingShortcutKind.value = null
        addShortcutOpen.value = false
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.ShortcutPicture) }
        }
    }

    fun requestShortcutGifPicker() {
        noteUserActivity()
        shortcutTargetPicker.value = null
        pendingShortcutKind.value = null
        addShortcutOpen.value = false
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.ShortcutGif) }
        }
    }

    fun requestWallpaperPicker() {
        noteUserActivity()
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.Wallpaper) }
        }
    }

    fun requestBgmPicker() {
        noteUserActivity()
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.Bgm) }
        }
    }

    fun requestProfileAvatarPicker() {
        noteUserActivity()
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.ProfileAvatar) }
        }
    }

    /** Select on a system card: choose your own banner for that console. */
    fun requestPlatformBanner(platformId: String) {
        noteUserActivity()
        if (platformId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                mediaPickerRequests.send(HomeMediaPickerRequest.PlatformBanner(platformId))
            }
        }
    }

    fun setPlatformBanner(platformId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching { platformArtStore.import(platformId, uri) }
                .onSuccess { emit(HomeEvent.ShowMessage("Console art updated.")) }
                .onFailure { error ->
                    emit(HomeEvent.ShowError(error.message ?: "Could not import that image."))
                }
        }
    }

    /** Photo / GIF for the open DM. See [attachToOpenDiscordDm] for why this leaves the shell. */
    fun requestDiscordAttachment() {
        noteUserActivity()
        if (discordRichPresence.dmThread.value.peerUserId == null) return
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.DiscordAttachment) }
        }
    }

    /**
     * Hands the picked file to the Discord app to upload.
     *
     * The Social SDK can only send message text — it has no upload endpoint — so a local photo
     * cannot leave the launcher on its own. Links do work end to end: paste or type an image /
     * GIF URL and both Discord and the shell render it inline.
     */
    fun attachToOpenDiscordDm(uri: Uri) {
        noteUserActivity()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = appContext.contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val toDiscord = Intent(intent).setPackage(DISCORD_PACKAGE)
        if (runCatching { appContext.startActivity(toDiscord); true }.getOrDefault(false)) return
        val chooser = Intent.createChooser(intent, "Send with")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { appContext.startActivity(chooser); true }.getOrDefault(false)) return
        emit(HomeEvent.ShowError("No app available to send that file."))
    }

    fun requestSteamOpenId() {
        noteUserActivity()
        viewModelScope.launch {
            runCatching { externalAuthRequests.send(HomeExternalAuthRequest.SteamOpenId) }
        }
    }

    fun applySteamOpenIdReturn(uri: android.net.Uri) {
        if (!SteamOpenId.isReturnUri(uri)) return
        val steamId = SteamOpenId.steamId64FromReturnUri(uri)
        if (steamId.isNullOrBlank()) {
            emit(HomeEvent.ShowError("Steam sign-in did not return a SteamID64."))
            return
        }
        viewModelScope.launch {
            preferences.setSteamId64(steamId)
            emit(HomeEvent.ShowMessage("Steam ID saved. Paste a Web API key if needed."))
        }
    }

    fun openShortcutEditorFromThemes() {
        noteUserActivity()
        themesOpen.value = false
        openVitaShortcutTray(edit = true)
    }

    fun openHomeShortcutsCustomize() {
        noteUserActivity()
        if (vitaShortcutTrayOpen.value) {
            openVitaShortcutEditMode()
            return
        }
        homeHubSection.value = HomeHubSection.Shortcuts
        homeShortcutsEditMode.value = true
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Columns
        homeShortcutIndex.value = homeShortcutIndex.value.coerceAtLeast(0)
    }

    fun closeHomeShortcutsCustomize() {
        noteUserActivity()
        homeShortcutsEditMode.value = false
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
    }

    fun toggleHomeShortcutsEditMode() {
        if (homeShortcutsEditMode.value) {
            closeHomeShortcutsCustomize()
        } else {
            openHomeShortcutsCustomize()
        }
    }

    fun focusShortcutCustomizeChrome(chrome: ShortcutCustomizeChrome) {
        noteUserActivity()
        if (!homeShortcutsEditMode.value) return
        shortcutCustomizeChrome.value = chrome
        if (chrome == ShortcutCustomizeChrome.Tiles) {
            homeHubSection.value = HomeHubSection.Shortcuts
        }
    }

    fun adjustShortcutGridColumns(delta: Int) {
        adjustShortcutGridDimension(columnsDelta = delta, rowsDelta = 0)
    }

    fun adjustShortcutGridRows(delta: Int) {
        adjustShortcutGridDimension(columnsDelta = 0, rowsDelta = delta)
    }

    private fun adjustShortcutGridDimension(columnsDelta: Int, rowsDelta: Int) {
        noteUserActivity()
        val nextColumns = (shortcutGridColumns.value + columnsDelta)
            .coerceIn(MIN_HOME_SHORTCUT_GRID_COLUMNS, MAX_HOME_SHORTCUT_GRID_COLUMNS)
        val nextRows = (shortcutGridRows.value + rowsDelta)
            .coerceIn(MIN_HOME_SHORTCUT_GRID_ROWS, MAX_HOME_SHORTCUT_GRID_ROWS)
        if (nextColumns == shortcutGridColumns.value && nextRows == shortcutGridRows.value) return
        shortcutGridColumns.value = nextColumns
        shortcutGridRows.value = nextRows
        viewModelScope.launch {
            preferences.setHomeShortcutGridLayout(nextColumns, nextRows)
            clampHomeShortcutSpansToGrid(nextColumns, nextRows)
        }
    }

    private suspend fun clampHomeShortcutSpansToGrid(columns: Int, rows: Int) {
        val current = homeShortcuts.value
        val next = current.map { item ->
            val clamped = item.span.clampTo(columns, rows)
            if (clamped == item.span) item else item.copy(span = clamped)
        }
        if (next == current) return
        homeShortcuts.value = next
        preferences.setHomeShortcuts(next)
    }

    private fun moveHomeShortcutSpatial(direction: ShortcutNavDirection) {
        val hub = uiState.value.homeHub
        val includeAdd = hub.shortcutsEditMode || hub.shortcuts.isEmpty()
        val placements = packShortcutPlacements(
            hub.shortcuts,
            includeAdd,
            columns = hub.shortcutGridColumns,
        )
        if (placements.isEmpty()) return
        val next = findNeighborShortcutIndex(placements, hub.shortcutIndex, direction)
        homeShortcutIndex.value = next
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
    }

    fun cycleFocusedShortcutSpan(index: Int? = null) {
        noteUserActivity()
        val hub = uiState.value.homeHub
        if (!hub.shortcutsEditMode) return
        val i = index ?: homeShortcutIndex.value
        val shortcut = hub.shortcuts.getOrNull(i) ?: return
        val nextSpan = shortcut.span.nextFitting(hub.shortcutGridColumns, hub.shortcutGridRows)
        viewModelScope.launch {
            val next = homeShortcuts.value.map { item ->
                if (item.id == shortcut.id) item.copy(span = nextSpan) else item
            }
            homeShortcuts.value = next
            preferences.setHomeShortcuts(next)
            emit(HomeEvent.ShowMessage("Size ${nextSpan.label}"))
        }
    }

    private fun openHomeShortcut(shortcut: HomeShortcut) {
        when (shortcut.kind) {
            HomeShortcutKind.Game -> {
                viewModelScope.launch {
                    val match = libraryRepository.observeGames().first()
                        .firstOrNull { it.id == shortcut.target }
                    if (match != null) launchGame(match)
                    else emit(HomeEvent.ShowError("That game is no longer in your library."))
                }
            }
            HomeShortcutKind.AndroidApp -> {
                viewModelScope.launch {
                    val match = libraryRepository.observeGames().first()
                        .firstOrNull { it.isAndroidApp && it.fileName == shortcut.target }
                        ?: libraryRepository.observeGames().first()
                            .firstOrNull { it.isAndroidApp && it.id.contains(shortcut.target) }
                    if (match != null) {
                        launchGame(match)
                    } else {
                        val launch = appContext.packageManager.getLaunchIntentForPackage(shortcut.target)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { appContext.startActivity(launch) }
                                .onFailure {
                                    emit(HomeEvent.ShowError("Could not open ${shortcut.title}."))
                                }
                        } else {
                            emit(HomeEvent.ShowError("App not installed: ${shortcut.title}"))
                        }
                    }
                }
            }
            HomeShortcutKind.Picture, HomeShortcutKind.Gif -> {
                val file = themeMediaStore.resolveShortcutArt(shortcut.target)
                    ?: File(shortcut.target).takeIf { it.isFile && it.length() > 0L }
                if (file == null) {
                    emit(HomeEvent.ShowError("Media file missing."))
                    return
                }
                val mime = if (shortcut.kind == HomeShortcutKind.Gif) "image/gif" else "image/*"
                val contentUri = runCatching {
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.files",
                        file,
                    )
                }.getOrNull()
                if (contentUri == null) {
                    emit(HomeEvent.ShowMessage("Pinned: ${shortcut.title}"))
                    return
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { appContext.startActivity(intent) }
                    .onFailure {
                        emit(HomeEvent.ShowMessage("Pinned: ${shortcut.title}"))
                    }
            }
        }
    }

    fun setHomeWallpaper(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = themeMediaStore.importWallpaper(uri)
                preferences.setHomeWallpaperPath(path)
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import wallpaper."))
            }
        }
    }

    fun clearHomeWallpaper() {
        viewModelScope.launch {
            themeMediaStore.clearWallpaper()
            preferences.setHomeWallpaperPath(null)
        }
    }

    private fun requestHomeFolderImage() {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.HomeFolderImage) }
        }
    }

    fun setHomeFolderImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = themeMediaStore.importFolderImage(uri)
                preferences.setHomeFolderImagePath(path)
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import folder image."))
            }
        }
    }

    fun setCustomBgm(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = themeMediaStore.importBgm(uri)
                preferences.setCustomBgmPath(path)
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import BGM."))
            }
        }
    }

    fun clearCustomBgm() {
        viewModelScope.launch {
            themeMediaStore.clearBgm()
            preferences.setCustomBgmPath(null)
        }
    }

    fun addShortcutPinRecentGame() {
        beginShortcutSizeStep(PendingShortcutKind.LibraryGame)
    }

    fun addShortcutPinAndroidApp() {
        beginShortcutSizeStep(PendingShortcutKind.AndroidApp)
    }

    fun addShortcutPinPicture() {
        beginShortcutSizeStep(PendingShortcutKind.Picture)
    }

    fun addShortcutPinGif() {
        beginShortcutSizeStep(PendingShortcutKind.Gif)
    }

    private fun openLibraryGameTargetPicker() {
        viewModelScope.launch {
            val games = libraryRepository.observeGames().first()
                .filter { !it.isAndroidApp }
                .sortedBy { it.title.lowercase() }
            if (games.isEmpty()) {
                emit(HomeEvent.ShowMessage("No library games to pin yet."))
                pendingShortcutKind.value = null
                return@launch
            }
            noteUserActivity()
            pendingShortcutKind.value = null
            shortcutTargetPicker.value = ShortcutTargetPickerUiState(
                kind = ShortcutPinTargetKind.LibraryGame,
                candidates = games,
                selectedIndex = 0,
            )
            addShortcutOpen.value = true
        }
    }

    private fun openAndroidAppTargetPicker() {
        viewModelScope.launch {
            val apps = libraryRepository.observeGames().first()
                .filter { it.isAndroidApp }
                .sortedBy { it.title.lowercase() }
            if (apps.isEmpty()) {
                emit(HomeEvent.ShowMessage("No Android apps synced yet. Open Settings to refresh."))
                pendingShortcutKind.value = null
                return@launch
            }
            noteUserActivity()
            pendingShortcutKind.value = null
            shortcutTargetPicker.value = ShortcutTargetPickerUiState(
                kind = ShortcutPinTargetKind.AndroidApp,
                candidates = apps,
                selectedIndex = 0,
            )
            addShortcutOpen.value = true
        }
    }

    fun selectShortcutTarget(index: Int) {
        noteUserActivity()
        shortcutTargetPicker.update { current ->
            if (current == null || current.candidates.isEmpty()) return@update current
            current.copy(
                selectedIndex = index.coerceIn(0, current.candidates.lastIndex),
            )
        }
    }

    fun confirmShortcutTarget() {
        val picker = shortcutTargetPicker.value ?: return
        val game = picker.selected ?: return
        val span = pendingShortcutSpan.value
        noteUserActivity()
        viewModelScope.launch {
            when (picker.kind) {
                ShortcutPinTargetKind.LibraryGame -> {
                    appendShortcut(
                        HomeShortcut(
                            id = UUID.randomUUID().toString(),
                            kind = HomeShortcutKind.Game,
                            title = game.title,
                            target = game.id,
                            artPath = game.gridArt,
                            span = span,
                        ),
                    )
                }
                ShortcutPinTargetKind.AndroidApp -> {
                    appendShortcut(
                        HomeShortcut(
                            id = UUID.randomUUID().toString(),
                            kind = HomeShortcutKind.AndroidApp,
                            title = game.title,
                            target = game.fileName,
                            artPath = InstalledAppSync.iconPathFor(game.fileName),
                            span = span,
                        ),
                    )
                }
            }
            shortcutTargetPicker.value = null
            pendingShortcutSpan.value = ShortcutSpan.Default
            addShortcutOpen.value = false
        }
    }

    /** B from the target list returns to the type chooser without pinning. */
    fun cancelShortcutTargetPicker() {
        noteUserActivity()
        shortcutTargetPicker.value = null
        pendingShortcutKind.value = null
    }

    private fun onAddShortcutNavAction(action: NavAction, state: HomeUiState) {
        val picker = state.homeHub.shortcutTargetPicker
        if (picker != null) {
            when (action) {
                NavAction.Up -> selectShortcutTarget(picker.selectedIndex - 1)
                NavAction.Down -> selectShortcutTarget(picker.selectedIndex + 1)
                NavAction.Confirm -> confirmShortcutTarget()
                NavAction.Cancel -> cancelShortcutTargetPicker()
                else -> Unit
            }
            return
        }
        if (state.homeHub.pendingShortcutKind != null) {
            when (action) {
                NavAction.Left -> cyclePendingShortcutSpan(-1)
                NavAction.Right -> cyclePendingShortcutSpan(1)
                NavAction.Confirm -> confirmPendingShortcutSpan()
                NavAction.Cancel -> cancelPendingShortcutSpan()
                else -> Unit
            }
            return
        }
        when (action) {
            NavAction.Cancel -> dismissAddShortcutChooser()
            else -> Unit
        }
    }

    fun addShortcutFromMedia(uri: Uri, gif: Boolean) {
        viewModelScope.launch {
            runCatching {
                val id = UUID.randomUUID().toString()
                val path = themeMediaStore.importShortcutArt(uri, id)
                val span = pendingShortcutSpan.value
                appendShortcut(
                    HomeShortcut(
                        id = id,
                        kind = if (gif) HomeShortcutKind.Gif else HomeShortcutKind.Picture,
                        title = if (gif) "GIF" else "Picture",
                        target = path,
                        artPath = path,
                        span = span,
                    ),
                )
                shortcutTargetPicker.value = null
                pendingShortcutSpan.value = ShortcutSpan.Default
                pendingShortcutKind.value = null
                addShortcutOpen.value = false
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import media."))
            }
        }
    }

    private suspend fun appendShortcut(shortcut: HomeShortcut) {
        val columns = shortcutGridColumns.value
        val rows = shortcutGridRows.value
        val clamped = shortcut.copy(span = shortcut.span.clampTo(columns, rows))
        val next = homeShortcuts.value + clamped
        homeShortcuts.value = next
        preferences.setHomeShortcuts(next)
        homeShortcutsEditMode.value = true
        shortcutCustomizeChrome.value = ShortcutCustomizeChrome.Tiles
        if (!vitaShortcutTrayOpen.value) {
            homeHubSection.value = HomeHubSection.Shortcuts
        }
        homeShortcutIndex.value = next.lastIndex
    }

    fun removeHomeShortcut(id: String) {
        viewModelScope.launch {
            val next = homeShortcuts.value.filterNot { it.id == id }
            homeShortcuts.value = next
            preferences.setHomeShortcuts(next)
            homeShortcutIndex.update { it.coerceIn(0, (next.size).coerceAtLeast(0)) }
        }
    }

    private fun onAccountPanelNavAction(action: NavAction) {
        // Social menu owns the gamepad while open — absorb L/R and LB/RB (tabs); no page hops.
        when (action) {
            NavAction.Left, NavAction.PreviousPlatform -> {
                if (notificationsOpen.value) return
                clearConversationReply()
                cycleSocialMenuTab(-1)
            }
            NavAction.Right, NavAction.NextPlatform -> {
                if (notificationsOpen.value) return
                clearConversationReply()
                cycleSocialMenuTab(1)
            }
            NavAction.Up -> moveAccountPanelSelection(-1)
            NavAction.Down -> moveAccountPanelSelection(1)
            NavAction.Confirm -> activateAccountPanelSelection()
            NavAction.Cancel -> {
                when {
                    conversationReply.value.conversationKey != null -> clearConversationReply()
                    discordRichPresence.dmThread.value.peerUserId != null -> handleDiscordDmBack()
                    notificationsOpen.value -> {
                        notificationsOpen.value = false
                        accountPanelSelectedIndex.value = 0
                    }
                    else -> toggleAccountPanel()
                }
            }
            NavAction.ScrapeMenu -> {
                when {
                    conversationReply.value.conversationKey != null -> Unit
                    discordRichPresence.dmThread.value.peerUserId != null -> Unit
                    notificationsOpen.value -> Unit
                    else -> toggleManagingCircle()
                }
            }
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            else -> Unit
        }
    }

    private fun toggleManagingCircle() {
        noteUserActivity()
        val entering = !managingCircle.value
        if (entering) {
            notificationsOpen.value = false
        }
        managingCircle.update { !it }
    }

    private fun onSystemPanelNavAction(action: NavAction) {
        when (action) {
            NavAction.Up -> moveSystemPanelSelection(-1)
            NavAction.Down -> moveSystemPanelSelection(1)
            NavAction.Confirm -> activateSystemPanelSelection()
            NavAction.Cancel -> {
                when {
                    systemFavoritePickerOpen.value -> closeFavoritePicker()
                    systemStatusEditorOpen.value -> closeStatusEditor()
                    else -> collapseHeroPanels()
                }
            }
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            else -> Unit
        }
    }

    private fun moveSystemPanelSelection(delta: Int) {
        val rows = currentSystemPanelRows()
        val size = rows.size
        if (size == 0) return
        systemPanelSelectedIndex.update { current ->
            (current + delta).coerceIn(0, size - 1)
        }
    }

    fun selectSystemPanelRow(index: Int) {
        noteUserActivity()
        val last = (currentSystemPanelRows().size - 1).coerceAtLeast(0)
        systemPanelSelectedIndex.value = index.coerceIn(0, last)
    }

    fun activateSystemPanelSelection(index: Int? = null) {
        noteUserActivity()
        val rows = currentSystemPanelRows()
        if (rows.isEmpty()) return
        val rowIndex = index ?: uiState.value.systemPanelSelectedIndex
        if (index != null) {
            systemPanelSelectedIndex.value = index.coerceIn(0, rows.lastIndex)
        }
        when (val row = rows.getOrNull(rowIndex)) {
            SystemPanelRow.Notifications -> openNotificationHistory()
            SystemPanelRow.EditProfile -> {
                closeStatusEditor()
                profileEditRequest.update { it + 1 }
            }
            is SystemPanelRow.JumpBack -> {
                val game = uiState.value.quickLaunchGames.firstOrNull { it.id == row.gameId } ?: return
                collapseHeroPanels()
                launchGame(game)
            }
            SystemPanelRow.Status -> {
                if (systemStatusEditorOpen.value) {
                    saveCustomStatus()
                } else {
                    openStatusEditor()
                }
            }
            SystemPanelRow.FavoriteGame -> openFavoritePicker()
            SystemPanelRow.ClearFavorite -> {
                clearFavoriteRaGame()
                closeFavoritePicker()
            }
            is SystemPanelRow.RaFavoritePick -> {
                val game = systemFavoritePickerGames.value.firstOrNull { it.gameId == row.gameId }
                if (game != null) {
                    setFavoriteRaGame(game)
                    closeFavoritePicker()
                }
            }
            SystemPanelRow.Brightness -> openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)
            SystemPanelRow.Wifi -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
            SystemPanelRow.Bluetooth -> openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            SystemPanelRow.AllSettings -> openSystemSettings(Settings.ACTION_SETTINGS)
            null -> Unit
        }
    }

    fun openNotificationHistory() {
        noteUserActivity()
        playNavCloseIfHeroPanelOpen()
        systemPanelExpanded.value = false
        accountPanelExpanded.value = false
        achievementsPanelExpanded.value = false
        notificationHistorySelectedIndex.value =
            if (uiState.value.notificationHistory.isEmpty()) 0 else 1
        notificationHistoryOpen.value = true
        shellNotifications.markAllRead()
    }

    fun closeNotificationHistory() {
        noteUserActivity()
        notificationHistoryOpen.value = false
    }

    fun selectNotificationHistoryIndex(index: Int) {
        noteUserActivity()
        val last = if (uiState.value.notificationHistory.isEmpty()) {
            0
        } else {
            uiState.value.notificationHistory.size
        }
        notificationHistorySelectedIndex.value = index.coerceIn(0, last)
    }

    fun clearNotificationHistory() {
        noteUserActivity()
        shellNotifications.clearHistory()
        notificationHistorySelectedIndex.value = 0
    }

    private fun onNotificationHistoryNavAction(action: NavAction) {
        val last = if (uiState.value.notificationHistory.isEmpty()) {
            0
        } else {
            uiState.value.notificationHistory.size
        }
        when (action) {
            NavAction.Up -> {
                notificationHistorySelectedIndex.update { (it - 1).coerceIn(0, last) }
            }
            NavAction.Down -> {
                notificationHistorySelectedIndex.update { (it + 1).coerceIn(0, last) }
            }
            NavAction.Cancel, NavAction.ToggleSystemPanel -> closeNotificationHistory()
            NavAction.Confirm -> activateSelectedNotificationHistory()
            NavAction.Options, NavAction.SwapScreens -> {
                val selected = notificationHistorySelectedIndex.value
                val history = uiState.value.notificationHistory
                if (selected <= 0 || history.isEmpty()) {
                    clearNotificationHistory()
                } else {
                    history.getOrNull(selected - 1)?.notification?.id?.let { id ->
                        dismissNotificationHistoryItem(id)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun onDiscordConversationNavAction(action: NavAction) {
        when (action) {
            NavAction.Confirm -> {
                viewModelScope.launch { discordRichPresence.sendDm() }
            }
            NavAction.Cancel -> handleDiscordDmBack()
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            else -> Unit
        }
    }

    private fun onXoraConversationNavAction(action: NavAction) {
        when (action) {
            NavAction.Confirm -> {
                if (xoraNetwork.state.value.dm.draft.isNotBlank()) sendOpenXoraDm()
            }
            NavAction.Cancel -> {
                if (xoraNetwork.state.value.dm.draft.isNotBlank()) {
                    xoraNetwork.updateDirectMessageDraft("")
                } else {
                    closeOpenXoraDm()
                }
            }
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            else -> Unit
        }
    }

    fun sendOpenXoraDm() {
        noteUserActivity()
        viewModelScope.launch {
            // Failures surface inline as dm.error in the conversation window.
            xoraNetwork.sendDirectMessage()
        }
    }

    private fun currentSystemPanelRows(): List<SystemPanelRow> {
        val jumpIds = uiState.value.quickLaunchGames.take(3).map { it.id }
        return buildSystemPanelRows(
            jumpBackGames = jumpIds,
            favoritePickerOpen = systemFavoritePickerOpen.value,
            favoritePickerGameIds = systemFavoritePickerGames.value.map { it.gameId },
        )
    }

    private fun systemProfileChromeFlow(): Flow<SystemProfileCardState> = combine(
        preferences.profile,
        discordRichPresence.state,
        libraryRepository.observeGames(),
        combine(
            combine(systemStatusEditorOpen, systemStatusDraft) { open, draft -> open to draft },
            combine(
                systemFavoritePickerOpen,
                systemFavoritePickerLoading,
                systemFavoritePickerGames,
                systemFavoritePickerError,
            ) { pickerOpen, pickerLoading, pickerGames, pickerError ->
                SystemProfilePickerBits(
                    favoritePickerOpen = pickerOpen,
                    favoritePickerLoading = pickerLoading,
                    favoritePickerGames = pickerGames,
                    favoritePickerError = pickerError,
                )
            },
        ) { status, picker ->
            SystemProfileChromeBits(
                statusEditorOpen = status.first,
                statusDraft = status.second,
                favoritePickerOpen = picker.favoritePickerOpen,
                favoritePickerLoading = picker.favoritePickerLoading,
                favoritePickerGames = picker.favoritePickerGames,
                favoritePickerError = picker.favoritePickerError,
            )
        },
        xoraNetwork.state,
    ) { profile, presence, games, chrome, xora ->
        val custom = profile.customStatus?.takeIf { it.isNotBlank() }
        val activityLine = resolveActivityStatusLine(presence.activity)
        val favorite = profile.favoriteRaGame?.let { pinned ->
            val playTime = games
                .filter { !it.isAndroidApp }
                .firstOrNull { it.title.equals(pinned.title, ignoreCase = true) }
                ?.playTimeMs
                ?: 0L
            SystemFavoriteGame(
                raGameId = pinned.gameId,
                title = pinned.title,
                imageIconUrl = pinned.imageIconUrl,
                playTimeMs = playTime,
            )
        }
        SystemProfileCardState(
            statusLine = custom ?: activityLine,
            isCustomStatus = custom != null,
            statusEditorOpen = chrome.statusEditorOpen,
            statusDraft = chrome.statusDraft,
            favorite = favorite,
            favoritePickerOpen = chrome.favoritePickerOpen,
            favoritePickerLoading = chrome.favoritePickerLoading,
            favoritePickerGames = chrome.favoritePickerGames,
            favoritePickerError = chrome.favoritePickerError,
            xoraNetworkSignedIn = xora.signedIn,
            xoraNetworkOnline = xora.selfOnline,
            xoraPresenceMode = xora.presenceMode,
        )
    }

    private data class SystemProfilePickerBits(
        val favoritePickerOpen: Boolean,
        val favoritePickerLoading: Boolean,
        val favoritePickerGames: List<RaCompletionGame>,
        val favoritePickerError: String?,
    )

    private data class SystemProfileChromeBits(
        val statusEditorOpen: Boolean,
        val statusDraft: String,
        val favoritePickerOpen: Boolean,
        val favoritePickerLoading: Boolean,
        val favoritePickerGames: List<RaCompletionGame>,
        val favoritePickerError: String?,
    )

    private fun resolveActivityStatusLine(
        activity: DiscordPresenceActivity,
    ): String = when (activity) {
        is DiscordPresenceActivity.Playing ->
            "playing ${activity.gameTitle}"
        is DiscordPresenceActivity.Browsing ->
            "Browsing ${activity.gameTitle}"
        DiscordPresenceActivity.InSora,
        DiscordPresenceActivity.Idle,
        -> "Browsing XOrA"
    }

    private fun openStatusEditor() {
        viewModelScope.launch {
            val custom = preferences.profile.first().customStatus.orEmpty()
            systemStatusDraft.value = custom
            systemStatusEditorOpen.value = true
        }
    }

    private fun closeStatusEditor() {
        systemStatusEditorOpen.value = false
        systemStatusDraft.value = ""
    }

    fun updateSystemStatusDraft(value: String) {
        systemStatusDraft.value = value.take(80)
    }

    fun saveCustomStatus() {
        viewModelScope.launch {
            val draft = systemStatusDraft.value.trim()
            preferences.setProfileCustomStatus(draft.ifBlank { null })
            closeStatusEditor()
        }
    }

    fun clearCustomStatus() {
        viewModelScope.launch {
            preferences.setProfileCustomStatus(null)
            closeStatusEditor()
        }
    }

    private fun openFavoritePicker() {
        if (systemFavoritePickerOpen.value) return
        closeStatusEditor()
        systemFavoritePickerOpen.value = true
        systemPanelSelectedIndex.value = 0
        viewModelScope.launch {
            systemFavoritePickerLoading.value = true
            systemFavoritePickerError.value = null
            val result = retroAchievements.fetchCompletionProgress(count = 50)
            systemFavoritePickerLoading.value = false
            result.fold(
                onSuccess = { games ->
                    systemFavoritePickerGames.value = games
                    if (games.isEmpty()) {
                        systemFavoritePickerError.value =
                            "No RetroAchievements games yet. Earn progress to pin a favorite."
                    }
                },
                onFailure = { error ->
                    systemFavoritePickerGames.value = emptyList()
                    systemFavoritePickerError.value =
                        error.message ?: "Could not load RetroAchievements games."
                },
            )
        }
    }

    private fun closeFavoritePicker() {
        systemFavoritePickerOpen.value = false
        systemFavoritePickerLoading.value = false
        systemFavoritePickerGames.value = emptyList()
        systemFavoritePickerError.value = null
        systemPanelSelectedIndex.value = 0
    }

    private fun setFavoriteRaGame(game: RaCompletionGame) {
        viewModelScope.launch {
            preferences.setProfileFavoriteRaGame(
                ProfileFavoriteRaGame(
                    gameId = game.gameId,
                    title = game.title,
                    imageIconUrl = game.imageIconUrl,
                ),
            )
        }
    }

    private fun clearFavoriteRaGame() {
        viewModelScope.launch {
            preferences.setProfileFavoriteRaGame(null)
        }
    }

    private fun openSystemSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { emit(HomeEvent.ShowError("Could not open system settings.")) }
    }

    fun selectSocialMenuTab(tab: SocialMenuTab) {
        noteUserActivity()
        if (socialMenuTab.value == tab) return
        clearConversationReply()
        socialMenuTab.value = tab
        accountPanelSelectedIndex.value = 0
        conversationRepository.refreshListenerEnabled()
        // Reuse the shared Steam friends cache; only refetch when empty or last load failed.
        if (tab == SocialMenuTab.Steam) {
            val steam = steamFriendsUi.value
            if (steam.isConfigured && !steam.isLoading && (steam.friends.isEmpty() || steam.error != null)) {
                refreshSteamFriends()
            }
        }
    }

    private fun cycleSocialMenuTab(delta: Int) {
        val tabs = SocialMenuTab.entries
        val current = tabs.indexOf(socialMenuTab.value).coerceAtLeast(0)
        val next = ((current + delta) % tabs.size + tabs.size) % tabs.size
        selectSocialMenuTab(tabs[next])
    }

    private fun moveAccountPanelSelection(delta: Int) {
        val size = uiState.value.accountPanelRows.size
        if (size == 0) return
        accountPanelSelectedIndex.update { current ->
            (current + delta).coerceIn(0, size - 1)
        }
    }

    fun selectAccountPanelRow(index: Int) {
        noteUserActivity()
        val size = uiState.value.accountPanelRows.size
        if (size == 0) return
        accountPanelSelectedIndex.value = index.coerceIn(0, size - 1)
    }

    fun activateAccountPanelSelection(index: Int? = null) {
        val state = uiState.value
        val rowIndex = index ?: state.accountPanelSelectedIndex
        if (index != null) {
            accountPanelSelectedIndex.value =
                index.coerceIn(0, (state.accountPanelRows.size - 1).coerceAtLeast(0))
        }
        val row = state.accountPanelRows.getOrNull(rowIndex) ?: return
        when (row) {
            AccountPanelRow.OpenNotifications -> {
                notificationsOpen.update { !it }
                accountPanelSelectedIndex.value = 0
            }
            AccountPanelRow.ManageCircle -> toggleManagingCircle()
            is AccountPanelRow.CircleEmptySlot -> {
                managingCircle.value = true
                emit(HomeEvent.ShowMessage("Pick a friend to pin."))
            }
            is AccountPanelRow.CircleMember -> {
                if (managingCircle.value) {
                    viewModelScope.launch { preferences.removeCirclePin(row.pin) }
                } else {
                    openCircleMemberConversation(row.pin, state.socialMenu)
                }
            }
            is AccountPanelRow.AddToCircle -> {
                viewModelScope.launch {
                    if (circlePins.value.size >= CIRCLE_FRIEND_LIMIT) {
                        emit(
                            HomeEvent.ShowMessage(
                                "Circle is full ($CIRCLE_FRIEND_LIMIT/$CIRCLE_FRIEND_LIMIT). Remove someone first.",
                            ),
                        )
                    } else {
                        preferences.addCirclePin(row.pin)
                    }
                }
            }
            is AccountPanelRow.RemoveFromCircle -> {
                viewModelScope.launch { preferences.removeCirclePin(row.pin) }
            }
            AccountPanelRow.SteamConfigure -> {
                collapseHeroPanels()
                emit(HomeEvent.OpenSettings)
            }
            AccountPanelRow.EnableNotificationAccess -> openNotificationListenerSettings()
            is AccountPanelRow.Conversation -> activateConversation(row.key)
            is AccountPanelRow.ConversationReplySend -> sendConversationReply(row.conversationKey)
            is AccountPanelRow.SteamFriend -> {
                if (managingCircle.value) {
                    viewModelScope.launch {
                        preferences.addCirclePin(CirclePin(CirclePinSource.Steam, row.steamId))
                    }
                } else {
                    openSteamFriendConversation(row.steamId, state.socialMenu)
                }
            }
            is AccountPanelRow.DiscordFriend -> {
                if (managingCircle.value) {
                    viewModelScope.launch {
                        preferences.addCirclePin(CirclePin(CirclePinSource.Discord, row.userId))
                    }
                } else {
                    openDiscordFriendConversation(row.userId, state.socialMenu)
                }
            }
            AccountPanelRow.DiscordConnect -> {
                collapseHeroPanels()
                val capability = discordRichPresence.state.value.capability
                when (capability) {
                    DiscordPresenceCapability.NeedsAccountLink,
                    DiscordPresenceCapability.NeedsDiscordApp,
                    DiscordPresenceCapability.Connected,
                    DiscordPresenceCapability.Failed,
                    -> emit(HomeEvent.LinkDiscordAccount)
                    DiscordPresenceCapability.SdkMissing,
                    DiscordPresenceCapability.NotConfigured,
                    -> emit(HomeEvent.OpenSettings)
                }
            }
            AccountPanelRow.DiscordOpenApp -> openDiscord()
            AccountPanelRow.DiscordDmSend -> {
                discordRichPresence.sendDm()
            }
            AccountPanelRow.DiscordDmClose -> {
                discordRichPresence.closeDm()
                accountPanelSelectedIndex.value = 0
            }
            AccountPanelRow.XoraNetworkSignIn -> {
                collapseHeroPanels()
                openDashboardRung()
            }
            is AccountPanelRow.XoraFriend -> {
                if (managingCircle.value) {
                    viewModelScope.launch {
                        preferences.addCirclePin(CirclePin(CirclePinSource.XoraNetwork, row.username))
                    }
                } else {
                    openXoraFriendConversation(row.username)
                }
            }
            AccountPanelRow.XoraDmSend -> {
                viewModelScope.launch {
                    xoraNetwork.sendDirectMessage().onFailure { error ->
                        emit(HomeEvent.ShowError(error.message ?: "Couldn't send that message."))
                    }
                }
            }
            AccountPanelRow.XoraDmClose -> closeOpenXoraDm()
        }
    }

    fun updateFriendSearchQuery(query: String) {
        noteUserActivity()
        friendSearchQuery.value = query
    }

    fun updateConversationReplyDraft(draft: String) {
        noteUserActivity()
        if (discordRichPresence.dmThread.value.peerUserId != null) {
            discordRichPresence.updateDmDraft(draft)
            return
        }
        if (xoraNetwork.state.value.dm.isOpen) {
            xoraNetwork.updateDirectMessageDraft(draft)
            return
        }
        conversationReply.update { current ->
            if (current.conversationKey == null) current else current.copy(draft = draft)
        }
    }

    private fun handleDiscordDmBack() {
        val thread = discordRichPresence.dmThread.value
        if (thread.draft.isNotBlank()) {
            discordRichPresence.updateDmDraft("")
        } else {
            closeOpenDiscordDm()
        }
    }

    fun sendOpenDiscordDm() {
        noteUserActivity()
        viewModelScope.launch { discordRichPresence.sendDm() }
    }

    fun closeOpenDiscordDm() {
        noteUserActivity()
        discordRichPresence.closeDm()
        accountPanelSelectedIndex.value = 0
    }

    private fun clearConversationReply() {
        conversationReply.value = ConversationReplyUiState()
    }

    private fun activateConversation(key: String) {
        val state = uiState.value
        val convo = state.socialMenu.conversation(key) ?: return
        val canReply = convo.canReply && conversationRepository.canReplyNow(key)
        if (canReply) {
            val replyState = ConversationReplyUiState(conversationKey = key, draft = "")
            conversationReply.value = replyState
            val rows = buildAccountPanelRows(
                tab = socialMenuTab.value,
                steam = steamFriendsUi.value,
                discord = discordSocialUi.value,
                conversations = conversationsUi.value,
                reply = replyState,
                discordDm = discordRichPresence.dmThread.value,
                circlePins = circlePins.value,
                managingCircle = managingCircle.value,
                friendSearchQuery = friendSearchQuery.value,
                xoraNetwork = xoraNetwork.state.value,
            )
            val sendIndex = rows.indexOfFirst {
                it is AccountPanelRow.ConversationReplySend && it.conversationKey == key
            }
            if (sendIndex >= 0) {
                accountPanelSelectedIndex.value = sendIndex
            }
            emit(HomeEvent.ShowMessage("Type a reply, then press A on Send."))
            return
        }
        openConversationTarget(convo, state.socialMenu.steam)
    }

    private fun sendConversationReply(key: String) {
        val draft = conversationReply.value.draft
        if (draft.isBlank()) {
            emit(HomeEvent.ShowMessage("Enter a reply first."))
            return
        }
        if (conversationRepository.reply(key, draft)) {
            clearConversationReply()
            emit(HomeEvent.ShowMessage("Reply sent."))
            return
        }
        val convo = uiState.value.socialMenu.conversation(key)
        clearConversationReply()
        if (convo != null) {
            openConversationTarget(convo, uiState.value.socialMenu.steam)
            emit(HomeEvent.ShowMessage("Reply unavailable — opened ${convo.appLabel}."))
        } else {
            emit(HomeEvent.ShowError("Could not send reply."))
        }
    }

    private fun openConversationTarget(
        convo: NotificationConversation,
        steam: SteamFriendsUiState,
    ) {
        if (convo.source == ConversationSource.Steam) {
            val steamId = convo.steamIdHint
                ?: steam.friends.firstOrNull {
                    it.displayName.equals(convo.title, ignoreCase = true)
                }?.steamId
            if (!steamId.isNullOrBlank()) {
                if (openExternalUrl("steam://friends/message/$steamId")) return
                if (openExternalUrl("https://steamcommunity.com/profiles/$steamId")) return
            }
        }
        if (convo.source == ConversationSource.Discord) {
            if (conversationRepository.openApp(convo.packageName)) return
            if (openExternalUrl("discord://")) return
            if (openExternalUrl("https://discord.com/channels/@me")) return
        }
        if (conversationRepository.openApp(convo.packageName)) return
        emit(HomeEvent.ShowError("Could not open ${convo.appLabel}."))
    }

    private fun openCircleMemberConversation(pin: CirclePin, social: SocialMenuUiState) {
        when (pin.source) {
            CirclePinSource.Steam -> openSteamFriendConversation(pin.id, social)
            CirclePinSource.Discord -> openDiscordFriendConversation(pin.id, social)
            CirclePinSource.XoraNetwork -> openXoraFriendConversation(pin.id)
        }
    }

    private fun openXoraFriendConversation(username: String) {
        socialMenuTab.value = SocialMenuTab.XoraNetwork
        managingCircle.value = false
        viewModelScope.launch {
            xoraNetwork.openDirectMessage(username).onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Couldn't open that conversation."))
            }
            accountPanelSelectedIndex.value = 0
        }
    }

    fun closeOpenXoraDm() {
        noteUserActivity()
        xoraNetwork.closeDirectMessage()
        accountPanelSelectedIndex.value = 0
    }

    private fun openSteamFriendConversation(steamId: String, social: SocialMenuUiState) {
        val friend = social.steam.friends.firstOrNull { it.steamId == steamId }
        val matchingConvo = social.conversations.steamConversations.firstOrNull { convo ->
            convo.steamIdHint == steamId ||
                (friend != null && convo.title.equals(friend.displayName, ignoreCase = true))
        }
        if (matchingConvo != null) {
            activateConversation(matchingConvo.key)
            return
        }
        if (openExternalUrl("steam://friends/message/$steamId")) return
        val profile = friend?.profileUrl ?: "https://steamcommunity.com/profiles/$steamId"
        if (openExternalUrl(profile)) return
        emit(HomeEvent.ShowError("Could not open Steam chat."))
    }

    private fun openDiscordFriendConversation(userId: String, social: SocialMenuUiState) {
        val friend = social.discord.friends.firstOrNull { it.userId == userId }
        val presence = discordRichPresence.state.value
        val sdkReady = presence.capability == DiscordPresenceCapability.Connected &&
            presence.nativeBridgeLoaded
        if (sdkReady) {
            socialMenuTab.value = SocialMenuTab.Discord
            managingCircle.value = false
            // Open the DM first, then collapse Social. Chat lives in DiscordConversationWindow
            // (not inside the pill), so collapsing must never call closeDm().
            discordRichPresence.openDm(
                userId = userId,
                displayName = friend?.displayName ?: userId,
                avatarUrl = friend?.avatarUrl,
            )
            playNavCloseIfHeroPanelOpen()
            accountPanelExpanded.value = false
            emit(HomeEvent.ShowMessage("Conversation open · A send · B close"))
            return
        }
        val matchingConvo = social.conversations.discordConversations.firstOrNull { convo ->
            friend != null && convo.title.equals(friend.displayName, ignoreCase = true)
        }
        if (matchingConvo != null) {
            activateConversation(matchingConvo.key)
            return
        }
        // SDK missing / not Connected — deep-link into the Discord app.
        if (openExternalUrl("discord://discord.com/users/$userId")) return
        if (openExternalUrl("https://discord.com/users/$userId")) return
        if (openExternalUrl("discord://")) return
        emit(HomeEvent.ShowError("Could not open Discord conversation."))
    }

    private fun openNotificationListenerSettings() {
        conversationRepository.refreshListenerEnabled()
        val intent = conversationRepository.notificationListenerSettingsIntent()
        runCatching { appContext.startActivity(intent) }
            .onFailure {
                emit(HomeEvent.ShowError("Could not open notification access settings."))
            }
    }

    private fun enrichSteamHints(
        state: ConversationsUiState,
        steam: SteamFriendsUiState,
    ): ConversationsUiState {
        if (steam.friends.isEmpty()) return state
        val enriched = state.conversations.map { convo ->
            if (convo.source != ConversationSource.Steam || !convo.steamIdHint.isNullOrBlank()) {
                convo
            } else {
                val match = steam.friends.firstOrNull {
                    it.displayName.equals(convo.title, ignoreCase = true)
                }
                if (match != null) convo.copy(steamIdHint = match.steamId) else convo
            }
        }
        return if (enriched == state.conversations) state else state.copy(conversations = enriched)
    }

    private fun openDiscord() {
        val configured = discordSocialUi.value.settings.openUrl.trim()
        val candidates = buildList {
            if (configured.isNotBlank()) add(configured)
            add("discord://")
            add("https://discord.com/app")
        }
        for (url in candidates) {
            if (openExternalUrl(url)) return
        }
        emit(HomeEvent.ShowError("Could not open Discord."))
    }

    /** Called from the Activity when [HomeEvent.LinkDiscordAccount] is handled. */
    fun linkDiscordAccount(activity: android.app.Activity) {
        noteUserActivity()
        discordRichPresence.startAccountLinking(activity)
        emit(HomeEvent.ShowMessage("Opening Discord account linking…"))
    }

    private fun openExternalUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun refreshSteamFriends() {
        val credentials = steamFriendsUi.value.credentials
        if (!credentials.isConfigured) {
            steamFriendsUi.update {
                it.copy(isLoading = false, friends = emptyList(), error = null)
            }
            return
        }
        viewModelScope.launch {
            steamFriendsUi.update { it.copy(isLoading = true, error = null) }
            val result = steamWebApiClient.fetchFriends(credentials.apiKey, credentials.steamId64)
            steamFriendsUi.update { current ->
                result.fold(
                    onSuccess = { summaries ->
                        current.copy(
                            isLoading = false,
                            error = null,
                            friends = summaries
                                .take(MAX_STEAM_FRIENDS)
                                .map { summary ->
                                SteamFriendEntry(
                                    steamId = summary.steamId,
                                    displayName = summary.displayName,
                                    avatarUrl = summary.avatarUrl,
                                    presence = steamPersonaToPresence(
                                        summary.personaState,
                                        inGame = !summary.currentGame.isNullOrBlank(),
                                    ),
                                    currentGame = summary.currentGame,
                                    profileUrl = summary.profileUrl,
                                )
                            },
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            friends = emptyList(),
                            error = error.message ?: "Could not load Steam friends.",
                        )
                    },
                )
            }
            conversationsUi.update { enrichSteamHints(it, steamFriendsUi.value) }
            if (result.isSuccess) {
                emitSteamFriendOnlineBanners(steamFriendsUi.value.friends)
            }
        }
    }

    private fun onRaLibraryNavAction(action: NavAction) {
        val detailOpen = raLibraryUi.value.gameDetailOpen
        when (action) {
            NavAction.Up -> if (detailOpen) moveRaCheevoSelection(0, -1) else moveRaLibrarySelection(-1)
            NavAction.Down -> if (detailOpen) moveRaCheevoSelection(0, 1) else moveRaLibrarySelection(1)
            NavAction.Left -> if (detailOpen) moveRaCheevoSelection(-1, 0) else cycleRaLibraryTab(-1)
            NavAction.Right -> if (detailOpen) moveRaCheevoSelection(1, 0) else cycleRaLibraryTab(1)
            NavAction.PreviousPlatform -> cycleRaLibraryPlatform(-1)
            NavAction.NextPlatform -> cycleRaLibraryPlatform(1)
            NavAction.Confirm -> activateRaLibrarySelection()
            NavAction.Cancel -> if (detailOpen) closeRaGameDetail() else closeRaLibrary()
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
            NavAction.SwapScreens -> swapScreenRoles()
            else -> Unit
        }
    }

    private fun onGuideNavAction(action: NavAction) {
        when (action) {
            NavAction.Up -> moveGuideSelection(-1)
            NavAction.Down -> moveGuideSelection(1)
            NavAction.Confirm -> activateGuideSelection()
            NavAction.Cancel -> closeGuide()
            // Absorb everything else so LB/RB, Start, Select, etc. do not leak through.
            else -> Unit
        }
    }

    private fun onGameSelectorNavAction(action: NavAction, state: HomeUiState) {
        // Dual / horizontal XMB: Left/Right = games, Up/Down = systems.
        // Single / vertical XMB: Up/Down = games, Left/Right = systems.
        // LB/RB still switch Home pages (RSS / Games) via onNavAction.
        val vertical = state.displayMode == DisplayMode.Single
        when (action) {
            NavAction.Left -> if (vertical) cycleTab(-1) else moveSelection(-1)
            NavAction.Right -> if (vertical) cycleTab(1) else moveSelection(1)
            NavAction.Up -> if (vertical) moveSelection(-1) else cycleTab(-1)
            NavAction.Down -> if (vertical) moveSelection(1) else cycleTab(1)
            NavAction.Confirm -> launchSelected()
            NavAction.Cancel -> {
                if (state.anyHeroPanelExpanded) collapseHeroPanels()
                else setHomePage(HomePage.Home)
            }
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
            NavAction.Options -> state.selectedGame?.let {
                emit(HomeEvent.OpenGameOptions(it.id))
            }
            NavAction.ScrapeMenu -> state.selectedGame?.let {
                romSaveRefresh.update { tick -> tick + 1 }
                emit(HomeEvent.OpenScrapeMenu(it.id))
            }
            NavAction.ToggleFavorite -> toggleFavorite()
            NavAction.SwapScreens -> swapScreenRoles()
            NavAction.PreviousPlatform, NavAction.NextPlatform, NavAction.ToggleGuide, NavAction.Menu -> Unit
        }
    }

    private fun onRssNavAction(action: NavAction, state: HomeUiState) {
        val columns = state.gridColumns.coerceIn(2, 6)
        when (action) {
            NavAction.Left -> moveRssSelection(-1)
            NavAction.Right -> moveRssSelection(1)
            NavAction.Up -> moveRssSelection(-columns)
            NavAction.Down -> moveRssSelection(columns)
            NavAction.Confirm -> openSelectedRssItem()
            NavAction.Cancel -> {
                if (state.anyHeroPanelExpanded) collapseHeroPanels()
                else setHomePage(HomePage.Home)
            }
            NavAction.ToggleAccountPanel -> toggleAccountPanel()
            NavAction.ToggleSystemPanel -> toggleSystemPanel()
            NavAction.ToggleAchievementsPanel -> toggleAchievementsPanel()
            NavAction.Options -> Unit
            NavAction.ScrapeMenu -> Unit
            NavAction.ToggleFavorite -> Unit
            NavAction.SwapScreens -> swapScreenRoles()
            NavAction.PreviousPlatform, NavAction.NextPlatform, NavAction.ToggleGuide, NavAction.Menu -> Unit
        }
    }

    fun toggleGuide() {
        if (guideOpen.value) closeGuide() else openGuide()
    }

    fun openGuide() {
        noteUserActivity()
        collapseHeroPanels()
        guideSelectedIndex.value = 0
        guideOpen.value = true
        gamepadDispatcher.guideOpen = true
        if (steamFriendsUi.value.isConfigured) refreshSteamFriends()
        emit(HomeEvent.BringShellToFront)
    }

    fun closeGuide() {
        if (!guideOpen.value) return
        noteUserActivity()
        guideOpen.value = false
        gamepadDispatcher.guideOpen = false
    }

    fun toggleStartSettings() {
        if (startSettingsOpen.value) closeStartSettings() else openStartSettings()
    }

    fun openStartSettings(category: StartSettingsCategory = StartSettingsCategory.Display) {
        noteUserActivity()
        collapseHeroPanels()
        if (guideOpen.value) closeGuide()
        startSettingsCategory.value = category
        startSettingsRowIndex.value = 0
        startSettingsOpen.value = true
        gamepadDispatcher.startSettingsOpen = true
    }

    fun closeStartSettings() {
        if (!startSettingsOpen.value) return
        noteUserActivity()
        startSettingsOpen.value = false
        gamepadDispatcher.startSettingsOpen = false
    }

    fun selectStartSettingsCategory(category: StartSettingsCategory) {
        noteUserActivity()
        if (startSettingsCategory.value == category) return
        startSettingsCategory.value = category
        startSettingsRowIndex.value = 0
    }

    fun selectStartSettingsRow(index: Int) {
        noteUserActivity()
        val last = (uiState.value.startSettings.rows.size - 1).coerceAtLeast(0)
        startSettingsRowIndex.value = index.coerceIn(0, last)
    }

    fun activateStartSettingsSelection(index: Int? = null) {
        noteUserActivity()
        if (index != null) selectStartSettingsRow(index)
        val row = uiState.value.startSettings.selectedRow ?: return
        val action = when (row) {
            is StartSettingsRow.Action -> row.action
            is StartSettingsRow.Toggle -> row.action
            is StartSettingsRow.Header -> return
        }
        performStartSettingsAction(action)
    }

    private fun onStartSettingsNavAction(action: NavAction) {
        when (action) {
            NavAction.Up -> moveStartSettingsRow(-1)
            NavAction.Down -> moveStartSettingsRow(1)
            NavAction.Left, NavAction.PreviousPlatform ->
                selectStartSettingsCategory(startSettingsCategory.value.previous())
            NavAction.Right, NavAction.NextPlatform ->
                selectStartSettingsCategory(startSettingsCategory.value.next())
            NavAction.Confirm -> activateStartSettingsSelection()
            NavAction.Cancel, NavAction.Menu -> closeStartSettings()
            // Absorb everything else so Select, face buttons, etc. do not leak through.
            else -> Unit
        }
    }

    private fun moveStartSettingsRow(delta: Int) {
        val rows = uiState.value.startSettings.rows
        if (rows.isEmpty()) return
        var next = startSettingsRowIndex.value
        repeat(rows.size) {
            next = (next + delta).coerceIn(0, rows.lastIndex)
            if (rows[next] !is StartSettingsRow.Header) {
                startSettingsRowIndex.value = next
                return
            }
            if (next == 0 && delta < 0) return
            if (next == rows.lastIndex && delta > 0) return
        }
    }

    private fun performStartSettingsAction(action: StartSettingsAction) {
        when (action) {
            StartSettingsAction.SwitchDisplayMode -> viewModelScope.launch {
                val next = when (preferences.settings.first().displayMode) {
                    DisplayMode.Single -> DisplayMode.Dual
                    DisplayMode.Dual -> DisplayMode.Single
                }
                preferences.setDisplayMode(next)
            }
            StartSettingsAction.CycleSecondaryRole -> viewModelScope.launch {
                val current = preferences.settings.first().secondaryDisplayRole
                preferences.setSecondaryDisplayRole(current.swapped())
            }
            StartSettingsAction.CycleTrailerDisplay -> viewModelScope.launch {
                val next = when (preferences.settings.first().trailerDisplayMode) {
                    TrailerDisplayMode.InIcon -> TrailerDisplayMode.FullBackground
                    TrailerDisplayMode.FullBackground -> TrailerDisplayMode.CornerPip
                    TrailerDisplayMode.CornerPip -> TrailerDisplayMode.InIcon
                }
                preferences.setTrailerDisplayMode(next)
            }
            StartSettingsAction.CycleGameIconIdleMedia -> viewModelScope.launch {
                val next = when (preferences.settings.first().gameIconIdleMedia) {
                    GameIconIdleMedia.Trailer -> GameIconIdleMedia.Screenshot
                    GameIconIdleMedia.Screenshot -> GameIconIdleMedia.Trailer
                }
                preferences.setGameIconIdleMedia(next)
            }
            StartSettingsAction.CycleThemeMode -> viewModelScope.launch {
                val values = ThemeMode.entries
                val current = preferences.settings.first().themeMode
                val next = values[(current.ordinal + 1) % values.size]
                preferences.setThemeMode(next)
            }
            StartSettingsAction.CycleFeedColumns -> viewModelScope.launch {
                val options = listOf(2, 3, 4, 5, 6)
                val current = preferences.settings.first().gridColumns.coerceIn(2, 6)
                val idx = options.indexOf(current).coerceAtLeast(0)
                preferences.setGridColumns(options[(idx + 1) % options.size])
            }
            StartSettingsAction.CycleUiTextScale -> viewModelScope.launch {
                val options = UI_TEXT_SCALE_PRESETS
                val current = preferences.settings.first().uiTextScale
                val idx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
                    .takeIf { it >= 0 } ?: 0
                preferences.setUiTextScale(options[(idx + 1) % options.size])
            }
            StartSettingsAction.ToggleUiFitMode -> viewModelScope.launch {
                val next = when (preferences.settings.first().uiFitMode) {
                    UiFitMode.Auto -> UiFitMode.System
                    UiFitMode.System -> UiFitMode.Auto
                }
                preferences.setUiFitMode(next)
            }
            StartSettingsAction.OpenSystemDisplay -> {
                closeStartSettings()
                openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)
            }
            is StartSettingsAction.SelectShellTheme -> viewModelScope.launch {
                preferences.setShellThemeId(action.themeId)
                val name = ShellThemeCatalog.resolve(action.themeId).id.displayName
                emit(HomeEvent.ShowMessage("Theme: $name"))
            }
            StartSettingsAction.OpenThemeCustomize -> {
                closeStartSettings()
                openThemesSheet(ThemesSheetTab.Customize)
            }
            StartSettingsAction.ShopThemesComingSoon -> {
                emit(HomeEvent.ShowMessage("XOrA Store themes are coming soon"))
            }
            StartSettingsAction.UploadThemeToShopComingSoon -> {
                emit(HomeEvent.ShowMessage("Theme uploads arrive with XOrA Store"))
            }
            StartSettingsAction.CycleGamesSecondarySlot -> viewModelScope.launch {
                val next = when (preferences.settings.first().gamesSecondarySlot) {
                    "Favorite" -> "Continue"
                    else -> "Favorite"
                }
                preferences.setGamesSecondarySlot(next)
            }
            StartSettingsAction.CycleXmbTitleStyle -> viewModelScope.launch {
                val next = when (preferences.settings.first().xmbTitleStyle) {
                    XmbTitleStyle.TitleIcons -> XmbTitleStyle.Text
                    XmbTitleStyle.Text -> XmbTitleStyle.TitleIcons
                }
                preferences.setXmbTitleStyle(next)
            }
            StartSettingsAction.CycleBgmVolume -> viewModelScope.launch {
                preferences.setBgmVolume(nextVolumeStep(preferences.settings.first().bgmVolume))
            }
            StartSettingsAction.CycleUiSfxVolume -> viewModelScope.launch {
                preferences.setUiSfxVolume(nextVolumeStep(preferences.settings.first().uiSfxVolume))
            }
            StartSettingsAction.ChooseBgm -> {
                closeStartSettings()
                requestBgmPicker()
            }
            StartSettingsAction.ClearBgm -> clearCustomBgm()
            StartSettingsAction.RefreshMetadata -> refreshMetadataFromStartSettings()
            StartSettingsAction.ToggleScrapeAfterScan -> viewModelScope.launch {
                preferences.setScrapeAfterScan(!preferences.settings.first().scrapeAfterScan)
            }
            StartSettingsAction.ToggleTrailerScrape -> viewModelScope.launch {
                preferences.setTrailerScrapeEnabled(!preferences.settings.first().trailerScrapeEnabled)
            }
            StartSettingsAction.ToggleManualScrape -> viewModelScope.launch {
                val enabled = !preferences.settings.first().manualScrapeEnabled
                preferences.setManualScrapeEnabled(enabled)
                if (enabled) {
                    emit(
                        HomeEvent.ShowMessage(
                            "Manuals will download on the next scrape (ScreenScraper account required)",
                        ),
                    )
                }
            }
            StartSettingsAction.ToggleIdleTrailers -> viewModelScope.launch {
                preferences.setTrailerEnabled(!preferences.settings.first().trailerEnabled)
            }
            StartSettingsAction.CycleTrailerSource -> viewModelScope.launch {
                val values = TrailerSourcePreference.entries
                val current = preferences.settings.first().trailerSourcePreference
                val next = values[(current.ordinal + 1) % values.size]
                preferences.setTrailerSourcePreference(next)
            }
            StartSettingsAction.OpenScraperCredentials,
            StartSettingsAction.SignInRetroAchievements,
            StartSettingsAction.OpenSocialSetup,
            StartSettingsAction.OpenAllSettings,
            -> {
                closeStartSettings()
                emit(HomeEvent.OpenSettings)
            }
            StartSettingsAction.ToggleRaEnabled -> viewModelScope.launch {
                preferences.setRaEnabled(!preferences.retroAchievementsSettings.first().enabled)
            }
            StartSettingsAction.ToggleRaHardcore -> viewModelScope.launch {
                preferences.setRaHardcore(!preferences.retroAchievementsSettings.first().hardcore)
            }
            StartSettingsAction.ToggleRaShowInLauncher -> viewModelScope.launch {
                preferences.setRaShowInLauncher(
                    !preferences.retroAchievementsSettings.first().showInLauncher,
                )
            }
            StartSettingsAction.SignInWithSteam -> {
                closeStartSettings()
                requestSteamOpenId()
            }
            StartSettingsAction.OpenSocialMenu -> {
                closeStartSettings()
                if (!accountPanelExpanded.value) toggleAccountPanel()
            }
            StartSettingsAction.OpenWifiBt -> {
                closeStartSettings()
                openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
            }
            StartSettingsAction.OpenNotificationAccess -> {
                closeStartSettings()
                openNotificationListenerSettings()
            }
            StartSettingsAction.LinkDiscord -> {
                closeStartSettings()
                emit(HomeEvent.LinkDiscordAccount)
            }
            StartSettingsAction.ToggleNotifications -> viewModelScope.launch {
                val enabling = !preferences.settings.first().notificationsEnabled
                preferences.setNotificationsEnabled(enabling)
                if (enabling) {
                    shellSystemNotifier.requestPostNotificationsPermission()
                }
            }
            StartSettingsAction.ToggleNotificationSound -> viewModelScope.launch {
                preferences.setNotificationSoundEnabled(
                    !preferences.settings.first().notificationSoundEnabled,
                )
            }
            StartSettingsAction.TestNotification -> {
                // Unique id each press so dedupe does not swallow repeats.
                // force=true so Test still previews when "Show banners" is off.
                shellNotifications.emit(
                    ShellNotification.InstallComplete(
                        id = "test-notification:${SystemClock.elapsedRealtime()}",
                        title = "Test notification",
                        subtitle = "XOrA banner preview",
                    ),
                    force = true,
                )
            }
            StartSettingsAction.EditHome -> {
                closeStartSettings()
                openThemesSheet(ThemesSheetTab.Customize)
            }
            StartSettingsAction.EditProfile -> {
                closeStartSettings()
                profileEditRequest.update { it + 1 }
            }
            StartSettingsAction.ScanEmulators -> viewModelScope.launch {
                val result = runCatching { playerSeeder.scanInstalled() }.getOrElse {
                    emit(HomeEvent.ShowError(it.message ?: "Emulator scan failed."))
                    return@launch
                }
                emit(
                    HomeEvent.ShowMessage(
                        buildString {
                            append("Found ${result.installedStandalone} emulator")
                            if (result.installedStandalone != 1) append('s')
                            append(" · ${result.installedXoraCores} XOrA core")
                            if (result.installedXoraCores != 1) append('s')
                            if (result.retroArchInstalled) {
                                append(" · RetroArch · ${result.installedCores} core")
                                if (result.installedCores != 1) append('s')
                            }
                        },
                    ),
                )
            }
            StartSettingsAction.InstallLatestUpdate -> installLatestGithubBuild()
            StartSettingsAction.Reboot -> requestDevicePower(reboot = true)
            StartSettingsAction.PowerDown -> requestDevicePower(reboot = false)
        }
    }

    private fun installLatestGithubBuild() {
        if (githubReleaseUpdater.isBusy) {
            emit(HomeEvent.ShowMessage("Update already downloading…"))
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            emit(HomeEvent.RequestUnknownAppSources)
            emit(
                HomeEvent.ShowMessage(
                    "Allow XOrA to install apps, then press Update again.",
                ),
            )
            return
        }
        viewModelScope.launch {
            emit(HomeEvent.ShowMessage("Fetching latest XOrA from GitHub…"))
            val installed = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull()
            val result = githubReleaseUpdater.downloadLatest(installed)
            result.fold(
                onSuccess = { update ->
                    when (update) {
                        is GithubUpdateResult.AlreadyCurrent ->
                            emit(HomeEvent.ShowMessage("Already on the latest build (${update.versionName})."))
                        is GithubUpdateResult.Downloaded -> {
                            val uri = FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.files",
                                update.apk,
                            )
                            emit(HomeEvent.ShowMessage("Installing XOrA ${update.versionName}…"))
                            emit(HomeEvent.InstallApk(uri))
                        }
                    }
                },
                onFailure = { error ->
                    emit(
                        HomeEvent.ShowError(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "Could not download the latest build.",
                        ),
                    )
                },
            )
        }
    }

    private fun nextVolumeStep(current: Float): Float {
        val steps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val nearest = steps.minByOrNull { kotlin.math.abs(it - current) } ?: current
        val idx = steps.indexOf(nearest).coerceAtLeast(0)
        return steps[(idx + 1) % steps.size]
    }

    private fun refreshMetadataFromStartSettings() {
        viewModelScope.launch {
            val progress = scanner.scan()
            if (progress.error != null) {
                emit(HomeEvent.ShowError(progress.error ?: "Scan failed."))
                return@launch
            }
            libraryHashScheduler.enqueue(rehashAll = false, replace = false)
            scraperScheduler.enqueue(replace = true)
            emit(
                HomeEvent.ShowMessage(
                    "Scanned ${progress.gamesFound} games — hashing ROMs & fetching artwork…",
                ),
            )
        }
    }

    private fun requestDevicePower(reboot: Boolean) {
        closeStartSettings()
        val label = if (reboot) "reboot" else "power off"
        // Best-effort: handheld / system builds may allow these; otherwise guide the user.
        val attempted = runCatching {
            if (reboot) {
                val pm = appContext.getSystemService(android.os.PowerManager::class.java)
                pm?.reboot(null)
                true
            } else {
                val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN").apply {
                    putExtra("android.intent.extra.KEY_CONFIRM", false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                true
            }
        }.getOrDefault(false)
        if (!attempted) {
            emit(
                HomeEvent.ShowMessage(
                    "Could not $label from XOrA. Use the device power menu.",
                ),
            )
        }
    }

    fun selectGuideIndex(index: Int) {
        noteUserActivity()
        val size = uiState.value.guide.rows.size
        if (size == 0) return
        guideSelectedIndex.value = index.coerceIn(0, size - 1)
    }

    fun activateGuideSelection() {
        val row = uiState.value.guide.selectedRow ?: return
        when (row) {
            GuideRow.Profile -> {
                closeGuide()
                if (!accountPanelExpanded.value) toggleAccountPanel()
            }
            is GuideRow.QuickLaunch -> {
                closeGuide()
                launchGame(row.game)
            }
            is GuideRow.Friend -> {
                val url = row.profileUrl
                    ?: "https://steamcommunity.com/profiles/${row.id}"
                openExternalUrl(url)
            }
            GuideRow.Settings -> {
                closeGuide()
                openStartSettings()
            }
            GuideRow.Achievements -> {
                closeGuide()
                openRaLibrary()
            }
            GuideRow.SwapScreens -> swapScreenRoles()
            GuideRow.SignInRa -> {
                closeGuide()
                if (!achievementsPanelExpanded.value) toggleAchievementsPanel()
            }
        }
    }

    private fun moveGuideSelection(delta: Int) {
        val size = uiState.value.guide.rows.size
        if (size == 0) return
        guideSelectedIndex.update { current ->
            (current + delta).coerceIn(0, size - 1)
        }
    }

    fun setHomePage(page: HomePage) {
        noteUserActivity()
        if (page != HomePage.RaLibrary && homePage.value == HomePage.RaLibrary) {
            // Leaving via shoulders — keep prior bookmark for a later RA open.
            homePageBeforeRaLibrary = page
        }
        if (homePage.value == page) {
            if (page == HomePage.RssFeed && rssUi.value.items.isEmpty() && !rssUi.value.isLoading) {
                refreshRssFeed()
            }
            return
        }
        homePage.value = page
        if (page == HomePage.RssFeed && rssUi.value.items.isEmpty()) {
            refreshRssFeed()
        }
    }

    fun openRaLibrary() {
        noteUserActivity()
        val raPrefs = raSettingsState.value
        if (!raPrefs.enabled || !raPrefs.showInLauncher) {
            viewModelScope.launch {
                emit(
                    HomeEvent.ShowError(
                        if (!raPrefs.enabled) {
                            "RetroAchievements is disabled in Settings."
                        } else {
                            "Show RA in launcher is off — enable it in Start → Scrape or Setup."
                        },
                    ),
                )
            }
            return
        }
        collapseHeroPanels()
        if (homePage.value != HomePage.Home) {
            homePageBeforeRaLibrary = homePage.value
            homePage.value = HomePage.Home
        }
        if (xoraDepth.value != XoraXmbDepth.RaLibrary) {
            rememberXoraFolder()
            xoraDepth.value = XoraXmbDepth.RaLibrary
        }
        refreshRaLibrary()
    }

    fun closeRaLibrary() {
        noteUserActivity()
        if (xoraDepth.value == XoraXmbDepth.RaLibrary) {
            drillOutXora()
            return
        }
        if (homePage.value != HomePage.RaLibrary) return
        homePage.value = homePageBeforeRaLibrary.takeUnless { it == HomePage.RaLibrary }
            ?: HomePage.Home
    }

    fun selectRaLibraryIndex(index: Int) {
        noteUserActivity()
        raLibraryUi.update { current ->
            val size = current.visibleGames.size
            if (size == 0) current
            else current.copy(selectedIndex = index.coerceIn(0, size - 1))
        }
    }

    fun selectRaLibraryTab(tab: RaLibraryTab) {
        noteUserActivity()
        closeRaGameDetail()
        raLibraryUi.update { it.copy(tab = tab, selectedIndex = 0) }
    }

    fun selectRaPlatformFilter(platform: String?) {
        noteUserActivity()
        closeRaGameDetail()
        raLibraryUi.update { it.copy(platformFilter = platform, selectedIndex = 0) }
    }

    fun selectRaCheevoIndex(index: Int) {
        noteUserActivity()
        raLibraryUi.update { current ->
            val last = (current.gameDetail?.achievements?.size ?: 1) - 1
            if (last < 0) current
            else current.copy(cheevoIndex = index.coerceIn(0, last))
        }
    }

    fun closeRaGameDetail() {
        raGameDetailJob?.cancel()
        raLibraryUi.update {
            it.copy(
                gameDetail = null,
                gameDetailLoading = false,
                gameDetailError = null,
                cheevoIndex = 0,
            )
        }
    }

    fun activateRaLibrarySelection() {
        val row = uiState.value.raLibrary.selectedGame ?: return
        if (uiState.value.raLibrary.gameDetailOpen &&
            uiState.value.raLibrary.gameDetail?.gameId == row.game.gameId &&
            !uiState.value.raLibrary.gameDetailLoading
        ) {
            return
        }
        openRaGameDetail(row.game.gameId)
    }

    fun refreshRaLibrary() {
        viewModelScope.launch {
            if (!retroAchievements.currentCredentials().isConfigured) {
                raLibraryUi.update {
                    it.copy(
                        isLoading = false,
                        games = emptyList(),
                        error = "Sign in to RetroAchievements to see your library.",
                    )
                }
                return@launch
            }
            raLibraryUi.update { it.copy(isLoading = true, error = null) }
            val progress = retroAchievements.fetchCompletionProgress()
            val recent = retroAchievements.fetchRecentUnlocks().getOrElse { emptyList() }
            if (recent.isNotEmpty()) emitNewRaUnlockBanners(recent)
            val badgesByTitle = recent.groupBy { it.gameTitle.lowercase() }
            raLibraryUi.update { current ->
                progress.fold(
                    onSuccess = { games ->
                        current.copy(
                            isLoading = false,
                            error = null,
                            games = games.map { game ->
                                val badges = badgesByTitle[game.title.lowercase()]
                                    .orEmpty()
                                    .map { it.badgeUrl }
                                    .distinct()
                                    .take(8)
                                RaLibraryGameRow(game = game, recentBadgeUrls = badges)
                            },
                            selectedIndex = current.selectedIndex.coerceIn(
                                0,
                                (games.size - 1).coerceAtLeast(0),
                            ),
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            error = error.message ?: "Could not load RetroAchievements library.",
                        )
                    },
                )
            }
        }
    }

    private fun moveRaLibrarySelection(delta: Int) {
        val size = uiState.value.raLibrary.visibleGames.size
        if (size == 0) return
        raLibraryUi.update { current ->
            current.copy(
                selectedIndex = (current.selectedIndex + delta).coerceIn(0, size - 1),
            )
        }
    }

    private fun cycleRaLibraryTab(delta: Int) {
        val tabs = RaLibraryTab.entries
        val current = raLibraryUi.value.tab.ordinal
        val next = ((current + delta) % tabs.size + tabs.size) % tabs.size
        selectRaLibraryTab(tabs[next])
    }

    private fun cycleRaLibraryPlatform(delta: Int) {
        val current = raLibraryUi.value
        val options = buildList {
            add(null)
            addAll(current.platforms)
        }
        if (options.size <= 1) return
        val idx = options.indexOf(current.platformFilter).let { if (it < 0) 0 else it }
        val next = ((idx + delta) % options.size + options.size) % options.size
        selectRaPlatformFilter(options[next])
    }

    private fun moveRaCheevoSelection(dx: Int, dy: Int) {
        val achievements = raLibraryUi.value.gameDetail?.achievements.orEmpty()
        if (achievements.isEmpty()) return
        val last = achievements.lastIndex
        val current = raLibraryUi.value.cheevoIndex.coerceIn(0, last)
        val next = when {
            dx != 0 -> (current + dx).coerceIn(0, last)
            dy != 0 -> (current + dy * RA_CHEEVO_GRID_COLUMNS).coerceIn(0, last)
            else -> current
        }
        selectRaCheevoIndex(next)
    }

    private fun openRaGameDetail(gameId: Int) {
        noteUserActivity()
        raGameDetailJob?.cancel()
        raLibraryUi.update {
            it.copy(
                gameDetailLoading = true,
                gameDetailError = null,
                gameDetail = it.gameDetail?.takeIf { detail -> detail.gameId == gameId },
                cheevoIndex = if (it.gameDetail?.gameId == gameId) it.cheevoIndex else 0,
            )
        }
        raGameDetailJob = viewModelScope.launch {
            val result = retroAchievements.fetchGameProgress(gameId)
            raLibraryUi.update { current ->
                result.fold(
                    onSuccess = { progress ->
                        current.copy(
                            gameDetailLoading = false,
                            gameDetail = progress,
                            gameDetailError = null,
                            cheevoIndex = current.cheevoIndex.coerceIn(
                                0,
                                (progress.achievements.size - 1).coerceAtLeast(0),
                            ),
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            gameDetailLoading = false,
                            gameDetail = null,
                            gameDetailError = error.message ?: "Could not load achievements.",
                        )
                    },
                )
            }
        }
    }

    private fun focusLocalGameForRa(raTitle: String, consoleId: Int) {
        val platformId = RaConsoleIds.platformIdFor(consoleId)
        val normalized = normalizeTitle(raTitle)
        viewModelScope.launch {
            val all = libraryRepository.observeGames().first()
            val match = all.firstOrNull { game ->
                !game.isAndroidApp &&
                    (platformId == null || game.platformId == platformId) &&
                    normalizeTitle(game.title) == normalized
            } ?: all.firstOrNull { game ->
                !game.isAndroidApp && normalizeTitle(game.title) == normalized
            } ?: all.firstOrNull { game ->
                !game.isAndroidApp &&
                    (platformId == null || game.platformId == platformId) &&
                    normalizeTitle(game.title).contains(normalized.take(12))
            }

            if (match == null) {
                emit(HomeEvent.ShowMessage("No matching game in your XOrA library."))
                return@launch
            }

            if (xoraDepth.value == XoraXmbDepth.RaLibrary) {
                drillOutXora()
            }
            homePage.value = HomePage.GameSelector
            val tabs = buildTabs(all, libraryRepository.observePlatformSummaries().first())
            val platformTab = tabs.indexOfFirst { it.platformId == match.platformId }
                .takeIf { it >= 0 }
                ?: tabs.indexOfFirst { it.kind == TabKind.All }.coerceAtLeast(0)
            val tabGames = gamesForTab(all, tabs.getOrNull(platformTab))
            val gameIndex = tabGames.indexOfFirst { it.id == match.id }.coerceAtLeast(0)
            selection.value = Selection(tabIndex = platformTab, gameIndex = gameIndex)
            if (!achievementsPanelExpanded.value) {
                toggleAchievementsPanel()
            }
        }
    }

    private fun normalizeTitle(title: String): String =
        title.lowercase()
            .removePrefix("~hack~ ")
            .removePrefix("~homebrew~ ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun selectRssItem(index: Int) {
        noteUserActivity()
        rssUi.update { current ->
            if (current.items.isEmpty()) current
            else current.copy(selectedIndex = index.coerceIn(0, current.items.lastIndex))
        }
    }

    fun openSelectedRssItem() {
        val item = uiState.value.rss.selectedItem ?: return
        val link = item.link.takeIf { it.isNotBlank() } ?: return
        noteUserActivity()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not open that article."))
            }
    }

    fun refreshRssFeed() {
        viewModelScope.launch {
            rssUi.update { it.copy(isLoading = true, error = null) }
            val result = rssFeedClient.fetch()
            rssUi.update { current ->
                result.fold(
                    onSuccess = { feed ->
                        current.copy(
                            isLoading = false,
                            items = feed.items,
                            selectedIndex = current.selectedIndex.coerceIn(
                                0,
                                (feed.items.size - 1).coerceAtLeast(0),
                            ),
                            error = null,
                            feedTitle = feed.title,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            error = error.message ?: "Could not load the feed.",
                        )
                    },
                )
            }
        }
    }

    private fun moveRssSelection(delta: Int) {
        val size = rssUi.value.items.size
        if (size == 0) return
        rssUi.update { current ->
            current.copy(selectedIndex = (current.selectedIndex + delta).coerceIn(0, size - 1))
        }
    }

    fun toggleAccountPanel() {
        noteUserActivity()
        val opening = !accountPanelExpanded.value
        accountPanelExpanded.value = opening
        if (opening) {
            systemPanelExpanded.value = false
            achievementsPanelExpanded.value = false
            socialMenuTab.value = SocialMenuTab.XoraNetwork
            managingCircle.value = false
            notificationsOpen.value = false
            friendSearchQuery.value = ""
            accountPanelSelectedIndex.value = 0
            if (steamFriendsUi.value.isConfigured) refreshSteamFriends()
            if (xoraNetwork.state.value.signedIn) {
                viewModelScope.launch { xoraNetwork.refreshFriends() }
            }
            conversationRepository.refreshListenerEnabled()
            playUiOneShot(UiOneShot.FriendsTab)
        } else {
            managingCircle.value = false
            notificationsOpen.value = false
            playUiOneShot(UiOneShot.NavClose)
        }
    }

    fun launchQuickGame(game: Game) {
        noteUserActivity()
        collapseHeroPanels()
        launchGame(game)
    }

    fun toggleSystemPanel() {
        noteUserActivity()
        if (notificationHistoryOpen.value) {
            closeNotificationHistory()
            return
        }
        val opening = !systemPanelExpanded.value
        systemPanelExpanded.value = opening
        if (opening) {
            accountPanelExpanded.value = false
            achievementsPanelExpanded.value = false
            // Focus the Notifications (bell) row first.
            systemPanelSelectedIndex.value = 0
            closeFavoritePicker()
            closeStatusEditor()
            refreshSystemPanelRaChrome()
            playUiOneShot(UiOneShot.ProfileTab)
        } else {
            closeFavoritePicker()
            closeStatusEditor()
            playUiOneShot(UiOneShot.NavClose)
        }
    }

    fun toggleAchievementsPanel() {
        noteUserActivity()
        val opening = !achievementsPanelExpanded.value
        if (opening) {
            val state = uiState.value
            if (state.homePage == HomePage.Home &&
                !state.xoraXmb.showsAchievementsCard &&
                state.homeHub.vitaShortcutLaunch?.game == null
            ) {
                return
            }
        }
        achievementsPanelExpanded.value = opening
        if (opening) {
            playNavCloseIfHeroPanelOpen()
            accountPanelExpanded.value = false
            systemPanelExpanded.value = false
            closeFavoritePicker()
            closeStatusEditor()
        }
    }

    fun collapseHeroPanels() {
        noteUserActivity()
        playNavCloseIfHeroPanelOpen()
        accountPanelExpanded.value = false
        systemPanelExpanded.value = false
        achievementsPanelExpanded.value = false
        notificationHistoryOpen.value = false
        closeFavoritePicker()
        closeStatusEditor()
    }

    private fun playNavCloseIfHeroPanelOpen() {
        if (accountPanelExpanded.value || systemPanelExpanded.value) {
            playUiOneShot(UiOneShot.NavClose)
        }
    }

    private fun playUiOneShot(shot: UiOneShot) {
        gamepadDispatcher.uiOneShotPlayer?.play(shot)
    }

    fun saveProfile(displayName: String, avatarPresetId: String) {
        viewModelScope.launch {
            preferences.setProfile(displayName, avatarPresetId)
        }
    }

    fun selectAvatarPreset(presetId: String) {
        viewModelScope.launch {
            preferences.setProfileAvatar(AvatarSource.Default, presetId = presetId)
            avatarStore.clear()
        }
    }

    fun setLocalAvatar(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val fileName = avatarStore.importFromUri(uri)
                preferences.setProfileAvatar(AvatarSource.Local, localFileName = fileName)
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import that image."))
            }
        }
    }

    fun useRaAvatar() {
        viewModelScope.launch {
            val creds = retroAchievements.currentCredentials()
            if (!creds.isConfigured) {
                emit(HomeEvent.ShowError("Sign in to RetroAchievements first."))
                return@launch
            }
            preferences.setProfileAvatar(AvatarSource.RetroAchievements)
            avatarStore.clear()
        }
    }

    fun useDiscordAvatar() {
        viewModelScope.launch {
            if (discordRichPresence.state.value.currentUserAvatarUrl.isNullOrBlank()) {
                emit(HomeEvent.ShowError("Link Discord first, then reopen this screen."))
                return@launch
            }
            preferences.setProfileAvatar(AvatarSource.Discord)
            avatarStore.clear()
        }
    }

    fun useXoraAvatar() {
        viewModelScope.launch {
            if (!xoraNetwork.state.value.signedIn) {
                emit(HomeEvent.ShowError("Sign in to XOrA Network first."))
                return@launch
            }
            applyXoraNetworkIdentity(forceAvatar = true)
            avatarStore.clear()
        }
    }

    fun setXoraPresenceMode(mode: XoraPresenceMode) {
        viewModelScope.launch {
            preferences.setXoraPresenceMode(mode.name)
            xoraNetwork.setPresenceMode(mode)
        }
    }

    /**
     * RT pill identity follows the signed-in XOrA Network username + avatar. [forceAvatar] is true
     * on a fresh sign-in/register; restore only overwrites the default / already-XOrA avatar so a
     * user-picked RA / Discord / photo stays put.
     */
    private suspend fun applyXoraNetworkIdentity(forceAvatar: Boolean) {
        val account = xoraNetwork.state.value.account ?: return
        val username = account.username.trim().ifBlank { return }
        val current = preferences.profile.first()
        preferences.setProfile(username.take(24), current.avatarPresetId)
        val shouldSetAvatar = forceAvatar ||
            current.avatarSource == AvatarSource.Default ||
            current.avatarSource == AvatarSource.XoraNetwork
        if (shouldSetAvatar) {
            preferences.setProfileAvatar(AvatarSource.XoraNetwork)
        }
    }

    private suspend fun clearXoraNetworkIdentityIfNeeded() {
        val current = preferences.profile.first()
        if (current.avatarSource == AvatarSource.XoraNetwork) {
            preferences.setProfileAvatar(AvatarSource.Default)
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            preferences.setProfileAvatar(AvatarSource.Default)
            avatarStore.clear()
        }
    }

    private fun resolveAvatarModel(
        profile: LocalProfile,
        raUsername: String?,
        discordAvatarUrl: String?,
        xoraAvatarUrl: String?,
    ): String? =
        when (profile.avatarSource) {
            AvatarSource.Default -> null
            AvatarSource.Local ->
                avatarStore.resolveFile(profile.localAvatarFileName)?.absolutePath
            AvatarSource.RetroAchievements ->
                raUsername?.takeIf { it.isNotBlank() }?.let(RaProfile::userPicUrlFor)
            AvatarSource.Discord -> discordAvatarUrl?.takeIf { it.isNotBlank() }
            AvatarSource.XoraNetwork -> xoraAvatarUrl?.takeIf { it.isNotBlank() }
        }

    fun selectAchievementsTab(tab: AchievementsPaneTab) {
        achievementsUi.update { it.copy(tab = tab, error = null) }
    }

    fun loginRetroAchievements(username: String, password: String) {
        viewModelScope.launch {
            achievementsUi.update {
                it.copy(
                    isLoggingIn = true,
                    error = null,
                    needsLogin = false,
                    pendingWebApiUsername = null,
                )
            }
            val result = retroAchievements.loginWithPassword(username, password)
            achievementsUi.update {
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is RaPasswordLoginResult.SignedIn ->
                                it.copy(
                                    isLoggingIn = false,
                                    needsLogin = false,
                                    pendingWebApiUsername = null,
                                    profile = outcome.profile,
                                    error = null,
                                )
                            is RaPasswordLoginResult.NeedsWebApiKey ->
                                it.copy(
                                    isLoggingIn = false,
                                    // Connect token is already stored — emulator can run.
                                    // Still prompt for Web API key for launcher library.
                                    needsLogin = false,
                                    pendingWebApiUsername = outcome.username,
                                    error = null,
                                )
                        }
                    },
                    onFailure = { error ->
                        it.copy(
                            isLoggingIn = false,
                            needsLogin = true,
                            pendingWebApiUsername = null,
                            error = RetroAchievementsClient.sanitizeErrorMessage(
                                error.message ?: "Invalid RetroAchievements credentials.",
                            ),
                        )
                    },
                )
            }
            if (result.getOrNull() is RaPasswordLoginResult.SignedIn) {
                val creds = retroAchievements.currentCredentials()
                achievementsUi.update {
                    it.copy(credentials = creds, needsLogin = !creds.isConfigured)
                }
            }
        }
    }

    fun loginRetroAchievementsWithApiKey(username: String, apiKey: String) {
        viewModelScope.launch {
            achievementsUi.update {
                it.copy(isLoggingIn = true, error = null, needsLogin = false)
            }
            val result = retroAchievements.saveCredentials(username, apiKey)
            achievementsUi.update {
                result.fold(
                    onSuccess = { profile ->
                        it.copy(
                            isLoggingIn = false,
                            needsLogin = false,
                            pendingWebApiUsername = null,
                            profile = profile,
                            credentials = it.credentials.copy(
                                username = profile.username,
                                apiKey = apiKey.trim(),
                            ),
                            error = null,
                        )
                    },
                    onFailure = { error ->
                        it.copy(
                            isLoggingIn = false,
                            needsLogin = true,
                            error = RetroAchievementsClient.sanitizeErrorMessage(
                                error.message ?: "Invalid RetroAchievements credentials.",
                            ),
                        )
                    },
                )
            }
        }
    }

    fun signOutRetroAchievements() {
        viewModelScope.launch {
            retroAchievements.clearCredentials()
            achievementsUi.value = AchievementsUiState(needsLogin = true)
        }
    }

    /**
     * Loads RA profile points + recent unlocks for the RT profile menu without opening the X pill.
     */
    private fun refreshSystemPanelRaChrome() {
        viewModelScope.launch {
            if (!retroAchievements.currentCredentials().isConfigured) return@launch
            runCatching {
                withTimeout(ACHIEVEMENTS_LOAD_TIMEOUT_MS) {
                    val profile = retroAchievements.fetchProfile().getOrNull()
                    val recent = retroAchievements.fetchRecentUnlocks().getOrElse { emptyList() }
                    achievementsUi.update { current ->
                        current.copy(
                            profile = profile ?: current.profile,
                            recent = recent.ifEmpty { current.recent },
                            needsLogin = false,
                            error = null,
                        )
                    }
                    if (recent.isNotEmpty()) emitNewRaUnlockBanners(recent)
                }
            }
        }
    }

    private suspend fun refreshAchievements(
        gameId: String?,
        tab: AchievementsPaneTab,
        signedIn: Boolean,
    ) {
        if (!signedIn) {
            achievementsUi.update {
                it.copy(
                    isLoading = false,
                    needsLogin = true,
                    profile = null,
                    gameLookup = null,
                    recent = emptyList(),
                    error = null,
                )
            }
            return
        }

        achievementsUi.update { it.copy(isLoading = true, error = null, needsLogin = false) }

        try {
            withTimeout(ACHIEVEMENTS_LOAD_TIMEOUT_MS) {
                val profile = retroAchievements.fetchProfile().getOrElse { error ->
                    achievementsUi.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = true,
                            error = error.message ?: "Could not reach RetroAchievements.",
                        )
                    }
                    return@withTimeout
                }

                val game = gameId?.let { libraryRepository.findById(it) }
                val gameLookup = retroAchievements.lookupSelectedGame(game)
                val recent = if (tab == AchievementsPaneTab.Recent) {
                    retroAchievements.fetchRecentUnlocks().getOrElse { emptyList() }
                } else {
                    achievementsUi.value.recent
                }

                achievementsUi.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        gameLookup = gameLookup,
                        recent = recent,
                        error = null,
                        needsLogin = false,
                    )
                }
                if (tab == AchievementsPaneTab.Recent && recent.isNotEmpty()) {
                    emitNewRaUnlockBanners(recent)
                }
            }
        } catch (_: TimeoutCancellationException) {
            achievementsUi.update {
                it.copy(
                    isLoading = false,
                    error = "Timed out talking to RetroAchievements.",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            achievementsUi.update {
                it.copy(
                    isLoading = false,
                    error = error.message ?: "Could not load achievements.",
                )
            }
        }
    }

    /** Clamps rather than wraps, so holding a direction settles at an edge instead of jumping. */
    private fun moveSelection(delta: Int) {
        val size = uiState.value.games.size
        if (size == 0) return

        selection.update { current ->
            current.copy(gameIndex = (current.gameIndex + delta).coerceIn(0, size - 1))
        }
    }

    /** Platforms wrap, because the rail is a short cycle the user is stepping through. */
    private fun cycleTab(delta: Int) {
        val count = uiState.value.tabs.size
        if (count == 0) return

        selection.update { current ->
            val next = ((current.tabIndex + delta) % count + count) % count
            Selection(tabIndex = next, gameIndex = 0)
        }
    }

    fun selectTab(index: Int) {
        noteUserActivity()
        selection.update { Selection(tabIndex = index, gameIndex = 0) }
    }

    fun selectGame(index: Int) {
        noteUserActivity()
        selection.update { it.copy(gameIndex = index) }
    }

    /** Called by the shell whenever display topology or the pane arrangement changes. */
    fun setDisplayContext(gridDisplayId: Int?, otherDisplayId: Int?) {
        this.gridDisplayId = gridDisplayId
        this.otherDisplayId = otherDisplayId
    }

    private fun detectedResolutionLabel(): String {
        val metrics = appContext.resources.displayMetrics
        val w = maxOf(metrics.widthPixels, metrics.heightPixels)
        val h = minOf(metrics.widthPixels, metrics.heightPixels)
        return if (w > 0 && h > 0) "${w}×${h}" else "Unknown"
    }

    /**
     * Resolves which screen a game should boot on, most specific setting first. A null result means
     * "no opinion", which lets Android use the default display and avoids the targeting guards
     * entirely.
     */
    private suspend fun resolveTargetDisplay(game: Game): Int? {
        val preference = game.launchDisplayPreference
            .takeIf { it != LaunchDisplayPreference.Inherit }
            ?: playerRepository.settingsFor(game.platformId)?.launchDisplayPreference
            ?: LaunchDisplayPreference.Inherit

        return when (preference) {
            LaunchDisplayPreference.Inherit -> null
            LaunchDisplayPreference.GridScreen -> gridDisplayId
            LaunchDisplayPreference.OtherScreen -> otherDisplayId
        }
    }

    fun launchSelected(targetDisplayId: Int? = null) {
        val game = uiState.value.selectedGame ?: return
        launchGame(game, targetDisplayId)
    }

    fun launchGame(game: Game, targetDisplayId: Int? = null) {
        if (isLaunching.value) return
        noteUserActivity()
        collapseHeroPanels()
        closeGuide()

        viewModelScope.launch {
            isLaunching.value = true
            try {
                // Chrome fades on [ArcadiaMotion.Launch], the wallpaper zooms after that, then
                // the wallpaper dissolves at [ArcadiaMotion.LaunchWallpaperFadeAt]. The plate
                // holds through [ArcadiaMotion.LaunchHold] before the emulator Activity takes over.
                // On failure, clearing isLaunching brings the shell back.
                val waitMs = if (appContext.isReduceMotionPreferred()) {
                    0L
                } else {
                    ArcadiaMotion.LaunchHold.toLong()
                }
                if (waitMs > 0L) delay(waitMs)

                val target = targetDisplayId ?: resolveTargetDisplay(game)
                when (val result = launcher.launch(game, target)) {
                    is LaunchResult.Launched -> {
                        // Bottom screen takes over as a companion panel for this session; the
                        // controller decides whether the game and display setup qualify.
                        gameCompanionController.onGameLaunched(
                            game = game,
                            launchDisplayId = target,
                            // The RA lookup tracks the XMB selection, so it only describes this
                            // game when the launch came from the grid rather than a shortcut.
                            raLookup = uiState.value.achievements.gameLookup
                                ?.takeIf { uiState.value.selectedGame?.id == game.id },
                        )
                        discordRichPresence.setActivity(
                            DiscordPresenceActivity.Playing(
                                gameTitle = game.title,
                                platformName = game.platform.displayName,
                            ),
                        )
                        result.displayFallbackReason?.let { emit(HomeEvent.ShowMessage(it)) }
                    }
                    is LaunchResult.NoPlayerConfigured -> emit(
                        HomeEvent.ShowError(
                            "No emulator is set up for ${result.platformName}. " +
                                "Choose one in Settings.",
                        ),
                    )
                    is LaunchResult.PlayerNotInstalled -> emit(
                        HomeEvent.ShowError(
                            if (result.player.uniqueId.startsWith("retroarch.")) {
                                val core = RetroArchCoreCatalog.byPlayerId(result.player.uniqueId)
                                    ?.label
                                    ?: "the required"
                                "RetroArch is not installed (${result.packageName}). " +
                                    "Install RetroArch and the $core core."
                            } else {
                                "${result.player.name} is not installed (${result.packageName})."
                            },
                        ),
                    )
                    is LaunchResult.UnsupportedSource -> emit(HomeEvent.ShowError(result.reason))
                    is LaunchResult.InvalidTemplate -> emit(
                        HomeEvent.ShowError("${result.player.name}: ${result.reason}"),
                    )
                    is LaunchResult.Failed -> emit(
                        HomeEvent.ShowError(
                            "Could not start ${result.player?.name ?: "the emulator"}: " +
                                result.reason,
                        ),
                    )
                }
            } finally {
                isLaunching.value = false
            }
        }
    }

    fun selectCompanionAction(action: GameCompanionAction) {
        gameCompanionController.selectAction(action)
    }

    fun activateCompanionAction() {
        gameCompanionController.openFocusedAction()
    }

    fun dismissCompanionOverlay() {
        gameCompanionController.dismissOverlay()
    }

    fun toggleFavorite() {
        val game = uiState.value.selectedGame ?: return
        viewModelScope.launch {
            libraryRepository.setFavorite(game.id, !game.favorite)
        }
    }

    fun setFavorite(gameId: String, favorite: Boolean) {
        viewModelScope.launch { libraryRepository.setFavorite(gameId, favorite) }
    }

    fun setGameHidden(gameId: String, hidden: Boolean) {
        viewModelScope.launch { preferences.setGameHidden(gameId, hidden) }
    }

    fun nudgeGameArtAlignment(gameId: String, dx: Float, dy: Float) {
        viewModelScope.launch {
            val current = preferences.gameArtAlignments.first()[gameId] ?: GameArtAlignment()
            preferences.setGameArtAlignment(gameId, current.nudged(dx, dy))
        }
    }

    fun resetGameArtAlignment(gameId: String) {
        viewModelScope.launch { preferences.setGameArtAlignment(gameId, null) }
    }

    fun nudgeWallpaperAlignment(dx: Float, dy: Float) {
        viewModelScope.launch {
            val settings = preferences.settings.first()
            val current = GameArtAlignment(settings.wallpaperAlignX, settings.wallpaperAlignY)
            preferences.setWallpaperAlignment(current.nudged(dx, dy))
        }
    }

    fun resetWallpaperAlignment() {
        viewModelScope.launch { preferences.setWallpaperAlignment(null) }
    }

    fun listSavesForGame(game: Game): List<GameSaveEntry> = gameSaveCatalog.listForGame(game)

    fun romSaveRefreshTick(): StateFlow<Int> = romSaveRefresh

    fun refreshRomSaves() {
        romSaveRefresh.update { it + 1 }
    }

    fun importSavesForGame(gameId: String) {
        viewModelScope.launch {
            val game = libraryRepository.findById(gameId) ?: return@launch
            val result = gameSaveCatalog.importExternal(game)
            refreshRomSaves()
            emit(
                HomeEvent.ShowMessage(
                    result.message ?: "No matching RetroArch / beside-ROM saves found.",
                ),
            )
        }
    }

    fun deleteSaveForGame(entry: GameSaveEntry) {
        viewModelScope.launch {
            val ok = gameSaveCatalog.delete(entry)
            refreshRomSaves()
            emit(
                if (ok) HomeEvent.ShowMessage("Deleted ${entry.fileName}")
                else HomeEvent.ShowError("Could not delete that save file."),
            )
        }
    }

    fun pickGameBoxArt(gameId: String) {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.GameBoxArt(gameId)) }
        }
    }

    fun pickGameBackground(gameId: String) {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.GameBackground(gameId)) }
        }
    }

    fun pickGameSoundBite(gameId: String) {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.GameSoundBite(gameId)) }
        }
    }

    fun pickGameIdleVideo(gameId: String) {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.GameIdleVideo(gameId)) }
        }
    }

    fun pickMusicCover(mediaId: String) {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.MusicCover(mediaId)) }
        }
    }

    fun pickMusicWallpaper(mediaId: String) {
        viewModelScope.launch {
            runCatching { mediaPickerRequests.send(HomeMediaPickerRequest.MusicWallpaper(mediaId)) }
        }
    }

    fun setGameBoxArt(gameId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = gameCustomMediaStore.importBoxArt(gameId, uri)
                libraryRepository.setBoxArtPath(gameId, path)
                emit(HomeEvent.ShowMessage("Box art updated."))
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import box art."))
            }
        }
    }

    fun setGameBackground(gameId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = gameCustomMediaStore.importBackground(gameId, uri)
                libraryRepository.setHeroImagePath(gameId, path)
                emit(HomeEvent.ShowMessage("Background updated."))
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import background."))
            }
        }
    }

    fun setGameSoundBite(gameId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val path = gameCustomMediaStore.importSoundBite(gameId, uri)
                libraryRepository.setSoundBitePath(gameId, path)
                gameSoundBitePlayer.play(path)
                emit(HomeEvent.ShowMessage("Sound bite updated."))
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import sound bite."))
            }
        }
    }

    fun clearGameBoxArt(gameId: String) {
        viewModelScope.launch {
            gameCustomMediaStore.clearBoxArt(gameId)
            libraryRepository.setBoxArtPath(gameId, null)
            emit(HomeEvent.ShowMessage("Box art cleared."))
        }
    }

    fun clearGameBackground(gameId: String) {
        viewModelScope.launch {
            gameCustomMediaStore.clearBackground(gameId)
            libraryRepository.setHeroImagePath(gameId, null)
            emit(HomeEvent.ShowMessage("Background cleared."))
        }
    }

    fun clearGameSoundBite(gameId: String) {
        viewModelScope.launch {
            gameCustomMediaStore.clearSoundBite(gameId)
            libraryRepository.setSoundBitePath(gameId, null)
            gameSoundBitePlayer.stop()
            emit(HomeEvent.ShowMessage("Sound bite cleared."))
        }
    }

    fun setGameIdleVideo(gameId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                gameCustomMediaStore.importIdleVideo(gameId, uri)
                bumpCustomMedia()
                emit(HomeEvent.ShowMessage("Idle video updated."))
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import idle video."))
            }
        }
    }

    fun clearGameIdleVideo(gameId: String) {
        viewModelScope.launch {
            gameCustomMediaStore.clearIdleVideo(gameId)
            bumpCustomMedia()
            emit(HomeEvent.ShowMessage("Idle video cleared."))
        }
    }

    fun idleVideoPath(gameId: String): String? = gameCustomMediaStore.findIdleVideo(gameId)

    fun musicCoverPath(mediaId: String): String? = gameCustomMediaStore.findBoxArt(mediaId)

    fun musicWallpaperPath(mediaId: String): String? = gameCustomMediaStore.findBackground(mediaId)

    fun setMusicCover(mediaId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                gameCustomMediaStore.importBoxArt(mediaId, uri)
                bumpCustomMedia()
                emit(HomeEvent.ShowMessage("Cover art updated."))
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import cover art."))
            }
        }
    }

    fun setMusicWallpaper(mediaId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                gameCustomMediaStore.importBackground(mediaId, uri)
                bumpCustomMedia()
                emit(HomeEvent.ShowMessage("Wallpaper updated."))
            }.onFailure { error ->
                emit(HomeEvent.ShowError(error.message ?: "Could not import wallpaper."))
            }
        }
    }

    fun clearMusicCover(mediaId: String) {
        viewModelScope.launch {
            gameCustomMediaStore.clearBoxArt(mediaId)
            bumpCustomMedia()
            emit(HomeEvent.ShowMessage("Cover art cleared."))
        }
    }

    fun clearMusicWallpaper(mediaId: String) {
        viewModelScope.launch {
            gameCustomMediaStore.clearBackground(mediaId)
            bumpCustomMedia()
            emit(HomeEvent.ShowMessage("Wallpaper cleared."))
        }
    }

    val customMediaEpochFlow: StateFlow<Int> get() = customMediaEpoch

    private fun bumpCustomMedia() {
        customMediaEpoch.update { it + 1 }
    }

    private fun overlayMusicCustomMedia(items: List<XoraXmbItem>, epoch: Int): List<XoraXmbItem> {
        if (epoch < 0) return items
        return items.map { item ->
            val mediaId = item.musicCustomMediaId() ?: return@map item
            val cover = gameCustomMediaStore.findBoxArt(mediaId)
            val wallpaper = gameCustomMediaStore.findBackground(mediaId)
            if (cover == null && wallpaper == null) item
            else item.copy(
                artPath = cover ?: item.artPath,
                heroPath = wallpaper,
            )
        }
    }

    private fun overlayGameArtAlignment(
        items: List<XoraXmbItem>,
        alignments: Map<String, GameArtAlignment>,
        continueGameId: String?,
        favoriteGameId: String?,
    ): List<XoraXmbItem> {
        if (alignments.isEmpty()) return items
        return items.map { item ->
            val gameId = when (val action = item.action) {
                is XoraXmbAction.LaunchGame -> action.gameId
                is XoraXmbAction.LaunchContinueOrFavorite ->
                    if (item.id == "favorite") favoriteGameId else continueGameId
                is XoraXmbAction.ResumeGame -> continueGameId
                else -> null
            } ?: return@map item
            val alignment = alignments[gameId] ?: return@map item
            item.copy(artAlignX = alignment.x, artAlignY = alignment.y)
        }
    }

    private fun openMusicCustomizeIfFocused(xmb: XoraXmbUiState): Boolean {
        val item = xmb.selectedItem ?: return false
        val id = item.musicCustomMediaId() ?: return false
        emit(HomeEvent.OpenMusicCustomize(id, item.title))
        return true
    }

    fun previewGameSoundBite(gameId: String) {
        viewModelScope.launch {
            val game = libraryRepository.findById(gameId)
            val path = game?.let(RomSoundBiteLocator::resolve)
            if (path.isNullOrBlank()) {
                emit(HomeEvent.ShowMessage("No sound bite set."))
            } else {
                gameSoundBitePlayer.play(path)
            }
        }
    }

    suspend fun scraperPreferenceForGame(gameId: String): ScraperPreference {
        val raw = preferences.gameScraperPreference(gameId) ?: return ScraperPreference.Auto
        return runCatching { ScraperPreference.valueOf(raw) }.getOrDefault(ScraperPreference.Auto)
    }

    suspend fun scraperPreferenceForPlatform(platformId: String): ScraperPreference {
        val raw = preferences.platformScraperPreference(platformId) ?: return ScraperPreference.Auto
        return runCatching { ScraperPreference.valueOf(raw) }.getOrDefault(ScraperPreference.Auto)
    }

    fun setGameScraperPreference(gameId: String, preference: ScraperPreference) {
        viewModelScope.launch {
            preferences.setGameScraperPreference(
                gameId,
                preference.name.takeUnless { preference == ScraperPreference.Auto },
            )
        }
    }

    fun setPlatformScraperPreference(platformId: String, preference: ScraperPreference) {
        viewModelScope.launch {
            preferences.setPlatformScraperPreference(
                platformId,
                preference.name.takeUnless { preference == ScraperPreference.Auto },
            )
        }
    }

    fun rescrapeGame(gameId: String) {
        viewModelScope.launch {
            libraryRepository.resetScrape(gameId)
            scraperScheduler.enqueue(replace = true)
            emit(HomeEvent.ShowMessage("Re-scrape queued for this game."))
        }
    }

    fun rescrapePlatform(platformId: String) {
        viewModelScope.launch {
            libraryRepository.resetScrapeForPlatform(platformId)
            scraperScheduler.enqueue(replace = true)
            emit(HomeEvent.ShowMessage("Re-scrape queued for this system."))
        }
    }

    fun setLaunchDisplayPreference(gameId: String, preference: LaunchDisplayPreference) {
        viewModelScope.launch {
            libraryRepository.setLaunchDisplayPreference(gameId, preference)
        }
    }

    fun setPlayerOverride(gameId: String, playerId: String?) {
        viewModelScope.launch { libraryRepository.setPlayerOverride(gameId, playerId) }
    }

    /** Launch profiles that claim a platform, for the per-game override picker. */
    suspend fun playersFor(platformId: String): List<Player> =
        playerRepository.getPlayers().filter { platformId in it.platformIds }

    suspend fun detectEmulatorsForPlatform(platformId: String): List<DetectedEmulator> =
        platformEmulatorDetector.detectForPlatform(platformId)

    suspend fun selectedPlatformEmulatorId(platformId: String): String? =
        platformEmulatorDetector.selectedPlayerId(platformId)

    suspend fun platformEmulatorLabel(platformId: String): String? {
        val selectedId = platformEmulatorDetector.selectedPlayerId(platformId) ?: return null
        val detected = platformEmulatorDetector.detectForPlatform(platformId)
            .firstOrNull { it.playerId == selectedId }
        if (detected != null) return detected.displayName
        return RetroArchCoreCatalog.byPlayerId(selectedId)?.let { "RetroArch · ${it.label}" }
            ?: playerRepository.findById(selectedId)?.name
    }

    fun emulatorEmptyMessage(platformId: String): String =
        platformEmulatorDetector.emptyMessage(platformId)

    fun selectPlatformEmulator(platformId: String, emulator: DetectedEmulator) {
        viewModelScope.launch {
            preferences.setPlatformEmulatorChoice(
                platformId,
                PlatformEmulatorChoice(
                    playerId = emulator.playerId,
                    packageName = emulator.packageName,
                    coreName = emulator.coreName,
                ),
            )
            playerRepository.selectPlayerForPlatform(platformId, emulator.playerId)
            emit(HomeEvent.ShowMessage("${emulator.displayName} set for this system"))
        }
    }

    fun clearPlatformEmulator(platformId: String) {
        viewModelScope.launch {
            preferences.setPlatformEmulatorChoice(platformId, null)
            playerRepository.selectPlayerForPlatform(platformId, null)
            if (platformId == "n64") {
                preferences.setN64UseMupen64PlusNext(false)
            }
        }
    }

    private fun swapScreenRoles() {
        viewModelScope.launch {
            val current = preferences.settings.first().secondaryDisplayRole
            val next = if (current == ScreenRole.Hero) ScreenRole.Grid else ScreenRole.Hero
            preferences.setSecondaryDisplayRole(next)
        }
    }

    /**
     * Called when the Activity pauses. [screenInteractive] should reflect
     * [android.os.PowerManager.isInteractive] so a screen-off wake can greet even if the
     * background duration was short.
     */
    fun onPaused(screenInteractive: Boolean) {
        backgroundedAtElapsed = SystemClock.elapsedRealtime()
        pausedWhileScreenOff = !screenInteractive
        gameCompanionController.onShellBackgrounded()
        // No foreground media service: local music left playing through sleep only burns battery.
        nowPlayingController.onShellBackgrounded()
    }

    /** Called when the shell regains focus, to record playtime and re-read permission state. */
    fun onResumed() {
        // Coming back from the emulator ends the play session, and with it the companion panel.
        gameCompanionController.onShellForegrounded()
        viewModelScope.launch { sessionTracker.settlePendingSession() }
        refreshInstalledApps()
        gamepadDispatcher.reset()
        refreshTrigger.update { it + 1 }
        viewModelScope.launch { pollRetroAchievementUnlocks() }
        if (steamFriendsUi.value.isConfigured) refreshSteamFriends()
        viewModelScope.launch { maybeShowWelcomeBack() }
    }

    fun dismissWelcomeBack() {
        if (!welcomeBackOpen.value) return
        noteUserActivity()
        welcomeBackOpen.value = false
    }

    fun skipBootIntro() {
        if (!bootIntroOpen.value) return
        bootIntroSkip.value = true
    }

    fun revealHomeAfterBoot() {
        homeIntroReveal.value = true
    }

    /** First-run Finish: play the boot clip, then reveal the XMB. */
    fun playBootIntroAfterOnboarding() {
        pendingColdStartWelcome = false
        if (welcomeBackOpen.value || bootIntroOpen.value) return
        homeIntroReveal.value = false
        bootIntroSkip.value = false
        bootIntroOpen.value = true
    }

    fun dismissBootIntro() {
        if (!bootIntroOpen.value) return
        noteUserActivity()
        bootIntroOpen.value = false
        bootIntroSkip.value = false
        homeIntroReveal.value = true
    }

    private suspend fun maybeShowWelcomeBack() {
        val onboardingDone = preferences.onboardingComplete.first()
        val screenWasOff = pausedWhileScreenOff
        backgroundedAtElapsed = null
        pausedWhileScreenOff = false

        if (!onboardingDone) return

        val coldStart = pendingColdStartWelcome
        if (pendingColdStartWelcome) pendingColdStartWelcome = false

        if (coldStart) {
            if (welcomeBackOpen.value || bootIntroOpen.value) return
            homeIntroReveal.value = false
            bootIntroSkip.value = false
            bootIntroOpen.value = true
            return
        }

        if (welcomeBackOpen.value || bootIntroOpen.value) return

        // Sleep / screen-off wake only — quick app switches stay on the XMB.
        if (screenWasOff) {
            welcomeBackOpen.value = true
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val DISCORD_PACKAGE = "com.discord"
        /** Marks Spotify ids inside the shared music rung so drilling knows which source to ask. */
        private const val SPOTIFY_ALBUM_PREFIX = "spotify:"
        /** Covers profile + hash + gameid + progress; longer than OkHttp call timeout would strand the spinner. */
        const val ACHIEVEMENTS_LOAD_TIMEOUT_MS = 60_000L
        const val GUIDE_QUICK_LAUNCH_RECENT = 5
        const val GUIDE_QUICK_LAUNCH_FAVORITES = 3
        const val INSIGHT_DEBOUNCE_MS = 380L
        /** Cap Steam friend rows/avatars so huge friend lists cannot balloon Social UI + Coil. */
        const val MAX_STEAM_FRIENDS = 200
        /** RA has no push channel — poll recent unlocks while the shell lives. */
        const val RA_UNLOCK_POLL_MS = 90_000L
        /** XOrA Network has no push channel either — poll friends + inbox while signed in. */
        const val XORA_SOCIAL_POLL_MS = 60_000L
        /** Website DMs land in `/api/notifications`; poll faster than the site's 30s bell. */
        const val XORA_INBOX_POLL_MS = 20_000L
        /** Nakama invite outbox — faster than the website inbox so session codes land quickly. */
        const val XORA_NETPLAY_INVITE_POLL_MS = 5_000L
        /** Open-conversation refresh — chat cadence, only while the DM pane is on screen. */
        const val XORA_DM_POLL_MS = 4_000L
        /** One press must not fire through two Photo Viewer layers. */
        const val PHOTO_LAYER_DEBOUNCE_MS = 250L
        /** Fullscreen photo chrome fades after this pause. */
        const val PHOTO_CONTROLS_HIDE_MS = 3_000L
        const val PHOTO_SLIDESHOW_INTERVAL_MS = 4_000L
    }

    private fun refreshInstalledApps() {
        viewModelScope.launch {
            runCatching { installedAppSync.refresh() }
        }
    }

    private fun emit(event: HomeEvent) {
        events.trySend(event)
    }

}

/** Identity of the focused ROM used to look up a hover sound bite without listing dirs every frame. */
private data class SoundBiteFocus(
    val id: String,
    val importedPath: String?,
    val romFilePath: String?,
    val title: String,
    val fileName: String,
)
