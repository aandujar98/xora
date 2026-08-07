package com.arcadia.shell.launcher.discord

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional Discord Social SDK surface.
 *
 * The partner AAR is not on Maven Central. When `discord_partner_sdk.aar` is present and the
 * native JNI bridge (`libsora_discord`) was built, this class drives real Rich Presence,
 * OAuth account linking, and friends. Without the AAR / .so, every call is a safe no-op.
 *
 * Mobile Rich Presence requires OAuth account linking + Connect()/Ready (Discord Social SDK
 * mobile docs). Unauthenticated RPC is desktop-only.
 */
internal class DiscordSocialSdkBridge {

    private val initialized = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var isAuthorized: Boolean = false
        private set

    @Volatile
    var isNativeLoaded: Boolean = false
        private set

    @Volatile
    var isSdkOnClasspath: Boolean = false
        private set

    @Volatile
    private var lastAppId: Long = 0L

    @Volatile
    private var statusListener: ((ready: Boolean, authorized: Boolean) -> Unit)? = null

    @Volatile
    private var tokenListener: ((access: String, refresh: String, expiresIn: Int) -> Unit)? = null

    @Volatile
    private var friendsListener: ((List<DiscordFriendEntry>) -> Unit)? = null

    @Volatile
    private var presenceResultListener: ((ok: Boolean, message: String) -> Unit)? = null

    @Volatile
    private var authErrorListener: ((message: String) -> Unit)? = null

    @Volatile
    private var messagesListener: ((recipientId: String, messages: List<DiscordDmMessage>) -> Unit)? =
        null

    @Volatile
    private var messageSendResultListener:
        ((ok: Boolean, error: String, recipientId: String, messageId: String) -> Unit)? = null

    @Volatile
    private var currentUserListener: ((userId: String) -> Unit)? = null

    @Volatile
    var currentUserId: String? = null
        private set

    /** Discord CDN avatar for the signed-in account; `.gif` when the user has an animated one. */
    @Volatile
    var currentUserAvatarUrl: String? = null
        private set

    fun setStatusListener(listener: ((ready: Boolean, authorized: Boolean) -> Unit)?) {
        statusListener = listener
    }

    fun setTokenListener(listener: ((access: String, refresh: String, expiresIn: Int) -> Unit)?) {
        tokenListener = listener
    }

    fun setFriendsListener(listener: ((List<DiscordFriendEntry>) -> Unit)?) {
        friendsListener = listener
    }

    fun setPresenceResultListener(listener: ((ok: Boolean, message: String) -> Unit)?) {
        presenceResultListener = listener
    }

    fun setAuthErrorListener(listener: ((message: String) -> Unit)?) {
        authErrorListener = listener
    }

    fun setMessagesListener(
        listener: ((recipientId: String, messages: List<DiscordDmMessage>) -> Unit)?,
    ) {
        messagesListener = listener
    }

    fun setMessageSendResultListener(
        listener: ((ok: Boolean, error: String, recipientId: String, messageId: String) -> Unit)?,
    ) {
        messageSendResultListener = listener
    }

    fun setCurrentUserListener(listener: ((userId: String) -> Unit)?) {
        currentUserListener = listener
    }

    fun detectClasspath() {
        isSdkOnClasspath = runCatching {
            Class.forName(SDK_INIT_CLASS)
            true
        }.getOrDefault(false)
    }

    fun isDiscordAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return DISCORD_PACKAGES.any { pkg ->
            runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }

    fun attachEngineActivity(activity: Activity) {
        detectClasspath()
        if (!isSdkOnClasspath) return
        runCatching {
            val clazz = Class.forName(SDK_INIT_CLASS)
            val method = clazz.getMethod("setEngineActivity", Activity::class.java)
            method.invoke(null, activity)
            Log.i(TAG, "DiscordSocialSdkInit.setEngineActivity ok")
        }.onFailure {
            Log.w(TAG, "setEngineActivity failed", it)
        }
    }

