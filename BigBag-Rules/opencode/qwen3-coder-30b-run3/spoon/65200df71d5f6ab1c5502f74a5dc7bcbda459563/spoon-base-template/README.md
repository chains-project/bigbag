# Generic Spoon Transformation for SonarLint Core API Migration

This transformation addresses the breaking API change in sonarlint-core 8.19.0.72745 where the method `AnalysisEngineConfiguration.Builder.addEnabledLanguages()` was removed.

## Problem
In sonarlint-core version 8.19.0.72745, the `addEnabledLanguages` method was removed from `AnalysisEngineConfiguration.Builder` class, causing compilation errors in projects that depend on this API.

## Solution
This Spoon transformation identifies and removes all occurrences of the broken API call pattern:
```java
AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
```

The transformation works by:
1. Finding all invocations of `addEnabledLanguages` method
2. Removing these method calls from the codebase
3. Allowing developers to manually implement the appropriate replacement based on the new API

## Usage
```bash
java -cp spoon-transformation.jar github.chains.Main /path/to/source/code
```

## Generalization
This transformation can be reused for similar API migration issues by:
1. Modifying the method name pattern in `isToBeProcessed()`
2. Adjusting the processing logic in `process()`
3. Changing the source directory parameter

## Note
This is a conservative approach that removes the broken API call rather than attempting to automatically replace it with the new API, as the specific replacement mechanism depends on the target API version.