#!/usr/bin/env bash
# ==============================================================================
# MACL TV Agent — Terminal Controller CLI
# Call your TV Agent directly from your terminal over ADB or Wi-Fi.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TV_HOST="${TV_HOST:-${ZENITH_HOST:-}}"
if [ -z "$TV_HOST" ]; then
    if [ -n "$(getprop ro.product.model 2>/dev/null)" ] || [ -f "/system/build.prop" ]; then
        TV_HOST="127.0.0.1"
    else
        TV_HOST="100.83.39.24"
    fi
fi
ADB="adb"
if ! command -v adb >/dev/null 2>&1; then
    if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    elif [ -n "${PREFIX:-}" ] && [ -x "$PREFIX/bin/adb" ]; then
        ADB="$PREFIX/bin/adb"
    elif [ -x "$HOME/.local/bin/adb" ]; then
        ADB="$HOME/.local/bin/adb"
    fi
fi
ADB_TARGET="$TV_HOST:5555"

show_help() {
    local cmd="zenith"
    cat <<EOF
Usage: $cmd [COMMAND | OPTIONS]

Type '$cmd' with no arguments to start an interactive chat session.

Interactive Mode:
  $cmd                             Start interactive chat session (ideal for Termux & tmux)
  $cmd --install                   Install 'zenith' to your PATH (\$PREFIX/bin or ~/.local/bin)

Natural Language & AI Tasks:
  $cmd "Open YouTube and play aviation landing videos"
  $cmd "Set volume to 40 and check prayer time"
  $cmd "Launch MACL Services flight board"

Direct TV Quick Controls:
  $cmd --home                     Return to TV Home screen immediately
  $cmd --close "<app_name>"       Close an app and return home
  $cmd --input [hdmi1|hdmi2|...]  Switch TV input source (hdmi1, hdmi2, hdmi3, av, tv, menu)
  $cmd --key [up|down|ok|back...] Send TV remote D-pad / navigation key
  $cmd --power [sleep|wake]       Control TV display power state
  $cmd --say "Your message"       Speak aloud on the TV (TTS)
  $cmd --volume <0-100>           Set volume level
  $cmd --open "<app_name>"        Launch any TV app by name
  $cmd --youtube "<search_query>" Search & open YouTube
  $cmd --media [play|pause|next]  Control current media playback
  $cmd --orb [start|stop]         Start or stop the floating notch HUD
  $cmd --status                   Check TV Agent and active screen
EOF
}

install_cli() {
    local target_bin=""
    if [ -n "${PREFIX:-}" ] && [ -d "$PREFIX/bin" ]; then
        target_bin="$PREFIX/bin/zenith"
    elif [ -w "/usr/local/bin" ]; then
        target_bin="/usr/local/bin/zenith"
    elif [ -d "$HOME/.local/bin" ]; then
        target_bin="$HOME/.local/bin/zenith"
    else
        mkdir -p "$HOME/.local/bin"
        target_bin="$HOME/.local/bin/zenith"
    fi

    ln -sf "$SCRIPT_DIR/tv-agent.sh" "$target_bin"
    chmod +x "$target_bin"
    echo "✓ Zenith successfully installed to: $target_bin"
    echo ""
    echo "Now you can simply type:"
    echo "  zenith"
    echo "from Termux or any terminal to launch your chat session!"
    if [ "$target_bin" = "$HOME/.local/bin/zenith" ]; then
        case ":$PATH:" in
            *:"$HOME/.local/bin":*) ;;
            *) echo "💡 Add '$HOME/.local/bin' to your PATH: export PATH=\"\$HOME/.local/bin:\$PATH\"" ;;
        esac
    fi
    exit 0
}

ensure_connected() {
    if ! command -v "$ADB" >/dev/null 2>&1; then
        echo "⚠️  'adb' command not found." >&2
        if [ -n "${PREFIX:-}" ]; then
            echo "👉 In Termux, install android-tools with:  pkg install android-tools" >&2
        else
            echo "👉 Please install Android platform tools (adb)." >&2
        fi
        exit 1
    fi
    "$ADB" connect "$ADB_TARGET" >/dev/null 2>&1 || true
    if [ -z "$(getprop ro.product.model 2>/dev/null)" ] && [ ! -f "/system/build.prop" ]; then
        "$ADB" -s "$ADB_TARGET" forward tcp:8787 tcp:8787 >/dev/null 2>&1 || true
    fi
}

