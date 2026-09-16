# What 0.4.1 → 0.5.6 actually added

There is no `v0.4.1` … `v0.5.5`. The only released builds bracketing the range are
`v0.4.0` (versionCode **431**, sha256 `9f8e7e8d…`) and `DNU-0.5.6` (versionCode **443**).
Twelve builds apart, so the diff between those two APKs *is* the range.

Method: diff the two dex string pools and the two decoded resource trees. Raw string diff is
~39k added / ~38k removed, almost entirely R8 renaming between builds; filtered to UI copy it
comes down to **12 strings**.

## The 12 strings added in the range

| String | In repo? |
| --- | --- |
| `Game Manual` | **missing** |
| `Manual found.` | **missing** |
| `Manual updated.` | **missing** |
| `Manual removed.` | **missing** |
| `Could not import that manual.` | **missing** |
| `Could not save the manual.` | **missing** |
| `Add Friend` | **missing** |
| `Remove Friend` | **missing** |
| `No videos in that folder.` | **missing** |
| `Artwork updated` | present |
| `Photo folders` | present |
| `Video folders` | present |

They cluster into three features:

1. **Game manuals** — a PDF manual per ROM, with import / update / remove and the hint
   `Drop a PDF next to the ROM, or add ScreenScraper credentials in Settings to download one.`
   Nothing of this exists in the repo.
2. **Friend add / remove** — 0.5.6's friend card carries a three-action row: Add/Remove
   Friend, Favorite/Unfavorite, Chat (decompiled class `nc5`, enum of three entries; the
   first is disabled unless the account can act). The repo has no friend-card action row at
   all — `"Chat"` appears nowhere in the source.
3. **Video folders** — the empty state `No videos in that folder.`

## Resources added in the range — all 20 already in the repo

Drawables: `dash_download`, `dash_music`, `dash_power`, `dash_search_device`, `dash_time`,
`dash_update`, `player_next`, `player_pause`, `player_play`, `player_prev`, `player_repeat`,
`player_shuf`, `xmb_folder`, `xmb_netplay`, `xora_time`.

Sounds: `chat_send.wav`, `notif_chat.wav`, `omo_welcome.wav`, `overlay_pause.wav`,
`window_chat.wav`.

(0.5.6 also *dropped* two theme previews, `theme_preview_dreamos.webp` and
`theme_preview_persona3_reload.webp`, which the repo still carries.)

So the art and audio for this range are all present — only the code for the three features
above is missing.

## Two problems with the CI build itself

**Discord is stripped.** The workflow moves `discord_partner_sdk.aar` aside before building,
to dodge an AGP restriction on local AARs in library modules. The cost is real: the built APK
is 84MB against DNU-0.5.6's 115MB, and the whole 31MB gap is native Discord code —
`libdiscord_partner_sdk.so` (8.6MB per ABI) and `libsora_discord.so` — absent from all four
ABIs. Discord cannot work in a CI build. `libxora_libretro.so` builds correctly and matches
DNU byte-for-byte in size, so the emulator core itself is fine.

**The release tag is wrong.** `Compute next version tag` increments from the newest `v0.2.x`
tag, so a build of versionName 0.5.6 / versionCode 445 published as `v0.2.346`.
