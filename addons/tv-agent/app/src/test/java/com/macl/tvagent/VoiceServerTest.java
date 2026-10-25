package com.macl.tvagent;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The pairing page over a real socket on loopback: what the phone will actually do. */
public class VoiceServerTest {
    private FakeBridge bridge;
    private VoiceServer server;
    private String code;
    private int port;

    @Before
    public void open() throws IOException {
        bridge = new FakeBridge();
        server = new VoiceServer(bridge);
        code = server.start();
        port = server.port();
    }

    @After
    public void close() {
        server.stop();
    }

    @Test
    public void thePageLoadsAndNamesItself() throws Exception {
        Reply reply = request("GET", "/", null);

        assertEquals(200, reply.status);
        assertTrue(reply.body.contains("TV Agent"));
        assertTrue(reply.body.contains("Send to TV"));
        assertFalse(reply.body.contains("__"));
    }

    @Test
    public void stateTellsThePhoneWhetherAKeyIsSet() throws Exception {
        Reply reply = request("GET", "/state", null);

        assertEquals(200, reply.status);
        JSONObject json = new JSONObject(reply.body);
        assertTrue(json.getBoolean("hasKey"));
        assertEquals("gemini", json.getString("provider"));
        assertEquals("gemini-3.8-flash", json.getString("model"));
        assertEquals("gemini-3.8-flash", json.getString("geminiModel"));
        assertEquals("@cf/zai-org/glm-4.7-flash", json.getString("cfModel"));
        assertFalse("the status line must not echo the Cloudflare account", json.has("cfAccount"));
        assertFalse(json.getBoolean("floating"));
    }

    @Test
    public void stateSaysWhenTheOrbIsFloating() throws Exception {
        bridge.floating = true;

        Reply reply = request("GET", "/state", null);

        assertEquals(200, reply.status);
        assertTrue(new JSONObject(reply.body).getBoolean("floating"));
    }

    @Test
    public void thePhoneCanHideTheOrb() throws Exception {
        bridge.floating = true;

        Reply reply = request("POST", "/orb", "code=" + code + "&action=hide");

        assertEquals(200, reply.status);
        assertTrue(bridge.hidden);
        assertTrue(new JSONObject(reply.body).getBoolean("ok"));
    }

    @Test
    public void hidingTheOrbStillNeedsTheCode() throws Exception {
        bridge.floating = true;

        Reply reply = request("POST", "/orb", "code=9999&action=hide");

        assertEquals(403, reply.status);
        assertFalse(bridge.hidden);
    }

    @Test
    public void thePhoneCanNudgeTheOrbInEveryDirection() throws Exception {
        bridge.floating = true;

        assertEquals(200, request("POST", "/orb", "code=" + code + "&action=left").status);
        assertEquals(200, request("POST", "/orb", "code=" + code + "&action=up").status);
        assertEquals(-1, bridge.movedX);
        assertEquals(-1, bridge.movedY);

        assertEquals(200, request("POST", "/orb", "code=" + code + "&action=right").status);
        assertEquals(200, request("POST", "/orb", "code=" + code + "&action=down").status);
        assertEquals(0, bridge.movedX);
        assertEquals(0, bridge.movedY);
    }

    @Test
    public void thePageKeepsItsCodeAndPortWhenItMovesToTheOrb() throws Exception {
        String oldCode = code;
        int oldPort = port;
        server.stop();

        server = new VoiceServer(bridge);
        assertEquals(oldCode, server.start(oldCode, oldPort));
        port = oldPort;
        assertEquals(oldPort, server.port());
        Reply reply = request("POST", "/ask", "code=" + oldCode + "&text=still+there");
        assertEquals(200, reply.status);
    }

    @Test
    public void theRightCodeSendsTheGoalToTheTv() throws Exception {
        Reply reply = request("POST", "/ask", "code=" + code + "&text=open+YouTube");

        assertEquals(200, reply.status);
        assertEquals(List.of("open YouTube"), bridge.asks);
        assertEquals("done: open YouTube", new JSONObject(reply.body).getString("answer"));
    }

