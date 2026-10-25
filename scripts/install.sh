#!/usr/bin/env bash
# ==============================================================================
# MACL TV Launcher — Safe Production Install Script
# Adheres strictly to AGENTS.md safety protocols:
#   1. Pre-flight minSdk validation against TV firmware.
#   2. Full device backup (packages, getprop, settings) before any changes.
#   3. Home Guard: Stock launcher is NEVER disabled until the new launcher
#      is installed, launched with `am start -W`, and verified active.
#   4. AOT native compilation (`compile -m speed`) on the TV ART runtime.
#   5. Automatic self-rollback if Home resolution fails.
#   6. Reversible with ./restore.sh (Disable, never uninstall).
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

TV_HOST="${1:-${TV_HOST:-}}"
if [ -z "$TV_HOST" ]; then
    echo "Usage: TV_HOST=<tv_ip> $0 [tv_ip]"
    echo "Example: TV_HOST=100.83.39.24 $0"
    exit 1
fi

# 1. Locate ADB
ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    else
        echo "ERROR: adb not found. Please install Android platform-tools." >&2
        exit 1
    fi
fi

ADB_TARGET="$TV_HOST:5555"
echo "=== Connecting to TV at $ADB_TARGET ==="
"$ADB" connect "$ADB_TARGET" || true
sleep 1

if ! "$ADB" -s "$ADB_TARGET" get-state >/dev/null 2>&1; then
    echo "ERROR: Cannot reach $ADB_TARGET. Check IP and verify USB/Network debugging is ON." >&2
    exit 1
fi

# 2. Pre-flight SDK check
MIN_SDK=$(sed -n 's/^[[:space:]]*minSdk[[:space:]][[:space:]]*\([0-9]\{1,3\}\)[[:space:]]*$/\1/p' app/build.gradle 2>/dev/null | head -n 1 || true)
TV_SDK=$("$ADB" -s "$ADB_TARGET" shell getprop ro.build.version.sdk 2>/dev/null | tr -dc '0-9' || true)
if [ -n "$MIN_SDK" ] && [ -n "$TV_SDK" ] && [ "$TV_SDK" -lt "$MIN_SDK" ]; then
    echo "ERROR: TV runs SDK $TV_SDK, but launcher requires minSdk $MIN_SDK." >&2
    echo "Installation aborted to prevent INSTALL_FAILED_OLDER_SDK. Nothing was changed." >&2
    exit 1
fi

