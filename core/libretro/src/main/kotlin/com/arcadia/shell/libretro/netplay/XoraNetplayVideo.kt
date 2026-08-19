package com.arcadia.shell.libretro.netplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * Host framebuffer for SharedConsole netplay (NES, SNES, GC, …).
 *
 * Players 2–4 do not run a second core — they watch this picture. 8/16-bit cores keep
 * native width. Hi-res home consoles downscale with bilinear filtering. Online uses a
 * Nakama-safe byte budget that still looks decent on typical home Wi‑Fi (~3 Mbps).
 */
object XoraNetplayVideo {
    const val MAX_WIDTH = 400
    /** Online matches LAN width so GameCube / N64 are not crushed to 256px. NES/SNES stay native. */
    const val ONLINE_MAX_WIDTH = 400
    const val MAX_BYTES = 24_000
    /**
     * Fits in Nakama chunks (900 B × 28). ~20 KB at 15 fps is about 2.4 Mbps —
     * playable on decent Wi‑Fi, sharp on a good connection.
     */
    const val ONLINE_MAX_BYTES = 20_000
    const val MIN_QUALITY = 48

    fun targetSize(srcW: Int, srcH: Int, maxWidth: Int): Pair<Int, Int> {
        if (srcW <= 0 || srcH <= 0 || maxWidth <= 0) return 0 to 0
        if (srcW <= maxWidth) return srcW to srcH
        val h = ((srcH.toLong() * maxWidth) / srcW).toInt().coerceAtLeast(1)
        return maxWidth to h
    }

    fun widthSteps(maxWidth: Int): IntArray {
        val steps = LinkedHashSet<Int>()
        steps += maxWidth.coerceAtLeast(1)
        for (width in intArrayOf(320, 256, 224, 192, 160, 128)) {
            if (width < maxWidth) steps += width
        }
        return steps.toIntArray()
    }

    fun jpegFromPackedRgba(
        packed: IntArray,
        maxWidth: Int = MAX_WIDTH,
        maxBytes: Int = MAX_BYTES,
    ): ByteArray? {
        if (packed.size < 2) return null
        val w = packed[0]
        val h = packed[1]
        if (w <= 0 || h <= 0 || packed.size < w * h + 2) return null
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        src.setPixels(packed, 2, w, 0, 0, w, h)
        var current: Bitmap = src
        val filterHiRes = w > 320
        return try {
            for (stepWidth in widthSteps(maxWidth)) {
                val (tw, th) = targetSize(current.width, current.height, stepWidth)
                if (tw <= 0 || th <= 0) continue
                if (tw != current.width || th != current.height) {
                    val scaled = Bitmap.createScaledBitmap(current, tw, th, filterHiRes)
                    if (scaled !== current && !current.isRecycled) current.recycle()
                    current = scaled
                }
                val qualities = if (stepWidth >= maxWidth) {
                    intArrayOf(76, 68, 60, MIN_QUALITY)
                } else {
                    intArrayOf(64, 56, MIN_QUALITY)
                }
                compressToBudget(current, qualities, maxBytes)?.let { return it }
            }
            null
        } finally {
            if (!current.isRecycled) current.recycle()
        }
    }

    fun bitmapFromJpeg(jpeg: ByteArray): Bitmap? {
        if (jpeg.isEmpty()) return null
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    private fun compressToBudget(
        bitmap: Bitmap,
        qualities: IntArray,
        maxBytes: Int,
    ): ByteArray? {
        for (format in compressFormats()) {
            for (quality in qualities) {
                val out = ByteArrayOutputStream()
                if (!bitmap.compress(format, quality, out)) continue
                val bytes = out.toByteArray()
                if (bytes.size in 1..maxBytes) return bytes
            }
        }
        return null
    }

    private fun compressFormats(): Array<Bitmap.CompressFormat> {
        return if (Build.VERSION.SDK_INT >= 30) {
            arrayOf(Bitmap.CompressFormat.WEBP_LOSSY, Bitmap.CompressFormat.JPEG)
        } else {
            @Suppress("DEPRECATION")
            arrayOf(Bitmap.CompressFormat.WEBP, Bitmap.CompressFormat.JPEG)
        }
    }
}
