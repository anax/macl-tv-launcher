package com.macl.tvagent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The small HTTP request reader behind the phone pairing page. No framework, no dependency. */
public final class HttpRequest {
    private static final int MAX_HEADER_LINE = 4096;

    public final String method;
    public final String path;
    public final String body;
    private final Map<String, String> query;

    private HttpRequest(String method, String path, String body, Map<String, String> query) {
        this.method = method;
        this.path = path;
        this.body = body;
        this.query = query;
    }

    public static HttpRequest read(InputStream in, int maxBody) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) throw new IOException("empty request");
        String[] bits = requestLine.split(" ");
        if (bits.length < 2) throw new IOException("bad request line");

        int contentLength = 0;
        while (true) {
            String line = readLine(in);
            if (line == null || line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if ("content-length".equals(name)) {
                try {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IOException("bad content-length");
                }
            }
        }
        if (contentLength < 0 || contentLength > maxBody) throw new IOException("body too large");

        byte[] raw = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = in.read(raw, read, contentLength - read);
            if (n < 0) throw new IOException("short body");
            read += n;
        }

        String target = bits[1];
        String path = target;
        Map<String, String> query = new HashMap<>();
        int question = target.indexOf('?');
        if (question >= 0) {
            path = target.substring(0, question);
            query = parseForm(target.substring(question + 1));
        }
        return new HttpRequest(bits[0].toUpperCase(Locale.ROOT), path, new String(raw, StandardCharsets.UTF_8), query);
    }

    /** A form or query parameter: the POST body wins, then the query string. */
    public String param(String name) {
        String fromBody = parseForm(body).get(name);
        return fromBody != null ? fromBody : query.get(name);
    }

    public static Map<String, String> parseForm(String encoded) {
        Map<String, String> out = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) return out;
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            try {
                out.put(decode(key), decode(value));
            } catch (UnsupportedEncodingException | IllegalArgumentException ignored) {
                // UTF-8 always exists; keep the raw pair otherwise.
            }
        }
        return out;
    }

    private static String decode(String value) throws UnsupportedEncodingException {
        return URLDecoder.decode(value, "UTF-8");
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (buffer.size() >= MAX_HEADER_LINE) throw new IOException("header line too long");
            buffer.write(b);
        }
        if (b == -1 && buffer.size() == 0) return null;
        String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
