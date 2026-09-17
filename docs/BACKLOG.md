# XOrA backlog — captured 2026-09-16

Everything requested in this session, with the **final** value where a later message revised an
earlier one. Kept in the repo so the list survives the session.

## Done

- [x] All Friends: in-game before online (`friendPresenceRank`, already InGame 0 / Online 1)
- [x] Friend status-change notification carrying their wording (`emitXoraFriendStatusBanners`)
- [x] Trailer scraping defaults off, playback stays on
- [x] Custom status visible when Away / Busy (in-game still wins)
- [x] Friend achievement banner type + compact 50% trophy banners
- [x] Mini player follows a playing track out of Music; sound bites hold while music plays
- [x] Standby fade wired to game launch / return
- [x] Mini player **fades out entirely** behind the Vita tray (revised from blur)

## Blocked — needs the profile card system

`XoraProfileCard` / `XoraProfileBadge` / `XoraProfileCards` / `fetchProfileCard` /
`publishProfileCard` / `FriendProfileAction` are absent from this source (present in DNU-0.5.6).

- [ ] Friend card badge selection: dim others 75%, scale + tilt, info bar beneath
- [ ] Badge info modal identical to the RA Grid modal; card lifts **5px**; thinner bar; no Points pill
- [ ] Badge description beneath the title, title larger than description
- [ ] Smoother easing on the card lift / return
- [ ] Friend achievement notifications (banner exists, no emitter — data is friends' recent badges)

## Dash Notifications

Bottom-left, flush to the bottom edge, white text on a thin gradient bar. Slide in from left with
fade; slide left and fade out. **+10% size.** Duration 3–5s scaled by word count, max **+50% then
−10%**. Text past half screen width truncates with "...". Not shown during the boot video.

Rate limit: one alert per category per minute. Exceptions: playtime logged, and a new song
(never repeat the same song back to back). Playtime alert appears **first** on return from a game.

| Alert | Icon | Sound |
| --- | --- | --- |
| Song playing | `dash_music` | none |
| Scraping | `dash_download` | `window_chat.wav` |
| Update available | `dash_update` | `notif_chat.wav` |
| Hours logged | `dash_time` | `overlay_pause.wav` |
| File scanning | `dash_search_device` | `chat_send.wav` |
| Errors | `dash_power` | `error_popup.wav` |

All sounds muted while a song is playing. Also carries: Vita bubble moved/updated, media applied
to a game — both borrowing the scraping icon and sound. Playtime logs **minutes** when under an hour.

## Music

- [ ] Player controls inert outside Now Playing
- [ ] Hide mini player when game media is shown (currently conflicts with the Achievement Card)
- [ ] Paused song keeps its custom Background Media instead of reverting to cover art
- [ ] Background Media fades when paused **outside** the Music tab
- [ ] Background media fades on navigating to another song / leaving the folder
- [ ] Main music player raised so it clears the Dash Notification (mini player unchanged)
- [ ] Now Playing icon sized to match the Game Icon

## Library / editor

- [ ] Media Editor: Hide → Favorite / Unfavorite; third option "Hide" (XMB) or "Unpin" (Vita)
- [ ] Favorites folder in Games under All Games, `xmb_folder` icon, browsable as a Game Select
- [ ] Favorites icons sized as All Games icons
- [ ] Remove the More Options bar from the Media Editor
- [ ] Tap a thumbnail to pan it, with live preview; touch drag and Left Stick both pan
- [ ] Panning is per-thumbnail: bubble → shortcut icon, banner → XMB / Vita game icon,
      wallpaper → XMB / Vita wallpaper. Wallpapers and bubbles pannable where applicable
- [ ] Vita pin picker: platform list does not scroll — fix

## UI

- [ ] CRT DIM gains a blur beneath it for pop-ups / cards only — **not** the Music Player's CRT DIM
- [ ] Themes UI: icons 50% smaller, stacked list, name beside icon, 16:9 thumbnails,
      filling the boundary — not zoomed, not stretched
- [ ] Friend listening banner: "[user] is now listening to" over "**[song]** by [artist]",
      song bold, artist smaller and lighter
- [ ] Friend playing banner uses the game's bubble icon (`gameIconPath` already on the type)
- [ ] Notifications suppressed until the boot video finishes
- [ ] Charging: drop the "+" and turn the battery bars green
- [ ] Vita Launch Menu status bar reuses the Media Editor's status bar asset

## Media tabs

- [ ] Video tab organised by folder, like Music
- [ ] Photo viewer: remove the left info modal, centre the preview, 30px corners,
      library strip thinned to one row
- [ ] Advanced Settings → Media: choosable directories for Video and for Photos, multiple each

## Settings

- [x] Backup Data and Restore Data above About XOrA, as `ShellDataBackup` in `core:datastore`.
      One zip in Downloads holding the preference store, the database and the artwork /
      manual / avatar / theme-media directories — artwork, metadata, playtime, shortcuts and
      layout, as asked. ROMs, cores, BIOS and save data stay out: large, re-downloadable, or
      the emulator's business rather than the shell's.
      Restore replaces what it finds and needs a restart, because the preference store and the
      database are open and still holding the old contents; the banner says so.
      Entries are checked against their resolved path, so a hand-made zip cannot write outside
      the app's own storage.

## Chirper voices — new subsystem

Customize Profile popup gains a voice alongside icon and display name. "Select a Chirp" bubble
(`Sound.png`) opens a picker. Pills use a #000000→#222222 gradient at 75% opacity; glow runs
#FFFFFF → the icon's dominant colour; glass capsules with glossy edges. Icons are GIFs that play
once per press, never looping. Selecting dims the others 50%, colours the active one, and plays a
sample. The chosen voice plays on the user's own status updates, and for friends receiving those
updates — custom message, in-game activity, messages.

| Chirper | Icon | Sound |
| --- | --- | --- |
| usagishade | `CHIRPER.gif` | `usagi_[1-5].wav` |
| usagiishii | `CHIRPER ISHII.gif` | `ishii_1.wav` |
| tonic | `CHIRPER CTONIC.gif` | `tonic_[1-4].wav` |
| iprinceangel | `CHIRPER ANGEL.gif` | `angel_[1-4].wav` |
| somarix | `CHIRPER SOMARIX.gif` | `somarix_[1-3].wav` |
| sora | `CHIRPER SORA.gif` | `sora_1.wav` |
| makoto | `CHIRPER MAKOTO.gif` | `makoto_[1-3].wav` |
| furogii | `CHIRPER MANMAN.gif` | `furogii_[1-3].wav` |
| l y n | `CHIRPER LYN.gif` | `lyn_[1-3].wav` |
| marlix | `CHIRPER MARLIX.gif` | `marlix_[1-4].wav` |

Assets are in the repo now — icons at `feature/home/res/raw/chirper_<id>.gif`, takes at
`app/res/raw/chirp_<id>_<n>.wav`, `Sound.png` as `xmb_sound`. See `docs/design/README.md`.

Built: `ChirperVoice` + `ChirpPlayer` (`core:model`), `ChirpSoundPlayer` (`app`), the picker and
the `Select a Chirp` / `Test Audio` row in the profile editor, and the chirp on the local player's
own status change.

Still open:
- Friends' updates chirp in *their* voice: XOrA Network has no chirper field yet, so a friend's
  banner is silent where the request wants it to speak. Needs a protocol change, not UI work.
- In-game activity and messages do not chirp yet — only a custom status does.
- `CHIRPER REGINA.gif` has no sound set and `0_[1-3].wav` / `00_[1-2].wav` have no chirper. Both
  are parked verbatim in `docs/design/chirper-unassigned/` waiting on which is which.
- The picker's pills are built from `docs/design/reference/EditProfile-SelectaChirp.jpg`, which
  shows light glass — not the `#000000 → #222222` at 75% the written request describes. The
  design won.

## Notes

- Figma links in the requests are not reachable from this environment, so anything specified only
  by a design — Dash Notification chrome, the badge modal, the Chirper popup — is built from the
  written description and needs checking against the design.
- Revisions taken as final: scraping sound **kept**; file-scanning sound **kept**; badge lift
  **5px**; dash duration **+50% then −10%**; mini player **fades** behind the Vita tray.
