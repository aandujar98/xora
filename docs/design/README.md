# Design reference

The 0.5.6 designs, kept in the repository so a lost session cannot take them with it. Every
Figma link in the feature requests resolves to one of the screens in `reference/` — the file
name is the Figma frame name with spaces removed. Figma itself is not reachable from CI or from
an agent session, so these are the source of truth for layout and colour.

## reference/

| File | What it fixes |
| --- | --- |
| `HOME-GAME.jpg` | XMB home over a game row: top-left friend cluster with unread count, top-right profile bubble, status bar (wifi / date / time / battery), game tile + title + `Recently Played` subtitle with the hairline rule. |
| `HOME-GAME-DashNotification.jpg` | Same frame, exported without the Dash layer — pixel-identical to `HOME-GAME.jpg`, so it says nothing about the Dash bar. Dash chrome is still built from prose. |
| `HOME-MUSIC-TRACK(nowplaying).jpg` | Now Playing: background media filling the screen, player card centred on the bottom — album art, title, artist, scrubber with elapsed / −remaining, transport row. |
| `HOME-MUSIC-ALBUMS.jpg` | Music tab album grid. |
| `MUSIC-EDIT.jpg` | The media editor: left column of actions (Edit Title / Hide / Remove), Album Art + Song Art + Wallpaper previews, then one row per slot with Scrape / Choose File / ✕. No "More options" bar. |
| `EditProfile.jpg` | Profile-bubble editor (`OpenProfile`), *not* the dashboard form: title in themed XMB caps over a hairline, big round chirper avatar, `Select a Chirp` speech bubble, `Test Audio`, `Username:` field, Cancel / Save. |
| `EditProfile-SelectaChirp.jpg` | Chirper picker, nothing chosen: light glass card over a dimmed, blurred shell; one pill per voice with the icon overlapping its left edge and a speaker at the right. |
| `EditProfile-SelectaChirp-UsagiShade.jpg` | The same picker with a voice chosen: the active pill takes a white → accent gradient, every other row dims. |
| `FriendCard-PROFILE.jpg` | Friend card at rest: avatar, status speech bubble, name in themed caps, `POINTS`, `RECENTLY EARNED` badge row, `FAVORITE GAME` with playtime, then `FRIENDS <n>` and three round actions. |
| `FriendCard-PROFILE-RABadge.jpg` | A badge selected: it lifts and keeps its gold rim while the rest dim, and the badge bar appears along the bottom. **Earlier revision** — the final bar is thinner and drops the points pill. |
| `FriendCard-PROFILE-CHAT.jpg` | Chat open: the card slides left, the thread opens on the right with name + game, bubbles, `Seen`, and the composer. |
| `HOME-SHORTCUT-PSO.jpg` | Vita LiveArea page: `◀ PRESS Ⓑ TO RETURN TO SHORTCUTS` in the status bar, and the collapsed status pill (trophies + friend avatars) in the panel's bottom-right. |
| `HOME-SHORTCUT-PSO-RA.jpg` | The same pill expanded: box art, title, platform chip, recent badges, `55/103` with a progress bar, and `RECENTLY PLAYED:` avatars with a `+3` overflow. |
| `RetroAchievements-GRID.jpg` / `-SELECT.jpg` | RetroAchievements grid, at rest and with a badge selected. |

## chirper-unassigned/

Shipped in `CHIRPER.zip` but not named by the feature request's table: `CHIRPER REGINA.gif` has no
sound set, and `0_[1-3].wav` / `00_[1-2].wav` have no chirper. Kept verbatim, unrenamed and out of
`res/`, rather than guessed into a mapping. They go into `ChirperVoice` once someone says which is
which.

## Where the mapped assets went

Icons are `feature/home/src/main/res/raw/chirper_<id>.gif`, resized 960² → 320² (they are
never drawn larger than a 220dp circle, and the originals cost 7.9 MB against 816 KB).
Sounds are `app/src/main/res/raw/chirp_<id>_<n>.wav`. The `usagishade` and `iprinceangel` takes
arrived as 32-bit float WAV, which `SoundPool` will not decode — all of them are normalised to
16-bit PCM with the metadata chunks stripped.
