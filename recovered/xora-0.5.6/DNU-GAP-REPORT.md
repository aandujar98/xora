# DNU-0.5.6 reconstruction — gap report

Source of truth: `XOrA-0.5.6-release.apk` from the `DNU-0.5.6` release,
sha256 `e552e0685a89b521ace18b20aeaf5595ddbaf49408e294aa60fcd692a7aae959`.

## What the APK can and cannot give back

The release APK is **R8-minified**: 6,518 of its 7,193 classes are obfuscated into
`defpackage` with single-letter names. So:

- **Not recoverable as source:** the Compose UI. Screen code must be rebuilt by hand.
- **Fully recoverable:** every resource and asset (byte-exact), every string literal in
  the dex, and the classes R8 kept by keep-rules — the `kotlinx.serialization` models,
  Hilt factories, Room entities, and the `XoraXmbAction` sealed class.

Two kept artifacts carry most of the signal:

- `com/arcadia/shell/feature/home/f.java` — the **entire XMB menu definition**: every
  rung's id, title, subtitle, action and icon, unobfuscated.
- `com/arcadia/shell/feature/home/XoraXmbAction.java` — the full action surface, so every
  screen 0.5.6 could reach is enumerable.

## Assets: essentially complete

Comparing the decoded APK resources against the repo:

| Resource | Status |
| --- | --- |
| `drawable-nodpi` | All present (`ra_logo` differs only in format: `.webp` in APK, `.png` in repo) |
| `res/raw` | All present (APK has `ayp_youtube_player.html`; repo additionally has `friends_tab.wav`) |
| `assets/` (boot, themes, overlays, particles, platform_art, music) | All present |
| `res/font` | **`roboto_medium_numbers.ttf` was missing — restored from the APK in this commit** |

The visual gap is therefore **code, not art**.

## Action surface: one gap

Diffing `XoraXmbAction` (35 actions in 0.5.6) against `XoraXmb.kt`:

- **`OpenAllFriends` — missing.** Everything else matches.

0.5.6's XOrA Network column, verbatim from `f.java`:

| id | title | subtitle |
| --- | --- | --- |
| `dashboard` | Dashboard | Profile, friends & games on XOrA Network |
| `all_friends` | **All Friends** | **Everyone you're friends with, online or not** |
| `store` | XOrA Store | Coming soon |
| `news` | XOrA NOW | Gaming & emulation feed |

The repo had Dashboard / Store / News — the All Friends rung was absent. Fixed in this commit.

## Screens still to rebuild

Confirmed by string-literal diff against the repo (present in the APK, absent from source):

### Edit Profile — the status bar by the profile bubble (class `sw3`) — **REBUILT**
Was entirely missing; the repo carried a 0.3.45-era form (Display name / Username /
Location fields with Save and Cancel). Rebuilt to 0.5.6's layout: the avatar bubble with its
pencil overlay on the left, USERNAME and XORA NETWORK STATUS stacked beside it, and the
PROFILE PICTURE sheet underneath. Three focus sections replace the five form rows, and the
sheet and status menu take the stick while open. 0.5.6 copy, all now in place:
- Headings: `EDIT PROFILE`, `USERNAME:`, `XORA NETWORK STATUS:`, `PROFILE PICTURE`
- Status values: `Online`, `Away`, `Busy`, `Offline`
- Picture sources: `Upload a new picture`, `Use your Discord picture` (disabled hint
  `Link Discord first`), `Use your RetroAchievements picture` (`Sign in to
  RetroAchievements first`), `Use your Steam picture` (`Add your Steam key and ID first`),
  `Use a colour instead`
- Avatar source tabs: `Colour`, `Photo`, `XOrA`, `RA`, `Discord`
- Affordances: `✎` pencil glyph, `▼` disclosure glyph

### Friends card actions (class `nc5`)
`Add Friend` and `Remove Friend` are missing from the profile card. Present already:
`FAVORITE GAME`, `POINTS`, `RECENTLY EARNED`, `PICK FAVORITE GAME`.

### Friends list states (classes `q90`, `fz4`)
- `Loading friends…`
- `No friends yet. Invites you send and receive show up here and on the website.`
- `No friends yet — add some from the Friends Card.`
- `Friend request sent to …`, `Couldn't load friends right now.`,
  `Couldn't update that friendship.`, `Couldn't send that invite.`

### ROM editing
- `Edit Title`
- `Browse every scraper, or upload from the Files app (Y on the picker).`
- `Any local mp4 / webm / mkv. Overrides YouTube for this title.`
- `Cheat / texture / ROM hacks will land here`

## Method for the remaining work

For each screen: locate its obfuscated class by grepping the decompiled sources for a
known string literal, read the Compose call order in that class to recover layout
structure, then rebuild the composable using the exact copy and the existing resources.
