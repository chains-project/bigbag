# Generic Solution for ScriptResult API Breaking Change

## Problem
The code-coverage-api-plugin is experiencing compilation failures due to the removal of `com.gargoylesoftware.htmlunit.ScriptResult` in the newer acceptance-test-harness version 5523.vd392288d2b_22.

## Root Cause
In `ChartUtil.java`, the code uses:
1. `ScriptResult scriptResult = new ScriptResult(result);`
2. `scriptResult.getJavaScriptResult().toString();`

These classes and methods are no longer available in the updated dependency.

## Solution Approach
Since this is a breaking change in the acceptance-test-harness library, here's the recommended fix:

### Step 1: Manual Fix for ChartUtil.java

In `/workspace/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java`, replace the usage:

FROM:
```java
import com.gargoylesoftware.htmlunit.ScriptResult;

// ... in getChartDataById method ...
ScriptResult scriptResult = new ScriptResult(result);
return scriptResult.getJavaScriptResult().toString();

// ... in getDataOfOnlyChartOnPageWithGivenToolAttribute method ...
Object scriptResult = new ScriptResult(result).getJavaScriptResult();
```

TO:
```java
// Remove import: com.gargoylesoftware.htmlunit.ScriptResult

// ... in getChartDataById method ...
return result.toString(); // Direct return of result

// ... in getDataOfOnlyChartOnPageWithGivenToolAttribute method ...
return result.toString(); // Direct return of result
```

### Step 2: Alternative Approach Using JavaScript Execution Result

If the result needs to be processed differently, you can use:

```java
// In getChartDataById method:
return result != null ? result.toString() : null;

// In getDataOfOnlyChartOnPageWithGivenToolAttribute method:
return result != null ? result.toString() : "";
```

## Generic Fix Pattern

For any Maven project facing this same issue:

1. Search for all occurrences of `ScriptResult` usage:
   ```bash
   grep -r "ScriptResult" src/
   ```

2. Replace patterns like:
   - `new ScriptResult(...)` → `(...)`
   - `scriptResult.getJavaScriptResult()` → `scriptResult` (or just the result directly)

3. Remove unused imports:
   - `import com.gargoylesoftware.htmlunit.ScriptResult;`

## Why This Occurs
This is a common issue when upgrading testing frameworks. The acceptance-test-harness library evolved and removed certain HtmlUnit integration classes that were used for JavaScript execution results in UI tests.

This transformation is safe since ScriptResult was simply a wrapper around the actual JavaScript execution result, which can be used directly.