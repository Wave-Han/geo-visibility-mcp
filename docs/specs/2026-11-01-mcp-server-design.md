# geo-visibility-mcp — Design Spec

Date: 2026-11-01 · Status: approved design (pending implementation plan)
Companion project: [Wave-Han/geo-visibility](https://github.com/Wave-Han/geo-visibility) (Python)

## 1. Purpose

A Java MCP server that exposes brand AI-visibility measurement as tools for AI
agents (Claude Code and any MCP client). It is the Java/Spring counterpart of
the Python toolkit: same data formats, same methodology, different stack.

Primary goal: a production-quality open-source artifact demonstrating
enterprise Java × MCP agent integration (Spring Boot 3 + Spring AI 2.0).

Secondary goal: agents become a real distribution channel for the measurement
methodology ("ask your agent how visible your brand is").

## 2. Version scope

**v1 (this spec)**
- Pure-Java core domain: question generation, 2 engine adapters (deepseek,
  zhipu), deterministic mention parsing (aliases), metric aggregation
- Reads and writes the Python project's data formats (YAML config, JSONL
  records) — can analyze data collected by the Python tool out of the box
- 5 MCP tools over STDIO transport
- Boot-strapped Spring Boot app, Maven, Java 17, JUnit 5, CI

**v2 (explicitly deferred)**
- LLM verdict/sentiment parsing, HTML reports, more engines (chatgpt, gemini,
  qwen, grok), Streamable HTTP transport, quarter-over-quarter diffing

## 3. Non-goals

- Not a service, SaaS, or client-facing product (independent research tool)
- No scraping of consumer web UIs; official provider APIs only
- No secrets in config files or logs

## 4. Project layout

Single Maven module, package-layered:

```
io.github.wavehan.geovisibility
├── server/   MCP tool definitions (@Tool) + Spring configuration
├── core/     domain models + parsing + metrics (no Spring dependencies)
├── engine/   provider adapters (RestClient + thread pool + retry)
└── data/     YAML config loading + JSONL read/write
```

- `core` must stay framework-free so it is unit-testable and portable.
- `server` depends on `core`, `engine`, `data`.
- Code comments and identifiers in English (project convention).

Coordinates: `io.github.wavehan:geo-visibility-mcp:0.1.0`, Java 17,
Spring Boot 3.3.x, Spring AI 2.0.x (`spring-ai-starter-mcp-server`), Jackson
+ `jackson-dataformat-yaml`. Exact dependency versions are verified against
Maven Central at implementation time (Spring AI moves fast).

## 5. MCP tools (v1)

All tools return JSON-serializable result objects. Engine failures never
throw to the MCP layer; tools report partial results with an `errors` array.

| Tool | Input | Output |
|---|---|---|
| `list_brands` | `category?` | brands: name, domain, tier, country, aliases |
| `list_engines` | — | engines: name, layer (search/memory), model, available (has key) |
| `measure_brand` | `brand`, `engines?`, `questionLimit?` (default 10) | runs questions, appends JSONL, returns per-engine summary metrics for that brand |
| `analyze_data` | `brand?`, `engine?` | metrics from existing JSONL: per-brand mention rate / avg position / verdict distribution, tier & country aggregates (per engine, never pooled across layers) |
| `probe_engine` | `engine` | grounding probe result: groundedLikely + evidence |

`measure_brand` default flow: 6 category discovery questions + brand-level
questions up to `questionLimit`; appends to
`<data-dir>/output/raw/<engine>.jsonl`; resume semantics identical to the
Python tool (existing successful qids are skipped; errored qids re-run).

`analyze_data` computes metrics per engine and tags each engine with its
layer (search/memory). It refuses to average across layers and says so in
the result payload.

`probe_engine` asks one current-events question with the search tool enabled
and inspects the response for retrieval artifacts (citations list,
url_citation annotations, grounding metadata) and dated phrasing
("as of my last update"). Returns evidence snippets, trimmed and redacted.

## 6. Data compatibility contract

The server operates on a `geo.data-dir` (default `./data`) containing:

```
<data-dir>/data/brands.yaml        # same schema as Python project
<data-dir>/data/questions.yaml     # same schema as Python project
<data-dir>/output/raw/<engine>.jsonl   # shared record store
```

Pointing `geo.data-dir` at a checkout of the Python repository works with
zero conversion.

**JSONL record fields (exact):** `engine`, `qid`, `dimension`
(discovery|comparison|trust|price_longtail|freshness), `brand` (nullable),
`question`, `answer`, `citations` (array of strings), `error` (nullable
string, redacted), `ts` (ISO-8601). One record per line, UTF-8, append-only.

