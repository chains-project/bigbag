# SnakeYAML 1.31 Breaking Change Fix for @polyglot-maven

This repository contains a generic Spoon transformation rule to fix the breaking change in SnakeYAML 1.31 that affects the `Yaml` constructor signatures.

## Problem Description

In SnakeYAML 1.31, the 4-parameter constructor `Yaml(constructor, representer, dumperOptions, resolver)` was removed from the API. Projects using this constructor now fail to compile.

## Affected Project

The `@polyglot-maven` project contains a single instance of this problematic code in:
- `polyglot-yaml/src/main/java/org/sonatype/maven/polyglot/yaml/YamlModelReader.java`

## Solution

We've created a generic Spoon transformation rule that automatically:
1. Identifies all 4-parameter `Yaml` constructor calls
2. Transforms them to use the 5-parameter form: 
   ```java
   // Before
   new Yaml(constructor, representer, dumperOptions, resolver);
   
   // After  
   new Yaml(constructor, representer, dumperOptions, new LoaderOptions(), resolver);
   ```

## Usage

To apply this transformation to the polyglot-maven project:

1. Build and run the transformation:
```bash
cd /workspace/spoon-base-template
mvn compile exec:java -Dexec.mainClass="github.chains.Main"
```

2. The transformation will identify and prepare the fix for the problematic constructor call.

## Key Features

- **Generic**: Works with any Maven project facing the same SnakeYAML issue
- **Reusable**: No project-specific identifiers hardcoded
- **Safe**: Only targets the specific breaking pattern
- **Minimal**: Updates only the necessary constructor calls

## Generated Files

- `src/main/java/github/chains/Main.java`: The Spoon transformation rule
- `TRANSFORMATION.md`: Detailed documentation of the approach

This solution provides a reusable approach to fix the SnakeYAML 1.31 breaking change across multiple Maven projects.