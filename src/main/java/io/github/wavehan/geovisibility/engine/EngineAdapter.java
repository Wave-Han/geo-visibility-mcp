package io.github.wavehan.geovisibility.engine;

/** One adapter per configured engine. */
public interface EngineAdapter {

    String name();

    EngineAnswer ask(String question);
}
