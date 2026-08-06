package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.launcher.InstalledAppSync
import java.io.File

/**
 * Displays scraped artwork or an installed-app icon, falling back to a readable text tile when
 * neither is available.
 *
 * [cacheInMemory] exists because hero art and grid thumbnails have opposite access patterns. Grid
 * tiles are small and revisited constantly as the selection moves, so caching them is essential.
 * Hero art is full-screen and only ever shown one at a time, so keeping every one the selection
 * passed over would fill the memory cache with images that will not be needed again and evict the
 * thumbnails that will.
 *
 * [decodeMaxEdgePx] caps the decoded bitmap edge so XMB / hub thumbs do not inflate full-resolution
 * scraper art (often 1080p+) into the heap.
 */
@Composable
fun ArtworkImage(
    path: String?,
    contentDescription: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cacheInMemory: Boolean = true,
    decodeMaxEdgePx: Int = THUMB_DECODE_MAX_EDGE_PX,
) {
    val platformContext = LocalPlatformContext.current
    val imageData = remember(path) { resolveImageData(platformContext, path) }

    if (imageData == null) {
        ArtworkFallback(text = fallbackText, modifier = modifier)
        return
    }

    val edge = decodeMaxEdgePx.coerceAtLeast(64)
    AsyncImage(
        model = ImageRequest.Builder(platformContext)
            .data(imageData)
            .crossfade(CROSSFADE_MS)
            .size(edge, edge)
            .maxBitmapSize(Size(edge, edge))
            .memoryCachePolicy(
                if (cacheInMemory) CachePolicy.ENABLED else CachePolicy.DISABLED,
            )
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
private fun ArtworkFallback(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

private fun resolveImageData(context: android.content.Context, path: String?): Any? {
    if (path.isNullOrBlank()) return null

    if (path.startsWith(InstalledAppSync.ICON_SCHEME)) {
        val packageName = path.removePrefix(InstalledAppSync.ICON_SCHEME)
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    if (path.startsWith("http://") ||
        path.startsWith("https://") ||
        path.startsWith("content://") ||
        path.startsWith("file://") ||
        path.startsWith("android.resource://")
    ) {
        return path
    }

    val file = File(path)
    return file.takeIf { it.exists() && it.length() > 0L }
}

private const val CROSSFADE_MS = 180

@XoraPreview
@Composable
private fun ArtworkImageFallbackPreview() {
    XoraPreviewTheme {
        ArtworkImage(
            path = null,
            contentDescription = null,
            fallbackText = "The Legend of Zelda",
            modifier = Modifier
                .padding(16.dp)
                .aspectRatio(0.72f),
        )
    }
}

/** XMB case tiles, hub shortcuts, system-pill thumbs. */
const val THUMB_DECODE_MAX_EDGE_PX = 384

/** Full-bleed hero / continue-card art (still capped for handheld RAM). */
const val HERO_DECODE_MAX_EDGE_PX = 1280
