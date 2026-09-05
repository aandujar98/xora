package com.arcadia.shell.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import com.arcadia.shell.model.ScreenRole
import com.arcadia.shell.model.ShortcutSpan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.shellDataStore: DataStore<Preferences> by preferencesDataStore("arcadia_prefs")

/**
 * Legacy library presentation preference. The shell always uses XMB for the game selector;
 * kept only so older DataStore values still decode cleanly.
 */
@Deprecated("Library is always XMB; preference is ignored.")
enum class LibraryLayout {
    Grid,
    Xmb,
}

/**
 * Whether the shell uses a single composed layout or splits across two physical displays.
 *
 * [Dual] keeps the current Presentation + horizontal XMB behaviour when a second display exists;
 * with only one display it falls back to the stacked single-screen host.
 * [Single] always uses the composed single-screen layout (vertical game selector) and does not
 * open a secondary Presentation for the game selector.
 */
enum class DisplayMode {
    Single,
    Dual,
}

/**
 * Whether the shell should resize chrome to the detected display resolution.
 *
 * [Auto] measures the active panel and scales dp layout to fit.
 * [System] leaves Android density alone (no extra fit pass).
 */
enum class UiFitMode {
    Auto,
    System,
}

/** Shell colour scheme preference. */
enum class ThemeMode {
    /** Follow the system light/dark setting. */
    System,
    Light,
    Dark,
}

/** How an idle game trailer is shown on the hero artwork pane. */
enum class TrailerDisplayMode {
    /** Trailer replaces the focused Game Icon cover art. */
    InIcon,
    /** Trailer replaces the full hero artwork behind UI chrome. */
    FullBackground,
    /** Trailer plays in a lower-right picture-in-picture region. */
    CornerPip,
}

/** What plays inside a focused Game Icon after idle. Trailers stay the default. */
enum class GameIconIdleMedia {
    Trailer,
    Screenshot,
}

/**
 * Preferred source when resolving a game trailer URL.
 *
 * [Auto] tries ScreenScraper → IGDB/Steam app → YouTube title search → Steam title search.
 * Specific values force a single source (useful when one provider is noisy).
 */
enum class TrailerSourcePreference {
    Auto,
    YouTube,
    Steam,
    ScreenScraper,
    Igdb,
}

/** Resolves whether the shell should render its dark Material scheme. */
fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** Where the account-pill avatar image comes from. */
enum class AvatarSource {
    /** Built-in colour preset + initial letter. */
    Default,
    /** Image copied into app storage. */
    Local,
    /** RetroAchievements user profile picture. */
    RetroAchievements,
    /** Linked Discord account's profile picture. */
    Discord,
    /** Signed-in XOrA Network username + avatar. */
    XoraNetwork,
}

/** How XMB ROM rows show the game title next to box art. */
enum class XmbTitleStyle {
    /** Prefer clear-logo / wheel art when available. */
    TitleIcons,
    /** Always show the game title as text. */
    Text,
}

data class ShellSettings(
    /**
     * Which pane the secondary physical display shows. The library always owns input focus, so this
     * effectively decides which screen the user is looking at while navigating.
     */
    val secondaryDisplayRole: ScreenRole = ScreenRole.Hero,
    /** Single composed layout vs dual-display Presentation split. Defaults to Dual. */
    val displayMode: DisplayMode = DisplayMode.Dual,
    /**
     * Auto-detect the panel resolution and scale the whole UI to fit.
     * Defaults to [UiFitMode.Auto].
     */
    val uiFitMode: UiFitMode = UiFitMode.Auto,
    @Suppress("DEPRECATION")
    @Deprecated("Library is always XMB; preference is ignored.")
    val libraryLayout: LibraryLayout = LibraryLayout.Xmb,
    /** Column count for the Home RSS feed grid (and its D-pad navigation). */
    val gridColumns: Int = 3,
    val iconScale: Float = 1f,
    /**
     * Multiplier for shell / XMB text size. Presets cycle Small → Medium → Large → Extra large
     * (0.8 / 0.9 / 1.0 / 1.15). Default is Small so titles stay compact on handhelds.
     */
    val uiTextScale: Float = DEFAULT_UI_TEXT_SCALE,
    val scrapeAfterScan: Boolean = true,
    /**
     * Games XMB slot under Retro Achievements: Continue (last played) or Favorite.
     * Stored as the enum name string.
     */
    val gamesSecondarySlot: String = "Continue",
    /**
     * XMB ROM browse: clear-logo title icons vs plain text titles.
     */
    val xmbTitleStyle: XmbTitleStyle = XmbTitleStyle.TitleIcons,
    /**
     * Download each game's scanned manual during a metadata scrape, for the companion screen to
     * page through. Off by default: manuals are the largest media ScreenScraper serves, and a big
     * library would quietly pull gigabytes on the first scan of anyone who never asked for them.
     */
    val manualScrapeEnabled: Boolean = false,
    /**
     * Mirror launchable Android apps into the library so they appear on the Apps tab and can
     * be pinned. On by default; turning it off prunes the synced rows on the next sync.
     */
    val androidAppSyncEnabled: Boolean = true,
    val lastScanAt: Long = 0,
    /** Looping shell soundtrack volume in the range 0f–1f. Zero mutes. */
    val bgmVolume: Float = DEFAULT_BGM_VOLUME,
    /** Navigation / UI one-shot SFX volume in the range 0f–1f. Zero mutes. Independent of BGM. */
    val uiSfxVolume: Float = DEFAULT_UI_SFX_VOLUME,
    /** Light / dark appearance. Defaults to dark to match the classic SORA shell. */
    val themeMode: ThemeMode = ThemeMode.Dark,
    /**
     * After a short idle on Home, play a resolved game trailer over the hero artwork.
     * On by default; resolution is lazy per selected game and stays silent when none is found.
     */
    val trailerEnabled: Boolean = true,
    /**
     * When true, resolve and persist [com.arcadia.shell.model.Game.trailerUrl] during metadata
     * scrape and on idle. When false, never fetch; already-stored URLs still play if idle trailers
     * are enabled.
     */
    val trailerScrapeEnabled: Boolean = true,
    /** Which provider(s) to use when [trailerScrapeEnabled] is on. */
    val trailerSourcePreference: TrailerSourcePreference = TrailerSourcePreference.Auto,
    val trailerDisplayMode: TrailerDisplayMode = TrailerDisplayMode.InIcon,
    /** Seconds without input before an idle trailer may start. */
    val trailerIdleSeconds: Int = DEFAULT_TRAILER_IDLE_SECONDS,
    /** Idle media inside the focused Game Icon. Trailers remain the default. */
    val gameIconIdleMedia: GameIconIdleMedia = GameIconIdleMedia.Trailer,
    /**
     * Absolute path to a user-picked Home wallpaper. Null / blank uses the active theme's
     * backdrop.
     */
    val homeWallpaperPath: String? = null,
    /** Horizontal wallpaper pan (`-1` left … `1` right), same bias as cover art. */
    val wallpaperAlignX: Float = 0f,
    /** Vertical wallpaper pan (`-1` top … `1` bottom). */
    val wallpaperAlignY: Float = 0f,
    /**
     * Absolute path to a gallery still cropped into the Games column Folder_IMG window.
     * Null / blank shows the checker placeholder.
     */
    val homeFolderImagePath: String? = null,
    /**
     * Absolute path to a user-picked looping BGM file. Null / blank uses the active
     * launcher theme's asset BGM when present, otherwise the bundled default
     * soundtrack (`raw/background`).
     */
    val customBgmPath: String? = null,
    /**
     * Absolute path to the Music category's on-device library folder. Null / blank means
     * "all device music" via MediaStore.
     */
    val musicLibraryPath: String? = null,
    /**
     * Active launcher theme pack id ([com.arcadia.shell.designsystem.ShellThemeId.id]).
     * Defaults to `"default"`.
     */
    val shellThemeId: String = DEFAULT_SHELL_THEME_ID,
    /**
     * When false, shell PS-style banners are dropped before enqueue so toasts stay hidden.
     */
    val notificationsEnabled: Boolean = true,
    /** Play a short UI chime when a shell notification banner becomes visible. */
    val notificationSoundEnabled: Boolean = true,
    val discordFriendOnlineNotifications: Boolean = true,
    val steamFriendOnlineNotifications: Boolean = true,
    val xoraFriendOnlineNotifications: Boolean = true,
    /**
     * Legacy: when true and no Choose Emulator entry for N64, launch via RetroArch
     * Mupen64Plus-Next. Superseded by per-platform Choose Emulator; kept so existing
     * installs keep working until the user picks an emulator.
     */
    val n64UseMupen64PlusNext: Boolean = false,
    /**
     * When true, games marked hidden still appear in library lists (with a Hidden
     * subtitle) so they can be unhidden. Off by default.
     */
    val showHiddenGames: Boolean = false,
)

/**
 * Per-platform emulator selection from Choose Emulator (game select → Select).
 *
 * [playerId] is a [com.arcadia.shell.model.Player.uniqueId] (standalone or
 * `retroarch.*`). [coreName] is set for RetroArch cores (e.g. `mupen64plus_next`).
 */
data class PlatformEmulatorChoice(
    val playerId: String,
    val packageName: String? = null,
    val coreName: String? = null,
)

