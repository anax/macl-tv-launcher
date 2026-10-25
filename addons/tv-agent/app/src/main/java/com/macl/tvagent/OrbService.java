package com.macl.tvagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The floating circle. It keeps the phone page alive while the TV shows another app, runs a task from the
 * phone, speaks the answer and shows it in a small bubble. It is not focusable: the remote stays with the
 * app you are watching. A pointer can tap the circle to open the full app.
 */
public final class OrbService extends Service {
    static final String EXTRA_CODE = "code";
    static final String EXTRA_PORT = "port";

    private static final String ACTION_STOP = "com.macl.tvagent.action.STOP_ORB";
    private static final String CHANNEL = "orb";
    private static final int NOTIFICATION_ID = 7;
    private static final String TAG = "AgentOrb";
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static volatile boolean running;
    private static volatile OrbService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private DeviceTools tools;
    private volatile AgentEngine engine;
    private TextToSpeech tts;
    private volatile boolean speechReady;
    private VoiceServer server;
    private WindowManager windows;
    private View orbRoot;
    private View notchDot;
    private TextView notchTitle;
    private TextView notchStep;
    private TextView notchBadge;
    private OrbView orb;
    private TextView bubble;
    private Runnable hideBubble;
    private boolean foreground;
    private int screenW;
    private int screenH;

