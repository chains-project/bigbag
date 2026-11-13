# Fix for libthrift 0.16.0 Breaking Change

This document outlines a solution for the breaking dependency update in libthrift 0.16.0, where `TSerializer.serialize()` method now throws `TException` instead of `TTransportException`.

## Problem Analysis

The libthrift 0.16.0 release introduced a breaking change to `TSerializer.serialize()` which now throws `org.apache.thrift.TException` instead of `org.apache.thrift.transport.TTransportException`. This causes compilation errors in projects that call `TSerializer.serialize()` without proper exception handling.

Example error:
```
unreported exception org.apache.thrift.transport.TTransportException; must be caught or declared to be thrown
```

## Solution Approach

We need to identify all `TSerializer.serialize()` calls and wrap them in try/catch blocks to handle the new `TException`.

### Specific Case in singer Project

File: `singer-commons/src/main/java/com/pinterest/singer/loggingaudit/client/AuditEventKafkaSender.java`
Line: 269
Code: `value = serializer.serialize(event);`

## Manual Fix for the Singer Project

The fix is to wrap the serialize call in a try/catch block:

```java
try {
    value = serializer.serialize(event);
} catch (TException e) {
    // Handle the exception appropriately
    e.printStackTrace();
    // Or more appropriately, log and drop the event
}
```

## Automated Solution Approach

To create a reusable JavaParser-based solution, you would:

1. Parse Java files to find `TSerializer.serialize()` method calls
2. Identify the scope of the calls (which objects are being serialized)
3. Wrap each call in a try/catch block for `TException`

However, due to compatibility issues with the JavaParser version in our environment, here's the core approach you'd implement:

```java
// Pseudocode for the transformation
if (methodCall.getNameAsString().equals("serialize")) {
    if (methodCall.getScope().map(scope -> 
        scope.toString().contains("TSerializer") || 
        scope.toString().contains("serializer") ||
        scope.toString().contains("new TSerializer()")
    ).orElse(false)) {
        // Wrap in try/catch block for TException
        // This is where the JavaParser AST manipulation would happen
    }
}
```

## Implementation Steps

1. Locate all TSerializer.serialize() calls in the codebase
2. For each call, create a try/catch block that catches TException
3. Preserve the original method call within the try block
4. Handle the TException appropriately in the catch block

## Testing

To test the fix in the singer project:
1. Apply the manual fix to `AuditEventKafkaSender.java` line 269
2. Run `mvn compile` to verify the compilation error is resolved
3. Run tests to verify functionality remains intact