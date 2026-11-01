package io.github.wavehan.geovisibility.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic alias-aware mention extraction. Word-boundary semantics match
 * the Python implementation (Latin-name assumption).
 */
public final class MentionParser {

    private MentionParser() {
    }

    public static List<BrandMatcher> buildMatchers(List<Brand> brands) {
        var out = new ArrayList<BrandMatcher>();
        for (Brand b : brands) {
            var names = new ArrayList<String>();
            names.add(b.name());
            names.addAll(b.aliases());
            var alternation = names.stream()
                    .map(Pattern::quote)
                    .reduce((a, c) -> a + "|" + c)
                    .orElseThrow();
            out.add(new BrandMatcher(b.name(),
                    Pattern.compile("\\b(?:" + alternation + ")\\b", Pattern.CASE_INSENSITIVE)));
        }
        return out;
    }

    public static List<Mention> parse(String answer, List<BrandMatcher> matchers) {
        record Hit(int start, String brand) {
        }
        var hits = new ArrayList<Hit>();
        for (var m : matchers) {
            var matcher = m.pattern().matcher(answer == null ? "" : answer);
            if (matcher.find()) {
                hits.add(new Hit(matcher.start(), m.brand()));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::start));
        var out = new ArrayList<Mention>();
        for (int i = 0; i < hits.size(); i++) {
            out.add(new Mention(hits.get(i).brand(), i + 1));
        }
        return out;
    }
}
