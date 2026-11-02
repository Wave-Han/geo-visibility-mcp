package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Regression: Spring binds Map&lt;String,Object&gt; values from YAML lists as
 * indexed maps ({"0": ...}); extra-body must surface a real list so the
 * request body carries a JSON array (zhipu 400s on "tools" as object).
 */
class ExtraBodyBindingTest {

    private static final String YAML = """
            geo:
              engines:
                - name: zhipu
                  type: compat
                  layer: memory
                  extra-body:
                    tools:
                      - type: web_search
                        web_search:
                          enable: true
                          search_result: true
            """;

    @Test
    void extraBodyListBindsAsJsonArray() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var sources = new MutablePropertySources();
        for (var ps : loader.load("yml", new ByteArrayResource(YAML.getBytes()))) {
            sources.addLast(ps);
        }
        var props = new Binder(ConfigurationPropertySources.from(sources))
                .bind("geo", GeoProperties.class).get();
        var extra = props.getEngines().get(0).getExtraBody();
        assertNotNull(extra);
        assertInstanceOf(List.class, extra.get("tools"));
        var json = new ObjectMapper().writeValueAsString(extra.get("tools"));
        assertEquals("[{\"type\":\"web_search\",\"web_search\":{\"enable\":true,\"search_result\":true}}]", json);
    }
}
