package com.arcadia.shell.libretro

/**
 * Maps a finger on a DS / 3DS panel to Libretro [RETRO_DEVICE_POINTER] coordinates.
 *
 * Pointer space is −32767…32767 over the **core framebuffer** the frontend received,
 * not the physical display. When expand splits a stacked frame across two panels,
 * the bottom display is only the lower half of that space.
 */
data class LibretroPointer(
    val x: Short,
    val y: Short,
    val pressed: Boolean,
)

enum class DualScreenPointerTarget {
    /** One view shows the full stacked (or side-by-side) framebuffer. */
    Combined,
    /** Top panel while expand is on — DS / 3DS stylus lives on the other screen. */
    TopHalf,
    /** Bottom panel while expand is on — the touch screen. */
    BottomHalf,
}

object DualScreenPointer {
    const val AXIS_MIN = -0x7fff
    const val AXIS_MAX = 0x7fff

    /**
     * Letterboxed image rectangle inside a view. [fill] is FIT_XY / stretch.
     * Returns `[left, top, right, bottom]` in view pixels.
     */
    fun contentRect(
        viewW: Int,
        viewH: Int,
        contentW: Int,
        contentH: Int,
        fill: Boolean,
    ): IntArray {
        if (viewW <= 0 || viewH <= 0) return intArrayOf(0, 0, viewW, viewH)
        if (fill || contentW <= 0 || contentH <= 0) {
            return intArrayOf(0, 0, viewW, viewH)
        }
        val scale = minOf(viewW.toFloat() / contentW, viewH.toFloat() / contentH)
        val rw = contentW * scale
        val rh = contentH * scale
        val left = ((viewW - rw) / 2f).toInt().coerceAtLeast(0)
        val top = ((viewH - rh) / 2f).toInt().coerceAtLeast(0)
        return intArrayOf(
            left,
            top,
            (left + rw).toInt().coerceAtMost(viewW),
            (top + rh).toInt().coerceAtMost(viewH),
        )
    }

    fun mapViewToPointer(
        viewX: Float,
        viewY: Float,
        viewW: Int,
        viewH: Int,
        contentW: Int,
        contentH: Int,
        fill: Boolean,
        target: DualScreenPointerTarget,
        pressed: Boolean,
    ): LibretroPointer? {
        if (target == DualScreenPointerTarget.TopHalf) return null
        if (viewW <= 0 || viewH <= 0) return null
        val rect = contentRect(viewW, viewH, contentW, contentH, fill)
        val left = rect[0].toFloat()
        val top = rect[1].toFloat()
        val width = (rect[2] - rect[0]).toFloat()
        val height = (rect[3] - rect[1]).toFloat()
        if (width < 1f || height < 1f) return null
        val nx = ((viewX - left) / width).coerceIn(0f, 1f)
        val ny = ((viewY - top) / height).coerceIn(0f, 1f)
        val inside = viewX >= left && viewX <= rect[2] && viewY >= top && viewY <= rect[3]
        val x = lerp(AXIS_MIN, AXIS_MAX, nx).toInt().toShort()
        val y = when (target) {
            DualScreenPointerTarget.Combined -> lerp(AXIS_MIN, AXIS_MAX, ny).toInt().toShort()
            DualScreenPointerTarget.BottomHalf -> lerp(0, AXIS_MAX, ny).toInt().toShort()
            DualScreenPointerTarget.TopHalf -> return null
        }
        return LibretroPointer(x = x, y = y, pressed = pressed && inside)
    }

    private fun lerp(a: Int, b: Int, t: Float): Float = a + (b - a) * t
}
