package com.arcadia.shell.libretro.netplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** JPEG + optional PCM for a host-authoritative home-console netplay frame. */
object XoraNetplayVideo {
    private const val MAX_WIDTH = 400
    private const val JPEG_QUALITY = 42
    private const val MAX_BYTES = 24_000

    fun jpegFromPackedRgba(packed: IntArray): ByteArray? {
        if (packed.size < 2) return null
        val w = packed[0]
        val h = packed[1]
        if (w <= 0 || h <= 0 || packed.size < w * h + 2) return null
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        src.setPixels(packed, 2, w, 0, 0, w, h)
        val scaled = if (w > MAX_WIDTH) {
            val nh = (h * MAX_WIDTH) / w
            Bitmap.createScaledBitmap(src, MAX_WIDTH, nh.coerceAtLeast(1), true).also {
                if (it !== src) src.recycle()
            }
        } else {
            src
        }
        return try {
            val out = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return null
            val bytes = out.toByteArray()
            if (bytes.size > MAX_BYTES) null else bytes
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    fun bitmapFromJpeg(jpeg: ByteArray): Bitmap? {
        if (jpeg.isEmpty()) return null
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }
}
