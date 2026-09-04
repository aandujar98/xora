package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.GameArtAlignment
import com.arcadia.shell.datastore.GameIconIdleMedia
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.launcher.music.MusicAlbum
import com.arcadia.shell.launcher.music.MusicTrack
import com.arcadia.shell.launcher.music.NowPlayingState
import com.arcadia.shell.launcher.photos.DevicePhoto
import com.arcadia.shell.launcher.photos.PhotoAccess
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.PlatformSummary
import com.arcadia.shell.model.ScanProgress
import com.arcadia.shell.model.ShortcutSpan

enum class TabKind { All, Favorites, Recent, Apps, Platform }

/**
 * Shell pages. Default landing is [Home] (XOrA XMB root).
 * RSS / RA / legacy GameSelector are opened from XMB drills or retained for dual layouts.
 */
enum class HomePage {
    /** Gaming / emulation news (XOrA News). */
    RssFeed,
    /** XOrA XMB home — Profiles, Settings, Games, Media, Music, Network. */
    Home,
    /** Legacy horizontal game strip (still used in dual layouts as needed). */
    GameSelector,
    /** RetroAchievements games-with-progress library. */
    RaLibrary,
}

/** Which vertical page of the Home hub is focused. */
enum class HomeHubSection {
    /** Smash-inspired shard menu. */
    ShardMenu,
    /** Customizable shortcut grid (scroll / page down from shards). */
    Shortcuts,
}

/** Focus target inside shortcut customize mode (Select to open, B to close). */
enum class ShortcutCustomizeChrome {
    /** Adjust board column count with L/R. */
    Columns,
    /** Adjust preferred row count with L/R. */
    Rows,
    /** Navigate tiles; Select cycles span, A removes / opens add. */
    Tiles,
}

/** Focusable shards on Home page 1. */
enum class HomeShard {
    Continue,
    RetroAchievements,
    Shop,
}

/** Which catalogue the add-shortcut target picker is browsing. */
enum class ShortcutPinTargetKind {
    LibraryGame,
    AndroidApp,
}

/** Pending pin type while the user chooses a tile size before the target/media step. */
enum class PendingShortcutKind {
    LibraryGame,
    AndroidApp,
    Picture,
    Gif,
}

/**
 * Controller-driven list after the user picks "Pin library game" or "Pin an Android app".
 * U/D moves [selectedIndex]; A confirms; B returns to the type chooser without adding.
 */
data class ShortcutTargetPickerUiState(
    val kind: ShortcutPinTargetKind,
    val candidates: List<Game>,
    val selectedIndex: Int = 0,
) {
    val selected: Game? get() = candidates.getOrNull(selectedIndex)
}