    fun ensureInitialized(applicationId: String): Boolean {
        detectClasspath()
        val appId = applicationId.trim().toLongOrNull() ?: run {
            Log.w(TAG, "ensureInitialized: invalid applicationId (need numeric snowflake)")
            return false
        }
        if (!isSdkOnClasspath) return false

        if (initialized.get() && lastAppId == appId && isNativeLoaded) {
            return true
        }

        if (!loadNative()) {
            return false
        }

        return try {
            if (initialized.get() && lastAppId != appId) {
                runCatching { nativeDestroy() }
                initialized.set(false)
                isReady = false
                isAuthorized = false
            }
            if (!initialized.compareAndSet(false, true)) {
                lastAppId = appId
                return true
            }
            lastAppId = appId
            val ok = nativeInit(appId)
            if (!ok) {
                initialized.set(false)
                Log.w(TAG, "nativeInit returned false")
                return false
            }
            Log.i(TAG, "nativeInit ok for appId=$appId")
            true
        } catch (t: Throwable) {
            // Catch Error as well as Exception — native crashes that surface as Throwable
            // must not kill the process if we can recover.
            Log.e(TAG, "ensureInitialized failed — Discord presence disabled", t)
            initialized.set(false)
            isReady = false
            isAuthorized = false
            false
        }
    }

    fun runCallbacks() {
        if (!isNativeLoaded || !initialized.get()) return
        runCatching { nativeRunCallbacks() }
            .onFailure { Log.w(TAG, "nativeRunCallbacks failed", it) }
    }

    fun updateRichPresence(
        details: String?,
        state: String?,
        name: String? = "XOrA",
        startUnixSeconds: Long = 0L,
    ) {
        if (!isNativeLoaded || !initialized.get()) {
            Log.i(TAG, "updateRichPresence skipped: native=${isNativeLoaded} init=${initialized.get()}")
            return
        }
        if (!isReady) {
            // Do not report a publish failure — that flips Settings into a false error state.
            Log.i(TAG, "updateRichPresence skipped: not Ready (authorized=$isAuthorized)")
            return
        }
        // ActivityTypes::Playing == 0 in Social SDK.
        runCatching {
            Log.i(TAG, "nativeSetActivity details=$details state=$state name=$name")
            nativeSetActivity(
                activityType = 0,
                name = name,
                state = state,
                details = details,
                startSecs = startUnixSeconds,
                endSecs = 0L,
                largeImage = null,
                largeText = null,
                smallImage = null,
                smallText = null,
                button1Label = null,
                button1Url = null,
                button2Label = null,
                button2Url = null,
            )
        }.onFailure {
            Log.w(TAG, "nativeSetActivity failed", it)
            hopMain { presenceResultListener?.invoke(false, it.message ?: "nativeSetActivity failed") }
        }
    }

    fun clearPresence() {
        if (!isNativeLoaded || !initialized.get() || !isReady) return
        runCatching { nativeClear() }
            .onFailure { Log.w(TAG, "nativeClear failed", it) }
    }

    /** Starts Discord OAuth account linking via the native Social SDK Authorize flow. */
    fun startAccountLinking(activity: Activity, applicationId: String) {
        attachEngineActivity(activity)
        if (!ensureInitialized(applicationId)) return
        // Already linked this session — nudge Connect instead of aborting Authorize.
        if (isAuthorized && !isReady) {
            Log.i(TAG, "startAccountLinking: authorized but not Ready — Connect()")
            runCatching { nativeConnect() }
                .onFailure { Log.w(TAG, "nativeConnect failed", it) }
            return
        }
        runCatching { nativeAuthorize() }
            .onFailure {
                Log.w(TAG, "nativeAuthorize failed", it)
                hopMain { authErrorListener?.invoke(it.message ?: "Authorize failed") }
            }
    }

    fun connectWithAccessToken(accessToken: String) {
        if (!isNativeLoaded || !initialized.get()) return
        if (accessToken.isBlank()) return
        runCatching { nativeSetTokenAndConnect(accessToken) }
            .onFailure {
                Log.w(TAG, "connectWithAccessToken failed", it)
                hopMain { authErrorListener?.invoke(it.message ?: "Connect failed") }
            }
    }

