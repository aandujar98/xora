package com.arcadia.shell.libretro

/**
 * How a packed DS / 3DS framebuffer is cut so the touch (bottom) LCD can fill a
 * second physical panel.
 */
enum class DualScreenSplitKind {
    /** Top LCD above bottom LCD (melonDS / Citra default when expand forces stacked). */
    Stacked,
    /** Top LCD left, bottom LCD right — fallback when a core ignores the stacked option. */
    SideBySide,
    ;

    val bottomPointerTarget: DualScreenPointerTarget
        get() = when (this) {
            Stacked -> DualScreenPointerTarget.BottomHalf
            SideBySide -> DualScreenPointerTarget.BottomRight
        }
}

data class DualScreenFrameRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

data class DualScreenFrameSplit(
    val kind: DualScreenSplitKind,
    val top: DualScreenFrameRect,
    val bottom: DualScreenFrameRect,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    val bottomPointerTarget: DualScreenPointerTarget get() = kind.bottomPointerTarget
}

/**
 * Locates the top and bottom LCDs inside a packed Libretro frame.
 *
 * Expand forces a stacked core layout, so most frames are taller than they are wide
 * (DS 256×384, 3DS 400×480, and integer scales of those). Cores that ignore the
 * option still emit side-by-side (DS 512×192, 3DS 720×240).
 */
object DualScreenFrameGeometry {

    fun split(width: Int, height: Int, platformId: String = ""): DualScreenFrameSplit? {
        if (width < 2 || height < 2) return null
        if (height >= width) {
            val topH = height / 2
            return DualScreenFrameSplit(
                kind = DualScreenSplitKind.Stacked,
                top = DualScreenFrameRect(0, 0, width, topH),
                bottom = stackedBottom(width, height, topH, platformId),
                frameWidth = width,
                frameHeight = height,
            )
        }
        threeDsSideBySide(width, height, platformId)?.let { return it }
        val leftW = width / 2
        return DualScreenFrameSplit(
            kind = DualScreenSplitKind.SideBySide,
            top = DualScreenFrameRect(0, 0, leftW, height),
            bottom = DualScreenFrameRect(leftW, 0, width - leftW, height),
            frameWidth = width,
            frameHeight = height,
        )
    }

    /**
     * 3DS stacked frames are 400×480 (and integer scales). The bottom LCD is 320×240
     * centered in the lower 400×240 band — sending the full-width half to the second
     * panel stretches the side padding and the touch screen looks letterboxed.
     */
    private fun stackedBottom(
        width: Int,
        height: Int,
        topH: Int,
        platformId: String,
    ): DualScreenFrameRect {
        val bottomH = height - topH
        if (!platformId.equals("3ds", ignoreCase = true)) {
            return DualScreenFrameRect(0, topH, width, bottomH)
        }
        val scale = when {
            width % 400 == 0 &&
                width / 400 in 1..8 &&
                height == 480 * (width / 400) -> width / 400
            else -> null
        }
        if (scale != null) {
            return DualScreenFrameRect(
                x = 40 * scale,
                y = 240 * scale,
                width = 320 * scale,
                height = 240 * scale,
            )
        }
        val bottomW = ((width * 320L) / 400L).toInt().coerceAtLeast(1)
        val x = ((width - bottomW) / 2).coerceAtLeast(0)
        return DualScreenFrameRect(x, topH, bottomW, bottomH)
    }

    /**
     * 3DS side-by-side is 400+320, not a 50/50 cut. Integer scales keep that ratio.
     */
    private fun threeDsSideBySide(
        width: Int,
        height: Int,
        platformId: String,
    ): DualScreenFrameSplit? {
        if (!platformId.equals("3ds", ignoreCase = true)) return null
        val scale = when {
            height % 240 == 0 && height / 240 in 1..8 -> height / 240
            else -> return null
        }
        val nativeWide = 720 * scale
        if (kotlin.math.abs(width - nativeWide) > scale) return null
        val topW = 400 * scale
        return DualScreenFrameSplit(
            kind = DualScreenSplitKind.SideBySide,
            top = DualScreenFrameRect(0, 0, topW, height),
            bottom = DualScreenFrameRect(topW, 0, width - topW, height),
            frameWidth = width,
            frameHeight = height,
        )
    }
}
