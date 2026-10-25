#!/usr/bin/env bash
# ==============================================================================
# MACL TV Launcher — Restore Stock Launcher Script
# Re-enables the stock launcher and clears default home activity.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TV_HOST="${1:-${TV_HOST:-}}"
if [ -z "$TV_HOST" ]; then
    read -r -p "Enter your TV IP address: " TV_HOST
fi

ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    fi
fi

ADB_TARGET="$TV_HOST:5555"
"$ADB" connect "$ADB_TARGET" || true

STOCK_LAUNCHER=""
if [ -f "$SCRIPT_DIR/backup/stock_launcher.txt" ]; then
    STOCK_LAUNCHER=$(cat "$SCRIPT_DIR/backup/stock_launcher.txt" | tr -d '\r\n')
fi

# Fallback stock launcher names if no backup found
if [ -z "$STOCK_LAUNCHER" ]; then
    for PKG in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.cvte.tv.launcher com.hisense.tv.launcher; do
        if "$ADB" -s "$ADB_TARGET" shell pm list packages -u | grep -q "$PKG"; then
            STOCK_LAUNCHER="$PKG"
            break
        fi
    done
fi

if [ -n "$STOCK_LAUNCHER" ]; then
    echo "Re-enabling stock launcher: $STOCK_LAUNCHER..."
    "$ADB" -s "$ADB_TARGET" shell pm enable --user 0 "$STOCK_LAUNCHER" || true
    "$ADB" -s "$ADB_TARGET" shell cmd package set-home-activity "$STOCK_LAUNCHER" 2>/dev/null || true
    "$ADB" -s "$ADB_TARGET" shell am start -c android.intent.category.HOME -a android.intent.action.MAIN
    echo "✓ Stock launcher $STOCK_LAUNCHER restored!"
else
    echo "Clearing default home activity..."
    "$ADB" -s "$ADB_TARGET" shell cmd package set-home-activity "" 2>/dev/null || true
    "$ADB" -s "$ADB_TARGET" shell am start -c android.intent.category.HOME -a android.intent.action.MAIN
    echo "✓ Default home cleared. Select your preferred launcher on the TV screen."
fi
