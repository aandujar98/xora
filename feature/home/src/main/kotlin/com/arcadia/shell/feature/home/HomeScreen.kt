package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.component.ButtonHintBar
import com.arcadia.shell.feature.home.component.hintsForGuide
import com.arcadia.shell.feature.home.component.hintsForPage
import com.arcadia.shell.feature.home.component.hintsForSocialMenu
import com.arcadia.shell.feature.home.component.hintsForStartSettings
import com.arcadia.shell.feature.home.component.hintsForSystemMenu
import com.arcadia.shell.model.ShortcutSpan

/**
 * The single-display layout: hero above, Home page below (hub, XMB, or RSS feed).
 *
 * Stateless on purpose. The dual-screen host renders [HeroPane] and the library/[RssPane]
 * separately against the same state, so keeping this composable free of its own state means the
 * two modes cannot drift apart in behaviour.
 *
 * Launch transition: library chrome and hint bar slide/fade out while the hero artwork holds as
 * the transition plate into the emulator (see [HeroPane] + [HomeViewModel.launchSelected]).
 *
 * Activity Result pickers are never registered here — the Activity-rooted shell observes
 * [HomeViewModel.mediaPickerRequestFlow] so Dual Mode Presentation panes stay crash-free.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectTab: (Int) -> Unit,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
    onSelectRssItem: (Int) -> Unit,
    onOpenRssItem: (Int) -> Unit,
    onRetryRss: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAccountPanel: () -> Unit,
    onToggleSystemPanel: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onToggleAchievementsPanel: () -> Unit,
    onSelectSocialTab: (SocialMenuTab) -> Unit,
    onSelectAccountRow: (Int) -> Unit,
    onActivateAccountRow: (Int?) -> Unit,
    onSelectSystemRow: (Int) -> Unit,
    onActivateSystemRow: (Int?) -> Unit,
    onSelectRaLibraryIndex: (Int) -> Unit,
    onSelectRaLibraryTab: (RaLibraryTab) -> Unit,
    onSelectRaPlatformFilter: (String?) -> Unit,
    onActivateRaLibrary: () -> Unit,
    onRetryRaLibrary: () -> Unit,
    onSelectHomeShard: (HomeShard) -> Unit = {},
    onActivateHomeShard: (HomeShard) -> Unit = {},
    onSelectHomeShortcut: (Int) -> Unit = {},
    onActivateHomeShortcut: (Int) -> Unit = {},
    onAddHomeShortcut: () -> Unit = {},
    onSelectXoraCategory: (Int) -> Unit = {},
    onSelectXoraItem: (Int) -> Unit = {},
    onActivateXoraItem: () -> Unit = {},
    onRequestWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onRequestBgm: () -> Unit = {},
    onClearBgm: () -> Unit = {},
    onOpenShortcutEditor: () -> Unit = {},
    onDismissThemes: () -> Unit = {},
    onSelectTheme: (String) -> Unit = {},
    onShopComingSoon: () -> Unit = {},
    onUploadComingSoon: () -> Unit = {},
    onDismissAddShortcut: () -> Unit = {},
    onPinRecentShortcut: () -> Unit = {},
    onPinAndroidShortcut: () -> Unit = {},
    onPinPictureShortcut: () -> Unit = {},
    onPinGifShortcut: () -> Unit = {},
    onSelectShortcutSpan: (ShortcutSpan) -> Unit = {},
    onConfirmShortcutSpan: () -> Unit = {},
    onCancelShortcutSpan: () -> Unit = {},
    onSelectShortcutTarget: (Int) -> Unit = {},
    onConfirmShortcutTarget: () -> Unit = {},
    onCancelShortcutTargetPicker: () -> Unit = {},
    onCycleHomeShortcutSpan: (Int) -> Unit = {},
    onAdjustShortcutColumns: (Int) -> Unit = {},
    onAdjustShortcutRows: (Int) -> Unit = {},
    onFocusShortcutCustomizeChrome: (ShortcutCustomizeChrome) -> Unit = {},
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit,
    onSelectAvatarPreset: (presetId: String) -> Unit,
    onRequestLocalAvatar: () -> Unit,
    onUseRaAvatar: () -> Unit,
    onUseDiscordAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit,
    onLoginRetroAchievements: (username: String, password: String) -> Unit,
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit,
    onSignOutRetroAchievements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentTween = arcadiaTween<Float>(ArcadiaMotion.Medium)
    val launchProgress by animateFloatAsState(
        targetValue = if (state.isLaunching) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Launch),
        label = "libraryLaunchChrome",
    )
    val libraryChromeAlpha = 1f - launchProgress
    val librarySlidePx = launchProgress * 96f

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = when {
                state.isLoading -> HomePhase.Loading
                state.needsSetup -> HomePhase.Setup
                else -> HomePhase.Library
            },
            transitionSpec = {
                fadeIn(contentTween) togetherWith fadeOut(contentTween)
            },
            label = "homePhase",
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                HomePhase.Loading -> LoadingState(modifier = Modifier.fillMaxSize())

                HomePhase.Setup -> SetupNotice(
                    hasStorageAccess = state.hasStorageAccess,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )

                HomePhase.Library -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.homePage == HomePage.Home) {
                        // XOrA XMB owns the cinematic launch hold (backdrop zooms, chrome exits).
                        // Do not fade the whole pane — that wiped the art plate.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            HomePageContent(
                                state = state,
                                onSelectTab = onSelectTab,
                                onSelectGame = onSelectGame,
                                onLaunchGame = onLaunchGame,
                                onSelectRssItem = onSelectRssItem,
                                onOpenRssItem = onOpenRssItem,
                                onRetryRss = onRetryRss,
                                onSelectRaLibraryIndex = onSelectRaLibraryIndex,
                                onSelectRaLibraryTab = onSelectRaLibraryTab,
                                onSelectRaPlatformFilter = onSelectRaPlatformFilter,
                                onActivateRaLibrary = onActivateRaLibrary,
                                onRetryRaLibrary = onRetryRaLibrary,
                                onSelectHomeShard = onSelectHomeShard,
                                onActivateHomeShard = onActivateHomeShard,
                                onSelectHomeShortcut = onSelectHomeShortcut,
                                onActivateHomeShortcut = onActivateHomeShortcut,
                                onAddHomeShortcut = onAddHomeShortcut,
                                onSelectXoraCategory = onSelectXoraCategory,
                                onSelectXoraItem = onSelectXoraItem,
                                onActivateXoraItem = onActivateXoraItem,
                                onToggleAccountPanel = onToggleAccountPanel,
                                onToggleSystemPanel = onToggleSystemPanel,
                                onOpenNotifications = onOpenNotifications,
                                onToggleAchievementsPanel = onToggleAchievementsPanel,
                                onSelectSocialTab = onSelectSocialTab,
                                onSelectAccountRow = onSelectAccountRow,
                                onActivateAccountRow = onActivateAccountRow,
                                onSelectSystemRow = onSelectSystemRow,
                                onActivateSystemRow = onActivateSystemRow,
                                onSaveProfile = onSaveProfile,
                                onSelectAvatarPreset = onSelectAvatarPreset,
                                onRequestLocalAvatar = onRequestLocalAvatar,
                                onUseRaAvatar = onUseRaAvatar,
                                onUseDiscordAvatar = onUseDiscordAvatar,
                                onClearAvatar = onClearAvatar,
                                onFriendSearchChange = onFriendSearchChange,
                                onReplyDraftChange = onReplyDraftChange,
                                onSelectAchievementsTab = onSelectAchievementsTab,
                                onLoginRetroAchievements = onLoginRetroAchievements,
                                onLoginRetroAchievementsWithApiKey =
                                    onLoginRetroAchievementsWithApiKey,
                                onSignOutRetroAchievements = onSignOutRetroAchievements,
                                onRequestWallpaper = onRequestWallpaper,
                                onClearWallpaper = onClearWallpaper,
                                onRequestBgm = onRequestBgm,
                                onClearBgm = onClearBgm,
                                onOpenShortcutEditor = onOpenShortcutEditor,
                                onDismissThemes = onDismissThemes,
                                onSelectTheme = onSelectTheme,
                                onShopComingSoon = onShopComingSoon,
                                onUploadComingSoon = onUploadComingSoon,
                                onDismissAddShortcut = onDismissAddShortcut,
                                onPinRecentShortcut = onPinRecentShortcut,
                                onPinAndroidShortcut = onPinAndroidShortcut,
                                onPinPictureShortcut = onPinPictureShortcut,
                                onPinGifShortcut = onPinGifShortcut,
                                onSelectShortcutSpan = onSelectShortcutSpan,
                                onConfirmShortcutSpan = onConfirmShortcutSpan,
                                onCancelShortcutSpan = onCancelShortcutSpan,
                                onSelectShortcutTarget = onSelectShortcutTarget,
                                onConfirmShortcutTarget = onConfirmShortcutTarget,
                                onCancelShortcutTargetPicker = onCancelShortcutTargetPicker,
                                onCycleHomeShortcutSpan = onCycleHomeShortcutSpan,
                                onAdjustShortcutColumns = onAdjustShortcutColumns,
                                onAdjustShortcutRows = onAdjustShortcutRows,
                                onFocusShortcutCustomizeChrome = onFocusShortcutCustomizeChrome,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (state.homePage == HomePage.RaLibrary) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = libraryChromeAlpha
                                    translationY = librarySlidePx
                                },
                        ) {
                            HomePageContent(
                                state = state,
                                onSelectTab = onSelectTab,
                                onSelectGame = onSelectGame,
                                onLaunchGame = onLaunchGame,
                                onSelectRssItem = onSelectRssItem,
                                onOpenRssItem = onOpenRssItem,
                                onRetryRss = onRetryRss,
                                onSelectRaLibraryIndex = onSelectRaLibraryIndex,
                                onSelectRaLibraryTab = onSelectRaLibraryTab,
                                onSelectRaPlatformFilter = onSelectRaPlatformFilter,
                                onActivateRaLibrary = onActivateRaLibrary,
                                onRetryRaLibrary = onRetryRaLibrary,
                                onSelectHomeShard = onSelectHomeShard,
                                onActivateHomeShard = onActivateHomeShard,
                                onSelectHomeShortcut = onSelectHomeShortcut,
                                onActivateHomeShortcut = onActivateHomeShortcut,
                                onAddHomeShortcut = onAddHomeShortcut,
                                onSelectXoraCategory = onSelectXoraCategory,
                                onSelectXoraItem = onSelectXoraItem,
                                onActivateXoraItem = onActivateXoraItem,
                                onToggleAccountPanel = onToggleAccountPanel,
                                onToggleSystemPanel = onToggleSystemPanel,
                                onOpenNotifications = onOpenNotifications,
                                onToggleAchievementsPanel = onToggleAchievementsPanel,
                                onSelectSocialTab = onSelectSocialTab,
                                onSelectAccountRow = onSelectAccountRow,
                                onActivateAccountRow = onActivateAccountRow,
                                onSelectSystemRow = onSelectSystemRow,
                                onActivateSystemRow = onActivateSystemRow,
                                onSaveProfile = onSaveProfile,
                                onSelectAvatarPreset = onSelectAvatarPreset,
                                onRequestLocalAvatar = onRequestLocalAvatar,
                                onUseRaAvatar = onUseRaAvatar,
                                onUseDiscordAvatar = onUseDiscordAvatar,
                                onClearAvatar = onClearAvatar,
                                onFriendSearchChange = onFriendSearchChange,
                                onReplyDraftChange = onReplyDraftChange,
                                onSelectAchievementsTab = onSelectAchievementsTab,
                                onLoginRetroAchievements = onLoginRetroAchievements,
                                onLoginRetroAchievementsWithApiKey =
                                    onLoginRetroAchievementsWithApiKey,
                                onSignOutRetroAchievements = onSignOutRetroAchievements,
                                onRequestWallpaper = onRequestWallpaper,
                                onClearWallpaper = onClearWallpaper,
                                onRequestBgm = onRequestBgm,
                                onClearBgm = onClearBgm,
                                onOpenShortcutEditor = onOpenShortcutEditor,
                                onDismissThemes = onDismissThemes,
                                onSelectTheme = onSelectTheme,
                                onShopComingSoon = onShopComingSoon,
                                onUploadComingSoon = onUploadComingSoon,
                                onDismissAddShortcut = onDismissAddShortcut,
                                onPinRecentShortcut = onPinRecentShortcut,
                                onPinAndroidShortcut = onPinAndroidShortcut,
                                onPinPictureShortcut = onPinPictureShortcut,
                                onPinGifShortcut = onPinGifShortcut,
                                onSelectShortcutSpan = onSelectShortcutSpan,
                                onConfirmShortcutSpan = onConfirmShortcutSpan,
                                onCancelShortcutSpan = onCancelShortcutSpan,
                                onSelectShortcutTarget = onSelectShortcutTarget,
                                onConfirmShortcutTarget = onConfirmShortcutTarget,
                                onCancelShortcutTargetPicker = onCancelShortcutTargetPicker,
                                onCycleHomeShortcutSpan = onCycleHomeShortcutSpan,
                                onAdjustShortcutColumns = onAdjustShortcutColumns,
                                onAdjustShortcutRows = onAdjustShortcutRows,
                                onFocusShortcutCustomizeChrome = onFocusShortcutCustomizeChrome,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (
                        state.displayMode == DisplayMode.Single &&
                        state.homePage == HomePage.GameSelector
                    ) {
                        VerticalGameSelectorPane(
                            state = state,
                            onSelectTab = onSelectTab,
                            onSelectGame = onSelectGame,
                            onLaunchGame = onLaunchGame,
                            onToggleAccountPanel = onToggleAccountPanel,
                            onToggleSystemPanel = onToggleSystemPanel,
                                onOpenNotifications = onOpenNotifications,
                            onToggleAchievementsPanel = onToggleAchievementsPanel,
                            onSelectSocialTab = onSelectSocialTab,
                            onSelectAccountRow = onSelectAccountRow,
                            onActivateAccountRow = onActivateAccountRow,
                            onSelectSystemRow = onSelectSystemRow,
                            onActivateSystemRow = onActivateSystemRow,
                            onSaveProfile = onSaveProfile,
                            onSelectAvatarPreset = onSelectAvatarPreset,
                            onRequestLocalAvatar = onRequestLocalAvatar,
                            onUseRaAvatar = onUseRaAvatar,
                            onUseDiscordAvatar = onUseDiscordAvatar,
                            onClearAvatar = onClearAvatar,
                            onFriendSearchChange = onFriendSearchChange,
                            onReplyDraftChange = onReplyDraftChange,
                            onSelectAchievementsTab = onSelectAchievementsTab,
                            onLoginRetroAchievements = onLoginRetroAchievements,
                            onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
                            onSignOutRetroAchievements = onSignOutRetroAchievements,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        HeroPane(
                            game = state.selectedGame,
                            profile = state.profile,
                            profileAvatarModel = state.profileAvatarModel,
                            raConfigured = state.achievements.credentials.isConfigured,
                            discordLinked = state.socialMenu.discord.avatarAvailable,
                            accountPanelExpanded = state.accountPanelExpanded,
                            systemPanelExpanded = state.systemPanelExpanded,
                            achievementsPanelExpanded = state.achievementsPanelExpanded,
                            achievements = state.achievements,
                            quickLaunchGames = state.quickLaunchGames,
                            socialMenu = state.socialMenu,
                            accountPanelRows = state.accountPanelRows,
                            accountPanelSelectedIndex = state.accountPanelSelectedIndex,
                            systemPanelSelectedIndex = state.systemPanelSelectedIndex,
                            trailer = state.trailer,
                            isLaunching = state.isLaunching,
                            rssItem = state.rss.selectedItem.takeIf {
                                state.homePage == HomePage.RssFeed
                            },
                            showHomeWallpaper = state.homePage == HomePage.Home,
                            homeWallpaperPath = state.homeHub.wallpaperPath,
                            onToggleAccountPanel = onToggleAccountPanel,
                            onToggleSystemPanel = onToggleSystemPanel,
                                onOpenNotifications = onOpenNotifications,
                            onToggleAchievementsPanel = onToggleAchievementsPanel,
                            onSelectSocialTab = onSelectSocialTab,
                            onSelectAccountRow = onSelectAccountRow,
                            onActivateAccountRow = onActivateAccountRow,
                            onSelectSystemRow = onSelectSystemRow,
                            onActivateSystemRow = onActivateSystemRow,
                            profileEditRequest = state.profileEditRequest,
                            onSaveProfile = onSaveProfile,
                            onSelectAvatarPreset = onSelectAvatarPreset,
                            onRequestLocalAvatar = onRequestLocalAvatar,
                            onUseRaAvatar = onUseRaAvatar,
                            onUseDiscordAvatar = onUseDiscordAvatar,
                            onClearAvatar = onClearAvatar,
                            onFriendSearchChange = onFriendSearchChange,
                            onReplyDraftChange = onReplyDraftChange,
                            onSelectAchievementsTab = onSelectAchievementsTab,
                            onLoginRetroAchievements = onLoginRetroAchievements,
                            onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
                            onSignOutRetroAchievements = onSignOutRetroAchievements,
                            modifier = Modifier.fillMaxWidth().weight(HERO_WEIGHT),
                        )

                        HomePageContent(
                            state = state,
                            onSelectTab = onSelectTab,
                            onSelectGame = onSelectGame,
                            onLaunchGame = onLaunchGame,
                            onSelectRssItem = onSelectRssItem,
                            onOpenRssItem = onOpenRssItem,
                            onRetryRss = onRetryRss,
                            onSelectRaLibraryIndex = onSelectRaLibraryIndex,
                            onSelectRaLibraryTab = onSelectRaLibraryTab,
                            onSelectRaPlatformFilter = onSelectRaPlatformFilter,
                            onActivateRaLibrary = onActivateRaLibrary,
                            onRetryRaLibrary = onRetryRaLibrary,
                            onSelectHomeShard = onSelectHomeShard,
                            onActivateHomeShard = onActivateHomeShard,
                            onSelectHomeShortcut = onSelectHomeShortcut,
                            onActivateHomeShortcut = onActivateHomeShortcut,
                            onAddHomeShortcut = onAddHomeShortcut,
                            onSelectXoraCategory = onSelectXoraCategory,
                            onSelectXoraItem = onSelectXoraItem,
                            onActivateXoraItem = onActivateXoraItem,
                            onRequestWallpaper = onRequestWallpaper,
                            onClearWallpaper = onClearWallpaper,
                            onRequestBgm = onRequestBgm,
                            onClearBgm = onClearBgm,
                            onOpenShortcutEditor = onOpenShortcutEditor,
                            onDismissThemes = onDismissThemes,
                            onSelectTheme = onSelectTheme,
                            onShopComingSoon = onShopComingSoon,
                            onUploadComingSoon = onUploadComingSoon,
                            onDismissAddShortcut = onDismissAddShortcut,
                            onPinRecentShortcut = onPinRecentShortcut,
                            onPinAndroidShortcut = onPinAndroidShortcut,
                            onPinPictureShortcut = onPinPictureShortcut,
                            onPinGifShortcut = onPinGifShortcut,
                            onSelectShortcutSpan = onSelectShortcutSpan,
                            onConfirmShortcutSpan = onConfirmShortcutSpan,
                            onCancelShortcutSpan = onCancelShortcutSpan,
                            onSelectShortcutTarget = onSelectShortcutTarget,
                            onConfirmShortcutTarget = onConfirmShortcutTarget,
                            onCancelShortcutTargetPicker = onCancelShortcutTargetPicker,
                            onCycleHomeShortcutSpan = onCycleHomeShortcutSpan,
                            onAdjustShortcutColumns = onAdjustShortcutColumns,
                            onAdjustShortcutRows = onAdjustShortcutRows,
                            onFocusShortcutCustomizeChrome = onFocusShortcutCustomizeChrome,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(GRID_WEIGHT)
                                .graphicsLayer {
                                    alpha = libraryChromeAlpha
                                    translationY = librarySlidePx
                                },
                        )
                    }

                    // Single-screen mode: hide legends so library/home reclaim the full viewport.
                    if (state.displayMode != DisplayMode.Single) {
                        ButtonHintBar(
                            hints = when {
                                state.guideOpen -> hintsForGuide()
                                state.startSettingsOpen -> hintsForStartSettings()
                                state.accountPanelExpanded -> hintsForSocialMenu()
                                state.systemPanelExpanded -> hintsForSystemMenu()
                                else -> hintsForPage(
                                    page = state.homePage,
                                    displayMode = state.displayMode,
                                    homeHub = state.homeHub,
                                    xmbDepth = state.xoraXmb.depth,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp, max = 72.dp)
                                .graphicsLayer {
                                    alpha = libraryChromeAlpha
                                    translationY = librarySlidePx
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid-role page content for Single and Dual layouts.
 *
 * Add / Themes overlays are in-tree (no nested Dialog window). Media pick requests go to the
 * ViewModel; only the Activity-rooted shell launches Activity Result contracts.
 */
