package com.arcadia.shell.xoranetwork

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one XOrA Network identity for the launcher. Wraps [XoraNetworkClient] with session
 * restore/refresh (survives process death), and exposes friendly [Result]s — callers never see
 * raw gateway errors. Public identity is the username; the Nakama UUID stays inside this module.
 */
@Singleton
class XoraNetworkRepository @Inject constructor(
    private val client: XoraNetworkClient,
    private val sessionStore: XoraSessionStore,
    private val authCookies: XoraNetworkAuthCookies,
    private val realtime: XoraNetworkRealtime,
    private val json: Json,
) {
    private val mutableState = MutableStateFlow(
        XoraNetworkState(
            configured = XoraNetworkClient.isConfigured,
            restoring = XoraNetworkClient.isConfigured,
        ),
    )
    val state: StateFlow<XoraNetworkState> = mutableState.asStateFlow()

    private var session: StoredXoraSession? = null
    private val sessionMutex = Mutex()
    @Volatile private var realtimeEnabled = false
    private val presenceOnline = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        realtime.setListener { event ->
            when (event) {
                is XoraPresenceEvent.Joins -> applyPresence(event.usernames, online = true)
                is XoraPresenceEvent.Leaves -> applyPresence(event.usernames, online = false)
            }
        }
    }

    /** Restores the persisted session on launch and hydrates account + friends. */
    suspend fun restore() {
        if (!XoraNetworkClient.isConfigured) {
            mutableState.update { it.copy(restoring = false) }
            return
        }
        val stored = sessionStore.read()
        if (stored == null) {
            mutableState.update { it.copy(restoring = false, signedIn = false) }
            return
        }
        session = stored
        authCookies.update(stored)
        val refreshed = runCatching { validAccessToken() }
        if (refreshed.isFailure) {
            mutableState.update { it.copy(restoring = false, signedIn = false) }
            return
        }
        mutableState.update { it.copy(signedIn = true) }
        refreshAccount()
        mutableState.update { it.copy(restoring = false) }
        refreshFriends()
        refreshNotifications()
        if (realtimeEnabled) connectRealtime()
    }

    /** Login only — `create=false` so this path can never mint a new account. */
    suspend fun signIn(email: String, password: String): Result<Unit> {
        XoraIdentityRules.emailError(email)?.let { return Result.failure(XoraNetworkException(it)) }
        if (password.isEmpty()) {
            return Result.failure(XoraNetworkException("Enter your password."))
        }
        return runCatching {
            val dto = client.authenticateEmail(email, password, create = false)
            adoptSession(StoredXoraSession(dto.token, dto.refreshToken))
        }.mapFriendly { statusCode ->
            when (statusCode) {
                401, 404 -> "Email or password is incorrect."
                else -> null
            }
        }
    }

    /**
     * Registration via `authenticateEmail(create = true, username)`. When the gateway reports the
     * session was NOT created, that email already exists — the accidental session is logged out
     * and the user is told to sign in instead (never silently signed in).
     */
    suspend fun register(
        email: String,
        password: String,
        username: String,
        displayName: String,
    ): Result<Unit> {
        XoraIdentityRules.emailError(email)?.let { return Result.failure(XoraNetworkException(it)) }
        XoraIdentityRules.passwordError(password)?.let { return Result.failure(XoraNetworkException(it)) }
        XoraIdentityRules.usernameError(username.trim())?.let { return Result.failure(XoraNetworkException(it)) }
        XoraIdentityRules.displayNameError(displayName.trim())?.let { return Result.failure(XoraNetworkException(it)) }
        return runCatching {
            val dto = client.authenticateEmail(email, password, create = true, username = username.trim())
            if (!dto.created) {
                runCatching { client.logout(dto.token, dto.refreshToken) }
                throw XoraNetworkException(
                    "That email already has an XOrA Network account. Sign in instead.",
                )
            }
            adoptSession(StoredXoraSession(dto.token, dto.refreshToken))
            runCatching { client.updateAccount(accessToken = dto.token, displayName = displayName.trim()) }
            refreshAccount()
        }.mapFriendly { null }
    }

    /** Full sign-out: server-side session invalidation plus local token deletion. */
    suspend fun signOut() {
        val current = session
        if (current != null) {
            runCatching { client.logout(current.accessToken, current.refreshToken) }
        }
        session = null
        sessionStore.clear()
        authCookies.clear()
        presenceOnline.clear()
        realtime.disconnect()
        mutableState.update {
            XoraNetworkState(configured = it.configured, restoring = false)
        }
    }

    suspend fun refreshAccount(): Result<Unit> = authenticated { token ->
        val dto = client.getAccount(token)
        val account = XoraAccount(
            username = dto.user.username,
            displayName = dto.user.displayName.ifBlank { dto.user.username },
            email = dto.email,
            location = dto.user.location,
            avatarUrl = dto.user.avatarUrl,
        )
        mutableState.update { it.copy(signedIn = true, account = account) }
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        location: String,
    ): Result<Unit> {
        val trimmedDisplay = displayName.trim()
        val trimmedUsername = username.trim()
        XoraIdentityRules.displayNameError(trimmedDisplay)?.let {
            return Result.failure(XoraNetworkException(it))
        }
        XoraIdentityRules.usernameError(trimmedUsername)?.let {
            return Result.failure(XoraNetworkException(it))
        }
        return authenticated { token ->
            val current = mutableState.value.account
            client.updateAccount(
                accessToken = token,
                displayName = trimmedDisplay.takeIf { it != current?.displayName },
                username = trimmedUsername.takeIf { it != current?.username },
                location = location.trim().takeIf { it != current?.location },
            )
            refreshAccount()
        }
    }

    suspend fun refreshFriends(): Result<Unit> {
        mutableState.update { it.copy(friendsLoading = true, friendsError = null) }
        val result = authenticated { token ->
            val refresh = session?.refreshToken.orEmpty()
            val fromNakama = runCatching {
                client.listFriends(token).mapNotNull { it.toXoraFriend() }
            }.getOrDefault(emptyList())
            val fromWebsite = if (refresh.isNotBlank()) {
                runCatching { client.listWebsiteFriends(token, refresh) }
                    .getOrDefault(WebsiteFriendsDataDto())
                    .let { data ->
                        data.incoming.mapNotNull { it.toXoraFriend(XoraFriendState.IncomingInvite) } +
                            data.outgoing.mapNotNull { it.toXoraFriend(XoraFriendState.OutgoingInvite) } +
                            data.friends.mapNotNull { it.toXoraFriend(XoraFriendState.Friend) }
                    }
            } else {
                emptyList()
            }
            val friends = mergeXoraFriends(fromNakama, fromWebsite).map { friend ->
                friend.copy(online = friend.online || friend.username.lowercase() in presenceOnline)
            }
            mutableState.update { it.copy(friends = friends, friendsError = null) }
            realtime.follow(friends.map { it.username })
            if (realtimeEnabled && !realtime.isConnected) connectRealtime()
        }
        mutableState.update { current ->
            current.copy(
                friendsLoading = false,
                friendsError = result.exceptionOrNull()?.message,
            )
        }
        return result
    }

    /** Sends an invite, or accepts one when [username] already has an incoming edge. */
    suspend fun addFriend(username: String): Result<Unit> {
        val target = username.trim()
        if (target.isEmpty()) {
            return Result.failure(XoraNetworkException("Enter a username first."))
        }
        val self = mutableState.value.account?.username
        if (self != null && target.equals(self, ignoreCase = true)) {
            return Result.failure(XoraNetworkException("That's you — add someone else."))
        }
        return authenticated { token ->
            client.addFriends(token, listOf(target))
            refreshFriends()
        }
    }

    /** Removes a friend, cancels an outgoing invite, or declines an incoming one. */
    suspend fun removeFriend(username: String): Result<Unit> = authenticated { token ->
        client.deleteFriends(token, listOf(username.trim()))
        refreshFriends()
    }

    /**
     * Reads the website's `xora_notifications` inbox for this user. The website may write it
     * server-side (system-owned) or per-user, so both owners are queried and merged. Failures
     * degrade to an empty inbox — never block sign-in/friends on this.
     */
    suspend fun refreshNotifications(): Result<Unit> = authenticated { token ->
        val usernameLower = (
            mutableState.value.account?.username
                ?: XoraJwt.username(token, json)
            ).lowercase()
        if (usernameLower.isBlank()) return@authenticated
        val ownUuid = XoraJwt.userId(token, json)
        val objects = runCatching {
            client.readStorageObjects(
                accessToken = token,
                collection = XoraNetworkClient.NOTIFICATIONS_COLLECTION,
                key = "inbox:$usernameLower",
                ownerIds = listOf(null, ownUuid.takeIf { it.isNotBlank() }),
            )
        }.getOrDefault(emptyList())
        val items = objects
            .flatMap { obj ->
                runCatching { json.decodeFromString<InboxValueDto>(obj.value).items }
                    .getOrDefault(emptyList())
            }
            .distinctBy { it.id.ifBlank { it.createdAt + it.fromUsername + it.body } }
            .map { dto ->
                XoraNotificationItem(
                    id = dto.id,
                    type = dto.type,
                    fromUsername = dto.fromUsername,
                    fromDisplayName = dto.fromDisplayName.ifBlank { dto.fromUsername },
                    body = dto.body,
                    createdAt = dto.createdAt,
                    read = dto.read,
                )
            }
            .sortedByDescending { it.createdAt }
        mutableState.update { it.copy(notifications = items) }
    }

    // -------------------------------------------------------------------------------------------

    private suspend fun adoptSession(newSession: StoredXoraSession) {
        session = newSession
        sessionStore.write(newSession)
        authCookies.update(newSession)
        mutableState.update { it.copy(signedIn = true) }
        refreshAccount()
        refreshFriends()
        refreshNotifications()
        if (realtimeEnabled) connectRealtime()
    }

    /** Runs [block] with a valid access token, refreshing (or signing out) as needed. */
    private suspend fun authenticated(block: suspend (String) -> Unit): Result<Unit> {
        return runCatching {
            val token = validAccessToken()
            block(token)
        }.mapFriendly { null }
    }

    private suspend fun validAccessToken(): String = sessionMutex.withLock {
        val current = session ?: throw XoraNetworkException("Sign in to XOrA Network first.")
        val expiresAt = XoraJwt.expirySeconds(current.accessToken, json)
        val now = System.currentTimeMillis() / 1000
        if (expiresAt > now + 60) return current.accessToken
        val refreshed = runCatching { client.refreshSession(current.refreshToken) }.getOrElse {
            // Refresh token rejected — the session is gone for good. Clean sign-out locally.
            session = null
            sessionStore.clear()
            authCookies.clear()
            presenceOnline.clear()
            realtime.disconnect()
            mutableState.update {
                XoraNetworkState(configured = it.configured, restoring = false)
            }
            throw XoraNetworkException("Your XOrA Network session expired. Sign in again.")
        }
        val next = StoredXoraSession(refreshed.token, refreshed.refreshToken.ifBlank { current.refreshToken })
        session = next
        sessionStore.write(next)
        authCookies.update(next)
        if (realtimeEnabled) connectRealtime()
        next.accessToken
    }

    /** Rewraps unexpected failures with friendly copy; [override] can specialise by HTTP status. */
    private fun <T> Result<T>.mapFriendly(override: (Int) -> String?): Result<Unit> = fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { error ->
            val friendly = when (error) {
                is XoraNetworkException -> {
                    val replacement = override(error.statusCode)
                    if (replacement != null) XoraNetworkException(replacement, error.statusCode) else error
                }
                else -> XoraNetworkException(
                    "Couldn't reach XOrA Network. Check your connection and try again.",
                )
            }
            Result.failure(friendly)
        },
    )

    /**
     * Opens the Nakama realtime socket while the shell is in the foreground so this account
     * (and followed friends) actually show as online. Closed on sleep so the radio stays quiet.
     */
    fun setRealtimeEnabled(enabled: Boolean) {
        realtimeEnabled = enabled
        if (!enabled) {
            realtime.disconnect()
            val self = mutableState.value.account?.username?.lowercase()
            if (self != null) presenceOnline.remove(self)
            mutableState.update { it.copy(selfOnline = false) }
            return
        }
        if (mutableState.value.signedIn) connectRealtime()
    }

    private fun connectRealtime() {
        val token = session?.accessToken?.takeIf { it.isNotBlank() } ?: return
        if (!realtime.isConnected) realtime.connect(token)
        realtime.follow(mutableState.value.friends.map { it.username })
    }

    private fun applyPresence(usernames: List<String>, online: Boolean) {
        val self = mutableState.value.account?.username?.lowercase()
        usernames.forEach { name ->
            val key = name.lowercase()
            if (online) presenceOnline.add(key) else presenceOnline.remove(key)
        }
        mutableState.update { state ->
            val keys = usernames.map { it.lowercase() }.toSet()
            val friends = state.friends.map { friend ->
                if (friend.username.lowercase() in keys) friend.copy(online = online) else friend
            }
            state.copy(
                friends = friends,
                selfOnline = self != null && self in presenceOnline,
            )
        }
    }
}
