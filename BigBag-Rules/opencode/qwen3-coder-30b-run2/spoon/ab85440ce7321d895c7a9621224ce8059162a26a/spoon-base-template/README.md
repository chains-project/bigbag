# Generic Spoon Transformation for Breaking Dependency Updates

This is a generic Spoon transformation rule designed to fix breaking changes in Maven projects affected by dependency updates, specifically targeting asto-core.

## Purpose

When a dependency like asto-core undergoes a breaking API change, this transformation can be applied to automatically fix common patterns in client code.

## Usage

```bash
mvn compile exec:java -Dexec.mainClass="github.chains.Main" -Dexec.args="/path/to/project/src"
```

Or compile and run with:

```bash
mvn compile
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project/src
```

## Features

- Identifies common breaking changes in asto-core API
- Handles method signature changes in Storage, Content, and Key interfaces
- Provides detailed logging of transformations applied
- Generic design that can be adapted to other breaking changes

## Supported Patterns

The transformation currently handles:
- Storage method calls (value, save, delete, exists, list, move, size, metadata, deleteAll, exclusively)
- Content method calls (single, size, subscribe, primitiveBytes, boxedBytes)
- Key method calls (string, parts, parent)

## Customization

To adapt this transformation for specific breaking changes:

1. Modify the `isStorageMethod`, `isContentMethod`, and `isKeyMethod` functions to match the actual breaking changes
2. Add specific transformation logic in the `handleMethodSignatureChanges` method
3. Extend pattern matching for other API elements that changed