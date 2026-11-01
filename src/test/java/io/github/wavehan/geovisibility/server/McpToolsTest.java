package io.github.wavehan.geovisibility.server;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "geo.data-dir=src/test/resources/fixtures")
class McpToolsTest {

    @Autowired
    McpTools tools;

    @Autowired
    ToolCallbackProvider provider;

    @Test
    void fiveToolsRegistered() {
        var names = new java.util.ArrayList<String>();
        for (var cb : provider.getToolCallbacks()) {
            names.add(cb.getToolDefinition().name());
        }
        assertEquals(5, names.size());
        assertTrue(names.containsAll(java.util.List.of(
                "list_brands", "list_engines", "measure_brand", "analyze_data", "probe_engine")),
                "got: " + names);
    }

    @Test
    void listBrandsReturnsJson() {
        var json = tools.list_brands(null);
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("Alpha"));
    }

    @Test
    void listEnginesReturnsJson() {
        var json = tools.list_engines();
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("deepseek"));
    }
}