/** Local hero-pill identity. Not tied to any online account. */
data class LocalProfile(
    val displayName: String = "Player",
    /** One of the built-in preset ids (`preset_0` … `preset_5`). */
    val avatarPresetId: String = "preset_0",
    val avatarSource: AvatarSource = AvatarSource.Default,
    /** Relative file name under [ProfileAvatarStore.DIR_NAME] when [avatarSource] is [AvatarSource.Local]. */
    val localAvatarFileName: String? = null,
    /**
     * Optional custom RT-card status. Blank/null falls back to live activity
     * (“Browsing XOrA”, “Playing …”).
     */
    val customStatus: String? = null,
    /** Library game id pinned under Favorite Game on the RT card. */
    val favoriteLibraryGameId: String? = null,
    /**
     * How this device should appear on XOrA Network: Online, Away, Busy, or Invisible.
     * Only published while signed in.
     */
    val xoraPresenceMode: String = "Online",
)

data class ScraperCredentials(
    val screenScraperUser: String = "",
    val screenScraperPassword: String = "",
    /**
     * ScreenScraper additionally requires developer credentials issued to an application, which
     * cannot be bundled here. Users who have their own may supply them to enable hash lookups.
     */
    val screenScraperDevId: String = "",
    val screenScraperDevPassword: String = "",
    val steamGridDbKey: String = "",
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
) {
    val hasScreenScraper: Boolean
        get() = screenScraperUser.isNotBlank() &&
            screenScraperPassword.isNotBlank() &&
            screenScraperDevId.isNotBlank() &&
            screenScraperDevPassword.isNotBlank()
    val hasSteamGridDb: Boolean get() = steamGridDbKey.isNotBlank()
    val hasIgdb: Boolean get() = igdbClientId.isNotBlank() && igdbClientSecret.isNotBlank()
    val hasAny: Boolean get() = hasScreenScraper || hasSteamGridDb || hasIgdb
}

/** RetroAchievements credentials for launcher Web API + in-emulator rcheevos. */
data class RetroAchievementsCredentials(
    val username: String = "",
    /** Control-panel Web API key (`y=`) for profile / library HTTP. */
    val apiKey: String = "",
    /**
     * Connect API token from `login2` (`t=`). Required by rcheevos in XOrA Emulator.
     * May match [apiKey] on older accounts where the two were interchangeable.
     */
    val connectToken: String = "",
) {
    val isConfigured: Boolean
        get() = username.isNotBlank() && (apiKey.isNotBlank() || connectToken.isNotBlank())

    /** Token passed to `rc_client_begin_login_with_token`. */
    val emulatorToken: String
        get() = connectToken.ifBlank { apiKey }
}

/**
 * Steam Web API credentials for the social menu friend list.
 *
 * Chat / DMs are not available via the public Web API — only friend presence summaries.
 */
data class SteamWebApiCredentials(
    val apiKey: String = "",
    /** 17-digit SteamID64 (e.g. from steamid.io). */
    val steamId64: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && steamId64.isNotBlank()
}

/**
 * Discord social-menu hooks. Stores an optional invite / profile deep link for
 * “Open Discord”, plus a Discord Application ID for Social SDK Rich Presence and
 * in-launcher friend DMs (communication scopes). Re-link after scope upgrades.
 */
data class DiscordSocialSettings(
    /** Invite URL, profile URL, or discord:// deep link. */
    val openUrl: String = "",
    /**
     * Discord Developer Portal Application (client) ID used for Rich Presence and
     * in-launcher Discord chat. Defaults to [DEFAULT_DISCORD_APPLICATION_ID] until
     * the user clears or overrides it. Live mobile presence / DMs still require
     * Discord’s proprietary Social SDK AAR.
     */
    val applicationId: String = DEFAULT_DISCORD_APPLICATION_ID,
) {
    val hasLink: Boolean get() = openUrl.isNotBlank()
    val hasApplicationId: Boolean get() = applicationId.isNotBlank()
}

/** Public Application ID for SORA (safe to ship; never put a client secret in the app). */
const val DEFAULT_DISCORD_APPLICATION_ID = "1531690290526683176"
private const val MAX_DISMISSED_SHELL_NOTIFICATION_IDS = 400

