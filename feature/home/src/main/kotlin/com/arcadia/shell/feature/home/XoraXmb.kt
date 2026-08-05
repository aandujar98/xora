package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import com.arcadia.shell.model.PlatformSummary

/** Top-level XOrA XMB categories (horizontal strip). */
enum class XoraXmbCategory {
    Profiles,
    Settings,
    Games,
    Media,
    Music,
    Network,
    ;

    val label: String
        get() = when (this) {
            Profiles -> "Profiles"
            Settings -> "Settings"
            Games -> "Games"
            Media -> "Media"
            Music -> "Music"
            Network -> "XOrA Network"
        }
}

/**
 * Whether the Games category’s secondary slot shows Continue or Favorite.
 * Persisted in ShellPreferences.
 */
enum class GamesSecondarySlot {
    Continue,
    Favorite,
}

/** How deep the XMB drill stack is under the focused category. */
enum class XoraXmbDepth {
    /** Vertical items for the current category. */
    Category,
    /** Games → All Games → system list. */
    Systems,
    /** Games → All Games → system → ROM list. */
    Roms,
}

/** One focusable row in the XMB vertical list. */
data class XoraXmbItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val action: XoraXmbAction,
    /** Optional box/hero thumb for ROM / Continue / Favorite rows. */
    val artPath: String? = null,
    /** Clear-logo / wheel title art — preferred over [title] text for ROM rows. */
    val logoPath: String? = null,
    /** Vector glyph when [artPath] is null. */
    val icon: XmbIcon = XmbIcon.System,
)

sealed interface XoraXmbAction {
    data object OpenProfile : XoraXmbAction
    data object GuestModeStub : XoraXmbAction
    data class OpenSettingsCategory(val category: StartSettingsCategory) : XoraXmbAction
    data object OpenRaLibrary : XoraXmbAction
    data object LaunchContinueOrFavorite : XoraXmbAction
    data object DrillAllGames : XoraXmbAction
    data object PhotosStub : XoraXmbAction
    data object VideosStub : XoraXmbAction
    data object MusicNowPlayingStub : XoraXmbAction
    data object MusicPlaylistStub : XoraXmbAction
    data object MusicAllStub : XoraXmbAction
    data object MusicDspStub : XoraXmbAction
    data object OpenFriends : XoraXmbAction
    data object StoreStub : XoraXmbAction
    data object OpenNews : XoraXmbAction
    data class DrillSystem(val platformId: String) : XoraXmbAction
    data class LaunchGame(val gameId: String) : XoraXmbAction
}

data class XoraXmbUiState(
    val categoryIndex: Int = 2, // Games
    val itemIndex: Int = 0,
    val depth: XoraXmbDepth = XoraXmbDepth.Category,
    /** When [depth] is Systems or Roms, the platform being browsed. */
    val drilledPlatformId: String? = null,
    val items: List<XoraXmbItem> = emptyList(),
    val gamesSecondarySlot: GamesSecondarySlot = GamesSecondarySlot.Continue,
    /** Detail for the hero / dual pane. */
    val focusTitle: String = "Games",
    val focusSubtitle: String? = "Browse your library",
    val focusGame: Game? = null,
) {
    val category: XoraXmbCategory
        get() = XoraXmbCategory.entries.getOrElse(categoryIndex) { XoraXmbCategory.Games }

    val selectedItem: XoraXmbItem? get() = items.getOrNull(itemIndex)
}

