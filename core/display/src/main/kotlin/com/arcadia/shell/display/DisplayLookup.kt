package com.arcadia.shell.display

import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Resolves a [Display] the same way [DisplayTopologyMonitor] enumerates panels.
 *
 * [DisplayManager.getDisplay] returns null for some private presentation displays
 * (AYN Thor-style bottom screens) even when [DisplayManager.DISPLAY_CATEGORY_PRESENTATION]
 * lists them. Overlay and Presentation hosts have to use this merge or the DS / 3DS
 * bottom LCD never reaches the second panel.
 */
fun DisplayManager.allLogicalDisplays(): List<Display> {
    val byId = LinkedHashMap<Int, Display>()
    displays.filter { it.isValid }.forEach { byId[it.displayId] = it }
    runCatching { getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION) }
        .getOrDefault(emptyArray())
        .filter { it.isValid }
        .forEach { display -> byId.putIfAbsent(display.displayId, display) }
    return byId.values.toList()
}

fun DisplayManager.resolveDisplay(displayId: Int): Display? =
    allLogicalDisplays().firstOrNull { it.displayId == displayId && it.isValid }
