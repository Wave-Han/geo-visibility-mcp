package io.github.wavehan.geovisibility.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedactorTest {

    @Test
    void stripsKeyValues() {
        var s = "Client error '404' for url 'https://x/v1?key=AIzaSecret123.xyz&x=1'";
        assertFalse(Redactor.redact(s).contains("AIzaSecret123"));
        assertTrue(Redactor.redact(s).contains("key=***"));
    }
}
