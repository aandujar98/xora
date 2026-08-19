package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.ThemeMode
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.TrailerSourcePreference
import com.arcadia.shell.datastore.UiFitMode
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.datastore.uiTextScaleLabel
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.ShellThemeId
import com.arcadia.shell.model.ScreenRole

/**
 * Start-button app config popup: categorized quick settings with a glass list + icon rail.
 *
 * Rail order (top → bottom): Display, Themes, Sound, Scrape, Social, Notifications, General.
 */
enum class StartSettingsCategory {
    Display,
    Themes,
    Sound,
    Scrape,
    Social,
    Notifications,
    General,
}

sealed interface StartSettingsRow {
    val id: String
    val title: String
    val subtitle: String?
    val trailingIcon: StartSettingsTrailingIcon?

    /** Non-activatable section label (skipped by D-pad focus). */
    data class Header(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val trailingIcon: StartSettingsTrailingIcon? = null,
    ) : StartSettingsRow

    data class Action(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val trailingIcon: StartSettingsTrailingIcon? = null,
        val action: StartSettingsAction,
    ) : StartSettingsRow

    data class Toggle(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        val checked: Boolean,
        val action: StartSettingsAction,
        override val trailingIcon: StartSettingsTrailingIcon? = null,
    ) : StartSettingsRow
}

enum class StartSettingsTrailingIcon {
    Edit,
}

sealed interface StartSettingsAction {
    // Display
    data object SwitchDisplayMode : StartSettingsAction
    data object CycleSecondaryRole : StartSettingsAction
    data object CycleTrailerDisplay : StartSettingsAction
    data object CycleThemeMode : StartSettingsAction
    data object CycleFeedColumns : StartSettingsAction
    data object CycleUiTextScale : StartSettingsAction
    data object ToggleUiFitMode : StartSettingsAction
    data object OpenSystemDisplay : StartSettingsAction

    /** Apply a launcher theme pack by stable id. */
    data class SelectShellTheme(val themeId: String) : StartSettingsAction

    /** Open the Themes customize sheet (wallpaper / BGM / shortcuts). */
    data object OpenThemeCustomize : StartSettingsAction

    /** Placeholder until XOrA Store theme downloads ship. */
    data object ShopThemesComingSoon : StartSettingsAction

    /** Stub for future theme pack upload to XOrA Store. */
    data object UploadThemeToShopComingSoon : StartSettingsAction

    // Sound
    data object CycleBgmVolume : StartSettingsAction
    data object CycleUiSfxVolume : StartSettingsAction
    data object ChooseBgm : StartSettingsAction
    data object ClearBgm : StartSettingsAction

    // Scrape
    data object RefreshMetadata : StartSettingsAction
    data object ToggleScrapeAfterScan : StartSettingsAction
    data object ToggleTrailerScrape : StartSettingsAction
    data object ToggleManualScrape : StartSettingsAction
    data object ToggleIdleTrailers : StartSettingsAction
    data object CycleTrailerSource : StartSettingsAction
    data object OpenScraperCredentials : StartSettingsAction
    data object SignInRetroAchievements : StartSettingsAction
    data object ToggleRaEnabled : StartSettingsAction
    data object ToggleRaHardcore : StartSettingsAction
    data object ToggleRaShowInLauncher : StartSettingsAction

    // Social
    data object OpenSocialMenu : StartSettingsAction
    data object OpenWifiBt : StartSettingsAction
    data object OpenNotificationAccess : StartSettingsAction
    data object OpenSocialSetup : StartSettingsAction
    data object SignInWithSteam : StartSettingsAction
    data object LinkDiscord : StartSettingsAction

    // Notifications
    data object ToggleNotifications : StartSettingsAction
    data object ToggleNotificationSound : StartSettingsAction
    data object TestNotification : StartSettingsAction

