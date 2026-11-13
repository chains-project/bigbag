# Dubbo AsyncRpcResult Compatibility Fix

This is a generic JavaParser transformation rule designed to fix compilation errors caused by breaking changes in Dubbo dependency updates (specifically version 2.7.21+).

## Problem

In Dubbo 2.7.21+, the signature of `RpcContext.getContext().setFuture()` changed to expect a `CompletableFuture` instead of an `AsyncRpcResult` object. This causes compilation errors when existing code tries to pass `AsyncRpcResult` directly to this method.

## Solution

This transformation automatically converts code patterns like:
```java
RpcContext.getContext().setFuture(asyncRpcResult);
FutureContext.getContext().setCompatibleFuture(asyncRpcResult);
```

To:
```java
RpcContext.getContext().setFuture(asyncRpcResult.getResponseFuture());
FutureContext.getContext().setCompatibleFuture(asyncRpcResult.getResponseFuture());
```

## Usage

1. Build the transformation:
```bash
mvn compile
```

2. Run the transformation on a Java file:
```bash
java -cp target/classes github.chains.Main /path/to/your/SourceFile.java
```

3. The transformation will modify the file in-place to fix the compatibility issue.

## Target Pattern Recognition

The transformation looks for:
- Method calls to `RpcContext.getContext().setFuture(...)`
- Method calls to `FutureContext.getContext().setCompatibleFuture(...)`
- Arguments that reference variables containing "asyncRpcResult" or "AsyncRpcResult"

## Compatibility

This rule is designed to be reusable across any Maven project that encounters this specific breaking change in Dubbo dependencies, not just the specific `@arex-agent-java` project.