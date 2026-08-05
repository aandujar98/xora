package com.arcadia.shell.conversations

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arcadia.shell.launcher.conversations.ConversationRepository
import com.arcadia.shell.launcher.conversations.MessagingPackages
import com.arcadia.shell.launcher.conversations.NotificationConversation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Feeds [ConversationRepository] from messaging-ish StatusBarNotifications when the user grants
 * Notification Access. Does not implement Steam CM chat — only notification previews + RemoteInput.
 */
@AndroidEntryPoint
class ShellNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var repository: ConversationRepository

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            repository.onListenerConnected()
            activeNotifications.orEmpty().forEach { sbn ->
                ingest(sbn, fromActiveSnapshot = true)
            }
            logDebug("Listener connected; active=${activeNotifications?.size ?: 0}")
        }.onFailure { Log.e(TAG, "onListenerConnected failed", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { repository.onListenerDisconnected() }
            .onFailure { Log.e(TAG, "onListenerDisconnected failed", it) }
        logDebug("Listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { ingest(sbn, fromActiveSnapshot = false) }
            .onFailure { Log.e(TAG, "onNotificationPosted failed", it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatching { repository.onNotificationRemoved(sbn.key) }
            .onFailure { Log.e(TAG, "onNotificationRemoved failed", it) }
    }

    private fun ingest(sbn: StatusBarNotification, fromActiveSnapshot: Boolean) {
        if (!isConversationCandidate(sbn)) return

        val notification = sbn.notification ?: return
        // Skip pure ongoing system chrome unless it still looks like messaging.
        if (sbn.isOngoing &&
            notification.category != Notification.CATEGORY_MESSAGE &&
            findReplyAction(notification) == null &&
            !MessagingPackages.KNOWN.contains(sbn.packageName)
        ) {
            return
        }

        val extras = notification.extras ?: return
        val title = extras.charSequence(Notification.EXTRA_TITLE)
            ?: extras.charSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.charSequence(Notification.EXTRA_TITLE_BIG)
            ?: return
        val text = extractText(notification, extras)
        if (title.isBlank() && text.isBlank()) return

        val reply = findReplyAction(notification)
        val conversation = NotificationConversation(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = MessagingPackages.appLabelFor(sbn.packageName),
            title = title.trim().ifBlank { MessagingPackages.appLabelFor(sbn.packageName) },
            text = text.trim(),
            postedAtEpochMs = sbn.postTime,
            canReply = reply != null,
            source = MessagingPackages.sourceFor(sbn.packageName),
            steamIdHint = null,
        )
        repository.upsert(conversation, reply)
        logDebug(
            "Ingest pkg=${sbn.packageName} canReply=${reply != null} snapshot=$fromActiveSnapshot",
        )
    }

    private fun isConversationCandidate(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        if (pkg == packageName) return false
        if (pkg in MessagingPackages.KNOWN) return true
        if (MessagingPackages.isSteamPackage(pkg) || MessagingPackages.isDiscordPackage(pkg)) {
            return true
        }

        val notification = sbn.notification ?: return false
        if (notification.category == Notification.CATEGORY_MESSAGE) return true
        if (notification.category == Notification.CATEGORY_EMAIL) return true

        val style = notification.extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        if (style.contains("MessagingStyle", ignoreCase = true)) return true

        // Free-form RemoteInput is a strong signal even for unknown packages.
        if (findReplyAction(notification) != null) return true

        return false
    }

    private fun extractText(notification: Notification, extras: android.os.Bundle): String {
        val messages = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.lastOrNull()
                ?.text
                ?.toString()
        }.getOrNull()
        if (!messages.isNullOrBlank()) return messages

        val bigText = extras.charSequence(Notification.EXTRA_BIG_TEXT)
        if (!bigText.isNullOrBlank()) return bigText

        val text = extras.charSequence(Notification.EXTRA_TEXT)
        if (!text.isNullOrBlank()) return text

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lastLine = lines?.lastOrNull()?.toString()
        if (!lastLine.isNullOrBlank()) return lastLine

        val summary = extras.charSequence(Notification.EXTRA_SUMMARY_TEXT)
        return summary.orEmpty()
    }

    private fun findReplyAction(notification: Notification): ConversationRepository.ReplyAction? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            val freeForm = remoteInputs.firstOrNull { it.allowFreeFormInput } ?: continue
            val intent = action.actionIntent ?: continue
            return ConversationRepository.ReplyAction(
                pendingIntent = intent,
                remoteInput = freeForm,
            )
        }
        return null
    }

    private fun android.os.Bundle.charSequence(key: String): String? =
        getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }

    private fun logDebug(message: String) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "ShellNotifListener"
    }
}
