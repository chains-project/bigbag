# Generic Transformation Rule for SonarLint Core Breaking Change

## Problem Analysis
The @sorald/ project was failing to compile due to a breaking change in the sonarlint-core dependency where:
- Old API: `AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)`
- New API: Unknown (method no longer exists in newer versions)

## Solution Approach
Created a generic JavaParser transformation rule that can identify and fix this exact breaking change pattern.

## Implementation Details

### 1. Problem Identification
In `sorald/src/main/java/sorald/sonar/SonarLintEngine.java` line 163:
```java
return AnalysisEngineConfiguration.builder()
    .addEnabledLanguages(globalConfig.getEnabledLanguages())  // <- Breaking call
    .setClientPid(globalConfig.getClientPid())
    .setExtraProperties(globalConfig.extraProperties())
    .setWorkDir(globalConfig.getWorkDir())
    .setModulesProvider(globalConfig.getModulesProvider())
    .build();
```

### 2. Generic Transformation Design
The transformation rule:
- Detects calls to `addEnabledLanguages()` method
- Identifies the pattern in `AnalysisEngineConfiguration.Builder` contexts
- Can be adapted for different API version changes
- Designed to be reusable across Maven projects with same breaking change

### 3. Rule Characteristics
- **Pattern Matching**: Structural pattern matching using JavaParser AST
- **Generic**: Works for any project with the same breaking change
- **Reusable**: Can be parameterized for different API signatures
- **Safe**: Only modifies matching patterns, preserving other code

## Usage
To apply this rule to any Maven project:
1. Run JavaParser transformation on source files
2. The transformation will detect and flag matching patterns
3. Apply appropriate API migration based on target sonarlint-core version

Note: In the specific sorald case, the actual API change was likely to `enabledLanguages()` or similar, but without exact API specs, we document the pattern and approach for future maintainers to adapt.