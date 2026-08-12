package com.arcadia.shell.scraper

/**
 * Title/artist matching for album-art search results.
 *
 * Folder dumps of game soundtracks rarely carry a tidy artist tag — "Persona 3 Reload" as a
 * directory name should still beat "Persona 3 Reload Original Soundtrack" from iTunes after the
 * soundtrack suffix is stripped.
 */
object MusicArtMatcher {
    data class Candidate(
        val title: String,
        val artist: String,
        val artUrl: String,
    )

    fun isScrapableTitle(title: String): Boolean {
        val normalized = normalize(title)
        return normalized.length >= 2 && normalized !in GENERIC_TITLES
    }

    fun isGenericArtist(artist: String): Boolean =
        normalize(artist).let { it.isBlank() || it in GENERIC_ARTISTS }

    fun cacheKey(title: String, artist: String): String {
        val album = normalize(title).ifBlank { "_" }
        val who = if (isGenericArtist(artist)) "_" else normalize(artist)
        return "$album|$who"
    }

    fun searchQuery(title: String, artist: String): String {
        val album = title.trim()
        val who = artist.trim()
        return if (!isGenericArtist(who)) "$album $who" else album
    }

    fun itunesHiResArtUrl(url: String): String =
        url.replace(Regex("""\d+x\d+bb"""), "600x600bb")

    fun pickBest(
        queryTitle: String,
        queryArtist: String,
        candidates: List<Candidate>,
        minScore: Int = MIN_SCORE,
    ): Candidate? =
        candidates
            .map { it to score(queryTitle, queryArtist, it.title, it.artist) }
            .filter { it.second >= minScore }
            .maxByOrNull { it.second }
            ?.first

    fun score(
        queryTitle: String,
        queryArtist: String,
        resultTitle: String,
        resultArtist: String,
    ): Int {
        val query = normalize(queryTitle)
        val result = normalize(resultTitle)
        if (query.isBlank() || result.isBlank()) return 0

        var points = when {
            query == result -> 100
            result.contains(query) || query.contains(result) -> 70
            else -> {
                val queryWords = words(query)
                val resultWords = words(result)
                if (queryWords.isEmpty() || resultWords.isEmpty()) return 0
                val overlap = queryWords.intersect(resultWords).size
                if (overlap == 0) return 0
                val coverage = overlap.toFloat() / queryWords.size
                if (coverage < 0.5f) return 0
                (coverage * 50f).toInt()
            }
        }

        val wantedArtist = normalize(queryArtist)
        val gotArtist = normalize(resultArtist)
        if (!isGenericArtist(queryArtist)) {
            points += when {
                wantedArtist == gotArtist -> 25
                gotArtist.contains(wantedArtist) || wantedArtist.contains(gotArtist) -> 12
                else -> 0
            }
        }
        return points
    }

    fun normalize(raw: String): String =
        raw.lowercase()
            .replace('_', ' ')
            .replace('.', ' ')
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\[[^\]]*\]"""), " ")
            .replace(NOISE, " ")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun words(normalized: String): Set<String> =
        normalized.split(' ').filter { it.length > 1 }.toSet()

    private val NOISE = Regex(
        """\b(ost|original\s+soundtrack|soundtrack|official|complete|deluxe|edition|""" +
            """disc\s*\d+|cd\s*\d+|vol(?:ume)?\s*\d+)\b""",
    )

    private val GENERIC_TITLES = setOf(
        "unknown album",
        "unknown",
        "music",
        "track",
        "audio",
        "songs",
    )

    private val GENERIC_ARTISTS = setOf(
        "unknown artist",
        "unknown",
        "various artists",
        "various",
        "<unknown>",
    )

    private const val MIN_SCORE = 55
}
