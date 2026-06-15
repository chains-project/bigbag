# BigBag
>*Agentic Generation of AST Transformation Rules for Fixing Breaking Updates*

BigBag is a pipeline that automatically repairs Java projects broken by a dependency update. Given a project that fails to compile after bumping a library version, BigBag (1) reproduces the failure inside a reproducible Docker environment, (2) pinpoints every source line impacted by the API change, (3) constructs a rich context prompt, and (4) drives an LLM-backed coding agent to write a source-transformation rule (Spoon or JavaParser) that brings the project back to a green build.

---

## Repository Structure

```
📦 transformer-agent (BigBag artifact)
├── core/                    ⇢ Orchestration pipelines, CLIs, repair entry-points
├── docker-build/            ⇢ Docker helpers — pull, extract, mount, reproduce
├── breaking-classifier/     ⇢ Maven build-log parser & failure classifier
├── api_changes/             ⇢ japicmp-based API diff generator
├── spoon-line-analyzer/     ⇢ Spoon-powered per-line construct scanner
├── change-impact-reporter/  ⇢ Fuses Spoon + japicmp output into a single JSON
├── BigBag-Rules/            ⇢ Raw repair-rule outputs for every evaluated combination
│   ├── opencode/            ⇢   OpenCode agent runs
│   │   ├── deepseek-v3-2/   ⇢     DeepSeek V3 results (spoon / javaparser)
│   │   ├── gpt-5-4-mini/    ⇢     GPT-4o-mini results
│   │   └── qwen3-coder-30b/ ⇢     Qwen3-Coder 30B results
│   └── geminiCLI/           ⇢   Gemini CLI agent runs
├── prompts/                 ⇢ Prompt templates used in the evaluation
├── analysis/                ⇢ Experiment data and result reports
└── llm/                     ⇢ Standalone LLM client utilities
```

---

## Approach Overview

```
Breaking update JSON
        │
        ▼
┌─────────────────────┐
│  Docker Extraction  │  Pull image → extract project + .m2 to disk
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Build Reproduction │  Re-run Maven inside Docker; capture build.log
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Failure Classifier │  Parse log → failure category + error locations
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  API Diff (japicmp) │  Compare old JAR vs new JAR → structural API changes
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Impact Analysis    │  Map API changes to source lines via Spoon
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Prompt Generation  │  Assemble context: errors + API diff + impacted lines
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│   LLM Coding Agent  │  Agent writes a Spoon / JavaParser transformation rule
│  (Gemini · OpenCode)│
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Rule Execution &   │  Apply rule → rebuild → verify green build
│  Verification       │
└─────────────────────┘
```

### LLM Agents & Rule Generators Evaluated

| Agent backend | Models evaluated |
|---|---|
| **OpenCode** | DeepSeek V3, GPT-4o-mini, Qwen3-Coder 30B |
| **Gemini CLI** | Gemini 2.x |

| Rule generator | Description |
|---|---|
| **Spoon** | AST-level Java source transformation |
| **JavaParser** | Token/AST-based transformation alternative |

---

## Requirements

| Dependency | Version |
|---|---|
| JDK | 21+ |
| Maven | 3.9+ |
| Docker | daemon running |
| Roseau (API diff) | built from source (see setup below) |

---

## Setup

### 1 — Install Roseau

Roseau is a dependency not yet on Maven Central. The provided script clones and installs it:

```bash
bash setup.sh
```

### 2 — Build all modules

```bash
mvn clean package -DskipTests
```

This produces a fat JAR for every module under the respective `target/` directory.

### 3 — Configure the environment

Copy `.env.example` to `.env` and fill in the required values:

```bash
cp .env.example .env
```

Key variables:

```dotenv
# LLM agent backend: "gemini" | "opencode"
AGENT_NAME=gemini
LLM_API_KEY=your_api_key_here

# Code transformation engine: "spoon" | "javaparser"
RULE_GENERATOR=spoon

# Paths to the rule-generator template and its API docs
BASE_TEMPLATE=/path/to/spoon-base-template
API_DOCS=/path/to/spoon-javadoc

# Input: directory of breaking-update JSON files (one per project)
INPUT_DIR=/path/to/breaking-updates

# Output: extracted projects, logs, and repair results
OUTPUT_DIR=/path/to/output

# Pipeline mode: "agent" (LLM agent) | "model" (direct prompt)
REPAIR_PIPELINE=agent
```

---

## Running the Repair Pipeline

### Process all breaking updates in a directory

```bash
java -jar core/target/core-1.0.0-SNAPSHOT.jar \
  --input-dir "$INPUT_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --repair-pipeline agent \
  --verbose
```

### Process a single breaking update

```bash
java -jar core/target/core-1.0.0-SNAPSHOT.jar \
  --input-dir "$INPUT_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --specific-file <breakingCommit> \
  --repair-pipeline agent
```

### Classify only (no repair)

Reproduce the failure and classify the build log without attempting repair:

```bash
java -jar core/target/core-1.0.0-SNAPSHOT.jar \
  --input-dir "$INPUT_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --classify \
  --no-repair
```

The full list of CLI flags is documented in `core/README.md`.

---

## Module Reference

| Module | Artifact | Purpose |
|---|---|---|
| `core` | `core-1.0.0-SNAPSHOT.jar` | End-to-end pipelines and CLI entry-points |
| `docker-build` | `docker-build-1.0.0-SNAPSHOT.jar` | Docker image pull, project extraction, build reproduction |
| `breaking-classifier` | `breaking-classifier-…-jar-with-dependencies.jar` | Build-log parsing and failure categorisation |
| `api_changes` | `japicmp-diff-tool-…-shaded.jar` | JAR-level API diff (added/removed/changed members) |
| `spoon-line-analyzer` | `spoon-line-analyzer-…-jar-with-dependencies.jar` | Per-line Java construct identification |
| `change-impact-reporter` | `change-impact-reporter-…-jar-with-dependencies.jar` | Merge Spoon + japicmp output into impact JSON |

Each module ships its own README with advanced flags and JSON schema descriptions.

---

## Replication

The `BigBag-Rules/` directory contains the raw rule outputs produced by every agent–model–generator combination evaluated in the paper. To re-run a specific combination:

```bash
# Example: OpenCode + DeepSeek V3 + Spoon
AGENT_NAME=opencode \
LLM_MODEL=deepseek/deepseek-chat \
RULE_GENERATOR=spoon \
java -jar core/target/core-1.0.0-SNAPSHOT.jar \
  --input-dir "$INPUT_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --repair-pipeline agent
```

Results are written per-commit under `OUTPUT_DIR/<breakingCommit>/` and summarised in the JSON report specified by `JSON_OUTPUT`.

---

## License

This artifact is released for academic reproducibility. See `LICENSE` for terms.
