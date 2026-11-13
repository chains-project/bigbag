# Generic Spoon Transformation for pm-wicket-utils 5.0 Breaking Changes

This project implements a generic Spoon transformation rule to fix compilation errors caused by breaking changes in pm-wicket-utils 5.0, specifically:

1. Removal of `javax.validation` package
2. Removal of `org.apache.commons.beanutils.PropertyUtils`

## Problem Description

The @wicket-crudifier/ project fails to compile due to breaking changes in pm-wicket-utils 5.0:

- `javax.validation` package was removed completely
- `org.apache.commons.beanutils.PropertyUtils` was removed

This causes compilation errors in files like:
- `ListControlGroups.java`
- `CrudifierView.java`

## Solution Approach

We've created a generic Spoon transformation that:

1. Identifies and removes deprecated `javax.validation.*` imports
2. Identifies and removes deprecated `org.apache.commons.beanutils.PropertyUtils` imports
3. Works with any Maven project affected by the same breaking changes
4. Is parameterizable for reuse across projects

## Implementation Details

### Main.java
- Entry point for the transformation
- Sets up Spoon launcher with source directory and output directory
- Configures the ValidationFixProcessor

### ValidationFixProcessor.java
- Processes all elements in the codebase
- Identifies imports that match the deprecated patterns
- Removes matching imports
- Reports actions taken during processing

## Usage

```bash
# Compile the project
mvn compile

# Run the transformation
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main <source-directory>
```

## Files Structure

- `src/main/java/github/chains/Main.java` - Main transformation entry point
- `src/main/java/github/chains/ValidationFixProcessor.java` - The core transformation logic

## Notes

This implementation focuses on removing problematic imports. For a complete solution that also handles replacement of usage patterns, additional processing would be needed.