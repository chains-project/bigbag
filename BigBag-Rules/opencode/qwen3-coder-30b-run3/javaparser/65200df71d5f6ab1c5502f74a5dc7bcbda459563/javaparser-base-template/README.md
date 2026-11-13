# Generic JavaParser Transformation for Sonarlint-Core Breaking Changes

This is a generic transformation rule to fix breaking changes in sonarlint-core version 8.19.0.72745 where the API was modified.

## Problem

In sonarlint-core 8.19.0.72745, the method `addEnabledLanguages(Set<Language>)` was removed from `AnalysisEngineConfiguration.Builder`, and replaced with `addEnabledLanguage(Language...)`.

## Solution

This transformation automatically converts code patterns from:
```java
AnalysisEngineConfiguration.builder()
    .addEnabledLanguages(globalConfig.getEnabledLanguages())
    ...
```

To:
```java
AnalysisEngineConfiguration.builder()
    .addEnabledLanguage(globalConfig.getEnabledLanguages().toArray(new Language[0]))
    ...
```

## How to Use

1. Compile the transformation:
   ```
   mvn compile
   ```

2. Run the transformation on your project:
   ```
   java -cp target/classes github.chains.Main /path/to/your/project
   ```

## Implementation Details

This rule is designed to be reusable for similar breaking changes. It can be extended to handle other API changes by:
1. Identifying the old API pattern
2. Determining the new pattern
3. Creating a JavaParser visitor to detect and transform the pattern

The transformation specifically targets the `addEnabledLanguages` method call pattern within `AnalysisEngineConfiguration.builder()` chains.

## Limitations

This is a simplified example demonstrating the concept. A production-ready version would use:
- Proper JavaParser AST traversal
- Better pattern matching
- Error handling
- Comprehensive testing