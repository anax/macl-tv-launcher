package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Builds an OpenAI-compatible chat request for Cloudflare Workers AI and reads the reply. Pure JSON:
 * no Android, no network, testable on the JVM.
 *
 * <p>The agent loop speaks Gemini's contents/functionCall shape, so this class translates both ways:
 * tool calls become OpenAI {@code tool_calls} with matching ids, and tool results become {@code tool}
 * messages. Assistant tool-call turns always carry a string {@code content} (Cloudflare's validator
 * rejects {@code null}), which is the one difference every OpenAI-Compatibility client has to get right.
 */
public final class CloudflareProtocol {
    private CloudflareProtocol() {
    }

    public static JSONObject buildRequest(String systemPrompt, JSONArray contents, JSONArray declarations,
                                          int maxOutputTokens, String model) throws JSONException {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        }

        List<String> pendingCalls = new ArrayList<>();
        int nextId = 0;
        for (int i = 0; i < contents.length(); i++) {
            JSONObject turn = contents.optJSONObject(i);
            JSONArray parts = turn == null ? null : turn.optJSONArray("parts");
            if (parts == null) continue;

            if ("model".equals(turn.optString("role"))) {
                StringBuilder text = new StringBuilder();
                JSONArray calls = new JSONArray();
                for (int p = 0; p < parts.length(); p++) {
                    JSONObject part = parts.optJSONObject(p);
                    if (part == null) continue;
                    JSONObject call = part.optJSONObject("functionCall");
                    if (call != null) {
                        String name = call.optString("name");
                        if (name.isEmpty()) continue;
                        String id = "call_" + (nextId++);
                        pendingCalls.add(id);
                        calls.put(new JSONObject()
                                .put("id", id)
                                .put("type", "function")
                                .put("function", new JSONObject()
                                        .put("name", name)
                                        .put("arguments", arguments(call.optJSONObject("args")).toString())));
                        continue;
                    }
                    // Thought parts are history for Gemini only; they are not sent to another provider.
                    String piece = part.optString("text", "");
                    if (!piece.isEmpty() && !part.optBoolean("thought", false)) text.append(piece);
                }
                // A tool-call turn still needs a string here: Cloudflare answers 400 for content: null.
                JSONObject message = new JSONObject().put("role", "assistant").put("content", text.toString());
                if (calls.length() > 0) message.put("tool_calls", calls);
                messages.put(message);
            } else {
                StringBuilder text = new StringBuilder();
                JSONArray results = new JSONArray();
                for (int p = 0; p < parts.length(); p++) {
                    JSONObject part = parts.optJSONObject(p);
                    if (part == null) continue;
                    JSONObject response = part.optJSONObject("functionResponse");
                    if (response != null) {
                        results.put(response);
                    } else {
                        String piece = part.optString("text", "");
                        if (!piece.isEmpty()) text.append(piece);
                    }
                }
                if (text.length() > 0) {
                    messages.put(new JSONObject().put("role", "user").put("content", text.toString()));
                }
                for (int r = 0; r < results.length(); r++) {
                    JSONObject response = results.optJSONObject(r);
                    String id = pendingCalls.isEmpty() ? "call_unknown" : pendingCalls.remove(0);
                    messages.put(new JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", id)
                            .put("content", resultText(response)));
                }
            }
        }

