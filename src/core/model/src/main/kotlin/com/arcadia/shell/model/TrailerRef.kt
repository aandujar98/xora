package com.arcadia.shell.model

/**
 * How a persisted trailer string should be played.
 *
 * Stored on [Game.trailerUrl] as either a direct media URI/path or a `youtube:` id prefix so the
 * shell can round-trip without a second table. YouTube refs may store comma-separated candidate
 * ids (`youtube:id1,id2`) so the player can fail over when a video blocks embedding.
 */
sealed interface TrailerRef {
    data class Direct(val uri: String) : TrailerRef
    data class YouTube(val videoIds: List<String>) : TrailerRef {
        init {
            require(videoIds.isNotEmpty()) { "YouTube trailer needs at least one video id" }
        }

        /** Primary / first candidate. */
        val videoId: String get() = videoIds.first()
    }
}

object TrailerRefs {
    private const val YOUTUBE_PREFIX = "youtube:"
    private val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")

    fun youtube(videoId: String): String = youtube(listOf(videoId))

    fun youtube(videoIds: List<String>): String {
        val ids = videoIds.map { it.trim() }.filter { it.matches(VIDEO_ID_PATTERN) }.distinct()
        require(ids.isNotEmpty()) { "No valid YouTube video ids" }
        return "$YOUTUBE_PREFIX${ids.joinToString(",")}"
    }

    fun encode(ref: TrailerRef): String = when (ref) {
        is TrailerRef.Direct -> ref.uri
        is TrailerRef.YouTube -> youtube(ref.videoIds)
    }

    fun parse(raw: String?): TrailerRef? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith(YOUTUBE_PREFIX, ignoreCase = true)) {
            val ids = value.substring(YOUTUBE_PREFIX.length)
                .split(',')
                .map { it.trim() }
                .filter { it.matches(VIDEO_ID_PATTERN) }
                .distinct()
            return ids.takeIf { it.isNotEmpty() }?.let { TrailerRef.YouTube(it) }
        }
        extractYouTubeId(value)?.let { return TrailerRef.YouTube(listOf(it)) }
        return TrailerRef.Direct(value)
    }

    fun extractYouTubeId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        // Bare 11-char ids occasionally show up from metadata providers.
        if (trimmed.matches(VIDEO_ID_PATTERN)) return trimmed

        val patterns = listOf(
            Regex("""(?:youtube\.com/watch\?(?:.*&)?v=|youtu\.be/|youtube\.com/embed/|youtube-nocookie\.com/embed/)([A-Za-z0-9_-]{11})"""),
            Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        )
        return patterns.firstNotNullOfOrNull { it.find(trimmed)?.groupValues?.getOrNull(1) }
    }
}
