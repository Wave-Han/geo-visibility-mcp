# geo-visibility-mcp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Java 17 / Spring Boot MCP server exposing 5 brand AI-visibility tools over STDIO, data-compatible with the Python geo-visibility toolkit.

**Architecture:** Single Maven module, four packages. `core` (domain + parsing + metrics, framework-free), `data` (YAML config + JSONL store), `engine` (2 HTTP adapters + registry), `server` (Spring AI `@Tool` layer + services). Java reads/writes the Python project's files verbatim.

**Tech Stack:** Java 17, Maven, Spring Boot 3.5.x, Spring AI 2.0.x (`spring-ai-starter-mcp-server`), Jackson + jackson-dataformat-yaml, JUnit 5, MockRestServiceServer.

**Spec:** `docs/specs/2026-11-01-mcp-server-design.md` (read both; the plan argues from the spec)

## Global Constraints

- Java 17; single Maven module; `io.github.wavehan:geo-visibility-mcp:0.1.0`; package root `io.github.wavehan.geovisibility`
- `core` package has NO Spring dependencies (pure Java + JUnit)
- JSONL fields exactly `engine,qid,dimension,brand,question,answer,citations,error,ts`; ts = `yyyy-MM-dd'T'HH:mm:ss` local; UTF-8; append-only; one record per line
- qid scheme `D###/C###/T###/P###/F###` byte-identical to the Python tool
- API keys only from environment (`api-key-env`); all persisted error strings pass `Redactor.redact`; HTTP error bodies truncated to 200 chars
- Never aggregate across engine layers; every engine tagged `search` or `memory`
- Code identifiers and comments in English; fixtures synthetic; `output/` never committed; MIT license
- Verify Spring Boot / Spring AI versions against Maven Central before first build (Task 1 Step 1) — the 2.0.x line moves fast

## Review Focus

1. **Brand names with regex metacharacters** (e.g. `Litter-Robot`) must match literally — test in Task 2.
2. **Requested engine whose env key is missing** is reported in `errors`, other engines still measured — Task 8.
3. **Missing `output/` directory** makes `analyze_data` return empty results with a note, not an exception — Task 9.
4. **Concurrent appends** (thread pool) must produce N valid JSONL lines, no interleaving corruption — Task 5.
5. **Oversized/HTML error body** (5 KB) is truncated to 200 chars and redacted before persisting — Task 6.

---

### Task 1: Maven skeleton, versions, CI

**Files:**
- Create: `pom.xml`, `src/main/java/io/github/wavehan/geovisibility/GeoVisibilityMcpApplication.java`, `src/main/resources/application.yml`, `src/test/java/io/github/wavehan/geovisibility/ApplicationContextTest.java`, `.github/workflows/ci.yml`, `LICENSE` (MIT, copyright "geo-visibility contributors")

**Interfaces:**
- Produces: runnable Spring context all later tasks extend; dependency set.

- [ ] **Step 1: Verify current artifact versions**

Run: `WebSearch "spring-ai-starter-mcp-server maven central latest version"` (or check https://central.sonatype.com). Pin: Spring Boot parent `3.5.x` latest patch, `spring-ai-bom` `2.0.x` latest. Record versions in pom below.

- [ ] **Step 2: Write pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.7</version> <!-- replace with verified latest 3.5.x -->
    <relativePath/>
  </parent>
  <groupId>io.github.wavehan</groupId>
  <artifactId>geo-visibility-mcp</artifactId>
  <version>0.1.0</version>
  <properties>
    <java.version>17</java.version>
    <spring-ai.version>2.0.1</spring-ai.version> <!-- replace with verified latest -->
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>${spring-ai.version}</version>
      <type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-mcp-server</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId><scope>test</scope>
    </dependency>
  </dependencies>
  <build><plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins></build>
</project>
```

- [ ] **Step 3: Application + yml**

```java
package io.github.wavehan.geovisibility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GeoVisibilityMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeoVisibilityMcpApplication.class, args);
    }
}
```

```yaml
# application.yml
spring:
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        name: geo-visibility-mcp
        version: 0.1.0
geo:
  data-dir: ./data
  engines: []
  measure:
    concurrency: 4
    retries: 2
```

- [ ] **Step 4: Failing-then-passing context test**

```java
package io.github.wavehan.geovisibility;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationContextTest {
    @Test
    void contextLoads() { }
}
```

Run: `mvn test`. Expect: first run may fail if starter version wrong — fix version, rerun until PASS.

- [ ] **Step 5: CI**

```yaml
# .github/workflows/ci.yml
name: build
on: [push, pull_request]
jobs:
  verify:
    runs-on: ubuntu-latest
    strategy: { matrix: { java: ["17", "21"] } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "${{ matrix.java }}" }
      - run: mvn -B -ntp verify
```

- [ ] **Step 6: Commit** — `git add -A && git commit -m "feat: maven skeleton with spring-ai mcp server starter"`

---

### Task 2: core domain + MentionParser (alias matching)

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/core/Brand.java`, `core/Mention.java`, `core/BrandMatcher.java`, `core/MentionParser.java`
- Test: `src/test/java/io/github/wavehan/geovisibility/core/MentionParserTest.java`

**Interfaces:**
- Produces:
  - `record Brand(String name, String domain, String tier, String country, List<String> competitors, List<String> aliases)`
  - `record Mention(String brand, int position)`
  - `record BrandMatcher(String brand, java.util.regex.Pattern pattern)`
  - `static List<BrandMatcher> MentionParser.buildMatchers(List<Brand> brands)`
  - `static List<Mention> MentionParser.parse(String answer, List<BrandMatcher> matchers)`

- [ ] **Step 1: Write failing tests (ported from Python + metachar case)**

```java
package io.github.wavehan.geovisibility.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MentionParserTest {
    static List<BrandMatcher> M = MentionParser.buildMatchers(List.of(
        new Brand("iRobot", "irobot.com", "leader", "INTL", List.of(), List.of("Roomba")),
        new Brand("Ecovacs", "ecovacs.com", "leader", "CN", List.of(), List.of("Deebot")),
        new Brand("Roborock", "roborock.com", "leader", "CN", List.of(), List.of()),
        new Brand("Litter-Robot", "litter-robot.com", "growth", "INTL", List.of(), List.of())));

    @Test void positionOrderByFirstAppearance() {
        var answer = "Roborock is the top pick. The Roomba j9 is classic; Ecovacs third.";
        var mentions = MentionParser.parse(answer, M);
        assertEquals(List.of("Roborock", "iRobot", "Ecovacs"),
                mentions.stream().map(Mention::brand).toList());
        assertEquals(List.of(1, 2, 3), mentions.stream().map(Mention::position).toList());
    }
    @Test void aliasCountsAsCanonical() {
        var m = MentionParser.parse("The Roomba Combo is the best budget pick.", M);
        assertEquals(List.of("iRobot"), m.stream().map(Mention::brand).toList());
    }
    @Test void wordBoundaryPreventsPartialMatch() {
        assertTrue(MentionParser.parse("Robotucky is different.", M).isEmpty());
    }
    @Test void metacharBrandNameMatchesLiterally() {
        var m = MentionParser.parse("The Litter-Robot 4 is great.", M);
        assertEquals(List.of("Litter-Robot"), m.stream().map(Mention::brand).toList());
    }
    @Test void caseInsensitive() {
        assertFalse(MentionParser.parse("ROBOROCK wins.", M).isEmpty());
    }
    @Test void emptyAnswerYieldsEmpty() {
        assertTrue(MentionParser.parse("", M).isEmpty());
    }
}
```

- [ ] **Step 2: Run** — `mvn test -Dtest=MentionParserTest`. Expected: compile FAIL (types missing).
- [ ] **Step 3: Implement**

```java
// Brand.java
package io.github.wavehan.geovisibility.core;
import java.util.List;

/** Measurement target brand with optional product-line aliases. */
public record Brand(String name, String domain, String tier, String country,
                    List<String> competitors, List<String> aliases) { }

// Mention.java
package io.github.wavehan.geovisibility.core;

/** One brand mention inside an answer; position = 1-based order of first appearance. */
public record Mention(String brand, int position) { }

// BrandMatcher.java
package io.github.wavehan.geovisibility.core;
import java.util.regex.Pattern;

/** Canonical brand name plus alias-or-name word-boundary pattern. */
public record BrandMatcher(String brand, Pattern pattern) { }

// MentionParser.java
package io.github.wavehan.geovisibility.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Deterministic alias-aware mention extraction. Latin-name assumption (same as Python). */
public final class MentionParser {
    private MentionParser() { }

    public static List<BrandMatcher> buildMatchers(List<Brand> brands) {
        var out = new ArrayList<BrandMatcher>();
        for (Brand b : brands) {
            var names = new ArrayList<String>();
            names.add(b.name());
            names.addAll(b.aliases());
            var alternation = names.stream()
                    .map(Pattern::quote)
                    .reduce((a, c) -> a + "|" + c).orElseThrow();
            out.add(new BrandMatcher(b.name(),
                    Pattern.compile("\\b(?:" + alternation + ")\\b", Pattern.CASE_INSENSITIVE)));
        }
        return out;
    }

    public static List<Mention> parse(String answer, List<BrandMatcher> matchers) {
        record Hit(int start, String brand) { }
        var hits = new ArrayList<Hit>();
        for (var m : matchers) {
            var matcher = m.pattern().matcher(answer);
            if (matcher.find()) hits.add(new Hit(matcher.start(), m.brand()));
        }
        hits.sort(Comparator.comparingInt(Hit::start));
        var out = new ArrayList<Mention>();
        for (int i = 0; i < hits.size(); i++) out.add(new Mention(hits.get(i).brand(), i + 1));
        return out;
    }
}
```

- [ ] **Step 4: Run** — `mvn test -Dtest=MentionParserTest`. Expected: PASS (6 tests).
- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(core): alias-aware mention parser"`

---