data class HomeHubUiState(
    val section: HomeHubSection = HomeHubSection.ShardMenu,
    val shard: HomeShard = HomeShard.Continue,
    val shortcutIndex: Int = 0,
    val shortcutsEditMode: Boolean = false,
    /** Focus inside customize chrome when [shortcutsEditMode] is true. */
    val customizeChrome: ShortcutCustomizeChrome = ShortcutCustomizeChrome.Tiles,
    /** Board columns (persisted); fewer → larger tiles. */
    val shortcutGridColumns: Int = DEFAULT_HOME_SHORTCUT_GRID_COLUMNS,
    /** Preferred visible rows (persisted); fewer → larger tiles. */
    val shortcutGridRows: Int = DEFAULT_HOME_SHORTCUT_GRID_ROWS,
    val shortcuts: List<HomeShortcut> = emptyList(),
    /**
     * Vita-style shortcut bubble tray over the XMB (Y toggles).
     * When open, D-pad/A navigate bubbles; Select edits; Y slides the tray back up.
     */
    val vitaShortcutTrayOpen: Boolean = false,
    /**
     * When true, [addShortcutOpen] only offers library games / Android apps (no picture/GIF),
     * and skips the tile-size step — used by the Vita bubble tray.
     */
    val vitaShortcutPinMode: Boolean = false,
    /** Isolated confirm page after a Vita bubble flips into the game. */
    val vitaShortcutLaunch: VitaShortcutLaunchUi? = null,
    /** Bubble currently flipping into the launch page. */
    val vitaShortcutDepartingIndex: Int? = null,
    /** Absolute path to custom wallpaper, or null for the bundled default. */
    val wallpaperPath: String? = null,
    val wallpaperAlignX: Float = 0f,
    val wallpaperAlignY: Float = 0f,
    /** Absolute path to custom BGM, or null for the bundled default. */
    val customBgmPath: String? = null,
    /** Most recently played non-app game for the Continue shard. */
    val continueGame: Game? = null,
    /** True while the Themes editor sheet is open (always hosted on the Activity window). */
    val themesOpen: Boolean = false,
    /** Tab shown when [themesOpen] is true — Customize for wallpaper/BGM, Presets for packs. */
    val themesSheetTab: ThemesSheetTab = ThemesSheetTab.Customize,
    /** True while the add-shortcut chooser is open (always hosted on the Activity window). */
    val addShortcutOpen: Boolean = false,
    /** Non-null while choosing tile size after a pin type was selected. */
    val pendingShortcutKind: PendingShortcutKind? = null,
    /** Tile size for the shortcut being added (also shown on the size step). */
    val pendingShortcutSpan: ShortcutSpan = ShortcutSpan.Default,
    /** Non-null while picking a library game or Android app to pin. */
    val shortcutTargetPicker: ShortcutTargetPickerUiState? = null,
)

/** Isolated Vita shortcut launch page — wallpaper + one game icon + title. */
data class VitaShortcutLaunchUi(
    val shortcut: HomeShortcut,
    val wallpaperPath: String?,
    val iconPath: String?,
    val game: Game? = null,
    val artAlignX: Float = 0f,
    val artAlignY: Float = 0f,
)

/** Idle trailer overlay for the hero pane. */
data class HeroTrailerState(
    val active: Boolean = false,
    /** Encoded trailer string from [com.arcadia.shell.model.TrailerRefs]. */
    val trailerUrl: String? = null,
    val displayMode: TrailerDisplayMode = TrailerDisplayMode.InIcon,
    val iconIdleMedia: GameIconIdleMedia = GameIconIdleMedia.Trailer,
    /** Screenshot / fanart paths for Game Icon idle media. */
    val screenshotPaths: List<String> = emptyList(),
)

data class LibraryTab(
    val id: String,
    val label: String,
    val kind: TabKind,
    val platformId: String? = null,
    val gameCount: Int = 0,
)

data class RssFeedItem(
    val id: String,
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: String?,
    val imageUrl: String?,
    /** Plain-text / lightly cleaned summary for the hero detail pane. */
    val description: String? = null,
    /** Direct video URL or YouTube watch/embed URL when the item includes one. */
    val videoUrl: String? = null,
)

data class RssUiState(
    val isLoading: Boolean = false,
    val items: List<RssFeedItem> = emptyList(),
    val selectedIndex: Int = 0,
    val error: String? = null,
    val feedTitle: String? = null,
) {
    val selectedItem: RssFeedItem? get() = items.getOrNull(selectedIndex)
    val isEmpty: Boolean get() = !isLoading && error == null && items.isEmpty()
}

/**
 * Focusable rows in the Guide menu (flat list for U/D navigation).
 */
sealed interface GuideRow {
    data object Profile : GuideRow
    data class QuickLaunch(val game: Game) : GuideRow
    data class Friend(
        val id: String,
        val displayName: String,
        val online: Boolean,
        val avatarUrl: String? = null,
        val profileUrl: String? = null,
        val currentGame: String? = null,
    ) : GuideRow
    data object Settings : GuideRow
    data object Achievements : GuideRow
    data object SwapScreens : GuideRow
    data object SignInRa : GuideRow
}

data class GuideUiState(
    val open: Boolean = false,
    val selectedIndex: Int = 0,
    val rows: List<GuideRow> = emptyList(),
) {
    val selectedRow: GuideRow? get() = rows.getOrNull(selectedIndex)
}

