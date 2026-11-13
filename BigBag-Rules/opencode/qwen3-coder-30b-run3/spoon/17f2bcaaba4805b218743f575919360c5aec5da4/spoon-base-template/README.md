# Generic Spoon Transformation for tinspin-indexes API Breaking Changes

This project demonstrates a generic Spoon transformation that can fix compilation errors caused by API breaking changes in the tinspin-indexes dependency version 1.8.0. Specifically, it addresses issues with `query1NN()` method calls that changed in the API.

## Problem

In tinspin-indexes 1.8.0, the `query1NN()` method's return type changed from direct values to `PointEntryDist<T>`. This causes compilation errors when code tries to call `.dist()` or `.value()` directly on the result.

## Solution

This transformation identifies patterns where `query1NN()` is followed by `.dist()` or `.value()` calls and transforms them to use the new API properly.

## Files

- `src/main/java/github/chains/Main.java`: The main transformation logic
- `pom.xml`: Maven dependencies for Spoon and Java 17

## Usage

1. Place this project in the same directory as the project with compilation errors
2. Modify the input path in `Main.java` to point to the correct source directory
3. Run with: `mvn compile exec:java -Dexec.mainClass="github.chains.Main"`

## How it Works

The transformation:
1. Scans source code for `query1NN()` method calls
2. Identifies patterns where `.dist()` or `.value()` are called on the result
3. Replaces these with appropriate calls to the new API (typically `.getDist()` and `.getValue()`)

Note: This is a generic implementation that may need customization based on the exact specific patterns in your codebase.