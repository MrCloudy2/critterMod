# Project assets

`icon.svg` is the source; `icon.png` is the 512x512 render used as the Modrinth and
GitHub icon. The capsule's upper shell is quartered into the four Safari biome
colours, using the same values the HUD does — Forest `#55FF55`, Cavern `#FFAA00`,
Icy `#55FFFF`, Haunted `#AA00AA`.

Re-render after editing the SVG:

```bash
rsvg-convert -w 512 -h 512 assets/icon.svg -o assets/icon.png
```
