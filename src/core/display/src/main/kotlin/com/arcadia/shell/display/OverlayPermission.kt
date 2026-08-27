package com.arcadia.shell.display

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * "Display over other apps" (`SYSTEM_ALERT_WINDOW`), the one permission that lets the shell keep
 * drawing on the second screen after an emulator has taken the foreground.
 *
 * It cannot be requested with the runtime permission dialog; the user has to toggle it in a system
 * settings page, so every caller has to treat "not granted" as a normal state and degrade instead
 * of failing.
 */
object OverlayPermission {

    fun isGranted(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    /**
     * Deep link to the per-app toggle. Started with `NEW_TASK` because callers include a Service
     * and content hosted on a secondary display.
     */
    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
