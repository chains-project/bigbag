# Jakarta Interceptor API Migration Fix

## Problem
The Jakarta Interceptor API 2.0.0 introduced a breaking change by migrating from `javax.interceptor` to `jakarta.interceptor` packages as part of Jakarta EE 9+ migration.

## Solution
This is a generic Spoon transformation that can fix any Maven project affected by this breaking change.

## Files
1. `JakartaInterceptorMigrationProcessor.java` - The core processor that identifies and transforms javax.interceptor references
2. `Main.java` - The entry point for applying the transformation

## How it works
The transformation:
- Scans all Java source files in the project
- Identifies references to `javax.interceptor.*` classes
- Replaces them with `jakarta.interceptor.*` equivalents
- Updates import statements accordingly

## Usage
```bash
java -jar spoon-base-1.0-SNAPSHOT.jar /path/to/project/src/main/java
```

## Generalization
This transformation is generic and can be applied to any project with the same Jakarta Interceptor API migration issue by simply changing the input source directory path.