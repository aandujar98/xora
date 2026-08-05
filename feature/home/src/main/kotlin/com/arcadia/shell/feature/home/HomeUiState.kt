package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.datastore.TrailerDisplayMode
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
    /** Absolute path to custom wallpaper, or null for the bundled default. */
    val wallpaperPath: String? = null,
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

/** Idle trailer overlay for the hero pane. */
data class HeroTrailerState(
    val active: Boolean = false,
    /** Encoded trailer string from [com.arcadia.shell.model.TrailerRefs]. */
    val trailerUrl: String? = null,
    val displayMode: TrailerDisplayMode = TrailerDisplayMode.FullBackground,
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
    val tabs: List<LibraryTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    val games: List<Game> = emptyList(),
    val selectedGameIndex: Int = 0,
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
    val achievementsPanelExpanded: Boolean = false,
    val achievements: AchievementsUiState = AchievementsUiState(),
    val trailer: HeroTrailerState = HeroTrailerState(),
    val rss: RssUiState = RssUiState(),
    val guide: GuideUiState = GuideUiState(),
    val startSettings: StartSettingsUiState = StartSettingsUiState(),
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
) {
    val selectedGame: Game? get() = games.getOrNull(selectedGameIndex)

    val selectedTab: LibraryTab? get() = tabs.getOrNull(selectedTabIndex)

    val guideOpen: Boolean get() = guide.open

    val startSettingsOpen: Boolean get() = startSettings.open

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

sealed interface HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent
    data class ShowError(val message: String) : HomeEvent
    data object OpenSettings : HomeEvent
    /** Start Discord Social SDK account linking (needs a foreground Activity). */
    data object LinkDiscordAccount : HomeEvent
    data class OpenGameOptions(val gameId: String) : HomeEvent
    /** Select button: scrape-source / favourite bottom sheet for [gameId]. */
    data class OpenScrapeMenu(val gameId: String) : HomeEvent
    /** Best-effort: reorder the shell task to the front when Guide opens. */
    data object BringShellToFront : HomeEvent
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
}

/**
 * External browser / Custom Tab launches that must start from the primary Activity (not a
 * Presentation). Same hoist pattern as [HomeMediaPickerRequest].
 */
sealed interface HomeExternalAuthRequest {
    data object SteamOpenId : HomeExternalAuthRequest
}