    fun refreshAndConnect(refreshToken: String) {
        if (!isNativeLoaded || !initialized.get()) return
        if (refreshToken.isBlank()) return
        runCatching { nativeRefreshToken(refreshToken) }
            .onFailure {
                Log.w(TAG, "nativeRefreshToken failed", it)
                hopMain { authErrorListener?.invoke(it.message ?: "Token refresh failed") }
            }
    }

    fun refreshFriends() {
        if (!isNativeLoaded || !initialized.get() || !isReady) return
        runCatching { nativeRefreshFriends() }
            .onFailure { Log.w(TAG, "nativeRefreshFriends failed", it) }
    }

    fun sendUserMessage(recipientId: String, content: String) {
        if (!isNativeLoaded || !initialized.get() || !isReady) {
            hopMain {
                messageSendResultListener?.invoke(
                    false,
                    "Discord not ready",
                    recipientId,
                    "0",
                )
            }
            return
        }
        val id = recipientId.trim().toLongOrNull()
        if (id == null) {
            hopMain {
                messageSendResultListener?.invoke(false, "Invalid recipient", recipientId, "0")
            }
            return
        }
        runCatching { nativeSendUserMessage(id, content) }
            .onFailure {
                Log.w(TAG, "nativeSendUserMessage failed", it)
                hopMain {
                    messageSendResultListener?.invoke(
                        false,
                        it.message ?: "Send failed",
                        recipientId,
                        "0",
                    )
                }
            }
    }

    fun loadUserMessages(recipientId: String, limit: Int = 50) {
        if (!isNativeLoaded || !initialized.get() || !isReady) return
        val id = recipientId.trim().toLongOrNull() ?: return
        runCatching { nativeLoadUserMessages(id, limit) }
            .onFailure { Log.w(TAG, "nativeLoadUserMessages failed", it) }
    }

    fun setShowingChat(showing: Boolean) {
        if (!isNativeLoaded || !initialized.get()) return
        runCatching { nativeSetShowingChat(showing) }
            .onFailure { Log.w(TAG, "nativeSetShowingChat failed", it) }
    }

    fun destroy() {
        if (!initialized.getAndSet(false)) return
        runCatching { nativeDestroy() }
        isReady = false
        isAuthorized = false
        currentUserId = null
        currentUserAvatarUrl = null
    }

    internal fun applyNativeStatus(statusCode: Int, ready: Boolean, authorized: Boolean) {
        isReady = ready
        isAuthorized = authorized
        Log.i(TAG, "status statusCode=$statusCode ready=$ready authorized=$authorized")
        hopMain {
            statusListener?.invoke(ready, authorized)
            if (ready) {
                refreshFriends()
            }
        }
    }

    internal fun applyNativeTokens(access: String, refresh: String, expiresIn: Int) {
        Log.i(TAG, "tokens received expiresIn=$expiresIn")
        hopMain { tokenListener?.invoke(access, refresh, expiresIn) }
    }

    internal fun applyNativeFriends(payload: String) {
        val friends = parseFriendsPayload(payload)
        hopMain { friendsListener?.invoke(friends) }
    }

    internal fun applyNativePresenceResult(ok: Boolean, message: String) {
        hopMain { presenceResultListener?.invoke(ok, message) }
    }

    internal fun applyNativeAuthError(message: String) {
        Log.w(TAG, "auth error: $message")
        hopMain { authErrorListener?.invoke(message) }
    }

    internal fun applyNativeMessages(recipientId: String, payload: String) {
        val messages = parseMessagesPayload(payload, currentUserId)
        hopMain { messagesListener?.invoke(recipientId, messages) }
    }

    internal fun applyNativeMessageSendResult(
        ok: Boolean,
        error: String,
        recipientId: String,
        messageId: String,
    ) {
        hopMain { messageSendResultListener?.invoke(ok, error, recipientId, messageId) }
    }

    /** Payload is `userId` or `userId\tavatarUrl` — older natives send the bare id. */
    internal fun applyNativeCurrentUser(payload: String) {
        val parts = payload.split('\t')
        val id = parts.getOrNull(0)?.trim().orEmpty()
        if (id.isEmpty()) return
        currentUserId = id
        currentUserAvatarUrl = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        Log.i(TAG, "currentUserId=$id avatar=${currentUserAvatarUrl ?: "(none)"}")
        hopMain { currentUserListener?.invoke(id) }
    }

