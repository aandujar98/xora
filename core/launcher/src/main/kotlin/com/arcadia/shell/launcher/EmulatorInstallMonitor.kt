package com.arcadia.shell.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the player table in sync with emulator apps installed on the device.
 *
 * Package add/remove broadcasts cover sideloads while XOrA is running. Returning to the
 * foreground covers installs that happened while the shell was in the background.
 */
@Singleton
class EmulatorInstallMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seeder: PlayerSeeder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val lastForegroundScanAt = AtomicLong(0L)
    private var debounceJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (!EmulatorInstallEvents.shouldRefresh(intent.action, replacing)) return
            scheduleRefresh()
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }

    fun onAppForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundScanAt.get() < EmulatorDetectPolicy.FOREGROUND_MIN_INTERVAL_MS) {
            return
        }
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(EmulatorDetectPolicy.PACKAGE_DEBOUNCE_MS)
            lastForegroundScanAt.set(System.currentTimeMillis())
            runCatching { seeder.scanInstalled() }
        }
    }
}
