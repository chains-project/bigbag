# Generic Transformation Rule for Breaking Dependency Update

## Problem Statement
The project `@code-coverage-api-plugin/` does not compile due to a breaking dependency update where `com.gargoylesoftware.htmlunit.ScriptResult` class was removed or replaced with an incompatible API.

## Breaking Change Analysis

### Old API Pattern
- Class: `com.gargoylesoftware.htmlunit.ScriptResult`
- Constructor: `ScriptResult(Object result)`
- Method: `Object getJavaScriptResult()`

### New API Reality
- The class `com.gargoylesoftware.htmlunit.ScriptResult` no longer exists in the dependency
- A different `ScriptResult` class exists in `org.jenkinsci.test.acceptance.plugins.scriptler` package with incompatible API

### Common Usage Patterns Found
1. `import com.gargoylesoftware.htmlunit.ScriptResult;`
2. `ScriptResult var = new ScriptResult(expr);`
3. `var.getJavaScriptResult()` 
4. `new ScriptResult(expr).getJavaScriptResult()`

## Generic Transformation Rule

### Transformation Logic
The transformation removes the unnecessary `ScriptResult` wrapper since `executeScript()` already returns the result directly:

1. **Remove imports** of `com.gargoylesoftware.htmlunit.ScriptResult`
2. **Replace constructor-method chains**: `new ScriptResult(expr).getJavaScriptResult()` → `expr`
3. **Update variable declarations**: `ScriptResult var = new ScriptResult(expr)` → `Object var = expr`
4. **Replace method calls**: `var.getJavaScriptResult()` → `var` (after type change)

### Spoon Implementation Details
The transformation uses Spoon AST patterns to:
- Match the old API pattern structurally using `CtConstructorCall` and `CtInvocation` elements
- Traverse all files in the source directory
- Parameterize by fully-qualified type name (`com.gargoylesoftware.htmlunit.ScriptResult`)
- Apply fixes wherever the old pattern appears

### Code Structure
- **Main class**: `github.chains.Main`
- **Configuration**: `REMOVED_CLASS` and `REMOVED_METHOD` constants
- **Phases**: 
  1. Import processing (handled by Spoon auto-imports)
  2. Variable declaration analysis
  3. Constructor call replacement
  4. Method call removal
  5. Variable type updates

## How to Use

### Compile the transformation:
```bash
cd /workspace/spoon-base-template
mvn compile
```

### Run on any project:
```bash
java -cp "target/classes:$(cat classpath.txt)" github.chains.Main <source-directory>
```

### Example:
```bash
java -cp "target/classes:$(cat classpath.txt)" github.chains.Main /path/to/project/src
```

## Generalizability
This transformation rule is generic and reusable because:

1. **No hardcoded project identifiers** - Uses AST patterns
2. **Parameterized by type names** - Can be adapted for other breaking changes
3. **Works on any source directory** - Input path is parameter
4. **Handles multiple patterns** - Covers common usage scenarios
5. **Uses Spoon AST** - Language-aware transformation, not regex

## Edge Cases Handled
1. Simple constructor-method chains: `new ScriptResult(expr).getJavaScriptResult()`
2. Variable declarations: `ScriptResult var = new ScriptResult(expr)`
3. Variable method calls: `var.getJavaScriptResult()`
4. Method chaining: `new ScriptResult(expr).getJavaScriptResult().toString()`

## Limitations
1. Requires Spoon dependencies at runtime
2. Source code must be mostly compilable for AST building
3. Complex expressions might need manual review

## Testing
The transformation was tested on `@code-coverage-api-plugin/` and successfully fixed:
- Removed `import com.gargoylesoftware.htmlunit.ScriptResult;`
- Replaced `new ScriptResult(result).getJavaScriptResult()` with `result`
- Updated variable types from `ScriptResult` to `Object`

After transformation, the project compiles successfully.