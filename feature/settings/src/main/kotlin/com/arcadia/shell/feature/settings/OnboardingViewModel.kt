package com.arcadia.shell.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.PlatformEmulatorChoice
import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.SteamWebApiCredentials
import com.arcadia.shell.launcher.InstalledPlayerProbe
import com.arcadia.shell.launcher.PlayerSeeder
import com.arcadia.shell.launcher.RetroArchCoreCatalog
import com.arcadia.shell.launcher.RetroArchPackages
import com.arcadia.shell.launcher.conversations.ConversationRepository
import com.arcadia.shell.launcher.discord.DiscordPresenceUiState
import com.arcadia.shell.launcher.discord.DiscordRichPresence
import com.arcadia.shell.libretro.XoraLibretroPlayers
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.retroachievements.RaPasswordLoginResult
import com.arcadia.shell.retroachievements.RetroAchievementsClient
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scanner.LibraryRootManager
import com.arcadia.shell.scanner.LibraryScanner
import com.arcadia.shell.scanner.StorageAccess
import com.arcadia.shell.scanner.StorageVolumeRoot
import com.arcadia.shell.scraper.LibraryHashScheduler
import com.arcadia.shell.scraper.SteamOpenId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class OnboardingStep {
    Welcome,
    DisplayMode,
    Library,
    Emulators,
    Scrapers,
    Social,
    RetroAchievements,
    Audio,
    Done,
}

