package com.arcadia.shell.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.UiFitMode
import com.arcadia.shell.datastore.resolveDarkTheme
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.display.DisplayOverlayWindow
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.display.OverlayPermission
import com.arcadia.shell.display.computeUiLayoutScale
import com.arcadia.shell.feature.home.GameCompanionController
import com.arcadia.shell.feature.home.GameCompanionPane
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Keeps the companion panel on the second screen after an emulator takes the foreground.
 *
 * The shell's own [com.arcadia.shell.display.SecondaryDisplayPane] cannot do this: it is a
 * `Presentation`, a Dialog hanging off MainActivity, and it is torn down with the Activity the
 * moment another app is resumed on the primary display. A `TYPE_APPLICATION_OVERLAY` window belongs
 * to the process instead, so it survives — at the cost of the "Display over other apps" permission,
 * which the user has to grant by hand.
 *
 * Foreground, because the whole point is to be alive while the user is doing something else and
 * a plain background service is a legitimate target for low-memory kills mid-session.
 */
@AndroidEntryPoint
class CompanionOverlayService : Service() {

    @Inject lateinit var controller: GameCompanionController
    @Inject lateinit var preferences: ShellPreferences
    @Inject lateinit var topologyMonitor: DisplayTopologyMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: DisplayOverlayWindow? = null

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()

        combine(controller.session, controller.companionDisplayId) { session, displayId ->
            if (session == null || displayId == null) null else displayId
        }
            .distinctUntilChanged()
            .onEach { displayId ->
                if (displayId == null) {
                    hideOverlay()
                    // Nothing left to show; releasing the notification is part of ending cleanly.
                    stopSelf()
                } else {
                    showOverlay(displayId)
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun showOverlay(displayId: Int) {
        // A moved or re-plugged screen means a new window, so the old one goes first.
        hideOverlay()
        val window = DisplayOverlayWindow(applicationContext)
        val shown = window.show(displayId) {
            val session by controller.session.collectAsState()
            val settings by preferences.settings.collectAsState(initial = ShellSettings())
            val darkTheme = settings.themeMode.resolveDarkTheme(isSystemInDarkTheme())
            val fitDisplay = topologyMonitor.current().displays
                .firstOrNull { it.displayId == displayId }
                ?: topologyMonitor.current().secondary
            val layoutScale = if (settings.uiFitMode == UiFitMode.Auto) {
                computeUiLayoutScale(fitDisplay)
            } else {
                1f
            }
            ArcadiaTheme(
                darkTheme = darkTheme,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
                uiLayoutScale = layoutScale,
            ) {
                session?.let { companion ->
                    GameCompanionPane(
                        companion = companion,
                        onSelectAction = controller::selectAction,
                        onActivateAction = controller::openFocusedAction,
                        onDismissOverlay = controller::dismissOverlay,
                    )
                }
            }
        }
        if (shown) {
            overlay = window
        } else {
            Log.i(TAG, "Companion overlay refused on display $displayId")
            stopSelf()
        }
    }

    private fun hideOverlay() {
        overlay?.dismiss()
        overlay = null
    }

    /**
     * Failure here is survivable: without the notification the process is merely killable, and the
     * panel still works for as long as it lives. Crashing the shell over it would not be.
     */
    private fun promoteToForeground() {
        runCatching {
            ensureChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("Companion screen active")
                .setContentText("Showing game info on the second screen")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "Could not promote companion overlay to foreground", it) }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Companion screen",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps game info on the second screen while a game is running"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "CompanionOverlay"
        private const val CHANNEL_ID = "sora_companion_screen"
        private const val NOTIFICATION_ID = 4201

        /**
         * Starts or stops the overlay host. Silently does nothing without the overlay permission —
         * the shell's own secondary pane still shows the panel while SORA is in front, so the
         * feature degrades rather than disappears.
         */
        fun setActive(context: Context, active: Boolean) {
            val intent = Intent(context, CompanionOverlayService::class.java)
            runCatching {
                if (active && OverlayPermission.isGranted(context)) {
                    context.startForegroundService(intent)
                } else {
                    context.stopService(intent)
                }
            }.onFailure { Log.w(TAG, "Companion overlay service transition failed", it) }
        }
    }
}
