package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentEngineTest {
    @Test
    public void aPlanRunsItsToolAndEndsWithTheAnswer() throws Exception {
        FakeTools devices = new FakeTools();
        devices.results.put("open_app", "opened YouTube");
        FakeLlm llm = new FakeLlm()
                .then(planTurn("Plan: open YouTube.", "open_app", new JSONObject().put("query", "YouTube")))
                .then(answerTurn("Opened YouTube. Louder?"));

        Recorder recorder = new Recorder();
        String answer = new AgentEngine(llm, devices, "system").run("movie night", recorder);

        assertEquals("Opened YouTube. Louder?", answer);
        assertEquals(List.of("open_app:YouTube"), devices.calls);
        assertEquals(List.of("Plan: open YouTube."), recorder.notes);
        assertTrue(recorder.steps.contains("open_app · YouTube"));
        assertTrue(recorder.steps.contains("   opened YouTube"));
        assertEquals(List.of(answer), recorder.answers);
        assertTrue(recorder.errors.isEmpty());

        // The model sees the tool result as a user turn before it writes the answer.
        assertEquals(3, llm.seen.get(1).length());
        JSONObject toolTurn = llm.seen.get(1).optJSONObject(2);
        assertEquals("user", toolTurn.optString("role"));
        JSONObject response = toolTurn.optJSONArray("parts").optJSONObject(0).optJSONObject("functionResponse");
        assertEquals("open_app", response.optString("name"));
        assertEquals("opened YouTube", response.optJSONObject("response").optString("result"));
    }

    @Test
    public void aBrokenToolBecomesAResultNotACrash() throws Exception {
        FakeTools devices = new FakeTools();
        devices.failures.put("open_app", "no such app");
        FakeLlm llm = new FakeLlm()
                .then(planTurn("Plan: open it.", "open_app", new JSONObject().put("query", "Nope")))
                .then(answerTurn("I could not open it."));

        Recorder recorder = new Recorder();
        String answer = new AgentEngine(llm, devices, "system").run("open nope", recorder);

        assertEquals("I could not open it.", answer);
        assertTrue(recorder.steps.contains("   error: no such app"));
        assertTrue(recorder.errors.isEmpty());
    }

    @Test
    public void theStepLimitStopsTheLoopAndStillAsksForWords() throws Exception {
        FakeTools devices = new FakeTools();
        JSONArray parts = new JSONArray();
        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            JSONObject args = new JSONObject().put("level", i);
            calls.add(new Call("volume", args));
            parts.put(new JSONObject().put("functionCall",
                    new JSONObject().put("name", "volume").put("args", args)));
        }
        FakeLlm llm = new FakeLlm()
                .then(new Turn("", parts, calls))
                .then(answerTurn("I stopped at the step limit."));

        Recorder recorder = new Recorder();
        String answer = new AgentEngine(llm, devices, "system").run("set every volume", recorder);

        assertEquals("I stopped at the step limit.", answer);
        assertEquals(AgentEngine.MAX_CALLS, devices.calls.size());
        // Every call in the model turn gets a response, or the API would refuse the next request.
        JSONObject toolTurn = llm.seen.get(1).optJSONObject(1);
        assertEquals(20, toolTurn.optJSONArray("parts").length());
        // The closing request must not offer tools.
        assertEquals(0, llm.declarations.get(1).length());
    }

    @Test
    public void aModelFailureIsReportedOnce() {
        FakeLlm llm = new FakeLlm();
        Recorder recorder = new Recorder();
        String answer = new AgentEngine(llm, new FakeTools(), "system").run("anything", recorder);

        assertNull(answer);
        assertEquals(1, recorder.errors.size());
        assertTrue(recorder.errors.get(0).contains("no turn"));
        assertTrue(recorder.answers.isEmpty());
    }

    @Test
    public void theAnswerIsNotRepeatedAsANote() throws Exception {
        FakeLlm llm = new FakeLlm().then(answerTurn("Just one line."));
        Recorder recorder = new Recorder();
        new AgentEngine(llm, new FakeTools(), "system").run("hello", recorder);

        assertEquals(List.of("Just one line."), recorder.answers);
        assertTrue(recorder.notes.isEmpty());
        assertFalse(recorder.steps.contains("Just one line."));
    }

    // ----- helpers -----------------------------------------------------------------------------

    private static Turn planTurn(String plan, String tool, JSONObject args) throws JSONException {
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", plan))
                .put(new JSONObject().put("functionCall",
                        new JSONObject().put("name", tool).put("args", args)));
        return new Turn(plan, parts, List.of(new Call(tool, args)));
    }

    private static Turn answerTurn(String text) throws JSONException {
        return new Turn(text, new JSONArray().put(new JSONObject().put("text", text)), List.of());
    }

    private static final class FakeLlm implements Llm {
        final Deque<Turn> queued = new ArrayDeque<>();
        final List<JSONArray> seen = new ArrayList<>();
        final List<JSONArray> declarations = new ArrayList<>();

        FakeLlm then(Turn turn) {
            queued.add(turn);
            return this;
        }

        @Override
        public Turn send(String systemPrompt, JSONArray contents, JSONArray tools) throws Exception {
            seen.add(new JSONArray(contents.toString()));
            declarations.add(new JSONArray(tools.toString()));
            if (queued.isEmpty()) throw new IllegalStateException("no turn queued");
            return queued.removeFirst();
        }
    }

    private static final class FakeTools implements Toolbox {
        final List<String> calls = new ArrayList<>();
        final Map<String, String> results = new HashMap<>();
        final Map<String, String> failures = new HashMap<>();

        @Override
        public String run(String name, JSONObject args) {
            calls.add(name + ":" + args.optString("query", args.optString("level", "")));
            String failure = failures.get(name);
            if (failure != null) throw new IllegalStateException(failure);
            return results.getOrDefault(name, "ok");
        }
    }

    private static final class Recorder implements AgentEngine.Listener {
        final List<String> notes = new ArrayList<>();
        final List<String> steps = new ArrayList<>();
        final List<String> answers = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void onNote(String text) {
            notes.add(text);
        }

        @Override
        public void onStep(String text) {
            steps.add(text);
        }

        @Override
        public void onAnswer(String text) {
            answers.add(text);
        }

        @Override
        public void onError(String message) {
            errors.add(message);
        }
    }
}
