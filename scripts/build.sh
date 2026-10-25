#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Building MACL TV Launcher Release APK ==="
cd "$PROJECT_ROOT"

# Ensure JDK 17
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "/opt/homebrew/Cellar/openjdk@17" ]; then
        export JAVA_HOME="$(ls -d /opt/homebrew/Cellar/openjdk@17/*/libexec/openjdk.jdk/Contents/Home 2>/dev/null | head -n 1)"
    fi
fi

# Ensure Android SDK
if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    fi
fi

./gradlew --no-daemon assembleRelease

APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    SIZE=$(stat -f %z "$APK_PATH" 2>/dev/null || stat -c %s "$APK_PATH" 2>/dev/null)
    echo "✓ Build successful: $APK_PATH ($SIZE bytes)"
else
    echo "✗ Error: APK not found at $APK_PATH" >&2
    exit 1
fi
