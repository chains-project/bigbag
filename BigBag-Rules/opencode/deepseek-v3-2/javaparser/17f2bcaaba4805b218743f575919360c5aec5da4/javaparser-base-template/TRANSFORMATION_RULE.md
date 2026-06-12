# Generic Transformation Rule for tinspin-indexes 1.8.0 Breaking Changes

## Overview
This JavaParser-based transformation rule automatically fixes compilation errors caused by breaking API changes in tinspin-indexes library version 1.8.0.

## Breaking Change Identified
**Deprecated API**: `KDTree.create(int dimensions, PointDistanceFunction distanceFunction)`

**New API Pattern**: `KDTree.create(int dimensions)` or `KDTree.create(IndexConfig config)`

**Transformation Required**: Remove the `PointDistanceFunction` parameter from `create()` method calls.

## Rule Implementation
The transformation rule (`KDTreeCreateTransformer` class) performs the following:

1. **Pattern Matching**: Identifies method calls to `create()` with exactly 2 arguments
2. **Heuristic Detection**: Uses heuristics to identify PointDistanceFunction arguments:
   - Lambda expressions (`(p1, p2) -> ...`)
   - Method references (`SomeClass::method`)
   - Explicit PointDistanceFunction type references
3. **Transformation**: Removes the second argument (the distance function)
4. **Warning**: Outputs guidance to use distance functions in query methods instead

## Generic Design Principles
1. **No Project-Specific Hardcoding**: The rule doesn't hardcode "KDTree" - it works for any class with a `create()` method taking a distance function
2. **Structural Pattern Matching**: Uses AST pattern matching instead of text-based matching
3. **Parameterized by API Patterns**: Can be extended for other breaking changes by modifying the pattern matcher

## Usage
```bash
java -jar javaparser.jar <source-directory>
```

## Extending for Other Breaking Changes
To handle other deprecated APIs (e.g., `knnQuery()` methods), add additional pattern matchers to the `KDTreeCreateTransformer` class following the same pattern:
1. Identify the method signature pattern
2. Check argument count and types
3. Apply the appropriate transformation

## Example Transformation
**Before**:
```java
KDTree.create(2, (p1, p2) -> {
    final double deltaX = p1[0] - p2[0];
    final double deltaY = p1[1] - p2[1];
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
});
```

**After**:
```java
KDTree.create(2);
```

## Notes
- The transformation assumes custom distance functions are no longer needed in constructors
- If custom distance logic is required, it should be moved to query methods like `knnQuery()`
- The rule is conservative - it only transforms when it detects patterns likely to be PointDistanceFunctions