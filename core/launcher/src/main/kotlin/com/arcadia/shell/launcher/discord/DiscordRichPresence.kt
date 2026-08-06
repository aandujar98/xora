package com.arcadia.shell.launcher.discord

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/**
 * Desired Discord Rich Presence activity for SORA.
 *
 * Live presence on Android uses Discord’s Social SDK (`discord_partner_sdk.aar` from the
 * Developer Portal) plus account linking. Classic discord-rpc IPC is desktop-only.
 */
sealed interface DiscordPresenceActivity {
    data object Idle : DiscordPresenceActivity

    /** User is in the SORA shell with no game focused. */
    data object InSora : DiscordPresenceActivity

    data class Browsing(
        val gameTitle: String,
        val platformName: String,
    ) : DiscordPresenceActivity

    data class Playing(
        val gameTitle: String,
        val platformName: String,
    ) : DiscordPresenceActivity
}

/**
 * Backend capability / connection status for Discord Rich Presence.
 *
 * Settings / Social UI surface the three primary states users care about:
 * [Connected], [NeedsDiscordApp], [SdkMissing] — plus [NotConfigured] when no Application ID.
 */
enum class DiscordPresenceCapability {
    /** No Application ID available. */
    NotConfigured,

    /**
     * Partner AAR / native bridge is not in this APK. Presence is tracked locally and a
     * shareable status bridge is available.
     */
    SdkMissing,

    /** SDK is present but Discord is not installed or not reachable for auth / RPC. */
    NeedsDiscordApp,

    /** SDK loaded; Discord account linking (OAuth) is required before live presence. */
    NeedsAccountLink,

    /** Social SDK client is ready and Rich Presence updates are publishing. */
    Connected,

    /** OAuth / Connect / publish failed — see [DiscordPresenceUiState.lastError]. */
    Failed,
}

/** A Discord relationship shown in Social once the Social SDK is linked. */
data class DiscordFriendEntry(
    val userId: String,
    val displayName: String,
    /** online_game | online_elsewhere | offline */
    val group: String,
    /**
     * Discord CDN avatar URL from Social SDK [UserHandle.AvatarUrl], or a constructed
     * `cdn.discordapp.com/avatars/{id}/{hash}.png` / default avatar when hash is known.
     */
    val avatarUrl: String? = null,
) {
    val isOnline: Boolean get() = group != "offline"
}

/** One DM message from the Social SDK (in-launcher Discord chat). */
data class DiscordDmMessage(
    val messageId: String,
    val authorId: String,
    val recipientId: String,
    val sentAtMs: Long,
    val sentFromGame: Boolean,
    val content: String,
    val isMine: Boolean = false,
)

