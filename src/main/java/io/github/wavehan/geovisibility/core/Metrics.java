package io.github.wavehan.geovisibility.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Aggregates mention metrics over record pools. Callers keep one pool per
 * engine; layers (search vs memory) are never pooled together.
 */
public final class Metrics {

    private Metrics() {
    }

    private static boolean mentionsBrand(RawRecord r, String brand, List<BrandMatcher> matchers) {
        return MentionParser.parse(r.answer(), matchers).stream()
                .anyMatch(m -> m.brand().equals(brand));
    }

    /** Share of discovery answers naming the brand; null when pool has no discovery records. */
    public static Double mentionRate(String brand, List<RawRecord> pool,
                                     List<BrandMatcher> matchers) {
        var d = pool.stream().filter(r -> "discovery".equals(r.dimension())).toList();
        if (d.isEmpty()) {
            return null;
        }
        long hits = d.stream().filter(r -> mentionsBrand(r, brand, matchers)).count();
        return (double) hits / d.size();
    }

    /** Mean first-appearance position in discovery answers; null when never mentioned. */
    public static Double avgPosition(String brand, List<RawRecord> pool,
                                     List<BrandMatcher> matchers) {
        var positions = pool.stream()
                .filter(r -> "discovery".equals(r.dimension()))
                .flatMap(r -> MentionParser.parse(r.answer(), matchers).stream())
                .filter(m -> m.brand().equals(brand))
                .mapToInt(Mention::position)
                .boxed()
                .toList();
        if (positions.isEmpty()) {
            return null;
        }
        return positions.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    /** Average mention rate per tier; brands without data are skipped, unknown tiers bucket as-is. */
    public static Map<String, Double> tierAverages(List<Brand> brands, List<RawRecord> pool,
                                                   List<BrandMatcher> matchers) {
        return groupAverages(brands, pool, matchers, Brand::tier);
    }

    /** Average mention rate per country (CN / INTL / ...). */
    public static Map<String, Double> countryAverages(List<Brand> brands, List<RawRecord> pool,
                                                      List<BrandMatcher> matchers) {
        return groupAverages(brands, pool, matchers, Brand::country);
    }

    private static Map<String, Double> groupAverages(List<Brand> brands, List<RawRecord> pool,
                                                     List<BrandMatcher> matchers,
                                                     Function<Brand, String> key) {
        Map<String, List<Double>> buckets = new HashMap<>();
        for (Brand b : brands) {
            Double rate = mentionRate(b.name(), pool, matchers);
            if (rate != null) {
                String k = key.apply(b) == null ? "unknown" : key.apply(b);
                buckets.computeIfAbsent(k, x -> new ArrayList<>()).add(rate);
            }
        }
        Map<String, Double> out = new HashMap<>();
        buckets.forEach((k, v) -> out.put(k,
                v.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));
        return out;
    }
}
