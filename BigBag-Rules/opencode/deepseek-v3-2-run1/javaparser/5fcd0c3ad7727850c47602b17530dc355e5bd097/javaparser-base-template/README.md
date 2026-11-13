# JavaParser API Migration Transformation

A generic, reusable JavaParser-based transformation for fixing breaking API changes in Java projects.

## Problem Solved

When a library makes a breaking API change like:
- Old: `CoverageDatabase.getClassInfo(Set<ClassName>)` returns `Collection<ClassInfo>`
- New: Method removed from `CoverageDatabase`, available on `CodeSource.getClassInfo(Collection<ClassName>)`

This tool automatically transforms client code to use the new API.

## Features

- **Generic**: Configurable old/new type and method names
- **Reusable**: Works on any Java project with similar breaking changes
- **Safe**: Adds explanatory comments to transformed code
- **Complete**: Processes entire directories recursively
- **Configurable**: Command-line options for different migration scenarios

## Usage

```bash
# Basic usage for PIT 1.10.0 migration
java -jar javaparser.jar <source-dir> <output-dir> --receiver codeSource

# Full options
java -jar javaparser.jar <source-dir> <output-dir> \
  --receiver codeSource \
  --old-type org.pitest.coverage.CoverageDatabase \
  --new-type org.pitest.classpath.CodeSource \
  --no-import \
  --no-comment
```

## Options

- `--receiver <name>`: Variable name for new receiver (default: codeSource)
- `--old-type <type>`: Fully qualified old type
- `--new-type <type>`: Fully qualified new type  
- `--no-import`: Don't add import for new type
- `--no-comment`: Don't add transformation comments

## Transformation Example

**Before:**
```java
coverage.getClassInfo(Collections.singleton(className));
```

**After:**
```java
codeSource.getClassInfo(Collections.singleton(className)); // API migration: org.pitest.coverage.CoverageDatabase.getClassInfo() -> org.pitest.classpath.CodeSource.getClassInfo()
```

## How It Works

1. Scans all `.java` files in source directory
2. Finds method calls matching `oldMethod`
3. Replaces receiver with `newReceiver`
4. Adds import for `newType` if needed
5. Adds explanatory comment
6. Writes transformed files to output directory

## Build

```bash
cd /workspace/javaparser-base-template
mvn clean package
```

## Limitations

- Only transforms method calls, doesn't add fields/parameters
- Doesn't handle method signature changes (parameter/return type)
- Manual dependency injection updates may be needed

## Design Principles

1. **Generic**: Not tied to specific project
2. **Configurable**: Parameters for different migrations
3. **Transparent**: Adds comments explaining changes
4. **Safe**: Outputs to separate directory, doesn't modify originals

## Extending

To handle more complex migrations, extend the `Main` class to:
- Add fields to classes
- Update constructors
- Handle method signature changes
- Update import statements