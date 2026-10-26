package com.macl.tvagent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The phone pairing page, up only while the TV shows the code. Same network only, four-digit code on every
 * request, ten wrong codes and it closes itself. It answers one question and saves a key; nothing else is served.
 */
public final class VoiceServer {
    public interface Bridge {
        boolean hasKey();

        String provider();

        /** The model the active provider calls. */
        String model();

        String geminiModel();

        String cfModel();

        boolean busy();

        /** True while the floating orb (and this page) is kept alive by the service. */
        boolean floating();

        /** Hides the floating orb, which also closes this page. */
        void hideOrb();

        /** Nudges the floating orb: dx rightwards, dy downwards. */
        void moveOrb(int dx, int dy);

        /** Runs the goal on the TV and blocks until there is an answer, or returns null. */
        String ask(String text);

        void saveConfig(String provider, String apiKey, String model,
                        String cfAccount, String cfToken, String cfModel);

        void onWrongCode();

        void onClosed();
    }

    private static final int FIRST_PORT = 8787;
    private static final int LAST_PORT = 8800;
    private static final int MAX_BODY = 16 * 1024;
    private static final int MAX_WRONG = 10;

    private final Bridge bridge;
    private final AtomicInteger wrong = new AtomicInteger();

    private ServerSocket server;
    private ExecutorService pool;
    private volatile String code = "";
    private volatile boolean running;
    private int port;

    public VoiceServer(Bridge bridge) {
        this.bridge = bridge;
    }

    /** Opens the port and returns the four-digit code to show on the TV. */
    public synchronized String start() throws IOException {
        return start(null, 0);
    }

