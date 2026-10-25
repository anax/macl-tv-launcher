package com.macl.tvagent;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What the tools actually do on the TV. Every method returns a short line the model can read. */
public final class DeviceTools implements Toolbox {
    private final Context ctx;
    private volatile TextToSpeech tts;
    private volatile boolean speechReady;
    private volatile List<App> apps;

    public DeviceTools(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public void attachSpeech(TextToSpeech engine) {
        this.tts = engine;
    }

    public void setSpeechReady(boolean ready) {
        this.speechReady = ready;
    }

    /** Called on resume: an app installed since the last look must be openable. */
    public void invalidateApps() {
        apps = null;
    }

    @Override
    public String run(String name, JSONObject args) {
        try {
            switch (name) {
                case ToolSpecs.OPEN_APP:
                    return openApp(args.optString("query"));
                case ToolSpecs.LIST_APPS:
                    return listApps();
                case ToolSpecs.SEARCH_YOUTUBE:
                    return searchYouTube(args.optString("query"));
                case ToolSpecs.OPEN_URL:
                    return openUrl(args.optString("url"));
                case ToolSpecs.OPEN_SETTINGS:
                    return openSettings(args.optString("screen"));
                case ToolSpecs.MEDIA:
                    return media(args.optString("command"));
                case ToolSpecs.VOLUME:
                    return volume(args.optInt("level", 20));
                case ToolSpecs.SAY:
                    return say(args.optString("text"));
                case ToolSpecs.TIME:
                    return time();
                default:
                    return "error: unknown tool " + name;
            }
        } catch (ActivityNotFoundException e) {
            return "error: nothing on this TV can do that";
        } catch (RuntimeException e) {
            return "error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** The app list for the standing prompt, short enough to send on every request. */
    public String appsSummary() {
        List<App> all = apps();
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (App app : all) {
            if (shown == 40) {
                out.append(", and ").append(all.size() - shown).append(" more");
                break;
            }
            if (shown > 0) out.append(", ");
            out.append(app.label).append(" (").append(app.pkg).append(')');
            shown++;
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private String openApp(String query) {
        String asked = query == null ? "" : query.trim();
        if (asked.isEmpty()) return "error: no app name given";
        List<App> all = apps();
        String needle = fold(asked);

        App exact = null;
        for (App app : all) {
            if (app.pkg.equalsIgnoreCase(asked) || fold(app.label).equals(needle)) {
                exact = app;
                break;
            }
        }
        if (exact == null) {
            List<App> partial = new ArrayList<>();
            for (App app : all) {
                if (fold(app.label).contains(needle) || app.pkg.toLowerCase(Locale.ROOT).contains(needle)) {
                    partial.add(app);
                }
            }
            if (partial.size() == 1) {
                exact = partial.get(0);
            } else if (partial.size() > 1) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < partial.size() && i < 4; i++) {
                    if (i > 0) names.append(", ");
                    names.append(partial.get(i).label);
                }
                return "error: more than one app matches \"" + asked + "\": " + names + ". Which one?";
            }
        }
        if (exact == null) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < all.size() && i < 12; i++) {
                if (i > 0) names.append(", ");
                names.append(all.get(i).label);
            }
            return "error: no app called \"" + asked + "\". Apps here include: " + names;
        }

        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage(exact.pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ctx.getPackageManager().resolveActivity(launch, 0) == null) {
            launch = ctx.getPackageManager().getLaunchIntentForPackage(exact.pkg);
            if (launch == null) return "error: " + exact.label + " cannot be opened";
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ctx.startActivity(launch);
        sleep(400);
        return "opened " + exact.label;
    }

    private String listApps() {
        JSONArray out = new JSONArray();
        for (App app : apps()) {
            if (out.length() == 80) break;
            try {
                out.put(new JSONObject().put("app", app.label).put("package", app.pkg));
            } catch (JSONException ignored) {
                // A name that cannot be encoded is skipped; the rest of the list still helps.
            }
        }
        return out.toString();
    }

    private String searchYouTube(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return "error: no search text";
        Intent deep = new Intent(Intent.ACTION_VIEW,
                Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(q)))
                .setPackage("com.google.android.youtube.tv")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(deep);
            sleep(400);
            return "searched YouTube for " + q;
        } catch (ActivityNotFoundException e) {
            openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(q));
            return "opened a YouTube search for " + q + " in the browser";
        }
    }

    private String openUrl(String url) {
        String address = url == null ? "" : url.trim();
        Uri uri = Uri.parse(address);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "error: only http and https links can be opened";
        }
        ctx.startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        sleep(400);
        return "opened " + address;
    }

    private String openSettings(String screen) {
        String page = screen == null ? "" : screen.trim().toLowerCase(Locale.ROOT);
        String action;
        switch (page) {
            case "wifi":
                action = Settings.ACTION_WIFI_SETTINGS;
                break;
            case "bluetooth":
                action = Settings.ACTION_BLUETOOTH_SETTINGS;
                break;
            case "display":
                action = Settings.ACTION_DISPLAY_SETTINGS;
                break;
            case "sound":
                action = Settings.ACTION_SOUND_SETTINGS;
                break;
            case "apps":
                action = Settings.ACTION_APPLICATION_SETTINGS;
                break;
            case "storage":
                action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
                break;
            case "about":
                action = Settings.ACTION_DEVICE_INFO_SETTINGS;
                break;
            case "date":
                action = Settings.ACTION_DATE_SETTINGS;
                break;
            case "home":
                action = Settings.ACTION_HOME_SETTINGS;
                break;
            case "tv":
                action = Settings.ACTION_SETTINGS;
                break;
            default:
                return "error: \"" + screen + "\" is not a page this TV app opens. Use wifi, bluetooth, display, "
                        + "sound, apps, storage, about, date, home or tv.";
        }
        ctx.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        sleep(400);
        return "opened the " + page + " settings";
    }

    private String media(String command) {
        String key = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        int code;
        switch (key) {
            case "play_pause":
                code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                break;
            case "play":
                code = KeyEvent.KEYCODE_MEDIA_PLAY;
                break;
            case "pause":
                code = KeyEvent.KEYCODE_MEDIA_PAUSE;
                break;
            case "next":
                code = KeyEvent.KEYCODE_MEDIA_NEXT;
                break;
            case "previous":
                code = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                break;
            case "stop":
                code = KeyEvent.KEYCODE_MEDIA_STOP;
                break;
            default:
                return "error: unknown media command " + command;
        }
        AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return "error: no audio service";
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
        return "sent " + key.replace('_', ' ');
    }

    private String volume(int level) {
        AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return "error: no audio service";
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int wanted = Math.max(0, Math.min(100, level));
        int step = Math.round(wanted * max / 100f);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0);
        return "volume is now " + Math.round(step * 100f / max) + "%";
    }

    private String say(String text) {
        String line = text == null ? "" : text.trim();
        if (line.isEmpty()) return "error: nothing to say";
        if (line.length() > 300) line = line.substring(0, 300);
        TextToSpeech engine = tts;
        if (engine == null || !speechReady) return "error: speech is not ready on this TV";
        engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "agent-step");
        return "spoken";
    }

    private String time() {
        ZonedDateTime now = ZonedDateTime.now();
        String date = now.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT));
        return date + " (" + now.getZone().getId() + ")";
    }

    private List<App> apps() {
        List<App> cached = apps;
        if (cached != null) return cached;
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> found = new LinkedHashMap<>();
        collect(pm, found, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(pm, found, Intent.CATEGORY_LAUNCHER);
        List<App> list = new ArrayList<>(found.values());
        list.sort(Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        apps = list;
        return list;
    }

    private void collect(PackageManager pm, Map<String, App> into, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(ctx.getPackageName())) continue;
            String label = info.loadLabel(pm) == null ? pkg : info.loadLabel(pm).toString().trim();
            if (label.isEmpty()) label = pkg;
            if (!into.containsKey(pkg)) into.put(pkg, new App(pkg, label));
        }
    }

    private static String fold(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class App {
        final String pkg;
        final String label;

        App(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }
}
