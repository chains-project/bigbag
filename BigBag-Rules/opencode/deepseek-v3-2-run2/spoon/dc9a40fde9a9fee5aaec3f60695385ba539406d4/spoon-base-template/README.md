# Dubbo 2.7.21 API Breaking Change Transformation

## Problem
Dubbo 2.7.21 introduced a breaking API change:
- `RpcContext.setFuture(Object)` → `RpcContext.setFuture(CompletableFuture<?>)`
- `FutureContext.setCompatibleFuture(Object)` → `FutureContext.setCompatibleFuture(CompletableFuture<?>)`

## Solution
The Spoon transformation in `/workspace/spoon-base-template/src/main/java/github/chains/Main.java` automatically fixes this by:

1. **For `RpcContext.setFuture(AsyncRpcResult)` calls:**
   - Original: `RpcContext.getContext().setFuture(asyncRpcResult)`
   - Transformed: `RpcContext.getContext().setFuture(new FutureAdapter<>(asyncRpcResult.getResponseFuture()))`

2. **For `FutureContext.setCompatibleFuture(AsyncRpcResult)` calls:**
   - Original: `FutureContext.getContext().setCompatibleFuture(asyncRpcResult)`
   - Transformed: `FutureContext.getContext().setCompatibleFuture(asyncRpcResult.getResponseFuture())`

## How to Use

### 1. Build the transformation:
```bash
cd /workspace/spoon-base-template
mvn clean package
```

### 2. Run the transformation on your project:
```bash
java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(cat cp.txt)" \
  github.chains.Main /path/to/your/project/src/main/java
```

### 3. Check transformed code:
Transformed files are written to `src/main/java/../transformed/`

### 4. Manual step (if needed):
Add import for `FutureAdapter` if not already present:
```java
import org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter;
```

## Breaking Change Details

**Old API (pre-2.7.21):**
```java
// Accepts any Future implementation
RpcContext.setFuture(Future future);
FutureContext.setCompatibleFuture(Future future);
```

**New API (2.7.21+):**
```java
// Requires CompletableFuture specifically
RpcContext.setFuture(CompletableFuture<?> future);
FutureContext.setCompatibleFuture(CompletableFuture<?> future);
```

**Why this transformation works:**
- `AsyncRpcResult.getResponseFuture()` returns `CompletableFuture<AppResponse>`
- `FutureAdapter` wraps `CompletableFuture<AppResponse>` into a `Future`-compatible interface
- The transformation preserves backward compatibility while meeting new API requirements

## Generic Applicability
This transformation is generic and can be applied to ANY Maven project affected by this Dubbo API change. It:
- Uses pattern matching to find `AsyncRpcResult` arguments
- Works with any variable naming convention (camelCase, snake_case, etc.)
- Handles both `RpcContext` and `FutureContext` method calls
- Maintains code structure and formatting