# Generic JavaParser Transformation for Jenkins Plugin Dependency Fixes

This tool provides a generic JavaParser transformation rule that can be used to fix breaking changes in Jenkins plugin dependencies.

## Purpose

When Jenkins plugins experience breaking changes due to dependency updates, this tool can help automatically fix common patterns of breaking changes by transforming Java source code files.

## Usage

```bash
java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main <source-directory>
```

Where:
- `<source-directory>` is the path to the Java source code that needs to be transformed

## What it does

The transformation handles common breaking changes such as:

1. Method signature changes (adding/removing parameters)
2. Method name changes
3. API migration patterns

## Extending for Specific Cases

To customize this transformation for a specific breaking change:

1. Modify the `handleMethodCallChanges` method to detect the specific pattern you're fixing
2. Add the appropriate logic to transform specific method calls
3. Update the detection logic in `shouldAddParameter` and `shouldRenameMethod` methods

## Example

For a breaking change where `oldMethod(String)` was changed to `newMethod(String, boolean)`, you would:
1. Add detection logic for `oldMethod` calls
2. Add logic to append `false` as a default parameter value

## Requirements

- Java 8+
- Maven for building