package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.BrandRepository;
import io.github.wavehan.geovisibility.data.GeoProperties;
import io.github.wavehan.geovisibility.data.JsonlStore;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free core into Spring. */
@Configuration
@EnableConfigurationProperties(GeoProperties.class)
public class AppConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    JsonlStore jsonlStore(ObjectMapper m) {
        return new JsonlStore(m);
    }

    @Bean
    BrandRepository brandRepository(GeoProperties p) {
        return new BrandRepository(p.getDataDir());
    }

    @Bean
    EngineRegistry engineRegistry(GeoProperties p) {
        return new EngineRegistry(p);
    }

    @Bean
    MeasureService measureService(BrandRepository b, JsonlStore s,
                                  EngineRegistry r, GeoProperties p) {
        return new MeasureService(b, s, r, p);
    }

    @Bean
    AnalysisService analysisService(BrandRepository b, JsonlStore s,
                                    EngineRegistry r, GeoProperties p) {
        return new AnalysisService(b, s, r, p);
    }

    @Bean
    ProbeService probeService(EngineRegistry r) {
        return new ProbeService(r);
    }

    @Bean
    org.springframework.ai.tool.ToolCallbackProvider toolCallbackProvider(McpTools tools) {
        return org.springframework.ai.tool.method.MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
