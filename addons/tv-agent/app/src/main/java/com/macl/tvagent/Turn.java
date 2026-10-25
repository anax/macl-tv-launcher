package com.macl.tvagent;

import org.json.JSONArray;

import java.util.List;

/** One model turn: what it said, the parts to keep in the history, and the calls it wants run. */
public final class Turn {
    public final String text;
    public final JSONArray parts;
    public final List<Call> calls;

    public Turn(String text, JSONArray parts, List<Call> calls) {
        this.text = text == null ? "" : text;
        this.parts = parts == null ? new JSONArray() : parts;
        this.calls = calls;
    }

    public boolean wantsTools() {
        return calls != null && !calls.isEmpty();
    }
}
