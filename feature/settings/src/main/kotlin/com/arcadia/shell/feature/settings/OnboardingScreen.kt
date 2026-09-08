package com.arcadia.shell.feature.settings

import android.app.Activity
import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcadia.shell.datastore.AvatarSource
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.datastore.SteamWebApiCredentials
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.DefaultThemeBackdrop
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.designsystem.xoraSwipeNavigate
import com.arcadia.shell.input.NavAction
import kotlinx.coroutines.flow.Flow
import com.arcadia.shell.designsystem.XoraSwipeDirection
import com.arcadia.shell.launcher.discord.DiscordPresenceCapability
import com.arcadia.shell.launcher.discord.DiscordPresenceUiState
import kotlin.math.roundToInt

private val AccentInk = Color(0xFF7EC8E8)
private val TrackInk = Color.White.copy(alpha = 0.16f)
private val MutedInk = Color.White.copy(alpha = 0.62f)

/**
 * First-run (and Settings-restarted) onboarding. Landscape / controller-friendly: A / Right / RB
 * advance, B / Left / LB go back, Y skips an optional step. D-pad and analog stick use the same
 * path as Home so handheld hat switches work.
 *
 * Steam Custom Tabs and Discord account linking are requested via [OnboardingViewModel] and
 * handled by ArcadiaShell (Activity-rooted), matching dual-screen / MainActivity auth hoisting.
 */
