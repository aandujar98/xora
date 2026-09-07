package com.arcadia.shell.designsystem

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * RAM / process-budget snapshot used to decide whether the shell should drop GPU-heavy
 * decoration (Haze blur, looping wallpaper video, idle trailers, ambient clocks).
 *
 * Galaxy A15-class phones report ~4–6 GiB totalMem. 8 GiB handhelds stay on the full path.
 */
data class DeviceVisualBudget(
    val totalRamBytes: Long,
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
) {
    val suggestsLiteVisuals: Boolean
        get() {
            if (isLowRamDevice) return true
            if (memoryClassMb in 1..LITE_MEMORY_CLASS_MB) return true
            if (totalRamBytes in 1 until LITE_RAM_BYTES) return true
            return false
        }

    /** e.g. `5.2 GB RAM` from [ActivityManager.MemoryInfo.totalMem]. */
    val usableRamLabel: String
        get() {
            if (totalRamBytes <= 0L) return "RAM unknown"
            val gb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
            return String.format("%.1f GB RAM", gb)
        }
}

/** 6 GiB — 4/6 GB phones report less; 8 GB handhelds typically report ~6.8–7.5 GiB usable. */
const val LITE_RAM_BYTES = 6L * 1024L * 1024L * 1024L

/** Default Android memory class on 4 GB devices is 192 or below. */
const val LITE_MEMORY_CLASS_MB = 192

fun readDeviceVisualBudget(context: Context): DeviceVisualBudget {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return DeviceVisualBudget(
        totalRamBytes = info.totalMem,
        memoryClassMb = am.memoryClass,
        isLowRamDevice = am.isLowRamDevice,
    )
}

/**
 * [override] is the user pref: `null` = Auto, `true` = Smooth, `false` = Full quality.
 * Battery saver always wins so a Quality override cannot keep Haze spinning on a dying pack.
 */
fun resolveLiteVisuals(
    override: Boolean?,
    deviceSuggestsLite: Boolean,
    powerSave: Boolean,
): Boolean = powerSave || (override ?: deviceSuggestsLite)

/** True when this composition should skip Haze, looping video, and ambient clocks. */
val LocalLiteVisuals = compositionLocalOf { false }

@Composable
fun rememberDeviceSuggestsLiteVisuals(): Boolean {
    val context = LocalContext.current
    return remember(context) { readDeviceVisualBudget(context).suggestsLiteVisuals }
}

@Composable
fun rememberLiteVisuals(): Boolean = LocalLiteVisuals.current