    private fun hopMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching(block).onFailure { Log.e(TAG, "main callback failed", it) }
        } else {
            mainHandler.post {
                runCatching(block).onFailure { Log.e(TAG, "main callback failed", it) }
            }
        }
    }

    private fun loadNative(): Boolean {
        if (isNativeLoaded) return true
        return try {
            System.loadLibrary(NATIVE_LIB)
            isNativeLoaded = true
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError and rare linker Errors must not crash the process.
            Log.i(TAG, "Native bridge $NATIVE_LIB not loaded: ${t.javaClass.simpleName}: ${t.message}")
            isNativeLoaded = false
            false
        }
    }

    private external fun nativeInit(appId: Long): Boolean
    private external fun nativeAuthorize()
    private external fun nativeSetTokenAndConnect(token: String)
    private external fun nativeConnect()
    private external fun nativeRefreshToken(refreshToken: String)
    private external fun nativeRefreshFriends()
    private external fun nativeSendUserMessage(recipientId: Long, content: String)
    private external fun nativeLoadUserMessages(recipientId: Long, limit: Int)
    private external fun nativeSetShowingChat(showing: Boolean)
    private external fun nativeSetActivity(
        activityType: Int,
        name: String?,
        state: String?,
        details: String?,
        startSecs: Long,
        endSecs: Long,
        largeImage: String?,
        largeText: String?,
        smallImage: String?,
        smallText: String?,
        button1Label: String?,
        button1Url: String?,
        button2Label: String?,
        button2Url: String?,
    )
    private external fun nativeClear()
    private external fun nativeRunCallbacks()
    private external fun nativeDestroy()

    companion object {
        private const val TAG = "SoraDiscord"
        const val NATIVE_LIB = "sora_discord"
        private const val SDK_INIT_CLASS = "com.discord.socialsdk.DiscordSocialSdkInit"

        private val DISCORD_PACKAGES = listOf(
            "com.discord",
            "com.discord.android",
        )

        /** Payload lines: `userId\\tdisplayName\\tgroup[\\tavatarUrl]` */
        fun parseFriendsPayload(payload: String): List<DiscordFriendEntry> {
            if (payload.isBlank()) return emptyList()
            return payload.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 3) return@mapNotNull null
                    val userId = parts[0]
                    val avatarFromSdk = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                    DiscordFriendEntry(
                        userId = userId,
                        displayName = parts[1].ifBlank { userId },
                        group = parts[2],
                        avatarUrl = avatarFromSdk ?: discordAvatarUrl(userId, avatarHash = null),
                    )
                }
                .toList()
        }

        /**
         * Payload lines (TSV):
         * `messageId\\tauthorId\\trecipientId\\tsentTimestampMs\\tsentFromGame\\tcontent`
         * optionally followed by `\\textraType\\textraTitle\\textraCount` — older natives stop
         * after the content field.
         */
        fun parseMessagesPayload(
            payload: String,
            currentUserId: String?,
        ): List<DiscordDmMessage> {
            if (payload.isBlank()) return emptyList()
            val self = currentUserId?.trim().orEmpty()
            return payload.lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 9)
                    if (parts.size < 6) return@mapNotNull null
                    val authorId = parts[1]
                    val content = parts[5]
                    val extraType = parts.getOrNull(6)?.trim().orEmpty()
                    DiscordDmMessage(
                        messageId = parts[0],
                        authorId = authorId,
                        recipientId = parts[2],
                        sentAtMs = parts[3].toLongOrNull() ?: 0L,
                        sentFromGame = parts[4] == "1" || parts[4].equals("true", ignoreCase = true),
                        content = content,
                        isMine = self.isNotEmpty() && authorId == self,
                        mediaUrls = extractMediaUrls(content),
                        attachment = extraType
                            .takeIf { it.isNotEmpty() && !it.equals("Other", ignoreCase = true) }
                            ?.let { type ->
                                DiscordMessageAttachment(
                                    type = type,
                                    title = parts.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() },
                                    count = parts.getOrNull(8)?.trim()?.toIntOrNull() ?: 1,
                                )
                            },
                    )
                }
                .toList()
        }

        private val URL_PATTERN = Regex("""https?://\S+""")

        private val IMAGE_HOSTS = setOf(
            "cdn.discordapp.com",
            "media.discordapp.net",
            "media.tenor.com",
            "tenor.com",
            "c.tenor.com",
            "media.giphy.com",
            "i.giphy.com",
            "giphy.com",
            "i.imgur.com",
            "imgur.com",
        )

        private val IMAGE_EXTENSIONS = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".apng")

        /**
         * Picks out links the shell can render as pictures. Extension alone is not enough —
         * Tenor and Giphy share links have no suffix — so known media hosts count too.
         */
        internal fun extractMediaUrls(content: String): List<String> =
            URL_PATTERN.findAll(content)
                .map { it.value.trimEnd('.', ',', ')', ']', '>') }
                .filter { url ->
                    val withoutQuery = url.substringBefore('?').lowercase()
                    val host = url.substringAfter("://", "")
                        .substringBefore('/')
                        .substringBefore(':')
                        .lowercase()
                    IMAGE_EXTENSIONS.any { withoutQuery.endsWith(it) } || host in IMAGE_HOSTS
                }
                .distinct()
                .take(MAX_INLINE_MEDIA)
                .toList()

        private const val MAX_INLINE_MEDIA = 4

        /**
         * Builds a Discord CDN avatar URL.
         * When [avatarHash] is null/blank, uses Discord's default avatar for the user id.
         */
        fun discordAvatarUrl(userId: String, avatarHash: String?): String {
            val hash = avatarHash?.trim().orEmpty()
            if (hash.isNotEmpty()) {
                val ext = if (hash.startsWith("a_")) "gif" else "png"
                return "https://cdn.discordapp.com/avatars/$userId/$hash.$ext"
            }
            // Default avatar index: (user_id >> 22) % 6 for new snowflakes; fallback % 5.
            val index = runCatching {
                val id = userId.toLong()
                if (id shr 22 != 0L) ((id shr 22) % 6).toInt() else (id % 5).toInt()
            }.getOrDefault(0).coerceIn(0, 5)
            return "https://cdn.discordapp.com/embed/avatars/$index.png"
        }

        /**
         * JNI entry point. Must stay as a static method on this class so the native bridge can
         * call `DiscordSocialSdkBridge.onNativeStatusChanged`.
         */
        @JvmStatic
        fun onNativeStatusChanged(statusCode: Int, ready: Boolean, authorized: Boolean) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeStatus(statusCode, ready, authorized)
        }

        @JvmStatic
        fun onNativeTokensReceived(accessToken: String?, refreshToken: String?, expiresIn: Int) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeTokens(
                accessToken.orEmpty(),
                refreshToken.orEmpty(),
                expiresIn,
            )
        }

        @JvmStatic
        fun onNativeFriendsUpdated(payload: String?) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeFriends(payload.orEmpty())
        }

        @JvmStatic
        fun onNativePresenceResult(ok: Boolean, message: String?) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativePresenceResult(
                ok,
                message.orEmpty(),
            )
        }

        @JvmStatic
        fun onNativeAuthError(message: String?) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeAuthError(message.orEmpty())
        }

        @JvmStatic
        fun onNativeMessagesUpdated(recipientId: String?, payload: String?) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeMessages(
                recipientId.orEmpty(),
                payload.orEmpty(),
            )
        }

        @JvmStatic
        fun onNativeMessageSendResult(
            ok: Boolean,
            error: String?,
            recipientId: String?,
            messageId: String?,
        ) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeMessageSendResult(
                ok,
                error.orEmpty(),
                recipientId.orEmpty(),
                messageId.orEmpty(),
            )
        }

        @JvmStatic
        fun onNativeCurrentUser(userId: String?) {
            DiscordSocialSdkBridgeHolder.bridge?.applyNativeCurrentUser(userId.orEmpty())
        }
    }
}