dispatch_prompt() {
    local text="$1"
    local target_host="$TV_HOST"
    if [ -n "$(getprop ro.product.model 2>/dev/null)" ] || [ -f "/system/build.prop" ]; then
        target_host="127.0.0.1"
    fi

    # 1. Direct HTTP Bridge (VoiceServer) if reachable
    if command -v curl >/dev/null 2>&1; then
        local url_host="127.0.0.1"
        if ! curl -s -m 1 "http://127.0.0.1:8787/state" >/dev/null 2>&1; then
            url_host="$target_host"
        fi
        if curl -s -m 2 "http://$url_host:8787/state" >/dev/null 2>&1; then
            echo "🧠 Thinking..."
            local resp
            resp=$(curl -s -X POST "http://$url_host:8787/ask" --data-urlencode "text=$text" 2>/dev/null || true)
            if [ -n "$resp" ]; then
                local ans
                ans=$(printf '%s' "$resp" | sed -n 's/.*"answer":"\([^"]*\)".*/\1/p' | sed 's/\\n/\n/g' | sed 's/\\"/"/g' || true)
                if [ -n "$ans" ]; then
                    echo ""
                    echo "🛰️ Zenith: $ans"
                    return 0
                fi
                local err
                err=$(printf '%s' "$resp" | sed -n 's/.*"error":"\([^"]*\)".*/\1/p' || true)
                if [ -n "$err" ]; then
                    echo "⚠️ $err"
                    return 1
                fi
            fi
        fi
    fi

    # 2. ADB Bridge Fallback with Response Listener
    "$ADB" -s "$ADB_TARGET" shell rm -f /data/local/tmp/zenith_response.txt >/dev/null 2>&1 || true

    if "$ADB" -s "$ADB_TARGET" shell dumpsys activity services com.macl.tvagent/.OrbService 2>/dev/null | grep -q "ServiceRecord"; then
        "$ADB" -s "$ADB_TARGET" shell am startforegroundservice -n com.macl.tvagent/.OrbService --es prompt "$text" >/dev/null 2>&1
    else
        "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "$text" >/dev/null 2>&1
    fi

    echo "🧠 Thinking..."
    local elapsed=0
    while [ $elapsed -lt 60 ]; do
        sleep 0.5
        local res
        res=$("$ADB" -s "$ADB_TARGET" shell cat /data/local/tmp/zenith_response.txt 2>/dev/null | tr -d '\r' || true)
        if [ -n "$res" ]; then
            if [[ "$res" == ANSWER:* ]]; then
                echo ""
                echo "🛰️ Zenith: ${res#ANSWER:}"
                return 0
            elif [[ "$res" == ERROR:* ]]; then
                echo ""
                echo "⚠️ Zenith: ${res#ERROR:}"
                return 1
            fi
        fi
        elapsed=$((elapsed + 1))
    done

    echo "✓ Task sent to TV (see TV screen for status)."
}

