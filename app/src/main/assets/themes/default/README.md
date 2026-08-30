# Default theme pack

Bundled assets (shipped with the app):

| File | Role |
|------|------|
| `wallpaper.mp4` | Full-bleed Home / Vita-tray loop (0.3x) |
| `bgm.mp3` | Looping home menu BGM |

Paths expected by the shell:

```
app/src/main/assets/themes/default/wallpaper.mp4
app/src/main/assets/themes/default/bgm.mp3
```

If `bgm.mp3` is missing at runtime, BGM falls back to `raw/background`.
If the wallpaper asset is missing, the flowing-wave backdrop is used.

`bgm.mp3` is encoded from the `xora-bgm` release (`XOrA.Menu.Theme.wav`, 14:48 loop).
