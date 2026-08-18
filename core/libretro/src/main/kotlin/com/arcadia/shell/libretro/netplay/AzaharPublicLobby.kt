package com.arcadia.shell.libretro.netplay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Which public-lobby overlay melonDS / Azahar can offer. */
enum class PublicLobbyKind {
    None,
    /** melonDS Nintendo WFC (Kaeru / Wiimmfi / AltWFC) — matchmaking is in-game. */
    NdsWfc,
    /** Citra/Azahar-style `GET {api}/lobby` rooms. XOrA can list them; Azahar Direct Connect uses the room IP. */
    AzaharRooms,
}

fun publicLobbyKind(platformId: String): PublicLobbyKind = when (platformId.trim().lowercase()) {
    "nds" -> PublicLobbyKind.NdsWfc
    "3ds" -> PublicLobbyKind.AzaharRooms
    else -> PublicLobbyKind.None
}

@Serializable
data class AzaharPublicRoom(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val owner: String = "",
    val ip: String = "",
    val port: Int = 0,
    @SerialName("max_player") val maxPlayer: Int = 0,
    @SerialName("net_version") val netVersion: Int = 0,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("preferred_game") val preferredGame: String = "",
    @SerialName("preferred_game_id") val preferredGameId: Long = 0,
    val members: List<AzaharPublicMember> = emptyList(),
)

@Serializable
data class AzaharPublicMember(
    val username: String = "",
    val nickname: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("game_name") val gameName: String = "",
    @SerialName("game_id") val gameId: Long = 0,
)

data class AzaharLobbyUi(
    val rooms: List<AzaharPublicRoom> = emptyList(),
    val status: String = "",
    val loading: Boolean = false,
    val sourceUrl: String = "",
    val standaloneInstalled: Boolean = false,
)

data class AzaharLobbyFetchResult(
    val rooms: List<AzaharPublicRoom> = emptyList(),
    val sourceUrl: String = "",
    val error: String? = null,
)

object AzaharPublicLobbies {
    const val HISTORICAL_CITRA_API = "https://api.citra-emu.org"
    /** Community Citra/Azahar room registry (Kex / ANTHENA / public halls). HTTP, not official Azahar. */
    const val COMMUNITY_AZAHAR_API = "http://88.198.47.46:5000"

    /** USA / EUR / JPN Mario Kart 7 title IDs used by Citra/Azahar lobby listings. */
    val MARIO_KART_7_TITLE_IDS = setOf(
        0x0004000000030700L,
        0x0004000000030800L,
        0x0004000000030600L,
    )

    /**
     * Featured open Mario Kart 7 Direct Connect. Used when the live registry has no
     * open MK7 room. Denver public hall accepts any game (including MK7).
     */
    val MARIO_KART_7_OPEN = AzaharPublicRoom(
        id = "xora-mk7-open",
        name = "Mario Kart 7 · open",
        description = "XOrA featured Azahar room. No password.",
        ip = "198.57.46.213",
        port = 5000,
        maxPlayer = 8,
        hasPassword = false,
        preferredGame = "Mario Kart 7",
    )

    val STANDALONE_PACKAGES = listOf(
        "org.azahar_emu.azahar",
        "io.github.lime3ds.android",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun lobbyRequestUrl(base: String): String {
        val trimmed = base.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/lobby", ignoreCase = true)) trimmed else "$trimmed/lobby"
    }

    fun candidateApiBases(configured: String): List<String> {
        val out = LinkedHashSet<String>()
        configured.trim().takeIf { it.isNotBlank() }?.let { out += it.trimEnd('/') }
        out += COMMUNITY_AZAHAR_API
        out += HISTORICAL_CITRA_API
        return out.toList()
    }

    fun parseLobbyJson(raw: String): List<AzaharPublicRoom> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyList()
        val rooms = root["rooms"] as? JsonArray ?: return emptyList()
        return rooms.mapNotNull { element -> parseRoom(element) }
    }

    private fun parseRoom(element: JsonElement): AzaharPublicRoom? {
        val obj = element as? JsonObject ?: return null
        val name = string(obj, "name")
        val id = string(obj, "id")
        if (name.isBlank() && id.isBlank()) return null
        return AzaharPublicRoom(
            id = id,
            name = name,
            description = string(obj, "description"),
            owner = string(obj, "owner"),
            ip = string(obj, "ip", "address"),
            port = int(obj, "port"),
            maxPlayer = int(obj, "max_player", "maxPlayers"),
            netVersion = int(obj, "net_version", "netVersion"),
            hasPassword = boolean(obj, "has_password", "hasPassword"),
            preferredGame = string(obj, "preferred_game", "preferredGameName", "preferredGame"),
            preferredGameId = long(obj, "preferred_game_id", "preferredGameId"),
            members = parseMembers(obj["members"] ?: obj["players"]),
        )
    }

