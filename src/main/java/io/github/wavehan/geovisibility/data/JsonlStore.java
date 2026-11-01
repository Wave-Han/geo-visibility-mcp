package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.RawRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Append-only JSONL store. Field names are the shared contract with the
 * Python tool; resume semantics: successful qids are skipped, errored
 * qids re-run, corrupted lines ignored.
 */
public class JsonlStore {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper mapper;

    public JsonlStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static String nowTs() {
        return LocalDateTime.now().format(TS);
    }

    /** All records from every *.jsonl in the directory; empty when dir missing. */
    public List<RawRecord> readAll(Path rawDir) {
        var out = new ArrayList<RawRecord>();
        if (!Files.isDirectory(rawDir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(rawDir)) {
            var sorted = files.filter(p -> p.toString().endsWith(".jsonl")).sorted().toList();
            for (Path p : sorted) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        out.add(mapper.readValue(line, RawRecord.class));
                    } catch (IOException ignored) {
                        // corrupted line: skip, same as the Python tool
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + rawDir, e);
        }
        return out;
    }

    /** Qids of records without an error; those are skipped on resume. */
    public Set<String> successfulQids(Path file) {
        var out = new HashSet<String>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    var r = mapper.readValue(line, RawRecord.class);
                    if (r.error() == null) {
                        out.add(r.qid());
                    }
                } catch (IOException ignored) {
                    // corrupted line
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
        return out;
    }

    /** Appends one record as a single UTF-8 JSON line; synchronized for concurrent writers. */
    public synchronized void append(Path file, RawRecord record) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(mapper.writeValueAsString(record));
                w.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot append " + file, e);
        }
    }
}
