# Transformer Agent Monorepo

This repository is a Maven multi-module project that bundles three complementary tools:

- `spoon-line-analyzer`: a Spoon-based CLI that inspects a Java source file and lists every construct found on a given line.
- `api_changes` (`japicmp-diff-tool`): a japicmp-powered utility that compares two JAR files and produces an enriched API change report.
- `change-impact-reporter`: a CLI that merges both analyses into a single JSON report.

## Requirements

- JDK 21+
- Maven 3.9+

## Build

```bash
mvn clean package
```

The Maven reactor builds every module and produces:

- `spoon-line-analyzer/target/spoon-line-analyzer-1.0.0-SNAPSHOT-jar-with-dependencies.jar`
- `api_changes/target/japicmp-diff-tool-1.0.0-SNAPSHOT-shaded.jar`
- `change-impact-reporter/target/change-impact-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

## Module Quickstart

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

See each module's README for in-depth instructions and additional examples.

