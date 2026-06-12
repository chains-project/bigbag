# Generic JavaParser Transformation Rule for Commons-IO Breaking Changes

This document describes a generic transformation rule to fix Maven projects affected by breaking changes in commons-io dependency updates.

## Problem Summary

The commons-io library has removed several classes and methods:
1. `BoundedInputStream` - removed from `org.apache.commons.io.input`
2. `ClosedInputStream` - removed from `org.apache.commons.io.input` 
3. `ThresholdingOutputStream` - removed from `org.apache.commons.io.output`
4. `NullPrintStream` - removed from `org.apache.commons.io.output`
5. `getByteCount()` method - removed from `CountingInputStream`

## Solution Approach

The transformation replaces these broken dependencies with alternatives:

1. **BoundedInputStream** → **CountingInputStream** (with proper parameter handling)
2. **ClosedInputStream** → **CountingInputStream** (no parameters)
3. **ThresholdingOutputStream** → **CountingOutputStream** (simplified implementation)
4. **NullPrintStream** → **java.io.PrintStream.nullOutputStream()**
5. **getByteCount()** → **getCount()**

## How to Use

1. Compile this transformation: `mvn compile`
2. Run it on any Maven project: `java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project/src/main/java`

## Key Concepts

This transformation is designed to be:
- **Reusable**: Works for any Maven project with these dependency issues
- **Generic**: Doesn't hardcode project-specific identifiers
- **Safe**: Only changes the specific problematic imports and method calls

## Implementation Details

The transformation handles:
- Import declarations removal
- Constructor parameter adjustments
- Method name replacements
- Static method calls replacement
- Complex class inheritance changes

This approach ensures that any project affected by the same breaking changes will be fixed consistently.