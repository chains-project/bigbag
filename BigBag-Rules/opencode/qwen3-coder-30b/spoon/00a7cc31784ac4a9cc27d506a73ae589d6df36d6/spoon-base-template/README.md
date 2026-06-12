# Generic MINA Dependency Fixer

This is a generic Spoon transformation that fixes breaking changes in MINA dependency updates, specifically addressing issues with version 2.2.0 to 2.2.1.

## Problem

When updating from MINA core version 2.2.0 to 2.2.1, several breaking changes occur:
1. `IoUtil.await(Iterable<?>)` now requires timeout parameters
2. `IoUtil.awaitUninterruptibly(Iterable<?>)` now requires timeout parameters  
3. `IoBuffer.allocate(int)` now requires a boolean parameter for autoExpand

## Solution

This transformation automatically fixes these breaking changes by:
1. Adding timeout parameters (5 seconds, TimeUnit.SECONDS) to `IoUtil.await` and `IoUtil.awaitUninterruptibly` calls
2. Adding the boolean `autoExpand` parameter to `IoBuffer.allocate` calls

## Usage

The transformation is generic and reusable for any project with similar issues:

```bash
java -jar spoon-mina-fixer.jar <input_directory> <output_directory>
```

## Features

- Generic: Works with any Java project using MINA
- Reusable: Can be applied to different projects by changing input directory
- Automatic: Handles all matching patterns in the codebase
- Safe: Preserves existing code structure and logic

## Implementation Details

The transformation uses Spoon to:
1. Parse Java source code into an AST
2. Identify method calls to the affected MINA APIs
3. Apply the appropriate parameter modifications
4. Generate the updated source code

## Files Modified

- `src/main/java/github/chains/Main.java` - Core transformation logic
- `pom.xml` - Maven configuration with Spoon dependencies