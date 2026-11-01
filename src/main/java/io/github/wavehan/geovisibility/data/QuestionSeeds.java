package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Parsed questions.yaml; same schema as the Python project (snake_case keys). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QuestionSeeds(String categoryTerm, Discovery discovery,
                            Map<String, List<String>> brandTemplates) {

    /** Discovery question seeds. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Discovery(Integer maxQuestions, List<String> intents,
                            List<String> subcategories, List<String> qualifiers,
                            List<Integer> budgetPrices) {
    }

    public static QuestionSeeds load(Path yaml) {
        try {
            return new ObjectMapper(new YAMLFactory()).readValue(yaml.toFile(), QuestionSeeds.class);
        } catch (Exception e) {
            throw new IllegalStateException("cannot load questions.yaml at " + yaml, e);
        }
    }
}
