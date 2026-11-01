package io.github.wavehan.geovisibility.core;

import java.util.regex.Pattern;

/** Canonical brand name plus alias-or-name word-boundary pattern. */
public record BrandMatcher(String brand, Pattern pattern) {
}
