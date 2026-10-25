# Agent

An assistant that lives on the TV and actually does things: give it a goal in your own words and it plans the
steps, opens the app, sets the volume, searches YouTube, and tells you what it did. For the
[Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md), so once you answer
**Show card** its tile reads "Agent" with a violet glow; on any other Android TV home it is an ordinary app.

It is built for this repo's TVs: Android 11 on a 1 GB, 32-bit (`armeabi-v7a`) Hisense set. No native code, no
AndroidX, 52 KB, and nothing runs until you start it — except the floating orb, which you start and stop yourself.

- **Multi-step, not a chat toy.** The model gets nine tools (`open_app`, `list_apps`, `search_youtube`,
  `open_url`, `open_settings`, `media`, `volume`, `say`, `time`) and loops: call, read the result, decide the next
  step, then answer in one or two sentences. A running task shows each step in the transcript.
- **Voice from your phone.** Typing on a TV remote is the worst part of any TV app, so **Phone voice** opens a
  page on the TV's own address. Put the phone on the same Wi-Fi, open the address, type the four-digit code once,
  then use the microphone key on the phone's keyboard. The answer appears on the phone and on the TV.
- **It reads the answer aloud** (Android TTS, on by default, one switch in Settings).
- **It can float over another app.** **Phone voice → Float over TV** leaves a small circle at the top right
  while you watch YouTube or anything else. The phone page keeps working, the circle pulses while a task runs,
  and the answer is spoken and shown in a bubble. The circle never takes the remote from the app behind it.
  See [Floating orb](#floating-orb).
- **Model:** Gemini by default (`gemini-3.8-flash`, changeable), or **Cloudflare Workers AI** with your account
  ID and an API token. Both keys are stored on the TV. Conversation and tool results go to the provider you
  choose and nowhere else.

### What it cannot do

The TV is the limit, not the model: it cannot switch HDMI inputs or change channels, uninstall apps, press keys
inside another app, or control other devices. Ask it for those and it says so. Apps appear in `list_apps` only
if they have a TV launcher entry.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | The selected model API (Gemini or Cloudflare Workers AI), and the phone page served on the local network. |
| `SYSTEM_ALERT_WINDOW` | The floating orb, drawn over the app you are watching. Granted by the installer's `appops` line, or by hand in Settings. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the orb and the phone page alive while another app is in front. |

That is all. There is **no** `RECORD_AUDIO`: the phone's keyboard does the listening, so the TV never opens a
microphone. No location, no storage, no accessibility service, no ads, no telemetry. The keys are kept in the
app's private preferences with backups off, and sent only to the selected provider: Google's endpoint as the
`x-goog-api-key` header, or Cloudflare's as a Bearer token. The transcript lives in memory and is gone when the
app is closed; the provider, the keys, the model and the read-aloud switch are the only things written to disk.

The floating orb is the one thing that outlives the app screen: it starts only when you press **Float over TV**
and stops when you hide it from the phone page or the notification. Nothing starts on boot.

**The phone page** listens on port 8787 (or the next free port) only while the code is on screen or the floating
orb is up, stops when both are gone, accepts only private/LAN addresses, requires the four-digit code for every
task, settings change and orb action, and closes itself after ten wrong codes. The page and a read-only status
line (provider, models, whether a key is set, whether the orb floats) are reachable on your Wi-Fi; the keys,
the Cloudflare token and the account ID are not. It serves one HTML page and four endpoints; it never lists
files. Anyone on your Wi-Fi who can see the code could send the TV a task, so show it to people you would hand
the remote to.

The key is not encrypted at rest: it is in the app's private storage, which another app cannot read without root,
but it is not a hardware keystore. Use a key you can revoke.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/tv-agent && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/agent.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/agent.apk; rm -f /data/local/tmp/agent.apk'
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow'
```

The last line grants **Display over other apps** for the floating orb. The curl installer does it for you; if it
is refused, enable the same switch in Settings.

Then open **Agent** (or answer **Show** on its card), press **Phone voice**, and paste a Gemini API key from
[aistudio.google.com](https://aistudio.google.com/apikey) into the page, or choose Cloudflare and fill in the
two fields below. Without a key the app still opens, but it cannot answer.

### Cloudflare Workers AI

The phone page and **Settings** have a provider switch. Cloudflare needs an **account ID** and an **API token**
with Workers AI access; create the token at
[dash.cloudflare.com](https://dash.cloudflare.com/profile/api-tokens) with the `Workers AI` permission. The
default model is `@cf/zai-org/glm-4.7-flash`, chosen for fast tool calling; any Workers AI model that supports
function calling works in the model field. The app calls the OpenAI-compatible
`/accounts/<id>/ai/v1/chat/completions` endpoint directly, so no Worker or gateway of your own is needed.

The request sends assistant tool-call turns with `content: ""` rather than `null`, which is what Cloudflare's
validator accepts, and turns its `tool_calls` back into the loop's function calls.

### Floating orb

**Phone voice → Float over TV** hands the page's code and port to a foreground service, moves the app back and
leaves a small violet circle at the top right of the screen. Open YouTube or anything else: the phone page keeps
working, the circle turns while a task runs, and the answer is spoken and shown in a bubble for about 15 seconds.

The circle is deliberately **not focusable**, so the D-pad still controls the app behind it. Dragging it with a
pointer moves it anywhere; on a remote-only TV, the four arrow buttons on the phone page nudge it, and it comes
back where you left it. A tap from a pointer or air mouse opens the full app and closes the circle; opening
Agent another way leaves it floating. To stop it, use **Hide the floating orb** on the phone page or the
notification's **Hide** action.

It needs **Display over other apps**, which the installer grants with
`appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow`. Revoke it with the same line and `deny`. While the orb
is up the phone page is reachable on your Wi-Fi until you hide it, so hide it when you are done.

The unit tests are plain JVM tests (no TV needed):

```bash
./gradlew testReleaseUnitTest lintRelease
```

## Install with curl

`serve.py` serves the built APK and a matching installer on this computer, so the TV needs no checkout, no
`adb push`, and no USB cable:

```bash
python3 addons/tv-agent/serve.py
```

It prints the address to use, for example `http://192.168.1.50:8788`. On the TV, in a shell (an adb shell, or a
terminal app):

