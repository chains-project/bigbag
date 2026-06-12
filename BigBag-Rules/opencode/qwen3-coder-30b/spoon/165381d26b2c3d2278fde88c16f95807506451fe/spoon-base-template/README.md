# JavaParser Breaking Change Fixer

This is a generic Spoon transformation template designed to fix breaking changes in JavaParser 3.24.0+ that affect Maven projects.

## Problem

When upgrading to JavaParser 3.24.0, projects using the API may encounter compilation errors due to changes in how `ParseResult` objects are handled. Specifically, the pattern `result.getResult().get()` may no longer compile because:

1. `getResult()` now returns `Optional<T>` instead of `T`
2. Direct `.get()` calls on `Optional` objects can cause runtime exceptions or compilation errors

## Solution

This transformation identifies and fixes the problematic patterns by replacing:
- `result.getResult().get()` 
- With proper Optional handling like `result.getResult().orElse(null)` or similar

## Usage

```bash
# Compile the transformation
mvn compile

# Run the transformation on a project directory
java -cp target/classes github.chains.Main /path/to/project /path/to/output
```

## How It Works

1. **Pattern Detection**: The transformation scans Java source files for patterns like `result.getResult().get()`
2. **Safe Replacement**: Replaces these patterns with proper Optional handling 
3. **Generic Approach**: Works across any Maven project with JavaParser usage
4. **Reusable**: Can be applied to multiple projects with the same issue

## Files Modified

- `src/main/java/github/chains/Main.java` - The transformation implementation

## Target API Changes

This transformation specifically addresses breaking changes in JavaParser 3.24.0 where:
- `ParseResult.getResult()` returns `Optional<T>` instead of `T`
- Direct `.get()` access is discouraged in favor of `.orElse()`, `.orElseThrow()`, or similar methods

## Implementation Notes

The transformation is designed to be generic and reusable across different projects. It:
- Uses the Spoon framework for AST-based code analysis
- Maintains code semantics while fixing compilation issues
- Is parameterized to work with any input directory
- Follows the template pattern for easy reuse