    private fun parseMembers(element: JsonElement?): List<AzaharPublicMember> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            AzaharPublicMember(
                username = string(obj, "username"),
                nickname = string(obj, "nickname"),
                avatarUrl = string(obj, "avatar_url", "avatarUrl"),
                gameName = string(obj, "game_name", "gameName"),
                gameId = long(obj, "game_id", "gameId"),
            )
        }
    }

    private fun string(obj: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val value = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun int(obj: JsonObject, vararg keys: String): Int {
        for (key in keys) {
            val primitive = obj[key] as? JsonPrimitive ?: continue
            primitive.intOrNull?.let { return it }
            primitive.contentOrNull?.toIntOrNull()?.let { return it }
        }
        return 0
    }

    private fun long(obj: JsonObject, vararg keys: String): Long {
        for (key in keys) {
            val primitive = obj[key] as? JsonPrimitive ?: continue
            primitive.longOrNull?.let { return it }
            primitive.contentOrNull?.toLongOrNull()?.let { return it }
        }
        return 0L
    }

    private fun boolean(obj: JsonObject, vararg keys: String): Boolean {
        for (key in keys) {
            val primitive = obj[key] as? JsonPrimitive ?: continue
            primitive.booleanOrNull?.let { return it }
            val raw = primitive.contentOrNull?.trim()?.lowercase().orEmpty()
            if (raw == "true" || raw == "1") return true
            if (raw == "false" || raw == "0") return false
        }
        return false
    }

    fun roomTitle(room: AzaharPublicRoom, fallbackIndex: Int = 1): String {
        val name = room.name.ifBlank { "Room $fallbackIndex" }
        val connect = directConnect(room)
        return if (connect.isBlank()) name else "$connect · $name"
    }

    fun roomSubtitle(room: AzaharPublicRoom): String {
        val players = "${room.members.size}/${room.maxPlayer.coerceAtLeast(room.members.size)}"
        val game = room.preferredGame.ifBlank {
            room.members.firstOrNull { it.gameName.isNotBlank() }?.gameName.orEmpty()
        }.ifBlank { "No game set" }
        val lock = if (room.hasPassword) "Password" else "Open"
        val connect = directConnect(room)
        return if (connect.isBlank()) {
            "$players · $game · $lock"
        } else {
            "$players · $game · $lock · $connect"
        }
    }

    /** Citra Direct Connect target (`ip:port`). Blank if the listing omitted an address. */
    fun directConnect(room: AzaharPublicRoom): String {
        val ip = room.ip.trim()
        if (ip.isEmpty() || room.port <= 0) return ""
        return "$ip:${room.port}"
    }

    fun isMarioKart7(room: AzaharPublicRoom): Boolean {
        val blob = listOf(room.name, room.preferredGame, room.description)
            .joinToString(" ")
            .lowercase()
        if (blob.contains("mario kart 7") ||
            blob.contains("mariokart 7") ||
            blob.contains("mario kart7") ||
            Regex("""\bmk7\b""").containsMatchIn(blob)
        ) {
            return true
        }
        return room.preferredGameId in MARIO_KART_7_TITLE_IDS
    }

    fun featuredMarioKart7(live: List<AzaharPublicRoom>): AzaharPublicRoom =
        live.firstOrNull { room ->
            isMarioKart7(room) && !room.hasPassword && directConnect(room).isNotBlank()
        } ?: MARIO_KART_7_OPEN

    /** MK7 open room first, then the rest of the live registry. */
    fun displayRooms(live: List<AzaharPublicRoom>): List<AzaharPublicRoom> {
        val featured = featuredMarioKart7(live)
        val featuredConnect = directConnect(featured)
        val rest = live.filterNot { room ->
            featuredConnect.isNotBlank() && directConnect(room) == featuredConnect
        }
        return listOf(featured) + rest
    }

    fun installedStandalonePackage(pm: PackageManager): String? =
        STANDALONE_PACKAGES.firstOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg) != null
        }

    fun launchStandalone(context: Context): Boolean {
        val pm = context.packageManager
        val pkg = installedStandalonePackage(pm) ?: return false
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun fetchRooms(client: OkHttpClient, configuredUrl: String): AzaharLobbyFetchResult {
        val errors = mutableListOf<String>()
        for (base in candidateApiBases(configuredUrl)) {
            val url = lobbyRequestUrl(base)
            if (url.isBlank()) continue
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
                    parseLobbyJson(body)
                }
            }
            val rooms = result.getOrNull()
            if (rooms != null) {
                return AzaharLobbyFetchResult(rooms = rooms, sourceUrl = url)
            }
            errors += "${hostLabel(base)}: ${result.exceptionOrNull()?.message ?: "failed"}"
        }
        return AzaharLobbyFetchResult(
            error = "No public lobby answered. Tried ${COMMUNITY_AZAHAR_API} then the " +
                "historical Citra API. Set a community GET {url}/lobby in Settings. " +
                errors.joinToString(" · "),
        )
    }

    private fun hostLabel(base: String): String =
        base.removePrefix("https://").removePrefix("http://").substringBefore('/')
}
