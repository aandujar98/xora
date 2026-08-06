package com.arcadia.shell.feature.home.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.ShellThemeId
import com.arcadia.shell.feature.home.AchievementsUiState
import com.arcadia.shell.feature.home.GameCompanionAction
import com.arcadia.shell.feature.home.GameCompanionOverlay
import com.arcadia.shell.feature.home.GameCompanionUiState
import com.arcadia.shell.feature.home.GameInsightUiState
import com.arcadia.shell.feature.home.GamesSecondarySlot
import com.arcadia.shell.feature.home.GuideRow
import com.arcadia.shell.feature.home.GuideUiState
import com.arcadia.shell.feature.home.HomeHubUiState
import com.arcadia.shell.feature.home.HomePage
import com.arcadia.shell.feature.home.HomeUiState
import com.arcadia.shell.feature.home.LibraryTab
import com.arcadia.shell.feature.home.RaLibraryGameRow
import com.arcadia.shell.feature.home.RaLibraryUiState
import com.arcadia.shell.feature.home.RssFeedItem
import com.arcadia.shell.feature.home.RssUiState
import com.arcadia.shell.feature.home.StartSettingsCategory
import com.arcadia.shell.feature.home.StartSettingsUiState
import com.arcadia.shell.feature.home.TabKind
import com.arcadia.shell.feature.home.XoraXmbCategory
import com.arcadia.shell.feature.home.XoraXmbUiState
import com.arcadia.shell.feature.home.buildStartSettingsRows
import com.arcadia.shell.feature.home.buildXoraCategoryItems
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import com.arcadia.shell.model.ScrapeState
import com.arcadia.shell.model.ShortcutSpan
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RaGameLookup
import com.arcadia.shell.retroachievements.RaGameProgress
import com.arcadia.shell.retroachievements.RaProfile
import com.arcadia.shell.retroachievements.RaRecentUnlock

/** Landscape handheld / TV-ish frame used by most shell previews. */
const val XORA_PREVIEW_DEVICE = "spec:width=1920dp,height=1080dp,dpi=240"

@Preview(
    name = "XOrA Landscape",
    device = XORA_PREVIEW_DEVICE,
    showBackground = true,
    backgroundColor = 0xFF0B1220,
)
annotation class XoraPreview

@Composable
fun XoraPreviewTheme(content: @Composable () -> Unit) {
    ArcadiaTheme(
        darkTheme = true,
        shellThemeId = ShellThemeId.Default.id,
        content = content,
    )
}

fun previewGame(
    id: String = "game_zelda",
    title: String = "The Legend of Zelda",
    platformId: String = "nes",
    favorite: Boolean = false,
    playTimeMs: Long = 3_600_000L,
): Game = Game(
    id = id,
    title = title,
    sortKey = title.lowercase(),
    platformId = platformId,
    fileName = "$title.nes",
    filePath = "/roms/$platformId/$title.nes",
    documentUri = null,
    sizeBytes = 262_144,
    favorite = favorite,
    playCount = 4,
    playTimeMs = playTimeMs,
    lastPlayedAt = 1_700_000_000_000L,
    scrapeState = ScrapeState.Matched,
)

fun previewGames(): List<Game> = listOf(
    previewGame(),
    previewGame(id = "game_metroid", title = "Metroid", favorite = true),
    previewGame(id = "game_mario", title = "Super Mario Bros.", platformId = "nes"),
    previewGame(id = "game_ff6", title = "Final Fantasy VI", platformId = "snes", playTimeMs = 12_000_000L),
)

fun previewProfile(): LocalProfile = LocalProfile(
    displayName = "Ash",
    avatarPresetId = "preset_0",
)

fun previewTabs(gameCount: Int = 4): List<LibraryTab> = listOf(
    LibraryTab(id = "all", label = "All", kind = TabKind.All, gameCount = gameCount),
    LibraryTab(
        id = "nes",
        label = "NES",
        kind = TabKind.Platform,
        platformId = "nes",
        gameCount = gameCount,
    ),
    LibraryTab(
        id = "snes",
        label = "SNES",
        kind = TabKind.Platform,
        platformId = "snes",
        gameCount = 1,
    ),
)

fun previewRaProfile(): RaProfile = RaProfile(
    username = "AshRA",
    totalPoints = 12_480,
    totalSoftcorePoints = 2_100,
)

fun previewRaProgress(): RaGameProgress = RaGameProgress(
    gameId = 1446,
    title = "The Legend of Zelda",
    consoleName = "NES",
    numAchievements = 40,
    numAwardedToUser = 18,
    numAwardedToUserHardcore = 12,
    achievements = listOf(
        RaAchievement(
            id = 1,
            title = "Sword Found",
            description = "Collect the white sword.",
            points = 5,
            badgeName = "001234",
            displayOrder = 1,
            earned = true,
            earnedHardcore = true,
        ),
        RaAchievement(
            id = 2,
            title = "Triforce Complete",
            description = "Assemble the Triforce.",
            points = 25,
            badgeName = "001235",
            displayOrder = 2,
            earned = false,
            earnedHardcore = false,
        ),
    ),
)

fun previewRecentUnlock(): RaRecentUnlock = RaRecentUnlock(
    achievementId = 1,
    title = "Sword Found",
    description = "Collect the white sword.",
    points = 5,
    badgeName = "001234",
    gameTitle = "The Legend of Zelda",
    consoleName = "NES",
    hardcore = true,
    date = "2026-01-15",
)

fun previewAchievementsSignedIn(): AchievementsUiState = AchievementsUiState(
    credentials = RetroAchievementsCredentials(username = "AshRA", apiKey = "preview"),
    profile = previewRaProfile(),
    gameLookup = RaGameLookup.Matched(previewRaProgress()),
    recent = listOf(previewRecentUnlock()),
    needsLogin = false,
)

