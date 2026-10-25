package com.macl.tvagent;

import org.json.JSONObject;

/** What the agent can do to the TV. The device implementation lives in {@link DeviceTools}. */
public interface Toolbox {
    /** Runs one tool and returns a short plain-text result for the model. Never throws. */
    String run(String name, JSONObject args);
}
