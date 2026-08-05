package com.arcadia.shell.retroachievements

/**
 * Connect API login2 session. [token] is the Connect token (`t=`), which is distinct from the
 * control-panel Web API key (`y=`). SORA still needs a Web API key for profile / progress calls.
 */
data class RaLoginSession(
    val username: String,
    val token: String,
)

/** Fresh Connect login for XOrA Emulator, including raw login2 JSON for rcheevos. */
data class RaEmulatorLogin(
    val session: RaLoginSession,
    val loginJson: String,
)

/** Prefetched Connect game payloads so the emulator can seed rcheevos offline of Cloudflare. */
data class RaEmulatorGameSession(
    val patchJson: String,
    val startSessionJson: String,
)

/** Outcome of username+password sign-in against RetroAchievements. */
sealed interface RaPasswordLoginResult {
    data class SignedIn(val profile: RaProfile) : RaPasswordLoginResult
    /**
     * Password was accepted, but the Connect token cannot authorize the Web API.
     * Caller should collect the control-panel Web API key once (username is already verified).
     */
    data class NeedsWebApiKey(
        val username: String,
        /** Connect token from the successful password login — keep for XOrA Emulator. */
        val connectToken: String,
    ) : RaPasswordLoginResult
}

data class RaProfile(
    val username: String,
    val totalPoints: Int,
    val totalSoftcorePoints: Int,
) {
    /** Public media CDN path for the signed-in user's profile picture. */
    val userPicUrl: String get() = userPicUrlFor(username)

    companion object {
        fun userPicUrlFor(username: String): String =
            "https://media.retroachievements.org/UserPic/${username.trim()}.png"
    }
}

data class RaAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String,
    val displayOrder: Int,
    val earned: Boolean,
    val earnedHardcore: Boolean,
) {
    val badgeUrl: String
        get() = "https://media.retroachievements.org/Badge/$badgeName.png"
}

data class RaGameProgress(
    val gameId: Int,
    val title: String,
    val consoleName: String,
    val numAchievements: Int,
    val numAwardedToUser: Int,
    val numAwardedToUserHardcore: Int,
    val achievements: List<RaAchievement>,
) {
    val progressLabel: String
        get() = if (numAchievements <= 0) "0/0" else "$numAwardedToUser/$numAchievements"
}

data class RaRecentUnlock(
    val achievementId: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String,
    val gameTitle: String,
    val consoleName: String,
    val hardcore: Boolean,
    val date: String,
) {
    val badgeUrl: String
        get() = "https://media.retroachievements.org/Badge/$badgeName.png"
}

sealed interface RaGameLookup {
    data class Matched(val progress: RaGameProgress) : RaGameLookup
    /** ROM cannot be hashed (unsupported archive, disc format, missing file, …). */
    data object NoHash : RaGameLookup
    /**
     * Hash was computed and the Connect API answered successfully, but no game is linked
     * to that MD5.
     */
    data class NoGame(val md5: String, val hashedBytes: Long) : RaGameLookup
    /** Network / HTTP / auth failure (includes distinct 403 WAF messaging). */
    data class Failed(val message: String) : RaGameLookup
}

/** One row from [API_GetUserCompletionProgress] — games the user has RA progress on. */
data class RaCompletionGame(
    val gameId: Int,
    val title: String,
    val imageIconPath: String,
    val consoleId: Int,
    val consoleName: String,
    val maxPossible: Int,
    val numAwarded: Int,
    val numAwardedHardcore: Int,
    val mostRecentAwardedDate: String?,
    val highestAwardKind: String?,
) {
    val imageIconUrl: String
        get() = when {
            imageIconPath.isBlank() -> ""
            imageIconPath.startsWith("http") -> imageIconPath
            else -> "https://media.retroachievements.org$imageIconPath"
        }

    val progressLabel: String
        get() = if (maxPossible <= 0) "0/0" else "$numAwarded/$maxPossible"

    val completionFraction: Float
        get() = if (maxPossible <= 0) 0f else (numAwarded.toFloat() / maxPossible).coerceIn(0f, 1f)

    val isMastered: Boolean
        get() = highestAwardKind.equals("mastered", ignoreCase = true) ||
            (maxPossible > 0 && numAwardedHardcore >= maxPossible)
}