/** Selected-game detail blurbs for the lower XMB insight panel. */
data class GameInsightUiState(
    val gameId: String? = null,
    val isLoading: Boolean = false,
    val summary: String? = null,
    val summarySourceLabel: String? = null,
    val releaseYear: Int? = null,
    val developer: String? = null,
    val genre: String? = null,
    val platformLabel: String? = null,
    val speedrunBlurb: String? = null,
    val trivia: List<String> = emptyList(),
    /** Local media-cache paths for gameplay stills in the right column (up to 6). */
    val screenshotPaths: List<String> = emptyList(),
    val screenshotsLoading: Boolean = false,
) {
    val hasMainCopy: Boolean get() = !summary.isNullOrBlank()
    val hasHighlights: Boolean
        get() = releaseYear != null ||
            !developer.isNullOrBlank() ||
            !genre.isNullOrBlank() ||
            !platformLabel.isNullOrBlank() ||
            !speedrunBlurb.isNullOrBlank() ||
            trivia.isNotEmpty()
    val hasScreenshots: Boolean get() = screenshotPaths.isNotEmpty()
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val homePage: HomePage = HomePage.Home,
    val homeHub: HomeHubUiState = HomeHubUiState(),
    /** Classic XOrA XMB navigation state for [HomePage.Home]. */
    val xoraXmb: XoraXmbUiState = XoraXmbUiState(),
    /** Emulator display prefs — [XoraEmulatorSettings.aspectMode] also letterboxes the XMB. */
    val xoraEmulator: XoraEmulatorSettings = XoraEmulatorSettings(),
    val tabs: List<LibraryTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    val games: List<Game> = emptyList(),
    val selectedGameIndex: Int = 0,
    /** Game ids the user hid from library lists. */
    val hiddenGameIds: Set<String> = emptySet(),
    /** Per-game cover pan inside the Game Icon. */
    val gameArtAlignments: Map<String, GameArtAlignment> = emptyMap(),
    /** Single-screen vertical selector vs dual-screen horizontal XMB. */
    val displayMode: DisplayMode = DisplayMode.Dual,
    /** Column count for the RSS feed grid (nav math + layout). */
    val gridColumns: Int = 3,
    val scanProgress: ScanProgress = ScanProgress(),
    val hasStorageAccess: Boolean = false,
    val configuredRootCount: Int = 0,
    val platformSummaries: List<PlatformSummary> = emptyList(),
    /** Name of the emulator that will handle the current selection, when one could be resolved. */
    val resolvedPlayerName: String? = null,
    val isLaunching: Boolean = false,
    val profile: LocalProfile = LocalProfile(),
    /** Absolute file path or RetroAchievements CDN URL for the profile avatar image. */
    val profileAvatarModel: String? = null,
    val accountPanelExpanded: Boolean = false,
    val systemPanelExpanded: Boolean = false,
    /** Focus index inside the RT system menu while [systemPanelExpanded]. */
    val systemPanelSelectedIndex: Int = 0,
    /** RT bell notification history overlay. */
    val notificationHistoryOpen: Boolean = false,
    val notificationHistory: List<com.arcadia.shell.launcher.notifications.ShellNotificationHistoryItem> =
        emptyList(),
    val notificationUnreadCount: Int = 0,
    val notificationHistorySelectedIndex: Int = 0,
    /** True while a toast is occupying the Friends pill slot. */
    val activeNotificationPresent: Boolean = false,
    /** RT profile card chrome (status, favorite game, pickers). */
    val systemProfile: SystemProfileCardState = SystemProfileCardState(),
    val achievementsPanelExpanded: Boolean = false,
    val achievements: AchievementsUiState = AchievementsUiState(),
    /** What the Music category is browsing and what is playing. */
    val music: MusicUiState = MusicUiState(),
    /** Media → Photos gallery, viewer, and edit / delete flows. */
    val photos: PhotosUiState = PhotosUiState(),
    /** XOrA Network → Dashboard tile board (account, friends, games, RA). */
    val dashboard: XoraDashboardUiState = XoraDashboardUiState(),
    val trailer: HeroTrailerState = HeroTrailerState(),
    val rss: RssUiState = RssUiState(),
    val guide: GuideUiState = GuideUiState(),
    val startSettings: StartSettingsUiState = StartSettingsUiState(),
    /** Settings → Update window (check GitHub, download, install). */
    val systemUpdate: SystemUpdateUiState = SystemUpdateUiState(),
    val insight: GameInsightUiState = GameInsightUiState(),
    val raLibrary: RaLibraryUiState = RaLibraryUiState(),
    /** Recent + favourite titles for the expanded account panel quick-launch list. */
    val quickLaunchGames: List<Game> = emptyList(),
    val socialMenu: SocialMenuUiState = SocialMenuUiState(),
    val accountPanelRows: List<AccountPanelRow> = emptyList(),
    val accountPanelSelectedIndex: Int = 0,
    /** Bumped when A activates Customize profile so the pill can open the editor sheet. */
    val profileEditRequest: Int = 0,
    /** Wake/resume greeting overlay on the primary display. */
    val welcomeBackOpen: Boolean = false,
    /** Cold-start boot clip overlay (fully closed → opened). */
    val bootIntroOpen: Boolean = false,
    /** Gamepad / caller asked the boot overlay to skip to the white fade. */
    val bootIntroSkip: Boolean = false,
    /**
     * False while the boot clip is still playing so XMB icons stay hidden, then true to bounce
     * them in as the white plate fades.
     */
    val homeIntroReveal: Boolean = true,
    /** Latest online netplay invite waiting for Accept / Decline. */
    val pendingNetplayInvite: NetplayInvitePrompt? = null,
    val netplayInvitePromptOpen: Boolean = false,
) {
    val selectedGame: Game? get() = games.getOrNull(selectedGameIndex)

    val selectedTab: LibraryTab? get() = tabs.getOrNull(selectedTabIndex)

    val guideOpen: Boolean get() = guide.open

    val startSettingsOpen: Boolean get() = startSettings.open

    val systemUpdateOpen: Boolean get() = systemUpdate.open

    /**
     * True when there are neither ROM folders nor a synced Apps tab yet. Apps alone are enough to
     * leave onboarding, so the shell can act as a home screen before any library roots exist.
     */
    val needsSetup: Boolean
        get() = configuredRootCount == 0 && tabs.none { it.kind == TabKind.Apps }

    val isEmptyAfterScan: Boolean
        get() = configuredRootCount > 0 && games.isEmpty() && !scanProgress.isRunning

    val anyHeroPanelExpanded: Boolean
        get() = accountPanelExpanded || systemPanelExpanded || achievementsPanelExpanded
}

