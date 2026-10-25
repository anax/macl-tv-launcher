# MACL TV Launcher Themes

This directory contains pre-configured visual themes tailored for Velana International Airport (MACL) lounges, living rooms, and office displays.

## Included Themes

| Theme | Description | Accent | Background |
|---|---|---|---|
| `macl-default` | Classic MACL Evening: Airport apron backdrop with frosted glass cards | `#00B2E3` (Turquoise) | `#0C184A` (Ocean Navy) |
| `macl-midnight` | OLED Midnight: Deep black background with high-contrast glowing neon cyan | `#00E5FF` (Neon Cyan) | `#040814` (Midnight) |
| `macl-lagoon` | Tropical Lagoon: Velana island turquoise waters with bright gold runway focus | `#FFC629` (Airport Gold) | `#022B3A` (Lagoon Blue) |
| `macl-sunrise` | Maldives Sunrise: Seaplane dawn horizon with coral peach glow | `#FF6F61` (Coral Dawn) | `#161233` (Twilight) |

## Applying a Theme to the TV

Use the included helper script:

```bash
# Push default theme
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-default

# Switch to OLED Midnight
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-midnight

# Switch to Tropical Lagoon
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-lagoon

# Switch to Maldives Sunrise
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-sunrise
```

## Theme Keys

Each theme contains a `config.json` file. Supported keys include:
- `macl_services`: `true` to display the interactive living room widget row (Live FIS flight board, prayer times countdown, RTL bus departures).
- `dock_title`: `false` for clean tvOS-style floating dock without overlapping header labels.
- `icon_only_buttons`: `true` to render top-bar pills (Inputs, Wi-Fi, Settings) as round icons.
- `dock_glass`: `true` for frosted glass effect on the bottom dock.
- `glow_style`: `"horizon"` or `"radial"`.
- `accent_color`: Hex color of the focus glow and active badges.
- `background_color`: Hex color of the main background canvas.
- `wallpaper`: `true` to use `wallpaper.jpg` inside the theme folder.
- `wallpaper_dim`: Dimming percentage (0-90) applied over the wallpaper.