/** Open DM thread state for the Social → Discord chat pane. */
data class DiscordDmThreadUiState(
    val peerUserId: String? = null,
    val peerDisplayName: String = "",
    val peerAvatarUrl: String? = null,
    val messages: List<DiscordDmMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

data class DiscordPresenceUiState(
    val applicationId: String = "",
    val activity: DiscordPresenceActivity = DiscordPresenceActivity.Idle,
    val capability: DiscordPresenceCapability = DiscordPresenceCapability.NotConfigured,
    /** Human-readable lines for Settings / Social UI. */
    val statusLine: String = "Add a Discord Application ID in Settings.",
    val detailLine: String = DETAIL_SDK_MISSING,
    /** Numbered install steps when [capability] is [DiscordPresenceCapability.SdkMissing]. */
    val setupSteps: List<String> = emptyList(),
    /** Mobile OAuth redirect URI to register in the Developer Portal. */
    val oauthRedirectUri: String = "",
    /** True when the Social SDK Java classes were found on the classpath. */
    val sdkOnClasspath: Boolean = false,
    /** True when the native JNI bridge loaded successfully. */
    val nativeBridgeLoaded: Boolean = false,
    /** Last auth / publish error for Settings / Social UI (null when healthy). */
    val lastError: String? = null,
    /** Discord friends from Social SDK (empty until linked / Ready). */
    val friends: List<DiscordFriendEntry> = emptyList(),
    /** Linked Discord user snowflake (for isMine on DM messages). */
    val currentUserId: String? = null,
    /**
     * True after OAuth UpdateToken succeeded (or a stored token is being restored) but before
     * [DiscordPresenceCapability.Connected] / Ready. Social UI shows “Connecting…” instead of
     * another “Needs link” prompt.
     */
    val connecting: Boolean = false,
    /**
     * True when Connected and the last UpdateRichPresence callback succeeded for a non-idle
     * activity (friends should see Playing SORA / browsing).
     */
    val presencePublishing: Boolean = false,
) {
    val isConfigured: Boolean get() = applicationId.isNotBlank()

    val connectionLabel: String
        get() = when (capability) {
            DiscordPresenceCapability.Connected ->
                if (presencePublishing) "Linked · Publishing presence" else "Linked · Connected"
            DiscordPresenceCapability.NeedsDiscordApp -> "Needs Discord app"
            DiscordPresenceCapability.NeedsAccountLink ->
                if (connecting) "Linking…" else "Link Discord to enable live presence"
            DiscordPresenceCapability.Failed -> "Link failed — retry"
            DiscordPresenceCapability.SdkMissing -> "SDK missing"
            DiscordPresenceCapability.NotConfigured -> "Not configured"
        }

    val shareText: String
        get() = when (val a = activity) {
            DiscordPresenceActivity.Idle -> "In XOrA"
            DiscordPresenceActivity.InSora -> "Playing XOrA"
            is DiscordPresenceActivity.Browsing ->
                "Browsing ${a.gameTitle} (${a.platformName}) · XOrA"
            is DiscordPresenceActivity.Playing ->
                "Playing ${a.gameTitle} on ${a.platformName} · XOrA"
        }

    companion object {
        const val DOCS_RICH_PRESENCE =
            "https://docs.discord.com/developers/discord-social-sdk/development-guides/setting-rich-presence"

        const val DOCS_ACCOUNT_LINKING_MOBILE =
            "https://docs.discord.com/developers/discord-social-sdk/development-guides/account-linking-on-mobile"

        const val PORTAL_APPLICATIONS =
            "https://discord.com/developers/applications"

        const val AAR_RELATIVE_PATH = "core/launcher/libs/discord_partner_sdk.aar"

        fun oauthRedirectUriFor(applicationId: String): String =
            "discord-${applicationId.trim()}:/authorize/callback"

        fun setupStepsFor(applicationId: String): List<String> {
            val id = applicationId.trim().ifBlank { "YOUR_APP_ID" }
            return listOf(
                "Open Discord Developer Portal → your app ($id). Set the application name to “XOrA” " +
                    "(that becomes the “Playing XOrA” line).",
                "Sidebar → Discord Social SDK → Downloads → Android package; extract discord_partner_sdk.aar.",
                "Copy it to $AAR_RELATIVE_PATH (filename must match exactly).",
                "In OAuth2, add redirect URI: ${oauthRedirectUriFor(id)}",
                "Enable Public Client on the OAuth2 tab (needed for on-device token exchange; no client secret in the app).",
                "Rebuild the app. Settings / Social should show “link account”, then Link Discord.",
            )
        }

        const val DETAIL_SDK_MISSING =
            "Live Rich Presence on Android needs Discord’s Social SDK partner AAR " +
                "($AAR_RELATIVE_PATH). It is not on Maven Central — download from the Developer Portal. " +
                "Until then XOrA keeps your Application ID and offers a shareable status bridge. " +
                "Docs: $DOCS_RICH_PRESENCE"

        const val DETAIL_CONNECTED =
            "Discord Social SDK Rich Presence is active. Discord shows “Playing XOrA” from " +
                "your Developer Portal application name; details/state are the secondary lines. " +
                "Keep Discord installed and signed in on this device. Logcat: SoraDiscord"

        const val DETAIL_NEEDS_DISCORD =
            "Install or open the Discord app, then Link Discord account (Social → Discord or Settings)."

        const val DETAIL_NEEDS_LINK =
            "On Android/handhelds, Rich Presence and in-launcher Discord DMs require Discord " +
                "account linking (Social → Discord → Link). Unauthenticated RPC is desktop-only. " +
                "After OAuth, status becomes Connected — friends see Playing XOrA and you can " +
                "message them inside Social. Portal: app name XOrA, Public Client on, redirect " +
                "URI registered. Scopes: Social SDK communication scopes (presence + messaging). " +
                "Re-link if you linked before messaging was enabled. Logcat: SoraDiscord"
    }
}

interface DiscordRichPresence {
    val state: StateFlow<DiscordPresenceUiState>

    /** Open Discord DM thread for the Social menu chat pane. */
    val dmThread: StateFlow<DiscordDmThreadUiState>

    fun setApplicationId(applicationId: String)

    fun setActivity(activity: DiscordPresenceActivity)

    /**
     * Clears remote Discord presence when leaving the app / going to background.
     * Does not wipe the intended activity so foreground can republish.
     */
    fun clear()

    /** Process entered foreground — restore SDK callbacks and republish presence. */
    fun onAppForeground()

    /** Process entered background — stop presence spam; keep OAuth callbacks alive. */
    fun onAppBackground()

    /**
     * Binds the Social SDK to the host [Activity] (required for Authorize / deep links).
     * Safe no-op when the AAR is absent.
     */
    fun attachHostActivity(activity: Activity)

    /** Starts Discord account linking when the Social SDK is present. */
    fun startAccountLinking(activity: Activity)

    /** Opens an in-launcher DM with a Discord friend (Social SDK messaging). */
    fun openDm(userId: String, displayName: String, avatarUrl: String?)

    /** Closes the in-launcher DM pane and clears draft / messages. */
    fun closeDm()

    fun updateDmDraft(draft: String)

    /** Sends [DiscordDmThreadUiState.draft] to the open peer. */
    fun sendDm()

    /** Reloads recent messages for the open DM peer. */
    fun refreshDm()

    /**
     * Builds a chooser intent that shares [DiscordPresenceUiState.shareText] (status bridge).
     * Returns null when there is nothing useful to share.
     */
    fun statusBridgeShareIntent(context: Context): Intent?

    /** Opens the Discord Developer Portal applications list (for AAR download). */
    fun openDeveloperPortalIntent(): Intent
}
