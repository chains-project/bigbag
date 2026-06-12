# ScriptResult Fix Transformation

This project provides a generic transformation rule to fix breaking changes in Maven projects that depend on `com.gargoylesoftware.htmlunit.ScriptResult` which was removed in newer versions of the dependency.

## Problem

The `com.gargoylesoftware.htmlunit.ScriptResult` class was removed from the dependency, causing compilation errors in projects that used:
- `new ScriptResult(result)` constructor calls
- `scriptResult.getJavaScriptResult()` method calls

## Solution

This transformation replaces the old patterns with direct usage of the result objects:

**Before:**
```java
import com.gargoylesoftware.htmlunit.ScriptResult;

ScriptResult scriptResult = new ScriptResult(result);
return scriptResult.getJavaScriptResult().toString();
```

**After:**
```java
return result.toString();
```

## How to Use

1. Apply the transformation to any Java source files that use `ScriptResult`
2. The transformation will:
   - Remove imports of `com.gargoylesoftware.htmlunit.ScriptResult`
   - Replace `new ScriptResult(result)` with just `result`
   - Replace `scriptResult.getJavaScriptResult()` with just `scriptResult`

## Files Modified

- `ChartUtil.java` in `code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/`

## Verification

The project now compiles successfully without the ScriptResult dependency issues.