### Task 3: Metrics (framework-free)

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/core/RawRecord.java`, `core/Metrics.java`
- Test: `src/test/java/io/github/wavehan/geovisibility/core/MetricsTest.java`

**Interfaces:**
- Consumes: `Brand`, `Mention`, `BrandMatcher`, `MentionParser` (Task 2)
- Produces:
  - `record RawRecord(String engine, String qid, String dimension, String brand, String question, String answer, List<String> citations, String error, String ts)`
  - `static Double Metrics.mentionRate(String brand, List<RawRecord> pool, List<BrandMatcher> matchers)` — share of discovery answers mentioning brand; `null` if pool has no discovery records
  - `static Double Metrics.avgPosition(String brand, List<RawRecord> pool, List<BrandMatcher> matchers)` — `null` if never mentioned
  - `static Map<String, Double> Metrics.tierAverages(List<Brand> brands, List<RawRecord> pool, List<BrandMatcher> matchers)`
  - `static Map<String, Double> Metrics.countryAverages(List<Brand> brands, List<RawRecord> pool, List<BrandMatcher> matchers)` — unknown tier/country bucket under its own literal key

- [ ] **Step 1: Failing tests (ported)**

```java
package io.github.wavehan.geovisibility.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MetricsTest {
    static final List<BrandMatcher> M = MentionParser.buildMatchers(List.of(
        new Brand("Petlibro", null, "growth", "CN", List.of(), List.of()),
        new Brand("Petkit", null, "leader", "CN", List.of(), List.of()),
        new Brand("Litter-Robot", null, "leader", "INTL", List.of(), List.of())));

    static RawRecord rec(String qid, String dim, String answer) {
        return new RawRecord("deepseek", qid, dim, null, "q", answer, List.of(), null, "2026-11-01T10:00:00");
    }

    @Test void mentionRateOverDiscoveryOnly() {
        var pool = List.of(rec("D001", "discovery", "Petlibro is solid."),
                rec("D002", "discovery", "Petkit and Petlibro both fine."),
                rec("D003", "discovery", "Petkit only."),
                rec("C001", "comparison", "Petlibro here but ignored."));
        assertEquals(2.0 / 3.0, Metrics.mentionRate("Petlibro", pool, M), 1e-9);
    }
    @Test void mentionRateNullWithoutDiscoveryPool() {
        assertNull(Metrics.mentionRate("Petlibro",
                List.of(rec("C001", "comparison", "Petlibro")), M));
    }
    @Test void avgPosition() {
        var pool = List.of(rec("D001", "discovery", "Petlibro first."),
                rec("D002", "discovery", "Petkit first, Petlibro third."));
        assertEquals(2.0, Metrics.avgPosition("Petlibro", pool, M), 1e-9);
    }
    @Test void avgPositionNullWhenNeverMentioned() {
        assertNull(Metrics.avgPosition("Litter-Robot",
                List.of(rec("D001", "discovery", "Petkit only.")), M));
    }
    @Test void tierAveragesBucketUnknownTier() {
        var brands = List.of(
                new Brand("Petkit", null, "leader", "CN", List.of(), List.of()),
                new Brand("MysteryBrand", null, "weird-tier", "CN", List.of(), List.of()));
        var pool = List.of(rec("D001", "discovery", "Petkit mentioned."),
                rec("D002", "discovery", "MysteryBrand mentioned."));
        var tiers = Metrics.tierAverages(brands, pool, M);
        assertEquals(1.0, tiers.get("leader"), 1e-9);
        assertEquals(1.0, tiers.get("weird-tier"), 1e-9);
    }
    @Test void countryAverages() {
        var brands = List.of(
                new Brand("Petkit", null, "leader", "CN", List.of(), List.of()),
                new Brand("Litter-Robot", null, "leader", "INTL", List.of(), List.of()));
        var pool = List.of(rec("D001", "discovery", "Petkit mentioned."),
                rec("D002", "discovery", "nothing here."));
        assertEquals(1.0, Metrics.countryAverages(brands, pool, M).get("CN"), 1e-9);
        assertEquals(0.0, Metrics.countryAverages(brands, pool, M).get("INTL"), 1e-9);
    }
}
```

- [ ] **Step 2: Run** — expect compile FAIL.
- [ ] **Step 3: Implement**

```java
// RawRecord.java
package io.github.wavehan.geovisibility.core;
import java.util.List;

/** One JSONL record; field names are the cross-tool contract with the Python project. */
public record RawRecord(String engine, String qid, String dimension, String brand,
                        String question, String answer, List<String> citations,
                        String error, String ts) { }