    /**
     * Opens the port, reusing a code and port when the phone page is handed from the activity to the
     * floating orb. An invalid code is replaced, and a busy port falls through to the next one.
     */
    public synchronized String start(String preferredCode, int preferredPort) throws IOException {
        if (running) return code;
        int first = preferredPort >= FIRST_PORT && preferredPort <= LAST_PORT ? preferredPort : FIRST_PORT;
        IOException last = null;
        int bound = 0;
        for (int pass = 0; pass < 2 && bound == 0; pass++) {
            int from = pass == 0 ? first : FIRST_PORT;
            int to = pass == 0 ? LAST_PORT : first - 1;
            for (int candidate = from; candidate <= to; candidate++) {
                ServerSocket socket = new ServerSocket();
                try {
                    socket.setReuseAddress(true);
                    socket.bind(new InetSocketAddress(candidate));
                    server = socket;
                    port = candidate;
                    bound = candidate;
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                        // Already closed; try the next port.
                    }
                }
            }
        }
        if (bound == 0) throw last != null ? last : new IOException("no free port");
        wrong.set(0);
        code = preferredCode != null && preferredCode.matches("\\d{4}")
                ? preferredCode
                : String.format(Locale.US, "%04d", new SecureRandom().nextInt(10_000));
        running = true;
        pool = Executors.newFixedThreadPool(4);
        Thread accept = new Thread(this::acceptLoop, "agent-voice");
        accept.setDaemon(true);
        accept.start();
        return code;
    }

    public void stop() {
        running = false;
        code = "";
        ServerSocket socket;
        ExecutorService workers;
        synchronized (this) {
            socket = server;
            server = null;
            workers = pool;
            pool = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing is best effort.
            }
        }
        if (workers != null) workers.shutdown();
    }

    /** The address to show on the TV, or null when no Wi-Fi address is up. */
    public String url() {
        if (!running) return null;
        String ip = localAddress();
        return ip == null ? null : "http://" + ip + ":" + port;
    }

    /** The bound port, for the tests. */
    int port() {
        return port;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                ExecutorService workers = pool;
                if (workers == null) {
                    closeQuietly(socket);
                    continue;
                }
                workers.execute(() -> handle(socket));
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket open = socket) {
            open.setSoTimeout(30_000);
            if (!isLocal(open.getInetAddress())) {
                respond(open, 403, "text/plain; charset=utf-8", "Local network only.");
                return;
            }
            HttpRequest request;
            try {
                request = HttpRequest.read(open.getInputStream(), MAX_BODY);
            } catch (IOException e) {
                return;
            }

            if ("GET".equals(request.method) && ("/".equals(request.path) || "/index.html".equals(request.path))) {
                respond(open, 200, "text/html; charset=utf-8", VoicePage.html());
            } else if ("GET".equals(request.method) && "/state".equals(request.path)) {
                JSONObject state = new JSONObject()
                        .put("ok", true)
                        .put("hasKey", bridge.hasKey())
                        .put("provider", bridge.provider())
                        .put("model", bridge.model())
                        .put("geminiModel", bridge.geminiModel())
                        .put("cfModel", bridge.cfModel())
                        .put("busy", bridge.busy())
                        .put("floating", bridge.floating());
                respondJson(open, 200, state);
            } else if ("POST".equals(request.method) && "/ask".equals(request.path)) {
                ask(open, request);
            } else if ("POST".equals(request.method) && "/config".equals(request.path)) {
                config(open, request);
            } else if ("POST".equals(request.method) && "/orb".equals(request.path)) {
                orb(open, request);
            } else {
                respond(open, 404, "text/plain; charset=utf-8", "Not found.");
            }
        } catch (IOException | JSONException ignored) {
            // The phone closed the tab, or the reply could not be written: nothing left to do.
        }
    }

    private void ask(Socket socket, HttpRequest request) throws IOException, JSONException {
        boolean isLocalNoCode = socket.getInetAddress() != null
                && socket.getInetAddress().isLoopbackAddress()
                && (request.param("code") == null || request.param("code").isEmpty());
        if (!isLocalNoCode && !checkCode(request.param("code"))) {
            wrongCode(socket);
            return;
        }
        String text = request.param("text");
        if (text == null || text.trim().isEmpty()) {
            respondJson(socket, 400, error("Nothing to ask."));
            return;
        }
        if (bridge.busy()) {
            respondJson(socket, 409, error("The TV is still working on the last task."));
            return;
        }
        String answer = bridge.ask(text.trim());
        if (answer == null || answer.isEmpty()) {
            respondJson(socket, 409, error("The TV did not finish. Try again."));
            return;
        }
        respondJson(socket, 200, new JSONObject()
                .put("ok", true)
                .put("answer", answer)
                .put("floating", bridge.floating()));
    }

    private void config(Socket socket, HttpRequest request) throws IOException, JSONException {
        if (!checkCode(request.param("code"))) {
            wrongCode(socket);
            return;
        }
        bridge.saveConfig(request.param("provider"), request.param("apiKey"), request.param("model"),
                request.param("cfAccount"), request.param("cfToken"), request.param("cfModel"));
        respondJson(socket, 200, new JSONObject()
                .put("ok", true)
                .put("hasKey", bridge.hasKey())
                .put("provider", bridge.provider())
                .put("model", bridge.model())
                .put("geminiModel", bridge.geminiModel())
                .put("cfModel", bridge.cfModel()));
    }

    private void orb(Socket socket, HttpRequest request) throws IOException, JSONException {
        if (!checkCode(request.param("code"))) {
            wrongCode(socket);
            return;
        }
        String action = request.param("action");
        if ("hide".equals(action)) {
            bridge.hideOrb();
        } else if ("left".equals(action)) {
            bridge.moveOrb(-1, 0);
        } else if ("right".equals(action)) {
            bridge.moveOrb(1, 0);
        } else if ("up".equals(action)) {
            bridge.moveOrb(0, -1);
        } else if ("down".equals(action)) {
            bridge.moveOrb(0, 1);
        }
        respondJson(socket, 200, new JSONObject()
                .put("ok", true)
                .put("floating", bridge.floating()));
    }

    private void wrongCode(Socket socket) throws IOException, JSONException {
        int count = wrong.incrementAndGet();
        respondJson(socket, 403, error("Wrong pairing code. Check the four digits on the TV."));
        bridge.onWrongCode();
        if (count >= MAX_WRONG) {
            bridge.onClosed();
            stop();
        }
    }

    private boolean checkCode(String given) {
        String expected = code;
        if (expected.isEmpty() || given == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                given.trim().getBytes(StandardCharsets.US_ASCII));
    }

    private static JSONObject error(String message) throws JSONException {
        return new JSONObject().put("ok", false).put("error", message);
    }

    private static void respondJson(Socket socket, int status, JSONObject body) throws IOException {
        respond(socket, status, "application/json; charset=utf-8", body.toString());
    }

    private static void respond(Socket socket, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder()
                .append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
                .append("Content-Type: ").append(type).append("\r\n")
                .append("Content-Length: ").append(bytes.length).append("\r\n")
                .append("Cache-Control: no-store\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("Connection: close\r\n\r\n");
        OutputStream out = socket.getOutputStream();
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 400:
                return "Bad Request";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 409:
                return "Conflict";
            default:
                return "Error";
        }
    }

    private static boolean isLocal(InetAddress address) {
        return address != null
                && (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress());
    }

    /** The TV's own Wi-Fi IPv4 address. Network enumeration needs no permission. */
    private static String localAddress() {
        String fallback = null;
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                String name = network.getName() == null ? "" : network.getName().toLowerCase(Locale.ROOT);
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
                    String ip = address.getHostAddress();
                    if (ip == null) continue;
                    if (name.startsWith("wlan") || name.startsWith("eth")) return ip;
                    if (fallback == null) fallback = ip;
                }
            }
        } catch (IOException ignored) {
            // No interfaces is the same as no address.
        }
        return fallback;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing to do.
        }
    }
}
