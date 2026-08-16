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
    @Volatile private var presenceMode = XoraPresenceMode.Online
    @Volatile private var playingLine: String = ""
    @Volatile private var socketAppearsOnline = true
    private val presenceOnline = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val presenceStatus = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        realtime.setListener { event ->
            when (event) {
                is XoraPresenceEvent.Joins -> applyPresence(event.users, online = true)
                is XoraPresenceEvent.Leaves -> applyPresence(event.users, online = false)
                XoraPresenceEvent.Connected -> applySelfOnline(presenceMode != XoraPresenceMode.Invisible)
                XoraPresenceEvent.Disconnected -> applySelfOnline(false)
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
        val now = System.currentTimeMillis()
        val lastActive = stored.lastActiveEpochMs.takeIf { it > 0L } ?: now
        if (sessionIdleExpired(lastActive, now)) {
            signOut()
            return
        }
        session = stored.copy(lastActiveEpochMs = lastActive)
        authCookies.update(session)
        mutableState.update { it.copy(signedIn = true) }
        val token = runCatching { validAccessToken() }
        if (token.isFailure) {
            mutableState.update {
                it.copy(restoring = false, signedIn = session != null)
            }
            return
        }
        mutableState.update { it.copy(signedIn = true) }
        refreshAccount()
        mutableState.update { it.copy(restoring = false) }
        refreshFriends()
        refreshNotifications()
        if (realtimeEnabled) connectRealtime()
    }

    /** Login only — website `/api/auth/login` so email OR username works; never mints an account. */
    suspend fun signIn(identifier: String, password: String): Result<Unit> {
        val id = identifier.trim()
        XoraIdentityRules.loginIdentifierError(id)?.let { return Result.failure(XoraNetworkException(it)) }
        if (password.isEmpty()) {
            return Result.failure(XoraNetworkException("Enter your password."))
        }
        return runCatching {
            val dto = runCatching { client.websiteLogin(id, password) }.getOrElse { error ->
                if (error is XoraNetworkException && isExpiredAuth(error.statusCode)) throw error
                if ('@' in id) client.authenticateEmail(id, password, create = false) else throw error
            }
            adoptSession(
                StoredXoraSession(dto.token, dto.refreshToken),
                identifier = id,
                password = password,
            )
        }.mapFriendly { statusCode ->
            when (statusCode) {
                401, 404 -> "Email, username, or password is incorrect."
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
            adoptSession(
                StoredXoraSession(dto.token, dto.refreshToken),
                identifier = email.trim(),
                password = password,
            )
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
        presenceStatus.clear()
        realtime.disconnect()
        client.clearCsrf()
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
                val key = friend.username.lowercase()
                friend.copy(
                    online = friend.online || key in presenceOnline,
                    status = presenceStatus[key].orEmpty().ifBlank { friend.status },
                )
            }.let { merged -> fillMissingFriendUserIds(token, merged) }
            mutableState.update { it.copy(friends = friends, friendsError = null) }
            followPresenceTargets()
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
     * Reads the website notification inbox (DMs, friend requests) plus Nakama storage as a
     * fallback. Website DMs never land in `xora_notifications` storage — `/api/notifications`
     * is the live source. Failures degrade to an empty inbox so sign-in/friends still work.
     */
    suspend fun refreshNotifications(): Result<Unit> = authenticated { token ->
        val refresh = session?.refreshToken.orEmpty()
        val websiteItems = runCatching {
            client.listWebsiteNotifications(token, refresh)
        }.getOrDefault(emptyList())
        val threadItems = syntheticThreadInboxItems(
            websiteItems = websiteItems,
            unreadThreads = runCatching { client.listMessageThreads(token, refresh) }
                .getOrDefault(emptyList()),
        )
        val usernameLower = (
            mutableState.value.account?.username
                ?: XoraJwt.username(token, json)
            ).lowercase()
        val ownUuid = XoraJwt.userId(token, json)
        val storageItems = if (usernameLower.isNotBlank()) {
            runCatching {
                client.readStorageObjects(
                    accessToken = token,
                    collection = XoraNetworkClient.NOTIFICATIONS_COLLECTION,
                    key = "inbox:$usernameLower",
                    ownerIds = listOf(null, ownUuid.takeIf { it.isNotBlank() }),
                )
            }.getOrDefault(emptyList())
                .flatMap { obj ->
                    runCatching { json.decodeFromString<InboxValueDto>(obj.value).items }
                        .getOrDefault(emptyList())
                }
        } else {
            emptyList()
        }
        val items = (websiteItems + threadItems + storageItems)
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

    /**
     * Writes a netplay invite the recipient can poll from this account's public-read storage.
     * Overwrites any previous pending invite to the same friend.
     */
    suspend fun sendNetplayInvite(
        toUsername: String,
        code: String,
        gameTitle: String,
        platformId: String,
        coreName: String,
    ): Result<Unit> {
        val target = toUsername.trim()
        if (target.isEmpty()) {
            return Result.failure(XoraNetworkException("Pick a friend to invite."))
        }
        val sessionCode = code.trim()
        if (!XoraNetplayInvites.hasJoinableCode(XoraNetplayInviteRecord(code = sessionCode))) {
            return Result.failure(XoraNetworkException("Couldn't start a session to invite with."))
        }
        return authenticated { token ->
            val account = mutableState.value.account
                ?: throw XoraNetworkException("Sign in to XOrA Network first.")
            val invite = XoraNetplayInviteRecord(
                code = sessionCode,
                toUsername = target,
                gameTitle = gameTitle.trim(),
                platformId = platformId.trim(),
                coreName = coreName.trim(),
                fromUsername = account.username,
                fromDisplayName = account.displayName.ifBlank { account.username },
                createdAtMs = System.currentTimeMillis(),
            )
            val encoded = XoraNetplayInvites.encodeValue(invite, json)
            client.writeStorageObject(
                accessToken = token,
                collection = XoraNetplayInvites.COLLECTION,
                key = XoraNetplayInvites.recipientKey(target),
                value = encoded,
                permissionRead = XoraNetplayInvites.PERMISSION_PUBLIC_READ,
            )
        }
    }

    /** Polls accepted friends' invite outboxes for rows addressed to this account. */
    suspend fun refreshNetplayInvites(): Result<Unit> = authenticated { token ->
        val self = (
            mutableState.value.account?.username
                ?: XoraJwt.username(token, json)
            ).trim()
        var owners = mutableState.value.acceptedFriends
            .map { it.userId.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (owners.isEmpty()) {
            val names = mutableState.value.acceptedFriends
                .map { it.username.trim() }
                .filter { it.isNotBlank() }
            if (names.isNotEmpty()) {
                owners = client.listUsersByUsernames(token, names)
                    .map { it.id.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
        }
        if (self.isBlank() || owners.isEmpty()) {
            mutableState.update { it.copy(netplayInvites = emptyList()) }
            return@authenticated
        }
        val objects = owners.chunked(40).flatMap { chunk ->
            client.readStorageObjects(
                accessToken = token,
                collection = XoraNetplayInvites.COLLECTION,
                key = XoraNetplayInvites.recipientKey(self),
                ownerIds = chunk,
            )
        }
        val now = System.currentTimeMillis()
        val invites = objects
            .flatMap { obj -> XoraNetplayInvites.parseValue(obj.value, json) }
            .let { XoraNetplayInvites.addressedTo(it, self, now) }
            .distinctBy { it.dedupeKey() }
            .sortedByDescending { it.createdAtMs }
        mutableState.update { it.copy(netplayInvites = invites) }
    }

    private suspend fun fillMissingFriendUserIds(
        token: String,
        friends: List<XoraFriend>,
    ): List<XoraFriend> {
        val missing = friends
            .filter { it.userId.isBlank() }
            .map { it.username.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (missing.isEmpty()) return friends
        val byName = runCatching { client.listUsersByUsernames(token, missing) }
            .getOrDefault(emptyList())
            .associateBy { it.username.trim().lowercase() }
        if (byName.isEmpty()) return friends
        return friends.map { friend ->
            if (friend.userId.isNotBlank()) {
                friend
            } else {
                friend.copy(userId = byName[friend.username.trim().lowercase()]?.id.orEmpty())
            }
        }
    }

    // -------------------------------------------------------------------------------------------

    private suspend fun adoptSession(
        newSession: StoredXoraSession,
        identifier: String? = null,
        password: String? = null,
    ) {
        val previous = session
        val merged = newSession.copy(
            identifier = identifier?.trim()?.ifBlank { null }
                ?: previous?.identifier?.ifBlank { null }
                ?: newSession.identifier,
            password = password?.takeIf { it.isNotEmpty() }
                ?: previous?.password?.takeIf { it.isNotEmpty() }
                ?: newSession.password,
            lastActiveEpochMs = System.currentTimeMillis(),
        )
        session = merged
        sessionStore.write(merged)
        authCookies.update(merged)
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
        val nowMs = System.currentTimeMillis()
        if (sessionIdleExpired(current.lastActiveEpochMs, nowMs)) {
            clearSessionLocked()
            throw XoraNetworkException("Signed out of XOrA Network after a week away.")
        }
        val expiresAt = XoraJwt.expirySeconds(current.accessToken, json)
        val nowSec = nowMs / 1000
        // The server mints refresh tokens that die BEFORE the access token (~1h vs ~2h). Rotate
        // while the refresh token is still alive; waiting for the access token to expire always
        // landed on a dead refresh token and forced the password re-auth path.
        val refreshExpiresAt = XoraJwt.expirySeconds(current.refreshToken, json)
        val refreshDyingSoon = refreshExpiresAt > 0 &&
            refreshExpiresAt <= nowSec + REFRESH_ROTATE_MARGIN_SECONDS
        if (expiresAt > nowSec + 60 && !refreshDyingSoon) {
            touchLastActiveLocked(nowMs)
            return current.accessToken
        }
        val refreshed = runCatching { client.refreshSession(current.refreshToken) }
        refreshed.getOrNull()?.let { dto ->
            persistLocked(
                current.copy(
                    accessToken = dto.token,
                    refreshToken = dto.refreshToken.ifBlank { current.refreshToken },
                    lastActiveEpochMs = nowMs,
                ),
            )
            if (realtimeEnabled) connectRealtime()
            return session!!.accessToken
        }
        val refreshError = refreshed.exceptionOrNull()
        val refreshStatus = (refreshError as? XoraNetworkException)?.statusCode ?: 0
        if (!isExpiredAuth(refreshStatus)) {
            if (expiresAt > nowSec) {
                touchLastActiveLocked(nowMs)
                return current.accessToken
            }
            throw refreshError
                ?: XoraNetworkException("Couldn't reach XOrA Network. Check your connection and try again.")
        }
        silentReauthLocked(current, nowMs)?.let { return it }
        // A proactive rotation that failed must not sign out a still-valid access token —
        // keep using it and retry the rotation on the next call.
        if (expiresAt > nowSec + 60) {
            touchLastActiveLocked(nowMs)
            return current.accessToken
        }
        clearSessionLocked()
        throw XoraNetworkException("Your XOrA Network session expired. Sign in again.")
    }

    private suspend fun silentReauthLocked(current: StoredXoraSession, nowMs: Long): String? {
        if (!current.canSilentReauth) return null
        val website = runCatching { client.websiteLogin(current.identifier, current.password) }
        val dto = website.getOrNull() ?: run {
            val error = website.exceptionOrNull() ?: return null
            val status = (error as? XoraNetworkException)?.statusCode ?: 0
            when {
                isExpiredAuth(status) && '@' in current.identifier -> {
                    val emailLogin = runCatching {
                        client.authenticateEmail(current.identifier, current.password, create = false)
                    }
                    emailLogin.getOrNull() ?: run {
                        val emailError = emailLogin.exceptionOrNull() ?: return null
                        if (emailError is XoraNetworkException && isExpiredAuth(emailError.statusCode)) {
                            return null
                        }
                        throw emailError
                    }
                }
                isExpiredAuth(status) -> return null
                else -> throw error
            }
        }
        persistLocked(
            current.copy(
                accessToken = dto.token,
                refreshToken = dto.refreshToken.ifBlank { current.refreshToken },
                lastActiveEpochMs = nowMs,
            ),
        )
        if (realtimeEnabled) connectRealtime()
        return session!!.accessToken
    }

    private fun persistLocked(next: StoredXoraSession) {
        session = next
        sessionStore.write(next)
        authCookies.update(next)
    }

    private fun touchLastActiveLocked(nowMs: Long) {
        val current = session ?: return
        val next = current.copy(lastActiveEpochMs = nowMs)
        session = next
        if (nowMs - current.lastActiveEpochMs >= 15L * 60 * 1000) {
            sessionStore.write(next)
        }
    }

    private fun clearSessionLocked() {
        session = null
        sessionStore.clear()
        authCookies.clear()
        presenceOnline.clear()
        presenceStatus.clear()
        realtime.disconnect()
        client.clearCsrf()
        mutableState.update {
            XoraNetworkState(configured = it.configured, restoring = false)
        }
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
            if (self != null) {
                presenceOnline.remove(self)
                presenceStatus.remove(self)
            }
            mutableState.update { it.copy(selfOnline = false) }
            return
        }
        if (mutableState.value.signedIn) connectRealtime()
    }

    fun setPresenceMode(mode: XoraPresenceMode) {
        if (presenceMode == mode) {
            publishStatus()
            return
        }
        presenceMode = mode
        mutableState.update { it.copy(presenceMode = mode) }
        if (!realtimeEnabled || !mutableState.value.signedIn) {
            mutableState.update { it.copy(selfOnline = false) }
            return
        }
        connectRealtime()
    }

    fun setPlayingLine(line: String?) {
        val next = line?.trim().orEmpty()
        if (playingLine == next) return
        playingLine = next
        if (presenceMode == XoraPresenceMode.Online) publishStatus()
    }

    /**
     * Creates or joins a named Nakama match on the already-open realtime socket.
     * Two devices that use the same name land in the same relayed match.
     */
    suspend fun openNamedMatch(name: String): Result<XoraNetworkMatchSession> {
        if (!mutableState.value.signedIn) {
            return Result.failure(XoraNetworkException("Sign in to XOrA Network first."))
        }
        setRealtimeEnabled(true)
        return runCatching {
            validAccessToken()
            connectRealtime()
            if (!realtime.awaitConnected(8_000L)) {
                throw XoraNetworkException("XOrA Network isn't online yet. Check your connection.")
            }
            realtime.createNamedMatch(name.trim())
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    if (error is XoraNetworkException) {
                        error
                    } else {
                        XoraNetworkException(
                            "Couldn't reach XOrA Network. Check your connection and try again.",
                        )
                    },
                )
            },
        )
    }

    suspend fun waitForMatchPeer(
        matchId: String,
        selfUserId: String,
        timeoutMs: Long = 180_000L,
    ): Result<Unit> = runCatching {
        realtime.waitForMatchPeer(matchId, selfUserId, timeoutMs)
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { error ->
            Result.failure(
                if (error is XoraNetworkException) {
                    error
                } else {
                    XoraNetworkException("Nobody joined with that code.")
                },
            )
        },
    )

    fun sendMatchData(matchId: String, opcode: Int, data: ByteArray, reliable: Boolean = true) {
        realtime.sendMatchData(matchId, opcode, data, reliable)
    }

    fun receiveMatchData(matchId: String, timeoutMs: Int): Pair<Int, ByteArray> =
        realtime.receiveMatchData(matchId, timeoutMs)

    fun leaveMatch(matchId: String) {
        realtime.leaveMatch(matchId)
    }

    suspend fun openDirectMessage(username: String): Result<Unit> {
        val target = username.trim()
        if (target.isEmpty()) {
            return Result.failure(XoraNetworkException("Pick a friend to message."))
        }
        mutableState.update {
            it.copy(
                dm = XoraDmUiState(
                    peerUsername = target,
                    peerDisplayName = it.friends.firstOrNull { friend ->
                        friend.username.equals(target, ignoreCase = true)
                    }?.displayName ?: target,
                    loading = true,
                    error = null,
                ),
            )
        }
        return authenticated { token ->
            val refresh = session?.refreshToken.orEmpty()
            val data = client.getMessageThread(token, refresh, target)
            mutableState.update { state ->
                state.copy(
                    dm = state.dm.copy(
                        peerUsername = data.username.ifBlank { target },
                        peerDisplayName = data.displayName.ifBlank { target },
                        messages = data.messages.map { it.toDirectMessage() },
                        loading = false,
                        error = null,
                    ),
                )
            }
        }.onFailure { error ->
            mutableState.update {
                it.copy(dm = it.dm.copy(loading = false, error = error.message))
            }
        }
    }

    fun closeDirectMessage() {
        mutableState.update { it.copy(dm = XoraDmUiState()) }
    }

    /**
     * Silent re-pull of the open thread so a live chat shows the peer's replies — banners are
     * suppressed for the open peer, so without this the conversation never updated until it was
     * closed and reopened. No loading flag, keeps the draft, and skips the swap mid-send so a
     * stale poll can't briefly erase a just-sent message. Failures keep the current messages.
     */
    suspend fun refreshOpenDirectMessage(): Result<Unit> {
        val peer = mutableState.value.dm.peerUsername?.trim().orEmpty()
        if (peer.isEmpty()) return Result.success(Unit)
        return authenticated { token ->
            val refresh = session?.refreshToken.orEmpty()
            val data = client.getMessageThread(token, refresh, peer)
            mutableState.update { state ->
                val dm = state.dm
                val samePeer = dm.peerUsername?.equals(peer, ignoreCase = true) == true
                if (!samePeer || dm.sending) {
                    state
                } else {
                    state.copy(
                        dm = dm.copy(
                            peerDisplayName = data.displayName.ifBlank { dm.peerDisplayName },
                            messages = data.messages.map { it.toDirectMessage() },
                            loading = false,
                        ),
                    )
                }
            }
        }
    }

    fun updateDirectMessageDraft(draft: String) {
        mutableState.update { it.copy(dm = it.dm.copy(draft = draft.take(500))) }
    }

    suspend fun sendDirectMessage(): Result<Unit> {
        val dm = mutableState.value.dm
        val peer = dm.peerUsername?.trim().orEmpty()
        val body = dm.draft.trim()
        if (peer.isEmpty()) {
            return Result.failure(XoraNetworkException("Pick a friend to message."))
        }
        if (body.isEmpty()) {
            return Result.failure(XoraNetworkException("Enter a message first."))
        }
        mutableState.update { it.copy(dm = it.dm.copy(sending = true, error = null)) }
        return authenticated { token ->
            val refresh = session?.refreshToken.orEmpty()
            val data = client.sendWebsiteMessage(token, refresh, peer, body)
            mutableState.update { state ->
                state.copy(
                    dm = state.dm.copy(
                        messages = data.messages.map { it.toDirectMessage() },
                        draft = "",
                        sending = false,
                        error = null,
                    ),
                )
            }
        }.onFailure { error ->
            mutableState.update {
                it.copy(dm = it.dm.copy(sending = false, error = error.message))
            }
        }
    }

    private fun connectRealtime() {
        val token = session?.accessToken?.takeIf { it.isNotBlank() } ?: return
        val appear = presenceMode != XoraPresenceMode.Invisible
        realtime.updateStatus(encodeXoraStatus(presenceMode, playingLine))
        when {
            !realtime.isConnected || socketAppearsOnline != appear -> {
                realtime.connect(token, appearOnline = appear)
                socketAppearsOnline = appear
            }
            appear -> publishStatus()
        }
        followPresenceTargets()
    }

    private fun publishStatus() {
        if (presenceMode == XoraPresenceMode.Invisible) return
        realtime.updateStatus(encodeXoraStatus(presenceMode, playingLine))
    }

    private fun followPresenceTargets() {
        val state = mutableState.value
        val self = state.account?.username
        realtime.follow(listOfNotNull(self) + state.friends.map { it.username })
    }

    private fun applySelfOnline(online: Boolean) {
        val visible = online && presenceMode != XoraPresenceMode.Invisible
        val self = mutableState.value.account?.username?.lowercase()
        if (self != null) {
            if (visible) presenceOnline.add(self) else presenceOnline.remove(self)
        }
        mutableState.update { it.copy(selfOnline = visible, presenceMode = presenceMode) }
    }

    private fun applyPresence(users: List<XoraPresenceUser>, online: Boolean) {
        val self = mutableState.value.account?.username?.lowercase()
        users.forEach { user ->
            val key = user.username.lowercase()
            if (online) {
                presenceOnline.add(key)
                presenceStatus[key] = user.status
            } else {
                presenceOnline.remove(key)
                presenceStatus.remove(key)
            }
        }
        mutableState.update { state ->
            val keys = users.map { it.username.lowercase() }.toSet()
            val friends = state.friends.map { friend ->
                val key = friend.username.lowercase()
                if (key !in keys) friend else {
                    friend.copy(
                        online = online,
                        status = if (online) presenceStatus[key].orEmpty() else "",
                    )
                }
            }
            val stillOnline = presenceMode != XoraPresenceMode.Invisible &&
                (realtime.isConnected || (self != null && self in presenceOnline))
            state.copy(
                friends = friends,
                selfOnline = stillOnline,
            )
        }
    }
}

private fun WebsiteMessageDto.toDirectMessage(): XoraDirectMessage = XoraDirectMessage(
    id = id,
    fromUsername = fromUsername,
    body = body,
    createdAt = createdAt,
)

/** Rotate the session while the refresh token still has at least this long to live. */
private const val REFRESH_ROTATE_MARGIN_SECONDS = 10L * 60
