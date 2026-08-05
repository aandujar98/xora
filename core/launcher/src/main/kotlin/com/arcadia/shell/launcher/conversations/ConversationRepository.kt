package com.arcadia.shell.launcher.conversations

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory + lightly persisted recent messaging notifications for the social Conversations UI.
 *
 * The [NotificationListenerService] in `:app` pushes posts/removals here. Reply PendingIntents are
 * kept only while the listener is connected and the notification is still replyable.
 */
@Singleton
class ConversationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationCenter: ShellNotificationCenter,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val replyActions = ConcurrentHashMap<String, ReplyAction>()

    private val _state = MutableStateFlow(
        ConversationsUiState(
            listenerEnabled = isNotificationListenerEnabled(),
            conversations = loadPersisted(),
        ),
    )
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInput: RemoteInput,
    )

    fun refreshListenerEnabled() {
        val enabled = isNotificationListenerEnabled()
        _state.update { current ->
            if (current.listenerEnabled == enabled) current
            else current.copy(listenerEnabled = enabled)
        }
        if (!enabled) {
            onListenerDisconnected()
        }
    }

    fun isNotificationListenerEnabled(): Boolean {
        val pkg = context.packageName
        if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)) {
            return true
        }
        // Fallback for OEM builds that don't surface the Compat set promptly.
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        if (flat.isEmpty()) return false
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.packageName == pkg
        }
    }

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun onListenerConnected() {
        _state.update {
            it.copy(
                listenerEnabled = true,
                listenerConnected = true,
            )
        }
    }

    fun onListenerDisconnected() {
        replyActions.clear()
        _state.update { current ->
            current.copy(
                listenerConnected = false,
                conversations = current.conversations.map { convo ->
                    if (convo.canReply) convo.copy(canReply = false) else convo
                },
            )
        }
        persist(_state.value.conversations)
    }

    fun upsert(
        conversation: NotificationConversation,
        reply: ReplyAction?,
    ) {
        if (reply != null) {
            replyActions[conversation.key] = reply
        } else {
            replyActions.remove(conversation.key)
        }
        val withReply = conversation.copy(canReply = reply != null)
        val previous = _state.value.conversations.firstOrNull { it.key == withReply.key }
        _state.update { current ->
            val without = current.conversations.filterNot { it.key == withReply.key }
            val next = (listOf(withReply) + without)
                .sortedByDescending { it.postedAtEpochMs }
                .take(MAX_RECENT)
            current.copy(
                listenerEnabled = true,
                conversations = next,
            )
        }
        persist(_state.value.conversations)
        emitMessageBannerIfNew(previous, withReply)
    }

    private fun emitMessageBannerIfNew(
        previous: NotificationConversation?,
        conversation: NotificationConversation,
    ) {
        val isNewPost = previous == null ||
            previous.postedAtEpochMs != conversation.postedAtEpochMs ||
            previous.text != conversation.text
        if (!isNewPost) return
        val snippet = conversation.text.trim().ifBlank { return }
        val sender = conversation.title.trim().ifBlank { conversation.appLabel }
        val bannerId = "msg:${conversation.key}:${conversation.postedAtEpochMs}"
        when (conversation.source) {
            ConversationSource.Discord -> notificationCenter.emit(
                ShellNotification.DiscordMessage(
                    id = bannerId,
                    sender = sender,
                    snippet = snippet,
                ),
            )
            ConversationSource.Steam -> notificationCenter.emit(
                ShellNotification.SteamMessage(
                    id = bannerId,
                    sender = sender,
                    snippet = snippet,
                ),
            )
            ConversationSource.Other -> Unit
        }
    }

    fun onNotificationRemoved(key: String) {
        replyActions.remove(key)
        _state.update { current ->
            current.copy(
                conversations = current.conversations.map { convo ->
                    if (convo.key == key && convo.canReply) convo.copy(canReply = false) else convo
                },
            )
        }
        persist(_state.value.conversations)
    }

    fun clearAll() {
        replyActions.clear()
        _state.update { it.copy(conversations = emptyList()) }
        prefs.edit().remove(KEY_RECENT).apply()
    }

    fun reply(key: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val action = replyActions[key] ?: return false
        return runCatching {
            val results = Bundle().apply {
                putCharSequence(action.remoteInput.resultKey, trimmed)
            }
            val fillIn = Intent()
            RemoteInput.addResultsToIntent(arrayOf(action.remoteInput), fillIn, results)
            action.pendingIntent.send(context, 0, fillIn)
            true
        }.getOrElse { error ->
            logDebug("Reply failed for key hash=${key.hashCode()}: ${error.javaClass.simpleName}")
            false
        }
    }

    fun canReplyNow(key: String): Boolean = replyActions.containsKey(key)

    fun openApp(packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching {
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }

    private fun persist(conversations: List<NotificationConversation>) {
        val array = JSONArray()
        conversations.take(MAX_RECENT).forEach { convo ->
            array.put(
                JSONObject()
                    .put("key", convo.key)
                    .put("packageName", convo.packageName)
                    .put("appLabel", convo.appLabel)
                    .put("title", convo.title)
                    .put("text", convo.text)
                    .put("postedAtEpochMs", convo.postedAtEpochMs)
                    .put("source", convo.source.name)
                    .put("steamIdHint", convo.steamIdHint),
            )
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    private fun loadPersisted(): List<NotificationConversation> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val sourceName = obj.optString("source", ConversationSource.Other.name)
                    val source = runCatching { ConversationSource.valueOf(sourceName) }
                        .getOrDefault(ConversationSource.Other)
                    add(
                        NotificationConversation(
                            key = obj.getString("key"),
                            packageName = obj.getString("packageName"),
                            appLabel = obj.optString("appLabel", MessagingPackages.appLabelFor(obj.getString("packageName"))),
                            title = obj.optString("title", "Conversation"),
                            text = obj.optString("text", ""),
                            postedAtEpochMs = obj.optLong("postedAtEpochMs", 0L),
                            canReply = false, // reply intents never survive process death
                            source = source,
                            steamIdHint = obj.optString("steamIdHint")
                                .takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrElse {
            logDebug("Failed to load persisted conversations")
            emptyList()
        }
    }

    private fun logDebug(message: String) {
        // Never log message bodies — only structural diagnostics, and only in debuggable builds.
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "ConversationRepo"
        private const val PREFS_NAME = "sora_conversations"
        private const val KEY_RECENT = "recent_json"
        const val MAX_RECENT = 40
    }
}
