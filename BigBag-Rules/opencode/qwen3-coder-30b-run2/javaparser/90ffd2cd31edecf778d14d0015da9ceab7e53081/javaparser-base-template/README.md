# Generic Logback 1.4.0 Breaking Change Fixer

This is a generic JavaParser-based transformation tool to fix breaking changes in logback 1.4.0 dependencies, specifically targeting `AsyncAppender` constructor changes.

## Problem

When upgrading to logback 1.4.0, several breaking changes were introduced that affect Java code:
- `AsyncAppender` constructor signature changes requiring `includeCallerData` parameter
- Other related API changes in logging configuration

## Solution

This transformation automatically detects and fixes calls to:
- `new AsyncAppender()` -> `new AsyncAppender(false)`

## Usage

```bash
mvn compile
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project
```

The tool recursively processes all Java files in the specified directory, applying the fix where needed.