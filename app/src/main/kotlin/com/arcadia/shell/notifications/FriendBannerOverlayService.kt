package com.arcadia.shell.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import com.arcadia.shell.MainActivity
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ShellSettings
import com.arcadia.shell.datastore.liteVisualsOverride
import com.arcadia.shell.datastore.resolveDarkTheme
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.display.DisplayOverlayWindow
import com.arcadia.shell.display.OverlayPermission
import com.arcadia.shell.feature.home.component.NotificationBannerHost
import com.arcadia.shell.launcher.notifications.AppForegroundTracker
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.isFriendPresenceBanner
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
 * Floating "friend online" / "friend started playing" banner over whatever app is in front.
 *
 * [ShellNotificationCenter] already queues these two event types into [ShellNotificationCenter.active]
 * while XOrA is backgrounded (see its `emit`), instead of posting them to the status bar, once this
 * feature is eligible. This service is the only other collector of that queue: it renders the same
 * [NotificationBannerHost] the in-app XMB uses, in a small floating `TYPE_APPLICATION_OVERLAY`
 * window, so the banner looks identical whether XOrA is in front or not.
 *
 * Foreground, for the same reason as [com.arcadia.shell.companion.CompanionOverlayService]: a plain
 * background service is a legitimate low-memory kill target mid-session.
 */
@AndroidEntryPoint
class FriendBannerOverlayService : Service() {

    @Inject lateinit var notificationCenter: ShellNotificationCenter
    @Inject lateinit var foregroundTracker: AppForegroundTracker
    @Inject lateinit var preferences: ShellPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: DisplayOverlayWindow? = null

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()

        combine(notificationCenter.active, foregroundTracker.isForeground) { active, foreground ->
            // The in-app banner already covers the foreground case; showing both would double up.
            if (foreground) null else active?.takeIf { it.isFriendPresenceBanner() }
        }
            .distinctUntilChanged()
            .onEach { eligible ->
                if (eligible == null) {
                    hideOverlay()
                    stopSelf()
                } else {
                    showOverlay()
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

    private fun showOverlay() {
        if (overlay?.isShowing == true) return
        val window = DisplayOverlayWindow(applicationContext)
        val shown = window.show(displayId = Display.DEFAULT_DISPLAY, fullScreen = false) {
            val settings by preferences.settings.collectAsState(initial = ShellSettings())
            val darkTheme = settings.themeMode.resolveDarkTheme(isSystemInDarkTheme())
            ArcadiaTheme(
                darkTheme = darkTheme,
                shellThemeId = settings.shellThemeId,
                uiTextScale = settings.uiTextScale,
                uiLayoutScale = 1f,
                liteVisualsOverride = settings.visualPerformanceMode.liteVisualsOverride(),
            ) {
                Box {
                    NotificationBannerHost(
                        center = notificationCenter,
                        onActivate = { bringXoraToForeground() },
                    )
                }
            }
        }
        if (shown) {
            overlay = window
        } else {
            Log.i(TAG, "Friend banner overlay refused")
            stopSelf()
        }
    }

    private fun bringXoraToForeground() {
        notificationCenter.dismiss()
        runCatching {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }.onFailure { Log.w(TAG, "Could not bring XOrA to foreground from friend banner", it) }
    }

    private fun hideOverlay() {
        overlay?.dismiss()
        overlay = null
    }

    /**
     * Failure here is survivable: without the notification the process is merely killable, and the
     * banner still works for as long as it lives. Crashing the shell over it would not be.
     */
    private fun promoteToForeground() {
        runCatching {
            ensureChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("Friend banner active")
                .setContentText("Showing friend activity over other apps")
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
        }.onFailure { Log.w(TAG, "Could not promote friend banner overlay to foreground", it) }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Friend banner",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps the friend-activity banner visible while another app is open"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "FriendBannerOverlay"
        private const val CHANNEL_ID = "sora_friend_banner"
        private const val NOTIFICATION_ID = 4202

        /**
         * Starts or stops the overlay host. Silently does nothing without the overlay permission —
         * the event still reaches Notification History, so the feature degrades rather than
         * disappears.
         */
        fun setActive(context: Context, active: Boolean) {
            val intent = Intent(context, FriendBannerOverlayService::class.java)
            runCatching {
                if (active && OverlayPermission.isGranted(context)) {
                    context.startForegroundService(intent)
                } else {
                    context.stopService(intent)
                }
            }.onFailure { Log.w(TAG, "Friend banner overlay service transition failed", it) }
        }
    }
}
