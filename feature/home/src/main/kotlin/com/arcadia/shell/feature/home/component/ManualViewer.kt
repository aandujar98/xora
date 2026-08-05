package com.arcadia.shell.feature.home.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a game manual on the companion screen: a scanned page image, or a PDF paged through
 * [PdfRenderer].
 *
 * PDFs are rendered rather than handed to a WebView, which cannot display them at all, and rather
 * than fired off as an `ACTION_VIEW` intent, which would open a document reader on the *primary*
 * display and interrupt the running game.
 */
@Composable
fun ManualViewer(
    path: String,
    modifier: Modifier = Modifier,
) {
    val lower = remember(path) { path.lowercase() }
    if (IMAGE_EXTENSIONS.any { lower.endsWith(it) }) {
        ArtworkImage(
            path = path,
            contentDescription = "Manual",
            fallbackText = "Manual",
            contentScale = ContentScale.Fit,
            cacheInMemory = false,
            decodeMaxEdgePx = PAGE_MAX_WIDTH_PX,
            modifier = modifier.background(Color.Black),
        )
        return
    }

    // Anything else is treated as a PDF: ScreenScraper only ever serves manuals in that format, and
    // sidecar files beside a ROM are conventionally .pdf too.
    val manual = remember(path) { PdfManual.open(path) }
    DisposableEffect(manual) {
        onDispose { manual?.close() }
    }

    if (manual == null || manual.pageCount == 0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "This manual could not be opened.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items = (0 until manual.pageCount).toList(), key = { it }) { index ->
                PdfPage(
                    manual = manual,
                    index = index,
                    widthPx = widthPx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    manual: PdfManual,
    index: Int,
    widthPx: Int,
    modifier: Modifier = Modifier,
) {
    val page by produceState<ImageBitmap?>(null, manual, index, widthPx) {
        if (widthPx <= 0) return@produceState
        value = withContext(Dispatchers.IO) { manual.render(index, widthPx)?.asImageBitmap() }
    }

    val bitmap = page
    if (bitmap == null) {
        Box(
            modifier = modifier.aspectRatio(PLACEHOLDER_ASPECT),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }

    Image(
        bitmap = bitmap,
        contentDescription = "Manual page ${index + 1}",
        contentScale = ContentScale.FillWidth,
        modifier = modifier,
    )
}

/**
 * One open PDF file. [PdfRenderer] allows a single open page at a time and is not thread-safe, so
 * every render is serialised through this holder.
 */
private class PdfManual private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int get() = runCatching { renderer.pageCount }.getOrDefault(0)

    @Synchronized
    fun render(index: Int, targetWidthPx: Int): Bitmap? = runCatching {
        val page = renderer.openPage(index)
        try {
            val width = targetWidthPx.coerceIn(PAGE_MIN_WIDTH_PX, PAGE_MAX_WIDTH_PX)
            val height = (width.toFloat() / page.width * page.height)
                .toInt()
                .coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Scanned manuals are transparent where the paper is, so an explicit white sheet keeps
            // the text readable against the dark panel.
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }.getOrNull()

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(path: String): PdfManual? = runCatching {
            val file = File(path)
            if (!file.isFile || file.length() == 0L) return null
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            PdfManual(descriptor, PdfRenderer(descriptor))
        }.getOrNull()
    }
}

private val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")
private const val PAGE_MIN_WIDTH_PX = 480
private const val PAGE_MAX_WIDTH_PX = 1400
private const val PLACEHOLDER_ASPECT = 0.72f
