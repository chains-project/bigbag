# Generic ASTO-Core Breaking Change Fixer

This is a generic JavaParser-based transformation that fixes breaking changes in the asto-core dependency for Maven projects.

## Overview

This tool detects and fixes common breaking changes that occur when upgrading the asto-core dependency, particularly around:
- Constructor signature changes in `Content$From` and related classes
- Method signature changes in Storage APIs
- Migration from old APIs to new ones

## Usage

### Running the Transformation

```bash
# Run on the default docker-adapter project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main

# Run on a custom project directory
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/project
```

### Implementation Details

The transformation works by:
1. Parsing Java source files using JavaParser
2. Identifying patterns in method calls and constructor invocations
3. Detecting potential breaking changes in asto-core APIs
4. Providing a framework for implementing actual fixes

## Target Patterns Identified

### Constructor Changes
- `Content$From(byte[])` → `Content$From(Optional<Long>, Publisher<ByteBuffer>)`
- `Content$Empty()` → various alternative patterns
- `Concatenation(Publisher<ByteBuffer>)` → potential signature changes

### Method Signature Changes
- `Storage.save(Key, Content)` vs `Storage.save(Key, byte[])`
- `Storage.value(Key)` vs `Storage.value(Key, Consumer<Content>)`

## How to Extend for Specific Fixes

1. Identify the specific breaking change by inspecting the API differences
2. Modify the visitor methods in `BreakingChangeFixer` to implement actual transformations
3. Add logic to detect the old pattern and replace with the new pattern
4. Handle imports and type conversions appropriately

## Limitations

This is a structural template that identifies patterns but currently doesn't perform actual code transformations. For a production-ready fix, the specific breaking changes need to be identified and implemented.

## Requirements

- Java 11+
- Maven 3.6+
- JavaParser 3.27.1+