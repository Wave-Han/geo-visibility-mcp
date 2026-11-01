package io.github.wavehan.geovisibility.engine;

/** Terminal adapter failure after retries; body truncated to 200 chars by the adapter. */
public class EngineException extends RuntimeException {

    private final int status;
    private final String body;

    public EngineException(int status, String body) {
        super("HTTP " + status + ": " + body);
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
