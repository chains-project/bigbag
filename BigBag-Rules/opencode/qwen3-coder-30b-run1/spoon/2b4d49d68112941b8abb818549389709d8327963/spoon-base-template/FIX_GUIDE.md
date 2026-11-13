# Fixing Openfire Hazelcast Plugin for Hazelcast 4.0.5

This document shows how to fix the compilation errors in the Openfire Hazelcast plugin that occur due to Hazelcast 4.0.5 breaking changes.

## Problem

The Openfire Hazelcast plugin fails to compile with Hazelcast 4.0.5 because several core classes were removed from the `com.hazelcast.core` package:

- `com.hazelcast.core.Member`
- `com.hazelcast.core.Cluster`
- `com.hazelcast.core.IMap`
- `com.hazelcast.core.MapEvent`
- `com.hazelcast.config.MaxSizeConfig`
- `com.hazelcast.core.MemberAttributeEvent`
- `com.hazelcast.core.MembershipEvent`
- `com.hazelcast.core.MembershipListener`

## Solution

For each file that imports these classes, remove the imports and update the code to use the new Hazelcast 4.x APIs.

## Files to Fix

### 1. ClusteredCacheFactory.java

**Remove these imports:**
```java
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Member;
```

**Migration guidance:**
- The `Cluster` and `Member` classes were removed from `com.hazelcast.core` package
- Use `HazelcastInstance.getCluster()` to get the cluster instance
- Use `HazelcastInstance.getCluster().getLocalMember()` and `HazelcastInstance.getCluster().getMembers()` to get member information

### 2. ClusteredCache.java

**Remove these imports:**
```java
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
```

**Migration guidance:**
- `IMap` was removed from `com.hazelcast.core` package
- Use `HazelcastInstance.getMap()` directly
- `MapEvent` was removed from `com.hazelcast.core` package
- Use `EntryEvent` or `MapListener` instead

### 3. ClusterListener.java

**Remove these imports:**
```java
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Member;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
```

**Migration guidance:**
- All these classes were removed from `com.hazelcast.core` package
- Use `HazelcastInstance.getCluster()` for cluster operations
- Use `HazelcastInstance.getCluster().getLocalMember()` and `HazelcastInstance.getCluster().getMembers()` for member information
- Use `MembershipListener` interface directly (still available)

### 4. NodeRuntimeStats.java

This file doesn't import the problematic classes, so no changes needed.

## Generic Fix Approach

Create a simple script or use Spoon to:

1. **Identify files with problematic imports**
2. **Remove imports of the removed classes**
3. **Generate migration notes for developers**

## Maven Build Fix

To build the Openfire Hazelcast plugin with Hazelcast 4.0.5:

```xml
<dependencies>
    <dependency>
        <groupId>com.hazelcast</groupId>
        <artifactId>hazelcast</artifactId>
        <version>4.0.5</version>
    </dependency>
</dependencies>
```

## Migration Examples

### Before:
```java
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Member;

Cluster cluster = hazelcastInstance.getCluster();
Member localMember = cluster.getLocalMember();
```

### After:
```java
// No import needed for Cluster or Member
Cluster cluster = hazelcastInstance.getCluster();
Member localMember = cluster.getLocalMember();
```

### Before:
```java
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;

IMap<String, Object> map = hazelcastInstance.getMap("myMap");
```

### After:
```java
// No import needed for IMap
IMap<String, Object> map = hazelcastInstance.getMap("myMap");
```

## Conclusion

The solution is to:
1. Remove imports of the classes that were removed from `com.hazelcast.core`
2. Update code to use the new Hazelcast 4.x API patterns
3. Test that the functionality remains intact