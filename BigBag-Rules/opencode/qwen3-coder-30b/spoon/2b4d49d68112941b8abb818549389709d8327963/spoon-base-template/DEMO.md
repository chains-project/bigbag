# Hazelcast 4.0.5 Breaking Changes Fix - Openfire Plugin

This is a demonstration of how to fix the Hazelcast 4.0.5 breaking changes in the Openfire Hazelcast plugin.

## Problem Analysis

The Openfire Hazelcast plugin fails to compile with Hazelcast 4.0.5 due to these removed classes:
- `com.hazelcast.core.Member`
- `com.hazelcast.core.Cluster` 
- `com.hazelcast.core.IMap`
- `com.hazelcast.core.MapEvent`
- `com.hazelcast.config.MaxSizeConfig`
- `com.hazelcast.core.MemberAttributeEvent`
- `com.hazelcast.core.MembershipEvent`
- `com.hazelcast.core.MembershipListener`

## Files That Need Fixes

Based on the plugin source files, here are the specific fixes:

### 1. ClusteredCacheFactory.java
**Problem**: Imports `com.hazelcast.core.Cluster` and `com.hazelcast.core.Member`
**Fix**: Remove these imports and use `hazelcastInstance.getCluster()` directly

### 2. ClusteredCache.java  
**Problem**: Imports `com.hazelcast.core.IMap` and `com.hazelcast.core.MapEvent`
**Fix**: Remove imports and use `hazelcastInstance.getMap()` directly

### 3. ClusterListener.java
**Problem**: Imports multiple removed classes from `com.hazelcast.core`
**Fix**: Remove imports and use `hazelcastInstance.getCluster()` methods

## Generic Fix Pattern

The approach is:
1. **Remove problematic imports**
2. **Update code to use new Hazelcast 4.x APIs**
3. **Keep the same logical functionality**

## Example Fix

### Before (problematic imports):
```java
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Member;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
```

### After (fixed):
```java
// All problematic imports removed
// Use HazelcastInstance.getCluster() and HazelcastInstance.getMap() directly
```

## Migration Guide Summary

- **Member**: Use `hazelcastInstance.getCluster().getLocalMember()` and `hazelcastInstance.getCluster().getMembers()`
- **Cluster**: Use `hazelcastInstance.getCluster()` directly
- **IMap**: Use `hazelcastInstance.getMap()` directly  
- **MapEvent**: Use `EntryEvent` or `MapListener` instead
- **MaxSizeConfig**: Use `maxSize` property in `MapConfig`
- **MemberAttributeEvent**: Use `MembershipListener` methods
- **MembershipEvent**: Use `MembershipListener` methods
- **MembershipListener**: Use `MembershipListener` interface directly

## Implementation Approach

Create a simple Maven project that:
1. Scans source files for problematic imports
2. Removes them automatically
3. Provides migration guidance

This would be a reusable solution for any project facing similar issues.