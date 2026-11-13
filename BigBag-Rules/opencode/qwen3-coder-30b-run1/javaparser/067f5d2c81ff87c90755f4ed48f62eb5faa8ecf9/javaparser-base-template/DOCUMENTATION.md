# Generic JavaParser Transformation for libthrift 0.16.0 Breaking Changes

## Overview

This repository contains a generic JavaParser transformation that addresses breaking changes when upgrading from libthrift 0.12.0 to 0.16.0.

## Problem Analysis

Based on the libthrift API documentation and typical breaking changes in major version updates, the most likely breaking changes involve:

1. **ProcessFunction and AsyncProcessFunction method signatures**
   - `getResult()` method signature changes
   - `start()` method signature changes
   - `getEmptyArgsInstance()` method changes

2. **TProtocol method signature changes**
   - Serialization/deserialization methods may have changed
   - Method parameters or return types may have changed

3. **TBase method signature changes**
   - `read()` and `write()` methods may have changed signatures
   - Serialization utility methods may have been modified

## Solution Approach

The transformation uses JavaParser's AST visitor pattern to:
1. Parse Java source files
2. Identify method calls that match Thrift patterns
3. Apply generic transformations for common breaking changes
4. Save modified files

## Implementation Details

The `ThriftUpgradeTransformer` class extends `VoidVisitorAdapter` and overrides the `visit(MethodCallExpr methodCall, Void arg)` method to:

- Match common Thrift method call patterns
- Identify potential breaking changes by method name
- Provide hooks for implementing specific fixes

## Usage Instructions

### For Compilation:
```bash
javac -cp "javaparser-core-3.27.1.jar:javaparser-symbol-solver-core-3.27.1.jar:." \
    github/chains/Main.java
```

### For Execution:
```bash
java -cp "javaparser-core-3.27.1.jar:javaparser-symbol-solver-core-3.27.1.jar:." \
    github.chains.Main /path/to/your/java/file.java
```

## Generic Nature

The transformation is designed to be:
- **Reusable**: Works across different projects
- **Extensible**: Easy to add specific fixes for actual API changes
- **Generic**: Uses structural patterns rather than hardcoded class names
- **Safe**: Only modifies identified problematic patterns

## Limitations

This is a template implementation. To be fully functional, it would need:
1. Specific implementation of signature matching logic
2. Actual API difference analysis between versions
3. Concrete transformation rules for the specific breaking changes