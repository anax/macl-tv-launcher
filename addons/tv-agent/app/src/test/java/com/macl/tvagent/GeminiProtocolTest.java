package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GeminiProtocolTest {
    @Test
    public void theRequestCarriesTheSystemPromptToolsAndLimit() throws Exception {
        JSONArray contents = new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", "movie night"))));

        JSONObject request = GeminiProtocol.buildRequest("be brief", contents, ToolSpecs.declarations(), 512);

        assertEquals("be brief",
                request.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals("movie night", request.getJSONArray("contents").getJSONObject(0)
                .getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals(512, request.getJSONObject("generationConfig").getInt("maxOutputTokens"));
        assertEquals(ToolSpecs.declarations().length(), request.getJSONArray("tools").getJSONObject(0)
                .getJSONArray("functionDeclarations").length());
        assertEquals("AUTO", request.getJSONObject("toolConfig").getJSONObject("functionCallingConfig")
                .getString("mode"));
    }

    @Test
    public void noToolsMeansNoToolKeys() throws Exception {
        JSONObject request = GeminiProtocol.buildRequest("be brief", new JSONArray(), new JSONArray(), 100);

        assertFalse(request.has("tools"));
        assertFalse(request.has("toolConfig"));
    }

    @Test
    public void aReplyKeepsThoughtsOutOfTheAnswerButInTheHistory() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"weighing options\",\"thought\":true},"
                + "{\"functionCall\":{\"name\":\"open_app\",\"args\":{\"query\":\"YouTube\"}}},"
                + "{\"text\":\"Plan: open YouTube.\"}]}}]}";

        Turn turn = GeminiProtocol.parse(body);

        assertEquals("Plan: open YouTube.", turn.text);
        assertEquals(1, turn.calls.size());
        assertEquals("open_app", turn.calls.get(0).name);
        assertEquals("YouTube", turn.calls.get(0).args.getString("query"));
        assertEquals(3, turn.parts.length());
    }

    @Test
    public void anApiErrorKeepsTheApisOwnMessage() {
        try {
            GeminiProtocol.parse("{\"error\":{\"message\":\"API key not valid. Please pass a valid API key.\"}}");
            fail("should have thrown");
        } catch (Exception e) {
            assertEquals("API key not valid. Please pass a valid API key.", e.getMessage());
        }
    }

    @Test
    public void aBlockedRequestSaysSo() {
        try {
            GeminiProtocol.parse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
            fail("should have thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("SAFETY"));
        }
    }

    @Test
    public void anEmptyReplyIsAnErrorNotAnEmptyAnswer() {
        try {
            GeminiProtocol.parse("{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"MAX_TOKENS\"}]}");
            fail("should have thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("MAX_TOKENS"));
        }
    }

    @Test
    public void aNonJsonErrorBodyIsShortenedForTheScreen() {
        String message = GeminiProtocol.describeError("502 Bad Gateway\n".repeat(40));
        assertTrue(message.length() <= 241);
        assertFalse(message.contains("\n"));
    }
}
