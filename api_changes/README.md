# Japicmp Diff Tool

Java CLI utility that compares two JAR files using [japicmp](https://github.com/siom79/japicmp) and produces a JSON report enriched with detailed metadata for every detected change.

## Requirements

- JDK 11 or newer
- Apache Maven 3.9+

## Build

```bash
mvn clean package
```

The build produces a shaded JAR ready to run at `target/japicmp-diff-tool-1.0.0-SNAPSHOT-shaded.jar`.

## Usage

```bash
java -jar target/japicmp-diff-tool-1.0.0-SNAPSHOT-shaded.jar <old-jar> <new-jar> [output-json]
```

- `old-jar`: path to the baseline JAR (previous version).
- `new-jar`: path to the JAR to compare against the baseline.
- `output-json` (optional): JSON file to write. Defaults to `japicmp-report.json` in the current directory.

For each impacted class, the report lists:

- Change status (added, removed, modified).
- Binary and source compatibility indicators.
- Detailed differences for classes, methods, constructors, and fields.
- Affected annotations, implemented interfaces, modifiers, and class file versions.
- Member-level metadata including old/new signatures, types, modifiers, compatibility flags, and parameter types.

## Example

```bash
java -jar target/japicmp-diff-tool-1.0.0-SNAPSHOT-shaded.jar libs/my-lib-1.0.0.jar libs/my-lib-1.1.0.jar output/diff.json
```

## Notes

- All classes (down to `private` members) are inspected, including synthetic members, to avoid missing relevant changes.
- Classes without modifications are omitted from the final report.
- Missing classes on the classpath are ignored so external dependencies do not abort the comparison.

