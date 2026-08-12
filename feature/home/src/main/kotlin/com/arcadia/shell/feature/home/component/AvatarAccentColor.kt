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
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val drawable = result.image.asDrawable(context.resources)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            sampleVibrantColor(bitmap)
        }.getOrNull()
    }

/**
 * Picks a saturated, mid-brightness sample so the username stays readable on dark glass.
 */
internal fun sampleVibrantColor(bitmap: Bitmap): Color? {
    val step = max(1, min(bitmap.width, bitmap.height) / 24)
    var bestScore = 0f
    var bestArgb = 0
    var found = false

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
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
                // Prefer vivid mid-tones; skip near-black / near-white.
                if (lightness in 0.18f..0.82f && saturation >= 0.12f) {
                    val score = saturation * 1.35f + (1f - kotlin.math.abs(lightness - 0.52f))
                    if (!found || score > bestScore) {
                        bestScore = score
                        bestArgb = pixel or 0xFF000000.toInt()
                        found = true
                    }
                }
            }
            x += step
        }
        y += step
    }

    if (!found) return null
    val color = Color(bestArgb)
    // Lift very dark accents so pink/green usernames stay punchy on charcoal glass.
    return boostForUsername(color)
}

private fun boostForUsername(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.45f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.62f, 0.96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
