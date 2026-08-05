package com.arcadia.shell.launcher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.arcadia.shell.database.repository.PlayerRepository
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.libretro.CoreDownloader
import com.arcadia.shell.libretro.CoreStore
import com.arcadia.shell.libretro.XoraLibretroPlayers
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepository: PlayerRepository,
    private val placeholderResolver: PlaceholderResolver,
    private val probe: InstalledPlayerProbe,
    private val sessionTracker: PlaySessionTracker,
    private val preferences: ShellPreferences,
    private val coreStore: CoreStore,
    private val coreDownloader: CoreDownloader,
) {
    private val activityManager: ActivityManager
        get() = requireNotNull(context.getSystemService(ActivityManager::class.java))

    private val supportsSecondaryDisplays: Boolean by lazy {
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
        )
    }

    /**
     * Picks the launch recipe for a game, in order of how explicitly the user asked for it: a
     * per-game override, then Choose Emulator ([ShellPreferences] platform map), then the legacy
     * N64 Mupen toggle, then Settings' platform player, then any installed player that claims the
     * platform and accepts the filename.
     */
    suspend fun resolvePlayer(game: Game): Player? {
        if (game.isAndroidApp) return null

        game.playerIdOverride?.let { override ->
            playerRepository.findById(override)?.let { player ->
                return bindRetroArchPackage(player) ?: player
            }
        }

        resolveChosenPlayer(game.platformId)?.let { return it }

        resolvePreferredPlayerId(game.platformId)?.let { preferredId ->
            resolvePlayerById(preferredId)?.let { return it }
        }

        // Prefer in-process XOrA Libretro when the matching core is already on disk.
        resolveXoraPlayerIfReady(game)?.let { return it }

        val candidates = playerRepository.getPlayers()
            .filter { game.platformId in it.platformIds && it.accepts(game.fileName) }

        val installed = probe.installedPlayers(candidates).firstOrNull()
        if (installed != null) return bindRetroArchPackage(installed) ?: installed

        return candidates.firstOrNull()?.let { bindRetroArchPackage(it) ?: it }
    }

    private suspend fun resolveXoraPlayerIfReady(game: Game): Player? {
        val candidates = playerRepository.getPlayers()
            .filter { XoraLibretroPlayers.isXoraPlayer(it) && game.platformId in it.platformIds }
        return candidates.firstOrNull { player ->
            val core = XoraLibretroPlayers.coreNameFromPlayer(player) ?: return@firstOrNull false
            coreStore.isInstalled(core)
        }
    }

    /**
     * Honours Choose Emulator even when the pick was a core discovered on disk rather than a
     * seeded recipe: those ids exist in no player table, so the recipe is rebuilt from the stored
     * core name instead of silently falling through to whatever else claims the platform.
     */
    private suspend fun resolveChosenPlayer(platformId: String): Player? {
        val choice = preferences.platformEmulatorChoice(platformId) ?: return null
        resolvePlayerById(choice.playerId)?.let { return it }

        val core = choice.coreName?.takeIf { it.isNotBlank() } ?: return null
        if (XoraLibretroPlayers.isXoraPlayerId(choice.playerId)) {
            return XoraLibretroPlayers.playerFor(platformId, core, core)
        }
        val packageName = choice.packageName?.takeIf { probe.isInstalled(it) }
            ?: RetroArchPackages.findInstalledPackage(probe)
            ?: return null
        return BuiltInPlayers.retroArchCorePlayer(
            playerId = choice.playerId,
            platformId = platformId,
            core = core,
            packageName = packageName,
        )
    }

    private suspend fun resolvePreferredPlayerId(platformId: String): String? {
        preferences.platformEmulatorChoice(platformId)?.playerId?.let { return it }
        if (platformId == "n64" && preferences.settings.first().n64UseMupen64PlusNext) {
            return BuiltInPlayers.RETROARCH_N64_PLAYER_ID
        }
        return playerRepository.settingsFor(platformId)?.selectedPlayerId
    }

    private suspend fun resolvePlayerById(playerId: String): Player? {
        val player = playerRepository.findById(playerId)
            ?: BuiltInPlayers.all.firstOrNull { it.uniqueId == playerId }
            ?: return null
        return bindRetroArchPackage(player) ?: player
    }

    suspend fun launch(game: Game, targetDisplayId: Int? = null): LaunchResult {
        if (game.isAndroidApp) return launchAndroidApp(game, targetDisplayId)

        val player = resolvePlayer(game)
            ?: return LaunchResult.NoPlayerConfigured(game.platform.displayName)

        if (XoraLibretroPlayers.isXoraPlayer(player)) {
            return launchXoraLibretro(game, player, targetDisplayId)
        }

        val packageName = player.packageName
            ?: return LaunchResult.InvalidTemplate(
                player,
                "The launch template has no -n component, so there is no app to start.",
            )

        if (!probe.isInstalled(packageName)) {
            val reason = if (RetroArchPackages.isRetroArchPlayer(player)) {
                RetroArchPackages.missingInstallMessage(
                    RetroArchPackages.coreNameFromPlayer(player),
                )
            } else {
                null
            }
            return if (reason != null) {
                LaunchResult.Failed(player, reason)
            } else {
                LaunchResult.PlayerNotInstalled(player, packageName)
            }
        }

        val intent = try {
            buildIntent(player, game)
        } catch (missing: MissingPlaceholderException) {
            return LaunchResult.UnsupportedSource(player, missing.message)
        } catch (throwable: Throwable) {
            return LaunchResult.InvalidTemplate(
                player,
                throwable.message ?: "Could not build a launch intent.",
            )
        }

        if (player.killPackageProcesses) {
            // Only affects background processes; a foreground emulator survives this by design.
            runCatching { activityManager.killBackgroundProcesses(packageName) }
        }

        val (options, fallbackReason) = displayOptions(intent, targetDisplayId)

        return try {
            context.startActivity(intent, options)
            sessionTracker.onLaunched(game.id)
            LaunchResult.Launched(
                player = player,
                displayId = if (options != null) targetDisplayId else null,
                displayFallbackReason = fallbackReason,
            )
        } catch (throwable: Throwable) {
            LaunchResult.Failed(
                player,
                throwable.message ?: throwable::class.simpleName ?: "Launch failed.",
            )
        }
    }

    private suspend fun launchXoraLibretro(
        game: Game,
        player: Player,
        targetDisplayId: Int?,
    ): LaunchResult {
        val coreName = XoraLibretroPlayers.coreNameFromPlayer(player)
            ?: return LaunchResult.InvalidTemplate(player, "XOrA Libretro player has no CORE_NAME.")
        val romPath = game.filePath
            ?: return LaunchResult.UnsupportedSource(
                player,
                "XOrA Emulator needs a filesystem ROM path (all-files access).",
            )

        val corePath = coreStore.resolveInstalledPath(coreName)
            ?: coreDownloader.ensureCore(coreName)
            ?: return LaunchResult.Failed(
                player,
                "Could not download Libretro core '$coreName'. Open Setup → XOrA Emulator.",
            )

        val intent = Intent().apply {
            component = ComponentName(XoraLibretroPlayers.PACKAGE, XoraLibretroPlayers.ACTIVITY)
            putExtra(XoraLibretroPlayers.EXTRA_ROM_PATH, romPath)
            putExtra(XoraLibretroPlayers.EXTRA_CORE_NAME, coreName)
            putExtra(XoraLibretroPlayers.EXTRA_CORE_PATH, corePath)
            putExtra(XoraLibretroPlayers.EXTRA_PLATFORM_ID, game.platformId)
            putExtra(XoraLibretroPlayers.EXTRA_GAME_ID, game.id)
            putExtra(XoraLibretroPlayers.EXTRA_GAME_TITLE, game.title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val (options, fallbackReason) = xoraEmulatorOptions(intent, targetDisplayId)
        return try {
            context.startActivity(intent, options)
            sessionTracker.onLaunched(game.id)
            LaunchResult.Launched(
                player = player,
                displayId = if (targetDisplayId != null && options != null && fallbackReason == null) {
                    targetDisplayId
                } else {
                    null
                },
                displayFallbackReason = fallbackReason,
            )
        } catch (throwable: Throwable) {
            LaunchResult.Failed(
                player,
                throwable.message ?: throwable::class.simpleName ?: "XOrA Emulator launch failed.",
            )
        }
    }

    /**
     * Crossfade into the in-process emulator (no Android slide). Display targeting is layered on
     * the same [ActivityOptions] when a secondary screen is requested.
     */
    private fun xoraEmulatorOptions(intent: Intent, targetDisplayId: Int?): Pair<Bundle?, String?> {
        val fade = ActivityOptions.makeCustomAnimation(
            context,
            com.arcadia.shell.libretro.R.anim.xora_fade_in,
            com.arcadia.shell.libretro.R.anim.xora_hold,
        )
        if (targetDisplayId == null) {
            return fade.toBundle() to null
        }
        if (!supportsSecondaryDisplays) {
            return fade.toBundle() to
                "This device does not support launching apps on a secondary display."
        }
        val allowed = runCatching {
            activityManager.isActivityStartAllowedOnDisplay(context, targetDisplayId, intent)
        }.getOrDefault(false)
        if (!allowed) {
            return fade.toBundle() to
                "Android refused to start this emulator on display $targetDisplayId, " +
                "so it opened on the main screen instead."
        }
        return runCatching {
            fade.setLaunchDisplayId(targetDisplayId).toBundle() to null
        }.getOrElse {
            fade.toBundle() to "Could not target display $targetDisplayId."
        }
    }

    /**
     * For RetroArch recipes, retarget `-n` / core / config paths to an installed package.
     * Returns null when the recipe is RetroArch but no known package is present.
     */
    private fun bindRetroArchPackage(player: Player): Player? {
        if (!RetroArchPackages.isRetroArchPlayer(player)) return player
        val installed = RetroArchPackages.findInstalledPackage(probe) ?: return null
        return RetroArchPackages.withPackage(player, installed)
    }

    private suspend fun launchAndroidApp(game: Game, targetDisplayId: Int?): LaunchResult {
        val packageName = game.filePath
            ?: return LaunchResult.Failed(null, "This app entry has no package name.")

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return LaunchResult.Failed(
                null,
                "${game.title} is not installed or has no launchable activity.",
            )

        val (options, fallbackReason) = displayOptions(intent, targetDisplayId)

        return try {
            context.startActivity(intent, options)
            sessionTracker.onLaunched(game.id)
            LaunchResult.Launched(
                player = null,
                displayId = if (options != null) targetDisplayId else null,
                displayFallbackReason = fallbackReason,
            )
        } catch (throwable: Throwable) {
            LaunchResult.Failed(
                null,
                throwable.message ?: throwable::class.simpleName ?: "Launch failed.",
            )
        }
    }

    internal fun buildIntent(player: Player, game: Game): Intent {
        val args = AmArgumentParser.parse(player.amStartArguments)
        val intent = Intent()

        if (args.packageName != null && args.className != null) {
            intent.component = ComponentName(args.packageName, args.className)
        }
        args.action?.let { intent.action = it }
        args.categories.forEach { intent.addCategory(it) }

        val resolvedData = args.data?.let { placeholderResolver.resolve(it, game) }
        when {
            resolvedData != null && args.mimeType != null ->
                intent.setDataAndType(resolvedData.toUri(), args.mimeType)
            resolvedData != null -> intent.data = resolvedData.toUri()
            args.mimeType != null -> intent.type = args.mimeType
        }

        args.extras.forEach { extra ->
            when (extra) {
                is AmExtra.StringValue ->
                    intent.putExtra(extra.key, placeholderResolver.resolve(extra.value, game))
                is AmExtra.BooleanValue -> intent.putExtra(extra.key, extra.value)
                is AmExtra.IntValue -> intent.putExtra(extra.key, extra.value)
                is AmExtra.LongValue -> intent.putExtra(extra.key, extra.value)
                is AmExtra.FloatValue -> intent.putExtra(extra.key, extra.value)
            }
        }

        intent.addFlags(args.flags)
        if (args.clearTask) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (args.clearTop) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // NEW_TASK is mandatory rather than optional: without it the emulator would be pushed onto
        // the shell's own task stack and could never be placed on a different display.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // FLAG_GRANT_* on the intent only covers Intent.data. Emulators that take the rom as a
        // string extra (NetherSX2 bootPath, etc.) need the same uris on ClipData, and every
        // content uri we can grant also needs an explicit package grant so the target UID can
        // openInputStream after startActivity returns.
        grantUriAccess(intent, args.packageName)

        return intent
    }

    private fun grantUriAccess(intent: Intent, targetPackage: String?) {
        val uris = linkedSetOf<Uri>()
        intent.data?.takeIf { it.scheme.equals("content", ignoreCase = true) }?.let(uris::add)
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.getString(key) ?: return@forEach
            if (value.startsWith("content://", ignoreCase = true)) {
                runCatching { value.toUri() }.getOrNull()?.let(uris::add)
            }
        }
        if (uris.isEmpty()) return

        // Refuse synthesized external-storage DocumentsContract URIs — FLAG_GRANT_READ_URI_PERMISSION
        // cannot authorize them and the emulator UID fails with a permission error on open.
        val grantable = uris.filterNot {
            ExternalStorageUris.isExternalStorageDocumentUri(it.toString())
        }
        if (grantable.isEmpty()) {
            throw MissingPlaceholderException(
                "{file.uri}",
                "This ROM only has an external-storage document URI that cannot be granted to " +
                    "another app. Re-scan with all-files access, or add the folder via the " +
                    "document picker.",
            )
        }

        val clip = ClipData.newRawUri("rom", grantable.first())
        grantable.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        targetPackage ?: return
        grantable.forEach { uri ->
            runCatching {
                context.grantUriPermission(
                    targetPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    /**
     * Builds the options bundle that targets a specific screen, or explains why it cannot.
     *
     * Every guard here is load-bearing. Without the feature check the platform silently ignores the
     * request and the game opens on the wrong screen; without
     * [ActivityManager.isActivityStartAllowedOnDisplay] the call throws [SecurityException] for
     * private displays or apps that are not resizeable.
     */
    private fun displayOptions(intent: Intent, targetDisplayId: Int?): Pair<Bundle?, String?> {
        if (targetDisplayId == null) return null to null

        if (!supportsSecondaryDisplays) {
            return null to "This device does not support launching apps on a secondary display."
        }

        val allowed = runCatching {
            activityManager.isActivityStartAllowedOnDisplay(context, targetDisplayId, intent)
        }.getOrDefault(false)

        if (!allowed) {
            return null to
                "Android refused to start this emulator on display $targetDisplayId, " +
                "so it opened on the main screen instead."
        }

        val options = runCatching {
            ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId).toBundle()
        }.getOrNull()

        return options to if (options == null) {
            "Could not target display $targetDisplayId."
        } else {
            null
        }
    }
}
