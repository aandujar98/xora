package com.arcadia.shell.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaRouter
import android.os.Build
import android.view.Display

/**
 * Resolves a [Display] the same way [DisplayTopologyMonitor] enumerates panels.
 *
 * [DisplayManager.getDisplay] returns null for some private presentation displays
 * (AYN Thor-style bottom screens) even when [DisplayManager.DISPLAY_CATEGORY_PRESENTATION]
 * lists them. HDMI often only appears on [MediaRouter.ROUTE_TYPE_LIVE_VIDEO].
 */
fun DisplayManager.allLogicalDisplays(): List<Display> {
    val byId = LinkedHashMap<Int, Display>()
    displays.filter { it.isValid }.forEach { byId[it.displayId] = it }
    runCatching { getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION) }
        .getOrDefault(emptyArray())
        .filter { it.isValid }
        .forEach { display -> byId.putIfAbsent(display.displayId, display) }
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching { getDisplays(DISPLAY_CATEGORY_ALL) }
            .getOrDefault(emptyArray())
            .filter { it.isValid }
            .forEach { display -> byId.putIfAbsent(display.displayId, display) }
    }
    return byId.values.toList()
}

fun Context.allGameDisplays(): List<Display> {
    val displayManager = getSystemService(DisplayManager::class.java) ?: return emptyList()
    val byId = LinkedHashMap<Int, Display>()
    displayManager.allLogicalDisplays().forEach { byId[it.displayId] = it }
    runCatching {
        val router = getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
        val route = router?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
        route?.presentationDisplay?.takeIf { it.isValid }?.let { display ->
            byId.putIfAbsent(display.displayId, display)
        }
    }
    return byId.values.toList()
}

fun DisplayManager.resolveDisplay(displayId: Int): Display? =
    allLogicalDisplays().firstOrNull { it.displayId == displayId && it.isValid }

private const val DISPLAY_CATEGORY_ALL = "android.hardware.display.category.ALL"
