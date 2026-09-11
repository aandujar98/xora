package com.arcadia.shell.feature.home

/** Bundled white-background wave loop (GitHub tag `music-BG-mask`). */
internal const val MUSIC_WAVE_MASK_ASSET = "music/wave_mask.mp4"

internal const val MUSIC_WAVE_MASK_URI = "asset:///$MUSIC_WAVE_MASK_ASSET"

/**
 * When music is playing on the Music column, the XMB backdrop is the track's cover.
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
): MusicCategoryBackdrop {
    val show = enabled && playing && category == XoraXmbCategory.Music
    return MusicCategoryBackdrop(
        showCover = show,
        coverPath = coverPath?.takeIf { show && it.isNotBlank() },
        // Now Playing is the cover's own page — the wave belongs to the column behind it, and
        // reading a tracklist through a moving mask is what it is there to decorate.
        showWaveMask = show && depth != XoraXmbDepth.NowPlaying,
    )
}
