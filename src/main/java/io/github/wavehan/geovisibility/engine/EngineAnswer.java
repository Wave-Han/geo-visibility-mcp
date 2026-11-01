package io.github.wavehan.geovisibility.engine;

import java.util.List;

/** Successful engine response; rawJson kept in memory for probe inspection. */
public record EngineAnswer(String answer, List<String> citations, String rawJson) {
}
