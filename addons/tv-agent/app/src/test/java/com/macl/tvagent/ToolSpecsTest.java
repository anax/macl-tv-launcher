package com.macl.tvagent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolSpecsTest {
    @Test
    public void everyDeclarationIsNamedDescribedAndUnique() throws Exception {
        JSONArray declarations = ToolSpecs.declarations();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < declarations.length(); i++) {
            JSONObject tool = declarations.getJSONObject(i);
            String name = tool.getString("name");
            assertTrue("duplicate " + name, names.add(name));
            assertTrue(ToolSpecs.isKnown(name));
            assertFalse(tool.optString("description").isEmpty());
        }
        assertEquals(names.size(), declarations.length());
    }

    @Test
    public void openAppAsksForOneRequiredString() throws Exception {
        JSONObject openApp = find("open_app");

        JSONObject parameters = openApp.getJSONObject("parameters");
        assertEquals("query", parameters.getJSONArray("required").getString(0));
        assertEquals("STRING", parameters.getJSONObject("properties").getJSONObject("query").getString("type"));
    }

    @Test
    public void settingsOffersOnlySafePages() throws Exception {
        JSONObject screen = find("open_settings").getJSONObject("parameters")
                .getJSONObject("properties").getJSONObject("screen");
        JSONArray options = screen.getJSONArray("enum");
        Set<String> values = new HashSet<>();
        for (int i = 0; i < options.length(); i++) values.add(options.getString(i));

        assertTrue(values.contains("wifi"));
        assertFalse(values.contains("factory_reset"));
        assertFalse(values.contains("developer"));
        assertEquals(10, values.size());
    }

    @Test
    public void volumeIsAnInteger() throws Exception {
        JSONObject level = find("volume").getJSONObject("parameters")
                .getJSONObject("properties").getJSONObject("level");
        assertEquals("INTEGER", level.getString("type"));
    }

    @Test
    public void unknownNamesAreRefused() {
        assertFalse(ToolSpecs.isKnown("uninstall_everything"));
    }

    private static JSONObject find(String name) throws Exception {
        JSONArray declarations = ToolSpecs.declarations();
        for (int i = 0; i < declarations.length(); i++) {
            JSONObject tool = declarations.getJSONObject(i);
            if (name.equals(tool.getString("name"))) return tool;
        }
        throw new AssertionError("no tool named " + name);
    }
}
