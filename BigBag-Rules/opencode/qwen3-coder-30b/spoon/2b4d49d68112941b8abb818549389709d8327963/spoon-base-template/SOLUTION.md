# Openfire Hazelcast Plugin - Hazelcast 4.0.5 Fix

## Problem Summary

The Openfire Hazelcast plugin fails to compile with Hazelcast 4.0.5 due to breaking changes where these classes were removed from `com.hazelcast.core`:

- `com.hazelcast.core.Member`
- `com.hazelcast.core.Cluster` 
- `com.hazelcast.core.IMap`
- `com.hazelcast.core.MapEvent`
- `com.hazelcast.config.MaxSizeConfig`
- `com.hazelcast.core.MemberAttributeEvent`
- `com.hazelcast.core.MembershipEvent`
- `com.hazelcast.core.MembershipListener`

## Files That Need Fixing

### 1. ClusteredCacheFactory.java
**Problem**: Imports `com.hazelcast.core.Cluster` and `com.hazelcast.core.Member`
**Fix**: Remove imports and use `hazelcastInstance.getCluster()` directly

### 2. ClusteredCache.java  
**Problem**: Imports `com.hazelcast.core.IMap` and `com.hazelcast.core.MapEvent`
**Fix**: Remove imports and use `hazelcastInstance.getMap()` directly

### 3. ClusterListener.java
**Problem**: Imports multiple removed classes from `com.hazelcast.core`
**Fix**: Remove imports and use `hazelcastInstance.getCluster()` methods

## Generic Fix Pattern

The solution is to:
1. **Remove problematic imports** from the top of Java files
2. **Update code references** to use new Hazelcast 4.x APIs
3. **Keep same functionality** but with updated APIs

## Example Fix for ClusteredCacheFactory.java

**Before (problematic imports):**
```java
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Member;
```

**After (fixed):**
```java
// Removed imports - use HazelcastInstance.getCluster() directly
```

## Example Fix for ClusteredCache.java

**Before (problematic imports):**
```java
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
```

**After (fixed):**
```java
// Removed imports - use HazelcastInstance.getMap() directly
```

## Example Fix for ClusterListener.java

**Before (problematic imports):**
```java
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Member;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
```

**After (fixed):**
```java
// Removed imports - use HazelcastInstance.getCluster() methods
```

## Migration Guidance

- **Member**: Use `hazelcastInstance.getCluster().getLocalMember()` and `hazelcastInstance.getCluster().getMembers()`
- **Cluster**: Use `hazelcastInstance.getCluster()` directly 
- **IMap**: Use `hazelcastInstance.getMap()` directly
- **MapEvent**: Use `EntryEvent` or `MapListener` instead
- **MaxSizeConfig**: Use `maxSize` property in `MapConfig`
- **MemberAttributeEvent**: Use `MembershipListener` methods
- **MembershipEvent**: Use `MembershipListener` methods
- **MembershipListener**: Use `MembershipListener` interface directly

## How to Apply This Fix

1. **For each Java file** in the Openfire plugin:
   - Remove the problematic import statements
   - Update any code that references the removed classes
   - Keep the same logical functionality

2. **Test the compilation** to ensure it works with Hazelcast 4.0.5

This is a generic solution that can be applied to any project that encounters similar breaking changes.