// Metrics.java
package io.github.wavehan.geovisibility.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aggregates mention metrics over record pools. Never pools across engines (caller's duty). */
public final class Metrics {
    private Metrics() { }

    static boolean mentions(List<RawRecord> discovery, String brand, List<BrandMatcher> m,
                            RawRecord r) {
        return MentionParser.parse(r.answer(), m).stream()
                .anyMatch(x -> x.brand().equals(brand));
    }

    public static Double mentionRate(String brand, List<RawRecord> pool,
                                     List<BrandMatcher> matchers) {
        var d = pool.stream().filter(r -> "discovery".equals(r.dimension())).toList();
        if (d.isEmpty()) return null;
        long hits = d.stream().filter(r -> mentions(d, brand, matchers, r)).count();
        return (double) hits / d.size();
    }

    public static Double avgPosition(String brand, List<RawRecord> pool,
                                     List<BrandMatcher> matchers) {
        var positions = pool.stream()
                .filter(r -> "discovery".equals(r.dimension()))
                .flatMap(r -> MentionParser.parse(r.answer(), matchers).stream())
                .filter(m -> m.brand().equals(brand))
                .mapToInt(Mention::position).boxed().toList();
        if (positions.isEmpty()) return null;
        return positions.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    public static Map<String, Double> tierAverages(List<Brand> brands, List<RawRecord> pool,
                                                   List<BrandMatcher> matchers) {
        return groupAverages(brands, pool, matchers, Brand::tier);
    }

    public static Map<String, Double> countryAverages(List<Brand> brands, List<RawRecord> pool,
                                                      List<BrandMatcher> matchers) {
        return groupAverages(brands, pool, matchers, Brand::country);
    }

    private static Map<String, Double> groupAverages(List<Brand> brands, List<RawRecord> pool,
                                                     List<BrandMatcher> matchers,
                                                     java.util.function.Function<Brand, String> key) {
        Map<String, List<Double>> buckets = new HashMap<>();
        for (Brand b : brands) {
            Double rate = mentionRate(b.name(), pool, matchers);
            if (rate != null) {
                String k = key.apply(b) == null ? "unknown" : key.apply(b);
                buckets.computeIfAbsent(k, x -> new java.util.ArrayList<>()).add(rate);
            }
        }
        Map<String, Double> out = new HashMap<>();
        buckets.forEach((k, v) -> out.put(k, v.stream().mapToDouble(Double::doubleValue)
                .average().orElseThrow()));
        return out;
    }
}
```

- [ ] **Step 4: Run** — `mvn test -Dtest=MetricsTest`. Expected: PASS (6 tests).
- [ ] **Step 5: Commit** — `git commit -am "feat(core): mention metrics with tier/country aggregation"`

---

### Task 4: data layer — YAML repositories + question generator (qid parity)

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/data/QuestionSeeds.java`, `data/BrandRepository.java`, `data/QuestionGenerator.java`
- Test: `src/test/java/io/github/wavehan/geovisibility/data/QuestionGeneratorTest.java`
- Create test fixture: `src/test/resources/fixtures/brands.yaml`, `src/test/resources/fixtures/questions.yaml`, `src/test/resources/golden/questions.json`

**Interfaces:**
- Consumes: `Brand`, `Question` — create `core/Question.java`:
  `record Question(String qid, String dimension, String text, String brand)` (brand nullable)
- Produces:
  - `record QuestionSeeds(String categoryTerm, Discovery discovery, Map<String, List<String>> brandTemplates)` with `record Discovery(Integer maxQuestions, List<String> intents, List<String> subcategories, List<String> qualifiers, List<Integer> budgetPrices)`
  - `BrandRepository(Path dataDir)` with `List<Brand> load()` (fails with clear message if `data/brands.yaml` missing)
  - `QuestionGenerator(List<Brand> brands, QuestionSeeds seeds)` with `List<Question> all()` and `List<Question> forBrand(Brand brand, int limit)` — `limit` caps total: 6 discovery first (or `maxQuestions` if smaller), then brand-level questions in order comparison→trust→price_longtail→freshness

- [ ] **Step 1: Fixtures (copied semantics from Python project, synthetic content)**

`fixtures/brands.yaml`:
```yaml
category: "test devices"
brands:
  - {name: Alpha, domain: alpha.com, tier: leader, country: CN, competitors: [Beta], aliases: [Alphatron]}
  - {name: Beta, domain: beta.com, tier: growth, country: INTL, competitors: [Alpha]}
```

`fixtures/questions.yaml` (same shape as Python `data/questions.yaml`):
```yaml
category_term: "test devices"
discovery:
  max_questions: 5
  intents: ["best", "top rated"]
  subcategories: ["widget", "gadget"]
  qualifiers: ["for pets", ""]
  budget_prices: [100]
brand_templates:
  comparison: ["{brand} vs {competitor}", "alternatives to {brand}"]
  trust: ["Is {brand} legit?"]
  price_longtail: ["Is {brand} worth the money?"]
  freshness: ["What's new from {brand} in 2025-2026?"]
```

`golden/questions.json` — generate from the Python project (both repos on disk):
```bash
cd ../geo-visibility && python -c "
from geo_visibility import config
from geo_visibility.questions import load_brands, build_question_set
import json, pathlib, shutil
cfg = config.load_config()
brands = load_brands(cfg['brands'])
qs = build_question_set(brands, cfg['questions'])
out = [{'qid': q.qid, 'dimension': q.dimension, 'text': q.text, 'brand': q.brand} for q in qs]
dest = pathlib.Path('../geo-visibility-mcp/src/test/resources/golden/questions.json')
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding='utf-8')
print(len(out), 'questions dumped')"
```

- [ ] **Step 2: Failing tests**

```java
package io.github.wavehan.geovisibility.data;

import io.github.wavehan.geovisibility.core.Brand;
import io.github.wavehan.geovisibility.core.Question;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuestionGeneratorTest {
    static final Path FIX = Path.of("src/test/resources/fixtures");

    static QuestionGenerator gen() {
        var brands = new BrandRepository(FIX).load();
        return new QuestionGenerator(brands,
                new BrandRepository(FIX).loadSeeds(FIX.resolve("data/questions.yaml")
                        .getParent() == null ? FIX : FIX)); // see loadSeeds below
    }

    @Test void forBrandRespectsLimitDiscoveryFirst() {
        var brands = new BrandRepository(FIX).load();
        var g = new QuestionGenerator(brands, seeds());
        var qs = g.forBrand(brands.get(0), 6);
        assertEquals(6, qs.size());
        assertEquals(5, qs.stream().filter(q -> q.dimension().equals("discovery")).count());
        assertEquals("C001", qs.get(5).qid());
    }
    @Test void templatesRenderBrandAndCompetitor() {
        var brands = new BrandRepository(FIX).load();
        var qs = new QuestionGenerator(brands, seeds()).forBrand(brands.get(1), 100);
        assertTrue(qs.stream().anyMatch(q -> q.text().equals("Beta vs Alpha")));
        assertTrue(qs.stream().anyMatch(q -> q.text().equals("Is Beta legit?")));
    }
    @Test void qidSchemeMatchesPythonGolden() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var golden = mapper.readValue(
                Path.of("src/test/resources/golden/questions.json").toFile(),
                mapper.getTypeFactory().constructCollectionType(List.class, Question.class));
        var brands = new BrandRepository(Path.of("../geo-visibility/data")).load();
        var seedsYaml = Path.of("../geo-visibility/data/questions.yaml");
        var generated = new QuestionGenerator(brands,
                QuestionSeeds.load(seedsYaml)).all();
        assertEquals(golden.size(), generated.size());
        for (int i = 0; i < golden.size(); i++) {
            assertEquals(golden.get(i).qid(), generated.get(i).qid(), "index " + i);
            assertEquals(golden.get(i).text(), generated.get(i).text(), "index " + i);
        }
    }
    private static QuestionSeeds seeds() {
        return QuestionSeeds.load(FIX.resolve("questions.yaml"));
    }
}
```

(The golden test runs only where the sibling Python repo exists — mark it `@org.junit.jupiter.api.condition.EnabledIf` on a files-exists check: `EnabledIf("pythonRepoPresent")` with a static helper returning `Path.of("../geo-visibility/data").toFile().exists()`.)

- [ ] **Step 3: Run** — expect compile FAIL.
- [ ] **Step 4: Implement**

```java
// core/Question.java
package io.github.wavehan.geovisibility.core;

/** Generated question; brand is null for category-level discovery questions. */
public record Question(String qid, String dimension, String text, String brand) { }

// data/QuestionSeeds.java
package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Parsed questions.yaml. */
public record QuestionSeeds(String categoryTerm, Discovery discovery,
                            Map<String, List<String>> brandTemplates) {
    public record Discovery(Integer maxQuestions, List<String> intents,
                            List<String> subcategories, List<String> qualifiers,
                            List<Integer> budgetPrices) { }

    public static QuestionSeeds load(Path yaml) {
        try {
            return new ObjectMapper(new YAMLFactory()).readValue(yaml.toFile(), QuestionSeeds.class);
        } catch (Exception e) {
            throw new IllegalStateException("cannot load questions.yaml at " + yaml, e);
        }
    }
}

// data/BrandRepository.java
package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.wavehan.geovisibility.core.Brand;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads brands.yaml from <dataDir>/data/brands.yaml (or <dataDir>/brands.yaml). */
public class BrandRepository {
    private final Path dataDir;
    public BrandRepository(Path dataDir) { this.dataDir = dataDir; }

    public List<Brand> load() {
        var p = dataDir.resolve("data/brands.yaml");
        if (!p.toFile().exists()) p = dataDir.resolve("brands.yaml");
        if (!p.toFile().exists())
            throw new IllegalStateException("brands.yaml not found under " + dataDir);
        record Wrapper(String category, List<Map<String, Object>> brands) { }
        try {
            var w = new ObjectMapper(new YAMLFactory()).readValue(p.toFile(), Wrapper.class);
            var out = new ArrayList<Brand>();
            for (var b : w.brands()) {
                out.add(new Brand((String) b.get("name"), (String) b.get("domain"),
                        (String) b.get("tier"), (String) b.get("country"),
                        strList(b.get("competitors")), strList(b.get("aliases"))));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("cannot parse " + p, e);
        }
    }
    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        return o == null ? List.of() : new ArrayList<>((List<String>) o);
    }
}

// data/QuestionGenerator.java
package io.github.wavehan.geovisibility.data;

import io.github.wavehan.geovisibility.core.Brand;
import io.github.wavehan.geovisibility.core.Question;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates the question set with the SAME qid scheme as the Python tool (D/C/T/P/F). */
public class QuestionGenerator {
    private static final Map<String, String> PREFIX = Map.of(
            "comparison", "C", "trust", "T", "price_longtail", "P", "freshness", "F");
    private static final List<String> DIMS =
            List.of("comparison", "trust", "price_longtail", "freshness");

    private final List<Brand> brands;
    private final QuestionSeeds seeds;

    public QuestionGenerator(List<Brand> brands, QuestionSeeds seeds) {
        this.brands = brands; this.seeds = seeds;
    }

    List<String> expandDiscovery() {
        var d = seeds.discovery();
        int limit = d.maxQuestions() == null ? 40 : d.maxQuestions();
        var out = new ArrayList<String>();
        for (String intent : d.intents())
            for (String sub : d.subcategories()) {
                out.add(intent + " " + sub);
                for (String qual : d.qualifiers())
                    if (!qual.isEmpty()) out.add(intent + " " + sub + " " + qual);
            }
        for (String sub : d.subcategories())
            for (Integer price : d.budgetPrices()) out.add("best " + sub + " under $" + price);
        Set<String> seen = new LinkedHashSet<>(out);
        return seen.stream().limit(limit).toList();
    }

    String competitorFor(Brand brand) {
        for (var c : brand.competitors()) if (!c.equals(brand.name())) return c;
        for (var b : brands) if (!b.name().equals(brand.name())) return b.name();
        return "competitor";
    }

    public List<Question> all() {
        var out = new ArrayList<Question>();
        int i = 0;
        for (String q : expandDiscovery())
            out.add(new Question("D%03d".formatted(++i), "discovery", q, null));
        var counters = new java.util.HashMap<String, Integer>();
        for (Brand brand : brands) {
            var ctx = Map.of("brand", brand.name(),
                    "competitor", competitorFor(brand),
                    "category", seeds.categoryTerm() == null ? "" : seeds.categoryTerm());
            for (String dim : DIMS)
                for (String tpl : seeds.brandTemplates().getOrDefault(dim, List.of())) {
                    counters.merge(dim, 1, Integer::sum);
                    var text = tpl;
                    for (var e : ctx.entrySet())
                        text = text.replace("{" + e.getKey() + "}", e.getValue());
                    out.add(new Question(PREFIX.get(dim) + "%03d".formatted(counters.get(dim)),
                            dim, text, brand.name()));
                }
        }
        return out;
    }

    public List<Question> forBrand(Brand brand, int limit) {
        var out = new ArrayList<Question>();
        var all = all();
        for (Question q : all) {
            if (out.size() >= limit) break;
            if (q.dimension().equals("discovery")) out.add(q);
        }
        for (Question q : all) {
            if (out.size() >= limit) break;
            if (brand.name().equals(q.brand())) out.add(q);
        }
        return out;
    }
}
```

Also add `QuestionSeeds.load(Path)` usage in the test via `QuestionSeeds.load(FIX.resolve("questions.yaml"))` (fixture dir has no `data/` nesting — adjust test `gen()` helper accordingly or drop it; keep the three real tests only).

- [ ] **Step 5: Run** — `mvn test -Dtest=QuestionGeneratorTest`. Expected: PASS (3 tests; golden test runs locally, skipped in CI).
- [ ] **Step 6: Commit** — `git add -A && git commit -m "feat(data): yaml repositories and python-parity question generator"`

---

### Task 5: JsonlStore (read / append / resume)

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/data/JsonlStore.java`
- Test: `src/test/java/io/github/wavehan/geovisibility/data/JsonlStoreTest.java`

**Interfaces:**
- Consumes: `RawRecord` (Task 3)
- Produces:
  - `JsonlStore(ObjectMapper mapper)` — Spring bean later
  - `List<RawRecord> readAll(Path rawDir)` — all `*.jsonl` in dir, empty list if dir missing; corrupted lines skipped
  - `Set<String> successfulQids(Path file)` — qids whose `error` is null
  - `synchronized void append(Path file, RawRecord record)` — creates parent dirs, one JSON line UTF-8
  - `static String nowTs()` — `yyyy-MM-dd'T'HH:mm:ss`

- [ ] **Step 1: Failing tests (incl. concurrent-append integrity)**

```java
package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.RawRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonlStoreTest {
    static final JsonlStore STORE = new JsonlStore(new ObjectMapper());
    static RawRecord rec(String qid, String error) {
        return new RawRecord("deepseek", qid, "discovery", null, "q", "a",
                List.of(), error, "2026-11-01T10:00:00");
    }

    @Test void successfulQidsSkipErrors(@TempDir Path dir) throws Exception {
        var f = dir.resolve("deepseek.jsonl");
        STORE.append(f, rec("D001", null));
        STORE.append(f, rec("D002", "HTTP 500: key=SECRET123 body"));
        STORE.append(f, rec("D003", null));
        assertEquals(Set.of("D001", "D003"), STORE.successfulQids(f));
    }
    @Test void readAllSkipsCorruptedLines(@TempDir Path dir) throws Exception {
        var f = dir.resolve("deepseek.jsonl");
        STORE.append(f, rec("D001", null));
        Files.writeString(f, "not-json\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(1, STORE.readAll(dir).size());
    }
    @Test void readAllMissingDirReturnsEmpty(@TempDir Path dir) {
        assertTrue(STORE.readAll(dir.resolve("nope")).isEmpty());
    }
    @Test void fiftyConcurrentAppendsProduceFiftyLines(@TempDir Path dir) throws Exception {
        var f = dir.resolve("zhipu.jsonl");
        var pool = Executors.newFixedThreadPool(8);
        var tasks = new java.util.ArrayList<Callable<Object>>();
        for (int i = 0; i < 50; i++) {
            int n = i;
            tasks.add(() -> { STORE.append(f, rec("D%03d".formatted(n), null)); return null; });
        }
        pool.invokeAll(tasks);
        pool.shutdown();
        assertEquals(50, Files.readAllLines(f).size());
        assertEquals(50, STORE.readAll(dir).size());
    }
}
```

- [ ] **Step 2: Run** — expect compile FAIL.
- [ ] **Step 3: Implement**

```java
package io.github.wavehan.geovisibility.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.RawRecord;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Append-only JSONL store; field contract shared with the Python tool. */
public class JsonlStore {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final ObjectMapper mapper;

    public JsonlStore(ObjectMapper mapper) { this.mapper = mapper; }

    public static String nowTs() { return LocalDateTime.now().format(TS); }

    public List<RawRecord> readAll(Path rawDir) {
        var out = new ArrayList<RawRecord>();
        if (!Files.isDirectory(rawDir)) return out;
        try (Stream<Path> files = Files.list(rawDir)) {
            var sorted = files.filter(p -> p.toString().endsWith(".jsonl")).sorted().toList();
            for (Path p : sorted) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    try { out.add(mapper.readValue(line, RawRecord.class)); }
                    catch (IOException ignored) { /* corrupted line: skip */ }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + rawDir, e);
        }
        return out;
    }

    public Set<String> successfulQids(Path file) {
        var out = new HashSet<String>();
        if (!Files.exists(file)) return out;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    var r = mapper.readValue(line, RawRecord.class);
                    if (r.error() == null) out.add(r.qid());
                } catch (IOException ignored) { }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
        return out;
    }

    public synchronized void append(Path file, RawRecord record) {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {
                w.write(mapper.writeValueAsString(record));
                w.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot append " + file, e);
        }
    }
}
```

- [ ] **Step 4: Run** — `mvn test -Dtest=JsonlStoreTest`. Expected: PASS (4 tests).
- [ ] **Step 5: Commit** — `git commit -am "feat(data): resumable jsonl store"`

---

### Task 6: Redactor + engine adapters (MockRestServiceServer)

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/engine/Redactor.java`, `engine/EngineAnswer.java`, `engine/EngineException.java`, `engine/EngineAdapter.java`, `engine/ResponsesEngine.java`, `engine/CompatEngine.java`
- Test: `src/test/java/io/github/wavehan/geovisibility/engine/RedactorTest.java`, `engine/EngineAdaptersTest.java`

**Interfaces:**
- Produces:
  - `static String Redactor.redact(String msg)` — `key=<value>` → `key=***`
  - `record EngineAnswer(String answer, List<String> citations, String rawJson)`
  - `class EngineException extends RuntimeException` with `int status()` and `String body()`
  - `interface EngineAdapter { String name(); EngineAnswer ask(String question); }`
  - `ResponsesEngine(String name, String baseUrl, String model, String apiKey, RestClient rest, long[] backoffMillis)` — POST `<baseUrl>/responses`, body `{model, tools:[{type:"web_search"}], input}`; extraction: `output[]` where `type=="message"` → `content[]` parts `output_text|text` → text; annotations `url_citation` → `url` or `url_citation.url`
  - `CompatEngine(String name, String baseUrl, String model, String apiKey, Map<String,Object> extraBody, RestClient rest, long[] backoffMillis)` — POST `<baseUrl>/chat/completions`; answer `choices[0].message.content`; citations from top-level `citations`
  - Both retry `backoffMillis.length` extra times, then throw `EngineException(status, first-200-chars-of-body)`

- [ ] **Step 1: Failing tests**

```java
// RedactorTest.java
package io.github.wavehan.geovisibility.engine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedactorTest {
    @Test void stripsKeyValues() {
        var s = "Client error '404' for url 'https://x/v1?key=AIzaSecret123.xyz&x=1'";
        assertTrue(!Redactor.redact(s).contains("AIzaSecret123"));
        assertTrue(Redactor.redact(s).contains("key=***"));
    }
}

// EngineAdaptersTest.java
package io.github.wavehan.geovisibility.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.core.RawRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EngineAdaptersTest {
    static final ObjectMapper M = new ObjectMapper();
    static final long[] NO_BACKOFF = {};

    @Test void compatParsesAnswerAndTopLevelCitations() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://fake.test/api/paas/v4/chat/completions"))
              .andRespond(withSuccess("""
                {"choices":[{"message":{"content":"Alpha is great"}}],
                 "citations":["https://alpha.com/a","https://reddit.com/r/x"]}""", null));
        var e = new CompatEngine("zhipu", "https://fake.test/api/paas/v4", "glm-4-flash",
                "k", Map.of("tools", List.of()), builder.build(), NO_BACKOFF);
        var ans = e.ask("best widget");
        assertEquals("Alpha is great", ans.answer());
        assertEquals(2, ans.citations().size());
    }
    @Test void responsesParsesUrlCitationAnnotations() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://fake.test/v1/responses"))
              .andRespond(withSuccess("""
                {"output":[{"type":"message","content":[
                  {"type":"output_text","text":"Beta wins",
                   "annotations":[{"type":"url_citation","url":"https://beta.com/x"}]}]}]}""", null));
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "deepseek-v4-flash",
                "k", builder.build(), NO_BACKOFF);
        var ans = e.ask("q");
        assertEquals("Beta wins", ans.answer());
        assertEquals(List.of("https://beta.com/x"), ans.citations());
    }
    @Test void httpErrorBecomesEngineExceptionWithBodyExcerpt() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var huge = "x".repeat(5000);
        server.expect(requestTo("https://fake.test/v1/responses"))
              .andRespond(withServerError().body("{\"error\":\"" + huge + "\"}"));
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "m",
                "k", builder.build(), NO_BACKOFF);
        var ex = assertThrows(EngineException.class, () -> e.ask("q"));
        assertEquals(500, ex.status());
        assertTrue(ex.body().length() <= 200);
    }
    @Test void retriesThenFails() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://fake.test/v1/responses"))
              .andRespond(withServerError());
        server.expect(requestTo("https://fake.test/v1/responses"))
              .andRespond(withServerError());
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "m",
                "k", builder.build(), new long[]{0, 0});
        assertThrows(EngineException.class, () -> e.ask("q"));
        server.verify();
    }
}
```

- [ ] **Step 2: Run** — expect compile FAIL.
- [ ] **Step 3: Implement**

```java
// Redactor.java
package io.github.wavehan.geovisibility.engine;
import java.util.regex.Pattern;

/** Strips key=<value> sequences before anything is persisted. */
public final class Redactor {
    private static final Pattern KEY = Pattern.compile("key=[A-Za-z0-9._\\-]+");
    private Redactor() { }
    public static String redact(String msg) {
        return msg == null ? null : KEY.matcher(msg).replaceAll("key=***");
    }
}

// EngineAnswer.java
package io.github.wavehan.geovisibility.engine;
import java.util.List;

/** Successful engine response; rawJson kept in memory for probe inspection. */
public record EngineAnswer(String answer, List<String> citations, String rawJson) { }

// EngineException.java
package io.github.wavehan.geovisibility.engine;

/** Terminal adapter failure after retries; body already truncated by the adapter. */
public class EngineException extends RuntimeException {
    private final int status;
    private final String body;
    public EngineException(int status, String body) {
        super("HTTP " + status + ": " + body);
        this.status = status; this.body = body;
    }
    public int status() { return status; }
    public String body() { return body; }
}

// EngineAdapter.java
package io.github.wavehan.geovisibility.engine;

public interface EngineAdapter {
    String name();
    EngineAnswer ask(String question);
}
```

```java
// ResponsesEngine.java
package io.github.wavehan.geovisibility.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** OpenAI Responses-style engine with web_search tool (deepseek, openai). */
public class ResponsesEngine implements EngineAdapter {
    private static final ObjectMapper M = new ObjectMapper();
    private final String name, baseUrl, model, apiKey;
    private final RestClient rest;
    private final long[] backoff;

    public ResponsesEngine(String name, String baseUrl, String model, String apiKey,
                           RestClient rest, long[] backoffMillis) {
        this.name = name; this.baseUrl = baseUrl; this.model = model;
        this.apiKey = apiKey; this.rest = rest; this.backoff = backoffMillis;
    }
    public String name() { return name; }

    public EngineAnswer ask(String question) {
        var body = Map.of("model", model,
                "tools", List.of(Map.of("type", "web_search")),
                "input", question);
        return exchange(body);
    }

    EngineAnswer exchange(Map<String, Object> body) {
        RestClientResponseException last = null;
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            if (attempt > 0) sleep(backoff[attempt - 1]);
            try {
                String raw = rest.post().uri(baseUrl + "/responses")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return extract(raw);
            } catch (RestClientResponseException e) {
                last = e;
                if (!e.getStatusCode().is5xxServerError() && e.getStatusCode().value() != 429)
                    throw shorten(e);          // 4xx other than 429: do not retry
            }
        }
        throw shorten(last);
    }

    private static EngineException shorten(RestClientResponseException e) {
        HttpStatusCode s = e.getStatusCode();
        String b = e.getResponseBodyAsString();
        return new EngineException(s.value(), b == null ? "" : b.substring(0, Math.min(200, b.length())));
    }

    static EngineAnswer extract(String raw) {
        try {
            JsonNode root = M.readTree(raw);
            var texts = new ArrayList<String>();
            var citations = new ArrayList<String>();
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode part : item.path("content")) {
                    String t = part.path("type").asText();
                    if ("output_text".equals(t) || "text".equals(t)) {
                        texts.add(part.path("text").asText());
                        for (JsonNode ann : part.path("annotations"))
                            if ("url_citation".equals(ann.path("type").asText())) {
                                String url = ann.path("url").asText(
                                        ann.path("url_citation").path("url").asText(""));
                                if (!url.isEmpty()) citations.add(url);
                            }
                    }
                }
            }
            return new EngineAnswer(String.join("\n", texts), citations, raw);
        } catch (Exception e) {
            throw new IllegalStateException("unparseable responses payload", e);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
    }
}

// CompatEngine.java
package io.github.wavehan.geovisibility.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OpenAI-compatible chat/completions engine (zhipu, qwen, perplexity, xai). */
public class CompatEngine implements EngineAdapter {
    private static final ObjectMapper M = new ObjectMapper();
    private final String name, baseUrl, model, apiKey;
    private final Map<String, Object> extraBody;
    private final RestClient rest;
    private final long[] backoff;

    public CompatEngine(String name, String baseUrl, String model, String apiKey,
                        Map<String, Object> extraBody, RestClient rest, long[] backoffMillis) {
        this.name = name; this.baseUrl = baseUrl; this.model = model;
        this.apiKey = apiKey; this.extraBody = extraBody;
        this.rest = rest; this.backoff = backoffMillis;
    }
    public String name() { return name; }

    public EngineAnswer ask(String question) {
        var body = new HashMap<String, Object>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", question)));
        if (extraBody != null) body.putAll(extraBody);
        RestClientResponseException last = null;
        for (int attempt = 0; attempt <= backoff.length; attempt++) {
            if (attempt > 0) sleepQuietly(backoff[attempt - 1]);
            try {
                String raw = rest.post().uri(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve().body(String.class);
                return extract(raw);
            } catch (RestClientResponseException e) {
                last = e;
                var code = e.getStatusCode().value();
                if (code != 429 && e.getStatusCode().value() < 500) {
                    String b = e.getResponseBodyAsString();
                    throw new EngineException(code,
                            b == null ? "" : b.substring(0, Math.min(200, b.length())));
                }
            }
        }
        String b = last == null ? "" : last.getResponseBodyAsString();
        throw new EngineException(last == null ? 0 : last.getStatusCode().value(),
                b == null ? "" : b.substring(0, Math.min(200, b.length())));
    }

    static EngineAnswer extract(String raw) {
        try {
            JsonNode root = M.readTree(raw);
            String answer = root.path("choices").path(0).path("message").path("content").asText("");
            var citations = new ArrayList<String>();
            for (JsonNode c : root.path("citations"))
                if (c.isTextual()) citations.add(c.asText());
            return new EngineAnswer(answer, citations, raw);
        } catch (Exception e) {
            throw new IllegalStateException("unparseable compat payload", e);
        }
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
    }
}
```

- [ ] **Step 4: Run** — `mvn test -Dtest='RedactorTest,EngineAdaptersTest'`. Expected: PASS (5 tests).
- [ ] **Step 5: Commit** — `git commit -am "feat(engine): responses/compat adapters with retry, redaction, body excerpts"`

---

### Task 7: EngineRegistry + config properties

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/data/GeoProperties.java`, `engine/EngineInfo.java`, `engine/EngineRegistry.java`
- Modify: `src/main/resources/application.yml` (fill `geo.engines` per spec §6)
- Test: `src/test/java/io/github/wavehan/geovisibility/engine/EngineRegistryTest.java`

**Interfaces:**
- Produces:
  - `@ConfigurationProperties(prefix="geo") class GeoProperties { Path dataDir; List<EngineConf> engines; Measure measure; ... }` with `EngineConf(String name, String type, String layer, String baseUrl, String model, String apiKeyEnv, boolean enabled, Map<String,Object> extraBody)` and `Measure(int concurrency, int retries)`
  - `record EngineInfo(String name, String layer, String model, boolean available)`
  - `EngineRegistry(GeoProperties props)` — `List<EngineInfo> engines()`; `Optional<EngineAdapter> adapter(String name)`; `List<EngineAdapter> availableAdapters()`; key = `System.getenv(apiKeyEnv)` non-blank; RestClient shared with 120s read timeout (`SimpleClientHttpRequestFactory`); backoff `[1000, 2000]` derived from `retries`
- Register `GeoProperties` via `@EnableConfigurationProperties` on the application class, and expose `@Bean ObjectMapper`, `@Bean JsonlStore`.

- [ ] **Step 1: Failing test**

```java
package io.github.wavehan.geovisibility.engine;

import io.github.wavehan.geovisibility.data.GeoProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EngineRegistryTest {
    static GeoProperties props() {
        var p = new GeoProperties();
        var ds = new GeoProperties.EngineConf();
        ds.setName("deepseek"); ds.setType("responses"); ds.setLayer("search");
        ds.setBaseUrl("https://api.deepseek.com/v1"); ds.setModel("deepseek-v4-flash");
        ds.setApiKeyEnv("DEEPSEEK_TEST_KEY"); ds.setEnabled(true);
        var zp = new GeoProperties.EngineConf();
        zp.setName("zhipu"); zp.setType("compat"); zp.setLayer("memory");
        zp.setBaseUrl("https://open.bigmodel.cn/api/paas/v4"); zp.setModel("glm-4-flash");
        zp.setApiKeyEnv("ZHIPU_DEFINITELY_MISSING_KEY"); zp.setEnabled(true);
        p.setEngines(List.of(ds, zp));
        var m = new GeoProperties.Measure(); m.setConcurrency(2); m.setRetries(2);
        p.setMeasure(m);
        return p;
    }

    @Test void missingKeyMeansUnavailable() {
        try { System.setProperty("DEEPSEEK_TEST_KEY", ""); } catch (Exception ignored) { }
        var reg = new EngineRegistry(props());
        var names = reg.engines().stream().map(EngineInfo::name).toList();
        assertEquals(List.of("deepseek", "zhipu"), names);
        assertTrue(reg.engines().get(0).available());   // env var set (even blank counts as set? NO):
    }
}
```

Refine expectation before implementing: `available == System.getenv(name) != null && !isBlank`. For the test, set env via assumption: since `System.getenv` cannot be set at runtime, assert `!reg.engines().get(1).available()` (missing var) and mark deepseek's availability assert with `assumeTrue(System.getenv("DEEPSEEK_TEST_KEY") != null)`. Adjust test accordingly (delete the `System.setProperty` line).

- [ ] **Step 2: Run** — expect compile FAIL.
- [ ] **Step 3: Implement** (`GeoProperties` = plain getters/setters as sketched; `EngineInfo` record; `EngineRegistry`):

```java
// engine/EngineRegistry.java
package io.github.wavehan.geovisibility.engine;

import io.github.wavehan.geovisibility.data.GeoProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds adapters from config; availability = env key present and non-blank. */
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
            if (!e.isEnabled()) continue;
            out.add(new EngineInfo(e.getName(), e.getLayer(), e.getModel(), hasKey(e)));
        }
        return out;
    }

    public Optional<EngineAdapter> adapter(String name) {
        for (var e : props.getEngines()) {
            if (!e.isEnabled() || !e.getName().equals(name) || !hasKey(e)) continue;
            long[] backoff = new long[props.getMeasure().getRetries()];
            for (int i = 0; i < backoff.length; i++) backoff[i] = 1000L * (i + 1);
            if ("responses".equals(e.getType()))
                return Optional.of(new ResponsesEngine(e.getName(), e.getBaseUrl(),
                        e.getModel(), System.getenv(e.getApiKeyEnv()), rest, backoff));
            return Optional.of(new CompatEngine(e.getName(), e.getBaseUrl(), e.getModel(),
                    System.getenv(e.getApiKeyEnv()), e.getExtraBody(), rest, backoff));
        }
        return Optional.empty();
    }

    public List<EngineAdapter> availableAdapters() {
        return props.getEngines().stream().filter(GeoProperties.EngineConf::isEnabled)
                .map(e -> e.getName()).map(this::adapter)
                .flatMap(Optional::stream).toList();
    }

    private boolean hasKey(GeoProperties.EngineConf e) {
        var v = System.getenv(e.getApiKeyEnv());
        return v != null && !v.isBlank();
    }
}
```

`application.yml` engines block: exactly the two engines from spec §6. Add `@EnableConfigurationProperties(GeoProperties.class)` + `@Bean JsonlStore/ObjectMapper/EngineRegistry` in a new `server/AppConfig.java`.

- [ ] **Step 4: Run** — `mvn test`. Expected: PASS (all).
- [ ] **Step 5: Commit** — `git commit -am "feat(engine): config-backed registry with env-key availability"`

---

### Task 8: MeasureService

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/server/MeasureService.java` (+ result records in same file)
- Test: `src/test/java/io/github/wavehan/geovisibility/server/MeasureServiceTest.java`

