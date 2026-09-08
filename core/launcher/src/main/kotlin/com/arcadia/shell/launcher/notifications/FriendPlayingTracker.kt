package com.arcadia.shell.launcher.notifications

/**
 * Diffs friend presence so a "now playing" banner fires when someone starts (or switches) a game,
 * without replaying the first snapshot after a reconnect.
 */
class FriendPlayingTracker {
    private var seeded = false
    private val lastGameById = linkedMapOf<String, String>()

    val isSeeded: Boolean get() = seeded

    fun reset() {
        seeded = false
        lastGameById.clear()
    }

    /**
     * First snapshot only remembers titles. Later snapshots return `(id, gameTitle)` for each
     * friend whose game became a new non-blank value.
     */
    fun consume(entries: List<Pair<String, String?>>): List<Pair<String, String>> {
        if (!seeded) {
            lastGameById.clear()
            for ((id, raw) in entries) {
                val game = normalizeGame(raw) ?: continue
                lastGameById[id] = game
            }
            seeded = true
            return emptyList()
        }
        val started = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        for ((id, raw) in entries) {
            if (id.isBlank()) continue
            seen += id
            val game = normalizeGame(raw)
            val previous = lastGameById[id]
            if (game != null && game != previous) {
                started += id to game
                lastGameById[id] = game
            } else if (game == null) {
                lastGameById.remove(id)
            }
        }
        lastGameById.keys.retainAll(seen)
        return started
    }

    private fun normalizeGame(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }
}

/** Banner line: `"pal is now playing Celeste"`. */
fun friendPlayingHeadline(displayName: String, gameTitle: String): String {
    val who = displayName.trim().ifBlank { "A friend" }
    val game = gameTitle.trim().ifBlank { "a game" }
    return "$who is now playing $game"
}

/**
 * XOrA Network status is `"playing Super Mario"` / `"Playing Super Mario"`. Steam already stores
 * the bare title. Returns null for Online / Away / Busy.
 */
fun playingGameTitleFromStatus(status: String?): String? {
    val raw = status?.trim().orEmpty()
    if (raw.isBlank()) return null
    if (raw.equals("Online", ignoreCase = true) ||
        raw.equals("Away", ignoreCase = true) ||
        raw.equals("Busy", ignoreCase = true) ||
        raw.equals("Offline", ignoreCase = true)
    ) {
        return null
    }
    val prefixes = listOf("Playing ", "playing ")
    for (prefix in prefixes) {
        if (raw.startsWith(prefix, ignoreCase = true) && raw.length > prefix.length) {
            return raw.substring(prefix.length).trim().takeIf { it.isNotBlank() }
        }
    }
    return null
}

/** Discord Social SDK often only says they are in a game; name the title when we have one. */
fun discordPlayingGameTitle(currentGame: String?, group: String): String? {
    currentGame?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    if (group == "online_game") return "a game"
    return null
}
