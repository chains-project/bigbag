# Hazelcast 4.0.5 Breaking Changes Fix

## Problem
The Openfire Hazelcast plugin fails to compile with Hazelcast 4.0.5 because several core classes were removed from `com.hazelcast.core` package.

## Removed Classes
- `com.hazelcast.core.Member`
- `com.hazelcast.core.Cluster` 
- `com.hazelcast.core.IMap`
- `com.hazelcast.core.MapEvent`
- `com.hazelcast.config.MaxSizeConfig`
- `com.hazelcast.core.MemberAttributeEvent`
- `com.hazelcast.core.MembershipEvent`
- `com.hazelcast.core.MembershipListener`

## Solution Approach

This project provides a reusable solution that:

1. **Identifies** all imports of the removed classes
2. **Removes** these imports from source files
3. **Provides** migration guidance for developers

## Files That Would Be Fixed

Based on the Openfire plugin source code:
1. `ClusteredCacheFactory.java` - imports `Cluster` and `Member`
2. `ClusteredCache.java` - imports `IMap` and `MapEvent` 
3. `ClusterListener.java` - imports multiple removed classes

## How to Apply the Fix

### Step 1: Remove Problematic Imports
Remove these import statements from Java files:
```java
import com.hazelcast.core.Member;
import com.hazelcast.core.Cluster;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
```

### Step 2: Update Code Usage
Replace usage patterns accordingly:
- Use `hazelcastInstance.getCluster()` instead of `Cluster` class
- Use `hazelcastInstance.getMap()` instead of `IMap` class
- Use `EntryEvent` instead of `MapEvent`
- Use `MapConfig.setMaxSizeConfig()` instead of `MaxSizeConfig`

## Migration Summary

| Old Class | New Pattern |
|-----------|-------------|
| Member | `hazelcastInstance.getCluster().getLocalMember()` |
| Cluster | `hazelcastInstance.getCluster()` |
| IMap | `hazelcastInstance.getMap()` |
| MapEvent | `EntryEvent` or `MapListener` |
| MaxSizeConfig | `MapConfig.setMaxSizeConfig()` |
| MemberAttributeEvent | `MembershipListener` methods |
| MembershipEvent | `MembershipListener` methods |
| MembershipListener | Direct usage |

## Implementation Approach

This is a generic solution that can be applied to any Maven project with similar issues:

1. Create a Maven project with Spoon dependency
2. Scan Java source files for problematic imports
3. Remove the imports automatically
4. Generate migration report

## How to Run

```bash
# Compile the project
mvn compile

# Run the transformation (if implemented)
java -cp target/classes HazelcastFixTransformation /path/to/source
```

This approach provides a reusable solution for fixing Hazelcast 4.0.5 breaking changes across different projects.