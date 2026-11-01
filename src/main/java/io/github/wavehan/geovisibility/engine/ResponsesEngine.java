package io.github.wavehan.geovisibility.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses-style engine with the web_search tool (deepseek, openai).
 * Retries 5xx/429 with backoff; other 4xx fail fast. Error bodies are
 * truncated to 200 chars so provider reasons stay visible in records.
 */
public class ResponsesEngine implements EngineAdapter {

    private static final ObjectMapper M = new ObjectMapper();

    private final String name;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final RestClient rest;
    private final long[] backoff;

    public ResponsesEngine(String name, String baseUrl, String model, String apiKey,
                           RestClient rest, long[] backoffMillis) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.rest = rest;
        this.backoff = backoffMillis;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public EngineAnswer ask(String question) {
        var body = Map.of("model", model,
                "tools", List.of(Map.of("type", "web_search")),
                "input", question);
        org.springframework.web.client.RestClientException last = null;
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            if (attempt > 0) {
                sleep(backoff[attempt - 1]);
            }
            try {
                String raw = rest.post().uri(baseUrl + "/responses")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return extract(raw);
            } catch (RestClientResponseException e) {
                // HTTP error: retry only 429/5xx, fail fast on other 4xx
                if (!(e.getStatusCode().value() == 429
                        || e.getStatusCode().value() >= 500)) {
                    throw shorten(e);
                }
                last = e;
            } catch (org.springframework.web.client.ResourceAccessException e) {
                // network-level failure (timeout, reset, DNS): always retry
                last = e;
            }
        }
        if (last instanceof RestClientResponseException rre) {
            throw shorten(rre);
        }
        String msg = last == null ? "unknown transport failure" : String.valueOf(last);
        throw new EngineException(0, msg.substring(0, Math.min(200, msg.length())));
    }

    private static EngineException shorten(RestClientResponseException e) {
        String b = e.getResponseBodyAsString();
        return new EngineException(e.getStatusCode().value(),
                b == null ? "" : b.substring(0, Math.min(200, b.length())));
    }

    static EngineAnswer extract(String raw) {
        try {
            JsonNode root = M.readTree(raw == null ? "{}" : raw);
            var texts = new ArrayList<String>();
            var citations = new ArrayList<String>();
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) {
                    continue;
                }
                for (JsonNode part : item.path("content")) {
                    String t = part.path("type").asText();
                    if ("output_text".equals(t) || "text".equals(t)) {
                        texts.add(part.path("text").asText());
                        for (JsonNode ann : part.path("annotations")) {
                            if ("url_citation".equals(ann.path("type").asText())) {
                                String url = ann.path("url").asText(
                                        ann.path("url_citation").path("url").asText(""));
                                if (!url.isEmpty()) {
                                    citations.add(url);
                                }
                            }
                        }
                    }
                }
            }
            return new EngineAnswer(String.join("\n", texts), citations, raw == null ? "" : raw);
        } catch (Exception e) {
            throw new IllegalStateException("unparseable responses payload", e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }
}
