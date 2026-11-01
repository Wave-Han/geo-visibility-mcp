package io.github.wavehan.geovisibility.engine;

import java.util.regex.Pattern;

/** Strips key=&lt;value&gt; sequences before anything is persisted. */
public final class Redactor {

    private static final Pattern KEY = Pattern.compile("key=[A-Za-z0-9._\\-]+");

    private Redactor() {
    }

    public static String redact(String msg) {
        return msg == null ? null : KEY.matcher(msg).replaceAll("key=***");
    }
}
