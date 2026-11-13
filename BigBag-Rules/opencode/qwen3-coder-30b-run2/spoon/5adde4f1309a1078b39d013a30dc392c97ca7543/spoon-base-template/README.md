# Spoon Transformation for SonarLint-Core API Change

This is a generic transformation rule to fix breaking changes in the sonarlint-core dependency where `addEnabledLanguages` was replaced with `setEnabledLanguages`.

## Problem
In sonarlint-core version 9.1.0.74321, the method `addEnabledLanguages` was removed from `AnalysisEngineConfiguration.Builder`, causing compilation errors in projects that use this API.

## Solution
This transformation replaces all calls to `addEnabledLanguages(...)` with `setEnabledLanguages(...)`.

## Usage

### Compile
```bash
mvn compile
```

### Run
```bash
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main <source-directory>
```

Where `<source-directory>` is the root directory of your Java project to transform.

## Implementation Details

The transformation:
1. Identifies all invocations of `addEnabledLanguages`
2. Replaces them with equivalent calls to `setEnabledLanguages`
3. Preserves all arguments and call context
4. Works generically across any project using this API pattern