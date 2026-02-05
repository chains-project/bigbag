# Breaking Classifier

`breaking-classifier` is a command-line tool that parses Maven build logs and extracts every compiler error, printing the affected file, line, column, and message (including the compiler's symbol/location hints).

## Requirements

- JDK 21+
- Maven 3.9+

## Build

From the repository root:

```bash
mvn -pl breaking-classifier -am package
```

This command builds all required dependencies and produces:

- `breaking-classifier/target/breaking-classifier-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

## Usage

Run the shaded JAR and pass the path to a change-impact report:

```bash
java -jar breaking-classifier/target/breaking-classifier-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  /path/to/maven-build.log
```

To also produce a JSON report:

```bash
java -jar breaking-classifier/target/breaking-classifier-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  /path/to/maven-build.log \
  --json-output build/reports/compiler-errors.json
```

Example output:

```
Failure log: /quickfixj/quickfixj-core/target/failsafe-reports/build.log
Failure category: COMPILATION_FAILURE
/quickfixj/quickfixj-core/src/main/java/quickfix/mina/acceptor/AbstractSocketAcceptor.java [COMPILATION_FAILURE]
  136:18: cannot find symbol
    symbol:   method setUseClientMode(boolean)
    location: variable sslFilter of type quickfix.mina.ssl.SSLFilter
/quickfixj/quickfixj-core/src/main/java/quickfix/mina/initiator/IoSessionInitiator.java [COMPILATION_FAILURE]
  195:22: cannot find symbol
    symbol:   method setUseClientMode(boolean)
    location: variable sslFilter of type quickfix.mina.ssl.SSLFilter
/quickfixj/quickfixj-core/src/main/java/quickfix/mina/ssl/SSLFilter.java [COMPILATION_FAILURE]
  43:9: constructor SslFilter in class org.apache.mina.filter.ssl.SslFilter cannot be applied to given types;
    required: javax.net.ssl.SSLContext
    found: javax.net.ssl.SSLContext,boolean
    reason: actual and formal argument lists differ in length
  78:38: cannot find symbol
    symbol:   variable PEER_ADDRESS
    location: class quickfix.mina.ssl.SSLFilter
```

If no compiler errors are found, the tool prints `No compiler errors detected in <file>` and exits with code `0`.

When `--json-output` is supplied, the tool writes the extracted errors as a pretty-printed JSON object to the provided file (creating parent directories if needed).

The JSON structure contains:

- `originalFailurePath`: path to the parsed Maven build log.
- `failureCategory`: category detected from Maven log patterns (e.g., `COMPILATION_FAILURE`, `TEST_FAILURE`, `JAVA_VERSION_FAILURE`, `DEPENDENCY_RESOLUTION_FAILURE`).
- `errorsByFile`: array where each element groups the errors for a single source file and includes:
  - `filePath`
  - `errors`: an array of objects with `lineNumber`, `columnNumber`, `message`, and `details`.

## Testing

```bash
mvn -pl breaking-classifier -am test
```

