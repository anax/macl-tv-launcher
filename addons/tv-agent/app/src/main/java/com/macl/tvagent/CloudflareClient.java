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

/**
 * Talks to Cloudflare Workers AI through its OpenAI-compatible endpoint, with the TV's own account id and
 * API token. One HTTPS call per turn, no SDK and no Worker of your own.
 */
final class CloudflareClient implements Llm {
    private static final String ENDPOINT = "https://api.cloudflare.com/client/v4/accounts/";
    private static final String PATH = "/ai/v1/chat/completions";
    private static final int MAX_TOKENS = 2048;

    private final Prefs prefs;

    CloudflareClient(Prefs prefs) {
        this.prefs = prefs;
    }

    @Override
    public Turn send(String systemPrompt, JSONArray contents, JSONArray declarations) throws Exception {
        String account = prefs.cfAccount();
        String token = prefs.cfToken();
        if (account.isEmpty() || token.isEmpty()) {
            throw new IllegalStateException(
                    "No Cloudflare account or API token yet. Add both with Phone voice or in Settings.");
        }
        String model = prefs.cfModel();
        String body = CloudflareProtocol.buildRequest(systemPrompt, contents, declarations, MAX_TOKENS, model)
                .toString();
        String address = ENDPOINT + account + PATH;

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
                connection.setRequestProperty("Authorization", "Bearer " + token);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String reply = read(stream);
                if (code == 200) return CloudflareProtocol.parse(reply);

                boolean retryable = code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
                if (retryable && attempt < 2) {
                    Thread.sleep(1200L * (attempt + 1));
                    continue;
                }
                throw new CloudflareProtocol.ApiException(
                        "HTTP " + code + ": " + CloudflareProtocol.describeError(reply));
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
