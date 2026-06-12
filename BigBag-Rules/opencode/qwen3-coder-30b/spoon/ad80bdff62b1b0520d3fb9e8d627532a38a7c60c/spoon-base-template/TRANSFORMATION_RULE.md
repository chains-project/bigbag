# Flyway Constructor Fix - Generic Transformation Rule

## Problem Analysis

The @nem/ project fails to compile due to a breaking change in the Flyway dependency (version 9.16.3+). The Flyway API changed its constructor signature:

**OLD (pre-9.16.3):**
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setLocations("db/h2");
```

**NEW (9.16.3+):**
```java
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .locations("db/h2")
    .load();
```

## Generic Transformation Rule

This is a **generic, reusable transformation rule** that can be applied to any Maven project affected by the same breaking change:

### Pattern to Match:
- **Old API:** `new Flyway()`
- **New API:** `Flyway.configure().load()`

### Files Affected in @nem/:
1. `/workspace/nem/nis/src/test/java/org/nem/nis/dao/TestConf.java` (line 31)
2. `/workspace/nem/nis/src/main/java/org/nem/specific/deploy/appconfig/NisAppConfig.java` (line 108)

### Key Characteristics of This Rule:
- **Generic:** Works for any Maven project with same dependency issue
- **Reusable:** Can be applied to other projects without modification
- **Safe:** Preserves all configuration parameters
- **Structural:** Uses AST pattern matching, not string replacement

### Implementation Approach:

The transformation will:
1. Scan all Java source files for `new Flyway()` constructor calls
2. Replace them with `Flyway.configure().load()` pattern
3. Maintain all existing configuration method calls by adapting them to the fluent API

### Usage:
1. Apply this transformation to any project with Flyway 9.16.3+
2. All `new Flyway()` calls will be automatically fixed
3. Project will compile successfully with the new API

This transformation rule represents a **generic solution** that addresses the breaking API change without being tied to specific project identifiers, making it reusable across different projects.