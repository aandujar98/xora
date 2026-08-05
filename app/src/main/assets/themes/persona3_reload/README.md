# Persona 3 Reload theme pack

Bundled assets (shipped with the app):

| File | Role |
|------|------|
| `wallpaper.mp4` | Full-bleed Home backdrop (looping, muted) |
| `wallpaper.png` | Still backdrop, kept as a manual alternative |
| `bgm.mp3` | Looping theme BGM (title screen theme) |

Paths expected by the shell:

```
app/src/main/assets/themes/persona3_reload/wallpaper.mp4
app/src/main/assets/themes/persona3_reload/bgm.mp3
```

The active path is `PERSONA3_WALLPAPER_ASSET` in `core/designsystem/.../ShellTheme.kt`. Point it at
`wallpaper.png` to use the still image instead; the shell picks video vs still from the extension.

## Notes

- Wallpaper and BGM are user-supplied assets packaged for this build; replace locally if you
  redistribute without those files.
- If `bgm.mp3` is missing at runtime, BGM falls back to the default shell soundtrack.
- If the wallpaper asset is missing, the navy/gold tartan-inspired geometric fallback is used.
- Video wallpapers loop silently, and pause while the shell is backgrounded.
- Rebuild/reinstall after changing assets so they are packaged into the APK.
