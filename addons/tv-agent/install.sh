#!/bin/sh
# Install Agent on an Android TV, from one URL.
#
# On the TV, or over its adb shell:
#   curl -fsSL http://<computer>:8788/install | sh
#
# On a computer, inside this repo (needs TV_HOST and adb, or tools/venv):
#   curl -fsSL http://<computer>:8788/install | sh
#
# serve.py fills in the server address, so the same script works on any host and port. Set
# AGENT_BASE_URL to use it without the server, and AGENT_APK_URL for a build hosted elsewhere.
set -eu

BASE="${AGENT_BASE_URL:-__BASE__}"
APK_URL="${AGENT_APK_URL:-$BASE/tv-agent.apk}"
SUMS_URL="${AGENT_SUMS_URL:-$BASE/SHA256SUMS}"

say() { printf 'Agent: %s\n' "$*"; }
die() { printf 'Agent: %s\n' "$*" >&2; exit 1; }

fetch() {
    url="$1"
    dest="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$url" -o "$dest"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$dest" "$url"
    elif command -v toybox >/dev/null 2>&1; then
        toybox wget -q -O "$dest" "$url"
    else
        return 127
    fi
}

case "$BASE" in
    http://*|https://*) ;;
    *) die "this copy has no server address; use the copy served by serve.py, or set AGENT_BASE_URL" ;;
esac

if [ -d /data/local/tmp ] && [ -w /data/local/tmp ]; then
    TMP=/data/local/tmp/tv-agent.apk
else
    TMP="${TMPDIR:-/tmp}/tv-agent.apk"
fi

say "downloading $APK_URL"
fetch "$APK_URL" "$TMP" || die "no curl, wget or toybox wget here; run this on a computer with adb instead"
[ -s "$TMP" ] || die "the download was empty"

if fetch "$SUMS_URL" "$TMP.sums" 2>/dev/null; then
    want=$(awk '/tv-agent\.apk$/ {print $1; exit}' "$TMP.sums" 2>/dev/null || true)
    if [ -n "${want:-}" ]; then
        if command -v sha256sum >/dev/null 2>&1; then
            got=$(sha256sum "$TMP" | awk '{print $1}')
        elif command -v shasum >/dev/null 2>&1; then
            got=$(shasum -a 256 "$TMP" | awk '{print $1}')
        else
            got=""
        fi
        if [ -n "$got" ] && [ "$got" != "$want" ]; then
            rm -f "$TMP"
            die "checksum mismatch: got $got, wanted $want"
        fi
    fi
    rm -f "$TMP.sums"
fi

if command -v pm >/dev/null 2>&1; then
    say "installing"
    pm install -r "$TMP" || die "pm install refused the APK"
    rm -f "$TMP"
    # The floating orb needs Display over other apps. Best effort: Settings can still grant it by hand.
    appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 \
        || say "enable Display over other apps for the floating orb"
    say "installed. Open Agent from Home; answer Show on its card."
    say "Phone voice > Float over TV keeps the page and shows the circle over other apps."
    if [ "${AGENT_LAUNCH:-1}" = "1" ] && command -v am >/dev/null 2>&1; then
        am start -n com.macl.tvagent/.AgentActivity >/dev/null 2>&1 || true
    fi
    exit 0
fi

# A computer: adb if it is here, then this repo's tv.py.
if command -v adb >/dev/null 2>&1; then
    if [ -n "${TV_HOST:-}" ] && adb connect "$TV_HOST:5555" >/dev/null 2>&1 \
        && adb -s "$TV_HOST:5555" install -r "$TMP"; then
        adb -s "$TV_HOST:5555" shell appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 \
            || say "enable Display over other apps for the floating orb"
        rm -f "$TMP"
        say "installed. Phone voice > Float over TV shows the circle over other apps."
        exit 0
    elif [ -z "${TV_HOST:-}" ] && adb install -r "$TMP"; then
        adb shell appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 \
            || say "enable Display over other apps for the floating orb"
        rm -f "$TMP"
        say "installed. Phone voice > Float over TV shows the circle over other apps."
        exit 0
    fi
fi

if [ -n "${TV_HOST:-}" ] && [ -x tools/venv/bin/python ] && [ -f tools/tv.py ]; then
    tools/venv/bin/python -I tools/tv.py --push "$TMP" /data/local/tmp/tv-agent.apk
    tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/tv-agent.apk; appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow; rm -f /data/local/tmp/tv-agent.apk'
    rm -f "$TMP"
    say "installed. Phone voice > Float over TV shows the circle over other apps."
    exit 0
fi

die "downloaded, but found no way to install: run this on the TV, or set TV_HOST and run it in the repo"
