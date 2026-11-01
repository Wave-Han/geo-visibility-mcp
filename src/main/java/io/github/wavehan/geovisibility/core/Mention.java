package io.github.wavehan.geovisibility.core;

/** One brand mention inside an answer; position = 1-based order of first appearance. */
public record Mention(String brand, int position) {
}