@Singleton
class ShellPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore get() = context.shellDataStore

    @Suppress("DEPRECATION")
    val settings: Flow<ShellSettings> = dataStore.data.map { prefs ->
        ShellSettings(
            secondaryDisplayRole = prefs[Keys.SECONDARY_ROLE]
                ?.let { name -> runCatching { ScreenRole.valueOf(name) }.getOrNull() }
                ?: ScreenRole.Hero,
            displayMode = prefs[Keys.DISPLAY_MODE]
                ?.let { name -> runCatching { DisplayMode.valueOf(name) }.getOrNull() }
                ?: DisplayMode.Dual,
            uiFitMode = prefs[Keys.UI_FIT_MODE]
                ?.let { name -> runCatching { UiFitMode.valueOf(name) }.getOrNull() }
                ?: UiFitMode.Auto,
            libraryLayout = prefs[Keys.LIBRARY_LAYOUT]
                ?.let { name -> runCatching { LibraryLayout.valueOf(name) }.getOrNull() }
                ?: LibraryLayout.Xmb,
            gridColumns = prefs[Keys.GRID_COLUMNS] ?: 3,
            iconScale = prefs[Keys.ICON_SCALE] ?: 1f,
            uiTextScale = (prefs[Keys.UI_TEXT_SCALE] ?: DEFAULT_UI_TEXT_SCALE)
                .coerceIn(MIN_UI_TEXT_SCALE, MAX_UI_TEXT_SCALE),
            scrapeAfterScan = prefs[Keys.SCRAPE_AFTER_SCAN] ?: true,
            gamesSecondarySlot = prefs[Keys.GAMES_SECONDARY_SLOT] ?: "Continue",
            xmbTitleStyle = prefs[Keys.XMB_TITLE_STYLE]
                ?.let { name -> runCatching { XmbTitleStyle.valueOf(name) }.getOrNull() }
                ?: XmbTitleStyle.TitleIcons,
            manualScrapeEnabled = prefs[Keys.MANUAL_SCRAPE_ENABLED] ?: false,
            androidAppSyncEnabled = prefs[Keys.ANDROID_APP_SYNC_ENABLED] ?: true,
            lastScanAt = prefs[Keys.LAST_SCAN_AT] ?: 0,
            bgmVolume = prefs[Keys.BGM_VOLUME] ?: DEFAULT_BGM_VOLUME,
            uiSfxVolume = prefs[Keys.UI_SFX_VOLUME] ?: DEFAULT_UI_SFX_VOLUME,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: ThemeMode.Dark,
            trailerEnabled = prefs[Keys.TRAILER_ENABLED] ?: true,
            trailerScrapeEnabled = prefs[Keys.TRAILER_SCRAPE_ENABLED] ?: true,
            trailerSourcePreference = prefs[Keys.TRAILER_SOURCE_PREFERENCE]
                ?.let { name -> runCatching { TrailerSourcePreference.valueOf(name) }.getOrNull() }
                ?: TrailerSourcePreference.Auto,
            trailerDisplayMode = prefs[Keys.TRAILER_DISPLAY_MODE]
                ?.let { name -> runCatching { TrailerDisplayMode.valueOf(name) }.getOrNull() }
                ?: TrailerDisplayMode.InIcon,
            trailerIdleSeconds = (prefs[Keys.TRAILER_IDLE_SECONDS] ?: DEFAULT_TRAILER_IDLE_SECONDS)
                .coerceIn(5, 60),
            gameIconIdleMedia = prefs[Keys.GAME_ICON_IDLE_MEDIA]
                ?.let { name -> runCatching { GameIconIdleMedia.valueOf(name) }.getOrNull() }
                ?: GameIconIdleMedia.Trailer,
            homeWallpaperPath = prefs[Keys.HOME_WALLPAPER_PATH]?.takeIf { it.isNotBlank() },
            wallpaperAlignX = (prefs[Keys.WALLPAPER_ALIGN_X] ?: 0f).coerceIn(-1f, 1f),
            wallpaperAlignY = (prefs[Keys.WALLPAPER_ALIGN_Y] ?: 0f).coerceIn(-1f, 1f),
            homeFolderImagePath = prefs[Keys.HOME_FOLDER_IMAGE_PATH]?.takeIf { it.isNotBlank() },
            customBgmPath = prefs[Keys.CUSTOM_BGM_PATH]?.takeIf { it.isNotBlank() },
            musicLibraryPath = prefs[Keys.MUSIC_LIBRARY_PATH]?.takeIf { it.isNotBlank() },
            shellThemeId = prefs[Keys.SHELL_THEME_ID]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SHELL_THEME_ID,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            notificationSoundEnabled = prefs[Keys.NOTIFICATION_SOUND_ENABLED] ?: true,
            discordFriendOnlineNotifications = prefs[Keys.DISCORD_FRIEND_ONLINE_NOTIFICATIONS] ?: true,
            steamFriendOnlineNotifications = prefs[Keys.STEAM_FRIEND_ONLINE_NOTIFICATIONS] ?: true,
            xoraFriendOnlineNotifications = prefs[Keys.XORA_FRIEND_ONLINE_NOTIFICATIONS] ?: true,
            n64UseMupen64PlusNext = prefs[Keys.N64_USE_MUPEN64PLUS_NEXT] ?: false,
            showHiddenGames = prefs[Keys.SHOW_HIDDEN_GAMES] ?: false,
        )
    }

    /**
     * Map of platformId → chosen emulator/core for library launch
     * (Choose Emulator on the scrape & library menu).
     */
    val platformEmulatorChoices: Flow<Map<String, PlatformEmulatorChoice>> =
        dataStore.data.map { prefs ->
            decodePlatformEmulatorChoices(prefs[Keys.PLATFORM_EMULATOR_CHOICES].orEmpty())
        }

    /** Built-in XOrA Emulator display / bezel / netplay preferences. */
    val xoraEmulatorSettings: Flow<XoraEmulatorSettings> = dataStore.data.map { prefs ->
        XoraEmulatorSettings(
            ndsScreenLayout = prefs[Keys.XORA_NDS_LAYOUT]
                ?.let { runCatching { DualScreenLayout.valueOf(it) }.getOrNull() }
                ?: DualScreenLayout.TopBottom,
            ndsScreenGap = (prefs[Keys.XORA_NDS_GAP] ?: 0).coerceIn(0, 100),
            threeDsScreenLayout = prefs[Keys.XORA_3DS_LAYOUT]
                ?.let { runCatching { ThreeDsScreenLayout.valueOf(it) }.getOrNull() }
                ?: ThreeDsScreenLayout.TopBottom,
            expandDualDisplay = prefs[Keys.XORA_EXPAND_DUAL] ?: true,
            aspectMode = prefs[Keys.XORA_ASPECT]
                ?.let { runCatching { XoraAspectMode.valueOf(it) }.getOrNull() }
                ?: XoraAspectMode.Core,
            integerScale = (prefs[Keys.XORA_INTEGER_SCALE] ?: 0).coerceIn(0, 8),
            internalResolution = prefs[Keys.XORA_INTERNAL_RES]
                ?.let { runCatching { XoraInternalResolution.valueOf(it) }.getOrNull() }
                ?: XoraInternalResolution.Native,
            bezelsEnabled = prefs[Keys.XORA_BEZELS_ENABLED] ?: true,
            blockOverlayWash = prefs[Keys.XORA_BLOCK_OVERLAY_WASH] ?: true,
            bezelOpacity = (prefs[Keys.XORA_BEZEL_OPACITY] ?: 0.88f).coerceIn(0f, 1f),
            audioVolume = (prefs[Keys.XORA_AUDIO_VOLUME] ?: 1f).coerceIn(0f, 1f),
            netplayEnabled = prefs[Keys.XORA_NETPLAY_ENABLED] ?: false,
            netplayNickname = prefs[Keys.XORA_NETPLAY_NICK]
                ?.takeIf { it.isNotBlank() } ?: "Player",
            netplayPort = (prefs[Keys.XORA_NETPLAY_PORT] ?: DEFAULT_NETPLAY_PORT)
                .coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT),
            netplaySpectator = prefs[Keys.XORA_NETPLAY_SPECTATOR] ?: false,
            netplayUseRelay = prefs[Keys.XORA_NETPLAY_RELAY] ?: false,
            netplayHostAddress = prefs[Keys.XORA_NETPLAY_HOST].orEmpty(),
            ndsWfcServer = prefs[Keys.XORA_NDS_WFC]
                ?.let { runCatching { NdsWfcServer.valueOf(it) }.getOrNull() }
                ?: NdsWfcServer.Kaeru,
            ndsWfcCustomDns = prefs[Keys.XORA_NDS_WFC_DNS].orEmpty(),
            azaharLobbyApiUrl = prefs[Keys.XORA_AZAHAR_LOBBY_API].orEmpty(),
            threeDsPretendoPrep = prefs[Keys.XORA_3DS_PRETENDO] ?: false,
            pspAdhocEnabled = prefs[Keys.XORA_PSP_ADHOC] ?: true,
            pspAdhocIsServer = prefs[Keys.XORA_PSP_ADHOC_SERVER] ?: false,
            preferredControllerName = prefs[Keys.XORA_CONTROLLER_NAME].orEmpty(),
            buttonMappings = decodeButtonMappings(prefs[Keys.XORA_BUTTON_MAPPINGS].orEmpty()),
        )
    }

    /** Latest incoming online netplay invite this device should auto-join. */
    val pendingNetplayJoin: Flow<PendingNetplayJoin> = dataStore.data.map { prefs ->
        PendingNetplayJoin(
            code = prefs[Keys.PENDING_NETPLAY_CODE].orEmpty(),
            platformId = prefs[Keys.PENDING_NETPLAY_PLATFORM].orEmpty(),
            gameTitle = prefs[Keys.PENDING_NETPLAY_GAME].orEmpty(),
            fromUsername = prefs[Keys.PENDING_NETPLAY_FROM].orEmpty(),
            coreName = prefs[Keys.PENDING_NETPLAY_CORE].orEmpty(),
            createdAtMs = prefs[Keys.PENDING_NETPLAY_AT] ?: 0L,
        )
    }

    /** RetroAchievements behaviour for launcher + XOrA Emulator. */
    val retroAchievementsSettings: Flow<RetroAchievementsSettings> = dataStore.data.map { prefs ->
        RetroAchievementsSettings(
            enabled = prefs[Keys.RA_ENABLED] ?: true,
            hardcore = prefs[Keys.RA_HARDCORE] ?: false,
            unlockNotifications = prefs[Keys.RA_UNLOCK_NOTIFICATIONS] ?: true,
            showInLauncher = prefs[Keys.RA_SHOW_IN_LAUNCHER] ?: true,
            richPresence = prefs[Keys.RA_RICH_PRESENCE] ?: true,
        )
    }

    /** Pinned Home hub shortcut tiles (page 2). */
    val homeShortcuts: Flow<List<HomeShortcut>> = dataStore.data.map { prefs ->
        decodeHomeShortcuts(prefs[Keys.HOME_SHORTCUTS].orEmpty())
    }

    /** Column / preferred-row density for the Home shortcut board (tiles scale with this). */
    val homeShortcutGridLayout: Flow<HomeShortcutGridLayout> = dataStore.data.map { prefs ->
        HomeShortcutGridLayout(
            columns = (prefs[Keys.HOME_SHORTCUT_GRID_COLUMNS] ?: DEFAULT_HOME_SHORTCUT_GRID_COLUMNS)
                .coerceIn(MIN_HOME_SHORTCUT_GRID_COLUMNS, MAX_HOME_SHORTCUT_GRID_COLUMNS),
            rows = (prefs[Keys.HOME_SHORTCUT_GRID_ROWS] ?: DEFAULT_HOME_SHORTCUT_GRID_ROWS)
                .coerceIn(MIN_HOME_SHORTCUT_GRID_ROWS, MAX_HOME_SHORTCUT_GRID_ROWS),
        )
    }

    val credentials: Flow<ScraperCredentials> = dataStore.data.map { prefs ->
        ScraperCredentials(
            screenScraperUser = prefs[Keys.SS_USER].orEmpty(),
            screenScraperPassword = prefs[Keys.SS_PASSWORD].orEmpty(),
            screenScraperDevId = prefs[Keys.SS_DEV_ID].orEmpty(),
            screenScraperDevPassword = prefs[Keys.SS_DEV_PASSWORD].orEmpty(),
            steamGridDbKey = prefs[Keys.SGDB_KEY].orEmpty(),
            igdbClientId = prefs[Keys.IGDB_ID].orEmpty(),
            igdbClientSecret = prefs[Keys.IGDB_SECRET].orEmpty(),
        )
    }

    val profile: Flow<LocalProfile> = dataStore.data.map { prefs ->
        LocalProfile(
            displayName = prefs[Keys.PROFILE_NAME]?.takeIf { it.isNotBlank() } ?: "Player",
            avatarPresetId = prefs[Keys.PROFILE_AVATAR]?.takeIf { it.isNotBlank() } ?: "preset_0",
            avatarSource = prefs[Keys.PROFILE_AVATAR_SOURCE]
                ?.let { name -> runCatching { AvatarSource.valueOf(name) }.getOrNull() }
                ?: AvatarSource.Default,
            localAvatarFileName = prefs[Keys.PROFILE_AVATAR_FILE]?.takeIf { it.isNotBlank() },
            customStatus = prefs[Keys.PROFILE_CUSTOM_STATUS]?.takeIf { it.isNotBlank() },
            favoriteLibraryGameId = prefs[Keys.PROFILE_FAVORITE_LIBRARY_GAME_ID]
                ?.takeIf { it.isNotBlank() },
            xoraPresenceMode = prefs[Keys.XORA_PRESENCE_MODE]?.takeIf { it.isNotBlank() } ?: "Online",
        )
    }

    val retroAchievements: Flow<RetroAchievementsCredentials> = dataStore.data.map { prefs ->
        RetroAchievementsCredentials(
            username = prefs[Keys.RA_USER].orEmpty(),
            apiKey = prefs[Keys.RA_API_KEY].orEmpty(),
            connectToken = prefs[Keys.RA_CONNECT_TOKEN].orEmpty(),
        )
    }

    val steamWebApi: Flow<SteamWebApiCredentials> = dataStore.data.map { prefs ->
        SteamWebApiCredentials(
            apiKey = prefs[Keys.STEAM_WEB_API_KEY].orEmpty(),
            steamId64 = prefs[Keys.STEAM_ID64].orEmpty(),
        )
    }

    val discordSocial: Flow<DiscordSocialSettings> = dataStore.data.map { prefs ->
        // null = never set → use SORA default. Explicit "" after Clear disables Rich Presence.
        val storedAppId = prefs[Keys.DISCORD_APPLICATION_ID]
        DiscordSocialSettings(
            openUrl = prefs[Keys.DISCORD_OPEN_URL].orEmpty(),
            applicationId = when (storedAppId) {
                null -> DEFAULT_DISCORD_APPLICATION_ID
                else -> storedAppId
            },
        )
    }

    /**
     * Whether first-run onboarding has been finished. Defaults to false so a fresh install
     * shows the welcome flow before the Home hub.
     */
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    /**
     * Mixed Steam + Discord pins for the LT “Pinned Friends” strip
     * (order preserved, max [CIRCLE_FRIEND_LIMIT]).
     *
     * Prefers [Keys.CIRCLE_PINS] JSON; falls back to legacy Steam-only
     * [Keys.CIRCLE_FRIEND_IDS] comma list.
     */
    val circlePins: Flow<List<CirclePin>> = dataStore.data.map { prefs ->
        val encoded = prefs[Keys.CIRCLE_PINS].orEmpty()
        if (encoded.isNotBlank()) {
            decodeCirclePins(encoded)
        } else {
            prefs[Keys.CIRCLE_FRIEND_IDS]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(CIRCLE_FRIEND_LIMIT)
                .map { CirclePin(source = CirclePinSource.Steam, id = it) }
        }
    }

    /**
     * MediaStore ids of photos the user marked as favorites in the Photo Viewer. Stored here so
     * favorite status never touches the image files themselves.
     */
    val favoritePhotoIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        decodeStringIdSet(prefs[Keys.FAVORITE_PHOTO_IDS].orEmpty())
    }

    suspend fun setPhotoFavorite(photoId: String, favorite: Boolean) = edit { prefs ->
        val current = decodeStringIdSet(prefs[Keys.FAVORITE_PHOTO_IDS].orEmpty())
        val next = if (favorite) current + photoId else current - photoId
        prefs[Keys.FAVORITE_PHOTO_IDS] = encodeStringIdSet(next)
    }

    /**
     * Library game ids the user chose to hide. Hidden titles stay in the database;
     * they are only filtered from lists unless [ShellSettings.showHiddenGames] is on.
     */
    val hiddenGameIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        decodeStringIdSet(prefs[Keys.HIDDEN_GAME_IDS].orEmpty())
    }

    suspend fun setGameHidden(gameId: String, hidden: Boolean) = edit { prefs ->
        val current = decodeStringIdSet(prefs[Keys.HIDDEN_GAME_IDS].orEmpty())
        val next = if (hidden) current + gameId else current - gameId
        prefs[Keys.HIDDEN_GAME_IDS] = encodeStringIdSet(next)
    }

    /** Per-game cover-art pan inside the Game Icon, biases in `-1f..1f`. */
    val gameArtAlignments: Flow<Map<String, GameArtAlignment>> = dataStore.data.map { prefs ->
        decodeGameArtAlignments(prefs[Keys.GAME_ART_ALIGNMENTS].orEmpty())
    }

    suspend fun setGameArtAlignment(gameId: String, alignment: GameArtAlignment?) = edit { prefs ->
        val current = decodeGameArtAlignments(prefs[Keys.GAME_ART_ALIGNMENTS].orEmpty())
        val next = if (alignment == null || alignment.isIdentity) {
            current - gameId
        } else {
            current + (gameId to alignment.clamped())
        }
        prefs[Keys.GAME_ART_ALIGNMENTS] = encodeGameArtAlignments(next)
    }

    /**
     * User-typed names, keyed by game id.
     *
     * Deliberately not a database column: the database falls back to a destructive migration, so
     * adding one would drop every library, playtime and favourite on upgrade. Living out here also
     * means a re-scrape cannot quietly overwrite a name the user chose by hand.
     */
    val gameTitleOverrides: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        decodeGameTitleOverrides(prefs[Keys.GAME_TITLE_OVERRIDES].orEmpty())
    }

    suspend fun setGameTitleOverride(gameId: String, title: String?) = edit { prefs ->
        val current = decodeGameTitleOverrides(prefs[Keys.GAME_TITLE_OVERRIDES].orEmpty())
        val cleaned = title?.trim()?.takeIf { it.isNotEmpty() }
        val next = if (cleaned == null) current - gameId else current + (gameId to cleaned)
        prefs[Keys.GAME_TITLE_OVERRIDES] = encodeGameTitleOverrides(next)
    }

    suspend fun setShowHiddenGames(enabled: Boolean) = edit {
        it[Keys.SHOW_HIDDEN_GAMES] = enabled
    }

    suspend fun setSecondaryDisplayRole(role: ScreenRole) = edit { it[Keys.SECONDARY_ROLE] = role.name }

    suspend fun setDisplayMode(mode: DisplayMode) = edit {
        it[Keys.DISPLAY_MODE] = mode.name
    }

    suspend fun setUiFitMode(mode: UiFitMode) = edit {
        it[Keys.UI_FIT_MODE] = mode.name
    }

    @Deprecated("Library is always XMB; preference is ignored.")
    suspend fun setLibraryLayout(layout: LibraryLayout) = edit {
        it[Keys.LIBRARY_LAYOUT] = layout.name
    }

    suspend fun setGridColumns(columns: Int) = edit {
        it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 6)
    }

    suspend fun setIconScale(scale: Float) = edit { it[Keys.ICON_SCALE] = scale.coerceIn(0.6f, 1.8f) }

    suspend fun setUiTextScale(scale: Float) = edit {
        it[Keys.UI_TEXT_SCALE] = scale.coerceIn(MIN_UI_TEXT_SCALE, MAX_UI_TEXT_SCALE)
    }

    suspend fun setScrapeAfterScan(enabled: Boolean) = edit { it[Keys.SCRAPE_AFTER_SCAN] = enabled }

    suspend fun setGamesSecondarySlot(slot: String) = edit {
        it[Keys.GAMES_SECONDARY_SLOT] = slot
    }

    suspend fun setXmbTitleStyle(style: XmbTitleStyle) = edit {
        it[Keys.XMB_TITLE_STYLE] = style.name
    }

    suspend fun setManualScrapeEnabled(enabled: Boolean) = edit {
        it[Keys.MANUAL_SCRAPE_ENABLED] = enabled
    }

    suspend fun setAndroidAppSyncEnabled(enabled: Boolean) = edit {
        it[Keys.ANDROID_APP_SYNC_ENABLED] = enabled
    }

    suspend fun setLastScanAt(timestamp: Long) = edit { it[Keys.LAST_SCAN_AT] = timestamp }

    suspend fun setBgmVolume(volume: Float) = edit {
        it[Keys.BGM_VOLUME] = volume.coerceIn(0f, 1f)
    }

    suspend fun setUiSfxVolume(volume: Float) = edit {
        it[Keys.UI_SFX_VOLUME] = volume.coerceIn(0f, 1f)
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit {
        it[Keys.THEME_MODE] = mode.name
    }

    suspend fun setTrailerEnabled(enabled: Boolean) = edit {
        it[Keys.TRAILER_ENABLED] = enabled
    }

    suspend fun setTrailerScrapeEnabled(enabled: Boolean) = edit {
        it[Keys.TRAILER_SCRAPE_ENABLED] = enabled
    }

    suspend fun setTrailerSourcePreference(preference: TrailerSourcePreference) = edit {
        it[Keys.TRAILER_SOURCE_PREFERENCE] = preference.name
    }

    suspend fun setTrailerDisplayMode(mode: TrailerDisplayMode) = edit {
        it[Keys.TRAILER_DISPLAY_MODE] = mode.name
    }

    suspend fun setTrailerIdleSeconds(seconds: Int) = edit {
        it[Keys.TRAILER_IDLE_SECONDS] = seconds.coerceIn(5, 60)
    }

    suspend fun setGameIconIdleMedia(media: GameIconIdleMedia) = edit {
        it[Keys.GAME_ICON_IDLE_MEDIA] = media.name
    }

    suspend fun setHomeWallpaperPath(path: String?) = edit {
        if (path.isNullOrBlank()) it.remove(Keys.HOME_WALLPAPER_PATH)
        else it[Keys.HOME_WALLPAPER_PATH] = path
    }

    suspend fun setWallpaperAlignment(alignment: GameArtAlignment?) = edit { prefs ->
        val next = (alignment ?: GameArtAlignment()).clamped()
        if (next.isIdentity) {
            prefs.remove(Keys.WALLPAPER_ALIGN_X)
            prefs.remove(Keys.WALLPAPER_ALIGN_Y)
        } else {
            prefs[Keys.WALLPAPER_ALIGN_X] = next.x
            prefs[Keys.WALLPAPER_ALIGN_Y] = next.y
        }
    }

    suspend fun setHomeFolderImagePath(path: String?) = edit {
        if (path.isNullOrBlank()) it.remove(Keys.HOME_FOLDER_IMAGE_PATH)
        else it[Keys.HOME_FOLDER_IMAGE_PATH] = path
    }

    suspend fun setCustomBgmPath(path: String?) = edit {
        if (path.isNullOrBlank()) it.remove(Keys.CUSTOM_BGM_PATH)
        else it[Keys.CUSTOM_BGM_PATH] = path
    }

    suspend fun setMusicLibraryPath(path: String?) = edit {
        if (path.isNullOrBlank()) it.remove(Keys.MUSIC_LIBRARY_PATH)
        else it[Keys.MUSIC_LIBRARY_PATH] = path
    }

    suspend fun setShellThemeId(themeId: String) = edit {
        val normalized = themeId.trim().ifBlank { DEFAULT_SHELL_THEME_ID }
        it[Keys.SHELL_THEME_ID] = normalized
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit {
        it[Keys.NOTIFICATIONS_ENABLED] = enabled
    }

    suspend fun setNotificationSoundEnabled(enabled: Boolean) = edit {
        it[Keys.NOTIFICATION_SOUND_ENABLED] = enabled
    }

    suspend fun setDiscordFriendOnlineNotifications(enabled: Boolean) = edit {
        it[Keys.DISCORD_FRIEND_ONLINE_NOTIFICATIONS] = enabled
    }

    suspend fun setSteamFriendOnlineNotifications(enabled: Boolean) = edit {
        it[Keys.STEAM_FRIEND_ONLINE_NOTIFICATIONS] = enabled
    }

    suspend fun setXoraFriendOnlineNotifications(enabled: Boolean) = edit {
        it[Keys.XORA_FRIEND_ONLINE_NOTIFICATIONS] = enabled
    }

    suspend fun setN64UseMupen64PlusNext(enabled: Boolean) = edit {
        it[Keys.N64_USE_MUPEN64PLUS_NEXT] = enabled
    }

    suspend fun setXoraNdsScreenLayout(layout: DualScreenLayout) = edit {
        it[Keys.XORA_NDS_LAYOUT] = layout.name
    }

    suspend fun setXoraNdsScreenGap(gap: Int) = edit {
        it[Keys.XORA_NDS_GAP] = gap.coerceIn(0, 100)
    }

    suspend fun setXora3dsScreenLayout(layout: ThreeDsScreenLayout) = edit {
        it[Keys.XORA_3DS_LAYOUT] = layout.name
    }

    suspend fun setXoraExpandDualDisplay(enabled: Boolean) = edit {
        it[Keys.XORA_EXPAND_DUAL] = enabled
    }

    suspend fun setXoraAspectMode(mode: XoraAspectMode) = edit {
        it[Keys.XORA_ASPECT] = mode.name
    }

    suspend fun setXoraIntegerScale(scale: Int) = edit {
        it[Keys.XORA_INTEGER_SCALE] = scale.coerceIn(0, 8)
    }

    suspend fun setXoraInternalResolution(resolution: XoraInternalResolution) = edit {
        it[Keys.XORA_INTERNAL_RES] = resolution.name
    }

    suspend fun setXoraBezelsEnabled(enabled: Boolean) = edit {
        it[Keys.XORA_BEZELS_ENABLED] = enabled
    }

    suspend fun setXoraBlockOverlayWash(enabled: Boolean) = edit {
        it[Keys.XORA_BLOCK_OVERLAY_WASH] = enabled
    }

    suspend fun setXoraBezelOpacity(opacity: Float) = edit {
        it[Keys.XORA_BEZEL_OPACITY] = opacity.coerceIn(0f, 1f)
    }

    suspend fun setXoraAudioVolume(volume: Float) = edit {
        it[Keys.XORA_AUDIO_VOLUME] = volume.coerceIn(0f, 1f)
    }

    /** Display, audio, and gamepad defaults. Leaves netplay identity / host address alone. */
    suspend fun resetXoraEmulatorPlaySettings() = edit {
        it.remove(Keys.XORA_ASPECT)
        it.remove(Keys.XORA_INTEGER_SCALE)
        it.remove(Keys.XORA_INTERNAL_RES)
        it.remove(Keys.XORA_BEZELS_ENABLED)
        it.remove(Keys.XORA_BLOCK_OVERLAY_WASH)
        it.remove(Keys.XORA_BEZEL_OPACITY)
        it.remove(Keys.XORA_AUDIO_VOLUME)
        it.remove(Keys.XORA_EXPAND_DUAL)
        it.remove(Keys.XORA_CONTROLLER_NAME)
        it.remove(Keys.XORA_BUTTON_MAPPINGS)
        it.remove(Keys.XORA_NDS_LAYOUT)
        it.remove(Keys.XORA_NDS_GAP)
        it.remove(Keys.XORA_3DS_LAYOUT)
    }

    suspend fun setXoraNetplayEnabled(enabled: Boolean) = edit {
        it[Keys.XORA_NETPLAY_ENABLED] = enabled
    }

    suspend fun setXoraNetplayNickname(nickname: String) = edit {
        it[Keys.XORA_NETPLAY_NICK] = nickname.trim().take(24).ifBlank { "Player" }
    }

    suspend fun setXoraNetplayPort(port: Int) = edit {
        it[Keys.XORA_NETPLAY_PORT] = port.coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT)
    }

    suspend fun setXoraNetplaySpectator(enabled: Boolean) = edit {
        it[Keys.XORA_NETPLAY_SPECTATOR] = enabled
    }

    suspend fun setXoraNetplayUseRelay(enabled: Boolean) = edit {
        it[Keys.XORA_NETPLAY_RELAY] = enabled
    }

    suspend fun setXoraNetplayHostAddress(address: String) = edit {
        it[Keys.XORA_NETPLAY_HOST] = address.trim().take(128)
    }

    suspend fun setXoraNdsWfcServer(server: NdsWfcServer) = edit {
        it[Keys.XORA_NDS_WFC] = server.name
    }

    suspend fun setXoraNdsWfcCustomDns(dns: String) = edit {
        it[Keys.XORA_NDS_WFC_DNS] = dns.trim().take(64)
    }

    suspend fun setXoraAzaharLobbyApiUrl(url: String) = edit {
        it[Keys.XORA_AZAHAR_LOBBY_API] = url.trim().take(256)
    }

    suspend fun setXoraThreeDsPretendoPrep(enabled: Boolean) = edit {
        it[Keys.XORA_3DS_PRETENDO] = enabled
    }

    suspend fun setXoraPspAdhocEnabled(enabled: Boolean) = edit {
        it[Keys.XORA_PSP_ADHOC] = enabled
    }

    suspend fun setXoraPspAdhocIsServer(enabled: Boolean) = edit {
        it[Keys.XORA_PSP_ADHOC_SERVER] = enabled
    }

    suspend fun setPendingNetplayJoin(join: PendingNetplayJoin) = edit {
        it[Keys.PENDING_NETPLAY_CODE] = join.code.trim().take(8)
        it[Keys.PENDING_NETPLAY_PLATFORM] = join.platformId.trim().take(64)
        it[Keys.PENDING_NETPLAY_GAME] = join.gameTitle.trim().take(128)
        it[Keys.PENDING_NETPLAY_FROM] = join.fromUsername.trim().take(128)
        it[Keys.PENDING_NETPLAY_CORE] = join.coreName.trim().take(64)
        it[Keys.PENDING_NETPLAY_AT] = join.createdAtMs
    }

    suspend fun clearPendingNetplayJoin() = edit {
        it.remove(Keys.PENDING_NETPLAY_CODE)
        it.remove(Keys.PENDING_NETPLAY_PLATFORM)
        it.remove(Keys.PENDING_NETPLAY_GAME)
        it.remove(Keys.PENDING_NETPLAY_FROM)
        it.remove(Keys.PENDING_NETPLAY_CORE)
        it.remove(Keys.PENDING_NETPLAY_AT)
    }

    /** Notification ids the user already cleared. Survives process death and app updates. */
    val dismissedShellNotificationIds: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.DISMISSED_SHELL_NOTIFICATION_IDS].orEmpty()
    }

    suspend fun addDismissedShellNotificationIds(ids: Collection<String>) = edit { prefs ->
        val incoming = ids.map { it.trim() }.filter { it.isNotEmpty() }
        if (incoming.isEmpty()) return@edit
        val merged = (prefs[Keys.DISMISSED_SHELL_NOTIFICATION_IDS].orEmpty() + incoming)
            .toMutableSet()
        while (merged.size > MAX_DISMISSED_SHELL_NOTIFICATION_IDS) {
            merged.remove(merged.first())
        }
        prefs[Keys.DISMISSED_SHELL_NOTIFICATION_IDS] = merged
    }

    /** Wall-clock time of the last GitHub release check, so resume does not poll every time. */
    val lastUpdateCheckAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK_AT] ?: 0L
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) = edit {
        it[Keys.LAST_UPDATE_CHECK_AT] = timestamp
    }

    /** Newest version already announced by a notification, so one release toasts once. */
    val announcedUpdateVersion: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.ANNOUNCED_UPDATE_VERSION].orEmpty()
    }

    suspend fun setAnnouncedUpdateVersion(version: String) = edit {
        it[Keys.ANNOUNCED_UPDATE_VERSION] = version.trim().take(64)
    }

    suspend fun setXoraPreferredControllerName(name: String) = edit {
        it[Keys.XORA_CONTROLLER_NAME] = name.trim().take(128)
    }

    suspend fun setXoraButtonMappings(mappings: Map<Int, Int>) = edit {
        it[Keys.XORA_BUTTON_MAPPINGS] = encodeButtonMappings(mappings)
    }

    suspend fun clearXoraButtonMappings() = edit {
        it[Keys.XORA_BUTTON_MAPPINGS] = ""
    }

    suspend fun setRaEnabled(enabled: Boolean) = edit {
        it[Keys.RA_ENABLED] = enabled
    }

    suspend fun setRaHardcore(enabled: Boolean) = edit {
        it[Keys.RA_HARDCORE] = enabled
    }

    suspend fun setRaUnlockNotifications(enabled: Boolean) = edit {
        it[Keys.RA_UNLOCK_NOTIFICATIONS] = enabled
    }

    suspend fun setRaShowInLauncher(enabled: Boolean) = edit {
        it[Keys.RA_SHOW_IN_LAUNCHER] = enabled
    }

    suspend fun setRaRichPresence(enabled: Boolean) = edit {
        it[Keys.RA_RICH_PRESENCE] = enabled
    }

    suspend fun platformEmulatorChoice(platformId: String): PlatformEmulatorChoice? =
        platformEmulatorChoices.first()[platformId]

    /**
     * Persists the Choose Emulator selection for [platformId]. Passing null clears it.
     * Also clears the legacy N64 Mupen toggle when an explicit N64 choice is stored.
     */
    suspend fun setPlatformEmulatorChoice(
        platformId: String,
        choice: PlatformEmulatorChoice?,
    ) = edit { prefs ->
        val current = decodePlatformEmulatorChoices(
            prefs[Keys.PLATFORM_EMULATOR_CHOICES].orEmpty(),
        ).toMutableMap()
        if (choice == null) {
            current.remove(platformId)
        } else {
            current[platformId] = choice
            if (platformId == "n64") {
                prefs[Keys.N64_USE_MUPEN64PLUS_NEXT] = false
            }
        }
        if (current.isEmpty()) {
            prefs.remove(Keys.PLATFORM_EMULATOR_CHOICES)
        } else {
            prefs[Keys.PLATFORM_EMULATOR_CHOICES] = encodePlatformEmulatorChoices(current)
        }
    }

    suspend fun setOnboardingComplete(done: Boolean) = edit {
        it[Keys.ONBOARDING_COMPLETE] = done
    }

    suspend fun setHomeShortcuts(shortcuts: List<HomeShortcut>) = edit {
        it[Keys.HOME_SHORTCUTS] = encodeHomeShortcuts(shortcuts)
    }

    suspend fun setHomeShortcutGridColumns(columns: Int) = edit {
        it[Keys.HOME_SHORTCUT_GRID_COLUMNS] = columns.coerceIn(
            MIN_HOME_SHORTCUT_GRID_COLUMNS,
            MAX_HOME_SHORTCUT_GRID_COLUMNS,
        )
    }

    suspend fun setHomeShortcutGridRows(rows: Int) = edit {
        it[Keys.HOME_SHORTCUT_GRID_ROWS] = rows.coerceIn(
            MIN_HOME_SHORTCUT_GRID_ROWS,
            MAX_HOME_SHORTCUT_GRID_ROWS,
        )
    }

    suspend fun setHomeShortcutGridLayout(columns: Int, rows: Int) = edit {
        it[Keys.HOME_SHORTCUT_GRID_COLUMNS] = columns.coerceIn(
            MIN_HOME_SHORTCUT_GRID_COLUMNS,
            MAX_HOME_SHORTCUT_GRID_COLUMNS,
        )
        it[Keys.HOME_SHORTCUT_GRID_ROWS] = rows.coerceIn(
            MIN_HOME_SHORTCUT_GRID_ROWS,
            MAX_HOME_SHORTCUT_GRID_ROWS,
        )
    }

    /**
     * One-shot flag so libraries that previously marked trailers "resolved" with a null URL
     * (before YouTube fallback) get another resolve pass.
     */
    suspend fun consumeTrailerPipelineMigration(): Boolean {
        val already = dataStore.data.map { it[Keys.TRAILER_PIPELINE_V2] ?: false }.first()
        if (already) return false
        edit { it[Keys.TRAILER_PIPELINE_V2] = true }
        return true
    }

    suspend fun setScreenScraperCredentials(user: String, password: String) = edit {
        it[Keys.SS_USER] = user
        it[Keys.SS_PASSWORD] = password
    }

    suspend fun setScreenScraperDevCredentials(devId: String, devPassword: String) = edit {
        it[Keys.SS_DEV_ID] = devId
        it[Keys.SS_DEV_PASSWORD] = devPassword
    }

    suspend fun setSteamGridDbKey(key: String) = edit { it[Keys.SGDB_KEY] = key }

    suspend fun setIgdbCredentials(clientId: String, clientSecret: String) = edit {
        it[Keys.IGDB_ID] = clientId
        it[Keys.IGDB_SECRET] = clientSecret
    }

    suspend fun setProfile(displayName: String, avatarPresetId: String) = edit {
        it[Keys.PROFILE_NAME] = displayName.trim().ifBlank { "Player" }
        it[Keys.PROFILE_AVATAR] = avatarPresetId.ifBlank { "preset_0" }
    }

    suspend fun setProfileCustomStatus(status: String?) = edit {
        val trimmed = status?.trim().orEmpty()
        if (trimmed.isBlank()) {
            it.remove(Keys.PROFILE_CUSTOM_STATUS)
        } else {
            it[Keys.PROFILE_CUSTOM_STATUS] = trimmed.take(80)
        }
    }

    suspend fun setXoraPresenceMode(mode: String) = edit {
        it[Keys.XORA_PRESENCE_MODE] = mode.trim().ifBlank { "Online" }
    }

    suspend fun setProfileFavoriteLibraryGame(gameId: String?) = edit {
        val trimmed = gameId?.trim().orEmpty()
        if (trimmed.isBlank()) {
            it.remove(Keys.PROFILE_FAVORITE_LIBRARY_GAME_ID)
        } else {
            it[Keys.PROFILE_FAVORITE_LIBRARY_GAME_ID] = trimmed
        }
    }

    suspend fun setProfileAvatar(
        source: AvatarSource,
        presetId: String? = null,
        localFileName: String? = null,
    ) = edit {
        it[Keys.PROFILE_AVATAR_SOURCE] = source.name
        if (presetId != null) {
            it[Keys.PROFILE_AVATAR] = presetId.ifBlank { "preset_0" }
        }
        when (source) {
            AvatarSource.Local -> {
                if (localFileName != null) {
                    it[Keys.PROFILE_AVATAR_FILE] = localFileName
                }
            }
            AvatarSource.Default,
            AvatarSource.RetroAchievements,
            AvatarSource.Discord,
            AvatarSource.XoraNetwork,
            -> {
                it[Keys.PROFILE_AVATAR_FILE] = ""
            }
        }
    }

    suspend fun setRetroAchievementsCredentials(
        username: String,
        apiKey: String,
        connectToken: String? = null,
    ) = edit {
        it[Keys.RA_USER] = username.trim()
        it[Keys.RA_API_KEY] = apiKey.trim()
        if (connectToken != null) {
            it[Keys.RA_CONNECT_TOKEN] = connectToken.trim()
        }
    }

    /** Persist Connect token from password login without clearing an existing Web API key. */
    suspend fun setRaConnectToken(username: String, connectToken: String) = edit {
        it[Keys.RA_USER] = username.trim()
        it[Keys.RA_CONNECT_TOKEN] = connectToken.trim()
    }

    suspend fun clearRetroAchievementsCredentials() = edit {
        it[Keys.RA_USER] = ""
        it[Keys.RA_API_KEY] = ""
        it[Keys.RA_CONNECT_TOKEN] = ""
    }

    suspend fun setSteamWebApiCredentials(apiKey: String, steamId64: String) = edit {
        it[Keys.STEAM_WEB_API_KEY] = apiKey.trim()
        it[Keys.STEAM_ID64] = steamId64.trim()
    }

    /** OpenID / manual SteamID64 only — leaves an existing Web API key intact. */
    suspend fun setSteamId64(steamId64: String) = edit {
        it[Keys.STEAM_ID64] = steamId64.trim()
    }

    suspend fun setSteamWebApiKey(apiKey: String) = edit {
        it[Keys.STEAM_WEB_API_KEY] = apiKey.trim()
    }

    suspend fun clearSteamWebApiCredentials() = edit {
        it[Keys.STEAM_WEB_API_KEY] = ""
        it[Keys.STEAM_ID64] = ""
    }

    suspend fun setDiscordOpenUrl(url: String) = edit {
        it[Keys.DISCORD_OPEN_URL] = url.trim()
    }

    suspend fun clearDiscordOpenUrl() = edit {
        it[Keys.DISCORD_OPEN_URL] = ""
    }

    suspend fun setDiscordApplicationId(applicationId: String) = edit {
        it[Keys.DISCORD_APPLICATION_ID] = applicationId.trim()
    }

    suspend fun clearDiscordApplicationId() = edit {
        it[Keys.DISCORD_APPLICATION_ID] = ""
    }

    /** Replaces the LT Circle pins. Truncates to [CIRCLE_FRIEND_LIMIT]. */
    suspend fun setCirclePins(pins: List<CirclePin>) = edit {
        val normalized = pins
            .map { it.copy(id = it.id.trim()) }
            .filter { it.id.isNotEmpty() }
            .distinctBy { it.key }
            .take(CIRCLE_FRIEND_LIMIT)
        it[Keys.CIRCLE_PINS] = encodeCirclePins(normalized)
        // Keep legacy Steam-only key in sync for older readers / backups.
        it[Keys.CIRCLE_FRIEND_IDS] = normalized
            .filter { pin -> pin.source == CirclePinSource.Steam }
            .map { pin -> pin.id }
            .joinToString(",")
    }

    /** Adds [pin] to Circle if under the limit and not already present. */
    suspend fun addCirclePin(pin: CirclePin) {
        val normalized = pin.copy(id = pin.id.trim())
        if (normalized.id.isEmpty()) return
        val current = circlePins.first()
        if (current.any { it.key == normalized.key } || current.size >= CIRCLE_FRIEND_LIMIT) return
        setCirclePins(current + normalized)
    }

    /** Removes a pin matching [pin]'s source + id. */
    suspend fun removeCirclePin(pin: CirclePin) {
        val normalized = pin.copy(id = pin.id.trim())
        if (normalized.id.isEmpty()) return
        setCirclePins(circlePins.first().filterNot { it.key == normalized.key })
    }

    /**
     * Preferred scraper for a single ROM. Empty / missing means inherit platform (or Auto).
     * Stored as the [com.arcadia.shell.scraper.ScraperPreference] enum name.
     */
    suspend fun setGameScraperPreference(gameId: String, preference: String?) = edit { prefs ->
        val key = gameScraperKey(gameId)
        if (preference.isNullOrBlank()) prefs.remove(key) else prefs[key] = preference
    }

    /** Preferred scraper for every game on a platform unless a per-game override exists. */
    suspend fun setPlatformScraperPreference(platformId: String, preference: String?) = edit { prefs ->
        val key = platformScraperKey(platformId)
        if (preference.isNullOrBlank()) prefs.remove(key) else prefs[key] = preference
    }

    suspend fun gameScraperPreference(gameId: String): String? =
        dataStore.data.map { it[gameScraperKey(gameId)] }.first()

    suspend fun platformScraperPreference(platformId: String): String? =
        dataStore.data.map { it[platformScraperKey(platformId)] }.first()

    /**
     * Per-game override wins; otherwise platform preference; otherwise `"Auto"`.
     */
    suspend fun resolveScraperPreference(gameId: String, platformId: String): String {
        gameScraperPreference(gameId)?.takeIf { it.isNotBlank() }?.let { return it }
        platformScraperPreference(platformId)?.takeIf { it.isNotBlank() }?.let { return it }
        return "Auto"
    }

    private fun gameScraperKey(gameId: String) =
        stringPreferencesKey("scraper_pref_game_$gameId")

    private fun platformScraperKey(platformId: String) =
        stringPreferencesKey("scraper_pref_platform_$platformId")

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val SECONDARY_ROLE = stringPreferencesKey("secondary_display_role")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val UI_FIT_MODE = stringPreferencesKey("ui_fit_mode")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val ICON_SCALE = floatPreferencesKey("icon_scale")
        val UI_TEXT_SCALE = floatPreferencesKey("ui_text_scale")
        val SCRAPE_AFTER_SCAN = booleanPreferencesKey("scrape_after_scan")
        val GAMES_SECONDARY_SLOT = stringPreferencesKey("games_secondary_slot")
        val XMB_TITLE_STYLE = stringPreferencesKey("xmb_title_style")
        val MANUAL_SCRAPE_ENABLED = booleanPreferencesKey("manual_scrape_enabled")
        val ANDROID_APP_SYNC_ENABLED = booleanPreferencesKey("android_app_sync_enabled")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val BGM_VOLUME = floatPreferencesKey("bgm_volume")
        val UI_SFX_VOLUME = floatPreferencesKey("ui_sfx_volume")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TRAILER_ENABLED = booleanPreferencesKey("trailer_enabled")
        val TRAILER_SCRAPE_ENABLED = booleanPreferencesKey("trailer_scrape_enabled")
        val TRAILER_SOURCE_PREFERENCE = stringPreferencesKey("trailer_source_preference")
        val TRAILER_DISPLAY_MODE = stringPreferencesKey("trailer_display_mode")
        val TRAILER_IDLE_SECONDS = intPreferencesKey("trailer_idle_seconds")
        val GAME_ICON_IDLE_MEDIA = stringPreferencesKey("game_icon_idle_media")
        val TRAILER_PIPELINE_V2 = booleanPreferencesKey("trailer_pipeline_v2")
        val SS_USER = stringPreferencesKey("screenscraper_user")
        val SS_PASSWORD = stringPreferencesKey("screenscraper_password")
        val SS_DEV_ID = stringPreferencesKey("screenscraper_dev_id")
        val SS_DEV_PASSWORD = stringPreferencesKey("screenscraper_dev_password")
        val SGDB_KEY = stringPreferencesKey("steamgriddb_key")
        val IGDB_ID = stringPreferencesKey("igdb_client_id")
        val IGDB_SECRET = stringPreferencesKey("igdb_client_secret")
        val PROFILE_NAME = stringPreferencesKey("profile_display_name")
        val PROFILE_AVATAR = stringPreferencesKey("profile_avatar_preset")
        val PROFILE_AVATAR_SOURCE = stringPreferencesKey("profile_avatar_source")
        val PROFILE_AVATAR_FILE = stringPreferencesKey("profile_avatar_file")
        val PROFILE_CUSTOM_STATUS = stringPreferencesKey("profile_custom_status")
        val XORA_PRESENCE_MODE = stringPreferencesKey("xora_presence_mode")
        val PROFILE_FAVORITE_LIBRARY_GAME_ID =
            stringPreferencesKey("profile_favorite_library_game_id")
        val RA_USER = stringPreferencesKey("retroachievements_user")
        val RA_API_KEY = stringPreferencesKey("retroachievements_api_key")
        val RA_CONNECT_TOKEN = stringPreferencesKey("retroachievements_connect_token")
        val STEAM_WEB_API_KEY = stringPreferencesKey("steam_web_api_key")
        val STEAM_ID64 = stringPreferencesKey("steam_id64")
        val DISCORD_OPEN_URL = stringPreferencesKey("discord_open_url")
        val DISCORD_APPLICATION_ID = stringPreferencesKey("discord_application_id")
        val CIRCLE_FRIEND_IDS = stringPreferencesKey("circle_friend_ids")
        /** JSON array of `{source,id}` Circle pins (Steam + Discord). */
        val CIRCLE_PINS = stringPreferencesKey("circle_pins")
        /** JSON array of MediaStore photo ids favourited in the Photo Viewer. */
        val FAVORITE_PHOTO_IDS = stringPreferencesKey("favorite_photo_ids")
        val HIDDEN_GAME_IDS = stringPreferencesKey("hidden_game_ids")
        val GAME_ART_ALIGNMENTS = stringPreferencesKey("game_art_alignments")
        val GAME_TITLE_OVERRIDES = stringPreferencesKey("game_title_overrides")
        val SHOW_HIDDEN_GAMES = booleanPreferencesKey("show_hidden_games")
        val HOME_WALLPAPER_PATH = stringPreferencesKey("home_wallpaper_path")
        val WALLPAPER_ALIGN_X = floatPreferencesKey("wallpaper_align_x")
        val WALLPAPER_ALIGN_Y = floatPreferencesKey("wallpaper_align_y")
        val HOME_FOLDER_IMAGE_PATH = stringPreferencesKey("home_folder_image_path")
        val CUSTOM_BGM_PATH = stringPreferencesKey("custom_bgm_path")
        val MUSIC_LIBRARY_PATH = stringPreferencesKey("music_library_path")
        val SHELL_THEME_ID = stringPreferencesKey("shell_theme_id")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_SOUND_ENABLED = booleanPreferencesKey("notification_sound_enabled")
        val DISCORD_FRIEND_ONLINE_NOTIFICATIONS =
            booleanPreferencesKey("discord_friend_online_notifications")
        val STEAM_FRIEND_ONLINE_NOTIFICATIONS =
            booleanPreferencesKey("steam_friend_online_notifications")
        val XORA_FRIEND_ONLINE_NOTIFICATIONS =
            booleanPreferencesKey("xora_friend_online_notifications")
        val N64_USE_MUPEN64PLUS_NEXT = booleanPreferencesKey("n64_use_mupen64plus_next")
        val XORA_NDS_LAYOUT = stringPreferencesKey("xora_nds_screen_layout")
        val XORA_NDS_GAP = intPreferencesKey("xora_nds_screen_gap")
        val XORA_3DS_LAYOUT = stringPreferencesKey("xora_3ds_screen_layout")
        val XORA_EXPAND_DUAL = booleanPreferencesKey("xora_expand_dual_display")
        val XORA_ASPECT = stringPreferencesKey("xora_aspect_mode")
        val XORA_INTEGER_SCALE = intPreferencesKey("xora_integer_scale")
        val XORA_INTERNAL_RES = stringPreferencesKey("xora_internal_resolution")
        val XORA_BEZELS_ENABLED = booleanPreferencesKey("xora_bezels_enabled")
        val XORA_BLOCK_OVERLAY_WASH = booleanPreferencesKey("xora_block_overlay_wash")
        val XORA_BEZEL_OPACITY = floatPreferencesKey("xora_bezel_opacity")
        val XORA_AUDIO_VOLUME = floatPreferencesKey("xora_audio_volume")
        val XORA_NETPLAY_ENABLED = booleanPreferencesKey("xora_netplay_enabled")
        val XORA_NETPLAY_NICK = stringPreferencesKey("xora_netplay_nickname")
        val XORA_NETPLAY_PORT = intPreferencesKey("xora_netplay_port")
        val XORA_NETPLAY_SPECTATOR = booleanPreferencesKey("xora_netplay_spectator")
        val XORA_NETPLAY_RELAY = booleanPreferencesKey("xora_netplay_relay")
        val XORA_NETPLAY_HOST = stringPreferencesKey("xora_netplay_host")
        val XORA_NDS_WFC = stringPreferencesKey("xora_nds_wfc_server")
        val XORA_NDS_WFC_DNS = stringPreferencesKey("xora_nds_wfc_custom_dns")
        val XORA_AZAHAR_LOBBY_API = stringPreferencesKey("xora_azahar_lobby_api")
        val XORA_3DS_PRETENDO = booleanPreferencesKey("xora_3ds_pretendo_prep")
        val XORA_PSP_ADHOC = booleanPreferencesKey("xora_psp_adhoc_enabled")
        val XORA_PSP_ADHOC_SERVER = booleanPreferencesKey("xora_psp_adhoc_is_server")
        val PENDING_NETPLAY_CODE = stringPreferencesKey("pending_netplay_join_code")
        val PENDING_NETPLAY_PLATFORM = stringPreferencesKey("pending_netplay_join_platform")
        val PENDING_NETPLAY_GAME = stringPreferencesKey("pending_netplay_join_game")
        val PENDING_NETPLAY_FROM = stringPreferencesKey("pending_netplay_join_from")
        val PENDING_NETPLAY_CORE = stringPreferencesKey("pending_netplay_join_core")
        val PENDING_NETPLAY_AT = longPreferencesKey("pending_netplay_join_at")
        val DISMISSED_SHELL_NOTIFICATION_IDS =
            stringSetPreferencesKey("dismissed_shell_notification_ids")
        val XORA_CONTROLLER_NAME = stringPreferencesKey("xora_preferred_controller")
        val XORA_BUTTON_MAPPINGS = stringPreferencesKey("xora_button_mappings")
        val RA_ENABLED = booleanPreferencesKey("ra_enabled")
        val RA_HARDCORE = booleanPreferencesKey("ra_hardcore")
        val RA_UNLOCK_NOTIFICATIONS = booleanPreferencesKey("ra_unlock_notifications")
        val RA_SHOW_IN_LAUNCHER = booleanPreferencesKey("ra_show_in_launcher")
        val RA_RICH_PRESENCE = booleanPreferencesKey("ra_rich_presence")
        /** JSON object: platformId → { playerId, packageName?, coreName? }. */
        val PLATFORM_EMULATOR_CHOICES = stringPreferencesKey("platform_emulator_choices")
        val HOME_SHORTCUTS = stringPreferencesKey("home_shortcuts")
        val HOME_SHORTCUT_GRID_COLUMNS = intPreferencesKey("home_shortcut_grid_columns")
        val HOME_SHORTCUT_GRID_ROWS = intPreferencesKey("home_shortcut_grid_rows")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val ANNOUNCED_UPDATE_VERSION = stringPreferencesKey("announced_update_version")
    }
}

