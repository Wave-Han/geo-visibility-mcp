package io.github.wavehan.geovisibility.engine;

import io.github.wavehan.geovisibility.data.GeoProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds adapters from config. Availability = the env var named by
 * api-key-env exists and is non-blank; unavailable engines are skipped
 * (or reported by callers).
 */
public class EngineRegistry {

    private final GeoProperties props;
    private final RestClient rest;

    public EngineRegistry(GeoProperties props) {
        this.props = props;
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(20_000);
        f.setReadTimeout(120_000);
        this.rest = RestClient.builder().requestFactory(f).build();
    }

    public List<EngineInfo> engines() {
        var out = new ArrayList<EngineInfo>();
        for (var e : props.getEngines()) {
            if (!e.isEnabled()) {
                continue;
            }
            out.add(new EngineInfo(e.getName(), e.getLayer(), e.getModel(), hasKey(e)));
        }
        return out;
    }

    public Optional<EngineAdapter> adapter(String name) {
        for (var e : props.getEngines()) {
            if (!e.isEnabled() || !e.getName().equals(name) || !hasKey(e)) {
                continue;
            }
            long[] backoff = new long[props.getMeasure().getRetries()];
            for (int i = 0; i < backoff.length; i++) {
                backoff[i] = 1000L * (i + 1);
            }
            if ("responses".equals(e.getType())) {
                return Optional.of(new ResponsesEngine(e.getName(), e.getBaseUrl(),
                        e.getModel(), System.getenv(e.getApiKeyEnv()), rest, backoff));
            }
            return Optional.of(new CompatEngine(e.getName(), e.getBaseUrl(), e.getModel(),
                    System.getenv(e.getApiKeyEnv()), e.getExtraBody(), rest, backoff));
        }
        return Optional.empty();
    }

    public List<EngineAdapter> availableAdapters() {
        var out = new ArrayList<EngineAdapter>();
        for (var e : props.getEngines()) {
            if (!e.isEnabled()) {
                continue;
            }
            adapter(e.getName()).ifPresent(out::add);
        }
        return out;
    }

    private boolean hasKey(GeoProperties.EngineConf e) {
        var v = System.getenv(e.getApiKeyEnv());
        return v != null && !v.isBlank();
    }
}
