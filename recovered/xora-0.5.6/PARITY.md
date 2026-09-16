# 0.5.6 parity — measured, not estimated

Method: extract every string literal from the APK's app-owned classes (the 1,198 classes
that reference `com.arcadia`), drop anything a non-app class also contains (inlined library
copy), keep what reads as XOrA-domain UI, and check each one against the repo's Kotlin —
allowing for `$interpolation` on our side.

## Headline

| | |
| --- | --- |
| 0.5.6 app-domain UI strings | **613** |
| Already present in repo source | **576 (93%)** |
| Genuinely missing | **37 (6%)**, ~10 of which are library noise |

**The source is not the reason the app looks wrong.**

## RetroAchievements: 83 of 84 strings present

Every RA UI string in 0.5.6 is in this repo except one (`Use your RetroAchievements
picture`, a profile-picture source row). Every RA entry point R8 kept is present too:
`selectAchievementsTab`, `selectRaCheevoIndex`, `toggleAchievementsPanel`, `setRaHardcore`,
`setRaUnlockNotifications`, `loginRetroAchievementsWithApiKey`, `signOutRetroAchievements`,
`clearRetroAchievementsCredentials`.

## Fonts: complete

| Font | In APK | In repo | Used by code |
| --- | --- | --- | --- |
| `xoireqe.ttf` | yes | yes | `XoraFonts.Title` — titles, menu names, primary labels |
| `fot_newrodin_pro_db.otf` | yes | yes | `XoraFonts.Secondary` / `XmbLabel` |
| `roboto_medium_numbers.ttf` | yes | yes (restored) | **nothing, in either build** |

The numbers font ships in 0.5.6 as resource `0x7f090001` and no code or XML in the APK
references it — it is an unused leftover there too. Restoring it gives byte-parity; it
changes nothing on screen. The two fonts that do matter are wired in `Type.kt` exactly as
0.5.6 had them.

`ae1` in the decompiled sources is `FontWeight`, not a font family — `ae1.r` is
FontWeight(700), `ae1.q` is FontWeight(600).

## What is actually missing (the real list)

Profile picture sources — `Use your Discord picture`, `Use your RetroAchievements picture`,
`Use your Steam picture`, `Add your Steam key and ID first`, `Add your Steam API key and
SteamID64 first.`, `Could not read your Steam profile picture.` (0.5.6 lists sources as rows
with per-source hints; we show chips. Steam has no `AvatarSource` here at all.)

Friend card — `Add Friend`, `Remove Friend`.

ROM / game editing — `Game Manual`, `Could not save the manual.`, `Drop a PDF next to the
ROM, or add ScreenScraper credentials in Settings to download one.`, `Each game shows its
own backgrounds.`, `Each game shows the images you added to it.`, `Each game cycles only the
images you added to it under Screenshots.`, `The backdrop is the one background of whichever
game you point at.`, `Stops the clip on the XMB. Also deletes a matching mp3 / wav sitting
beside the ROM.`, `Album Art`, `Unknown emulator`.

Music / media — `Library music is the song that is playing. XMB music is the theme loop
behind the menu.`, `On · scanlines over song backgrounds and friend cards`, `Open the
Spotify app on a phone, PC, or speaker first — XOrA starts playback there.`, `No videos in
that folder.`

Netplay — `A cycles Kaeru / Wiimmfi / AltWFC / Off · then open Nintendo Wi-Fi Connection in
the game`.

Discord — `Use Sign in with Discord above (redirect …`.

Settings — `XOrA is your home screen. Turning this off returns you to your previous
launcher.`

## The thing that actually needs fixing first

CI last went green on **1 September**, on the 0.2.x line. Every run since has failed, and
the 0.5.6 reconstruction has **never been compiled**. The releases after that date were
uploaded by hand or built on the Cowork PC that is now unreachable.

So whatever build is being compared against DNU-0.5.6 was not produced from this source.
That — not the 6% — is why screens look wrong. Getting a build out of CI is the only way to
see where the source actually stands, and the only way to verify any of this work.

## What cannot be checked this way

String and resource parity is measurable. Pixel parity is not: the APK is R8-minified, so
layout lives in obfuscated lambdas. Spacing, sizes and colours can be read case by case out
of the decompiled Compose calls, but there is no way to diff them wholesale, and no way at
all to confirm them without building and looking.