/** Activity-scoped auth that ArcadiaShell must hoist (Custom Tabs / Discord OAuth). */
sealed interface OnboardingExternalAuthRequest {
    data object SteamOpenId : OnboardingExternalAuthRequest
    data object LinkDiscord : OnboardingExternalAuthRequest
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val settings: ShellSettings = ShellSettings(),
    val hasStorageAccess: Boolean = false,
    val roots: List<LibraryRoot> = emptyList(),
    val suggestedVolumes: List<StorageVolumeRoot> = emptyList(),
    val gameCount: Int = 0,
    val scanRunning: Boolean = false,
    val scanCompleted: Boolean = false,
    val scanError: String? = null,
    val filesSeen: Int = 0,
    val platformChoices: List<PlatformPlayerChoice> = emptyList(),
    val notificationListenerEnabled: Boolean = false,
    val retroAchievements: RetroAchievementsCredentials = RetroAchievementsCredentials(),
    val raAuthBusy: Boolean = false,
    val raAuthError: String? = null,
    val raPendingWebApiUsername: String? = null,
    val steamWebApi: SteamWebApiCredentials = SteamWebApiCredentials(),
    val discordPresence: DiscordPresenceUiState = DiscordPresenceUiState(),
    val message: String? = null,
) {
    val stepIndex: Int get() = OnboardingStep.entries.indexOf(step)
    val stepCount: Int get() = OnboardingStep.entries.size
    val canGoBack: Boolean get() = stepIndex > 0
    val isLast: Boolean get() = step == OnboardingStep.Done
    val canAdvance: Boolean get() = step != OnboardingStep.Emulators || !scanRunning
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: ShellPreferences,
    private val storageAccess: StorageAccess,
    private val rootManager: LibraryRootManager,
    private val libraryRepository: LibraryRepository,
    private val scanner: LibraryScanner,
    private val playerRepository: PlayerRepository,
    private val probe: InstalledPlayerProbe,
    private val playerSeeder: PlayerSeeder,
    private val libraryHashScheduler: LibraryHashScheduler,
    private val conversationRepository: ConversationRepository,
    private val retroAchievements: RetroAchievementsRepository,
    private val discordRichPresence: DiscordRichPresence,
) : ViewModel() {

    private val step = MutableStateFlow(OnboardingStep.Welcome)
    private val refreshTrigger = MutableStateFlow(0)
    private val message = MutableStateFlow<String?>(null)
    private val raBusy = MutableStateFlow(false)
    private val raError = MutableStateFlow<String?>(null)
    private val raPendingWebApiUser = MutableStateFlow<String?>(null)
    private val scanRunning = MutableStateFlow(false)
    private val scanCompleted = MutableStateFlow(false)
    private val scanError = MutableStateFlow<String?>(null)
    private val filesSeen = MutableStateFlow(0)
    private val scanMutex = Mutex()

    private val externalAuthRequests = Channel<OnboardingExternalAuthRequest>(Channel.BUFFERED)
    val externalAuthRequestFlow: Flow<OnboardingExternalAuthRequest> =
        externalAuthRequests.receiveAsFlow()

    private val storageFlow = combine(
        rootManager.observeRoots(),
        refreshTrigger,
    ) { roots, _ ->
        Triple(
            storageAccess.hasAllFilesAccess,
            roots,
            rootManager.suggestedRoots(),
        )
    }

    private val raAuthFlow = combine(raBusy, raError, raPendingWebApiUser) { busy, error, pending ->
        Triple(busy, error, pending)
    }

    private val socialFlow = combine(
        preferences.retroAchievements,
        preferences.steamWebApi,
        discordRichPresence.state,
        raAuthFlow,
    ) { ra, steam, discord, raAuth ->
        SocialBundle(
            retroAchievements = ra,
            steamWebApi = steam,
            discordPresence = discord,
            raBusy = raAuth.first,
            raError = raAuth.second,
            raPendingWebApiUser = raAuth.third,
        )
    }

    private data class SocialBundle(
        val retroAchievements: RetroAchievementsCredentials,
        val steamWebApi: SteamWebApiCredentials,
        val discordPresence: DiscordPresenceUiState,
        val raBusy: Boolean,
        val raError: String?,
        val raPendingWebApiUser: String?,
    )

    private val baseFlow = combine(
        step,
        preferences.settings,
        storageFlow,
        libraryRepository.observeGames(),
        message,
    ) { currentStep, settings, storage, games, msg ->
        BaseBundle(
            step = currentStep,
            settings = settings,
            hasStorageAccess = storage.first,
            roots = storage.second,
            suggestedVolumes = storage.third,
            gameCount = games.size,
            message = msg,
        )
    }

    private data class BaseBundle(
        val step: OnboardingStep,
        val settings: ShellSettings,
        val hasStorageAccess: Boolean,
        val roots: List<LibraryRoot>,
        val suggestedVolumes: List<StorageVolumeRoot>,
        val gameCount: Int,
        val message: String?,
    )

    private val scanFlow = combine(scanRunning, scanCompleted, scanError, filesSeen) {
            running, done, error, seen ->
        ScanBundle(running, done, error, seen)
    }

    private data class ScanBundle(
        val running: Boolean,
        val completed: Boolean,
        val error: String?,
        val filesSeen: Int,
    )

    private val emulatorFlow = combine(
        libraryRepository.observePlatformSummaries(),
        playerRepository.observePlayers(),
        preferences.platformEmulatorChoices,
        scanFlow,
    ) { summaries, players, choices, scan ->
        EmulatorBundle(
            platformChoices = summaries.map { summary ->
                buildPlatformPlayerChoice(
                    summary = summary,
                    players = players,
                    preferredPlayerId = choices[summary.platform.id]?.playerId,
                    probe = probe,
                )
            },
            scan = scan,
        )
    }

    private data class EmulatorBundle(
        val platformChoices: List<PlatformPlayerChoice>,
        val scan: ScanBundle,
    )

    val uiState: StateFlow<OnboardingUiState> = combine(
        baseFlow,
        socialFlow,
        emulatorFlow,
    ) { base, social, emulators ->
        OnboardingUiState(
            step = base.step,
            settings = base.settings,
            hasStorageAccess = base.hasStorageAccess,
            roots = base.roots,
            suggestedVolumes = base.suggestedVolumes,
            gameCount = base.gameCount,
            scanRunning = emulators.scan.running,
            scanCompleted = emulators.scan.completed,
            scanError = emulators.scan.error,
            filesSeen = emulators.scan.filesSeen,
            platformChoices = emulators.platformChoices,
            notificationListenerEnabled = conversationRepository.isNotificationListenerEnabled(),
            retroAchievements = social.retroAchievements,
            raAuthBusy = social.raBusy,
            raAuthError = social.raError,
            raPendingWebApiUsername = social.raPendingWebApiUser,
            steamWebApi = social.steamWebApi,
            discordPresence = social.discordPresence,
            message = base.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun refresh() {
        conversationRepository.refreshListenerEnabled()
        refreshTrigger.value += 1
    }

    fun next() {
        if (step.value == OnboardingStep.Emulators && scanRunning.value) return
        val entries = OnboardingStep.entries
        val index = entries.indexOf(step.value)
        if (index < entries.lastIndex) {
            step.value = entries[index + 1]
        }
        if (step.value == OnboardingStep.Emulators) {
            ensureLibraryScanned()
        }
    }

    fun back() {
        val entries = OnboardingStep.entries
        val index = entries.indexOf(step.value)
        if (index > 0) {
            step.value = entries[index - 1]
        }
    }

    fun skipOptional() {
        when (step.value) {
            OnboardingStep.Scrapers,
            OnboardingStep.Social,
            OnboardingStep.RetroAchievements,
            -> next()
            else -> next()
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch { preferences.setDisplayMode(mode) }
    }

    fun setBgmVolume(volume: Float) {
        viewModelScope.launch { preferences.setBgmVolume(volume) }
    }

    fun setUiSfxVolume(volume: Float) {
        viewModelScope.launch { preferences.setUiSfxVolume(volume) }
    }

    fun allFilesAccessIntent(): Intent = storageAccess.allFilesAccessIntent()

    fun openDocumentTreeIntent(): Intent = storageAccess.openDocumentTreeIntent()

    fun notificationListenerSettingsIntent(): Intent =
        conversationRepository.notificationListenerSettingsIntent()

    fun addFilesystemRoot(path: String) {
        viewModelScope.launch {
            rootManager.addFilesystemRoot(path)
                .onSuccess {
                    message.value = "Added ${it.label}"
                    scanLibrary(force = true)
                }
                .onFailure { message.value = it.message }
            refresh()
        }
    }

    fun addSafRoot(treeUri: Uri) {
        viewModelScope.launch {
            rootManager.addSafRoot(treeUri)
                .onSuccess {
                    message.value = "Added ${it.label}"
                    scanLibrary(force = true)
                }
                .onFailure { message.value = it.message }
            refresh()
        }
    }

    fun ensureLibraryScanned() = scanLibrary(force = false)

    fun retryLibraryScan() = scanLibrary(force = true)

    private fun scanLibrary(force: Boolean) {
        viewModelScope.launch {
            scanMutex.withLock {
                if (scanCompleted.value && !force) return@withLock
                scanRunning.value = true
                scanError.value = null
                runCatching { playerSeeder.scanInstalled() }
                val progressJob = launch {
                    scanner.progress.collect { filesSeen.value = it.filesSeen }
                }
                val progress = scanner.scan()
                progressJob.cancel()
                filesSeen.value = progress.filesSeen
                scanError.value = progress.error
                if (progress.error == null) {
                    libraryHashScheduler.enqueue(rehashAll = false, replace = false)
                }
                scanCompleted.value = true
                scanRunning.value = false
            }
        }
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

    fun listDirectories(path: String): List<java.io.File> =
        runCatching {
            java.io.File(path).listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }.getOrDefault(emptyList())

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
                            message.value =
                                "Signed in to RetroAchievements as ${outcome.profile.username}."
                        }
                        is RaPasswordLoginResult.NeedsWebApiKey -> {
                            raPendingWebApiUser.value = outcome.username
                            message.value =
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

    fun setRetroAchievementsCredentials(username: String, apiKey: String) {
        viewModelScope.launch {
            raBusy.value = true
            raError.value = null
            val result = retroAchievements.saveCredentials(username, apiKey)
            raBusy.value = false
            result.fold(
                onSuccess = {
                    raPendingWebApiUser.value = null
                    message.value = "Signed in to RetroAchievements as ${it.username}."
                },
                onFailure = { error ->
                    raError.value = RetroAchievementsClient.sanitizeErrorMessage(
                        error.message ?: "Invalid RetroAchievements credentials.",
                    )
                },
            )
        }
    }

    fun requestSteamOpenId() {
        viewModelScope.launch {
            runCatching { externalAuthRequests.send(OnboardingExternalAuthRequest.SteamOpenId) }
        }
    }

    fun setSteamWebApiKey(apiKey: String) {
        viewModelScope.launch { preferences.setSteamWebApiKey(apiKey) }
    }

    fun applySteamOpenIdReturn(uri: Uri): Boolean {
        if (!SteamOpenId.isReturnUri(uri)) return false
        val steamId = SteamOpenId.steamId64FromReturnUri(uri) ?: return false
        viewModelScope.launch {
            preferences.setSteamId64(steamId)
            message.value = "Steam ID saved ($steamId). Paste a Web API key if needed."
        }
        return true
    }

    fun requestLinkDiscord() {
        viewModelScope.launch {
            runCatching { externalAuthRequests.send(OnboardingExternalAuthRequest.LinkDiscord) }
        }
    }

    fun linkDiscordAccount(activity: android.app.Activity) {
        discordRichPresence.startAccountLinking(activity)
        message.value = "Opening Discord account linking…"
    }

    fun consumeMessage() {
        message.value = null
    }

    fun showMessage(text: String) {
        message.value = text
    }

    /**
     * Marks onboarding finished in prefs. Caller should clear any session force flag and return
     * to the Home hub.
     */
    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingComplete(true)
            onFinished()
        }
    }
}
