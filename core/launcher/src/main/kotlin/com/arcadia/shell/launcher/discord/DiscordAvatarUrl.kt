package com.arcadia.shell.launcher.discord

/**
 * Discord serves animated avatars from a hash that starts with `a_`. The Social SDK and many
 * CDN copies still hand back `.png` / `.webp` for those hashes, which Coil freezes on frame one.
 * Swap the still extension for `.gif` so the LT social pill (and every other avatar) can play.
 */
fun preferAnimatedDiscordAvatarUrl(url: String?): String? {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return url
    val match = DISCORD_STILL_ANIMATED_AVATAR.matchEntire(raw) ?: return raw
    val query = match.groupValues[2]
    return "${match.groupValues[1]}.gif$query"
}

private val DISCORD_STILL_ANIMATED_AVATAR = Regex(
    """^(https?://(?:cdn\.discordapp\.com|media\.discordapp\.net)/avatars/\d+/a_[A-Za-z0-9_]+)\.(?:png|webp|jpg|jpeg)(\?.*)?$""",
    RegexOption.IGNORE_CASE,
)
