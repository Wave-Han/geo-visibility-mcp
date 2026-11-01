package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.engine.EngineAnswer;
import io.github.wavehan.geovisibility.engine.EngineException;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import io.github.wavehan.geovisibility.engine.Redactor;

import java.util.regex.Pattern;

/**
 * probe_engine backend: asks a current-events question and reports whether
 * the engine shows retrieval artifacts (grounded) or memory markers (dated).
 */
public class ProbeService {

    /** probe_engine result. */
    public record ProbeResult(String engine, boolean groundedLikely,
                               boolean datedPhrasingFound, String evidence) {
    }

    private static final String QUESTION =
            "What are the best robot vacuums right now? Name the current flagship models.";
    private static final Pattern DATED = Pattern.compile("(?i)as of (my|our) (last )?update");

    private final EngineRegistry registry;

    public ProbeService(EngineRegistry registry) {
        this.registry = registry;
    }

    public ProbeResult probe(String engineName) {
        var adapter = registry.adapter(engineName);
        if (adapter.isEmpty()) {
            return new ProbeResult(engineName, false, false,
                    "engine unavailable (no API key or not configured)");
        }
        EngineAnswer ans;
        try {
            ans = adapter.get().ask(QUESTION);
        } catch (EngineException e) {
            return new ProbeResult(engineName, false, false,
                    "probe failed: HTTP " + e.status() + ": " + Redactor.redact(e.body()));
        }
        boolean grounded = !ans.citations().isEmpty()
                || ans.rawJson().contains("url_citation")
                || ans.rawJson().contains("groundingMetadata")
                || ans.rawJson().contains("web_search");
        boolean dated = DATED.matcher(ans.answer() == null ? "" : ans.answer()).find();
        String evidence = ans.answer() == null ? "" : ans.answer();
        evidence = Redactor.redact(evidence);
        evidence = evidence.substring(0, Math.min(200, evidence.length()));
        return new ProbeResult(engineName, grounded, dated, evidence);
    }
}
