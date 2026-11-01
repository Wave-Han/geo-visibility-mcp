package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.util.List;

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
