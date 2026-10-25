package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The tool declarations sent to the model, and the names the loop will accept back. */
public final class ToolSpecs {
    public static final String OPEN_APP = "open_app";
    public static final String LIST_APPS = "list_apps";
    public static final String SEARCH_YOUTUBE = "search_youtube";
    public static final String OPEN_URL = "open_url";
    public static final String OPEN_SETTINGS = "open_settings";
    public static final String MEDIA = "media";
    public static final String VOLUME = "volume";
    public static final String SAY = "say";
    public static final String TIME = "time";

    private ToolSpecs() {
    }

    public static JSONArray declarations() {
        try {
            JSONArray out = new JSONArray();
            out.put(function(OPEN_APP,
                    "Open an app that is installed on this TV, by its name or its package.",
                    props(named("query", new JSONObject()
                            .put("type", "STRING")
                            .put("description", "App name as shown on the TV (\"YouTube\", \"Netflix\") or its package name."))),
                    "query"));
            out.put(function(LIST_APPS,
                    "List the apps installed on this TV with their package names.",
                    null));
            out.put(function(SEARCH_YOUTUBE,
                    "Search YouTube on the TV, in the YouTube app when it is installed.",
                    props(named("query", new JSONObject()
                            .put("type", "STRING")
                            .put("description", "What to search for."))),
                    "query"));
            out.put(function(OPEN_URL,
                    "Open an http or https page in the TV's browser.",
                    props(named("url", new JSONObject()
                            .put("type", "STRING")
                            .put("description", "A full http:// or https:// address."))),
                    "url"));
            out.put(function(OPEN_SETTINGS,
                    "Open one Settings page on the TV. Only these pages exist; never try another.",
                    props(named("screen", enumOf("The Settings page to open.",
                            "wifi", "bluetooth", "display", "sound", "apps", "storage", "about", "date", "home", "tv"))),
                    "screen"));
            out.put(function(MEDIA,
                    "Send a playback key to whatever is playing.",
                    props(named("command", enumOf("Which key to send.",
                            "play_pause", "play", "pause", "next", "previous", "stop"))),
                    "command"));
            out.put(function(VOLUME,
                    "Set the TV volume.",
                    props(named("level", new JSONObject()
                            .put("type", "INTEGER")
                            .put("description", "Volume from 0 (silent) to 100 (loudest)."))),
                    "level"));
            out.put(function(SAY,
                    "Speak one short line through the TV speakers, while a task is running.",
                    props(named("text", new JSONObject()
                            .put("type", "STRING")
                            .put("description", "At most one sentence, under 200 characters."))),
                    "text"));
            out.put(function(TIME,
                    "The TV's current date, time and time zone.",
                    null));
            return out;
        } catch (JSONException e) {
            throw new IllegalStateException("tool declarations", e);
        }
    }

    /** True for a name the loop knows how to run; anything else is refused by the device layer. */
    public static boolean isKnown(String name) {
        switch (name) {
            case OPEN_APP:
            case LIST_APPS:
            case SEARCH_YOUTUBE:
            case OPEN_URL:
            case OPEN_SETTINGS:
            case MEDIA:
            case VOLUME:
            case SAY:
            case TIME:
                return true;
            default:
                return false;
        }
    }

    private static JSONObject function(String name, String description, JSONObject parameters, String... required)
            throws JSONException {
        JSONObject fn = new JSONObject()
                .put("name", name)
                .put("description", description);
        if (parameters != null) {
            JSONArray names = new JSONArray();
            for (String key : required) names.put(key);
            if (names.length() > 0) parameters.put("required", names);
            fn.put("parameters", parameters);
        }
        return fn;
    }

    private static JSONObject named(String name, JSONObject schema) throws JSONException {
        return new JSONObject().put("name", name).put("schema", schema);
    }

    private static JSONObject props(JSONObject... entries) throws JSONException {
        JSONObject properties = new JSONObject();
        for (JSONObject entry : entries) {
            properties.put(entry.getString("name"), entry.getJSONObject("schema"));
        }
        return new JSONObject().put("type", "OBJECT").put("properties", properties);
    }

    private static JSONObject enumOf(String description, String... values) throws JSONException {
        JSONArray options = new JSONArray();
        for (String value : values) options.put(value);
        return new JSONObject()
                .put("type", "STRING")
                .put("description", description)
                .put("enum", options);
    }
}
