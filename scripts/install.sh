#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

TV_HOST="${1:-${TV_HOST:-}}"
if [ -z "$TV_HOST" ]; then
    echo "Usage: TV_HOST=<tv_ip> $0 [tv_ip]"
    echo "Example: TV_HOST=100.83.39.24 $0"
    exit 1
fi

ADB="adb"
if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
fi

echo "=== Connecting to TV at $TV_HOST:5555 ==="
"$ADB" connect "$TV_HOST:5555" || true

# Build if needed
APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    "$SCRIPT_DIR/build.sh"
fi

echo "=== Installing MACL TV Launcher ==="
"$ADB" -s "$TV_HOST:5555" install -r "$APK_PATH"

echo "=== Granting location permission for Wi-Fi SSID display ==="
"$ADB" -s "$TV_HOST:5555" shell pm grant com.macl.tvlauncher android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true

echo "=== Setting MACL TV Launcher as Default Home ==="
"$ADB" -s "$TV_HOST:5555" shell cmd package set-home-activity com.macl.tvlauncher/.MainActivity 2>/dev/null || true

echo "=== Launching MACL TV Launcher ==="
"$ADB" -s "$TV_HOST:5555" shell am start -n com.macl.tvlauncher/.MainActivity

echo "✓ MACL TV Launcher deployed and running on $TV_HOST!"
