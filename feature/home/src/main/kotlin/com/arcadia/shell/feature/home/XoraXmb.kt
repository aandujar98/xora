package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.datastore.label
import com.arcadia.shell.launcher.music.MusicAlbum
import com.arcadia.shell.launcher.music.MusicSource
import com.arcadia.shell.launcher.music.MusicTrack
import com.arcadia.shell.launcher.photos.DeviceMediaFolder
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.PlatformSummary

/** Top-level XOrA XMB categories (horizontal strip). */
enum class XoraXmbCategory {
    Profiles,
    Settings,
    Games,
    Media,
    Videos,
    Music,
    Network,
    ;

    val label: String
        get() = when (this) {
            Profiles -> "Profiles"
            Settings -> "Settings"
            Games -> "Games"
            Media -> "Photos"
            Videos -> "Videos"
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
    /** Games → XOrA Emulator → display / controllers / bezels. */
    Emulator,
    /** Music → Link DSP Accounts → Spotify / Apple Music / YouTube Music. */
    DspAccounts,
    /** Music → Playlist → album / playlist cards. */
    MusicAlbums,
    /** Music → an album's songs (or All music). */
    MusicTracks,
    /** Music → Now Playing — full-bleed player over the cover art. */
    NowPlaying,
    /** Media → Photos — full-bleed PSP-style gallery over the wallpaper. */
    Photos,
    /** XOrA Network → Dashboard — Metro-style tile board over the wallpaper. */
    Dashboard,
}

/** One focusable row in the XMB vertical list. */
data class XoraXmbItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val action: XoraXmbAction,
    /** Optional box/hero thumb for ROM / Continue / Favorite rows. */
    val artPath: String? = null,
    /** Optional wallpaper (still or video) shown behind a focused album / track. */
    val heroPath: String? = null,
    /** Clear-logo / wheel title art — preferred over [title] text for ROM rows. */
    val logoPath: String? = null,
    /** Accumulated play time for ROM rows ([Game.playTimeMs]). */
    val playTimeMs: Long = 0,
    /** Library size for system rows, shown by the platform picker. */
    val gameCount: Int = 0,
    /** System rows: an emulator is assigned, so the platform can actually be played. */
    val ready: Boolean = false,
    /** Short console name for ROM rows, shown as a chip on the detail panel. */
    val platformLabel: String? = null,
    /** Vector glyph when [artPath] is null. */
    val icon: XmbIcon = XmbIcon.System,
)

/** Album / track / Now Playing art uses a 1×1 plate, not the landscape game card. */
fun XoraXmbItem.isMusicCoverArt(): Boolean {
    if (artPath.isNullOrBlank()) return false
    return when (action) {
        is XoraXmbAction.OpenNowPlaying,
        is XoraXmbAction.PlayMusicTrack,
        is XoraXmbAction.DrillMusicAlbum,
        -> true
        else -> id == "now" ||
            id.startsWith("album_") ||
            id.startsWith("track_") ||
            id.startsWith("music_folder_")
    }
}

/** Stable id for custom cover / wallpaper files on an album or track row. */
fun XoraXmbItem.musicCustomMediaId(): String? = when (val action = action) {
    is XoraXmbAction.DrillMusicAlbum -> "album_${action.albumId}"
    is XoraXmbAction.PlayMusicTrack -> "track_${action.trackId}"
    else -> null
}

