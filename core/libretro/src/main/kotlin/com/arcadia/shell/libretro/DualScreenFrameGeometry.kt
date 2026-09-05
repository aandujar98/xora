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
                bottom = DualScreenFrameRect(0, topH, width, height - topH),
            )
        }
        threeDsSideBySide(width, height, platformId)?.let { return it }
        val leftW = width / 2
        return DualScreenFrameSplit(
            kind = DualScreenSplitKind.SideBySide,
            top = DualScreenFrameRect(0, 0, leftW, height),
            bottom = DualScreenFrameRect(leftW, 0, width - leftW, height),
        )
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
        )
    }
}
