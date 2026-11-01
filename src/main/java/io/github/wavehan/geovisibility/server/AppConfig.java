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
}