```bash
curl -fsSL http://<computer>:8788/install | sh
```

The same one-liner works over Tailscale, which is useful when the TV and the computer are not on the same
Wi-Fi: `serve.py` prints the `100.x` address too. It never uploads anything; it is a read-only server for four
fixed paths (`/install`, `/tv-agent.apk`, `/SHA256SUMS` and a small page at `/`).

The served installer is `install.sh` with the server address filled in. It downloads the APK, checks it against
`/SHA256SUMS` when a checksum tool is present, installs it with `pm install -r`, grants the floating orb's
overlay permission and opens it once. From the repo's own TV shell it is one command:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'curl -fsSL http://<computer>:8788/install | sh'
```

To download without installing, or to install by hand:

```bash
curl -fsSL http://<computer>:8788/tv-agent.apk -o /data/local/tmp/tv-agent.apk
pm install -r /data/local/tmp/tv-agent.apk
appops set com.macl.tvagent SYSTEM_ALERT_WINDOW allow
```

If the TV has no `curl`, `wget` or `toybox wget`, run the same URL from a computer with `adb` (or with
`TV_HOST` set inside this repo, which uses `tools/tv.py`); the installer detects where it is and picks the right
path. `--apk <path>` serves a build from somewhere else, and `--bind 127.0.0.1` keeps the server on this computer.

## Cost

Gemini Flash is billed per token by Google by default, not by this app. A plan-and-two-steps task is a few
thousand tokens, so it is fractions of a cent; the free tier of the API is enough to try it. Workers AI bills in
Neurons against a daily free allocation, and the flash models above are small enough that a task barely moves it.

## Notes for the next person

- The model loop is `AgentEngine`, the wording of the rules is `Prompt`, and the wire format is
  `GeminiProtocol`; all three are Android-free and covered by the tests in `app/src/test`.
- The function-calling history keeps the model's raw parts (including thought parts and signatures), which the
  API needs when a call is answered; only visible text reaches the screen.
- `AgentEngine` caps a task at 10 model turns and 14 tool calls. Missing responses for calls are still sent with
  a "step limit reached" result, so the conversation stays valid.
