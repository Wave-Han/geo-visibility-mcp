package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestionGeneratorTest {

    static final Path FIX = Path.of("src/test/resources/fixtures");
    static final Path PY = Path.of("../geo-visibility/data");

    static boolean pythonRepoPresent() {
        return PY.toFile().exists();
    }

    @Test
    void forBrandRespectsLimitDiscoveryFirst() {
        var brands = new BrandRepository(FIX).load();
        var g = new QuestionGenerator(brands, QuestionSeeds.load(FIX.resolve("questions.yaml")));
        var qs = g.forBrand(brands.get(0), 6);
        assertEquals(6, qs.size());
        assertEquals(5, qs.stream().filter(q -> q.dimension().equals("discovery")).count());
        // first brand-level question after 5 discovery questions is comparison C001
        assertEquals("C001", qs.get(5).qid());
    }

    @Test
    void templatesRenderBrandAndCompetitor() {
        var brands = new BrandRepository(FIX).load();
        var qs = new QuestionGenerator(brands, QuestionSeeds.load(FIX.resolve("questions.yaml")))
                .forBrand(brands.get(1), 100);
        assertTrue(qs.stream().anyMatch(q -> q.text().equals("Beta vs Alpha")));
        assertTrue(qs.stream().anyMatch(q -> q.text().equals("Is Beta legit?")));
    }

    @Test
    void forBrandCapsDiscoveryAtSixEvenWhenMoreExist() {
        // spec §5: 6 discovery first, then brand-level until the cap
        var brands = new BrandRepository(FIX).load();
        var seeds = new QuestionSeeds("test devices",
                new QuestionSeeds.Discovery(40, List.of("best", "top rated"),
                        List.of("widget", "gadget"), List.of("for pets", "small homes"),
                        List.of(100, 200)),
                Map.of(
                        "comparison", List.of("{brand} vs {competitor}"),
                        "trust", List.of("Is {brand} legit?"),
                        "price_longtail", List.of("Is {brand} worth the money?"),
                        "freshness", List.of("What's new from {brand}?")));
        var g = new QuestionGenerator(brands, seeds);
        // sanity: plenty of discovery questions available
        assertTrue(g.expandDiscovery().size() >= 10);
        var qs = g.forBrand(brands.get(0), 10);
        assertEquals(6, qs.stream().filter(q -> q.dimension().equals("discovery")).count());
        assertEquals(4, qs.stream().filter(q -> q.brand() != null).count());
    }

    @Test
    void seedsLocateFallsBackToRootLevelYaml(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.copy(FIX.resolve("questions.yaml"), dir.resolve("questions.yaml"));
        assertEquals(dir.resolve("questions.yaml"), QuestionSeeds.locate(dir));
    }

    @Test
    void seedsLocatePrefersDataSubdir(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.createDirectories(dir.resolve("data"));
        java.nio.file.Files.copy(FIX.resolve("questions.yaml"), dir.resolve("data/questions.yaml"));
        java.nio.file.Files.copy(FIX.resolve("questions.yaml"), dir.resolve("questions.yaml"));
        assertEquals(dir.resolve("data/questions.yaml"), QuestionSeeds.locate(dir));
    }

    @Test
    @EnabledIf("pythonRepoPresent")
    void qidSchemeMatchesPythonGolden() throws Exception {
        var mapper = new ObjectMapper();
        List<Question> golden = mapper.readValue(
                Path.of("src/test/resources/golden/questions.json").toFile(),
                mapper.getTypeFactory().constructCollectionType(List.class, Question.class));
        var generated = new QuestionGenerator(
                new BrandRepository(PY).load(),
                QuestionSeeds.load(PY.resolve("questions.yaml"))).all();
        assertEquals(golden.size(), generated.size());
        for (int i = 0; i < golden.size(); i++) {
            assertEquals(golden.get(i).qid(), generated.get(i).qid(), "index " + i);
            assertEquals(golden.get(i).text(), generated.get(i).text(), "index " + i);
            assertEquals(golden.get(i).brand(), generated.get(i).brand(), "index " + i);
        }
    }
}