@Composable
fun OnboardingScreen(
    brandIcon: Painter,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    padActions: Flow<NavAction>? = null,
    onPadCapture: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val profilePadRegistry = remember { SettingsPadRegistry() }
    var profilePad by remember {
        mutableStateOf(SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0))
    }
    val profilePadNow = rememberUpdatedState(profilePad)
    val scraperListPadRegistry = remember { SettingsPadRegistry() }
    val scraperSheetPadRegistry = remember { SettingsPadRegistry() }
    var scraperPad by remember {
        mutableStateOf(SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0))
    }
    val scraperPadNow = rememberUpdatedState(scraperPad)
    var scraperSheet by remember { mutableStateOf<OnboardingScraperService?>(null) }
    val scraperSheetNow = rememberUpdatedState(scraperSheet)

    val safPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(viewModel::addSafRoot)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(viewModel::setLocalAvatar)
    }

    val onFinishedNow = rememberUpdatedState(onFinished)
    val stateNow = rememberUpdatedState(state)
    val pickerOpenNow = rememberUpdatedState(showFolderPicker)
    DisposableEffect(onPadCapture) {
        onPadCapture(true)
        onDispose { onPadCapture(false) }
    }
    LaunchedEffect(padActions) {
        val flow = padActions ?: return@LaunchedEffect
        var lastDirectionalMs: Long? = null
        flow.collect { action ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (!shouldAcceptOnboardingPadRepeat(action, now, lastDirectionalMs)) {
                return@collect
            }
            if (action == NavAction.Left || action == NavAction.Right) {
                lastDirectionalMs = now
            }
            val current = stateNow.value
            val scraperOpen = scraperSheetNow.value != null
            val intraForm = current.step == OnboardingStep.Profile ||
                current.step == OnboardingStep.Scrapers
            if (intraForm &&
                (action == NavAction.Left || action == NavAction.Right ||
                    action == NavAction.Up || action == NavAction.Down)
            ) {
                val registry = when {
                    current.step == OnboardingStep.Scrapers && scraperOpen ->
                        scraperSheetPadRegistry
                    current.step == OnboardingStep.Scrapers -> scraperListPadRegistry
                    else -> profilePadRegistry
                }
                val padNow = if (current.step == OnboardingStep.Scrapers) {
                    scraperPadNow.value
                } else {
                    profilePadNow.value
                }
                val layout = registry.layout()
                val next = settingsPadAfterAction(
                    settingsPadCoerce(padNow, layout),
                    action,
                    sectionCount = 1,
                    layout = layout,
                )
                if (current.step == OnboardingStep.Scrapers) scraperPad = next else profilePad = next
                return@collect
            }
            when (
                onboardingPadCommand(
                    action = action,
                    canGoBack = current.canGoBack,
                    canAdvance = current.canAdvance,
                    optional = isOptional(current.step),
                    pickerOpen = pickerOpenNow.value || scraperOpen,
                    intraForm = intraForm,
                )
            ) {
                OnboardingPadCommand.Next -> {
                    if (current.isLast) viewModel.finish(onFinishedNow.value) else viewModel.next()
                }
                OnboardingPadCommand.Back -> viewModel.back()
                OnboardingPadCommand.Skip -> viewModel.skipOptional()
                OnboardingPadCommand.DismissPicker -> {
                    if (scraperOpen) {
                        scraperSheet = null
                        scraperPad = SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0)
                    } else {
                        showFolderPicker = false
                    }
                }
                OnboardingPadCommand.Activate -> {
                    val registry = when {
                        current.step == OnboardingStep.Scrapers && scraperOpen ->
                            scraperSheetPadRegistry
                        current.step == OnboardingStep.Scrapers -> scraperListPadRegistry
                        else -> profilePadRegistry
                    }
                    val padNow = if (current.step == OnboardingStep.Scrapers) {
                        scraperPadNow.value
                    } else {
                        profilePadNow.value
                    }
                    val layout = registry.layout()
                    val focusId = settingsPadFocusId(
                        settingsPadCoerce(padNow, layout),
                        layout,
                    )
                    focusId?.let(registry::binding)?.activate?.invoke()
                }
                OnboardingPadCommand.None -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.step) {
        if (state.step != OnboardingStep.Scrapers) {
            scraperSheet = null
            scraperPad = SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0)
        }
    }

    LaunchedEffect(state.message) {
        // Transient toast-style message is shown in the card footer; clear after display window.
        if (state.message != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.consumeMessage()
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            volumes = state.suggestedVolumes,
            listDirectories = viewModel::listDirectories,
            onDismiss = { showFolderPicker = false },
            onPick = { path ->
                viewModel.addFilesystemRoot(path)
                showFolderPicker = false
            },
        )
    }

        Box(
            modifier = modifier
                .fillMaxSize()
                .xoraSwipeNavigate(
                    vertical = false,
                    onSwipe = { direction ->
                        when (direction) {
                            XoraSwipeDirection.Left -> {
                                if (!state.isLast && state.canAdvance) viewModel.next()
                            }
                            XoraSwipeDirection.Right -> {
                                if (state.canGoBack) viewModel.back()
                            }
                            else -> Unit
                        }
                    },
                ),
        ) {
        // Same looping wallpaper the themed home shell shows, so first run already looks like XOrA.
        DefaultThemeBackdrop(modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    val intraForm = state.step == OnboardingStep.Profile ||
                        state.step == OnboardingStep.Scrapers
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            if (intraForm) return@onPreviewKeyEvent false
                            if (state.isLast) {
                                viewModel.finish(onFinished)
                            } else if (state.canAdvance) {
                                viewModel.next()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_B,
                        KeyEvent.KEYCODE_BACK,
                        -> {
                            if (scraperSheet != null) {
                                scraperSheet = null
                                scraperPad = SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0)
                                true
                            } else if (state.canGoBack) {
                                viewModel.back()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                .padding(horizontal = 40.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingStepRail(
                    step = state.step,
                    stepIndex = state.stepIndex,
                    stepCount = state.stepCount,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .xoraModalGlass(ArcadiaGlass.PanelShape),
                ) {
                    val fadeInSpec = arcadiaTween<Float>(ArcadiaMotion.Medium)
                    val fadeOutSpec = arcadiaTween<Float>(ArcadiaMotion.Fast)
                    val slideInSpec = arcadiaTween<IntOffset>(ArcadiaMotion.Medium)
                    val slideOutSpec = arcadiaTween<IntOffset>(ArcadiaMotion.Fast)
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val enter = fadeIn(fadeInSpec) +
                                slideInHorizontally(
                                    animationSpec = slideInSpec,
                                    initialOffsetX = { if (forward) it / 8 else -it / 8 },
                                )
                            val exit = fadeOut(fadeOutSpec) +
                                slideOutHorizontally(
                                    animationSpec = slideOutSpec,
                                    targetOffsetX = { if (forward) -it / 10 else it / 10 },
                                )
                            enter togetherWith exit
                        },
                        label = "onboardingStep",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                    ) { step ->
                        val padRegistry = when (step) {
                            OnboardingStep.Profile -> profilePadRegistry
                            OnboardingStep.Scrapers ->
                                if (scraperSheet != null) scraperSheetPadRegistry
                                else scraperListPadRegistry
                            else -> null
                        }
                        val padState = when (step) {
                            OnboardingStep.Profile -> profilePad
                            OnboardingStep.Scrapers -> scraperPad
                            else -> null
                        }
                        val padLayout = padRegistry?.layout()
                        CompositionLocalProvider(
                            LocalSettingsPadRegistry provides padRegistry,
                            LocalSettingsPadFocusId provides padLayout?.let { layout ->
                                settingsPadFocusId(
                                    settingsPadCoerce(padState!!, layout),
                                    layout,
                                )
                            },
                        ) {
                        when (step) {
                            OnboardingStep.Welcome -> WelcomeStep(brandIcon = brandIcon)
                            OnboardingStep.Profile -> ProfileStep(
                                profile = state.profile,
                                avatarPath = state.avatarPath,
                                onNameChange = viewModel::setProfileName,
                                onSelectPreset = viewModel::selectAvatarPreset,
                                onPickPhoto = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                onClearPhoto = viewModel::clearLocalAvatar,
                            )
                            OnboardingStep.DisplayMode -> DisplayModeStep(
                                mode = state.settings.displayMode,
                                onSelect = viewModel::setDisplayMode,
                            )
                            OnboardingStep.Library -> LibraryStep(
                                hasStorageAccess = state.hasStorageAccess,
                                roots = state.roots,
                                onGrantAccess = {
                                    permissionLauncher.launch(viewModel.allFilesAccessIntent())
                                },
                                onAddFolder = { showFolderPicker = true },
                                onAddSaf = {
                                    safPicker.launch(viewModel.openDocumentTreeIntent())
                                },
                            )
                            OnboardingStep.AndroidApps -> AndroidAppsStep(
                                apps = state.androidApps,
                                selectedPackages = state.selectedAndroidPackages,
                                query = state.androidAppQuery,
                                onQueryChange = viewModel::setAndroidAppQuery,
                                onToggle = viewModel::toggleAndroidApp,
                                onSelectAll = viewModel::selectAllAndroidApps,
                                onClear = viewModel::clearAndroidApps,
                            )
                            OnboardingStep.Emulators -> EmulatorsStep(
                                scanRunning = state.scanRunning,
                                scanCompleted = state.scanCompleted,
                                scanError = state.scanError,
                                filesSeen = state.filesSeen,
                                hasFolders = state.roots.isNotEmpty(),
                                choices = state.platformChoices,
                                onEnsureScan = viewModel::ensureLibraryScanned,
                                onRetry = viewModel::retryLibraryScan,
                                onSelectPlayer = viewModel::selectPlayer,
                            )
                            OnboardingStep.Scrapers -> ScrapersStep(
                                credentials = state.credentials,
                                open = scraperSheet,
                                onOpen = { service ->
                                    scraperSheet = service
                                    scraperPad = SettingsPadNavState(
                                        0,
                                        SettingsPadZone.Controls,
                                        0,
                                        0,
                                    )
                                },
                                onClose = {
                                    scraperSheet = null
                                    scraperPad = SettingsPadNavState(
                                        0,
                                        SettingsPadZone.Controls,
                                        0,
                                        0,
                                    )
                                },
                                onSteamGridDbKey = viewModel::setSteamGridDbKey,
                                onIgdb = viewModel::setIgdbCredentials,
                                onScreenScraper = viewModel::setScreenScraperCredentials,
                                onScreenScraperDev = viewModel::setScreenScraperDevCredentials,
                            )
                            OnboardingStep.Social -> SocialStep(
                                steam = state.steamWebApi,
                                discordPresence = state.discordPresence,
                                notificationListenerEnabled = state.notificationListenerEnabled,
                                onSignInSteam = viewModel::requestSteamOpenId,
                                onSteamApiKey = viewModel::setSteamWebApiKey,
                                onLinkDiscord = viewModel::requestLinkDiscord,
                                onOpenNotificationAccess = {
                                    permissionLauncher.launch(
                                        viewModel.notificationListenerSettingsIntent(),
                                    )
                                },
                            )
                            OnboardingStep.RetroAchievements -> RetroAchievementsStep(
                                configured = state.retroAchievements,
                                isBusy = state.raAuthBusy,
                                error = state.raAuthError,
                                pendingWebApiUsername = state.raPendingWebApiUsername,
                                onPasswordSignIn = viewModel::loginRetroAchievements,
                                onApiKeySignIn = viewModel::setRetroAchievementsCredentials,
                            )
                            OnboardingStep.Audio -> AudioStep(
                                bgmVolume = state.settings.bgmVolume,
                                uiSfxVolume = state.settings.uiSfxVolume,
                                onBgmChange = viewModel::setBgmVolume,
                                onSfxChange = viewModel::setUiSfxVolume,
                            )
                            OnboardingStep.Done -> DoneStep()
                        }
                        }
                    }
                }

                state.message?.let { msg ->
                    XoraSecondaryText(text = msg, fontSize = 13.sp, fillColor = AccentInk)
                }

                OnboardingActions(
                    state = state,
                    onBack = viewModel::back,
                    onNext = {
                        if (state.isLast) viewModel.finish(onFinished) else viewModel.next()
                    },
                    onSkip = viewModel::skipOptional,
                )

                OnboardingHints(state = state, scraperSheet = scraperSheet)
            }
        }
    }
}

