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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.DualScreenLayout
import com.arcadia.shell.datastore.NdsWfcServer
import com.arcadia.shell.datastore.ThemeMode
import com.arcadia.shell.datastore.ThreeDsScreenLayout
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.GameIconIdleMedia
import com.arcadia.shell.datastore.TrailerSourcePreference
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.XoraInternalResolution
import com.arcadia.shell.datastore.label
import com.arcadia.shell.display.OverlayPermission
import com.arcadia.shell.launcher.discord.DiscordPresenceCapability
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LiquidGlassSurface
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.R as DsR
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.ScreenRole
import com.arcadia.shell.libretro.netplay.AzaharPublicLobbies
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFolderPicker by remember { mutableStateOf(false) }
    var showMusicFolderPicker by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(SetupSection.Display) }
    val listState = rememberLazyListState()

    // Switching tabs must land at the top, not halfway down the previous section.
    LaunchedEffect(section) { listState.scrollToItem(0) }

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
    Box(modifier = Modifier.fillMaxSize()) {
        backdrop()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
                ),
        )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        userScrollEnabled = true,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth().animateItem(),
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
                                color = Color.White.copy(alpha = 0.58f),
                            )
                        }
                    }
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Done",
                            fontFamily = XoraFonts.XmbLabel,
                            color = Color.White,
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(SetupSection.entries, key = { it.name }) { entry ->
                        SetupSectionTab(
                            section = entry,
                            selected = entry == section,
                            onClick = { section = entry },
                        )
                    }
                }
            }
        }

        if (state.xoraDownloadRunning || state.message != null || state.xoraDownloadError != null) {
            item(key = "status_banner") {
                val downloadError = state.xoraDownloadError
                val bannerMessage = state.message
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
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
        item(key = "appearance") {
            SettingsCard(
                title = "Appearance",
                iconRes = DsR.drawable.xmb_figma_device,
                modifier = Modifier.animateItem(),
            ) {
                SettingsFieldLabel("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.System -> "System"
                                        ThemeMode.Light -> "Light"
                                        ThemeMode.Dark -> "Dark"
                                    },
                                )
                            },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("XMB game titles")
                Text(
                    text = "Clear logos beside box art, or plain text titles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.xmbTitleStyle == XmbTitleStyle.TitleIcons,
                        onClick = { viewModel.setXmbTitleStyle(XmbTitleStyle.TitleIcons) },
                        label = { Text(text = "Title icons") },
                    )
                    FilterChip(
                        selected = state.settings.xmbTitleStyle == XmbTitleStyle.Text,
                        onClick = { viewModel.setXmbTitleStyle(XmbTitleStyle.Text) },
                        label = { Text(text = "Text") },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Trailer display")
                Text(
                    text = "How idle trailers fill the game selector when they play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.trailerDisplayMode ==
                            TrailerDisplayMode.InIcon,
                        onClick = {
                            viewModel.setTrailerDisplayMode(TrailerDisplayMode.InIcon)
                        },
                        enabled = state.settings.trailerEnabled,
                        label = { Text(text = "Game icon") },
                    )
                    FilterChip(
                        selected = state.settings.trailerDisplayMode ==
                            TrailerDisplayMode.FullBackground,
                        onClick = {
                            viewModel.setTrailerDisplayMode(TrailerDisplayMode.FullBackground)
                        },
                        enabled = state.settings.trailerEnabled,
                        label = { Text(text = "Full background") },
                    )
                    FilterChip(
                        selected = state.settings.trailerDisplayMode ==
                            TrailerDisplayMode.CornerPip,
                        onClick = {
                            viewModel.setTrailerDisplayMode(TrailerDisplayMode.CornerPip)
                        },
                        enabled = state.settings.trailerEnabled,
                        label = { Text(text = "Corner PIP") },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Game Icon idle")
                Text(
                    text = "What fills the focused Game Icon. Trailers stay the default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.gameIconIdleMedia ==
                            GameIconIdleMedia.Trailer,
                        onClick = {
                            viewModel.setGameIconIdleMedia(GameIconIdleMedia.Trailer)
                        },
                        label = { Text(text = "Trailers") },
                    )
                    FilterChip(
                        selected = state.settings.gameIconIdleMedia ==
                            GameIconIdleMedia.Screenshot,
                        onClick = {
                            viewModel.setGameIconIdleMedia(GameIconIdleMedia.Screenshot)
                        },
                        label = { Text(text = "Screenshots") },
                    )
                }
            }
        }

        // 2. Library / Layout — display mode, feed grid + second screen
        item(key = "library_layout") {
            SettingsCard(
                title = "Library / Layout",
                iconRes = DsR.drawable.xmb_figma_folder,
                modifier = Modifier.animateItem(),
            ) {
                SettingsFieldLabel("Display mode")
                Text(
                    text = "Single screen uses a vertical game selector on one display. Dual screen " +
                        "splits library and artwork when a second display is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.displayMode == DisplayMode.Single,
                        onClick = { viewModel.setDisplayMode(DisplayMode.Single) },
                        label = { Text(text = "Single screen") },
                    )
                    FilterChip(
                        selected = state.settings.displayMode == DisplayMode.Dual,
                        onClick = { viewModel.setDisplayMode(DisplayMode.Dual) },
                        label = { Text(text = "Dual screen") },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Library columns: ${state.settings.gridColumns}")
                Text(
                    text = "Columns for the dual-screen library grid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5, 6).forEach { columns ->
                        FilterChip(
                            selected = state.settings.gridColumns == columns,
                            onClick = { viewModel.setGridColumns(columns) },
                            label = { Text(text = columns.toString()) },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SettingsFieldLabel("Second screen shows")
                Text(
                    text = "Used when Dual screen mode has a second display.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenRole.entries.forEach { role ->
                        FilterChip(
                            selected = state.settings.secondaryDisplayRole == role,
                            onClick = { viewModel.setSecondaryDisplayRole(role) },
                            enabled = state.settings.displayMode == DisplayMode.Dual,
                            label = {
                                Text(
                                    text = when (role) {
                                        ScreenRole.Hero -> "Artwork"
                                        ScreenRole.Grid -> "Library"
                                    },
                                )
                            },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                CompanionScreenPermissionRow(
                    enabled = state.settings.displayMode == DisplayMode.Dual,
                )

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
                    Switch(
                        checked = state.settings.showHiddenGames,
                        onCheckedChange = viewModel::setShowHiddenGames,
                    )
                }
            }
        }

        }

        if (section == SetupSection.Audio) {
        // 3. Audio — BGM + UI SFX
        item(key = "audio") {
            SettingsCard(
                title = "Audio",
                iconRes = DsR.drawable.xmb_figma_music,
                modifier = Modifier.animateItem(),
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
                Slider(
                    value = draftVolume,
                    onValueChange = { draftVolume = it },
                    onValueChangeFinished = { viewModel.setBgmVolume(draftVolume) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                Slider(
                    value = draftSfx,
                    onValueChange = { draftSfx = it },
                    onValueChangeFinished = { viewModel.setUiSfxVolume(draftSfx) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showMusicFolderPicker = true },
                        enabled = state.hasStorageAccess,
                    ) {
                        Text(text = "Choose folder")
                    }
                    if (!state.settings.musicLibraryPath.isNullOrBlank()) {
                        OutlinedButton(onClick = { viewModel.setMusicLibraryPath(null) }) {
                            Text(text = "Use all device music")
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
        item(key = "trailers") {
            SettingsCard(
                title = "Trailers",
                iconRes = DsR.drawable.xmb_figma_video,
                modifier = Modifier.animateItem(),
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
                    Switch(
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrailerSourcePreference.entries.forEach { preference ->
                        FilterChip(
                            selected = state.settings.trailerSourcePreference == preference,
                            onClick = { viewModel.setTrailerSourcePreference(preference) },
                            enabled = state.settings.trailerScrapeEnabled,
                            label = {
                                Text(
                                    text = when (preference) {
                                        TrailerSourcePreference.Auto -> "Auto"
                                        TrailerSourcePreference.YouTube -> "YouTube"
                                        TrailerSourcePreference.Steam -> "Steam"
                                        TrailerSourcePreference.ScreenScraper -> "ScreenScraper"
                                        TrailerSourcePreference.Igdb -> "IGDB"
                                    },
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Idle trailers", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.settings.trailerEnabled,
                        onCheckedChange = viewModel::setTrailerEnabled,
                    )
                }
            }
        }

        // 5. Scrapers / Metadata
        item(key = "scrapers") {
            SettingsCard(
                title = "Scrapers / Metadata",
                iconRes = DsR.drawable.xmb_figma_photo,
                modifier = Modifier.animateItem(),
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
                )

                PairedSecretFields(
                    firstLabel = "ScreenScraper user",
                    secondLabel = "ScreenScraper password",
                    firstValue = state.credentials.screenScraperUser,
                    secondValue = state.credentials.screenScraperPassword,
                    onCommit = viewModel::setScreenScraperCredentials,
                )

                PairedSecretFields(
                    firstLabel = "ScreenScraper dev id",
                    secondLabel = "ScreenScraper dev password",
                    firstValue = state.credentials.screenScraperDevId,
                    secondValue = state.credentials.screenScraperDevPassword,
                    onCommit = viewModel::setScreenScraperDevCredentials,
                )

                PairedSecretFields(
                    firstLabel = "IGDB client id",
                    secondLabel = "IGDB client secret",
                    firstValue = state.credentials.igdbClientId,
                    secondValue = state.credentials.igdbClientSecret,
                    onCommit = viewModel::setIgdbCredentials,
                )

                FilterChip(
                    selected = state.settings.scrapeAfterScan,
                    onClick = { viewModel.setScrapeAfterScan(!state.settings.scrapeAfterScan) },
                    label = { Text(text = "Fetch artwork automatically after a scan") },
                )

                FilterChip(
                    selected = state.settings.manualScrapeEnabled,
                    onClick = {
                        viewModel.setManualScrapeEnabled(!state.settings.manualScrapeEnabled)
                    },
                    label = { Text(text = "Download game manuals") },
                )
                Text(
                    text = "Manuals come from ScreenScraper and are the largest media it serves, " +
                        "so a big library can pull several gigabytes. Read them from the companion " +
                        "screen while a game runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = viewModel::scrapeNow,
                        enabled = !state.isScraping && state.gameCount > 0,
                    ) {
                        Text(text = if (state.isScraping) "Fetching…" else "Fetch artwork now")
                    }
                    if (state.isScraping) {
                        OutlinedButton(onClick = viewModel::cancelScrape) {
                            Text(text = "Stop")
                        }
                    }
                }
            }
        }

        }

        if (section == SetupSection.Accounts) {
        // 6. RetroAchievements
        item(key = "ra") {
            SettingsCard(
                title = "RetroAchievements",
                iconRes = DsR.drawable.xmb_figma_trophy,
                modifier = Modifier.animateItem(),
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
                    Switch(
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
                    Switch(
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
                    Switch(
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
                    Switch(
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
                    Switch(
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
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = viewModel::clearRetroAchievementsCredentials) {
                        Text(text = "Sign out")
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

        // 7. Social
        item(key = "social") {
            SettingsCard(
                title = "Social",
                iconRes = DsR.drawable.xmb_figma_network,
                modifier = Modifier.animateItem(),
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

                SettingsFieldLabel("Sign in with Steam")
                Text(
                    text = "Sign in for Steam ID; API key still required once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

                SecretField(
                    label = "Steam Web API key",
                    value = state.steamWebApi.apiKey,
                    onCommit = viewModel::setSteamWebApiKey,
                )
                var steamIdDraft by remember(state.steamWebApi.steamId64) {
                    mutableStateOf(state.steamWebApi.steamId64)
                }
                OutlinedTextField(
                    value = steamIdDraft,
                    onValueChange = { steamIdDraft = it },
                    label = { Text(text = "SteamID64 (from Sign in with Steam)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused && steamIdDraft != state.steamWebApi.steamId64) {
                                viewModel.setSteamId64(steamIdDraft)
                            }
                        },
                )

                if (state.steamWebApi.apiKey.isNotBlank() || state.steamWebApi.steamId64.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::clearSteamWebApiCredentials) {
                        Text(text = "Clear Steam credentials")
                    }
                }

                SettingsFieldLabel("Discord")
                var discordDraft by remember(state.discordSocial.openUrl) {
                    mutableStateOf(state.discordSocial.openUrl)
                }
                OutlinedTextField(
                    value = discordDraft,
                    onValueChange = { discordDraft = it },
                    label = { Text(text = "Discord invite / profile URL") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused && discordDraft != state.discordSocial.openUrl) {
                                viewModel.setDiscordOpenUrl(discordDraft)
                            }
                        },
                )

                if (state.discordSocial.hasLink) {
                    OutlinedButton(onClick = viewModel::clearDiscordOpenUrl) {
                        Text(text = "Clear Discord link")
                    }
                }

                var discordAppIdDraft by remember(state.discordSocial.applicationId) {
                    mutableStateOf(state.discordSocial.applicationId)
                }
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
                        .onFocusChanged { focus ->
                            if (!focus.isFocused &&
                                discordAppIdDraft != state.discordSocial.applicationId
                            ) {
                                viewModel.setDiscordApplicationId(discordAppIdDraft)
                            }
                        },
                )

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
                    OutlinedButton(onClick = viewModel::clearDiscordApplicationId) {
                        Text(text = "Clear Application ID")
                    }
                }
            }
        }

        }

        if (section == SetupSection.Storage) {
        // 8. Storage / Library roots — access, folders, scan
        item(key = "storage") {
            SettingsCard(
                title = "Storage / Library",
                iconRes = DsR.drawable.xmb_figma_folder,
                modifier = Modifier.animateItem(),
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
                    Button(onClick = { permissionLauncher.launch(viewModel.allFilesAccessIntent()) }) {
                        Text(text = "Grant all-files access")
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showFolderPicker = true },
                        enabled = state.hasStorageAccess,
                    ) {
                        Text(text = "Add folder")
                    }
                    OutlinedButton(
                        onClick = { safPicker.launch(viewModel.openDocumentTreeIntent()) },
                    ) {
                        Text(text = "Add via document picker")
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
                    Button(
                        onClick = viewModel::scanNow,
                        enabled = state.roots.isNotEmpty(),
                    ) {
                        Text(text = "Scan now")
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
                    Switch(
                        checked = state.settings.androidAppSyncEnabled,
                        onCheckedChange = viewModel::setAndroidAppSyncEnabled,
                    )
                }
                Text(
                    text = if (state.settings.androidAppSyncEnabled) {
                        "${state.androidAppCount} apps on the Apps tab. " +
                            "Syncs automatically when the shell regains focus."
                    } else {
                        "Installed apps stay out of the library and the Apps tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = viewModel::syncAndroidAppsNow,
                    enabled = state.settings.androidAppSyncEnabled && !state.isSyncingApps,
                ) {
                    Text(text = if (state.isSyncingApps) "Syncing…" else "Sync apps now")
                }
            }
        }

        }

        if (section == SetupSection.System) {
        // 9. System / Launcher — HOME role (host) + emulators / players
        item(key = "system") {
            Column(
                modifier = Modifier.animateItem(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                systemSection()
                SettingsCard(
                    title = "Onboarding",
                    iconRes = DsR.drawable.xmb_figma_settings,
                ) {
                    Text(
                        text = "Replay the first-run welcome flow for display mode, library " +
                            "folders, and audio tips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onGoToOnboarding) {
                        Text(text = "Go to Onboarding")
                    }
                }
            }
        }

        }

        if (section == SetupSection.Emulators) {
        item(key = "emulators_choose_hint") {
            Text(
                text = "Tip: on a ROM, press Select → ROM options to customize art, " +
                    "sound bite, and saves, or Choose Emulator to pick " +
                    "an installed app or RetroArch core for the current system.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "emulators_scan") {
            SettingsCard(
                title = "Detect installed emulators",
                iconRes = DsR.drawable.xmb_figma_game,
                modifier = Modifier.animateItem(),
            ) {
                Text(
                    text = "Rescan for apps like Cemu, Eden, Dolphin, and RetroArch cores. " +
                        "Use this after installing a new emulator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::scanEmulators) {
                    Text(text = "Scan for emulators")
                }
            }
        }

        item(key = "xora_emulator_cores") {
            SettingsCard(
                title = "XOrA Emulator (Libretro)",
                iconRes = DsR.drawable.xmb_figma_game,
                modifier = Modifier.animateItem(),
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
                        state.xoraDownloadRunning -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
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
                    Switch(
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
                )
                if (state.retroAchievements.isConfigured) {
                    Text(
                        text = "Signed in as ${state.retroAchievements.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = viewModel::clearRetroAchievementsCredentials) {
                        Text(text = "Sign out")
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
                OutlinedButton(
                    onClick = viewModel::hashAllRoms,
                    enabled = !state.isHashingRoms,
                ) {
                    Text(text = if (state.isHashingRoms) "Hashing…" else "Hash all ROMs")
                }
            }
        }

        item(key = "xora_display") {
            val xora = state.xoraEmulator
            SettingsCard(
                title = "XOrA · Display",
                iconRes = DsR.drawable.xmb_figma_device,
                modifier = Modifier.animateItem(),
            ) {
                Text(
                    text = "Aspect ratio applies to every core, including the XOrA emulator. " +
                        "Auto keeps the framebuffer. 16:9, 1:1, 4:3 and the other ratios " +
                        "letterbox a forced box. DS / 3DS layout options are below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsFieldLabel("Aspect ratio")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XoraAspectMode.entries.forEach { mode ->
                        FilterChip(
                            selected = xora.aspectMode == mode,
                            onClick = { viewModel.setXoraAspectMode(mode) },
                            label = { Text(text = mode.label()) },
                        )
                    }
                }
                if (xora.aspectMode == XoraAspectMode.Integer) {
                    val scaleLabel = if (xora.integerScale == 0) {
                        "Auto (largest fit)"
                    } else {
                        "${xora.integerScale}×"
                    }
                    SettingsFieldLabel("Integer scale · $scaleLabel")
                    Slider(
                        value = xora.integerScale.toFloat(),
                        onValueChange = { viewModel.setXoraIntegerScale(it.roundToInt()) },
                        valueRange = 0f..6f,
                        steps = 5,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsFieldLabel("Nintendo DS layout")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DualScreenLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = xora.ndsScreenLayout == layout,
                            onClick = { viewModel.setXoraNdsScreenLayout(layout) },
                            label = { Text(text = layout.label()) },
                        )
                    }
                }
                SettingsFieldLabel("DS screen gap (${xora.ndsScreenGap}px)")
                Slider(
                    value = xora.ndsScreenGap.toFloat(),
                    onValueChange = { viewModel.setXoraNdsScreenGap(it.roundToInt()) },
                    valueRange = 0f..64f,
                    steps = 15,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsFieldLabel("Nintendo 3DS layout")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThreeDsScreenLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = xora.threeDsScreenLayout == layout,
                            onClick = { viewModel.setXora3dsScreenLayout(layout) },
                            label = { Text(text = layout.label()) },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Expand to dual displays",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "melonDS / Azahar: top screen fills the primary panel, " +
                                "bottom (touch) screen fills the secondary. On by default " +
                                "on clamshell / dual-screen devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = xora.expandDualDisplay,
                        onCheckedChange = viewModel::setXoraExpandDualDisplay,
                    )
                }
                SettingsFieldLabel("Internal resolution (3DS / Azahar)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XoraInternalResolution.entries.forEach { res ->
                        FilterChip(
                            selected = xora.internalResolution == res,
                            onClick = { viewModel.setXoraInternalResolution(res) },
                            label = { Text(text = res.label()) },
                        )
                    }
                }
            }
        }

        item(key = "xora_bezels") {
            val xora = state.xoraEmulator
            SettingsCard(
                title = "XOrA · System bezels",
                iconRes = DsR.drawable.xmb_figma_photo,
                modifier = Modifier.animateItem(),
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
                    Switch(
                        checked = xora.bezelsEnabled,
                        onCheckedChange = viewModel::setXoraBezelsEnabled,
                    )
                }
                SettingsFieldLabel(
                    "Bezel strength (${(xora.bezelOpacity * 100f).roundToInt()}%)",
                )
                Slider(
                    value = xora.bezelOpacity,
                    onValueChange = viewModel::setXoraBezelOpacity,
                    valueRange = 0.35f..1f,
                    enabled = xora.bezelsEnabled,
                )
            }
        }

        item(key = "xora_netplay") {
            val xora = state.xoraEmulator
            var nickDraft by remember(xora.netplayNickname) {
                mutableStateOf(xora.netplayNickname)
            }
            var hostDraft by remember(xora.netplayHostAddress) {
                mutableStateOf(xora.netplayHostAddress)
            }
            var portDraft by remember(xora.netplayPort.toString()) {
                mutableStateOf(xora.netplayPort.toString())
            }
            SettingsCard(
                title = "XOrA · Netplay",
                iconRes = DsR.drawable.xmb_figma_network,
                modifier = Modifier.animateItem(),
            ) {
                Text(
                    text = "Host or join from the in-game side menu (Pause → Netplay). " +
                        "Home consoles (NES, SNES, N64, Genesis, PS1, GameCube, …) share one " +
                        "game on the host — players 2–4 watch a compressed picture of that " +
                        "screen. Handhelds each run their own game. Online uses a 6-character " +
                        "XOrA Network code. Local Wireless uses an IP and port on the same Wi‑Fi. " +
                        "Nickname is shared with cores (DS MAC, PPSSPP MAC, Libretro username).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Online home consoles and handhelds are fine on decent home Wi‑Fi. " +
                        "About 3 Mbps host upload and joiner download is enough. 8+ Mbps " +
                        "matches Local Wireless. Same-Wi‑Fi Local Wireless still has less delay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Enable netplay", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = xora.netplayEnabled,
                        onCheckedChange = viewModel::setXoraNetplayEnabled,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (xora.netplayUseRelay) "Online" else "Local Wireless",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = xora.netplayUseRelay,
                        onCheckedChange = viewModel::setXoraNetplayUseRelay,
                        enabled = xora.netplayEnabled,
                    )
                }
                SettingsFieldLabel("Nickname")
                OutlinedTextField(
                    value = nickDraft,
                    onValueChange = { nickDraft = it.take(24) },
                    singleLine = true,
                    enabled = xora.netplayEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) {
                                viewModel.setXoraNetplayNickname(nickDraft)
                            }
                        },
                )
                SettingsFieldLabel("Listen port")
                OutlinedTextField(
                    value = portDraft,
                    onValueChange = { raw ->
                        portDraft = raw.filter { it.isDigit() }.take(5)
                    },
                    singleLine = true,
                    enabled = xora.netplayEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) {
                                portDraft.toIntOrNull()?.let(viewModel::setXoraNetplayPort)
                            }
                        },
                )
                SettingsFieldLabel("Default join address")
                OutlinedTextField(
                    value = hostDraft,
                    onValueChange = { hostDraft = it.take(128) },
                    singleLine = true,
                    enabled = xora.netplayEnabled,
                    placeholder = { Text("192.168.1.10 or 192.168.1.10:55435") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) {
                                viewModel.setXoraNetplayHostAddress(hostDraft)
                            }
                        },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Spectator when joining", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = xora.netplaySpectator,
                        onCheckedChange = viewModel::setXoraNetplaySpectator,
                        enabled = xora.netplayEnabled,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Nintendo DS · Nintendo WFC",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "melonDS talks to fan-run Nintendo WFC servers (Kaeru, Wiimmfi). " +
                        "Open Nintendo Wi-Fi Connection inside the game — Mario Kart DS " +
                        "matchmaking is that menu, not XOrA Host/Join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NdsWfcServer.entries.forEach { server ->
                        FilterChip(
                            selected = xora.ndsWfcServer == server,
                            onClick = { viewModel.setXoraNdsWfcServer(server) },
                            label = { Text(text = server.label()) },
                        )
                    }
                }
                if (xora.ndsWfcServer == NdsWfcServer.Custom) {
                    var dnsDraft by remember(xora.ndsWfcCustomDns) {
                        mutableStateOf(xora.ndsWfcCustomDns)
                    }
                    SettingsFieldLabel("Custom WFC DNS")
                    OutlinedTextField(
                        value = dnsDraft,
                        onValueChange = { dnsDraft = it.take(64) },
                        singleLine = true,
                        placeholder = { Text("178.62.43.212") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                if (!focus.isFocused) {
                                    viewModel.setXoraNdsWfcCustomDns(dnsDraft)
                                }
                            },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "PSP · PPSSPP AdHoc",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Each PSP runs its own PPSSPP. WLAN and Pro AdHoc are how games " +
                        "see other players. Hosting an XOrA session makes this device the " +
                        "AdHoc server; joiners use the host IP (Default join address / " +
                        "the host's advertised LAN IP).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Enable PPSSPP WLAN / AdHoc", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = xora.pspAdhocEnabled,
                        onCheckedChange = viewModel::setXoraPspAdhocEnabled,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "This device is the AdHoc server",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Turn on for one phone when you are not using XOrA Host/Join.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = xora.pspAdhocIsServer,
                        onCheckedChange = viewModel::setXoraPspAdhocIsServer,
                        enabled = xora.pspAdhocEnabled,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "3DS · Azahar rooms",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Azahar/Citra rooms are ENet Direct Connect IPs from the community " +
                        "registry. Blank uses ${AzaharPublicLobbies.COMMUNITY_AZAHAR_API}. " +
                        "XOrA Host/Join is on Netplay — it is not an Azahar room. Sitting in " +
                        "a listed room still needs standalone Azahar. Mario Kart 7 open is " +
                        "pinned at the top of Public lobbies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var lobbyDraft by remember(xora.azaharLobbyApiUrl) {
                    mutableStateOf(xora.azaharLobbyApiUrl)
                }
                SettingsFieldLabel("Public lobby API")
                OutlinedTextField(
                    value = lobbyDraft,
                    onValueChange = { lobbyDraft = it.take(256) },
                    singleLine = true,
                    placeholder = { Text(AzaharPublicLobbies.COMMUNITY_AZAHAR_API) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) {
                                viewModel.setXoraAzaharLobbyApiUrl(lobbyDraft)
                            }
                        },
                )
                Text(
                    text = "GET {url}/lobby. Community registry is " +
                        "${AzaharPublicLobbies.COMMUNITY_AZAHAR_API}. Room rows show ip:port.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "3DS · Cart dumps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Azahar will not launch encrypted games (standalone or in XOrA). " +
                        "Use a decrypted .cci — a decrypted .3ds of the same CCI image also " +
                        "works. Encrypted 1:1 dumps fail. Homebrew .3dsx is fine. XOrA " +
                        "cannot decrypt carts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "3DS · Pretendo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pretendo cannot run in XOrA Emulator. DS Kaeru works here " +
                        "because melonDS libretro has a WFC DNS option. 3DS Pretendo is " +
                        "Nimbus (Home Menu CIA) plus a dumped NAND, plus standalone Azahar's " +
                        "LLE online modules and 3GX plugin loader — Azahar libretro never " +
                        "reads those settings. Play Pretendo titles in standalone Azahar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.platformChoices.isEmpty()) {
            item(key = "emulators_empty") {
                Text(
                    text = "Scan a library first and the systems you own will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(items = state.platformChoices, key = { it.summary.platform.id }) { choice ->
            PlatformPlayerCard(
                choice = choice,
                onSelect = { playerId ->
                    viewModel.selectPlayer(choice.summary.platform.id, playerId)
                },
                modifier = Modifier.animateItem(),
            )
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
    Display("Display", "Theme, layout, and how the library is presented", DsR.drawable.xmb_figma_device),
    Audio("Audio", "Soundtrack and interface sounds", DsR.drawable.xmb_figma_music),
    Media("Media", "Trailers and artwork scraping", DsR.drawable.xmb_figma_video),
    Accounts("Accounts", "RetroAchievements, Steam, and Discord", DsR.drawable.xmb_figma_network),
    Storage("Storage", "Library folders, scanning, and app sync", DsR.drawable.xmb_figma_folder),
    System("System", "Home screen role and onboarding", DsR.drawable.xmb_figma_settings),
    Emulators("Emulators", "XOrA Emulator and per-system players", DsR.drawable.xmb_figma_game),
}

@Composable
private fun SetupSectionTab(
    section: SetupSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalShellTheme.current.colors
    val shape = ArcadiaGlass.ChipShape
    Row(
        modifier = Modifier
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
            color = Color.White.copy(alpha = if (selected) 1f else 0.68f),
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
    modifier: Modifier = Modifier,
) {
    var draft by remember(value) { mutableStateOf(value) }

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
            .onFocusChanged { focus -> if (!focus.isFocused && draft != value) onCommit(draft) },
    )
}

/** Two fields that only make sense together, so they are saved as a pair. */
@Composable
private fun PairedSecretFields(
    firstLabel: String,
    secondLabel: String,
    firstValue: String,
    secondValue: String,
    onCommit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var first by remember(firstValue) { mutableStateOf(firstValue) }
    var second by remember(secondValue) { mutableStateOf(secondValue) }

    val commit = {
        if (first != firstValue || second != secondValue) onCommit(first, second)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = first,
            onValueChange = { first = it },
            label = { Text(text = firstLabel) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus -> if (!focus.isFocused) commit() },
        )
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
                .weight(1f)
                .onFocusChanged { focus -> if (!focus.isFocused) commit() },
        )
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
) {
    var username by remember(configured.username, pendingWebApiUsername) {
        mutableStateOf(pendingWebApiUsername ?: configured.username)
    }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showAdvanced by remember(pendingWebApiUsername) {
        mutableStateOf(!pendingWebApiUsername.isNullOrBlank())
    }

    if (!pendingWebApiUsername.isNullOrBlank()) {
        Text(
            text = "Password accepted for $pendingWebApiUsername. Paste your Web API key from " +
                "https://retroachievements.org/controlpanel.php (Keys).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Web API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                onApiKeySignIn(pendingWebApiUsername, apiKey)
                apiKey = ""
            },
            enabled = !isBusy && apiKey.isNotBlank(),
        ) {
            Text(if (isBusy) "Saving…" else "Save API key")
        }
        return
    }

    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
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

    TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !isBusy) {
        Text(if (showAdvanced) "Hide API key option" else "Paste Web API key instead")
    }
    if (showAdvanced) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Web API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
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

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    content: @Composable () -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = ArcadiaGlass.CardShape,
        tone = GlassTone.OverMedia,
        intensity = GlassIntensity.Standard,
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
            TextButton(onClick = onRemove) { Text(text = "Remove") }
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
                text = "No launch profile ships for this system yet.",
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
}
