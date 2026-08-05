# XOrA

A landscape, controller-first Android emulation frontend (branded **XOrA**). It organizes a ROM
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
> existing XOrA/`com.sora.shell` once (old builds used a PC-local key), then install again. Optional
> production signing: copy `keystore.properties.example` → `keystore.properties` (gitignored).

## Requirements

- JDK 17 or newer (the build is verified against Microsoft OpenJDK 21)
- Android SDK with platform 37 and build tools installed
- A device or emulator running Android 10 (API 29) or newer

Set `JAVA_HOME` and either `ANDROID_HOME` or `local.properties` (`sdk.dir=...`) before building.

## Building

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew test                        # unit tests for the pure logic modules
./gradlew installDebug                # install onto a connected device
```

On Windows use `gradlew.bat`.

## First run

1. Grant **all-files access** in Setup. This is load-bearing rather than a convenience: Dolphin and
   DuckStation accept only a real filesystem path in their launch intents, which the Storage Access
   Framework cannot produce. Folders picked through the document picker still work, but only for the
   emulators that take a `content://` URI.
2. Add one or more library folders, then run a scan. Platforms are detected from file extensions and
   from folder names, so a `roms/snes` layout is understood without any configuration.
3. Assign an emulator per system under **Emulators**. Profiles for RetroArch, Dolphin, DuckStation,
   PPSSPP, AetherSX2 and others are seeded on first launch, and the list is filtered to what is
   actually installed.
4. Optionally add scraper credentials under **Artwork**. Every source is independent, so a
   SteamGridDB key alone is enough to get artwork for most of a library.

## Controls

| Input | Action |
| --- | --- |
| D-pad / left stick | Move the selection |
| A | Launch |
| X | Game options |
| Y | Toggle favourite |
| L1 / R1 | Page through the grid |
| L2 / R2 | Previous / next system |
| Start | Setup |
| Select | Swap which screen shows which pane |

Touch works throughout, but the layout is built for a controller.

## Module layout

| Module | Responsibility |
| --- | --- |
| `:app` | Activity, navigation, DI wiring, the opt-in HOME alias |
| `:core:model` | Platform catalog, domain types, filename cleaning |
| `:core:database` | Room entities, DAOs, repositories |
| `:core:datastore` | Preferences and scraper credentials |
| `:core:designsystem` | Theme and shared Compose building blocks |
| `:core:scanner` | Library traversal for both filesystem and SAF roots |
| `:core:launcher` | Player profiles, `am` argument parsing, intent construction |
| `:core:libretro` | Embedded Libretro host, core downloader, in-process play |
| `:core:display` | Display topology and the `Presentation` host |
| `:core:input` | Gamepad event pipeline and navigation actions |
| `:core:scraper` | ROM hashing, artwork sources, WorkManager job, media cache |
| `:feature:home` | Library UI, hero and grid panes |
| `:feature:settings` | Setup, library folders, emulator assignment |

## Notes on the design

External emulator support remains data-driven `am start` templates. Additionally, XOrA can run
compatible systems in-process via Libretro (cores downloaded at runtime; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and Setup → **XOrA Emulator**).

External launch recipes are `am start` templates stored in the
database, so adding a new emulator means editing a field rather than shipping a release, and
configurations authored for Daijishō can be imported unchanged.

Grid navigation uses an explicit index model rather than Compose focus traversal, which resolves the
next focusable geometrically and lazily and so cannot keep up with a held d-pad across a grid whose
rows have not been composed yet.

Taking over the home screen is a two-step opt-in behind a disabled manifest alias. Enabling it only
adds SORA to the launcher chooser; the user still picks it in system settings.

## Discord Rich Presence (optional)

Live Rich Presence uses Discord’s Social SDK partner AAR (not on Maven Central). Without it, SORA
still builds and keeps a status-bridge fallback.

1. Download the Social SDK zip from the Developer Portal → **Discord Social SDK** → **Downloads**.
2. Copy `discord_social_sdk/lib/release/discord_partner_sdk.aar` to
   `core/launcher/libs/discord_partner_sdk.aar` (see that folder’s README).
3. In the portal OAuth2 tab, register
   `discord-1531690290526683176:/authorize/callback` and enable **Public Client**.
4. Rebuild. Settings → Social should show link-account (not “SDK missing”); then link from
   Social → Discord.

## Testing dual-screen behaviour

The developer option **Simulate secondary displays** exercises the topology, role-swapping, and
`Presentation` paths without hardware. `setLaunchDisplayId` against third-party emulators ultimately
needs a real dual-screen device such as an AYANEO Pocket DS or Anbernic RG DS, since whether a start
is permitted depends on the target app's own `resizeableActivity` declaration.
