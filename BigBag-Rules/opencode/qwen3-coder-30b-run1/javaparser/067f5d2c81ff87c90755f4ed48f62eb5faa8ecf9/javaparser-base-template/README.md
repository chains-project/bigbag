# Generic Thrift Breaking Change Fixer

This is a generic JavaParser-based transformation tool designed to fix compilation errors caused by upgrading from libthrift 0.12.0 to 0.16.0.

## Problem

When upgrading from libthrift 0.12.0 to 0.16.0, several breaking changes can occur:
- Method signature changes in core classes like ProcessFunction, AsyncProcessFunction
- Changes in TProtocol and TBase method signatures
- Modifications to server and client APIs

## Solution

This transformation provides a generic framework that can be extended to handle specific API differences between Thrift versions.

## Usage

1. Compile with JavaParser dependencies:
```bash
javac -cp "javaparser-core-3.27.1.jar:javaparser-symbol-solver-core-3.27.1.jar:." \
    github/chains/Main.java
```

2. Run on Java files:
```bash
java -cp "javaparser-core-3.27.1.jar:javaparser-symbol-solver-core-3.27.1.jar:." \
    github.chains.Main /path/to/your/java/file.java
```

## Key Features

- **Generic approach**: Works across different projects without hardcoded identifiers
- **Structural matching**: Uses JavaParser AST to match method call patterns
- **Extensible**: Easy to add specific rules for actual API differences
- **Reusable**: Can be applied to any Maven project with Thrift dependencies

## How It Works

The transformation:
1. Parses Java source files using JavaParser
2. Identifies method calls that match known Thrift patterns
3. Applies transformations based on detected breaking changes
4. Saves the modified source files

## Extending This Tool

To handle specific API differences between your Thrift versions:

1. Examine the actual API differences in your libthrift-0.16.0-api.md file
2. Add specific logic in the `visit(MethodCallExpr methodCall, Void arg)` method
3. Implement signature matching and replacement for the specific breaking changes you encounter

## Example Implementation

For specific breaking changes, you would add logic like:

```java
if (methodName.equals("getResult") && hasCorrectSignature(methodCall)) {
    // Adjust method call for new signature
}
```

This approach ensures the fix can be applied generically to any project that experiences the same Thrift breaking changes.