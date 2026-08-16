package com.arcadia.shell.libretro.netplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** JPEG + optional PCM for a host-authoritative home-console netplay frame. */
object XoraNetplayVideo {
    const val MAX_WIDTH = 400
    const val ONLINE_MAX_WIDTH = 240
    private const val JPEG_QUALITY = 42
    const val MAX_BYTES = 24_000
    /** Keep online JPEGs small enough to chunk into a few Nakama match_data packets. */
    const val ONLINE_MAX_BYTES = 6_000

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
        val scaled = if (w > maxWidth) {
            val nh = (h * maxWidth) / w
            Bitmap.createScaledBitmap(src, maxWidth, nh.coerceAtLeast(1), true).also {
                if (it !== src) src.recycle()
            }
        } else {
            src
        }
        return try {
            for (quality in intArrayOf(JPEG_QUALITY, 32, 24, 16)) {
                val out = ByteArrayOutputStream()
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) continue
                val bytes = out.toByteArray()
                if (bytes.size <= maxBytes) return bytes
            }
            null
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    fun bitmapFromJpeg(jpeg: ByteArray): Bitmap? {
        if (jpeg.isEmpty()) return null
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }
}
