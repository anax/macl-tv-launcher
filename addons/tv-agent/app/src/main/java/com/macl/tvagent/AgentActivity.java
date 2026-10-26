package com.macl.tvagent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The whole app: a transcript, one input line, and the phone pairing page. The agent loop runs off the main
 * thread; this class only draws what it reports and turns a finished answer into speech.
 */
public final class AgentActivity extends Activity implements VoiceServer.Bridge {
    private static final int KIND_USER = 0;
    private static final int KIND_AGENT = 1;
    private static final int KIND_STEP = 2;
    private static final int KIND_ERROR = 3;
    private static final int MAX_VIEWS = 60;
    private static final long PHONE_WAIT_SECONDS = 180;

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private Prefs prefs;
    private DeviceTools tools;
    private AgentEngine engine;
    private TextToSpeech tts;
    private volatile boolean speechReady;
    private VoiceServer voice;

    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private TextView status;
    private View voicePanel;
    private TextView phoneWhere;
    private TextView phoneUrl;
    private TextView phoneCode;
    private Button phoneFloat;
    private Button phoneClose;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_agent);

        prefs = new Prefs(this);
        tools = new DeviceTools(this);
        transcript = findViewById(R.id.transcript);
        scroll = findViewById(R.id.scroll);
        input = findViewById(R.id.input);
        send = findViewById(R.id.send);
        status = findViewById(R.id.status);
        voicePanel = findViewById(R.id.voicePanel);
        phoneWhere = findViewById(R.id.phoneWhere);
        phoneUrl = findViewById(R.id.phoneUrl);
        phoneCode = findViewById(R.id.phoneCode);
        phoneFloat = findViewById(R.id.phoneFloat);
        phoneClose = findViewById(R.id.phoneClose);

        engine = buildEngine();

        send.setOnClickListener(v -> submit());
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND) {
                submit();
                return true;
            }
            return false;
        });
        findViewById(R.id.voice).setOnClickListener(v -> openVoice());
        findViewById(R.id.settings).setOnClickListener(v -> openSettings());
        phoneFloat.setOnClickListener(v -> floatOrb());
        phoneClose.setOnClickListener(v -> closeVoice());

        startSpeech();
        updateStatus();
        welcome();
        send.requestFocus();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String prompt = intent.getStringExtra("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = intent.getStringExtra("text");
        }
        if (prompt != null && !prompt.trim().isEmpty()) {
            final String query = prompt.trim();
            main.postDelayed(() -> startTask(query, null), 400);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tools.invalidateApps();
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (voicePanel.getVisibility() == View.VISIBLE) closeVoice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        closeVoice();
        work.shutdownNow();
        if (tts != null) tts.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (voicePanel.getVisibility() == View.VISIBLE) {
            closeVoice();
            return;
        }
        super.onBackPressed();
    }

    // ----- the conversation -------------------------------------------------------------------

    private void welcome() {
        append(getString(R.string.welcome_title) + "\n" + getString(R.string.welcome_body), KIND_AGENT);
        if (!prefs.hasKey()) append(getString(R.string.key_missing), KIND_STEP);
    }

    private void submit() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        if (busy.get()) {
            append(getString(R.string.busy), KIND_STEP);
            return;
        }
        input.setText("");
        startTask(text, null);
    }

    private void startTask(String text, PhoneCall phone) {
        if (!busy.compareAndSet(false, true)) {
            if (phone != null) phone.finish(null);
            return;
        }
        append(text, KIND_USER);
        setWorking(true);

        AgentEngine.Listener listener = new AgentEngine.Listener() {
            @Override
            public void onNote(String note) {
                post(() -> append(note, KIND_AGENT));
            }

            @Override
            public void onStep(String step) {
                post(() -> append(step, KIND_STEP));
            }

            @Override
            public void onAnswer(String answer) {
                writeResponseFile(answer, null);
                post(() -> {
                    append(answer, KIND_AGENT);
                    speak(answer);
                    finishTask();
                    if (phone != null) phone.finish(answer);
                });
            }

            @Override
            public void onError(String message) {
                writeResponseFile(null, message);
                post(() -> {
                    append(getString(R.string.error, message), KIND_ERROR);
                    finishTask();
                    if (phone != null) phone.finish(null);
                });
            }
        };

        work.execute(() -> {
            try {
                writeResponseFile(null, null);
                engine.run(text, listener);
            } catch (RuntimeException e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                writeResponseFile(null, message);
                listener.onError(message);
            }
        });
    }

    private void finishTask() {
        busy.set(false);
        setWorking(false);
    }

    private void writeResponseFile(String text, String error) {
        try {
            java.io.File file = new java.io.File("/data/local/tmp/zenith_response.txt");
            if (text == null && error == null) {
                file.delete();
                return;
            }
            java.io.FileWriter writer = new java.io.FileWriter(file);
            if (error != null) {
                writer.write("ERROR:" + error + "\n");
            } else {
                writer.write("ANSWER:" + text + "\n");
            }
            writer.close();
            file.setReadable(true, false);
        } catch (Exception ignored) {
        }
    }

    private void setWorking(boolean working) {
        send.setEnabled(!working);
        send.setAlpha(working ? 0.4f : 1f);
        if (working) {
            status.setText("Working…");
        } else {
            updateStatus();
        }
    }

    private void updateStatus() {
        String m = prefs.activeModel();
        if ("gemini-3.8-flash".equalsIgnoreCase(m) || "gemini-2.5-flash".equalsIgnoreCase(m)) {
            m = "Gemini 3.8 Flash Medium";
        } else if (m.contains("glm-4.7-flash")) {
            m = "GLM 4.7 Flash";
        }
        status.setText(m + "  ⌵");
    }

    private AgentEngine buildEngine() {
        Llm llm = prefs.isCloudflare() ? new CloudflareClient(prefs) : new GeminiClient(prefs);
        return new AgentEngine(llm, tools, Prompt.system(tools.appsSummary()));
    }

    private void append(String text, int kind) {
        if (text == null || text.isEmpty()) return;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(kind == KIND_STEP ? 13 : 16);
        view.setLineSpacing(dp(3), 1f);
        int padH = dp(16);
        int padV = dp(10);
        view.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        switch (kind) {
            case KIND_USER:
                view.setBackgroundResource(R.drawable.bubble_user);
                view.setTextColor(0xFFFFFFFF);
                params.gravity = android.view.Gravity.END;
                break;
            case KIND_STEP:
                view.setTypeface(Typeface.MONOSPACE);
                view.setTextColor(0xFF9CA3AF);
                params.setMarginStart(dp(6));
                break;
            case KIND_ERROR:
                view.setBackgroundResource(R.drawable.bubble_agent);
                view.setTextColor(0xFFFF6B6B);
                break;
            default:
                view.setBackgroundResource(R.drawable.bubble_agent);
                view.setTextColor(0xFFE5E7EB);
        }

        transcript.addView(view, params);
        while (transcript.getChildCount() > MAX_VIEWS) transcript.removeViewAt(0);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void post(Runnable runnable) {
        main.post(() -> {
            if (!isFinishing() && !isDestroyed()) runnable.run();
        });
    }

    // ----- speech ----------------------------------------------------------------------------

    private void startSpeech() {
        tts = new TextToSpeech(this, result -> {
            boolean ok = result == TextToSpeech.SUCCESS;
            if (ok) {
                int language = tts.setLanguage(Locale.getDefault());
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.ENGLISH);
                }
            }
            speechReady = ok;
            tools.attachSpeech(tts);
            tools.setSpeechReady(ok);
        });
    }

    private void speak(String text) {
        if (!prefs.speak()) return;
        TextToSpeech speaker = tts;
        String line = text == null ? "" : text.trim();
        if (speaker == null || !speechReady || line.isEmpty()) return;
        if (line.length() > 400) line = line.substring(0, 400);
        speaker.speak(line, TextToSpeech.QUEUE_FLUSH, null, "agent-answer");
    }

    // ----- phone voice ---------------------------------------------------------------------

    private void openVoice() {
        if (voice != null) {
            closeVoice();
            return;
        }
        VoiceServer server = new VoiceServer(this);
        try {
            String code = server.start();
            String url = server.url();
            boolean ready = url != null;
            if (url == null) {
                phoneWhere.setText(R.string.phone_no_wifi);
                phoneUrl.setText("");
                phoneCode.setText("");
            } else {
                phoneWhere.setText(R.string.phone_where);
                phoneUrl.setText(url);
                phoneCode.setText(code);
            }
            voice = server;
            voicePanel.setVisibility(View.VISIBLE);
            phoneFloat.setEnabled(ready);
            phoneFloat.setAlpha(ready ? 1f : 0.5f);
            if (ready) {
                phoneFloat.requestFocus();
            } else {
                phoneClose.requestFocus();
            }
        } catch (IOException e) {
            server.stop();
            append(getString(R.string.error, e.getMessage()), KIND_ERROR);
        }
    }

    /** Hands the page's code and port to the orb service, then gets out of the way. */
    private void floatOrb() {
        if (voice == null) return;
        String code = phoneCode.getText().toString();
        int port = voice.port();
        closeVoice();
        if (!Settings.canDrawOverlays(this)) {
            append(getString(R.string.orb_permission), KIND_STEP);
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException ignored) {
                // Some TVs hide that page; the installer's appops line is the fallback.
            }
            return;
        }
        Intent intent = new Intent(this, OrbService.class)
                .putExtra(OrbService.EXTRA_CODE, code)
                .putExtra(OrbService.EXTRA_PORT, port);
        try {
            startForegroundService(intent);
        } catch (RuntimeException e) {
            append(getString(R.string.error, e.getMessage()), KIND_ERROR);
            return;
        }
        append(getString(R.string.float_started), KIND_STEP);
        moveTaskToBack(true);
    }

    private void closeVoice() {
        VoiceServer server = voice;
        voice = null;
        if (server != null) server.stop();
        voicePanel.setVisibility(View.GONE);
        send.requestFocus();
    }

    @Override
    public boolean hasKey() {
        return prefs.hasKey();
    }

    @Override
    public String provider() {
        return prefs.provider();
    }

    @Override
    public String model() {
        return prefs.activeModel();
    }

    @Override
    public String geminiModel() {
        return prefs.model();
    }

    @Override
    public String cfModel() {
        return prefs.cfModel();
    }

    @Override
    public boolean busy() {
        return busy.get();
    }

    @Override
    public boolean floating() {
        return OrbService.isRunning();
    }

    @Override
    public void hideOrb() {
        stopService(new Intent(this, OrbService.class));
    }

    @Override
    public void moveOrb(int dx, int dy) {
        // The page may be served by the activity while the orb floats on the service's page.
        OrbService.moveBy(dx, dy);
    }

    @Override
    public String ask(String text) {
        PhoneCall call = new PhoneCall();
        main.post(() -> startTask(text, call));
        try {
            if (!call.done.await(PHONE_WAIT_SECONDS, TimeUnit.SECONDS)) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return call.answer;
    }

    @Override
    public void saveConfig(String provider, String apiKey, String model,
                           String cfAccount, String cfToken, String cfModel) {
        prefs.saveConfig(provider, apiKey, model, cfAccount, cfToken, cfModel);
        engine = buildEngine();
        post(this::updateStatus);
    }

    @Override
    public void onWrongCode() {
        post(() -> append(getString(R.string.wrong_code), KIND_STEP));
    }

    @Override
    public void onClosed() {
        post(() -> {
            closeVoice();
            append(getString(R.string.phone_closed), KIND_STEP);
        });
    }

    // ----- settings ----------------------------------------------------------------------------

    private void openSettings() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        Spinner provider = view.findViewById(R.id.provider);
        View geminiGroup = view.findViewById(R.id.geminiGroup);
        View cfGroup = view.findViewById(R.id.cfGroup);
        EditText key = view.findViewById(R.id.key);
        EditText model = view.findViewById(R.id.model);
        EditText cfAccount = view.findViewById(R.id.cfAccount);
        EditText cfToken = view.findViewById(R.id.cfToken);
        EditText cfModel = view.findViewById(R.id.cfModel);
        android.widget.CheckBox speak = view.findViewById(R.id.speak);

        String[] names = getResources().getStringArray(R.array.providers);
        String[] values = getResources().getStringArray(R.array.provider_values);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        provider.setAdapter(adapter);
        boolean savedCloudflare = prefs.isCloudflare();
        provider.setSelection(savedCloudflare ? 1 : 0);
        geminiGroup.setVisibility(savedCloudflare ? View.GONE : View.VISIBLE);
        cfGroup.setVisibility(savedCloudflare ? View.VISIBLE : View.GONE);
        // The first layout of a fresh spinner can ignore an early setSelection; repeat it once attached.
        provider.post(() -> provider.setSelection(savedCloudflare ? 1 : 0, false));
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                boolean cloudflare = position == 1;
                geminiGroup.setVisibility(cloudflare ? View.GONE : View.VISIBLE);
                cfGroup.setVisibility(cloudflare ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        key.setText(prefs.key());
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        model.setText(prefs.model());
        model.setInputType(InputType.TYPE_CLASS_TEXT);
        cfAccount.setText(prefs.cfAccount());
        cfAccount.setInputType(InputType.TYPE_CLASS_TEXT);
        // The token is never shown back; an empty field keeps the stored one.
        cfToken.setText("");
        cfToken.setHint(prefs.cfToken().isEmpty()
                ? R.string.settings_cf_token_hint : R.string.settings_cf_token_set);
        cfToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        cfModel.setText(prefs.cfModel());
        cfModel.setInputType(InputType.TYPE_CLASS_TEXT);
        speak.setChecked(prefs.speak());

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(view)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    prefs.saveProvider(values[provider.getSelectedItemPosition()]);
                    prefs.saveKey(key.getText().toString());
                    prefs.saveModel(model.getText().toString());
                    prefs.saveCloudflare(cfAccount.getText().toString(), cfToken.getText().toString(),
                            cfModel.getText().toString());
                    prefs.saveSpeak(speak.isChecked());
                    engine = buildEngine();
                    updateStatus();
                    append(getString(R.string.settings_saved), KIND_STEP);
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PhoneCall {
        final CountDownLatch done = new CountDownLatch(1);
        volatile String answer;

        void finish(String text) {
            answer = text;
            done.countDown();
        }
    }
}