**brands.yaml:** `category` + `brands[]` with `name`, `domain?`, `tier`
(leader|growth|emerging), `country` (CN|INTL), `competitors[]`, `aliases[]`.

**questions.yaml:** `category_term`, `discovery` seeds (intents,
subcategories, qualifiers, budget_prices, max_questions), `brand_templates`
per dimension. Question generation must produce the same qid scheme as the
Python tool (D###/C###/T###/P###/F###) so both tools resume each other's
files.

**Engine config** lives in Spring `application.yml` (not the data dir):

```yaml
geo:
  data-dir: ./data
  engines:
    - name: deepseek
      type: responses          # responses | compat
      layer: search
      base-url: https://api.deepseek.com/v1
      model: deepseek-v4-flash  # verify at run time; providers rename often
      api-key-env: DEEPSEEK_API_KEY
      enabled: true
    - name: zhipu
      type: compat
      layer: memory            # glm-4-flash does not execute web_search (verified)
      base-url: https://open.bigmodel.cn/api/paas/v4
      model: glm-4-flash
      api-key-env: ZHIPU_API_KEY
      enabled: true
      extra-body:
        tools: [{type: web_search, web_search: {enable: true, search_result: true}}]
```

Tuning: `geo.measure.concurrency` (default 4), `geo.measure.retries` (2),
HTTP timeout 120s.

## 7. Engine adapters

- `responses` type: POST `<base-url>/responses` with
  `{model, tools:[{type:"web_search"}], input}`; answer text and citations
  extracted from `output[]` message parts and `url_citation` annotations
  (same defensive parsing as the Python `OpenAIResponsesEngine`).
- `compat` type: POST `<base-url>/chat/completions` with OpenAI-compatible
  body plus `extra-body` merged in; citations from top-level `citations`
  field when present.
- Transport: Spring `RestClient`, fixed thread pool (`concurrency`), retry
  with exponential backoff (1s, 2s), then an error record is written.
- API keys are read only from the env var named by `api-key-env`. Keys are
  never placed in URLs. Every error message passes a redaction step
  (`key=...` → `key=***`) before persisting.
- HTTP error responses are recorded with status + response-body excerpt
  (first 200 chars), so provider reasons (model retired, quota) stay visible.

## 8. Parsing and metrics (ported, framework-free)

- Alias-aware word-boundary matching (`Roomba`→iRobot, `Deebot`→Ecovacs);
  mention position = order of first appearance.
- Mention rate = mentions / discovery answers (per engine pool).
- Verdict distribution placeholder in v1: heuristic parser cannot extract
  verdicts; `analyze_data` reports verdict fields as unavailable unless a
  persisted LLM parse exists (Python `parsed_*.json` is NOT read in v1;
  fields return `null` with a note). No fabricated semantics.
- Tier/country aggregates computed per engine, never pooled across layers.

## 9. Testing

| Area | Cases (ported from the Python suite) |
|---|---|
| Parsing | alias counts as canonical brand; word boundary prevents partial match; position by first appearance |
| Metrics | mention rate over discovery only; avg position; verdict filtering by brand; tier & country aggregation |
| Resume | successful qids skipped, errored re-run, corrupted lines ignored |
| Security | `key=` redaction in error strings |
| Data | brands.yaml/questions.yaml load; qid scheme identical to Python (golden comparison against the Python project's generated question list) |
| Adapters | MockWebServer: success parse (both types), citations extraction, HTTP error body propagation, retry-then-error-record |
| MCP | Spring context smoke test: STDIO transport starts, all 5 tools registered |

Fixture data is synthetic; real collected data is never committed.

## 10. Transport, integration, release

- STDIO via `spring-ai-starter-mcp-server`. README documents Claude Code
  registration (`.mcp.json` snippet with `java -jar` command and env vars).
- CI: GitHub Actions, JDK 17 + 21 matrix, `mvn verify`.
- MIT license; README in English with a Chinese section; cross-linked with
  the Python repo in both directions.
- Repo: `Wave-Han/geo-visibility-mcp`.

## 11. Risks

| Risk | Mitigation |
|---|---|
| Spring AI API churn (2.0.x) | pin exact version in CI; adapters isolated in `server` package |
| Provider model renames | model names are config, not code; probe + clear error bodies |
| Scope creep toward v2 | non-goals enforced by spec; LLM parse/report interfaces reserved but unimplemented |
| Accidentally committing real data | fixtures synthetic; .gitignore excludes `output/` |