fun previewAchievementsNeedsLogin(): AchievementsUiState = AchievementsUiState(
    needsLogin = true,
)

fun previewInsight(): GameInsightUiState = GameInsightUiState(
    gameId = "game_zelda",
    summary = "Explore the kingdom of Hyrule and defeat Ganon.",
    developer = "Nintendo",
    genre = "Action-Adventure",
    releaseYear = 1986,
    platformLabel = "NES",
    trivia = listOf("One of the defining open-world adventures on NES."),
)

fun previewRaLibrary(): RaLibraryUiState = RaLibraryUiState(
    games = listOf(
        RaLibraryGameRow(
            game = RaCompletionGame(
                gameId = 1446,
                title = "The Legend of Zelda",
                imageIconPath = "/Images/000001.png",
                consoleId = 7,
                consoleName = "NES",
                maxPossible = 40,
                numAwarded = 18,
                numAwardedHardcore = 12,
                mostRecentAwardedDate = "2026-01-15",
                highestAwardKind = null,
            ),
            recentBadgeUrls = emptyList(),
        ),
        RaLibraryGameRow(
            game = RaCompletionGame(
                gameId = 228,
                title = "Super Mario World",
                imageIconPath = "/Images/000002.png",
                consoleId = 3,
                consoleName = "SNES",
                maxPossible = 58,
                numAwarded = 40,
                numAwardedHardcore = 30,
                mostRecentAwardedDate = "2026-02-01",
                highestAwardKind = "beaten",
            ),
        ),
    ),
)

fun previewRss(): RssUiState = RssUiState(
    isLoading = false,
    items = listOf(
        RssFeedItem(
            id = "1",
            title = "XOrA 0.2 ships Libretro host",
            link = "https://example.com/1",
            source = "XOrA Blog",
            publishedAt = "Today",
            imageUrl = null,
            description = "Play NES and more in-process.",
        ),
        RssFeedItem(
            id = "2",
            title = "Controller-first shell tips",
            link = "https://example.com/2",
            source = "XOrA Blog",
            publishedAt = "Yesterday",
            imageUrl = null,
            description = "D-pad navigation and Start for Setup.",
        ),
    ),
)

fun previewGuide(): GuideUiState = GuideUiState(
    open = true,
    rows = listOf(
        GuideRow.Profile,
        GuideRow.Settings,
        GuideRow.Achievements,
        GuideRow.SwapScreens,
        GuideRow.QuickLaunch(previewGame()),
    ),
    selectedIndex = 0,
)

fun previewStartSettings(): StartSettingsUiState = StartSettingsUiState(
    open = true,
    category = StartSettingsCategory.Display,
    selectedRowIndex = 0,
    rows = buildStartSettingsRows(
        category = StartSettingsCategory.Display,
        settings = ShellSettings(),
        isScraping = false,
        isScanning = false,
        hasCustomBgm = false,
    ),
    settings = ShellSettings(),
)

fun previewCompanion(
    overlay: GameCompanionOverlay = GameCompanionOverlay.None,
): GameCompanionUiState = GameCompanionUiState(
    gameId = "game_zelda",
    title = "The Legend of Zelda",
    platformLabel = "NES",
    focusedAction = GameCompanionAction.About,
    overlay = overlay,
    about = previewInsight(),
)

fun previewShortcuts(): List<HomeShortcut> = listOf(
    HomeShortcut(
        id = "sc_1",
        kind = HomeShortcutKind.Game,
        title = "Zelda",
        target = "game_zelda",
        span = ShortcutSpan.OneByOne,
    ),
    HomeShortcut(
        id = "sc_2",
        kind = HomeShortcutKind.AndroidApp,
        title = "Settings",
        target = "com.android.settings",
        span = ShortcutSpan.OneByOne,
    ),
)

fun previewAchievementNotification(): ShellNotification.AchievementUnlocked =
    ShellNotification.AchievementUnlocked(
        id = "n1",
        title = "Sword Found",
        description = "Collect the white sword.",
        points = 5,
        badgeUrl = null,
        gameTitle = "The Legend of Zelda",
        hardcore = true,
    )

fun previewHomeUi(
    homePage: HomePage = HomePage.Home,
    games: List<Game> = previewGames(),
): HomeUiState {
    val continueGame = games.firstOrNull()
    return HomeUiState(
        isLoading = false,
        homePage = homePage,
        homeHub = HomeHubUiState(
            continueGame = continueGame,
            shortcuts = previewShortcuts(),
        ),
        xoraXmb = XoraXmbUiState(
            categoryIndex = XoraXmbCategory.Games.ordinal,
            itemIndex = 0,
            items = buildXoraCategoryItems(
                category = XoraXmbCategory.Games,
                profileName = "Ash",
                gamesSecondarySlot = GamesSecondarySlot.Continue,
                continueGame = continueGame,
                favoriteGame = games.firstOrNull { it.favorite },
            ),
            focusGame = continueGame,
            focusTitle = continueGame?.title ?: "Games",
            focusSubtitle = continueGame?.platform?.displayName,
        ),
        tabs = previewTabs(games.size),
        games = games,
        selectedGameIndex = 0,
        hasStorageAccess = true,
        configuredRootCount = 1,
        resolvedPlayerName = "XOrA Emulator",
        profile = previewProfile(),
        achievements = previewAchievementsSignedIn(),
        insight = previewInsight(),
        rss = previewRss(),
        raLibrary = previewRaLibrary(),
        quickLaunchGames = games.take(3),
    )
}
