#!/usr/bin/env bash
# ==============================================================================
# MACL TV Launcher — Automated Setup & Companion Addons Installer
# 1-Click setup that runs the safe install script, builds & installs the add-ons,
# and verifies the living room experience on any Android TV.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "================================================================="
echo "       🇲🇻 MACL TV Launcher — Complete Setup Installer           "
echo "================================================================="
echo ""

# 1. Determine TV IP / Host
TV_HOST="${1:-${TV_HOST:-}}"
if [ -z "$TV_HOST" ]; then
    echo "Please ensure USB/Network Debugging (ADB) is enabled on your TV."
    read -r -p "Enter your TV IP address (e.g. 192.168.1.50 or Tailscale IP): " TV_HOST
fi

if [ -z "$TV_HOST" ]; then
    echo "ERROR: TV IP address is required." >&2
    exit 1
fi

ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    fi
fi

ADB_TARGET="$TV_HOST:5555"

# 2. Run the Production Safe Install Script
echo ""
echo ">>> STEP 1: Installing MACL TV Launcher with Safety Guards..."
"$SCRIPT_DIR/scripts/install.sh" "$TV_HOST"

# 3. Build & Install MACL Services Addon (FIS, Prayers, RTL Bus)
if [ -d "$SCRIPT_DIR/addons/macl-services" ]; then
    echo ""
    echo ">>> STEP 2: Installing MACL Services Addon (FIS / Prayer Times / Bus)..."
    (cd "$SCRIPT_DIR/addons/macl-services" && ./gradlew --no-daemon assembleRelease)
    SERVICES_APK="$SCRIPT_DIR/addons/macl-services/app/build/outputs/apk/release/app-release.apk"
    if [ -f "$SERVICES_APK" ]; then
        "$ADB" -s "$ADB_TARGET" install -r "$SERVICES_APK"
        rm -rf "$SCRIPT_DIR/addons/macl-services/app/build"
        echo "✓ MACL Services addon installed."
    fi
fi

# 4. Build & Install TV Agent Addon
if [ -d "$SCRIPT_DIR/addons/tv-agent" ]; then
    echo ""
    echo ">>> STEP 3: Installing TV Agent Addon..."
    (cd "$SCRIPT_DIR/addons/tv-agent" && ./gradlew --no-daemon assembleRelease)
    AGENT_APK="$SCRIPT_DIR/addons/tv-agent/app/build/outputs/apk/release/app-release.apk"
    if [ -f "$AGENT_APK" ]; then
        "$ADB" -s "$ADB_TARGET" install -r "$AGENT_APK"
        rm -rf "$SCRIPT_DIR/addons/tv-agent/app/build"
        echo "✓ TV Agent addon installed."
    fi
fi

# 5. Final Launch & Verification
echo ""
echo ">>> STEP 4: Bringing up MACL Living Room Home Screen..."
"$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvlauncher/.MainActivity

echo ""
echo "================================================================="
echo "🎉 ALL DONE! MACL TV Launcher and Addons are running on $TV_HOST."
echo "   - Switch themes: ./scripts/switch-theme.sh <theme>"
echo "   - Restore stock launcher anytime: ./restore.sh"
echo "================================================================="