    // General
    data object CycleGamesSecondarySlot : StartSettingsAction
    data object CycleXmbTitleStyle : StartSettingsAction
    data object EditHome : StartSettingsAction
    data object EditProfile : StartSettingsAction
    data object ScanEmulators : StartSettingsAction
    data object OpenAllSettings : StartSettingsAction
    data object Reboot : StartSettingsAction
    data object PowerDown : StartSettingsAction
}

data class StartSettingsUiState(
    val open: Boolean = false,
    val category: StartSettingsCategory = StartSettingsCategory.Display,
    val selectedRowIndex: Int = 0,
    val rows: List<StartSettingsRow> = emptyList(),
    /** Snapshot labels driven by live [ShellSettings]. */
    val settings: ShellSettings = ShellSettings(),
    val isScraping: Boolean = false,
    val isScanning: Boolean = false,
) {
    val selectedRow: StartSettingsRow? get() = rows.getOrNull(selectedRowIndex)
}

fun buildStartSettingsRows(
    category: StartSettingsCategory,
    settings: ShellSettings,
    isScraping: Boolean,
    isScanning: Boolean,
    hasCustomBgm: Boolean,
    hasCustomWallpaper: Boolean = false,
    shopThemeIds: List<String> = emptyList(),
    customWallpaperLabel: String = "Custom media",
    detectedResolutionLabel: String = "Unknown",
    raSettings: RetroAchievementsSettings = RetroAchievementsSettings(),
): List<StartSettingsRow> = when (category) {
    StartSettingsCategory.Display -> listOf(
        StartSettingsRow.Action(
            id = "switch_mode",
            title = "Switch Mode",
            subtitle = when (settings.displayMode) {
                DisplayMode.Single -> "Single screen"
                DisplayMode.Dual -> "Dual screen"
            },
            action = StartSettingsAction.SwitchDisplayMode,
        ),
        StartSettingsRow.Action(
            id = "second_screen",
            title = "Second screen shows",
            subtitle = when (settings.secondaryDisplayRole) {
                ScreenRole.Hero -> "Artwork"
                ScreenRole.Grid -> "Library"
            },
            action = StartSettingsAction.CycleSecondaryRole,
        ),
        StartSettingsRow.Action(
            id = "ui_fit",
            title = "Fit screen resolution",
            subtitle = when (settings.uiFitMode) {
                UiFitMode.Auto -> "Auto · $detectedResolutionLabel"
                UiFitMode.System -> "Off · System density"
            },
            action = StartSettingsAction.ToggleUiFitMode,
        ),
        StartSettingsRow.Action(
            id = "trailer_display",
            title = "Trailer display",
            subtitle = when (settings.trailerDisplayMode) {
                TrailerDisplayMode.FullBackground -> "Full background"
                TrailerDisplayMode.CornerPip -> "Corner PIP"
            },
            action = StartSettingsAction.CycleTrailerDisplay,
        ),
        StartSettingsRow.Action(
            id = "theme",
            title = "Theme",
            subtitle = when (settings.themeMode) {
                ThemeMode.System -> "System"
                ThemeMode.Light -> "Light"
                ThemeMode.Dark -> "Dark"
            },
            action = StartSettingsAction.CycleThemeMode,
        ),
        StartSettingsRow.Action(
            id = "feed_columns",
            title = "Feed columns",
            subtitle = settings.gridColumns.toString(),
            action = StartSettingsAction.CycleFeedColumns,
        ),
        StartSettingsRow.Action(
            id = "text_size",
            title = "Text size",
            subtitle = uiTextScaleLabel(settings.uiTextScale),
            action = StartSettingsAction.CycleUiTextScale,
        ),
        StartSettingsRow.Action(
            id = "system_display",
            title = "Display settings",
            subtitle = "Brightness & system display",
            action = StartSettingsAction.OpenSystemDisplay,
        ),
    )

    StartSettingsCategory.Themes -> buildList {
        val active = ShellThemeId.fromId(settings.shellThemeId)
        add(
            StartSettingsRow.Header(
                id = "hdr_presets",
                title = "Presets",
                subtitle = "Launcher theme packs",
            ),
        )
        ShellThemeCatalog.all.forEach { theme ->
            val selected = theme.id == active
            add(
                StartSettingsRow.Action(
                    id = "theme_${theme.id.id}",
                    title = theme.id.displayName,
                    subtitle = buildString {
                        append(theme.description)
                        if (selected) append(" · Active")
                        theme.bgm?.let { bgm ->
                            append(" · BGM: ")
                            append(bgm.displayHint)
                        }
                    },
                    action = StartSettingsAction.SelectShellTheme(theme.id.id),
                ),
            )
        }
        add(
            StartSettingsRow.Header(
                id = "hdr_shop",
                title = "From XOrA Store",
                subtitle = "Downloaded theme packs",
            ),
        )
        if (shopThemeIds.isEmpty()) {
            add(
                StartSettingsRow.Action(
                    id = "shop_themes_empty",
                    title = "Coming from XOrA Store",
                    subtitle = "Downloadable themes will appear here",
                    action = StartSettingsAction.ShopThemesComingSoon,
                ),
            )
        } else {
            shopThemeIds.forEach { themeId ->
                add(
                    StartSettingsRow.Action(
                        id = "shop_theme_$themeId",
                        title = themeId,
                        subtitle = "Installed from XOrA Store",
                        action = StartSettingsAction.SelectShellTheme(themeId),
                    ),
                )
            }
        }
        add(
            StartSettingsRow.Header(
                id = "hdr_customize",
                title = "Customize",
                subtitle = "Your wallpaper & soundtrack",
            ),
        )
        add(
            StartSettingsRow.Action(
                id = "theme_customize",
                title = "Customize…",
                subtitle = buildString {
                    append(if (hasCustomWallpaper) customWallpaperLabel else "Theme backdrop")
                    append(" · ")
                    append(if (hasCustomBgm) "Custom BGM" else "Theme / default BGM")
                },
                trailingIcon = StartSettingsTrailingIcon.Edit,
                action = StartSettingsAction.OpenThemeCustomize,
            ),
        )
        add(
            StartSettingsRow.Action(
                id = "upload_theme_shop",
                title = "Upload theme to XOrA Store",
                subtitle = "Coming soon",
                action = StartSettingsAction.UploadThemeToShopComingSoon,
            ),
        )
    }

    StartSettingsCategory.Sound -> buildList {
        add(
            StartSettingsRow.Action(
                id = "bgm_volume",
                title = "Background music",
                subtitle = "${(settings.bgmVolume * 100f).toInt()}% · A cycles",
                action = StartSettingsAction.CycleBgmVolume,
            ),
        )
        add(
            StartSettingsRow.Action(
                id = "sfx_volume",
                title = "UI sounds",
                subtitle = "${(settings.uiSfxVolume * 100f).toInt()}% · A cycles",
                action = StartSettingsAction.CycleUiSfxVolume,
            ),
        )
        add(
            StartSettingsRow.Action(
                id = "choose_bgm",
                title = "Choose soundtrack",
                subtitle = if (hasCustomBgm) "Custom track" else "Default soundtrack",
                action = StartSettingsAction.ChooseBgm,
            ),
        )
        if (hasCustomBgm) {
            add(
                StartSettingsRow.Action(
                    id = "clear_bgm",
                    title = "Restore default soundtrack",
                    action = StartSettingsAction.ClearBgm,
                ),
            )
        }
    }

    StartSettingsCategory.Scrape -> listOf(
        StartSettingsRow.Action(
            id = "refresh_metadata",
            title = "Refresh Metadata",
            subtitle = when {
                isScanning -> "Scanning…"
                isScraping -> "Fetching artwork…"
                else -> "Scan library & fetch artwork"
            },
            action = StartSettingsAction.RefreshMetadata,
        ),
        StartSettingsRow.Toggle(
            id = "scrape_after_scan",
            title = "Auto-scrape after scan",
            checked = settings.scrapeAfterScan,
            action = StartSettingsAction.ToggleScrapeAfterScan,
        ),
        StartSettingsRow.Toggle(
            id = "trailer_scrape",
            title = "Scrape trailers",
            checked = settings.trailerScrapeEnabled,
            action = StartSettingsAction.ToggleTrailerScrape,
        ),
        StartSettingsRow.Toggle(
            id = "manual_scrape",
            title = "Scrape game manuals",
            subtitle = if (settings.manualScrapeEnabled) {
                "On · needs ScreenScraper · large downloads"
            } else {
                "Off · read manuals on the companion screen"
            },
            checked = settings.manualScrapeEnabled,
            action = StartSettingsAction.ToggleManualScrape,
        ),
        StartSettingsRow.Toggle(
            id = "idle_trailers",
            title = "Idle trailers",
            checked = settings.trailerEnabled,
            action = StartSettingsAction.ToggleIdleTrailers,
        ),
        StartSettingsRow.Action(
            id = "trailer_source",
            title = "Trailer source",
            subtitle = trailerSourceLabel(settings.trailerSourcePreference),
            action = StartSettingsAction.CycleTrailerSource,
        ),
        StartSettingsRow.Action(
            id = "ra_signin",
            title = "Sign in to RetroAchievements",
            subtitle = "Username & password in Setup",
            action = StartSettingsAction.SignInRetroAchievements,
        ),
        StartSettingsRow.Toggle(
            id = "ra_enabled",
            title = "RetroAchievements",
            subtitle = if (raSettings.enabled) {
                "On · XOrA Emulator + launcher"
            } else {
                "Off"
            },
            checked = raSettings.enabled,
            action = StartSettingsAction.ToggleRaEnabled,
        ),
        StartSettingsRow.Toggle(
            id = "ra_hardcore",
            title = "Hardcore mode",
            subtitle = if (raSettings.hardcore) {
                "On · no save states in-emulator"
            } else {
                "Off · softcore"
            },
            checked = raSettings.hardcore,
            action = StartSettingsAction.ToggleRaHardcore,
        ),
        StartSettingsRow.Toggle(
            id = "ra_in_launcher",
            title = "Show RA in launcher",
            subtitle = "XMB shard & library shortcuts",
            checked = raSettings.showInLauncher,
            action = StartSettingsAction.ToggleRaShowInLauncher,
        ),
        StartSettingsRow.Action(
            id = "scraper_creds",
            title = "Scraper credentials",
            subtitle = "ScreenScraper, IGDB, SteamGridDB",
            action = StartSettingsAction.OpenScraperCredentials,
        ),
    )

    StartSettingsCategory.Social -> listOf(
        StartSettingsRow.Action(
            id = "open_social",
            title = "Open Social",
            subtitle = "Pinned Friends, Steam & Discord",
            action = StartSettingsAction.OpenSocialMenu,
        ),
        StartSettingsRow.Action(
            id = "steam_signin",
            title = "Sign in with Steam",
            subtitle = "OpenID for SteamID64; API key still required once",
            action = StartSettingsAction.SignInWithSteam,
        ),
        StartSettingsRow.Action(
            id = "wifi_bt",
            title = "WIFI/BT Settings",
            subtitle = "System wireless & Bluetooth",
            action = StartSettingsAction.OpenWifiBt,
        ),
        StartSettingsRow.Action(
            id = "notification_access",
            title = "Notification access",
            subtitle = "Conversations & message previews",
            action = StartSettingsAction.OpenNotificationAccess,
        ),
        StartSettingsRow.Action(
            id = "social_setup",
            title = "Steam & Discord setup",
            subtitle = "API key, Discord link & Application ID",
            action = StartSettingsAction.OpenSocialSetup,
        ),
        StartSettingsRow.Action(
            id = "link_discord",
            title = "Link Discord account",
            subtitle = "Rich Presence via Social SDK",
            action = StartSettingsAction.LinkDiscord,
        ),
    )

    StartSettingsCategory.Notifications -> listOf(
        StartSettingsRow.Toggle(
            id = "banners_enabled",
            title = "Show notifications",
            subtitle = "Banners in XOrA - system alerts when away",
            checked = settings.notificationsEnabled,
            action = StartSettingsAction.ToggleNotifications,
        ),
        StartSettingsRow.Toggle(
            id = "notification_sound",
            title = "Notification sound",
            subtitle = if (settings.notificationSoundEnabled) "On · banners & system alerts" else "Off",
            checked = settings.notificationSoundEnabled,
            action = StartSettingsAction.ToggleNotificationSound,
        ),
        StartSettingsRow.Action(
            id = "test_notification",
            title = "Test notification",
            subtitle = "Preview banner & chime",
            action = StartSettingsAction.TestNotification,
        ),
    )

    StartSettingsCategory.General -> listOf(
        StartSettingsRow.Action(
            id = "games_secondary_slot",
            title = "Games secondary slot",
            subtitle = when (settings.gamesSecondarySlot) {
                "Favorite" -> "Favorite"
                else -> "Continue"
            },
            action = StartSettingsAction.CycleGamesSecondarySlot,
        ),
        StartSettingsRow.Action(
            id = "xmb_title_style",
            title = "XMB game titles",
            subtitle = when (settings.xmbTitleStyle) {
                XmbTitleStyle.TitleIcons -> "Title icons"
                XmbTitleStyle.Text -> "Text"
            },
            action = StartSettingsAction.CycleXmbTitleStyle,
        ),
        StartSettingsRow.Action(
            id = "edit_home",
            title = "Edit Home",
            subtitle = "Wallpaper, soundtrack & shortcuts",
            trailingIcon = StartSettingsTrailingIcon.Edit,
            action = StartSettingsAction.EditHome,
        ),
        StartSettingsRow.Action(
            id = "edit_profile",
            title = "Edit Profile",
            subtitle = "Name & avatar",
            trailingIcon = StartSettingsTrailingIcon.Edit,
            action = StartSettingsAction.EditProfile,
        ),
        StartSettingsRow.Action(
            id = "scan_emulators",
            title = "Scan for emulators",
            subtitle = "Detect Cemu, Eden, Dolphin, RetroArch cores…",
            action = StartSettingsAction.ScanEmulators,
        ),
        StartSettingsRow.Action(
            id = "all_settings",
            title = "Advanced Settings",
            subtitle = "XOrA Setup, cores & library",
            action = StartSettingsAction.OpenAllSettings,
        ),
        StartSettingsRow.Action(
            id = "reboot",
            title = "Reboot",
            subtitle = "Restart this device",
            action = StartSettingsAction.Reboot,
        ),
        StartSettingsRow.Action(
            id = "power_down",
            title = "Power Off",
            subtitle = "Shut down this device",
            action = StartSettingsAction.PowerDown,
        ),
    )
}

private fun trailerSourceLabel(preference: TrailerSourcePreference): String = when (preference) {
    TrailerSourcePreference.Auto -> "Auto"
    TrailerSourcePreference.YouTube -> "YouTube"
    TrailerSourcePreference.Steam -> "Steam"
    TrailerSourcePreference.ScreenScraper -> "ScreenScraper"
    TrailerSourcePreference.Igdb -> "IGDB"
}

fun StartSettingsCategory.next(): StartSettingsCategory {
    val values = StartSettingsCategory.entries
    return values[(ordinal + 1) % values.size]
}

fun StartSettingsCategory.previous(): StartSettingsCategory {
    val values = StartSettingsCategory.entries
    return values[(ordinal - 1 + values.size) % values.size]
}