    public static boolean isRunning() {
        return running;
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    /** Moves the orb from the full app's phone page, when the orb is the one serving it. */
    static void moveBy(int dx, int dy) {
        OrbService service = instance;
        if (service != null) service.moveOrb(dx, dy);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        instance = this;
        prefs = new Prefs(this);
        tools = new DeviceTools(this);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startSpeech();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        if (intent != null) {
            String prompt = intent.getStringExtra("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                prompt = intent.getStringExtra("text");
            }
            if (prompt != null && !prompt.trim().isEmpty()) {
                final String query = prompt.trim();
                new Thread(() -> ask(query)).start();
            }
        }
        if (server != null) return START_NOT_STICKY;

        String code = intent == null ? null : intent.getStringExtra(EXTRA_CODE);
        int port = intent == null ? 0 : intent.getIntExtra(EXTRA_PORT, 0);
        if (!showOrb()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        server = new VoiceServer(new OrbBridge());
        try {
            server.start(code, port);
            Log.d(TAG, "phone page moved to the orb on port " + server.port());
        } catch (IOException e) {
            showBubble(getString(R.string.orb_error, message(e)), true);
            main.postDelayed(this::stopSelf, 6000);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        BUSY.set(false);
        if (instance == this) instance = null;
        Log.d(TAG, "onDestroy, overlay was " + (orbRoot != null ? "shown" : "gone"));
        if (server != null) {
            server.stop();
            server = null;
        }
        if (hideBubble != null) main.removeCallbacks(hideBubble);
        if (orbRoot != null) {
            try {
                windows.removeView(orbRoot);
            } catch (RuntimeException ignored) {
                // The window may already be gone with the service.
            }
            orbRoot = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ----- the circle --------------------------------------------------------------------------

    private boolean showOrb() {
        if (orbRoot != null) return true;
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "no overlay permission");
            Toast.makeText(this, R.string.orb_permission, Toast.LENGTH_LONG).show();
            return false;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        screenW = metrics.widthPixels;
        screenH = metrics.heightPixels;

        View notch = LayoutInflater.from(this).inflate(R.layout.view_agent_notch, null);
        notchDot = notch.findViewById(R.id.notchDot);
        notchTitle = notch.findViewById(R.id.notchTitle);
        notchStep = notch.findViewById(R.id.notchStep);
        notchBadge = notch.findViewById(R.id.notchBadge);
        notch.setOnClickListener(v -> openApp());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = 0;
        try {
            windows.addView(notch, params);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not add the overlay", e);
            Toast.makeText(this, R.string.orb_permission, Toast.LENGTH_LONG).show();
            return false;
        }
        orbRoot = notch;
        Log.d(TAG, "notch overlay added at top center");
        return true;
    }

    /** Pointer drag: move the circle anywhere, and remember where it was left. */
    private void attachDrag(WindowManager.LayoutParams params) {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        orb.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                        if (moved) {
                            params.x = clampX(startX - Math.round(dx));
                            params.y = clampY(startY + Math.round(dy));
                            windows.updateViewLayout(orbRoot, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            prefs.saveOrbPosition(params.x, params.y);
                        } else {
                            orb.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (moved) prefs.saveOrbPosition(params.x, params.y);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** Nudge from the phone page; dx is rightwards, dy is downwards. */
    private void moveOrb(int dx, int dy) {
        main.post(() -> {
            if (orbRoot == null) return;
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) orbRoot.getLayoutParams();
            if (params == null) return;
            params.x = clampX(params.x - dx * dp(48));
            params.y = clampY(params.y + dy * dp(48));
            windows.updateViewLayout(orbRoot, params);
            prefs.saveOrbPosition(params.x, params.y);
        });
    }

    private int clampX(int x) {
        return Math.max(0, Math.min(x, Math.max(0, screenW - viewWidth())));
    }

    private int clampY(int y) {
        return Math.max(0, Math.min(y, Math.max(0, screenH - viewHeight())));
    }

    private int viewWidth() {
        return orbRoot != null && orbRoot.getWidth() > 0 ? orbRoot.getWidth() : dp(76);
    }

    private int viewHeight() {
        return orbRoot != null && orbRoot.getHeight() > 0 ? orbRoot.getHeight() : dp(64);
    }

    private void openApp() {
        if (BUSY.get()) {
            showBubble(getString(R.string.busy), false);
            return;
        }
        Intent intent = new Intent(this, AgentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        stopSelf();
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // Background activity starts are allowed with the overlay permission; if not, Home still works.
        }
    }

    private void showBubble(String text, boolean error) {
        main.post(() -> {
            if (bubble == null || text == null || text.isEmpty()) return;
            bubble.setText(text);
            bubble.setTextColor(error ? 0xFFFF9AA9 : 0xFFE6EBF2);
            bubble.setVisibility(View.VISIBLE);
            if (hideBubble != null) main.removeCallbacks(hideBubble);
            hideBubble = () -> {
                if (bubble != null) bubble.setVisibility(View.GONE);
            };
            main.postDelayed(hideBubble, 15_000);
        });
    }

    private void setOrbWorking(boolean working) {
        main.post(() -> {
            if (orb != null) orb.setWorking(working);
        });
    }

    private GradientDrawable bubbleBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF217142C);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), 0xFF3A2F6B);
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ----- the task from the phone --------------------------------------------------------------

    private String ask(String text) {
        if (!BUSY.compareAndSet(false, true)) return null;
        setNotchState("🧠 Planning...", 0xFF00B2E3);
        try {
            final String[] answer = {null};
            engine().run(text, new AgentEngine.Listener() {
                @Override
                public void onNote(String note) {
                    setNotchState("🧠 " + note, 0xFF00B2E3);
                }

                @Override
                public void onStep(String step) {
                    setNotchState("⚙️ " + step, 0xFFFFC629);
                }

                @Override
                public void onAnswer(String text) {
                    answer[0] = text;
                    setNotchState("✓ " + text, 0xFF10B981);
                    speak(text);
                    main.postDelayed(() -> setNotchState("Ready", 0xFF00B2E3), 6000);
                }

                @Override
                public void onError(String message) {
                    setNotchState("✗ " + message, 0xFFFF5252);
                    main.postDelayed(() -> setNotchState("Ready", 0xFF00B2E3), 6000);
                }
            });
            return answer[0];
        } catch (RuntimeException e) {
            setNotchState("✗ " + message(e), 0xFFFF5252);
            return null;
        } finally {
            BUSY.set(false);
        }
    }

    private void setNotchState(String text, int color) {
        main.post(() -> {
            if (notchStep != null) {
                notchStep.setText(text);
                notchStep.setTextColor(color);
            }
            if (notchDot != null) {
                notchDot.setBackgroundColor(color);
            }
        });
    }

    private synchronized AgentEngine engine() {
        if (engine == null) {
            Llm llm = prefs.isCloudflare() ? new CloudflareClient(prefs) : new GeminiClient(prefs);
            engine = new AgentEngine(llm, tools, Prompt.system(tools.appsSummary()));
        }
        return engine;
    }

    // ----- speech ------------------------------------------------------------------------------

    private void startSpeech() {
        tts = new TextToSpeech(this, status -> {
            TextToSpeech speaker = tts;
            boolean ok = status == TextToSpeech.SUCCESS && speaker != null;
            if (ok) {
                int language = speaker.setLanguage(Locale.getDefault());
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    speaker.setLanguage(Locale.ENGLISH);
                }
            }
            speechReady = ok;
            if (speaker != null) {
                tools.attachSpeech(speaker);
                tools.setSpeechReady(ok);
            }
        });
    }

    private void speak(String text) {
        if (!prefs.speak()) return;
        TextToSpeech speaker = tts;
        String line = text == null ? "" : text.trim();
        if (speaker == null || !speechReady || line.isEmpty()) return;
        if (line.length() > 400) line = line.substring(0, 400);
        speaker.speak(line, TextToSpeech.QUEUE_FLUSH, null, "agent-orb");
    }

    // ----- the notification required by a foreground service -----------------------------------

    private void startInForeground() {
        if (foreground) return;
        Notification notification = notification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foreground = true;
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, getString(R.string.orb_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, AgentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OrbService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_orb),
                getString(R.string.orb_stop), stop).build();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_orb)
                .setContentTitle(getString(R.string.orb_notification_title))
                .setContentText(getString(R.string.orb_notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(action)
                .build();
    }

    private static String message(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    /** The phone page talks to the service while the orb is up. */
    private final class OrbBridge implements VoiceServer.Bridge {
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
            return BUSY.get();
        }

        @Override
        public boolean floating() {
            return true;
        }

        @Override
        public void hideOrb() {
            main.post(OrbService.this::stopSelf);
        }

        @Override
        public void moveOrb(int dx, int dy) {
            OrbService.this.moveOrb(dx, dy);
        }

        @Override
        public String ask(String text) {
            return OrbService.this.ask(text);
        }

        @Override
        public void saveConfig(String provider, String apiKey, String model,
                               String cfAccount, String cfToken, String cfModel) {
            prefs.saveConfig(provider, apiKey, model, cfAccount, cfToken, cfModel);
            engine = null;
        }

        @Override
        public void onWrongCode() {
            // The page already says which digits are wrong.
        }

        @Override
        public void onClosed() {
            main.post(OrbService.this::stopSelf);
        }
    }
}
