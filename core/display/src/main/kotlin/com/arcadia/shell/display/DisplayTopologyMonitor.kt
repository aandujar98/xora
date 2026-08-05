package com.arcadia.shell.display

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.arcadia.shell.model.DisplayTopology
import com.arcadia.shell.model.ShellDisplay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Watches the set of attached displays. Clamshell handhelds report both panels up front, but an
 * external screen over USB-C or AirPlay-style casting can appear and vanish at any moment, so the
 * shell treats topology as a stream rather than something read once at startup.
 */
class DisplayTopologyMonitor(private val context: Context) {

    private val displayManager = requireNotNull(context.getSystemService(DisplayManager::class.java))

    private val supportsActivitiesOnSecondaryDisplays: Boolean =
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
        )

    fun current(): DisplayTopology = readTopology()

    fun topology(): Flow<DisplayTopology> = callbackFlow {
        trySend(readTopology())

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                trySend(readTopology())
            }

            override fun onDisplayRemoved(displayId: Int) {
                trySend(readTopology())
            }

            override fun onDisplayChanged(displayId: Int) {
                trySend(readTopology())
            }
        }

        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        awaitClose { displayManager.unregisterDisplayListener(listener) }
    }.distinctUntilChanged()

    private fun readTopology() = DisplayTopology(
        displays = displayManager.displays.filter { it.isValid }.map { it.toShellDisplay() },
        supportsActivitiesOnSecondaryDisplays = supportsActivitiesOnSecondaryDisplays,
    )

    private fun Display.toShellDisplay(): ShellDisplay {
        // A display context reports the metrics of that specific screen; the deprecated
        // Display.getMetrics would report the primary panel's values instead.
        val metrics = context.createDisplayContext(this).resources.displayMetrics
        return ShellDisplay(
            displayId = displayId,
            name = name ?: "Display $displayId",
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            isPrimary = displayId == Display.DEFAULT_DISPLAY,
            isPublic = flags and Display.FLAG_PRIVATE == 0,
        )
    }
}
