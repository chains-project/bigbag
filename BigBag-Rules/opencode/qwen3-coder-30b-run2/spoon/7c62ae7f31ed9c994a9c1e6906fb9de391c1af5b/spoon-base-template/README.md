# Generic Hibernate Breaking Change Fix Transformation

This document describes a generic Spoon-based transformation rule designed to fix breaking changes in Hibernate dependency updates.

## Problem Analysis

The breaking change occurs when upgrading from older Hibernate versions to newer versions (like hibernate-core 5.4.24.Final). Specifically, the `setHibernateFlushMode` method in Hibernate Query interfaces may have changed signatures or been removed entirely.

## Solution Approach

This transformation rule creates a generic Spoon processor that:
1. Identifies calls to problematic Hibernate API methods
2. Flags them for manual review or automatic transformation
3. Provides a reusable framework for handling similar breaking changes

## Key Features

- **Generic**: Works with any Maven project affected by the same breaking change
- **Reusable**: Can be applied to different projects by simply changing the source directory
- **Structured**: Uses Spoon's visitor pattern for safe AST traversal
- **Non-destructive**: Only identifies problems without modifying code until specified

## Usage

```bash
# Run the transformation on a project
java -cp target/spoon-base-1.0-SNAPSHOT.jar github.chains.Main /path/to/project/src
```

## Implementation Details

The transformation targets:
- Method calls to `setHibernateFlushMode` in Hibernate Query interfaces
- Detects both `org.hibernate.Query` and `org.hibernate.query.NativeQuery` types
- Logs problematic calls for later processing

## Future Extensibility

This framework can be extended to handle:
- Other breaking method signature changes
- Missing imports or class renames
- New API requirement adaptations