/** Join/decline copy for an online netplay invite popup. */
data class NetplayInvitePrompt(
    val hostName: String,
    val gameTitle: String,
    val sessionCode: String,
    val platformId: String = "",
    val coreName: String = "",
    val fromUsername: String = "",
)

sealed interface HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent
    data class ShowError(val message: String) : HomeEvent
    data object OpenSettings : HomeEvent
    /** Start Discord Social SDK account linking (needs a foreground Activity). */
    data object LinkDiscordAccount : HomeEvent
    /** Music browsing needs the runtime audio permission before MediaStore returns anything. */
    data class RequestAudioAccess(val permission: String) : HomeEvent
    /** Photo Viewer needs the runtime image permission before MediaStore returns anything. */
    data class RequestImageAccess(val permissions: List<String>) : HomeEvent
    /** MediaStore deletion consent — launched as an IntentSender from the primary Activity. */
    data class RequestPhotoDelete(val intentSender: android.content.IntentSender) : HomeEvent
    data class OpenGameOptions(val gameId: String) : HomeEvent
    /** Select button: ROM options (customize + saves + scrape) for [gameId]. */
    data class OpenScrapeMenu(val gameId: String) : HomeEvent
    /** Select / Options on an album or track: custom cover and wallpaper. */
    data class OpenMusicCustomize(val mediaId: String, val title: String) : HomeEvent
    /** Best-effort: reorder the shell task to the front when Guide opens. */
    data object BringShellToFront : HomeEvent
    /** Open system settings so XOrA can install the downloaded APK. */
    data object RequestUnknownAppSources : HomeEvent
    /** Launch the package installer for a FileProvider APK URI. */
    data class InstallApk(val uri: android.net.Uri) : HomeEvent
}

