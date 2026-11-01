package io.github.wavehan.geovisibility.core;

import java.util.List;

/** One JSONL record; field names are the cross-tool contract with the Python project. */
public record RawRecord(String engine, String qid, String dimension, String brand,
                        String question, String answer, List<String> citations,
                        String error, String ts) {
}
