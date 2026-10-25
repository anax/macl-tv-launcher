package com.macl.tvagent;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** The few things that survive a reboot: the provider, its credentials and model, and whether to read aloud. */
public final class Prefs {
    static final String GEMINI = "gemini";
    static final String CLOUDFLARE = "cloudflare";

    static final String DEFAULT_MODEL = "gemini-3.8-flash";
    static final String DEFAULT_CF_MODEL = "@cf/zai-org/glm-4.7-flash";

    private static final String FILE = "agent";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_API = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_CF_ACCOUNT = "cf_account";
    private static final String KEY_CF_TOKEN = "cf_token";
    private static final String KEY_CF_MODEL = "cf_model";
    private static final String KEY_SPEAK = "speak";
    private static final String KEY_ORB_X = "orb_x";
    private static final String KEY_ORB_Y = "orb_y";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String provider() {
        return CLOUDFLARE.equals(prefs.getString(KEY_PROVIDER, GEMINI)) ? CLOUDFLARE : GEMINI;
    }

    public boolean isCloudflare() {
        return CLOUDFLARE.equals(provider());
    }

    /** The Gemini key. The Cloudflare token has its own field so switching back does not lose either. */
    public String key() {
        return prefs.getString(KEY_API, "").trim();
    }

    public String model() {
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).trim();
        return model.isEmpty() ? DEFAULT_MODEL : model;
    }

    public String cfAccount() {
        return prefs.getString(KEY_CF_ACCOUNT, "").trim();
    }

    public String cfToken() {
        return prefs.getString(KEY_CF_TOKEN, "").trim();
    }

    public String cfModel() {
        String model = prefs.getString(KEY_CF_MODEL, DEFAULT_CF_MODEL).trim();
        return cleanCloudflareModel(model).isEmpty() ? DEFAULT_CF_MODEL : model;
    }

    /** The model the active provider will call, for the status line. */
    public String activeModel() {
        return isCloudflare() ? cfModel() : model();
    }

    public boolean hasKey() {
        if (isCloudflare()) return !cfAccount().isEmpty() && !cfToken().isEmpty();
        return !key().isEmpty();
    }

    public boolean speak() {
        return prefs.getBoolean(KEY_SPEAK, true);
    }

    /** Where the floating orb was left, in window coordinates; -1 means "top right". */
    public int orbX() {
        return prefs.getInt(KEY_ORB_X, -1);
    }

    public int orbY() {
        return prefs.getInt(KEY_ORB_Y, -1);
    }

    public void saveOrbPosition(int x, int y) {
        prefs.edit().putInt(KEY_ORB_X, x).putInt(KEY_ORB_Y, y).apply();
    }

    public void saveProvider(String provider) {
        prefs.edit().putString(KEY_PROVIDER, CLOUDFLARE.equals(provider) ? CLOUDFLARE : GEMINI).apply();
    }

    public void saveKey(String key) {
        prefs.edit().putString(KEY_API, key == null ? "" : key.trim()).apply();
    }

    public void saveModel(String model) {
        String clean = cleanModel(model);
        if (clean.isEmpty()) return;
        prefs.edit().putString(KEY_MODEL, clean).apply();
    }

    /** From Settings on the TV: an empty field keeps what is already stored. */
    public void saveCloudflare(String account, String token, String model) {
        SharedPreferences.Editor edit = prefs.edit();
        String cleanAccount = cleanAccount(account);
        String cleanModel = cleanCloudflareModel(model);
        if (!cleanAccount.isEmpty()) edit.putString(KEY_CF_ACCOUNT, cleanAccount);
        if (token != null && !token.trim().isEmpty()) edit.putString(KEY_CF_TOKEN, token.trim());
        if (!cleanModel.isEmpty()) edit.putString(KEY_CF_MODEL, cleanModel);
        edit.apply();
    }

    public void saveSpeak(boolean speak) {
        prefs.edit().putBoolean(KEY_SPEAK, speak).apply();
    }

    /** From the phone page: an empty field means "keep what is there". */
    public void saveConfig(String provider, String apiKey, String model,
                           String cfAccount, String cfToken, String cfModel) {
        SharedPreferences.Editor edit = prefs.edit();
        if (CLOUDFLARE.equals(provider) || GEMINI.equals(provider)) edit.putString(KEY_PROVIDER, provider);
        if (apiKey != null && !apiKey.trim().isEmpty()) edit.putString(KEY_API, apiKey.trim());
        String clean = cleanModel(model);
        if (!clean.isEmpty()) edit.putString(KEY_MODEL, clean);
        String cleanAccount = cleanAccount(cfAccount);
        if (!cleanAccount.isEmpty()) edit.putString(KEY_CF_ACCOUNT, cleanAccount);
        if (cfToken != null && !cfToken.trim().isEmpty()) edit.putString(KEY_CF_TOKEN, cfToken.trim());
        String cleanCf = cleanCloudflareModel(cfModel);
        if (!cleanCf.isEmpty()) edit.putString(KEY_CF_MODEL, cleanCf);
        edit.apply();
    }

    /** Gemini model IDs are letters, digits, dot, dash and underscore; "models/" is accepted and dropped. */
    public static String cleanModel(String model) {
        if (model == null) return "";
        String clean = model.trim();
        if (clean.startsWith("models/")) clean = clean.substring("models/".length());
        clean = clean.toLowerCase(Locale.ROOT);
        return clean.matches("[a-z0-9._-]{1,64}") ? clean : "";
    }

    /** Workers AI model IDs look like {@code @cf/zai-org/glm-4.7-flash}. */
    public static String cleanCloudflareModel(String model) {
        if (model == null) return "";
        String clean = model.trim();
        return clean.matches("[A-Za-z0-9@/._-]{1,120}") ? clean : "";
    }

    /** Cloudflare account ids are 32 hex characters; stay a little more generous than that. */
    public static String cleanAccount(String account) {
        if (account == null) return "";
        String clean = account.trim();
        return clean.matches("[A-Za-z0-9_-]{8,64}") ? clean : "";
    }
}
