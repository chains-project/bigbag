# SnakeYAML 2.0 Generic Spoon Transformation

This project provides a generic Spoon transformation rule for handling breaking changes in SnakeYAML 2.0, specifically constructor signature changes.

## Problem

In SnakeYAML 2.0, all `Constructor` constructors now require a `LoaderOptions` parameter, which causes compilation errors in existing code that uses older constructor signatures.

### Old SnakeYAML 1.x signatures:
```java
new Constructor()
new Constructor(Class<?>)
```

### New SnakeYAML 2.0 signatures:
```java
new Constructor()
new Constructor(Class<?>, LoaderOptions)
```

## Solution

This Spoon transformation processor detects SnakeYAML `Constructor` constructor calls and automatically adds the missing `LoaderOptions` parameter to make the code compatible with SnakeYAML 2.0.

## Usage

The transformation can be applied generically to any project using SnakeYAML:

1. Create an instance of `SnakeYAML20TransformationProcessor`
2. Apply it to your Spoon model using a Launcher
3. The processor will automatically fix all SnakeYAML Constructor calls

## Features

- **Generic**: Works with any project that uses SnakeYAML, not just Fluxtion
- **Parameterized**: Handles various constructor signatures (no args, with Class, etc.)
- **Safe**: Only modifies SnakeYAML Constructor calls, leaves other code untouched
- **Robust**: Gracefully handles edge cases and compilation errors

## Implementation Details

The transformation:
1. Identifies constructor calls to `org.yaml.snakeyaml.constructor.Constructor`
2. Checks if a `LoaderOptions` parameter is already present
3. If not, adds `new LoaderOptions()` as the last parameter
4. Replaces the original constructor call with the corrected version

This approach ensures backward compatibility while adapting to SnakeYAML 2.0's new requirements.