**Interfaces:**
- Consumes: `BrandRepository`, `QuestionGenerator`, `QuestionSeeds`, `JsonlStore`, `EngineRegistry`, `MentionParser`, `Metrics`, `GeoProperties`
- Produces:
  - `record EngineMeasure(String engine, String layer, Double mentionRate, Double avgPosition, int run, int skipped, List<String> errors)`
  - `record MeasureResult(String brand, List<EngineMeasure> engines)`
  - `MeasureService(BrandRepository brands, JsonlStore store, EngineRegistry registry, GeoProperties props)`
  - `MeasureResult measure(String brandName, List<String> engineNamesOrNull, Integer questionLimitOrNull)` — unknown brand → `MeasureResult` with `engines=[]` and error appended via a `errors` field on MeasureResult: change to `record MeasureResult(String brand, List<EngineMeasure> engines, List<String> errors)`; unavailable requested engine → entry in `errors`, others proceed; records appended per engine at `<dataDir>/output/raw/<engine>.jsonl`; metrics computed over that engine's whole file after the run

- [ ] **Step 1: Failing tests**

```java
package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.*;
import io.github.wavehan.geovisibility.engine.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MeasureServiceTest {
    static final Path FIX = Path.of("src/test/resources/fixtures");

    static GeoProperties props(@TempDir Path dir) {
        var p = new GeoProperties();
        p.setDataDir(FIX);
        var m = new GeoProperties.Measure(); m.setConcurrency(2); m.setRetries(0);
        p.setMeasure(m);
        p.setEngines(List.of(conf("mockengine", dir)));
        return p;
    }
    static GeoProperties.EngineConf conf(String name, Path dir) {
        var c = new GeoProperties.EngineConf();
        c.setName(name); c.setType("compat"); c.setLayer("search");
        c.setBaseUrl("https://mock.test/v1"); c.setModel("m");
        c.setApiKeyEnv("MEASURE_TEST_KEY"); c.setEnabled(true);
        return c;
    }

    @Test void measuresBrandAndWritesJsonl(@TempDir Path dir) {
        // route data dir output into tempdir by copying fixtures structure
        try {
            java.nio.file.Files.createDirectories(dir.resolve("data"));
            java.nio.file.Files.copy(FIX.resolve("brands.yaml"), dir.resolve("data/brands.yaml"));
            java.nio.file.Files.copy(FIX.resolve("questions.yaml"), dir.resolve("data/questions.yaml"));
        } catch (Exception e) { throw new RuntimeException(e); }

        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(
            "{\"choices\":[{\"message\":{\"content\":\"Alpha is the best widget for pets\"}}]}",
            org.springframework.http.MediaType.APPLICATION_JSON));

        var props = props(dir);
        props.setDataDir(dir);
        var registry = new TestRegistry(builder.build(), props);
        var svc = new MeasureService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), registry, props);
        var result = svc.measure("Alpha", null, 4);

        assertEquals(1, result.engines().size());
        var em = result.engines().get(0);
        assertEquals("mockengine", em.engine());
        assertEquals(4, em.run() + em.skipped());
        assertTrue(em.mentionRate() > 0);
        assertTrue(java.nio.file.Files.exists(dir.resolve("output/raw/mockengine.jsonl")));
    }
    @Test void unknownBrandReportsError(@TempDir Path dir) {
        var props = props(dir);
        var svc = new MeasureService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), new TestRegistry(null, props), props);
        var result = svc.measure("Nope", null, 4);
        assertTrue(result.engines().isEmpty());
        assertFalse(result.errors().isEmpty());
    }

    /** Registry override that injects a RestClient-bound mock adapter. */
    static class TestRegistry extends EngineRegistry {
        private final EngineAdapter adapter;
        TestRegistry(org.springframework.web.client.RestClient rest, GeoProperties props) {
            super(props);
            this.adapter = rest == null ? null : new CompatEngine("mockengine",
                    "https://mock.test/v1", "m", "k", null, rest, new long[0]);
        }
        @Override public java.util.Optional<EngineAdapter> adapter(String name) {
            return adapter == null ? java.util.Optional.empty() : java.util.Optional.of(adapter);
        }
        @Override public List<EngineAdapter> availableAdapters() {
            return adapter == null ? List.of() : List.of(adapter);
        }
    }
}
```

