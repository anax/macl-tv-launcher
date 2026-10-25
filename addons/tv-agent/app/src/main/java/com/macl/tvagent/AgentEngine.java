package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The agent loop: the model plans, the tools act, the model sees each result and continues, and the last text is
 * the answer. Blocking on purpose; callers run it off the main thread.
 */
public final class AgentEngine {
    public interface Listener {
        /** A remark or plan from the model, shown as it arrives. */
        void onNote(String text);

        /** A tool call and its result, one line each. */
        void onStep(String text);

        /** The final answer. Called once on a successful run. */
        void onAnswer(String text);

        /** Something went wrong before an answer. */
        void onError(String message);
    }

    static final int MAX_ROUNDS = 10;
    static final int MAX_CALLS = 14;
    static final int MAX_HISTORY = 24;

    private final Llm llm;
    private final Toolbox tools;
    private final String systemPrompt;
    private final JSONArray contents = new JSONArray();

    public AgentEngine(Llm llm, Toolbox tools, String systemPrompt) {
        this.llm = llm;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
    }

    /** Runs one goal to an answer. Returns the answer, or null when the model failed. */
    public String run(String goal, Listener listener) {
        if (goal == null || goal.trim().isEmpty()) return null;
        trim();
        try {
            contents.put(userText(goal.trim()));
        } catch (JSONException e) {
            listener.onError(message(e));
            return null;
        }

        String finalText = null;
        int calls = 0;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            Turn turn;
            try {
                turn = llm.send(systemPrompt, contents, ToolSpecs.declarations());
            } catch (Exception e) {
                trim();
                listener.onError(message(e));
                return null;
            }
            if (turn == null || turn.parts.length() == 0) {
                trim();
                listener.onError("The model returned an empty answer.");
                return null;
            }
            try {
                contents.put(modelTurn(turn.parts));
            } catch (JSONException e) {
                listener.onError(message(e));
                return null;
            }
            if (!turn.text.isEmpty() && turn.wantsTools()) listener.onNote(turn.text);

            if (!turn.wantsTools()) {
                finalText = turn.text;
                break;
            }

            JSONArray responses = new JSONArray();
            for (Call call : turn.calls) {
                String result;
                if (calls >= MAX_CALLS) {
                    result = "error: step limit reached, this call was not run";
                } else {
                    calls++;
                    listener.onStep(describe(call));
                    result = runTool(call);
                    listener.onStep("   " + shorten(result));
                }
                try {
                    responses.put(functionResponse(call.name, result));
                } catch (JSONException e) {
                    listener.onError(message(e));
                    return null;
                }
            }
            try {
                contents.put(new JSONObject().put("role", "user").put("parts", responses));
            } catch (JSONException e) {
                listener.onError(message(e));
                return null;
            }
            if (calls >= MAX_CALLS) break;
        }

        if (finalText == null || finalText.isEmpty()) {
            // No tools this time: ask for one closing sentence, so a long task still ends in words.
            try {
                Turn closing = llm.send(systemPrompt, contents, new JSONArray());
                if (closing != null) {
                    if (closing.parts.length() > 0) contents.put(modelTurn(closing.parts));
                    if (!closing.text.isEmpty()) finalText = closing.text;
                }
            } catch (Exception e) {
                listener.onNote(message(e));
            }
        }

        trim();
        if (finalText == null || finalText.isEmpty()) finalText = "Stopped after too many steps.";
        listener.onAnswer(finalText);
        return finalText;
    }

    private String runTool(Call call) {
        try {
            String result = tools.run(call.name, call.args);
            return result == null ? "" : result;
        } catch (RuntimeException e) {
            // A broken tool is a result, not a crash: the model can try another route.
            return "error: " + message(e);
        }
    }

    private void trim() {
        while (contents.length() > MAX_HISTORY) contents.remove(0);
        while (contents.length() > 0 && !isTextUserTurn(contents.optJSONObject(0))) contents.remove(0);
    }

    private static boolean isTextUserTurn(JSONObject turn) {
        if (turn == null || !"user".equals(turn.optString("role"))) return false;
        JSONArray parts = turn.optJSONArray("parts");
        if (parts == null || parts.length() == 0) return false;
        JSONObject first = parts.optJSONObject(0);
        return first != null && first.has("text") && !first.has("functionResponse");
    }

    private static JSONObject userText(String text) throws JSONException {
        return new JSONObject().put("role", "user").put("parts",
                new JSONArray().put(new JSONObject().put("text", text)));
    }

    private static JSONObject modelTurn(JSONArray parts) throws JSONException {
        return new JSONObject().put("role", "model").put("parts", parts);
    }

    private static JSONObject functionResponse(String name, String result) throws JSONException {
        return new JSONObject().put("functionResponse", new JSONObject()
                .put("name", name)
                .put("response", new JSONObject().put("result", result)));
    }

    private static String describe(Call call) {
        String value = call.args.optString("query",
                call.args.optString("url",
                        call.args.optString("screen",
                                call.args.optString("command",
                                        call.args.optString("text",
                                                call.args.optString("level", ""))))));
        String line = call.name + (value.isEmpty() ? "" : " · " + value);
        return line.length() > 90 ? line.substring(0, 90) + "…" : line;
    }

    private static String shorten(String result) {
        String oneLine = result.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    private static String message(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
