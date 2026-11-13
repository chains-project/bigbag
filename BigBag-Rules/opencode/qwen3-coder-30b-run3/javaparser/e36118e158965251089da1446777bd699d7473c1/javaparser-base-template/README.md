# Generic HTTP API Transformation Tool

This repository contains a generic JavaParser-based transformation tool designed to fix breaking API changes in HTTP libraries, particularly when upgrading from version v1.0.0 to v1.0.1 of `com.artipie:http`.

## Problem

When upgrading the `com.artipie:http` dependency from v1.0.0 to v1.0.1, developers encounter breaking API changes that prevent compilation. Common breaking changes include:
- Changes in `ArtipieHttpException` constructor signatures
- Changes in method signatures for HTTP response objects
- Changes in how exceptions are instantiated

## Solution Approach

This tool provides a generic framework that can be customized to fix any specific breaking changes by scanning Java source code and applying targeted transformations.

## Transformation Logic

The transformation specifically targets breaking changes in `ArtipieHttpException` constructor signatures:
- Old: `new ArtipieHttpException(status, message, throwable)` 
- New: `new ArtipieHttpException(status, message)`

This removes the third parameter (typically a Throwable) that was valid in older versions but is no longer accepted in v1.0.1.

## Usage

### Running the Transformation

```bash
# Compile the transformation tool
mvn compile

# Apply to a source directory
java -cp target/classes:<classpath> github.chains.Main /path/to/source
```

## How to Customize for Specific Breaking Changes

1. Modify the `HttpApiChangeVisitor` class in `Main.java`
2. Add specific detection logic for the breaking changes you encounter
3. Implement targeted transformation rules for that specific API change

## Important Notes

- This implementation provides a framework for generic transformations
- For production use, you should customize the transformation rules to match your exact breaking changes
- The transformation preserves all existing functionality except for the specific API fixes
- Testing is required after applying transformations to ensure correctness