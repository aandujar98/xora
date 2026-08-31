# Default theme pack

Bundled assets (shipped with the app):

| File | Role |
|------|------|
| `wallpaper.mp4` | Full-bleed Home / Vita-tray loop (`bg_wave_loop` / `_.LOOP.mp4`, 60 fps) |
| `bgm.mp3` | Looping home menu BGM |

Paths expected by the shell:

```
app/src/main/assets/themes/default/wallpaper.mp4
app/src/main/assets/themes/default/bgm.mp3
```

If `bgm.mp3` is missing at runtime, BGM falls back to `raw/background`.
If the wallpaper asset is missing, the flowing-wave backdrop is used.

`wallpaper.mp4` is the `_.LOOP.mp4` clip from the `bg_wave_loop` GitHub release, remuxed as
muted H.264 1080p60 for ExoPlayer. `bgm.mp3` is encoded from the `xora-bgm` release
(`XOrA.Menu.Theme.wav`, 14:48 loop).
