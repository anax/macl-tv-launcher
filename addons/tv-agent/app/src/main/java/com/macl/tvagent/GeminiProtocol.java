package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Builds a generateContent request and reads the reply. Pure JSON: no Android, no network, testable on the JVM. */
public final class GeminiProtocol {
    private GeminiProtocol() {
    }

    public static JSONObject buildRequest(String systemPrompt, JSONArray contents, JSONArray declarations,
                                          int maxOutputTokens) throws JSONException {
        JSONObject root = new JSONObject()
                .put("systemInstruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
                // No temperature: Gemini 3 is tuned for its default, and lowering it invites loops.
                .put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", maxOutputTokens));
        if (declarations != null && declarations.length() > 0) {
            root.put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", declarations)));
            root.put("toolConfig", new JSONObject().put("functionCallingConfig",
                    new JSONObject().put("mode", "AUTO")));
        }
        return root;
    }

    /** Reads candidates[0]. Throws with the API's own message when the reply is an error or empty. */
    public static Turn parse(String body) throws JSONException, ApiException {
        JSONObject root = new JSONObject(body);
        if (root.has("error")) {
            JSONObject error = root.optJSONObject("error");
            String message = error != null ? error.optString("message") : "unknown error";
            throw new ApiException(message.isEmpty() ? "The model refused the request." : message);
        }
        JSONObject feedback = root.optJSONObject("promptFeedback");
        if (feedback != null && !feedback.optString("blockReason").isEmpty()) {
            throw new ApiException("The model blocked that request (" + feedback.optString("blockReason") + ").");
        }
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new ApiException("The model returned no answer.");
        }
        JSONObject candidate = candidates.optJSONObject(0);
        JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            String reason = candidate == null ? "" : candidate.optString("finishReason");
            throw new ApiException(reason.isEmpty() || "STOP".equals(reason)
                    ? "The model returned an empty answer."
                    : "The model stopped early (" + reason + ").");
        }

        StringBuilder visible = new StringBuilder();
        StringBuilder any = new StringBuilder();
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            JSONObject call = part.optJSONObject("functionCall");
            if (call != null) {
                String name = call.optString("name");
                if (!name.isEmpty()) calls.add(new Call(name, call.optJSONObject("args")));
                continue;
            }
            String text = part.optString("text", "");
            if (text.isEmpty()) continue;
            any.append(text);
            if (!part.optBoolean("thought", false)) visible.append(text);
        }
        String text = visible.length() > 0 ? visible.toString() : any.toString();
        return new Turn(text.trim(), parts, calls);
    }

    /** A readable one-line form of an error body, for the TV screen. */
    public static String describeError(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null && !error.optString("message").isEmpty()) return error.optString("message");
        } catch (JSONException ignored) {
            // Not JSON: fall through to the truncated body.
        }
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 240 ? oneLine.substring(0, 240) + "…" : oneLine;
    }

    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }
}
