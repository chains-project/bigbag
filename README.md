# Transformer Agent Monorepo

Transformer Agent is a Maven multi-module workspace that bundles every component required to analyze and repair breaking dependency updates. It combines static analysis, API differencing, change impact reporting, Docker-based reproduction, and build-log classification into a single toolkit inspired by the Bacardi workflow.

```
📦 transformer-agent
├── core/                    ⇢ Pipelines, CLIs, orchestration utilities
├── docker-build/            ⇢ Docker helpers used by the pipelines
├── breaking-classifier/     ⇢ Maven build log parser & classifier
├── api_changes/             ⇢ japicmp-based API diff generator
├── spoon-line-analyzer/     ⇢ Spoon-powered per-line construct scanner
├── change-impact-reporter/  ⇢ Merges construct usage + API diffs into JSON
└── prompts/, analysis/, …   ⇢ Prompt templates, experiment data, reports
```

## Requirements

- JDK 21+
- Maven 3.9+
- Docker daemon (for the extraction/build flows inside the `core` module)

## Building Everything

```bash
mvn clean package
```

The reactor builds every module and produces shaded/fat JARs under each module’s `target/` directory (see the list above). Docker images are **not** built automatically; Docker is only required when the runtime pipelines pull/extract containers.

## Module Overview

| Module | Purpose | Key artifacts |
| --- | --- | --- |
| `core` | High-level pipelines (Breaking Update processor, Bump analyzer, Breaking Change CLI). Orchestrates Docker extraction, log classification, and result management. | `core/target/core-1.0.0-SNAPSHOT.jar`, `core/target/bump-analyzer-1.0.0-SNAPSHOT.jar` |
| `docker-build` | Thin wrapper around `docker-java` with utilities to pull images, extract projects/M2 folders, mount volumes, run builds, and fetch artifacts from containers. | `docker-build/target/docker-build-1.0.0-SNAPSHOT.jar` |
| `breaking-classifier` | Parses Maven build logs, groups compiler/test errors per file, classifies them into failure categories, and exports structured JSON (`BreakingReport`). | `breaking-classifier/target/breaking-classifier-1.0.0-SNAPSHOT-jar-with-dependencies.jar` |
| `spoon-line-analyzer` | Uses Spoon to inspect a source file and list every construct that appears on a specific line. Useful for mapping code usages back to API change events. | `spoon-line-analyzer/target/spoon-line-analyzer-1.0.0-SNAPSHOT-jar-with-dependencies.jar` |
| `api_changes` (`japicmp-diff-tool`) | Compares two JARs, captures rich API diffs (added/removed/changed members), and emits JSON that can be correlated with source constructs. | `api_changes/target/japicmp-diff-tool-1.0.0-SNAPSHOT-shaded.jar` |
| `change-impact-reporter` | Merges construct usage (from Spoon) and API diffs (from japicmp) into a single JSON report describing which lines are affected by each breaking change. | `change-impact-reporter/target/change-impact-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar` |

Each module also carries its own README with advanced flags, JSON schemas, and usage notes.

## Core Module Highlights

The `core` module is the “conductor” of the repo. It exposes multiple entry points:

- **Breaking Update Processor CLI** (`com.example.core.Main`): scans benchmark JSON files, extracts Docker images, reproduces builds, and classifies failures in bulk. See `core/README.md` for CLI flags.
- **Bump Analyzer** (`com.example.core.bump.BumpAnalyzerMain`): analyzes version combinations for a given dependency and emits reports about compatibility ranges.
- **Breaking Change CLI** (`com.example.core.breakingchange.BreakingChangeCli`): processes a single BreakingChange JSON by downloading the referenced Docker image, extracting the project to disk, locating build logs, running `breaking-classifier`, and writing a structured JSON summary (failure category, error counts, log path, etc.). Example:

```bash
java -cp core/target/core-1.0.0-SNAPSHOT.jar com.example.core.breakingchange.BreakingChangeCli \
  --input ./breaking-change.json \
  --output-dir ./extracted-projects \
  --result-json ./reports/breaking-change-report.json \
  --force \
  --verbose
```

## Module Quickstarts

### spoon-line-analyzer

```bash
java -jar spoon-line-analyzer/target/spoon-line-analyzer-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --project /path/to/project \
  --file src/main/java/package/Class.java \
  --line 42
```

### api_changes (japicmp-diff-tool)

```bash
java -jar api_changes/target/japicmp-diff-tool-1.0.0-SNAPSHOT-shaded.jar \
  old.jar new.jar output.json
```

### change-impact-reporter

```bash
java -jar change-impact-reporter/target/change-impact-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --project /path/to/project \
  --file src/main/java/package/Class.java \
  --line 42 \
  --old-jar old.jar \
  --new-jar new.jar \
  --output output.json
```

### breaking-classifier

```bash
java -jar breaking-classifier/target/breaking-classifier-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --log /path/to/maven.log \
  --json-output report.json
```

### docker-build (library)

Used programmatically from `core`. You rarely run it directly, but you can explore `se.kth.DockerBuild` to understand how project extraction and build reproduction are orchestrated.

## Next Steps

1. Build everything once with `mvn clean package`.
2. Inspect module READMEs for advanced scenarios (e.g., running classifiers, fusing Spoon + japicmp reports, executing the Bacardi-style pipeline).
3. Create a `.env` file at the repository root (or copy from `.env.example`) with all CLI options. Every key must be present; execution stops if a required entry is missing. Example:

```
INPUT_DIR=/absolute/path/to/breaking-updates
OUTPUT_DIR=./output
SPECIFIC_FILE=
CATEGORY=COMPILATION_FAILURE
EXTRACT=true
CLASSIFY=true
CLEAN=true
VERBOSE=false
JSON_OUTPUT=./reports/breaking-updates-results.json
```

4. Provide API keys and `.env` entries (see the `analysis/` folder and prompts/) if you plan to integrate LLM-based repair strategies downstream.
