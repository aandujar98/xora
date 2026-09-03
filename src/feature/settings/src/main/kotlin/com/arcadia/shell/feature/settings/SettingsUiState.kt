package com.arcadia.shell.feature.settings

import com.arcadia.shell.datastore.DiscordSocialSettings
import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.SteamWebApiCredentials
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.launcher.discord.DiscordPresenceUiState
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.PlatformSummary
import com.arcadia.shell.model.Player
import com.arcadia.shell.model.ScanProgress
import com.arcadia.shell.scanner.StorageVolumeRoot

data class PlatformPlayerChoice(
    val summary: PlatformSummary,
    val candidates: List<Player>,
    val selectedPlayerId: String?,
    /** The player that would actually run, including the automatic fallback. */
    val effectivePlayer: Player?,
    val isInstalled: Boolean,
)

/** One XOrA Libretro core row for Setup → XOrA Emulator status. */
data class XoraCoreInstallRow(
    val platformId: String,
    val platformLabel: String,
    val core: String,
    val label: String,
    val installed: Boolean,
)

data class SettingsUiState(
    val hasStorageAccess: Boolean = false,
    val roots: List<LibraryRoot> = emptyList(),
    val suggestedVolumes: List<StorageVolumeRoot> = emptyList(),
    val gameCount: Int = 0,
    /** Installed Android apps currently mirrored into the library. */
    val androidAppCount: Int = 0,
    val isSyncingApps: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val platformChoices: List<PlatformPlayerChoice> = emptyList(),
    val settings: ShellSettings = ShellSettings(),
    val xoraEmulator: XoraEmulatorSettings = XoraEmulatorSettings(),
    val raSettings: RetroAchievementsSettings = RetroAchievementsSettings(),
    val credentials: ScraperCredentials = ScraperCredentials(),
    val retroAchievements: RetroAchievementsCredentials = RetroAchievementsCredentials(),
    val steamWebApi: SteamWebApiCredentials = SteamWebApiCredentials(),
    val discordSocial: DiscordSocialSettings = DiscordSocialSettings(),
    val discordPresence: DiscordPresenceUiState = DiscordPresenceUiState(),
    val notificationListenerEnabled: Boolean = false,
    val isScraping: Boolean = false,
    val isHashingRoms: Boolean = false,
    val missingRomHashes: Int = 0,
    val message: String? = null,
    val raAuthBusy: Boolean = false,
    val raAuthError: String? = null,
    val raPendingWebApiUsername: String? = null,
    val xoraCores: List<XoraCoreInstallRow> = emptyList(),
    val xoraCoresInstalled: Int = 0,
    val xoraCoresTotal: Int = 0,
    val xoraDownloadRunning: Boolean = false,
    val xoraDownloadMessage: String? = null,
    val xoraDownloadError: String? = null,
)