- [ ] **Step 2: Run** — expect compile FAIL.
- [ ] **Step 3: Implement**

```java
package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.core.*;
import io.github.wavehan.geovisibility.data.*;
import io.github.wavehan.geovisibility.engine.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Runs a brand's questions across engines, persists JSONL, returns per-engine metrics. */
public class MeasureService {
    public record EngineMeasure(String engine, String layer, Double mentionRate,
                                 Double avgPosition, int run, int skipped, List<String> errors) { }
    public record MeasureResult(String brand, List<EngineMeasure> engines, List<String> errors) { }

    private final BrandRepository brands;
    private final JsonlStore store;
    private final EngineRegistry registry;
    private final GeoProperties props;

    public MeasureService(BrandRepository brands, JsonlStore store,
                          EngineRegistry registry, GeoProperties props) {
        this.brands = brands; this.store = store; this.registry = registry; this.props = props;
    }

    public MeasureResult measure(String brandName, List<String> engineNames, Integer limitBoxed) {
        int limit = limitBoxed == null ? 10 : limitBoxed;
        var errors = new ArrayList<String>();
        var all = brands.load();
        var brand = all.stream().filter(b -> b.name().equals(brandName)).findFirst();
        if (brand.isEmpty()) {
            errors.add("unknown brand: " + brandName
                    + " (call list_brands to see the sample)");
            return new MeasureResult(brandName, List.of(), errors);
        }
        var seeds = QuestionSeeds.load(props.getDataDir().resolve("data/questions.yaml"));
        var questions = new QuestionGenerator(all, seeds).forBrand(brand.get(), limit);
        var matchers = MentionParser.buildMatchers(all);

        List<EngineAdapter> adapters;
        if (engineNames == null || engineNames.isEmpty()) {
            adapters = registry.availableAdapters();
        } else {
            adapters = new ArrayList<>();
            for (String n : engineNames) {
                var a = registry.adapter(n);
                if (a.isEmpty()) errors.add("engine unavailable (no API key?): " + n);
                else adapters.add(a.get());
            }
        }

        var engineMeasures = new ArrayList<EngineMeasure>();
        ExecutorService pool = Executors.newFixedThreadPool(props.getMeasure().getConcurrency());
        try {
            for (EngineAdapter adapter : adapters) {
                Path file = props.getDataDir().resolve("output/raw/" + adapter.name() + ".jsonl");
                var done = store.successfulQids(file);
                var todo = questions.stream().filter(q -> !done.contains(q.qid())).toList();
                List<Future<Object>> futures = new ArrayList<>();
                for (Question q : todo) {
                    futures.add(pool.submit(() -> {
                        try {
                            var ans = adapter.ask(q.text());
                            store.append(file, new RawRecord(adapter.name(), q.qid(),
                                    q.dimension(), q.brand(), q.text(), ans.answer(),
                                    ans.citations(), null, JsonlStore.nowTs()));
                        } catch (EngineException e) {
                            store.append(file, new RawRecord(adapter.name(), q.qid(),
                                    q.dimension(), q.brand(), q.text(), "", List.of(),
                                    Redactor.redact("HTTP " + e.status() + ": " + e.body()),
                                    JsonlStore.nowTs()));
                        }
                        return null;
                    }));
                }
                for (Future<Object> f : futures) f.get();
                var records = store.readAll(props.getDataDir().resolve("output/raw"))
                        .stream().filter(r -> r.engine().equals(adapter.name())).toList();
                var info = registry.engines().stream()
                        .filter(e -> e.name().equals(adapter.name())).findFirst();
                engineMeasures.add(new EngineMeasure(adapter.name(),
                        info.map(EngineInfo::layer).orElse("unknown"),
                        Metrics.mentionRate(brandName, records, matchers),
                        Metrics.avgPosition(brandName, records, matchers),
                        todo.size(), done.stream().mapToInt(x -> 1)
                                .filter(x -> questions.stream()
                                        .anyMatch(q -> q.qid().equals(x))).count(),
                        List.of()));
            }
        } catch (Exception e) {
            errors.add(Redactor.redact(String.valueOf(e)));
        } finally {
            pool.shutdownNow();
        }
        return new MeasureResult(brandName, engineMeasures, errors);
    }
}
```