/** Preferred Home shortcut board density (cell size = hub area ÷ cols / rows). */
data class HomeShortcutGridLayout(
    val columns: Int = DEFAULT_HOME_SHORTCUT_GRID_COLUMNS,
    val rows: Int = DEFAULT_HOME_SHORTCUT_GRID_ROWS,
)

/** Default landscape density — coarser than the old packed 8-col board so tiles read larger. */
const val DEFAULT_HOME_SHORTCUT_GRID_COLUMNS = 6
const val DEFAULT_HOME_SHORTCUT_GRID_ROWS = 3
const val MIN_HOME_SHORTCUT_GRID_COLUMNS = 4
const val MAX_HOME_SHORTCUT_GRID_COLUMNS = 10
const val MIN_HOME_SHORTCUT_GRID_ROWS = 2
const val MAX_HOME_SHORTCUT_GRID_ROWS = 6

internal fun encodePlatformEmulatorChoices(
    choices: Map<String, PlatformEmulatorChoice>,
): String {
    val obj = JSONObject()
    choices.forEach { (platformId, choice) ->
        obj.put(
            platformId,
            JSONObject()
                .put("playerId", choice.playerId)
                .put("packageName", choice.packageName.orEmpty())
                .put("coreName", choice.coreName.orEmpty()),
        )
    }
    return obj.toString()
}