        JSONObject request = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("max_tokens", maxOutputTokens)
                .put("stream", false);
        if (declarations != null && declarations.length() > 0) request.put("tools", tools(declarations));
        return request;
    }

    /** Reads choices[0], or the native /ai/run wrapper, and returns the turn in the loop's shape. */
    public static Turn parse(String body) throws JSONException, ApiException {
        JSONObject root = new JSONObject(body);
        if (root.has("success") && !root.optBoolean("success", true)) {
            throw new ApiException(apiError(root));
        }
        if (root.has("error")) {
            throw new ApiException(describeError(body));
        }

        JSONObject message = null;
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null) {
            message = message(choices);
        } else {
            JSONObject result = root.optJSONObject("result");
            if (result != null && result.optJSONArray("choices") != null) {
                message = message(result.optJSONArray("choices"));
            } else if (result != null && (result.has("response") || result.has("tool_calls"))) {
                return fromNative(result);
            }
        }
        if (message == null) {
            throw new ApiException("The model returned no answer.");
        }

        String text = content(message.opt("content"));
        JSONArray toolCalls = message.optJSONArray("tool_calls");
        List<Call> calls = new ArrayList<>();
        JSONArray parts = new JSONArray();
        if (!text.isEmpty()) parts.put(new JSONObject().put("text", text));
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject call = toolCalls.optJSONObject(i);
                JSONObject function = call == null ? null : call.optJSONObject("function");
                String name = function == null ? "" : function.optString("name");
                if (name.isEmpty()) continue;
                JSONObject args = parseArguments(function.opt("arguments"));
                calls.add(new Call(name, args));
                parts.put(new JSONObject().put("functionCall",
                        new JSONObject().put("name", name).put("args", args)));
            }
        }
        if (parts.length() == 0) {
            String reason = choicesReason(choices);
            throw new ApiException(reason.isEmpty() || "stop".equals(reason)
                    ? "The model returned an empty answer."
                    : "The model stopped early (" + reason + ").");
        }
        return new Turn(text.trim(), parts, calls);
    }

    /** A readable one-line form of an error body, for the TV screen. */
    public static String describeError(String body) {
        try {
            JSONObject root = new JSONObject(body);
            String message = apiError(root);
            if (!message.isEmpty()) return message;
        } catch (JSONException ignored) {
            // Not JSON: fall through to the truncated body.
        }
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 240 ? oneLine.substring(0, 240) + "…" : oneLine;
    }

    private static JSONObject message(JSONArray choices) {
        if (choices.length() == 0) return null;
        JSONObject choice = choices.optJSONObject(0);
        return choice == null ? null : choice.optJSONObject("message");
    }

    private static String choicesReason(JSONArray choices) {
        if (choices == null || choices.length() == 0) return "";
        JSONObject choice = choices.optJSONObject(0);
        return choice == null ? "" : choice.optString("finish_reason", "");
    }

    private static Turn fromNative(JSONObject result) throws JSONException, ApiException {
        String text = content(result.opt("response"));
        JSONArray toolCalls = result.optJSONArray("tool_calls");
        List<Call> calls = new ArrayList<>();
        JSONArray parts = new JSONArray();
        if (!text.isEmpty()) parts.put(new JSONObject().put("text", text));
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject call = toolCalls.optJSONObject(i);
                String name = call == null ? "" : call.optString("name");
                if (name.isEmpty()) continue;
                JSONObject args = arguments(call.optJSONObject("arguments"));
                calls.add(new Call(name, args));
                parts.put(new JSONObject().put("functionCall",
                        new JSONObject().put("name", name).put("args", args)));
            }
        }
        if (parts.length() == 0) throw new ApiException("The model returned an empty answer.");
        return new Turn(text.trim(), parts, calls);
    }

    /** OpenAI content can be a string or an array of text parts. */
    private static String content(Object content) {
        if (content == null || content == JSONObject.NULL) return "";
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            StringBuilder text = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++) {
                Object part = parts.opt(i);
                if (part instanceof String) {
                    text.append((String) part);
                } else if (part instanceof JSONObject) {
                    JSONObject object = (JSONObject) part;
                    String piece = object.optString("text", "");
                    if (!piece.isEmpty()) text.append(piece);
                }
            }
            return text.toString();
        }
        return String.valueOf(content);
    }

    private static JSONObject parseArguments(Object arguments) {
        if (arguments instanceof JSONObject) return (JSONObject) arguments;
        if (arguments instanceof String) {
            try {
                return new JSONObject((String) arguments);
            } catch (JSONException ignored) {
                // Some models wrap the object in prose; keep it so the tool can report a bad call.
                return new JSONObject();
            }
        }
        return new JSONObject();
    }

    private static JSONObject arguments(JSONObject args) {
        return args == null ? new JSONObject() : args;
    }

    private static String resultText(JSONObject functionResponse) {
        if (functionResponse == null) return "";
        JSONObject envelope = functionResponse.optJSONObject("response");
        Object result = envelope == null ? null : envelope.opt("result");
        if (result == null || result == JSONObject.NULL) return "";
        return result instanceof String ? (String) result : result.toString();
    }

    /** Gemini declarations, in uppercase JSON Schema, become OpenAI tools. */
    private static JSONArray tools(JSONArray declarations) throws JSONException {
        JSONArray tools = new JSONArray();
        for (int i = 0; i < declarations.length(); i++) {
            JSONObject declaration = declarations.optJSONObject(i);
            if (declaration == null) continue;
            JSONObject function = new JSONObject()
                    .put("name", declaration.optString("name"))
                    .put("description", declaration.optString("description"));
            JSONObject parameters = declaration.optJSONObject("parameters");
            if (parameters != null) function.put("parameters", lowerTypes(parameters));
            tools.put(new JSONObject().put("type", "function").put("function", function));
        }
        return tools;
    }

    private static JSONObject lowerTypes(JSONObject schema) throws JSONException {
        JSONObject out = new JSONObject();
        Iterator<String> keys = schema.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = schema.opt(key);
            if ("type".equals(key) && value instanceof String) {
                out.put(key, ((String) value).toLowerCase(Locale.ROOT));
            } else if (value instanceof JSONObject) {
                out.put(key, lowerTypes((JSONObject) value));
            } else if (value instanceof JSONArray) {
                JSONArray array = new JSONArray();
                JSONArray source = (JSONArray) value;
                for (int i = 0; i < source.length(); i++) {
                    Object item = source.opt(i);
                    array.put(item instanceof JSONObject ? lowerTypes((JSONObject) item) : item);
                }
                out.put(key, array);
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String apiError(JSONObject root) {
        JSONObject error = root.optJSONObject("error");
        if (error != null && !error.optString("message").isEmpty()) return error.optString("message");
        JSONArray errors = root.optJSONArray("errors");
        if (errors != null && errors.length() > 0) {
            JSONObject first = errors.optJSONObject(0);
            if (first != null && !first.optString("message").isEmpty()) return first.optString("message");
        }
        return "";
    }

    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }
}