Note: fix the `skipped` computation to the simple form `questions.size() - todo.size()` (the stream-count sketch above is wrong; use this simpler expression in the real code).

- [ ] **Step 4: Run** — `mvn test -Dtest=MeasureServiceTest`. Expected: PASS (2 tests). Then `mvn test` (regression green).
- [ ] **Step 5: Commit** — `git commit -am "feat(server): measure orchestration with per-engine metrics"`

---

### Task 9: AnalysisService

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/server/AnalysisService.java`
- Create fixture: `src/test/resources/fixtures/raw/synthetic.jsonl`
- Test: `src/test/java/io/github/wavehan/geovisibility/server/AnalysisServiceTest.java`

**Interfaces:**
- Consumes: `BrandRepository`, `JsonlStore`, `EngineRegistry`, `Metrics`, `MentionParser`
- Produces:
  - `record BrandMetrics(String brand, String tier, String country, Double mentionRate, Double avgPosition)`
  - `record EngineAnalysis(String engine, String layer, List<BrandMetrics> brands, Map<String,Double> tierAverages, Map<String,Double> countryAverages)`
  - `record AnalysisResult(List<EngineAnalysis> engines, boolean dataFound, String note)`
  - `AnalysisService(BrandRepository brands, JsonlStore store, EngineRegistry registry)`
  - `AnalysisResult analyze(String brandFilter, String engineFilter)` — missing output dir → `dataFound=false`, note explains; never pools across engines; note states layers are kept separate

- [ ] **Step 1: Synthetic fixture** (10 lines: 4 discovery answers on engine `e1` mentioning Alpha in 2, plus a trust record; engine `e2` empty of mentions):

```jsonl
{"engine":"e1","qid":"D001","dimension":"discovery","brand":null,"question":"best widget","answer":"Alpha is the top pick overall.","citations":[],"error":null,"ts":"2026-11-01T10:00:00"}
{"engine":"e1","qid":"D002","dimension":"discovery","brand":null,"question":"best gadget","answer":"Beta leads here.","citations":[],"error":null,"ts":"2026-11-01T10:00:01"}
{"engine":"e1","qid":"D003","dimension":"discovery","brand":null,"question":"best widget for pets","answer":"Alpha again, Beta close.","citations":[],"error":null,"ts":"2026-11-01T10:00:02"}
{"engine":"e1","qid":"D004","dimension":"discovery","brand":null,"question":"top rated widget","answer":"Gamma surprise winner.","citations":[],"error":null,"ts":"2026-11-01T10:00:03"}
{"engine":"e1","qid":"T001","dimension":"trust","brand":"Alpha","question":"Is Alpha legit?","answer":"Yes, Alpha is legitimate.","citations":[],"error":null,"ts":"2026-11-01T10:00:04"}
{"engine":"e2","qid":"D001","dimension":"discovery","brand":null,"question":"best widget","answer":"Beta only here.","citations":[],"error":null,"ts":"2026-11-01T10:00:05"}
```

(Add Gamma to `fixtures/brands.yaml` as `tier: emerging, country: CN` for the aggregate test.)

- [ ] **Step 2: Failing tests**

```java
package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.*;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {
    static final Path FIX = Path.of("src/test/resources/fixtures");

    @Test void analyzesPerEngineWithoutPooling(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.createDirectories(dir.resolve("output/raw"));
        Files.copy(FIX.resolve("brands.yaml"), dir.resolve("data/brands.yaml"));
        Files.copy(FIX.resolve("raw/synthetic.jsonl"),
                dir.resolve("output/raw/synthetic.jsonl"));
        var svc = new AnalysisService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), registryWithEngines("e1", "e2"));
        var result = svc.analyze(null, null);
        assertTrue(result.dataFound());
        assertEquals(2, result.engines().size());
        var e1 = result.engines().get(0);
        assertEquals(0.5, find(e1, "Alpha").mentionRate(), 1e-9);
        assertNull(find(e1, "Alpha").avgPosition());  // avgPosition null? Alpha pos 1&1 -> 1.0; adjust:
        var e2 = result.engines().get(1);
        assertEquals(0.0, find(e2, "Alpha").mentionRate(), 1e-9);
    }
    @Test void missingOutputDirReturnsDataFoundFalse(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.copy(FIX.resolve("brands.yaml"), dir.resolve("data/brands.yaml"));
        var svc = new AnalysisService(new BrandRepository(dir),
                new JsonlStore(new ObjectMapper()), registryWithEngines("e1"));
        var result = svc.analyze(null, null);
        assertFalse(result.dataFound());
        assertNotNull(result.note());
    }
    static io.github.wavehan.geovisibility.core.BrandMetrics find(
            AnalysisService.EngineAnalysis e, String name) {
        return e.brands().stream().filter(b -> b.brand().equals(name)).findFirst().orElseThrow();
    }
    static EngineRegistry registryWithEngines(String... names) {
        var p = new GeoProperties();
        var list = new java.util.ArrayList<GeoProperties.EngineConf>();
        for (String n : names) {
            var c = new GeoProperties.EngineConf();
            c.setName(n); c.setType("compat"); c.setLayer("search");
            c.setBaseUrl("https://x.test"); c.setModel("m");
            c.setApiKeyEnv("MISSING_KEY_" + n); c.setEnabled(true);
            list.add(c);
        }
        p.setEngines(list);
        return new EngineRegistry(p);
    }
}
```

(Fix the `avgPosition` assertion to `assertEquals(1.0, ...)` — Alpha appears at position 1 in both D001 and D003.)

- [ ] **Step 3: Implement** — read all records via `store.readAll(dataDir/output/raw)`; group by engine; for each configured engine with records: per-brand `BrandMetrics` (all brands from repository), tier/country averages via `Metrics`; layer from registry (default `"unknown"`); `note = "engines are analyzed separately by layer; pooled averages are intentionally not computed"`; filters applied before grouping.

```java
package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.core.*;
import io.github.wavehan.geovisibility.data.*;
import io.github.wavehan.geovisibility.engine.EngineInfo;
import io.github.wavehan.geovisibility.engine.EngineRegistry;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/** analyze_data tool backend: metrics over existing JSONL, per engine, never pooled. */
public class AnalysisService {
    public record BrandMetrics(String brand, String tier, String country,
                               Double mentionRate, Double avgPosition) { }
    public record EngineAnalysis(String engine, String layer, List<BrandMetrics> brands,
                                 Map<String, Double> tierAverages,
                                 Map<String, Double> countryAverages) { }
    public record AnalysisResult(List<EngineAnalysis> engines, boolean dataFound, String note) { }