internal fun decodePlatformEmulatorChoices(raw: String): Map<String, PlatformEmulatorChoice> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        buildMap {
            obj.keys().forEach { platformId ->
                val entry = obj.optJSONObject(platformId) ?: return@forEach
                val playerId = entry.optString("playerId").takeIf { it.isNotBlank() }
                    ?: return@forEach
                put(
                    platformId,
                    PlatformEmulatorChoice(
                        playerId = playerId,
                        packageName = entry.optString("packageName").takeIf { it.isNotBlank() },
                        coreName = entry.optString("coreName").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun encodeHomeShortcuts(shortcuts: List<HomeShortcut>): String {
    val array = JSONArray()
    shortcuts.forEach { shortcut ->
        array.put(
            JSONObject()
                .put("id", shortcut.id)
                .put("kind", shortcut.kind.name)
                .put("title", shortcut.title)
                .put("target", shortcut.target)
                .put("artPath", shortcut.artPath.orEmpty())
                .put("span", shortcut.span.name),
        )
    }
    return array.toString()
}

internal fun decodeHomeShortcuts(raw: String): List<HomeShortcut> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val kind = runCatching {
                    HomeShortcutKind.valueOf(obj.optString("kind"))
                }.getOrNull() ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val target = obj.optString("target").takeIf { it.isNotBlank() } ?: continue
                add(
                    HomeShortcut(
                        id = id,
                        kind = kind,
                        title = obj.optString("title").ifBlank { "Shortcut" },
                        target = target,
                        artPath = obj.optString("artPath").takeIf { it.isNotBlank() },
                        span = ShortcutSpan.fromStored(obj.optString("span").takeIf { it.isNotBlank() }),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

/** Social source for a pinned LT “Pinned Friends” friend. */
enum class CirclePinSource {
    Steam,
    Discord,
    XoraNetwork,
}

/** One pinned friend in Pinned Friends (SteamID64 or Discord user id). */
data class CirclePin(
    val source: CirclePinSource,
    val id: String,
) {
    val key: String get() = "${source.name}:$id"
}

/** Max friends the user can pin in LT “Pinned Friends”. */
const val CIRCLE_FRIEND_LIMIT = 5

internal fun encodeStringIdSet(ids: Set<String>): String {
    val array = JSONArray()
    ids.forEach { id -> if (id.isNotBlank()) array.put(id) }
    return array.toString()
}

internal fun decodeStringIdSet(raw: String): Set<String> {
    if (raw.isBlank()) return emptySet()
    return runCatching {
        val array = JSONArray(raw)
        buildSet {
            for (i in 0 until array.length()) {
                val id = array.optString(i).trim()
                if (id.isNotEmpty()) add(id)
            }
        }
    }.getOrDefault(emptySet())
}

/** Cover-art pan inside a Game Icon. Compose [BiasAlignment] uses `-1..1` on each axis. */
data class GameArtAlignment(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    val isIdentity: Boolean get() = x == 0f && y == 0f

    fun clamped(): GameArtAlignment = GameArtAlignment(
        x = x.coerceIn(-1f, 1f),
        y = y.coerceIn(-1f, 1f),
    )

    fun nudged(dx: Float, dy: Float): GameArtAlignment = GameArtAlignment(
        x = (x + dx).coerceIn(-1f, 1f),
        y = (y + dy).coerceIn(-1f, 1f),
    )
}

/** One D-pad / button step when panning cover art inside the icon. */
const val GAME_ART_ALIGN_STEP = 0.12f

internal fun encodeGameArtAlignments(map: Map<String, GameArtAlignment>): String {
    val obj = JSONObject()
    map.forEach { (id, alignment) ->
        if (id.isBlank() || alignment.isIdentity) return@forEach
        obj.put(
            id,
            JSONObject()
                .put("x", alignment.x.toDouble())
                .put("y", alignment.y.toDouble()),
        )
    }
    return obj.toString()
}

internal fun decodeGameArtAlignments(raw: String): Map<String, GameArtAlignment> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        buildMap {
            obj.keys().forEach { id ->
                val entry = obj.optJSONObject(id) ?: return@forEach
                val alignment = GameArtAlignment(
                    x = entry.optDouble("x", 0.0).toFloat(),
                    y = entry.optDouble("y", 0.0).toFloat(),
                ).clamped()
                if (!alignment.isIdentity) put(id, alignment)
            }
        }
    }.getOrDefault(emptyMap())
}

/** Longer than any real title, but short enough that a pasted essay cannot bloat the store. */
const val GAME_TITLE_MAX_LENGTH = 120

internal fun encodeGameTitleOverrides(map: Map<String, String>): String {
    val obj = JSONObject()
    map.forEach { (id, title) ->
        val cleaned = title.trim().take(GAME_TITLE_MAX_LENGTH)
        if (id.isNotBlank() && cleaned.isNotEmpty()) obj.put(id, cleaned)
    }
    return obj.toString()
}

internal fun decodeGameTitleOverrides(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        buildMap {
            obj.keys().forEach { id ->
                val title = obj.optString(id).trim().take(GAME_TITLE_MAX_LENGTH)
                if (title.isNotEmpty()) put(id, title)
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun encodeCirclePins(pins: List<CirclePin>): String {
    val array = JSONArray()
    pins.forEach { pin ->
        array.put(
            JSONObject()
                .put("source", pin.source.name)
                .put("id", pin.id),
        )
    }
    return array.toString()
}

internal fun decodeCirclePins(raw: String): List<CirclePin> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val source = runCatching {
                    CirclePinSource.valueOf(obj.optString("source"))
                }.getOrNull() ?: continue
                val id = obj.optString("id").trim()
                if (id.isEmpty()) continue
                add(CirclePin(source = source, id = id))
            }
        }
            .distinctBy { it.key }
            .take(CIRCLE_FRIEND_LIMIT)
    }.getOrDefault(emptyList())
}

const val DEFAULT_BGM_VOLUME = 0.35f

/** Default UI navigation SFX level — audible even when BGM is turned down. */
const val DEFAULT_UI_SFX_VOLUME = 0.7f

const val DEFAULT_TRAILER_IDLE_SECONDS = 5

/** Matches [com.arcadia.shell.designsystem.ShellThemeId.Default.id]. */
const val DEFAULT_SHELL_THEME_ID = "default"

/** Default shell text size — slightly under 1× so XMB titles stay compact. */
const val DEFAULT_UI_TEXT_SCALE = 0.85f
const val MIN_UI_TEXT_SCALE = 0.75f
const val MAX_UI_TEXT_SCALE = 1.3f

/** Preset steps cycled from Start → Display → Text size. */
val UI_TEXT_SCALE_PRESETS: List<Float> = listOf(0.8f, 0.85f, 1.0f, 1.15f)

fun uiTextScaleLabel(scale: Float): String = when {
    scale < 0.83f -> "Small"
    scale < 0.95f -> "Medium small"
    scale < 1.08f -> "Medium"
    else -> "Large"
}
