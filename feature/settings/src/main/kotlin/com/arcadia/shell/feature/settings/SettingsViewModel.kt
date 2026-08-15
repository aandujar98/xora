package com.arcadia.shell.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.PlatformEmulatorChoice
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ThemeMode
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.datastore.TrailerSourcePreference
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.launcher.BuiltInPlayers
import com.arcadia.shell.launcher.InstalledPlayerProbe
import com.arcadia.shell.launcher.InstalledAppSync
import com.arcadia.shell.launcher.PlayerSeeder
import com.arcadia.shell.launcher.RetroArchCoreCatalog
import com.arcadia.shell.launcher.RetroArchPackages
import com.arcadia.shell.launcher.conversations.ConversationRepository
import com.arcadia.shell.libretro.CoreDownloader
import com.arcadia.shell.libretro.CoreStore
import com.arcadia.shell.libretro.XoraCoreCatalog
import com.arcadia.shell.libretro.XoraLibretroPlayers
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.PlatformSummary
import com.arcadia.shell.model.Player
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.ScreenRole
import com.arcadia.shell.scanner.LibraryRootManager
import com.arcadia.shell.scanner.LibraryScanner
import com.arcadia.shell.scanner.StorageAccess
import com.arcadia.shell.scraper.LibraryHashScheduler
import com.arcadia.shell.scraper.ScraperScheduler
import com.arcadia.shell.scraper.SteamOpenId
import com.arcadia.shell.retroachievements.RaPasswordLoginResult
import com.arcadia.shell.retroachievements.RetroAchievementsClient
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val storageAccess: StorageAccess,
    private val rootManager: LibraryRootManager,
    private val scanner: LibraryScanner,
    private val libraryRepository: LibraryRepository,
    private val playerRepository: PlayerRepository,
    private val probe: InstalledPlayerProbe,
    private val playerSeeder: PlayerSeeder,
    private val preferences: ShellPreferences,
    private val scraperScheduler: ScraperScheduler,
    private val libraryHashScheduler: LibraryHashScheduler,
    private val conversationRepository: ConversationRepository,
    private val discordRichPresence: com.arcadia.shell.launcher.discord.DiscordRichPresence,
    private val retroAchievements: RetroAchievementsRepository,
    private val coreDownloader: CoreDownloader,
    private val coreStore: CoreStore,
    private val xoraCatalog: XoraCoreCatalog,
    private val installedAppSync: InstalledAppSync,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val transientMessage = MutableStateFlow<String?>(null)
    private val raBusy = MutableStateFlow(false)
    private val appSyncBusy = MutableStateFlow(false)
    private val raError = MutableStateFlow<String?>(null)
    private val raPendingWebApiUser = MutableStateFlow<String?>(null)

    private val playersFlow = combine(
        playerRepository.observePlayers(),
        playerRepository.observePlatformSettings(),
        libraryRepository.observePlatformSummaries(),
        preferences.settings,
        preferences.platformEmulatorChoices,
    ) { players, platformSettings, summaries, settings, emulatorChoices ->
        val selectedByPlatform = platformSettings.associate { it.platformId to it.selectedPlayerId }
        summaries.map { summary ->
            val platformId = summary.platform.id
            val preferredId = emulatorChoices[platformId]?.playerId
                ?: selectedByPlatform[platformId]
                ?: BuiltInPlayers.RETROARCH_N64_PLAYER_ID.takeIf {
                    platformId == "n64" && settings.n64UseMupen64PlusNext
                }
            platformChoice(
                summary = summary,
                players = players,
                preferredPlayerId = preferredId,
            )
        }
    }

    private val storageFlow = combine(
        rootManager.observeRoots(),
        refreshTrigger,
    ) { roots, _ ->
        StorageState(
            hasAccess = storageAccess.hasAllFilesAccess,
            roots = roots,
            suggestedVolumes = rootManager.suggestedRoots(),
        )
    }

    private data class StorageState(
        val hasAccess: Boolean,
        val roots: List<LibraryRoot>,
        val suggestedVolumes: List<com.arcadia.shell.scanner.StorageVolumeRoot>,
    )

    private data class ConfigState(
        val settings: com.arcadia.shell.datastore.ShellSettings,
        val credentials: com.arcadia.shell.datastore.ScraperCredentials,
        val retroAchievements: com.arcadia.shell.datastore.RetroAchievementsCredentials,
        val steamWebApi: com.arcadia.shell.datastore.SteamWebApiCredentials,
        val discordSocial: com.arcadia.shell.datastore.DiscordSocialSettings,
        val discordPresence: com.arcadia.shell.launcher.discord.DiscordPresenceUiState,
        val message: String?,
        val isScraping: Boolean,
        val isHashingRoms: Boolean,
    )

    private val credentialsFlow = combine(
        preferences.credentials,
        preferences.retroAchievements,
        preferences.steamWebApi,
        preferences.discordSocial,
    ) { scraper, ra, steam, discord ->
        CredentialBundle(scraper, ra, steam, discord)
    }

    private data class CredentialBundle(
        val scraper: com.arcadia.shell.datastore.ScraperCredentials,
        val ra: com.arcadia.shell.datastore.RetroAchievementsCredentials,
        val steam: com.arcadia.shell.datastore.SteamWebApiCredentials,
        val discord: com.arcadia.shell.datastore.DiscordSocialSettings,
    )

    private val backgroundJobsFlow = combine(
        scraperScheduler.isRunning(),
        libraryHashScheduler.isRunning(),
    ) { scraping, hashing -> scraping to hashing }

    private val configFlow = combine(
        preferences.settings,
        credentialsFlow,
        discordRichPresence.state,
        transientMessage,
        backgroundJobsFlow,
    ) { settings, creds, discordPresence, message, jobs ->
        ConfigState(
            settings = settings,
            credentials = creds.scraper,
            retroAchievements = creds.ra,
            steamWebApi = creds.steam,
            discordSocial = creds.discord,
            discordPresence = discordPresence,
            message = message,
            isScraping = jobs.first,
            isHashingRoms = jobs.second,
        )
    }

    private val baseUiState: StateFlow<SettingsUiState> = combine(
        storageFlow,
        playersFlow,
        configFlow,
        scanner.progress,
        libraryRepository.observeGames(),
    ) { storage, choices, config, progress, games ->
        SettingsUiState(
            hasStorageAccess = storage.hasAccess,
            roots = storage.roots,
            suggestedVolumes = storage.suggestedVolumes,
            gameCount = games.count { !it.isAndroidApp },
            androidAppCount = games.count { it.isAndroidApp },
            scanProgress = progress,
            platformChoices = choices,
            settings = config.settings,
            credentials = config.credentials,
            retroAchievements = config.retroAchievements,
            steamWebApi = config.steamWebApi,
            discordSocial = config.discordSocial,
            discordPresence = config.discordPresence,
            notificationListenerEnabled = conversationRepository.isNotificationListenerEnabled(),
            isScraping = config.isScraping,
            isHashingRoms = config.isHashingRoms,
            missingRomHashes = games.count { !it.isAndroidApp && it.md5.isNullOrBlank() },
            message = config.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val xoraCoresFlow = coreStore.installedCoreNames.map { installed ->
        xoraCatalog.all.map { entry ->
            val platform = PlatformCatalog.byId(entry.platformId)
            XoraCoreInstallRow(
                platformId = entry.platformId,
                platformLabel = platform?.shortName ?: entry.platformId.uppercase(),
                core = entry.core,
                label = entry.label,
                installed = entry.core in installed,
            )
        }
    }

    private val raUiFlow = combine(
        raBusy,
        raError,
        raPendingWebApiUser,
    ) { busy, error, pending ->
        Triple(busy, error, pending)
    }

    private val xoraStatusFlow = combine(
        xoraCoresFlow,
        coreDownloader.downloadProgress,
        preferences.xoraEmulatorSettings,
        preferences.retroAchievementsSettings,
    ) { cores, download, xoraPrefs, raPrefs ->
        XoraStatusBundle(cores, download, xoraPrefs, raPrefs)
    }

    private data class XoraStatusBundle(
        val cores: List<XoraCoreInstallRow>,
        val download: com.arcadia.shell.libretro.CoreDownloadProgress,
        val xoraPrefs: com.arcadia.shell.datastore.XoraEmulatorSettings,
        val raPrefs: com.arcadia.shell.datastore.RetroAchievementsSettings,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        baseUiState,
        raUiFlow,
        xoraStatusFlow,
        appSyncBusy,
    ) { base, ra, xora, syncingApps ->
        val (busy, error, pending) = ra
        val uniqueCores = xora.cores.distinctBy { it.core }
        base.copy(
            isSyncingApps = syncingApps,
            raAuthBusy = busy,
            raAuthError = error,
            raPendingWebApiUsername = pending,
            xoraEmulator = xora.xoraPrefs,
            raSettings = xora.raPrefs,
            xoraCores = xora.cores,
            xoraCoresInstalled = uniqueCores.count { it.installed },
            xoraCoresTotal = uniqueCores.size,
            xoraDownloadRunning = xora.download.running,
            xoraDownloadMessage = xora.download.message,
            xoraDownloadError = xora.download.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun platformChoice(
        summary: PlatformSummary,
        players: List<Player>,
        preferredPlayerId: String?,
    ): PlatformPlayerChoice {
        val candidates = players.filter { summary.platform.id in it.platformIds }
        // Mirrors GameLauncher: explicit choice, else first installed candidate.
        val effective = candidates.firstOrNull { it.uniqueId == preferredPlayerId }
            ?: probe.installedPlayers(candidates).firstOrNull()

        return PlatformPlayerChoice(
            summary = summary,
            candidates = candidates,
            selectedPlayerId = preferredPlayerId,
            effectivePlayer = effective,
            isInstalled = effective?.let { probe.isInstalled(it) } == true,
        )
    }

    fun allFilesAccessIntent(): Intent = storageAccess.allFilesAccessIntent()

    fun openDocumentTreeIntent(): Intent = storageAccess.openDocumentTreeIntent()

    fun notificationListenerSettingsIntent(): Intent =
        conversationRepository.notificationListenerSettingsIntent()

    /** Re-reads permission state, which cannot be observed and only changes outside the app. */
    fun refresh() {
        conversationRepository.refreshListenerEnabled()
        refreshTrigger.value += 1
    }

    fun addFilesystemRoot(path: String) {
        viewModelScope.launch {
            rootManager.addFilesystemRoot(path)
                .onSuccess {
                    transientMessage.value = "Added ${it.label}"
                    scanNow()
                }
                .onFailure { transientMessage.value = it.message }
            refresh()
        }
    }

    fun addSafRoot(treeUri: Uri) {
        viewModelScope.launch {
            rootManager.addSafRoot(treeUri)
                .onSuccess { root ->
                    transientMessage.value = when (root.kind) {
                        RootKind.Filesystem ->
                            "Added ${root.label} as a filesystem folder (shared with XOrA Emulator)."
                        RootKind.SafTree ->
                            "Added ${root.label}. Grant all-files access so XOrA Emulator can open these games."
                    }
                    scanNow()
                }
                .onFailure { transientMessage.value = it.message }
            refresh()
        }
    }

    fun removeRoot(root: LibraryRoot) {
        viewModelScope.launch {
            rootManager.remove(root)
            transientMessage.value = "Removed ${root.label}"
            refresh()
        }
    }

    fun setAndroidAppSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAndroidAppSyncEnabled(enabled)
            // Apply straight away: turning it off prunes the mirrored rows.
            runCatching { installedAppSync.refresh() }
            transientMessage.value = if (enabled) {
                "Installed apps will appear on the Apps tab."
            } else {
                "Installed apps removed from the library."
            }
        }
    }

    fun syncAndroidAppsNow() {
        if (appSyncBusy.value) return
        viewModelScope.launch {
            appSyncBusy.value = true
            val result = runCatching { installedAppSync.refresh() }
            appSyncBusy.value = false
            transientMessage.value = result.fold(
                onSuccess = { "Installed apps synced." },
                onFailure = { it.message ?: "Could not sync installed apps." },
            )
        }
    }

    fun scanNow() {
        viewModelScope.launch {
            val progress = scanner.scan()
            transientMessage.value = progress.error
                ?: "Scanned ${progress.filesSeen} files and found ${progress.gamesFound} games."

            if (progress.error == null) {
                // Always hash new ROMs so launcher RetroAchievements can identify them.
                libraryHashScheduler.enqueue(rehashAll = false, replace = false)
                // Newly indexed games start out with no artwork, so a scan is the natural moment to
                // queue a scrape rather than making the user ask for it separately.
                if (preferences.settings.first().scrapeAfterScan) {
                    scraperScheduler.enqueue()
                }
            }
        }
    }

    fun scrapeNow() {
        if (!uiState.value.credentials.hasAny) {
            transientMessage.value = "Add at least one artwork source below first."
            return
        }
        scraperScheduler.enqueue()
        transientMessage.value = "Fetching artwork in the background."
    }

    /**
     * Recomputes RetroAchievements MD5s for every ROM on every hashable platform so the launcher
     * matches XOrA Emulator identification.
     */
    fun hashAllRoms() {
        libraryHashScheduler.enqueue(rehashAll = true, replace = true)
        transientMessage.value =
            "Hashing all ROMs for RetroAchievements in the background…"
    }

    fun cancelScrape() {
        scraperScheduler.cancel()
    }

    fun setScrapeAfterScan(enabled: Boolean) {
        viewModelScope.launch { preferences.setScrapeAfterScan(enabled) }
    }

    fun setManualScrapeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setManualScrapeEnabled(enabled) }
    }

    fun setScreenScraperDevCredentials(devId: String, devPassword: String) {
        viewModelScope.launch { preferences.setScreenScraperDevCredentials(devId, devPassword) }
    }

    fun setIgdbCredentials(clientId: String, clientSecret: String) {
        viewModelScope.launch { preferences.setIgdbCredentials(clientId, clientSecret) }
    }

    /**
     * Re-syncs bundled launch recipes and reports how many emulators / RetroArch cores are present.
     * Sideloaded apps like Cemu then appear in Choose Emulator without restarting SORA.
     */
    fun scanEmulators() {
        viewModelScope.launch {
            val result = runCatching { playerSeeder.scanInstalled() }.getOrElse {
                transientMessage.value = it.message ?: "Emulator scan failed."
                return@launch
            }
            transientMessage.value = buildString {
                append("Found ${result.installedStandalone} emulator")
                if (result.installedStandalone != 1) append('s')
                append(" · ${result.installedXoraCores} XOrA core")
                if (result.installedXoraCores != 1) append('s')
                if (result.retroArchInstalled) {
                    append(" · RetroArch with ${result.installedCores} core")
                    if (result.installedCores != 1) append('s')
                    append(" on storage")
                } else {
                    append(" · RetroArch not installed")
                }
            }
        }
    }

    fun downloadXoraCores() {
        viewModelScope.launch {
            transientMessage.value = "Downloading XOrA Libretro cores…"
            runCatching { coreDownloader.downloadMissingPrimaries() }
                .onSuccess {
                    coreStore.refreshInstalled()
                    val installed = coreStore.installedCoreNames.value.size
                    val total = xoraCatalog.all.map { it.core }.toSet().size
                    transientMessage.value =
                        if (installed >= total) {
                            "XOrA cores ready — all $installed installed"
                        } else {
                            "XOrA cores: $installed of $total installed (primaries downloaded)"
                        }
                }
                .onFailure {
                    transientMessage.value = it.message ?: "Core download failed"
                }
        }
    }

    fun setXoraNdsScreenLayout(layout: com.arcadia.shell.datastore.DualScreenLayout) {
        viewModelScope.launch { preferences.setXoraNdsScreenLayout(layout) }
    }

    fun setXoraNdsScreenGap(gap: Int) {
        viewModelScope.launch { preferences.setXoraNdsScreenGap(gap) }
    }

    fun setXora3dsScreenLayout(layout: com.arcadia.shell.datastore.ThreeDsScreenLayout) {
        viewModelScope.launch { preferences.setXora3dsScreenLayout(layout) }
    }

    fun setXoraExpandDualDisplay(enabled: Boolean) {
        viewModelScope.launch { preferences.setXoraExpandDualDisplay(enabled) }
    }

    fun setXoraAspectMode(mode: com.arcadia.shell.datastore.XoraAspectMode) {
        viewModelScope.launch { preferences.setXoraAspectMode(mode) }
    }

    fun setXoraIntegerScale(scale: Int) {
        viewModelScope.launch { preferences.setXoraIntegerScale(scale) }
    }

    fun setXoraInternalResolution(resolution: com.arcadia.shell.datastore.XoraInternalResolution) {
        viewModelScope.launch { preferences.setXoraInternalResolution(resolution) }
    }

    fun setXoraBezelsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setXoraBezelsEnabled(enabled) }
    }

    fun setXoraBezelOpacity(opacity: Float) {
        viewModelScope.launch { preferences.setXoraBezelOpacity(opacity) }
    }

    fun setXoraAudioVolume(volume: Float) {
        viewModelScope.launch { preferences.setXoraAudioVolume(volume) }
    }

    fun setXoraNetplayEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setXoraNetplayEnabled(enabled) }
    }

    fun setXoraNetplayNickname(nickname: String) {
        viewModelScope.launch { preferences.setXoraNetplayNickname(nickname) }
    }

    fun setXoraNetplayPort(port: Int) {
        viewModelScope.launch { preferences.setXoraNetplayPort(port) }
    }

    fun setXoraNetplaySpectator(enabled: Boolean) {
        viewModelScope.launch { preferences.setXoraNetplaySpectator(enabled) }
    }

    fun setXoraNetplayUseRelay(enabled: Boolean) {
        viewModelScope.launch { preferences.setXoraNetplayUseRelay(enabled) }
    }

    fun setXoraNetplayHostAddress(address: String) {
        viewModelScope.launch { preferences.setXoraNetplayHostAddress(address) }
    }

    fun setRaEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setRaEnabled(enabled) }
    }

    fun setRaHardcore(enabled: Boolean) {
        viewModelScope.launch { preferences.setRaHardcore(enabled) }
    }

    fun setRaUnlockNotifications(enabled: Boolean) {
        viewModelScope.launch { preferences.setRaUnlockNotifications(enabled) }
    }

    fun setRaShowInLauncher(enabled: Boolean) {
        viewModelScope.launch { preferences.setRaShowInLauncher(enabled) }
    }

    fun setRaRichPresence(enabled: Boolean) {
        viewModelScope.launch { preferences.setRaRichPresence(enabled) }
    }

    fun selectPlayer(platformId: String, playerId: String?) {
        viewModelScope.launch {
            playerRepository.selectPlayerForPlatform(platformId, playerId)
            if (playerId == null) {
                preferences.setPlatformEmulatorChoice(platformId, null)
            } else {
                val player = playerRepository.findById(playerId)
                val core = player?.let { XoraLibretroPlayers.coreNameFromPlayer(it) }
                    ?: RetroArchCoreCatalog.byPlayerId(playerId)?.core
                    ?: player?.let { RetroArchPackages.coreNameFromPlayer(it) }
                preferences.setPlatformEmulatorChoice(
                    platformId,
                    PlatformEmulatorChoice(
                        playerId = playerId,
                        packageName = player?.packageName
                            ?: XoraLibretroPlayers.PACKAGE.takeIf {
                                XoraLibretroPlayers.isXoraPlayerId(playerId)
                            },
                        coreName = core,
                    ),
                )
            }
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { preferences.setGridColumns(columns) }
    }

    fun setSecondaryDisplayRole(role: ScreenRole) {
        viewModelScope.launch { preferences.setSecondaryDisplayRole(role) }
    }

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch { preferences.setDisplayMode(mode) }
    }

    fun setBgmVolume(volume: Float) {
        viewModelScope.launch { preferences.setBgmVolume(volume) }
    }

    fun setMusicLibraryPath(path: String?) {
        viewModelScope.launch { preferences.setMusicLibraryPath(path) }
    }

    fun setUiSfxVolume(volume: Float) {
        viewModelScope.launch { preferences.setUiSfxVolume(volume) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setXmbTitleStyle(style: XmbTitleStyle) {
        viewModelScope.launch { preferences.setXmbTitleStyle(style) }
    }

    fun setTrailerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTrailerEnabled(enabled) }
    }

    fun setTrailerScrapeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setTrailerScrapeEnabled(enabled)
            if (enabled) {
                // Let idle / next scrape retry games that previously found nothing.
                libraryRepository.clearNullTrailerResolutions()
            }
        }
    }

    fun setTrailerSourcePreference(preference: TrailerSourcePreference) {
        viewModelScope.launch {
            preferences.setTrailerSourcePreference(preference)
            libraryRepository.clearNullTrailerResolutions()
        }
    }

    fun setTrailerDisplayMode(mode: TrailerDisplayMode) {
        viewModelScope.launch { preferences.setTrailerDisplayMode(mode) }
    }

    fun setScreenScraperCredentials(user: String, password: String) {
        viewModelScope.launch { preferences.setScreenScraperCredentials(user, password) }
    }

    fun setSteamGridDbKey(key: String) {
        viewModelScope.launch { preferences.setSteamGridDbKey(key) }
    }

    fun setRetroAchievementsCredentials(username: String, apiKey: String) {
        viewModelScope.launch {
            raBusy.value = true
            raError.value = null
            val result = retroAchievements.saveCredentials(username, apiKey)
            raBusy.value = false
            result.fold(
                onSuccess = {
                    raPendingWebApiUser.value = null
                    transientMessage.value = "Signed in to RetroAchievements as ${it.username}."
                },
                onFailure = { error ->
                    raError.value = RetroAchievementsClient.sanitizeErrorMessage(
                        error.message ?: "Invalid RetroAchievements credentials.",
                    )
                },
            )
        }
    }

    fun loginRetroAchievements(username: String, password: String) {
        viewModelScope.launch {
            raBusy.value = true
            raError.value = null
            raPendingWebApiUser.value = null
            val result = retroAchievements.loginWithPassword(username, password)
            raBusy.value = false
            result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is RaPasswordLoginResult.SignedIn -> {
                            raPendingWebApiUser.value = null
                            transientMessage.value =
                                "Signed in to RetroAchievements as ${outcome.profile.username}."
                        }
                        is RaPasswordLoginResult.NeedsWebApiKey -> {
                            raPendingWebApiUser.value = outcome.username
                            transientMessage.value =
                                "Password accepted (emulator ready). Paste your Web API key " +
                                    "from the RA control panel for launcher features."
                        }
                    }
                },
                onFailure = { error ->
                    raError.value = RetroAchievementsClient.sanitizeErrorMessage(
                        error.message ?: "Invalid RetroAchievements credentials.",
                    )
                },
            )
        }
    }

    fun clearRetroAchievementsCredentials() {
        viewModelScope.launch {
            retroAchievements.clearCredentials()
            raPendingWebApiUser.value = null
            raError.value = null
        }
    }

    fun setSteamWebApiCredentials(apiKey: String, steamId64: String) {
        viewModelScope.launch {
            preferences.setSteamWebApiCredentials(apiKey, steamId64)
        }
    }

    fun setSteamWebApiKey(apiKey: String) {
        viewModelScope.launch { preferences.setSteamWebApiKey(apiKey) }
    }

    fun setSteamId64(steamId64: String) {
        viewModelScope.launch { preferences.setSteamId64(steamId64) }
    }

    fun clearSteamWebApiCredentials() {
        viewModelScope.launch { preferences.clearSteamWebApiCredentials() }
    }

    fun steamOpenIdAuthorizationUrl(): String = SteamOpenId.authorizationUrl()

    fun applySteamOpenIdReturn(uri: Uri): Boolean {
        if (!SteamOpenId.isReturnUri(uri)) return false
        val steamId = SteamOpenId.steamId64FromReturnUri(uri) ?: return false
        viewModelScope.launch {
            preferences.setSteamId64(steamId)
            transientMessage.value = "Steam ID saved. Paste a Web API key below if needed."
        }
        return true
    }

    fun setDiscordOpenUrl(url: String) {
        viewModelScope.launch { preferences.setDiscordOpenUrl(url) }
    }

    fun clearDiscordOpenUrl() {
        viewModelScope.launch { preferences.clearDiscordOpenUrl() }
    }

    fun setDiscordApplicationId(applicationId: String) {
        viewModelScope.launch { preferences.setDiscordApplicationId(applicationId) }
    }

    fun clearDiscordApplicationId() {
        viewModelScope.launch { preferences.clearDiscordApplicationId() }
    }

    fun openDiscordDeveloperPortalIntent(): Intent =
        discordRichPresence.openDeveloperPortalIntent()

    fun listDirectories(path: String): List<java.io.File> =
        runCatching {
            java.io.File(path).listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }.getOrDefault(emptyList())

    fun consumeMessage() {
        transientMessage.value = null
    }
}