/**
 * One-shot media picker requests. Observed only from the primary Activity composition so
 * [androidx.activity.compose.rememberLauncherForActivityResult] never runs under a secondary
 * [android.app.Presentation] window (Activity Result is not reliable across Presentation tokens).
 */
sealed interface HomeMediaPickerRequest {
    data object ShortcutPicture : HomeMediaPickerRequest
    data object ShortcutGif : HomeMediaPickerRequest
    data object Wallpaper : HomeMediaPickerRequest
    data object Bgm : HomeMediaPickerRequest
    data object ProfileAvatar : HomeMediaPickerRequest

    /** Photo / GIF to attach to the open Discord DM. */
    data object DiscordAttachment : HomeMediaPickerRequest

    /** Banner art for a console card in the system picker. */
    data class PlatformBanner(val platformId: String) : HomeMediaPickerRequest
    data class GameBoxArt(val gameId: String) : HomeMediaPickerRequest
    data class GameBackground(val gameId: String) : HomeMediaPickerRequest
    data class GameSoundBite(val gameId: String) : HomeMediaPickerRequest
    data class GameIdleVideo(val gameId: String) : HomeMediaPickerRequest
    data class MusicCover(val mediaId: String) : HomeMediaPickerRequest
    data class MusicWallpaper(val mediaId: String) : HomeMediaPickerRequest

    /** Gallery still for the Games column Folder_IMG window. */
    data object HomeFolderImage : HomeMediaPickerRequest
}

/**
 * External browser / Custom Tab launches that must start from the primary Activity (not a
 * Presentation). Same hoist pattern as [HomeMediaPickerRequest].
 */
/** Music browsing plus the shared Now Playing state behind the pill and the player page. */
data class MusicUiState(
    val albums: List<MusicAlbum> = emptyList(),
    /** Songs for the drilled album, or every song under All music. */
    val tracks: List<MusicTrack> = emptyList(),
    val drilledAlbumId: String? = null,
    val isLoading: Boolean = false,
    /** False until the user grants audio access; the browse rungs stay empty until then. */
    val hasAudioAccess: Boolean = true,
    val nowPlaying: NowPlayingState = NowPlayingState(),
) {
    /** Cover art for whichever music rung is focused, used as the XMB backdrop. */
    val nowPlayingArtPath: String? get() = nowPlaying.track?.albumArtUri
}

/** Order of rows in the Photo Viewer's Options popup. */
enum class PhotoOption(val label: String) {
    View("View"),
    Edit("Edit"),
    MarkFavorite("Mark as Favorite"),
    Delete("Delete"),
    ShareToNetwork("Share to XOrA Network"),
}

/** Tools along the bottom of the non-destructive photo edit screen. */
enum class PhotoEditTool(val label: String) {
    RotateLeft("Rotate left"),
    RotateRight("Rotate right"),
    Crop("Crop"),
    Reset("Reset"),
    Save("Save"),
    Cancel("Cancel"),
}

/** Center-crop presets the edit screen cycles through. Null aspect = no crop. */
val PHOTO_CROP_PRESETS: List<Pair<String, Float?>> = listOf(
    "Off" to null,
    "1:1" to 1f,
    "4:3" to 4f / 3f,
    "16:9" to 16f / 9f,
)

/** Non-destructive edit session for one photo. Nothing is written until Save. */
data class PhotoEditUiState(
    val photo: DevicePhoto,
    /** Multiples of 90, applied before crop. */
    val rotationDeg: Int = 0,
    val cropIndex: Int = 0,
    val toolIndex: Int = 0,
    val saving: Boolean = false,
) {
    val cropAspect: Float? get() = PHOTO_CROP_PRESETS[cropIndex].second
    val cropLabel: String get() = PHOTO_CROP_PRESETS[cropIndex].first
}

