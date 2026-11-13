# Flyway 9.x Migration Tool

This tool detects Flyway constructor calls that need to be updated for compatibility with Flyway 9.x.

## Problem

In Flyway 9.x, the constructor `new Flyway()` is no longer available. The API has changed to require a fluent configuration approach.

## Detected Issues

The tool found 2 problematic constructor calls in the NEM project:

1. **File:** `TestConf.java`, **Line:** 31
   - **Old Code:** `new Flyway()`
   - **Fix:** Change to `Flyway.configure().dataSource(...).locations(...).load()`

2. **File:** `NisAppConfig.java`, **Line:** 108
   - **Old Code:** `new Flyway()`
   - **Fix:** Change to `Flyway.configure().dataSource(...).locations(...).load()`

## Migration Pattern

**Before (Flyway < 9.x):**
```java
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.setLocations("db/h2");
```

**After (Flyway 9.x):**
```java
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .locations("db/h2")
    .load();
```

## How to Fix

1. Locate each occurrence reported by this tool
2. Replace the constructor call with the new fluent API pattern
3. Ensure all necessary configuration methods are properly migrated to the fluent API

## Usage

```bash
mvn compile exec:java -Dexec.mainClass="github.chains.SimpleFlywayDetector"
```