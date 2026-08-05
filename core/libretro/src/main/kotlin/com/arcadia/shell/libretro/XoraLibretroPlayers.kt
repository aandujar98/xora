package com.arcadia.shell.libretro

import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.Player

/**
 * Built-in launch recipes that start [XoraLibretroActivity] inside `com.sora.shell`.
 *
 * [GameLauncher] detects these by [ID_PREFIX] and builds the activity Intent with ROM / core
 * extras (templates alone cannot express downloadable core paths).
 */
object XoraLibretroPlayers {

    const val PACKAGE = "com.sora.shell"
    const val ACTIVITY = "com.arcadia.shell.libretro.XoraLibretroActivity"
    const val ID_PREFIX = "xora.libretro."

    const val EXTRA_ROM_PATH = "ROM_PATH"
    const val EXTRA_CORE_NAME = "CORE_NAME"
    const val EXTRA_CORE_PATH = "CORE_PATH"
    const val EXTRA_PLATFORM_ID = "PLATFORM_ID"
    const val EXTRA_GAME_ID = "GAME_ID"
    const val EXTRA_GAME_TITLE = "GAME_TITLE"

    fun isXoraPlayer(player: Player): Boolean = player.uniqueId.startsWith(ID_PREFIX)

    fun isXoraPlayerId(playerId: String): Boolean = playerId.startsWith(ID_PREFIX)

    fun coreNameFromPlayer(player: Player): String? {
        val match = Regex("""-e\s+CORE_NAME\s+(\S+)""").find(player.amStartArguments)
        return match?.groupValues?.getOrNull(1)
            ?: player.uniqueId.removePrefix(ID_PREFIX).substringAfter('.', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
    }

    fun playerId(platformId: String, core: String): String =
        "$ID_PREFIX$platformId.${core.lowercase()}"

    /** Marker template so [Player.packageName] resolves to our app for install checks. */
    fun launchTemplate(core: String): String =
        "-n $PACKAGE/$ACTIVITY -e CORE_NAME $core -e ROM_PATH {file.path}"

    /** One default player per platform (first catalog entry). */
    fun primaryPlayers(catalog: XoraCoreCatalog): List<Player> {
        val seenPlatforms = linkedSetOf<String>()
        return catalog.all.mapNotNull { entry ->
            if (!seenPlatforms.add(entry.platformId)) return@mapNotNull null
            playerFor(entry.platformId, entry.core, entry.label)
        }
    }

    /** Every catalog core as a Choose Emulator option (primaries + alternates). */
    fun allPlayers(catalog: XoraCoreCatalog): List<Player> =
        catalog.all.mapNotNull { entry ->
            if (PlatformCatalog.byId(entry.platformId) == null) return@mapNotNull null
            playerFor(entry.platformId, entry.core, entry.label)
        }

    fun playerFor(platformId: String, core: String, label: String): Player {
        val shortName = PlatformCatalog.byId(platformId)?.shortName ?: platformId.uppercase()
        return Player(
            uniqueId = playerId(platformId, core),
            name = "XOrA Emulator ($shortName · $label)",
            amStartArguments = launchTemplate(core),
            acceptedFilenameRegex = extensionRegex(setOf(platformId)),
            killPackageProcesses = false,
            platformIds = setOf(platformId),
            builtIn = true,
        )
    }

    private fun extensionRegex(platformIds: Set<String>): String {
        val extensions = platformIds
            .mapNotNull { PlatformCatalog.byId(it) }
            .flatMap(GamePlatform::extensions)
            .distinct()
            .sorted()
        if (extensions.isEmpty()) return ""
        return "(?i)^.*\\.(${extensions.joinToString("|")})$"
    }
}