    private final BrandRepository brands;
    private final JsonlStore store;
    private final EngineRegistry registry;

    public AnalysisService(BrandRepository brands, JsonlStore store, EngineRegistry registry) {
        this.brands = brands; this.store = store; this.registry = registry;
    }

    public AnalysisResult analyze(String brandFilter, String engineFilter) {
        var rawDir = ((GeoProperties) null) == null ? null : null; // replaced below
        throw new IllegalStateException("placeholder");
    }
}
```

The plan sketch above must be completed — the constructor needs `GeoProperties` for the data dir. Final signature: `AnalysisService(BrandRepository brands, JsonlStore store, EngineRegistry registry, GeoProperties props)`; implementation:

```java
public AnalysisResult analyze(String brandFilter, String engineFilter) {
    var rawDir = props.getDataDir().resolve("output/raw");
    if (!Files.isDirectory(rawDir)) {
        return new AnalysisResult(List.of(), false,
                "no measurement data found under " + rawDir
                + " - run measure_brand first or point geo.data-dir at the Python project");
    }
    var all = brands.load();
    var matchers = MentionParser.buildMatchers(all);
    var byEngine = store.readAll(rawDir).stream()
            .filter(r -> engineFilter == null || engineFilter.equals(r.engine()))
            .collect(Collectors.groupingBy(RawRecord::engine));
    var layerByName = registry.engines().stream()
            .collect(Collectors.toMap(EngineInfo::name, EngineInfo::layer));
    var out = new ArrayList<EngineAnalysis>();
    for (var entry : byEngine.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).toList()) {
        var pool = entry.getValue();
        var metrics = all.stream()
                .filter(b -> brandFilter == null || brandFilter.equals(b.name()))
                .map(b -> new BrandMetrics(b.name(), b.tier(), b.country(),
                        Metrics.mentionRate(b.name(), pool, matchers),
                        Metrics.avgPosition(b.name(), pool, matchers)))
                .sorted(Comparator.comparing(BrandMetrics::mentionRate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        out.add(new EngineAnalysis(entry.getKey(),
                layerByName.getOrDefault(entry.getKey(), "unknown"),
                metrics,
                Metrics.tierAverages(all, pool, matchers),
                Metrics.countryAverages(all, pool, matchers)));
    }
    return new AnalysisResult(out, !out.isEmpty(),
            "engines are analyzed separately (search vs memory layer); "
            + "pooled averages are intentionally not computed");
}
```

- [ ] **Step 4: Run** — `mvn test -Dtest=AnalysisServiceTest` then `mvn test`. Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(server): per-engine analysis without cross-layer pooling"`

---

### Task 10: ProbeService

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/server/ProbeService.java`
- Test: `src/test/java/io/github/wavehan/geovisibility/server/ProbeServiceTest.java`

**Interfaces:**
- Consumes: `EngineRegistry`
- Produces:
  - `record ProbeResult(String engine, boolean groundedLikely, boolean datedPhrasingFound, String evidence)`
  - `ProbeService(EngineRegistry registry)`
  - `ProbeResult probe(String engineName)` — unknown/unavailable engine → `ProbeResult(engine, false, false, "engine unavailable (no API key?)")`

Probe question: `"What are the best robot vacuums right now? Name the current flagship models."` Detection: `groundedLikely = !citations.isEmpty() || rawJson contains "url_citation" || "groundingMetadata" || "web_search"`; `datedPhrasingFound = answer matches "(?i)as of (my|our) (last )?update"`; `evidence = first 200 chars of Redactor.redact(answer)`.

- [ ] **Step 1: Failing tests** — three MockRestServiceServer cases: (a) citations present → grounded true; (b) answer "As of my last update, ..." no citations → grounded false, dated true; (c) unknown engine name → unavailable message. (Reuse the TestRegistry override pattern from Task 8.)

- [ ] **Step 2: Run** — expect compile FAIL.

- [ ] **Step 3: Implement**

```java
package io.github.wavehan.geovisibility.server;

import io.github.wavehan.geovisibility.engine.*;

import java.util.regex.Pattern;

/** probe_engine backend: is the engine actually retrieving, or answering from memory? */
public class ProbeService {
    public record ProbeResult(String engine, boolean groundedLikely,
                               boolean datedPhrasingFound, String evidence) { }

    private static final String QUESTION =
            "What are the best robot vacuums right now? Name the current flagship models.";
    private static final Pattern DATED = Pattern.compile("(?i)as of (my|our) (last )?update");

    private final EngineRegistry registry;
    public ProbeService(EngineRegistry registry) { this.registry = registry; }

    public ProbeResult probe(String engineName) {
        var adapter = registry.adapter(engineName);
        if (adapter.isEmpty())
            return new ProbeResult(engineName, false, false,
                    "engine unavailable (no API key or not configured)");
        EngineAnswer ans;
        try {
            ans = adapter.get().ask(QUESTION);
        } catch (EngineException e) {
            return new ProbeResult(engineName, false, false,
                    "probe failed: HTTP " + e.status() + ": " + Redactor.redact(e.body()));
        }
        boolean grounded = !ans.citations().isEmpty()
                || ans.rawJson().contains("url_citation")
                || ans.rawJson().contains("groundingMetadata")
                || ans.rawJson().contains("web_search");
        boolean dated = DATED.matcher(ans.answer()).find();
        String evidence = ans.answer() == null ? "" : ans.answer();
        evidence = Redactor.redact(evidence);
        evidence = evidence.substring(0, Math.min(200, evidence.length()));
        return new ProbeResult(engineName, grounded, dated, evidence);
    }
}
```

- [ ] **Step 4: Run** — `mvn test -Dtest=ProbeServiceTest`. Expected: PASS (3 tests).
- [ ] **Step 5: Commit** — `git commit -am "feat(server): engine grounding probe"`

---

### Task 11: MCP tool layer + STDIO wiring

**Files:**
- Create: `src/main/java/io/github/wavehan/geovisibility/server/AppConfig.java` (if not in Task 7), `server/McpTools.java`, `src/main/resources/logback-spring.xml`
- Modify: `GeoVisibilityMcpApplication.java` (`@EnableConfigurationProperties`)
- Test: `src/test/java/io/github/wavehan/geovisibility/server/McpToolsTest.java`

**Interfaces:**
- Consumes: all services
- Produces: Spring bean `ToolCallbackProvider` exposing 5 tools; methods return JSON strings
  - `String listBrands(String category)`
  - `String listEngines()`
  - `String measureBrand(String brand, String enginesCsv, Integer questionLimit)`
  - `String analyzeData(String brand, String engine)`
  - `String probeEngine(String engine)`

- [ ] **Step 1: Critical STDIO config — logging must not touch stdout**

`logback-spring.xml`:
```xml
<configuration>
  <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    <encoder><pattern>%d{HH:mm:ss.SSS} %-5level %logger{0} - %msg%n</pattern></encoder>
  </appender>
  <root level="INFO"><appender-ref ref="STDERR"/></root>
</configuration>
```

- [ ] **Step 2: Failing test — all five tool methods registered and callable**

```java
package io.github.wavehan.geovisibility.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class McpToolsTest {
    @Autowired McpTools tools;
    @Autowired org.springframework.ai.tool.ToolCallbackProvider provider;

    @Test void fiveToolsRegistered() {
        assertEquals(5, provider.getToolCallbacks().length);
    }
    @Test void listEnginesReturnsJson() throws Exception {
        var json = tools.listEngines();
        assertTrue(json.startsWith("[") || json.startsWith("{"));
    }
}
```

- [ ] **Step 3: Implement**

```java
// server/AppConfig.java
package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.*;
import io.github.wavehan.geovisibility.engine.EngineRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GeoProperties.class)
public class AppConfig {
    @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    @Bean JsonlStore jsonlStore(ObjectMapper m) { return new JsonlStore(m); }
    @Bean BrandRepository brandRepository(GeoProperties p) { return new BrandRepository(p.getDataDir()); }
    @Bean EngineRegistry engineRegistry(GeoProperties p) { return new EngineRegistry(p); }
    @Bean MeasureService measureService(BrandRepository b, JsonlStore s,
                                        EngineRegistry r, GeoProperties p) {
        return new MeasureService(b, s, r, p);
    }
    @Bean AnalysisService analysisService(BrandRepository b, JsonlStore s,
                                          EngineRegistry r, GeoProperties p) {
        return new AnalysisService(b, s, r, p);
    }
    @Bean ProbeService probeService(EngineRegistry r) { return new ProbeService(r); }
    @Bean org.springframework.ai.tool.ToolCallbackProvider tools(McpTools t) {
        return org.springframework.ai.tool.method.MethodToolCallbackProvider.builder()
                .toolObjects(t).build();
    }
}
```

```java
// server/McpTools.java
package io.github.wavehan.geovisibility.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wavehan.geovisibility.data.BrandRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** The 5 MCP tools exposed to agents. All return JSON strings. */
@Service
public class McpTools {
    private final ObjectMapper json;
    private final BrandRepository brands;
    private final io.github.wavehan.geovisibility.engine.EngineRegistry registry;
    private final MeasureService measure;
    private final AnalysisService analyze;
    private final ProbeService probe;

