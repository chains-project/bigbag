# Change Impact Reporter

Command-line tool that merges the Spoon analysis (`spoon-line-analyzer`) with japicmp’s API diff (`api_changes`). It produces a JSON report listing the AST constructs found on a target line and the API changes that may affect them.

## Requirements

- JDK 21+
- Maven 3.9+
- The `spoon-line-analyzer` and `api_changes` modules must be available (running `mvn package` at the repository root takes care of this).

## Build

```bash
mvn -pl change-impact-reporter -am package
```

The command generates the fat jar at `change-impact-reporter/target/change-impact-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar`.

## Usage

```bash
java -jar change-impact-reporter/target/change-impact-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --project /path/to/maven/project \
  --file src/main/java/package/Class.java \
  --line 123 \
  --old-jar /path/to/old.jar \
  --new-jar /path/to/new.jar \
  --output /path/to/report.json
```

### Key options

- `--project`, `-p`: Maven project containing the source file.
- `--file`, `-f`: target source file (absolute or relative to the project root).
- `--line`, `-l`: 1-based line number to inspect.
- `--old-jar`: previous dependency version.
- `--new-jar`: new dependency version.
- `--output`, `-o` (optional): output JSON path (defaults to `change-impact-report.json`).
- `--verbose`, `-v` (optional): prints the full stack trace on failure.

## Output

The JSON report includes:

- Project, file and line metadata.
- Information about the compared JARs.
- A list of Spoon constructs (`METHOD_INVOCATION`, `FIELD_ACCESS`, etc.) found on the selected line, each enriched with:
  - Dependency origin (project source, Maven dependency, JDK).
  - Related API changes at class or member level as reported by japicmp.

## Example

```bash
java -jar change-impact-reporter/target/change-impact-reporter-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  --project /Users/frankreyesgarcia/Documents/WORK/PHD/Bacardi/projects/0abf7148300f40a1da0538ab060552bca4a2f1d8/biapi \
  --file /Users/frankreyesgarcia/Documents/WORK/PHD/Bacardi/projects/0abf7148300f40a1da0538ab060552bca4a2f1d8/biapi/src/main/java/xdev/tableexport/export/ReportBuilder.java \
  --line 369 \
  --old-jar /Users/frankreyesgarcia/Documents/WORK/PHD/transformer-agent/api_changes/jasperreports-6.18.1.jar \
  --new-jar /Users/frankreyesgarcia/Documents/WORK/PHD/transformer-agent/api_changes/jasperreports-6.19.1.jar \
  --output /Users/frankreyesgarcia/Documents/WORK/PHD/transformer-agent/change-impact-reporter/target/report-line-369.json
```

The JSON file specified via `--output` contains the final report.

