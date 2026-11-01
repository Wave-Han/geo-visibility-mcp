package io.github.wavehan.geovisibility.engine;

import io.github.wavehan.geovisibility.data.GeoProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineRegistryTest {

    static GeoProperties props() {
        var p = new GeoProperties();
        var ds = new GeoProperties.EngineConf();
        ds.setName("deepseek");
        ds.setType("responses");
        ds.setLayer("search");
        ds.setBaseUrl("https://api.deepseek.com/v1");
        ds.setModel("deepseek-v4-flash");
        ds.setApiKeyEnv("DEEPSEEK_API_KEY");
        ds.setEnabled(true);
        var zp = new GeoProperties.EngineConf();
        zp.setName("zhipu");
        zp.setType("compat");
        zp.setLayer("memory");
        zp.setBaseUrl("https://open.bigmodel.cn/api/paas/v4");
        zp.setModel("glm-4-flash");
        zp.setApiKeyEnv("ZHIPU_DEFINITELY_MISSING_KEY_XYZ");
        zp.setEnabled(true);
        p.setEngines(List.of(ds, zp));
        var m = new GeoProperties.Measure();
        m.setConcurrency(2);
        m.setRetries(2);
        p.setMeasure(m);
        return p;
    }

    @Test
    void missingEnvKeyMeansUnavailable() {
        var reg = new EngineRegistry(props());
        var engines = reg.engines();
        assertEquals(List.of("deepseek", "zhipu"),
                engines.stream().map(EngineInfo::name).toList());
        // env var with this name cannot exist -> never available
        var zhipu = engines.get(1);
        assertEquals("memory", zhipu.layer());
        assertFalse(zhipu.available());
        assertTrue(reg.adapter("zhipu").isEmpty());
    }

    @Test
    void adapterBuiltWhenKeyPresent() {
        Assumptions.assumeTrue(System.getenv("DEEPSEEK_API_KEY") != null,
                "DEEPSEEK_API_KEY not set in this environment");
        var reg = new EngineRegistry(props());
        assertTrue(reg.adapter("deepseek").isPresent());
        assertTrue(reg.engines().get(0).available());
    }
}
