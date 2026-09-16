# Third-party notices — XOrA Emulator (Libretro)

XOrA embeds a **Libretro host** (`:core:libretro`) and can download **Libretro cores** at runtime
from the public Libretro Android nightly buildbot. Most cores are **not** shipped inside the APK.

For GBA Game Link, XOrA also embeds **libmgba** (Mozilla Public License 2.0) and runs two GBA
cores in-process on mGBA's SIO lockstep coordinator — the same arrangement as desktop mGBA's
"New multiplayer window". Matching libretro mGBA cores on two phones is not a cable.

XOrA does **not** include ROMs, BIOS images, or other copyrighted game content. You must supply
only files you have the right to use.

## Libretro API

- **libretro.h** — Copyright (C) 2010–2024 The RetroArch team  
  MIT-style permission notice (see header in `core/libretro/src/main/cpp/libretro.h`).

## Embedded libmgba (GBA Game Link)

- **libmgba** — Copyright (c) 2013–2026 Jeffrey Pfau  
  Mozilla Public License 2.0. Source: [mgba-emu/mgba](https://github.com/mgba-emu/mgba).  
  Fetched at build time into `core/libretro/src/main/cpp/third_party/mgba/` (not shipped as a separate `.so`; linked into `libxora_libretro.so`).

## Downloaded cores (examples)

Each core is subject to **its own license**. The in-app core list shows a short license label;
consult the upstream project for the full text before redistribution.

| Core (base name) | Typical systems | License (summary) |
| --- | --- | --- |
| mesen / fceumm / nestopia | NES | GPLv2 |
| snes9x / bsnes | SNES | Non-commercial / GPLv3 (bsnes) |
| mupen64plus_next / parallel_n64 | N64 | GPLv2 |
| gambatte | GB / GBC | GPLv2 |
| mgba | GBA | MPL-2.0 |
| melonds | NDS | GPLv3 |
| genesis_plus_gx | Genesis / SMS / GG / Sega CD | Non-commercial |
| picodrive | 32X / Genesis | MAME / GPLv2 |
| pcsx_rearmed / swanstation | PS1 | GPLv2 / GPLv3 |
| ppsspp | PSP | GPLv2+ |
| fbneo | Arcade / Neo Geo | Non-commercial |
| stella | Atari 2600 | GPLv2 |
| handy | Atari Lynx | zlib |
| mednafen_pce_fast | PC Engine | GPLv2 |
| mednafen_wswan / mednafen_ngp | WonderSwan / NGP | GPLv2 |
| bluemsx | MSX | GPLv2 |
| vice_x64 | C64 | GPLv2 |
| puae | Amiga | GPLv2 |
| opera | 3DO | GPLv3 (BIOS restrictions) |
| dosbox_pure | DOS | GPLv2 |
| flycast | Dreamcast | GPLv2 |
| yabause | Saturn | GPLv2 |

Upstream sources: [libretro organization](https://github.com/libretro),
[Libretro buildbot](https://buildbot.libretro.com/).

## BIOS / system files

Some cores require user-provided BIOS or firmware. Place files in the XOrA **system** directory:

```
Android/data/com.sora.shell/files/system/
```

(or the app-private `files/system` path shown by your file manager for `com.sora.shell`).

Common examples (filenames vary by core; check that core’s docs):

| System | Notes |
| --- | --- |
| PlayStation (PS1) | SCPHxxxx.BIN (region-specific) for many PS1 cores |
| Sega CD | `bios_CD_U.bin` / `bios_CD_E.bin` / `bios_CD_J.bin` (Genesis Plus GX) |
| Amiga | Kickstart ROMs for PUAE |
| 3DO | `panafz10.bin` (Opera) — often non-redistributable |
| NDS | Firmware optional depending on melonDS settings |

**Do not** redistribute BIOS dumps with XOrA builds.

## Heavy systems (external emulators)

GameCube / Wii, Wii U, Switch, PS2, PS3, and Vita remain **external apps** (Dolphin, Cemu, Eden,
AetherSX2, etc.). XOrA continues to launch those via intent recipes; they are not Libretro hosts
inside this process.

## Save states

XOrA Libretro save states are stored under:

```
Android/data/com.sora.shell/files/saves/<platformId>/
```
