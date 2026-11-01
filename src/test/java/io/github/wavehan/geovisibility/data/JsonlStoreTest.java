package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.RawRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class JsonlStoreTest {

    static final JsonlStore STORE = new JsonlStore(new ObjectMapper());

    static RawRecord rec(String qid, String error) {
        return new RawRecord("deepseek", qid, "discovery", null, "q", "a",
                List.of(), error, "2026-11-01T10:00:00");
    }

    @Test
    void successfulQidsSkipErrors(@TempDir Path dir) {
        var f = dir.resolve("deepseek.jsonl");
        STORE.append(f, rec("D001", null));
        STORE.append(f, rec("D002", "HTTP 500: key=SECRET123 body"));
        STORE.append(f, rec("D003", null));
        assertEquals(Set.of("D001", "D003"), STORE.successfulQids(f));
    }

    @Test
    void readAllSkipsCorruptedLines(@TempDir Path dir) throws Exception {
        var f = dir.resolve("deepseek.jsonl");
        STORE.append(f, rec("D001", null));
        Files.writeString(f, "not-json\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(1, STORE.readAll(dir).size());
    }

    @Test
    void readAllMissingDirReturnsEmpty(@TempDir Path dir) {
        assertTrue(STORE.readAll(dir.resolve("nope")).isEmpty());
    }

    @Test
    void fiftyConcurrentAppendsProduceFiftyLines(@TempDir Path dir) throws Exception {
        var f = dir.resolve("zhipu.jsonl");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int n = i;
            tasks.add(() -> {
                STORE.append(f, rec("D%03d".formatted(n), null));
                return null;
            });
        }
        pool.invokeAll(tasks);
        pool.shutdown();
        assertEquals(50, Files.readAllLines(f).size());
        assertEquals(50, STORE.readAll(dir).size());
    }
}
