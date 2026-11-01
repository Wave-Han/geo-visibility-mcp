package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.wavehan.geovisibility.core.Brand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads brands.yaml from &lt;dataDir&gt;/data/brands.yaml or &lt;dataDir&gt;/brands.yaml.
 * The schema matches the Python project (category + brands list).
 */
public class BrandRepository {

    private final Path dataDir;

    public BrandRepository(Path dataDir) {
        this.dataDir = dataDir;
    }

    @SuppressWarnings("unchecked")
    public List<Brand> load() {
        var p = dataDir.resolve("data/brands.yaml");
        if (!p.toFile().exists()) {
            p = dataDir.resolve("brands.yaml");
        }
        if (!p.toFile().exists()) {
            throw new IllegalStateException("brands.yaml not found under " + dataDir);
        }
        record Wrapper(String category, List<Map<String, Object>> brands) {
        }
        try {
            var w = new ObjectMapper(new YAMLFactory()).readValue(p.toFile(), Wrapper.class);
            var out = new ArrayList<Brand>();
            for (var b : w.brands()) {
                out.add(new Brand((String) b.get("name"), (String) b.get("domain"),
                        (String) b.get("tier"), (String) b.get("country"),
                        strList(b.get("competitors")), strList(b.get("aliases"))));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("cannot parse " + p, e);
        }
    }

    private static List<String> strList(Object o) {
        return o == null ? List.of() : new ArrayList<>((List<String>) o);
    }
}