/** Media → Photos: gallery, fullscreen viewer, slideshow, options popup, edit + delete flows. */
data class PhotosUiState(
    val photos: List<DevicePhoto> = emptyList(),
    val focusedIndex: Int = 0,
    val isLoading: Boolean = false,
    /** Null until the rung has been opened once and access was checked. */
    val access: PhotoAccess? = null,
    /** MediaStore bucket id when opened from a Folder_Photo row; null is the full library. */
    val albumFilter: String? = null,
    val albumTitle: String? = null,
    /** MediaStore ids the user favourited (persisted in preferences, never in the files). */
    val favoriteIds: Set<String> = emptySet(),
    val loadError: String? = null,
    val optionsOpen: Boolean = false,
    val optionIndex: Int = 0,
    val fullscreenOpen: Boolean = false,
    /** Fullscreen chrome fades after a pause and returns on any input. */
    val fullscreenControlsVisible: Boolean = true,
    val slideshowActive: Boolean = false,
    val deleteConfirmOpen: Boolean = false,
    /** True when the destructive button holds controller focus in the confirm dialog. */
    val deleteConfirmDeleteFocused: Boolean = false,
    val edit: PhotoEditUiState? = null,
) {
    val focusedPhoto: DevicePhoto? get() = photos.getOrNull(focusedIndex)
    val focusedIsFavorite: Boolean get() = focusedPhoto?.id?.let { it in favoriteIds } == true
    val pageCount: Int get() = if (photos.isEmpty()) 1 else (photos.size + PHOTO_PAGE_SIZE - 1) / PHOTO_PAGE_SIZE
    val currentPage: Int get() = focusedIndex / PHOTO_PAGE_SIZE
    /** Options / viewer / edit / delete sit above the gallery and must clear LT/RT chrome. */
    val chromeOverlayOpen: Boolean
        get() = optionsOpen || fullscreenOpen || deleteConfirmOpen || edit != null
}

/** 2 rows × 5 columns per gallery page, matching the concept layout. */
const val PHOTO_GRID_COLUMNS = 5
const val PHOTO_GRID_ROWS = 2
const val PHOTO_PAGE_SIZE = PHOTO_GRID_COLUMNS * PHOTO_GRID_ROWS

/** Everything the Photo Viewer pane can ask the shell to do (touch and gamepad funnel here). */
sealed interface PhotoPaneCommand {
    data class Focus(val index: Int) : PhotoPaneCommand
    data class Open(val index: Int) : PhotoPaneCommand
    data object OpenOptions : PhotoPaneCommand
    data object CloseOptions : PhotoPaneCommand
    data class FocusOption(val index: Int) : PhotoPaneCommand
    data class ActivateOption(val index: Int) : PhotoPaneCommand
    data object StartSlideshow : PhotoPaneCommand
    data object CloseViewer : PhotoPaneCommand
    data object NextPhoto : PhotoPaneCommand
    data object PreviousPhoto : PhotoPaneCommand
    data object RevealControls : PhotoPaneCommand
    data class FocusDeleteChoice(val delete: Boolean) : PhotoPaneCommand
    data object ConfirmDelete : PhotoPaneCommand
    data object CancelDelete : PhotoPaneCommand
    data class FocusEditTool(val index: Int) : PhotoPaneCommand
    data class ActivateEditTool(val index: Int) : PhotoPaneCommand
    data object RequestAccess : PhotoPaneCommand
    data object Retry : PhotoPaneCommand
    data object Back : PhotoPaneCommand
}

sealed interface HomeExternalAuthRequest {
    data object SteamOpenId : HomeExternalAuthRequest
    /** Spotify Authorization Code + PKCE (Custom Tab → sora://spotify-auth). */
    data class SpotifyOAuth(val authorizeUrl: String) : HomeExternalAuthRequest
}