fun buildXoraCategoryItems(
    category: XoraXmbCategory,
    profileName: String,
    gamesSecondarySlot: GamesSecondarySlot,
    continueGame: Game?,
    favoriteGame: Game?,
): List<XoraXmbItem> = when (category) {
    XoraXmbCategory.Profiles -> listOf(
        XoraXmbItem(
            id = "profile",
            title = profileName.ifBlank { "Player" },
            subtitle = "Edit name & avatar",
            action = XoraXmbAction.OpenProfile,
            icon = XmbIcon.User,
        ),
        XoraXmbItem(
            id = "guest",
            title = "Guest Mode",
            subtitle = "Coming soon",
            action = XoraXmbAction.GuestModeStub,
            icon = XmbIcon.Guest,
        ),
    )
    XoraXmbCategory.Settings -> listOf(
        XoraXmbItem(
            id = "set_general",
            title = "General",
            subtitle = "Home, profile & power",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.General),
            icon = XmbIcon.General,
        ),
        XoraXmbItem(
            id = "set_display",
            title = "Display",
            subtitle = "Single / dual & appearance",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.Display),
            icon = XmbIcon.Display,
        ),
        XoraXmbItem(
            id = "set_themes",
            title = "Themes",
            subtitle = "Presets, wallpaper & BGM",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.Themes),
            icon = XmbIcon.Themes,
        ),
        XoraXmbItem(
            id = "set_sound",
            title = "Sound",
            subtitle = "Volumes & soundtrack",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.Sound),
            icon = XmbIcon.Sound,
        ),
        XoraXmbItem(
            id = "set_scrape",
            title = "Scrape",
            subtitle = "Artwork, trailers & manuals",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.Scrape),
            icon = XmbIcon.Scrape,
        ),
        XoraXmbItem(
            id = "set_social",
            title = "Social Integration",
            subtitle = "Steam, Discord & messages",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.Social),
            icon = XmbIcon.Social,
        ),
        XoraXmbItem(
            id = "set_notify",
            title = "Notifications",
            subtitle = "Banners & sounds",
            action = XoraXmbAction.OpenSettingsCategory(StartSettingsCategory.Notifications),
            icon = XmbIcon.Notifications,
        ),
    )
    XoraXmbCategory.Games -> {
        val secondary = when (gamesSecondarySlot) {
            GamesSecondarySlot.Continue -> XoraXmbItem(
                id = "continue",
                title = "Continue",
                subtitle = continueGame?.title ?: "No recent game yet",
                action = XoraXmbAction.LaunchContinueOrFavorite,
                artPath = continueGame?.boxArtPath ?: continueGame?.heroImagePath,
                logoPath = continueGame?.logoImagePath,
                icon = XmbIcon.Continue,
            )
            GamesSecondarySlot.Favorite -> XoraXmbItem(
                id = "favorite",
                title = "Favorite",
                subtitle = favoriteGame?.title ?: "No favourite pinned yet",
                action = XoraXmbAction.LaunchContinueOrFavorite,
                artPath = favoriteGame?.boxArtPath ?: favoriteGame?.heroImagePath,
                logoPath = favoriteGame?.logoImagePath,
                icon = XmbIcon.Favorite,
            )
        }
        listOf(
            XoraXmbItem(
                id = "ra",
                title = "Retro Achievements",
                subtitle = "Progress & hardcore library",
                action = XoraXmbAction.OpenRaLibrary,
                icon = XmbIcon.Trophy,
            ),
            secondary,
            XoraXmbItem(
                id = "all_games",
                title = "All Games",
                subtitle = "Browse by system",
                action = XoraXmbAction.DrillAllGames,
                icon = XmbIcon.Folder,
            ),
        )
    }
    XoraXmbCategory.Media -> listOf(
        XoraXmbItem(
            id = "photos",
            title = "Photos",
            subtitle = "Coming soon",
            action = XoraXmbAction.PhotosStub,
            icon = XmbIcon.Photo,
        ),
        XoraXmbItem(
            id = "videos",
            title = "Videos",
            subtitle = "Coming soon",
            action = XoraXmbAction.VideosStub,
            icon = XmbIcon.Video,
        ),
    )
    XoraXmbCategory.Music -> listOf(
        XoraXmbItem(
            id = "now",
            title = "Now Playing",
            subtitle = "Coming soon",
            action = XoraXmbAction.MusicNowPlayingStub,
            icon = XmbIcon.NowPlaying,
        ),
        XoraXmbItem(
            id = "playlist",
            title = "Playlist",
            subtitle = "Coming soon",
            action = XoraXmbAction.MusicPlaylistStub,
            icon = XmbIcon.Playlist,
        ),
        XoraXmbItem(
            id = "all_music",
            title = "All music",
            subtitle = "Coming soon",
            action = XoraXmbAction.MusicAllStub,
            icon = XmbIcon.Music,
        ),
        XoraXmbItem(
            id = "dsp",
            title = "DSP Integration",
            subtitle = "Spotify & Apple Music — coming soon",
            action = XoraXmbAction.MusicDspStub,
            icon = XmbIcon.Dsp,
        ),
    )
    XoraXmbCategory.Network -> listOf(
        XoraXmbItem(
            id = "friends",
            title = "Friends",
            subtitle = "Circle, Steam & Discord",
            action = XoraXmbAction.OpenFriends,
            icon = XmbIcon.Friends,
        ),
        XoraXmbItem(
            id = "store",
            title = "XOrA Store",
            subtitle = "Coming soon",
            action = XoraXmbAction.StoreStub,
            icon = XmbIcon.Store,
        ),
        XoraXmbItem(
            id = "news",
            title = "XOrA News",
            subtitle = "Gaming & emulation feed",
            action = XoraXmbAction.OpenNews,
            icon = XmbIcon.News,
        ),
    )
}

fun buildXoraSystemItems(
    summaries: List<PlatformSummary>,
    artByPlatformId: Map<String, String> = emptyMap(),
): List<XoraXmbItem> =
    summaries
        .filter { it.gameCount > 0 && it.platform.id != "android" }
        .sortedBy { it.platform.displayName.lowercase() }
        .map { summary ->
            val platformId = summary.platform.id
            XoraXmbItem(
                id = "sys_$platformId",
                title = summary.platform.displayName,
                subtitle = "${summary.gameCount} game${if (summary.gameCount == 1) "" else "s"}",
                action = XoraXmbAction.DrillSystem(platformId),
                artPath = artByPlatformId[platformId],
                icon = XmbIcon.GamePad,
            )
        }

fun buildXoraRomItems(games: List<Game>): List<XoraXmbItem> =
    games.map { game ->
        XoraXmbItem(
            id = "rom_${game.id}",
            title = game.title,
            subtitle = if (game.favorite) "Favourite" else game.platform.shortName,
            action = XoraXmbAction.LaunchGame(game.id),
            artPath = game.boxArtPath ?: game.heroImagePath,
            logoPath = game.logoImagePath,
            icon = XmbIcon.Games,
        )
    }