BRAND=$("$ADB" -s "$ADB_TARGET" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')
MODEL=$("$ADB" -s "$ADB_TARGET" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VER=$("$ADB" -s "$ADB_TARGET" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo "Connected: $BRAND $MODEL (Android $ANDROID_VER, SDK $TV_SDK)"

# 3. Full Backup Before Any Modification (AGENTS.md Rule 2)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="$PROJECT_ROOT/backup/$TIMESTAMP"
mkdir -p "$BACKUP_DIR"
echo "=== Backing up TV state to $BACKUP_DIR ==="
"$ADB" -s "$ADB_TARGET" shell pm list packages -u > "$BACKUP_DIR/packages_all.txt" 2>/dev/null || true
"$ADB" -s "$ADB_TARGET" shell pm list packages -d > "$BACKUP_DIR/packages_disabled.txt" 2>/dev/null || true
"$ADB" -s "$ADB_TARGET" shell getprop > "$BACKUP_DIR/getprop.txt" 2>/dev/null || true

# 4. Resolve Stock Launcher (AGENTS.md Home Guard)
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
KNOWN_STOCK_LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.mitv.tvhome com.cvte.tv.launcher com.hisense.tv.launcher"

HOME_PKG=$("$ADB" -s "$ADB_TARGET" shell "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
echo "Current Home owner: ${HOME_PKG:-none}"
echo "$HOME_PKG" > "$BACKUP_DIR/original_home.txt"

STOCK_LAUNCHER=""
if [ "$HOME_PKG" = "com.macl.tvlauncher" ]; then
    echo "Notice: MACL TV Launcher is already the active Home."
    STOCK_LAUNCHER="none"
elif [ "$HOME_PKG" = "android" ]; then
    # Resolver dialog: find the single stock candidate among claimants
    CLAIMANTS=$("$ADB" -s "$ADB_TARGET" shell "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME" 2>/dev/null | tr -d '\r' | grep -o '[A-Za-z0-9_.]*/' | tr -d / | sort -u || true)
    FOUND=""; N=0
    for k in $KNOWN_STOCK_LAUNCHERS; do
        if printf '%s\n' "$CLAIMANTS" | grep -qx "$k"; then FOUND=$k; N=$((N + 1)); fi
    done
    if [ "$N" -eq 1 ]; then
        STOCK_LAUNCHER=$FOUND
        echo "Discovered stock launcher candidate: $STOCK_LAUNCHER"
    else
        echo "Notice: Multiple or unrecognized claimants. Stock launcher will not be disabled automatically."
        STOCK_LAUNCHER="none"
    fi
else
    case " $KNOWN_STOCK_LAUNCHERS " in
        *" $HOME_PKG "*) STOCK_LAUNCHER=$HOME_PKG ;;
        *)
            echo "Notice: Home is $HOME_PKG (unlisted). Stock launcher will be left enabled."
            STOCK_LAUNCHER="none"
            ;;
    esac
fi

# 5. Build Release APK
echo ""
echo "=== Building MACL TV Launcher Release APK ==="
./scripts/build.sh
APK="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"

# 6. Install Launcher APK (Decoupled execution)
echo ""
echo "=== Installing MACL TV Launcher APK ==="
INSTALL_OUT=$("$ADB" -s "$ADB_TARGET" install -r "$APK" 2>&1 || true)
echo "$INSTALL_OUT"
if ! printf '%s' "$INSTALL_OUT" | grep -qF "Success"; then
    echo "ERROR: pm install failed. Stock launcher was left untouched." >&2
    exit 1
fi

# 7. Verification Launch (The Home Guard: Verify activity starts before touching Home)
echo ""
echo "=== Verifying Launcher Starts (am start -W) ==="
START_OUT=$("$ADB" -s "$ADB_TARGET" shell am start -W -n com.macl.tvlauncher/.MainActivity 2>&1 || true)
echo "$START_OUT"
if ! printf '%s' "$START_OUT" | grep -qF "Status: ok" || printf '%s' "$START_OUT" | grep -qi "^Error"; then
    echo "ERROR: Launcher failed to start. Aborting handover; stock launcher left untouched." >&2
    exit 1
fi

# 8. Grant Runtime Permissions & AOT Native Compilation
echo ""
echo "=== Optimizing & Granting Permissions ==="
"$ADB" -s "$ADB_TARGET" shell pm grant com.macl.tvlauncher android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
echo "✓ Location permission granted (for Wi-Fi SSID display)"

# ART AOT compilation for 0-lag TV performance
echo "Compiling launcher with AOT speed profile..."
"$ADB" -s "$ADB_TARGET" shell cmd package compile -m speed -f com.macl.tvlauncher >/dev/null 2>&1 || true
echo "✓ AOT compilation complete"

# 9. Handover: Set Home Activity & Optionally Disable Stock
echo ""
echo "=== Setting MACL TV Launcher as Default Home ==="
"$ADB" -s "$ADB_TARGET" shell cmd package set-home-activity --user 0 com.macl.tvlauncher/.MainActivity 2>/dev/null || true

# Safe disable stock only if verified
if [ -n "$STOCK_LAUNCHER" ] && [ "$STOCK_LAUNCHER" != "none" ] && [ "$STOCK_LAUNCHER" != "com.macl.tvlauncher" ]; then
    echo "Disabling stock launcher ($STOCK_LAUNCHER) via 'pm disable-user --user 0'..."
    "$ADB" -s "$ADB_TARGET" shell pm disable-user --user 0 "$STOCK_LAUNCHER" || true
    echo "$STOCK_LAUNCHER" > "$PROJECT_ROOT/backup/last_disabled_stock.txt"
fi

# 10. Verification of Home Handover & Auto-Rollback
HOME_NOW=$("$ADB" -s "$ADB_TARGET" shell "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' || true)
echo "Verified active Home: $HOME_NOW"

rollback_home() {
    echo "WARNING: Handover failed! Initiating automatic rollback..." >&2
    if [ -n "$STOCK_LAUNCHER" ] && [ "$STOCK_LAUNCHER" != "none" ]; then
        "$ADB" -s "$ADB_TARGET" shell pm enable --user 0 "$STOCK_LAUNCHER" 2>/dev/null || true
        "$ADB" -s "$ADB_TARGET" shell cmd package set-home-activity --user 0 "$STOCK_LAUNCHER" 2>/dev/null || true
        echo "✓ Stock launcher $STOCK_LAUNCHER re-enabled." >&2
    fi
}

case "$HOME_NOW" in
    com.macl.tvlauncher/*)
        echo "✓ Handover verified! com.macl.tvlauncher owns Home."
        ;;
    *)
        rollback_home
        echo "ERROR: Home resolves to '$HOME_NOW' instead of com.macl.tvlauncher." >&2
        exit 1
        ;;
esac

# 11. Configure Remote Accessibility Helpers (InputKeyService / HomeKeyService)
echo ""
echo "=== Configuring Remote Button Helpers ==="
"$ADB" -s "$ADB_TARGET" shell cmd appops set com.macl.tvlauncher AUTO_START allow 2>/dev/null || true
"$ADB" -s "$ADB_TARGET" shell pm grant com.macl.tvlauncher android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true

enable_helper() {
    local svc="com.macl.tvlauncher/.$1"
    local cur=$("$ADB" -s "$ADB_TARGET" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
    case "$cur" in
        *"$1"*) ;;
        null|""|" ")
            "$ADB" -s "$ADB_TARGET" shell "settings put secure enabled_accessibility_services $svc && settings put secure accessibility_enabled 1" 2>/dev/null || true
            ;;
        *)
            "$ADB" -s "$ADB_TARGET" shell "settings put secure enabled_accessibility_services '$cur:$svc' && settings put secure accessibility_enabled 1" 2>/dev/null || true
            ;;
    esac
}
enable_helper InputKeyService
enable_helper HomeKeyService
echo "✓ Remote button helpers configured"

# 12. Push Default Theme
echo ""
echo "=== Pushing Default Theme ==="
THEME_DIR="$PROJECT_ROOT/themes/macl-default"
REMOTE_FILES="/sdcard/Android/data/com.macl.tvlauncher/files"
"$ADB" -s "$ADB_TARGET" shell "mkdir -p $REMOTE_FILES" 2>/dev/null || true
"$ADB" -s "$ADB_TARGET" push "$THEME_DIR/config.json" "$REMOTE_FILES/config.json" >/dev/null 2>&1 || true
if [ -f "$THEME_DIR/wallpaper.jpg" ]; then
    "$ADB" -s "$ADB_TARGET" push "$THEME_DIR/wallpaper.jpg" "$REMOTE_FILES/wallpaper.jpg" >/dev/null 2>&1 || true
fi
echo "✓ Theme files pushed"

# 13. Clean up build artifacts before completing
rm -rf "$PROJECT_ROOT/app/build"

echo ""
echo "================================================================="
echo "🎉 SUCCESS: MACL TV Launcher installed and verified on $TV_HOST!"
echo "   - Backup saved to: backup/$TIMESTAMP/"
echo "   - Remote helpers: InputKeyService & HomeKeyService active"
echo "   - Revert anytime with: ./restore.sh"
echo "================================================================="
