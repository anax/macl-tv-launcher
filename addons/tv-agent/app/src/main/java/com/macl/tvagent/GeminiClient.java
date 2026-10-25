package com.macl.tvagent;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Talks to the Gemini API with the TV's own key. No SDK, no service, one HTTPS call per turn. */
final class GeminiClient implements Llm {
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_TOKENS = 2048;

    private final Prefs prefs;

    GeminiClient(Prefs prefs) {
        this.prefs = prefs;
    }

    @Override
    public Turn send(String systemPrompt, JSONArray contents, JSONArray declarations) throws Exception {
        String key = prefs.key();
        if (key.isEmpty()) {
            throw new IllegalStateException("No API key yet. Add one with Phone voice or in Settings.");
        }
        String model = Prefs.cleanModel(prefs.model());
        if (model.isEmpty()) {
            throw new IllegalStateException("The model name is not valid. Fix it in Settings.");
        }
        String body = GeminiProtocol.buildRequest(systemPrompt, contents, declarations, MAX_TOKENS).toString();
        String address = ENDPOINT + model + ":generateContent";

        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20_000);
                connection.setReadTimeout(150_000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("x-goog-api-key", key);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String reply = read(stream);
                if (code == 200) return GeminiProtocol.parse(reply);

                boolean retryable = code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
                if (retryable && attempt < 2) {
                    Thread.sleep(1200L * (attempt + 1));
                    continue;
                }
                throw new GeminiProtocol.ApiException("HTTP " + code + ": " + GeminiProtocol.describeError(reply));
            } catch (IOException e) {
                last = e;
                if (attempt < 2) {
                    Thread.sleep(1200L * (attempt + 1));
                    continue;
                }
                throw e;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last != null ? last : new IOException("The model did not answer.");
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
