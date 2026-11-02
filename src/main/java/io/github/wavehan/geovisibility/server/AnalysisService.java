package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.core.Brand;
import io.github.wavehan.geovisibility.core.BrandMatcher;
import io.github.wavehan.geovisibility.core.MentionParser;
import io.github.wavehan.geovisibility.core.Metrics;
import io.github.wavehan.geovisibility.core.RawRecord;
import io.github.wavehan.geovisibility.data.BrandRepository;
import io.github.wavehan.geovisibility.data.GeoProperties;
import io.github.wavehan.geovisibility.data.JsonlStore;
import io.github.wavehan.geovisibility.engine.EngineInfo;
import io.github.wavehan.geovisibility.engine.EngineRegistry;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * analyze_data backend: metrics over already-collected JSONL, computed per
 * engine. Engines are analyzed separately (search vs memory layer); pooled
 * averages are intentionally not computed.
 */
public class AnalysisService {

    /** Per-brand metrics inside one engine's analysis. */
    public record BrandMetrics(String brand, String tier, String country,
                               Double mentionRate, Double avgPosition) {
    }

    /** One engine's analysis block. */
    public record EngineAnalysis(String engine, String layer, List<BrandMetrics> brands,
                                 Map<String, Double> tierAverages,
                                 Map<String, Double> countryAverages) {
    }

    /** Whole analyze_data result. */
    public record AnalysisResult(List<EngineAnalysis> engines, boolean dataFound, String note) {
    }

    private final BrandRepository brands;
    private final JsonlStore store;
    private final EngineRegistry registry;
    private final GeoProperties props;

    public AnalysisService(BrandRepository brands, JsonlStore store,
                           EngineRegistry registry, GeoProperties props) {
        this.brands = brands;
        this.store = store;
        this.registry = registry;
        this.props = props;
    }

    public AnalysisResult analyze(String brandFilter, String engineFilter) {
        var rawDir = props.getDataDir().resolve("output/raw");
        if (!Files.isDirectory(rawDir)) {
            return new AnalysisResult(List.of(), false,
                    "no measurement data found under " + rawDir
                            + " - run measure_brand first, or point geo.data-dir"
                            + " at the Python project checkout");
        }
        var all = brands.load();
        var matchers = MentionParser.buildMatchers(all);
        var byEngine = store.readAll(rawDir).stream()
                // metrics only over valid (non-errored) records,
                // same rule as MeasureService and the Python parser
                .filter(r -> r.error() == null)
                .filter(r -> engineFilter == null || engineFilter.isBlank()
                        || engineFilter.equals(r.engine()))
                .filter(r -> brandFilter == null || brandFilter.isBlank()
                        || brandFilter.equals(r.brand())
                        // keep discovery answers regardless of brand filter:
                        // mention rate is computed over the discovery pool
                        || "discovery".equals(r.dimension()))
                .collect(Collectors.groupingBy(RawRecord::engine));

        var layerByName = registry.engines().stream()
                .collect(Collectors.toMap(EngineInfo::name, EngineInfo::layer));

        var out = new ArrayList<EngineAnalysis>();
        for (var entry : byEngine.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            var pool = entry.getValue();
            var metrics = all.stream()
                    .filter(b -> brandFilter == null || brandFilter.isBlank()
                            || brandFilter.equals(b.name()))
                    .map(b -> new BrandMetrics(b.name(), b.tier(), b.country(),
                            Metrics.mentionRate(b.name(), pool, matchers),
                            Metrics.avgPosition(b.name(), pool, matchers)))
                    .sorted(Comparator.comparing(BrandMetrics::mentionRate,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            out.add(new EngineAnalysis(entry.getKey(),
                    layerByName.getOrDefault(entry.getKey(), "unknown"),
                    metrics,
                    Metrics.tierAverages(all, pool, matchers),
                    Metrics.countryAverages(all, pool, matchers)));
        }
        return new AnalysisResult(out, !out.isEmpty(),
                "engines are analyzed separately (search vs memory layer); "
                        + "pooled averages are intentionally not computed");
    }
}
