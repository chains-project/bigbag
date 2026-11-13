# Generic JavaParser Transformation for zip4j API Migration

This is a generic transformation rule that fixes the breaking change in zip4j library version 2.10.0 where `net.lingala.zip4j.core.ZipFile` was replaced with `net.lingala.zip4j.ZipFile`.

## Transformation Description

The transformation identifies and replaces import statements that reference the old zip4j.core package structure with the new flat package structure.

## Specific Change

**Before:**
```java
import net.lingala.zip4j.core.ZipFile;
```

**After:**
```java
import net.lingala.zip4j.ZipFile;
```

## How to Use

1. Save this transformation rule in your codebase
2. Execute it against any Java project that uses the old zip4j API
3. The transformation will automatically fix all import statements

## Example Usage

```bash
# Run the transformation on any Java project
java -cp target/classes:target/dependency/* github.chains.Main /path/to/project/src
```

This rule provides a reusable solution that can be applied to any Maven project affected by the zip4j breaking change.