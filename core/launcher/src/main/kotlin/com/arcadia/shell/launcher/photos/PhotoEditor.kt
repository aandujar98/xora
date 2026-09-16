package com.arcadia.shell.launcher.photos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Non-destructive photo edits: the result is always inserted as a NEW MediaStore image under
 * Pictures/XOrA. The original file is never rewritten.
 */
@Singleton
class PhotoEditor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Applies [rotationDeg] (multiples of 90) and an optional center crop to [cropAspect]
     * (width / height, applied after rotation), then saves a copy. Returns the new content uri.
     */
    suspend fun saveEditedCopy(
        sourceUri: Uri,
        rotationDeg: Int,
        cropAspect: Float?,
        baseName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        val source = decode(sourceUri) ?: return@withContext null
        val edited = runCatching { transform(source, rotationDeg, cropAspect) }
            .getOrNull() ?: return@withContext null

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanBase = baseName.substringBeforeLast('.').ifBlank { "photo" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${cleanBase}_edit_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XOrA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext null

        val written = runCatching {
            resolver.openOutputStream(target)?.use { output ->
                edited.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            } == true
        }.getOrDefault(false)

        if (!written) {
            runCatching { resolver.delete(target, null, null) }
            return@withContext null
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        runCatching { resolver.update(target, values, null, null) }
        target
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val edge = maxOf(info.size.width, info.size.height)
            if (edge > MAX_EDIT_EDGE_PX) {
                val scale = MAX_EDIT_EDGE_PX.toFloat() / edge
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrNull()

    private fun transform(source: Bitmap, rotationDeg: Int, cropAspect: Float?): Bitmap {
        val rotation = ((rotationDeg % 360) + 360) % 360
        val rotated = if (rotation == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        val aspect = cropAspect ?: return rotated
        val current = rotated.width.toFloat() / rotated.height
        if (kotlin.math.abs(current - aspect) < 0.01f) return rotated
        val (cropWidth, cropHeight) = if (current > aspect) {
            // Too wide — trim the sides.
            (rotated.height * aspect).roundToInt() to rotated.height
        } else {
            rotated.width to (rotated.width / aspect).roundToInt()
        }
        val width = min(cropWidth, rotated.width).coerceAtLeast(1)
        val height = min(cropHeight, rotated.height).coerceAtLeast(1)
        val left = (rotated.width - width) / 2
        val top = (rotated.height - height) / 2
        return Bitmap.createBitmap(rotated, left, top, width, height)
    }

    private companion object {
        const val JPEG_QUALITY = 92
        /** Editing decodes to at most this edge — full 100MP originals are never inflated. */
        const val MAX_EDIT_EDGE_PX = 3072
    }
}