run_repl() {
    ensure_connected
    echo ""
    echo "╔═══════════════════════════════════════════════════════════════════╗"
    echo "║               🛰️  ZENITH // AMBIENT TV AGENT REPL                ║"
    echo "║       Connected Target: $ADB_TARGET                        ║"
    echo "║       Optimized for tmux terminal panes & SSH remote sessions     ║"
    echo "╚═══════════════════════════════════════════════════════════════════╝"
    echo "Commands: /home, /close <app>, /input <src>, /vol <0-100>, /say <msg>, /status, exit"
    echo "Type any goal or natural language task below."
    echo ""

    while true; do
        read -r -p "zenith ❯ " input || break
        [ -z "$input" ] && continue
        case "$input" in
            exit|quit|q|:q)
                echo "Exiting TV Agent session."
                break
                ;;
            /help)
                show_help
                ;;
            /home)
                "$SCRIPT_DIR/tv-agent.sh" --home
                ;;
            /close*)
                arg="${input#* }"
                "$SCRIPT_DIR/tv-agent.sh" --close "$arg"
                ;;
            /input*)
                arg="${input#* }"
                "$SCRIPT_DIR/tv-agent.sh" --input "$arg"
                ;;
            /power*)
                arg="${input#* }"
                "$SCRIPT_DIR/tv-agent.sh" --power "$arg"
                ;;
            /key*)
                arg="${input#* }"
                "$SCRIPT_DIR/tv-agent.sh" --key "$arg"
                ;;
            /status)
                ensure_connected
                echo "=== TV Agent Status ==="
                echo "Target: $ADB_TARGET"
                FOCUSED=$("$ADB" -s "$ADB_TARGET" shell "dumpsys window | grep -E 'mCurrentFocus'" 2>/dev/null | tr -d '\r')
                echo "Active Window: $FOCUSED"
                ;;
            /notch*|/orb*)
                arg="${input#* }"
                [ "$arg" = "$input" ] && arg="start"
                if [ "$arg" = "start" ]; then
                    echo "🔮 Starting floating ambient notch HUD..."
                    "$ADB" -s "$ADB_TARGET" shell am startforegroundservice -n com.macl.tvagent/.OrbService >/dev/null 2>&1
                    echo "✓ Ambient notch active."
                else
                    echo "Hiding floating notch HUD..."
                    "$ADB" -s "$ADB_TARGET" shell am stopservice com.macl.tvagent/.OrbService >/dev/null 2>&1
                    echo "✓ Ambient notch stopped."
                fi
                ;;
            /vol*)
                level="${input#* }"
                echo "🔊 Setting volume to $level%..."
                "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Set volume to $level" >/dev/null 2>&1
                ;;
            /say*)
                msg="${input#* }"
                echo "🗣️ Speaking: \"$msg\"..."
                "$ADB" -s "$ADB_TARGET" shell am start -n com.macl.tvagent/.AgentActivity --es prompt "Speak using tool say: $msg" >/dev/null 2>&1
                ;;
            *)
                dispatch_prompt "$input"
                ;;
        esac
        echo ""
    done
}

if [ $# -eq 0 ]; then
    run_repl
    exit 0
fi

case "$1" in
    --install|install)
        install_cli
        ;;
    --repl|-i|repl|chat)
        run_repl
        ;;
    --help|-h)
        show_help
        exit 0
        ;;
    --home)
        ensure_connected
        "$ADB" -s "$ADB_TARGET" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1
        echo "✓ Returned to TV Home."
        ;;
    --close)
        ensure_connected
        TARGET="${2:-}"
        if [ -z "$TARGET" ]; then
            echo "Error: --close requires an app name." >&2
            exit 1
        fi
        dispatch_prompt "close $TARGET"
        ;;
    --input)
        ensure_connected
        SRC="${2:-menu}"
        dispatch_prompt "switch input to $SRC"
        ;;
    --power)
        ensure_connected
        ACT="${2:-sleep}"
        if [ "$ACT" = "wake" ]; then
            "$ADB" -s "$ADB_TARGET" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
            echo "✓ TV screen woke up."
        else
            dispatch_prompt "put TV into sleep mode"
        fi
        ;;
    --key)
        ensure_connected
        KEY="${2:-}"
        case "$KEY" in
            up) CODE=19 ;;
            down) CODE=20 ;;
            left) CODE=21 ;;
            right) CODE=22 ;;
            select|enter|ok) CODE=23 ;;
            back) CODE=4 ;;
            home) CODE=3 ;;
            menu) CODE=82 ;;
            volup) CODE=24 ;;
            voldn) CODE=25 ;;
            mute) CODE=164 ;;
            *) echo "Unknown key: $KEY"; exit 1 ;;
        esac
        "$ADB" -s "$ADB_TARGET" shell input keyevent "$CODE" >/dev/null 2>&1
        echo "✓ Sent remote key $KEY ($CODE)."
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
        dispatch_prompt "$*"
        ;;
esac
