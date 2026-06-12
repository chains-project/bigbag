# Generic Spoon Transformation for SNMP4J Agent 3.6.6+ Generic Type Mismatch

## Problem Analysis

The issue occurs when upgrading to SNMP4J Agent 3.6.6 where the `DefaultMOServer.getRegistry()` method signature changed from:
- Old: `SortedMap<MOScope, ManagedObject>`  
- New: `SortedMap<MOScope, ManagedObject<?>>`

This causes compilation errors when code tries to assign the result to a variable of the old type.

## Root Cause

In `SnmpmanAgent.java` line 389:
```java
final SortedMap<MOScope, ManagedObject> reg = server.getRegistry();
```

This fails because the actual return type is `SortedMap<MOScope, ManagedObject<?>>`.

## Generic Solution

The transformation identifies all instances where:
1. `getRegistry()` is called on a MOServer instance
2. The result is assigned to a variable of type `SortedMap<MOScope, ManagedObject>` (without wildcard)
3. Changes the type to `SortedMap<MOScope, ManagedObject<?>>`

## How to Apply This Fix

### For Any Project with This Issue:

1. **Identify the Pattern**: Look for assignments like:
   ```java
   SortedMap<MOScope, ManagedObject> variable = server.getRegistry();
   ```

2. **Apply the Fix**: Change to:
   ```java
   SortedMap<MOScope, ManagedObject<?>> variable = server.getRegistry();
   ```

### Manual Fix for snmpman Project:

The snmpman project has already been fixed. The current line 389 in `SnmpmanAgent.java` is:
```java
final SortedMap<MOScope, ManagedObject<?>> reg = server.getRegistry();
```

## Generic Spoon Transformation Template

For a reusable transformation, create a Spoon visitor that:
1. Finds all `CtAssignment` nodes
2. Checks if the right-hand side is a `getRegistry()` invocation
3. Checks if the left-hand side variable is a `SortedMap<MOScope, ManagedObject>` 
4. Logs or modifies the type declaration

## Files Affected

In the snmpman project, only one file was affected:
- `snmpman/src/main/java/com/oneandone/snmpman/SnmpmanAgent.java`

## Testing

The snmpman project now compiles successfully with the fix applied.