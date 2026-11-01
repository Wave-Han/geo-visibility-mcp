package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.BrandRepository;
import io.github.wavehan.geovisibility.data.GeoProperties;
import io.github.wavehan.geovisibility.data.JsonlStore;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {

    static final Path FIX = Path.of("src/test/resources/fixtures");

    @Test
    void analyzesPerEngineWithoutPooling(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.createDirectories(dir.resolve("output/raw"));
        Files.copy(FIX.resolve("brands.yaml"), dir.resolve("data/brands.yaml"));
        Files.copy(FIX.resolve("raw/synthetic.jsonl"), dir.resolve("output/raw/synthetic.jsonl"));

        var svc = new AnalysisService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), registryWithEngines("e1", "e2"), props(dir));
        var result = svc.analyze(null, null);

        assertTrue(result.dataFound());
        assertEquals(2, result.engines().size());
        var e1 = result.engines().get(0);
        assertEquals("e1", e1.engine());
        var alpha = find(e1, "Alpha");
        assertEquals(0.5, alpha.mentionRate(), 1e-9);
        assertEquals(1.0, alpha.avgPosition(), 1e-9);
        var e2 = result.engines().get(1);
        assertEquals(0.0, find(e2, "Alpha").mentionRate(), 1e-9);
        // note must state layers are kept separate
        assertTrue(result.note().contains("separately"));
    }

    @Test
    void missingOutputDirReturnsDataFoundFalse(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.copy(FIX.resolve("brands.yaml"), dir.resolve("data/brands.yaml"));
        var svc = new AnalysisService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), registryWithEngines("e1"), props(dir));
        var result = svc.analyze(null, null);
        assertFalse(result.dataFound());
        assertNotNull(result.note());
    }

    static io.github.wavehan.geovisibility.server.AnalysisService.BrandMetrics find(
            AnalysisService.EngineAnalysis e, String name) {
        return e.brands().stream()
                .filter(b -> b.brand().equals(name))
                .findFirst()
                .orElseThrow();
    }

    static GeoProperties props(Path dataDir) {
        var p = new GeoProperties();
        p.setDataDir(dataDir);
        var m = new GeoProperties.Measure();
        p.setMeasure(m);
        return p;
    }

    static EngineRegistry registryWithEngines(String... names) {
        var p = new GeoProperties();
        var list = new ArrayList<GeoProperties.EngineConf>();
        for (String n : names) {
            var c = new GeoProperties.EngineConf();
            c.setName(n);
            c.setType("compat");
            c.setLayer("search");
            c.setBaseUrl("https://x.test");
            c.setModel("m");
            c.setApiKeyEnv("MISSING_KEY_" + n);
            c.setEnabled(true);
            list.add(c);
        }
        p.setEngines(list);
        return new EngineRegistry(p);
    }
}
