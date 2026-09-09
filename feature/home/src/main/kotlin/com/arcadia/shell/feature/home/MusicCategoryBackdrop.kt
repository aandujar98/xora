package com.arcadia.shell.feature.home

/** Bundled white-background wave loop (GitHub tag `music-BG-mask`). */
internal const val MUSIC_WAVE_MASK_ASSET = "music/wave_mask.mp4"

internal const val MUSIC_WAVE_MASK_URI = "asset:///$MUSIC_WAVE_MASK_ASSET"

/**
 * When music is playing on the Music column, the XMB backdrop is the track's cover.
 * The wave mask is a separate Multiply layer so white in the video drops out.
 */
internal data class MusicCategoryBackdrop(
    val showCover: Boolean,
    val coverPath: String?,
    val showWaveMask: Boolean,
)

internal fun musicCategoryBackdrop(
    category: XoraXmbCategory,
    playing: Boolean,
    enabled: Boolean,
    coverPath: String?,
): MusicCategoryBackdrop {
    val show = enabled && playing && category == XoraXmbCategory.Music
    return MusicCategoryBackdrop(
        showCover = show,
        coverPath = coverPath?.takeIf { show && it.isNotBlank() },
        showWaveMask = show,
    )
}
