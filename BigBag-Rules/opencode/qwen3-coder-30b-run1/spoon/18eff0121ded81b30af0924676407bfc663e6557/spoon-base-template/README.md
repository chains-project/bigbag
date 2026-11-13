# Spoon Transformation for ScriptResult Fix

This project provides a Spoon transformation to fix the ScriptResult issue in ChartUtil.java where `com.gargoylesoftware.htmlunit.ScriptResult` is no longer available in newer acceptance-test-harness dependencies.

## Problem

In older versions of acceptance-test-harness, the `ScriptResult` class was available in `com.gargoylesoftware.htmlunit`. In newer versions, this class was removed, causing compilation errors.

## Solution

This transformation:

1. Removes the import statement for `com.gargoylesoftware.htmlunit.ScriptResult`
2. Replaces usage of `ScriptResult` with direct access to the result object

## Usage

```bash
# Compile the project
mvn compile

# Run the transformation
java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/source/directory
```

## What it fixes

**Before:**
```java
import com.gargoylesoftware.htmlunit.ScriptResult;

// ...

Object result = pageObject.executeScript(...);
ScriptResult scriptResult = new ScriptResult(result);
return scriptResult.getJavaScriptResult().toString();
```

**After:**
```java
// Import removed

// ...

Object result = pageObject.executeScript(...);
return result.toString();
```

## Implementation Details

The transformation works by:
1. Identifying classes that import ScriptResult
2. Removing the import statement
3. Finding and replacing patterns where ScriptResult is used
4. Replacing `scriptResult.getJavaScriptResult().toString()` with `result.toString()`