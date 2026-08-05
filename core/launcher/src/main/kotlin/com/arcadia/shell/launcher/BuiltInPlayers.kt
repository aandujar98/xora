package com.arcadia.shell.launcher

import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.Player

/**
 * Launch recipes shipped with the app, seeded on first run and editable afterwards.
 *
 * These templates are the accumulated result of the community reverse-engineering each emulator's
 * intent surface; the argument names are dictated by the emulators themselves and cannot be
 * guessed or normalised.
 */
object BuiltInPlayers {

    /** Stable id for the N64 RetroArch + Mupen64Plus-Next profile. */
    const val RETROARCH_N64_PLAYER_ID = RetroArchPackages.N64_PLAYER_ID

    private const val RETROARCH_PACKAGE = RetroArchPackages.PACKAGE_AARCH64

    /**
     * Deliberately `by lazy` rather than a direct initializer. Properties in an object initialize in
     * declaration order, so building this list eagerly read [retroArchCores] before it was assigned
     * and threw from the class initializer. Deferring to first access makes the list independent of
     * where anything below it happens to be declared.
     */
    val all: List<Player> by lazy {
        buildList {
            addAll(standalonePlayers)
            addAll(retroArchPlayers)
        }
    }

    private val standalonePlayers: List<Player> get() = listOf(
        player(
            id = "dolphin.gamecube",
            name = "Dolphin (GameCube)",
            platformIds = setOf("gamecube"),
            template = "-n org.dolphinemu.dolphinemu/.ui.main.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-e AutoStartFile {file.path}",
        ),
        player(
            id = "dolphin.wii",
            name = "Dolphin (Wii)",
            platformIds = setOf("wii"),
            template = "-n org.dolphinemu.dolphinemu/.ui.main.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-e AutoStartFile {file.path}",
        ),
        // Current Cemu Android (lowercase package). Daijisho / frontend convention.
        player(
            id = "cemu.wiiu",
            name = "Cemu",
            platformIds = setOf("wiiu"),
            template = "-n info.cemu.cemu/.emulation.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // AYN Odin / side-by-side build: applicationId suffix, Kotlin package unchanged.
        player(
            id = "cemu.wiiu.odin",
            name = "Cemu (Odin)",
            platformIds = setOf("wiiu"),
            template = "-n info.cemu.cemu.odin/info.cemu.cemu.emulation.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Pre-0.1 Cemu Android used a capital-C applicationId.
        player(
            id = "cemu.wiiu.legacy",
            name = "Cemu (legacy)",
            platformIds = setOf("wiiu"),
            template = "-n info.cemu.Cemu/.emulation.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        player(
            id = "duckstation.ps1",
            name = "DuckStation",
            platformIds = setOf("ps1"),
            template = "-n com.github.stenzek.duckstation/.EmulationActivity " +
                "-a android.intent.action.MAIN " +
                "-e bootPath {file.path} " +
                "--ez resumeState false",
        ),
        player(
            id = "ppsspp.psp",
            name = "PPSSPP",
            platformIds = setOf("psp"),
            // Grantable content URI (SAF document or FileProvider) — see PlaceholderResolver.
            template = "-n org.ppsspp.ppsspp/.PpssppActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri}",
        ),
        // NetherSX2 (and original AetherSX2) share xyz.aethersx2.android. bootPath must be a
        // grantable content URI — FileProvider when indexed by path, SAF document otherwise.
        player(
            id = "nethersx2.ps2",
            name = "NetherSX2 / AetherSX2",
            platformIds = setOf("ps2"),
            template = "-n xyz.aethersx2.android/.EmulationActivity " +
                "-a android.intent.action.MAIN " +
                "-e bootPath {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Keep the historical id so existing per-game overrides still resolve after upsert.
        player(
            id = "aethersx2.ps2",
            name = "AetherSX2 (legacy id)",
            platformIds = setOf("ps2"),
            template = "-n xyz.aethersx2.android/.EmulationActivity " +
                "-a android.intent.action.MAIN " +
                "-e bootPath {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        player(
            id = "melonds.nds",
            name = "melonDS",
            platformIds = setOf("nds"),
            template = "-n me.magnum.melonds/.ui.romlist.RomListActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri}",
        ),
        player(
            id = "drastic.nds",
            name = "DraStic",
            platformIds = setOf("nds"),
            template = "-n com.dsemu.drastic/.DraSticActivity " +
                "-a android.intent.action.VIEW " +
                "-e GAMEPATH {file.path}",
        ),
        // Azahar sideload / Obtainium (org.azahar_emu.azahar).
        player(
            id = "azahar.vanilla",
            name = "Azahar (Vanilla)",
            platformIds = setOf("3ds"),
            template = "-n org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Azahar on Google Play keeps the historical Lime3DS applicationId.
        player(
            id = "azahar.play",
            name = "Azahar (Play Store)",
            platformIds = setOf("3ds"),
            template = "-n io.github.lime3ds.android/org.citra.citra_emu.activities.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Legacy Citra (and some "Citrus" rebrands that kept the original package id).
        player(
            id = "citra.3ds",
            name = "Citra / Citrus",
            platformIds = setOf("3ds"),
            template = "-n org.citra.citra_emu/.ui.main.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-e GamePath {file.path}",
        ),
        player(
            id = "citra.mmj",
            name = "Citra MMJ",
            platformIds = setOf("3ds"),
            template = "-n org.citra.emu/.ui.main.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-e GamePath {file.path}",
        ),
        // Current Play Store / recent APKs use com.flycast.emulator.MainActivity.
        player(
            id = "flycast.dreamcast",
            name = "Flycast",
            platformIds = setOf("dreamcast"),
            template = "-n com.flycast.emulator/com.flycast.emulator.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Older Flycast builds still register the reicast activity class name.
        player(
            id = "flycast.dreamcast.legacy",
            name = "Flycast (legacy activity)",
            platformIds = setOf("dreamcast"),
            template = "-n com.flycast.emulator/com.reicast.emulator.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        player(
            id = "redream.dreamcast",
            name = "Redream",
            platformIds = setOf("dreamcast"),
            template = "-n io.recompiled.redream/.MainActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri}",
        ),
        player(
            id = "yabasanshiro.saturn",
            name = "Yaba Sanshiro 2",
            platformIds = setOf("saturn"),
            template = "-n org.devmiyax.yabasanshioro2/org.uoyabause.android.Yabause " +
                "-a android.intent.action.VIEW " +
                "-e org.uoyabause.android.FileNameEx {file.path}",
        ),
        player(
            id = "vita3k.psvita",
            name = "Vita3K",
            platformIds = setOf("psvita"),
            template = "-n org.vita3k.emulator/.Emulator " +
                "-a android.intent.action.VIEW " +
                "-e AmStartPath {file.path}",
        ),
        // Eden (yuzu lineage). Mainline / Play / sideload applicationId.
        player(
            id = "eden.switch",
            name = "Eden",
            platformIds = setOf("switch"),
            template = "-n dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Eden Legacy product flavor (older GPU / OS targets).
        player(
            id = "eden.switch.legacy",
            name = "Eden Legacy",
            platformIds = setOf("switch"),
            template = "-n dev.legacy.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        // Eden Nightly builds ship with an applicationId suffix.
        player(
            id = "eden.switch.nightly",
            name = "Eden Nightly",
            platformIds = setOf("switch"),
            template = "-n dev.eden.eden_emulator.nightly/org.yuzu.yuzu_emu.activities.EmulationActivity " +
                "-a android.intent.action.VIEW " +
                "-d {file.uri} " +
                "--activity-clear-task --activity-clear-top",
            killPackageProcesses = true,
        ),
        player(
            id = "aps3e.ps3",
            name = "aPS3e",
            platformIds = setOf("ps3"),
            template = "-n aenu.aps3e/.Emulator " +
                "-a android.intent.action.VIEW " +
                "-e path {file.path}",
        ),
        player(
            id = "mupen64.n64",
            name = "Mupen64Plus FZ",
            platformIds = setOf("n64"),
            template = "-n org.mupen64plusae.v3.fzurita/paulscode.android.mupen64plusae." +
                "SplashActivity " +
                "-a android.intent.action.VIEW " +
                "-e ROM {file.path}",
        ),
    )

    /**
     * RetroArch needs an explicit core per profile; the core path is absolute inside its own
     * private data directory. Alternate cores for the same system (e.g. N64 ParaLLEl) are
     * separate players so Choose Emulator / Settings can pick them by id.
     */
    private val retroArchPlayers: List<Player> get() = RetroArchCoreCatalog.all.map { option ->
        val platform = PlatformCatalog.requireById(option.platformId)
        player(
            id = option.playerId,
            name = "RetroArch (${platform.shortName} · ${option.label})",
            platformIds = setOf(option.platformId),
            template = RetroArchPackages.launchTemplate(RETROARCH_PACKAGE, option.core),
            killPackageProcesses = true,
        )
    }

    /**
     * Launch recipe for a core found on disk that has no catalog entry, so a core downloaded
     * after this build shipped is still launchable once the user picks it.
     */
    fun retroArchCorePlayer(
        playerId: String,
        platformId: String,
        core: String,
        packageName: String,
    ): Player {
        val shortName = PlatformCatalog.byId(platformId)?.shortName ?: platformId.uppercase()
        return player(
            id = playerId,
            name = "RetroArch ($shortName · ${RetroArchCoreCatalog.labelForCore(core)})",
            platformIds = setOf(platformId),
            template = RetroArchPackages.launchTemplate(packageName, core),
            killPackageProcesses = true,
        )
    }

    private fun player(
        id: String,
        name: String,
        platformIds: Set<String>,
        template: String,
        killPackageProcesses: Boolean = false,
    ) = Player(
        uniqueId = id,
        name = name,
        amStartArguments = template,
        acceptedFilenameRegex = extensionRegex(platformIds),
        killPackageProcesses = killPackageProcesses,
        platformIds = platformIds,
        builtIn = true,
    )

    /** Builds a case-insensitive extension whitelist from the platforms a player serves. */
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
