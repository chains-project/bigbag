# Spoon Line Analyzer

CLI utility built on top of Spoon to inspect a Maven project and list every construct (imports, method calls, constructor calls, field accesses, type references, annotations) used on a specific source line. Each construct is classified with the dependency it originates from (project sources, Maven dependencies, JDK, or unknown).

## Prerequisites

- JDK 17+
- Maven 3.9+

## Build

```bash
mvn package
```

This produces a runnable fat jar under `target/spoon-line-analyzer-0.1.0-SNAPSHOT-jar-with-dependencies.jar`.

## Usage

```bash
java -jar target/spoon-line-analyzer-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
  --project /path/to/maven/project \
  --file src/main/java/com/example/MyClass.java \
  --line 42
```

Options:

- `--project`, `-p`: path to the Maven project to analyse.
- `--file`, `-f`: source file to inspect (absolute path or relative to the project root).
- `--line`, `-l`: 1-based line number to analyse.
- `--dependency`, `-d`: optional filter to only print constructs whose dependency origin or coordinates contain the supplied token.
- `--verbose`, `-v`: show stack traces when a failure occurs.

Example output:

```
- METHOD_INVOCATION   com.example.service.UserService#findAll()
  Dependency: com.example:service-module:1.2.0 (MAVEN_DEPENDENCY)
  Artifact path: /path/to/local/repo/com/example/service-module/1.2.0/service-module-1.2.0.jar
- IMPORT             java.util.List
  Dependency: java:java.base (JDK)
```

## Testing

```bash
mvn test
```

Tests build a Spoon model of a bundled sample Maven project and assert that the analyzer correctly reports import usage as well as field and method accesses on a selected line. This gives confidence that both the AST scan and the dependency resolution logic work end-to-end.

## Development Notes

- The analyzer uses `MavenLauncher` to build a full Spoon model (classpath resolution enabled) before scanning individual compilation units.
- Dependency ownership is resolved via the runtime location of each referenced type; for Maven dependencies the jar manifest is read to recover group/artifact/version.
- Import statements are processed separately because they are not visited by the standard `CtScanner` traversal.