sealed interface XoraXmbAction {
    data object OpenProfile : XoraXmbAction
    data object GuestModeStub : XoraXmbAction
    data class OpenSettingsCategory(val category: StartSettingsCategory) : XoraXmbAction
    /** Fetch the newest GitHub Releases APK and open the system installer. */
    data object InstallLatestUpdate : XoraXmbAction
    data object OpenRaLibrary : XoraXmbAction
    data object LaunchContinueOrFavorite : XoraXmbAction
    data object DrillAllGames : XoraXmbAction
    /** Games column Folder_IMG — pick a gallery still to sit in the folder window. */
    data object PickHomeFolderImage : XoraXmbAction
    /** Games → XOrA Emulator settings list. */
    data object DrillXoraEmulator : XoraXmbAction
    /** In-emulator XMB only — close overlay and keep playing. */
    data object ResumeGame : XoraXmbAction
    /** In-emulator XMB only — leave the session and return to the launcher. */
    data object QuitGame : XoraXmbAction
    data object SaveGameState : XoraXmbAction
    data object LoadGameState : XoraXmbAction
    data object ResetGame : XoraXmbAction
    /** Media → Photos — the device photo gallery. */
    data object OpenPhotos : XoraXmbAction
    /** Photos column — an album / Camera / Screenshots folder. */
    data class OpenPhotoFolder(val folderId: String) : XoraXmbAction
    data object VideosStub : XoraXmbAction
    /** Videos column — an album of videos stored in a folder. */
    data class OpenVideoFolder(val folderId: String) : XoraXmbAction
    /** Music → Now Playing page. */
    data object OpenNowPlaying : XoraXmbAction
    /** Music → Playlist → album / playlist cards. */
    data object DrillMusicAlbums : XoraXmbAction
    /** Music → All music → every song as cards. */
    data object DrillAllSongs : XoraXmbAction
    /** An album card → its songs. */
    data class DrillMusicAlbum(val albumId: String) : XoraXmbAction
    /** A song card → becomes Now Playing. */
    data class PlayMusicTrack(val trackId: String) : XoraXmbAction
    /** Music → Link DSP Accounts card rung. */
    data object DrillDspAccounts : XoraXmbAction
    /** DSP provider card — start OAuth / show linked state. */
    data class LinkDspAccount(val provider: DspProvider) : XoraXmbAction
    /** XOrA Network → Dashboard — profile, friends, games & RA over the wallpaper. */
    data object OpenDashboard : XoraXmbAction
    data object StoreStub : XoraXmbAction
    data object OpenNews : XoraXmbAction
    data class DrillSystem(val platformId: String) : XoraXmbAction
    data class LaunchGame(val gameId: String) : XoraXmbAction
    /** Cycle / toggle a built-in emulator preference from the XMB list. */
    data class ToggleXoraEmulatorSetting(val setting: XoraEmulatorXmbSetting) : XoraXmbAction
    /** Jump into full Setup → XOrA Emulator (cores, storage, RA login). */
    data object OpenFullXoraEmulatorSetup : XoraXmbAction
}

/** Digital service providers under Music → Link DSP Accounts. */
enum class DspProvider {
    Spotify,
    AppleMusic,
    YoutubeMusic,
}

/** Rows under Games → XOrA Emulator. */
enum class XoraEmulatorXmbSetting {
    Aspect,
    Bezels,
    BezelOpacity,
    InternalResolution,
    ExpandDualDisplay,
    PreferredController,
    ClearButtonMappings,
    Netplay,
    /** RetroAchievements hardcore (disables save states; applies live in-session). */
    RaHardcore,
}

data class XoraXmbUiState(
    val categoryIndex: Int = 2, // Games
    val itemIndex: Int = 0,
    val depth: XoraXmbDepth = XoraXmbDepth.Category,
    /** When [depth] is Systems or Roms, the platform being browsed. */
    val drilledPlatformId: String? = null,
    val items: List<XoraXmbItem> = emptyList(),
    val gamesSecondarySlot: GamesSecondarySlot = GamesSecondarySlot.Continue,
    /** Clear-logo title icons vs text for ROM browse rows. */
    val titleStyle: XmbTitleStyle = XmbTitleStyle.TitleIcons,
    /** Detail for the hero / dual pane. */
    val focusTitle: String = "Games",
    val focusSubtitle: String? = "Browse your library",
    val focusGame: Game? = null,
) {
    val category: XoraXmbCategory
        get() = XoraXmbCategory.entries.getOrElse(categoryIndex) { XoraXmbCategory.Games }

    val selectedItem: XoraXmbItem? get() = items.getOrNull(itemIndex)

    /**
     * The bottom-right RA card only belongs next to a real game (recents plate, a ROM,
     * or in-session Resume). Trophy / Device / Folder and every other category stay clean.
     */
    val showsAchievementsCard: Boolean
        get() {
            if (focusGame == null) return false
            return when (selectedItem?.action) {
                is XoraXmbAction.LaunchGame,
                is XoraXmbAction.LaunchContinueOrFavorite,
                is XoraXmbAction.ResumeGame,
                -> true
                else -> depth == XoraXmbDepth.Roms
            }
        }
}

