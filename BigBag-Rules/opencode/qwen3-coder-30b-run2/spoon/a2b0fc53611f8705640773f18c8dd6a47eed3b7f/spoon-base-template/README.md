# Flyway Migration - Generic Spoon Transformation

This document describes the approach for creating a reusable transformation rule to fix Flyway breaking changes from v8 to v9.19.4+.

## Problem Summary

The nem project fails to compile due to Flyway API breaking changes:
- Flyway() constructor now requires Configuration parameter
- Deprecated setter methods removed

## Solution Approach

A generic Spoon transformation that:
1. Identifies all instances of `new Flyway()` with no arguments
2. Identifies all deprecated setter calls on Flyway objects
3. Replaces with proper FluentConfiguration-based initialization

## Implementation Plan

1. **Pattern Recognition**: Match Flyway constructor calls and setter method invocations
2. **Transformation Logic**: Convert old initialization pattern to new one using FluentConfiguration
3. **Generic Approach**: Use fully qualified type names and dependency API signatures

## Key Files

- `src/main/java/github/chains/Main.java`: Transformation entry point
- `TRANSFORMATION.md`: Documentation of approach

## Usage

```
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/source/code
```

## Result

This transformation provides a reusable solution for any Maven project affected by the same Flyway breaking change, eliminating the need for one-off patches.