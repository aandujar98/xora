package com.arcadia.shell.feature.home

/** Bundled white-background wave loop (GitHub tag `music-BG-mask`). */
internal const val MUSIC_WAVE_MASK_ASSET = "music/wave_black.mp4"

internal const val MUSIC_WAVE_MASK_URI = "asset:///$MUSIC_WAVE_MASK_ASSET"

/**
 * While music plays, the XMB backdrop is the track's cover — on the Music column and anywhere
 * else you wander, so a song keeps the screen instead of ending at the column it started on.
 * The wave mask is a separate Multiply layer so white in the video drops out.
 *
 * [playing] is "actively playing", not "has a track loaded": pausing fades the cover and the
 * wave back out, and pressing play brings them back.
 */
internal data class MusicCategoryBackdrop(
    val showCover: Boolean,
    val coverPath: String?,
    val showWaveMask: Boolean,
)

internal fun musicCategoryBackdrop(
    category: XoraXmbCategory,
    depth: XoraXmbDepth,
    playing: Boolean,
    enabled: Boolean,
    coverPath: String?,
    /**
     * A track is loaded, whether or not it is running. Background Media belongs to the song, so
     * it stays up through a pause — dropping back to the album cover made pausing look like the
     * song had been unloaded.
     */
    hasTrack: Boolean = playing,
    /** Background Media set on the playing song or its album; beats the cover when present. */
    backgroundMediaPath: String? = null,
    /**
     * The column under the cursor has game art of its own to show — a wallpaper, hero or box art.
     * Off the Music column that art wins, and the song's backdrop fades out until you leave it.
     */
    gameMediaPresent: Boolean = false,
): MusicCategoryBackdrop {
    // Music owns its own column outright; elsewhere it fills only the screens a game is not
    // already claiming, so browsing a library still looks like the library.
    val onMusic = category == XoraXmbCategory.Music
    val allowed = enabled && (onMusic || !gameMediaPresent)
    val live = allowed && playing
    val media = backgroundMediaPath?.takeIf {
        allowed && (playing || (hasTrack && onMusic)) && it.isNotBlank()
    }
    return MusicCategoryBackdrop(
        showCover = live || media != null,
        coverPath = media ?: coverPath?.takeIf { live && it.isNotBlank() },
        // Now Playing is the cover's own page — the wave belongs to the column behind it, and
        // reading a tracklist through a moving mask is what it is there to decorate.
        showWaveMask = live && depth != XoraXmbDepth.NowPlaying,
    )
}
