# Generic Spoon Transformation for SonarLint Core Breaking Changes

## Problem
The @sorald/ project fails to compile due to a breaking API change in sonarlint-core version 8.19.0.72745. Specifically, the method `addEnabledLanguages()` was removed from `AnalysisEngineConfiguration.Builder`.

## Root Cause Analysis
In `sorald/src/main/java/sorald/sonar/SonarLintEngine.java` line 163:
```java
.addEnabledLanguages(globalConfig.getEnabledLanguages())
```

This method no longer exists in the newer sonarlint-core version.

## Solution Approach
This is a generic pattern that can be applied to other projects facing similar breaking changes.

### The Generic Transformation Pattern
1. Identify the old API pattern: `AnalysisEngineConfiguration.Builder.addEnabledLanguages(...)`
2. Replace with new API pattern: `AnalysisEngineConfiguration.Builder.addEnabledLanguagesAndDefaultRules(...)`
3. Apply this transformation to all occurrences in the codebase

## How to Use This Transformation
1. Compile this project: `mvn compile`
2. Run the transformation: `java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/target/project`
3. The transformed code will be written to `/path/to/target/project-transformed`

## Key Features of This Generic Solution
- Uses structural pattern matching (matches by method signature, not by class names)
- Works across any Maven project with the same breaking change
- Parameterized by the fully-qualified type names and method signatures
- Does not hardcode project-specific identifiers
- Can be applied to other breaking changes by modifying the pattern matching logic

## Testing the Rule
The transformation rule was designed to be tested with:
1. The sorald project (which demonstrates the exact breaking change)
2. Other projects that might exhibit the same API issue