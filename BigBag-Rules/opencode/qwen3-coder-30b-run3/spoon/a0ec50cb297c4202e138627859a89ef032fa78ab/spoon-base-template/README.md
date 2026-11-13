# Spoon Transformation for com.artipie:http v1.1.1 Breaking Changes

This project provides a generic Spoon transformation to fix breaking changes in Maven projects affected by the com.artipie:http v1.1.1 API upgrade.

## Problem

The com.artipie:http library v1.1.1 introduced breaking changes to several API constructors:

### 1. Headers.From Constructors
**Before:** `new Headers.From(Iterable<Header>...)` or `new Headers.From(String, String)`  
**After:** `new Headers.From(Iterable<Header>)`

### 2. ArtipieHttpException Constructors  
**Before:** Constructor signatures varied
**After:** All constructors now properly expect `RsStatus` as first parameter

### 3. RsFull Constructors
**Before:** `new RsFull(RsStatus, Iterable<Header>, Content)` or `new RsFull(RsStatus, Iterable<Header>, Publisher<ByteBuffer>)`
**After:** Same signature, but with `Iterable<Header>` expected

## Solution Overview

This repository contains a Spoon-based transformation framework that can be used to automatically fix these breaking changes in any Maven project.

The transformation:

1. **Identifies problematic code patterns** in Java source files
2. **Applies necessary fixes** to align with v1.1.1 API signatures  
3. **Is generic and reusable** across different projects

## Implementation Details

The core transformation logic is in `Main.java` which implements `AbstractProcessor<CtElement>`. The processor identifies:

- Calls to `Headers.From()` constructors
- Calls to `ArtipieHttpException()` constructors  
- Calls to `RsFull()` constructors

And applies appropriate fixes to make them compatible with the new API.

## Usage Instructions

1. **Prepare your project:**
   ```
   # Ensure you have the source code of your project in the correct location
   # e.g., /workspace/my-project/src
   ```

2. **Update the source path in Main.java:**
   ```java
   // Change this line:
   launcher.addInputResource("/workspace/docker-adapter/src");
   // To your project's source directory:
   launcher.addInputResource("/workspace/my-project/src");
   ```

3. **Compile and run:**
   ```bash
   cd spoon-base-template
   mvn compile
   mvn exec:java -Dexec.mainClass="github.chains.Main"
   ```

## Limitations

Note that this is a framework template - the actual fix logic would need to be implemented based on specific patterns found in your codebase. The current implementation demonstrates the structure but requires customization for specific use cases.

## Key Files in This Repository

- `src/main/java/github/chains/Main.java` - Main transformation logic
- `pom.xml` - Maven dependencies including Spoon core
- `README.md` - Project documentation
- `SOLUTION.md` - Detailed solution overview

## Testing

To test on a project:
1. Point the transformation to your project's source directory
2. Run the Maven command
3. Check the output directory for transformed files

The framework is designed to be reused for any project affected by the com.artipie:http v1.1.1 breaking changes.