# Boot intro

Cold-start clip played once when XOrA opens after a full close.

| File | Role |
|------|------|
| `bootup.mp4` | Quality: 1080p 60 fps H.264 High + AAC, ends on white |
| `bootup_lite.mp4` | Performance / Auto-on-Performance: 720p 30 fps Constrained Baseline + AAC, ~630 kbps video |

`bootup.mp4` is encoded from the `xora-boot` GitHub release (`_.BOOTUP.mp4`). The lite encode is the same clip, scaled and re-encoded so RG Rotate / Galaxy A15-class decoders stay smooth. After the last white frame the shell fades to the XMB.
