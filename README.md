# geo-visibility-mcp

![build](https://github.com/Wave-Han/geo-visibility-mcp/actions/workflows/ci.yml/badge.svg)

Java / Spring MCP server that measures **brand visibility in AI answer engines** — reproducibly, and data-compatible with [geo-visibility (Python)](https://github.com/Wave-Han/geo-visibility).

Point `geo.data-dir` at a checkout of the Python project and this server can analyze its collected data (and vice versa): both tools read and write the same JSONL record format and the same question-id scheme, verified by a golden test of 360 questions.

## Why

Buyers increasingly ask AI engines before they buy. This server exposes that measurement to any MCP client (Claude Code, IDE agents, your own orchestration):

| tool | what it does |
|---|---|
| `list_brands` | brand sample (tier / country / aliases) |
| `list_engines` | engines + layer (search vs memory) + API-key availability |
| `measure_brand` | run questions, save JSONL, per-engine mention rate & position |
| `analyze_data` | metrics over collected JSONL — engines analyzed separately, never pooled |
| `probe_engine` | is the engine actually retrieving, or answering from memory? |

**Two-layer model**: a search-grounded engine measures today's recommendations; a memory-answering engine measures what training data remembers. The same engine can silently fall from one to the other — `probe_engine` detects it.

## Build

Requires Java 17+ and Maven.

```bash
mvn package
```

## Register with Claude Code

```bash
export DEEPSEEK_API_KEY=...        # and/or ZHIPU_API_KEY
claude mcp add geo-visibility \
  --env DEEPSEEK_API_KEY=... \
  -- java -jar target/geo-visibility-mcp-0.1.0.jar --geo.data-dir=/path/to/data
```

`--geo.data-dir` points at a directory containing `data/brands.yaml`, `data/questions.yaml` (and, once you measure, `output/raw/*.jsonl`). Use the bundled `sample-data/` to try it without any keys.

Then ask your agent: *"use list_engines, then analyze_data"*.

## Configuration

`application.yml` — engine adapters (deepseek `responses` type, zhipu `compat` type), layer tags, `api-key-env` names, concurrency/retries. API keys are read **only** from environment variables; every persisted error string is redacted (`key=***`) and HTTP error bodies are truncated to 200 chars so provider reasons ("model retired", "quota") stay visible.

## Design notes

- Mentions are counted by deterministic regex with brand aliases (`Roomba`→iRobot, `Deebot`→Ecovacs); the LLM semantic layer (verdicts) is deferred to v2 — no fabricated semantics.
- `analyze_data` never averages across engine layers; the gap between "remembered" and "recommended today" is the finding.
- Append-only JSONL, resume-safe: successful question ids are skipped, errored ones re-run — interop with the Python tool's files is byte-compatible.
- 37 tests, including cases ported from the Python suite; CI runs on JDK 17 and 21.
- STDIO transport; all logs go to stderr so stdout carries only the protocol.

## Limitations

- API behavior is not identical to consumer web UIs.
- Discovery questions are a sample; treat rates as relative, not absolute.
- Provider model names change often — they live in config, verify before a run.

## 中文简介

Java 版品牌 AI 可见性测量 MCP 服务器(Spring Boot 3 + Spring AI 2.0):五类工具、双引擎适配、与
[Python 版](https://github.com/Wave-Han/geo-visibility) 数据格式互通(golden 测试验证 360 个问题
ID 逐一致);搜索层/记忆层分开分析,绝不混合;断点续跑、别名匹配、密钥脱敏。设计文档见
`docs/specs/`。

## License

MIT