/** Home Games column: Trophy, recents plate, Device library, Folder_IMG. */
const val GAMES_ITEM_TROPHY = 0
const val GAMES_ITEM_RECENTS = 1
const val GAMES_ITEM_LIBRARY = 2
const val GAMES_ITEM_FOLDER = 3

fun defaultXoraCategoryItemIndex(category: XoraXmbCategory): Int =
    if (category == XoraXmbCategory.Games) GAMES_ITEM_RECENTS else 0

fun buildXoraCategoryItems(
    category: XoraXmbCategory,
    /** The player's own display name — not whichever emulator would launch the selected game. */
    profileName: String,
    /** Local path or CDN url for the player's avatar, shown on the profile row. */
    profileAvatarPath: String? = null,
    gamesSecondarySlot: GamesSecondarySlot,
    continueGame: Game?,
    favoriteGame: Game?,
    /**
     * When true (PS3-style in-emulator XMB), include the XOrA Emulator row.
     * Hidden on the launcher Home XMB — only appears while a game session is open.
     */
    showXoraEmulator: Boolean = false,
    /** "Title — Artist" for the Music → Now Playing row. */
    nowPlayingLabel: String? = null,
    /** Cover art for the Music → Now Playing row. */
    nowPlayingArtPath: String? = null,
    /** Gallery still cropped into the Games column Folder_IMG window. */
    homeFolderImagePath: String? = null,
    /** Camera / Screenshots albums listed under Photos. */
    photoFolders: List<DeviceMediaFolder> = emptyList(),
    /** Video albums listed under Videos. */
    videoFolders: List<DeviceMediaFolder> = emptyList(),
    /** On-device albums listed under Music as Folder_Music rows. */
    musicFolders: List<MusicAlbum> = emptyList(),
): List<XoraXmbItem> = when (category) {
    XoraXmbCategory.Profiles -> listOf(
        XoraXmbItem(
            id = "profile",
            title = profileName.ifBlank { "Player" },
            subtitle = "Edit name & avatar",
            action = XoraXmbAction.OpenProfile,
            artPath = profileAvatarPath,
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
        XoraXmbItem(
            id = "set_update",
            title = "Update",
            subtitle = "Install latest GitHub build",
            action = XoraXmbAction.InstallLatestUpdate,
            icon = XmbIcon.General,
        ),
    )
    XoraXmbCategory.Games -> {
        val secondary = when (gamesSecondarySlot) {
            GamesSecondarySlot.Continue -> XoraXmbItem(
                id = "continue",
                title = continueGame?.title ?: "Game 0",
                subtitle = "Recently Played",
                action = XoraXmbAction.LaunchContinueOrFavorite,
                artPath = continueGame?.heroImagePath ?: continueGame?.boxArtPath,
                logoPath = continueGame?.logoImagePath,
                playTimeMs = continueGame?.playTimeMs ?: 0L,
                platformLabel = continueGame?.platform?.displayName,
                icon = XmbIcon.Continue,
            )
            GamesSecondarySlot.Favorite -> XoraXmbItem(
                id = "favorite",
                title = favoriteGame?.title ?: "Game 0",
                subtitle = "Recently Played",
                action = XoraXmbAction.LaunchContinueOrFavorite,
                artPath = favoriteGame?.heroImagePath ?: favoriteGame?.boxArtPath,
                logoPath = favoriteGame?.logoImagePath,
                playTimeMs = favoriteGame?.playTimeMs ?: 0L,
                platformLabel = favoriteGame?.platform?.displayName,
                icon = XmbIcon.Favorite,
            )
        }
        buildList {
            if (showXoraEmulator) {
                // PS3-style in-session Games column — Resume first, then emulator prefs.
                add(
                    XoraXmbItem(
                        id = "resume",
                        title = "Resume",
                        subtitle = continueGame?.title ?: "Back to game",
                        action = XoraXmbAction.ResumeGame,
                        artPath = continueGame?.boxArtPath ?: continueGame?.heroImagePath,
                        logoPath = continueGame?.logoImagePath,
                        icon = XmbIcon.Continue,
                    ),
                )
                add(
                    XoraXmbItem(
                        id = "xora_emulator",
                        title = "XOrA Emulator",
                        subtitle = "Controllers, bezels & display",
                        action = XoraXmbAction.DrillXoraEmulator,
                        icon = XmbIcon.Emulator,
                    ),
                )
                add(
                    XoraXmbItem(
                        id = "save_state",
                        title = "Save state",
                        subtitle = "Slot 0",
                        action = XoraXmbAction.SaveGameState,
                        icon = XmbIcon.Folder,
                    ),
                )
                add(
                    XoraXmbItem(
                        id = "load_state",
                        title = "Load state",
                        subtitle = "Slot 0",
                        action = XoraXmbAction.LoadGameState,
                        icon = XmbIcon.Folder,
                    ),
                )
                add(
                    XoraXmbItem(
                        id = "reset_game",
                        title = "Reset",
                        subtitle = "Restart the soft session",
                        action = XoraXmbAction.ResetGame,
                        icon = XmbIcon.General,
                    ),
                )
                add(
                    XoraXmbItem(
                        id = "quit_game",
                        title = "Quit to XOrA",
                        subtitle = "Leave this game",
                        action = XoraXmbAction.QuitGame,
                        icon = XmbIcon.Settings,
                    ),
                )
            } else {
                add(
                    XoraXmbItem(
                        id = "ra",
                        title = "Retro Achievements",
                        subtitle = "Progress & hardcore library",
                        action = XoraXmbAction.OpenRaLibrary,
                        icon = XmbIcon.Trophy,
                    ),
                )
                add(secondary)
                add(
                    XoraXmbItem(
                        id = "all_games",
                        title = "Library",
                        subtitle = "Platforms & titles",
                        action = XoraXmbAction.DrillAllGames,
                        icon = XmbIcon.Device,
                    ),
                )
                add(
                    XoraXmbItem(
                        id = "home_folder",
                        title = "Folder",
                        subtitle = if (homeFolderImagePath.isNullOrBlank()) {
                            "Attach a cover from Gallery"
                        } else {
                            "Custom folder"
                        },
                        action = XoraXmbAction.PickHomeFolderImage,
                        artPath = homeFolderImagePath,
                        icon = XmbIcon.Folder,
                    ),
                )
            }
        }
    }
    XoraXmbCategory.Media -> buildList {
        add(
            XoraXmbItem(
                id = "photos",
                title = "Photos",
                subtitle = "Pictures & screenshots on this device",
                action = XoraXmbAction.OpenPhotos,
                icon = XmbIcon.FolderPhoto,
            ),
        )
        addAll(photoFolderItems(photoFolders))
    }
    XoraXmbCategory.Videos -> buildList {
        add(
            XoraXmbItem(
                id = "videos",
                title = "Videos",
                subtitle = "Clips on this device",
                action = XoraXmbAction.VideosStub,
                icon = XmbIcon.FolderVideo,
            ),
        )
        addAll(videoFolderItems(videoFolders))
    }
    XoraXmbCategory.Music -> buildList {
        add(
            XoraXmbItem(
                id = "now",
                title = "Now Playing",
                subtitle = nowPlayingLabel ?: "Nothing playing yet",
                action = XoraXmbAction.OpenNowPlaying,
                artPath = nowPlayingArtPath,
                icon = XmbIcon.NowPlaying,
            ),
        )
        add(
            XoraXmbItem(
                id = "playlist",
                title = "Playlist",
                subtitle = "Albums & playlists",
                action = XoraXmbAction.DrillMusicAlbums,
                icon = XmbIcon.Playlist,
            ),
        )
        add(
            XoraXmbItem(
                id = "all_music",
                title = "All music",
                subtitle = "Every song on this device",
                action = XoraXmbAction.DrillAllSongs,
                icon = XmbIcon.FolderMusic,
            ),
        )
        add(
            XoraXmbItem(
                id = "dsp",
                title = "Link DSP Accounts",
                subtitle = "Spotify, Apple Music & YouTube Music",
                action = XoraXmbAction.DrillDspAccounts,
                icon = XmbIcon.Dsp,
            ),
        )
        addAll(musicFolderItems(musicFolders))
    }
    XoraXmbCategory.Network -> listOf(
        XoraXmbItem(
            id = "dashboard",
            title = "Dashboard",
            subtitle = "Profile, friends & games on XOrA Network",
            action = XoraXmbAction.OpenDashboard,
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

/** Music → Playlist — album / playlist cards, browsed like the system picker. */
fun buildXoraMusicAlbumItems(albums: List<MusicAlbum>): List<XoraXmbItem> =
    albums.map { album ->
        XoraXmbItem(
            id = "album_${album.id}",
            title = album.title,
            subtitle = album.artist,
            action = XoraXmbAction.DrillMusicAlbum(album.id),
            artPath = album.artUri,
            gameCount = album.trackCount,
            icon = if (album.isPlaylist) {
                XmbIcon.Playlist
            } else if (album.source == MusicSource.Device) {
                XmbIcon.FolderMusic
            } else {
                XmbIcon.Music
            },
        )
    }

/** Music → an album's songs, or every song under All music. */
fun buildXoraMusicTrackItems(tracks: List<MusicTrack>): List<XoraXmbItem> =
    tracks.map { track ->
        XoraXmbItem(
            id = "track_${track.id}",
            title = track.title,
            subtitle = track.artist,
            action = XoraXmbAction.PlayMusicTrack(track.id),
            artPath = track.albumArtUri,
            playTimeMs = track.durationMs,
            platformLabel = track.albumTitle.takeIf { it.isNotBlank() },
            icon = XmbIcon.Music,
        )
    }

/** Music → Link DSP Accounts — provider cards; [ready] means the account is linked. */
fun buildXoraDspItems(spotifyLinked: Boolean): List<XoraXmbItem> = listOf(
    XoraXmbItem(
        id = "dsp_spotify",
        title = "Spotify",
        subtitle = if (spotifyLinked) "Linked" else "Tap to connect",
        action = XoraXmbAction.LinkDspAccount(DspProvider.Spotify),
        ready = spotifyLinked,
        icon = XmbIcon.Spotify,
    ),
    XoraXmbItem(
        id = "dsp_apple",
        title = "Apple Music",
        subtitle = "Coming soon",
        action = XoraXmbAction.LinkDspAccount(DspProvider.AppleMusic),
        ready = false,
        icon = XmbIcon.AppleMusic,
    ),
    XoraXmbItem(
        id = "dsp_youtube",
        title = "YouTube Music",
        subtitle = "Coming soon",
        action = XoraXmbAction.LinkDspAccount(DspProvider.YoutubeMusic),
        ready = false,
        icon = XmbIcon.YoutubeMusic,
    ),
)

fun buildXoraSystemItems(
    summaries: List<PlatformSummary>,
    artByPlatformId: Map<String, String> = emptyMap(),
    readyPlatformIds: Set<String> = emptySet(),
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
                gameCount = summary.gameCount,
                ready = platformId in readyPlatformIds,
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
            playTimeMs = game.playTimeMs,
            platformLabel = game.platform.shortName,
            icon = XmbIcon.Games,
        )
    }

private const val MEDIA_FOLDER_CAP = 32

private fun mediaCountLabel(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"

private fun photoFolderItems(folders: List<DeviceMediaFolder>): List<XoraXmbItem> =
    folders.take(MEDIA_FOLDER_CAP).map { folder ->
        XoraXmbItem(
            id = "photo_folder_${folder.id}",
            title = folder.title,
            subtitle = mediaCountLabel(folder.itemCount, "photo", "photos"),
            action = XoraXmbAction.OpenPhotoFolder(folder.id),
            artPath = folder.coverUri,
            gameCount = folder.itemCount,
            icon = XmbIcon.FolderPhoto,
        )
    }

private fun videoFolderItems(folders: List<DeviceMediaFolder>): List<XoraXmbItem> =
    folders.take(MEDIA_FOLDER_CAP).map { folder ->
        XoraXmbItem(
            id = "video_folder_${folder.id}",
            title = folder.title,
            subtitle = mediaCountLabel(folder.itemCount, "video", "videos"),
            action = XoraXmbAction.OpenVideoFolder(folder.id),
            artPath = folder.coverUri,
            gameCount = folder.itemCount,
            icon = XmbIcon.FolderVideo,
        )
    }

private fun musicFolderItems(albums: List<MusicAlbum>): List<XoraXmbItem> =
    albums
        .filter { !it.isPlaylist && it.source == MusicSource.Device }
        .take(MEDIA_FOLDER_CAP)
        .map { album ->
            XoraXmbItem(
                id = "music_folder_${album.id}",
                title = album.title,
                subtitle = album.artist,
                action = XoraXmbAction.DrillMusicAlbum(album.id),
                artPath = album.artUri,
                gameCount = album.trackCount,
                icon = XmbIcon.FolderMusic,
            )
        }

/** Games → XOrA Emulator — global prefs applied on the next (and live) emulator session. */
fun buildXoraEmulatorItems(
    settings: XoraEmulatorSettings,
    raHardcore: Boolean = false,
): List<XoraXmbItem> {
    val controllerLabel = settings.preferredControllerName
        .takeIf { it.isNotBlank() }
        ?: "Any controller"
    val mappingLabel = if (settings.buttonMappings.isEmpty()) {
        "Default"
    } else {
        "${settings.buttonMappings.size} custom"
    }
    return listOf(
        XoraXmbItem(
            id = "emu_aspect",
            title = "Aspect ratio",
            subtitle = settings.aspectMode.label(),
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.Aspect),
            icon = XmbIcon.Display,
        ),
        XoraXmbItem(
            id = "emu_bezels",
            title = "System bezels",
            subtitle = if (settings.bezelsEnabled) "On" else "Off",
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.Bezels),
            icon = XmbIcon.Emulator,
        ),
        XoraXmbItem(
            id = "emu_bezel_opacity",
            title = "Bezel opacity",
            subtitle = "${(settings.bezelOpacity * 100f).toInt()}%",
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.BezelOpacity),
            icon = XmbIcon.Display,
        ),
        XoraXmbItem(
            id = "emu_internal_res",
            title = "Internal resolution",
            subtitle = settings.internalResolution.label(),
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.InternalResolution),
            icon = XmbIcon.Display,
        ),
        XoraXmbItem(
            id = "emu_expand",
            title = "Expand dual display",
            subtitle = when {
                settings.expandDualDisplay -> "On · each DS/3DS screen fills a panel"
                else -> "Off"
            },
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.ExpandDualDisplay),
            icon = XmbIcon.Display,
        ),
        XoraXmbItem(
            id = "emu_controller",
            title = "Preferred controller",
            subtitle = "$controllerLabel · A cycles",
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.PreferredController),
            icon = XmbIcon.GamePad,
        ),
        XoraXmbItem(
            id = "emu_mappings",
            title = "Button mappings",
            subtitle = "$mappingLabel · A clears custom",
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.ClearButtonMappings),
            icon = XmbIcon.GamePad,
        ),
        XoraXmbItem(
            id = "emu_netplay",
            title = "Netplay menu",
            subtitle = if (settings.netplayEnabled) "On" else "Off",
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.Netplay),
            icon = XmbIcon.Network,
        ),
        XoraXmbItem(
            id = "emu_ra_hardcore",
            title = "Hardcore RetroAchievements",
            subtitle = if (raHardcore) {
                "On · save states disabled"
            } else {
                "Off · softcore"
            },
            action = XoraXmbAction.ToggleXoraEmulatorSetting(XoraEmulatorXmbSetting.RaHardcore),
            icon = XmbIcon.Trophy,
        ),
        XoraXmbItem(
            id = "emu_full_setup",
            title = "Full Setup",
            subtitle = "Cores, storage & RetroAchievements login",
            action = XoraXmbAction.OpenFullXoraEmulatorSetup,
            icon = XmbIcon.Settings,
        ),
    )
}
