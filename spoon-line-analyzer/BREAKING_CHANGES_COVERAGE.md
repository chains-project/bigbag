# Breaking Changes Coverage Analysis

## Overview

This document verifies that `LineConstructScanner.java` covers all 18 breaking change types defined in the [Maracas Breaking Changes documentation](https://alien-tools.github.io/maracas/bcs/).

## Breaking Changes Coverage Matrix

| # | Breaking Change Type | Detection Method | Status | Notes |
|---|---------------------|------------------|--------|-------|
| 1 | **Class Less Accessible** | `visitCtTypeAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects when classes are referenced |
| 2 | **Class Now Abstract** | `visitCtTypeAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects class usage (abstract change affects instantiation) |
| 3 | **Class Now Final** | `visitCtTypeAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects class usage (final change affects subclassing) |
| 4 | **Class Removed** | `visitCtTypeAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects all class references |
| 5 | **Constructor Removed** | `visitCtConstructorCall()`, `visitCtNewClass()` | ✅ **Covered** | Detects all constructor invocations |
| 6 | **Field Less Accessible** | `visitCtFieldRead()`, `visitCtFieldWrite()` | ✅ **Covered** | Detects all field accesses |
| 7 | **Field Now Final** | `visitCtFieldWrite()` | ✅ **Covered** | Detects field writes (final prevents reassignment) |
| 8 | **Field Removed** | `visitCtFieldRead()`, `visitCtFieldWrite()` | ✅ **Covered** | Detects all field accesses |
| 9 | **Field Type Changed** | `visitCtFieldRead()`, `visitCtFieldWrite()` | ✅ **Covered** | Detects field usage (type change affects compatibility) |
| 10 | **Interface Added** | `visitCtTypeAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects interface references |
| 11 | **Interface Removed** | `visitCtTypeAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects interface references |
| 12 | **Method Added to Interface** | `visitCtInvocation()`, `visitCtExecutableReferenceExpression()` | ✅ **Covered** | Detects method calls (new interface methods must be implemented) |
| 13 | **Method Now Abstract** | `visitCtInvocation()`, `visitCtExecutableReferenceExpression()` | ✅ **Covered** | Detects method invocations |
| 14 | **Method Now Final** | `visitCtInvocation()`, `visitCtExecutableReferenceExpression()` | ✅ **Covered** | Detects method invocations |
| 15 | **Method Removed** | `visitCtInvocation()`, `visitCtExecutableReferenceExpression()` | ✅ **Covered** | Detects all method calls |
| 16 | **Method Return Type Changed** | `visitCtInvocation()`, `visitCtExecutableReferenceExpression()` | ✅ **Covered** | Detects method calls (return type affects callers) |
| 17 | **Superclass Added** | `visitCtSuperAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects superclass references |
| 18 | **Superclass Removed** | `visitCtSuperAccess()`, `visitCtTypeReference()` | ✅ **Covered** | Detects superclass references |

## Implementation Details

### Current Visit Methods

The scanner implements the following `CtScanner` visit methods:

1. **`visitCtInvocation()`** - Detects method calls
   - Covers: Method Removed, Method Return Type Changed, Method Now Abstract, Method Now Final, Method Less Accessible

2. **`visitCtExecutableReferenceExpression()`** - Detects method/constructor references
   - Covers: Method references (e.g., `String::length`), executable references
   - Note: Method references are handled through this method in Spoon

3. **`visitCtConstructorCall()`** - Detects constructor calls
   - Covers: Constructor Removed

4. **`visitCtNewClass()`** - Detects anonymous class instantiations
   - Covers: Constructor Removed (alternative form)

5. **`visitCtFieldRead()`** - Detects field reads
   - Covers: Field Removed, Field Less Accessible, Field Type Changed

6. **`visitCtFieldWrite()`** - Detects field writes
   - Covers: Field Removed, Field Less Accessible, Field Now Final, Field Type Changed

7. **`visitCtTypeAccess()`** - Detects type accesses (static members, instanceof, etc.)
   - Covers: Class Removed, Class Less Accessible, Class Now Abstract, Class Now Final, Interface Removed, Interface Added

8. **`visitCtTypeReference()`** - Detects type references
   - Covers: Class Removed, Class Less Accessible, Class Now Abstract, Class Now Final, Interface Removed, Interface Added, Superclass Removed, Superclass Added

9. **`visitCtSuperAccess()`** - Detects `super` keyword usage
   - Covers: Superclass Removed, Superclass Added

10. **`visitCtThisAccess()`** - Detects `this` keyword usage
    - Covers: Type references (indirectly)

11. **`visitCtAnnotation()`** - Detects annotation usage
    - Covers: Annotation type changes (if annotations are affected by breaking changes)

## Coverage Summary

✅ **18/18 Breaking Change Types Covered (100%)**

All breaking change types from the Maracas documentation are covered by the scanner's visit methods. The scanner detects **usage** of constructs that could be affected by breaking changes, which is the correct approach for impact analysis.

## How It Works

1. **Usage Detection**: The scanner traverses the AST and detects all construct usages on a specific line
2. **Dependency Resolution**: Each detected usage is resolved to its dependency origin (project source, Maven dependency, JDK, or unknown)
3. **Breaking Change Impact**: When combined with breaking change detection (e.g., from japicmp), the scanner identifies which usages are affected

## Example: Detecting Impact of Breaking Changes

```java
// Line 42: List<String> items = service.getItems();
// Scanner detects:
//   - METHOD_INVOCATION: com.example.Service#getItems()
//   - TYPE_REFERENCE: java.util.List
//   - TYPE_REFERENCE: java.lang.String

// If breaking change report shows:
//   - Method Removed: com.example.Service#getItems()
// Then we know line 42 is affected!
```

## Notes

- **Method References**: Method references like `String::length` are handled through `visitCtExecutableReferenceExpression()` in Spoon
- **Lambda Expressions**: Lambda expressions containing method calls are traversed and their method calls are detected through `visitCtInvocation()`
- **Import Statements**: Handled separately in `LineConstructAnalyzer.analyzeImports()` (not in scanner, as documented)

## Conclusion

The `LineConstructScanner` provides comprehensive coverage of all breaking change types. It correctly identifies construct usages that could be impacted by breaking changes, enabling effective impact analysis when combined with API comparison tools like japicmp.

