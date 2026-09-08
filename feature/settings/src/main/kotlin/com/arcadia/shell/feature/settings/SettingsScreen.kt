package com.arcadia.shell.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcadia.shell.datastore.AndroidAppInclusionMode
import com.arcadia.shell.datastore.ThemeMode
import com.arcadia.shell.datastore.VisualPerformanceMode
import com.arcadia.shell.datastore.visualPerformanceModeLabel
import com.arcadia.shell.datastore.visualPerformanceModeSubtitle
import com.arcadia.shell.launcher.selectedAndroidPackages
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.GameIconIdleMedia
import com.arcadia.shell.datastore.TrailerSourcePreference
import com.arcadia.shell.display.OverlayPermission
import com.arcadia.shell.launcher.discord.DiscordPresenceCapability
import com.arcadia.shell.designsystem.readDeviceVisualBudget
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LiquidGlassSurface
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.designsystem.R as DsR
import com.arcadia.shell.input.NavAction
import kotlinx.coroutines.flow.Flow
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import kotlin.math.roundToInt

/**
 * @param systemSection extra settings supplied by the hosting app. The home-screen role is
 *   controlled through the app's own manifest alias, which this module cannot reach.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onGoToOnboarding: () -> Unit = {},
    systemSection: @Composable () -> Unit = {},
    /** Host art — the same settings hero used on the companion display. */
    backdrop: @Composable BoxScope.() -> Unit = {},
    padActions: Flow<NavAction>? = null,
    onPadCapture: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFolderPicker by remember { mutableStateOf(false) }
    var showMusicFolderPicker by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(SetupSection.Display) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val padRegistry = remember { SettingsPadRegistry() }
    var pad by remember {
        mutableStateOf(
            SettingsPadNavState(
                sectionIndex = 0,
                zone = SettingsPadZone.Controls,
                rowIndex = 0,
                colIndex = 0,
            ),
        )
    }
    val onBackNow = rememberUpdatedState(onBack)
    val padNow = rememberUpdatedState(pad)
    val sectionNow = rememberUpdatedState(section)

    LaunchedEffect(section) {
        scrollState.scrollTo(0)
        pad = SettingsPadNavState(
            sectionIndex = SetupSection.entries.indexOf(section).coerceAtLeast(0),
            zone = SettingsPadZone.Controls,
            rowIndex = 0,
            colIndex = 0,
        )
    }

    DisposableEffect(onPadCapture) {
        onPadCapture(true)
        onDispose { onPadCapture(false) }
    }
    LaunchedEffect(padActions, padRegistry, density) {
        val flow = padActions ?: return@LaunchedEffect
        val sections = SetupSection.entries
        val scrollStep = with(density) { 96.dp.toPx() }
        flow.collect { action ->
            val layout = padRegistry.layout()
            val current = settingsPadCoerce(padNow.value, layout)
            if (action == NavAction.Cancel || action == NavAction.Menu) {
                onBackNow.value()
                return@collect
            }
            val focusId = settingsPadFocusId(current, layout)
            val binding = focusId?.let(padRegistry::binding)
            if (action == NavAction.Confirm) {
                when (current.zone) {
                    SettingsPadZone.Done -> onBackNow.value()
                    SettingsPadZone.Tabs -> {
                        pad = settingsPadAfterAction(
                            current,
                            NavAction.Confirm,
                            sections.size,
                            layout,
                        )
                    }
                    SettingsPadZone.Controls -> binding?.activate?.invoke()
                }
                return@collect
            }
            val delta = when (action) {
                NavAction.Left -> -1
                NavAction.Right -> 1
                else -> null
            }
            if (delta != null && current.zone == SettingsPadZone.Controls) {
                val adjust = binding?.adjust?.invoke()
                if (adjust != null) {
                    adjust(delta)
                    return@collect
                }
            }
            val next = settingsPadAfterAction(
                current,
                action,
                sections.size,
                layout,
            )
            if (settingsPadShouldScrollPage(current, next, action)) {
                val pageDelta = if (action == NavAction.Down) scrollStep else -scrollStep
                scrollState.animateScrollBy(pageDelta)
                return@collect
            }
            pad = next
            val nextSection = sections[next.sectionIndex]
            if (nextSection != sectionNow.value) {
                section = nextSection
            }
        }
    }

    BackHandler(onBack = onBack)

    val safPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(viewModel::addSafRoot)
        }
    }

    // Permission changes happen in system settings, so state has to be re-read on return.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterTween = arcadiaTween<Float>(ArcadiaMotion.Slow)

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

    if (showMusicFolderPicker) {
        FolderPickerDialog(
            volumes = state.suggestedVolumes,
            listDirectories = viewModel::listDirectories,
            onDismiss = { showMusicFolderPicker = false },
            onPick = { path ->
                viewModel.setMusicLibraryPath(path)
                showMusicFolderPicker = false
            },
        )
    }

    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(enterTween) + slideInVertically(
            animationSpec = arcadiaTween(ArcadiaMotion.Slow),
            initialOffsetY = { it / 24 },
        ),
        exit = fadeOut(enterTween),
        modifier = modifier.fillMaxSize(),
    ) {
    ArcadiaTheme(darkTheme = true) {
    val setupScheme = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        onBackground = Color.White,
    )
    MaterialTheme(colorScheme = setupScheme) {
    val padLayout = padRegistry.layout()
    val coercedPad = settingsPadCoerce(pad, padLayout)
    val padFocusId = settingsPadFocusId(coercedPad, padLayout)
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        LocalSettingsPadFocusId provides padFocusId,
        LocalSettingsPadRegistry provides padRegistry,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        backdrop()
        // Opaque plate so leftover Home chrome cannot show through Setup.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141418)),
        )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        run {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(DsR.drawable.xmb_figma_settings),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Column {
                            Text(
                                text = "Setup",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = XoraFonts.Title,
                                    letterSpacing = XoraFonts.TitleLetterSpacing,
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = section.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = XoraFonts.Secondary,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                    SettingsPadTarget(
                        id = SettingsPadIds.Done,
                        onActivate = onBack,
                        listed = false,
                    ) {
                        TextButton(onClick = onBack) {
                            Text(
                                text = "Done",
                                fontFamily = XoraFonts.XmbLabel,
                                color = Color.White,
                            )
                        }
                    }
                }
                SettingsPadTarget(
                    id = SettingsPadIds.Tabs,
                    onActivate = {
                        pad = coercedPad.copy(
                            zone = SettingsPadZone.Controls,
                            rowIndex = 0,
                            colIndex = 0,
                        )
                    },
                    listed = false,
                    showFocusBorder = false,
                ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(SetupSection.entries, key = { it.name }) { entry ->
                        SetupSectionTab(
                            section = entry,
                            selected = entry == section,
                            focused = padFocusId == SettingsPadIds.Tabs && entry == section,
                            onClick = { section = entry },
                        )
                    }
                }
                }
            }
        }

        if (state.xoraDownloadRunning || state.message != null || state.xoraDownloadError != null) {
            run {
                val downloadError = state.xoraDownloadError
                val bannerMessage = state.message
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = ArcadiaGlass.PanelShape,
                            tone = GlassTone.Surface,
                            intensity = GlassIntensity.Subtle,
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        state.xoraDownloadRunning -> {
                            Text(
                                text = state.xoraDownloadMessage ?: "Downloading XOrA cores…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        downloadError != null -> {
                            Text(
                                text = downloadError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        bannerMessage != null -> {
                            Text(
                                text = bannerMessage,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (section == SetupSection.Display) {
        // 1. Appearance — theme + how trailers are presented
        run {
            SettingsCard(
                title = "Appearance",
                iconRes = DsR.drawable.xmb_figma_device,
                modifier = Modifier,
            ) {
                SettingsFieldLabel("Theme")
                SettingsPadRow("theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        PadChip(
                            id = "theme_${mode.name}",
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = when (mode) {
                                ThemeMode.System -> "System"
                                ThemeMode.Light -> "Light"
                                ThemeMode.Dark -> "Dark"
                            },
                        )
                    }
                }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Performance")
                val deviceBudget = remember(context) { readDeviceVisualBudget(context) }
                Text(
                    text = "Default is Auto: this phone's RAM and memory class pick Performance " +
                        "or Quality. Performance uses a static wallpaper and skips glass blur, " +
                        "looping video, and idle trailers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadRow("perf") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VisualPerformanceMode.entries.forEach { mode ->
                            PadChip(
                                id = "perf_${mode.name}",
                                selected = state.settings.visualPerformanceMode == mode,
                                onClick = { viewModel.setVisualPerformanceMode(mode) },
                                label = visualPerformanceModeLabel(mode),
                            )
                        }
                    }
                }
                Text(
                    text = visualPerformanceModeSubtitle(
                        mode = state.settings.visualPerformanceMode,
                        deviceSuggestsLite = deviceBudget.suggestsLiteVisuals,
                        deviceRamLabel = deviceBudget.usableRamLabel,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Trailer display")
                Text(
                    text = "How idle trailers fill the game selector when they play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadRow("trailer_display") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PadChip(
                            id = "trailer_in_icon",
                            selected = state.settings.trailerDisplayMode == TrailerDisplayMode.InIcon,
                            onClick = { viewModel.setTrailerDisplayMode(TrailerDisplayMode.InIcon) },
                            enabled = state.settings.trailerEnabled,
                            label = "Game icon",
                        )
                        PadChip(
                            id = "trailer_full_bg",
                            selected = state.settings.trailerDisplayMode ==
                                TrailerDisplayMode.FullBackground,
                            onClick = {
                                viewModel.setTrailerDisplayMode(TrailerDisplayMode.FullBackground)
                            },
                            enabled = state.settings.trailerEnabled,
                            label = "Full background",
                        )
                        PadChip(
                            id = "trailer_pip",
                            selected = state.settings.trailerDisplayMode == TrailerDisplayMode.CornerPip,
                            onClick = { viewModel.setTrailerDisplayMode(TrailerDisplayMode.CornerPip) },
                            enabled = state.settings.trailerEnabled,
                            label = "Corner PIP",
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Game Icon idle")
                Text(
                    text = "What fills the focused Game Icon. Screenshots play your stills " +
                        "and GIFs (add them in the ROM editor), fading every few seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadRow("idle_media") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PadChip(
                            id = "idle_trailer",
                            selected = state.settings.gameIconIdleMedia == GameIconIdleMedia.Trailer,
                            onClick = { viewModel.setGameIconIdleMedia(GameIconIdleMedia.Trailer) },
                            label = "Trailers",
                        )
                        PadChip(
                            id = "idle_screenshot",
                            selected = state.settings.gameIconIdleMedia == GameIconIdleMedia.Screenshot,
                            onClick = { viewModel.setGameIconIdleMedia(GameIconIdleMedia.Screenshot) },
                            label = "Screenshots",
                        )
                    }
                }
            }
        }

        // 2. Library / Layout — display mode, feed grid + second screen
        run {
            SettingsCard(
                title = "Library / Layout",
                iconRes = DsR.drawable.xmb_figma_folder,
                modifier = Modifier,
            ) {
                SettingsFieldLabel("Library columns: ${state.settings.gridColumns}")
                Text(
                    text = "Columns for the RSS feed grid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadRow("grid_cols") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4, 5, 6).forEach { columns ->
                            PadChip(
                                id = "grid_cols_$columns",
                                selected = state.settings.gridColumns == columns,
                                onClick = { viewModel.setGridColumns(columns) },
                                label = columns.toString(),
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = "Show hidden games", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Hidden titles stay in your library. Turn this on to list them " +
                                "again and unhide from ROM options.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PadSwitch(
                        id = "show_hidden_games",
                        checked = state.settings.showHiddenGames,
                        onCheckedChange = viewModel::setShowHiddenGames,
                    )
                }
            }
        }

        }

        if (section == SetupSection.Audio) {
        // 3. Audio — BGM + UI SFX
        run {
            SettingsCard(
                title = "Audio",
                iconRes = DsR.drawable.xmb_figma_music,
                modifier = Modifier,
            ) {
                SettingsFieldLabel("Background music")
                Text(
                    text = "Looping soundtrack while XOrA is open. Muted at 0%. Pauses when the " +
                        "app backgrounds or an emulator takes over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var draftVolume by remember(state.settings.bgmVolume) {
                    mutableFloatStateOf(state.settings.bgmVolume)
                }
                val percent = (draftVolume * 100f).roundToInt()

                Text(
                    text = "Volume: $percent%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsPadTarget(
                    id = "audio_bgm",
                    onActivate = { },
                    onAdjust = { delta ->
                        val next = (draftVolume + delta * 0.05f).coerceIn(0f, 1f)
                        draftVolume = next
                        viewModel.setBgmVolume(next)
                    },
                ) {
                    Slider(
                        value = draftVolume,
                        onValueChange = { draftVolume = it },
                        onValueChangeFinished = { viewModel.setBgmVolume(draftVolume) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("UI sounds")
                Text(
                    text = "Cursor, confirm, and cancel clicks. Independent of soundtrack volume. " +
                        "Muted at 0%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var draftSfx by remember(state.settings.uiSfxVolume) {
                    mutableFloatStateOf(state.settings.uiSfxVolume)
                }
                val sfxPercent = (draftSfx * 100f).roundToInt()

                Text(
                    text = "Volume: $sfxPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsPadTarget(
                    id = "audio_sfx",
                    onActivate = { },
                    onAdjust = { delta ->
                        val next = (draftSfx + delta * 0.05f).coerceIn(0f, 1f)
                        draftSfx = next
                        viewModel.setUiSfxVolume(next)
                    },
                ) {
                    Slider(
                        value = draftSfx,
                        onValueChange = { draftSfx = it },
                        onValueChangeFinished = { viewModel.setUiSfxVolume(draftSfx) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Music library folder")
                Text(
                    text = "Where Music → Playlist / All music look for on-device songs " +
                        "(mp3, wav, flac, and similar). Leave empty to use all music indexed " +
                        "on this device. Album artwork is filled automatically from embedded " +
                        "tags, a cover.jpg in the folder, or an online lookup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.settings.musicLibraryPath?.takeIf { it.isNotBlank() }
                        ?: "All device music",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsPadRow("audio_folder") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsPadTarget(
                        id = "audio_choose_folder",
                        onActivate = {
                            if (state.hasStorageAccess) showMusicFolderPicker = true
                        },
                    ) {
                        Button(
                            onClick = { showMusicFolderPicker = true },
                            enabled = state.hasStorageAccess,
                        ) {
                            Text(text = "Choose folder")
                        }
                    }
                    if (!state.settings.musicLibraryPath.isNullOrBlank()) {
                        SettingsPadTarget(
                            id = "audio_clear_folder",
                            onActivate = { viewModel.setMusicLibraryPath(null) },
                        ) {
                            OutlinedButton(onClick = { viewModel.setMusicLibraryPath(null) }) {
                                Text(text = "Use all device music")
                            }
                        }
                    }
                }
                }
                if (!state.hasStorageAccess) {
                    Text(
                        text = "Grant all-files access under Storage / Library to pick a folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        }

        if (section == SetupSection.Media) {
        // 4. Trailers — scrape / source / idle (display mode lives under Appearance)
        run {
            SettingsCard(
                title = "Trailers",
                iconRes = DsR.drawable.xmb_figma_video,
                modifier = Modifier,
            ) {
                Text(
                    text = "Playnite-style trailers: resolve a YouTube or Steam URL for the " +
                        "selected game, store it, then play muted after " +
                        "${state.settings.trailerIdleSeconds}s idle on the game selector. " +
                        "Shell music keeps playing at full volume while a trailer is muted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Scrape trailers", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "trailer_scrape",
                        checked = state.settings.trailerScrapeEnabled,
                        onCheckedChange = viewModel::setTrailerScrapeEnabled,
                    )
                }
                Text(
                    text = "When on, trailers are looked up during metadata scrape and lazily " +
                        "on idle. Turn off to stop all network trailer lookups; already-saved " +
                        "URLs still play if idle trailers are enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsFieldLabel("Trailer source")
                SettingsPadRow("trailer_src") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrailerSourcePreference.entries.forEach { preference ->
                        PadChip(
                            id = "trailer_src_${preference.name}",
                            selected = state.settings.trailerSourcePreference == preference,
                            onClick = { viewModel.setTrailerSourcePreference(preference) },
                            enabled = state.settings.trailerScrapeEnabled,
                            label = when (preference) {
                                TrailerSourcePreference.Auto -> "Auto"
                                TrailerSourcePreference.YouTube -> "YouTube"
                                TrailerSourcePreference.Steam -> "Steam"
                                TrailerSourcePreference.ScreenScraper -> "ScreenScraper"
                                TrailerSourcePreference.Igdb -> "IGDB"
                            },
                        )
                    }
                }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Idle trailers", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "idle_trailers",
                        checked = state.settings.trailerEnabled,
                        onCheckedChange = viewModel::setTrailerEnabled,
                    )
                }
            }
        }

        // 5. Scrapers / Metadata
        run {
            SettingsCard(
                title = "Scrapers / Metadata",
                iconRes = DsR.drawable.xmb_figma_photo,
                modifier = Modifier,
            ) {
                Text(
                    text = "XOrA looks up artwork from whichever sources you configure. " +
                        "ScreenScraper matches by file hash and is the most accurate, but it also " +
                        "needs developer credentials. SteamGridDB matches by title and provides " +
                        "the widescreen art and logos this layout is built around.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SecretField(
                    label = "SteamGridDB API key",
                    value = state.credentials.steamGridDbKey,
                    onCommit = viewModel::setSteamGridDbKey,
                    padId = "scraper_sgdb",
                )

                PairedSecretFields(
                    firstLabel = "ScreenScraper user",
                    secondLabel = "ScreenScraper password",
                    firstValue = state.credentials.screenScraperUser,
                    secondValue = state.credentials.screenScraperPassword,
                    onCommit = viewModel::setScreenScraperCredentials,
                    firstPadId = "scraper_ss_user",
                    secondPadId = "scraper_ss_pass",
                )

                PairedSecretFields(
                    firstLabel = "ScreenScraper dev id",
                    secondLabel = "ScreenScraper dev password",
                    firstValue = state.credentials.screenScraperDevId,
                    secondValue = state.credentials.screenScraperDevPassword,
                    onCommit = viewModel::setScreenScraperDevCredentials,
                    firstPadId = "scraper_ss_devid",
                    secondPadId = "scraper_ss_devpass",
                )

                PairedSecretFields(
                    firstLabel = "IGDB client id",
                    secondLabel = "IGDB client secret",
                    firstValue = state.credentials.igdbClientId,
                    secondValue = state.credentials.igdbClientSecret,
                    onCommit = viewModel::setIgdbCredentials,
                    firstPadId = "scraper_igdb_id",
                    secondPadId = "scraper_igdb_secret",
                )

                PadChip(
                    id = "scrape_after_scan",
                    selected = state.settings.scrapeAfterScan,
                    onClick = { viewModel.setScrapeAfterScan(!state.settings.scrapeAfterScan) },
                    label = "Fetch artwork automatically after a scan",
                )

                PadChip(
                    id = "manual_scrape",
                    selected = state.settings.manualScrapeEnabled,
                    onClick = { viewModel.setManualScrapeEnabled(!state.settings.manualScrapeEnabled) },
                    label = "Download game manuals",
                )
                Text(
                    text = "Manuals come from ScreenScraper and are the largest media it serves, " +
                        "so a big library can pull several gigabytes. Read them from the companion " +
                        "screen while a game runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingsPadRow("scrape_actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsPadTarget(
                        id = "scrape_now",
                        onActivate = {
                            if (!state.isScraping && state.gameCount > 0) viewModel.scrapeNow()
                        },
                    ) {
                        Button(
                            onClick = viewModel::scrapeNow,
                            enabled = !state.isScraping && state.gameCount > 0,
                        ) {
                            Text(text = if (state.isScraping) "Fetching…" else "Fetch artwork now")
                        }
                    }
                    if (state.isScraping) {
                        SettingsPadTarget(
                            id = "scrape_stop",
                            onActivate = viewModel::cancelScrape,
                        ) {
                            OutlinedButton(onClick = viewModel::cancelScrape) {
                                Text(text = "Stop")
                            }
                        }
                    }
                }
                }
            }
        }

        }

        if (section == SetupSection.Accounts) {
        // 6. RetroAchievements
        run {
            SettingsCard(
                title = "RetroAchievements",
                iconRes = DsR.drawable.xmb_figma_trophy,
                modifier = Modifier,
            ) {
                Text(
                    text = "Shared by the XOrA launcher (XMB · press X) and XOrA Emulator. " +
                        "Sign in with username/password (required for the emulator). " +
                        "Paste a Web API key if RA asks — that unlocks launcher library features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Enable RetroAchievements", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "ra_enable",
                        checked = state.raSettings.enabled,
                        onCheckedChange = viewModel::setRaEnabled,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Hardcore mode", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Disables save-state loads in XOrA Emulator. Server-side " +
                                "hardcore unlocks need RetroAchievements to approve the XOrA " +
                                "client — until then they demote to softcore (“Unknown Emulator”).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PadSwitch(
                        id = "ra_hardcore",
                        checked = state.raSettings.hardcore,
                        onCheckedChange = viewModel::setRaHardcore,
                        enabled = state.raSettings.enabled,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Unlock notifications", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "ra_unlock_notifs",
                        checked = state.raSettings.unlockNotifications,
                        onCheckedChange = viewModel::setRaUnlockNotifications,
                        enabled = state.raSettings.enabled,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Show in launcher", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "XMB RetroAchievements shard and Start menu shortcuts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PadSwitch(
                        id = "ra_show_launcher",
                        checked = state.raSettings.showInLauncher,
                        onCheckedChange = viewModel::setRaShowInLauncher,
                        enabled = state.raSettings.enabled,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Rich presence text", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "ra_rich_presence",
                        checked = state.raSettings.richPresence,
                        onCheckedChange = viewModel::setRaRichPresence,
                        enabled = state.raSettings.enabled,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                RetroAchievementsSignInFields(
                    configured = state.retroAchievements,
                    isBusy = state.raAuthBusy,
                    error = state.raAuthError,
                    pendingWebApiUsername = state.raPendingWebApiUsername,
                    onPasswordSignIn = viewModel::loginRetroAchievements,
                    onApiKeySignIn = viewModel::setRetroAchievementsCredentials,
                )

                if (state.retroAchievements.isConfigured) {
                    Text(
                        text = "Signed in as ${state.retroAchievements.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    SettingsPadTarget(
                        id = "ra_sign_out",
                        onActivate = viewModel::clearRetroAchievementsCredentials,
                    ) {
                        OutlinedButton(onClick = viewModel::clearRetroAchievementsCredentials) {
                            Text(text = "Sign out")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsFieldLabel("ROM hashes")
                Text(
                    text = if (state.isHashingRoms) {
                        "Hashing ROMs in the background…"
                    } else if (state.missingRomHashes == 0) {
                        "All library ROMs have RetroAchievements hashes."
                    } else {
                        "${state.missingRomHashes} ROMs still need a hash. " +
                            "XOrA Emulator hashes on launch; the launcher needs this pass."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadTarget(
                    id = "ra_hash_roms",
                    onActivate = { if (!state.isHashingRoms) viewModel.hashAllRoms() },
                ) {
                    Button(
                        onClick = viewModel::hashAllRoms,
                        enabled = !state.isHashingRoms,
                    ) {
                        Text(
                            text = if (state.isHashingRoms) {
                                "Hashing…"
                            } else {
                                "Hash all ROMs"
                            },
                        )
                    }
                }
            }
        }

        // 7. Social
        run {
            SettingsCard(
                title = "Social",
                iconRes = DsR.drawable.xmb_figma_network,
                modifier = Modifier,
            ) {
                Text(
                    text = "LT opens the social menu. Sign in with Steam for SteamID64; a Steam " +
                        "Web API key is still required once (Steam has no password→API key for " +
                        "third parties). Conversations show message previews from Steam, Discord, " +
                        "and other messaging apps when Notification Access is on — reply works when " +
                        "the notification exposes RemoteInput. In-launcher Discord friend chat uses " +
                        "the Social SDK communication scopes (Link Discord in Social; re-link if you " +
                        "connected before messaging was enabled). Live Rich Presence on Android also " +
                        "needs that account link — unauthenticated RPC is desktop-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingsFieldLabel("Conversations")
                Text(
                    text = if (state.notificationListenerEnabled) {
                        "Notification access is on. Social → Steam / Discord shows recent message notifications."
                    } else {
                        "Shows message previews from apps on this device when notification access is on."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadTarget(
                    id = "social_notif_access",
                    onActivate = {
                        permissionLauncher.launch(viewModel.notificationListenerSettingsIntent())
                    },
                ) {
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(viewModel.notificationListenerSettingsIntent())
                        },
                    ) {
                        Text(
                            text = if (state.notificationListenerEnabled) {
                                "Notification access settings"
                            } else {
                                "Notification access for conversations"
                            },
                        )
                    }
                }

                SettingsFieldLabel("Sign in with Steam")
                Text(
                    text = "Sign in for Steam ID; API key still required once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadTarget(
                    id = "social_steam_signin",
                    onActivate = {
                        val customTabs = androidx.browser.customtabs.CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                        runCatching {
                            customTabs.launchUrl(
                                context,
                                android.net.Uri.parse(viewModel.steamOpenIdAuthorizationUrl()),
                            )
                        }
                    },
                ) {
                    Button(
                        onClick = {
                            val customTabs = androidx.browser.customtabs.CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .build()
                            runCatching {
                                customTabs.launchUrl(
                                    context,
                                    android.net.Uri.parse(viewModel.steamOpenIdAuthorizationUrl()),
                                )
                            }
                        },
                    ) {
                        Text(
                            text = if (state.steamWebApi.steamId64.isNotBlank()) {
                                "Re-link Steam (ID ${state.steamWebApi.steamId64})"
                            } else {
                                "Sign in with Steam"
                            },
                        )
                    }
                }

                SecretField(
                    label = "Steam Web API key",
                    value = state.steamWebApi.apiKey,
                    onCommit = viewModel::setSteamWebApiKey,
                    padId = "steam_api_key",
                )
                var steamIdDraft by remember(state.steamWebApi.steamId64) {
                    mutableStateOf(state.steamWebApi.steamId64)
                }
                val steamIdRequester = remember { FocusRequester() }
                SettingsPadTarget(
                    id = "social_steam_id",
                    onActivate = { steamIdRequester.requestFocus() },
                ) {
                    OutlinedTextField(
                        value = steamIdDraft,
                        onValueChange = { steamIdDraft = it },
                        label = { Text(text = "SteamID64 (from Sign in with Steam)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(steamIdRequester)
                            .onFocusChanged { focus ->
                                if (!focus.isFocused && steamIdDraft != state.steamWebApi.steamId64) {
                                    viewModel.setSteamId64(steamIdDraft)
                                }
                            },
                    )
                }

                if (state.steamWebApi.apiKey.isNotBlank() || state.steamWebApi.steamId64.isNotBlank()) {
                    SettingsPadTarget(
                        id = "social_steam_clear",
                        onActivate = viewModel::clearSteamWebApiCredentials,
                    ) {
                        OutlinedButton(onClick = viewModel::clearSteamWebApiCredentials) {
                            Text(text = "Clear Steam credentials")
                        }
                    }
                }

                SettingsFieldLabel("Discord")
                var discordDraft by remember(state.discordSocial.openUrl) {
                    mutableStateOf(state.discordSocial.openUrl)
                }
                val discordInviteRequester = remember { FocusRequester() }
                SettingsPadTarget(
                    id = "social_discord_invite",
                    onActivate = { discordInviteRequester.requestFocus() },
                ) {
                    OutlinedTextField(
                        value = discordDraft,
                        onValueChange = { discordDraft = it },
                        label = { Text(text = "Discord invite / profile URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(discordInviteRequester)
                            .onFocusChanged { focus ->
                                if (!focus.isFocused && discordDraft != state.discordSocial.openUrl) {
                                    viewModel.setDiscordOpenUrl(discordDraft)
                                }
                            },
                    )
                }

                if (state.discordSocial.hasLink) {
                    SettingsPadTarget(
                        id = "social_discord_clear",
                        onActivate = viewModel::clearDiscordOpenUrl,
                    ) {
                        OutlinedButton(onClick = viewModel::clearDiscordOpenUrl) {
                            Text(text = "Clear Discord link")
                        }
                    }
                }

                var discordAppIdDraft by remember(state.discordSocial.applicationId) {
                    mutableStateOf(state.discordSocial.applicationId)
                }
                val discordAppIdRequester = remember { FocusRequester() }
                SettingsPadTarget(
                    id = "social_discord_app_id",
                    onActivate = { discordAppIdRequester.requestFocus() },
                ) {
                    OutlinedTextField(
                        value = discordAppIdDraft,
                        onValueChange = { discordAppIdDraft = it },
                        label = { Text(text = "Discord Application ID (Rich Presence)") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = buildString {
                                    append("Status: ${state.discordPresence.connectionLabel}")
                                    append(" · ")
                                    append(state.discordPresence.statusLine)
                                    append(" · ")
                                    append(
                                        "Default is XOrA's Application ID; override or Clear to disable. " +
                                            "Public Application ID only — never put a client secret here.",
                                    )
                                },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(discordAppIdRequester)
                            .onFocusChanged { focus ->
                                if (!focus.isFocused &&
                                    discordAppIdDraft != state.discordSocial.applicationId
                                ) {
                                    viewModel.setDiscordApplicationId(discordAppIdDraft)
                                }
                            },
                    )
                }

                Text(
                    text = state.discordPresence.detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (state.discordPresence.capability) {
                    DiscordPresenceCapability.SdkMissing -> {
                        SettingsFieldLabel("Enable live Discord SDK")
                        val context = LocalContext.current
                        state.discordPresence.setupSteps.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.discordPresence.oauthRedirectUri.isNotBlank()) {
                            Text(
                                text = "OAuth redirect URI: ${state.discordPresence.oauthRedirectUri}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        SettingsPadTarget(
                            id = "social_discord_portal",
                            onActivate = {
                                runCatching {
                                    context.startActivity(viewModel.openDiscordDeveloperPortalIntent())
                                }
                            },
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(viewModel.openDiscordDeveloperPortalIntent())
                                    }
                                },
                            ) {
                                Text(text = "Open Discord Developer Portal")
                            }
                        }
                    }
                    DiscordPresenceCapability.NeedsAccountLink,
                    DiscordPresenceCapability.NeedsDiscordApp,
                    DiscordPresenceCapability.Failed,
                    -> {
                        Text(
                            text = "Social SDK is in this build. Use Social → Circle/Messages → " +
                                "Link Discord (redirect ${state.discordPresence.oauthRedirectUri}). " +
                                "Public Client must be enabled. Presence is visible to Discord friends.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DiscordPresenceCapability.Connected -> {
                        val friendCount = state.discordPresence.friends.size
                        Text(
                            text = if (state.discordPresence.presencePublishing) {
                                "Linked · Publishing presence" +
                                    if (friendCount > 0) " · $friendCount Discord friends." else "."
                            } else if (friendCount > 0) {
                                "Linked · Connected · $friendCount Discord friends loaded."
                            } else {
                                "Linked · Connected. Browse a game to publish presence."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DiscordPresenceCapability.NotConfigured -> Unit
                }

                if (state.discordSocial.hasApplicationId) {
                    SettingsPadTarget(
                        id = "social_discord_app_id_clear",
                        onActivate = viewModel::clearDiscordApplicationId,
                    ) {
                        OutlinedButton(onClick = viewModel::clearDiscordApplicationId) {
                            Text(text = "Clear Application ID")
                        }
                    }
                }
            }
        }

        }

        if (section == SetupSection.Storage) {
        // 8. Storage / Library roots — access, folders, scan
        run {
            SettingsCard(
                title = "Storage / Library",
                iconRes = DsR.drawable.xmb_figma_folder,
                modifier = Modifier,
            ) {
                SettingsFieldLabel("Storage access")
                Text(
                    text = if (state.hasStorageAccess) {
                        "All-files access granted. XOrA Launcher and XOrA Emulator share one " +
                            "library — folders open by real path (no duplicate ROM entries)."
                    } else {
                        "Without all-files access, XOrA can still read folders you pick through " +
                            "the document picker, but XOrA Emulator, Dolphin, and DuckStation " +
                            "will not be able to open those games."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.hasStorageAccess) {
                    SettingsPadTarget(
                        id = "storage_grant_access",
                        onActivate = { permissionLauncher.launch(viewModel.allFilesAccessIntent()) },
                    ) {
                        Button(onClick = { permissionLauncher.launch(viewModel.allFilesAccessIntent()) }) {
                            Text(text = "Grant all-files access")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Library folders")
                if (state.roots.isEmpty()) {
                    Text(
                        text = "No folders yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsPadRow("storage_add") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsPadTarget(
                        id = "storage_add_folder",
                        onActivate = { if (state.hasStorageAccess) showFolderPicker = true },
                    ) {
                        Button(
                            onClick = { showFolderPicker = true },
                            enabled = state.hasStorageAccess,
                        ) {
                            Text(text = "Add folder")
                        }
                    }
                    SettingsPadTarget(
                        id = "storage_add_saf",
                        onActivate = { safPicker.launch(viewModel.openDocumentTreeIntent()) },
                    ) {
                        OutlinedButton(
                            onClick = { safPicker.launch(viewModel.openDocumentTreeIntent()) },
                        ) {
                            Text(text = "Add via document picker")
                        }
                    }
                }
                }

                state.roots.forEach { root ->
                    RootRowInline(
                        root = root,
                        onRemove = { viewModel.removeRoot(root) },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Scan")
                Text(
                    text = "${state.gameCount} games indexed. XOrA scans automatically when " +
                        "you add a folder and when files change inside it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.scanProgress.isRunning) {
                    Text(
                        text = "Scanning ${state.scanProgress.currentRoot ?: ""} — " +
                            "${state.scanProgress.gamesFound} found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    SettingsPadTarget(
                        id = "storage_scan_now",
                        onActivate = { if (state.roots.isNotEmpty()) viewModel.scanNow() },
                    ) {
                        Button(
                            onClick = viewModel::scanNow,
                            enabled = state.roots.isNotEmpty(),
                        ) {
                            Text(text = "Scan now")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Android apps")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sync installed apps",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PadSwitch(
                        id = "android_sync",
                        checked = state.settings.androidAppSyncEnabled,
                        onCheckedChange = viewModel::setAndroidAppSyncEnabled,
                    )
                }
                Text(
                    text = if (state.settings.androidAppSyncEnabled) {
                        "${state.androidAppCount} apps on the Android platform. " +
                            "Syncs automatically when the shell regains focus."
                    } else {
                        "Installed apps stay out of the library and the Android platform."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadTarget(
                    id = "android_sync_now",
                    onActivate = {
                        if (state.settings.androidAppSyncEnabled && !state.isSyncingApps) {
                            viewModel.syncAndroidAppsNow()
                        }
                    },
                ) {
                    Button(
                        onClick = viewModel::syncAndroidAppsNow,
                        enabled = state.settings.androidAppSyncEnabled && !state.isSyncingApps,
                    ) {
                        Text(text = if (state.isSyncingApps) "Syncing…" else "Sync apps now")
                    }
                }
                if (state.settings.androidAppSyncEnabled) {
                    var androidAppQuery by remember { mutableStateOf("") }
                    val selectedPackages = selectedAndroidPackages(
                        mode = state.settings.androidAppInclusionMode,
                        allowlist = state.settings.androidAppAllowlist,
                        allPackages = state.launchableAndroidApps.map { it.packageName }.toSet(),
                    )
                    Text(
                        text = if (state.settings.androidAppInclusionMode ==
                            AndroidAppInclusionMode.All
                        ) {
                            "Every launchable app is included. Uncheck any to switch to a custom list."
                        } else {
                            "Only the ticked apps appear on the Android platform."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AndroidAppPicker(
                        apps = state.launchableAndroidApps,
                        selectedPackages = selectedPackages,
                        query = androidAppQuery,
                        onQueryChange = { androidAppQuery = it },
                        onToggle = viewModel::toggleAndroidAppIncluded,
                        onSelectAll = viewModel::includeAllAndroidApps,
                        onClear = { viewModel.setAndroidAppAllowlist(emptySet()) },
                    )
                }
            }
        }

        }

        if (section == SetupSection.System) {
        // 9. System / Launcher — HOME role (host) + emulators / players
        run {
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                systemSection()
                SettingsCard(
                    title = "Onboarding",
                    iconRes = DsR.drawable.xmb_figma_settings,
                ) {
                    Text(
                        text = "Replay the first-run welcome flow for display mode, library " +
                            "folders, Android apps, and audio tips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsPadTarget(
                        id = "system_onboarding",
                        onActivate = onGoToOnboarding,
                    ) {
                        OutlinedButton(onClick = onGoToOnboarding) {
                            Text(text = "Go to Onboarding")
                        }
                    }
                }
            }
        }

        }

        if (section == SetupSection.Emulators) {
        run {
            Text(
                text = "Tip: on a ROM, press Select → ROM options to customize art, " +
                    "sound bite, and saves, or Choose Emulator to pick " +
                    "an installed app or RetroArch core for the current system.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier,
            )
        }

        run {
            SettingsCard(
                title = if (state.detectedEmulatorApps.isEmpty()) {
                    "Detected emulators"
                } else {
                    "Detected emulators (${state.detectedEmulatorApps.size})"
                },
                iconRes = DsR.drawable.xmb_figma_game,
                modifier = Modifier,
            ) {
                Text(
                    text = "XOrA watches this device. Installing an emulator adds it here; " +
                        "uninstalling it removes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.detectedEmulatorApps.isEmpty()) {
                    Text(
                        text = "None installed yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.detectedEmulatorApps.forEachIndexed { index, app ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = app.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                                if (app.platformLabels.isNotEmpty()) {
                                    Text(
                                        text = app.platformLabels.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                SettingsPadTarget(
                    id = "emulators_refresh",
                    onActivate = viewModel::scanEmulators,
                ) {
                    Button(onClick = viewModel::scanEmulators) {
                        Text(text = "Refresh now")
                    }
                }
            }
        }

        run {
            SettingsCard(
                title = "XOrA Emulator (Libretro)",
                iconRes = DsR.drawable.xmb_figma_game,
                modifier = Modifier,
            ) {
                Text(
                    text = "Built-in Libretro host. Downloads cores from the Libretro buildbot " +
                        "into app storage (not bundled in the APK). Place BIOS files under " +
                        "Android/data/com.sora.shell/files/system/ when a core needs them " +
                        "(e.g. PS1). See THIRD_PARTY_NOTICES.md for core licenses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingsFieldLabel("Filesystem ROM access")
                Text(
                    text = if (state.hasStorageAccess) {
                        "All-files access granted. XOrA Emulator can open ROMs by real " +
                            "filesystem path (required for Libretro load)."
                    } else {
                        "XOrA Emulator needs all-files access so it can pass a real filesystem " +
                            "ROM path to Libretro. Document-picker library folders alone are not enough."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadTarget(
                    id = "emulators_filesystem",
                    onActivate = { permissionLauncher.launch(viewModel.allFilesAccessIntent()) },
                ) {
                    if (!state.hasStorageAccess) {
                        Button(onClick = { permissionLauncher.launch(viewModel.allFilesAccessIntent()) }) {
                            Text(text = "Allow access to system files")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(viewModel.allFilesAccessIntent()) },
                        ) {
                            Text(text = "System files access settings")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val statusLine = when {
                    state.xoraDownloadRunning ->
                        state.xoraDownloadMessage ?: "Downloading cores…"
                    state.xoraDownloadError != null ->
                        state.xoraDownloadError!!
                    state.xoraCoresTotal == 0 ->
                        "No cores listed in catalog."
                    state.xoraCoresInstalled == state.xoraCoresTotal ->
                        "All ${state.xoraCoresInstalled} catalog cores installed."
                    state.xoraCoresInstalled == 0 ->
                        "No cores downloaded yet (${state.xoraCoresTotal} available)."
                    else ->
                        "${state.xoraCoresInstalled} of ${state.xoraCoresTotal} cores installed."
                }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        state.xoraDownloadError != null -> MaterialTheme.colorScheme.error
                        else -> Color.White
                    },
                )
                if (state.xoraDownloadRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val missing = state.xoraCores
                    .distinctBy { it.core }
                    .filterNot { it.installed }
                    .take(8)
                if (missing.isNotEmpty() && !state.xoraDownloadRunning) {
                    Text(
                        text = "Missing: " + missing.joinToString { "${it.platformLabel} (${it.label})" } +
                            if (state.xoraCores.count { !it.installed } > missing.size) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val installedSample = state.xoraCores
                    .distinctBy { it.core }
                    .filter { it.installed }
                    .take(6)
                if (installedSample.isNotEmpty() && !state.xoraDownloadRunning) {
                    Text(
                        text = "Installed: " + installedSample.joinToString { it.label } +
                            if (state.xoraCoresInstalled > installedSample.size) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsPadTarget(
                    id = "xora_download_cores",
                    onActivate = { if (!state.xoraDownloadRunning) viewModel.downloadXoraCores() },
                ) {
                    Button(
                        onClick = viewModel::downloadXoraCores,
                        enabled = !state.xoraDownloadRunning,
                    ) {
                        Text(
                            text = if (state.xoraDownloadRunning) {
                                "Downloading…"
                            } else {
                                "Download missing cores"
                            },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "RetroAchievements (XOrA Emulator)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Same account as the launcher. Sign in here to unlock achievements " +
                        "while playing in XOrA Emulator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Enable RetroAchievements", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "xora_ra_enable",
                        checked = state.raSettings.enabled,
                        onCheckedChange = viewModel::setRaEnabled,
                    )
                }
                RetroAchievementsSignInFields(
                    configured = state.retroAchievements,
                    isBusy = state.raAuthBusy,
                    error = state.raAuthError,
                    pendingWebApiUsername = state.raPendingWebApiUsername,
                    onPasswordSignIn = viewModel::loginRetroAchievements,
                    onApiKeySignIn = viewModel::setRetroAchievementsCredentials,
                    padPrefix = "xora_emu_ra",
                )
                if (state.retroAchievements.isConfigured) {
                    Text(
                        text = "Signed in as ${state.retroAchievements.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    SettingsPadTarget(
                        id = "xora_emu_ra_sign_out",
                        onActivate = viewModel::clearRetroAchievementsCredentials,
                    ) {
                        OutlinedButton(onClick = viewModel::clearRetroAchievementsCredentials) {
                            Text(text = "Sign out")
                        }
                    }
                }
                Text(
                    text = if (state.missingRomHashes == 0) {
                        "Library hashes ready for launcher RetroAchievements."
                    } else {
                        "${state.missingRomHashes} ROMs still need hashing for launcher RA."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPadTarget(
                    id = "xora_hash_roms",
                    onActivate = { if (!state.isHashingRoms) viewModel.hashAllRoms() },
                ) {
                    OutlinedButton(
                        onClick = viewModel::hashAllRoms,
                        enabled = !state.isHashingRoms,
                    ) {
                        Text(text = if (state.isHashingRoms) "Hashing…" else "Hash all ROMs")
                    }
                }
            }
        }

        run {
            val xora = state.xoraEmulator
            SettingsCard(
                title = "XOrA · System bezels",
                iconRes = DsR.drawable.xmb_figma_photo,
                modifier = Modifier,
            ) {
                Text(
                    text = "NSO bezels use the overlay pack layout: `cfg/nso-gba.cfg` points at " +
                        "`img/nso-gba.png` (and `nso-gba-full` for full screen). Drop the pack’s " +
                        "`img` folder into XOrA’s overlays directory or next to your ROMs. " +
                        "Your profile picture replaces the top-left icon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Enable bezels", style = MaterialTheme.typography.bodyMedium)
                    PadSwitch(
                        id = "xora_bezels",
                        checked = xora.bezelsEnabled,
                        onCheckedChange = viewModel::setXoraBezelsEnabled,
                    )
                }
                SettingsFieldLabel(
                    "Bezel strength (${(xora.bezelOpacity * 100f).roundToInt()}%)",
                )
                SettingsPadTarget(
                    id = "xora_bezel_opacity",
                    onActivate = { },
                    onAdjust = { delta ->
                        viewModel.setXoraBezelOpacity(
                            (xora.bezelOpacity + delta * 0.05f).coerceIn(0.35f, 1f),
                        )
                    },
                ) {
                    Slider(
                        value = xora.bezelOpacity,
                        onValueChange = viewModel::setXoraBezelOpacity,
                        valueRange = 0.35f..1f,
                        enabled = xora.bezelsEnabled,
                    )
                }
            }
        }

        if (state.platformChoices.isEmpty()) {
            run {
                Text(
                    text = "Per-system players appear after a library scan, or as soon as " +
                        "XOrA detects a standalone emulator.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier,
                )
            }
        }

        state.platformChoices.forEach { choice ->
            PlatformPlayerCard(
                choice = choice,
                onSelect = { playerId ->
                    viewModel.selectPlayer(choice.summary.platform.id, playerId)
                },
                modifier = Modifier,
            )
        }
        }
    }
    }
    }
    }
    }
    }
}

/**
 * Setup is a long form, so it is split into tabs instead of one endless scroll. Each entry maps
 * to a contiguous run of cards in the list.
 */
private enum class SetupSection(
    val label: String,
    val description: String,
    val iconRes: Int,
) {
    Display("Display", "Theme, trailers, and library text", DsR.drawable.xmb_figma_device),
    Audio("Audio", "Soundtrack and interface sounds", DsR.drawable.xmb_figma_music),
    Media("Media", "Trailers and artwork scraping", DsR.drawable.xmb_figma_video),
    Accounts("Accounts", "RetroAchievements, Steam, and Discord", DsR.drawable.xmb_figma_network),
    Storage("Storage", "Library folders, scanning, and app sync", DsR.drawable.xmb_figma_folder),
    System("System", "Home screen role and onboarding", DsR.drawable.xmb_figma_settings),
    Emulators("Emulators", "Detected apps and per-system players", DsR.drawable.xmb_figma_game),
}


@Composable
private fun SetupSectionTab(
    section: SetupSection,
    selected: Boolean,
    onClick: () -> Unit,
    focused: Boolean = false,
) {
    val theme = LocalShellTheme.current.colors
    val shape = ArcadiaGlass.ChipShape
    Row(
        modifier = Modifier
            .then(
                if (focused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.88f), shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            theme.focusStart.copy(alpha = 0.42f),
                            theme.focusEnd.copy(alpha = 0.32f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.06f),
                        ),
                    )
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(section.iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = XoraFonts.XmbLabel,
            ),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun SettingsFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = XoraFonts.XmbLabel,
        ),
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = modifier,
    )
}

@Composable
private fun PadChip(
    id: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String,
) {
    SettingsPadTarget(id = id, onActivate = { if (enabled) onClick() }) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            label = { Text(text = label) },
        )
    }
}

@Composable
private fun PadSwitch(
    id: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsPadTarget(id = id, onActivate = { if (enabled) onCheckedChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * "Display over other apps" for the companion bottom screen.
 *
 * This one cannot be requested with a runtime dialog, and the user grants it in a system settings
 * page and comes back, so the state is re-read on every resume rather than remembered once.
 */
@Composable
private fun CompanionScreenPermissionRow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = OverlayPermission.isGranted(context)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsFieldLabel("Companion bottom screen")
        Text(
            text = "While a single-screen game runs in Dual screen mode, the second display shows " +
                "the game's artwork with About and Manual. DS, 3DS, and Wii U are skipped — their " +
                "emulators own that screen. Staying visible after the emulator takes over needs " +
                "\"Display over other apps\"; without it the panel only shows while XOrA is in front.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (granted) "Permission granted" else "Permission not granted",
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (!granted) {
                SettingsPadTarget(
                    id = "companion_overlay_allow",
                    onActivate = {
                        if (enabled) {
                            runCatching {
                                context.startActivity(OverlayPermission.settingsIntent(context))
                            }
                        }
                    },
                ) {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = {
                            runCatching { context.startActivity(OverlayPermission.settingsIntent(context)) }
                        },
                    ) {
                        Text(text = "Allow")
                    }
                }
            }
        }
    }
}

/**
 * Credential entry that commits only when focus leaves the field.
 *
 * Writing on every keystroke would push a DataStore write per character and, worse, would make the
 * recomposed value fight the user's cursor as they type.
 */
@Composable
private fun SecretField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    padId: String,
    modifier: Modifier = Modifier,
) {
    var draft by remember(value) { mutableStateOf(value) }
    val requester = remember { FocusRequester() }

    SettingsPadTarget(id = padId, onActivate = { requester.requestFocus() }) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(text = label) },
            singleLine = true,
            visualTransformation = if (draft.isBlank()) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onFocusChanged { focus -> if (!focus.isFocused && draft != value) onCommit(draft) },
        )
    }
}

/** Two fields that only make sense together, so they are saved as a pair. */
@Composable
private fun PairedSecretFields(
    firstLabel: String,
    secondLabel: String,
    firstValue: String,
    secondValue: String,
    onCommit: (String, String) -> Unit,
    firstPadId: String,
    secondPadId: String,
    modifier: Modifier = Modifier,
) {
    var first by remember(firstValue) { mutableStateOf(firstValue) }
    var second by remember(secondValue) { mutableStateOf(secondValue) }
    val firstRequester = remember { FocusRequester() }
    val secondRequester = remember { FocusRequester() }

    val commit = {
        if (first != firstValue || second != secondValue) onCommit(first, second)
    }

    SettingsPadRow(firstPadId) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsPadTarget(
            id = firstPadId,
            onActivate = { firstRequester.requestFocus() },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = first,
                onValueChange = { first = it },
                label = { Text(text = firstLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstRequester)
                    .onFocusChanged { focus -> if (!focus.isFocused) commit() },
            )
        }
        SettingsPadTarget(
            id = secondPadId,
            onActivate = { secondRequester.requestFocus() },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = second,
                onValueChange = { second = it },
                label = { Text(text = secondLabel) },
                singleLine = true,
                visualTransformation = if (second.isBlank()) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(secondRequester)
                    .onFocusChanged { focus -> if (!focus.isFocused) commit() },
            )
        }
    }
    }
}

@Composable
internal fun RetroAchievementsSignInFields(
    configured: com.arcadia.shell.datastore.RetroAchievementsCredentials,
    isBusy: Boolean,
    error: String?,
    pendingWebApiUsername: String?,
    onPasswordSignIn: (username: String, password: String) -> Unit,
    onApiKeySignIn: (username: String, apiKey: String) -> Unit,
    padPrefix: String = "ra",
) {
    var username by remember(configured.username, pendingWebApiUsername) {
        mutableStateOf(pendingWebApiUsername ?: configured.username)
    }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showAdvanced by remember(pendingWebApiUsername) {
        mutableStateOf(!pendingWebApiUsername.isNullOrBlank())
    }

    val userRequester = remember { FocusRequester() }
    val passRequester = remember { FocusRequester() }
    val apiRequester = remember { FocusRequester() }

    if (!pendingWebApiUsername.isNullOrBlank()) {
        Text(
            text = "Password accepted for $pendingWebApiUsername. Paste your Web API key from " +
                "https://retroachievements.org/controlpanel.php (Keys).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsPadTarget(
            id = "${padPrefix}_api_key",
            onActivate = { apiRequester.requestFocus() },
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Web API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().focusRequester(apiRequester),
            )
        }
        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        SettingsPadTarget(
            id = "${padPrefix}_save_api",
            onActivate = {
                if (!isBusy && apiKey.isNotBlank()) {
                    onApiKeySignIn(pendingWebApiUsername, apiKey)
                    apiKey = ""
                }
            },
        ) {
            Button(
                onClick = {
                    onApiKeySignIn(pendingWebApiUsername, apiKey)
                    apiKey = ""
                },
                enabled = !isBusy && apiKey.isNotBlank(),
            ) {
                Text(if (isBusy) "Saving…" else "Save API key")
            }
        }
        return
    }

    SettingsPadTarget(
        id = "${padPrefix}_username",
        onActivate = { userRequester.requestFocus() },
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(userRequester),
        )
    }
    SettingsPadTarget(
        id = "${padPrefix}_password",
        onActivate = { passRequester.requestFocus() },
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().focusRequester(passRequester),
        )
    }
    error?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
    SettingsPadTarget(
        id = "${padPrefix}_sign_in",
        onActivate = {
            if (!isBusy && username.isNotBlank() && password.isNotEmpty()) {
                val pass = password
                password = ""
                onPasswordSignIn(username, pass)
            }
        },
    ) {
        Button(
            onClick = {
                val pass = password
                password = ""
                onPasswordSignIn(username, pass)
            },
            enabled = !isBusy && username.isNotBlank() && password.isNotEmpty(),
        ) {
            Text(if (isBusy) "Signing in…" else "Sign in")
        }
    }

    SettingsPadTarget(
        id = "${padPrefix}_toggle_api",
        onActivate = { if (!isBusy) showAdvanced = !showAdvanced },
    ) {
        TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !isBusy) {
            Text(if (showAdvanced) "Hide API key option" else "Paste Web API key instead")
        }
    }
    if (showAdvanced) {
        SettingsPadTarget(
            id = "${padPrefix}_api_key_adv",
            onActivate = { apiRequester.requestFocus() },
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Web API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().focusRequester(apiRequester),
            )
        }
        SettingsPadTarget(
            id = "${padPrefix}_sign_in_api",
            onActivate = {
                if (!isBusy && username.isNotBlank() && apiKey.isNotBlank()) {
                    onApiKeySignIn(username, apiKey)
                    apiKey = ""
                }
            },
        ) {
            OutlinedButton(
                onClick = {
                    onApiKeySignIn(username, apiKey)
                    apiKey = ""
                },
                enabled = !isBusy && username.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Text(if (isBusy) "Signing in…" else "Sign in with API key")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    focused: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.88f), ArcadiaGlass.CardShape)
                } else {
                    Modifier
                },
            )
            .xoraModalGlass(ArcadiaGlass.CardShape, shimmer = false),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = XoraFonts.XmbLabel,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            content()
        }
    }
}

/** Compact root row nested inside the Storage / Library card. */
@Composable
private fun RootRowInline(
    root: LibraryRoot,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = ArcadiaGlass.CardShape,
        tone = GlassTone.OverMedia,
        intensity = GlassIntensity.Subtle,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = root.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    text = when (root.kind) {
                        RootKind.Filesystem -> root.location
                        RootKind.SafTree -> "Document picker folder — path-based emulators cannot use it"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsPadTarget(
                id = "root_remove_${root.id}",
                onActivate = onRemove,
            ) {
                TextButton(onClick = onRemove) { Text(text = "Remove") }
            }
        }
    }
}

@Composable
private fun PlatformPlayerCard(
    choice: PlatformPlayerChoice,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = "${choice.summary.platform.displayName} (${choice.summary.gameCount})",
        modifier = modifier,
    ) {
        if (choice.candidates.isEmpty()) {
            Text(
                text = "No emulator for this system is installed yet. " +
                    "Install one and XOrA will pick it up automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return@SettingsCard
        }

        Text(
            text = when {
                choice.effectivePlayer == null -> "Nothing installed that can open these games."
                choice.isInstalled -> "Opens with ${choice.effectivePlayer.name}"
                else -> "${choice.effectivePlayer.name} is selected but not installed."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (choice.isInstalled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choice.candidates.forEach { player ->
                PadChip(
                    id = "player_${choice.summary.platform.id}_${player.uniqueId}",
                    selected = choice.selectedPlayerId == player.uniqueId,
                    onClick = {
                        onSelect(
                            if (choice.selectedPlayerId == player.uniqueId) null else player.uniqueId,
                        )
                    },
                    label = player.name,
                )
            }
        }
    }
}
