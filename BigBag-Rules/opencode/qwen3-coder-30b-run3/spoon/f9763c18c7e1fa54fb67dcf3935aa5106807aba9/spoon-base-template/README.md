# Generic Spoon Transformation for ScriptResult Removal

This is a generic Spoon transformation that fixes compilation errors caused by the removal of `com.gargoylesoftware.htmlunit.ScriptResult` class in dependency updates.

## Problem
The `com.gargoylesoftware.htmlunit.ScriptResult` class was removed from newer versions of HTMLUnit/Selenium, causing compilation errors in projects that depended on it.

## Solution
This transformation scans Java source files and:
1. Identifies `new ScriptResult(...)` constructor calls
2. Replaces them with the direct result from script execution
3. Removes the import statement for ScriptResult if no longer needed

## Usage

### For the code-coverage-api-plugin specifically:
1. Navigate to the spoon-base-template directory
2. Run: `mvn compile exec:java -Dexec.mainClass="github.chains.Main"`

This will transform all Java files in the resources directory.

### For other projects:
1. Place your Java source files in the source directory
2. Modify the `Main.java` to point to your source path
3. Run the transformation

## Example Transformation

**Before:**
```java
import com.gargoylesoftware.htmlunit.ScriptResult;

Object result = executeScript("some script");
ScriptResult scriptResult = new ScriptResult(result);
String data = scriptResult.getJavaScriptResult().toString();
```

**After:**
```java
Object result = executeScript("some script");
String data = result.toString();
```

## Key Features

- **Generic**: Works with any Java project that has ScriptResult usage patterns
- **Safe**: Only transforms `new ScriptResult(...)` constructor calls
- **Minimal**: Doesn't affect unrelated code or imports
- **Reusable**: Can be used across multiple projects