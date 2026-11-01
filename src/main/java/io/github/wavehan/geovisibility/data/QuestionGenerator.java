package io.github.wavehan.geovisibility.data;

import io.github.wavehan.geovisibility.core.Brand;
import io.github.wavehan.geovisibility.core.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates the question set with the SAME qid scheme as the Python tool
 * (D### discovery, C/T/P/F### brand-level), so both tools resume each
 * other's JSONL files.
 */
public class QuestionGenerator {

    private static final Map<String, String> PREFIX = Map.of(
            "comparison", "C", "trust", "T", "price_longtail", "P", "freshness", "F");
    private static final List<String> DIMS =
            List.of("comparison", "trust", "price_longtail", "freshness");

    private final List<Brand> brands;
    private final QuestionSeeds seeds;

    public QuestionGenerator(List<Brand> brands, QuestionSeeds seeds) {
        this.brands = brands;
        this.seeds = seeds;
    }

    List<String> expandDiscovery() {
        var d = seeds.discovery();
        int limit = d == null || d.maxQuestions() == null ? 40 : d.maxQuestions();
        var out = new ArrayList<String>();
        if (d != null) {
            for (String intent : d.intents()) {
                for (String sub : d.subcategories()) {
                    out.add(intent + " " + sub);
                    for (String qual : d.qualifiers()) {
                        if (!qual.isEmpty()) {
                            out.add(intent + " " + sub + " " + qual);
                        }
                    }
                }
            }
            for (String sub : d.subcategories()) {
                for (Integer price : d.budgetPrices()) {
                    out.add("best " + sub + " under $" + price);
                }
            }
        }
        Set<String> seen = new LinkedHashSet<>(out);
        return seen.stream().limit(limit).toList();
    }

    String competitorFor(Brand brand) {
        for (var c : brand.competitors()) {
            if (!c.equals(brand.name())) {
                return c;
            }
        }
        for (var b : brands) {
            if (!b.name().equals(brand.name())) {
                return b.name();
            }
        }
        return "competitor";
    }

    public List<Question> all() {
        var out = new ArrayList<Question>();
        int i = 0;
        for (String q : expandDiscovery()) {
            out.add(new Question("D%03d".formatted(++i), "discovery", q, null));
        }
        var counters = new HashMap<String, Integer>();
        for (Brand brand : brands) {
            var ctx = Map.of("brand", brand.name(),
                    "competitor", competitorFor(brand),
                    "category", seeds.categoryTerm() == null ? "" : seeds.categoryTerm());
            for (String dim : DIMS) {
                for (String tpl : seeds.brandTemplates().getOrDefault(dim, List.of())) {
                    counters.merge(dim, 1, Integer::sum);
                    var text = tpl;
                    for (var e : ctx.entrySet()) {
                        text = text.replace("{" + e.getKey() + "}", e.getValue());
                    }
                    out.add(new Question(PREFIX.get(dim) + "%03d".formatted(counters.get(dim)),
                            dim, text, brand.name()));
                }
            }
        }
        return out;
    }

    /** Up to limit questions: discovery first, then this brand's own questions. */
    public List<Question> forBrand(Brand brand, int limit) {
        var out = new ArrayList<Question>();
        var all = all();
        for (Question q : all) {
            if (out.size() >= limit) {
                break;
            }
            if (q.dimension().equals("discovery")) {
                out.add(q);
            }
        }
        for (Question q : all) {
            if (out.size() >= limit) {
                break;
            }
            if (brand.name().equals(q.brand())) {
                out.add(q);
            }
        }
        return out;
    }
}
