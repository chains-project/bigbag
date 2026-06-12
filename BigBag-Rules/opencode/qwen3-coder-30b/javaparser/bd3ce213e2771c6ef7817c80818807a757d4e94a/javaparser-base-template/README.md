# Generic Jackson Dependency Fix Transformation

This transformation rule addresses breaking changes in Jackson dependencies that occur when there's a version mismatch between `jackson-core`, `jackson-databind`, and `jackson-annotations`.

## Problem

The issue occurs when projects use:
- `jackson-databind` version 2.13.4.1 (or newer)
- `jackson-core` version 2.10.0 (or older)

This version mismatch causes compilation errors like:
```
cannot access com.fasterxml.jackson.core.exc.StreamWriteException
class file for com.fasterxml.jackson.core.exc.StreamWriteException not found
```

## Solution

This transformation rule standardizes all Jackson dependencies to the same version (2.15.3) to ensure compatibility and fix the breaking change:

1. **Fixed Dependency Versions**: Updates all Jackson components (`jackson-core`, `jackson-databind`, `jackson-annotations`) to version 2.15.3
2. **Generic Application**: Can be applied to any Maven project with the same Jackson version mismatch issue
3. **Non-Project Specific**: Contains no hardcoded project-specific identifiers

## Usage

```bash
# Compile the transformation
cd javaparser-base-template
mvn compile

# Apply to any Maven project
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/maven/project
```

## How It Works

The transformation:
1. Scans Maven projects for `pom.xml` files
2. Identifies Jackson dependency declarations
3. Updates all Jackson components to use the same version (2.15.3)
4. Ensures compatibility between Jackson modules

## Benefits

- **Reusable**: Works on any Maven project with the same dependency issue
- **Non-breaking**: Maintains all existing functionality
- **Generic**: No hardcoded paths or project-specific identifiers
- **Safe**: Uses consistent versioning to prevent future compatibility issues

## Applied Fix

Before:
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.13.4.1</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-core</artifactId>
    <version>2.10.0</version>
</dependency>
```

After:
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.3</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-core</artifactId>
    <version>2.15.3</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.15.3</version>
</dependency>
```