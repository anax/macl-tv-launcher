# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Gemini CLI, Cursor, Copilot, Antigravity) working in this repository.

## First: Which job are you doing?

| You were asked to | Do this |
|---|---|
| Clean up / set up a TV: install launcher & addons | Read [PROMPT.md](PROMPT.md) and follow it. Use `./setup.sh` or `scripts/install.sh`. |
| Modify this repository: launcher, widgets, themes, addons | Read on from [Repo Map](#repo-map). |

---

### Non-Negotiable Safety Rules

- **Disable, never uninstall**: `pm disable-user --user 0`, one package at a time. No `pm uninstall`, no root, nothing flashed. Every step must be reversible via `./restore.sh`.
- **Back up first**: Always dump installed package list, disabled list, `getprop`, and original Home resolver into `backup/<timestamp>/` before any change.
- **Leave essential packages alone**: Input services behind HDMI/tuner, source menu, Bluetooth remote pairing, on-screen keyboard, Play Store, and Play Services.
- **Never disable core system packages**: `com.android.*`, `com.google.android.*`, `com.android.systemui`, `com.android.settings`, `com.android.tv.settings`, `android`.
- **The Home Guard**: The stock launcher is disabled **only after** `com.macl.tvlauncher` is installed, verified via `am start -W` (returning `Status: ok`), and set as default Home.
- **Close ADB afterwards**: USB and Network debugging off, authorisations revoked, port 5555 confirmed closed.

---

## Repo Map

| Path | What it is |
|---|---|
| `app/` | The core reference launcher (`com.macl.tvlauncher`): pure Java, AndroidX RecyclerView, DynamicAnimation, `minSdk` 26, zero network permissions. |
| `addons/` | Companion modular add-ons: `macl-services` (FIS, Prayer Times, Bus) and `tv-agent` (voice assistant). |
| `themes/` | Visual theme folders (`macl-default`, `macl-midnight`, `macl-lagoon`, `macl-sunrise`), each with `config.json`, `wallpaper.jpg`, and `screenshot.jpg`. |
| `scripts/` | Tooling: `build.sh`, `install.sh` (safety-guarded installer), `switch-theme.sh`. |
| `setup.sh` | 1-Click interactive TV deployment script. |
| `restore.sh` | 1-Click stock launcher restore utility. |
| `PROMPT.md` | Copy-paste instructions for AI coding agents. |
| `CONTRIBUTING.md` | Contributor guidelines for themes, add-ons, and TV support. |
| `SECURITY.md` | Security and network policy. |

---

## Launcher Architecture & Rules

1. **Zero Network in Core Launcher**:
   - `app/` has **no `INTERNET` permission**. All flight data and airport bus schedules are rendered statically or fed via companion add-ons (`addons/macl-services`).
   - This keeps the launcher light and stable on low-RAM TV hardware (1 GB RAM).

2. **tvOS Living Room Focus System**:
   - Focused elements scale smoothly by `1.05x` with 150ms animation duration.
   - Active focus ring uses MACL turquoise `#00B2E3` or theme `accent_color`.

3. **Hard Size Gate**:
   - Release APK (`app/build/outputs/apk/release/app-release.apk`) must remain **under 491,520 bytes (480 KB)**.
   - Run `stat -f %z app/build/outputs/apk/release/app-release.apk` after building to verify.

4. **No Binary Artifacts**:
   - Never commit `.apk`, `.aab`, or `.class` files into git. Builds are compiled on demand.
