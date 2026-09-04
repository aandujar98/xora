package com.arcadia.shell.feature.home.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Resolves a vibrant accent from the profile avatar for the RT username tint.
 * Falls back to [fallback] (usually the avatar preset color) while loading or on failure.
 */
@Composable
fun rememberAvatarAccentColor(
    imageModel: String?,
    fallback: Color,
): Color {
    val context = LocalContext.current
    var accent by remember(imageModel, fallback) { mutableStateOf(fallback) }

    LaunchedEffect(imageModel, fallback) {
        if (imageModel.isNullOrBlank()) {
            accent = fallback
            return@LaunchedEffect
        }
        accent = extractDominantAccent(context, imageModel) ?: fallback
    }

    return accent
}

private suspend fun extractDominantAccent(context: Context, model: Any): Color? =
    withContext(Dispatchers.IO) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(Size(96, 96))
                .allowHardware(false)
                .build()
            val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
            val bitmap = bitmapFromCoil(context, result) ?: return@runCatching null
            sampleVibrantColor(bitmap)
        }.getOrNull()
    }

private fun bitmapFromCoil(context: Context, result: SuccessResult): Bitmap? {
    val image = result.image
    if (image is BitmapImage) return image.bitmap
    runCatching { return image.toBitmap() }
    val drawable = image.asDrawable(context.resources)
    return (drawable as? BitmapDrawable)?.bitmap
}

/**
 * Center-weighted hue of the disc so a pink icon yields a pink username, not a blue shirt/sky
 * sample from the rim.
 */
internal fun sampleVibrantColor(bitmap: Bitmap): Color? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val step = max(1, min(width, height) / 32)
    val cx = (width - 1) / 2f
    val cy = (height - 1) / 2f
    val radius = min(width, height) / 2f

    val bucketCount = 18
    val sumR = FloatArray(bucketCount)
    val sumG = FloatArray(bucketCount)
    val sumB = FloatArray(bucketCount)
    val weight = FloatArray(bucketCount)

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val dx = x - cx
            val dy = y - cy
            val dist = hypot(dx, dy)
            if (dist <= radius * 0.92f) {
                val pixel = bitmap.getPixel(x, y)
                val a = (pixel ushr 24) and 0xFF
                if (a >= 160) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val maxC = max(r, max(g, b)).toFloat()
                    val minC = min(r, min(g, b)).toFloat()
                    val lightness = (maxC + minC) / (2f * 255f)
                    val saturation = if (maxC <= 0f) 0f else (maxC - minC) / maxC
                    if (lightness in 0.12f..0.88f && saturation >= 0.08f) {
                        val hue = hueDegrees(r, g, b)
                        val bucket = ((hue / 360f) * bucketCount).toInt().coerceIn(0, bucketCount - 1)
                        val centerW = 1.35f - (dist / radius)
                        val score = saturation * 1.1f + (1f - kotlin.math.abs(lightness - 0.52f))
                        val w = centerW * score
                        sumR[bucket] += r * w
                        sumG[bucket] += g * w
                        sumB[bucket] += b * w
                        weight[bucket] += w
                    }
                }
            }
            x += step
        }
        y += step
    }

    var best = -1
    var bestWeight = 0f
    for (i in weight.indices) {
        if (weight[i] > bestWeight) {
            bestWeight = weight[i]
            best = i
        }
    }
    if (best < 0 || bestWeight <= 0f) return null
    val color = Color(
        red = (sumR[best] / bestWeight / 255f).coerceIn(0f, 1f),
        green = (sumG[best] / bestWeight / 255f).coerceIn(0f, 1f),
        blue = (sumB[best] / bestWeight / 255f).coerceIn(0f, 1f),
    )
    return boostForUsername(color)
}

private fun hueDegrees(r: Int, g: Int, b: Int): Float {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val maxC = max(rf, max(gf, bf))
    val minC = min(rf, min(gf, bf))
    val delta = maxC - minC
    if (delta <= 1e-5f) return 0f
    val hue = when (maxC) {
        rf -> ((gf - bf) / delta) % 6f
        gf -> (bf - rf) / delta + 2f
        else -> (rf - gf) / delta + 4f
    }
    var deg = hue * 60f
    if (deg < 0f) deg += 360f
    return deg
}

private fun boostForUsername(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.28f, 0.92f)
    hsv[2] = hsv[2].coerceIn(0.58f, 0.96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
