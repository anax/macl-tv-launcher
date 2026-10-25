#!/usr/bin/env bash
# ==============================================================================
# MACL TV Launcher — Automated TV Setup & Replacement Script
# Connects to any Android TV / Google TV over ADB, installs the launcher & addons,
# configures themes, and sets MACL Launcher as the default home screen.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "================================================================="
echo "       🇲🇻 MACL TV Launcher — Living Room Setup Installer         "
echo "================================================================="
echo ""

# 1. Determine TV IP / Host
TV_HOST="${1:-${TV_HOST:-}}"
if [ -z "$TV_HOST" ]; then
    echo "Please ensure USB/Network Debugging (ADB) is enabled on your TV."
    read -r -p "Enter your TV IP address (e.g. 192.168.1.50 or Tailscale IP): " TV_HOST
fi

if [ -z "$TV_HOST" ]; then
    echo "✗ Error: TV IP address is required." >&2
    exit 1
fi

# 2. Locate ADB
ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    else
        echo "✗ Error: adb not found. Please install Android platform-tools." >&2
        exit 1
    fi
fi

# 3. Locate Java & Android SDK
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "/opt/homebrew/Cellar/openjdk@17" ]; then
        export JAVA_HOME="$(ls -d /opt/homebrew/Cellar/openjdk@17/*/libexec/openjdk.jdk/Contents/Home 2>/dev/null | head -n 1)"
    fi
fi
if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    fi
fi

# 4. Connect to TV
echo ""
echo "Connecting to TV at $TV_HOST:5555..."
"$ADB" connect "$TV_HOST:5555" || true
sleep 1

# Check ADB state
ADB_TARGET="$TV_HOST:5555"
if ! "$ADB" -s "$ADB_TARGET" get-state >/dev/null 2>&1; then
    echo "✗ Unable to connect to $ADB_TARGET. Please verify IP and accept the ADB debugging prompt on your TV screen." >&2
    exit 1
fi

MODEL=$("$ADB" -s "$ADB_TARGET" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
BRAND=$("$ADB" -s "$ADB_TARGET" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')
ANDROID_VER=$("$ADB" -s "$ADB_TARGET" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo "✓ Connected to: $BRAND $MODEL (Android $ANDROID_VER)"

# 5. Detect and backup current stock launcher
STOCK_LAUNCHER=$("$ADB" -s "$ADB_TARGET" shell "cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN | grep packageName=" 2>/dev/null | sed -e 's/.*packageName=//' | tr -d '\r' || true)
if [ -n "$STOCK_LAUNCHER" ]; then
    echo "✓ Detected current home package: $STOCK_LAUNCHER"
    mkdir -p "$SCRIPT_DIR/backup"
    echo "$STOCK_LAUNCHER" > "$SCRIPT_DIR/backup/stock_launcher.txt"
fi

# 6. Build & Install MACL TV Launcher
echo ""
echo "=== Building & Installing MACL TV Launcher ==="
./scripts/build.sh
LAUNCHER_APK="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
"$ADB" -s "$ADB_TARGET" install -r "$LAUNCHER_APK"
rm -rf "$SCRIPT_DIR/app/build"
echo "✓ Launcher installed successfully."

# 7. Build & Install MACL Services Addon (FIS, Prayers, Bus)
if [ -d "$SCRIPT_DIR/addons/macl-services" ]; then
    echo ""
    echo "=== Building & Installing MACL Services Addon ==="
    (cd "$SCRIPT_DIR/addons/macl-services" && ./gradlew --no-daemon assembleRelease)
    SERVICES_APK="$SCRIPT_DIR/addons/macl-services/app/build/outputs/apk/release/app-release.apk"
    if [ -f "$SERVICES_APK" ]; then
        "$ADB" -s "$ADB_TARGET" install -r "$SERVICES_APK"
        rm -rf "$SCRIPT_DIR/addons/macl-services/app/build"
        echo "✓ MACL Services addon installed successfully."
    fi
fi

# 8. Build & Install TV Agent Addon
if [ -d "$SCRIPT_DIR/addons/tv-agent" ]; then
    echo ""
    echo "=== Building & Installing TV Agent Addon ==="
    (cd "$SCRIPT_DIR/addons/tv-agent" && ./gradlew --no-daemon assembleRelease)
    AGENT_APK="$SCRIPT_DIR/addons/tv-agent/app/build/outputs/apk/release/app-release.apk"
    if [ -f "$AGENT_APK" ]; then
        "$ADB" -s "$ADB_TARGET" install -r "$AGENT_APK"
        rm -rf "$SCRIPT_DIR/addons/tv-agent/app/build"
        echo "✓ TV Agent addon installed successfully."
    fi
fi

# 9. Grant Location Permission (Wi-Fi SSID display)
"$ADB" -s "$ADB_TARGET" shell pm grant com.macl.tvlauncher android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true

# 10. Push Default MACL Theme & Wallpaper
echo ""
echo "=== Pushing MACL Default Glass Theme ==="
THEME_DIR="$SCRIPT_DIR/themes/macl-default"
REMOTE_THEME="/sdcard/Android/data/com.macl.tvlauncher/files"
"$ADB" -s "$ADB_TARGET" shell "mkdir -p $REMOTE_THEME"
"$ADB" -s "$ADB_TARGET" push "$THEME_DIR/config.json" "$REMOTE_THEME/config.json"
if [ -f "$THEME_DIR/wallpaper.jpg" ]; then
    "$ADB" -s "$ADB_TARGET" push "$THEME_DIR/wallpaper.jpg" "$REMOTE_THEME/wallpaper.jpg"
fi
echo "✓ Theme applied."

# 11. Set as Default Launcher
echo ""
echo "=== Setting MACL TV Launcher as Default Home ==="
"$ADB" -s "$ADB_TARGET" shell cmd package set-home-activity com.macl.tvlauncher/.MainActivity 2>/dev/null || true

# Ask user if they want to disable stock launcher to prevent it from ever overriding
echo ""
read -r -p "Do you want to disable the stock launcher ($STOCK_LAUNCHER) so MACL Launcher is permanent? [y/N]: " DISABLE_STOCK || DISABLE_STOCK="N"
if [[ "$DISABLE_STOCK" =~ ^[Yy]$ ]] && [ -n "$STOCK_LAUNCHER" ] && [ "$STOCK_LAUNCHER" != "com.macl.tvlauncher" ]; then
    echo "Disabling $STOCK_LAUNCHER..."
    "$ADB" -s "$ADB_TARGET" shell pm disable-user --user 0 "$STOCK_LAUNCHER" || true
    echo "✓ Stock launcher disabled. (Can be restored anytime with ./restore.sh)"
fi

# 12. Start MACL TV Launcher
echo ""
echo "=== Launching MACL TV Launcher ==="
"$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvlauncher/.MainActivity

echo ""
echo "================================================================="
echo "🎉 Setup Complete! MACL TV Launcher is now running on your TV."
echo "   Switch themes anytime using: ./scripts/switch-theme.sh <theme>"
echo "   Restore stock launcher anytime using: ./restore.sh"
echo "================================================================="
