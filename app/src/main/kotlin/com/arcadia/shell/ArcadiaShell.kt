package com.arcadia.shell

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcadia.shell.datastore.resolveDarkTheme
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.SkyBackground
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.rememberLaunchCinematic
import com.arcadia.shell.display.SecondaryDisplayPane
import com.arcadia.shell.feature.home.ChooseEmulatorSheet
import com.arcadia.shell.feature.home.GameCompanionPane
import com.arcadia.shell.feature.home.GameOptionsDialog
import com.arcadia.shell.feature.home.HeroPane
import com.arcadia.shell.feature.home.HomeEvent
import com.arcadia.shell.feature.home.HomeExternalAuthRequest
import com.arcadia.shell.feature.home.HomeMediaPickerRequest
import com.arcadia.shell.feature.home.HomePage
import com.arcadia.shell.feature.home.HomePageContent
import com.arcadia.shell.feature.home.HomeScreen
import com.arcadia.shell.feature.home.HomeUiState
import com.arcadia.shell.feature.home.HomeViewModel
import com.arcadia.shell.feature.home.MusicCustomizeSheet
import com.arcadia.shell.feature.home.RomOptionsSheet
import com.arcadia.shell.feature.home.ThemesSheet
import com.arcadia.shell.libretro.GameSaveEntry
import com.arcadia.shell.feature.home.XoraXmbHeroDetail
import com.arcadia.shell.feature.home.component.GuidePanel
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.feature.home.component.NotificationHistoryPanel
import com.arcadia.shell.feature.home.component.NetplayInvitePromptDialog
import com.arcadia.shell.feature.home.component.DiscordConversationWindow
import com.arcadia.shell.feature.home.component.XoraConversationWindow
import com.arcadia.shell.feature.home.component.StartSettingsPanel
import com.arcadia.shell.feature.home.component.WelcomeBackOverlay
import com.arcadia.shell.feature.home.component.BootIntroOverlay
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.feature.settings.OnboardingExternalAuthRequest
import com.arcadia.shell.feature.settings.OnboardingScreen
import com.arcadia.shell.feature.settings.OnboardingViewModel
import com.arcadia.shell.feature.settings.SettingsScreen
import com.arcadia.shell.home.ShellUiState
import com.arcadia.shell.launcher.DetectedEmulator
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.ScreenRole
import com.arcadia.shell.role.HomeRoleCard
import com.arcadia.shell.scraper.ScraperPreference
import com.arcadia.shell.scraper.SteamOpenId

private enum class ShellRoute { Home, Settings }

/**
 * Root of the shell. Decides between the single-display layout and the split dual-display
 * arrangement, and owns navigation between the library and setup.
 */
