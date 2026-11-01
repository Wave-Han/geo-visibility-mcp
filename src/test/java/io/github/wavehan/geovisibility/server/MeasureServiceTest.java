package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.BrandRepository;
import io.github.wavehan.geovisibility.data.GeoProperties;
import io.github.wavehan.geovisibility.data.JsonlStore;
import io.github.wavehan.geovisibility.engine.CompatEngine;
import io.github.wavehan.geovisibility.engine.EngineAdapter;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MeasureServiceTest {

    static final Path FIX = Path.of("src/test/resources/fixtures");

    static void copyFixtures(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.copy(FIX.resolve("brands.yaml"), dir.resolve("data/brands.yaml"));
        Files.copy(FIX.resolve("questions.yaml"), dir.resolve("data/questions.yaml"));
    }

    static GeoProperties props(Path dataDir) {
        var p = new GeoProperties();
        p.setDataDir(dataDir);
        var m = new GeoProperties.Measure();
        m.setConcurrency(2);
        m.setRetries(0);
        p.setMeasure(m);
        var c = new GeoProperties.EngineConf();
        c.setName("mockengine");
        c.setType("compat");
        c.setLayer("search");
        c.setBaseUrl("https://mock.test/v1");
        c.setModel("m");
        c.setApiKeyEnv("MEASURE_TEST_KEY");
        c.setEnabled(true);
        p.setEngines(List.of(c));
        return p;
    }

    @Test
    void measuresBrandAndWritesJsonl(@TempDir Path dir) throws Exception {
        copyFixtures(dir);
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                        anything())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":"
                                + "\"Alpha is the best widget for pets\"}}]}",
                        MediaType.APPLICATION_JSON));
        var props = props(dir);
        var registry = new TestRegistry(builder.build(), props);
        var svc = new MeasureService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), registry, props);

        var result = svc.measure("Alpha", null, 4);

        assertEquals(1, result.engines().size());
        var em = result.engines().get(0);
        assertEquals("mockengine", em.engine());
        assertEquals(4, em.run() + em.skipped());
        assertTrue(em.mentionRate() > 0);
        assertTrue(Files.exists(dir.resolve("output/raw/mockengine.jsonl")));
    }

    @Test
    void unknownBrandReportsError(@TempDir Path dir) throws Exception {
        copyFixtures(dir);
        var props = props(dir);
        var svc = new MeasureService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), new TestRegistry(null, props), props);
        var result = svc.measure("Nope", null, 4);
        assertTrue(result.engines().isEmpty());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void perEngineErrorsSurfaceWhenAllQuestionsFail(@TempDir Path dir) throws Exception {
        copyFixtures(dir);
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                        anything())
                .andRespond(org.springframework.test.web.client.response
                        .MockRestResponseCreators.withUnauthorizedRequest());
        var props = props(dir);
        var svc = new MeasureService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()),
                new TestRegistry(builder.build(), props), props);

        var result = svc.measure("Alpha", null, 4);
        var em = result.engines().get(0);
        assertEquals(4, em.run());
        assertFalse(em.errors().isEmpty(), "per-question failures must surface in errors");
        // all-errored run has no valid discovery pool -> null, not a fake 0.0
        assertNull(em.mentionRate());
    }

    /** Registry override injecting a RestClient-backed mock adapter. */
    static class TestRegistry extends EngineRegistry {

        private final EngineAdapter adapter;

        TestRegistry(RestClient rest, GeoProperties props) {
            super(props);
            this.adapter = rest == null ? null
                    : new CompatEngine("mockengine", "https://mock.test/v1", "m",
                            "k", null, rest, new long[0]);
        }

        @Override
        public Optional<EngineAdapter> adapter(String name) {
            return adapter == null ? Optional.empty() : Optional.of(adapter);
        }

        @Override
        public List<EngineAdapter> availableAdapters() {
            return adapter == null ? List.of() : List.of(adapter);
        }
    }
}
