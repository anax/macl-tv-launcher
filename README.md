# MACL TV Launcher

An ultra-premium, lightweight, tvOS-inspired Android TV launcher built specifically for **Maldives Airports Company Limited (MACL)** living rooms, executive lounges, and terminal information displays.

![MACL TV Launcher](themes/macl-default/screenshot.jpg)

## Features

- **Living Room Dashboard Widgets**:
  - **Live Flight Information Display (FIS)**: Direct live status updates for Velana International Airport (`MLE`) departures and arrivals (`fis.com.mv`), highlighting flight numbers, destination, status, and gate numbers.
  - **Live Prayer Times Countdown**: Next prayer countdown calculation for Malé / Hulhulé (UTC+5), displaying time remaining and next prayer name.
  - **Airport Bus Schedule**: Real-time departure countdowns for RTL Hulhumalé and RTL Malé Express routes.
- **tvOS Aesthetics & Motion**:
  - Frosted glass cards with smooth `1.05x` focus elevation animations and glowing turquoise focus rings (`#00B2E3`).
  - Floating bottom dock with frosted glass styling and squircle application tiles.
  - Top header with greeting, large clock, and clean round icon controls for Inputs, Wi-Fi, and Settings.
- **Included Add-ons ([addons/](addons/))**:
  - **`macl-services` (`com.macl.services`)**: Interactive Flight Information Display System (native board and web view), prayer times with adhan timings, and bus schedule explorer.
  - **`tv-agent` (`com.macl.tvagent`)**: AI TV voice & remote assistant for quick TV control and airport queries.
- **Integrated Theme System ([themes/](themes/))**:
  - Support for multiple airport color schemes (`macl-default`, `macl-midnight`, `macl-lagoon`, `macl-sunrise`).
  - Seamless theme pushing and switching on the fly over ADB.
- **Performance & Privacy**:
  - Pure Java with AndroidX DynamicAnimation and RecyclerView.
  - Ultra-lean footprint (< 480 KB release APK).
  - High performance on smart TV hardware (tested on Android TV 11 MediaTek boards).

---

## Repository Structure

```
macl-tv-launcher/
├── setup.sh              # 1-Click interactive setup & launcher replacement installer
├── restore.sh            # 1-Click stock launcher restore utility
├── app/                  # Main Android TV Launcher app (com.macl.tvlauncher)
├── addons/               # Companion TV Add-on apps
│   ├── macl-services/    # Flight board (FIS), Prayer times, and Bus schedules
│   └── tv-agent/         # AI voice & remote assistant
├── themes/               # Pre-configured visual themes
│   ├── macl-default/     # Classic MACL Evening with airport apron backdrop
│   ├── macl-midnight/    # Pure OLED deep black with neon cyan glow
│   ├── macl-lagoon/      # Tropical Velana lagoon teal and gold
│   └── macl-sunrise/     # Maldives dawn twilight and coral glow
└── scripts/              # Build, install, and theme deployment utilities
    ├── build.sh          # Compiles release APK
    ├── install.sh        # Deploys APK and configures TV home
    └── switch-theme.sh   # Pushes theme to the TV
```

---

## 🚀 One-Command TV Setup (Replace Any TV Launcher)

Any user can connect to their TV over ADB (Wi-Fi, Ethernet, or Tailscale) and completely replace the stock launcher with MACL Launcher and its companion services:

```bash
# Clone the repository
git clone https://github.com/anax/macl-tv-launcher.git
cd macl-tv-launcher

# Run the automated installer (replace with your TV IP or Tailscale IP)
TV_HOST=100.83.39.24 ./setup.sh
```

The installer will automatically:
1. Connect to the TV over ADB.
2. Detect the TV model and back up the current stock launcher.
3. Build and install **MACL TV Launcher** (`com.macl.tvlauncher`).
4. Build and install **MACL Services** (`com.macl.services`).
5. Build and install **TV Agent** (`com.macl.tvagent`).
6. Push the default frosted glass airport theme.
7. Set MACL Launcher as the default Home screen (with optional stock launcher debloat).
8. Launch the fresh living room experience immediately!

---

## 🎨 Switching Themes

Switch between themes on the fly without reinstalling:

```bash
# OLED Midnight (pure black with glowing neon cyan)
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-midnight

# Tropical Lagoon (Velana turquoise and runway gold)
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-lagoon

# Maldives Sunrise (coral dawn glow)
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-sunrise

# Classic Evening Apron
TV_HOST=100.83.39.24 ./scripts/switch-theme.sh themes/macl-default
```

---

## 🔄 Restoring Stock Launcher

If you ever wish to revert back to your TV's original stock launcher:

```bash
TV_HOST=100.83.39.24 ./restore.sh
```

---

## License
MIT License. Created for Maldives Airports Company Limited.
