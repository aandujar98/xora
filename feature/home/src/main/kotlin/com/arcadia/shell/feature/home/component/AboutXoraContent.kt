package com.arcadia.shell.feature.home.component

/** One open-source component credited in the About XOrA panel. */
internal data class OpenSourceCredit(val name: String, val license: String)

/**
 * Condensed from `THIRD_PARTY_NOTICES.md` at the repo root — the Libretro cores XOrA can download
 * at runtime, each under its own upstream license. Cores are fetched on demand per platform and
 * are not bundled inside the APK.
 */
internal val XORA_OPEN_SOURCE_CREDITS: List<OpenSourceCredit> = listOf(
    OpenSourceCredit("libretro.h (RetroArch team)", "MIT-style"),
    OpenSourceCredit("libmgba (GBA Game Link)", "MPL-2.0"),
    OpenSourceCredit("mesen / fceumm / nestopia (NES)", "GPLv2"),
    OpenSourceCredit("snes9x / bsnes (SNES)", "Non-commercial / GPLv3"),
    OpenSourceCredit("mupen64plus_next / parallel_n64 (N64)", "GPLv2"),
    OpenSourceCredit("gambatte (GB / GBC)", "GPLv2"),
    OpenSourceCredit("mgba (GBA)", "MPL-2.0"),
    OpenSourceCredit("melonds (NDS)", "GPLv3"),
    OpenSourceCredit("genesis_plus_gx (Genesis / SMS / GG / Sega CD)", "Non-commercial"),
    OpenSourceCredit("picodrive (32X / Genesis)", "MAME / GPLv2"),
    OpenSourceCredit("pcsx_rearmed / swanstation (PS1)", "GPLv2 / GPLv3"),
    OpenSourceCredit("ppsspp (PSP)", "GPLv2+"),
    OpenSourceCredit("fbneo (Arcade / Neo Geo)", "Non-commercial"),
    OpenSourceCredit("stella (Atari 2600)", "GPLv2"),
    OpenSourceCredit("handy (Atari Lynx)", "zlib"),
    OpenSourceCredit("mednafen_pce_fast (PC Engine)", "GPLv2"),
    OpenSourceCredit("mednafen_wswan / mednafen_ngp (WonderSwan / NGP)", "GPLv2"),
    OpenSourceCredit("bluemsx (MSX)", "GPLv2"),
    OpenSourceCredit("vice_x64 (C64)", "GPLv2"),
    OpenSourceCredit("puae (Amiga)", "GPLv2"),
    OpenSourceCredit("opera (3DO)", "GPLv3 (BIOS restrictions)"),
    OpenSourceCredit("dosbox_pure (DOS)", "GPLv2"),
    OpenSourceCredit("flycast (Dreamcast)", "GPLv2"),
    OpenSourceCredit("yabause (Saturn)", "GPLv2"),
)

/** Plain-language summary of what XOrA is, shown at the top of the About panel. */
internal const val ABOUT_XORA_SUMMARY = "XOrA turns your device into a game-console-style " +
    "launcher. It organizes your library, launches emulators and installed apps, and adds " +
    "social features like Discord and RetroAchievements — all from one XMB-style menu."

/** Ownership / content disclaimer, shown right under the summary. */
internal const val ABOUT_XORA_DISCLAIMER = "XOrA is not affiliated with, endorsed by, or " +
    "sponsored by Sony Interactive Entertainment or the PlayStation brand — the XMB-style menu " +
    "is just XOrA's visual theme. XOrA does not come packaged with any games, ROMs, or BIOS " +
    "files: you must supply your own, legally obtained."
