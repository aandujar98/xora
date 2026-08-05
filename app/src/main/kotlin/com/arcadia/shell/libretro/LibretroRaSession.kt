package com.arcadia.shell.libretro

import android.util.Log
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.RetroAchievementsSettings
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.model.Game
import com.arcadia.shell.retroachievements.RaConsoleIds
import com.arcadia.shell.retroachievements.RetroAchievementsRepository
import com.arcadia.shell.scraper.RomHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Starts a RetroAchievements session for an in-process Libretro game:
 * hash ROM → resolve game id (Connect + Web API fallback) → login → load into rcheevos.
 *
 * Softcore vs hardcore follows [RetroAchievementsSettings.hardcore].
 */
class LibretroRaSession(
    private val scope: CoroutineScope,
    private val okHttpClient: OkHttpClient,
    private val retroAchievements: RetroAchievementsRepository,
    private val romHasher: RomHasher,
    private val libraryRepository: LibraryRepository,
    private val notifications: ShellNotificationCenter,
    private val gameTitle: String,
    private val raSettings: RetroAchievementsSettings = RetroAchievementsSettings(),
) {
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var bridge: LibretroRaBridge? = null
    private var startJob: Job? = null
    private var attached = false

    fun start(romPath: String, platformId: String, gameId: String) {
        stop()
        if (!raSettings.enabled) {
            _status.value = "RA: disabled in Settings"
            return
        }

        val raBridge = LibretroRaBridge(
            httpClient = okHttpClient,
            onUnlocked = { id, title, description, points, badgeUrl, hardcore ->
                if (raSettings.unlockNotifications) {
                    notifications.emit(
                        ShellNotification.AchievementUnlocked(
                            id = "ra-live:$id:${System.currentTimeMillis()}",
                            title = title.ifBlank { "Achievement" },
                            description = description.takeIf { it.isNotBlank() },
                            points = points.takeIf { it > 0 },
                            badgeUrl = badgeUrl.takeIf { it.isNotBlank() },
                            gameTitle = gameTitle,
                            hardcore = hardcore,
                        ),
                        force = true,
                    )
                }
                LibretroNative.nativeRaSummary()?.let { _status.value = "RA: $it" }
            },
            onStatusChanged = { message ->
                _status.value = message
            },
        )
        bridge = raBridge
        LibretroNative.nativeRaAttach(raBridge)
        attached = true
        LibretroNative.nativeRaSetHardcore(raSettings.hardcore)

        startJob = scope.launch {
            val creds = retroAchievements.currentCredentials()
            if (!creds.isConfigured) {
                _status.value = "RA: sign in under Settings → RetroAchievements"
                return@launch
            }

            _status.value = "RA: hashing ROM…"
            val storedMd5 = withContext(Dispatchers.IO) {
                libraryRepository.findById(gameId)?.md5?.trim()?.lowercase()
                    ?.takeIf { it.length == 32 }
            }
            val md5 = if (storedMd5 != null) {
                storedMd5
            } else {
                val game = Game(
                    id = gameId,
                    title = gameTitle,
                    sortKey = gameTitle.lowercase(),
                    platformId = platformId,
                    fileName = File(romPath).name,
                    filePath = romPath,
                    documentUri = null,
                    sizeBytes = File(romPath).length().coerceAtLeast(0L),
                )
                val hashes = withContext(Dispatchers.IO) { romHasher.hash(game) }
                if (hashes == null) {
                    _status.value = "RA: could not hash this ROM"
                    return@launch
                }
                runCatching {
                    libraryRepository.setHashes(gameId, hashes.crc32, hashes.md5, hashes.sha1)
                }
                hashes.md5.lowercase()
            }

            _status.value = "RA: identifying ROM…"
            val resolvedId = withContext(Dispatchers.IO) {
                retroAchievements.resolveRomGameId(md5, platformId)
            }.getOrElse { error ->
                _status.value = "RA: ${error.message ?: "could not identify ROM"}"
                return@launch
            }
            if (resolvedId == null) {
                _status.value = "RA: no RetroAchievements set for this ROM"
                Log.i(TAG, "No RA set for md5=$md5 platform=$platformId")
                return@launch
            }
            // Feed rcheevos the same Connect gameid payload the launcher resolved (avoids a
            // second Cloudflare-blocked dorequest from native).
            LibretroNative.nativeRaQueueGameIdResponse(
                """{"Success":true,"GameID":$resolvedId}""",
            )
            Log.i(TAG, "RA identified md5=$md5 → gameId=$resolvedId")

            val consoleId = RaConsoleIds.forPlatform(platformId) ?: 0
            val memOk = LibretroNative.nativeRaInitMemory(consoleId)
            if (!memOk) {
                Log.w(TAG, "RA memory map incomplete for console=$consoleId — trying anyway")
            }

            _status.value = if (raSettings.hardcore) {
                "RA: signing in (hardcore)…"
            } else {
                "RA: signing in…"
            }
            LibretroNative.nativeRaSetHardcore(raSettings.hardcore)

            // Refresh Connect token on the launcher HTTP stack, then inject that JSON into
            // rcheevos so native login does not make a second (often Cloudflare-blocked) request.
            val login = withContext(Dispatchers.IO) {
                retroAchievements.refreshEmulatorSession()
            }.getOrElse { error ->
                _status.value = "RA: ${error.message ?: "login failed"}"
                return@launch
            }

            LibretroNative.nativeRaQueueLoginResponse(login.loginJson)
            LibretroNative.nativeRaLoadGame(md5)
            LibretroNative.nativeRaLogin(login.session.username, login.session.token)
        }
    }

    fun onEmulatorReset() {
        if (attached) LibretroNative.nativeRaReset()
    }

    fun doFrame() {
        if (attached) LibretroNative.nativeRaDoFrame()
    }

    fun idle() {
        if (attached) LibretroNative.nativeRaIdle()
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        if (attached) {
            runCatching {
                LibretroNative.nativeRaUnloadGame()
                LibretroNative.nativeRaDetach()
            }
            attached = false
        }
        bridge = null
        _status.value = null
    }

    private companion object {
        const val TAG = "LibretroRaSession"
    }
}
