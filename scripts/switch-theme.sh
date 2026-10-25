#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

THEME_PATH="${1:-}"
TV_HOST="${TV_HOST:-}"

if [ -z "$THEME_PATH" ] || [ -z "$TV_HOST" ]; then
    echo "Usage: TV_HOST=<tv_ip> $0 <path_to_theme_folder>"
    echo "Example: TV_HOST=100.83.39.24 $0 themes/macl-default"
    exit 1
fi

if [[ "$THEME_PATH" != /* ]]; then
    THEME_PATH="$PROJECT_ROOT/$THEME_PATH"
fi

if [ ! -f "$THEME_PATH/config.json" ]; then
    echo "✗ Error: config.json not found in $THEME_PATH" >&2
    exit 1
fi

ADB="adb"
if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
fi

echo "=== Connecting to TV at $TV_HOST:5555 ==="
"$ADB" connect "$TV_HOST:5555" || true

REMOTE_DIR="/sdcard/Android/data/com.macl.tvlauncher/files"
echo "=== Pushing theme files to $REMOTE_DIR ==="
"$ADB" -s "$TV_HOST:5555" shell "mkdir -p $REMOTE_DIR"
"$ADB" -s "$TV_HOST:5555" push "$THEME_PATH/config.json" "$REMOTE_DIR/config.json"

if [ -f "$THEME_PATH/wallpaper.jpg" ]; then
    "$ADB" -s "$TV_HOST:5555" push "$THEME_PATH/wallpaper.jpg" "$REMOTE_DIR/wallpaper.jpg"
fi

echo "=== Restarting MACL TV Launcher ==="
"$ADB" -s "$TV_HOST:5555" shell am force-stop com.macl.tvlauncher
sleep 1
"$ADB" -s "$TV_HOST:5555" shell am start -n com.macl.tvlauncher/.MainActivity

echo "✓ Theme applied successfully!"
