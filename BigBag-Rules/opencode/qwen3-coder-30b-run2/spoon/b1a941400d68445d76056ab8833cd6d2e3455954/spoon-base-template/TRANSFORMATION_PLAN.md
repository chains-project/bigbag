# SnakeYAML 2.0 Compatibility Transformation

## Overview

This document describes a Spoon transformation to resolve SnakeYAML 2.0 breaking changes affecting the fluxtion project.

## Problem

SnakeYAML 2.0 introduced breaking changes to Constructor class constructors that now require a LoaderOptions parameter. Specifically:
- Old: `new Constructor()` or `new Constructor(Class<?>)`
- New: `new Constructor()` or `new Constructor(Class<?>, LoaderOptions)`

This causes compilation errors in projects that use older SnakeYAML constructor signatures.

## Solution

A Spoon transformation that automatically identifies and fixes SnakeYAML Constructor calls by adding the missing LoaderOptions parameter.

## Implementation Plan

The transformation will be implemented as a Spoon processor with these key features:

1. **Pattern Matching**: Detect all constructor calls to `org.yaml.snakeyaml.constructor.Constructor`
2. **Parameter Detection**: Check if LoaderOptions is already present as the last parameter
3. **Transformation Logic**: Add `new LoaderOptions()` as the final parameter when missing
4. **Generic Application**: Work across any project using SnakeYAML, not just fluxtion

## Key Requirements

- **Generic**: Works with any project using SnakeYAML
- **Safe**: Only modifies SnakeYAML Constructor calls, preserves other code
- **Robust**: Gracefully handles edge cases and malformed code
- **Production Ready**: Follows Spoon best practices for transformations

## Usage Pattern

```java
// Before transformation (SnakeYAML 1.x):
Constructor c1 = new Constructor();
Constructor c2 = new Constructor(SomeClass.class);

// After transformation (compatible with SnakeYAML 2.0):
Constructor c1 = new Constructor(new LoaderOptions());
Constructor c2 = new Constructor(SomeClass.class, new LoaderOptions());
```

## Processor Implementation Details

The processor will:
1. Extend `AbstractProcessor<CtConstructorCall>`
2. Override `isToBeProcessed()` to filter SnakeYAML Constructor calls
3. Override `process()` to add LoaderOptions parameter when needed
4. Use Spoon's factory methods to construct the new constructor calls
5. Replace the original constructor calls with corrected versions

This approach ensures that projects can upgrade to SnakeYAML 2.0 without manual code modifications to every Constructor call.