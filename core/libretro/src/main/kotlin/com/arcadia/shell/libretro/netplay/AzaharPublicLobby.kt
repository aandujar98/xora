package com.arcadia.shell.libretro.netplay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Which public-lobby overlay melonDS / Azahar can offer. */
enum class PublicLobbyKind {
    None,
    /** melonDS Nintendo WFC (Kaeru / Wiimmfi / AltWFC) — matchmaking is in-game. */
    NdsWfc,
    /** Citra/Azahar-style `GET {api}/lobby` rooms — join is standalone Azahar only. */
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

@Serializable
private data class AzaharLobbyFile(
    val rooms: List<AzaharPublicRoom> = emptyList(),
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
        out += HISTORICAL_CITRA_API
        return out.toList()
    }

    fun parseLobbyJson(raw: String): List<AzaharPublicRoom> {
        val file = json.decodeFromString(AzaharLobbyFile.serializer(), raw)
        return file.rooms.filter { it.name.isNotBlank() || it.id.isNotBlank() }
    }

    fun roomSubtitle(room: AzaharPublicRoom): String {
        val players = "${room.members.size}/${room.maxPlayer.coerceAtLeast(room.members.size)}"
        val game = room.preferredGame.ifBlank {
            room.members.firstOrNull { it.gameName.isNotBlank() }?.gameName.orEmpty()
        }.ifBlank { "No game set" }
        val lock = if (room.hasPassword) "Password" else "Open"
        return "$players · $game · $lock"
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
                    val body = response.body?.string().orEmpty()
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
            error = "No public lobby answered. Azahar does not host official rooms. " +
                "Set a community lobby URL in Settings, or open standalone Azahar. " +
                errors.joinToString(" · "),
        )
    }

    private fun hostLabel(base: String): String =
        base.removePrefix("https://").removePrefix("http://").substringBefore('/')
}
