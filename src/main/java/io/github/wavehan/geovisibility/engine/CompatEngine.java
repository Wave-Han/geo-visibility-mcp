package io.github.wavehan.geovisibility.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat/completions engine (zhipu, qwen, perplexity, xai).
 * extraBody is merged into the request (e.g. zhipu web_search tool).
 */
public class CompatEngine implements EngineAdapter {

    private static final ObjectMapper M = new ObjectMapper();

    private final String name;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Map<String, Object> extraBody;
    private final RestClient rest;
    private final long[] backoff;

    public CompatEngine(String name, String baseUrl, String model, String apiKey,
                        Map<String, Object> extraBody, RestClient rest, long[] backoffMillis) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.extraBody = extraBody;
        this.rest = rest;
        this.backoff = backoffMillis;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public EngineAnswer ask(String question) {
        var body = new HashMap<String, Object>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", question)));
        if (extraBody != null) {
            body.putAll(extraBody);
        }
        RestClientResponseException last = null;
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            if (attempt > 0) {
                sleep(backoff[attempt - 1]);
            }
            try {
                String raw = rest.post().uri(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return extract(raw);
            } catch (RestClientResponseException e) {
                last = e;
                int code = e.getStatusCode().value();
                if (code != 429 && code < 500) {
                    throw shorten(e);
                }
            }
        }
        throw shorten(last);
    }

    private static EngineException shorten(RestClientResponseException e) {
        String b = e.getResponseBodyAsString();
        return new EngineException(e.getStatusCode().value(),
                b == null ? "" : b.substring(0, Math.min(200, b.length())));
    }

    private static EngineException shortenLast(RestClientResponseException last) {
        if (last == null) {
            return new EngineException(0, "");
        }
        return shorten(last);
    }

    static EngineAnswer extract(String raw) {
        try {
            JsonNode root = M.readTree(raw == null ? "{}" : raw);
            String answer = root.path("choices").path(0).path("message").path("content").asText("");
            var citations = new ArrayList<String>();
            for (JsonNode c : root.path("citations")) {
                if (c.isTextual()) {
                    citations.add(c.asText());
                }
            }
            return new EngineAnswer(answer, citations, raw == null ? "" : raw);
        } catch (Exception e) {
            throw new IllegalStateException("unparseable compat payload", e);
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
