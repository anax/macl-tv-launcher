#!/usr/bin/env bash
# ==============================================================================
# MACL TV Agent — Terminal Controller CLI
# Call your TV Agent directly from your terminal over ADB or Wi-Fi.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TV_HOST="${TV_HOST:-100.83.39.24}"
ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    fi
fi
ADB_TARGET="$TV_HOST:5555"

show_help() {
    cat <<EOF
Usage: ./scripts/tv-agent.sh [COMMAND | OPTIONS]

Natural Language & AI Tasks:
  ./scripts/tv-agent.sh "Open YouTube and play aviation landing videos"
  ./scripts/tv-agent.sh "Set volume to 40 and check prayer time"
  ./scripts/tv-agent.sh "Launch MACL Services flight board"

Direct TV Quick Controls:
  ./scripts/tv-agent.sh --say "Your message"       Speak aloud on the TV (TTS)
  ./scripts/tv-agent.sh --volume <0-100>           Set volume level
  ./scripts/tv-agent.sh --open "<app_name>"        Launch any TV app by name
  ./scripts/tv-agent.sh --youtube "<search_query>" Search & open YouTube
  ./scripts/tv-agent.sh --media [play|pause|next]  Control current media playback
  ./scripts/tv-agent.sh --orb [start|stop]         Start or stop the floating orb
  ./scripts/tv-agent.sh --status                   Check TV Agent and active screen
EOF
}

if [ $# -eq 0 ]; then
    show_help
    exit 0
fi

ensure_connected() {
    "$ADB" connect "$ADB_TARGET" >/dev/null 2>&1 || true
}

case "$1" in
    --help|-h)
        show_help
        exit 0
        ;;
    --say)
        ensure_connected
        TEXT="${2:-}"
        if [ -z "$TEXT" ]; then
            echo "Error: --say requires a text string." >&2
            exit 1
        fi
        echo "🗣️ Speaking on TV: \"$TEXT\""
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Speak using tool say: $TEXT" >/dev/null 2>&1
        echo "✓ Sent to TV."
        ;;
    --volume)
        ensure_connected
        VOL="${2:-}"
        if [ -z "$VOL" ]; then
            echo "Error: --volume requires a level between 0 and 100." >&2
            exit 1
        fi
        echo "🔊 Setting TV volume to $VOL%..."
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Set volume to $VOL" >/dev/null 2>&1
        echo "✓ Volume command sent."
        ;;
    --open)
        ensure_connected
        APP="${2:-}"
        if [ -z "$APP" ]; then
            echo "Error: --open requires an application name." >&2
            exit 1
        fi
        echo "🚀 Launching $APP on TV..."
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Open app $APP" >/dev/null 2>&1
        echo "✓ Launch request sent."
        ;;
    --youtube)
        ensure_connected
        QUERY="${2:-}"
        if [ -z "$QUERY" ]; then
            echo "Error: --youtube requires a search query." >&2
            exit 1
        fi
        echo "📺 Searching YouTube on TV for: \"$QUERY\"..."
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Search YouTube for $QUERY" >/dev/null 2>&1
        echo "✓ YouTube search sent."
        ;;
    --media)
        ensure_connected
        CMD="${2:-play_pause}"
        echo "⏯️ Sending media command: $CMD"
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Media command $CMD" >/dev/null 2>&1
        echo "✓ Media command sent."
        ;;
    --orb)
        ensure_connected
        ACTION="${2:-start}"
        if [ "$ACTION" = "start" ]; then
            echo "🔮 Starting floating ambient orb over TV screen..."
            "$ADB" -s "$ADB_TARGET" shell am startforegroundservice -n com.macl.tvagent/.OrbService >/dev/null 2>&1
            echo "✓ Orb active on TV."
        else
            echo "Hiding floating orb..."
            "$ADB" -s "$ADB_TARGET" shell am stopservice com.macl.tvagent/.OrbService >/dev/null 2>&1
            echo "✓ Orb stopped."
        fi
        ;;
    --status)
        ensure_connected
        echo "=== TV Agent Status ==="
        echo "Target: $ADB_TARGET"
        FOCUSED=$("$ADB" -s "$ADB_TARGET" shell "dumpsys window | grep -E 'mCurrentFocus'" 2>/dev/null | tr -d '\r')
        echo "Active Window: $FOCUSED"
        PKG_STATE=$("$ADB" -s "$ADB_TARGET" shell pm path com.macl.tvagent 2>/dev/null || true)
        if [ -n "$PKG_STATE" ]; then
            echo "TV Agent: Installed (com.macl.tvagent)"
        else
            echo "TV Agent: Not installed"
        fi
        ;;
    *)
        ensure_connected
        PROMPT="$*"
        echo "🤖 Sending task to TV Agent: \"$PROMPT\""
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "$PROMPT" >/dev/null 2>&1
        echo "✓ Prompt dispatched to TV. Agent is executing task."
        ;;
esac
