# Flyway v8 to v9.19.4 Migration Transformation

## Problem Analysis

The @nem/ project fails to compile due to a breaking change in the Flyway dependency:

### Breaking Changes in Flyway v9.19.4:
1. **Constructor Change**: `new Flyway()` now requires `org.flywaydb.core.api.configuration.Configuration` parameter
2. **Removed Methods**: 
   - `setDataSource(DataSource)` 
   - `setClassLoader(ClassLoader)`
   - `setLocations(String)`
   - `setValidateOnMigrate(Boolean)`

### Error Messages:
```
constructor Flyway in class org.flywaydb.core.Flyway cannot be applied to given types;
  required: org.flywaydb.core.api.configuration.Configuration
  found:    no arguments
cannot find symbol
  symbol:   method setDataSource(javax.sql.DataSource)
```

## Generic Transformation Strategy

### Abstract Pattern:
- **Old Pattern**: `new Flyway().setDataSource(...).setLocations(...)` 
- **New Pattern**: `new Flyway(FluentConfiguration.configure().dataSource(...).locations(...).load())`

### Implementation Approach:
1. **Match all `new Flyway()` calls** that take no arguments
2. **Remove deprecated setter method calls** on Flyway objects
3. **Replace with FluentConfiguration pattern**

## Transformation Rule Design

The transformation should be generic enough to handle any project with the same breaking change. The key elements are:

### 1. Pattern Matching:
- Match constructor calls to `org.flywaydb.core.Flyway` with zero parameters
- Match setter calls to `setDataSource`, `setClassLoader`, `setLocations`, `setValidateOnMigrate` on any variable of type `Flyway`

### 2. Replacement Strategy:
- Replace constructor with proper `new Flyway(Configuration)` call
- Convert the initialization sequence to use `FluentConfiguration.configure()...` pattern
- Remove deprecated setter method calls

### 3. Generic Parameters:
- Fully qualified type names (not project-specific)
- Method signatures from the dependency API (not client code)

## Technical Implementation

The transformation would need to:
1. Parse Java AST to identify Flyway constructor and method calls
2. Replace the problematic constructor with new Configuration-based one
3. Remove deprecated setters that no longer exist
4. Reconstruct the initialization in the new API format

This approach ensures the transformation can be reused across any project facing the same Flyway breaking change.