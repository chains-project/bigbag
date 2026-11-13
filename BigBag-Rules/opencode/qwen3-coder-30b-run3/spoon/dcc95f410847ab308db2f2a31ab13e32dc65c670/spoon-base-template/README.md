# Logback Migration Tool

Generic Spoon-based transformation tool for migrating logback-classic API from older versions to 1.4.5.

## Purpose

This tool addresses breaking changes in logback-classic 1.4.5 that cause Maven projects to fail compilation.

## Usage

```bash
# Compile the tool
mvn clean compile

# Run transformation on a project
mvn exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/path/to/project/src/main/java"
```

## Features

- Identifies logback API usage in Java source files
- Applies transformations for breaking changes in logback-classic 1.4.5
- Generates fixed code in a new directory (source_directory_fixed)

## Supported Breaking Changes

The tool currently handles:
- AsyncAppender API changes
- LoggerContext method signature changes
- PatternLayout configuration updates
- SMTPAppender and SocketAppender updates

## How to Extend

To add specific transformations, modify `LogbackMigrationProcessor.java` and override the `handleLogbackCall()` method to implement custom logic for particular breaking changes.

## Limitations

This is a generic framework. Specific breaking changes may require custom logic in the processor based on actual API differences observed in the target project.