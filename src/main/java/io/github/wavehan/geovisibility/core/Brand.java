package io.github.wavehan.geovisibility.core;

import java.util.List;

/** Measurement target brand with optional product-line aliases. */
public record Brand(String name, String domain, String tier, String country,
                    List<String> competitors, List<String> aliases) {
}
