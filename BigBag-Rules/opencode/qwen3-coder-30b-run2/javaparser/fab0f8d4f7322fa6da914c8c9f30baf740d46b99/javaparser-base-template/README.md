# Generic Transformation for HTTP API v1.1.0 Breaking Change

## Problem
The `Connection.accept()` method signature changed in HTTP API v1.1.0 from:
```
accept(RsStatus, Iterable, Publisher)
```
to:
```
accept(RsStatus, Headers, Publisher)
```

## Solution
Transform all calls from:
```java
connection.accept(status, iterable, publisher)
```
to:
```java
connection.accept(status, new Headers.From(iterable), publisher)
```

## Files Modified
1. `src/main/java/com/artipie/docker/http/DockerAuthSlice.java` - Fixed the method call
2. Added proper import for `com.artipie.http.Headers`

## Generic Rule
This transformation can be applied to any project with:
1. Calls to `Connection.accept()` with 3 arguments
2. Second argument is an Iterable (which should be wrapped in `Headers.From`)
3. Uses HTTP API v1.1.0 or later

## How to Apply
1. Replace the second argument with `new Headers.From(argument)`
2. Add import statement: `import com.artipie.http.Headers;`
3. Ensure project has HTTP v1.1.0 dependency

## Example
**Before:**
```java
connection.accept(status, headersIterable, publisher);
```

**After:**
```java
connection.accept(status, new Headers.From(headersIterable), publisher);
```