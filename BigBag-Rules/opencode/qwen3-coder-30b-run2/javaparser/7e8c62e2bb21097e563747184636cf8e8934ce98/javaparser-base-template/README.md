# Generic JavaParser Dependency Transformation Tool

This tool provides a generic mechanism to fix breaking dependency updates in Java projects using JavaParser.

## Purpose

Fix compilation issues caused by breaking changes in dependencies (such as the acceptance test harness) by automatically transforming Java source code to adapt to new APIs.

## Usage

```bash
java -cp target/javaparser-transformation-tool-1.0-SNAPSHOT.jar github.chains.Main /path/to/source
```

## How It Works

The tool recursively processes all Java files in the specified directory:
1. Parses each Java file using JavaParser
2. Applies generic transformation rules to detect and fix breaking changes
3. Writes the transformed code back to the original files

## Extending the Tool

To customize the tool for specific breaking changes:

1. Extend the `TransformVisitor` class
2. Override methods like `visit(MethodCallExpr)` to detect and transform specific patterns
3. Add rules for detecting deprecated APIs and converting them to new equivalents

## Example Transformation Rules

The tool can be extended to handle patterns like:
- Method signature changes (adding/removing parameters)
- Method name changes 
- Class/package relocation
- API deprecations and replacements

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Build

```bash
mvn clean package
```