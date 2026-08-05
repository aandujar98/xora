package com.arcadia.shell.launcher.discord

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.arcadia.shell.launcher.notifications.FriendNetwork
import com.arcadia.shell.launcher.notifications.ShellNotification
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discord Rich Presence controller for SORA.
 *
 * When `discord_partner_sdk.aar` is bundled and the native bridge is built, publishes real
 * Social SDK Rich Presence (Playing SORA / Browsing {game} / Playing {game}), restores OAuth
 * tokens, and surfaces Discord friends. Without the AAR, tracks the intended activity and
 * offers a shareable status bridge so the app still builds and runs.
 *
 * Mobile (official Social SDK docs, July 2026): Rich Presence requires account linking +
 * Connect()/Ready. Unauthenticated RPC is desktop-only. After Ready, SetApplicationId is
 * already set and UpdateRichPresence publishes to the linked Discord account.
 */
@Singleton
class DiscordPresenceController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tokenStore: DiscordTokenStore,
    private val notificationCenter: ShellNotificationCenter,
) : DiscordRichPresence {

    private val bridge = DiscordSocialSdkBridge()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var callbackJob: Job? = null
    private var publishJob: Job? = null
    private var activityStartedAtUnix: Long = 0L
    private var lastPublishKey: String? = null
    private var lastPublishAtMs: Long = 0L
    private var appInForeground: Boolean = true
    private var lastPublishOk: Boolean? = null
    private var lastPublishMessage: String? = null
    private var lastAuthError: String? = null
    private var lastDeferredLogAtMs: Long = 0L
    /** First friends snapshot only seeds online ids so reconnects do not flood banners. */
    private var discordOnlineSeeded = false
    private val knownOnlineDiscordIds = linkedSetOf<String>()

    private val _state = MutableStateFlow(DiscordPresenceUiState())
    override val state: StateFlow<DiscordPresenceUiState> = _state.asStateFlow()

    init {
        DiscordSocialSdkBridgeHolder.bridge = bridge
        bridge.detectClasspath()
        bridge.setStatusListener { ready, authorized ->
            if (ready) lastAuthError = null
            _state.update { current ->
                rebuild(
                    applicationId = current.applicationId,
                    activity = current.activity,
                    ready = ready,
                    authorized = authorized,
                    friends = current.friends,
                )
            }
            if (ready) {
                Log.i(TAG, "Social SDK Ready — publishing Rich Presence")
                schedulePublish(immediate = true)
            } else if (appInForeground) {
                restoreTokensIfNeeded()
            }
        }
        bridge.setTokenListener { access, refresh, expiresIn ->
            lastAuthError = null
            tokenStore.save(access, refresh, expiresIn)
            Log.i(TAG, "OAuth tokens stored (expiresIn=${expiresIn}s)")
        }
        bridge.setFriendsListener { friends ->
            emitDiscordFriendOnlineBanners(friends)
            _state.update { current ->
                rebuild(
                    applicationId = current.applicationId,
                    activity = current.activity,
                    ready = bridge.isReady,
                    authorized = bridge.isAuthorized,
                    friends = friends,
                )
            }
        }
        bridge.setPresenceResultListener { ok, message ->
            val deferred = !ok && (
                message.contains("Connect required", ignoreCase = true) ||
                    message.contains("not Ready", ignoreCase = true) ||
                    message.contains("desktop-only", ignoreCase = true)
                )
            if (deferred) {
                Log.i(TAG, "Presence deferred: $message")
                return@setPresenceResultListener
            }
            lastPublishOk = ok
            lastPublishMessage = message
            if (ok) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "UpdateRichPresence ok msg=$message")
                } else {
                    Log.i(TAG, "UpdateRichPresence ok")
                }
            } else {
                Log.w(TAG, "UpdateRichPresence FAILED msg=$message")
            }
            _state.update { current ->
                rebuild(
                    applicationId = current.applicationId,
                    activity = current.activity,
                    ready = bridge.isReady,
                    authorized = bridge.isAuthorized,
                    friends = current.friends,
                )
            }
        }
        bridge.setAuthErrorListener { message ->
            lastAuthError = message.ifBlank { "Discord auth failed" }
            Log.w(TAG, "Auth error: $lastAuthError")
            _state.update { current ->
                rebuild(
                    applicationId = current.applicationId,
                    activity = current.activity,
                    ready = bridge.isReady,
                    authorized = bridge.isAuthorized,
                    friends = current.friends,
                )
            }
        }
        _state.value = rebuild(
            applicationId = "",
            activity = DiscordPresenceActivity.Idle,
            ready = false,
            authorized = false,
            friends = emptyList(),
        )
    }

    override fun setApplicationId(applicationId: String) {
        val id = applicationId.trim()
        val previous = _state.value.applicationId
        if (id.isNotBlank() && id != previous) {
            startSdk(id)
        } else if (id.isBlank()) {
            stopSdk()
            tokenStore.clear()
        } else if (id.isNotBlank()) {
            // Same id — ensure client is alive (e.g. after process restore).
            startSdk(id)
        }
        _state.update { current ->
            rebuild(
                applicationId = id,
                activity = if (id.isBlank()) DiscordPresenceActivity.Idle else current.activity,
                ready = bridge.isReady,
                authorized = bridge.isAuthorized,
                friends = if (id.isBlank()) emptyList() else current.friends,
            )
        }
        if (id.isNotBlank()) {
            schedulePublish(immediate = true)
        }
    }

    override fun setActivity(activity: DiscordPresenceActivity) {
        val started = System.currentTimeMillis() / 1000L
        if (activity !is DiscordPresenceActivity.Idle) {
            activityStartedAtUnix = started
        }
        _state.update { current ->
            if (!current.isConfigured) {
                rebuild(
                    applicationId = "",
                    activity = DiscordPresenceActivity.Idle,
                    ready = false,
                    authorized = false,
                    friends = emptyList(),
                )
            } else {
                rebuild(
                    applicationId = current.applicationId,
                    activity = activity,
                    ready = bridge.isReady,
                    authorized = bridge.isAuthorized,
                    friends = current.friends,
                )
            }
        }
        schedulePublish(immediate = false)
    }

    /**
     * Clears remote Discord presence only. Keeps the intended [DiscordPresenceActivity] so
     * [onAppForeground] can republish without waiting for a selection change.
     */
    override fun clear() {
        runCatching { bridge.clearPresence() }
            .onFailure { Log.e(TAG, "clearPresence failed", it) }
        lastPublishKey = null
        lastPublishOk = null
        lastPublishMessage = null
        _state.update { current ->
            rebuild(
                applicationId = current.applicationId,
                activity = current.activity,
                ready = bridge.isReady,
                authorized = bridge.isAuthorized,
                friends = current.friends,
            )
        }
    }

    override fun onAppForeground() {
        appInForeground = true
        val id = _state.value.applicationId
        if (id.isNotBlank()) {
            startSdk(id)
        }
        schedulePublish(immediate = true)
        _state.update { current ->
            rebuild(
                applicationId = current.applicationId,
                activity = current.activity,
                ready = bridge.isReady,
                authorized = bridge.isAuthorized,
                friends = current.friends,
            )
        }
    }

    override fun onAppBackground() {
        appInForeground = false
        // Keep the callback loop lightly ticking so OAuth / Connect can finish while Discord's
        // Custom Tab is open, but stop spamming presence updates.
        publishJob?.cancel()
        publishJob = null
    }

    override fun attachHostActivity(activity: Activity) {
        runCatching { bridge.attachEngineActivity(activity) }
            .onFailure { Log.e(TAG, "attachHostActivity failed", it) }
        val id = _state.value.applicationId
        if (id.isNotBlank()) {
            startSdk(id)
        }
        _state.update { current ->
            rebuild(
                applicationId = current.applicationId,
                activity = current.activity,
                ready = bridge.isReady,
                authorized = bridge.isAuthorized,
                friends = current.friends,
            )
        }
    }

    override fun startAccountLinking(activity: Activity) {
        val id = _state.value.applicationId
        if (id.isBlank()) {
            Log.w(TAG, "startAccountLinking skipped: no Application ID")
            return
        }
        lastAuthError = null
        Log.i(TAG, "startAccountLinking appId=$id")
        runCatching { bridge.startAccountLinking(activity, id) }
            .onFailure {
                Log.e(TAG, "startAccountLinking failed", it)
                lastAuthError = it.message ?: "Account link failed"
            }
        _state.update { current ->
            rebuild(
                applicationId = current.applicationId,
                activity = current.activity,
                ready = bridge.isReady,
                authorized = bridge.isAuthorized,
                friends = current.friends,
            )
        }
    }

    override fun statusBridgeShareIntent(context: Context): Intent? {
        val snapshot = _state.value
        if (!snapshot.isConfigured) return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, snapshot.shareText)
            putExtra(Intent.EXTRA_SUBJECT, "XOrA status")
        }
        return Intent.createChooser(send, "Share XOrA status")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun openDeveloperPortalIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(DiscordPresenceUiState.PORTAL_APPLICATIONS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startSdk(applicationId: String) {
        val ok = runCatching { bridge.ensureInitialized(applicationId) }
            .onFailure { Log.e(TAG, "ensureInitialized crashed — degrading without SDK", it) }
            .getOrDefault(false)
        if (!ok) {
            Log.i(TAG, "Social SDK not active (AAR / native bridge missing or init failed)")
            stopCallbackLoop()
            return
        }
        startCallbackLoop()
        restoreTokensIfNeeded()
    }

    private fun restoreTokensIfNeeded() {
        if (bridge.isReady) return
        val tokens = tokenStore.read() ?: return
        when {
            tokens.hasAccess && !tokens.isExpired -> {
                Log.i(TAG, "Restoring Discord access token → Connect")
                bridge.connectWithAccessToken(tokens.accessToken)
            }
            tokens.hasRefresh -> {
                Log.i(TAG, "Refreshing Discord access token → Connect")
                bridge.refreshAndConnect(tokens.refreshToken)
            }
            tokens.hasAccess -> {
                // Expired access with no refresh — try once; SDK may reject.
                Log.i(TAG, "Trying expired access token → Connect")
                bridge.connectWithAccessToken(tokens.accessToken)
            }
        }
    }

    private fun stopSdk() {
        stopCallbackLoop()
        publishJob?.cancel()
        publishJob = null
        runCatching { bridge.destroy() }
            .onFailure { Log.e(TAG, "bridge.destroy failed", it) }
    }

    private fun startCallbackLoop() {
        if (callbackJob?.isActive == true) return
        callbackJob = scope.launch {
            while (isActive) {
                runCatching { bridge.runCallbacks() }
                    .onFailure { Log.w(TAG, "runCallbacks failed", it) }
                delay(if (appInForeground) CALLBACK_FOREGROUND_MS else CALLBACK_BACKGROUND_MS)
            }
        }
    }

    private fun stopCallbackLoop() {
        callbackJob?.cancel()
        callbackJob = null
    }

    private fun schedulePublish(immediate: Boolean) {
        publishJob?.cancel()
        if (immediate) {
            doPublish()
            return
        }
        publishJob = scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            doPublish()
        }
    }

    private fun doPublish() {
        val snapshot = _state.value
        if (!snapshot.isConfigured) return
        if (!bridge.isNativeLoaded) {
            logDeferred("publish skipped: native bridge not loaded")
            return
        }
        if (!appInForeground && snapshot.activity !is DiscordPresenceActivity.Playing) {
            return
        }

        // Mobile Social SDK: UpdateRichPresence is reliable only after Connect()/Ready.
        // Unauthenticated desktop RPC does not apply on handhelds (Discord docs).
        if (!bridge.isReady) {
            restoreTokensIfNeeded()
            logDeferred(
                "publish deferred until Social SDK Ready " +
                    "(authorized=${bridge.isAuthorized}; Link Discord account if needed)",
            )
            return
        }

        // Discord line 1 ("Playing SORA") comes from the Developer Portal application name.
        // details = line 2, state = line 3.
        val (details, activityState, name) = when (val activity = snapshot.activity) {
            DiscordPresenceActivity.Idle -> {
                bridge.clearPresence()
                lastPublishKey = "idle"
                return
            }
            DiscordPresenceActivity.InSora -> Triple("In the library", "Browsing", "XOrA")
            is DiscordPresenceActivity.Browsing -> Triple(
                "Browsing ${activity.gameTitle}",
                activity.platformName,
                "XOrA",
            )
            is DiscordPresenceActivity.Playing -> Triple(
                "Playing ${activity.gameTitle}",
                activity.platformName,
                "XOrA",
            )
        }

        val key = "$details|$activityState|$name|${activityStartedAtUnix}"
        val now = SystemClock.elapsedRealtime()
        if (key == lastPublishKey && now - lastPublishAtMs < PUBLISH_MIN_INTERVAL_MS) {
            return
        }
        lastPublishKey = key
        lastPublishAtMs = now

        Log.i(TAG, "UpdateRichPresence details=$details state=$activityState")
        runCatching {
            bridge.updateRichPresence(
                details = details,
                state = activityState,
                name = name,
                startUnixSeconds = activityStartedAtUnix,
            )
        }.onFailure {
            Log.e(TAG, "updateRichPresence crashed — shell continues", it)
            lastPublishOk = false
            lastPublishMessage = it.message
            _state.update { current ->
                rebuild(
                    applicationId = current.applicationId,
                    activity = current.activity,
                    ready = bridge.isReady,
                    authorized = bridge.isAuthorized,
                    friends = current.friends,
                )
            }
        }
    }

    private fun logDeferred(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDeferredLogAtMs < DEFERRED_LOG_MIN_INTERVAL_MS) return
        lastDeferredLogAtMs = now
        Log.i(TAG, message)
    }

    private fun rebuild(
        applicationId: String,
        activity: DiscordPresenceActivity,
        ready: Boolean,
        authorized: Boolean,
        friends: List<DiscordFriendEntry>,
    ): DiscordPresenceUiState {
        bridge.detectClasspath()
        val configured = applicationId.isNotBlank()
        val sdkOnClasspath = bridge.isSdkOnClasspath
        val nativeLoaded = bridge.isNativeLoaded
        val discordInstalled = bridge.isDiscordAppInstalled(appContext)
        val redirect = if (configured) {
            DiscordPresenceUiState.oauthRedirectUriFor(applicationId)
        } else {
            ""
        }
        val steps = if (configured) {
            DiscordPresenceUiState.setupStepsFor(applicationId)
        } else {
            emptyList()
        }

        val authFailed = !lastAuthError.isNullOrBlank()
        val publishFailed = lastPublishOk == false && ready
        val hasTokens = tokenStore.read() != null
        val connecting = !ready && (authorized || hasTokens) && !authFailed

        val capability = when {
            !configured -> DiscordPresenceCapability.NotConfigured
            !sdkOnClasspath || !nativeLoaded -> DiscordPresenceCapability.SdkMissing
            !discordInstalled -> DiscordPresenceCapability.NeedsDiscordApp
            ready -> DiscordPresenceCapability.Connected
            authFailed && !authorized && !hasTokens -> DiscordPresenceCapability.Failed
            else -> DiscordPresenceCapability.NeedsAccountLink
        }

        val publishHint = when {
            publishFailed -> " · presence publish failed"
            lastPublishOk == true && ready -> ""
            else -> ""
        }

        val statusLine = when (capability) {
            DiscordPresenceCapability.NotConfigured ->
                "Add a Discord Application ID in Settings."
            DiscordPresenceCapability.SdkMissing -> when (activity) {
                DiscordPresenceActivity.Idle ->
                    "Application ID saved · drop discord_partner_sdk.aar then rebuild"
                DiscordPresenceActivity.InSora ->
                    "Would show: Playing XOrA · SDK missing"
                is DiscordPresenceActivity.Browsing ->
                    "Would show: Browsing ${activity.gameTitle} · SDK missing"
                is DiscordPresenceActivity.Playing ->
                    "Would show: Playing ${activity.gameTitle} · SDK missing"
            }
            DiscordPresenceCapability.NeedsDiscordApp ->
                "Install / open the Discord app, then Link Discord account"
            DiscordPresenceCapability.Failed ->
                "Failed · ${lastAuthError ?: lastPublishMessage ?: "see logcat:SoraDiscord"}"
            DiscordPresenceCapability.NeedsAccountLink ->
                when {
                    connecting -> "Connecting… finishing Discord account link"
                    else -> "Link Discord to enable live presence"
                }
            DiscordPresenceCapability.Connected -> when (activity) {
                DiscordPresenceActivity.Idle -> "Linked · Rich Presence idle$publishHint"
                DiscordPresenceActivity.InSora -> "Linked · Publishing: Playing XOrA$publishHint"
                is DiscordPresenceActivity.Browsing ->
                    "Linked · Publishing: Browsing ${activity.gameTitle}$publishHint"
                is DiscordPresenceActivity.Playing ->
                    "Linked · Publishing: Playing ${activity.gameTitle}$publishHint"
            }
        }

        val detailLine = when (capability) {
            DiscordPresenceCapability.Connected -> {
                val base = DiscordPresenceUiState.DETAIL_CONNECTED
                if (publishFailed && !lastPublishMessage.isNullOrBlank()) {
                    "$base Publish error: $lastPublishMessage"
                } else {
                    base
                }
            }
            DiscordPresenceCapability.Failed ->
                "Discord account link / Connect failed: ${lastAuthError ?: "unknown"}. " +
                    "Check Public Client is enabled and redirect URI is $redirect. " +
                    DiscordPresenceUiState.DETAIL_NEEDS_LINK
            DiscordPresenceCapability.NeedsDiscordApp -> DiscordPresenceUiState.DETAIL_NEEDS_DISCORD
            DiscordPresenceCapability.NeedsAccountLink ->
                if (connecting) {
                    "OAuth succeeded — calling Connect(). When status becomes Connected, Discord " +
                        "shows Playing XOrA. Redirect URI: $redirect"
                } else {
                    "${DiscordPresenceUiState.DETAIL_NEEDS_LINK} Redirect URI: $redirect"
                }
            DiscordPresenceCapability.SdkMissing,
            DiscordPresenceCapability.NotConfigured,
            -> DiscordPresenceUiState.DETAIL_SDK_MISSING
        }

        return DiscordPresenceUiState(
            applicationId = applicationId,
            activity = activity,
            capability = capability,
            statusLine = statusLine,
            detailLine = detailLine,
            setupSteps = if (capability == DiscordPresenceCapability.SdkMissing) steps else emptyList(),
            oauthRedirectUri = redirect,
            sdkOnClasspath = sdkOnClasspath,
            nativeBridgeLoaded = nativeLoaded,
            lastError = lastAuthError ?: lastPublishMessage.takeIf { publishFailed },
            friends = if (capability == DiscordPresenceCapability.Connected) {
                friends.take(MAX_FRIENDS_UI)
            } else {
                emptyList()
            },
            connecting = connecting,
            presencePublishing = capability == DiscordPresenceCapability.Connected &&
                lastPublishOk == true &&
                activity !is DiscordPresenceActivity.Idle,
        )
    }

    private fun emitDiscordFriendOnlineBanners(friends: List<DiscordFriendEntry>) {
        val onlineNow = friends.filter { it.isOnline }
        val onlineIds = onlineNow.map { it.userId }.toSet()
        if (!discordOnlineSeeded) {
            knownOnlineDiscordIds.clear()
            knownOnlineDiscordIds.addAll(onlineIds)
            discordOnlineSeeded = true
            return
        }
        for (friend in onlineNow) {
            if (friend.userId in knownOnlineDiscordIds) continue
            knownOnlineDiscordIds.add(friend.userId)
            val activity = when (friend.group) {
                "online_game" -> "In a game"
                else -> null
            }
            notificationCenter.emit(
                ShellNotification.FriendOnline(
                    id = "discord-online:${friend.userId}:${SystemClock.elapsedRealtime()}",
                    displayName = friend.displayName.ifBlank { "Discord friend" },
                    network = FriendNetwork.Discord,
                    avatarUrl = friend.avatarUrl,
                    activityLabel = activity,
                ),
            )
        }
        knownOnlineDiscordIds.retainAll(onlineIds)
    }

    companion object {
        private const val TAG = "SoraDiscord"
        private const val PUBLISH_DEBOUNCE_MS = 450L
        private const val PUBLISH_MIN_INTERVAL_MS = 2_000L
        private const val CALLBACK_FOREGROUND_MS = 1_000L
        private const val CALLBACK_BACKGROUND_MS = 2_500L
        private const val DEFERRED_LOG_MIN_INTERVAL_MS = 8_000L
        private const val MAX_FRIENDS_UI = 40
    }
}

/** Lets JNI reach the active [DiscordSocialSdkBridge] without a hard Activity dependency. */
internal object DiscordSocialSdkBridgeHolder {
    @Volatile
    var bridge: DiscordSocialSdkBridge? = null
}

/** Back-compat name used by older call sites / docs. */
typealias DiscordRichPresenceManager = DiscordPresenceController