/**
 * One segment per step instead of a single percentage bar, so the flow shows how much is left
 * and names where you are. Optional steps say so here rather than only via a Skip button.
 */
@Composable
private fun OnboardingStepRail(
    step: OnboardingStep,
    stepIndex: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= stepIndex) AccentInk else TrackInk),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            XoraSecondaryText(
                text = stepLabel(step),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (isOptional(step)) {
                XoraSecondaryText(
                    text = "OPTIONAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fillColor = MutedInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            XoraSecondaryText(
                text = "${stepIndex + 1} / $stepCount",
                fontSize = 12.sp,
                fillColor = MutedInk,
                maxLines = 1,
            )
        }
    }
}

private fun stepLabel(step: OnboardingStep): String = when (step) {
    OnboardingStep.Welcome -> "Welcome"
    OnboardingStep.Profile -> "Profile"
    OnboardingStep.DisplayMode -> "Display"
    OnboardingStep.Library -> "Library"
    OnboardingStep.AndroidApps -> "Android"
    OnboardingStep.Emulators -> "Emulators"
    OnboardingStep.Scrapers -> "Artwork"
    OnboardingStep.Social -> "Social"
    OnboardingStep.RetroAchievements -> "Achievements"
    OnboardingStep.Audio -> "Sound"
    OnboardingStep.Done -> "Finish"
}