    public McpTools(ObjectMapper json, BrandRepository brands,
                    io.github.wavehan.geovisibility.engine.EngineRegistry registry,
                    MeasureService measure, AnalysisService analyze, ProbeService probe) {
        this.json = json; this.brands = brands; this.registry = registry;
        this.measure = measure; this.analyze = analyze; this.probe = probe;
    }

    @Tool(description = "List the brand sample: name, tier, country, aliases.")
    public String list_brands(@ToolParam(required = false) String category) {
        try {
            return json.writeValueAsString(brands.load().stream()
                    .filter(b -> category == null || category.isBlank())
                    .toList());
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    @Tool(description = "List configured engines with layer (search/memory) and API-key availability.")
    public String list_engines() {
        try { return json.writeValueAsString(registry.engines()); }
        catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    @Tool(description = "Measure one brand's AI visibility: runs questions on selected engines, "
            + "saves JSONL, returns per-engine mention rate and position. enginesCsv optional "
            + "(comma-separated); questionLimit default 10 (6 discovery + brand questions).")
    public String measure_brand(@ToolParam String brand,
                                @ToolParam(required = false) String enginesCsv,
                                @ToolParam(required = false) Integer questionLimit) {
        try {
            List<String> engines = enginesCsv == null || enginesCsv.isBlank() ? null
                    : Arrays.stream(enginesCsv.split(",")).map(String::trim).toList();
            return json.writeValueAsString(measure.measure(brand, engines, questionLimit));
        } catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    @Tool(description = "Analyze already-collected JSONL data: per-engine brand metrics and "
            + "tier/country aggregates. Layers are never pooled.")
    public String analyze_data(@ToolParam(required = false) String brand,
                               @ToolParam(required = false) String engine) {
        try { return json.writeValueAsString(analyze.analyze(brand, engine)); }
        catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }

    @Tool(description = "Probe whether an engine actually performs web retrieval "
            + "(vs answering from training memory).")
    public String probe_engine(@ToolParam String engine) {
        try { return json.writeValueAsString(probe.probe(engine)); }
        catch (Exception e) { return "{\"error\":\"" + e.getMessage() + "\"}"; }
    }
}
```

If the `@Tool`/`MethodToolCallbackProvider` API differs in the verified Spring AI version (method names moved between 1.0 and 2.0), adjust imports/signatures to the current API discovered in Task 1 Step 1 — the bean shape (a `ToolCallbackProvider` exposing the 5 methods) is the contract, not the class names.

- [ ] **Step 4: Run** — `mvn test`. Expected: all PASS including `McpToolsTest` (5 tools). Manual smoke: `mvn package -DskipTests && echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"t","version":"0"}}}' | java -jar target/geo-visibility-mcp-0.1.0.jar` → JSON-RPC response on stdout.
- [ ] **Step 5: Commit** — `git commit -am "feat(server): five mcp tools over stdio"`

---

### Task 12: README, end-to-end demo, publish

**Files:**
- Create: `README.md`; Modify: `.github/workflows/ci.yml` (unchanged from Task 1), sample `data/` copy script
- Test: manual end-to-end (documented in README)

- [ ] **Step 1: Copy sample data files into the repo** (synthetic, from Task 4 fixtures — place at `sample-data/`), and add README sections:

```markdown
# geo-visibility-mcp

Java/Spring MCP server that measures brand visibility in AI answer engines.
Data-compatible with [geo-visibility (Python)](https://github.com/Wave-Han/geo-visibility):
point `geo.data-dir` at a checkout of the Python project and this server can
analyze its collected data (and vice versa).

## Install
requires Java 17+
mvn package

## Register with Claude Code
claude mcp add geo-visibility --env DEEPSEEK_API_KEY=... -- java -jar target/geo-visibility-mcp-0.1.0.jar --geo.data-dir=/path/to/data

## Tools
| tool | what it does |
|---|---|
| list_brands | brand sample (tier/country/aliases) |
| list_engines | engines + layer + key availability |
| measure_brand | run questions, save JSONL, per-engine metrics |
| analyze_data | metrics over collected JSONL (never pools layers) |
| probe_engine | is the engine really retrieving? |

## Quick start (no keys needed)
java -jar target/geo-visibility-mcp-0.1.0.jar --geo.data-dir=sample-data
then call list_brands / analyze_data via any MCP client.

## 中文简介
Java 版品牌 AI 可见性测量 MCP 服务器:五类工具、双引擎适配、与 Python 版
数据格式互通;搜索层/记忆层分开分析,绝不混合。详见 docs/specs/。
```

- [ ] **Step 2: End-to-end verification** (requires DEEPSEEK_API_KEY):
  - `mvn package`
  - register in Claude Code per README, then ask the agent: *"use list_engines, then probe_engine deepseek, then measure_brand Alpha with limit 4, then analyze_data"* — all four must succeed; check `output/raw/deepseek.jsonl` grew.
- [ ] **Step 3: Cross-tool check** — run `analyze_data` with `geo.data-dir` pointed at the **Python repo** (720-record dataset): returns metrics per engine without error.
- [ ] **Step 4: Publish** — `gh repo create geo-visibility-mcp --public --source=. --push` with description "Java/Spring MCP server measuring brand visibility in AI answer engines — data-compatible with geo-visibility (Python)"; add topics `mcp` `mcp-server` `spring-ai` `java` `model-context-protocol` `geo`; edit Python repo README to cross-link.
- [ ] **Step 5: Commit + push** — `git add -A && git commit -m "docs: readme and publish" && git push`

---

## Self-Review (done)

- **Spec coverage:** §4 layout→Tasks 2-7; §5 tools→Tasks 8-11; §6 contract→Tasks 4-5; §7 adapters→Task 6; §8 metrics→Tasks 2-3,9; §9 testing→per-task + Task 11 smoke; §10 transport/CI→Tasks 1,11,12. v2 items intentionally absent.
- **Type consistency:** `RawRecord`/`Question`/`BrandMatcher` names used identically across tasks; MeasureService `skipped` computation simplified to `questions.size() - todo.size()` (noted inline).
- **Placeholders:** Task 9 Step 3 contains an intermediate sketch followed by the real implementation — executors use the final version with `GeoProperties` constructor. Task 11 notes API-name adjustment is allowed, bean shape is the contract.
- **Review Focus tests:** metachars (T2), missing key engine (T7/T8), missing output dir (T9), concurrent appends (T5), oversized body (T6).
