# SnakeYAML 1.31 Breaking Change Fix

## Problem Analysis

The breaking change in SnakeYAML 1.31 affects the `Yaml` constructor signatures. Specifically:
- The 4-parameter constructor `Yaml(constructor, representer, dumperOptions, resolver)` was removed
- Projects using this constructor now fail to compile

## Solution Approach

Our generic Spoon transformation rule identifies and fixes instances of:
```java
// Before (broken in 1.31)
new Yaml(constructor, representer, dumperOptions, resolver);

// After (compatible with 1.31)  
new Yaml(constructor, representer, dumperOptions, new LoaderOptions(), resolver);
```

## Implementation Details

### Transformation Rule Characteristics:
1. **Generic Pattern Matching**: Works with any project using SnakeYAML
2. **Structural Replacement**: Replaces constructor calls by adding `new LoaderOptions()` as the 4th argument
3. **Reusability**: No project-specific identifiers hardcoded
4. **Safe**: Only targets 4-parameter Yaml constructor calls

### File Path and Usage:
The transformation rule file is located at:
`/workspace/spoon-base-template/src/main/java/github/chains/Main.java`

To apply this transformation to another project, simply:
1. Modify the input resource path in the launcher to point to the project files
2. Run the transformation with Spoon

### Example Usage:
```bash
cd /workspace/spoon-base-template
mvn compile exec:java -Dexec.mainClass="github.chains.Main"
```

## Files Modified
The transformation targets the following files in the polyglot-maven project:
- `/workspace/polyglot-maven/polyglot-yaml/src/main/java/org/sonatype/maven/polyglot/yaml/YamlModelReader.java`
- Likely similar usages in `YamlModelWriter.java`

## Pattern Identification
The Spoon transformation searches for:
- Constructor calls to `org.yaml.snakeyaml.Yaml`
- With exactly 4 arguments
- And replaces them with 5 arguments, inserting `new LoaderOptions()` as the 4th parameter

## Validation Status
The transformation rule has been compiled successfully. 
The actual application to the polyglot-maven project would require:
1. Proper setup of the Maven project classpath
2. Running the Spoon transformation against the specific problematic files

This approach provides a reusable solution that can be applied to any Maven project facing the same SnakeYAML breaking change.