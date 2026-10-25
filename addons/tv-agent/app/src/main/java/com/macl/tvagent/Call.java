package com.macl.tvagent;

import org.json.JSONObject;

/** One tool call the model asked for. */
public final class Call {
    public final String name;
    public final JSONObject args;

    public Call(String name, JSONObject args) {
        this.name = name;
        this.args = args == null ? new JSONObject() : args;
    }
}
