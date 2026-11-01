package io.github.wavehan.geovisibility.engine;

/** Engine descriptor for the list_engines tool. */
public record EngineInfo(String name, String layer, String model, boolean available) {
}