@Composable
fun HomePageContent(
    state: HomeUiState,
    onSelectTab: (Int) -> Unit,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
    onSelectRssItem: (Int) -> Unit,
    onOpenRssItem: (Int) -> Unit,
    onRetryRss: () -> Unit,
    onSelectRaLibraryIndex: (Int) -> Unit = {},
    onSelectRaLibraryTab: (RaLibraryTab) -> Unit = {},
    onSelectRaPlatformFilter: (String?) -> Unit = {},
    onActivateRaLibrary: () -> Unit = {},
    onRetryRaLibrary: () -> Unit = {},
    onSelectHomeShard: (HomeShard) -> Unit = {},
    onActivateHomeShard: (HomeShard) -> Unit = {},
    onSelectHomeShortcut: (Int) -> Unit = {},
    onActivateHomeShortcut: (Int) -> Unit = {},
    onAddHomeShortcut: () -> Unit = {},
    onSelectXoraCategory: (Int) -> Unit = {},
    onSelectXoraItem: (Int) -> Unit = {},
    onActivateXoraItem: () -> Unit = {},
    onToggleAccountPanel: () -> Unit = {},
    onToggleSystemPanel: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onToggleAchievementsPanel: () -> Unit = {},
    onSelectSocialTab: (SocialMenuTab) -> Unit = {},
    onSelectAccountRow: (Int) -> Unit = {},
    onActivateAccountRow: (Int?) -> Unit = {},
    onSelectSystemRow: (Int) -> Unit = {},
    onActivateSystemRow: (Int?) -> Unit = {},
    onSaveProfile: (displayName: String, avatarPresetId: String) -> Unit = { _, _ -> },
    onSelectAvatarPreset: (presetId: String) -> Unit = {},
    onRequestLocalAvatar: () -> Unit = {},
    onUseRaAvatar: () -> Unit = {},
    onUseDiscordAvatar: () -> Unit = {},
    onClearAvatar: () -> Unit = {},
    onFriendSearchChange: (String) -> Unit = {},
    onReplyDraftChange: (String) -> Unit = {},
    onSelectAchievementsTab: (AchievementsPaneTab) -> Unit = {},
    onLoginRetroAchievements: (username: String, password: String) -> Unit = { _, _ -> },
    onLoginRetroAchievementsWithApiKey: (username: String, apiKey: String) -> Unit = { _, _ -> },
    onSignOutRetroAchievements: () -> Unit = {},
    onRequestWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onRequestBgm: () -> Unit = {},
    onClearBgm: () -> Unit = {},
    onOpenShortcutEditor: () -> Unit = {},
    onDismissThemes: () -> Unit = {},
    onSelectTheme: (String) -> Unit = {},
    onShopComingSoon: () -> Unit = {},
    onUploadComingSoon: () -> Unit = {},
    onDismissAddShortcut: () -> Unit = {},
    onPinRecentShortcut: () -> Unit = {},
    onPinAndroidShortcut: () -> Unit = {},
    onPinPictureShortcut: () -> Unit = {},
    onPinGifShortcut: () -> Unit = {},
    onSelectShortcutSpan: (ShortcutSpan) -> Unit = {},
    onConfirmShortcutSpan: () -> Unit = {},
    onCancelShortcutSpan: () -> Unit = {},
    onSelectShortcutTarget: (Int) -> Unit = {},
    onConfirmShortcutTarget: () -> Unit = {},
    onCancelShortcutTargetPicker: () -> Unit = {},
    onCycleHomeShortcutSpan: (Int) -> Unit = {},
    onAdjustShortcutColumns: (Int) -> Unit = {},
    onAdjustShortcutRows: (Int) -> Unit = {},
    onFocusShortcutCustomizeChrome: (ShortcutCustomizeChrome) -> Unit = {},
    showWallpaperBackdrop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fadeTween = arcadiaTween<Float>(ArcadiaMotion.Medium)
    val slideTween = arcadiaTween<IntOffset>(ArcadiaMotion.Medium)
    AnimatedContent(
        targetState = state.homePage,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            if (forward) {
                (slideInHorizontally(slideTween) { it / 3 } + fadeIn(fadeTween)) togetherWith
                    (slideOutHorizontally(slideTween) { -it / 3 } + fadeOut(fadeTween))
            } else {
                (slideInHorizontally(slideTween) { -it / 3 } + fadeIn(fadeTween)) togetherWith
                    (slideOutHorizontally(slideTween) { it / 3 } + fadeOut(fadeTween))
            }
        },
        label = "homePage",
        modifier = modifier,
    ) { page ->
        when (page) {
            HomePage.Home -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    val trayOpen = state.homeHub.vitaShortcutTrayOpen
                    XoraHomeXmbPane(
                        state = state,
                        onSelectCategory = onSelectXoraCategory,
                        onSelectItem = onSelectXoraItem,
                        onActivateItem = onActivateXoraItem,
                        onToggleAccountPanel = onToggleAccountPanel,
                        onToggleSystemPanel = onToggleSystemPanel,
                        onOpenNotifications = onOpenNotifications,
                        onToggleAchievementsPanel = onToggleAchievementsPanel,
                        onSelectSocialTab = onSelectSocialTab,
                        onSelectAccountRow = onSelectAccountRow,
                        onActivateAccountRow = onActivateAccountRow,
                        onSelectSystemRow = onSelectSystemRow,
                        onActivateSystemRow = onActivateSystemRow,
                        onSaveProfile = onSaveProfile,
                        onSelectAvatarPreset = onSelectAvatarPreset,
                        onRequestLocalAvatar = onRequestLocalAvatar,
                        onUseRaAvatar = onUseRaAvatar,
                        onUseDiscordAvatar = onUseDiscordAvatar,
                        onClearAvatar = onClearAvatar,
                        onFriendSearchChange = onFriendSearchChange,
                        onReplyDraftChange = onReplyDraftChange,
                        onSelectAchievementsTab = onSelectAchievementsTab,
                        onLoginRetroAchievements = onLoginRetroAchievements,
                        onLoginRetroAchievementsWithApiKey = onLoginRetroAchievementsWithApiKey,
                        onSignOutRetroAchievements = onSignOutRetroAchievements,
                        // Dual: LT/RT live on the Hero role; Single: chrome sits on the XMB itself.
                        showPillChrome = state.displayMode == DisplayMode.Single,
                        modifier = Modifier.fillMaxSize(),
                        overlayContent = {
                            VitaShortcutTray(
                                visible = trayOpen,
                                shortcuts = state.homeHub.shortcuts,
                                selectedIndex = state.homeHub.shortcutIndex,
                                editMode = state.homeHub.shortcutsEditMode,
                                xmbCategoryIndex = state.xoraXmb.categoryIndex,
                                onSelect = onSelectHomeShortcut,
                                onActivate = onActivateHomeShortcut,
                                onAddSlot = onAddHomeShortcut,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                    if (state.homeHub.addShortcutOpen) {
                        AddShortcutSheet(
                            picker = state.homeHub.shortcutTargetPicker,
                            pendingKind = state.homeHub.pendingShortcutKind,
                            pendingSpan = state.homeHub.pendingShortcutSpan,
                            onDismiss = onDismissAddShortcut,
                            onPinRecentGame = onPinRecentShortcut,
                            onPinAndroidApp = onPinAndroidShortcut,
                            onPinPicture = onPinPictureShortcut,
                            onPinGif = onPinGifShortcut,
                            onSelectSpan = onSelectShortcutSpan,
                            onConfirmSpan = onConfirmShortcutSpan,
                            onCancelSpan = onCancelShortcutSpan,
                            onSelectTarget = onSelectShortcutTarget,
                            onConfirmTarget = onConfirmShortcutTarget,
                            onCancelTargetPicker = onCancelShortcutTargetPicker,
                            appsAndRomsOnly = state.homeHub.vitaShortcutPinMode,
                        )
                    }
                    // ThemesSheet is hosted on the primary Activity overlay in ArcadiaShell —
                    // not here — so Customize works from Start settings on any page / display.
                }
            }

            HomePage.GameSelector -> XmbPane(
                state = state,
                onSelectTab = onSelectTab,
                onSelectGame = onSelectGame,
                onLaunchGame = onLaunchGame,
                modifier = Modifier.fillMaxSize(),
            )

            HomePage.RssFeed -> RssPane(
                state = state,
                onSelectItem = onSelectRssItem,
                onOpenItem = onOpenRssItem,
                onRetry = onRetryRss,
                modifier = Modifier.fillMaxSize(),
            )

            HomePage.RaLibrary -> RaLibraryPane(
                state = state,
                onSelectIndex = onSelectRaLibraryIndex,
                onSelectTab = onSelectRaLibraryTab,
                onSelectPlatformFilter = onSelectRaPlatformFilter,
                onActivate = onActivateRaLibrary,
                onRetry = onRetryRaLibrary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private enum class HomePhase { Loading, Setup, Library }

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SetupNotice(
    hasStorageAccess: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(0.65f).padding(32.dp),
        ) {
            Text(
                text = "Point XOrA at your ROMs",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (hasStorageAccess) {
                    "Storage access is granted. Add the folders your games live in and XOrA " +
                        "will work out which system each one belongs to."
                } else {
                    "XOrA needs access to your files. Emulators like Dolphin and DuckStation " +
                        "only accept a real file path, which the document picker cannot provide."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenSettings) {
                Text(text = "Open setup")
            }
        }
    }
}

private const val HERO_WEIGHT = 0.56f
private const val GRID_WEIGHT = 0.44f
