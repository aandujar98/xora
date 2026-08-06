package com.arcadia.shell.retroachievements

import com.arcadia.shell.datastore.RetroAchievementsCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetroAchievementsClient @Inject constructor(
    httpClient: OkHttpClient,
    private val json: Json,
) {
    /** Dedicated client so every RA call (Connect + Web API) carries a valid User-Agent. */
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val withUa = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            chain.proceed(withUa)
        })
        .build()

    /** consoleId → (md5 → gameId), populated from Web API GetGameList fallback. */
    private val hashLibraryByConsole = ConcurrentHashMap<Int, Map<String, Int>>()

    /**
     * Connect API password login (`r=login2`). Returns a Connect session [RaLoginSession.token]
     * (not the Web API key). Never logs [password].
     */
    suspend fun login(username: String, password: String): Result<RaLoginSession> =
        withContext(Dispatchers.IO) {
            val user = username.trim()
            if (user.isBlank() || password.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Username and password are required."),
                )
            }
            runCatching {
                // Prefer POST so the password is not written into proxy/access logs as a query string.
                val body = postForm(
                    "$SITE_BASE/dorequest.php",
                    mapOf(
                        "r" to "login2",
                        "u" to user,
                        "p" to password,
                    ),
                )
                parseLoginBody(body)
            }
        }

    /**
     * Connect API token re-login (`r=login2` with `t=`). Returns the raw JSON body so the
     * emulator host can apply it to rcheevos without a second Cloudflare-facing request.
     */
    suspend fun loginWithTokenBody(username: String, connectToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            val user = username.trim()
            val token = connectToken.trim()
            if (user.isBlank() || token.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Username and Connect token are required."),
                )
            }
            runCatching {
                postForm(
                    "$SITE_BASE/dorequest.php",
                    mapOf(
                        "r" to "login2",
                        "u" to user,
                        "t" to token,
                    ),
                )
            }
        }

    /**
     * Connect API token re-login (`r=login2` with `t=`). Refreshes an existing Connect session
     * token. Does not accept the control-panel Web API key.
     */
    suspend fun loginWithToken(username: String, connectToken: String): Result<RaLoginSession> =
        loginWithTokenBody(username, connectToken).mapCatching { parseLoginBody(it) }

    /**
     * Connect `r=patch` — achievement definitions for [gameId]. Raw JSON for rcheevos injection.
     */
    suspend fun fetchPatchBody(
        username: String,
        connectToken: String,
        gameId: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = username.trim()
        val token = connectToken.trim()
        if (user.isBlank() || token.isEmpty() || gameId <= 0) {
            return@withContext Result.failure(
                IllegalArgumentException("Username, Connect token, and game id are required."),
            )
        }
        runCatching {
            postForm(
                "$SITE_BASE/dorequest.php",
                mapOf(
                    "r" to "patch",
                    "u" to user,
                    "t" to token,
                    "g" to gameId.toString(),
                ),
            )
        }
    }

    /**
     * Connect `r=startsession` — starts a play session and returns unlock state. Raw JSON for
     * rcheevos injection so the emulator host never re-hits Cloudflare for session start.
     */
    suspend fun startSessionBody(
        username: String,
        connectToken: String,
        gameId: Int,
        md5: String,
        hardcore: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        val user = username.trim()
        val token = connectToken.trim()
        val hash = md5.trim().lowercase()
        if (user.isBlank() || token.isEmpty() || gameId <= 0 || hash.length != 32) {
            return@withContext Result.failure(
                IllegalArgumentException("Username, Connect token, game id, and MD5 are required."),
            )
        }
        runCatching {
            postForm(
                "$SITE_BASE/dorequest.php",
                mapOf(
                    "r" to "startsession",
                    "u" to user,
                    "t" to token,
                    "g" to gameId.toString(),
                    "h" to if (hardcore) "1" else "0",
                    "m" to hash,
                    "l" to RCHEEVOS_CLIENT_VERSION,
                ),
            )
        }
    }

    suspend fun fetchProfile(credentials: RetroAchievementsCredentials): Result<RaProfile> =
        withContext(Dispatchers.IO) {
            if (!credentials.isConfigured) {
                return@withContext Result.failure(IllegalStateException("Not signed in."))
            }
            runCatching {
                val body = get(
                    "$API_BASE/API_GetUserProfile.php",
                    mapOf(
                        "y" to credentials.apiKey,
                        "u" to credentials.username,
                    ),
                )
                val obj = json.parseToJsonElement(body).jsonObject
                RaProfile(
                    username = obj.string("User") ?: credentials.username,
                    totalPoints = obj.int("TotalPoints") ?: 0,
                    totalSoftcorePoints = obj.int("TotalSoftcorePoints") ?: 0,
                )
            }
        }

    private fun parseLoginBody(body: String): RaLoginSession {
        val obj = json.parseToJsonElement(body).jsonObject
        val success = obj["Success"]?.jsonPrimitive?.booleanOrNull == true
        if (!success) {
            val error = obj.string("Error")
                ?: obj.string("Code")
                ?: "Invalid user/password combination."
            error(sanitizeErrorMessage(error))
        }
        val token = obj.string("Token")?.trim().orEmpty()
        val loggedInUser = obj.string("User")?.trim().orEmpty()
        if (token.isBlank() || loggedInUser.isBlank()) {
            error("Login response missing Token or User.")
        }
        return RaLoginSession(username = loggedInUser, token = token)
    }

    /** Parse a successful Connect `login2` JSON body (used by the emulator host). */
    fun parseLoginResponse(body: String): RaLoginSession = parseLoginBody(body)

    /**
     * Resolves an RA game id from an MD5. [Result] failure means the API/network failed;
     * success with `null` means the hash is unknown (no linked set).
     *
     * Prefers Connect `gameid` (POST, then GET). On HTTP 403 from Cloudflare/WAF, falls back to
     * the authenticated Web API game-list+hashes endpoint when [credentials] and [consoleId] are
     * available.
     */
    suspend fun resolveGameId(
        md5: String,
        credentials: RetroAchievementsCredentials? = null,
        consoleId: Int? = null,
    ): Result<Int?> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = md5.lowercase()
            when (val connect = resolveGameIdConnect(hash)) {
                is ConnectLookup.Matched -> return@runCatching connect.gameId
                is ConnectLookup.NoMatch -> return@runCatching null
                is ConnectLookup.Blocked -> {
                    val apiKey = credentials?.apiKey?.trim().orEmpty()
                    if (apiKey.isNotBlank() && consoleId != null && consoleId > 0) {
                        return@runCatching resolveGameIdViaWebLibrary(
                            hash = hash,
                            consoleId = consoleId,
                            apiKey = apiKey,
                        )
                    }
                    error(connect.message)
                }
            }
        }
    }

    private sealed interface ConnectLookup {
        data class Matched(val gameId: Int?) : ConnectLookup
        data object NoMatch : ConnectLookup
        data class Blocked(val message: String) : ConnectLookup
    }

    private fun resolveGameIdConnect(hash: String): ConnectLookup {
        val attempts = listOf(
            { postForm("$SITE_BASE/dorequest.php", mapOf("r" to "gameid", "m" to hash)) },
            {
                get(
                    "$SITE_BASE/dorequest.php",
                    mapOf("r" to "gameid", "m" to hash),
                )
            },
        )
        var lastBlocked: String? = null
        for (attempt in attempts) {
            try {
                val body = attempt()
                return parseGameIdBody(body)
            } catch (e: IllegalStateException) {
                val msg = e.message.orEmpty()
                if (msg.contains("403")) {
                    lastBlocked = sanitizeErrorMessage(msg)
                    continue
                }
                throw e
            }
        }
        return ConnectLookup.Blocked(lastBlocked ?: "HTTP 403 Forbidden")
    }

    private fun parseGameIdBody(body: String): ConnectLookup =
        when (val parsed = parseGameIdResponse(body, json)) {
            is GameIdParse.Matched -> ConnectLookup.Matched(parsed.gameId)
            GameIdParse.NoMatch -> ConnectLookup.NoMatch
            is GameIdParse.Blocked -> ConnectLookup.Blocked(parsed.message)
        }

    private fun resolveGameIdViaWebLibrary(hash: String, consoleId: Int, apiKey: String): Int? {
        val cached = hashLibraryByConsole[consoleId]
        if (cached != null) return cached[hash]

        val body = get(
            "$API_BASE/API_GetGameList.php",
            mapOf(
                "y" to apiKey,
                "i" to consoleId.toString(),
                "f" to "1",
                "h" to "1",
            ),
        )
        val array = json.parseToJsonElement(body).jsonArray
        val map = HashMap<String, Int>()
        for (element in array) {
            val obj = element.jsonObject
            val id = obj.int("ID") ?: continue
            val hashes = obj["Hashes"]?.jsonArray ?: continue
            for (h in hashes) {
                val md5 = h.jsonPrimitive.contentOrNull?.lowercase() ?: continue
                map[md5] = id
            }
        }
        hashLibraryByConsole[consoleId] = map
        return map[hash]
    }

    suspend fun fetchGameProgress(
        credentials: RetroAchievementsCredentials,
        gameId: Int,
    ): Result<RaGameProgress> = withContext(Dispatchers.IO) {
        if (!credentials.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Not signed in."))
        }
        runCatching {
            val body = get(
                "$API_BASE/API_GetGameInfoAndUserProgress.php",
                mapOf(
                    "y" to credentials.apiKey,
                    "u" to credentials.username,
                    "g" to gameId.toString(),
                ),
            )
            parseGameProgress(body)
        }
    }

    suspend fun fetchRecentUnlocks(
        credentials: RetroAchievementsCredentials,
        minutes: Int = RECENT_WINDOW_MINUTES,
    ): Result<List<RaRecentUnlock>> = withContext(Dispatchers.IO) {
        if (!credentials.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Not signed in."))
        }
        runCatching {
            val body = get(
                "$API_BASE/API_GetUserRecentAchievements.php",
                mapOf(
                    "y" to credentials.apiKey,
                    "u" to credentials.username,
                    "m" to minutes.toString(),
                ),
            )
            val array = json.parseToJsonElement(body).jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                RaRecentUnlock(
                    achievementId = obj.int("AchievementID") ?: return@mapNotNull null,
                    title = obj.string("Title").orEmpty(),
                    description = obj.string("Description").orEmpty(),
                    points = obj.int("Points") ?: 0,
                    badgeName = obj.string("BadgeName").orEmpty(),
                    gameTitle = obj.string("GameTitle").orEmpty(),
                    consoleName = obj.string("ConsoleName").orEmpty(),
                    hardcore = (obj.int("HardcoreMode") ?: 0) == 1,
                    date = obj.string("Date").orEmpty(),
                )
            }
        }
    }

    /**
     * Games the signed-in user has earned (or started) achievements on.
     * Does not include full badge strips — use [fetchRecentUnlocks] / per-game progress for icons.
     */
    suspend fun fetchCompletionProgress(
        credentials: RetroAchievementsCredentials,
        count: Int = COMPLETION_PAGE_SIZE,
        offset: Int = 0,
    ): Result<List<RaCompletionGame>> = withContext(Dispatchers.IO) {
        if (!credentials.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Not signed in."))
        }
        runCatching {
            val body = get(
                "$API_BASE/API_GetUserCompletionProgress.php",
                mapOf(
                    "y" to credentials.apiKey,
                    "u" to credentials.username,
                    "c" to count.coerceIn(1, 500).toString(),
                    "o" to offset.coerceAtLeast(0).toString(),
                ),
            )
            parseCompletionProgress(body)
        }
    }

    private fun parseCompletionProgress(body: String): List<RaCompletionGame> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["Results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val obj = element.jsonObject
            val gameId = obj.int("GameID") ?: return@mapNotNull null
            RaCompletionGame(
                gameId = gameId,
                title = obj.string("Title").orEmpty(),
                imageIconPath = obj.string("ImageIcon").orEmpty(),
                consoleId = obj.int("ConsoleID") ?: 0,
                consoleName = obj.string("ConsoleName").orEmpty(),
                maxPossible = obj.int("MaxPossible") ?: 0,
                numAwarded = obj.int("NumAwarded") ?: 0,
                numAwardedHardcore = obj.int("NumAwardedHardcore") ?: 0,
                mostRecentAwardedDate = obj.string("MostRecentAwardedDate"),
                highestAwardKind = obj.string("HighestAwardKind"),
            )
        }
    }

    private fun parseGameProgress(body: String): RaGameProgress {
        val obj = json.parseToJsonElement(body).jsonObject
        val gameId = obj.int("ID") ?: error("Missing game id")
        val achievementsObj = obj["Achievements"]?.jsonObject
        val achievements = achievementsObj?.entries?.mapNotNull { (_, value) ->
            val a = value.jsonObject
            RaAchievement(
                id = a.int("ID") ?: return@mapNotNull null,
                title = a.string("Title").orEmpty(),
                description = a.string("Description").orEmpty(),
                points = a.int("Points") ?: 0,
                badgeName = a.string("BadgeName").orEmpty(),
                displayOrder = a.int("DisplayOrder") ?: 0,
                earned = a.string("DateEarned") != null,
                earnedHardcore = a.string("DateEarnedHardcore") != null,
            )
        }.orEmpty().sortedWith(
            compareBy<RaAchievement> { it.displayOrder }.thenBy { it.id },
        )

        return RaGameProgress(
            gameId = gameId,
            title = obj.string("Title").orEmpty(),
            consoleName = obj.string("ConsoleName").orEmpty(),
            numAchievements = obj.int("NumAchievements") ?: achievements.size,
            numAwardedToUser = obj.int("NumAwardedToUser") ?: achievements.count { it.earned },
            numAwardedToUserHardcore = obj.int("NumAwardedToUserHardcore")
                ?: achievements.count { it.earnedHardcore },
            achievements = achievements,
        )
    }

    private fun get(url: String, query: Map<String, String>): String {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .build()
        return execute(request)
    }

    private fun postForm(url: String, fields: Map<String, String>): String {
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .post(form)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()
        return execute(request)
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error(httpErrorMessage(response.code, body))
            }
            if (looksLikeHtml(body)) {
                error(httpErrorMessage(response.code.takeIf { it != 0 } ?: 403, body))
            }
            return body
        }
    }

    private fun httpErrorMessage(code: Int, body: String): String {
        if (looksLikeHtml(body) || body.contains("403 Forbidden", ignoreCase = true)) {
            return when (code) {
                403 -> "HTTP 403 Forbidden"
                429 -> "HTTP 429 Too Many Requests"
                else -> "HTTP $code"
            }
        }
        val snippet = body.replace(Regex("\\s+"), " ").take(120)
        return if (snippet.isBlank() || looksLikeHtml(snippet)) {
            "HTTP $code"
        } else {
            "HTTP $code: $snippet"
        }
    }

    companion object {
        const val SITE_BASE = "https://retroachievements.org"
        const val API_BASE = "$SITE_BASE/API"
        /**
         * Connect API requires a non-empty UA (`IntegrationName/Version`). Cloudflare/nginx 403s
         * bare OkHttp defaults on dorequest.php. Keep this aligned with the shipped app name —
         * blocked/legacy names can make `gameid` return Success:false + GameID:0.
         */
        const val USER_AGENT = "XOrA/1.0.0"

        /**
         * `l=` on Connect `startsession` — must match the vendored rcheevos [RCHEEVOS_VERSION_STRING].
         * Kept as a Kotlin constant so the emulator can prefetch the session without linking native.
         */
        /** Matches vendored rcheevos when patch==0 → "major.minor". */
        const val RCHEEVOS_CLIENT_VERSION = "11.6"

        /** Roughly one year — enough for a browseable “all recent” list without dumping history. */
        const val RECENT_WINDOW_MINUTES = 525_600

        /** First page of the RA library list (API max 500). */
        const val COMPLETION_PAGE_SIZE = 200

        fun looksLikeHtml(body: String): Boolean {
            val trimmed = body.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (trimmed.isEmpty()) return false
            return trimmed.startsWith("<") ||
                trimmed.contains("<html", ignoreCase = true) ||
                trimmed.contains("<!DOCTYPE", ignoreCase = true) ||
                trimmed.contains("<head>", ignoreCase = true) ||
                trimmed.contains("cloudflareinsights", ignoreCase = true) ||
                (trimmed.contains("nginx", ignoreCase = true) &&
                    trimmed.contains("Forbidden", ignoreCase = true))
        }

        fun sanitizeErrorMessage(message: String): String {
            if (!looksLikeHtml(message) && !message.contains("<html", ignoreCase = true)) {
                // Collapse accidental HTML fragments that slipped past earlier checks.
                if (message.contains('<') &&
                    (message.contains("html", ignoreCase = true) ||
                        message.contains("nginx", ignoreCase = true))
                ) {
                    return if (message.contains("403")) "HTTP 403 Forbidden" else "HTTP error"
                }
                return message
            }
            return if (message.contains("403")) "HTTP 403 Forbidden" else "HTTP error"
        }

        /**
         * Parses Connect `r=gameid` JSON. Exposed for unit tests — keep in sync with RAWeb
         * GetGameIdFromHashAction (Success:true/GameID:0 = unknown; Success:false = error/block).
         */
        fun parseGameIdResponse(body: String, json: Json = Json { ignoreUnknownKeys = true }): GameIdParse {
            if (looksLikeHtml(body)) {
                return GameIdParse.Blocked("HTTP 403 Forbidden")
            }
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                if (looksLikeHtml(body)) {
                    return GameIdParse.Blocked("HTTP 403 Forbidden")
                }
                throw it
            }
            val success = obj.flexibleBoolean("Success") ?: false
            val gameId = obj.flexibleInt("GameID")?.takeIf { it > 0 }
            if (!success) {
                val error = obj.flexibleString("Error")
                    ?: obj.flexibleString("Code")
                    ?: "RetroAchievements rejected the request."
                return GameIdParse.Blocked(sanitizeErrorMessage(error))
            }
            return if (gameId != null) {
                GameIdParse.Matched(gameId)
            } else {
                GameIdParse.NoMatch
            }
        }

        private fun JsonObject.flexibleString(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.flexibleBoolean(key: String): Boolean? {
            val primitive = this[key]?.jsonPrimitive ?: return null
            primitive.booleanOrNull?.let { return it }
            return when (primitive.contentOrNull?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        }

        private fun JsonObject.flexibleInt(key: String): Int? {
            val primitive = this[key]?.jsonPrimitive ?: return null
            primitive.intOrNull?.let { return it }
            return primitive.contentOrNull?.toIntOrNull()
        }
    }

    sealed interface GameIdParse {
        data class Matched(val gameId: Int) : GameIdParse
        data object NoMatch : GameIdParse
        data class Blocked(val message: String) : GameIdParse
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    /** Web API historically mixes numeric JSON and digit strings for ids. */
    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key]?.jsonPrimitive ?: return null
        primitive.intOrNull?.let { return it }
        return primitive.contentOrNull?.toIntOrNull()
    }
}
