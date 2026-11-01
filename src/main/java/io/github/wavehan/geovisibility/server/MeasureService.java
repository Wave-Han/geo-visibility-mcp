package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.core.Brand;
import io.github.wavehan.geovisibility.core.BrandMatcher;
import io.github.wavehan.geovisibility.core.MentionParser;
import io.github.wavehan.geovisibility.core.Metrics;
import io.github.wavehan.geovisibility.core.RawRecord;
import io.github.wavehan.geovisibility.data.BrandRepository;
import io.github.wavehan.geovisibility.data.GeoProperties;
import io.github.wavehan.geovisibility.data.JsonlStore;
import io.github.wavehan.geovisibility.data.QuestionGenerator;
import io.github.wavehan.geovisibility.data.QuestionSeeds;
import io.github.wavehan.geovisibility.engine.EngineAdapter;
import io.github.wavehan.geovisibility.engine.EngineException;
import io.github.wavehan.geovisibility.engine.EngineInfo;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import io.github.wavehan.geovisibility.engine.Redactor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs one brand's questions across the selected engines, appends JSONL
 * records, and returns per-engine metrics computed over the engine's whole
 * file (old + new records).
 */
public class MeasureService {

    /** Per-engine summary for a measure run. */
    public record EngineMeasure(String engine, String layer, Double mentionRate,
                                 Double avgPosition, int run, int skipped,
                                 List<String> errors) {
    }

    /** Whole measure_brand result. */
    public record MeasureResult(String brand, List<EngineMeasure> engines,
                                 List<String> errors) {
    }

    private final BrandRepository brands;
    private final JsonlStore store;
    private final EngineRegistry registry;
    private final GeoProperties props;

    public MeasureService(BrandRepository brands, JsonlStore store,
                          EngineRegistry registry, GeoProperties props) {
        this.brands = brands;
        this.store = store;
        this.registry = registry;
        this.props = props;
    }

    public MeasureResult measure(String brandName, List<String> engineNames,
                                 Integer questionLimitBoxed) {
        int limit = questionLimitBoxed == null || questionLimitBoxed < 1 ? 10 : questionLimitBoxed;
        var errors = new ArrayList<String>();
        var all = brands.load();
        var brandOpt = all.stream().filter(b -> b.name().equals(brandName)).findFirst();
        if (brandOpt.isEmpty()) {
            errors.add("unknown brand: " + brandName + " (call list_brands to see the sample)");
            return new MeasureResult(brandName, List.of(), errors);
        }
        Brand brand = brandOpt.get();

        var seeds = QuestionSeeds.load(QuestionSeeds.locate(props.getDataDir()));
        var questions = new QuestionGenerator(all, seeds).forBrand(brand, limit);
        var matchers = MentionParser.buildMatchers(all);

        List<EngineAdapter> adapters;
        if (engineNames == null || engineNames.isEmpty()) {
            adapters = registry.availableAdapters();
            if (adapters.isEmpty()) {
                errors.add("no engine available - set an API key "
                        + "(see list_engines for key availability)");
            }
        } else {
            adapters = new ArrayList<>();
            for (String n : engineNames) {
                var a = registry.adapter(n);
                if (a.isEmpty()) {
                    errors.add("engine unavailable (no API key or not configured): " + n);
                } else {
                    adapters.add(a.get());
                }
            }
        }

        var engineMeasures = new ArrayList<EngineMeasure>();
        ExecutorService pool = Executors.newFixedThreadPool(props.getMeasure().getConcurrency());
        try {
            for (EngineAdapter adapter : adapters) {
                Path file = props.getDataDir()
                        .resolve("output/raw/" + adapter.name() + ".jsonl");
                var done = store.successfulQids(file);
                var todo = questions.stream().filter(q -> !done.contains(q.qid())).toList();
                var engineErrors = java.util.Collections.synchronizedList(new ArrayList<String>());

                List<Future<Object>> futures = new ArrayList<>();
                for (var q : todo) {
                    futures.add(pool.submit(() -> {
                        try {
                            var ans = adapter.ask(q.text());
                            store.append(file, new RawRecord(adapter.name(), q.qid(),
                                    q.dimension(), q.brand(), q.text(), ans.answer(),
                                    ans.citations(), null, JsonlStore.nowTs()));
                        } catch (EngineException e) {
                            String msg = Redactor.redact("HTTP " + e.status() + ": " + e.body());
                            engineErrors.add(q.qid() + " " + msg);
                            store.append(file, new RawRecord(adapter.name(), q.qid(),
                                    q.dimension(), q.brand(), q.text(), "", List.of(),
                                    msg, JsonlStore.nowTs()));
                        } catch (Exception e) {
                            // any other per-question failure becomes an error record
                            String msg = Redactor.redact(String.valueOf(e));
                            engineErrors.add(q.qid() + " " + msg);
                            store.append(file, new RawRecord(adapter.name(), q.qid(),
                                    q.dimension(), q.brand(), q.text(), "", List.of(),
                                    msg, JsonlStore.nowTs()));
                        }
                        return null;
                    }));
                }
                for (Future<Object> f : futures) {
                    f.get();
                }

                // metrics only over valid (non-errored) records, same as the Python parser
                var records = store.readAll(props.getDataDir().resolve("output/raw")).stream()
                        .filter(r -> r.engine().equals(adapter.name()))
                        .filter(r -> r.error() == null)
                        .toList();
                var layer = registry.engines().stream()
                        .filter(e -> e.name().equals(adapter.name()))
                        .findFirst().map(EngineInfo::layer).orElse("unknown");
                engineMeasures.add(new EngineMeasure(
                        adapter.name(), layer,
                        Metrics.mentionRate(brandName, records, matchers),
                        Metrics.avgPosition(brandName, records, matchers),
                        todo.size(),
                        questions.size() - todo.size(),
                        List.copyOf(engineErrors)));
            }
        } catch (Exception e) {
            errors.add(Redactor.redact(String.valueOf(e)));
        } finally {
            pool.shutdownNow();
        }
        return new MeasureResult(brandName, engineMeasures, errors);
    }
}
