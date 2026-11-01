package io.github.wavehan.geovisibility.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricsTest {

    static final List<BrandMatcher> MATCHERS = MentionParser.buildMatchers(List.of(
            new Brand("Petlibro", null, "growth", "CN", List.of(), List.of()),
            new Brand("Petkit", null, "leader", "CN", List.of(), List.of()),
            new Brand("Litter-Robot", null, "leader", "INTL", List.of(), List.of())));

    static RawRecord rec(String qid, String dim, String answer) {
        return new RawRecord("deepseek", qid, dim, null, "q", answer, List.of(), null,
                "2026-11-01T10:00:00");
    }

    @Test
    void mentionRateOverDiscoveryOnly() {
        var pool = List.of(
                rec("D001", "discovery", "Petlibro is solid."),
                rec("D002", "discovery", "Petkit and Petlibro both fine."),
                rec("D003", "discovery", "Petkit only."),
                rec("C001", "comparison", "Petlibro here but ignored."));
        assertEquals(2.0 / 3.0, Metrics.mentionRate("Petlibro", pool, MATCHERS), 1e-9);
    }

    @Test
    void mentionRateNullWithoutDiscoveryPool() {
        assertNull(Metrics.mentionRate("Petlibro",
                List.of(rec("C001", "comparison", "Petlibro")), MATCHERS));
    }

    @Test
    void avgPosition() {
        // position = order of first appearance per answer (heuristic semantics)
        var pool = List.of(
                rec("D001", "discovery", "Petlibro first."),
                rec("D002", "discovery", "Petkit first, Petlibro second."));
        assertEquals(1.5, Metrics.avgPosition("Petlibro", pool, MATCHERS), 1e-9);
    }

    @Test
    void avgPositionNullWhenNeverMentioned() {
        assertNull(Metrics.avgPosition("Litter-Robot",
                List.of(rec("D001", "discovery", "Petkit only.")), MATCHERS));
    }

    @Test
    void tierAveragesBucketUnknownTier() {
        var brands = List.of(
                new Brand("Petkit", null, "leader", "CN", List.of(), List.of()),
                new Brand("MysteryBrand", null, "weird-tier", "CN", List.of(), List.of()));
        var matchers = MentionParser.buildMatchers(brands);
        var pool = List.of(
                rec("D001", "discovery", "Petkit mentioned."),
                rec("D002", "discovery", "MysteryBrand mentioned."));
        var tiers = Metrics.tierAverages(brands, pool, matchers);
        assertEquals(0.5, tiers.get("leader"), 1e-9);
        assertEquals(0.5, tiers.get("weird-tier"), 1e-9);
    }

    @Test
    void countryAverages() {
        var brands = List.of(
                new Brand("Petkit", null, "leader", "CN", List.of(), List.of()),
                new Brand("Litter-Robot", null, "leader", "INTL", List.of(), List.of()));
        var pool = List.of(
                rec("D001", "discovery", "Petkit mentioned."),
                rec("D002", "discovery", "nothing here."));
        var countries = Metrics.countryAverages(brands, pool, MATCHERS);
        assertEquals(0.5, countries.get("CN"), 1e-9);
        assertEquals(0.0, countries.get("INTL"), 1e-9);
    }
}