/** Steps that only link external accounts, so they can be skipped without breaking setup. */
private fun isOptional(step: OnboardingStep): Boolean =
    step == OnboardingStep.AndroidApps ||
        step == OnboardingStep.Scrapers ||
        step == OnboardingStep.Social ||
        step == OnboardingStep.RetroAchievements

@Composable
private fun WelcomeStep(brandIcon: Painter) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = brandIcon,
            contentDescription = "XOrA",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = "XOrA",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Welcome. Start with your local profile, then a few choices get your " +
                "library, display, and sound ready. You can change everything later in Setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private data class OnboardingAvatarPreset(val id: String, val color: Color)

private val OnboardingAvatarPresets = listOf(
    OnboardingAvatarPreset("preset_0", Color(0xFF6E7BFF)),
    OnboardingAvatarPreset("preset_1", Color(0xFF37D6A0)),
    OnboardingAvatarPreset("preset_2", Color(0xFFFFC24B)),
    OnboardingAvatarPreset("preset_3", Color(0xFFFF5C6C)),
    OnboardingAvatarPreset("preset_4", Color(0xFFA6AEFF)),
    OnboardingAvatarPreset("preset_5", Color(0xFF4ECDC4)),
)

@Composable
private fun ProfileStep(
    profile: LocalProfile,
    avatarPath: String?,
    onNameChange: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
) {
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    val nameRequester = remember { FocusRequester() }
    val photo = remember(avatarPath) {
        avatarPath?.let { BitmapFactory.decodeFile(it) }
    }
    val usingPhoto = profile.avatarSource == AvatarSource.Local && photo != null
    val preset = OnboardingAvatarPresets.firstOrNull { it.id == profile.avatarPresetId }
        ?: OnboardingAvatarPresets.first()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepTitle("Your profile")
        Text(
            text = "This name and picture show on the Home social card. You can change them later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(preset.color)
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null && usingPhoto) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        SettingsPadRow("profile_photo") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsPadTarget(
                    id = "profile_upload",
                    onActivate = onPickPhoto,
                ) {
                    Button(onClick = onPickPhoto) { Text("Upload photo") }
                }
                if (usingPhoto) {
                    SettingsPadTarget(
                        id = "profile_clear_photo",
                        onActivate = onClearPhoto,
                    ) {
                        OutlinedButton(onClick = onClearPhoto) { Text("Use colour") }
                    }
                }
            }
        }
        SettingsPadRow("profile_presets") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingAvatarPresets.forEach { swatch ->
                    SettingsPadTarget(
                        id = "profile_preset_${swatch.id}",
                        onActivate = { onSelectPreset(swatch.id) },
                        shape = CircleShape,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch.color)
                                .clickable { onSelectPreset(swatch.id) }
                                .then(
                                    if (!usingPhoto && swatch.id == profile.avatarPresetId) {
                                        Modifier.border(2.dp, Color.White, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
        SettingsPadTarget(
            id = "profile_name",
            onActivate = { nameRequester.requestFocus() },
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { next ->
                    name = next.take(24)
                    onNameChange(name)
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameRequester),
            )
        }
    }
}

@Composable
private fun DisplayModeStep(
    mode: DisplayMode,
    onSelect: (DisplayMode) -> Unit,
) {
    LaunchedEffect(Unit) { onSelect(DisplayMode.Single) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle("Display")
        Text(
            text = "The Home XMB is a single-screen menu. DS and 3DS games still use their " +
                "own dual-LCD layout from the emulator overlay.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = true,
                onClick = { onSelect(DisplayMode.Single) },
                label = { Text("Single screen") },
            )
        }
    }
}

