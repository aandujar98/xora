package com.arcadia.shell.launcher.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts Android status-bar notifications for [ShellNotification] events when SORA is not
 * foreground. Channels are created once; sound follows [soundEnabled] via [setSilent].
 */
@Singleton
class ShellSystemNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Master enable mirrored from shell preferences (same toggle as banners). */
    @Volatile
    var notificationsEnabled: Boolean = true

    /** When false, posts are silent (channel still DEFAULT importance). */
    @Volatile
    var soundEnabled: Boolean = true

    /**
     * Set when a background post was skipped for missing [Manifest.permission.POST_NOTIFICATIONS].
     * [MainActivity] should prompt on next resume.
     */
    @Volatile
    var pendingPermissionPrompt: Boolean = false
        private set

    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<Unit> = _permissionRequests.asSharedFlow()

    private var channelsReady = false

    fun ensureChannels() {
        if (channelsReady) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SOCIAL,
                "Friends & messages",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Discord / Steam friends and messages, trophies"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads & library",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Library scans and install stand-ins"
            },
        )
        channelsReady = true
    }

    fun hasPostPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Emit a one-shot request for the Activity to show the runtime permission dialog. */
    fun requestPostNotificationsPermission() {
        if (hasPostPermission()) {
            pendingPermissionPrompt = false
            return
        }
        pendingPermissionPrompt = true
        _permissionRequests.tryEmit(Unit)
    }

    fun clearPendingPermissionPrompt() {
        pendingPermissionPrompt = false
    }

    /**
     * Post a status-bar notification. Returns false if disabled, lacking permission, or notify fails.
     */
    fun post(notification: ShellNotification): Boolean {
        if (!notificationsEnabled) return false
        ensureChannels()
        if (!hasPostPermission()) {
            pendingPermissionPrompt = true
            return false
        }
        val copy = notification.toCopy()
        val channel = channelFor(notification)
        val contentTitle = copy.body
        val contentText = buildString {
            append(copy.category)
            if (copy.subtitle.isNotBlank()) {
                append(" · ")
                append(copy.subtitle)
            }
        }
        val tap = launchPendingIntent()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText),
            )
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(categoryFor(notification))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(groupFor(notification))
            .setSilent(!soundEnabled)
            .setOnlyAlertOnce(true)

        val id = androidNotifyId(notification)
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            true
        }.getOrDefault(false)
    }

    private fun launchPendingIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        return PendingIntent.getActivity(
            context,
            REQUEST_LAUNCH,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun channelFor(notification: ShellNotification): String = when (notification) {
        is ShellNotification.GameDownloading,
        is ShellNotification.InstallComplete,
        -> CHANNEL_DOWNLOADS
        else -> CHANNEL_SOCIAL
    }

    private fun groupFor(notification: ShellNotification): String = when (notification) {
        is ShellNotification.GameDownloading,
        is ShellNotification.InstallComplete,
        -> GROUP_DOWNLOADS
        else -> GROUP_SOCIAL
    }

    private fun categoryFor(notification: ShellNotification): String = when (notification) {
        is ShellNotification.DiscordMessage,
        is ShellNotification.SteamMessage,
        -> NotificationCompat.CATEGORY_MESSAGE
        is ShellNotification.FriendOnline -> NotificationCompat.CATEGORY_SOCIAL
        is ShellNotification.AchievementUnlocked -> NotificationCompat.CATEGORY_STATUS
        is ShellNotification.GameDownloading,
        is ShellNotification.InstallComplete,
        -> NotificationCompat.CATEGORY_PROGRESS
    }

    /**
     * Stable-ish notify ids so repeat friend-online for the same person replaces rather than
     * stacking; message ids already include postedAt so each message is distinct.
     */
    private fun androidNotifyId(notification: ShellNotification): Int {
        val stable = when (notification) {
            is ShellNotification.FriendOnline ->
                notification.id.substringBeforeLast(':').ifBlank { notification.id }
            is ShellNotification.GameDownloading ->
                "download:${notification.title}"
            else -> notification.id
        }
        var hash = stable.hashCode()
        if (hash == 0) hash = SystemClock.elapsedRealtime().toInt()
        return hash
    }

    companion object {
        const val CHANNEL_SOCIAL = "sora_social"
        const val CHANNEL_DOWNLOADS = "sora_downloads"
        private const val GROUP_SOCIAL = "sora_group_social"
        private const val GROUP_DOWNLOADS = "sora_group_downloads"
        private const val REQUEST_LAUNCH = 4101
    }
}
