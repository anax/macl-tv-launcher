package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CloudflareProtocolTest {
    @Test
    public void theRequestUsesOpenAiMessagesAndLowersTheSchemaTypes() throws Exception {
        JSONArray contents = new JSONArray()
                .put(user("open YouTube"))
                .put(new JSONObject().put("role", "model").put("parts", new JSONArray()
                        .put(new JSONObject().put("text", "Plan: open YouTube."))
                        .put(new JSONObject().put("functionCall", new JSONObject()
                                .put("name", "open_app")
                                .put("args", new JSONObject().put("query", "YouTube"))))))
                .put(new JSONObject().put("role", "user").put("parts", new JSONArray()
                        .put(new JSONObject().put("functionResponse", new JSONObject()
                                .put("name", "open_app")
                                .put("response", new JSONObject().put("result", "opened YouTube"))))));

        JSONObject request = CloudflareProtocol.buildRequest(
                "be brief", contents, ToolSpecs.declarations(), 512, "@cf/zai-org/glm-4.7-flash");

        assertEquals("@cf/zai-org/glm-4.7-flash", request.getString("model"));
        assertEquals(512, request.getInt("max_tokens"));
        assertFalse(request.getBoolean("stream"));

        JSONArray messages = request.getJSONArray("messages");
        assertEquals(4, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("open YouTube", messages.getJSONObject(1).getString("content"));

        JSONObject assistant = messages.getJSONObject(2);
        assertEquals("assistant", assistant.getString("role"));
        assertEquals("Plan: open YouTube.", assistant.getString("content"));
        JSONObject call = assistant.getJSONArray("tool_calls").getJSONObject(0);
        assertEquals("call_0", call.getString("id"));
        assertEquals("function", call.getString("type"));
        assertEquals("open_app", call.getJSONObject("function").getString("name"));
        JSONObject args = new JSONObject(call.getJSONObject("function").getString("arguments"));
        assertEquals("YouTube", args.getString("query"));

        JSONObject tool = messages.getJSONObject(3);
        assertEquals("tool", tool.getString("role"));
        assertEquals("call_0", tool.getString("tool_call_id"));
        assertEquals("opened YouTube", tool.getString("content"));

        JSONObject function = request.getJSONArray("tools").getJSONObject(0).getJSONObject("function");
        assertEquals("open_app", function.getString("name"));
        assertEquals("object", function.getJSONObject("parameters").getString("type"));
        assertEquals("string", function.getJSONObject("parameters").getJSONObject("properties")
                .getJSONObject("query").getString("type"));
        assertEquals(9, request.getJSONArray("tools").length());
    }

    @Test
    public void aToolOnlyTurnSendsAnEmptyStringNotNull() throws Exception {
        JSONArray contents = new JSONArray()
                .put(user("turn it down"))
                .put(new JSONObject().put("role", "model").put("parts", new JSONArray()
                        .put(new JSONObject().put("functionCall", new JSONObject()
                                .put("name", "volume")
                                .put("args", new JSONObject().put("level", 20))))));

        JSONObject message = CloudflareProtocol.buildRequest("be brief", contents, new JSONArray(), 100, "m")
                .getJSONArray("messages").getJSONObject(2);

        assertEquals("assistant", message.getString("role"));
        assertEquals("", message.getString("content"));
        assertEquals("call_0", message.getJSONArray("tool_calls").getJSONObject(0).getString("id"));
    }

    @Test
    public void noToolsMeansNoToolsKey() throws Exception {
        JSONObject request = CloudflareProtocol.buildRequest(
                "be brief", new JSONArray().put(user("hello")), new JSONArray(), 100, "m");

        assertFalse(request.has("tools"));
    }

    @Test
    public void aReplyKeepsTextAndToolCalls() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Plan: open YouTube.\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"open_app\",\"arguments\":\"{\\\"query\\\":\\\"YouTube\\\"}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}]}";

        Turn turn = CloudflareProtocol.parse(body);

        assertEquals("Plan: open YouTube.", turn.text);
        assertEquals(1, turn.calls.size());
        assertEquals("open_app", turn.calls.get(0).name);
        assertEquals("YouTube", turn.calls.get(0).args.getString("query"));
        assertEquals(2, turn.parts.length());
        assertEquals("open_app", turn.parts.getJSONObject(1).getJSONObject("functionCall").getString("name"));
    }

    @Test
    public void contentCanBeAnArrayOfTextParts() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"Hello \"},{\"type\":\"text\",\"text\":\"world\"}]}}]}";

        assertEquals("Hello world", CloudflareProtocol.parse(body).text);
    }

    @Test
    public void theNativeResultWrapperIsAcceptedToo() throws Exception {
        String body = "{\"result\":{\"response\":\"Done.\",\"tool_calls\":["
                + "{\"name\":\"time\",\"arguments\":{}}]}}";

        Turn turn = CloudflareProtocol.parse(body);

        assertEquals("Done.", turn.text);
        assertEquals("time", turn.calls.get(0).name);
    }

    @Test
    public void aCloudflareErrorKeepsItsOwnMessage() {
        try {
            CloudflareProtocol.parse("{\"success\":false,\"errors\":[{\"code\":10000,"
                    + "\"message\":\"Authentication error\"}]}");
            fail("should have thrown");
        } catch (Exception e) {
            assertEquals("Authentication error", e.getMessage());
        }
    }

    @Test
    public void anOpenAiErrorKeepsItsOwnMessage() {
        try {
            CloudflareProtocol.parse("{\"error\":{\"message\":\"Invalid model\"}}");
            fail("should have thrown");
        } catch (Exception e) {
            assertEquals("Invalid model", e.getMessage());
        }
    }

    @Test
    public void anEmptyReplyIsAnErrorNotAnEmptyAnswer() {
        try {
            CloudflareProtocol.parse("{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"stop\"}]}");
            fail("should have thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("empty"));
        }
    }

    @Test
    public void aNonJsonErrorBodyIsShortenedForTheScreen() {
        String message = CloudflareProtocol.describeError("502 Bad Gateway\n".repeat(40));
        assertTrue(message.length() <= 241);
        assertFalse(message.contains("\n"));
    }

    private static JSONObject user(String text) throws Exception {
        return new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }
}