@Composable
private fun LibraryStep(
    hasStorageAccess: Boolean,
    roots: List<com.arcadia.shell.model.LibraryRoot>,
    onGrantAccess: () -> Unit,
    onAddFolder: () -> Unit,
    onAddSaf: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle("Library folders")
        Text(
            text = "Point XOrA at the folder that holds your games. That folder can be named " +
                "anything — ROMs, My Games, Emulation. What matters is the console folders " +
                "inside it (PSP, PS2, PSP Games, PS2 ISOs) and the ROM files in those. " +
                "All-files access lets path-based emulators open those files directly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasStorageAccess) {
            Button(onClick = onGrantAccess) { Text("Grant all-files access") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAddFolder, enabled = hasStorageAccess) {
                Text("Add folder")
            }
            OutlinedButton(onClick = onAddSaf) {
                Text("Document picker")
            }
        }
        if (roots.isEmpty()) {
            Text(
                text = "No folders yet — you can skip and add them in Setup anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            roots.take(4).forEach { root ->
                Text(
                    text = "• ${root.label}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (roots.size > 4) {
                Text(
                    text = "+${roots.size - 4} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AndroidAppsStep(
    apps: List<com.arcadia.shell.launcher.InstalledApp>,
    selectedPackages: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle("Android apps")
        Text(
            text = "Pick which installed apps belong on the Android platform. " +
                "Skip includes every launchable app. Continue saves the ones you tick — " +
                "you can change this later in Setup → Storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AndroidAppPicker(
            apps = apps,
            selectedPackages = selectedPackages,
            query = query,
            onQueryChange = onQueryChange,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
            onClear = onClear,
        )
    }
}

@Composable
private fun EmulatorsStep(
    scanRunning: Boolean,
    scanCompleted: Boolean,
    scanError: String?,
    filesSeen: Int,
    hasFolders: Boolean,
    choices: List<PlatformPlayerChoice>,
    onEnsureScan: () -> Unit,
    onRetry: () -> Unit,
    onSelectPlayer: (String, String?) -> Unit,
) {
    LaunchedEffect(Unit) {
        onEnsureScan()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepTitle("Emulators")
        when {
            scanRunning || !scanCompleted -> {
                Text(
                    text = if (filesSeen > 0) {
                        "Scanning your library… $filesSeen files so far."
                    } else {
                        "Scanning your library for ROMs…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
            scanError != null -> {
                Text(
                    text = scanError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRetry) { Text("Try again") }
            }
            choices.isEmpty() -> {
                Text(
                    text = "XOrA didn't detect any ROMs. Put games inside console folders " +
                        "(PSP, PS2, PSP Games, PS2 ISOs — the parent can be named anything) " +
                        "and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hasFolders) {
                    Text(
                        text = "No library folders yet — go back and add one first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onRetry) { Text("Try again") }
            }
            else -> {
                Text(
                    text = "These systems have games. Pick the emulator XOrA should use for each.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choices.forEach { choice ->
                    OnboardingPlatformEmulatorCard(
                        choice = choice,
                        onSelect = { playerId ->
                            onSelectPlayer(choice.summary.platform.id, playerId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPlatformEmulatorCard(
    choice: PlatformPlayerChoice,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${choice.summary.platform.displayName} (${choice.summary.gameCount})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                choice.candidates.isEmpty() ->
                    "No emulator for this system is installed yet. XOrA will add one when you install it."
                choice.effectivePlayer == null -> "Nothing installed that can open these games."
                choice.isInstalled -> "Opens with ${choice.effectivePlayer.name}"
                else -> "${choice.effectivePlayer.name} is selected but not installed."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (choice.isInstalled || choice.effectivePlayer == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        choice.candidates.forEach { player ->
            FilterChip(
                selected = choice.selectedPlayerId == player.uniqueId,
                onClick = {
                    onSelect(
                        if (choice.selectedPlayerId == player.uniqueId) null else player.uniqueId,
                    )
                },
                label = { Text(text = player.name) },
            )
        }
    }
}

@Composable
private fun ScrapersStep(
    credentials: ScraperCredentials,
    open: OnboardingScraperService?,
    onOpen: (OnboardingScraperService) -> Unit,
    onClose: () -> Unit,
    onSteamGridDbKey: (String) -> Unit,
    onIgdb: (String, String) -> Unit,
    onScreenScraper: (String, String) -> Unit,
    onScreenScraperDev: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (open == null) {
            StepTitle("Artwork scrapers")
            Text(
                text = "Open a service to paste your API key or account. Covers and logos fill " +
                    "in after a library scan. Skip and add them later in Setup if you want.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScraperServiceButton(
                id = "scraper_open_sgdb",
                title = "SteamGridDB",
                subtitle = "Widescreen grids, heroes, and logos.",
                configured = credentials.hasSteamGridDb,
                onClick = { onOpen(OnboardingScraperService.SteamGridDb) },
            )
            ScraperServiceButton(
                id = "scraper_open_igdb",
                title = "IGDB",
                subtitle = "Twitch / IGDB client id and secret.",
                configured = credentials.hasIgdb,
                onClick = { onOpen(OnboardingScraperService.Igdb) },
            )
            ScraperServiceButton(
                id = "scraper_open_ss",
                title = "ScreenScraper",
                subtitle = "Hash matches for covers, videos, and manuals.",
                configured = credentials.hasScreenScraper,
                onClick = { onOpen(OnboardingScraperService.ScreenScraper) },
            )
            Text(
                text = "Music art (iTunes, Deezer) needs no key. You can change any of these " +
                    "later in Setup → Scrapers / Metadata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ScraperServiceSheet(
                service = open,
                credentials = credentials,
                onClose = onClose,
                onSteamGridDbKey = onSteamGridDbKey,
                onIgdb = onIgdb,
                onScreenScraper = onScreenScraper,
                onScreenScraperDev = onScreenScraperDev,
            )
        }
    }
}

@Composable
private fun ScraperServiceButton(
    id: String,
    title: String,
    subtitle: String,
    configured: Boolean,
    onClick: () -> Unit,
) {
    SettingsPadTarget(id = id, onActivate = onClick) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (configured) "Saved" else "Set up",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (configured) AccentInk else MutedInk,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun ScraperServiceSheet(
    service: OnboardingScraperService,
    credentials: ScraperCredentials,
    onClose: () -> Unit,
    onSteamGridDbKey: (String) -> Unit,
    onIgdb: (String, String) -> Unit,
    onScreenScraper: (String, String) -> Unit,
    onScreenScraperDev: (String, String) -> Unit,
) {
    var steamKey by remember(credentials.steamGridDbKey) {
        mutableStateOf(credentials.steamGridDbKey)
    }
    var igdbId by remember(credentials.igdbClientId) { mutableStateOf(credentials.igdbClientId) }
    var igdbSecret by remember(credentials.igdbClientSecret) {
        mutableStateOf(credentials.igdbClientSecret)
    }
    var ssUser by remember(credentials.screenScraperUser) {
        mutableStateOf(credentials.screenScraperUser)
    }
    var ssPass by remember(credentials.screenScraperPassword) {
        mutableStateOf(credentials.screenScraperPassword)
    }
    var ssDevId by remember(credentials.screenScraperDevId) {
        mutableStateOf(credentials.screenScraperDevId)
    }
    var ssDevPass by remember(credentials.screenScraperDevPassword) {
        mutableStateOf(credentials.screenScraperDevPassword)
    }

    val save: () -> Unit = {
        when (service) {
            OnboardingScraperService.SteamGridDb -> onSteamGridDbKey(steamKey)
            OnboardingScraperService.Igdb -> onIgdb(igdbId, igdbSecret)
            OnboardingScraperService.ScreenScraper -> {
                onScreenScraper(ssUser, ssPass)
                onScreenScraperDev(ssDevId, ssDevPass)
            }
        }
        onClose()
    }

    val title = when (service) {
        OnboardingScraperService.SteamGridDb -> "SteamGridDB"
        OnboardingScraperService.Igdb -> "IGDB"
        OnboardingScraperService.ScreenScraper -> "ScreenScraper"
    }
    val body = when (service) {
        OnboardingScraperService.SteamGridDb ->
            "Paste the Web API key from steamgriddb.com → Account. This is the art this " +
                "layout is built around."
        OnboardingScraperService.Igdb ->
            "Create a Twitch / IGDB application and paste the client id and secret."
        OnboardingScraperService.ScreenScraper ->
            "Account user and password, plus the developer id and password issued for your app. " +
                "Hash lookups need all four."
    }

    StepTitle(title)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (service) {
        OnboardingScraperService.SteamGridDb -> OnboardingSecretField(
            label = "SteamGridDB API key",
            value = steamKey,
            onCommit = { steamKey = it },
            live = true,
        )
        OnboardingScraperService.Igdb -> {
            OnboardingSecretField(
                label = "IGDB client id",
                value = igdbId,
                onCommit = { igdbId = it },
                live = true,
            )
            OnboardingSecretField(
                label = "IGDB client secret",
                value = igdbSecret,
                onCommit = { igdbSecret = it },
                live = true,
            )
        }
        OnboardingScraperService.ScreenScraper -> {
            OnboardingSecretField(
                label = "ScreenScraper user",
                value = ssUser,
                onCommit = { ssUser = it },
                live = true,
            )
            OnboardingSecretField(
                label = "ScreenScraper password",
                value = ssPass,
                onCommit = { ssPass = it },
                live = true,
            )
            OnboardingSecretField(
                label = "Developer id",
                value = ssDevId,
                onCommit = { ssDevId = it },
                live = true,
            )
            OnboardingSecretField(
                label = "Developer password",
                value = ssDevPass,
                onCommit = { ssDevPass = it },
                live = true,
            )
        }
    }
    SettingsPadRow("scraper_sheet_actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsPadTarget(id = "scraper_sheet_back", onActivate = onClose) {
                OutlinedButton(onClick = onClose) { Text("Back") }
            }
            SettingsPadTarget(id = "scraper_sheet_save", onActivate = save) {
                Button(onClick = save) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SocialStep(
    steam: SteamWebApiCredentials,
    discordPresence: DiscordPresenceUiState,
    notificationListenerEnabled: Boolean,
    onSignInSteam: () -> Unit,
    onSteamApiKey: (String) -> Unit,
    onLinkDiscord: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepTitle("Social")
        Text(
            text = "Sign in with Steam for your SteamID64, paste a Web API key once, and link " +
                "Discord for Rich Presence. Notification access lets XOrA mirror chat previews.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = onSignInSteam) {
            Text(
                text = if (steam.steamId64.isNotBlank()) {
                    "Re-link Steam (ID ${steam.steamId64})"
                } else {
                    "Sign in with Steam"
                },
            )
        }
        if (steam.steamId64.isNotBlank()) {
            Text(
                text = "Steam ID linked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OnboardingSecretField(
            label = "Steam Web API key",
            value = steam.apiKey,
            onCommit = onSteamApiKey,
        )

        val discordLabel = when {
            discordPresence.connecting -> "Connecting Discord…"
            discordPresence.capability == DiscordPresenceCapability.Connected ->
                "Discord linked"
            discordPresence.capability == DiscordPresenceCapability.NeedsDiscordApp ->
                "Install Discord"
            discordPresence.capability == DiscordPresenceCapability.Failed ->
                "Retry Discord link"
            discordPresence.capability == DiscordPresenceCapability.SdkMissing ->
                "Discord SDK missing"
            discordPresence.capability == DiscordPresenceCapability.NotConfigured ->
                "Link Discord"
            else -> "Link Discord"
        }
        val canLinkDiscord = discordPresence.capability == DiscordPresenceCapability.NeedsAccountLink ||
            discordPresence.capability == DiscordPresenceCapability.NeedsDiscordApp ||
            discordPresence.capability == DiscordPresenceCapability.Failed ||
            discordPresence.capability == DiscordPresenceCapability.Connected ||
            (discordPresence.capability == DiscordPresenceCapability.NotConfigured &&
                discordPresence.applicationId.isNotBlank())

        OutlinedButton(
            onClick = onLinkDiscord,
            enabled = canLinkDiscord && !discordPresence.connecting &&
                discordPresence.capability != DiscordPresenceCapability.SdkMissing,
        ) {
            Text(discordLabel)
        }
        if (discordPresence.capability == DiscordPresenceCapability.Connected) {
            Text(
                text = "Discord account linked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (discordPresence.capability == DiscordPresenceCapability.SdkMissing) {
            Text(
                text = "Discord Social SDK is not in this build — link later from Social when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!notificationListenerEnabled) {
            OutlinedButton(onClick = onOpenNotificationAccess) {
                Text("Open notification access")
            }
        } else {
            Text(
                text = "Notification access is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RetroAchievementsStep(
    configured: RetroAchievementsCredentials,
    isBusy: Boolean,
    error: String?,
    pendingWebApiUsername: String?,
    onPasswordSignIn: (username: String, password: String) -> Unit,
    onApiKeySignIn: (username: String, apiKey: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepTitle("Sign in to RetroAchievements")
        Text(
            text = "Sign in with your username and password. If RA asks for a Web API key, " +
                "paste it once from your control panel (Keys).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (configured.isConfigured && pendingWebApiUsername.isNullOrBlank()) {
            Text(
                text = "Signed in as ${configured.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            RetroAchievementsSignInFields(
                configured = configured,
                isBusy = isBusy,
                error = error,
                pendingWebApiUsername = pendingWebApiUsername,
                onPasswordSignIn = onPasswordSignIn,
                onApiKeySignIn = onApiKeySignIn,
            )
        }
    }
}

@Composable
private fun OnboardingSecretField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    live: Boolean = false,
) {
    var draft by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = if (live) value else draft,
        onValueChange = { next ->
            if (live) onCommit(next) else draft = next
        },
        label = { Text(text = label) },
        singleLine = true,
        visualTransformation = if ((if (live) value else draft).isBlank()) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (!live && !focus.isFocused && draft != value) onCommit(draft)
            },
    )
}

@Composable
private fun AudioStep(
    bgmVolume: Float,
    uiSfxVolume: Float,
    onBgmChange: (Float) -> Unit,
    onSfxChange: (Float) -> Unit,
) {
    var draftBgm by remember(bgmVolume) { mutableFloatStateOf(bgmVolume) }
    var draftSfx by remember(uiSfxVolume) { mutableFloatStateOf(uiSfxVolume) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle("Audio")
        Text(
            text = "Soundtrack and UI click volumes. Themes on Home can also swap wallpaper " +
                "and custom BGM later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Background music: ${(draftBgm * 100f).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = draftBgm,
            onValueChange = { draftBgm = it },
            onValueChangeFinished = { onBgmChange(draftBgm) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "UI sounds: ${(draftSfx * 100f).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = draftSfx,
            onValueChange = { draftSfx = it },
            onValueChangeFinished = { onSfxChange(draftSfx) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepTitle("You're set")
        Text(
            text = "Head to Home to browse games, pin shortcuts, and open Setup anytime from " +
                "the hub. Welcome to XOrA.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    XoraTitleText(text = text, fontSize = 26.sp, maxLines = 2)
}

@Composable
private fun OnboardingActions(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val optional = isOptional(state.step)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            enabled = state.canGoBack,
        ) {
            Text("Back")
        }
        Spacer(modifier = Modifier.weight(1f))
        if (optional) {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
        Button(
            onClick = onNext,
            enabled = state.canAdvance,
        ) {
            Text(
                when {
                    state.isLast -> "Finish"
                    optional -> "Continue"
                    else -> "Next"
                },
            )
        }
    }
}

@Composable
private fun OnboardingHints(
    state: OnboardingUiState,
    scraperSheet: OnboardingScraperService? = null,
) {
    val optional = isOptional(state.step)
    val hints = buildList {
        if (state.step == OnboardingStep.Profile) {
            add("A" to "Use photo / colour / name")
            add("U/D/L/R" to "Move")
            add("RB" to "Next")
            if (state.canGoBack) add("B / LB" to "Back")
        } else if (state.step == OnboardingStep.Scrapers) {
            add("A" to if (scraperSheet != null) "Save / use field" else "Open service")
            add("U/D" to "Move")
            add("RB" to "Continue")
            add("B / LB" to if (scraperSheet != null) "Close" else "Back")
            add("Y" to "Skip")
        } else {
            add("A / → / RB" to if (state.isLast) "Finish" else if (optional) "Continue" else "Next")
            if (state.canGoBack) add("B / ← / LB" to "Back")
            if (optional) add("Y" to "Skip")
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (button, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = button,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
