# Generic Spoon Transformation for Dubbo Breaking Changes

## Overview

This is a generic Spoon transformation designed to fix breaking dependency updates in Maven projects that experience API changes in libraries like Dubbo. The transformation targets common breaking changes in method signatures or API availability.

## Problem Description

When a Maven project's dependencies are updated to newer versions, breaking changes in APIs can cause compilation errors. For example, when upgrading from one version of Dubbo to another, methods may change signatures, be removed entirely, or have different return types.

## Transformation Strategy

The transformation works by:

1. **Pattern Matching**: Identifying specific method calls that are known to break between versions
2. **Generic Replacement**: Replacing problematic patterns with compatible alternatives
3. **Parameterization**: Making the transformation configurable for different dependency versions

## Example Breakages Handled

### 1. whenCompleteWithContext Method Signature Changes
- Old versions: `result.whenCompleteWithContext(consumer)`
- New versions: May have different signatures or be unavailable

### 2. Service Name Resolution Changes
- Old versions: Using `invoker.getInterface().getName()` or `invoker.getUrl().getServiceInterface()`
- New versions: Using `invocation.getTargetServiceUniqueName()` (v3 only)

### 3. Result Interface Compatibility
- Different Result implementations between Dubbo versions

## Usage

```bash
java -cp spoon-base-template/target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main /path/to/project/src
```

## Implementation Details

The transformation uses Spoon's AST traversal to:
- Find method invocations matching specific patterns
- Replace them with compatible alternatives
- Maintain the semantic meaning of the original code

## Customization

To customize this transformation for other breaking changes:
1. Modify the pattern matching logic in `applyTransformations()`
2. Add new transformation methods for specific breaking changes
3. Parameterize the replacements with version-specific values

## Files Modified

- `src/main/java/github/chains/Main.java`: Core transformation logic
- `pom.xml`: Dependencies for the Spoon transformation

This transformation ensures that Maven projects can be migrated to newer dependency versions without manual code modifications, making it reusable across different breaking changes.