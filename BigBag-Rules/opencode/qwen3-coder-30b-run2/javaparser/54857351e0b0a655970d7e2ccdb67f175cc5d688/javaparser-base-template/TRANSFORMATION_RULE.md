# Generic Transformation Rule for zip4j Breaking Change

## Problem Analysis

In zip4j version 2.10.0, the package structure changed:
- OLD: `import net.lingala.zip4j.core.ZipFile;`
- NEW: `import net.lingala.zip4j.ZipFile;`

This breaks any Maven project that imports `net.lingala.zip4j.core.ZipFile` and tries to use the `ZipFile` class.

## Solution

Replace all occurrences of:
```
import net.lingala.zip4j.core.ZipFile;
```

With:
```
import net.lingala.zip4j.ZipFile;
```

## Implementation Details

### Pattern Matching
- **Old Pattern**: `import net.lingala.zip4j.core.ZipFile;`
- **New Pattern**: `import net.lingala.zip4j.ZipFile;`

### Structural Transformation
The transformation is straightforward - it's a simple string replacement at the import level, not requiring complex AST traversal.

### Usage
This transformation should be applied to any Maven project that imports the old zip4j API.

## Files Modified
- `src/main/java/io/qameta/allure/maven/AllureCommandline.java` (verified fixed)

## Validation
After applying this transformation, the project should compile without the error:
```
package net.lingala.zip4j.core does not exist
```

This is a generic solution that can be applied to any project that uses the old zip4j API.