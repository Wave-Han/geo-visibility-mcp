package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.BrandRepository;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * The five MCP tools exposed to agents. Tool names use snake_case on
 * purpose: they match the spec and the Python CLI subcommands.
 */
@Service
public class McpTools {

    private final ObjectMapper json;
    private final BrandRepository brands;
    private final EngineRegistry registry;
    private final MeasureService measure;
    private final AnalysisService analyze;
    private final ProbeService probe;

    public McpTools(ObjectMapper json, BrandRepository brands, EngineRegistry registry,
                    MeasureService measure, AnalysisService analyze, ProbeService probe) {
        this.json = json;
        this.brands = brands;
        this.registry = registry;
        this.measure = measure;
        this.analyze = analyze;
        this.probe = probe;
    }

    @Tool(description = "List the brand sample: name, tier, country, aliases.")
    public String list_brands(@ToolParam(required = false) String category) {
        try {
            return json.writeValueAsString(brands.load());
        } catch (Exception e) {
            return error(e);
        }
    }

    @Tool(description = "List configured engines with layer (search/memory) and API-key availability.")
    public String list_engines() {
        try {
            return json.writeValueAsString(registry.engines());
        } catch (Exception e) {
            return error(e);
        }
    }

    @Tool(description = "Measure one brand's AI visibility: runs questions on selected engines, "
            + "saves JSONL, returns per-engine mention rate and position. enginesCsv optional "
            + "(comma-separated); questionLimit default 10 (6 discovery + brand questions).")
    public String measure_brand(@ToolParam(description = "Brand name from list_brands") String brand,
                                @ToolParam(required = false, description =
                                        "Comma-separated engine names; default all available")
                                String enginesCsv,
                                @ToolParam(required = false, description =
                                        "Total questions per engine; default 10")
                                Integer questionLimit) {
        try {
            List<String> engines = enginesCsv == null || enginesCsv.isBlank() ? null
                    : Arrays.stream(enginesCsv.split(",")).map(String::trim).toList();
            return json.writeValueAsString(measure.measure(brand, engines, questionLimit));
        } catch (Exception e) {
            return error(e);
        }
    }

    @Tool(description = "Analyze already-collected JSONL data: per-engine brand metrics and "
            + "tier/country aggregates. Layers are never pooled.")
    public String analyze_data(@ToolParam(required = false) String brand,
                               @ToolParam(required = false) String engine) {
        try {
            return json.writeValueAsString(analyze.analyze(brand, engine));
        } catch (Exception e) {
            return error(e);
        }
    }

    @Tool(description = "Probe whether an engine actually performs web retrieval "
            + "(vs answering from training memory).")
    public String probe_engine(@ToolParam(description = "Engine name from list_engines") String engine) {
        try {
            return json.writeValueAsString(probe.probe(engine));
        } catch (Exception e) {
            return error(e);
        }
    }

    private String error(Exception e) {
        try {
            return json.writeValueAsString(java.util.Map.of("error", String.valueOf(e.getMessage())));
        } catch (Exception e2) {
            return "{\"error\":\"serialization failed\"}";
        }
    }
}