    @Test
    public void theWrongCodeIsRefusedAndCounted() throws Exception {
        Reply reply = request("POST", "/ask", "code=9999&text=something");

        assertEquals(403, reply.status);
        assertEquals(1, bridge.wrongCodes);
        assertTrue(bridge.asks.isEmpty());
        assertNotEquals("9999", code);
    }

    @Test
    public void anEmptyGoalIsRejectedBeforeTheModelSeesIt() throws Exception {
        Reply reply = request("POST", "/ask", "code=" + code + "&text=+");

        assertEquals(400, reply.status);
        assertTrue(bridge.asks.isEmpty());
    }

    @Test
    public void aBusyTvSaysSo() throws Exception {
        bridge.busy = true;

        Reply reply = request("POST", "/ask", "code=" + code + "&text=hello");

        assertEquals(409, reply.status);
        assertFalse(new JSONObject(reply.body).getBoolean("ok"));
    }

    @Test
    public void aKeySavedFromThePhoneStaysOffThePage() throws Exception {
        Reply reply = request("POST", "/config",
                "code=" + code + "&provider=gemini&apiKey=AIzaTEST&model=gemini-3.8-flash");

        assertEquals(200, reply.status);
        assertEquals("gemini", bridge.savedProvider);
        assertEquals("AIzaTEST", bridge.savedKey);
        assertFalse(reply.body.contains("AIzaTEST"));
    }

    @Test
    public void aCloudflareTokenFromThePhoneIsSavedAndNeverEchoed() throws Exception {
        Reply reply = request("POST", "/config",
                "code=" + code + "&provider=cloudflare&cfAccount=0123456789abcdef0123456789abcdef"
                        + "&cfToken=cfSECRET&cfModel=%40cf%2Fzai-org%2Fglm-4.7-flash");

        assertEquals(200, reply.status);
        assertEquals("cloudflare", bridge.savedProvider);
        assertEquals("0123456789abcdef0123456789abcdef", bridge.savedCfAccount);
        assertEquals("cfSECRET", bridge.savedCfToken);
        assertEquals("@cf/zai-org/glm-4.7-flash", bridge.savedCfModel);
        assertFalse(reply.body.contains("cfSECRET"));
    }

    @Test
    public void anythingElseIs404() throws Exception {
        assertEquals(404, request("GET", "/etc/passwd", null).status);
        assertEquals(404, request("GET", "/ask", null).status);
    }

    private Reply request(String method, String path, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = read(stream);
        connection.disconnect();
        return new Reply(status, text);
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = stream.read(chunk)) != -1) buffer.write(chunk, 0, n);
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static final class Reply {
        final int status;
        final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class FakeBridge implements VoiceServer.Bridge {
        final List<String> asks = new ArrayList<>();
        String provider = "gemini";
        String savedProvider;
        String savedKey;
        String savedModel;
        String savedCfAccount;
        String savedCfToken;
        String savedCfModel;
        int wrongCodes;
        boolean busy;
        boolean closed;
        boolean hasKey = true;
        boolean floating;
        boolean hidden;
        int movedX;
        int movedY;

        @Override
        public boolean hasKey() {
            return hasKey;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public String model() {
            return "gemini-3.8-flash";
        }

        @Override
        public String geminiModel() {
            return "gemini-3.8-flash";
        }

        @Override
        public String cfModel() {
            return "@cf/zai-org/glm-4.7-flash";
        }

        @Override
        public boolean busy() {
            return busy;
        }

        @Override
        public boolean floating() {
            return floating;
        }

        @Override
        public void hideOrb() {
            hidden = true;
            floating = false;
        }

        @Override
        public void moveOrb(int dx, int dy) {
            movedX += dx;
            movedY += dy;
        }

        @Override
        public String ask(String text) {
            asks.add(text);
            return "done: " + text;
        }

        @Override
        public void saveConfig(String provider, String apiKey, String model,
                               String cfAccount, String cfToken, String cfModel) {
            savedProvider = provider;
            savedKey = apiKey;
            savedModel = model;
            savedCfAccount = cfAccount;
            savedCfToken = cfToken;
            savedCfModel = cfModel;
        }

        @Override
        public void onWrongCode() {
            wrongCodes++;
        }

        @Override
        public void onClosed() {
            closed = true;
        }
    }
}
