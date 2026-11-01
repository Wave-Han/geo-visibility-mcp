package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.data.GeoProperties;
import io.github.wavehan.geovisibility.engine.CompatEngine;
import io.github.wavehan.geovisibility.engine.EngineAdapter;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProbeServiceTest {

    static GeoProperties props() {
        var p = new GeoProperties();
        var c = new GeoProperties.EngineConf();
        c.setName("probe-engine");
        c.setType("compat");
        c.setLayer("search");
        c.setBaseUrl("https://probe.test/v1");
        c.setModel("m");
        c.setApiKeyEnv("PROBE_TEST_KEY");
        c.setEnabled(true);
        p.setEngines(List.of(c));
        var m = new GeoProperties.Measure();
        p.setMeasure(m);
        return p;
    }

    static ProbeService probeWith(RestClient rest) {
        return new ProbeService(new TestRegistry(rest, props()));
    }

    @Test
    void citationsMeanGrounded() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("https://probe.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"The Roborock Saros 10R and Dreame X50 lead in 2026."}}],
                         "citations":["https://vacuumwars.com/x"]}""",
                        MediaType.APPLICATION_JSON));
        var result = probeWith(builder.build()).probe("probe-engine");
        assertTrue(result.groundedLikely());
        assertFalse(result.datedPhrasingFound());
        assertTrue(result.evidence().contains("Saros"));
    }

    @Test
    void datedPhrasingMeansMemory() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("https://probe.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"As of my last update, the Roomba s9+ is the flagship."}}]}""",
                        MediaType.APPLICATION_JSON));
        var result = probeWith(builder.build()).probe("probe-engine");
        assertFalse(result.groundedLikely());
        assertTrue(result.datedPhrasingFound());
    }

    @Test
    void unavailableEngineReportsMessage() {
        var result = probeWith(null).probe("probe-engine");
        assertFalse(result.groundedLikely());
        assertTrue(result.evidence().contains("unavailable"));
    }

    /** Registry override injecting a mock adapter. */
    static class TestRegistry extends EngineRegistry {

        private final EngineAdapter adapter;

        TestRegistry(RestClient rest, GeoProperties props) {
            super(props);
            this.adapter = rest == null ? null
                    : new CompatEngine("probe-engine", "https://probe.test/v1", "m",
                            "k", null, rest, new long[0]);
        }

        @Override
        public Optional<EngineAdapter> adapter(String name) {
            return adapter == null ? Optional.empty() : Optional.of(adapter);
        }
    }
}
