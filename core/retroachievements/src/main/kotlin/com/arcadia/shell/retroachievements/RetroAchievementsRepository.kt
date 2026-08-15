package com.arcadia.shell.retroachievements

import android.util.Log
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.Game
import com.arcadia.shell.scraper.RaHashRules
import com.arcadia.shell.scraper.RomHasher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetroAchievementsRepository @Inject constructor(
    private val preferences: ShellPreferences,
    private val client: RetroAchievementsClient,
    private val hasher: RomHasher,
    private val libraryRepository: LibraryRepository,
) {
    private val gameIdByMd5 = ConcurrentHashMap<String, Int>()

    val credentials: Flow<RetroAchievementsCredentials> = preferences.retroAchievements

    suspend fun currentCredentials(): RetroAchievementsCredentials = credentials.first()

    suspend fun saveCredentials(
        username: String,
        apiKey: String,
        connectToken: String? = null,
    ): Result<RaProfile> {
        val existing = currentCredentials()
        val tokenToKeep = connectToken?.trim().orEmpty().ifBlank { existing.connectToken }
        preferences.setRetroAchievementsCredentials(
            username = username,
            apiKey = apiKey,
            connectToken = tokenToKeep,
        )
        val creds = RetroAchievementsCredentials(
            username = username.trim(),
            apiKey = apiKey.trim(),
            connectToken = tokenToKeep,
        )
        val profile = client.fetchProfile(creds)
        if (profile.isFailure) {
            // Keep Connect token so XOrA Emulator stays signed in; revert Web API key.
            preferences.setRetroAchievementsCredentials(
                username = existing.username.ifBlank { username.trim() },
                apiKey = existing.apiKey,
                connectToken = tokenToKeep,
            )
        }
        return profile
    }

    /**
     * Sign in with RetroAchievements username + password via Connect `login2`.
     *
     * Always stores the Connect token for XOrA Emulator (rcheevos). On success the Connect
     * token is also tried as the Web API key (legacy accounts where they matched). If the Web
     * API rejects it, returns [RaPasswordLoginResult.NeedsWebApiKey] so the UI can collect the
     * control-panel key once — password is never persisted.
     */
    suspend fun loginWithPassword(username: String, password: String): Result<RaPasswordLoginResult> {
        val session = client.login(username, password).getOrElse {
            return Result.failure(it)
        }
        // Emulator needs this even when the Web API still wants a separate key.
        preferences.setRaConnectToken(session.username, session.token)

        val tokenCreds = RetroAchievementsCredentials(
            username = session.username,
            apiKey = session.token,
            connectToken = session.token,
        )
        val profile = client.fetchProfile(tokenCreds)
        if (profile.isSuccess) {
            preferences.setRetroAchievementsCredentials(
                username = session.username,
                apiKey = session.token,
                connectToken = session.token,
            )
            return Result.success(RaPasswordLoginResult.SignedIn(profile.getOrThrow()))
        }
        return Result.success(
            RaPasswordLoginResult.NeedsWebApiKey(
                username = session.username,
                connectToken = session.token,
            ),
        )
    }

    /**
     * Refresh the Connect token via the launcher HTTP stack (Cloudflare-safe UA) and persist it.
     * Returns session + raw login2 JSON so the emulator can seed rcheevos without a second request.
     */
    suspend fun refreshEmulatorSession(): Result<RaEmulatorLogin> {
        val creds = currentCredentials()
        if (creds.username.isBlank()) {
            return Result.failure(IllegalStateException("Not signed in."))
        }
        val token = creds.emulatorToken
        if (token.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Sign in with username + password in Settings (Connect token required).",
                ),
            )
        }
        val body = client.loginWithTokenBody(creds.username, token).getOrElse { error ->
            val msg = RetroAchievementsClient.sanitizeErrorMessage(
                error.message ?: "Invalid RetroAchievements credentials.",
            )
            if (creds.connectToken.isBlank() && creds.apiKey.isNotBlank()) {
                return Result.failure(
                    IllegalStateException(
                        "Re-sign in with username + password in Settings " +
                            "(Web API key alone does not work in XOrA Emulator).",
                    ),
                )
            }
            return Result.failure(IllegalStateException(msg))
        }
        val parsed = runCatching { client.parseLoginResponse(body) }.getOrElse { error ->
            return Result.failure(
                IllegalStateException(
                    RetroAchievementsClient.sanitizeErrorMessage(
                        error.message ?: "Could not parse RetroAchievements login response.",
                    ),
                ),
            )
        }

        preferences.setRaConnectToken(parsed.username, parsed.token)
        preferences.setRetroAchievementsCredentials(
            username = parsed.username,
            apiKey = creds.apiKey.ifBlank { parsed.token },
            connectToken = parsed.token,
        )
        return Result.success(RaEmulatorLogin(session = parsed, loginJson = body))
    }

    suspend fun clearCredentials() {
        preferences.clearRetroAchievementsCredentials()
        gameIdByMd5.clear()
    }

    suspend fun fetchProfile(): Result<RaProfile> =
        client.fetchProfile(currentCredentials())

    suspend fun fetchRecentUnlocks(): Result<List<RaRecentUnlock>> =
        client.fetchRecentUnlocks(currentCredentials())

    suspend fun fetchCompletionProgress(
        count: Int = RetroAchievementsClient.COMPLETION_PAGE_SIZE,
        offset: Int = 0,
    ): Result<List<RaCompletionGame>> =
        client.fetchCompletionProgress(currentCredentials(), count = count, offset = offset)

    /**
     * Resolve a ROM MD5 to a RetroAchievements game id using the same Connect + Web API hash
     * library fallback as the launcher. Used by XOrA Emulator so Cloudflare-blocked Connect
     * `gameid` calls still succeed.
     */
    suspend fun resolveRomGameId(md5: String, platformId: String): Result<Int?> {
        val creds = currentCredentials()
        val consoleId = RaConsoleIds.forPlatform(platformId)
        return client.resolveGameId(md5.lowercase(), credentials = creds, consoleId = consoleId)
    }

    /** Web API progress for a RetroAchievements game the emulator already identified. */
    suspend fun fetchGameProgress(gameId: Int): Result<RaGameProgress> {
        val creds = currentCredentials()
        if (!creds.isConfigured) {
            return Result.failure(IllegalStateException("Not signed in."))
        }
        return client.fetchGameProgress(creds, gameId)
    }

    /**
     * Prefetch Connect `patch` + `startsession` JSON on the launcher HTTP stack for the emulator.
     * Pair with [refreshEmulatorSession] so rcheevos never needs a live dorequest after login.
     */
    suspend fun prefetchEmulatorGameSession(
        username: String,
        connectToken: String,
        gameId: Int,
        md5: String,
        hardcore: Boolean,
    ): Result<RaEmulatorGameSession> {
        val patch = client.fetchPatchBody(username, connectToken, gameId).getOrElse {
            return Result.failure(
                IllegalStateException(
                    RetroAchievementsClient.sanitizeErrorMessage(
                        it.message ?: "Could not download achievement set.",
                    ),
                ),
            )
        }
        connectSuccessOrFail(patch, "Could not download achievement set.").getOrElse {
            return Result.failure(it)
        }
        val start = client.startSessionBody(
            username = username,
            connectToken = connectToken,
            gameId = gameId,
            md5 = md5,
            hardcore = hardcore,
        ).getOrElse {
            return Result.failure(
                IllegalStateException(
                    RetroAchievementsClient.sanitizeErrorMessage(
                        it.message ?: "Could not start RetroAchievements session.",
                    ),
                ),
            )
        }
        connectSuccessOrFail(start, "Could not start RetroAchievements session.").getOrElse {
            return Result.failure(it)
        }
        return Result.success(
            RaEmulatorGameSession(patchJson = patch, startSessionJson = start),
        )
    }

    private fun connectSuccessOrFail(body: String, fallback: String): Result<Unit> {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("{")) {
            return Result.failure(IllegalStateException("RetroAchievements response was blocked."))
        }
        return runCatching {
            val obj = CONNECT_JSON.parseToJsonElement(trimmed).jsonObject
            val success = obj["Success"]?.jsonPrimitive?.booleanOrNull
            if (success == false) {
                val error = obj["Error"]?.jsonPrimitive?.contentOrNull
                    ?: obj["Code"]?.jsonPrimitive?.contentOrNull
                    ?: fallback
                error(RetroAchievementsClient.sanitizeErrorMessage(error))
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = {
                Result.failure(
                    IllegalStateException(
                        RetroAchievementsClient.sanitizeErrorMessage(it.message ?: fallback),
                    ),
                )
            },
        )
    }

    suspend fun lookupSelectedGame(game: Game?): RaGameLookup {
        if (game == null || game.isAndroidApp) return RaGameLookup.NoHash

        val extension = game.fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension == "7z") {
            return RaGameLookup.Failed(
                "7z archives cannot be hashed for RetroAchievements yet. " +
                    "Use a zip or the extracted ROM.",
            )
        }
        if (game.platformId in RaHashRules.UNSUPPORTED_CUSTOM_HASH_PLATFORMS) {
            return RaGameLookup.Failed(
                "RetroAchievements uses a disc/encrypted hash for " +
                    "${game.platform.displayName} that XOrA does not compute yet.",
            )
        }

        if (game.platformId in RaHashRules.DISC_HASH_PLATFORMS &&
            extension in com.arcadia.shell.scraper.RaDiscHash.UNSUPPORTED_DISC_EXTENSIONS
        ) {
            return RaGameLookup.Failed(
                ".$extension discs cannot be hashed on-device yet for " +
                    "${game.platform.displayName}. Use .cue/.bin, .iso, or .gdi when available.",
            )
        }

        // Prefer a previously stored library hash (from "Hash all ROMs" / scrape). Live-hash when
        // missing so selection still works before the background pass finishes.
        val storedMd5 = game.md5?.trim()?.lowercase()?.takeIf { it.length == 32 }
        val hashes = if (storedMd5 != null) {
            null
        } else {
            try {
                withTimeout(HASH_TIMEOUT_MS) { hasher.hash(game) }
            } catch (_: TimeoutCancellationException) {
                Log.w(TAG, "Hash timeout for ${game.fileName}")
                return RaGameLookup.Failed("Timed out hashing this ROM for RetroAchievements.")
            } ?: return RaGameLookup.Failed(
                when {
                    game.filePath == null && game.documentUri == null ->
                        "Could not hash this ROM for RetroAchievements (missing file access)."
                    game.platformId in RaHashRules.DISC_HASH_PLATFORMS ->
                        "Could not compute the RetroAchievements disc hash for this " +
                            "${game.platform.displayName} image. Try .cue/.bin, .iso, or .gdi."
                    else ->
                        "Could not hash this ROM for RetroAchievements " +
                            "(missing file access, or a format that needs a custom hash). " +
                            "Run Setup → RetroAchievements → Hash all ROMs."
                },
            )
        }

        val md5 = storedMd5 ?: hashes!!.md5.lowercase()
        val hashedBytes = hashes?.hashedBytes ?: game.sizeBytes
        if (storedMd5 == null && hashes != null) {
            runCatching {
                libraryRepository.setHashes(game.id, hashes.crc32, hashes.md5, hashes.sha1)
            }
        }
        Log.i(
            TAG,
            "Hash ok for ${game.fileName} platform=${game.platformId} " +
                "bytes=$hashedBytes md5=$md5 " +
                "(${if (storedMd5 != null) "stored" else "live"}) — resolving game id",
        )

        val creds = currentCredentials()
        val consoleId = RaConsoleIds.forPlatform(game.platformId)

        val resolvedId = gameIdByMd5[md5] ?: run {
            val apiResult = try {
                withTimeout(API_TIMEOUT_MS) {
                    client.resolveGameId(md5, credentials = creds, consoleId = consoleId)
                }
            } catch (_: TimeoutCancellationException) {
                return RaGameLookup.Failed("Timed out resolving this game on RetroAchievements.")
            }

            apiResult.fold(
                onSuccess = { id ->
                    if (id == null) {
                        Log.i(TAG, "API no-match for md5=$md5 (${game.fileName})")
                    } else {
                        Log.i(TAG, "API matched md5=$md5 → gameId=$id")
                        gameIdByMd5[md5] = id
                    }
                    id
                },
                onFailure = { error ->
                    val safe = RetroAchievementsClient.sanitizeErrorMessage(
                        error.message ?: "network error",
                    )
                    Log.w(TAG, "API resolve failed for md5=$md5: $safe")
                    return RaGameLookup.Failed(
                        "Could not look up this ROM on RetroAchievements: $safe",
                    )
                },
            )
        } ?: return RaGameLookup.NoGame(md5 = md5, hashedBytes = hashedBytes)

        if (!creds.isConfigured) {
            return RaGameLookup.Failed("Not signed in.")
        }

        return try {
            withTimeout(API_TIMEOUT_MS) {
                client.fetchGameProgress(creds, resolvedId).fold(
                    onSuccess = { RaGameLookup.Matched(it) },
                    onFailure = {
                        RaGameLookup.Failed(
                            RetroAchievementsClient.sanitizeErrorMessage(
                                it.message ?: "Could not load achievements.",
                            ),
                        )
                    },
                )
            }
        } catch (_: TimeoutCancellationException) {
            RaGameLookup.Failed("Timed out loading achievements for this game.")
        }
    }

    private companion object {
        const val TAG = "RetroAchievements"
        const val HASH_TIMEOUT_MS = 45_000L
        const val API_TIMEOUT_MS = 30_000L
        private val CONNECT_JSON = Json { ignoreUnknownKeys = true }
    }
}
