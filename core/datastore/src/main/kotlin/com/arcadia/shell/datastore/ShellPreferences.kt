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
    /** Trailer replaces the full hero artwork behind UI chrome. */
    FullBackground,
    /** Trailer plays in a lower-right picture-in-picture region. */
    CornerPip,
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
    val trailerDisplayMode: TrailerDisplayMode = TrailerDisplayMode.FullBackground,
    /** Seconds without input before an idle trailer may start. */
    val trailerIdleSeconds: Int = DEFAULT_TRAILER_IDLE_SECONDS,
    /**
     * Absolute path to a user-picked Home wallpaper. Null / blank uses the built-in
     * `sora_home_wallpaper` drawable.
     */
    val homeWallpaperPath: String? = null,
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
    /**
     * Legacy: when true and no Choose Emulator entry for N64, launch via RetroArch
     * Mupen64Plus-Next. Superseded by per-platform Choose Emulator; kept so existing
     * installs keep working until the user picks an emulator.
     */
    val n64UseMupen64PlusNext: Boolean = false,
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
                ?: TrailerDisplayMode.FullBackground,
            trailerIdleSeconds = (prefs[Keys.TRAILER_IDLE_SECONDS] ?: DEFAULT_TRAILER_IDLE_SECONDS)
                .coerceIn(5, 60),
            homeWallpaperPath = prefs[Keys.HOME_WALLPAPER_PATH]?.takeIf { it.isNotBlank() },
            customBgmPath = prefs[Keys.CUSTOM_BGM_PATH]?.takeIf { it.isNotBlank() },
            musicLibraryPath = prefs[Keys.MUSIC_LIBRARY_PATH]?.takeIf { it.isNotBlank() },
            shellThemeId = prefs[Keys.SHELL_THEME_ID]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SHELL_THEME_ID,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            notificationSoundEnabled = prefs[Keys.NOTIFICATION_SOUND_ENABLED] ?: true,
            n64UseMupen64PlusNext = prefs[Keys.N64_USE_MUPEN64PLUS_NEXT] ?: false,
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
            expandDualDisplay = prefs[Keys.XORA_EXPAND_DUAL] ?: false,
            aspectMode = prefs[Keys.XORA_ASPECT]
                ?.let { runCatching { XoraAspectMode.valueOf(it) }.getOrNull() }
                ?: XoraAspectMode.Core,
            integerScale = (prefs[Keys.XORA_INTEGER_SCALE] ?: 0).coerceIn(0, 8),
            internalResolution = prefs[Keys.XORA_INTERNAL_RES]
                ?.let { runCatching { XoraInternalResolution.valueOf(it) }.getOrNull() }
                ?: XoraInternalResolution.Native,
            bezelsEnabled = prefs[Keys.XORA_BEZELS_ENABLED] ?: true,
            bezelOpacity = (prefs[Keys.XORA_BEZEL_OPACITY] ?: 0.88f).coerceIn(0f, 1f),
            netplayEnabled = prefs[Keys.XORA_NETPLAY_ENABLED] ?: false,
            netplayNickname = prefs[Keys.XORA_NETPLAY_NICK]
                ?.takeIf { it.isNotBlank() } ?: "Player",
            netplayPort = (prefs[Keys.XORA_NETPLAY_PORT] ?: DEFAULT_NETPLAY_PORT)
                .coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT),
            netplaySpectator = prefs[Keys.XORA_NETPLAY_SPECTATOR] ?: false,
            netplayUseRelay = prefs[Keys.XORA_NETPLAY_RELAY] ?: false,
            netplayHostAddress = prefs[Keys.XORA_NETPLAY_HOST].orEmpty(),
            preferredControllerName = prefs[Keys.XORA_CONTROLLER_NAME].orEmpty(),
            buttonMappings = decodeButtonMappings(prefs[Keys.XORA_BUTTON_MAPPINGS].orEmpty()),
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
     * Mixed Steam + Discord pins for the LT “Your Circle” strip
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

    suspend fun setHomeWallpaperPath(path: String?) = edit {
        if (path.isNullOrBlank()) it.remove(Keys.HOME_WALLPAPER_PATH)
        else it[Keys.HOME_WALLPAPER_PATH] = path
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

    suspend fun setXoraBezelOpacity(opacity: Float) = edit {
        it[Keys.XORA_BEZEL_OPACITY] = opacity.coerceIn(0f, 1f)
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
            AvatarSource.Default, AvatarSource.RetroAchievements, AvatarSource.Discord -> {
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
        val HOME_WALLPAPER_PATH = stringPreferencesKey("home_wallpaper_path")
        val CUSTOM_BGM_PATH = stringPreferencesKey("custom_bgm_path")
        val MUSIC_LIBRARY_PATH = stringPreferencesKey("music_library_path")
        val SHELL_THEME_ID = stringPreferencesKey("shell_theme_id")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_SOUND_ENABLED = booleanPreferencesKey("notification_sound_enabled")
        val N64_USE_MUPEN64PLUS_NEXT = booleanPreferencesKey("n64_use_mupen64plus_next")
        val XORA_NDS_LAYOUT = stringPreferencesKey("xora_nds_screen_layout")
        val XORA_NDS_GAP = intPreferencesKey("xora_nds_screen_gap")
        val XORA_3DS_LAYOUT = stringPreferencesKey("xora_3ds_screen_layout")
        val XORA_EXPAND_DUAL = booleanPreferencesKey("xora_expand_dual_display")
        val XORA_ASPECT = stringPreferencesKey("xora_aspect_mode")
        val XORA_INTEGER_SCALE = intPreferencesKey("xora_integer_scale")
        val XORA_INTERNAL_RES = stringPreferencesKey("xora_internal_resolution")
        val XORA_BEZELS_ENABLED = booleanPreferencesKey("xora_bezels_enabled")
        val XORA_BEZEL_OPACITY = floatPreferencesKey("xora_bezel_opacity")
        val XORA_NETPLAY_ENABLED = booleanPreferencesKey("xora_netplay_enabled")
        val XORA_NETPLAY_NICK = stringPreferencesKey("xora_netplay_nickname")
        val XORA_NETPLAY_PORT = intPreferencesKey("xora_netplay_port")
        val XORA_NETPLAY_SPECTATOR = booleanPreferencesKey("xora_netplay_spectator")
        val XORA_NETPLAY_RELAY = booleanPreferencesKey("xora_netplay_relay")
        val XORA_NETPLAY_HOST = stringPreferencesKey("xora_netplay_host")
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

/** Social source for a pinned LT “Your Circle” friend. */
enum class CirclePinSource {
    Steam,
    Discord,
}

/** One pinned friend in Your Circle (SteamID64 or Discord user id). */
data class CirclePin(
    val source: CirclePinSource,
    val id: String,
) {
    val key: String get() = "${source.name}:$id"
}

/** Max friends the user can pin in LT “Your Circle”. */
const val CIRCLE_FRIEND_LIMIT = 5

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

const val DEFAULT_TRAILER_IDLE_SECONDS = 10

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
