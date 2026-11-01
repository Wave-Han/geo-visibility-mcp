package io.github.wavehan.geovisibility.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** All geo.* configuration: data dir, engines, measure tuning. */
@ConfigurationProperties(prefix = "geo")
public class GeoProperties {

    private Path dataDir = Path.of("./data");

    private List<EngineConf> engines = new ArrayList<>();

    private Measure measure = new Measure();

    /** One engine entry from application.yml. */
    public static class EngineConf {

        private String name;
        private String type;          // responses | compat
        private String layer;         // search | memory
        private String baseUrl;
        private String model;
        private String apiKeyEnv;
        private boolean enabled = true;
        private Map<String, Object> extraBody;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLayer() {
            return layer;
        }

        public void setLayer(String layer) {
            this.layer = layer;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, Object> getExtraBody() {
            return extraBody;
        }

        public void setExtraBody(Map<String, Object> extraBody) {
            this.extraBody = extraBody;
        }
    }

    /** Concurrency / retry tuning. */
    public static class Measure {

        private int concurrency = 4;
        private int retries = 2;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public List<EngineConf> getEngines() {
        return engines;
    }

    public void setEngines(List<EngineConf> engines) {
        this.engines = engines;
    }

    public Measure getMeasure() {
        return measure;
    }

    public void setMeasure(Measure measure) {
        this.measure = measure;
    }
}
