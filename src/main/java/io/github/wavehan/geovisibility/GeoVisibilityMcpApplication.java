package io.github.wavehan.geovisibility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point; runs as an MCP server over STDIO. */
@SpringBootApplication
public class GeoVisibilityMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoVisibilityMcpApplication.class, args);
    }
}
