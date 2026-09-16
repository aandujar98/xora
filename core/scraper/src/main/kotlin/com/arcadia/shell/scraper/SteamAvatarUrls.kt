package com.arcadia.shell.scraper

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Picks a playable Steam avatar URL, preferring an equipped animated GIF/WebP over the still JPG. */
internal object SteamAvatarUrls {

    fun fromPlayerSummary(obj: JsonObject): String? {
        val nested = runCatching { obj["animated_avatar"]?.jsonObject }.getOrNull()
            ?.let { playableAnimatedUrl(it) }
        if (!nested.isNullOrBlank()) return nested
        return obj["avatarfull"]?.jsonPrimitive?.contentOrNull
            ?: obj["avatarmedium"]?.jsonPrimitive?.contentOrNull
            ?: obj["avatar"]?.jsonPrimitive?.contentOrNull
    }

    fun fromEquippedProfileItems(response: JsonObject): String? {
        val root = runCatching { response["response"]?.jsonObject }.getOrNull() ?: response
        val animated = runCatching { root["animated_avatar"]?.jsonObject }.getOrNull() ?: return null
        return playableAnimatedUrl(animated)
    }

    fun playableAnimatedUrl(image: JsonObject): String? {
        val candidates = listOf(
            image["image_large"]?.jsonPrimitive?.contentOrNull,
            image["image_small"]?.jsonPrimitive?.contentOrNull,
        )
        return candidates.firstOrNull { url ->
            !url.isNullOrBlank() && isPlayableAnimatedAvatar(url)
        }
    }

    fun isPlayableAnimatedAvatar(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        return lower.endsWith(".gif") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".apng") ||
            lower.contains("format=gif") ||
            lower.contains("format=webp")
    }
}
