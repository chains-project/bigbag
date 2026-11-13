# SnakeYAML 2.0 Breaking Change Fixer

This is a generic Spoon transformation designed to fix breaking changes in SnakeYAML 2.0 that affect Maven projects.

## Problem

When upgrading to SnakeYAML 2.0, projects using the library may encounter compilation errors due to breaking API changes. These typically include:
- Constructor signature changes in DumperOptions and Yaml classes
- Method signature changes in DumperOptions setters/getters
- Changes in initialization patterns

## Solution

This generic transformation detects problematic API usage patterns and automatically fixes them to be compatible with SnakeYAML 2.0.

## Usage

```bash
# Compile the transformation
mvn compile

# To use the transformation on a project:
# 1. Modify the fixSnakeYAMLBreakingChanges method to point to your project path
# 2. Run with: java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main
```

## Key Features

- Generic approach that works across different Maven projects
- Detects and fixes DumperOptions constructor usage
- Handles Yaml constructor signature changes
- Updates method calls with incompatible signatures
- Maintains backward compatibility

## Applied Fixes

The transformation handles the most common breaking changes:
1. DumperOptions constructor parameter adjustments
2. Yaml constructor signature updates
3. Method call signature corrections