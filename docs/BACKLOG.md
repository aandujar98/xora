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

## Badge inspection, and what the friend card still needs

`XoraProfileCard` / `XoraProfileBadge` / `XoraProfileCards` / `fetchProfileCard` /
`publishProfileCard` / `FriendProfileAction` are absent from this source (present in DNU-0.5.6),
so there is no *friend* card to select a badge on. The behaviour is built on the RT profile card
instead, which has the same Recently Earned strip and the same layout; the components move across
unchanged once the friend card lands.

- [x] Badge selection, on the profile card that exists: 5px lift, others to a quarter, both on
      one soft curve. The *friend* card still needs `XoraProfileCard` before it can have one.
- [x] Badge info bar: thinner, no points pill, light silver per the RA Grid design
- [x] Badge description beneath the title, title larger than description
- [x] Smoother easing on the lift and the return — `BadgeEasing`
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

- [x] Player controls inert outside Now Playing — the mini pill has no transport at all, and
      Up / Down / Confirm are transport keys only at `XoraXmbDepth.NowPlaying`
- [x] Hide mini player when game media is shown — `showsGameMedia`, and it yields the corner to
      the Achievement Card rather than stacking with it
- [x] Paused song keeps its custom Background Media — `MusicCategoryBackdrop` holds `hasTrack`
      through a pause, because dropping back to the cover made pausing look like an unload
- [x] Background Media fades when paused outside the Music tab — off the Music column the
      backdrop only fills screens a game is not already claiming
- [x] Background media fades on navigating away — same rule, driven by `gameMediaPresent`
- [x] Main music player raised clear of the Dash Notification, by the bar's own height plus a
      gap rather than a guessed constant. Mini player untouched.
- [ ] Now Playing icon sized to match the Game Icon — the rung already carries `artPath`; what
      is left is the XMB item renderer, which sizes art rungs differently from game rungs
- [ ] The player card is also narrower and lower than
      `docs/design/reference/HOME-MUSIC-TRACK(nowplaying).jpg` has it: measured 496/798/928x251
      against the built 583/830/753x234. Needs the inner column re-laid out, not just the box

## Library / editor

- [ ] Media Editor: Hide → Favorite / Unfavorite; third option "Hide" (XMB) or "Unpin" (Vita).
      Favourite and Hide both exist, but as rows in `romEditorRows`, not as the left-hand button
      column `docs/design/reference/MUSIC-EDIT.jpg` shows (Edit Title / Hide / Remove). Turning
      the row list into that column is a restructure of the whole editor, so it wants a look
      before it is built
- [x] Favorites folder in Games under All Games — `XoraXmbAction.DrillFavorites`, `FolderFavorites`
- [ ] Favorites icons sized as All Games icons
- [ ] Remove the More Options bar from the Media Editor
- [ ] Tap a thumbnail to pan it, with live preview; touch drag and Left Stick both pan
- [ ] Panning is per-thumbnail: bubble → shortcut icon, banner → XMB / Vita game icon,
      wallpaper → XMB / Vita wallpaper. Wallpapers and bubbles pannable where applicable
- [x] Vita pin picker scrolls — the platform column is a `LazyColumn` that follows the cursor;
      the plain `Column` it replaced left everything past the panel's height unreachable

## UI

- [x] CRT DIM blur for pop-ups only — `popupBlur` in `XoraHomeXmbPane`, maxed with the tray blur
- [x] Themes thumbnails fill their boundary — `ContentScale.FillBounds`.
      **Still open:** the stacked list layout with 50%-smaller icons and the name beside them
- [x] Friend listening banner — `BannerContent.subtitleEmphasis` carries the bold song title
- [x] Friend playing banner uses `gameIconPath ?: avatarUrl`
- [x] Notifications suppressed until the boot video finishes — `bootIntroHold.first { !it }`
- [x] Charging: green cells, no "+" — `batteryFillColor` and `BatteryGlyph`
- [x] Vita Launch Menu status bar — `VitaLiveAreaStatusBar`, plus the corner status panel from
      `docs/design/reference/HOME-SHORTCUT-PSO*.jpg`

## Media tabs

- [x] Video tab organised by folder — `videoFolderItems`, same shape as photos
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
