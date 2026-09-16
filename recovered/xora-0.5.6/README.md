# XOrA 0.5.6 source recovery

Claude Cowork built **0.5.6** (`versionCode` 443) from local git `876f311` on `cursor/vita-peel-music-bg-cca5`. That SHA was never pushed. The machine later reported `computer_unreachable`, with **25 unpushed commits** plus a dirty tree.

This branch is a reconstruction so the work is not lost on GitHub. It is **not** a byte-identical checkout of `876f311`.

## What is still only on the Cowork PC

Unpushed commits as of 2026-09-15 22:59 UTC (`git log -5`):

- `876f311` Draw the music player's transport with the ICONS-2 player bitmaps.
- `83adae7` Show the song's custom cover in both music players.
- `412d693` Bump to 0.5.0.
- `d9f0488` Fade the mini player, tint friends' names from their pictures, and quiet All Friends' cards.
- `b3a52bb` Bump to 0.4.5.

`876f311` was `0.5.0` (`versionCode` 437). The APK tagged `DNU-0.5.6` was built from the dirty tree on top of that commit.

Session: https://claude.ai/code/session_01R1osoDnjTkPJSiz96yjFDz

A `git push` to `cursor/preserve-0-5-6-cca5` is queued on that session. Opening Cowork on the machine that built 0.5.6 will finish the original 25-commit push.

## What this branch recovered

From the Cowork working-tree diff (`+1773 / -356` vs `0.5.0`) and Write/Edit tool payloads:

- New Dash notification sources and tests
- Patches that applied cleanly onto `0.3.45` (see `apply-results.json`)
- Edit() replays that still matched `0.3.45` context (48 of 86)
- Version label `0.5.6` / `443` so PackageManager treats this as that sideload line
- Per-file patches under `file-patches/` for hunks that need `0.5.0` context

APK (binary already on GitHub, not committed — 110MB):

- https://github.com/aandujar98/xora/releases/tag/DNU-0.5.6
- sha256 `e552e0685a89b521ace18b20aeaf5595ddbaf49408e294aa60fcd692a7aae959`
