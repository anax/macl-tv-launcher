package com.macl.tvagent;

import org.json.JSONArray;

/** The model boundary, small on purpose: the agent loop is tested with a fake model. */
public interface Llm {
    /**
     * Sends the whole conversation and returns one model turn.
     *
     * @param systemPrompt   the standing instructions
     * @param contents       user / model / functionResponse turns
     * @param declarations   tool declarations, empty to forbid tool calls
     */
    Turn send(String systemPrompt, JSONArray contents, JSONArray declarations) throws Exception;
}
