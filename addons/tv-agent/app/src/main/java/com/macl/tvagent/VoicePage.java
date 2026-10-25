package com.macl.tvagent;

/** The page the phone opens while Phone voice is showing on the TV. One file, no external resource. */
public final class VoicePage {
    private VoicePage() {
    }

    public static String html() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                <title>TV Agent</title>
                <style>
                  :root { color-scheme: dark; }
                  * { box-sizing: border-box; }
                  body { margin: 0; background: #0b0f14; color: #e6ebf2;
                         font: 17px/1.45 -apple-system, system-ui, "Segoe UI", Roboto, sans-serif; }
                  main { max-width: 560px; margin: 0 auto; padding: 22px 20px 48px; }
                  h1 { margin: 6px 0 2px; font-size: 26px; letter-spacing: .14em; color: #9c86ff; text-transform: uppercase; }
                  .muted { color: #8a97a8; }
                  .small { font-size: 14px; }
                  section { background: #121821; border: 1px solid #232e3c; border-radius: 16px;
                            padding: 16px; margin: 16px 0; }
                  label { display: block; font-size: 13px; letter-spacing: .12em; text-transform: uppercase;
                          color: #8a97a8; margin: 2px 0 8px; }
                  input, textarea, select { width: 100%; background: #0b0f14; color: #fff; border: 1px solid #2c3948;
                                    border-radius: 12px; padding: 14px; font: inherit; }
                  input:focus, textarea:focus, select:focus { outline: none; border-color: #7c5cff; }
                  #code { font: 28px/1.2 ui-monospace, monospace; letter-spacing: .3em; text-align: center; }
                  textarea { resize: vertical; min-height: 84px; }
                  button { width: 100%; margin-top: 12px; padding: 15px 18px; border: 0; border-radius: 12px;
                           background: #7c5cff; color: #fff; font: 600 17px/1 inherit; }
                  button:disabled { opacity: .5; }
                  .row { display: flex; gap: 10px; }
                  .move { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
                  .move button { margin-top: 0; padding: 12px 0; font-size: 20px; }
                  .out { background: #17142c; border: 1px solid #3a2f6b; border-radius: 14px; padding: 16px;
                         font-size: 18px; white-space: pre-wrap; }
                  .out.bad { border-color: #7a3340; background: #241318; }
                  summary { cursor: pointer; color: #b3becc; font-weight: 600; }
                  details[open] summary { margin-bottom: 14px; }
                  details input + label, details select + label { margin-top: 12px; }
                  details p { margin: 8px 0 0; }
                </style>
                </head>
                <body>
                <main>
                  <h1>TV Agent</h1>
                  <p id="state" class="muted">Connecting to the TV…</p>

                  <section>
                    <label for="code">Pairing code from the TV</label>
                    <input id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="4" placeholder="1234">
                  </section>

                  <section>
                    <label for="text">What should the TV do?</label>
                    <textarea id="text" rows="3" autocapitalize="sentences"
                      placeholder="Movie night: open YouTube and search for trains"></textarea>
                    <div class="row"><button id="send" type="button">Send to TV</button></div>
                    <p class="muted small">Voice: tap the microphone key on your phone's keyboard, then speak.</p>
                  </section>

                  <div id="out" class="out" hidden></div>

                  <section id="orbBox" hidden>
                    <label>Floating over the TV</label>
                    <div class="move">
                      <button id="orbUp" type="button" aria-label="Move the orb up">↑</button>
                      <button id="orbLeft" type="button" aria-label="Move the orb left">←</button>
                      <button id="orbRight" type="button" aria-label="Move the orb right">→</button>
                      <button id="orbDown" type="button" aria-label="Move the orb down">↓</button>
                    </div>
                    <p class="muted small">Nudge the circle, or drag it with a pointer. The page closes when you hide it.</p>
                    <button id="hideOrb" type="button">Hide the floating orb</button>
                  </section>

                  <details id="keybox">
                    <summary>Model, key and provider</summary>
                    <label for="provider">Provider</label>
                    <select id="provider">
                      <option value="gemini">Gemini</option>
                      <option value="cloudflare">Cloudflare Workers AI</option>
                    </select>

                    <div id="geminiBox">
                      <label for="apikey">Gemini API key</label>
                      <input id="apikey" type="password" autocomplete="off" placeholder="AIza…">
                      <label for="model">Gemini model</label>
                      <input id="model" type="text" autocomplete="off">
                      <p class="muted small">The key stays on the TV. Make one at aistudio.google.com.</p>
                    </div>

                    <div id="cfBox" hidden>
                      <label for="cfAccount">Cloudflare account ID</label>
                      <input id="cfAccount" type="text" autocomplete="off" placeholder="Saved on the TV; leave blank to keep it">
                      <label for="cfToken">Cloudflare API token</label>
                      <input id="cfToken" type="password" autocomplete="off" placeholder="Workers AI access">
                      <label for="cfModel">Cloudflare model</label>
                      <input id="cfModel" type="text" autocomplete="off" placeholder="@cf/zai-org/glm-4.7-flash">
                      <p class="muted small">A token with Workers AI access, from dash.cloudflare.com. The model above is the fast tool-calling one.</p>
                    </div>

                    <div class="row"><button id="save" type="button">Save</button></div>
                  </details>
                </main>
                <script>
                  const $ = (id) => document.getElementById(id);
                  const store = localStorage;
                  $('code').value = store.getItem('agentCode') || '';

                  function providerName(p) {
                    return p === 'cloudflare' ? 'Cloudflare' : 'Gemini';
                  }

                  function showProvider() {
                    const cloudflare = $('provider').value === 'cloudflare';
                    $('geminiBox').hidden = cloudflare;
                    $('cfBox').hidden = !cloudflare;
                    $('save').textContent = cloudflare ? 'Save Cloudflare settings' : 'Save Gemini key';
                  }
                  $('provider').onchange = showProvider;

                  async function refresh() {
                    try {
                      const r = await fetch('/state');
                      const j = await r.json();
                      $('provider').value = j.provider || 'gemini';
                      $('model').value = j.geminiModel || '';
                      $('cfModel').value = j.cfModel || '';
                      $('state').textContent = j.hasKey
                        ? providerName(j.provider) + ' · ' + j.model + ' · ready' + (j.floating ? ' · floating' : '')
                        : 'No key yet for ' + providerName(j.provider) + ' — open the settings below.';
                      $('orbBox').hidden = !j.floating;
                      if (!j.hasKey) $('keybox').open = true;
                      showProvider();
                    } catch (e) {
                      $('state').textContent = 'Cannot reach the TV. Same Wi-Fi?';
                    }
                  }
                  refresh();
                  window.addEventListener('focus', refresh);
                  document.addEventListener('visibilitychange', () => {
                    if (!document.hidden) refresh();
                  });

                  function code() {
                    const c = $('code').value.trim();
                    store.setItem('agentCode', c);
                    return c;
                  }

                  $('send').onclick = async () => {
                    const text = $('text').value.trim();
                    if (!text) { $('text').focus(); return; }
                    const box = $('out');
                    box.hidden = false;
                    box.className = 'out';
                    box.textContent = 'Working on the TV…';
                    $('send').disabled = true;
                    try {
                      const body = new URLSearchParams({ code: code(), text });
                      const r = await fetch('/ask', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body
                      });
                      const j = await r.json();
                      if (j.ok) {
                        box.textContent = j.answer || 'Done.';
                        $('text').value = '';
                        $('orbBox').hidden = !j.floating;
                      } else {
                        box.className = 'out bad';
                        box.textContent = j.error || 'No answer.';
                      }
                    } catch (e) {
                      box.className = 'out bad';
                      box.textContent = 'Lost the TV. Check the code and the Wi-Fi.';
                    } finally {
                      $('send').disabled = false;
                    }
                  };

                  $('save').onclick = async () => {
                    const box = $('out');
                    box.hidden = false;
                    box.className = 'out';
                    box.textContent = 'Saving…';
                    try {
                      const body = new URLSearchParams({
                        code: code(),
                        provider: $('provider').value,
                        apiKey: $('apikey').value.trim(),
                        model: $('model').value.trim(),
                        cfAccount: $('cfAccount').value.trim(),
                        cfToken: $('cfToken').value.trim(),
                        cfModel: $('cfModel').value.trim()
                      });
                      const r = await fetch('/config', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body
                      });
                      const j = await r.json();
                      box.textContent = j.ok ? 'Saved on the TV.' : (j.error || 'Not saved.');
                      $('apikey').value = '';
                      $('cfToken').value = '';
                      refresh();
                    } catch (e) {
                      box.className = 'out bad';
                      box.textContent = 'Lost the TV. Check the code and the Wi-Fi.';
                    }
                  };

                  async function orbAction(action) {
                    const box = $('out');
                    box.hidden = false;
                    box.className = 'out';
                    box.textContent = action === 'hide' ? 'Hiding…' : 'Moving…';
                    try {
                      const body = new URLSearchParams({ code: code(), action });
                      const r = await fetch('/orb', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body
                      });
                      const j = await r.json();
                      if (!j.ok) {
                        box.className = 'out bad';
                        box.textContent = j.error || 'Not moved.';
                        return;
                      }
                      if (action === 'hide') {
                        box.textContent = 'Orb hidden. Reopen Agent on the TV to float again.';
                        $('orbBox').hidden = true;
                      } else {
                        box.hidden = true;
                      }
                    } catch (e) {
                      box.className = 'out bad';
                      box.textContent = action === 'hide'
                        ? 'Hid the orb. The page is closing.'
                        : 'Lost the TV. Check the code and the Wi-Fi.';
                    }
                  }

                  $('orbUp').onclick = () => orbAction('up');
                  $('orbLeft').onclick = () => orbAction('left');
                  $('orbRight').onclick = () => orbAction('right');
                  $('orbDown').onclick = () => orbAction('down');
                  $('hideOrb').onclick = () => orbAction('hide');
                </script>
                </body>
                </html>
                """;
    }
}