@Composable
fun ArcadiaShell(
    shellState: ShellUiState,
    homeViewModel: HomeViewModel,
    onSetHomeCandidate: (Boolean) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRestartOnboarding: () -> Unit,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val gameCompanion by homeViewModel.gameCompanion.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(ShellRoute.Home) }
    var optionsGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var scrapeMenuGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var musicCustomizeId by rememberSaveable { mutableStateOf<String?>(null) }
    var musicCustomizeTitle by rememberSaveable { mutableStateOf("") }
    var chooseEmulatorPlatformId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val routeTween = arcadiaTween<Float>(ArcadiaMotion.Medium)
    // Game options dialog blocks the dispatcher; bottom sheets keep it on so SheetNavCapture works.
    val dialogOverlayOpen = optionsGameId != null
    val sheetOverlayOpen = scrapeMenuGameId != null ||
        chooseEmulatorPlatformId != null ||
        musicCustomizeId != null
    val overlayOpen = dialogOverlayOpen || sheetOverlayOpen
    val context = LocalContext.current
    var pendingGameMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMusicMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPlatformBannerId by rememberSaveable { mutableStateOf<String?>(null) }

    // Activity Result launchers must live only in this Activity-rooted composition. Home hub /
    // shortcuts also compose under ComposePresentation on dual-screen, where nested Dialog and
    // Presentation-scoped launchers crash.
    val shortcutPicturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) homeViewModel.addShortcutFromMedia(uri, gif = false)
    }
    val shortcutGifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) homeViewModel.addShortcutFromMedia(uri, gif = true)
    }
    // OpenDocument (not ImageOnly PickVisualMedia) so stills, GIFs, and looping MP4/WebM all work.
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) homeViewModel.setHomeWallpaper(uri)
    }
    val folderImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) homeViewModel.setHomeFolderImage(uri)
    }
    val bgmPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) homeViewModel.setCustomBgm(uri)
    }
    val profileAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) homeViewModel.setLocalAvatar(uri)
    }
    val platformBannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val platformId = pendingPlatformBannerId
        pendingPlatformBannerId = null
        if (uri != null && platformId != null) {
            homeViewModel.setPlatformBanner(platformId, uri)
        }
    }
    // GetContent, not PickVisualMedia: photo-picker URIs are not grantable to another app.
    val discordAttachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) homeViewModel.attachToOpenDiscordDm(uri)
    }
    // Music browses MediaStore, which stays empty until audio access is granted.
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        homeViewModel.onAudioAccessResult(granted)
    }
    // Photos: multiple-permission request (READ_MEDIA_IMAGES + partial-access on 14+).
    val imagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        homeViewModel.onImageAccessResult()
    }
    // MediaStore deletion consent dialog (createDeleteRequest / RecoverableSecurityException).
    val photoDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        homeViewModel.onPhotoDeleteResult(result.resultCode == Activity.RESULT_OK)
    }
    val gameBoxArtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val gameId = pendingGameMediaId
        pendingGameMediaId = null
        if (uri != null && gameId != null) homeViewModel.setGameBoxArt(gameId, uri)
    }
    val gameBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val gameId = pendingGameMediaId
        pendingGameMediaId = null
        if (uri != null && gameId != null) homeViewModel.setGameBackground(gameId, uri)
    }
    val gameSoundBitePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val gameId = pendingGameMediaId
        pendingGameMediaId = null
        if (uri != null && gameId != null) homeViewModel.setGameSoundBite(gameId, uri)
    }
    val gameIdleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val gameId = pendingGameMediaId
        pendingGameMediaId = null
        if (uri != null && gameId != null) homeViewModel.setGameIdleVideo(gameId, uri)
    }
    val musicCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val mediaId = pendingMusicMediaId
        pendingMusicMediaId = null
        if (uri != null && mediaId != null) homeViewModel.setMusicCover(mediaId, uri)
    }
    val musicWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val mediaId = pendingMusicMediaId
        pendingMusicMediaId = null
        if (uri != null && mediaId != null) homeViewModel.setMusicWallpaper(mediaId, uri)
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.mediaPickerRequestFlow.collect { request ->
            runCatching {
                when (request) {
                    HomeMediaPickerRequest.ShortcutPicture -> shortcutPicturePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    HomeMediaPickerRequest.ShortcutGif -> shortcutGifPicker.launch("image/gif")
                    HomeMediaPickerRequest.Wallpaper -> wallpaperPicker.launch(
                        arrayOf("image/*", "video/*"),
                    )
                    HomeMediaPickerRequest.Bgm -> bgmPicker.launch("audio/*")
                    HomeMediaPickerRequest.ProfileAvatar -> profileAvatarPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    HomeMediaPickerRequest.DiscordAttachment ->
                        discordAttachmentPicker.launch("image/*")
                    is HomeMediaPickerRequest.PlatformBanner -> {
                        pendingPlatformBannerId = request.platformId
                        platformBannerPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }
                    is HomeMediaPickerRequest.GameBoxArt -> {
                        pendingGameMediaId = request.gameId
                        gameBoxArtPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    is HomeMediaPickerRequest.GameBackground -> {
                        pendingGameMediaId = request.gameId
                        gameBackgroundPicker.launch(arrayOf("image/*", "video/*"))
                    }
                    is HomeMediaPickerRequest.GameSoundBite -> {
                        pendingGameMediaId = request.gameId
                        gameSoundBitePicker.launch("audio/*")
                    }
                    is HomeMediaPickerRequest.GameIdleVideo -> {
                        pendingGameMediaId = request.gameId
                        gameIdleVideoPicker.launch(arrayOf("video/*"))
                    }
                    is HomeMediaPickerRequest.MusicCover -> {
                        pendingMusicMediaId = request.mediaId
                        musicCoverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    is HomeMediaPickerRequest.MusicWallpaper -> {
                        pendingMusicMediaId = request.mediaId
                        musicWallpaperPicker.launch(arrayOf("image/*", "video/*"))
                    }
                    HomeMediaPickerRequest.HomeFolderImage -> folderImagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
        }
    }

    LaunchedEffect(homeViewModel, context) {
        homeViewModel.externalAuthRequestFlow.collect { request ->
            when (request) {
                HomeExternalAuthRequest.SteamOpenId -> {
                    val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                    runCatching {
                        tabs.launchUrl(context, Uri.parse(SteamOpenId.authorizationUrl()))
                    }.onFailure {
                        snackbarHostState.showSnackbar("Could not open Steam sign-in.")
                    }
                }
                is HomeExternalAuthRequest.SpotifyOAuth -> {
                    val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                    runCatching {
                        tabs.launchUrl(context, Uri.parse(request.authorizeUrl))
                    }.onFailure {
                        snackbarHostState.showSnackbar("Could not open Spotify sign-in.")
                    }
                }
            }
        }
    }

    // The ViewModel handles Confirm from the gamepad itself, so it needs to be told which physical
    // screen the grid is on before a launch can be targeted anywhere.
    LaunchedEffect(shellState.gridDisplayId, shellState.otherDisplayId) {
        homeViewModel.setDisplayContext(shellState.gridDisplayId, shellState.otherDisplayId)
    }

    // Gamepad navigation belongs to the library only. Setup, onboarding, and the options dialog
    // are ordinary forms. Bottom sheets keep the dispatcher on so Select/U/D/B reach SheetNavCapture.
    LaunchedEffect(route, dialogOverlayOpen, shellState.showOnboarding) {
        homeViewModel.gamepadDispatcher.isEnabled =
            route == ShellRoute.Home && !dialogOverlayOpen && !shellState.showOnboarding
    }

    // Idle trailers are Home-only; Settings, options, Guide, Start config, welcome-back, boot, and launch overlay must return to artwork.
    LaunchedEffect(route, overlayOpen, state.isLaunching, state.guideOpen, state.startSettingsOpen, state.welcomeBackOpen, state.bootIntroOpen, shellState.showOnboarding) {
        homeViewModel.setTrailerGateAllowed(
            allowed = route == ShellRoute.Home &&
                !overlayOpen &&
                !state.isLaunching &&
                !state.guideOpen &&
                !state.startSettingsOpen &&
                !state.welcomeBackOpen &&
                !state.bootIntroOpen &&
                !shellState.showOnboarding,
        )
    }

    // Drop a queued wake greeting / boot clip if onboarding or Settings owns the shell.
    LaunchedEffect(state.welcomeBackOpen, state.bootIntroOpen, route, shellState.showOnboarding) {
        if (shellState.showOnboarding || route != ShellRoute.Home) {
            if (state.welcomeBackOpen) homeViewModel.dismissWelcomeBack()
            if (state.bootIntroOpen) homeViewModel.dismissBootIntro()
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is HomeEvent.ShowError -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Long,
                )
                HomeEvent.OpenSettings -> route = ShellRoute.Settings
                HomeEvent.LinkDiscordAccount -> {
                    val activity = context as? Activity
                    if (activity != null) {
                        homeViewModel.linkDiscordAccount(activity)
                    } else {
                        snackbarHostState.showSnackbar("Could not start Discord linking.")
                    }
                }
                is HomeEvent.RequestAudioAccess -> audioPermissionLauncher.launch(event.permission)
                is HomeEvent.RequestImageAccess ->
                    imagePermissionLauncher.launch(event.permissions.toTypedArray())
                is HomeEvent.RequestPhotoDelete -> runCatching {
                    photoDeleteLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build(),
                    )
                }.onFailure { homeViewModel.onPhotoDeleteResult(confirmed = false) }
                is HomeEvent.OpenGameOptions -> optionsGameId = event.gameId
                is HomeEvent.OpenScrapeMenu -> scrapeMenuGameId = event.gameId
                is HomeEvent.OpenMusicCustomize -> {
                    musicCustomizeId = event.mediaId
                    musicCustomizeTitle = event.title
                }
                HomeEvent.BringShellToFront -> bringShellToFront(context)
                HomeEvent.RequestUnknownAppSources -> {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            snackbarHostState.showSnackbar("Open system settings and allow XOrA to install apps.")
                        }
                }
                is HomeEvent.InstallApk -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(event.uri, "application/vnd.android.package-archive")
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK,
                        )
                    }
                    context.packageManager.queryIntentActivities(intent, 0).forEach { resolve ->
                        context.grantUriPermission(
                            resolve.activityInfo.packageName,
                            event.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            snackbarHostState.showSnackbar(
                                it.message ?: "Could not open the package installer.",
                            )
                        }
                }
            }
        }
    }

    if (!shellState.prefsReady) {
        SkyBackground(modifier = modifier.fillMaxSize(), sparkle = false) {}
        return
    }

    if (shellState.showOnboarding) {
        // Own the onboarding VM here so Steam Custom Tabs / Discord OAuth stay Activity-rooted
        // (same hoist pattern as HomeExternalAuthRequest for dual-screen).
        val onboardingViewModel: OnboardingViewModel = hiltViewModel()
        LaunchedEffect(onboardingViewModel, context) {
            onboardingViewModel.externalAuthRequestFlow.collect { request ->
                when (request) {
                    OnboardingExternalAuthRequest.SteamOpenId -> {
                        val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
                        runCatching {
                            tabs.launchUrl(context, Uri.parse(SteamOpenId.authorizationUrl()))
                        }.onFailure {
                            onboardingViewModel.showMessage("Could not open Steam sign-in.")
                        }
                    }
                    OnboardingExternalAuthRequest.LinkDiscord -> {
                        val activity = context as? Activity
                        if (activity != null) {
                            onboardingViewModel.linkDiscordAccount(activity)
                        } else {
                            onboardingViewModel.showMessage("Could not start Discord linking.")
                        }
                    }
                }
            }
        }
        OnboardingScreen(
            brandIcon = painterResource(R.mipmap.ic_launcher_foreground),
            onFinished = {
                onOnboardingFinished()
                route = ShellRoute.Home
            },
            viewModel = onboardingViewModel,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)

        Box(modifier = contentModifier) {
            // Keep Home mounted under Setup so the dim settings plate can show wallpaper through.
            if (shellState.useDualLayout) {
                PaneForRole(
                    role = shellState.primaryDisplayRole,
                    state = state,
                    homeViewModel = homeViewModel,
                )
            } else {
                HomeScreen(
                    state = state,
                    onSelectTab = homeViewModel::selectTab,
                    onSelectGame = homeViewModel::selectGame,
                    onLaunchGame = { index ->
                        homeViewModel.selectGame(index)
                        homeViewModel.launchSelected()
                    },
                    onSelectRssItem = homeViewModel::selectRssItem,
                    onOpenRssItem = { index ->
                        homeViewModel.selectRssItem(index)
                        homeViewModel.openSelectedRssItem()
                    },
                    onRetryRss = homeViewModel::refreshRssFeed,
                    onOpenSettings = { route = ShellRoute.Settings },
                    onToggleAccountPanel = homeViewModel::toggleAccountPanel,
                    onToggleSystemPanel = homeViewModel::toggleSystemPanel,
                    onOpenNotifications = homeViewModel::openNotificationHistory,
                    onToggleAchievementsPanel = homeViewModel::toggleAchievementsPanel,
                    onSelectSocialTab = homeViewModel::selectSocialMenuTab,
                    onSelectAccountRow = homeViewModel::selectAccountPanelRow,
                    onActivateAccountRow = homeViewModel::activateAccountPanelSelection,
                    onSelectSystemRow = homeViewModel::selectSystemPanelRow,
                    onActivateSystemRow = homeViewModel::activateSystemPanelSelection,
                    onSystemStatusDraftChange = homeViewModel::updateSystemStatusDraft,
                    onSaveCustomStatus = homeViewModel::saveCustomStatus,
                    onClearCustomStatus = homeViewModel::clearCustomStatus,
                    onSelectRaLibraryIndex = homeViewModel::selectRaLibraryIndex,
                    onSelectRaLibraryTab = homeViewModel::selectRaLibraryTab,
                    onSelectRaPlatformFilter = homeViewModel::selectRaPlatformFilter,
                    onActivateRaLibrary = homeViewModel::activateRaLibrarySelection,
                    onRetryRaLibrary = homeViewModel::refreshRaLibrary,
                    onSelectHomeShard = homeViewModel::selectHomeShard,
                    onActivateHomeShard = homeViewModel::activateHomeShard,
                    onSelectHomeShortcut = homeViewModel::selectHomeShortcut,
                    onActivateHomeShortcut = { homeViewModel.activateHomeShortcut(it) },
                    onAddHomeShortcut = homeViewModel::openAddShortcutChooser,
                    onSelectXoraCategory = homeViewModel::selectXoraCategory,
                    onSelectXoraItem = homeViewModel::selectXoraItem,
                    onActivateXoraItem = homeViewModel::activateXoraSelection,
                    onToggleNowPlaying = homeViewModel::toggleNowPlaying,
                    onSkipPreviousTrack = homeViewModel::skipPreviousTrack,
                    onSkipNextTrack = homeViewModel::skipNextTrack,
                    onToggleShuffle = homeViewModel::toggleShuffle,
                    onToggleRepeat = homeViewModel::toggleRepeat,
                    onRequestWallpaper = homeViewModel::requestWallpaperPicker,
                    onClearWallpaper = homeViewModel::clearHomeWallpaper,
                    onRequestBgm = homeViewModel::requestBgmPicker,
                    onClearBgm = homeViewModel::clearCustomBgm,
                    onOpenShortcutEditor = homeViewModel::openShortcutEditorFromThemes,
                    onDismissThemes = homeViewModel::dismissThemesSheet,
                    onSelectTheme = homeViewModel::selectShellTheme,
                    onShopComingSoon = homeViewModel::notifyShopThemesComingSoon,
                    onUploadComingSoon = homeViewModel::notifyThemeUploadComingSoon,
                    onDismissAddShortcut = homeViewModel::dismissAddShortcutChooser,
                    onPinRecentShortcut = homeViewModel::addShortcutPinRecentGame,
                    onPinAndroidShortcut = homeViewModel::addShortcutPinAndroidApp,
                    onPinPictureShortcut = homeViewModel::addShortcutPinPicture,
                    onPinGifShortcut = homeViewModel::addShortcutPinGif,
                    onSelectShortcutSpan = homeViewModel::selectPendingShortcutSpan,
                    onConfirmShortcutSpan = homeViewModel::confirmPendingShortcutSpan,
                    onCancelShortcutSpan = homeViewModel::cancelPendingShortcutSpan,
                    onSelectShortcutTarget = homeViewModel::selectShortcutTarget,
                    onConfirmShortcutTarget = homeViewModel::confirmShortcutTarget,
                    onCancelShortcutTargetPicker = homeViewModel::cancelShortcutTargetPicker,
                    onCycleHomeShortcutSpan = { homeViewModel.cycleFocusedShortcutSpan(it) },
                    onAdjustShortcutColumns = homeViewModel::adjustShortcutGridColumns,
                    onAdjustShortcutRows = homeViewModel::adjustShortcutGridRows,
                    onFocusShortcutCustomizeChrome = homeViewModel::focusShortcutCustomizeChrome,
                    onSaveProfile = homeViewModel::saveProfile,
                    onSelectAvatarPreset = homeViewModel::selectAvatarPreset,
                    onRequestLocalAvatar = homeViewModel::requestProfileAvatarPicker,
                    onUseRaAvatar = homeViewModel::useRaAvatar,
                    onUseDiscordAvatar = homeViewModel::useDiscordAvatar,
                    onUseXoraAvatar = homeViewModel::useXoraAvatar,
                    onXoraPresenceMode = homeViewModel::setXoraPresenceMode,
                    onClearAvatar = homeViewModel::clearAvatar,
                    onClearNotifications = homeViewModel::clearNotificationHistory,
                    onFriendSearchChange = homeViewModel::updateFriendSearchQuery,
                    onReplyDraftChange = homeViewModel::updateConversationReplyDraft,
                    onSelectAchievementsTab = homeViewModel::selectAchievementsTab,
                    onLoginRetroAchievements = homeViewModel::loginRetroAchievements,
                    onLoginRetroAchievementsWithApiKey =
                        homeViewModel::loginRetroAchievementsWithApiKey,
                    onSignOutRetroAchievements = homeViewModel::signOutRetroAchievements,
                    onPhotoCommand = homeViewModel::onPhotoCommand,
                    onDashboardCommand = homeViewModel::onDashboardCommand,
                )
            }

            AnimatedVisibility(
                visible = route == ShellRoute.Settings,
                enter = fadeIn(routeTween),
                exit = fadeOut(routeTween),
                modifier = Modifier.fillMaxSize(),
            ) {
                SettingsScreen(
                    onBack = { route = ShellRoute.Home },
                    onGoToOnboarding = onRestartOnboarding,
                    systemSection = {
                        HomeRoleCard(
                            state = shellState.homeRole,
                            onSetHomeCandidate = onSetHomeCandidate,
                            onOpenHomeSettings = onOpenHomeSettings,
                        )
                    },
                )
            }

            if (route == ShellRoute.Home) {
                GuideOverlay(
                    state = state,
                    homeViewModel = homeViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                StartSettingsOverlay(
                    state = state,
                    homeViewModel = homeViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                ThemesCustomizeOverlay(
                    state = state,
                    homeViewModel = homeViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                // Primary Activity only — same rule as Start settings / notification banners.
                BootIntroOverlay(
                    visible = state.bootIntroOpen && !shellState.showOnboarding,
                    skip = state.bootIntroSkip,
                    onRevealHome = homeViewModel::revealHomeAfterBoot,
                    onFinished = homeViewModel::dismissBootIntro,
                    modifier = Modifier.fillMaxSize(),
                )
                WelcomeBackOverlay(
                    visible = state.welcomeBackOpen && !shellState.showOnboarding,
                    profile = state.profile,
                    profileAvatarModel = state.profileAvatarModel,
                    onDismiss = homeViewModel::dismissWelcomeBack,
                    modifier = Modifier.fillMaxSize(),
                )
                NotificationBannerHost(
                    center = homeViewModel.shellNotifications,
                    ltExpanded = state.accountPanelExpanded,
                    onActivate = homeViewModel::activateShellNotification,
                )
                NotificationHistoryPanel(
                    open = state.notificationHistoryOpen,
                    items = state.notificationHistory,
                    selectedIndex = state.notificationHistorySelectedIndex,
                    onSelectIndex = homeViewModel::selectNotificationHistoryIndex,
                    onActivate = homeViewModel::activateSelectedNotificationHistory,
                    onClear = homeViewModel::clearNotificationHistory,
                    onDismissItem = homeViewModel::dismissNotificationHistoryItem,
                    onDismiss = homeViewModel::closeNotificationHistory,
                    modifier = Modifier.fillMaxSize(),
                )
                NetplayInvitePromptDialog(
                    prompt = state.pendingNetplayInvite.takeIf { state.netplayInvitePromptOpen },
                    onJoin = homeViewModel::confirmNetplayInvitePrompt,
                    onDecline = homeViewModel::dismissNetplayInvitePrompt,
                )
                DiscordConversationWindow(
                    open = state.socialMenu.isDiscordDmOpen,
                    thread = state.socialMenu.discordDm,
                    friends = state.socialMenu.discord.friends,
                    onDraftChange = homeViewModel::updateConversationReplyDraft,
                    onSend = homeViewModel::sendOpenDiscordDm,
                    onAttachMedia = homeViewModel::requestDiscordAttachment,
                    onDismiss = homeViewModel::closeOpenDiscordDm,
                    modifier = Modifier.fillMaxSize(),
                )
                XoraConversationWindow(
                    open = state.socialMenu.isXoraDmOpen,
                    network = state.socialMenu.xoraNetwork,
                    onDraftChange = homeViewModel::updateConversationReplyDraft,
                    onSend = homeViewModel::sendOpenXoraDm,
                    onDismiss = homeViewModel::closeOpenXoraDm,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (shellState.useDualLayout) {
        SecondaryDisplayPane(displayId = shellState.secondaryDisplayId) {
            // A presentation is its own composition root, so the theme has to be applied again.
            val darkTheme = shellState.themeMode.resolveDarkTheme(isSystemInDarkTheme())
            ArcadiaTheme(
                darkTheme = darkTheme,
                shellThemeId = shellState.shellThemeId,
                uiTextScale = shellState.uiTextScale,
                uiLayoutScale = shellState.secondaryUiLayoutScale,
            ) {
                when (route) {
                    ShellRoute.Settings -> SettingsCompanionPane(
                        modifier = Modifier.fillMaxSize(),
                    )

                    ShellRoute.Home -> Box(modifier = Modifier.fillMaxSize()) {
                        val companion = gameCompanion
                        if (companion != null) {
                            // A game owns the primary screen, so this pane becomes its companion.
                            // The same panel is re-hosted by CompanionOverlayService once the shell
                            // Activity stops and takes this Presentation down with it.
                            GameCompanionPane(
                                companion = companion,
                                onSelectAction = homeViewModel::selectCompanionAction,
                                onActivateAction = homeViewModel::activateCompanionAction,
                                onDismissOverlay = homeViewModel::dismissCompanionOverlay,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            PaneForRole(
                                role = shellState.secondaryDisplayRole,
                                state = state,
                                homeViewModel = homeViewModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Guide may still mirror; Start settings + notification banners stay on
                            // the primary Activity display only (topology.primary / first screen).
                            GuideOverlay(
                                state = state,
                                homeViewModel = homeViewModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Social often lives on the Hero/secondary pane — host the DM window
                            // here too so opening a friend chat is visible on that screen.
                            DiscordConversationWindow(
                                open = state.socialMenu.isDiscordDmOpen,
                                thread = state.socialMenu.discordDm,
                                friends = state.socialMenu.discord.friends,
                                onDraftChange = homeViewModel::updateConversationReplyDraft,
                                onSend = homeViewModel::sendOpenDiscordDm,
                                onAttachMedia = homeViewModel::requestDiscordAttachment,
                                onDismiss = homeViewModel::closeOpenDiscordDm,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    optionsGameId?.let { gameId ->
        val game = state.games.firstOrNull { it.id == gameId }
        if (game == null) {
            optionsGameId = null
        } else {
            GameOptionsDialog(
                game = game,
                isDualScreen = shellState.isDualScreen,
                loadPlayers = homeViewModel::playersFor,
                onDismiss = { optionsGameId = null },
                onPlay = {
                    optionsGameId = null
                    homeViewModel.launchSelected()
                },
                onToggleFavorite = { favorite -> homeViewModel.setFavorite(gameId, favorite) },
                onSelectPlayer = { playerId -> homeViewModel.setPlayerOverride(gameId, playerId) },
                onSelectDisplay = { preference ->
                    homeViewModel.setLaunchDisplayPreference(gameId, preference)
                },
            )
        }
    }

    scrapeMenuGameId?.let { gameId ->
        val game = state.games.firstOrNull { it.id == gameId }
        if (game == null) {
            scrapeMenuGameId = null
        } else {
            val gamePref by produceState(ScraperPreference.Auto, game.id) {
                value = homeViewModel.scraperPreferenceForGame(game.id)
            }
            val platformPref by produceState(ScraperPreference.Auto, game.platformId) {
                value = homeViewModel.scraperPreferenceForPlatform(game.platformId)
            }
            val emulatorLabel by produceState<String?>(null, game.platformId) {
                value = homeViewModel.platformEmulatorLabel(game.platformId)
            }
            val saveTick by homeViewModel.romSaveRefreshTick()
                .collectAsStateWithLifecycle()
            val mediaEpoch by homeViewModel.customMediaEpochFlow.collectAsStateWithLifecycle()
            val saves by produceState(emptyList<GameSaveEntry>(), game.id, saveTick, game.filePath) {
                value = homeViewModel.listSavesForGame(game)
            }
            val idlePath by produceState(
                homeViewModel.idleVideoPath(game.id),
                game.id,
                mediaEpoch,
            ) {
                value = homeViewModel.idleVideoPath(game.id)
            }
            SheetNavCapture(homeViewModel)
            RomOptionsSheet(
                game = game,
                saves = saves,
                gamePreference = gamePref,
                platformPreference = platformPref,
                currentEmulatorLabel = emulatorLabel,
                navActions = homeViewModel.sheetNavActionFlow,
                onDismiss = { scrapeMenuGameId = null },
                onToggleFavorite = { favorite -> homeViewModel.setFavorite(gameId, favorite) },
                hidden = gameId in state.hiddenGameIds,
                onToggleHidden = { hidden -> homeViewModel.setGameHidden(gameId, hidden) },
                artAlignX = state.gameArtAlignments[gameId]?.x ?: 0f,
                artAlignY = state.gameArtAlignments[gameId]?.y ?: 0f,
                onNudgeCover = { dx, dy -> homeViewModel.nudgeGameArtAlignment(gameId, dx, dy) },
                onResetCover = { homeViewModel.resetGameArtAlignment(gameId) },
                onPickBoxArt = { homeViewModel.pickGameBoxArt(gameId) },
                onPickBackground = { homeViewModel.pickGameBackground(gameId) },
                onPickSoundBite = { homeViewModel.pickGameSoundBite(gameId) },
                onPickIdleVideo = { homeViewModel.pickGameIdleVideo(gameId) },
                onClearBoxArt = { homeViewModel.clearGameBoxArt(gameId) },
                onClearBackground = { homeViewModel.clearGameBackground(gameId) },
                onClearSoundBite = { homeViewModel.clearGameSoundBite(gameId) },
                onClearIdleVideo = { homeViewModel.clearGameIdleVideo(gameId) },
                onPreviewSoundBite = { homeViewModel.previewGameSoundBite(gameId) },
                idleVideoPath = idlePath,
                onImportSaves = { homeViewModel.importSavesForGame(gameId) },
                onDeleteSave = { entry -> homeViewModel.deleteSaveForGame(entry) },
                onSetGamePreference = { pref ->
                    homeViewModel.setGameScraperPreference(gameId, pref)
                },
                onSetPlatformPreference = { pref ->
                    homeViewModel.setPlatformScraperPreference(game.platformId, pref)
                },
                onChooseEmulator = {
                    scrapeMenuGameId = null
                    chooseEmulatorPlatformId = game.platformId
                },
                onRescrapeGame = { homeViewModel.rescrapeGame(gameId) },
                onRescrapePlatform = { homeViewModel.rescrapePlatform(game.platformId) },
            )
        }
    }

    musicCustomizeId?.let { mediaId ->
        val mediaEpoch by homeViewModel.customMediaEpochFlow.collectAsStateWithLifecycle()
        val coverPath by produceState(homeViewModel.musicCoverPath(mediaId), mediaId, mediaEpoch) {
            value = homeViewModel.musicCoverPath(mediaId)
        }
        val wallpaperPath by produceState(
            homeViewModel.musicWallpaperPath(mediaId),
            mediaId,
            mediaEpoch,
        ) {
            value = homeViewModel.musicWallpaperPath(mediaId)
        }
        SheetNavCapture(homeViewModel)
        MusicCustomizeSheet(
            title = musicCustomizeTitle,
            coverPath = coverPath,
            wallpaperPath = wallpaperPath,
            navActions = homeViewModel.sheetNavActionFlow,
            onDismiss = { musicCustomizeId = null },
            onPickCover = { homeViewModel.pickMusicCover(mediaId) },
            onPickWallpaper = { homeViewModel.pickMusicWallpaper(mediaId) },
            onClearCover = { homeViewModel.clearMusicCover(mediaId) },
            onClearWallpaper = { homeViewModel.clearMusicWallpaper(mediaId) },
        )
    }

    chooseEmulatorPlatformId?.let { platformId ->
        val platform = PlatformCatalog.byId(platformId)
        if (platform == null) {
            chooseEmulatorPlatformId = null
        } else {
            val options by produceState(initialValue = emptyList<DetectedEmulator>(), platformId) {
                value = homeViewModel.detectEmulatorsForPlatform(platformId)
            }
            val selectedId by produceState<String?>(null, platformId) {
                value = homeViewModel.selectedPlatformEmulatorId(platformId)
            }
            SheetNavCapture(homeViewModel)
            ChooseEmulatorSheet(
                platform = platform,
                options = options,
                selectedPlayerId = selectedId,
                emptyMessage = homeViewModel.emulatorEmptyMessage(platformId),
                navActions = homeViewModel.sheetNavActionFlow,
                onSelect = { emulator ->
                    homeViewModel.selectPlatformEmulator(platformId, emulator)
                    chooseEmulatorPlatformId = null
                },
                onClear = {
                    homeViewModel.clearPlatformEmulator(platformId)
                },
                onDismiss = { chooseEmulatorPlatformId = null },
            )
        }
    }
}

/**
 * Redirects controller nav to whichever bottom sheet is composed, for as long as it is composed.
 */
@Composable
private fun SheetNavCapture(homeViewModel: HomeViewModel) {
    DisposableEffect(homeViewModel) {
        homeViewModel.setBottomSheetNavOpen(true)
        onDispose { homeViewModel.setBottomSheetNavOpen(false) }
    }
}

@Composable
private fun GuideOverlay(
    state: HomeUiState,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    GuidePanel(
        guide = state.guide,
        profile = state.profile,
        profileAvatarModel = state.profileAvatarModel,
        achievements = state.achievements,
        onSelectIndex = homeViewModel::selectGuideIndex,
        onActivate = homeViewModel::activateGuideSelection,
        onDismiss = homeViewModel::closeGuide,
        modifier = modifier,
    )
}

@Composable
private fun StartSettingsOverlay(
    state: HomeUiState,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    StartSettingsPanel(
        state = state.startSettings,
        onSelectCategory = homeViewModel::selectStartSettingsCategory,
        onSelectRow = homeViewModel::selectStartSettingsRow,
        onActivate = { homeViewModel.activateStartSettingsSelection() },
        onDismiss = homeViewModel::closeStartSettings,
        modifier = modifier,
    )
}

/**
 * Wallpaper / BGM customize sheet on the primary Activity only.
 *
 * Kept out of [HomePageContent] / Presentation panes: those used to host ThemesSheet, so
 * Start → Themes → Customize… set `themesOpen` without drawing anything when the user wasn't
 * already on the Home page (or was looking at the wrong display).
 */
@Composable
private fun ThemesCustomizeOverlay(
    state: HomeUiState,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    if (!state.homeHub.themesOpen) return
    Box(modifier = modifier) {
        ThemesSheet(
            activeThemeId = LocalShellTheme.current.id.id,
            shopThemeIds = emptyList(),
            hasCustomWallpaper = !state.homeHub.wallpaperPath.isNullOrBlank(),
            customWallpaperLabel = state.homeHub.wallpaperPath
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: "Custom wallpaper",
            hasCustomBgm = !state.homeHub.customBgmPath.isNullOrBlank(),
            shortcutCount = state.homeHub.shortcuts.size,
            initialTab = state.homeHub.themesSheetTab,
            onDismiss = homeViewModel::dismissThemesSheet,
            onSelectTheme = homeViewModel::selectShellTheme,
            onShopComingSoon = homeViewModel::notifyShopThemesComingSoon,
            onUploadComingSoon = homeViewModel::notifyThemeUploadComingSoon,
            onRequestWallpaper = homeViewModel::requestWallpaperPicker,
            onClearWallpaper = homeViewModel::clearHomeWallpaper,
            onRequestBgm = homeViewModel::requestBgmPicker,
            onClearBgm = homeViewModel::clearCustomBgm,
            onManageShortcuts = homeViewModel::openShortcutEditorFromThemes,
            wallpaperAlignX = state.homeHub.wallpaperAlignX,
            wallpaperAlignY = state.homeHub.wallpaperAlignY,
            onNudgeWallpaper = homeViewModel::nudgeWallpaperAlignment,
            onResetWallpaper = homeViewModel::resetWallpaperAlignment,
        )
    }
}

/** Full-bleed XOrA brand art for the secondary display while Settings owns the primary. */
@Composable
private fun SettingsCompanionPane(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.sora_settings_hero),
        contentDescription = "XOrA",
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

private fun bringShellToFront(context: android.content.Context) {
    val activity = context as? Activity ?: return
    runCatching {
        val am = activity.getSystemService(ActivityManager::class.java) ?: return
        @Suppress("DEPRECATION")
        am.moveTaskToFront(activity.taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION)
    }
}

@Composable
private fun PaneForRole(
    role: ScreenRole,
    state: HomeUiState,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
    val exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
    AnimatedContent(
        targetState = role,
        transitionSpec = { enter togetherWith exit },
        label = "paneRole",
        modifier = modifier,
    ) { currentRole ->
        when (currentRole) {
            ScreenRole.Hero -> {
                if (state.homePage == HomePage.Home) {
                    XoraXmbHeroDetail(
                        state = state,
                        onToggleAccountPanel = homeViewModel::toggleAccountPanel,
                        onToggleSystemPanel = homeViewModel::toggleSystemPanel,
                    onOpenNotifications = homeViewModel::openNotificationHistory,
                        onToggleAchievementsPanel = homeViewModel::toggleAchievementsPanel,
                        onSelectSocialTab = homeViewModel::selectSocialMenuTab,
                        onSelectAccountRow = homeViewModel::selectAccountPanelRow,
                        onActivateAccountRow = homeViewModel::activateAccountPanelSelection,
                        onSelectSystemRow = homeViewModel::selectSystemPanelRow,
                        onActivateSystemRow = homeViewModel::activateSystemPanelSelection,
                        onSystemStatusDraftChange = homeViewModel::updateSystemStatusDraft,
                        onSaveCustomStatus = homeViewModel::saveCustomStatus,
                        onClearCustomStatus = homeViewModel::clearCustomStatus,
                        onSaveProfile = homeViewModel::saveProfile,
                        onSelectAvatarPreset = homeViewModel::selectAvatarPreset,
                        onRequestLocalAvatar = homeViewModel::requestProfileAvatarPicker,
                        onUseRaAvatar = homeViewModel::useRaAvatar,
                        onUseDiscordAvatar = homeViewModel::useDiscordAvatar,
                        onUseXoraAvatar = homeViewModel::useXoraAvatar,
                        onXoraPresenceMode = homeViewModel::setXoraPresenceMode,
                        onClearAvatar = homeViewModel::clearAvatar,
                        onClearNotifications = homeViewModel::clearNotificationHistory,
                        onFriendSearchChange = homeViewModel::updateFriendSearchQuery,
                        onReplyDraftChange = homeViewModel::updateConversationReplyDraft,
                        onSelectAchievementsTab = homeViewModel::selectAchievementsTab,
                        onLoginRetroAchievements = homeViewModel::loginRetroAchievements,
                        onLoginRetroAchievementsWithApiKey =
                            homeViewModel::loginRetroAchievementsWithApiKey,
                        onSignOutRetroAchievements = homeViewModel::signOutRetroAchievements,
                        showPillChrome = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    HeroPane(
                        game = state.selectedGame,
                        profile = state.profile,
                        profileAvatarModel = state.profileAvatarModel,
                        raConfigured = state.achievements.credentials.isConfigured,
                        accountPanelExpanded = state.accountPanelExpanded,
                        systemPanelExpanded = state.systemPanelExpanded,
                        achievementsPanelExpanded = state.achievementsPanelExpanded,
                        achievements = state.achievements,
                        quickLaunchGames = state.quickLaunchGames,
                        socialMenu = state.socialMenu,
                        accountPanelRows = state.accountPanelRows,
                        accountPanelSelectedIndex = state.accountPanelSelectedIndex,
                        systemPanelSelectedIndex = state.systemPanelSelectedIndex,
                        systemProfile = state.systemProfile,
                        trailer = state.trailer,
                        isLaunching = state.isLaunching,
                        rssItem = state.rss.selectedItem.takeIf {
                            state.homePage == HomePage.RssFeed
                        },
                        showHomeWallpaper = false,
                        homeWallpaperPath = state.homeHub.wallpaperPath,
                        wallpaperAlignX = state.homeHub.wallpaperAlignX,
                        wallpaperAlignY = state.homeHub.wallpaperAlignY,
                        onToggleAccountPanel = homeViewModel::toggleAccountPanel,
                        onToggleSystemPanel = homeViewModel::toggleSystemPanel,
                        onOpenNotifications = homeViewModel::openNotificationHistory,
                        notificationUnreadCount = state.notificationUnreadCount,
                        activeNotificationPresent = state.activeNotificationPresent,
                        onToggleAchievementsPanel = homeViewModel::toggleAchievementsPanel,
                        onSelectSocialTab = homeViewModel::selectSocialMenuTab,
                        onSelectAccountRow = homeViewModel::selectAccountPanelRow,
                        onActivateAccountRow = homeViewModel::activateAccountPanelSelection,
                        onSelectSystemRow = homeViewModel::selectSystemPanelRow,
                        onActivateSystemRow = homeViewModel::activateSystemPanelSelection,
                        onSystemStatusDraftChange = homeViewModel::updateSystemStatusDraft,
                        onSaveCustomStatus = homeViewModel::saveCustomStatus,
                        onClearCustomStatus = homeViewModel::clearCustomStatus,
                        profileEditRequest = state.profileEditRequest,
                        onSaveProfile = homeViewModel::saveProfile,
                        onSelectAvatarPreset = homeViewModel::selectAvatarPreset,
                        onRequestLocalAvatar = homeViewModel::requestProfileAvatarPicker,
                        onUseRaAvatar = homeViewModel::useRaAvatar,
                        onUseDiscordAvatar = homeViewModel::useDiscordAvatar,
                        onUseXoraAvatar = homeViewModel::useXoraAvatar,
                        onXoraPresenceMode = homeViewModel::setXoraPresenceMode,
                        onClearAvatar = homeViewModel::clearAvatar,
                        onClearNotifications = homeViewModel::clearNotificationHistory,
                        onFriendSearchChange = homeViewModel::updateFriendSearchQuery,
                        onReplyDraftChange = homeViewModel::updateConversationReplyDraft,
                        onSelectAchievementsTab = homeViewModel::selectAchievementsTab,
                        onLoginRetroAchievements = homeViewModel::loginRetroAchievements,
                        onLoginRetroAchievementsWithApiKey =
                            homeViewModel::loginRetroAchievementsWithApiKey,
                        onSignOutRetroAchievements = homeViewModel::signOutRetroAchievements,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            ScreenRole.Grid -> {
                val launchProgress = rememberLaunchCinematic(state.isLaunching).chrome
                HomePageContent(
                    state = state,
                    onSelectTab = homeViewModel::selectTab,
                    onSelectGame = homeViewModel::selectGame,
                    onLaunchGame = { index ->
                        homeViewModel.selectGame(index)
                        homeViewModel.launchSelected()
                    },
                    onSelectRssItem = homeViewModel::selectRssItem,
                    onOpenRssItem = { index ->
                        homeViewModel.selectRssItem(index)
                        homeViewModel.openSelectedRssItem()
                    },
                    onRetryRss = homeViewModel::refreshRssFeed,
                    onSelectRaLibraryIndex = homeViewModel::selectRaLibraryIndex,
                    onSelectRaLibraryTab = homeViewModel::selectRaLibraryTab,
                    onSelectRaPlatformFilter = homeViewModel::selectRaPlatformFilter,
                    onActivateRaLibrary = homeViewModel::activateRaLibrarySelection,
                    onRetryRaLibrary = homeViewModel::refreshRaLibrary,
                    onSelectHomeShard = homeViewModel::selectHomeShard,
                    onActivateHomeShard = homeViewModel::activateHomeShard,
                    onSelectHomeShortcut = homeViewModel::selectHomeShortcut,
                    onActivateHomeShortcut = { homeViewModel.activateHomeShortcut(it) },
                    onAddHomeShortcut = homeViewModel::openAddShortcutChooser,
                    onSelectXoraCategory = homeViewModel::selectXoraCategory,
                    onSelectXoraItem = homeViewModel::selectXoraItem,
                    onActivateXoraItem = homeViewModel::activateXoraSelection,
                    onToggleNowPlaying = homeViewModel::toggleNowPlaying,
                    onSkipPreviousTrack = homeViewModel::skipPreviousTrack,
                    onSkipNextTrack = homeViewModel::skipNextTrack,
                    onToggleShuffle = homeViewModel::toggleShuffle,
                    onToggleRepeat = homeViewModel::toggleRepeat,
                    onToggleAccountPanel = homeViewModel::toggleAccountPanel,
                    onToggleSystemPanel = homeViewModel::toggleSystemPanel,
                    onOpenNotifications = homeViewModel::openNotificationHistory,
                    onToggleAchievementsPanel = homeViewModel::toggleAchievementsPanel,
                    onSelectSocialTab = homeViewModel::selectSocialMenuTab,
                    onSelectAccountRow = homeViewModel::selectAccountPanelRow,
                    onActivateAccountRow = homeViewModel::activateAccountPanelSelection,
                    onSelectSystemRow = homeViewModel::selectSystemPanelRow,
                    onActivateSystemRow = homeViewModel::activateSystemPanelSelection,
                    onSystemStatusDraftChange = homeViewModel::updateSystemStatusDraft,
                    onSaveCustomStatus = homeViewModel::saveCustomStatus,
                    onClearCustomStatus = homeViewModel::clearCustomStatus,
                    onSaveProfile = homeViewModel::saveProfile,
                    onSelectAvatarPreset = homeViewModel::selectAvatarPreset,
                    onRequestLocalAvatar = homeViewModel::requestProfileAvatarPicker,
                    onUseRaAvatar = homeViewModel::useRaAvatar,
                    onUseDiscordAvatar = homeViewModel::useDiscordAvatar,
                    onUseXoraAvatar = homeViewModel::useXoraAvatar,
                    onXoraPresenceMode = homeViewModel::setXoraPresenceMode,
                    onClearAvatar = homeViewModel::clearAvatar,
                    onClearNotifications = homeViewModel::clearNotificationHistory,
                    onFriendSearchChange = homeViewModel::updateFriendSearchQuery,
                    onReplyDraftChange = homeViewModel::updateConversationReplyDraft,
                    onSelectAchievementsTab = homeViewModel::selectAchievementsTab,
                    onLoginRetroAchievements = homeViewModel::loginRetroAchievements,
                    onLoginRetroAchievementsWithApiKey =
                        homeViewModel::loginRetroAchievementsWithApiKey,
                    onSignOutRetroAchievements = homeViewModel::signOutRetroAchievements,
                    onRequestWallpaper = homeViewModel::requestWallpaperPicker,
                    onClearWallpaper = homeViewModel::clearHomeWallpaper,
                    onRequestBgm = homeViewModel::requestBgmPicker,
                    onClearBgm = homeViewModel::clearCustomBgm,
                    onOpenShortcutEditor = homeViewModel::openShortcutEditorFromThemes,
                    onDismissThemes = homeViewModel::dismissThemesSheet,
                    onSelectTheme = homeViewModel::selectShellTheme,
                    onShopComingSoon = homeViewModel::notifyShopThemesComingSoon,
                    onUploadComingSoon = homeViewModel::notifyThemeUploadComingSoon,
                    onDismissAddShortcut = homeViewModel::dismissAddShortcutChooser,
                    onPinRecentShortcut = homeViewModel::addShortcutPinRecentGame,
                    onPinAndroidShortcut = homeViewModel::addShortcutPinAndroidApp,
                    onPinPictureShortcut = homeViewModel::addShortcutPinPicture,
                    onPinGifShortcut = homeViewModel::addShortcutPinGif,
                    onSelectShortcutSpan = homeViewModel::selectPendingShortcutSpan,
                    onConfirmShortcutSpan = homeViewModel::confirmPendingShortcutSpan,
                    onCancelShortcutSpan = homeViewModel::cancelPendingShortcutSpan,
                    onSelectShortcutTarget = homeViewModel::selectShortcutTarget,
                    onConfirmShortcutTarget = homeViewModel::confirmShortcutTarget,
                    onCancelShortcutTargetPicker = homeViewModel::cancelShortcutTargetPicker,
                    onCycleHomeShortcutSpan = { homeViewModel.cycleFocusedShortcutSpan(it) },
                    onAdjustShortcutColumns = homeViewModel::adjustShortcutGridColumns,
                    onAdjustShortcutRows = homeViewModel::adjustShortcutGridRows,
                    onFocusShortcutCustomizeChrome = homeViewModel::focusShortcutCustomizeChrome,
                    onPhotoCommand = homeViewModel::onPhotoCommand,
                    onDashboardCommand = homeViewModel::onDashboardCommand,
                    showWallpaperBackdrop = state.homePage == HomePage.Home,
                    // XMB owns its own launch hold; fading the whole Grid pane wiped the art.
                    modifier = if (state.homePage == HomePage.Home) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 1f - launchProgress
                                translationY = launchProgress * 96f
                            }
                    },
                )
            }
        }
    }
}
