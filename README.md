# Project Tuzi

A landscape, controller-first Android emulation frontend (branded **SoraTuzi**). It organizes a ROM
library, scrapes artwork, and launches games — either through an embedded **Libretro host**
(`:core:libretro`, downloadable cores) or via external emulators you already have installed
(Dolphin, RetroArch, PPSSPP, …). On a dual-screen handheld the game grid and the artwork pane
spread across both physical displays; everywhere else they collapse into one landscape layout.

> **Install identity:** `applicationId` is `com.sora.shell` for both debug and release (no `.debug`
> suffix). Sideloads update in place over prior `com.sora.shell` builds (0.1.0–0.1.6 and current).
> A one-off Desktop `SORA-0.1.7.apk` was accidentally packaged as `com.sora.shell.debug` — uninstall
> that orphan if it is still present. This id is intentional versus former `com.tuzi.shell`; TUZI
> installs are not replaced.
>
> **Signing:** Debug and unsigned-release sideloads use the repo’s `debug.keystore` so every machine
> produces the same certificate. If you see *App not installed* / package conflict, uninstall the
> existing TuziSora/`com.sora.shell` once (old builds used a PC-local key), then install again. Optional
> production signing: copy `keystore.properties.example` → `keystore.properties` (gitignored).

## Requirements

