# Generic Jackson API Compatibility Transformer

This is a generic JavaParser-based transformation tool designed to fix breaking changes in Jackson dependencies across Maven projects.

## Problem Addressed

This tool solves the issue where Maven projects break due to incompatible Jackson dependency versions. Specifically, it addresses scenarios where:

1. Jackson core and databind have mismatched versions
2. Newer Jackson versions introduce breaking changes (e.g., `StreamWriteException` not found)
3. Projects need to be retrofitted to work with updated dependency versions

## Usage

```bash
# Run the transformation on a directory
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project/src/main/java
```

Or run directly on the OCR4all project:
```bash
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /workspace/OCR4all/src/main/java
```

## Transformation Rules Implemented

Currently, this tool serves as a framework that can be extended with specific transformation rules for:
- Exception handling patterns that became incompatible
- Method signature changes
- Removed or relocated classes
- API changes affecting Jackson serialization

## Extending This Template

To create specific transformations for a project:
1. Extend the `ApiCompatibilityTransformer` class
2. Add pattern matching logic in the `visit()` methods
3. Implement specific transformation logic for the breaking changes

## Example Pattern Matching

The transformation framework supports:
- Method call pattern detection
- Import statement analysis
- Exception handling pattern rewriting
- Type resolution for API compatibility

This design allows the same tool to be reused across projects with different breaking Jackson changes, ensuring maximum reusability.