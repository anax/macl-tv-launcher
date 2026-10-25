package com.macl.tvagent;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class HttpRequestTest {
    @Test
    public void aPostCarriesFormFieldsAndQuery() throws Exception {
        HttpRequest request = post("code=1234&text=open+YouTube%21");

        assertEquals("POST", request.method);
        assertEquals("/ask", request.path);
        assertEquals("1234", request.param("code"));
        assertEquals("open YouTube!", request.param("text"));
    }

    @Test
    public void aGetReadsItsQuery() throws Exception {
        HttpRequest request = read("GET /state?code=0042 HTTP/1.1\r\nHost: tv\r\n\r\n");

        assertEquals("GET", request.method);
        assertEquals("/state", request.path);
        assertEquals("0042", request.param("code"));
        assertNull(request.param("text"));
    }

    @Test
    public void aBodyOverTheLimitIsRefused() throws Exception {
        try {
            read("POST /ask HTTP/1.1\r\nContent-Length: 50\r\n\r\n" + "x".repeat(50), 8);
            fail("should have thrown");
        } catch (IOException expected) {
            assertEquals("body too large", expected.getMessage());
        }
    }

    @Test
    public void aBadPercentEscapeDoesNotCrashThePage() throws Exception {
        HttpRequest request = post("text=100%");

        // The broken pair is dropped, the request still parses.
        assertEquals("POST", request.method);
        assertNull(request.param("text"));
    }

    private static HttpRequest post(String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "POST /ask HTTP/1.1\r\nHost: 10.0.0.2\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + bytes.length + "\r\n\r\n";
        return read(head + body, 4096);
    }

    private static HttpRequest read(String raw) throws IOException {
        return read(raw, 4096);
    }

    private static HttpRequest read(String raw, int maxBody) throws IOException {
        return HttpRequest.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), maxBody);
    }
}
