# jcabi-aspects 0.25.1 Breaking Change Fix

This project provides a generic Spoon transformation to fix compilation errors in Maven projects caused by breaking changes in jcabi-aspects 0.25.1.

## Problem

The @jcabi-simpledb/ project does not compile due to a breaking dependency update in jcabi-aspects 0.25.1. The most likely breaking change is in the @Loggable annotation, which changed from integer constants (0-4) to enum constants.

## Solution

This repository contains a generic transformation rule that:

1. Identifies @Loggable annotations with integer literal values (0-4)
2. Replaces them with corresponding enum constant references
3. Is reusable across any Maven project with the same breaking change

## Transformation Details

**Before (broken):**
```java
@Loggable(0) // DEBUG
@Loggable(1) // INFO
@Loggable(2) // WARN
@Loggable(3) // ERROR
@Loggable(4) // TRACE
```

**After (fixed):**
```java
@Loggable(Loggable.DEBUG)
@Loggable(Loggable.INFO)
@Loggable(Loggable.WARN)
@Loggable(Loggable.ERROR)
@Loggable(Loggable.TRACE)
```

## Usage

1. Compile the transformation:
   ```bash
   mvn compile
   ```

2. Run the transformation on a project:
   ```bash
   java -cp "spoon-core-10.0.0.jar:target/classes:." github.chains.Main /path/to/affected/project/src
   ```

3. Verify by compiling the project:
   ```bash
   mvn compile
   ```

## Generalizability

The transformation rule contains no hardcoded project-specific identifiers:
- Uses fully-qualified type names from the dependency
- Applies to any project with the same breaking change
- Only requires changing the input source directory path to apply to other projects