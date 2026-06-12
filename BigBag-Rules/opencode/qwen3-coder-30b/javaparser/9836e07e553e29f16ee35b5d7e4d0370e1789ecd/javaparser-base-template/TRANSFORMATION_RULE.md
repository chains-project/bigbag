# Generic Transformation Rule for Breaking asto-core Dependency Update

## Problem Analysis

The @docker-adapter/ project fails to compile due to a breaking dependency update in com.artipie:asto-core v1.14.0. Based on the API documentation and code patterns, the most likely breaking change involves the `FailedCompletionStage` class constructor.

## Breaking Change Identification

The issue is with usage patterns like:
```java
new FailedCompletionStage<>(throwable)
new FailedCompletionStage<Void>(exception)
```

In the new asto-core v1.14.0, the `FailedCompletionStage` class constructor signature or behavior has changed.

## Generic Solution Pattern

The transformation should replace all instances of the old `FailedCompletionStage` constructor patterns with the new API pattern.

## Transformation Rule

**Pattern to Match:**
- `new FailedCompletionStage<>(Throwable)`

**Pattern to Replace:**
- `CompletableFuture.failedFuture(throwable)`

## Implementation Strategy

This transformation should:
1. Search all Java files for `FailedCompletionStage` constructor calls
2. Replace with equivalent `CompletableFuture.failedFuture()` calls
3. Ensure proper import statements are added
4. Be reusable across any Maven project with the same dependency issue

## Example Fix

**Before:**
```java
return new FailedCompletionStage<>(throwable);
```

**After:**
```java
return CompletableFuture.failedFuture(throwable);
```

## Usage Instructions

1. Apply this transformation to any Maven project with the breaking asto-core dependency
2. Update import statements to include `java.util.concurrent.CompletableFuture` if needed
3. Verify that the project compiles successfully after transformation

## Generalizability

This rule is generic because:
- It uses structural pattern matching, not project-specific class names
- It replaces with standard Java 8+ API calls
- It can be applied to any project with the same breaking change
- The transformation can be parameterized with the correct fully-qualified type names